require 'socket'
require 'google/protobuf'
require_relative 'Message_pb'
require_relative 'Configuration_pb'
require_relative 'Capacity_pb'

class ConfigReader
  attr_reader :fault_tolerance_level

  def initialize(file_path)
    @file_path = file_path
    parse_config
  end

  private

  def parse_config
    File.readlines(@file_path).each do |line|
      if line.start_with?("fault_tolerance_level")
        @fault_tolerance_level = line.split('=').last.strip.to_i
      end
    end
  end
end

class Configuration
  attr_reader :message

  def initialize(fault_tolerance_level, method)
    @message = CommunicationConfig::Configuration.new
    @message.fault_tolerance_level = fault_tolerance_level
    @message.method = method
  end
end

class Message
  attr_reader :message

  def initialize(demand, response)
    @message = Communication::Message.new
    @message.demand = demand
    @message.response = response
  end
end

class Capacity
  attr_reader :message

  def initialize(server_status, timestamp)
    @message = Communication::Capacity.new
    @message.server_status = server_status
    @message.timestamp = timestamp
  end
end

class Admin
  SERVER_PORTS = [7001, 7002, 7003]
 
  def initialize(config_file)
    config_reader = ConfigReader.new(config_file)
    @fault_tolerance_level = config_reader.fault_tolerance_level
    @config_message = Configuration.new(@fault_tolerance_level, "STRT").message
  end
 
  def create_sockets
      sockets = []
      SERVER_PORTS.each do |port|
        socket = TCPSocket.new('localhost', port)
        sockets << { socket: socket, port: port }
      end
      sockets
    end
 
  def send_start_command
    responses = {}
    sockets_with_ports = create_sockets
    
    sockets_with_ports.each do |item|
      socket = item[:socket]
      port = item[:port]
      begin
        request_data = @config_message.to_proto
        send_request(socket, request_data)
        puts "Başlama komutu gönderildi: Server #{port}"
 
        response_data = read_response(socket)
        response = Communication::Message.decode(response_data)
        puts "Gelen mesaj: Demand: #{response.demand}, Response: #{response.response}"
        
        case response.response
        when :YEP
          responses[port] = true
          puts "YEP mesajı aldı, işlem başarılı!"
        when :NOPE
          responses[port] = false
          puts "NOPE mesajı aldı, işlem başarısız!"
          send_stop_command(socket,port)
        else
          puts "Bilinmeyen yanıt: #{response.response}"
          send_stop_command(socket,port)
        end
        
      rescue StandardError => e
        puts "send_start_command: Sunucuya bağlanırken hata oluştu: #{e.message}"
      end
    end
    
    if responses.values.all?
      loop do
        begin
          check_capacity(sockets_with_ports, responses)
          sleep(5)
        rescue StandardError => e
          puts "check_capacity: Kapasite sorgusunda hata oluştu: #{e.message}"
          sockets_with_ports.each {|item| send_stop_command(item[:socket], item[:port])}
          break
        end
      end
    end
 
    sockets_with_ports.each {|item| item[:socket].close}
  end
 
  def check_capacity(sockets_with_ports, responses)
      sockets_with_ports.each do |item|
        socket = item[:socket]
        port = item[:port]
        if responses[port] == true
          begin
              data = Message.new("CPCTY", "YEP").message.to_proto
              send_request(socket, data)
              puts "Kapasite sorgusu gönderildi: Server #{port}"
    
              capacity_data = read_response(socket)
              capacity = Communication::Capacity.decode(capacity_data)
              puts "Server 1: #{capacity.server1_status}, Server 2: #{capacity.server2_status}, Server 3: #{capacity.server3_status}, "
          rescue StandardError => e
              puts "check_capacity: Sunucuya bağlanırken hata oluştu: #{e.message}"
          end
      end
    end
  end
 
  def send_stop_command(socket, port)
      begin
          data = Configuration.new(@fault_tolerance_level, "STOP").message.to_proto
          send_request(socket, data)
          puts "Durdurma komutu gönderildi: Server #{port}"
      rescue StandardError => e
          puts "send_stop_command: Sunucuya bağlanırken hata oluştu: #{e.message}"
      end
  end

  def send_request(socket, data)
    socket.write([data.bytesize].pack('N'))
    socket.write(data)
  end

  def read_response(socket)
    length_bytes = socket.read(4)
    if length_bytes.nil? || length_bytes.bytesize < 4
      raise "Bağlantıdan yeterli uzunluk bilgisi okunamadı."
    end

    length = length_bytes.unpack1('N')

    if length <= 0 || length > 10_000
      raise "Geçersiz veri uzunluğu: #{length}"
    end

    data = socket.read(length)
    if data.nil? || data.bytesize != length
      raise "Tam veri alınamadı."
    end

    return data
  end
end

admin = Admin.new('dist_subs.conf')
admin.send_start_command
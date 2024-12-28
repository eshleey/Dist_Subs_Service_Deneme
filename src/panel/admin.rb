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
  SERVER_PORTS = [7004, 7005, 7006]

  def initialize(config_file)
    config_reader = ConfigReader.new(config_file)
    @fault_tolerance_level = config_reader.fault_tolerance_level
    @config_message = Configuration.new(@fault_tolerance_level, "STRT").message
  end

  def read_message(socket)
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

    message = Communication::Message.decode(data)
    puts "Gelen mesaj: Demand: #{message.demand}, Response: #{message.response}"

    return message
  end


  def read_capacity(socket)
    puts "read_capacity içerisine girdi."
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

    begin
      capacity = Communication::Capacity.decode(data)
    
      status_str = if capacity.respond_to?(:server1_status)
        "Server 1: #{capacity.server1_status}, Server 2: #{capacity.server2_status}, Server 3: #{capacity.server3_status}"
      else
        "Server Status: #{capacity.server_status}"
      end

      puts "Server #{socket.peeraddr[1]} - Durum: #{status_str}, Zaman damgası: #{capacity.timestamp}"

      return capacity
    rescue Google::Protobuf::ParseError => e
      puts "read_capacity: Protobuf decode hatası: #{e.message}"
      return nil
    end
  end

  def send_start_command
    responses = {}

    SERVER_PORTS.each do |port|
      begin
        socket = TCPSocket.new('localhost', port)
        data = @config_message.to_proto
        socket.write([data.bytesize].pack('N'))
        socket.write(data)
        puts "Başlatma komutu gönderildi: Server #{port}"

        response = read_message(socket)
    
        case response.response
          when :YEP
            responses[port] = true
            puts "Sunucu: YEP mesajı aldı, işlem başarılı!"
          when :NOPE
            responses[port] = false
            puts "Sunucu: NOPE mesajı aldı, işlem başarısız!"
          else
            puts "Bilinmeyen yanıt: #{response.response}"
        end

        socket.close
      rescue StandardError => e
        puts "send_start_command: Sunucuya bağlanırken hata oluştu: #{e.message}"
      end
    end
    check_capacity2(responses)
    send_stop_command
  end

    def check_capacity2(responses)
    puts "check_capacity içerisine girdi."
      SERVER_PORTS.each do |port|
        if responses[port] == true
            begin
                socket = TCPSocket.new('localhost', port)
                request = Message.new("CPCTY", "YEP").message.to_proto
                socket.write([request.bytesize].pack('N'))
                socket.write(request)
                puts "Kapasite sorgusu gönderildi: Server #{port}"

                capacity = read_capacity(socket)
                socket.close
            rescue StandardError => e
                puts "check_capacity: Sunucuya bağlanırken hata oluştu: #{e.message}"
            end
        end
    end
  end

    def send_stop_command
      responses = {}
      SERVER_PORTS.each do |port|
        begin
           socket = TCPSocket.new('localhost', port)
           config_message = Configuration.new(@fault_tolerance_level, "STOP").message
           data = config_message.to_proto
           socket.write([data.bytesize].pack('N'))
           socket.write(data)
           puts "Durdurma komutu gönderildi: Server #{port}"
           socket.close
        rescue StandardError => e
          puts "send_stop_command: Sunucuya bağlanırken hata oluştu: #{e.message}"
        end
    end
  end
end

admin = Admin.new('dist_subs.conf')
admin.send_start_command
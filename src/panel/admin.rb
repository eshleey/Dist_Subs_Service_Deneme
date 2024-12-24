require 'socket'
require 'google/protobuf'
require_relative './Message_pb'
require_relative './Configuration_pb'
require_relative './Capacity_pb'

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

class ConfigurationMessage
  attr_reader :message

  def initialize(fault_tolerance_level, method)
    @message = Communication::Configuration.new
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
    @config_message = ConfigurationMessage.new(@fault_tolerance_level, "STRT").message
  end

  def read_response(socket)
    length_bytes = socket.read(4)
    if length_bytes.nil? || length_bytes.bytesize < 4
      raise "Bağlantıdan yeterli uzunluk bilgisi okunamadı."
    end

    length = length_bytes.unpack1('N')
    puts "Gelen veri uzunluğu: #{length}"

    if length <= 0 || length > 10_000
      raise "Geçersiz veri uzunluğu: #{length}"
    end

    data = socket.read(length)
    if data.nil? || data.bytesize != length
      raise "Tam veri alınamadı."
    end

    message = Communication::Message.decode(data)
    puts "Gelen mesaj: Demand: #{message.demand}, Response: #{message.response}"
    puts "Response Türü: #{message.response.class}, Değer: #{message.response}"

    return message
  end

  def send_start_command
    # Sunuculardan gelen yanıtları saklamak için bir hash
    responses = {}
    
    SERVER_PORTS.each do |port|
      begin
        socket = TCPSocket.new('localhost', port)
        data = @config_message.to_proto
        socket.write([data.bytesize].pack('N'))
        socket.write(data)
        puts "Başlatma komutu gönderildi: Server #{port}"

        response = read_response(socket)
        
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
        puts "Sunucuya bağlanırken hata oluştu: #{e.message}"
      end
    end

    # 'YEP' yanıtı veren sunucularda kapasite sorgusu başlat
    check_capacity(responses)
  end

  def check_capacity(responses)
    while true
      SERVER_PORTS.each do |port|
        if responses[port] == true
          begin
            socket = TCPSocket.new('localhost', port)

            # Kapasite sorgusu için Message nesnesini oluştur
            request = Message.new("CPCTY", nil).message
            socket.write(request.to_proto)

            # Sunucudan gelen kapasite bilgisi
            response = Communication::Capacity.decode(socket.read)
            puts "Server #{port} - Durum: #{response.server1_status || response.server2_status || response.server3_status}, Zaman damgası: #{response.timestamp}"

            socket.close
          rescue StandardError => e
            puts "Sunucuya bağlanırken hata oluştu: #{e.message}"
          end
        end
      end
      sleep 5 # 5 saniye bekle
    end
  end
end

admin = Admin.new('dist_subs.conf')
admin.send_start_command
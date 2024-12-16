require 'socket'
require 'google/protobuf'
require './Message_pb'
require './Configuration_pb'
require './Capacity_pb'

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
  SERVER_PORTS = [7001, 7002, 7003]

  def initialize(config_file)
    config_reader = ConfigReader.new(config_file)
    @fault_tolerance_level = config_reader.fault_tolerance_level
    @config_message = ConfigurationMessage.new(@fault_tolerance_level, "STRT").message
  end

  def send_start_command
    # Sunuculardan gelen yanıtları saklamak için bir hash
    responses = {}

    SERVER_PORTS.each do |port|
      begin
        socket = TCPSocket.new('localhost', port)
        socket.write(@config_message.to_proto)
        puts "Başlatma komutu gönderildi: Server #{port}"

        # Sunucudan yanıt almak için Message nesnesini hazırla
        response_message = Message.new("STRT", "YEP").message
        socket.write(response_message.to_proto)

        # Sunucudan gelen yanıtı al
        response_bytes = socket.read
        response = Communication::Message.decode(response_bytes)

        # Yanıt "YEP" ise kapasite sorgusu yapacak sunucuları kaydet
        if response.response == "YEP"
          responses[port] = true
          puts "Server #{port} 'YEP' yanıtı aldı, kapasite sorgusu yapılacak."
        else
          responses[port] = false
          puts "Server #{port} 'YEP' yanıtı almadı."
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
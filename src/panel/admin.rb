require 'socket'
require 'google/protobuf'
require 'logger'

# Protobuf dosyalarını yükleme
$LOAD_PATH.unshift('.')
require_relative 'Message_pb'
require_relative 'Configuration_pb'
require_relative 'Capacity_pb'

# Loglama için Logger nesnesi oluştur
$logger = Logger.new(STDOUT)
$logger.level = Logger::DEBUG  # Tüm log seviyelerini göster

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
          $logger.debug "ConfigReader: Fault tolerance level okundu: #{@fault_tolerance_level}"
        end
      end
    end
end

class Configuration
    attr_reader :message
  
    def initialize(fault_tolerance_level, method)
        @message = CommunicationConfig::Configuration.new
        @message.fault_tolerance_level = fault_tolerance_level
        @message.method = CommunicationConfig::MethodType::STRT #method enum tipinde olmalı
        $logger.debug "Configuration: Yeni Configuration nesnesi oluşturuldu: #{@message.inspect}"
    end
end
  
class Message
    attr_reader :message
  
    def initialize(demand, response)
      @message = Communication::Message.new
      @message.demand = demand
      @message.response = response
      $logger.debug "Message: Yeni Message nesnesi oluşturuldu: #{@message.inspect}"
    end
end

class Capacity
    attr_reader :message
  
     def initialize(server1_status = nil, server2_status = nil, server3_status = nil, timestamp)
        @message = Communication::Capacity.new
        @message.server1_status = server1_status unless server1_status.nil?
        @message.server2_status = server2_status unless server2_status.nil?
        @message.server3_status = server3_status unless server3_status.nil?
        @message.timestamp = timestamp
        $logger.debug "Capacity: Yeni Capacity nesnesi oluşturuldu: #{@message.inspect}"
    end
end


class Admin
    SERVER_PORTS = [7004, 7005, 7006]

    def initialize(config_file)
        config_reader = ConfigReader.new(config_file)
        @fault_tolerance_level = config_reader.fault_tolerance_level
        @config_message = Configuration.new(@fault_tolerance_level, "STRT").message
        $logger.debug "Admin: Yeni Admin nesnesi oluşturuldu. Fault tolerance level: #{@fault_tolerance_level}"
    end
  
    def send_message(socket, message)
        data = message.to_proto
        $logger.debug "Admin: Mesaj gönderiliyor. Boyut: #{data.bytesize}, Mesaj: #{message.inspect}"
        socket.write([data.bytesize].pack('N'))
        socket.write(data)
    end
  
    def read_message(socket, type)
        length_bytes = socket.read(4)
        
         if length_bytes.nil? || length_bytes.bytesize < 4
            $logger.error "Admin: Bağlantıdan yeterli uzunluk bilgisi okunamadı."
            raise "Bağlantıdan yeterli uzunluk bilgisi okunamadı."
         end
          length = length_bytes.unpack1('N')
          if length <= 0 || length > 10_000
              $logger.error "Admin: Geçersiz veri uzunluğu: #{length}"
              raise "Geçersiz veri uzunluğu: #{length}"
          end
  
          data = socket.read(length)
          if data.nil? || data.bytesize != length
             $logger.error "Admin: Tam veri alınamadı. Alınan boyut: #{data.nil? ? "nil" : data.bytesize}, Beklenen boyut: #{length}"
            raise "Tam veri alınamadı."
          end
        
          $logger.debug "Admin: Mesaj okundu. Boyut: #{length}, Tip: #{type}"
  
        case type
        when 'message'
             message = Communication::Message.decode(data)
             $logger.debug "Admin: Message decode edildi: #{message.inspect}"
            return message
        when 'capacity'
             capacity = Communication::Capacity.decode(data)
             $logger.debug "Admin: Capacity decode edildi: #{capacity.inspect}"
            return capacity
        else
             $logger.error "Admin: Bilinmeyen mesaj tipi: #{type}"
           raise "Bilinmeyen mesaj tipi: #{type}"
        end
    end
  
    def send_start_command
      responses = {}
      
      SERVER_PORTS.each do |port|
        begin
            $logger.debug "Admin: Sunucuya bağlanılıyor: Port #{port}"
          socket = TCPSocket.new('localhost', port)
           $logger.debug "Admin: Sunucuya bağlandı: Port #{port}"
          send_message(socket, @config_message)
          puts "Başlatma komutu gönderildi: Server #{port}"
  
          response = read_message(socket, 'message')
           $logger.debug "Admin: Sunucudan yanıt alındı: #{response.inspect}"
          case response.response
          when Communication::Response::YEP
            responses[port] = true
            puts "Sunucu: YEP mesajı aldı, işlem başarılı!"
             $logger.debug "Admin: Sunucu #{port} YEP yanıtı verdi."
          when Communication::Response::NOPE
            responses[port] = false
            puts "Sunucu: NOPE mesajı aldı, işlem başarısız!"
             $logger.debug "Admin: Sunucu #{port} NOPE yanıtı verdi."
          else
            puts "Bilinmeyen yanıt: #{response.response}"
             $logger.warn "Admin: Sunucu #{port} bilinmeyen yanıt verdi: #{response.response}"
          end
          socket.close
           $logger.debug "Admin: Sunucu bağlantısı kapatıldı: Port #{port}"
        rescue StandardError => e
            $logger.error "Admin: Sunucuya bağlanırken hata oluştu: #{e.message}"
          puts "Sunucuya bağlanırken hata oluştu: #{e.message}"
        end
      end
  
      check_capacity(responses)
    end
  
    def check_capacity(responses)
        while true
            responses.each do |port, is_successful|
                if is_successful
                  begin
                       $logger.debug "Admin: Kapasite bilgisi için sunucuya bağlanılıyor: Port #{port}"
                    socket = TCPSocket.new('localhost', port)
                       $logger.debug "Admin: Kapasite bilgisi için sunucuya bağlandı: Port #{port}"
                      message = Message.new(Communication::Demand::CPCTY, nil).message
                      send_message(socket, message)
                        $logger.debug "Admin: Kapasite isteği gönderildi: Sunucu #{port}"
                    response = read_message(socket, 'capacity')
                        $logger.debug "Admin: Kapasite bilgisi alındı: #{response.inspect}"
                    server_status = response.server1_status || response.server2_status || response.server3_status
                    puts "Server #{port} - Durum: #{server_status}, Zaman damgası: #{response.timestamp}"
                    socket.close
                       $logger.debug "Admin: Kapasite bilgisi için sunucu bağlantısı kapatıldı: Port #{port}"
                  rescue StandardError => e
                       $logger.error "Admin: Sunucuya bağlanırken hata oluştu (kapasite): #{e.message}"
                    puts "Sunucuya bağlanırken hata oluştu (kapasite): #{e.message}"
                  end
                end
            end
            sleep 5
        end
    end
end
  
  admin = Admin.new('dist_subs.conf')
  admin.send_start_command
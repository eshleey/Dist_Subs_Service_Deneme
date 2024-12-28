require 'google/protobuf'
require 'socket'
require 'timeout'

# Protobuf sınıflarını yükle
$LOAD_PATH.unshift('./src/main/java')
require 'com/example/util/CapacityOuterClass'
require 'com/example/util/ConfigurationOuterClass'
require 'com/example/util/MessageOuterClass'
require 'com/example/util/SubscriberOuterClass'

# Gerekli sınıflar
Capacity = Com::Example::Util::CapacityOuterClass::Capacity
Configuration = Com::Example::Util::ConfigurationOuterClass::Configuration
Message = Com::Example::Util::MessageOuterClass::Message
Demand = Com::Example::Util::MessageOuterClass::Demand
Response = Com::Example::Util::MessageOuterClass::Response
MethodType = Com::Example::Util::ConfigurationOuterClass::MethodType


# Hata tolerans seviyesini dosyadan oku
begin
  fault_tolerance_level = File.read("dist_subs.conf").strip.to_i
rescue Errno::ENOENT
  puts "Hata: dist_subs.conf dosyası bulunamadı."
  exit
end

# Configuration nesnesini oluştur
configuration = Configuration.new(
  fault_tolerance_level: fault_tolerance_level,
  method: MethodType::STRT
)

puts "Yapılandırma: #{configuration.inspect}"

# Sunucu adresleri ve portları (örnek)
servers = [
  { host: 'localhost', port: 8081, name: "Server1"},
  { host: 'localhost', port: 8082, name: "Server2"},
  { host: 'localhost', port: 8083, name: "Server3"}
]

# Sunuculara START isteği gönder ve yanıtları kontrol et
def send_start_command(servers)
    successful_servers = []
    servers.each do |server|
        begin
          Timeout.timeout(2) do # 2 saniye zaman aşımı
            socket = TCPSocket.new(server[:host], server[:port])
             
            message = Message.new(demand: Demand::STRT)
            socket.write(message.to_proto)

            response_data = socket.recv(1024)
            response = Message.decode(response_data)
            
            if response.response == Response::YEP
                puts "#{server[:name]} -> Başlatma başarılı"
                successful_servers << server
            else
                puts "#{server[:name]} -> Başlatma başarısız"
            end

            socket.close
           end
         rescue Timeout::Error
            puts "#{server[:name]} -> Bağlantı Zaman Aşımı"
          rescue Errno::ECONNREFUSED
            puts "#{server[:name]} -> Bağlantı Reddedildi"
         rescue => e
            puts "#{server[:name]} -> Hata oluştu: #{e.message}"
        end
    end

    return successful_servers
end

successful_servers = send_start_command(servers)

# Başarılı sunuculara kapasite sorguları gönder
def query_capacity(successful_servers)
    successful_servers.each do |server|
       Thread.new do
          loop do
            begin
              Timeout.timeout(2) do
                socket = TCPSocket.new(server[:host], server[:port])

                message = Message.new(demand: Demand::CPCTY)
                socket.write(message.to_proto)
                
                capacity_data = socket.recv(1024)
                capacity = Capacity.decode(capacity_data)
                puts "#{server[:name]} -> Kapasite bilgisi: #{capacity.inspect}"
                socket.close
            end
           rescue Timeout::Error
                puts "#{server[:name]} -> Kapasite sorgusu zaman aşımı."
            rescue Errno::ECONNREFUSED
                puts "#{server[:name]} -> Kapasite sorgusu bağlantı reddedildi."
            rescue => e
                puts "#{server[:name]} -> Kapasite sorgusu hatası: #{e.message}"
            end
            sleep 5
          end
       end
    end
end

query_capacity(successful_servers)


# Ana threadin devam etmesini bekle
while true
    sleep 1
end
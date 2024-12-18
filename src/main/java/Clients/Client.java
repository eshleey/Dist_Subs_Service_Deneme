package Clients;

import communication.SubscriberOuterClass.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public class Client {

    private static final String SERVER_HOST = "localhost"; // Sunucu adresi
    private static final int SERVER_PORT = 7001; // Server1 portu

    public static void main(String[] args) {
        try {
            // Sunucuya bağlan
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("Connected to server: " + SERVER_HOST + ":" + SERVER_PORT);

            // Stream oluştur
            OutputStream output = socket.getOutputStream();

            // Abone ekleme (SUBS) isteği gönder
            Subscriber subscriber = createSubscriberForSub();
            sendSubscriberMessage(output, subscriber);
            System.out.println("Subscriber SUBS message sent.");

            // Bekleme ekle, sorun varsa burada zaman kazanabiliriz
            Thread.sleep(100);

            // Abone silme (DEL) isteği gönder
            Subscriber delSubscriber = createSubscriberForDel(subscriber.getID());
            sendSubscriberMessage(output, delSubscriber);
            System.out.println("Subscriber DEL message sent.");

            // Stream ve bağlantıyı kapat
            output.close();
            socket.close();
            System.out.println("Connection closed.");

        } catch (IOException | InterruptedException e) {
            System.err.println("Error in client: " + e.getMessage());
        }
    }

    // SUBS isteği için Subscriber nesnesi oluşturur
    private static Subscriber createSubscriberForSub() {
        return Subscriber.newBuilder()
                .setDemand(DemandType.SUBS) // Abone ekleme
                .setID(12) // Benzersiz ID
                .setNameSurname("Jane DOE")
                .setStartDate(System.currentTimeMillis())
                .setLastAccessed(System.currentTimeMillis())
                .addAllInterests(Arrays.asList("sports", "lifestyle", "cooking", "psychology"))
                .setIsOnline(true) // Çevrimiçi durumu
                .build();
    }

    // DEL isteği için Subscriber nesnesi oluşturur
    private static Subscriber createSubscriberForDel(int id) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.DEL) // Abonelikten çıkma isteği
                .setID(id) // ID üzerinden silme işlemi yapılacak
                .build();
    }

    // Subscriber nesnesini sunucuya gönderen fonksiyon
    private static void sendSubscriberMessage(OutputStream output, Subscriber subscriber) {
        try {
            byte[] data = subscriber.toByteArray(); // Protobuf mesajını byte[]'e çevir
            output.write(data); // Veriyi sunucuya gönder
            output.flush();
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }
}

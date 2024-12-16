package dist_servers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import communication.MessageOuterClass.Message;
import communication.CapacityOuterClass.Capacity;
import communication.SubscriberOuterClass.Subscriber;

public class Server3 {
    private static final int PORT = 7003;
    private static final String HOST = "localhost";
    private static final int SERVER1_PORT = 7001;
    private static final int SERVER2_PORT = 7002;
    private static final int THREAD_POOL_SIZE = 10;

    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        // Veya ExecutorService executorService = Executors.newCachedThreadPool(); kullanılabilir.
        // Bu durumda, thread sayısı ihtiyaca göre dinamik olarak artar ve azalır.

        new Thread(() -> startServer(executorService)).start();
        new Thread(() -> connectToServer(SERVER1_PORT, "Server1")).start();
        new Thread(() -> connectToServer(SERVER2_PORT, "Server2")).start();
    }

    private static void startServer(ExecutorService executorService) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port: " + PORT);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Connection established: " + clientSocket.getRemoteSocketAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    System.err.println("Connection error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream()) {

            // istemciden gelen ilk mesaj okunur işlenir
            byte[] messageBytes = readFromStream(input);
            Message message = Message.parseFrom(messageBytes);
            processMessage(message, output);

            // istemciden gelen abone mesajı okunur işlenir
            byte[] subscriberBytes = readFromStream(input);
            Subscriber subscriber = Subscriber.parseFrom(subscriberBytes);
            processSubscriber(subscriber);

            // abone ekleme/çıkarma işlemi sonrası kapasite güncellemesi yapılır tüm sunuculara bildirilir
            broadcastCapacityUpdate();

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    // InputStreamdan gelen verileri okur ve byte dizisi olarak döndürür
    // buffer ile optimize edilebilir
    private static byte[] readFromStream(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = input.read(buffer);
        byte[] data = new byte[bytesRead];
        System.arraycopy(buffer, 0, data, 0, bytesRead);
        return data;
        // Bu çalışmayabilir öyle olursa aşağıdakini denesene sude
        // Veya aşağıdaki kod ile de okuma yapılabilir
        // return input.readAllBytes();
    }

    private static void processMessage(Message message, OutputStream output) throws IOException {
        switch (message.getDemand()) {
            case "STRT":
                Message yepMessage = Message.newBuilder().setResponse("YEP").build();
                writeToStream(output, yepMessage.toByteArray());
                break;
            case "CPCTY":
                Capacity capacityMessage = Capacity.newBuilder()
                        .setServer1Status(capacity.get()) // aktif kapasite bilgisi
                        .setTimestamp(System.currentTimeMillis() / 1000)
                        .build();
                writeToStream(output, capacityMessage.toByteArray());
                break;
            default:
                // Geçersiz yanıtlara karşı "NOPE" yanıtı gönderilir
                System.out.println("Unknown demand type: " + message.getDemand());
                Message nopMessage = Message.newBuilder().setResponse("NOPE").build();
                writeToStream(output, nopMessage.toByteArray());
                break;
        }
    }

    private static void processSubscriber(Subscriber subscriber) {
        // Gelen subscriber talebine göre DemandType türüne uygun işlem yaparız
        switch (subscriber.getDemand()) {
            case SUBS: // DemandType.SUBS -> Abone ekleme işlemi
                addSubscriber(subscriber);
                break;
            case DEL: // DemandType.DEL -> Abone çıkarma işlemi
                removeSubscriber(subscriber.getID());
                break;
            default: // Geçersiz talepler
                System.out.println("Unsupported subscriber demand: " + subscriber.getDemand());
                break;
        }
    }

    private static void writeToStream(OutputStream output, byte[] data) throws IOException {
        output.write(data);
        output.flush();
    }

    private static void addSubscriber(Subscriber subscriber) {
        if (capacity.get() > 0) {
            subscribers.put(subscriber.getID(), subscriber);
            capacity.decrementAndGet();
            System.out.println("Subscriber added: " + subscriber.getID());
        } else {
            System.out.println("No capacity left to add subscriber!");
        }
    }

    private static void removeSubscriber(int id) {
        if (subscribers.remove(id) != null) {
            capacity.incrementAndGet();
            System.out.println("Subscriber removed: " + id);
        } else {
            System.out.println("Subscriber not found: " + id);
        }
    }

    private static void broadcastCapacityUpdate() {
        Capacity capacityMessage = Capacity.newBuilder()
                .setServer1Status(capacity.get()) // Güncel sunucu kapasitesi
                .setTimestamp(System.currentTimeMillis() / 1000)
                .build();

        new Thread(() -> sendCapacityUpdate(SERVER1_PORT, capacityMessage)).start();
        new Thread(() -> sendCapacityUpdate(SERVER2_PORT, capacityMessage)).start();
    }

    private static void sendCapacityUpdate(int port, Capacity capacityMessage) {
        try (Socket socket = new Socket(HOST, port);
             OutputStream output = socket.getOutputStream()) {

            writeToStream(output, capacityMessage.toByteArray());
            System.out.println("Capacity update sent to port: " + port);

        } catch (IOException e) {
            System.err.println("Failed to send capacity update to port: " + port);
        }
    }

    private static void connectToServer(int port, String serverName) {
        while (true) {
            try (Socket connection = new Socket(HOST, port)) {
                System.out.println("Connection established to " + serverName);
                break;
            } catch (IOException e) {
                System.out.println("Failed to connect to " + serverName + ". Retrying...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Connection retry interrupted");
                    break;
                }
            }
        }
    }
}
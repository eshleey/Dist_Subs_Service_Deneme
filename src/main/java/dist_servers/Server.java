package dist_servers;

import communication.SubscriberOuterClass.Subscriber;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static final int PORT = 7001;
    private static final int THREAD_POOL_SIZE = 10;

    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);

    public static void main(String[] args) {
        startServers(args);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

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

            // İstemciden mesajı al ve işle
            byte[] subscriberBytes = readFromStream(input);
            Subscriber subscriber = Subscriber.parseFrom(subscriberBytes);

            System.out.println("Received subscriber request: " + subscriber);

            // Talebi işle ve sonucu istemciye gönder
            String response = processSubscriber(subscriber);
            output.write(response.getBytes());
            output.flush();

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static byte[] readFromStream(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = input.read(buffer);
        byte[] data = new byte[bytesRead];
        System.arraycopy(buffer, 0, data, 0, bytesRead);
        return data;
    }

    private static String processSubscriber(Subscriber subscriber) {
        switch (subscriber.getDemand()) {
            case SUBS:
                return addSubscriber(subscriber);
            case DEL:
                return removeSubscriber(subscriber.getID());
            default:
                return "Unsupported demand type.";
        }
    }

    private static String addSubscriber(Subscriber subscriber) {
        if (capacity.get() > 0) {
            subscribers.put(subscriber.getID(), subscriber);
            capacity.decrementAndGet();
            System.out.println("Subscriber added: " + subscriber.getID());
            return "Subscriber added successfully.";
        } else {
            return "Capacity full. Cannot add subscriber.";
        }
    }

    private static String removeSubscriber(int id) {
        if (subscribers.remove(id) != null) {
            capacity.incrementAndGet();
            System.out.println("Subscriber removed: " + id);
            return "Subscriber removed successfully.";
        } else {
            return "No subscriber with ID: " + id;
        }
    }

    private static void startServers(String[] args) {
        Server2.main(args);
        Server3.main(args);
    }
}
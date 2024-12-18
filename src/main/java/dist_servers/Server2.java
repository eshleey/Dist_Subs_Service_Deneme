package dist_servers;

import communication.SubscriberOuterClass.Subscriber;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server2 {
    private static final int PORT = 7002;
    private static final int THREAD_POOL_SIZE = 10;

    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);

    private static final Set<Integer> idSet = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(PORT);
             ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            System.out.println("Server listening on port: " + PORT);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Connection established: " + clientSocket.getRemoteSocketAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                    //startServer(args);
                } catch (IOException e) {
                    System.err.println("Connection error: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream()) {
            while (true) {
                // İstemciden mesajı al ve işle
                byte[] subscriberBytes = readFromStream(input);
                if (subscriberBytes.length == 0) {
                    System.out.println("No data received from client.");
                    return;
                }
                Subscriber subscriber = Subscriber.parseFrom(subscriberBytes);

                System.out.println("Received subscriber request: ID: " + subscriber.getID());
                System.out.println("Demand Type: " + subscriber.getDemand());

                // Talebi işle ve sonucu istemciye gönder
                String response = processSubscriber(subscriber);
                output.write(response.getBytes());
                output.flush();

                System.out.println("Response sent: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static byte[] readFromStream(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead = input.read(buffer);
        if (bytesRead == -1) {
            return new byte[0];
        }
        byte[] data = new byte[bytesRead];
        System.arraycopy(buffer, 0, data, 0, bytesRead);
        return data;
    }

    private static String processSubscriber(Subscriber subscriber) {
        switch (subscriber.getDemand()) {
            case SUBS -> {
                return handleIDList(subscriber, "add", subscriber.getID());
            }
            case DEL -> {
                return handleIDList(subscriber, "del", subscriber.getID());
            }
            default -> {
                return "Invalid demand type.";
            }
        }
    }

    private static String handleIDList(Subscriber subscriber, String option, int id) {
        switch (option) {
            case "add" -> {
                if (idSet.size() < 3) {
                    if (idSet.contains(id)) {
                        return "Already subscribed with ID: " + id;
                    }
                    else {
                        idSet.add(id);
                        return addSubscriber(subscriber);
                    }
                }
                else {
                    return "List is full. Cannot add more ID";
                }
            }
            case "del" -> {
                if (idSet.contains(id)) {
                    idSet.remove(id);
                    return removeSubscriber(subscriber.getID());
                }
                else {
                    return "ID does not exist: " + id;
                }
            }
            default -> {
                return "Invalid option type.";
            }
        }
    }

    private static String addSubscriber(Subscriber subscriber) {
        if (capacity.get() > 0) {
            subscribers.put(subscriber.getID(), subscriber);
            capacity.decrementAndGet();
            return "Subscriber added: " + subscriber.getID();
        } else {
            return "Capacity full. Cannot add subscriber.";
        }
    }

    private static String removeSubscriber(int id) {
        if (subscribers.remove(id) != null) {
            capacity.incrementAndGet();
            return "Subscriber removed: " + id;
        } else {
            return "No subscriber with ID: " + id;
        }
    }

    private static void startServer(String[] args) {
        try {
            Server3.main(args);
        }
        catch (Exception e) {
            System.err.println("Start servers function error: " + e);
        }
    }
}
package dist_servers;

import communication.SubscriberOuterClass.Subscriber;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static final int CLIENT_PORT = 7001; // Client bağlantıları için port
    private static final int SERVER2_PORT = 7002; // Server2 ile bağlantı için port
    private static final int SERVER3_PORT = 7003; // Server3 ile bağlantı için port
    private static final String HOST = "localhost";
    private static final int THREAD_POOL_SIZE = 10; // Thread havuz boyutu

    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, Boolean> idSet = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);

    public static void main(String[] args) {
        try (ServerSocket clientServerSocket = new ServerSocket(CLIENT_PORT);
             ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            System.out.println("Server listening on port: " + CLIENT_PORT);

            // Client bağlantıları için server başlat
            new Thread(() -> startClientServer(executorService, clientServerSocket)).start();

            // Diğer server'lara bağlan
            new Thread(() -> connectToServer(SERVER2_PORT, "Server2")).start();
            new Thread(() -> connectToServer(SERVER3_PORT, "Server3")).start();

            while (true) {
                try {
                    // Client bağlantılarını kabul et
                    Socket clientSocket = clientServerSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    System.err.println("Connection error: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void startClientServer(ExecutorService executorService, ServerSocket clientServerSocket) {
        while (true) {
            try {
                // Client bağlantısını kabul et
                Socket clientSocket = clientServerSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                System.err.println();
            }
        }
    }

    private static void connectToServer(int port, String serverName) {
        while (true) {
            try (Socket connection = new Socket(HOST, port)) {
                System.out.println("Connected to " + serverName);
                // Server ile iletişim mantığı burada eklenebilir
                break;
            } catch (IOException e) {
                System.out.println("Failed to connect to " + serverName + ", retrying...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    System.err.println();
                }
            }
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

    public static String handleIDList(Subscriber subscriber, String option, int id) {
        final ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            switch (option) {
                case "add" -> {
                    if (idSet.size() < 3) {
                        if (idSet.putIfAbsent(id, true) == null) {
                            return addSubscriber(subscriber);
                        } else {
                            return "Already subscribed with ID: " + id;
                        }
                    } else {
                        return "List is full. Cannot add more ID";
                    }
                }
                case "del" -> {
                    if (idSet.remove(id) != null) {
                        return removeSubscriber(subscriber.getID());
                    } else {
                        return "ID does not exist: " + id;
                    }
                }
                default -> {
                    return "Invalid option type.";
                }
            }
        } finally {
            lock.unlock();
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

    private static void handleClient(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream()) {
            while (true) {
                // Client'ten gelen mesajı al ve işle
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
}
package dist_servers;

import communication.SubscriberOuterClass.Subscriber;
import communication.MessageOuterClass.Message;
import communication.CapacityOuterClass.Capacity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server3 {
    private static final int PORT = 7003;
    private static final String HOST = "localhost";
    private static final int SERVER1_PORT = 7001;
    private static final int SERVER2_PORT = 7002;
    private static final int THREAD_POOL_SIZE = 10; // Thread pool size for handling client connections

    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            System.out.println("Server listening on port: " + PORT);

            // Start server thread
            new Thread(() -> startServer(executorService, serverSocket)).start();

            // Start client threads to connect to other servers
            new Thread(() -> connectToServer(SERVER1_PORT, "Server1")).start();
            new Thread(() -> connectToServer(SERVER2_PORT, "Server2")).start();

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Connection established: " + clientSocket.getRemoteSocketAddress());
                    executorService.submit(() -> handleClient(clientSocket));
                    //startServers(args);
                } catch (IOException e) {
                    System.err.println("Connection error: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void startServer(ExecutorService executorService, ServerSocket serverSocket) {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection established: " + clientSocket.getRemoteSocketAddress());
                executorService.submit(() -> handleClientToServer(clientSocket));
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void handleClientToServer(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream()) {

            byte[] messageBytes = input.readAllBytes();
            Message message = Message.parseFrom(messageBytes);

            if ("CPCTY".equals(message.getDemand())) {
                Capacity capacity = Capacity.newBuilder()
                        .setServer1Status(1000)
                        .setTimestamp(System.currentTimeMillis() / 1000)
                        .build();

                output.write(capacity.toByteArray());
                output.flush();
            } else {
                System.out.println("Unknown demand type: " + message.getDemand());
            }
        } catch (IOException e) {
            System.out.println("Client handling error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void connectToServer(int port, String serverName) {
        while (true) {
            try (Socket connection = new Socket(HOST, port)) {
                System.out.println("Connection established to " + serverName);
                // Communication logic can be added here
                break;
            } catch (IOException e) {
                System.out.println("Failed to connect to " + serverName + ", retrying...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
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

    private static String handleIDList(Subscriber subscriber, String option, int id) {
        switch (option) {
            case "add" -> {
                if (Server.SharedResources.idSet.size() < 3) {
                    if (Server.SharedResources.idSet.putIfAbsent(id, true) == null) {
                        return addSubscriber(subscriber);
                    } else {
                        return "Already subscribed with ID: " + id;
                    }
                } else {
                    return "List is full. Cannot add more ID";
                }
            }
            case "del" -> {
                if (Server.SharedResources.idSet.remove(id) != null) {
                    return removeSubscriber(subscriber.getID());
                } else {
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
}
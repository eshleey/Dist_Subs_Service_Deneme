package dist_servers;

import communication.ProtobufHandler;
import communication.SubscriberOuterClass.Subscriber;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler {
    private int clientPort;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);
    private static final ProtobufHandler protobufHandler = new ProtobufHandler();

    public ClientHandler(int clientPort) {
        this.clientPort = clientPort;
    }

    public void startClient() {
        ServerSocket clientServerSocket = null;
        try {
            clientServerSocket = new ServerSocket(clientPort);
            System.out.println("Server listening for client on port: " + clientPort);
            ServerSocket finalClientSocket = clientServerSocket;
            executorService.submit(() -> acceptClientConnections(finalClientSocket));

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            if (clientServerSocket != null && !clientServerSocket.isClosed()) {
                try {
                    clientServerSocket.close();
                    System.out.println("Client server socket is closed.");
                } catch (IOException e) {
                    System.err.println("Error closing client server socket: " + e.getMessage());
                }
            }
            executorService.shutdown();
        }
    }

    public void acceptClientConnections(ServerSocket clientServerSocket) {
        while (true) {
            try {
                if (clientServerSocket == null || clientServerSocket.isClosed()) {
                    System.err.println("Client server socket is closed. Stopping client connection attempts.");
                    break;
                }
                Socket clientSocket = clientServerSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                executorService.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                System.err.println("Client server socket is closed. No longer accepting clients.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
            while (!clientSocket.isClosed()) {
                try {
                    Subscriber sub = protobufHandler.receiveProtobufMessage(inputStream, Subscriber.class);

                    if (sub == null) {
                        System.err.println("Received null Subscriber message.");
                    } else {
                        System.out.println("Subscriber Message: " + sub);
                        processSubscriber(sub);
                    }
                } catch (EOFException e) {
                    System.out.println("Client connection closed gracefully.");
                    break;
                } catch (SocketException e) {
                    System.err.println("Socket error while handling client: " + e.getMessage());
                    break;
                } catch (IOException e) {
                    System.err.println("IO error while handling client: " + e.getMessage());
                    break;
                }
            }
        } catch (SocketException e) {
            System.err.println("Socket error on client socket: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO error on client socket: " + e.getMessage());
        } finally {
            // DistributedServerHandler.closeSocket(clientSocket);
        }
    }

    private void processSubscriber(Subscriber sub) {
        switch (sub.getDemand()) {
            case SUBS -> {
                if (!subscribers.containsKey(sub.getID())) {
                    if (subscribers.size() < capacity.get()) {
                        subscribers.put(sub.getID(), sub);
                        System.out.println("Subscriber added: ID " + sub.getID());
                    }
                } else {
                    System.out.println("Already subscribed with ID: " + sub.getID());
                }
            }
            case ONLN -> {
                if (subscribers.containsKey(sub.getID())) {
                    if (!subscribers.get(sub.getID()).getIsOnline()) {
                        Subscriber updatedSub = subscribers.get(sub.getID()).toBuilder()
                                .setIsOnline(true)
                                .build();

                        subscribers.put(sub.getID(), updatedSub);
                        System.out.println("Subscriber status made online: ID " + sub.getID());
                    } else {
                        System.out.println("Subscriber status already online: ID" + sub.getID());
                    }
                } else {
                    System.out.println("No subscriber with ID: " + sub.getID());
                }
            }
            case OFFL -> {
                if (subscribers.containsKey(sub.getID())) {
                    if (subscribers.get(sub.getID()).getIsOnline()) {
                        Subscriber updatedSub = subscribers.get(sub.getID()).toBuilder()
                                .setIsOnline(false)
                                .build();

                        subscribers.put(sub.getID(), updatedSub);
                        System.out.println("Subscriber status made offline: ID " + sub.getID());
                    } else {
                        System.out.println("Subscriber status already offline: ID" + sub.getID());
                    }
                } else {
                    System.out.println("No subscriber with ID: " + sub.getID());
                }
            }
            case DEL -> {
                if (subscribers.containsKey(sub.getID())) {
                    subscribers.remove(sub.getID());
                    System.out.println("Subscriber removed: ID " + sub.getID());
                } else {
                    System.out.println("No subscriber with ID: " + sub.getID());
                }
            }
            default -> System.err.println("Invalid demand type.");
        }
    }
}

package dist_servers;

import communication.ProtobufHandler;
import communication.SubscriberOuterClass.Subscriber;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler {
    private static final int[] SERVER_PORTS = {7001, 7002, 7003};
    private static final String HOST = "localhost";
    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);
    private static final ProtobufHandler protobufHandler = new ProtobufHandler();

    public static void acceptClientConnections(ServerSocket serverSocket, ExecutorService executorService, int port) {
        while (true) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    System.err.println("Server socket is closed, stopping client connection attempts.");
                    break;
                }
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                executorService.submit(() -> handleClient(clientSocket, port));
            } catch (SocketException e) {
                System.err.println("ServerSocket is closed, no longer accepting clients.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private static void handleClient(Socket clientSocket, int port) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
            while (!clientSocket.isClosed()) {
                try {
                    byte[] lengthBytes = new byte[4];
                    inputStream.readFully(lengthBytes);
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    byte[] data = new byte[length];
                    inputStream.readFully(data);
                    Subscriber sub = protobufHandler.receiveProtobufMessage(inputStream, Subscriber.class);
                    if (sub != null) {
                        System.out.println("Received subscriber message: " + sub);
                        processSubscriber(sub, port);
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
            DistributedServerHandler.closeSocket(clientSocket);
        }
    }

    private static void processSubscriber(Subscriber sub, int port) {
        switch (sub.getDemand()) {
            case SUBS -> {
                System.out.println(subscribers.containsKey(sub.getID()));
                if (!subscribers.containsKey(sub.getID())) {
                    if (subscribers.size() < capacity.get()) {
                        subscribers.put(sub.getID(), sub);
                        System.out.println("Subscriber added: ID " + sub.getID());
                    } else {
                        ForwardToOtherServers(sub, port);
                    }
                } else {
                    System.out.println("Already subscribed with ID: " + sub.getID());
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

    private static void ForwardToOtherServers(Subscriber sub, int clientPort) {
        if (subscribers.size() >= capacity.get()) {
            for (int port : SERVER_PORTS) {
                if (port != clientPort) {
                    try (Socket socket = new Socket(HOST, port);
                         DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                        protobufHandler.sendProtobufMessage(output, sub);
                        System.out.println("Subscriber forwarded to server on port: " + port);
                        return; // Successfully forwarded, exit
                    } catch (IOException e) {
                        System.err.println("Failed to forward subscriber to server on port: " + port + " - " + e.getMessage());
                    }
                }
            }
            System.out.println("Failed to forward subscriber to any other server.");
        } else {
            System.out.println("Current server capacity is not full. No need to forward.");
        }
    }
}

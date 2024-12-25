package dist_servers;

import communication.SubscriberOuterClass;

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
    private static final ConcurrentMap<Integer, SubscriberOuterClass.Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);
    private static final ServerHandler serverHandler = new ServerHandler();
    private static final ProtobufHandler protobufHandler = new ProtobufHandler();

    public void acceptClientConnections(ServerSocket serverSocket, ExecutorService executorService, int[] ports, int clientPort, String host) {
        while (true) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    System.err.println("Server socket is closed, stopping client connection attempts.");
                    break;
                }
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                executorService.submit(() -> handleClient(clientSocket, ports, clientPort, host));
            } catch (SocketException e) {
                System.err.println("Server socket is closed, no longer accepting clients.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket, int[] ports, int clientPort, String host) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream())) {
            while (!clientSocket.isClosed()) {
                try {
                    byte[] lengthBytes = new byte[4];
                    inputStream.readFully(lengthBytes);
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    byte[] data = new byte[length];
                    inputStream.readFully(data);
                    SubscriberOuterClass.Subscriber sub = protobufHandler.receiveProtobufMessage(inputStream, SubscriberOuterClass.Subscriber.class);
                    if (sub != null) {
                        System.out.println("Received subscriber message: " + sub);
                        processSubscriber(sub, ports, clientPort, host);
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
            serverHandler.closeSocket(clientSocket);
        }
    }

    private void processSubscriber(SubscriberOuterClass.Subscriber sub, int[] ports, int clientPort, String host) {
        switch (sub.getDemand()) {
            case SUBS -> {
                if (!subscribers.containsKey(sub.getID())) {
                    if (subscribers.size() < capacity.get()) {
                        subscribers.put(sub.getID(), sub);
                        System.out.println("Subscriber added: ID " + sub.getID());
                    } else {
                        forwardToOtherServers(sub, ports, clientPort, host);
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

    private void forwardToOtherServers(SubscriberOuterClass.Subscriber sub, int[] ports, int clientPort, String host) {
        if (subscribers.size() >= capacity.get()) {
            for (int port : ports) {
                if (port != clientPort) {
                    try (Socket socket = new Socket(host, port);
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

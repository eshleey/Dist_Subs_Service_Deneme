package dist_servers;

import com.google.protobuf.MessageLite;
import communication.CapacityOuterClass.Capacity;
import communication.ConfigurationOuterClass.Configuration;
import communication.ConfigurationOuterClass.MethodType;
import communication.MessageOuterClass.Message;
import communication.MessageOuterClass.Demand;
import communication.MessageOuterClass.Response;
import communication.SubscriberOuterClass.Subscriber;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class Server1 {
    private static final int ADMIN_PORT = 7004;
    private static final int CLIENT_PORT = 7001;
    private static final int[] SERVER_PORTS = {7002, 7003};
    private static final String HOST = "localhost";
    private static final int THREAD_POOL_SIZE = 10;
    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger capacity = new AtomicInteger(1000);
    private static final Queue<Subscriber> queue = new ConcurrentLinkedQueue<>();
    private static final List<Socket> serverSockets = new CopyOnWriteArrayList<>();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static boolean isRunning = false;

    public static void main(String[] args) {
        Server1 server = new Server1();
        server.startServer();
    }

    private void startServer() {
        ServerSocket adminServerSocket = null;
        ServerSocket clientServerSocket = null;
        try {
            adminServerSocket = new ServerSocket(ADMIN_PORT);
            System.out.println("Server listening for admin on port: " + ADMIN_PORT);
            clientServerSocket = new ServerSocket(CLIENT_PORT);
            System.out.println("Server listening for clients on port: " + CLIENT_PORT);

            final ServerSocket finalClientSocket = clientServerSocket;
            final ServerSocket finalAdminSocket = adminServerSocket;

            ExecutorService adminExecutor = Executors.newFixedThreadPool(2);

            executorService.submit(() -> acceptClientConnections(finalClientSocket));
            adminExecutor.submit(() -> acceptAdminConnections(finalAdminSocket));

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
            if (adminServerSocket != null && !adminServerSocket.isClosed()) {
                try {
                    adminServerSocket.close();
                    System.out.println("Admin server socket closed.");
                } catch (IOException e) {
                    System.err.println("Error closing admin server socket: " + e.getMessage());
                }
            }
            if (clientServerSocket != null && !clientServerSocket.isClosed()) {
                try {
                    clientServerSocket.close();
                    System.out.println("Client server socket closed.");
                } catch (IOException e) {
                    System.err.println("Error closing client server socket: " + e.getMessage());
                }
            }
            executorService.shutdown();
        }
    }

    private void acceptClientConnections(ServerSocket serverSocket) {
        while (true) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    System.err.println("Server socket is closed, stopping client connection attempts.");
                    break;
                }
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                executorService.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                System.err.println("ServerSocket is closed, no longer accepting clients.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private void acceptAdminConnections(ServerSocket serverSocket) {
        while (true) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    System.err.println("Server socket is closed, stopping admin connection attempts.");
                    break;
                }
                Socket adminSocket = serverSocket.accept();
                System.out.println("Admin connected: " + adminSocket.getInetAddress());
                executorService.submit(() -> handleAdminConnection(adminSocket));
            } catch (SocketException e) {
                System.err.println("ServerSocket is closed, no longer accepting admins.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting admin connection: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())) {
            while (!clientSocket.isClosed()) {
                try {
                    byte[] lengthBytes = new byte[4];
                    inputStream.readFully(lengthBytes);
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    byte[] data = new byte[length];
                    inputStream.readFully(data);
                    Subscriber sub = receiveProtobufMessage(inputStream, Subscriber.class);
                    if (sub != null) {
                        System.out.println("Received subscriber message: " + sub);
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
            closeSocket(clientSocket);
        }
    }

    private void processSubscriber(Subscriber sub) {
        switch (sub.getDemand()) {
            case SUBS -> {
                if (!subscribers.containsKey(sub.getID())) {
                    if (subscribers.size() < capacity.get()) {
                        subscribers.put(sub.getID(), sub);
                        System.out.println("Subscriber added: ID " + sub.getID());
                    } else {
                        ForwardToOtherServers(sub);
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

    private void handleAdminConnection(Socket adminSocket) {
        try (DataInputStream input = new DataInputStream(adminSocket.getInputStream());
             DataOutputStream output = new DataOutputStream(adminSocket.getOutputStream())) {

            Configuration config = receiveProtobufMessage(input, Configuration.class);
            if (config != null) {
                System.out.println("Configuration received: " + config);
                int toleranceLevel = config.getFaultToleranceLevel();
                boolean start = config.getMethod() == MethodType.STRT;
                isRunning = start;

                Message responseMessage = Message.newBuilder()
                        .setDemand(Demand.STRT)
                        .setResponse(start ? Response.YEP : Response.NOPE)
                        .build();

                sendProtobufMessage(output, responseMessage);
                System.out.println("Response sent to admin: " + responseMessage.getResponse());

                if (start) {
                    linkServers(toleranceLevel);
                }
            }

            while (isRunning && !adminSocket.isClosed()) {
                try {
                    Message request = receiveProtobufMessage(input, Message.class);
                    if (request != null && request.getDemand() == Demand.CPCTY) {
                        Capacity capacityInfo = Capacity.newBuilder()
                                .setServer1Status(subscribers.size())
                                .setServer2Status(queue.size())
                                .setServer3Status(queue.size())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                        sendProtobufMessage(output, capacityInfo);
                        System.out.println("Capacity sent to admin: " + capacityInfo);
                    }
                } catch (EOFException e) {
                    System.out.println("Admin disconnected.");
                    break;
                } catch (IOException e) {
                    System.err.println("Error handling admin request: " + e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error handling admin connection: " + e.getMessage());
        } finally {
            closeSocket(adminSocket);
        }
    }

    private void linkServers(int toleranceLevel) {
        for (int port : SERVER_PORTS) {
            executorService.submit(() -> connectServer(port));
        }
    }

    private void connectServer(int port) {
        try {
            Socket serverSocket = new Socket(HOST, port);
            serverSockets.add(serverSocket);
            System.out.println("Connected to server: " + serverSocket.getInetAddress() + ":" + port);
        } catch (IOException e) {
            System.err.println("Error connecting to server " + port + ": " + e.getMessage());
        }
    }

    private void ForwardToOtherServers(Subscriber sub) {
        if (subscribers.size() >= capacity.get()) {
            for (int port : SERVER_PORTS) {
                if (port != CLIENT_PORT) {
                    try (Socket socket = new Socket(HOST, port);
                         DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                        sendProtobufMessage(output, sub);
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

    // helpers
    private <T extends MessageLite> void sendProtobufMessage(DataOutputStream output, T message) throws IOException {
        byte[] data = message.toByteArray();
        System.out.println("Data: " + Arrays.toString(data));
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(data.length).array();
        System.out.println("Length Bytes: " + Arrays.toString(lengthBytes));
        output.write(lengthBytes);
        output.write(data);
        output.flush();
    }

    private <T extends com.google.protobuf.MessageLite> T receiveProtobufMessage(DataInputStream input, Class<T> clazz) throws IOException {
        try {
            byte[] lengthBytes = new byte[4];
            int bytesRead = input.read(lengthBytes);
            if (bytesRead == -1) {
                return null; // Connection closed
            }
            if (bytesRead != 4) {
                throw new IOException("Could not read full message length.");
            }
            int length = ByteBuffer.wrap(lengthBytes).getInt();
            byte[] data = new byte[length];
            input.readFully(data);
            return parseFrom(data, clazz);
        } catch (EOFException e) {
            return null; // Connection closed
        }
    }

    private <T extends com.google.protobuf.MessageLite> T parseFrom(byte[] data, Class<T> clazz) throws IOException {
        try {
            java.lang.reflect.Method parseFromMethod = clazz.getMethod("parseFrom", byte[].class);
            return clazz.cast(parseFromMethod.invoke(null, data));
        } catch (Exception e) {
            throw new IOException("Error parsing protobuf message: " + e.getMessage(), e);
        }
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
}
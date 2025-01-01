package dist_servers;

import communication.CapacityOuterClass.Capacity;
import communication.ConfigurationOuterClass.Configuration;
import communication.ConfigurationOuterClass.MethodType;
import communication.MessageOuterClass.*;
import communication.ProtobufHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;
import java.time.Instant;

public class AdminHandler {
    private final int adminPort;
    static boolean isRunning = false;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public AdminHandler(int adminPort) {
        this.adminPort = adminPort;
    }

    public static boolean getIsRunning() {
        return isRunning;
    }

    public void setIsRunning(boolean isRunning) {
        AdminHandler.isRunning = isRunning;
    }

    public void startAdmin() {
        ServerSocket adminServerSocket = null;
        try {
            adminServerSocket = new ServerSocket(adminPort);
            System.out.println("Server listening for admin on port: " + adminPort);
            ServerSocket finalServerSocket = adminServerSocket;
            executorService.submit(() -> acceptAdminConnections(finalServerSocket));

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
            executorService.shutdown();
        }
    }

    public void acceptAdminConnections(ServerSocket adminServerSocket) {
        while (true) {
            try {
                if (adminServerSocket == null || adminServerSocket.isClosed()) {
                    System.err.println("Admin server socket is closed. Stopping admin connection attempts.");
                    break;
                }
                Socket adminSocket = adminServerSocket.accept();
                System.out.println("Admin connected: " + adminSocket.getInetAddress());
                executorService.submit(() -> {
                    try {
                        handleAdmin(adminSocket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (SocketException e) {
                System.err.println("Admin server socket is closed. No longer accepting admins.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting admin connection: " + e.getMessage());
            }
        }
    }

    public void handleAdmin(Socket adminSocket) throws IOException {
        try (DataInputStream input = new DataInputStream(adminSocket.getInputStream());
             DataOutputStream output = new DataOutputStream(adminSocket.getOutputStream())) {

            Configuration configuration = ProtobufHandler.receiveProtobufMessage(input, Configuration.class);

            if (configuration != null) {
                System.out.println("Configuration from admin received: " + configuration.getMethod());

                if (configuration.getMethod() == MethodType.STRT) {
                    setIsRunning(true);

                    Message response = Message.newBuilder()
                            .setDemand(Demand.STRT)
                            .setResponse(Response.YEP)
                            .build();

                    ProtobufHandler.sendProtobufMessage(output, response);
                    System.out.println("Message sent to admin: " + response.getResponse());

                    // diğer serverlara bağlanma kodları

                } else if (configuration.getMethod() == MethodType.STOP) {
                    adminSocket.close();
                } else {
                    System.out.println("Invalid Configuration type.");
                }
            } else {
                System.out.println("Received null Configuration.");
            }

            while (isRunning && !adminSocket.isClosed()) {
                try {
                    Message message = ProtobufHandler.receiveProtobufMessage(input, Message.class);
                    if (message != null) {
                        System.out.println("Message from admin received: " + message.getDemand());

                        if (message.getDemand() == Demand.CPCTY) {
                            long timestamp = Instant.now().getEpochSecond();
                            Capacity capacity = Capacity.newBuilder()
                                    .setServer1Status(1000)
                                    .setTimestamp(timestamp)
                                    .build();

                            ProtobufHandler.sendProtobufMessage(output, capacity);
                            System.out.println("Capacity sent to admin: " + capacity);
                        } else {
                            System.out.println("Invalid Message type.");
                        }
                    } else {
                        System.err.println("Received null Message.");
                        break;
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
            // DistributedServerHandler.closeSocket(adminSocket);
        }
    }
}
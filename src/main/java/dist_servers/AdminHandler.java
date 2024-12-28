package dist_servers;

import communication.CapacityOuterClass.Capacity;
import communication.MessageOuterClass.*;
import communication.SubscriberOuterClass.Subscriber;
import communication.ProtobufHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Queue;
import java.util.concurrent.*;
import java.time.Instant;

public class AdminHandler {
    private static final ConcurrentMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();
    private static final Queue<Subscriber> queue = new ConcurrentLinkedQueue<>();
    private static boolean isRunning = false;
    private static final DistributedServerHandler DISTRIBUTED_SERVER_HANDLER = new DistributedServerHandler();
    private static final ProtobufHandler protobufHandler = new ProtobufHandler();

    public static void acceptAdminConnections(ServerSocket serverSocket, ExecutorService executorService, int[] ports, int clientPort, String host) {
        while (true) {
            try {
                if (serverSocket == null || serverSocket.isClosed()) {
                    System.err.println("Server socket is closed. Stopping admin connection attempts.");
                    break;
                }
                Socket adminSocket = serverSocket.accept();
                System.out.println("Admin connected: " + adminSocket.getInetAddress());
                executorService.submit(() -> handleAdmin(adminSocket, executorService, ports, clientPort, host));
            } catch (SocketException e) {
                System.err.println("ServerSocket is closed. No longer accepting admins.");
                break;
            } catch (IOException e) {
                System.err.println("Error accepting admin connection: " + e.getMessage());
            }
        }
    }

    public static void handleAdmin(Socket adminSocket, ExecutorService executorService, int[] ports, int clientPort, String host) {
        try (DataInputStream input = new DataInputStream(adminSocket.getInputStream());
             DataOutputStream output = new DataOutputStream(adminSocket.getOutputStream())) {

            Message request = protobufHandler.receiveProtobufMessage(input, Message.class);

            if (request != null) {
                System.out.println("Request from admin received: " + request.getDemand());
                if (request.getDemand() == Demand.STRT) {
                    isRunning = true;

                    Message message = Message.newBuilder()
                            .setDemand(Demand.STRT)
                            .setResponse(Response.YEP)
                            .build();

                    protobufHandler.sendProtobufMessage(output, message);
                    System.out.println("Message sent to admin: " + message.getDemand());

                    for (int port : ports) {
                        if (port != clientPort) {
                            executorService.submit(() -> DISTRIBUTED_SERVER_HANDLER.connectServer(port, host));
                        }
                    }
                }

                while (isRunning && !adminSocket.isClosed()) {
                    try {
                        if (request.getDemand() == Demand.CPCTY) {
                            System.out.println("Request from admin received: " + request.getDemand());
                            long timestamp = Instant.now().getEpochSecond();
                            Capacity capacity = Capacity.newBuilder().setServer1Status(1000).setTimestamp(timestamp).build();
                            protobufHandler.sendProtobufMessage(output, capacity);
                            System.out.println("Capacity sent to admin: " + capacity);
                        }
                    } catch (EOFException e) {
                        System.out.println("Admin disconnected.");
                        break;
                    } catch (IOException e) {
                        System.err.println("Error handling admin request: " + e.getMessage());
                        break;
                    }
                }
            } else {
                System.err.println("Received null Message.");
            }
        } catch (IOException e) {
            System.err.println("Error handling admin connection: " + e.getMessage());
        } finally {
            DistributedServerHandler.closeSocket(adminSocket);
        }
    }
}
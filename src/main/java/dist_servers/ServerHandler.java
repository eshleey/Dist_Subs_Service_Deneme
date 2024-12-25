package dist_servers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerHandler {
    private static final int[] SERVER_PORTS = {7001, 7002, 7003};
    private static final String HOST = "localhost";
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService adminExecutor = Executors.newFixedThreadPool(4);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final List<Socket> serverSockets = new CopyOnWriteArrayList<>();
    private static final AdminHandler adminHandler = new AdminHandler();
    private static final ClientHandler clientHandler = new ClientHandler();

    public void startServer(int adminPort, int clientPort) {
        ServerSocket adminServerSocket = null;
        ServerSocket clientServerSocket = null;
        try {
            adminServerSocket = new ServerSocket(adminPort);
            System.out.println("Server listening for admin on port: " + adminPort);
            clientServerSocket = new ServerSocket(clientPort);
            System.out.println("Server listening for clients on port: " + clientPort);

            final ServerSocket finalClientSocket = clientServerSocket;
            final ServerSocket finalAdminSocket = adminServerSocket;

            executorService.submit(() -> clientHandler.acceptClientConnections(finalClientSocket, executorService, SERVER_PORTS, clientPort, HOST));
            adminExecutor.submit(() -> adminHandler.acceptAdminConnections(finalAdminSocket, adminExecutor, SERVER_PORTS, clientPort, HOST));

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

    public void connectServer(int port, String host) {
        try {
            Socket serverSocket = new Socket(host, port);
            serverSockets.add(serverSocket);
            System.out.println("Connected to server: " + serverSocket.getInetAddress() + ":" + port);
        } catch (IOException e) {
            System.err.println("Error connecting to server " + port + ": " + e.getMessage());
        }
    }

    public void linkServers(ExecutorService executorService, int[] ports, int clientPort, String host) {
        for (int port : ports) {
            if (port != clientPort) {
                executorService.submit(() -> connectServer(port, host));
            }
        }
    }

    public void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
}

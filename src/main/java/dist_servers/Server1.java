package dist_servers;

import java.io.*;
import java.net.ServerSocket;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Server1 {
    private static final int ADMIN_PORT = 7004;
    private static final int CLIENT_PORT = 7001;
    private static final int[] SERVER_PORTS = {7001, 7002, 7003};
    private static final String HOST = "localhost";
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final AdminHandler adminHandler = new AdminHandler();
    private static final ClientHandler clientHandler = new ClientHandler();

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

            executorService.submit(() -> clientHandler.acceptClientConnections(finalClientSocket, executorService, SERVER_PORTS, CLIENT_PORT, HOST));
            adminExecutor.submit(() -> adminHandler.acceptAdminConnections(finalAdminSocket, adminExecutor, SERVER_PORTS, HOST));

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
}
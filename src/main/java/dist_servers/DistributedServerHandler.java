package dist_servers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DistributedServerHandler {
    private int port;
    private List<Socket> connectedServers;
    private ServerSocket serverSocket;
    private static final int CONNECTION_RETRY_INTERVAL = 5000; // 5 saniye
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public DistributedServerHandler(int port) {
        this.port = port;
        this.connectedServers = new ArrayList<>();
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);
            executorService.submit(this::connectToOtherServers);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void connectToOtherServers() {
        int[] otherPorts = {5001, 5002, 5003};
        while (true) {
            for (int otherPort : otherPorts) {
                if (otherPort != this.port && !isConnected(otherPort)) {
                    try {
                        Socket socket = new Socket("localhost", otherPort);
                        connectedServers.add(socket);
                        System.out.println("Connected to server on port " + otherPort);
                        // handleConnection(socket);
                    } catch (IOException e) {
                        System.out.println("Cannot connect to server on port " + otherPort + ". Retrying in " + CONNECTION_RETRY_INTERVAL / 1000 + " seconds.");
                    }
                }
            }
            try {
                Thread.sleep(CONNECTION_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean isConnected(int port) {
        return connectedServers.stream().anyMatch(socket -> socket.getPort() == port && socket.isConnected() && !socket.isClosed());
    }
}
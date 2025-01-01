package dist_servers;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FailureDetector {
    private final List<ServerHandler> servers;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final int HEALTH_CHECK_TIMEOUT_MS = 1000; // Bağlantı için zaman aşımı

    public FailureDetector(List<ServerHandler> servers) {
        System.out.println("FailureDetector started.");
        this.servers = servers;
        startHealthChecks();
    }

    private void startHealthChecks() {
        System.out.println("Health checks starting.");
        int healthCheckInterval = 5;
        executor.scheduleAtFixedRate(this::checkServerHealth, 0, healthCheckInterval, TimeUnit.SECONDS);
    }

    private void checkServerHealth() {
        System.out.println("Sağlık kontrolü yapılıyor...");
        for (ServerHandler server : servers) {
            boolean isAlive = isServerReachable(server.getPort()); // Aktif sağlık kontrolü
            System.out.println("Server " + server.getId() + " durumu: Ulaşılabilir=" + isAlive + ", Mevcut Alive=" + server.getIsAlive());
            if (!isAlive && server.getIsAlive()) {
                server.setIsAlive(false);
                System.out.println("Server " + server.getId() + " is Down!");
            } else if (isAlive && !server.getIsAlive()) {
                server.setIsAlive(true);
                System.out.println("Server " + server.getId() + " is UP!");
            }
        }
        checkPrimaryStatus();
    }

    private boolean isServerReachable(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            // Sağlık kontrolü mesajı gönder
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("HEALTH_CHECK");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void checkPrimaryStatus() {
        ServerHandler primaryServer = null;
        for (ServerHandler server : servers) {
            if (server.getIsPrimary() && server.getIsAlive()) {
                primaryServer = server;
            }
        }

        if (primaryServer == null || !primaryServer.getIsAlive()) {
            electNewPrimary();
        }
    }

    public void electNewPrimary() {
        System.out.println("Primary Server failed, starting new election");
        ServerHandler newPrimary = null;
        int lowestId = Integer.MAX_VALUE;
        for (ServerHandler server : servers) {
            if (server.getIsAlive() && server.getId() < lowestId) {
                lowestId = server.getId();
                newPrimary = server;
            }
        }

        if (newPrimary != null) {
            for (ServerHandler server : servers) {
                server.setIsPrimary(server == newPrimary);
            }
            System.out.println("New primary server is " + newPrimary.getId());
            syncData(newPrimary);
        } else {
            System.out.println("No server is available");
        }
    }

    private void syncData(ServerHandler newPrimary) {
        for (ServerHandler server : servers) {
            if (!server.getIsPrimary()) {
                server.setSubscriberData(newPrimary.getSubscriberData());
                System.out.println("Data synchronized with server " + server.getId());
            }
        }
    }
}
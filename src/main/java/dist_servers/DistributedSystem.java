package dist_servers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistributedSystem {
    private static final List<ServerHandler> servers = new ArrayList<>();
    private static final Map<Integer, ClientHandler> clients = new HashMap<Integer, ClientHandler>();

    static {
        servers.add(Server1.getServerHandler());
        servers.add(Server2.getServerHandler());
        servers.add(Server3.getServerHandler());

        clients.put(6001, Server1.getClientHandler());
        clients.put(6002, Server1.getClientHandler());
        clients.put(6003, Server1.getClientHandler());
    }

    public static synchronized List<ServerHandler> getServers() {
        return servers;
    }

    public static synchronized Map<Integer, ClientHandler> getClients() { return clients; }

    public static void main(String[] args) {
        FailureDetector failureDetector = new FailureDetector(servers);
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
package dist_servers;

import java.util.ArrayList;
import java.util.List;

public class DistributedSystem {
    private static final List<ServerHandler> servers = new ArrayList<>();

    static {
        servers.add(Server1.getServerHandler());
        servers.add(Server2.getServerHandler());
        servers.add(Server3.getServerHandler());
    }

    public static synchronized List<ServerHandler> getServers() {
        return servers;
    }

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
package dist_servers;

@SuppressWarnings("unused")
public class Server2 {
    private static final int ADMIN_PORT = 7005;
    private static final int CLIENT_PORT = 7002;

    public static void main(String[] args) {
        DistributedServerHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
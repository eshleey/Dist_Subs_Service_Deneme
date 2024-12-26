package dist_servers;

@SuppressWarnings("unused")
public class Server1 {
    private static final int ADMIN_PORT = 7004;
    private static final int CLIENT_PORT = 7001;

    public static void main(String[] args) {
        DistributedServerHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
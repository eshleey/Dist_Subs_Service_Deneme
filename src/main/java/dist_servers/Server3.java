package dist_servers;

@SuppressWarnings("unused")
public class Server3 {
    private static final int ADMIN_PORT = 7006;
    private static final int CLIENT_PORT = 7003;

    public static void main(String[] args) {
        DistributedServerHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
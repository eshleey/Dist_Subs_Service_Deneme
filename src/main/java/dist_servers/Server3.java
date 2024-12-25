package dist_servers;

@SuppressWarnings("unused")
public class Server3 {
    private static final int ADMIN_PORT = 7006;
    private static final int CLIENT_PORT = 7003;
    private static final ServerHandler serverHandler = new ServerHandler();

    public static void main(String[] args) {
        serverHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
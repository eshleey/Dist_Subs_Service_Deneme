package dist_servers;

@SuppressWarnings("unused")
public class Server2 {
    private static final int ADMIN_PORT = 7005;
    private static final int CLIENT_PORT = 7002;
    private static final ServerHandler serverHandler = new ServerHandler();

    public static void main(String[] args) {
        serverHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
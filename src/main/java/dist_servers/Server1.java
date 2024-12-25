package dist_servers;

@SuppressWarnings("unused")
public class Server1 {
    private static final int ADMIN_PORT = 7004;
    private static final int CLIENT_PORT = 7001;
    private static final ServerHandler serverHandler = new ServerHandler();

    public static void main(String[] args) {
        serverHandler.startServer(ADMIN_PORT, CLIENT_PORT);
    }
}
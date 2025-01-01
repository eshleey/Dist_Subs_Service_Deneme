package dist_servers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("unused")
public class Server1 {
    private static final int SERVER_PORT = 5001;
    private static final int ADMIN_PORT = 7001;
    private static final int CLIENT_PORT = 6001;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ServerHandler serverHandler = new ServerHandler(1, SERVER_PORT, true);
    private static final AdminHandler adminHandler = new AdminHandler(ADMIN_PORT);
    private static final ClientHandler clientHandler = new ClientHandler(CLIENT_PORT);

    public static ServerHandler getServerHandler() {
        return serverHandler;
    }

    public static void main(String[] args) {
        executorService.submit(serverHandler::startServer);
        executorService.submit(adminHandler::startAdmin);
        executorService.submit(clientHandler::startClient);
    }
}
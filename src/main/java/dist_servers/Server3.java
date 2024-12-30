package dist_servers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("unused")
public class Server3 {
    private static final int SERVER_PORT = 5003;
    private static final int ADMIN_PORT = 7003;
    private static final int CLIENT_PORT = 6003;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final DistributedServerHandler serverHandler = new DistributedServerHandler(SERVER_PORT);
    private static final AdminHandler adminHandler = new AdminHandler(ADMIN_PORT);
    private static final ClientHandler clientHandler = new ClientHandler(CLIENT_PORT);

    public static void main(String[] args) {
        executorService.submit(serverHandler::startServer);
        executorService.submit(adminHandler::startAdmin);
        executorService.submit(clientHandler::startClient);
    }
}
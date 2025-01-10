package dist_servers;

import communication.ProtobufHandler;
import communication.SubscriberOuterClass.Subscriber;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerHandler {
    private final int id;
    private final int port;
    private boolean isPrimary;
    private boolean isAlive;
    private ConcurrentMap<Integer, Subscriber> subscriberData = new ConcurrentHashMap<>();
    private List<Socket> connectedServers;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private static final int CONNECTION_RETRY_INTERVAL = 5000; // 5 saniye
    private static final int THREAD_POOL_SIZE = 10;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static int primaryPort = 0;

    public ServerHandler(int id, int port, boolean isPrimary) {
        this.id = id;
        this.port = port;
        this.isPrimary = isPrimary;
        this.isAlive = true;
        this.connectedServers = new ArrayList<>();
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public int getId() {
        return id;
    }

    public int getPort() {
        return port;
    }

    public boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(boolean primary) {
        isPrimary = primary;
    }

    public boolean getIsAlive() {
        return isAlive;
    }

    public void setIsAlive(boolean alive) {
        isAlive = alive;
    }

    public void addSubscriberData(Subscriber newSubscriberData) {
        if (isPrimary) {
            this.subscriberData.put(newSubscriberData.getID(), newSubscriberData);
            syncData(newSubscriberData);
            System.out.println("Subscriber added with id " + newSubscriberData.getID() + ". Synced to other servers");
        }
    }

    public ConcurrentMap<Integer, Subscriber> getSubscriberData() {
        return subscriberData;
    }

    public void setSubscriberData(ConcurrentMap<Integer, Subscriber> subscriberData) {
        this.subscriberData = subscriberData;
    }

    public void printStatus(){
        System.out.println("Server "+ id + ", Primary: " + isPrimary + ", Alive: " + isAlive );
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);
            executorService.submit(this::handleClientConnection);
            executorService.submit(this::connectToOtherServers);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClientConnection() {
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClientRequest(clientSocket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClientRequest(Socket clientSocket){
        try(DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())){

            String request = input.readUTF();
            if("HEALTH_CHECK".equals(request)){
                System.out.println("Sağlık kontrolü isteği alındı: " + clientSocket.getInetAddress());
                return;
            } else if("GET_PRIMARY_PORT".equals(request)){
                if(isPrimary){
                    output.writeInt(this.port);
                    System.out.println("Primary port sent to client: " + this.port + " from primary");
                    primaryPort = this.port;
                }else{
                    int pPort =  0;
                    for(ServerHandler sh : DistributedSystem.getServers()){
                        if(sh.getIsPrimary() && sh.getIsAlive()){
                            pPort = sh.getPort();
                            break;
                        }
                    }

                    output.writeInt(pPort);
                    System.out.println("Secondary port sent to client: " + pPort + " from secondary");
                }
            }
        }catch (IOException e){
            if (!(e instanceof java.io.EOFException)) {
                e.printStackTrace();
            } else {
                System.out.println("Sağlık kontrolü bağlantısı kapatıldı.");
            }
        }
    }

    private void connectToOtherServers() {
        while (!AdminHandler.getIsRunning()) {
            try {
                Thread.sleep(1000); // 1 saniye bekle
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        int[] otherPorts = {5001, 5002, 5003};
        for (int otherPort : otherPorts) {
            if (otherPort != this.port && !isConnected(otherPort)) {
                try {
                    Socket socket = new Socket("localhost", otherPort);
                    connectedServers.add(socket);
                    System.out.println("Connected to server on port " + otherPort);
                    executorService.submit(()->handleSync(socket));
                } catch (IOException e) {
                    System.out.println("Cannot connect to server on port " + otherPort + ". Retrying in " + CONNECTION_RETRY_INTERVAL / 1000 + " seconds.");
                }
            }
        }

        try {
            Thread.sleep(CONNECTION_RETRY_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    private void handleSync(Socket socket){
        try(DataInputStream input = new DataInputStream(socket.getInputStream());){
            while(true){
                Subscriber subscriber = ProtobufHandler.receiveProtobufMessage(input, Subscriber.class);
                if(subscriber != null){
                    addSubscriberData(subscriber);
                }

            }

        }catch (IOException e){
            e.printStackTrace();
        }
    }


    private boolean isConnected(int port) {
        try{
            return connectedServers.stream().anyMatch(socket -> socket.getPort() == port && socket.isConnected() && !socket.isClosed());
        }catch (Exception e){
            return false;
        }
    }

    private void syncData(Subscriber subscriber) {
        if (isPrimary) {
            for(Socket socket: connectedServers){
                try {
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    ProtobufHandler.sendProtobufMessage(output, subscriber);
                    System.out.println("Data sync to server with port " + socket.getPort());
                } catch (IOException e) {
                    System.err.println("Error during data synchronization with server. " + socket.getPort() + e.getMessage());
                }
            }
        }
    }
}
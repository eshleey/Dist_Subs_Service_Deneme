package Clients;

import java.io.IOException;

public class Client1 {
    private static final int PORT = 7001;
    private static final int ID = 14;

    public static void main(String[] args) {
        try {
            ClientHandler.connectServer(PORT);
            ClientHandler.sendRequest("SUBS", ID, ClientHandler.getOutput());
            ClientHandler.sendRequest("DEL", ID, ClientHandler.getOutput());
            ClientHandler.receiveResponse(ClientHandler.getInput());
        } catch (IOException e) {
            System.err.println("Connection or IO error: " + e.getMessage());
        } finally {
            ClientHandler.disconnectServer();
        }
    }
}
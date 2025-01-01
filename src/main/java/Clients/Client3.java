package Clients;

import java.io.IOException;

public class Client3 {
    private static final int ID = 14;

    public static void main(String[] args) {
        try {
            ClientHandler.connectServer();
            if(ClientHandler.getOutput() == null){
                System.err.println("Output stream is null, check connection.");
                return;
            }
            Thread.sleep(100);
            ClientHandler.sendRequest("SUBS", ID, ClientHandler.getOutput());
            Thread.sleep(100);
            ClientHandler.sendRequest("ONLN", ID, ClientHandler.getOutput());
            Thread.sleep(100);
            ClientHandler.sendRequest("OFFL", ID, ClientHandler.getOutput());
            Thread.sleep(100);
            ClientHandler.sendRequest("DEL", ID, ClientHandler.getOutput());
            ClientHandler.receiveResponse(ClientHandler.getInput());
        } catch (IOException e) {
            System.err.println("Connection or IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            ClientHandler.disconnectServer();
        }
    }
}
package Clients;

import communication.ProtobufHandler;
import communication.SubscriberOuterClass.Subscriber;
import dist_servers.DistributedSystem;
import dist_servers.ServerHandler;

import java.util.Arrays;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private static final String SERVER_HOST = "localhost";
    private static Socket socket;
    private static DataOutputStream output;
    private static DataInputStream input;

    public static void connectServer(int clientPort) throws IOException {
        try {
            socket = new Socket(SERVER_HOST, clientPort);
            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            System.out.println("Connected to server: " + SERVER_HOST + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage() + " to " + clientPort);
        }
    }

    public static void disconnectServer() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Disconnected from server.");
            }
        } catch (IOException e) {
            System.err.println("Error while disconnecting: " + e.getMessage());
        }
    }

    public static DataOutputStream getOutput() {
        return output;
    }

    public static DataInputStream getInput() {
        return input;
    }

    public static void sendRequest(String request, int id, DataOutputStream output) throws IOException {
        Subscriber subscriber = switch (request) {
            case "SUBS" -> SubscriberHandler.createSubscriberForSubs(id, "Jane DOE",
                    Arrays.asList("sports", "lifestyle", "cooking", "psychology"));
            case "ONLN" -> SubscriberHandler.createSubscriberForOnln(id);
            case "OFFL" -> SubscriberHandler.createSubscriberForOffl(id);
            case "DEL" -> SubscriberHandler.createSubscriberForDel(id);
            default -> throw new IllegalArgumentException("Invalid request type: " + request);
        };

        System.out.println("Subscriber: " + subscriber);
        ProtobufHandler.sendProtobufMessage(output, subscriber);
        System.out.println("Request: " + request);
    }

    public static void receiveResponse(DataInputStream input) throws IOException {
        Subscriber subscriber = ProtobufHandler.receiveProtobufMessage(input, Subscriber.class);
        if (subscriber != null) {
            System.out.println("Response received: " + subscriber);
        } else {
            System.err.println("Failed to receive response.");
        }
    }
}

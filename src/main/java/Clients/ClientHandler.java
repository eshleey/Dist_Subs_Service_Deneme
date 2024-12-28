package Clients;

import communication.ProtobufHandler;
import communication.SubscriberOuterClass.Subscriber;

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

    private static final ProtobufHandler protobufHandler = new ProtobufHandler();

    public static void connectServer(int port) throws IOException {
        if (socket != null && !socket.isClosed()) {
            System.out.println("Already connected to a server.");
            return;
        }
        try {
            socket = new Socket(SERVER_HOST, port);
            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            System.out.println("Connected to server: " + SERVER_HOST + ":" + port);
        } catch (IOException ie) {
            System.err.println("Connection error: " + ie.getMessage());
            throw ie;
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
        protobufHandler.sendProtobufMessage(output, subscriber);
        System.out.println("Request: " + request);
    }

    public static void receiveResponse(DataInputStream input) throws IOException {
        Subscriber subscriber = protobufHandler.receiveProtobufMessage(input, Subscriber.class);
        if (subscriber != null) {
            System.out.println("Response received: " + subscriber);
        } else {
            System.err.println("Failed to receive response.");
        }
    }
}

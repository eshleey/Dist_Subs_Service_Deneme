package Clients;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import communication.MessageOuterClass.Message;
import communication.SubscriberOuterClass;
import communication.SubscriberOuterClass.Subscriber;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 7001;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {

            // Sunucuya STRT mesajı gönder
            sendStartMessage(output);
            Message responseMessage = receiveMessage(input);

            // Sunucudan gelen yanıt kontrolü
            if ("YEP".equals(responseMessage.getResponse())) {
                System.out.println("Connection established successfully.");
            } else {
                System.out.println("Failed to establish connection.");
                return;
            }

            // Abone olma talebi gönder (SUBS)
            sendSubscribeRequest(output);
            responseMessage = receiveMessage(input);

            if ("NOPE".equals(responseMessage.getResponse())) {
                System.out.println("Failed to subscribe.");
            } else {
                System.out.println("Successfully subscribed.");
            }

            // Abonelikten çıkma talebi gönder (DEL)
            sendUnsubscribeRequest(output);
            responseMessage = receiveMessage(input);

            if ("NOPE".equals(responseMessage.getResponse())) {
                System.out.println("Failed to unsubscribe.");
            } else {
                System.out.println("Successfully unsubscribed.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendStartMessage(OutputStream output) throws IOException {
        Message startMessage = Message.newBuilder()
                .setDemand("STRT")
                .build();
        output.write(startMessage.toByteArray());
        output.flush();
    }

    private static void sendSubscribeRequest(OutputStream output) throws IOException {
        Subscriber subscriber = Subscriber.newBuilder()
                .setDemand(SubscriberOuterClass.DemandType.SUBS)
                .setID(12)
                .setNameSurname("Jane DOE")
                .setStartDate(1729802522)
                .setLastAccessed(1729806522)
                .addInterests("sports")
                .addInterests("lifestyle")
                .addInterests("cooking")
                .addInterests("psychology")
                .setIsOnline(true)
                .build();

        byte[] subscriberBytes = subscriber.toByteArray();
        output.write(subscriberBytes);
        output.flush();
    }

    private static void sendUnsubscribeRequest(OutputStream output) throws IOException {
        Subscriber subscriber = Subscriber.newBuilder()
                .setDemand(SubscriberOuterClass.DemandType.DEL)
                .setID(12)
                .build(); // ID ile birlikte abonelikten çıkma talebi

        byte[] subscriberBytes = subscriber.toByteArray();
        output.write(subscriberBytes);
        output.flush();
    }

    private static Message receiveMessage(InputStream input) throws IOException {
        byte[] messageBytes = input.readAllBytes();
        return Message.parseFrom(messageBytes);
    }
}

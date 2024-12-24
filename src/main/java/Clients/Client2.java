package Clients;

import com.google.protobuf.MessageLite;
import communication.ConfigurationOuterClass.Configuration;
import communication.ConfigurationOuterClass.MethodType;
import communication.MessageOuterClass.Message;
import communication.SubscriberOuterClass.Subscriber;
import communication.SubscriberOuterClass.DemandType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Client2 {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 7002;

    public static void main(String[] args) throws ReflectiveOperationException {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataOutputStream output = new DataOutputStream(socket.getOutputStream());
             DataInputStream input = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected to server: " + SERVER_HOST + ":" + SERVER_PORT);

            // Send Configuration
            Configuration config = createConfiguration();
            sendProtobufMessage(output, config);
            System.out.println("Configuration message sent.");

            Thread.sleep(100);

            // Send SUBS request
            Subscriber subscriber = createSubscriberForSub();
            sendProtobufMessage(output, subscriber);
            System.out.println("Subscriber SUBS message sent.");

            Thread.sleep(100);

            // Send DEL request
            Subscriber delSubscriber = createSubscriberForDel(subscriber.getID());
            sendProtobufMessage(output, delSubscriber);
            System.out.println("Subscriber DEL message sent.");

            // Wait for server response
            Message response = receiveProtobufMessage(input, Message.class);
            if (response != null) {
                System.out.println("Response received: " + response);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error in client: " + e.getMessage());
        }
    }

    private static Configuration createConfiguration() {
        return Configuration.newBuilder()
                .setMethod(MethodType.STRT)
                .setFaultToleranceLevel(1)
                .build();
    }

    private static Subscriber createSubscriberForSub() {
        return Subscriber.newBuilder()
                .setDemand(DemandType.SUBS)
                .setID(14)
                .setNameSurname("Jane DOE")
                .setStartDate(System.currentTimeMillis())
                .setLastAccessed(System.currentTimeMillis())
                .addAllInterests(Arrays.asList("sports", "lifestyle", "cooking", "psychology"))
                .setIsOnline(true)
                .build();
    }

    private static Subscriber createSubscriberForDel(int id) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.DEL)
                .setID(id)
                .build();
    }

    // --- Helper methods for sending and receiving Protobuf messages ---
    private static <T extends MessageLite> void sendProtobufMessage(DataOutputStream output, T message) throws IOException {
        byte[] data = message.toByteArray();
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(data.length).array();
        output.write(lengthBytes);
        output.write(data);
        output.flush();
    }

    private static <T extends MessageLite> T receiveProtobufMessage(DataInputStream input, Class<T> clazz) throws IOException, ReflectiveOperationException {
        try {
            byte[] lengthBytes = new byte[4];
            int bytesRead = input.read(lengthBytes);
            if (bytesRead == -1) {
                return null; // Connection closed
            }
            if (bytesRead != 4) {
                throw new IOException("Could not read full message length.");
            }
            int length = ByteBuffer.wrap(lengthBytes).getInt();
            byte[] data = new byte[length];
            input.readFully(data);
            return parseFrom(data, clazz);
        } catch (EOFException e) {
            return null; // Connection closed
        }
    }

    private static <T extends MessageLite> T parseFrom(byte[] data, Class<T> clazz) throws ReflectiveOperationException {
        java.lang.reflect.Method parseFromMethod = clazz.getMethod("parseFrom", byte[].class);
        return clazz.cast(parseFromMethod.invoke(null, data));
    }
}
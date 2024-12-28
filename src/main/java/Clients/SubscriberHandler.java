package Clients;

import communication.SubscriberOuterClass.Subscriber;
import communication.SubscriberOuterClass.DemandType;

import java.util.List;

public class SubscriberHandler {
    public static Subscriber createSubscriberForSubs(int id, String nameSurname, List<String> interests) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.SUBS)
                .setID(id)
                .setNameSurname(nameSurname)
                .setStartDate(System.currentTimeMillis())
                .setLastAccessed(System.currentTimeMillis())
                .addAllInterests(interests)
                .setIsOnline(true)
                .build();
    }

    public static Subscriber createSubscriberForOnln(int id) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.ONLN)
                .setID(id)
                .build();
    }

    public static Subscriber createSubscriberForOffl(int id) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.OFFL)
                .setID(id)
                .build();
    }

    public static Subscriber createSubscriberForDel(int id) {
        return Subscriber.newBuilder()
                .setDemand(DemandType.DEL)
                .setID(id)
                .build();
    }
}

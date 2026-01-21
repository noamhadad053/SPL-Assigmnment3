package bgu.spl.net.srv;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionsImpl<T> implements Connections<T>{

    //concurrent HashMap and concurrent LinkedQueue like in PS8
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelSubscribers = new ConcurrentHashMap<>();
    @Override
    public boolean send(int connectionId, T msg){
        //lookup clients handler
        ConnectionHandler<T> handler = activeConnections.get(connectionId);


        //client connected
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;

    }

    @Override
    public void send(String channel, T msg){
        //find subscribers
        ConcurrentLinkedQueue<Integer> subscribers = channelSubscribers.get(channel);

        if (subscribers != null) {
            for (Integer userId : subscribers) {
                send(userId,msg);
            }
        }

        return;

    }

    @Override
    public void disconnect(int connectionId){
        activeConnections.remove(connectionId);

        for (ConcurrentHashMap<Integer, Integer> subscribers : channelSubscribers.values()) {
        subscribers.remove(connectionId); 
        }

    }

    @Override
    public void subscribe(String channel, int connectionId, int subscriptionId) {
        
        channelSubscribers.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
                          .put(connectionId, subscriptionId);
    }

    @Override
    public void unsubscribe(String channel, int connectionId, int subscriptionId) {
        if (channelSubscribers.containsKey(channel)) {
            channelSubscribers.get(channel).remove(connectionId);
        }
    }


    //we need to send client 0 the id header 0 and client 1 the id header 1
    //helper function to do that
    public void sendMessage(String channel, String body, String messageId) {
        if (channelSubscribers.containsKey(channel)) {
            ConcurrentHashMap<Integer, Integer> subscribers = channelSubscribers.get(channel);
            
            for (var entry : subscribers.entrySet()) {
                Integer connId = entry.getKey();
                Integer subId = entry.getValue(); // <--- This is the magic number you need!
                
                // Construct the frame specifically for this user
                String frame = "MESSAGE\n" +
                            "subscription:" + subId + "\n" +
                            "message-id:" + messageId + "\n" +
                            "destination:" + channel + "\n" +
                            "\n" +
                            body + "\u0000";
                
                send(connId, (T) frame); // Cast only works if T is String
            }
        }
    }
    
}

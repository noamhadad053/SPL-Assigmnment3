package bgu.spl.net.srv;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionsImpl<T> implements Connections<T>{

    //concurrent HashMap and concurrent LinkedQueue like in PS8
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> channelSubscribers = new ConcurrentHashMap<>();
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

        for (ConcurrentLinkedQueue<Integer> subscribers : channelSubscribers.values()) {
            subscribers.remove(connectionId); 
        }

    }
    
}

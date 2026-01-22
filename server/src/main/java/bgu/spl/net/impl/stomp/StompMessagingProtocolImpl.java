package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    //for unique messageIDs
    private static final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private String loggedInUser = null;
    //remember which id belongs to which channel
    private Map<Integer, String> clientSubscriptions = new HashMap<>();

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public String process(String message) {
        //parsing the messages
        String[] lines = message.split("\n");
        if (lines.length == 0) return null;

        String command = lines[0].trim();

        switch (command) {
            case "CONNECT":
                handleConnect(message);
                break;
            case "SUBSCRIBE":
                handleSubscribe(message);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(message);
                break;
            case "SEND":
                handleSend(message);
                break;
            case "DISCONNECT":
                handleDisconnect(message);
                break;
            default:
                System.out.println("Received unknown command: " + command);
        }

        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void handleConnect(String msg) {
        Map<String, String> headers = parseHeaders(msg);
        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String host = headers.get("host");
        String acceptVersion = headers.get("accept-version");

        //checks that there's no errors using the helper function
        if (login == null || passcode == null || acceptVersion == null || host == null) {
            sendError(null, "Malformed Frame", "Missing mandatory headers");
            return;
        }

        UserManager userManager = UserManager.getInstance();

        if (userManager.isUserActive(login)) {
            sendError(null, "User already logged in", "User " + login + " is already active.");
            return;
        }

        

        if (!userManager.login(login, passcode)) {
            // user isn't active and login is false so password is wrong
            sendError(null, "Wrong password", "Password does not match.");
            return;
        }

        this.loggedInUser = login;

        System.out.println("User " + login + " connected successfully.");

        String response = "CONNECTED\n" +
                          "version:1.2\n" +
                          "\n" + 
                          "\u0000";
        
        connections.send(connectionId, response);

    }

    private void handleDisconnect(String msg) {
        
        Map<String, String> headers = parseHeaders(msg);
        String receiptId = headers.get("receipt");
        
        //logout the user
        if (loggedInUser != null) {
            UserManager.getInstance().logout(loggedInUser);
        }

        //send receipt
        String response = "RECEIPT\n" +
                          "receipt-id:" + receiptId + "\n" +
                          "\n" + 
                          "\u0000";
        connections.send(connectionId, response);

        //terminate
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private void handleSubscribe(String msg) {
        Map<String, String> headers = parseHeaders(msg);
        
        String destination = headers.get("destination");
        String subIdStr = headers.get("id");
        String receipt = headers.get("receipt");
        

        if (destination == null || subIdStr == null) {
            sendError(receipt, "Malformed Frame", "Missing 'destination' or 'id' header");
            return;
        }

        
        int subscriptionId;
        try {
            subscriptionId = Integer.parseInt(subIdStr);
        } catch (NumberFormatException e) {
            sendError(receipt, "Malformed Frame", "Subscription ID must be a number");
            return;
        }

        connections.subscribe(destination, connectionId, subscriptionId);

        clientSubscriptions.put(subscriptionId, destination);

        System.out.println("Client " + connectionId + " subscribed to " + destination + " with ID " + subscriptionId);

        
        if (receipt != null) {
            String response = "RECEIPT\n" +
                              "receipt-id:" + receipt + "\n" +
                              "\n" + 
                              "\u0000";
            connections.send(connectionId, response);
        }

    }

    private void handleUnsubscribe(String msg) {
        Map<String, String> headers = parseHeaders(msg);
        String subIdStr = headers.get("id");
        String receipt = headers.get("receipt");

        if (subIdStr == null) {
            sendError(receipt, "Malformed Frame", "Missing 'id' header");
            return;
        }

        try {
            int subscriptionId = Integer.parseInt(subIdStr);
            
            // search for the channel name
            String channel = clientSubscriptions.remove(subscriptionId);
            
            if (channel != null) {
                connections.unsubscribe(channel, connectionId, subscriptionId);
                
                if (receipt != null) {
                    String response = "RECEIPT\n" +
                                      "receipt-id:" + receipt + "\n" +
                                      "\n" + 
                                      "\u0000";
                    connections.send(connectionId, response);
                }
            } else {
                 // sub not found
                 if (receipt != null) {
                     connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n\u0000");
                 }
            }
            
        } catch (NumberFormatException e) {
            sendError(receipt, "Malformed Frame", "Subscription ID must be a number");
        }
    }

    private void handleSend(String msg) {
        Map<String, String> headers = parseHeaders(msg);
        
        String destination = headers.get("destination");
        String receipt = headers.get("receipt");

        
        if (destination == null) {
            sendError(receipt, "Malformed Frame", "Missing 'destination' header");
            return;
        }

        // Check if user is actually subscribed to this channel
        
        if (!clientSubscriptions.containsValue(destination)) {
             sendError(receipt, "Access Denied", "You must be subscribed to the channel to send messages.");
             return;
        }

        // body starts after the first blank line.
        String body = "";
        int blankLineIndex = msg.indexOf("\n\n");
        if (blankLineIndex != -1) {
            // +2 to skip the two newlines
            body = msg.substring(blankLineIndex + 2);
        }

        //messageId generated by time might get called at the same time decided to use atomic integers instead
        String messageId = "" + messageIdCounter.getAndIncrement();
        
        // broadcasts using the helper method from connectionsimpl
        connections.sendMessage(destination, body, messageId);
        
        // 6. Send Receipt (if requested)
        if (receipt != null) {
            String response = "RECEIPT\n" +
                              "receipt-id:" + receipt + "\n" +
                              "\n" + 
                              "\u0000";
            connections.send(connectionId, response);
        }
    }





    //---------------------helper classes:

    //helper erropr class
    private void sendError(String receiptId, String message, String description) {
        String errorFrame = "ERROR\n" +
                            (receiptId != null ? "receipt-id:" + receiptId + "\n" : "") +
                            "message:" + message + "\n" +
                            "\n" +
                            description + "\n" +
                            "\u0000";
        
        connections.send(connectionId, errorFrame);
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }
    //helper parsing class
    private Map<String, String> parseHeaders(String msg) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = msg.split("\n");
        
        // Start from index 1 (skip the command line)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) break; // Stop at the blank line (end of headers)
            
            String[] parts = line.split(":", 2); // Split only on the first ':'
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return headers;
    }

}

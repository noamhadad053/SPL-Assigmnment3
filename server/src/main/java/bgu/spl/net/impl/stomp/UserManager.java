package bgu.spl.net.impl.stomp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class UserManager {


    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    //store active users in here
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    //make sure all threads share the same DB
    private static class Holder {
        private static final UserManager INSTANCE = new UserManager();
    }

    public static UserManager getInstance() {
        return Holder.INSTANCE;
    }


    public synchronized boolean login(String username, String password) {
        // Case 1: User is already logged in -> Fail
        if (activeUsers.contains(username)) {
            return false; 
        }

        // Case 2: New User -> Register automatically (as per spec)
        if (!users.containsKey(username)) {
            users.put(username, password);
            activeUsers.add(username);
            return true;
        }

        // Case 3: Existing User -> Check Password
        String storedPassword = users.get(username);
        if (storedPassword.equals(password)) {
            activeUsers.add(username);
            return true;
        } else {
            return false; // Wrong password
        }
    }

    public void logout(String username) {
        activeUsers.remove(username);
    }
    
    // Helper to check why login failed (optional, for specific error messages)
    public boolean isUserActive(String username) {
        return activeUsers.contains(username);
    }

    public boolean isPasswordCorrect(String username, String password) {
        return users.containsKey(username) && users.get(username).equals(password);
    }
}
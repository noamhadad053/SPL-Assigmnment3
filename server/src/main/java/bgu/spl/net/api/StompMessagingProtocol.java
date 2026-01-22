package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

//messaging protocol expects process to return T but stomp messaging protocol expects process to return void.
//  Server requires messaging protocol so there's a conflict. There wasn't an answer to the question on the forums.
public interface StompMessagingProtocol<T>  extends MessagingProtocol<T>{
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    void start(int connectionId, Connections<T> connections);
    
    //changed return type to T. Process isn't a public method so it's fine to change it's signature
    T process(T message);
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}

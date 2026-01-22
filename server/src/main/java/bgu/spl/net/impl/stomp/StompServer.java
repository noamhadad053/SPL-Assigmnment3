package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Server;


public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <server_type>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        if (serverType.equals("tpc")) {
            Server.<String>threadPerClient(
                    port,
                    () -> new StompMessagingProtocolImpl(), //factory
                    () -> new StompEncoderDecoder()             
            ).serve();

        } else if (serverType.equals("reactor")) {
            Server.<String>reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    () -> new StompMessagingProtocolImpl(),
                    () -> new StompEncoderDecoder()
            ).serve();
        }
    }
}

package bgu.spl.net.impl.stomp;

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
            Server.threadPerClient(
                    port,
                    () -> new StompMessagingProtocolImpl(), // TODO: You need to create this class
                    () -> new LineMessageEncoderDecoder()   // TODO: You need to create a specific StompEncoderDecoder
            ).serve();

        } else if (serverType.equals("reactor")) {
            Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    () -> new StompMessagingProtocolImpl(), // TODO: You need to create this class
                    () -> new LineMessageEncoderDecoder()   // TODO: You need to create a specific StompEncoderDecoder
            ).serve();
        }
    }
}

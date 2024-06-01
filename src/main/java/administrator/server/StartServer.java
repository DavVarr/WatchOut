package administrator.server;

import java.io.IOException;

public class StartServer {

    public static void main(String[] args) throws IOException {
        System.out.println("Server starting...");
        Server server = Server.getInstance();
        System.out.println("Hit return to stop...");
        System.in.read();
        System.out.println("Stopping server...");
        server.shutdown();
        System.out.println("Server stopped");
    }
}

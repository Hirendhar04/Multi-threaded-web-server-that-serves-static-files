package webserver;

/**
 * The entry point for the SimpleWebServer.
 * <p>
 * Starts the server using the provided port and public directory.
 * </p>
 */
public class Main {
    
    /**
     * Starts the web server with the given command-line arguments.
     *
     * @param args Port number and public directory path.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Main <port> <public_directory>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        String publicDir = args[1];
        new SimpleWebServer(port, publicDir).start();
    }
}

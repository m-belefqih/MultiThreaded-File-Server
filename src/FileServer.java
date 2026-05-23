import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {
    public static void main(String[] args) {
        SharedState sharedState = new SharedState();

        Thread statsThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // 5 seconds
                    System.out.println("\n" + sharedState.getStats() + "\n");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Stats thread interrupted.");
                    break;
                }
            }
        });
        statsThread.setDaemon(true);
        statsThread.start();

        try (ServerSocket serverSocket = new ServerSocket(12346)) {
            System.out.println("File server started on port 12346...");
            System.out.println("Waiting for clients...");

            while (true) {
                // The accept() call blocks until a client connects.
                Socket clientSocket = serverSocket.accept();
                System.out.println("New Client connected: " + clientSocket.getInetAddress().getHostAddress());

                // Create a new handler for the client.
                ClientHandler clientHandler = new ClientHandler(clientSocket, sharedState);

                // Launch the handler in a new thread to process the client's requests
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port 12346: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

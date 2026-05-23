import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MultiClientTest {

    private static final String HOSTNAME = "localhost"; // 127.0.0.1
    private static final int PORT = 12346;
    private static final int NUM_CLIENTS = 3;
    private static final String FILE_TO_GET = "notes.txt";

    public static void main(String[] args) {
        System.out.println("Starting " + NUM_CLIENTS + " concurrent clients...");
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS);

        for (int i = 1; i <= NUM_CLIENTS; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    runClient(clientId);
                } catch (Exception e) {
                    System.err.println("Client " + clientId + " error: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Multi-client test finished.");
    }

    private static void runClient(int clientId) throws IOException {
        System.out.println("Client " + clientId + ": Connecting...");

        try (Socket socket = new Socket(HOSTNAME, PORT)) {

            // FIX: Un seul stream de base, partagé entre BufferedReader et DataInputStream
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(dataIn));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // 1. LIST
            System.out.println("Client " + clientId + ": Sending LIST...");
            out.println("LIST");
            String line;
            // FIX: Lire jusqu'à ligne vide (pas null)
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                System.out.println("Client " + clientId + " [LIST]: " + line);
            }

            // 2. GET
            System.out.println("Client " + clientId + ": Sending GET " + FILE_TO_GET + "...");
            out.println("GET " + FILE_TO_GET);
            receiveFile(FILE_TO_GET + "_client" + clientId, dataIn);

            // 3. QUIT
            System.out.println("Client " + clientId + ": Sending QUIT...");
            out.println("QUIT");
        }

        System.out.println("Client " + clientId + ": Test completed successfully.");
    }

    private static void receiveFile(String fileName, DataInputStream dataIn) throws IOException {
        long fileSize = dataIn.readLong();
        if (fileSize == -1) {
            System.err.println("File not found: " + fileName);
            return;
        }

        File downloadsDir = new File("downloads");
        if (!downloadsDir.exists()) downloadsDir.mkdirs();

        File file = new File(downloadsDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int bytesRead;
            while (totalRead < fileSize &&
                    (bytesRead = dataIn.read(buffer, 0,
                            (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("Client downloaded: " + file.getPath());
        }
    }
}
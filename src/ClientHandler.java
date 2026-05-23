import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final SharedState sharedState;
    private PrintWriter out;
    private BufferedReader in;
    private DataOutputStream dataOut;

    public ClientHandler(Socket socket, SharedState sharedState) {
        this.clientSocket = socket;
        this.sharedState = sharedState;
    }

    @Override
    public void run() {
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        // Critical Section: Modifying the shared state by adding a new client.
        // Synchronized in SharedState to ensure thread safety.
        sharedState.addClient(clientInfo);

        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            dataOut = new DataOutputStream(clientSocket.getOutputStream());

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] tokens = inputLine.split(" ", 2);
                String command = tokens[0].toUpperCase();

                sharedState.logOperation("Received command '" + command + "' from " + clientInfo);

                switch (command) {
                    case "LIST":
                        handleList();
                        break;
                    case "GET":
                        if (tokens.length > 1) {
                            handleGet(tokens[1]);
                        } else {
                            out.println("ERREUR : nom de fichier manquant.");
                        }
                        break;
                    case "QUIT":
                        return; // Exit the loop and close the connection.
                    default:
                        out.println("ERREUR : commande inconnue.");
                }
            }
        } catch (IOException e) {
            // This block catches I/O errors, such as an abrupt client disconnection.
            System.err.println("Error handling client " + clientInfo + ": " + e.getMessage());
        } finally {
            // This 'finally' block ensures that cleanup happens whether the loop exits
            // normally (QUIT) or due to an exception (abrupt disconnection).
            try {
                // Critical Section: Modifying shared state by removing a client.
                // Synchronized in SharedState to ensure thread safety.
                sharedState.removeClient(clientInfo);
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleList() {
        try (Stream<Path> stream = Files.list(Paths.get("shared"))) {
            String fileList = stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
            // The response is sent as a multi-line string, terminated by a final empty line from the client's perspective.
            out.println(fileList.isEmpty() ? "Aucun fichier disponible." : fileList);
            out.println(); // Signal the end of the list
        } catch (IOException e) {
            out.println("ERREUR : impossible de récupérer la liste des fichiers.");
            out.println(); // Signal the end of the error message
            e.printStackTrace();
        }
    }

    private void handleGet(String fileName) {
        Path path = Paths.get("shared", fileName);
        if (Files.exists(path) && Files.isRegularFile(path)) {
            try {
                long fileSize = Files.size(path);
                // Protocol: First, send the file size as a long.
                dataOut.writeLong(fileSize);

                // Protocol: Second, send the file content as raw bytes.
                try (InputStream fileIn = new FileInputStream(path.toFile())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileIn.read(buffer)) != -1) {
                        dataOut.write(buffer, 0, bytesRead);
                    }
                    dataOut.flush();
                }

                // Critical Section: Incrementing the download counter.
                // Synchronized in SharedState to prevent race conditions.
                sharedState.incrementDownloads();
                sharedState.logOperation("File '" + fileName + "' downloaded by " + clientSocket.getInetAddress().getHostAddress());

            } catch (IOException e) {
                // The client might disconnect during transfer, leading to an IOException.
                // We log this error instead of letting it crash the thread.
                System.err.println("Error sending file " + fileName + ": " + e.getMessage());
            }
        } else {
            try {
                dataOut.writeLong(-1);
            } catch (IOException e) {
                System.err.println("Error signaling file not found: " + e.getMessage());
            }
        }
    }
}

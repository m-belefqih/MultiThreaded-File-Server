import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class FileClient {
    public static void main(String[] args) {
        String hostname = "localhost"; // 127.0.0.1
        int port = 12346;

        try (Socket socket = new Socket(hostname, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             DataInputStream dataIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(dataIn));
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to file server successfully.");
            System.out.println("################ Commands ################");
            System.out.println("# LIST:           List available files   #");
            System.out.println("# GET <filename>: Download a file        #");
            System.out.println("# QUIT:           Disconnect from server #");
            System.out.println("##########################################");
            System.out.println("Enter your commands:");

            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = consoleReader.readLine();

                if (userInput == null || "QUIT".equalsIgnoreCase(userInput.trim())) {
                    if (userInput != null) {
                        out.println(userInput); // Send QUIT to server
                    }
                    System.out.println("Closing connection.");
                    break;
                }

                out.println(userInput);
                handleServerResponse(userInput, in, dataIn);
            }

        } catch (UnknownHostException ex) {
            System.err.println("Server not found: " + ex.getMessage());
        } catch (SocketException ex) {
            System.err.println("Connection to server lost: " + ex.getMessage());
        } catch (IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void handleServerResponse(String command, BufferedReader in, DataInputStream dataIn) throws IOException {
        String[] tokens = command.trim().split("\\s+", 2);
        String action = tokens[0].toUpperCase();

        switch (action) {
            case "LIST":
                System.out.println("--- Files on Server ---");
                String serverResponse;
                try {
                    while ((serverResponse = in.readLine()) != null && !serverResponse.isEmpty()) {
                        System.out.println(serverResponse);
                    }
                } catch (SocketException e) {
                    throw new IOException("Connection lost while reading file list.", e);
                }
                System.out.println("-----------------------");
                break;

            case "GET":
                if (tokens.length < 2) {
                    System.err.println("Error: Filename is required for GET command.");
                    return;
                }
                receiveFile(tokens[1], dataIn);
                break;

            default:
                // For unknown commands, the server sends a single-line error message.
                System.out.println("Server: " + in.readLine());
                break;
        }
    }

    private static void receiveFile(String fileName, DataInputStream dataIn) throws IOException {
        long fileSize = dataIn.readLong();

        if (fileSize == -1) {
            System.err.println("Server error: File not found or could not be sent.");
            return;
        }

        File downloadsDir = new File("downloads");
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            System.err.println("Error: Could not create downloads directory.");
            return;
        }

        File file = new File(downloadsDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            System.out.println("Downloading " + fileName + " (" + fileSize + " bytes)...");
            while (totalRead < fileSize && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            if (totalRead == fileSize) {
                System.out.println("Download complete: " + file.getPath());
            } else {
                System.err.println("Download failed: Incomplete file received.");
            }
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
    }
}

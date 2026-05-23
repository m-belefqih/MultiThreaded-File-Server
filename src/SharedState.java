import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SharedState {

    /**
     * Shared structures
     */
    private final List<String> connectedClients = new ArrayList<>(); // List of connected clients
    private final List<String> operationLog = new ArrayList<>(); // Log of operations performed by clients
    private int downloadCounter = 0; // downloaded files counter

    public synchronized void addClient(String clientInfo) {
        connectedClients.add(clientInfo);
        logOperation("Client connected: " + clientInfo);
    }

    public synchronized void removeClient(String clientInfo) {
        connectedClients.remove(clientInfo);
        logOperation("Client disconnected: " + clientInfo);
    }

    public synchronized void logOperation(String operation) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        operationLog.add(timestamp + " - " + operation);
    }

    public synchronized void incrementDownloads() {
        downloadCounter++;
    }

    public synchronized String getStats() {
        String clients = "Current connected clients: " + connectedClients.size();
        String downloads = "Total files downloaded: " + downloadCounter;
        
        return "------------Server Stats------------\n" +
               String.format("| %-32s |\n", clients) +
               String.format("| %-32s |\n", downloads) +
               "------------------------------------";
    }
}

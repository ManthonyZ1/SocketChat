import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 5000;
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> conversationLog = new ArrayList<>();

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(Server::saveConversation));
        System.out.println("Server starting on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)), true)) {

                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                writer.println("Server connected. Type messages to chat. Send EXIT to quit.");
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("EXIT".equalsIgnoreCase(line.trim())) {
                        conversationLog.add("Client: " + line);
                        writer.println("Server: Goodbye!");
                        conversationLog.add("Server: Goodbye!");
                        break;
                    }
                    String message = "Client: " + line;
                    conversationLog.add(message);
                    System.out.println(message);
                    String response = "Server: " + line;
                    conversationLog.add(response);
                    writer.println(response);
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            saveConversation();
        }
    }

    private static void saveConversation() {
        if (conversationLog.isEmpty()) {
            return;
        }
        try {
            Path logDir = Path.of("logs");
            Files.createDirectories(logDir);
            String filename = "server_chat_" + LocalDateTime.now().format(LOG_FORMAT) + ".txt";
            Path logFile = logDir.resolve(filename);
            Files.write(logFile, conversationLog, StandardCharsets.UTF_8);
            System.out.println("Conversation saved to: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save conversation: " + e.getMessage());
        }
    }
}

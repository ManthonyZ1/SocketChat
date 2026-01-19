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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static final int PORT = 5000;
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> conversationLog = Collections.synchronizedList(new ArrayList<>());
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger clientCounter = new AtomicInteger(1);

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(Server::saveConversation));
        System.out.println("Server starting on port " + PORT + "...");

        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, clientCounter.getAndIncrement());
                clients.add(handler);
                executor.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            executor.shutdown();
            saveConversation();
        }
    }

    private static void broadcast(String message) {
        conversationLog.add(message);
        System.out.println(message);
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    private static void removeClient(ClientHandler client) {
        clients.remove(client);
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

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int clientId;
        private PrintWriter writer;

        ClientHandler(Socket socket, int clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true)) {
                this.writer = out;
                String joinMessage = "User " + clientId + " joined the chat.";
                broadcast(joinMessage);
                out.println("Connected as User " + clientId + ". Type messages to chat. Send EXIT to quit.");
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("EXIT".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                    broadcast("User " + clientId + ": " + line);
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            } finally {
                removeClient(this);
                broadcast("User " + clientId + " left the chat.");
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        void sendMessage(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }
    }
}

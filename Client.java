import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Client extends JFrame {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final List<String> conversationLog = new ArrayList<>();

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public Client() {
        super("Socket Chat Client");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);

        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(event -> sendMessage());
        inputField.addActionListener(event -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeClient();
            }
        });

        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket(HOST, PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

            Thread listener = new Thread(this::listenForMessages, "server-listener");
            listener.setDaemon(true);
            listener.start();
        } catch (IOException e) {
            appendMessage("Unable to connect to server: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                appendMessage(line);
                conversationLog.add(line);
            }
        } catch (IOException e) {
            appendMessage("Connection closed.");
        } finally {
            saveConversation();
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty() || writer == null) {
            return;
        }
        writer.println(message);
        String formatted = "Client: " + message;
        appendMessage(formatted);
        conversationLog.add(formatted);
        inputField.setText("");
        if ("EXIT".equalsIgnoreCase(message)) {
            closeClient();
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + System.lineSeparator());
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void closeClient() {
        if (writer != null) {
            writer.println("EXIT");
        }
        saveConversation();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            appendMessage("Error closing socket: " + e.getMessage());
        }
    }

    private void saveConversation() {
        if (conversationLog.isEmpty()) {
            return;
        }
        try {
            Path logDir = Path.of("logs");
            Files.createDirectories(logDir);
            String filename = "client_chat_" + LocalDateTime.now().format(LOG_FORMAT) + ".txt";
            Path logFile = logDir.resolve(filename);
            Files.write(logFile, conversationLog, StandardCharsets.UTF_8);
            appendMessage("Conversation saved to: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            appendMessage("Failed to save conversation: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
        });
    }
}

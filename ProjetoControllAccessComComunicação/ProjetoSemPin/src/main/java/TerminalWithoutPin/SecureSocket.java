package TerminalWithoutPin;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.google.gson.*;

public class SecureSocket {
    private SSLSocket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean isConnected = false;
    private ExecutorService listenerExecutor;
    private Consumer<JsonObject> messageHandler;
    private Gson gson;
    
    // Constructor for client connection
    public SecureSocket(String host, int port, String keystorePath, String password) throws Exception {
        this.gson = new Gson();
        SSLContext sslContext = createSSLContext(keystorePath, password);
        SSLSocketFactory factory = sslContext.getSocketFactory();
        this.socket = (SSLSocket) factory.createSocket(host, port);
        initializeStreams();
    }
    
    // Constructor for server-side socket (when you accept a connection)
    public SecureSocket(SSLSocket acceptedSocket) throws IOException {
        this.gson = new Gson();
        this.socket = acceptedSocket;
        initializeStreams();
    }
    
    private void initializeStreams() throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.isConnected = true;
        this.listenerExecutor = Executors.newSingleThreadExecutor();
    }
    
    // Send a JSON object
    public void sendJson(JsonObject jsonObject) {
        if (!isConnected) {
            throw new IllegalStateException("Socket not connected");
        }
        writer.println(gson.toJson(jsonObject));
    }
    
    // Send any object as JSON
    public void sendJson(Object object) {
        if (!isConnected) {
            throw new IllegalStateException("Socket not connected");
        }
        writer.println(gson.toJson(object));
    }
    
    // Send a message (legacy method)
    public void send(String message) {
        if (!isConnected) {
            throw new IllegalStateException("Socket not connected");
        }
        writer.println(message);
    }
    
    // Receive a single JSON message (blocking)
    public JsonObject receiveJson() throws IOException {
        if (!isConnected) {
            throw new IllegalStateException("Socket not connected");
        }
        String jsonString = reader.readLine();
        if (jsonString == null) return null;
        return JsonParser.parseString(jsonString).getAsJsonObject();
    }
    
    // Receive a single message (blocking) - legacy method
    public String receive() throws IOException {
        if (!isConnected) {
            throw new IllegalStateException("Socket not connected");
        }
        return reader.readLine();
    }
    
    // Start listening for JSON messages asynchronously
    public void startJsonListening(Consumer<JsonObject> messageHandler) {
        this.messageHandler = messageHandler;
        listenerExecutor.submit(() -> {
            try {
                String message;
                while (isConnected && (message = reader.readLine()) != null) {
                    try {
                        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
                        messageHandler.accept(jsonObject);
                    } catch (JsonSyntaxException e) {
                        System.err.println("Invalid JSON received: " + message);
                    }
                }
            } catch (IOException e) {
                if (isConnected) {
                    System.err.println("Error reading JSON message: " + e.getMessage());
                }
            }
        });
    }
    
    // Start listening for messages asynchronously (legacy method)
    public void startListening(Consumer<String> messageHandler) {
        listenerExecutor.submit(() -> {
            try {
                String message;
                while (isConnected && (message = reader.readLine()) != null) {
                    messageHandler.accept(message);
                }
            } catch (IOException e) {
                if (isConnected) {
                    System.err.println("Error reading message: " + e.getMessage());
                }
            }
        });
    }
    
    // Stop listening
    public void stopListening() {
        if (listenerExecutor != null && !listenerExecutor.isShutdown()) {
            listenerExecutor.shutdown();
        }
    }
    
    // Check if connected
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    // Close the connection
    public void close() throws IOException {
        isConnected = false;
        stopListening();
        if (reader != null) reader.close();
        if (writer != null) writer.close();
        if (socket != null) socket.close();
    }
    
    // Get remote address info
    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
    
    // Helper method to create SSL context
    private static SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(keystorePath)) {
            keyStore.load(is, password.toCharArray());
        }
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, password.toCharArray());
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keyStore);
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        
        return sslContext;
    }
}
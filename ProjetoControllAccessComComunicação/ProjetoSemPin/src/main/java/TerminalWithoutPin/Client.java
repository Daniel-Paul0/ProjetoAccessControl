package TerminalWithoutPin;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

import com.google.gson.JsonObject;

public class Client {
    private SecureSocket socket;
    private String clientId;
    private String userId;
    private CardChannel cardChannel;
    private ResponseAPDU response;
    private GUITerminalWithoutPin gui;
    private CardTerminal terminal;
    private CountDownLatch authLatch;



    private static final byte[] APPLET_AID = {
        (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0A, 0x03
    };

    private static final byte INS_GET_ID = (byte) 0x02;
    private static final byte INS_VERIFY_PIN = (byte) 0x07;
    private static final byte INS_GET_ENCRYPTED_CHALLENGE = (byte) 0x09;
    private static final byte CLA = (byte) 0x80;

    public Client(String host, int port, String clientId, String userId) throws Exception {
        this.socket = new SecureSocket(host, port, "src/main/resources/keystore.jks", "password");
        this.clientId = clientId;
        this.userId = userId;
    

        socket.startJsonListening(this::handleServerMessage);

        System.out.println("[" + clientId + "] Connected to protocol server!");
    }

    private void handleServerMessage(JsonObject message) {
        try {
            System.out.println("[" + clientId + "] Received message: " + message.toString());

            String messageType = message.get("type").getAsString();

            switch (messageType) {
                case "challenge":
                    handleChallenge(message);
                    break;

                case "auth_success":
                    handleAuthSuccess(message);
                    break;

                case "error":
                    handleError(message);
                    break;

                case "auth_error":
                    System.out.println("ola");
                    handleAuthError(message);
                    break;

                default:
                    System.out.println("[" + clientId + "] Unknown message type: " + messageType);
                    System.out.println("Full message: " + message);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[" + clientId + "] Error handling message: " + e.getMessage());
        }
    }

    private void handleChallenge(JsonObject message) throws CardException {
  
        String challenge = message.get("challenge").getAsString();
        byte[] challengeBytes = Base64.getDecoder().decode(challenge);

        CommandAPDU getDigest= new CommandAPDU(CLA, INS_GET_ENCRYPTED_CHALLENGE, 0x00, 0x00, challengeBytes);
        this.response = this.cardChannel.transmit(getDigest);
        byte[] encryptedChallengeCard = response.getData();
        String encryptedHex = Base64.getEncoder().encodeToString(encryptedChallengeCard);

        

        JsonObject challengeMessage = new JsonObject();
        challengeMessage.addProperty("type", "challenge_response");
        challengeMessage.addProperty("digest", encryptedHex);
        System.out.println(challengeMessage.toString());
        sendMessage(challengeMessage);

    }

    private void handleAuthSuccess(JsonObject message) throws Exception{
        this.gui.showAccessAllowed();
        try {
            Thread.sleep(2000);  
        } catch (Exception e) {
        }
        this.gui.showMessage("Please Remove Card");
        removeCard(this.terminal);

        if (this.authLatch != null) {
            this.authLatch.countDown();        
        }
    }

    private void handleError(JsonObject message) throws Exception {
        System.out.println("ola");
        String error = message.get("auth_error").getAsString();
        this.gui.showMessage(error);
        try {
            Thread.sleep(2000);  
        } catch (Exception e) {
        }
        this.gui.showMessage("Please Remove Card");
        removeCard(this.terminal);

        if (this.authLatch != null) {
            this.authLatch.countDown();        
        }

    }

    private void handleAuthError(JsonObject message) throws Exception {
        gui.showAccessDenied(" Invalid Challenge");
        removeCard(terminal);

        if (this.authLatch != null) {
            this.authLatch.countDown();        
        }
    }

    private void sendMessage(JsonObject Message){
        socket.sendJson(Message);
    }

    public void init(GUITerminalWithoutPin gui) throws Exception {
        this.gui = gui;
        // initialize reader
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals.isEmpty()) {
            gui.showMessage("No Reader Found.");
            return;
        }
        this.terminal = terminals.get(0);

        while (true) {
            //initialize card and terminal 
            gui.awaitCard();

            Card card = initializeCard(this.terminal);
            this.cardChannel = card.getBasicChannel();

            // Seleciona applet
            CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID);
            this.response = cardChannel.transmit(select);

            // Gets Id from card
            CommandAPDU getId = new CommandAPDU(CLA, INS_GET_ID, 0x00, 0x00, 64);
            this.response = cardChannel.transmit(getId);
            System.out.println("ID recebido: " + new String(response.getData(), "UTF-8"));  
            String idRequest = bytesToHex(response.getData());

            System.out.println("[" + clientId + "] Sending auth_request for user: " + userId);
            JsonObject initMessage = new JsonObject();
            initMessage.addProperty("type", "auth_request");
            initMessage.addProperty("id", idRequest);

            this.authLatch = new CountDownLatch(1);
            sendMessage(initMessage);

            try {
                this.authLatch.await(); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public void close() throws Exception {
        socket.close();
        System.out.println("[" + clientId + "] Connection closed");
    }

    private static Card initializeCard(CardTerminal terminal) throws Exception {
        terminal.waitForCardPresent(0);
        return terminal.connect("*");
    }

    private static void removeCard(CardTerminal terminal) throws Exception {
        while (terminal.isCardPresent()) {
            try {
                Thread.sleep(2000);  
            } catch (Exception e) {
            }
        }
    }

        
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hexString) {
        int len = hexString.length();
        byte[] result = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int byteValue = Integer.parseInt(hexString.substring(i, i + 2), 16);
            result[i / 2] = (byte) byteValue;
        }
        return result;
    }
 }
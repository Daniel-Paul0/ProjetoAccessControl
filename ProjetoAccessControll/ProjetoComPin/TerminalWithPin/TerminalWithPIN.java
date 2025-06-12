package TerminalWithPIN;

import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.smartcardio.*;

public class TerminalWithPIN {

    private static final byte[] APPLET_AID = {
        (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0A, 0x03
    };

    private static final byte INS_GET_ID = (byte) 0x02;
    private static final byte INS_SET_AES_KEY = (byte) 0x03;
    private static final byte INS_VERIFY_PIN = (byte) 0x07;
    private static final byte INS_GET_ENCRYPTED_CHALLENGE = (byte) 0x09;
    private static final byte CLA = (byte) 0x80;

    public static void Start(GUITerminalWithPin gui) throws Exception {

        //generates AES KEY (Symmetric key)
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey secretKey = keyGen.generateKey();
        byte[] keyBytes = secretKey.getEncoded();

        // fake id and challenge for test
        String idCard = "1234";
        String challenge = "challenge";
        CardTerminal terminal;

        // Initialize hash function
        MessageDigest hashFunction = MessageDigest.getInstance("SHA-256");

        // initialize reader
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals.isEmpty()) {
            gui.showMessage("No Reader Found.");
            return;
        }
        terminal = terminals.get(0);

        while (true) {
            //initialize card and terminal 
            gui.awaitCard();

            Card card = initializeCard(terminal);
            CardChannel cardChannel = card.getBasicChannel();

            // Seleciona applet
            CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID);
            ResponseAPDU response = cardChannel.transmit(select);

            // verification for correct pin
            boolean pinVerify = false;

            for (int i = 0; i < 3; i++) {
                String PIN = gui.inputPIN(); 

                byte[] cardPIN = PIN.getBytes("UTF-8");
                CommandAPDU verifyPIN = new CommandAPDU(CLA, INS_VERIFY_PIN, 0x00, 0x00, cardPIN);
                response = cardChannel.transmit(verifyPIN);

                if (response.getSW() == 0x9000) {
                    pinVerify = true;
                    break;
                } else {
                    gui.showAccessDenied("Incorrect PIN");
                    Thread.sleep(2500); 
                }
            }

            if (!pinVerify) {
                gui.showAccessDenied("Too many attempts");
                Thread.sleep(2000); 
                gui.showMessage("Please Remove Card");
                removeCard(terminal);
                continue;
            }

            // Send Symmetric key to the card
            CommandAPDU setAESKey = new CommandAPDU(CLA, INS_SET_AES_KEY, 0x00, 0x00, keyBytes);
            response = cardChannel.transmit(setAESKey);

            // Gets Id from card
            CommandAPDU getId = new CommandAPDU(CLA, INS_GET_ID, 0x00, 0x00, 64);
            response = cardChannel.transmit(getId);
            String idRequest = new String(response.getData(), "UTF-8");

            if (!idRequest.equals(idCard)) {
                gui.showAccessDenied("This Card is NOT Allowed in this Door!!");
                removeCard(terminal);
                continue;
            }

            // Sends Challenge to the card
            byte[] challengeBytes = challenge.getBytes("UTF-8");

            //Manual Padding for encrypt algorytm 
            int ivSize = 16;
            int paddingLength = ivSize - (challengeBytes.length % ivSize);
            byte[] paddedInput = Arrays.copyOf(challengeBytes, challengeBytes.length + paddingLength);
            for (int i = challengeBytes.length; i < paddedInput.length; i++) {
                paddedInput[i] = (byte) paddingLength;
            }

            // Generates random iv
            byte[] iv = new byte[ivSize];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize AES Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            // Encripts
            byte[] encryptedChallenge = cipher.doFinal(paddedInput);

            // IV + encryptedChallenge
            byte[] challengeEncript = new byte[iv.length + encryptedChallenge.length];
            System.arraycopy(iv, 0, challengeEncript , 0, iv.length);
            System.arraycopy(encryptedChallenge, 0, challengeEncript , iv.length, encryptedChallenge.length);

            // Sends Challenge to the card
            CommandAPDU getDigest= new CommandAPDU(CLA, INS_GET_ENCRYPTED_CHALLENGE, 0x00, 0x00, challengeEncript);
            response = cardChannel.transmit(getDigest);

            byte[] encryptedChallengeCard = response.getData();

            // Gets iv and encrypted digest
            byte[] ivCard = Arrays.copyOfRange(encryptedChallengeCard, 0, 16);
            byte[] ciphertextCard = Arrays.copyOfRange(encryptedChallengeCard, 16, encryptedChallengeCard.length);
            
            byte[] digest = hashFunction.digest(paddedInput);

            // Decrypt Challenge
            SecretKeySpec keySpecCard = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpecCard = new IvParameterSpec(ivCard);

            Cipher aes = Cipher.getInstance("AES/CBC/NoPadding"); 
            aes.init(Cipher.DECRYPT_MODE, keySpecCard, ivSpecCard);
            byte[] decryptedChallenge = aes.doFinal(ciphertextCard);

            // Verify if digest are equal
            if(!Arrays.equals(digest, decryptedChallenge)){
              gui.showAccessDenied(" Invalid Challenge");
              removeCard(terminal);
              continue;
            }

            gui.showAccessAllowed();
            Thread.sleep(4000); 
            gui.showMessage("Please Remove Card");
            removeCard(terminal);
        }
    }

    private static Card initializeCard(CardTerminal terminal) throws Exception {
        terminal.waitForCardPresent(0);
        return terminal.connect("*");
    }

    private static void removeCard(CardTerminal terminal) throws Exception {
        while (terminal.isCardPresent()) {
            Thread.sleep(500);
        }
    }
}

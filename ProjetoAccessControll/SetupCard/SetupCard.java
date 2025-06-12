import java.util.List;
import java.util.Scanner;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.smartcardio.*;

public class SetupCard {

    private static final byte[] APPLET_AID = {
        (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0A, 0x03
    };
    private static final byte INS_SET_ID = (byte) 0x01;
    private static final byte INS_SET_AES_KEY = (byte) 0x03;
    private static final byte INS_SET_PIN = (byte) 0x08;

    private static final byte CLA = (byte) 0x80;

    public static void main(String[] args) throws Exception {

        //generates AES KEY (Symmetric key)
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); 

        SecretKey secretKey = keyGen.generateKey();

        byte[] keyBytes = secretKey.getEncoded();
        
        int function;
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        Card card = initializeCard();
        if (card == null){
            System.out.println("Sistema Encerrado devido a falta de cartão ou leitor");
            scanner.close();
            return;
        }

        CardChannel cardChannel = card.getBasicChannel();

        // Select applet
        CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID);
        ResponseAPDU response = cardChannel.transmit(select);
        checkStatus(response, "Selecionar applet");
            
        // Send Symmetric key to the card
        CommandAPDU setAESKey = new CommandAPDU(CLA, INS_SET_AES_KEY, 0x00, 0x00, keyBytes);
        response = cardChannel.transmit(setAESKey);
        checkStatus(response, "Enviar AESKEY");
     
        while (running) { 
            
            System.out.println("/--------Menu-----------/");
            System.out.println("1. Send ID");
            System.out.println("2. Send PIN");
            System.out.println("3. Exit");

            function = scanner.nextInt();
            scanner.nextLine();
        

            switch(function){
                case 1:
                    System.out.print("Input ID: ");
                    String id = scanner.nextLine();
                    System.out.println("Sending ID to the card.");
                    // Send ID
                    byte[] cardId = id.getBytes("UTF-8");
                    CommandAPDU setId = new CommandAPDU(CLA, INS_SET_ID, 0x00, 0x00, cardId);
                    response = cardChannel.transmit(setId);
                    checkStatus(response, "Enviar ID");
                    break;

                case 2:
                    System.out.print("Input PIN:  ");  
                    String pin = scanner.nextLine();
                    System.out.println("Sending PIN to the card.");
                    // Send PIN
                    byte[] cardPIN = pin.getBytes("UTF-8");
                    CommandAPDU setPIN= new CommandAPDU(CLA, INS_SET_PIN, 0x00, 0x00, cardPIN);
                    response = cardChannel.transmit(setPIN);
                    checkStatus(response, "Enviar PIN");     
                    break;

                case 3: 
                    System.out.println("Fim de Sessão.");
                    card.disconnect(false);
                    running = false;
                    System.out.println("Por favor, remova o cartão...");
                    removeCard(card);
                    scanner.close();
                    break;
                    
                default:
                    System.out.println("Opção Inválida!");
            }
        }
        scanner.close();
    }
    static Card initializeCard() throws Exception {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
            System.out.println("Nenhum leitor encontrado.");
            return null;
            }

            CardTerminal terminal = terminals.get(0);
            System.out.println("Usando leitor: " + terminal.getName());

            // Espera indefinidamente até que um cartão esteja presente
            System.out.println("Por favor insira o cartão...");
            terminal.waitForCardPresent(0); // 0 = espera infinita

            // Conecta ao cartão
            Card card = terminal.connect("*");
            System.out.println("Cartão conectado!");

            return card;

        } catch (CardException e) {
            System.out.println("Erro ao conectar ao leitor/cartão: " + e.getMessage());
        }

        return null;
    }   

    private static void checkStatus(ResponseAPDU response, String op) {
        if (response.getSW() != 0x9000) {
            throw new RuntimeException(op + " falhou com status: " + Integer.toHexString(response.getSW()));
        } else {
            System.out.println(op + " OK");
        }
    }

    public static void removeCard(Card card) {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                System.out.println("Nenhum terminal encontrado.");
                return;
            }
            CardTerminal terminal = terminals.get(0);
            while (terminal.isCardPresent()) {
                Thread.sleep(500); 
            }

        } catch (Exception e) {
            System.err.println("Erro ao aguardar remoção do cartão: " + e.getMessage());
        }
    }


}

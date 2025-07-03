package TerminalWithoutPin;

public class main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GUITerminalWithoutPin gui = new GUITerminalWithoutPin();
            new Thread(() -> {
                try {
                    String testUserId = "2";
                    Client client = new Client("192.168.1.84", 12345, "24", testUserId);
                    Thread.sleep(1000);
                    client.init(gui);
                } catch (Exception e) {
                    gui.showMessage("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        });
    }
}

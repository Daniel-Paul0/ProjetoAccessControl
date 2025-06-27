package TerminalWithoutPin;

public class Main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GUITerminalWithoutPin gui = new GUITerminalWithoutPin();
            new Thread(() -> {
                try {
                    String testUserId = "2";
                    Client client = new Client("localhost", 12345, "TestClient", testUserId);
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

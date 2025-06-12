package TerminalWithPIN;

public class Main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GUITerminalWithPin gui = new GUITerminalWithPin();
            new Thread(() -> {
                try {
                    TerminalWithPIN.Start(gui);
                } catch (Exception e) {
                    gui.showMessage("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        });
    }
}

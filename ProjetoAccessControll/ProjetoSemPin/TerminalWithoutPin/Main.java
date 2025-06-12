package TerminalWithoutPIN;

public class Main {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            GUITerminalWithoutPin gui = new GUITerminalWithoutPin();
            new Thread(() -> {
                try {
                    TerminalWithoutPIN.iniciar(gui);
                } catch (Exception e) {
                    gui.showMessage("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        });
    }
}

package TerminalWithoutPIN;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class GUITerminalWithoutPin extends JFrame {

    private JLabel statusLabel;
    private JButton exitButton;

    public GUITerminalWithoutPin(){
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Main Layout
        setLayout(new BorderLayout());

        // Status Layout
        statusLabel = new JLabel("Please Insert Card...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 64));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.GRAY);
        statusLabel.setForeground(Color.WHITE);
        add(statusLabel, BorderLayout.CENTER);

        // Top Painel with Exit Button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.setOpaque(false); 

        exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Arial", Font.BOLD, 18));
        exitButton.setBackground(Color.RED.darker());
        exitButton.setForeground(Color.WHITE);
        exitButton.setFocusPainted(false);
        exitButton.setPreferredSize(new Dimension(100, 40));
        exitButton.addActionListener(e -> {
            System.exit(0);
        });

        topPanel.add(exitButton);
        add(topPanel, BorderLayout.NORTH);

        setVisible(true);

        // Full Screen Mode
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(this);
        }

        // Esc Button To exit
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ESCAPE){
                    System.exit(0);
                }
            }
        });

        setFocusable(true);
        requestFocusInWindow();
    }

    public void showAccessAllowed() {
        statusLabel.setText("Access Allowed");
        statusLabel.setBackground(Color.GREEN.darker());
    }

    public void showAccessDenied(String reason) {
        statusLabel.setText("Access Denied (" + reason + ")");
        statusLabel.setBackground(Color.RED.darker());
    }

    public void showMessage(String menssage) {
        statusLabel.setText(menssage);
        statusLabel.setBackground(Color.ORANGE.darker());
    }

    public void awaitCard() {
        statusLabel.setText("Please Insert Card...");
        statusLabel.setBackground(Color.GRAY);
    }
}

package TerminalWithPin;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class GUITerminalWithPin extends JFrame {

    private JLabel statusLabel;
    private JButton exitButton;
    private JPasswordField pinField;
    private JButton  confirmationButton;
    private String inputPin;
    private final Object pinLock = new Object();

    public GUITerminalWithPin(){
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Main Layout
        setLayout(new BorderLayout());

        // Central Status Label
        statusLabel = new JLabel("Please Insert Card...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 64));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.GRAY);
        statusLabel.setForeground(Color.WHITE);
        add(statusLabel, BorderLayout.CENTER);

        // Top painel for exit button
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

        // Pin Field
        pinField = new JPasswordField(10);
        pinField.setFont(new Font("Arial", Font.PLAIN, 32));
        confirmationButton = new JButton("Confirm PIN");
        confirmationButton.setFont(new Font("Arial", Font.BOLD, 24));

        JPanel pinPanel = new JPanel();
        pinPanel.setBackground(Color.WHITE);
        pinPanel.add(new JLabel("Enter PIN: "));
        pinPanel.add(pinField);
        pinPanel.add(confirmationButton);
        add(pinPanel, BorderLayout.SOUTH);
        pinPanel.setVisible(false);

        confirmationButton.addActionListener(e -> {
            synchronized (pinLock) {
                inputPin = new String(pinField.getPassword());
                pinPanel.setVisible(false);
                pinField.setText("");
                pinLock.notify(); 
            }
        });


        setVisible(true);

        // Fullscreen Mode
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(this);
        }

        // Esc shortcut to exit
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

    public String inputPIN() {
        JPanel pinPanel = (JPanel) getContentPane().getComponent(2); // pin panel
        pinPanel.setVisible(true);
        statusLabel.setText("Please enter your PIN");
        statusLabel.setBackground(Color.BLUE.darker());

        synchronized (pinLock) {
            try {
                pinLock.wait(); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return inputPin;
    }
}

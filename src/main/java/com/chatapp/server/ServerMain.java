package com.chatapp.server;

import com.chatapp.common.config.AppConfig;
import com.chatapp.server.ui.ServerWindow;
import com.formdev.flatlaf.FlatDarkLaf;
import com.chatapp.common.security.TextMessageCrypto;

import javax.swing.*;

/**
 * Điểm khởi chạy chính của Server (NetBeans: Chuột phải -> Run File).
 */
public class ServerMain {
    public static void main(String[] args) {
        boolean headless = java.awt.GraphicsEnvironment.isHeadless() || java.util.Arrays.asList(args).contains("--headless");
        try { AppConfig.validate(); TextMessageCrypto.validateKey(); }
        catch (SecurityException e) {
            System.err.println(e.getMessage());
            if (!headless) JOptionPane.showMessageDialog(null, e.getMessage(), "Cấu hình ứng dụng", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (headless) {
            ChatServer server = new ChatServer(AppConfig.serverPort());
            try { server.start(); }
            catch (java.io.IOException | SecurityException e) {
                System.err.println("Không thể khởi động Server: " + e.getMessage());
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            return;
        }
        // Áp dụng giao diện hiện đại FlatDarkLaf
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }

        SwingUtilities.invokeLater(() -> {
            ServerWindow window = new ServerWindow();
            window.setVisible(true);
        });
    }
}

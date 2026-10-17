package com.chatapp.server;

import com.chatapp.server.ui.ServerWindow;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Điểm khởi chạy chính của Server (NetBeans: Chuột phải -> Run File).
 */
public class ServerMain {
    public static void main(String[] args) {
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

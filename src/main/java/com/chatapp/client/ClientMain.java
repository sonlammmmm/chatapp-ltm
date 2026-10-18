/**
 * ClientMain.java — Khởi chạy Client, thiết lập giao diện và mở màn hình đăng nhập.
 */
package com.chatapp.client;

import com.chatapp.common.config.AppConfig;
import com.chatapp.client.ui.LoginRegisterDialog;
import com.chatapp.client.ui.MainChatWindow;
import com.chatapp.common.model.User;
import com.chatapp.common.security.TextMessageCrypto;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.Frame;

/**
 * Điểm khởi chạy chính của Client (NetBeans: Chuột phải -> Run File).
 * Có thể mở nhiều cửa sổ Client cùng lúc để kiểm thử truyền nhận tin nhắn và tệp tin P2P.
 */
public class ClientMain {
    /** Điểm vào chương trình, khởi tạo ứng dụng tương ứng. */
    public static void main(String[] args) {
        initTheme();
        launchClient(null, true);
    }

    /** Thiết lập giao diện Swing dùng cho ứng dụng. */
    public static void initTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }

    /** Mở luồng đăng nhập và cửa sổ chat Client. */
    public static void launchClient(Frame owner, boolean exitOnCancel) {
        try { AppConfig.validate(); TextMessageCrypto.validateKey(); }
        catch (SecurityException e) {
            JOptionPane.showMessageDialog(owner, e.getMessage(), "Cấu hình ứng dụng", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            LoginRegisterDialog loginDialog = new LoginRegisterDialog(owner, client);
            loginDialog.setVisible(true);

            if (loginDialog.isLoginSuccessful()) {
                User currentUser = client.getCurrentUser();
                MainChatWindow mainWindow = new MainChatWindow(client, currentUser);
                mainWindow.setVisible(true);
            } else {
                client.disconnect();
                if (exitOnCancel) {
                    System.exit(0);
                }
            }
        });
    }
}

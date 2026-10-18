package com.chatapp.client.ui;

import com.chatapp.common.config.AppConfig;
import com.chatapp.client.ChatClient;
import com.chatapp.client.ClientListener;
import com.chatapp.common.model.ChatMessage;
import com.chatapp.common.model.ChatRoom;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * Giao diện Đăng nhập / Đăng ký tài khoản kèm phím tắt đăng nhập nhanh cho 3 thành viên nhóm.
 */
public class LoginRegisterDialog extends JDialog implements ClientListener {
    private final ChatClient client;
    private JTextField hostField;
    private JTextField portField;

    // Login tab
    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JButton btnLogin;

    // Register tab
    private JTextField regUserField;
    private JPasswordField regPassField;
    private JTextField regNameField;
    private JButton btnRegister;

    private JLabel lblStatus;
    private javax.swing.Timer requestTimer;
    private boolean busy;
    private boolean isLoginSuccessful = false;

    public LoginRegisterDialog(Frame owner, ChatClient client) {
        super(owner, "ChatApp | CLIENT — Đăng nhập", true);
        this.client = client;
        this.client.setListener(this);

        setSize(540, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        initUI();
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Header: Cấu hình kết nối Server
        JPanel serverPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        serverPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình kết nối Máy chủ"));

        serverPanel.add(new JLabel("Địa chỉ IP Server:"));
        hostField = new JTextField(AppConfig.serverHost());
        serverPanel.add(hostField);

        serverPanel.add(new JLabel("Cổng (Port):"));
        portField = new JTextField(String.valueOf(AppConfig.serverPort()));
        serverPanel.add(portField);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        JLabel title = new JLabel("CLIENT  /  Kết nối để trò chuyện");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(100, 180, 255));
        header.add(title, BorderLayout.NORTH);
        header.add(serverPanel, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>Nhập IP LAN / Radmin VPN hoặc địa chỉ public của máy chạy Server.<br>127.0.0.1 chỉ dùng trên máy Server. Máy chủ cần được khởi động trước.</html>");
        header.add(hint, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        // Center: Tab Đăng nhập & Đăng ký
        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB ĐĂNG NHẬP
        JPanel loginPanel = new JPanel(new BorderLayout(10, 10));
        loginPanel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel loginForm = new JPanel(new GridLayout(2, 2, 8, 12));
        loginForm.add(new JLabel("Tên đăng nhập:"));
        loginUserField = new JTextField();
        loginForm.add(loginUserField);

        loginForm.add(new JLabel("Mật khẩu:"));
        loginPassField = new JPasswordField();
        loginForm.add(loginPassField);

        btnLogin = new JButton("Đăng nhập");
        btnLogin.setBackground(new Color(0, 78, 160));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogin.addActionListener(e -> performLogin());

        JPanel loginCenter = new JPanel(new BorderLayout(10, 10));
        loginCenter.add(loginForm, BorderLayout.NORTH);
        // Demo credentials are intentionally absent from the normal sign-in flow.
        loginCenter.add(btnLogin, BorderLayout.SOUTH);

        loginPanel.add(loginCenter, BorderLayout.CENTER);
        tabbedPane.addTab("Đăng nhập", loginPanel);

        // TAB ĐĂNG KÝ
        JPanel regPanel = new JPanel(new BorderLayout(10, 10));
        regPanel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel regForm = new JPanel(new GridLayout(3, 2, 8, 12));
        regForm.add(new JLabel("Tên đăng nhập:"));
        regUserField = new JTextField();
        regForm.add(regUserField);

        regForm.add(new JLabel("Mật khẩu:"));
        regPassField = new JPasswordField();
        regForm.add(regPassField);

        regForm.add(new JLabel("Họ và tên:"));
        regNameField = new JTextField();
        regForm.add(regNameField);

        btnRegister = new JButton("Đăng ký tài khoản");
        btnRegister.setBackground(new Color(40, 167, 69));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegister.addActionListener(e -> performRegister());

        regPanel.add(regForm, BorderLayout.NORTH);
        regPanel.add(btnRegister, BorderLayout.SOUTH);
        tabbedPane.addTab("Đăng ký mới", regPanel);

        tabbedPane.addChangeListener(e -> getRootPane().setDefaultButton(tabbedPane.getSelectedIndex() == 0 ? btnLogin : btnRegister));
        content.add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        lblStatus = new JLabel("Sẵn sàng kết nối.");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(190, 200, 215));
        content.add(lblStatus, BorderLayout.SOUTH);

        setContentPane(new JScrollPane(content));
        getRootPane().setDefaultButton(btnLogin);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(600, screen.width - 60), Math.min(660, screen.height - 80));
        setLocationRelativeTo(getOwner());
    }

    private boolean ensureConnected() {
        if (client.isConnected()) return true;

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Cổng Server phải là số nguyên!", "Lỗi", JOptionPane.ERROR_MESSAGE));
            return false;
        }

        try {
            SwingUtilities.invokeLater(() -> lblStatus.setText("Đang kết nối tới " + host + ":" + port + "..."));
            client.connect(host, port);
            return true;
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Không thể kết nối tới Server: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Không thể kết nối đến máy chủ tại " + host + ":" + port +
                        "\nVui lòng đảm bảo Server đã được bấm Khởi động!", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            });
            return false;
        }
    }

    private void performLogin() {
        if (busy) return;
        String username = loginUserField.getText().trim();
        String password = new String(loginPassField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        lblStatus.setText("Đang kết nối tới máy chủ...");

        new Thread(() -> {
            boolean connected = ensureConnected();
            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable() || !busy) return;
                if (!connected) setBusy(false);
                if (connected) {
                    lblStatus.setText("Đang xác thực thông tin đăng nhập...");
                    client.login(username, password);
                }
            });
        }, "LoginConnectThread").start();
    }

    private void performRegister() {
        if (busy) return;
        String username = regUserField.getText().trim();
        String password = new String(regPassField.getPassword()).trim();
        String fullName = regNameField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ các thông tin đăng ký!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        lblStatus.setText("Đang kết nối tới máy chủ...");

        new Thread(() -> {
            boolean connected = ensureConnected();
            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable() || !busy) return;
                if (!connected) setBusy(false);
                if (connected) {
                    lblStatus.setText("Đang tạo tài khoản mới...");
                    client.register(username, password, fullName);
                }
            });
        }, "RegisterConnectThread").start();
    }

    public void requireUser(String username) {
        loginUserField.setText(username);
        loginUserField.setEditable(false);
        btnRegister.setVisible(false);
    }

    private void setBusy(boolean value) {
        busy = value;
        btnLogin.setEnabled(!value);
        btnRegister.setEnabled(!value);
        hostField.setEnabled(!value);
        portField.setEnabled(!value);
        loginUserField.setEnabled(!value);
        loginPassField.setEnabled(!value);
        regUserField.setEnabled(!value);
        regPassField.setEnabled(!value);
        regNameField.setEnabled(!value);
        if (requestTimer != null) requestTimer.stop();
        if (value) {
            requestTimer = new javax.swing.Timer(AppConfig.requestTimeout(), e -> {
                client.disconnect();
                setBusy(false);
                lblStatus.setText("Máy chủ chưa phản hồi. Vui lòng thử lại.");
            });
            requestTimer.setRepeats(false);
            requestTimer.start();
        }
    }

    @Override public void dispose() {
        if (requestTimer != null) requestTimer.stop();
        super.dispose();
    }

    public boolean isLoginSuccessful() {
        return isLoginSuccessful;
    }

    // Callbacks từ ChatClient
    @Override
    public void onLoginSuccess(User user) {
        SwingUtilities.invokeLater(() -> {
            setBusy(false);
            isLoginSuccessful = true;
            dispose(); // Đóng form đăng nhập, MainChatWindow sẽ mở
        });
    }

    @Override
    public void onLoginFail(String reason) {
        SwingUtilities.invokeLater(() -> {
            setBusy(false);
            lblStatus.setText("Đăng nhập thất bại: " + reason);
            JOptionPane.showMessageDialog(this, reason, "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            setBusy(false);
            lblStatus.setText(message);
            if (success) {
                JOptionPane.showMessageDialog(this, message, "Đăng ký thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, message, "Đăng ký thất bại", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public void onUserListUpdated(List<User> users) {}
    @Override
    public void onRoomListUpdated(List<ChatRoom> rooms) {}
    @Override
    public void onMessageReceived(ChatMessage message) {}
    @Override
    public void onMessageStatusUpdated(String messageId, ChatMessage.Status status) {}
    @Override
    public void onUserTyping(String sender, String target, boolean typing) {}
    @Override
    public void onFileTransferRequested(FileTransferRequest request) {}
    @Override
    public void onFileTransferAccepted(FileTransferRequest request) {}
    @Override
    public void onFileTransferRejected(FileTransferRequest request) {}
    @Override
    public void onConnectionLost() {
        SwingUtilities.invokeLater(() -> { setBusy(false); lblStatus.setText("Mất kết nối tới máy chủ. Vui lòng thử lại."); });
    }
}

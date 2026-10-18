package com.chatapp.client.ui;

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
 * Giao diện Đăng nhập / Đăng ký tài khoản kèm phím tắt đăng nhập nhanh cho 4 thành viên nhóm.
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
    private boolean isLoginSuccessful = false;

    public LoginRegisterDialog(Frame owner, ChatClient client) {
        super(owner, "ChatApp | CLIENT — Đăng nhập", true);
        this.client = client;
        this.client.setListener(this);

        setSize(540, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Header: Cấu hình kết nối Server
        JPanel serverPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        serverPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình kết nối Máy chủ TCP"));

        serverPanel.add(new JLabel("Địa chỉ IP Server:"));
        hostField = new JTextField("127.0.0.1");
        serverPanel.add(hostField);

        serverPanel.add(new JLabel("Cổng (Port):"));
        portField = new JTextField("8888");
        serverPanel.add(portField);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        JLabel title = new JLabel("CLIENT  /  Kết nối để trò chuyện");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(100, 180, 255));
        header.add(title, BorderLayout.NORTH);
        header.add(serverPanel, BorderLayout.CENTER);
        JLabel hint = new JLabel("<html>Cùng máy: 127.0.0.1. Khác máy: nhập IP LAN của máy chạy Server.<br>Máy chủ cần được khởi động trước khi đăng nhập.</html>");
        header.add(hint, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        // Center: Tab Đăng nhập & Đăng ký
        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB ĐĂNG NHẬP
        JPanel loginPanel = new JPanel(new BorderLayout(10, 10));
        loginPanel.setBorder(new EmptyBorder(15, 10, 10, 10));

        JPanel loginForm = new JPanel(new GridLayout(2, 2, 8, 12));
        loginForm.add(new JLabel("Tên đăng nhập:"));
        loginUserField = new JTextField("giahuy");
        loginForm.add(loginUserField);

        loginForm.add(new JLabel("Mật khẩu:"));
        loginPassField = new JPasswordField("123456");
        loginForm.add(loginPassField);

        btnLogin = new JButton("Đăng nhập");
        btnLogin.setBackground(new Color(0, 122, 255));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogin.addActionListener(e -> performLogin());

        // Phím tắt đăng nhập nhanh cho 4 thành viên
        JPanel quickLoginPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        quickLoginPanel.setBorder(BorderFactory.createTitledBorder("Đăng nhập nhanh (Test tài khoản 4 SV)"));

        JButton btnHuy = new JButton("1. Gia Huy");
        btnHuy.addActionListener(e -> setQuickUser("giahuy", "123456"));

        JButton btnLam = new JButton("2. Sơn Lâm");
        btnLam.addActionListener(e -> setQuickUser("sonlam", "123456"));

        JButton btnDu = new JButton("3. Phong Dư");
        btnDu.addActionListener(e -> setQuickUser("phongdu", "123456"));

        JButton btnNgochuy = new JButton("4. Ngọc Huy");
        btnNgochuy.addActionListener(e -> setQuickUser("ngochuy", "123456"));

        quickLoginPanel.add(btnHuy);
        quickLoginPanel.add(btnLam);
        quickLoginPanel.add(btnDu);
        quickLoginPanel.add(btnNgochuy);

        JPanel loginCenter = new JPanel(new BorderLayout(10, 10));
        loginCenter.add(loginForm, BorderLayout.NORTH);
        loginCenter.add(quickLoginPanel, BorderLayout.CENTER);
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

        content.add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        lblStatus = new JLabel("Sẵn sàng kết nối.");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(100, 110, 125));
        content.add(lblStatus, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void setQuickUser(String user, String pass) {
        loginUserField.setText(user);
        loginPassField.setText(pass);
        performLogin();
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
                JOptionPane.showMessageDialog(this, "Không thể kết nối đến Máy chủ TCP Socket tại " + host + ":" + port +
                        "\nVui lòng đảm bảo Server đã được bấm Khởi động!", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            });
            return false;
        }
    }

    private void performLogin() {
        String username = loginUserField.getText().trim();
        String password = new String(loginPassField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnLogin.setEnabled(false);
        lblStatus.setText("Đang kết nối tới máy chủ TCP...");

        new Thread(() -> {
            boolean connected = ensureConnected();
            SwingUtilities.invokeLater(() -> {
                btnLogin.setEnabled(true);
                if (connected) {
                    lblStatus.setText("Đang xác thực thông tin đăng nhập...");
                    client.login(username, password);
                }
            });
        }, "LoginConnectThread").start();
    }

    private void performRegister() {
        String username = regUserField.getText().trim();
        String password = new String(regPassField.getPassword()).trim();
        String fullName = regNameField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ các thông tin đăng ký!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnRegister.setEnabled(false);
        lblStatus.setText("Đang kết nối tới máy chủ TCP...");

        new Thread(() -> {
            boolean connected = ensureConnected();
            SwingUtilities.invokeLater(() -> {
                btnRegister.setEnabled(true);
                if (connected) {
                    lblStatus.setText("Đang tạo tài khoản mới...");
                    client.register(username, password, fullName);
                }
            });
        }, "RegisterConnectThread").start();
    }

    public boolean isLoginSuccessful() {
        return isLoginSuccessful;
    }

    // Callbacks từ ChatClient
    @Override
    public void onLoginSuccess(User user) {
        SwingUtilities.invokeLater(() -> {
            isLoginSuccessful = true;
            dispose(); // Đóng form đăng nhập, MainChatWindow sẽ mở
        });
    }

    @Override
    public void onLoginFail(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Đăng nhập thất bại: " + reason);
            JOptionPane.showMessageDialog(this, reason, "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
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
        SwingUtilities.invokeLater(() -> lblStatus.setText("Mất kết nối tới máy chủ!"));
    }
}

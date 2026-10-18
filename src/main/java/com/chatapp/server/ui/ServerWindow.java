package com.chatapp.server.ui;

import com.chatapp.client.ClientMain;
import com.chatapp.common.model.User;
import com.chatapp.server.ChatServer;
import com.chatapp.server.db.DatabaseManager;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * Giao diện quản trị Máy chủ TCP Socket (Server GUI).
 * Tích hợp nút khởi chạy nhanh Đăng nhập Client và Đăng ký tài khoản mới trực tiếp trên Server.
 */
public class ServerWindow extends JFrame {
    private ChatServer server;
    private JTextField portField;
    private JButton btnToggle;
    private JLabel statusLabel;
    private JTextArea logArea;
    private DefaultTableModel userTableModel;
    private DefaultTableModel dbUserTableModel;
    private DefaultListModel<String> roomListModel;

    public ServerWindow() {
        setTitle("ChatApp | SERVER — Quản trị máy chủ");
        setSize(1020, 680);
        setMinimumSize(new Dimension(850, 520));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Top Control Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(30, 35, 45));

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 4));
        heading.setOpaque(false);
        JLabel title = new JLabel("SERVER  /  Quản trị máy chủ");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        heading.add(title);
        JLabel description = new JLabel("Khởi động máy chủ, theo dõi kết nối và nhật ký. Mở Client riêng để nhắn tin.");
        description.setForeground(new Color(200, 205, 215));
        heading.add(description);
        topPanel.add(heading, BorderLayout.NORTH);

        // Bên trái thanh điều khiển: Cổng & Nút Start/Stop Server
        JPanel leftControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        leftControl.setOpaque(false);

        JLabel lblPort = new JLabel("Cổng lắng nghe (Port):");
        lblPort.setForeground(Color.WHITE);
        lblPort.setFont(new Font("Segoe UI", Font.BOLD, 13));

        portField = new JTextField("8888", 5);
        portField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        btnToggle = new JButton("Khởi động Server");
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnToggle.setBackground(new Color(40, 167, 69));
        btnToggle.setForeground(Color.WHITE);
        btnToggle.setFocusPainted(false);
        btnToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnToggle.addActionListener(e -> toggleServer());

        statusLabel = new JLabel("● Trạng thái: Chưa hoạt động");
        statusLabel.setForeground(new Color(220, 53, 69));
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        leftControl.add(lblPort);
        leftControl.add(portField);
        leftControl.add(btnToggle);
        leftControl.add(statusLabel);
        topPanel.add(leftControl, BorderLayout.CENTER);

        // Bên phải thanh điều khiển: Nút Đăng nhập Client & Nút Đăng ký tài khoản
        JPanel rightControl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        rightControl.setOpaque(false);

        JButton btnLoginClient = new JButton("Mở Client trên máy này");
        btnLoginClient.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLoginClient.setBackground(new Color(0, 122, 255));
        btnLoginClient.setForeground(Color.WHITE);
        btnLoginClient.setFocusPainted(false);
        btnLoginClient.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLoginClient.setToolTipText("Khởi chạy ngay một cửa sổ Client Đăng nhập để nhắn tin & truyền file");
        btnLoginClient.addActionListener(e -> openClientLogin());

        JButton btnRegisterUser = new JButton("➕ Đăng ký tài khoản");
        btnRegisterUser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegisterUser.setBackground(new Color(55, 65, 81)); // Đồng bộ màu trung tính hiện đại
        btnRegisterUser.setForeground(Color.WHITE);
        btnRegisterUser.setFocusPainted(false);
        btnRegisterUser.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegisterUser.setToolTipText("Mở form đăng ký thêm tài khoản mới vào cơ sở dữ liệu");
        btnRegisterUser.addActionListener(e -> openRegisterDialog());

        rightControl.add(btnLoginClient);
        rightControl.add(btnRegisterUser);
        topPanel.add(rightControl, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // Center SplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(380);
        splitPane.setResizeWeight(0.38);

        // Left Panel: Online Users, Rooms & Database Users
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(new EmptyBorder(5, 10, 5, 5));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Tab 1: Người dùng trực tuyến (Online)
        userTableModel = new DefaultTableModel(new String[]{"Tài khoản", "Địa chỉ IP"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable userTable = new JTable(userTableModel);
        userTable.setRowHeight(24);
        tabbedPane.addTab("Người dùng Online", new JScrollPane(userTable));

        // Tab 2: Phòng chat
        roomListModel = new DefaultListModel<>();
        roomListModel.addElement("Sảnh Chung (lobby)");
        roomListModel.addElement("Nhóm 03 - Đồ án LTM (nhom03)");
        JList<String> roomList = new JList<>(roomListModel);
        tabbedPane.addTab("Phòng Chat Nhóm", new JScrollPane(roomList));

        // Tab 3: Danh sách tất cả tài khoản trong Database
        JPanel dbUserPanel = new JPanel(new BorderLayout(5, 5));
        dbUserTableModel = new DefaultTableModel(new String[]{"Tài khoản", "Họ và tên"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable dbUserTable = new JTable(dbUserTableModel);
        dbUserTable.setRowHeight(24);
        dbUserPanel.add(new JScrollPane(dbUserTable), BorderLayout.CENTER);

        JPanel dbUserActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton btnRefreshDb = new JButton("🔄 Làm mới");
        btnRefreshDb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRefreshDb.addActionListener(e -> updateDbUserTable());
        JButton btnAddDbUser = new JButton("+ Đăng ký mới");
        btnAddDbUser.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnAddDbUser.addActionListener(e -> openRegisterDialog());
        dbUserActionPanel.add(btnRefreshDb);
        dbUserActionPanel.add(btnAddDbUser);
        dbUserPanel.add(dbUserActionPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Tài khoản CSDL", dbUserPanel);

        leftPanel.add(tabbedPane, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        // Right Panel: Nhật ký hoạt động (Logs)
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new EmptyBorder(5, 5, 5, 10));

        JPanel logHeader = new JPanel(new BorderLayout());
        JLabel lblLog = new JLabel("Nhật ký hệ thống (Real-time TCP Socket Logs):");
        lblLog.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnClearLog = new JButton("Xóa log");
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearLog.addActionListener(e -> logArea.setText(""));
        logHeader.add(lblLog, BorderLayout.WEST);
        logHeader.add(btnClearLog, BorderLayout.EAST);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 24, 30));
        logArea.setForeground(new Color(230, 235, 240));
        logArea.setCaretColor(Color.WHITE);
        JScrollPane scrollLog = new JScrollPane(logArea);

        rightPanel.add(logHeader, BorderLayout.NORTH);
        rightPanel.add(scrollLog, BorderLayout.CENTER);

        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(6, 15, 6, 15));
        bottomBar.setBackground(new Color(45, 52, 65));

        JLabel creditLabel = new JLabel("Đề tài: Ứng dụng Chat & Truyền tệp P2P | Nhóm 03: Trần Gia Huy - Trần Sỷ Sơn Lâm - Lưu Phong Dư - Đặng Ngọc Huy");
        creditLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        creditLabel.setForeground(new Color(200, 205, 215));

        bottomBar.add(creditLabel, BorderLayout.WEST);
        add(bottomBar, BorderLayout.SOUTH);

        // Load danh sách người dùng ban đầu
        updateDbUserTable();
    }

    private void toggleServer() {
        if (server == null || !server.isRunning()) {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port < 1024 || port > 65535) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập cổng từ 1024 đến 65535!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Cổng phải là một số nguyên hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                server = new ChatServer(port);
                server.setLogListener(msg -> SwingUtilities.invokeLater(() -> {
                    logArea.append(msg + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                    updateOnlineTable();
                }));
                server.start();

                btnToggle.setText("Dừng Server");
                btnToggle.setBackground(new Color(220, 53, 69));
                statusLabel.setText("● Trạng thái: ĐANG CHẠY (Port: " + port + ")");
                statusLabel.setForeground(new Color(40, 167, 69));
                portField.setEnabled(false);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Không thể mở Server tại cổng " + port + ": " + e.getMessage(), "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            server.stop();
            btnToggle.setText("Khởi động Server");
            btnToggle.setBackground(new Color(40, 167, 69));
            statusLabel.setText("● Trạng thái: ĐÃ DỪNG");
            statusLabel.setForeground(new Color(220, 53, 69));
            portField.setEnabled(true);
            userTableModel.setRowCount(0);
        }
    }

    /**
     * Mở ngay cửa sổ Đăng nhập Client từ Server.
     */
    public void openClientLogin() {
        if (server == null || !server.isRunning()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Máy chủ chưa khởi động. Bạn có muốn khởi động Server ngay bây giờ để mở Client không?",
                    "Server chưa chạy", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                toggleServer();
                if (server == null || !server.isRunning()) return;
            } else {
                return;
            }
        }
        ClientMain.launchClient(this, false);
    }

    /**
     * Mở hộp thoại Đăng ký tài khoản người dùng mới trực tiếp vào SQLite.
     */
    public void openRegisterDialog() {
        JDialog regDialog = new JDialog(this, "Đăng ký tài khoản người dùng mới", true);
        regDialog.setSize(420, 260);
        regDialog.setLocationRelativeTo(this);
        regDialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 12));
        form.add(new JLabel("Tên đăng nhập:"));
        JTextField txtUser = new JTextField();
        form.add(txtUser);

        form.add(new JLabel("Mật khẩu:"));
        JPasswordField txtPass = new JPasswordField();
        form.add(txtPass);

        form.add(new JLabel("Họ và tên:"));
        JTextField txtFullName = new JTextField();
        form.add(txtFullName);

        panel.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnCancel = new JButton("Hủy");
        btnCancel.addActionListener(e -> regDialog.dispose());

        JButton btnSubmit = new JButton("Đăng ký ngay");
        btnSubmit.setBackground(new Color(40, 167, 69));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();
            String fn = txtFullName.getText().trim();

            if (u.isEmpty() || p.isEmpty() || fn.isEmpty()) {
                JOptionPane.showMessageDialog(regDialog, "Vui lòng nhập đầy đủ các thông tin!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean ok = DatabaseManager.getInstance().registerUser(u, p, fn);
            if (ok) {
                JOptionPane.showMessageDialog(regDialog, "Đăng ký tài khoản [" + u + "] thành công!\nTài khoản đã được lưu vào CSDL SQLite.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                updateDbUserTable();
                if (logArea != null) {
                    logArea.append("[ĐĂNG KÝ] Tài khoản mới [" + u + "] (" + fn + ") đã được tạo qua Server GUI.\n");
                }
                regDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(regDialog, "Tên tài khoản [" + u + "] đã tồn tại trong hệ thống!", "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(btnCancel);
        btnPanel.add(btnSubmit);
        panel.add(btnPanel, BorderLayout.SOUTH);

        regDialog.setContentPane(panel);
        regDialog.setVisible(true);
    }

    private void updateOnlineTable() {
        if (server == null) return;
        userTableModel.setRowCount(0);
        server.getOnlineClients().forEach((user, handler) -> {
            userTableModel.addRow(new Object[]{user, handler.getClientIp()});
        });
    }

    private void updateDbUserTable() {
        if (dbUserTableModel == null) return;
        dbUserTableModel.setRowCount(0);
        List<User> list = DatabaseManager.getInstance().getAllRegisteredUsers();
        for (User u : list) {
            dbUserTableModel.addRow(new Object[]{u.getUsername(), u.getFullName()});
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored2) {
            }
        }

        SwingUtilities.invokeLater(() -> {
            ServerWindow window = new ServerWindow();
            window.setVisible(true);
        });
    }
}

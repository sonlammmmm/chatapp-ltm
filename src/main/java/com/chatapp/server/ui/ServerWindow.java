/**
 * ServerWindow.java — Hiển thị bảng điều khiển Server, tài khoản, kết nối và nhật ký hoạt động.
 */
package com.chatapp.server.ui;

import com.chatapp.common.config.AppConfig;
import com.chatapp.client.ClientMain;
import com.chatapp.common.model.User;
import com.chatapp.common.model.ChatRoom;
import com.chatapp.server.ChatServer;
import com.chatapp.server.db.DatabaseManager;

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
    private final String appVersion = readAppVersion();
    private ChatServer server;
    private JTextField portField;
    private JTextField serverIpField;
    private JButton btnToggle;
    private JLabel statusLabel;
    private JTextArea logArea;
    private DefaultTableModel userTableModel;
    private DefaultTableModel dbUserTableModel;
    private DefaultListModel<String> roomListModel;
    private JTabbedPane tabbedPane;
    private JPanel usersView, roomsView, accountsView, logView;
    private JLabel usersEmpty, roomsEmpty, accountsEmpty, logEmpty;
    private JScrollPane scrollLog;
    private final JCheckBox followLog = new JCheckBox("Theo dõi trực tiếp", true);
    private final Color primary = new Color(0, 78, 160);
    private final Color muted = new Color(200, 205, 215);
    private final Color danger = new Color(255, 170, 180);
    private final Color success = new Color(135, 225, 160);

    /** Khởi tạo đối tượng ServerWindow với dữ liệu và tài nguyên ban đầu. */
    public ServerWindow() {
        setTitle("ChatApp v" + appVersion + " | SERVER — Quản trị máy chủ");
        setSize(1020, 680);
        setMinimumSize(new Dimension(850, 520));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            /** Thu hồi tài nguyên và xử lý đóng cửa sổ chat. */
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                if (!confirmStop()) return;
                if (server != null) server.stop();
                dispose();
            }
        });
        setLocationRelativeTo(null);

        initUI();
    }

    /** Tạo thành phần giao diện, bố cục và đăng ký các sự kiện thao tác. */
    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Top Control Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        topPanel.setBackground(new Color(30, 35, 45));

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel("Quản trị máy chủ");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setToolTipText("Khởi động máy chủ, theo dõi kết nối và nhật ký.");
        heading.add(title, BorderLayout.CENTER);
        topPanel.add(heading, BorderLayout.NORTH);

        // Bên trái thanh điều khiển: Cổng & Nút Start/Stop Server
        JPanel leftControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        leftControl.setOpaque(false);

        JLabel lblPort = new JLabel("Cổng:");
        lblPort.setForeground(Color.WHITE);
        lblPort.setFont(new Font("Segoe UI", Font.BOLD, 13));

        portField = new JTextField(String.valueOf(AppConfig.serverPort()), 5);
        portField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        btnToggle = new JButton("Khởi động máy chủ");
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnToggle.setBackground(primary);
        btnToggle.setForeground(Color.WHITE);
        btnToggle.setFocusPainted(false);
        btnToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnToggle.addActionListener(e -> toggleServer());

        statusLabel = new JLabel("Chưa khởi động");
        statusLabel.setForeground(muted);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        leftControl.add(lblPort);
        leftControl.add(portField);
        leftControl.add(btnToggle);
        leftControl.add(statusLabel);
        topPanel.add(leftControl, BorderLayout.CENTER);

        JPanel addressPanel = new JPanel(new BorderLayout(8, 0));
        addressPanel.setOpaque(false);
        JLabel addressLabel = new JLabel("IP máy chủ:");
        addressLabel.setForeground(muted);
        serverIpField = new JTextField();
        serverIpField.setEditable(false);
        serverIpField.setForeground(Color.WHITE);
        serverIpField.setToolTipText("Client khác máy dùng IP cùng mạng LAN. Nếu có nhiều IP, chọn địa chỉ của Wi-Fi/Ethernet đang dùng. Cùng máy dùng 127.0.0.1. Có thể chọn và sao chép địa chỉ.");
        serverIpField.getAccessibleContext().setAccessibleName("Địa chỉ IP máy chủ");
        updateServerIp();
        addressPanel.add(addressLabel, BorderLayout.WEST);
        addressPanel.add(serverIpField, BorderLayout.CENTER);
        topPanel.add(addressPanel, BorderLayout.SOUTH);

        // Bên phải thanh điều khiển: Nút Đăng nhập Client & Nút Đăng ký tài khoản
        JPanel rightControl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControl.setOpaque(false);

        JButton btnLoginClient = new JButton("Mở cửa sổ trò chuyện");
        btnLoginClient.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLoginClient.setBackground(new Color(55, 65, 81));
        btnLoginClient.setForeground(Color.WHITE);
        btnLoginClient.setFocusPainted(false);
        btnLoginClient.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLoginClient.setToolTipText("Khởi chạy ngay một cửa sổ Client Đăng nhập để nhắn tin & truyền file");
        btnLoginClient.addActionListener(e -> openClientLogin());

        JButton btnRegisterUser = new JButton("+ Tạo tài khoản");
        btnRegisterUser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegisterUser.setBackground(new Color(55, 65, 81)); // Đồng bộ màu trung tính hiện đại
        btnRegisterUser.setForeground(Color.WHITE);
        btnRegisterUser.setFocusPainted(false);
        btnRegisterUser.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRegisterUser.setToolTipText("Mở form đăng ký thêm tài khoản mới vào cơ sở dữ liệu");
        btnRegisterUser.addActionListener(e -> openRegisterDialog());

        rightControl.add(btnLoginClient);
        rightControl.add(btnRegisterUser);
        heading.add(rightControl, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Center SplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.38);

        // Left Panel: Online Users, Rooms & Database Users
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(new EmptyBorder(5, 10, 5, 5));

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Tab 1: Người dùng trực tuyến (Online)
        userTableModel = new DefaultTableModel(new String[]{"Tài khoản", "Địa chỉ IP"}, 0) {
            /** Quy định ô trong bảng có được chỉnh sửa trực tiếp hay không. */
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable userTable = new JTable(userTableModel);
        userTable.setRowHeight(24);
        usersEmpty = emptyLabel("Khởi động máy chủ để nhận kết nối.");
        usersView = stateView(new JScrollPane(userTable), usersEmpty);
        tabbedPane.addTab("Trực tuyến (0)", usersView);

        // Tab 2: Phòng chat
        roomListModel = new DefaultListModel<>();

        JList<String> roomList = new JList<>(roomListModel);
        roomsEmpty = emptyLabel("Khởi động máy chủ để xem phòng chat.");
        roomsView = stateView(new JScrollPane(roomList), roomsEmpty);
        tabbedPane.addTab("Phòng chat (0)", roomsView);

        // Tab 3: Danh sách tất cả tài khoản trong Database
        JPanel dbUserPanel = new JPanel(new BorderLayout(5, 5));
        dbUserTableModel = new DefaultTableModel(new String[]{"Tài khoản", "Họ và tên"}, 0) {
            /** Quy định ô trong bảng có được chỉnh sửa trực tiếp hay không. */
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable dbUserTable = new JTable(dbUserTableModel);
        dbUserTable.setRowHeight(24);
        accountsEmpty = emptyLabel("Chưa có tài khoản. Chọn + Tạo tài khoản.");
        accountsView = stateView(new JScrollPane(dbUserTable), accountsEmpty);
        dbUserPanel.add(accountsView, BorderLayout.CENTER);

        JPanel dbUserActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton btnRefreshDb = new JButton("Làm mới");
        btnRefreshDb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRefreshDb.addActionListener(e -> updateDbUserTable());
        JButton btnAddDbUser = new JButton("+ Tạo tài khoản");
        btnAddDbUser.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnAddDbUser.addActionListener(e -> openRegisterDialog());
        dbUserActionPanel.add(btnRefreshDb);
        dbUserActionPanel.add(btnAddDbUser);
        dbUserPanel.add(dbUserActionPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Tài khoản (0)", dbUserPanel);

        leftPanel.add(tabbedPane, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        // Right Panel: Nhật ký hoạt động (Logs)
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new EmptyBorder(5, 5, 5, 10));

        JPanel logHeader = new JPanel(new BorderLayout());
        JLabel lblLog = new JLabel("Nhật ký hệ thống");
        lblLog.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnClearLog = new JButton("Xóa nhật ký");
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearLog.addActionListener(e -> {
            logArea.setText("");
            showData(logView, false);
            logEmpty.setText("Chưa có nhật ký mới.");
        });
        logHeader.add(lblLog, BorderLayout.WEST);
        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        followLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        followLog.setToolTipText("Tự cuộn khi đang ở cuối nhật ký. Bỏ chọn để dừng theo dõi.");
        logActions.add(followLog); logActions.add(btnClearLog);
        logHeader.add(logActions, BorderLayout.EAST);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 24, 30));
        logArea.setForeground(new Color(230, 235, 240));
        logArea.setCaretColor(Color.WHITE);
        ((javax.swing.text.DefaultCaret)logArea.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        scrollLog = new JScrollPane(logArea);
        logEmpty = emptyLabel("Khởi động máy chủ để bắt đầu ghi nhật ký.");
        logEmpty.setOpaque(true);
        logEmpty.setBackground(new Color(20, 24, 30));
        logView = stateView(scrollLog, logEmpty);
        followLog.addActionListener(e -> {
            if (followLog.isSelected()) scrollLog.getVerticalScrollBar().setValue(scrollLog.getVerticalScrollBar().getMaximum());
        });

        rightPanel.add(logHeader, BorderLayout.NORTH);
        rightPanel.add(logView, BorderLayout.CENTER);

        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(6, 15, 6, 15));
        bottomBar.setBackground(new Color(45, 52, 65));

        JLabel creditLabel = new JLabel("Đề tài: Ứng dụng Chat & Truyền tệp P2P | Nhóm 03: Trần Gia Huy - Trần Sỷ Sơn Lâm - Đặng Ngọc Huy");
        creditLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        creditLabel.setForeground(new Color(200, 205, 215));

        bottomBar.add(creditLabel, BorderLayout.CENTER);
        JLabel versionLabel = new JLabel("v" + appVersion);
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        versionLabel.setForeground(muted);
        bottomBar.add(versionLabel, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        // Load danh sách người dùng ban đầu
        updateDbUserTable();
    }

    /** Bật hoặc dừng Server theo trạng thái hiện tại. */
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
                ChatServer source = server;
                server.setLogListener(msg -> SwingUtilities.invokeLater(() -> {
                    if (server == source && isDisplayable()) appendLog(msg);
                }));
                server.setStateListener(() -> SwingUtilities.invokeLater(() -> {
                    if (server == source && isDisplayable()) refreshState();
                }));
                server.start();
                updateServerIp();

                btnToggle.setText("Dừng máy chủ");
                btnToggle.setBackground(new Color(85, 42, 51));
                btnToggle.setForeground(danger);
                statusLabel.setText("Đang chạy • Cổng " + port);
                statusLabel.setForeground(success);
                portField.setEnabled(false);
                refreshState();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Không thể mở Server tại cổng " + port + ": " + e.getMessage(), "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            if (!confirmStop()) return;
            server.stop();
            btnToggle.setText("Khởi động máy chủ");
            btnToggle.setBackground(primary);
            btnToggle.setForeground(Color.WHITE);
            statusLabel.setText("Đã dừng");
            statusLabel.setForeground(muted);
            portField.setEnabled(true);
            refreshState();
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
        regDialog.setResizable(true);

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
        btnSubmit.setBackground(primary);
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
                    appendLog("[ĐĂNG KÝ] Tài khoản mới [" + u + "] (" + fn + ") đã được tạo qua màn quản trị.");
                }
                regDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(regDialog, "Tên tài khoản [" + u + "] đã tồn tại trong hệ thống!", "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(btnCancel);
        btnPanel.add(btnSubmit);
        panel.add(btnPanel, BorderLayout.SOUTH);

        regDialog.setContentPane(new JScrollPane(panel));
        regDialog.getRootPane().setDefaultButton(btnSubmit);
        regDialog.setVisible(true);
    }

    /** Làm mới bảng người dùng đang kết nối. */
    private void updateOnlineTable() {
        userTableModel.setRowCount(0);
        if (server == null || !server.isRunning()) return;
        server.getOnlineClients().forEach((user, handler) -> {
            userTableModel.addRow(new Object[]{user, handler.getClientIp()});
        });
    }

    /** Làm mới bảng tài khoản đã đăng ký. */
    private void updateDbUserTable() {
        if (dbUserTableModel == null) return;
        dbUserTableModel.setRowCount(0);
        List<User> list = DatabaseManager.getInstance().getAllRegisteredUsers();
        for (User u : list) {
            dbUserTableModel.addRow(new Object[]{u.getUsername(), u.getFullName()});
        }
        tabbedPane.setTitleAt(2, "Tài khoản (" + list.size() + ")");
        showData(accountsView, !list.isEmpty());
    }

    /** Đọc phiên bản ứng dụng từ tài nguyên cấu hình. */
    private static String readAppVersion() {
        try (java.io.InputStream stream = ServerWindow.class.getResourceAsStream("/app-version.properties")) {
            if (stream != null) {
                java.util.Properties properties = new java.util.Properties();
                properties.load(stream);
                String version = properties.getProperty("version", "").trim();
                if (!version.isEmpty() && !version.contains("${")) return version;
            }
        } catch (IOException ignored) {
            // A missing build resource must not prevent server administration.
        }
        return "không xác định";
    }

    /** Xác định và hiển thị địa chỉ IP của Server. */
    private void updateServerIp() {
        java.util.Set<String> addresses = new java.util.TreeSet<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                java.net.NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> entries = network.getInetAddresses();
                while (entries.hasMoreElements()) {
                    java.net.InetAddress address = entries.nextElement();
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                        addresses.add(address.getHostAddress());
                }
            }
            serverIpField.setText(addresses.isEmpty()
                    ? "Chưa có IPv4 mạng LAN — cùng máy dùng 127.0.0.1" : String.join(" / ", addresses));
        } catch (java.net.SocketException | SecurityException e) {
            serverIpField.setText("Không đọc được IP mạng — cùng máy dùng 127.0.0.1");
        }
        serverIpField.setCaretPosition(0);
    }

    /** Tạo nhãn hiển thị khi danh sách chưa có dữ liệu. */
    private static JLabel emptyLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(new Color(200, 205, 215));
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setBorder(new EmptyBorder(16, 12, 16, 12));
        return label;
    }

    /** Tạo vùng hiển thị dữ liệu hoặc thông báo danh sách trống. */
    private static JPanel stateView(JComponent data, JLabel empty) {
        JPanel panel = new JPanel(new CardLayout());
        panel.add(data, "DATA"); panel.add(empty, "EMPTY");
        showData(panel, false);
        return panel;
    }

    /** Chuyển giữa vùng dữ liệu và thông báo trống. */
    private static void showData(JPanel panel, boolean hasData) {
        ((CardLayout)panel.getLayout()).show(panel, hasData ? "DATA" : "EMPTY");
    }

    /** Thêm dòng nhật ký vào vùng theo dõi trên giao diện. */
    void appendLog(String message) {
        JScrollBar bar = scrollLog.getVerticalScrollBar();
        boolean follow = followLog.isSelected() && (logArea.getDocument().getLength() == 0
                || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 16);
        logArea.append(message + "\n");
        showData(logView, true);
        if (follow) SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    /** Đồng bộ trạng thái Server lên các thành phần giao diện. */
    void refreshState() {
        updateOnlineTable();
        boolean running = server != null && server.isRunning();
        usersEmpty.setText(running ? "Chưa có người dùng kết nối." : "Khởi động máy chủ để nhận kết nối.");
        showData(usersView, userTableModel.getRowCount() > 0);
        tabbedPane.setTitleAt(0, "Trực tuyến (" + userTableModel.getRowCount() + ")");
        roomListModel.clear();
        if (running) {
            List<ChatRoom> rooms = server.getRoomManager().getAllRooms();
            rooms.sort(java.util.Comparator.comparing(ChatRoom::getRoomName));
            for (ChatRoom room : rooms) {
                roomListModel.addElement(room.getRoomName() + " • " + room.getMembers().size() + " thành viên");
            }
        }
        roomsEmpty.setText(running ? "Chưa có phòng chat." : "Khởi động máy chủ để xem phòng chat.");
        showData(roomsView, !roomListModel.isEmpty());
        tabbedPane.setTitleAt(1, "Phòng chat (" + roomListModel.size() + ")");
        updateDbUserTable();
    }

    /** Hỏi người vận hành xác nhận trước khi dừng Server. */
    protected boolean confirmStop() {
        if (server == null || !server.isRunning()) return true;
        int count = server.getOnlineClients().size();
        return JOptionPane.showConfirmDialog(this,
                "Dừng máy chủ sẽ ngắt " + count + " kết nối người dùng đang đăng nhập. Tiếp tục?",
                "Xác nhận dừng máy chủ", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Điểm vào chương trình, khởi tạo ứng dụng tương ứng. */
    public static void main(String[] args) {
        com.chatapp.server.ServerMain.main(args);
    }
}

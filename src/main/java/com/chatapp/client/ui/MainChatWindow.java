package com.chatapp.client.ui;

import com.chatapp.client.ChatClient;
import com.chatapp.client.ClientListener;
import com.chatapp.client.file.P2PFileReceiver;
import com.chatapp.client.file.P2PFileSender;
import com.chatapp.client.ui.components.ChatBubblePanel;
import com.chatapp.client.ui.components.RoomListCellRenderer;
import com.chatapp.client.ui.components.UserListCellRenderer;
import com.chatapp.common.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cửa sổ giao diện chính của ứng dụng Chat Client (Hỗ trợ Chat 1-1, Chat Nhóm, P2P File & Mã hóa AES).
 */
public class MainChatWindow extends JFrame implements ClientListener {
    private final ChatClient client;
    private final User currentUser;

    // Sidebar lists
    private JTabbedPane sidebarTabs;
    private DefaultListModel<ChatRoom> roomListModel;
    private JList<ChatRoom> roomList;
    private DefaultListModel<User> userListModel;
    private JList<User> userList;

    // Chat target state
    private String currentTargetId = "lobby"; // ID của phòng hoặc username đang chat
    private String currentTargetTitle = "Sảnh Chung";
    private boolean isCurrentTargetGroup = true;

    // Header components
    private JLabel headerAvatarLabel;
    private Color headerAvatarBgColor = new Color(90, 80, 140);
    private JLabel lblTargetTitle;
    private JLabel lblTargetSub;
    private JLabel lblTyping;

    // Chat area components
    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_MESSAGES = "MESSAGES";
    private CardLayout chatBodyCardLayout;
    private JPanel chatBodyPanel;
    private JPanel messagesPanel;
    private JScrollPane messagesScrollPane;
    private JTextField messageInputField;
    private JButton btnSend;
    private JButton btnAttach;

    // Lưu trữ tin nhắn theo từng phiên (targetId -> List<ChatMessage>)
    private final Map<String, List<ChatMessage>> chatHistory = new ConcurrentHashMap<>();
    private final Map<String, ChatBubblePanel> messageBubbleMap = new ConcurrentHashMap<>();

    // Debounce cho trạng thái gõ bàn phím (Typing...)
    private javax.swing.Timer typingTimer;
    private javax.swing.Timer peerTypingClearTimer;
    // Quản lý các file đang chờ truyền P2P theo phiên (receiver_fileName -> File)
    private final Map<String, File> pendingOutgoingFiles = new ConcurrentHashMap<>();

    public MainChatWindow(ChatClient client, User currentUser) {
        this.client = client;
        this.currentUser = currentUser;
        this.client.setListener(this);

        setTitle("Ứng dụng Chat TCP Socket & Truyền tệp P2P - [" + currentUser.getFullName() + "]");
        setSize(1000, 700);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                client.disconnect();
            }
        });

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // SplitPane chính: Trái = Sidebar, Phải = Chat Area
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(310);
        mainSplit.setResizeWeight(0.3);

        // ================= SIDEBAR (TRÁI) =================
        JPanel sidebar = new JPanel(new BorderLayout(5, 8));
        sidebar.setBackground(new Color(24, 28, 36));
        sidebar.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Thông tin cá nhân Profile Card
        JPanel profileCard = new JPanel(new BorderLayout(10, 0));
        profileCard.setBackground(new Color(36, 42, 54));
        profileCard.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel myAvatar = new JLabel(currentUser.getFullName().substring(0, 1).toUpperCase(), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 122, 255));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        myAvatar.setPreferredSize(new Dimension(36, 36));
        myAvatar.setForeground(Color.WHITE);
        myAvatar.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel profileInfo = new JPanel(new GridLayout(2, 1, 0, 2));
        profileInfo.setOpaque(false);
        JLabel lblMyName = new JLabel(currentUser.getFullName());
        lblMyName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblMyName.setForeground(Color.WHITE);

        JLabel lblMyUser = new JLabel("@" + currentUser.getUsername() + " (Đang online)");
        lblMyUser.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblMyUser.setForeground(new Color(40, 167, 69));

        profileInfo.add(lblMyName);
        profileInfo.add(lblMyUser);

        profileCard.add(myAvatar, BorderLayout.WEST);
        profileCard.add(profileInfo, BorderLayout.CENTER);
        sidebar.add(profileCard, BorderLayout.NORTH);

        // 2. Danh sách Phòng và Bạn bè qua JTabbedPane (Phương án A)
        sidebarTabs = new JTabbedPane(JTabbedPane.TOP);
        sidebarTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sidebarTabs.setBackground(new Color(24, 28, 36));
        sidebarTabs.setForeground(new Color(200, 210, 225));

        // Tab Bạn bè trực tuyến
        JPanel usersSection = new JPanel(new BorderLayout(0, 5));
        usersSection.setOpaque(false);
        usersSection.setBorder(new EmptyBorder(6, 2, 2, 2));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(new Color(24, 28, 36));
        userList.setCellRenderer(new UserListCellRenderer());
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                roomList.clearSelection();
                selectUser(userList.getSelectedValue());
            }
        });
        JScrollPane usersScroll = new JScrollPane(userList);
        usersScroll.setBorder(null);
        usersScroll.getViewport().setBackground(new Color(24, 28, 36));
        usersSection.add(usersScroll, BorderLayout.CENTER);

        // Tab Phòng chat nhóm
        JPanel roomsSection = new JPanel(new BorderLayout(0, 6));
        roomsSection.setOpaque(false);
        roomsSection.setBorder(new EmptyBorder(6, 2, 2, 2));

        JPanel roomsHeader = new JPanel(new BorderLayout());
        roomsHeader.setOpaque(false);
        roomsHeader.setBorder(new EmptyBorder(2, 4, 6, 4));

        JLabel lblRooms = new JLabel("PHÒNG CHAT NHÓM");
        lblRooms.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblRooms.setForeground(new Color(150, 160, 175));

        JButton btnNewRoom = new JButton("+ Tạo");
        btnNewRoom.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnNewRoom.setBackground(new Color(0, 122, 255));
        btnNewRoom.setForeground(Color.WHITE);
        btnNewRoom.setFocusPainted(false);
        btnNewRoom.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNewRoom.setMargin(new Insets(2, 8, 2, 8));
        btnNewRoom.addActionListener(e -> new CreateRoomDialog(this, client).setVisible(true));

        roomsHeader.add(lblRooms, BorderLayout.WEST);
        roomsHeader.add(btnNewRoom, BorderLayout.EAST);
        roomsSection.add(roomsHeader, BorderLayout.NORTH);

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setBackground(new Color(24, 28, 36));
        roomList.setCellRenderer(new RoomListCellRenderer());
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && roomList.getSelectedValue() != null) {
                userList.clearSelection();
                selectRoom(roomList.getSelectedValue());
            }
        });
        JScrollPane roomsScroll = new JScrollPane(roomList);
        roomsScroll.setBorder(null);
        roomsScroll.getViewport().setBackground(new Color(24, 28, 36));
        roomsSection.add(roomsScroll, BorderLayout.CENTER);

        sidebarTabs.addTab("💬 Bạn bè", usersSection);
        sidebarTabs.addTab("👥 Phòng nhóm", roomsSection);
        sidebar.add(sidebarTabs, BorderLayout.CENTER);

        mainSplit.setLeftComponent(sidebar);

        // ================= CHAT AREA (PHẢI) =================
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(new Color(20, 24, 30));

        // 1. Header khu vực chat (Avatar mini + Title/Sub + Badge mã hóa)
        JPanel chatHeader = new JPanel(new BorderLayout(12, 0));
        chatHeader.setBackground(new Color(28, 33, 42));
        chatHeader.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel headerLeft = new JPanel(new BorderLayout(12, 0));
        headerLeft.setOpaque(false);

        headerAvatarLabel = new JLabel("#", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(headerAvatarBgColor);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        headerAvatarLabel.setPreferredSize(new Dimension(38, 38));
        headerAvatarLabel.setForeground(Color.WHITE);
        headerAvatarLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);

        lblTargetTitle = new JLabel("Sảnh Chung");
        lblTargetTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTargetTitle.setForeground(Color.WHITE);

        lblTargetSub = new JLabel("Phòng trò chuyện tự do");
        lblTargetSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTargetSub.setForeground(new Color(160, 170, 185));

        titlePanel.add(lblTargetTitle);
        titlePanel.add(lblTargetSub);

        headerLeft.add(headerAvatarLabel, BorderLayout.WEST);
        headerLeft.add(titlePanel, BorderLayout.CENTER);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        headerRight.setOpaque(false);

        JLabel lblSecurity = new JLabel("🔒 AES-128 End-to-End");
        lblSecurity.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblSecurity.setForeground(new Color(40, 167, 69));
        headerRight.add(lblSecurity);

        chatHeader.add(headerLeft, BorderLayout.WEST);
        chatHeader.add(headerRight, BorderLayout.EAST);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // 2. Nội dung chat: CardLayout (Empty State <-> Danh sách tin nhắn)
        chatBodyCardLayout = new CardLayout();
        chatBodyPanel = new JPanel(chatBodyCardLayout);
        chatBodyPanel.setBackground(new Color(20, 24, 30));

        // Card 1: Giao diện trống thân thiện (Empty State)
        JPanel emptyStatePanel = createEmptyStatePanel();
        chatBodyPanel.add(emptyStatePanel, CARD_EMPTY);

        // Card 2: Cuộn tin nhắn
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(new Color(20, 24, 30));

        messagesScrollPane = new JScrollPane(messagesPanel);
        messagesScrollPane.setBorder(null);
        messagesScrollPane.getViewport().setBackground(new Color(20, 24, 30));
        messagesScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatBodyPanel.add(messagesScrollPane, CARD_MESSAGES);

        chatPanel.add(chatBodyPanel, BorderLayout.CENTER);

        // 3. Khung nhập tin nhắn & Chỉ báo đang gõ (Typing Indicator ở dưới)
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(new Color(20, 24, 30));

        JPanel typingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        typingPanel.setOpaque(false);
        lblTyping = new JLabel("");
        lblTyping.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblTyping.setForeground(new Color(0, 170, 255));
        typingPanel.add(lblTyping);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(new Color(28, 33, 42));
        inputPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        btnAttach = new JButton("📎 Gửi File P2P");
        btnAttach.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAttach.setToolTipText("Gửi tệp tin dung lượng lớn trực tiếp P2P (Có hỗ trợ Resume)");
        btnAttach.addActionListener(e -> chooseAndSendFile());

        messageInputField = new JTextField();
        messageInputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        messageInputField.addActionListener(e -> sendMessage());
        messageInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                notifyTyping();
            }
        });

        btnSend = new JButton("Gửi ➤");
        btnSend.setBackground(new Color(0, 122, 255));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.setFocusPainted(false);
        btnSend.addActionListener(e -> sendMessage());

        inputPanel.add(btnAttach, BorderLayout.WEST);
        inputPanel.add(messageInputField, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);

        bottomContainer.add(typingPanel, BorderLayout.NORTH);
        bottomContainer.add(inputPanel, BorderLayout.CENTER);
        chatPanel.add(bottomContainer, BorderLayout.SOUTH);

        mainSplit.setRightComponent(chatPanel);
        add(mainSplit, BorderLayout.CENTER);

        refreshMessagesView();
    }

    private JPanel createEmptyStatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(20, 24, 30));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel icon = new JLabel("💬", SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 46));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Chưa có tin nhắn nào", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(243, 244, 246));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Hãy gửi lời chào đầu tiên hoặc chia sẻ tệp tin P2P!", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(156, 163, 175));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tip = new JLabel("🔒 Kết nối mã hóa an toàn AES-128 End-to-End", SwingConstants.CENTER);
        tip.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tip.setForeground(new Color(16, 185, 129));
        tip.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(icon);
        content.add(Box.createVerticalStrut(10));
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(12));
        content.add(tip);

        panel.add(content);
        return panel;
    }

    private void notifyTyping() {
        if (typingTimer == null) {
            client.sendTypingStatus(currentTargetId, true);
            typingTimer = new javax.swing.Timer(2000, e -> {
                client.sendTypingStatus(currentTargetId, false);
                typingTimer.stop();
                typingTimer = null;
            });
            typingTimer.setRepeats(false);
            typingTimer.start();
        }
    }

    private void selectRoom(ChatRoom room) {
        currentTargetId = room.getRoomId();
        currentTargetTitle = "# " + room.getRoomName();
        isCurrentTargetGroup = true;

        lblTargetTitle.setText(currentTargetTitle);
        lblTargetSub.setText(room.getMembers().size() + " thành viên • " + (room.getDescription() != null ? room.getDescription() : ""));
        lblTyping.setText("");
        btnAttach.setEnabled(false); // Tính năng truyền file P2P là 1-1 theo đề tài
        btnAttach.setToolTipText("Tính năng truyền file P2P áp dụng cho phiên chat 1-1");

        headerAvatarLabel.setText("#");
        headerAvatarBgColor = new Color(90, 80, 140);
        headerAvatarLabel.repaint();

        refreshMessagesView();
        client.joinRoom(room.getRoomId());
    }

    private void selectUser(User user) {
        currentTargetId = user.getUsername();
        currentTargetTitle = user.getFullName() + " (@" + user.getUsername() + ")";
        isCurrentTargetGroup = false;

        lblTargetTitle.setText(currentTargetTitle);
        lblTargetSub.setText("● Đang trực tuyến (IP: " + (user.getIpAddress() != null ? user.getIpAddress() : "LAN") + ")");
        lblTyping.setText("");
        btnAttach.setEnabled(true);
        btnAttach.setToolTipText("Gửi tệp tin dung lượng lớn trực tiếp P2P cho " + user.getFullName());

        String firstLetter = user.getFullName() != null && !user.getFullName().isEmpty()
                ? user.getFullName().substring(0, 1).toUpperCase()
                : (user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername().substring(0, 1).toUpperCase() : "?");
        headerAvatarLabel.setText(firstLetter);
        int hash = Math.abs(user.getUsername() != null ? user.getUsername().hashCode() : 0);
        Color[] avatarColors = {
            new Color(0, 122, 255),
            new Color(16, 185, 129),
            new Color(139, 92, 246),
            new Color(245, 158, 11),
            new Color(236, 72, 153)
        };
        headerAvatarBgColor = avatarColors[hash % avatarColors.length];
        headerAvatarLabel.repaint();

        refreshMessagesView();
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty()) return;

        messageInputField.setText("");

        if (isCurrentTargetGroup) {
            client.sendGroupMessage(currentTargetId, text);
            // Hiển thị tin nhắn của mình trong nhóm
            ChatMessage myMsg = new ChatMessage(currentUser.getUsername(), currentTargetId, text, true);
            myMsg.setSenderFullName(currentUser.getFullName());
            addMessageToHistory(currentTargetId, myMsg);
        } else {
            client.sendDirectMessage(currentTargetId, text);
            // Hiển thị tin nhắn của mình gửi 1-1
            ChatMessage myMsg = new ChatMessage(currentUser.getUsername(), currentTargetId, text, false);
            myMsg.setSenderFullName(currentUser.getFullName());
            addMessageToHistory(currentTargetId, myMsg);
        }
    }

    private void addMessageToHistory(String targetId, ChatMessage msg) {
        chatHistory.computeIfAbsent(targetId, k -> new ArrayList<>()).add(msg);
        if (targetId.equalsIgnoreCase(currentTargetId)) {
            appendMessageBubble(msg);
        }
    }

    private void appendMessageBubble(ChatMessage msg) {
        boolean isSelf = msg.getSender().equalsIgnoreCase(currentUser.getUsername());
        ChatBubblePanel bubble = new ChatBubblePanel(msg, isSelf);
        messageBubbleMap.put(msg.getMessageId(), bubble);

        messagesPanel.add(bubble);
        if (chatBodyCardLayout != null && chatBodyPanel != null) {
            chatBodyCardLayout.show(chatBodyPanel, CARD_MESSAGES);
        }
        messagesPanel.revalidate();
        messagesPanel.repaint();

        // Tự động cuộn xuống dưới cùng
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = messagesScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void refreshMessagesView() {
        messagesPanel.removeAll();
        messageBubbleMap.clear();

        List<ChatMessage> list = chatHistory.get(currentTargetId);
        if (list == null || list.isEmpty()) {
            if (chatBodyCardLayout != null && chatBodyPanel != null) {
                chatBodyCardLayout.show(chatBodyPanel, CARD_EMPTY);
            }
        } else {
            for (ChatMessage msg : list) {
                boolean isSelf = msg.getSender().equalsIgnoreCase(currentUser.getUsername());
                ChatBubblePanel bubble = new ChatBubblePanel(msg, isSelf);
                messageBubbleMap.put(msg.getMessageId(), bubble);
                messagesPanel.add(bubble);
            }
            if (chatBodyCardLayout != null && chatBodyPanel != null) {
                chatBodyCardLayout.show(chatBodyPanel, CARD_MESSAGES);
            }
        }
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    // ================= XỬ LÝ TRUYỀN FILE P2P =================
    private void chooseAndSendFile() {
        if (isCurrentTargetGroup) {
            JOptionPane.showMessageDialog(this, "Tính năng truyền file P2P chỉ áp dụng cho Chat 1-1!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp tin muốn gửi cho " + currentTargetTitle);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.exists() || file.length() == 0) {
                JOptionPane.showMessageDialog(this, "Tệp tin không tồn tại hoặc rỗng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String transferKey = currentTargetId + "_" + file.getName();
            pendingOutgoingFiles.put(transferKey, file);
            // Gửi yêu cầu khởi tạo truyền file qua Server
            client.sendFileRequest(currentTargetId, file);

            JOptionPane.showMessageDialog(this,
                    "Đã gửi yêu cầu truyền file [" + file.getName() + "]\nĐang chờ người nhận chấp nhận...",
                    "Yêu cầu truyền file", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ================= CLIENT LISTENER IMPLEMENTATIONS =================
    @Override
    public void onLoginSuccess(User user) {}

    @Override
    public void onLoginFail(String reason) {}

    @Override
    public void onRegisterResult(boolean success, String message) {}

    @Override
    public void onUserListUpdated(List<User> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (User u : users) {
                // Không hiển thị chính mình trong danh sách bạn bè
                if (!u.getUsername().equalsIgnoreCase(currentUser.getUsername())) {
                    userListModel.addElement(u);
                }
            }
            if (sidebarTabs != null && sidebarTabs.getTabCount() > 0) {
                sidebarTabs.setTitleAt(0, "💬 Bạn bè (" + userListModel.size() + ")");
            }
        });
    }

    @Override
    public void onRoomListUpdated(List<ChatRoom> rooms) {
        SwingUtilities.invokeLater(() -> {
            roomListModel.clear();
            for (ChatRoom r : rooms) {
                roomListModel.addElement(r);
            }
            if (sidebarTabs != null && sidebarTabs.getTabCount() > 1) {
                sidebarTabs.setTitleAt(1, "👥 Phòng nhóm (" + roomListModel.size() + ")");
            }
        });
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            String conversationKey;
            if (message.isGroup()) {
                conversationKey = message.getRecipient(); // roomId
            } else {
                conversationKey = message.getSender(); // người gửi tới mình
            }

            addMessageToHistory(conversationKey, message);

            // Báo đã xem nếu đang mở cửa sổ chat của người này
            if (!message.isGroup() && conversationKey.equalsIgnoreCase(currentTargetId)) {
                client.sendMessageRead(message.getSender(), message.getMessageId());
            }
        });
    }

    @Override
    public void onMessageStatusUpdated(String messageId, ChatMessage.Status status) {
        SwingUtilities.invokeLater(() -> {
            ChatBubblePanel bubble = messageBubbleMap.get(messageId);
            if (bubble != null) {
                bubble.updateStatus(status);
            }
        });
    }

    @Override
    public void onUserTyping(String sender, String target) {
        SwingUtilities.invokeLater(() -> {
            if (sender.equalsIgnoreCase(currentTargetId) || target.equalsIgnoreCase(currentTargetId)) {
                lblTyping.setText("✎ " + sender + " đang nhập tin...");
                if (peerTypingClearTimer != null) peerTypingClearTimer.stop();
                peerTypingClearTimer = new javax.swing.Timer(2500, e -> lblTyping.setText(""));
                peerTypingClearTimer.setRepeats(false);
                peerTypingClearTimer.start();
            }
        });
    }

    @Override
    public void onFileTransferRequested(FileTransferRequest request) {
        SwingUtilities.invokeLater(() -> {
            String message = "Người dùng [" + request.getSender() + "] muốn gửi cho bạn một tệp tin:\n"
                    + "• Tên file: " + request.getFileName() + "\n"
                    + "• Dung lượng: " + request.getFormattedSize() + "\n\n"
                    + "Bạn có muốn chấp nhận nhận tệp tin này qua kết nối trực tiếp P2P không?";

            int choice = JOptionPane.showConfirmDialog(this, message, "Yêu cầu nhận tệp P2P", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                // Cho người dùng chọn thư mục lưu (mặc định thư mục Downloads hoặc thư mục hiện tại)
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setDialogTitle("Chọn thư mục lưu tệp tin");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                File defaultDir = new File(System.getProperty("user.home"), "Downloads");
                if (defaultDir.exists()) {
                    dirChooser.setSelectedFile(defaultDir);
                }

                File saveDir = defaultDir.exists() ? defaultDir : new File(".");
                if (dirChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    saveDir = dirChooser.getSelectedFile();
                }

                try {
                    // Mở ServerSocket nhận P2P
                    File finalSaveDir = saveDir;
                    P2PFileReceiver[] receiverHolder = new P2PFileReceiver[1];

                    FileTransferProgressDialog progressDialog = new FileTransferProgressDialog(
                            this, request.getFileName(), request.getFileSize(), false,
                            () -> {
                                if (receiverHolder[0] != null) receiverHolder[0].cancel();
                            }
                    );

                    receiverHolder[0] = new P2PFileReceiver(request, finalSaveDir, progressDialog);

                    // Gửi thông báo chấp nhận kèm Port P2P cho phía người gửi
                    client.acceptFileRequest(request, receiverHolder[0].getListeningPort());

                    // Chạy luồng nhận tệp tin
                    new Thread(receiverHolder[0], "P2PFileReceiverThread").start();
                    progressDialog.setVisible(true);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Không thể mở cổng nhận file P2P: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                client.rejectFileRequest(request);
            }
        });
    }

    @Override
    public void onFileTransferAccepted(FileTransferRequest request) {
        SwingUtilities.invokeLater(() -> {
            String transferKey = request.getReceiver() + "_" + request.getFileName();
            File fileToSend = pendingOutgoingFiles.remove(transferKey);
            if (fileToSend == null) {
                // Fallback tìm kiếm theo tên tệp nếu receiver key có sai khác định dạng
                for (Map.Entry<String, File> entry : pendingOutgoingFiles.entrySet()) {
                    if (entry.getKey().endsWith("_" + request.getFileName())) {
                        fileToSend = pendingOutgoingFiles.remove(entry.getKey());
                        break;
                    }
                }
            }

            if (fileToSend == null || !fileToSend.exists()) {
                JOptionPane.showMessageDialog(this, "Tệp tin cần gửi [" + request.getFileName() + "] không còn tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            File finalFileToSend = fileToSend;
            P2PFileSender[] senderHolder = new P2PFileSender[1];
            FileTransferProgressDialog progressDialog = new FileTransferProgressDialog(
                    this, request.getFileName(), request.getFileSize(), true,
                    () -> {
                        if (senderHolder[0] != null) senderHolder[0].cancel();
                    }
            );

            senderHolder[0] = new P2PFileSender(request, finalFileToSend, progressDialog);
            new Thread(senderHolder[0], "P2PFileSenderThread").start();
            progressDialog.setVisible(true);
        });
    }

    @Override
    public void onFileTransferRejected(FileTransferRequest request) {
        SwingUtilities.invokeLater(() -> {
            String transferKey = request.getReceiver() + "_" + request.getFileName();
            pendingOutgoingFiles.remove(transferKey);
            JOptionPane.showMessageDialog(this,
                    "Người dùng [" + request.getReceiver() + "] đã TỪ CHỐI nhận tệp tin [" + request.getFileName() + "].",
                    "Từ chối truyền file", JOptionPane.WARNING_MESSAGE);
        });
    }

    @Override
    public void onConnectionLost() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ TCP Socket!", "Cảnh báo ngắt kết nối", JOptionPane.WARNING_MESSAGE);
            dispose();
            System.exit(0);
        });
    }
}

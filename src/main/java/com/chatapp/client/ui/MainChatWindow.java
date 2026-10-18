package com.chatapp.client.ui;

import com.chatapp.client.ChatClient;
import com.chatapp.client.ClientListener;
import com.chatapp.client.file.P2PFileReceiver;
import com.chatapp.client.file.P2PFileSender;
import com.chatapp.client.ui.components.ChatBubblePanel;
import com.chatapp.client.ui.components.FileCardPanel;
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
    // Local transfer state is keyed by transfer ID, including simultaneous group transfers.
    private final Map<String, File> pendingOutgoingFiles = new ConcurrentHashMap<>();

    private final Map<String, FileCardPanel> fileCards = new HashMap<>();

    public MainChatWindow(ChatClient client, User currentUser) {
        this.client = client;
        this.currentUser = currentUser;

        setTitle("ChatApp | CLIENT — " + currentUser.getFullName() + " | Chat & Gửi file P2P");
        setSize(1000, 700);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                refreshMessagesView();
            }
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                for (FileCardPanel card : fileCards.values()) card.cancelTransfer();
                client.disconnect();
            }
        });

        initUI();
        this.client.setListener(this);
        client.joinRoom(currentTargetId);
        client.requestHistory(currentTargetId, true);
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

        JLabel lblSecurity = new JLabel("🔒 AES-128-GCM (khóa chung bản demo)");
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

        JLabel tip = new JLabel("🔒 Kết nối mã hóa an toàn AES-128-GCM (khóa chung bản demo)", SwingConstants.CENTER);
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

    private void stopTyping() {
        if (typingTimer != null) {
            typingTimer.stop();
            typingTimer = null;
            client.sendTypingStatus(currentTargetId, false);
        }
    }

    private void notifyTyping() {
        client.sendTypingStatus(currentTargetId, true);
        if (typingTimer == null) {
            String typingTarget = currentTargetId;
            typingTimer = new javax.swing.Timer(2000, e -> {
                client.sendTypingStatus(typingTarget, false);
                typingTimer.stop();
                typingTimer = null;
            });
            typingTimer.setRepeats(false);
            typingTimer.start();
        } else {
            typingTimer.restart();
        }
    }

    private void selectRoom(ChatRoom room) {
        stopTyping();
        currentTargetId = room.getRoomId();
        currentTargetTitle = "# " + room.getRoomName();
        isCurrentTargetGroup = true;

        lblTargetTitle.setText(currentTargetTitle);
        lblTargetSub.setText(room.getMembers().size() + " thành viên • " + (room.getDescription() != null ? room.getDescription() : ""));
        lblTyping.setText("");
        btnAttach.setEnabled(true);
        btnAttach.setToolTipText("Gửi file tới từng thành viên trong nhóm đang online");

        headerAvatarLabel.setText("#");
        headerAvatarBgColor = new Color(90, 80, 140);
        headerAvatarLabel.repaint();

        refreshMessagesView();
        client.joinRoom(room.getRoomId());
        client.requestHistory(currentTargetId,true);
    }

    private void selectUser(User user) {
        stopTyping();
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
        client.requestHistory(currentTargetId,false);
    }

    @Override
    public void onHistoryReceived(String target, List<ChatMessage> messages) {
        SwingUtilities.invokeLater(() -> {
            List<ChatMessage> existing = chatHistory.computeIfAbsent(target,k -> new ArrayList<>());
            java.util.Set<String> ids = new java.util.HashSet<>();
            for(ChatMessage msg : existing) ids.add(msg.getMessageId());
            List<ChatMessage> merged = new ArrayList<>();
            for(ChatMessage msg : messages) if(ids.add(msg.getMessageId())) merged.add(msg);
            merged.addAll(existing);
            merged.sort(java.util.Comparator.comparingLong(ChatMessage::getTimestamp));
            chatHistory.put(target,merged);
            if(target.equals(currentTargetId)) refreshMessagesView();
        });
    }

    @Override
    public void onServerError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,message,"Lỗi máy chủ",JOptionPane.ERROR_MESSAGE));
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty()) return;

        messageInputField.setText("");

        ChatMessage msg = isCurrentTargetGroup
                ? client.sendGroupMessage(currentTargetId, text)
                : client.sendDirectMessage(currentTargetId, text);
        addMessageToHistory(currentTargetId, msg);
    }

    private void addMessageToHistory(String targetId, ChatMessage msg) {
        List<ChatMessage> history = chatHistory.computeIfAbsent(targetId, k -> new ArrayList<>());
        for (ChatMessage existing : history) {
            if (existing.getMessageId().equals(msg.getMessageId())) return;
        }
        history.add(msg);
        if (targetId.equalsIgnoreCase(currentTargetId)) {
            appendMessageBubble(msg);
        }
    }

    private void appendMessageBubble(ChatMessage msg) {
        messagesPanel.add(messageView(msg));
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
                if (!fileCards.containsKey(msg.getMessageId()) && isFocused() && !msg.isGroup() && !msg.getSender().equals(currentUser.getUsername())
                        && msg.getStatus() != ChatMessage.Status.READ) {
                    client.sendMessageRead(msg.getSender(), msg.getMessageId());
                    msg.setStatus(ChatMessage.Status.READ);
                }
                messagesPanel.add(messageView(msg));
            }
            if (chatBodyCardLayout != null && chatBodyPanel != null) {
                chatBodyCardLayout.show(chatBodyPanel, CARD_MESSAGES);
            }
        }
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    // ================= XỬ LÝ TRUYỀN FILE P2P =================
    private JComponent messageView(ChatMessage msg) {
        FileCardPanel card = fileCards.get(msg.getMessageId());
        if (card != null) return card;
        ChatBubblePanel bubble = new ChatBubblePanel(msg, msg.getSender().equals(currentUser.getUsername()));
        messageBubbleMap.put(msg.getMessageId(), bubble);
        return bubble;
    }

    private FileCardPanel addFileCard(FileTransferRequest request, boolean outgoing) {
        FileCardPanel existing = fileCards.get(request.getTransferId());
        if (existing != null) return existing;
        FileCardPanel card = new FileCardPanel(request, outgoing);
        fileCards.put(request.getTransferId(), card);
        String target = request.getRoomId() != null ? request.getRoomId()
                : (outgoing ? request.getReceiver() : request.getSender());
        ChatMessage entry = new ChatMessage(request.getSender(), target, request.getFileName(), request.getRoomId() != null);
        entry.setMessageId(request.getTransferId());
        addMessageToHistory(target, entry);
        return card;
    }

    private void chooseAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file gửi tới " + currentTargetTitle);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        String target = currentTargetId;
        boolean group = isCurrentTargetGroup;
        btnAttach.setEnabled(false);
        new Thread(() -> {
            try {
                List<FileTransferRequest> requests = client.prepareFileRequests(target, group, file);
                SwingUtilities.invokeLater(() -> {
                    btnAttach.setEnabled(true);
                    for (FileTransferRequest request : requests) {
                        pendingOutgoingFiles.put(request.getTransferId(), file);
                        addFileCard(request, true);
                        client.sendFileRequest(request);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    btnAttach.setEnabled(true);
                    JOptionPane.showMessageDialog(this, e.getMessage(), "File", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "FileChecksumThread").start();
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
            if (isFocused() && !message.isGroup() && conversationKey.equalsIgnoreCase(currentTargetId)) {
                client.sendMessageRead(message.getSender(), message.getMessageId());
                message.setStatus(ChatMessage.Status.READ);
            }
        });
    }

    @Override
    public void onMessageStatusUpdated(String messageId, ChatMessage.Status status) {
        SwingUtilities.invokeLater(() -> {
            for (List<ChatMessage> messages : chatHistory.values()) {
                for (ChatMessage msg : messages) {
                    if (!messageId.equals(msg.getMessageId())) continue;
                    if (status.ordinal() > msg.getStatus().ordinal()) msg.setStatus(status);
                    ChatBubblePanel bubble = messageBubbleMap.get(messageId);
                    if (bubble != null) bubble.updateStatus(msg.getStatus());
                    return;
                }
            }
        });
    }

    @Override
    public void onUserTyping(String sender, String target, boolean typing) {
        SwingUtilities.invokeLater(() -> {
            if (sender.equalsIgnoreCase(currentTargetId) || target.equalsIgnoreCase(currentTargetId)) {
                lblTyping.setText(typing ? "✎ " + sender + " đang nhập tin..." : "");
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
            if (fileCards.containsKey(request.getTransferId())) return;
            FileCardPanel card = addFileCard(request, false);
            card.offer(() -> acceptFile(request, card), () -> {
                client.rejectFileRequest(request);
                card.onError("Bạn đã từ chối nhận file.");
            });
            String target = request.getRoomId() == null ? request.getSender() : request.getRoomId();
            if (!target.equals(currentTargetId)) {
                lblTyping.setText("Có file mới từ " + request.getSender() + " trong " + target + ". Mở cuộc trò chuyện để nhận.");
            }
        });
    }

    private void acceptFile(FileTransferRequest request, FileCardPanel card) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn thư mục nhận " + request.getFileName());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            P2PFileReceiver receiver = new P2PFileReceiver(request, chooser.getSelectedFile(), card);
            card.transferring(receiver::cancel);
            new Thread(receiver, "P2PReceive-" + request.getTransferId()).start();
            client.acceptFileRequest(request, receiver.getListeningPort());
        } catch (Exception e) {
            client.rejectFileRequest(request);
            card.onError("Không thể nhận: " + e.getMessage());
        }
    }

    @Override
    public void onFileTransferAccepted(FileTransferRequest request) {
        SwingUtilities.invokeLater(() -> {
            File file = pendingOutgoingFiles.remove(request.getTransferId());
            FileCardPanel card = fileCards.get(request.getTransferId());
            if (card == null) return;
            if (file == null || !file.isFile()) {
                card.onError("File nguồn không còn tồn tại.");
                return;
            }
            P2PFileSender sender = new P2PFileSender(request, file, card);
            card.transferring(sender::cancel);
            new Thread(sender, "P2PSend-" + request.getTransferId()).start();
        });
    }

    @Override
    public void onFileTransferRejected(FileTransferRequest request) {
        SwingUtilities.invokeLater(() -> {
            pendingOutgoingFiles.remove(request.getTransferId());
            FileCardPanel card = fileCards.get(request.getTransferId());
            if (card != null) card.onError(request.getStatus() == FileTransferRequest.Status.FAILED
                    ? "Không thể gửi: người nhận offline, đã rời nhóm hoặc máy chủ bận."
                    : request.getReceiver() + " đã từ chối.");
        });
    }

    @Override
    public void onConnectionLost() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ TCP Socket!", "Cảnh báo ngắt kết nối", JOptionPane.WARNING_MESSAGE);
            for (FileCardPanel card : fileCards.values()) card.cancelTransfer();
            dispose();
            System.exit(0);
        });
    }
}

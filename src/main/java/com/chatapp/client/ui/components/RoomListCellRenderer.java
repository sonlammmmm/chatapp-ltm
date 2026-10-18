package com.chatapp.client.ui.components;

import com.chatapp.common.model.ChatRoom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tùy biến hiển thị danh sách phòng chat nhóm (Group Chat).
 * Áp dụng Flyweight Pattern tái sử dụng component để cuộn 60 FPS mượt mà.
 */
public class RoomListCellRenderer extends JPanel implements ListCellRenderer<Object> {
    private final JLabel iconLabel;
    private final JLabel nameLabel;
    private final JLabel descLabel;

    private java.util.Map<String, Integer> unread = java.util.Collections.emptyMap();
    public RoomListCellRenderer(java.util.Map<String, Integer> unread) {
        this();
        this.unread = unread;
    }

    public RoomListCellRenderer() {
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(8, 12, 8, 12));
        setOpaque(true);

        iconLabel = new JLabel("#", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(90, 80, 140));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconLabel.setPreferredSize(new Dimension(36, 36));
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        infoPanel.setOpaque(false);

        nameLabel = new JLabel();
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(Color.WHITE);

        descLabel = new JLabel();
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descLabel.setForeground(new Color(156, 163, 175));

        infoPanel.add(nameLabel);
        infoPanel.add(descLabel);

        add(iconLabel, BorderLayout.WEST);
        add(infoPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (isSelected) {
            setBackground(new Color(37, 51, 70));
        } else {
            setBackground(new Color(24, 28, 36));
        }

        if (value instanceof ChatRoom) {
            ChatRoom room = (ChatRoom) value;
            int count = unread.getOrDefault(room.getRoomId(), 0);
            nameLabel.setText(room.getRoomName() + (count > 0 ? " (" + count + " mới)" : ""));
            int memberCount = room.getMembers() != null ? room.getMembers().size() : 0;
            String desc = room.getDescription() != null && !room.getDescription().isEmpty()
                    ? room.getDescription()
                    : "Phòng trò chuyện nhóm";
            descLabel.setText(memberCount + " thành viên • " + desc);
            setToolTipText(room.getRoomName() + " — " + memberCount + " thành viên • " + desc);
        }

        return this;
    }
}

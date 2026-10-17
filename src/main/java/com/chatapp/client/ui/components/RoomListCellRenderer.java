package com.chatapp.client.ui.components;

import com.chatapp.common.model.ChatRoom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tùy biến hiển thị danh sách phòng chat nhóm (Group Chat).
 */
public class RoomListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(6, 10, 6, 10));

        if (isSelected) {
            panel.setBackground(new Color(45, 60, 85));
        } else {
            panel.setBackground(new Color(28, 33, 42));
        }

        if (value instanceof ChatRoom) {
            ChatRoom room = (ChatRoom) value;

            // Biểu tượng nhóm #
            JLabel iconLabel = new JLabel("#", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(90, 80, 140));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            iconLabel.setPreferredSize(new Dimension(32, 32));
            iconLabel.setForeground(Color.WHITE);
            iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

            JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            infoPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(room.getRoomName());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nameLabel.setForeground(Color.WHITE);

            int memberCount = room.getMembers() != null ? room.getMembers().size() : 0;
            JLabel descLabel = new JLabel(memberCount + " thành viên" + (room.getDescription() != null && !room.getDescription().isEmpty() ? " • " + room.getDescription() : ""));
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            descLabel.setForeground(new Color(160, 170, 185));

            infoPanel.add(nameLabel);
            infoPanel.add(descLabel);

            panel.add(iconLabel, BorderLayout.WEST);
            panel.add(infoPanel, BorderLayout.CENTER);
        }

        return panel;
    }
}

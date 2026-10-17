package com.chatapp.client.ui.components;

import com.chatapp.common.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tùy biến hiển thị danh sách người dùng trong JList (Avatar, Tên đầy đủ, Trạng thái online).
 */
public class UserListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(6, 10, 6, 10));

        if (isSelected) {
            panel.setBackground(new Color(45, 60, 85));
        } else {
            panel.setBackground(new Color(28, 33, 42));
        }

        if (value instanceof User) {
            User user = (User) value;

            // Avatar tròn chữ cái đầu
            String firstLetter = user.getFullName() != null && !user.getFullName().isEmpty()
                    ? user.getFullName().substring(0, 1).toUpperCase()
                    : user.getUsername().substring(0, 1).toUpperCase();

            JLabel avatarLabel = new JLabel(firstLetter, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(70, 95, 135));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            avatarLabel.setPreferredSize(new Dimension(32, 32));
            avatarLabel.setForeground(Color.WHITE);
            avatarLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

            // Thông tin tên
            JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            infoPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(user.getFullName() != null ? user.getFullName() : user.getUsername());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nameLabel.setForeground(Color.WHITE);

            JLabel userLabel = new JLabel("@" + user.getUsername());
            userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            userLabel.setForeground(new Color(160, 170, 185));

            infoPanel.add(nameLabel);
            infoPanel.add(userLabel);

            // Chấm trạng thái xanh
            JLabel statusDot = new JLabel("●");
            statusDot.setForeground(new Color(40, 167, 69)); // Xanh lá trực tuyến
            statusDot.setFont(new Font("Segoe UI", Font.BOLD, 14));

            panel.add(avatarLabel, BorderLayout.WEST);
            panel.add(infoPanel, BorderLayout.CENTER);
            panel.add(statusDot, BorderLayout.EAST);
        }

        return panel;
    }
}

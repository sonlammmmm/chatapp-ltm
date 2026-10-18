/**
 * UserListCellRenderer.java — Vẽ từng mục người dùng với ảnh đại diện và trạng thái trong danh sách Swing.
 */
package com.chatapp.client.ui.components;

import com.chatapp.common.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tùy biến hiển thị danh sách người dùng trong JList (Avatar tròn, Tên đầy đủ, Trạng thái online).
 * Áp dụng Flyweight Pattern tái sử dụng component để tối ưu bộ nhớ và cuộn 60 FPS mượt mà.
 */
public class UserListCellRenderer extends JPanel implements ListCellRenderer<Object> {
    private final AvatarLabel avatarLabel;
    private final JLabel nameLabel;
    private final JLabel userLabel;
    private final JLabel statusDot;

    private static class AvatarLabel extends JLabel {
        private Color bgColor = new Color(0, 122, 255);

        /** Khởi tạo nhãn ảnh đại diện người dùng. */
        public AvatarLabel() {
            super("", SwingConstants.CENTER);
            setPreferredSize(new Dimension(36, 36));
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
        }

        /** Đặt màu nền ảnh đại diện và yêu cầu vẽ lại. */
        public void setBgColor(Color color) {
            this.bgColor = color;
        }

        /** Vẽ phần tùy biến của thành phần Swing. */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private java.util.Map<String, Integer> unread = java.util.Collections.emptyMap();
    /** Khởi tạo đối tượng UserListCellRenderer với dữ liệu và tài nguyên ban đầu. */
    public UserListCellRenderer(java.util.Map<String, Integer> unread) {
        this();
        this.unread = unread;
    }

    /** Khởi tạo đối tượng UserListCellRenderer với dữ liệu và tài nguyên ban đầu. */
    public UserListCellRenderer() {
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(8, 12, 8, 12));
        setOpaque(true);

        avatarLabel = new AvatarLabel();

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        infoPanel.setOpaque(false);

        nameLabel = new JLabel();
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(Color.WHITE);

        userLabel = new JLabel();
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        userLabel.setForeground(new Color(156, 163, 175));

        infoPanel.add(nameLabel);
        infoPanel.add(userLabel);

        statusDot = new JLabel("●");
        statusDot.setForeground(new Color(40, 167, 69)); // Xanh lá trực tuyến
        statusDot.setFont(new Font("Segoe UI", Font.BOLD, 14));

        add(avatarLabel, BorderLayout.WEST);
        add(infoPanel, BorderLayout.CENTER);
        add(statusDot, BorderLayout.EAST);
    }

    /** Tạo nội dung hiển thị cho một mục danh sách và trạng thái được chọn. */
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (isSelected) {
            setBackground(new Color(37, 51, 70)); // Highlight navy hiện đại
        } else {
            setBackground(new Color(24, 28, 36)); // Sidebar background
        }

        if (value instanceof User) {
            User user = (User) value;
            String firstLetter = user.getFullName() != null && !user.getFullName().isEmpty()
                    ? user.getFullName().substring(0, 1).toUpperCase()
                    : (user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername().substring(0, 1).toUpperCase() : "?");
            avatarLabel.setText(firstLetter);

            // Phối màu avatar theo hashcode của username
            int hash = Math.abs(user.getUsername() != null ? user.getUsername().hashCode() : 0);
            Color[] avatarColors = {
                new Color(0, 122, 255),   // Xanh Apple
                new Color(16, 185, 129),  // Xanh lục ngọc
                new Color(139, 92, 246),  // Tím hoa cà
                new Color(245, 158, 11),  // Vàng cam
                new Color(236, 72, 153)   // Hồng phấn
            };
            avatarLabel.setBgColor(avatarColors[hash % avatarColors.length]);

            nameLabel.setText(user.getFullName() != null ? user.getFullName() : user.getUsername());
            userLabel.setText("@" + user.getUsername() + (user.getIpAddress() != null ? " • " + user.getIpAddress() : ""));
            int count = unread.getOrDefault(user.getUsername(), 0);
            statusDot.setText(count > 0 ? String.valueOf(count) : "●");
            statusDot.setForeground(count > 0 ? Color.WHITE : new Color(40, 167, 69));
            statusDot.setVisible(count > 0 || user.isOnline());
            userLabel.setText("@" + user.getUsername() + (user.isOnline() ? " • Trực tuyến" : " • Ngoại tuyến"));
        }

        return this;
    }
}

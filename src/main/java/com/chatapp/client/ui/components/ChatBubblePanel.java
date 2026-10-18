/**
 * ChatBubblePanel.java — Hiển thị nội dung, thời gian và trạng thái của một tin nhắn.
 */
package com.chatapp.client.ui.components;

import com.chatapp.common.model.ChatMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Hiển thị tin nhắn dạng bong bóng chat (Chat Bubble) hiện đại.
 * Phân biệt tin nhắn gửi đi (căn phải, màu xanh) và tin nhắn nhận được (căn trái, màu xám).
 */
public class ChatBubblePanel extends JPanel {
    private final ChatMessage message;
    private final boolean isSelf;
    private JLabel statusLabel;

    /** Khởi tạo đối tượng ChatBubblePanel với dữ liệu và tài nguyên ban đầu. */
    public ChatBubblePanel(ChatMessage message, boolean isSelf) {
        this.message = message;
        this.isSelf = isSelf;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(4, 10, 4, 10));

        initComponent();
    }

    /** Tạo nội dung và bố cục của thành phần giao diện. */
    private void initComponent() {
        JPanel container = new JPanel(new BorderLayout(5, 2));
        container.setOpaque(false);

        // Header: Tên người gửi (nếu là tin nhắn nhóm hoặc từ người khác)
        if (!isSelf && message.isGroup()) {
            String displayName = message.getSenderFullName() != null ? message.getSenderFullName() : message.getSender();
            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            nameLabel.setForeground(new Color(100, 160, 240));
            container.add(nameLabel, BorderLayout.NORTH);
        }

        // Nội dung tin nhắn
        JTextArea textArea = new JTextArea(message.getContent());
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Ràng buộc số cột để tự động xuống dòng, chống tràn ngang
        int textLength = message.getContent() != null ? message.getContent().length() : 0;
        if (textLength > 40) {
            textArea.setColumns(36);
        }

        textArea.setSize(textArea.getPreferredSize().width, Short.MAX_VALUE);

        // Bo góc nền bong bóng (12px chuẩn Flat UI)
        JPanel bubble = new JPanel(new BorderLayout()) {
            /** Vẽ phần tùy biến của thành phần Swing. */
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelf) {
                    g2.setColor(new Color(0, 78, 160)); // Màu xanh Apple/Telegram
                } else {
                    g2.setColor(new Color(50, 56, 68)); // Màu xám tối hiện đại
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);

        if (isSelf) {
            textArea.setForeground(Color.WHITE);
        } else {
            textArea.setForeground(new Color(235, 240, 245));
        }
        bubble.add(textArea, BorderLayout.CENTER);

        // Footer: Thời gian & Trạng thái [Đã gửi / Đã nhận / Đã xem]
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        footer.setOpaque(false);

        JLabel timeLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(message.getTimestamp())));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timeLabel.setForeground(new Color(225, 235, 245));
        footer.add(timeLabel);

        if (isSelf && !message.isGroup()) {
            statusLabel = new JLabel(getStatusText(message.getStatus()));
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            statusLabel.setForeground(new Color(150, 220, 255));
            footer.add(statusLabel);
        }

        bubble.add(footer, BorderLayout.SOUTH);
        container.add(bubble, BorderLayout.CENTER);

        // Canh lề phải cho mình, lề trái cho người khác
        JPanel alignmentWrapper = new JPanel(new FlowLayout(isSelf ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        alignmentWrapper.setOpaque(false);
        alignmentWrapper.add(container);

        add(alignmentWrapper, BorderLayout.CENTER);
    }

    /** Chuyển trạng thái tin nhắn thành ký hiệu hiển thị. */
    private String getStatusText(ChatMessage.Status status) {
        if (status == null) return "Đã gửi";
        switch (status) {
            case DELIVERED:
                return "Đã nhận";
            case READ:
                return "Đã xem";
            default:
                return "Đã gửi";
        }
    }

    /** Cập nhật trạng thái gửi, nhận hoặc đọc trên bong bóng tin nhắn. */
    public void updateStatus(ChatMessage.Status status) {
        if (statusLabel != null) {
            statusLabel.setText(getStatusText(status));
            if (status == ChatMessage.Status.READ) {
                statusLabel.setForeground(new Color(170, 245, 185)); // Màu xanh lá khi đã xem
            }
            revalidate();
            repaint();
        }
    }

    /** Lấy kích thước tối thiểu cho bố cục thành phần Swing. */
    @Override public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    /** Lấy kích thước tối đa cho bố cục thành phần Swing. */
    @Override public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** Lấy mã định danh tin nhắn. */
    public String getMessageId() {
        return message.getMessageId();
    }
}

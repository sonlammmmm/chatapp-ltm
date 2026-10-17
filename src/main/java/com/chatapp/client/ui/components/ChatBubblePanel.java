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

    public ChatBubblePanel(ChatMessage message, boolean isSelf) {
        this.message = message;
        this.isSelf = isSelf;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(4, 10, 4, 10));

        initComponent();
    }

    private void initComponent() {
        JPanel container = new JPanel(new BorderLayout(5, 2));
        container.setOpaque(false);

        // Header: Tên người gửi (nếu là tin nhắn nhóm hoặc từ người khác)
        if (!isSelf) {
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

        // Bo góc nền bong bóng
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelf) {
                    g2.setColor(new Color(0, 122, 255)); // Màu xanh Apple/Telegram
                } else {
                    g2.setColor(new Color(50, 56, 68)); // Màu xám tối hiện đại
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
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

        JLabel timeLabel = new JLabel(message.getFormattedTime());
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(new Color(180, 185, 195));
        footer.add(timeLabel);

        if (isSelf && !message.isGroup()) {
            statusLabel = new JLabel(getStatusText(message.getStatus()));
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
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

    private String getStatusText(ChatMessage.Status status) {
        if (status == null) return "✓ Đã gửi";
        switch (status) {
            case DELIVERED:
                return "✓✓ Đã nhận";
            case READ:
                return "✓✓ Đã xem";
            default:
                return "✓ Đã gửi";
        }
    }

    public void updateStatus(ChatMessage.Status status) {
        if (statusLabel != null) {
            statusLabel.setText(getStatusText(status));
            if (status == ChatMessage.Status.READ) {
                statusLabel.setForeground(new Color(52, 199, 89)); // Màu xanh lá khi đã xem
            }
            revalidate();
            repaint();
        }
    }

    public String getMessageId() {
        return message.getMessageId();
    }
}

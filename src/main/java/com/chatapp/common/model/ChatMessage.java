package com.chatapp.common.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Đối tượng biểu diễn tin nhắn văn bản (Chat 1-1 hoặc Chat Nhóm).
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        SENT,       // Đã gửi lên server
        DELIVERED,  // Đã chuyển đến máy người nhận
        READ        // Người nhận đã xem
    }

    private String messageId;
    private String sender;
    private String senderFullName;
    private String recipient; // username hoặc roomId
    private String content;   // Nội dung tin nhắn (đã mã hóa hoặc giải mã)
    private boolean isGroup;
    private long timestamp;
    private Status status;
    private boolean isEncrypted;

    public ChatMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.status = Status.SENT;
    }

    public ChatMessage(String sender, String recipient, String content, boolean isGroup) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.isGroup = isGroup;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSenderFullName() {
        return senderFullName;
    }

    public void setSenderFullName(String senderFullName) {
        this.senderFullName = senderFullName;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }
}

/**
 * ChatMessage.java — Biểu diễn tin nhắn, người gửi, người nhận, thời gian và trạng thái xử lý.
 */
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

    /** Khởi tạo đối tượng ChatMessage với dữ liệu và tài nguyên ban đầu. */
    public ChatMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.status = Status.SENT;
    }

    /** Khởi tạo đối tượng ChatMessage với dữ liệu và tài nguyên ban đầu. */
    public ChatMessage(String sender, String recipient, String content, boolean isGroup) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.isGroup = isGroup;
    }

    /** Lấy thời gian tin nhắn đã định dạng để hiển thị. */
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    // Getters and Setters
    /** Lấy mã định danh tin nhắn. */
    public String getMessageId() {
        return messageId;
    }

    /** Cập nhật mã định danh tin nhắn. */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /** Lấy tên tài khoản người gửi. */
    public String getSender() {
        return sender;
    }

    /** Cập nhật tên tài khoản người gửi. */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /** Lấy họ tên người gửi. */
    public String getSenderFullName() {
        return senderFullName;
    }

    /** Cập nhật họ tên người gửi. */
    public void setSenderFullName(String senderFullName) {
        this.senderFullName = senderFullName;
    }

    /** Lấy người nhận hoặc mã phòng đích. */
    public String getRecipient() {
        return recipient;
    }

    /** Cập nhật người nhận hoặc mã phòng đích. */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    /** Lấy nội dung tin nhắn. */
    public String getContent() {
        return content;
    }

    /** Cập nhật nội dung tin nhắn. */
    public void setContent(String content) {
        this.content = content;
    }

    /** Cho biết cờ tin nhắn nhóm. */
    public boolean isGroup() {
        return isGroup;
    }

    /** Cập nhật cờ tin nhắn nhóm. */
    public void setGroup(boolean group) {
        isGroup = group;
    }

    /** Lấy thời điểm tạo tính bằng mili giây Unix. */
    public long getTimestamp() {
        return timestamp;
    }

    /** Cập nhật thời điểm tạo tính bằng mili giây Unix. */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Lấy trạng thái xử lý. */
    public Status getStatus() {
        return status;
    }

    /** Cập nhật trạng thái xử lý. */
    public void setStatus(Status status) {
        this.status = status;
    }

    /** Cho biết cờ nội dung đã mã hóa. */
    public boolean isEncrypted() {
        return isEncrypted;
    }

    /** Cập nhật cờ nội dung đã mã hóa. */
    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }
}

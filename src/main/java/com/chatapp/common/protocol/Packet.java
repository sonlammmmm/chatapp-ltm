package com.chatapp.common.protocol;

import com.chatapp.common.model.PacketType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;

/**
 * Gói tin chuẩn trao đổi giữa Client và Server qua TCP Socket.
 * Sử dụng Gson để tuần tự hóa (Serialize) và giải tuần tự (Deserialize).
 */
public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new GsonBuilder().create();

    private PacketType type;
    private String sender;
    private String recipient;
    private String payload; // Dữ liệu JSON hoặc chuỗi văn bản (có thể mã hóa AES)
    private long timestamp;

    public Packet() {
        this.timestamp = System.currentTimeMillis();
    }

    public Packet(PacketType type, String sender, String payload) {
        this();
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    public Packet(PacketType type, String sender, String recipient, String payload) {
        this(type, sender, payload);
        this.recipient = recipient;
    }

    // Tiện ích chuyển đổi sang JSON
    public String toJson() {
        return gson.toJson(this);
    }

    // Tiện ích tạo đối tượng Packet từ chuỗi JSON
    public static Packet fromJson(String json) {
        try {
            return gson.fromJson(json, Packet.class);
        } catch (Exception e) {
            return null;
        }
    }

    // Tiện ích đóng gói đối tượng bất kỳ thành payload JSON
    public static String serializePayload(Object obj) {
        return gson.toJson(obj);
    }

    // Tiện ích giải mã payload JSON thành kiểu Class mong muốn
    public <T> T deserializePayload(Class<T> classOfT) {
        try {
            return gson.fromJson(this.payload, classOfT);
        } catch (Exception e) {
            return null;
        }
    }

    // Getters and Setters
    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "type=" + type +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", payload='" + (payload != null && payload.length() > 50 ? payload.substring(0, 50) + "..." : payload) + '\'' +
                '}';
    }
}

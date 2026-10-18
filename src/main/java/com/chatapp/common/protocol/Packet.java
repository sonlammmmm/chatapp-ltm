/**
 * Packet.java — Đóng gói dữ liệu trao đổi Client–Server và chuyển đổi đối tượng sang hoặc từ JSON.
 */
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

    /** Khởi tạo đối tượng Packet với dữ liệu và tài nguyên ban đầu. */
    public Packet() {
        this.timestamp = System.currentTimeMillis();
    }

    /** Khởi tạo đối tượng Packet với dữ liệu và tài nguyên ban đầu. */
    public Packet(PacketType type, String sender, String payload) {
        this();
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    /** Khởi tạo đối tượng Packet với dữ liệu và tài nguyên ban đầu. */
    public Packet(PacketType type, String sender, String recipient, String payload) {
        this(type, sender, payload);
        this.recipient = recipient;
    }

    // Tiện ích chuyển đổi sang JSON
    /** Chuyển gói tin hiện tại thành chuỗi JSON. */
    public String toJson() {
        return gson.toJson(this);
    }

    // Tiện ích tạo đối tượng Packet từ chuỗi JSON
    /** Đọc gói tin từ JSON, trả null nếu không thể phân tích. */
    public static Packet fromJson(String json) {
        try {
            return gson.fromJson(json, Packet.class);
        } catch (Exception e) {
            return null;
        }
    }

    // Tiện ích đóng gói đối tượng bất kỳ thành payload JSON
    /** Chuyển đối tượng dữ liệu thành payload JSON. */
    public static String serializePayload(Object obj) {
        return gson.toJson(obj);
    }

    // Tiện ích giải mã payload JSON thành kiểu Class mong muốn
    /** Đọc payload JSON thành kiểu yêu cầu, trả null khi chuyển đổi lỗi. */
    public <T> T deserializePayload(Class<T> classOfT) {
        try {
            return gson.fromJson(this.payload, classOfT);
        } catch (Exception e) {
            return null;
        }
    }

    // Getters and Setters
    /** Lấy loại gói tin. */
    public PacketType getType() {
        return type;
    }

    /** Cập nhật loại gói tin. */
    public void setType(PacketType type) {
        this.type = type;
    }

    /** Lấy tên tài khoản người gửi. */
    public String getSender() {
        return sender;
    }

    /** Cập nhật tên tài khoản người gửi. */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /** Lấy người nhận hoặc mã phòng đích. */
    public String getRecipient() {
        return recipient;
    }

    /** Cập nhật người nhận hoặc mã phòng đích. */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    /** Lấy dữ liệu bên trong gói tin. */
    public String getPayload() {
        return payload;
    }

    /** Cập nhật dữ liệu bên trong gói tin. */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /** Lấy thời điểm tạo tính bằng mili giây Unix. */
    public long getTimestamp() {
        return timestamp;
    }

    /** Cập nhật thời điểm tạo tính bằng mili giây Unix. */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Tạo chuỗi biểu diễn đối tượng phục vụ hiển thị hoặc chẩn đoán. */
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

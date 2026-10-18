/**
 * User.java — Biểu diễn thông tin hiển thị, trạng thái kết nối và địa chỉ mạng của người dùng.
 */
package com.chatapp.common.model;

import java.io.Serializable;

/**
 * Đối tượng biểu diễn người dùng trong hệ thống chat.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String fullName;
    private boolean isOnline;
    private String ipAddress;
    private int p2pPort; // Cổng P2P nếu đang mở để nhận file

    /** Khởi tạo đối tượng User với dữ liệu và tài nguyên ban đầu. */
    public User() {
    }

    /** Khởi tạo đối tượng User với dữ liệu và tài nguyên ban đầu. */
    public User(String username, String fullName, boolean isOnline) {
        this.username = username;
        this.fullName = fullName;
        this.isOnline = isOnline;
    }

    /** Lấy tên tài khoản. */
    public String getUsername() {
        return username;
    }

    /** Cập nhật tên tài khoản. */
    public void setUsername(String username) {
        this.username = username;
    }

    /** Lấy họ tên hiển thị. */
    public String getFullName() {
        return fullName;
    }

    /** Cập nhật họ tên hiển thị. */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** Cho biết trạng thái trực tuyến. */
    public boolean isOnline() {
        return isOnline;
    }

    /** Cập nhật trạng thái trực tuyến. */
    public void setOnline(boolean online) {
        isOnline = online;
    }

    /** Lấy địa chỉ IP. */
    public String getIpAddress() {
        return ipAddress;
    }

    /** Cập nhật địa chỉ IP. */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /** Lấy cổng truyền tệp P2P. */
    public int getP2pPort() {
        return p2pPort;
    }

    /** Cập nhật cổng truyền tệp P2P. */
    public void setP2pPort(int p2pPort) {
        this.p2pPort = p2pPort;
    }

    /** Tạo chuỗi biểu diễn đối tượng phục vụ hiển thị hoặc chẩn đoán. */
    @Override
    public String toString() {
        return fullName != null && !fullName.isEmpty() ? fullName + " (" + username + ")" : username;
    }
}

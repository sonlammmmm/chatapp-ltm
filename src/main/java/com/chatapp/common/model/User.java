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

    public User() {
    }

    public User(String username, String fullName, boolean isOnline) {
        this.username = username;
        this.fullName = fullName;
        this.isOnline = isOnline;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getP2pPort() {
        return p2pPort;
    }

    public void setP2pPort(int p2pPort) {
        this.p2pPort = p2pPort;
    }

    @Override
    public String toString() {
        return fullName != null && !fullName.isEmpty() ? fullName + " (" + username + ")" : username;
    }
}

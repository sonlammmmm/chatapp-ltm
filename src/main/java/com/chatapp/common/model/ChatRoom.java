/**
 * ChatRoom.java — Biểu diễn phòng chat nhóm và danh sách thành viên.
 */
package com.chatapp.common.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Quản lý đối tượng phòng chat nhóm (Chat Room).
 */
public class ChatRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    private String roomId;
    private String roomName;
    private String creator;
    private Set<String> members = new HashSet<>();
    private String description;

    /** Khởi tạo đối tượng ChatRoom với dữ liệu và tài nguyên ban đầu. */
    public ChatRoom() {
    }

    /** Khởi tạo đối tượng ChatRoom với dữ liệu và tài nguyên ban đầu. */
    public ChatRoom(String roomId, String roomName, String creator, String description) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.creator = creator;
        this.description = description;
        this.members.add(creator);
    }

    /** Lấy mã phòng chat. */
    public String getRoomId() {
        return roomId;
    }

    /** Cập nhật mã phòng chat. */
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    /** Lấy tên phòng chat. */
    public String getRoomName() {
        return roomName;
    }

    /** Cập nhật tên phòng chat. */
    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    /** Lấy tài khoản tạo phòng. */
    public String getCreator() {
        return creator;
    }

    /** Cập nhật tài khoản tạo phòng. */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /** Lấy danh sách thành viên. */
    public Set<String> getMembers() {
        return members;
    }

    /** Cập nhật danh sách thành viên. */
    public void setMembers(Set<String> members) {
        this.members = members;
    }

    /** Thêm tài khoản vào danh sách thành viên phòng. */
    public void addMember(String username) {
        this.members.add(username);
    }

    /** Xóa tài khoản khỏi danh sách thành viên phòng. */
    public void removeMember(String username) {
        this.members.remove(username);
    }

    /** Kiểm tra tài khoản có thuộc phòng chat hay không. */
    public boolean hasMember(String username) {
        return this.members.contains(username);
    }

    /** Lấy mô tả phòng chat. */
    public String getDescription() {
        return description;
    }

    /** Cập nhật mô tả phòng chat. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Tạo chuỗi biểu diễn đối tượng phục vụ hiển thị hoặc chẩn đoán. */
    @Override
    public String toString() {
        return roomName + " (" + members.size() + " thành viên)";
    }
}

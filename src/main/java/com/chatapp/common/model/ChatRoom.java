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

    public ChatRoom() {
    }

    public ChatRoom(String roomId, String roomName, String creator, String description) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.creator = creator;
        this.description = description;
        this.members.add(creator);
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void setMembers(Set<String> members) {
        this.members = members;
    }

    public void addMember(String username) {
        this.members.add(username);
    }

    public void removeMember(String username) {
        this.members.remove(username);
    }

    public boolean hasMember(String username) {
        return this.members.contains(username);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return roomName + " (" + members.size() + " thành viên)";
    }
}

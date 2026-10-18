package com.chatapp.server;

import com.chatapp.common.model.ChatRoom;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý danh sách phòng chat nhóm (Group Chat).
 */
public class RoomManager {
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    public RoomManager() {
        // Tạo phòng mặc định "Sảnh Chung"
        ChatRoom lobby = new ChatRoom("lobby", "Sảnh Chung", "System", "Phòng trò chuyện tự do cho tất cả thành viên");
        rooms.put("lobby", lobby);

        ChatRoom room1 = new ChatRoom("nhom03", "Nhóm 03 - Đồ án LTM", "giahuy", "Phòng thảo luận đề tài đồ án lập trình mạng");
        rooms.put("nhom03", room1);

    }

    public synchronized ChatRoom createRoom(String roomName, String creator, String description) {
        String roomId = "room_" + UUID.randomUUID().toString().substring(0, 8);
        ChatRoom room = new ChatRoom(roomId, roomName, creator, description);
        rooms.put(roomId, room);
        return room;
    }

    public synchronized boolean joinRoom(String roomId, String username) {
        ChatRoom room = rooms.get(roomId);
        if (room != null) {
            room.addMember(username);
            return true;
        }
        return false;
    }

    public synchronized boolean leaveRoom(String roomId, String username) {
        ChatRoom room = rooms.get(roomId);
        if (room != null && !roomId.equals("lobby")) {
            room.removeMember(username);
            return true;
        }
        return false;
    }

    public synchronized void restoreRooms(List<ChatRoom> saved) {
        for (ChatRoom room : saved) rooms.put(room.getRoomId(), room);
    }

    public ChatRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<ChatRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    public Set<String> getRoomMembers(String roomId) {
        ChatRoom room = rooms.get(roomId);
        return room != null ? room.getMembers() : Collections.emptySet();
    }
}

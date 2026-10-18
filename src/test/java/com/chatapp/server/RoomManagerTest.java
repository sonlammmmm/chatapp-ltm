package com.chatapp.server;

import com.chatapp.common.model.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RoomManagerTest {

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager = new RoomManager();
    }

    @Test
    @DisplayName("Kiểm tra khởi tạo phòng mặc định (Sảnh chung và Nhóm 03)")
    void testInitialRooms() {
        assertNotNull(roomManager.getRoom("lobby"));
        assertNotNull(roomManager.getRoom("nhom03"));
        assertEquals("Sảnh Chung", roomManager.getRoom("lobby").getRoomName());
    }

    @Test
    @DisplayName("Kiểm tra tạo phòng mới và quản lý thành viên")
    void testCreateAndJoinRoom() {
        ChatRoom room = roomManager.createRoom("Lập Trình Mạng", "sonlam", "Phòng học tập");
        assertNotNull(room);
        assertTrue(room.getRoomId().startsWith("room_"));
        assertTrue(room.hasMember("sonlam"));

        // Người khác tham gia phòng
        boolean joined = roomManager.joinRoom(room.getRoomId(), "ngochuy");
        assertTrue(joined);
        assertTrue(room.hasMember("ngochuy"));

        // Rời phòng
        boolean left = roomManager.leaveRoom(room.getRoomId(), "ngochuy");
        assertTrue(left);
        assertFalse(room.hasMember("ngochuy"));

        // Không cho phép rời Sảnh Chung
        boolean leaveLobby = roomManager.leaveRoom("lobby", "sonlam");
        assertFalse(leaveLobby, "Không được phép rời khỏi Sảnh Chung (lobby)");
    }
}

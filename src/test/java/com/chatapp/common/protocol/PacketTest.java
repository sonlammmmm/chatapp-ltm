package com.chatapp.common.protocol;

import com.chatapp.common.model.ChatMessage;
import com.chatapp.common.model.PacketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacketTest {

    @Test
    @DisplayName("Kiểm tra tuần tự hóa và giải tuần tự Packet JSON")
    void testPacketSerialization() {
        Packet packet = new Packet(PacketType.MSG_DIRECT, "alice", "bob", "Hello Bob!");
        String json = packet.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"MSG_DIRECT\""));
        assertTrue(json.contains("\"sender\":\"alice\""));
        assertTrue(json.contains("\"recipient\":\"bob\""));

        Packet deserialized = Packet.fromJson(json);
        assertNotNull(deserialized);
        assertEquals(PacketType.MSG_DIRECT, deserialized.getType());
        assertEquals("alice", deserialized.getSender());
        assertEquals("bob", deserialized.getRecipient());
        assertEquals("Hello Bob!", deserialized.getPayload());
    }

    @Test
    @DisplayName("Kiểm tra đóng gói và giải nén payload đối tượng phức tạp")
    void testObjectPayloadSerialization() {
        ChatMessage msg = new ChatMessage("giahuy", "nhom03", "Test nội dung chat nhóm", true);
        msg.setSenderFullName("Trần Gia Huy");

        Packet packet = new Packet(PacketType.MSG_GROUP, "giahuy", "nhom03", Packet.serializePayload(msg));
        String json = packet.toJson();

        Packet receivedPacket = Packet.fromJson(json);
        assertNotNull(receivedPacket);

        ChatMessage unpackedMsg = receivedPacket.deserializePayload(ChatMessage.class);
        assertNotNull(unpackedMsg);
        assertEquals("giahuy", unpackedMsg.getSender());
        assertEquals("Trần Gia Huy", unpackedMsg.getSenderFullName());
        assertEquals("nhom03", unpackedMsg.getRecipient());
        assertEquals("Test nội dung chat nhóm", unpackedMsg.getContent());
        assertTrue(unpackedMsg.isGroup());
    }
}

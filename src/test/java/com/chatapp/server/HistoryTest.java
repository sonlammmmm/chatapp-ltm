package com.chatapp.server;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.server.db.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {
    @TempDir Path dir;
    @Test void historySurvivesNewServerProcessAndIsScoped() throws Exception {
        for (String phase : new String[]{"write", "read"}) {
            Path log = dir.resolve(phase + ".log");
            Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), Probe.class.getName(), phase)
                    .directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(p.waitFor(20, TimeUnit.SECONDS));
                assertEquals(0, p.exitValue(), new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8));
            } finally { p.destroyForcibly(); }
        }
    }
    public static class Probe {
        public static void main(String[] args) throws Exception {
            ChatServer server = new ChatServer(0);
            server.start();
            try (ServerFlowTest.Probe.Peer a = new ServerFlowTest.Probe.Peer(server.getPort());
                 ServerFlowTest.Probe.Peer b = new ServerFlowTest.Probe.Peer(server.getPort());
                 ServerFlowTest.Probe.Peer c = new ServerFlowTest.Probe.Peer(server.getPort())) {
                a.login("giahuy"); b.login("sonlam"); c.login("ngochuy");
                if (args[0].equals("write")) {
                    ChatMessage direct = new ChatMessage("fake", "sonlam", "history direct", false);
                    a.send(new Packet(PacketType.MSG_DIRECT, "fake", "sonlam", Packet.serializePayload(direct)));
                    b.until(PacketType.MSG_DIRECT);
                    a.send(new Packet(PacketType.ROOM_CREATE,"giahuy","{\"roomName\":\"Saved room\"}"));
                    String room = a.until(PacketType.ROOM_CREATE_SUCCESS).deserializePayload(ChatRoom.class).getRoomId();
                    Files.writeString(Path.of("room.txt"),room);
                    a.send(new Packet(PacketType.MSG_GROUP,"giahuy",room,Packet.serializePayload(new ChatMessage("fake",room,"group history",true))));
                    a.send(new Packet(PacketType.HISTORY_REQUEST,"giahuy",room,"true"));
                    if(a.until(PacketType.HISTORY_RESPONSE).deserializePayload(ChatMessage[].class).length != 1) throw new AssertionError("Group not saved");
                } else {
                    a.send(new Packet(PacketType.HISTORY_REQUEST,"giahuy","sonlam","false"));
                    ChatMessage[] history = a.until(PacketType.HISTORY_RESPONSE).deserializePayload(ChatMessage[].class);
                    if(history.length != 1 || !history[0].getSender().equals("giahuy") || !history[0].getContent().equals("history direct")) throw new AssertionError("Direct history lost");
                    b.send(new Packet(PacketType.HISTORY_REQUEST,"sonlam","giahuy","false"));
                    if(b.until(PacketType.HISTORY_RESPONSE).deserializePayload(ChatMessage[].class).length != 1) throw new AssertionError("Receiver history lost");
                    c.send(new Packet(PacketType.HISTORY_REQUEST,"giahuy","sonlam","false"));
                    if(c.until(PacketType.HISTORY_RESPONSE).deserializePayload(ChatMessage[].class).length != 0) throw new AssertionError("Private history leaked");
                    String room = Files.readString(Path.of("room.txt"));
                    c.send(new Packet(PacketType.HISTORY_REQUEST,"ngochuy",room,"true"));
                    c.noPacket(PacketType.HISTORY_RESPONSE);
                    a.send(new Packet(PacketType.HISTORY_REQUEST,"giahuy",room,"true"));
                    if(a.until(PacketType.HISTORY_RESPONSE).deserializePayload(ChatMessage[].class).length != 1) throw new AssertionError("Room history lost");
                    DatabaseManager db = server.getDbManager();
                    for(int i=0;i<205;i++) db.saveMessage(new ChatMessage("sonlam","giahuy","item " + i,false));
                    java.util.List<ChatMessage> recent = db.loadHistory("giahuy","sonlam",false);
                    if(recent.size()!=200 || !recent.get(0).getContent().equals("item 5") || !recent.get(199).getContent().equals("item 204")) throw new AssertionError("History order or limit");
                }
            } finally { server.stop(); }
        }
    }
}

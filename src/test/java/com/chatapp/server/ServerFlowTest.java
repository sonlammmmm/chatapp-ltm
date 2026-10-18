package com.chatapp.server;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.security.AESUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ServerFlowTest {
    @TempDir Path temporaryDirectory;

    @Test void serverFlowsInIsolatedDatabase() throws Exception {
        Path log = temporaryDirectory.resolve("probe.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .directory(temporaryDirectory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Server probe must finish");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
        } finally { process.destroyForcibly(); }
    }

    public static class Probe {
        static class Peer implements AutoCloseable {
            Socket socket;
            BufferedReader in;
            BufferedWriter out;
            Peer(int port) throws Exception {
                socket = new Socket("127.0.0.1", port);
                socket.setSoTimeout(2000);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            }
            void send(Packet p) throws Exception { out.write(AESUtil.encrypt(p.toJson())); out.newLine(); out.flush(); }
            Packet until(PacketType type) throws Exception {
                for (int i = 0; i < 20; i++) {
                    Packet p = Packet.fromJson(AESUtil.decrypt(in.readLine()));
                    if (p.getType() == type) return p;
                }
                throw new AssertionError("Missing packet: " + type);
            }
            void login(String user) throws Exception {
                send(new Packet(PacketType.AUTH_LOGIN, user, "{\"username\":\"" + user + "\",\"password\":\"123456\"}"));
                until(PacketType.AUTH_LOGIN_SUCCESS);
            }
            void noPacket(PacketType type) throws Exception {
                socket.setSoTimeout(250);
                try { until(type); throw new AssertionError("Unexpected packet: " + type); }
                catch (SocketTimeoutException expected) { }
                finally { socket.setSoTimeout(2000); }
            }
            public void close() throws Exception { socket.close(); }
        }
        public static void main(String[] args) throws Exception {
            ChatServer server = new ChatServer(0);
            server.start();
            try (Peer a = new Peer(server.getPort()); Peer b = new Peer(server.getPort()); Peer c = new Peer(server.getPort()); Peer guest = new Peer(server.getPort())) {
                a.login("giahuy"); b.login("sonlam");
                Packet list;
                do { list = a.until(PacketType.USER_LIST_UPDATE); } while (!list.getPayload().contains("sonlam"));
                c.login("ngochuy");
                ChatMessage spoof = new ChatMessage("ngochuy", "giahuy", "hello", false);
                b.send(new Packet(PacketType.MSG_DIRECT, "ngochuy", "giahuy", Packet.serializePayload(spoof)));
                ChatMessage delivered = a.until(PacketType.MSG_DIRECT).deserializePayload(ChatMessage.class);
                if (!"sonlam".equals(delivered.getSender())) throw new AssertionError("Sender spoof accepted");
                a.send(new Packet(PacketType.ROOM_CREATE, "giahuy", "{\"roomName\":\"private\"}"));
                String room = a.until(PacketType.ROOM_CREATE_SUCCESS).deserializePayload(ChatRoom.class).getRoomId();
                b.send(new Packet(PacketType.MSG_GROUP, "sonlam", room, Packet.serializePayload(new ChatMessage("sonlam", room, "blocked", true))));
                a.noPacket(PacketType.MSG_GROUP);
                ChatMessage message = new ChatMessage("giahuy", "sonlam", "receipt", false);
                a.send(new Packet(PacketType.MSG_DIRECT, "giahuy", "sonlam", Packet.serializePayload(message)));
                b.until(PacketType.MSG_DIRECT);
                guest.send(new Packet(PacketType.MSG_READ, "sonlam", "giahuy", message.getMessageId()));
                c.send(new Packet(PacketType.MSG_READ, "sonlam", "giahuy", message.getMessageId()));
                a.noPacket(PacketType.MSG_READ);
                b.send(new Packet(PacketType.MSG_READ, "fake", "giahuy", message.getMessageId()));
                if (!"sonlam".equals(a.until(PacketType.MSG_READ).getSender())) throw new AssertionError("Invalid receipt sender");
                FileTransferRequest file = new FileTransferRequest("fake", "sonlam", "file", 1, "0".repeat(64));
                a.send(new Packet(PacketType.FILE_REQUEST, "fake", "sonlam", Packet.serializePayload(file)));
                FileTransferRequest routed = b.until(PacketType.FILE_REQUEST).deserializePayload(FileTransferRequest.class);
                routed.setReceiverPort(12345);
                routed.setReceiverIp("192.0.2.1");
                c.send(new Packet(PacketType.FILE_ACCEPT, "sonlam", "giahuy", Packet.serializePayload(routed)));
                a.noPacket(PacketType.FILE_ACCEPT);
                b.send(new Packet(PacketType.FILE_ACCEPT, "sonlam", "giahuy", Packet.serializePayload(routed)));
                FileTransferRequest accepted = a.until(PacketType.FILE_ACCEPT).deserializePayload(FileTransferRequest.class);
                if (!"127.0.0.1".equals(accepted.getReceiverIp()) || !"giahuy".equals(accepted.getSender())) throw new AssertionError("Untrusted signaling metadata");

                b.send(new Packet(PacketType.ROOM_JOIN, "sonlam", room));
                do { list = a.until(PacketType.ROOM_LIST_UPDATE); } while (!list.getPayload().contains("sonlam"));
                c.send(new Packet(PacketType.ROOM_JOIN, "ngochuy", room));
                // A history reply from the same socket is a barrier after joining.
                c.send(new Packet(PacketType.HISTORY_REQUEST, "ngochuy", room, "true"));
                c.until(PacketType.HISTORY_RESPONSE);
                FileTransferRequest toB = new FileTransferRequest("giahuy", "sonlam", "same.bin", 1, "0".repeat(64));
                FileTransferRequest toC = new FileTransferRequest("giahuy", "ngochuy", "same.bin", 1, "0".repeat(64));
                toB.setRoomId(room); toC.setRoomId(room);
                a.send(new Packet(PacketType.FILE_REQUEST, "giahuy", "sonlam", Packet.serializePayload(toB)));
                a.send(new Packet(PacketType.FILE_REQUEST, "giahuy", "ngochuy", Packet.serializePayload(toC)));
                FileTransferRequest requestB = b.until(PacketType.FILE_REQUEST).deserializePayload(FileTransferRequest.class);
                FileTransferRequest requestC = c.until(PacketType.FILE_REQUEST).deserializePayload(FileTransferRequest.class);
                if (!room.equals(requestB.getRoomId()) || requestB.getTransferId().equals(requestC.getTransferId())) throw new AssertionError("Group transfers mixed");
                requestB.setReceiverPort(12345);
                b.send(new Packet(PacketType.FILE_ACCEPT, "sonlam", "giahuy", Packet.serializePayload(requestB)));
                FileTransferRequest groupAccepted = a.until(PacketType.FILE_ACCEPT).deserializePayload(FileTransferRequest.class);
                c.send(new Packet(PacketType.FILE_REJECT, "ngochuy", "giahuy", Packet.serializePayload(requestC)));
                FileTransferRequest groupRejected = a.until(PacketType.FILE_REJECT).deserializePayload(FileTransferRequest.class);
                if (!toB.getTransferId().equals(groupAccepted.getTransferId()) || !toC.getTransferId().equals(groupRejected.getTransferId())
                        || groupRejected.getStatus() != FileTransferRequest.Status.REJECTED) throw new AssertionError("Independent group replies failed");
                toC.setTransferId("not-member"); toC.setRoomId("missing-room");
                a.send(new Packet(PacketType.FILE_REQUEST, "giahuy", "ngochuy", Packet.serializePayload(toC)));
                if (a.until(PacketType.FILE_REJECT).deserializePayload(FileTransferRequest.class).getStatus() != FileTransferRequest.Status.FAILED) throw new AssertionError("Invalid group allowed");
                c.noPacket(PacketType.FILE_REQUEST);
                toC.setTransferId("offline"); toC.setRoomId(null); toC.setReceiver("offline-user");
                a.send(new Packet(PacketType.FILE_REQUEST, "giahuy", "offline-user", Packet.serializePayload(toC)));
                if (a.until(PacketType.FILE_REJECT).deserializePayload(FileTransferRequest.class).getStatus() != FileTransferRequest.Status.FAILED) throw new AssertionError("Offline transfer stuck");
            } finally { server.stop(); }
        }
    }
}

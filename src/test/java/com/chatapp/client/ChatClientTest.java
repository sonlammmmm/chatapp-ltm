package com.chatapp.client;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.security.AESUtil;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatClientTest {
    @Test void encryptedLoginSharedMessageIdAndCachedLists(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Packet> wireMessage = new CompletableFuture<>();
            CountDownLatch login = new CountDownLatch(1);
            CountDownLatch list = new CountDownLatch(1);
            CountDownLatch rooms = new CountDownLatch(1);
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    String raw = in.readLine();
                    assertFalse(raw.contains("password"));
                    assertEquals(PacketType.AUTH_LOGIN, Packet.fromJson(AESUtil.decrypt(raw)).getType());
                    Packet[] replies = {
                        new Packet(PacketType.AUTH_LOGIN_SUCCESS, "SERVER", Packet.serializePayload(new User("a", "A", true))),
                        new Packet(PacketType.USER_LIST_UPDATE, "SERVER", "[{\"username\":\"a\"},{\"username\":\"b\"},{\"username\":\"c\"},{\"username\":\"outsider\"}]"),
                        new Packet(PacketType.ROOM_LIST_UPDATE, "SERVER", "[{\"roomId\":\"lobby\",\"members\":[\"a\",\"b\",\"c\",\"offline\"]}]")
                    };
                    for (Packet packet : replies) { out.write(AESUtil.encrypt(packet.toJson())); out.newLine(); }
                    out.flush();
                    wireMessage.complete(Packet.fromJson(AESUtil.decrypt(in.readLine())));
                } catch (Throwable e) { wireMessage.completeExceptionally(e); }
            });
            ChatClient client = new ChatClient();
            client.setListener((ClientListener) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClientListener.class}, (p, m, a) -> {
                if (m.getName().equals("onLoginSuccess")) login.countDown();
                if (m.getName().equals("onUserListUpdated") && !((java.util.List<?>) a[0]).isEmpty()) list.countDown();
                if (m.getName().equals("onRoomListUpdated") && !((java.util.List<?>) a[0]).isEmpty()) rooms.countDown();
                return null;
            }));
            peer.start();
            try {
                client.connect("127.0.0.1", server.getLocalPort());
                client.login("a", "password");
                assertTrue(login.await(5, TimeUnit.SECONDS));
                assertTrue(list.await(5, TimeUnit.SECONDS));
                assertTrue(rooms.await(5, TimeUnit.SECONDS));
                File file = java.nio.file.Files.write(dir.resolve("same-name.bin"), new byte[]{1, 2, 3}).toFile();
                java.util.List<FileTransferRequest> requests = client.prepareFileRequests("lobby", true, file);
                assertEquals(java.util.Set.of("b", "c"), requests.stream().map(FileTransferRequest::getReceiver).collect(java.util.stream.Collectors.toSet()));
                assertEquals(2, requests.stream().map(FileTransferRequest::getTransferId).distinct().count());
                assertTrue(requests.stream().allMatch(r -> "lobby".equals(r.getRoomId())));
                assertEquals(requests.get(0).getFileChecksum(), requests.get(1).getFileChecksum());
                assertNotEquals(requests.get(0).getTransferId(), client.prepareFileRequests("b", false, file).get(0).getTransferId());
                assertThrows(IOException.class, () -> client.prepareFileRequests("missing", true, file));
                CountDownLatch cached = new CountDownLatch(1);
                client.setListener((ClientListener) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClientListener.class}, (p, m, a) -> {
                    if (m.getName().equals("onUserListUpdated") && !((java.util.List<?>) a[0]).isEmpty()) cached.countDown();
                    return null;
                }));
                assertTrue(cached.await(1, TimeUnit.SECONDS));
                ChatMessage shown = client.sendDirectMessage("b", "hello");
                ChatMessage sent = wireMessage.get(5, TimeUnit.SECONDS).deserializePayload(ChatMessage.class);
                assertEquals(shown.getMessageId(), sent.getMessageId());
                assertEquals("hello", sent.getContent());
            } finally { client.disconnect(); peer.join(6000); }
        }
    }
}

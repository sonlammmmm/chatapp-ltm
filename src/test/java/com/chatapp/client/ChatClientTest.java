package com.chatapp.client;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.security.AESUtil;
import com.chatapp.common.security.TextMessageCrypto;
import com.chatapp.common.security.TextEncryptionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ChatClientTest {
    private String previousKey;
    @BeforeEach void configureKey() {
        previousKey = System.getProperty("chatapp.textKey");
        System.setProperty("chatapp.textKey", TextEncryptionFixture.KEY);
    }
    @AfterEach void restoreKey() {
        if (previousKey == null) System.clearProperty("chatapp.textKey");
        else System.setProperty("chatapp.textKey", previousKey);
    }
    @Test void fileEndpointsUseServerAddressForRelayAndLocalServerReceiver() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.2"))) {
            BlockingQueue<FileTransferRequest> endpoints = new LinkedBlockingQueue<>();
            CompletableFuture<Void> peerResult = new CompletableFuture<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    for (String ip : new String[]{"127.0.0.1", "::1", "26.2.3.4"}) {
                        FileTransferRequest request = new FileTransferRequest("a", "b", "x", 0, "0".repeat(64));
                        request.setReceiverIp(ip);
                        output.write(AESUtil.encrypt(new Packet(PacketType.FILE_ACCEPT, "SERVER", Packet.serializePayload(request)).toJson()));
                        output.newLine();
                    }
                    for (PacketType type : new PacketType[]{PacketType.FILE_REQUEST, PacketType.FILE_ACCEPT}) {
                        FileTransferRequest request = new FileTransferRequest("a", "b", "x", 0, "0".repeat(64));
                        request.setRelayed(true);
                        request.setReceiverIp("192.0.2.1");
                        output.write(AESUtil.encrypt(new Packet(type, "SERVER", Packet.serializePayload(request)).toJson()));
                        output.newLine();
                    }
                    output.flush();
                    peerResult.complete(null);
                } catch (Throwable error) { peerResult.completeExceptionally(error); }
            });
            ChatClient client = new ChatClient();
            client.setListener((ClientListener) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ClientListener.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onFileTransferRequested") || method.getName().equals("onFileTransferAccepted"))
                            endpoints.add((FileTransferRequest) args[0]);
                        return null;
                    }));
            peer.start();
            try {
                client.connect("127.0.0.2", server.getLocalPort());
                for (String expected : new String[]{"127.0.0.2", "127.0.0.2", "26.2.3.4", "127.0.0.2", "127.0.0.2"}) {
                    FileTransferRequest endpoint = endpoints.poll(5, TimeUnit.SECONDS);
                    assertNotNull(endpoint);
                    assertEquals(expected, endpoint.getReceiverIp());
                }
                peerResult.get(5, TimeUnit.SECONDS);
            } finally { client.disconnect(); peer.join(6000); }
        }
    }
    @Test void corruptedEncryptedBodyNeverReachesDisplayCallback() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch lost = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean displayed = new java.util.concurrent.atomic.AtomicBoolean();
            CompletableFuture<Void> peerResult = new CompletableFuture<>();
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    in.readLine();
                    ChatMessage bad = new ChatMessage("b", "a", "v1:invalid", false);
                    bad.setEncrypted(true);
                    Packet[] replies = {
                        new Packet(PacketType.AUTH_LOGIN_SUCCESS, "SERVER", Packet.serializePayload(new User("a", "A", true))),
                        new Packet(PacketType.MSG_DIRECT, "b", "a", Packet.serializePayload(bad))
                    };
                    for (Packet packet : replies) { out.write(AESUtil.encrypt(packet.toJson())); out.newLine(); }
                    out.flush();
                    peerResult.complete(null);
                } catch (Throwable error) { peerResult.completeExceptionally(error); }
            });
            ChatClient client = new ChatClient();
            client.setListener((ClientListener) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ClientListener.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onMessageReceived")) displayed.set(true);
                        if (method.getName().equals("onConnectionLost")) lost.countDown();
                        return null;
                    }));
            peer.start();
            try {
                client.connect("127.0.0.1", server.getLocalPort());
                client.login("a", "password");
                assertTrue(lost.await(5, TimeUnit.SECONDS));
                peerResult.get(5, TimeUnit.SECONDS);
                assertFalse(displayed.get());
            } finally { client.disconnect(); peer.join(6000); }
        }
    }
    @Test void encryptedLoginSharedMessageIdAndCachedLists(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Packet> wireMessage = new CompletableFuture<>();
            CompletableFuture<Packet> wireGroup = new CompletableFuture<>();
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
                    wireGroup.complete(Packet.fromJson(AESUtil.decrypt(in.readLine())));
                } catch (Throwable e) { wireMessage.completeExceptionally(e); wireGroup.completeExceptionally(e); }
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
                assertEquals("hello", shown.getContent());
                assertFalse(shown.isEncrypted(), "local echo must be display-ready");
                assertTrue(sent.isEncrypted());
                assertNotEquals("hello", sent.getContent());
                assertEquals("hello", TextMessageCrypto.decrypt(sent));
                ChatMessage groupShown = client.sendGroupMessage("lobby", "Nhóm 🔒");
                Packet groupPacket = wireGroup.get(5, TimeUnit.SECONDS);
                assertEquals(PacketType.MSG_GROUP, groupPacket.getType());
                ChatMessage groupSent = groupPacket.deserializePayload(ChatMessage.class);
                assertTrue(groupSent.isEncrypted());
                assertEquals(groupShown.getMessageId(), groupSent.getMessageId());
                assertEquals("Nhóm 🔒", groupShown.getContent());
                assertEquals("Nhóm 🔒", TextMessageCrypto.decrypt(groupSent));
            } finally { client.disconnect(); peer.join(6000); }
        }
    }
}

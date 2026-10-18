package com.chatapp.server;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.security.AESUtil;
import com.chatapp.common.security.TextMessageCrypto;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý kết nối TCP riêng biệt cho từng Client (Multi-threading).
 * Đọc và phân phối các gói tin Packet tương ứng.
 */
public class ClientHandler implements Runnable {
    private final ChatServer server;
    private final Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private volatile User currentUser;
    private final java.util.Map<String, String> sentRecipients = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>() {
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> entry) {
                    // shortcut: retain receipts for the latest 1000 messages per connection; persist for long sessions.
                    return size() > 1000;
                }
            });
    private volatile boolean isConnected = true;

    public ClientHandler(ChatServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    public String getClientIp() {
        if (socket != null && socket.getInetAddress() != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String rawJson;
            while (isConnected && (rawJson = in.readLine()) != null) {
                Packet packet = Packet.fromJson(AESUtil.decrypt(rawJson));
                if (packet != null) {
                    processPacket(packet);
                }
            }
        } catch (IOException | SecurityException e) {
            // Client ngắt kết nối đột ngột
        } finally {
            closeConnection();
        }
    }

    private void processPacket(Packet packet) {
        try {
            if (packet.getType() == null) return;
            if (currentUser == null && packet.getType() != PacketType.AUTH_LOGIN
                    && packet.getType() != PacketType.AUTH_REGISTER) return;
            if (currentUser != null) packet.setSender(currentUser.getUsername());
            switch (packet.getType()) {
                case AUTH_LOGIN:
                    handleLogin(packet);
                    break;
                case AUTH_REGISTER:
                    handleRegister(packet);
                    break;
                case AUTH_LOGOUT:
                    closeConnection();
                    break;
                case HISTORY_REQUEST:
                    boolean group = Boolean.parseBoolean(packet.getPayload());
                    String target = packet.getRecipient();
                    if(target == null || (group && !server.getRoomManager().getRoomMembers(target).contains(currentUser.getUsername()))) return;
                    sendPacket(new Packet(PacketType.HISTORY_RESPONSE,"SERVER",target,Packet.serializePayload(server.getDbManager().loadHistory(currentUser.getUsername(),target,group))));
                    break;
                case MSG_DIRECT:
                    handleDirectMessage(packet);
                    break;
                case MSG_GROUP:
                    handleGroupMessage(packet);
                    break;
                case MSG_DELIVERED:
                case MSG_READ:
                    handleMessageStatus(packet);
                    break;
                case USER_TYPING:
                    handleTypingStatus(packet);
                    break;
                case ROOM_CREATE:
                    handleRoomCreate(packet);
                    break;
                case ROOM_JOIN:
                    handleRoomJoin(packet);
                    break;
                case ROOM_LEAVE:
                    handleRoomLeave(packet);
                    break;
                case FILE_REQUEST:
                case FILE_ACCEPT:
                case FILE_REJECT:
                case FILE_P2P_INFO:
                case FILE_P2P_READY:
                    handleFileTransferSignaling(packet);
                    break;
                default:
                    server.log("[CẢNH BÁO] Không xác định được loại gói tin: " + packet.getType());
                    break;
            }
        } catch (Exception e) {
            server.log("[LỖI] Xử lý gói tin thất bại: " + e.getMessage());
            sendPacket(new Packet(PacketType.SYSTEM_ERROR, "SERVER", "Không thể xử lý hoặc lưu dữ liệu. Vui lòng thử lại."));
        }
    }

    private void handleLogin(Packet packet) {
        if (currentUser != null) return;
        try {
            JsonObject json = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();

            User user = server.getDbManager().authenticateUser(username, password);
            if (user != null) {
                this.currentUser = user;
                if (server.registerOnlineUser(username, this)) {
                    user.setIpAddress(getClientIp());
                    sendPacket(new Packet(PacketType.AUTH_LOGIN_SUCCESS, "SERVER", Packet.serializePayload(user)));
                    server.log("[ĐĂNG NHẬP] Người dùng [" + username + "] đã đăng nhập thành công.");
                } else {
                    this.currentUser = null;
                    sendPacket(new Packet(PacketType.AUTH_LOGIN_FAIL, "SERVER", "Tài khoản này đang được đăng nhập ở thiết bị khác!"));
                }
            } else {
                sendPacket(new Packet(PacketType.AUTH_LOGIN_FAIL, "SERVER", "Tên đăng nhập hoặc mật khẩu không chính xác!"));
            }
        } catch (Exception e) {
            sendPacket(new Packet(PacketType.AUTH_LOGIN_FAIL, "SERVER", "Dữ liệu đăng nhập không hợp lệ!"));
        }
    }

    private void handleRegister(Packet packet) {
        try {
            JsonObject json = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();
            String fullName = json.get("fullName").getAsString().trim();

            boolean success = server.getDbManager().registerUser(username, password, fullName);
            if (success) {
                sendPacket(new Packet(PacketType.AUTH_REGISTER_SUCCESS, "SERVER", "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay."));
                server.notifyStateChanged();
                server.log("[ĐĂNG KÝ] Tài khoản mới [" + username + "] (" + fullName + ") đã được tạo.");
            } else {
                sendPacket(new Packet(PacketType.AUTH_REGISTER_FAIL, "SERVER", "Tên tài khoản đã tồn tại trong hệ thống!"));
            }
        } catch (Exception e) {
            sendPacket(new Packet(PacketType.AUTH_REGISTER_FAIL, "SERVER", "Dữ liệu đăng ký không hợp lệ!"));
        }
    }

    private void handleDirectMessage(Packet packet) throws java.sql.SQLException {
        if (currentUser == null) return;
        ChatMessage msg = packet.deserializePayload(ChatMessage.class);
        if (msg == null) throw new SecurityException("Invalid message payload.");
        if (msg != null) {
            if (msg.getRecipient() == null || msg.getMessageId() == null) return;
            msg.setSender(currentUser.getUsername());
            msg.setSenderFullName(currentUser.getFullName());
            msg.setGroup(false);
            if(msg.getMessageId() == null || msg.getContent() == null) throw new SecurityException("Message ID and content are required.");
            validateEncryptedMessage(msg);
            msg.setTimestamp(System.currentTimeMillis());
            server.getDbManager().saveMessage(msg);
            packet.setPayload(Packet.serializePayload(msg));
            String recipient = msg.getRecipient();
            server.log("[CHAT 1-1] Từ [" + currentUser.getUsername() + "] tới [" + recipient + "]");
            sentRecipients.put(msg.getMessageId(), recipient);
            if (!server.sendDirectPacket(recipient, packet)) sentRecipients.remove(msg.getMessageId());
        }
    }

    private void handleGroupMessage(Packet packet) throws java.sql.SQLException {
        if (currentUser == null) return;
        ChatMessage msg = packet.deserializePayload(ChatMessage.class);
        if (msg == null) throw new SecurityException("Invalid message payload.");
        if (msg != null) {
            String roomId = msg.getRecipient();
            if (roomId == null || !server.getRoomManager().getRoomMembers(roomId).contains(currentUser.getUsername())) return;
            msg.setSender(currentUser.getUsername());
            msg.setSenderFullName(currentUser.getFullName());
            msg.setGroup(true);
            if(msg.getMessageId() == null || msg.getContent() == null) throw new SecurityException("Message ID and content are required.");
            validateEncryptedMessage(msg);
            msg.setTimestamp(System.currentTimeMillis());
            server.getDbManager().saveMessage(msg);
            packet.setPayload(Packet.serializePayload(msg));
            server.log("[CHAT NHÓM] Từ [" + currentUser.getUsername() + "] tới phòng [" + roomId + "]");
            server.broadcastToRoom(roomId, packet, currentUser.getUsername());
        }
    }

    private void validateEncryptedMessage(ChatMessage msg) {
        if (!msg.isEncrypted()) throw new SecurityException("Plaintext messages are not accepted.");
        TextMessageCrypto.decrypt(msg);
    }

    private void handleMessageStatus(Packet packet) {
        if (currentUser == null || packet.getRecipient() == null || packet.getPayload() == null) return;
        ClientHandler sender = server.getOnlineClients().get(packet.getRecipient());
        if (sender == null || !currentUser.getUsername().equals(sender.sentRecipients.get(packet.getPayload()))) return;
        server.sendDirectPacket(packet.getRecipient(), packet);
        if (packet.getType() == PacketType.MSG_READ) sender.sentRecipients.remove(packet.getPayload());
    }

    private void handleTypingStatus(Packet packet) {
        if (currentUser == null) return;
        if (packet.getRecipient() != null) {
            if (packet.getRecipient().startsWith("room_") || packet.getRecipient().equals("lobby") || packet.getRecipient().equals("nhom03")) {
                if (server.getRoomManager().getRoomMembers(packet.getRecipient()).contains(currentUser.getUsername()))
                    server.broadcastToRoom(packet.getRecipient(), packet, currentUser.getUsername());
            } else {
                server.sendDirectPacket(packet.getRecipient(), packet);
            }
        }
    }

    private void handleRoomCreate(Packet packet) {
        if (currentUser == null) return;
        try {
            JsonObject json = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
            String roomName = json.get("roomName").getAsString().trim();
            String description = json.has("description") ? json.get("description").getAsString() : "";

            ChatRoom room = server.getRoomManager().createRoom(roomName, currentUser.getUsername(), description);
            server.getDbManager().saveRoom(room);
            server.log("[PHÒNG] [" + currentUser.getUsername() + "] đã tạo phòng mới: " + roomName);

            sendPacket(new Packet(PacketType.ROOM_CREATE_SUCCESS, "SERVER", Packet.serializePayload(room)));
            server.broadcastRoomList();
        } catch (Exception e) {
            sendPacket(new Packet(PacketType.ROOM_CREATE_FAIL, "SERVER", "Lỗi tạo phòng chat: " + e.getMessage()));
        }
    }

    private void handleRoomJoin(Packet packet) {
        if (currentUser == null) return;
        String roomId = packet.getPayload();
        server.getRoomManager().joinRoom(roomId, currentUser.getUsername());
        server.broadcastRoomList();
    }

    private void handleRoomLeave(Packet packet) {
        if (currentUser == null) return;
        String roomId = packet.getPayload();
        server.getRoomManager().leaveRoom(roomId, currentUser.getUsername());
        server.broadcastRoomList();
    }

    // Điều phối báo hiệu (Signaling) cho việc truyền tệp tin P2P
    private void handleFileTransferSignaling(Packet packet) {
        if (currentUser == null) return;
        FileTransferRequest request = packet.deserializePayload(FileTransferRequest.class);
        if (request == null) return;

        switch (packet.getType()) {
            case FILE_REQUEST:
                request.setSender(currentUser.getUsername());
                if (request.getTransferId() == null || request.getFileName() == null
                        || request.getReceiver() == null || request.getFileSize() < 0
                        || request.getFileChecksum() == null || !request.getFileChecksum().matches("[0-9a-f]{64}")) return;
                if (request.getRoomId() != null &&
                        (!server.getRoomManager().getRoomMembers(request.getRoomId()).contains(currentUser.getUsername())
                        || !server.getRoomManager().getRoomMembers(request.getRoomId()).contains(request.getReceiver()))) {
                    request.setStatus(FileTransferRequest.Status.FAILED);
                    sendPacket(new Packet(PacketType.FILE_REJECT, "SERVER", Packet.serializePayload(request)));
                    return;
                }
                if (server.pendingTransfers.size() >= 1000) {
                    request.setStatus(FileTransferRequest.Status.FAILED);
                    sendPacket(new Packet(PacketType.FILE_REJECT, "SERVER", Packet.serializePayload(request)));
                    return;
                }
                request.setStatus(FileTransferRequest.Status.PENDING);
                // The server chooses the transport; never trust relay credentials supplied by a client.
                request.setRelayed(false);
                request.setRelayToken(null);
                request.setReceiverIp(null);
                request.setReceiverPort(0);
                if (server.pendingTransfers.putIfAbsent(request.getTransferId(), request) != null) return;
                FileRelayServer relay = server.getFileRelay();
                if (relay != null && !relay.register(request)) {
                    server.pendingTransfers.remove(request.getTransferId(), request);
                    request.setStatus(FileTransferRequest.Status.FAILED);
                    sendPacket(new Packet(PacketType.FILE_REJECT, "SERVER", Packet.serializePayload(request)));
                    return;
                }
                packet.setPayload(Packet.serializePayload(request));
                server.log("[FILE P2P] Yêu cầu gửi file: " + request.getFileName() + " (" + request.getFormattedSize() + ") từ [" + currentUser.getUsername() + "] tới [" + request.getReceiver() + "]");
                if (!server.sendDirectPacket(request.getReceiver(), packet)) {
                    server.pendingTransfers.remove(request.getTransferId(), request);
                    if (relay != null) relay.remove(request.getTransferId());
                    request.setStatus(FileTransferRequest.Status.FAILED);
                    sendPacket(new Packet(PacketType.FILE_REJECT, "SERVER", Packet.serializePayload(request)));
                }
                break;
            case FILE_ACCEPT:
            case FILE_REJECT:
                FileTransferRequest original = server.pendingTransfers.get(request.getTransferId());
                if (original == null || !currentUser.getUsername().equals(original.getReceiver())) return;
                if (packet.getType() == PacketType.FILE_ACCEPT && !original.isRelayed()) {
                    if (request.getReceiverPort() < 1 || request.getReceiverPort() > 65535) return;
                    original.setReceiverIp(getClientIp());
                    original.setReceiverPort(request.getReceiverPort());
                }
                if (!server.pendingTransfers.remove(request.getTransferId(), original)) return;
                FileRelayServer respondingRelay = server.getFileRelay();
                if (packet.getType() == PacketType.FILE_REJECT && respondingRelay != null)
                    respondingRelay.remove(original.getTransferId());
                original.setStatus(packet.getType() == PacketType.FILE_ACCEPT
                        ? FileTransferRequest.Status.ACCEPTED : FileTransferRequest.Status.REJECTED);
                if (packet.getType() == PacketType.FILE_ACCEPT)
                    server.log("[FILE " + (original.isRelayed() ? "RELAY" : "P2P") + "] "
                            + original.getSender() + " -> " + original.getReceiver() + ", cổng " + original.getReceiverPort());
                server.sendDirectPacket(original.getSender(), new Packet(packet.getType(),
                        currentUser.getUsername(), original.getSender(), Packet.serializePayload(original)));
                break;
            case FILE_P2P_INFO:
            case FILE_P2P_READY:
                break;
            default:
                break;
        }
    }

    public void sendPacket(Packet packet) {
        if (!isConnected || out == null) return;
        try {
            synchronized (out) {
                out.write(AESUtil.encrypt(packet.toJson()));
                out.newLine();
                out.flush();
            }
        } catch (IOException | SecurityException e) {
            closeConnection();
        }
    }

    public void closeConnection() {
        synchronized (this) {
            if (!isConnected) return;
            isConnected = false;
        }

        if (currentUser != null) {
            server.unregisterOnlineUser(currentUser.getUsername());
        }

        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }
}

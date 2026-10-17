package com.chatapp.server;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
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
    private User currentUser;
    private boolean isConnected = true;

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
                Packet packet = Packet.fromJson(rawJson);
                if (packet != null) {
                    processPacket(packet);
                }
            }
        } catch (IOException e) {
            // Client ngắt kết nối đột ngột
        } finally {
            closeConnection();
        }
    }

    private void processPacket(Packet packet) {
        try {
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
        }
    }

    private void handleLogin(Packet packet) {
        try {
            JsonObject json = JsonParser.parseString(packet.getPayload()).getAsJsonObject();
            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();

            User user = server.getDbManager().authenticateUser(username, password);
            if (user != null) {
                if (server.registerOnlineUser(username, this)) {
                    this.currentUser = user;
                    user.setIpAddress(getClientIp());
                    sendPacket(new Packet(PacketType.AUTH_LOGIN_SUCCESS, "SERVER", Packet.serializePayload(user)));
                    server.log("[ĐĂNG NHẬP] Người dùng [" + username + "] đã đăng nhập thành công.");
                } else {
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
                server.log("[ĐĂNG KÝ] Tài khoản mới [" + username + "] (" + fullName + ") đã được tạo.");
            } else {
                sendPacket(new Packet(PacketType.AUTH_REGISTER_FAIL, "SERVER", "Tên tài khoản đã tồn tại trong hệ thống!"));
            }
        } catch (Exception e) {
            sendPacket(new Packet(PacketType.AUTH_REGISTER_FAIL, "SERVER", "Dữ liệu đăng ký không hợp lệ!"));
        }
    }

    private void handleDirectMessage(Packet packet) {
        if (currentUser == null) return;
        ChatMessage msg = packet.deserializePayload(ChatMessage.class);
        if (msg != null) {
            String recipient = msg.getRecipient();
            server.log("[CHAT 1-1] Từ [" + currentUser.getUsername() + "] tới [" + recipient + "]");
            boolean delivered = server.sendDirectPacket(recipient, packet);

            // Tự động phản hồi gói tin MSG_DELIVERED cho người gửi nếu người nhận online
            if (delivered) {
                Packet ack = new Packet(PacketType.MSG_DELIVERED, recipient, currentUser.getUsername(), msg.getMessageId());
                sendPacket(ack);
            }
        }
    }

    private void handleGroupMessage(Packet packet) {
        if (currentUser == null) return;
        ChatMessage msg = packet.deserializePayload(ChatMessage.class);
        if (msg != null) {
            String roomId = msg.getRecipient();
            server.log("[CHAT NHÓM] Từ [" + currentUser.getUsername() + "] tới phòng [" + roomId + "]");
            server.broadcastToRoom(roomId, packet, currentUser.getUsername());
        }
    }

    private void handleMessageStatus(Packet packet) {
        // Gửi tiếp thông báo Đã nhận / Đã xem cho người gửi ban đầu
        if (packet.getRecipient() != null) {
            server.sendDirectPacket(packet.getRecipient(), packet);
        }
    }

    private void handleTypingStatus(Packet packet) {
        if (currentUser == null) return;
        if (packet.getRecipient() != null) {
            if (packet.getRecipient().startsWith("room_") || packet.getRecipient().equals("lobby") || packet.getRecipient().equals("nhom03")) {
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
                server.log("[FILE P2P] Yêu cầu gửi file: " + request.getFileName() + " (" + request.getFormattedSize() + ") từ [" + currentUser.getUsername() + "] tới [" + request.getReceiver() + "]");
                server.sendDirectPacket(request.getReceiver(), packet);
                break;
            case FILE_ACCEPT:
                server.log("[FILE P2P] [" + currentUser.getUsername() + "] chấp nhận nhận file từ [" + request.getSender() + "]. Bắt đầu thiết lập P2P Socket.");
                // Client nhận file đã mở ServerSocket và đính kèm IP & Port
                if (request.getReceiverIp() == null || request.getReceiverIp().isEmpty()) {
                    request.setReceiverIp(getClientIp());
                }
                Packet acceptPkt = new Packet(PacketType.FILE_ACCEPT, currentUser.getUsername(), request.getSender(), Packet.serializePayload(request));
                server.sendDirectPacket(request.getSender(), acceptPkt);
                break;
            case FILE_REJECT:
                server.log("[FILE P2P] [" + currentUser.getUsername() + "] đã từ chối nhận file từ [" + request.getSender() + "].");
                server.sendDirectPacket(request.getSender(), packet);
                break;
            case FILE_P2P_INFO:
            case FILE_P2P_READY:
                server.sendDirectPacket(packet.getRecipient(), packet);
                break;
            default:
                break;
        }
    }

    public synchronized void sendPacket(Packet packet) {
        if (!isConnected || out == null) return;
        try {
            out.write(packet.toJson());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            closeConnection();
        }
    }

    public synchronized void closeConnection() {
        if (!isConnected) return;
        isConnected = false;

        if (currentUser != null) {
            server.unregisterOnlineUser(currentUser.getUsername());
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }
}

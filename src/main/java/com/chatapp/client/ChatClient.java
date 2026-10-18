package com.chatapp.client;

import com.chatapp.common.model.*;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.security.AESUtil;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Điều khiển kết nối mạng TCP Socket từ Client tới Server.
 * Tích hợp mã hóa AES đối xứng cho tin nhắn văn bản.
 */
public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private volatile boolean isConnected = false;
    private User currentUser;
    private volatile ClientListener listener;
    private volatile List<User> onlineUsers = java.util.Collections.emptyList();
    private volatile List<ChatRoom> rooms = java.util.Collections.emptyList();
    private Thread listenerThread;
    private static final Gson gson = new Gson();

    public ChatClient() {
    }

    public synchronized void setListener(ClientListener listener) {
        this.listener = listener;
        listener.onUserListUpdated(onlineUsers);
        listener.onRoomListUpdated(rooms);
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected) return;

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        isConnected = true;

        listenerThread = new Thread(this::listenLoop, "ClientListenerThread");
        listenerThread.start();
    }

    public synchronized void disconnect() {
        isConnected = false;

        try {
            if (currentUser != null) {
                sendPacket(new Packet(PacketType.AUTH_LOGOUT, currentUser.getUsername(), ""));
            }
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ignored) {}

        currentUser = null;
    }

    private void listenLoop() {
        try {
            String line;
            while (isConnected && (line = in.readLine()) != null) {
                Packet packet = Packet.fromJson(AESUtil.decrypt(line));
                if (packet != null) {
                    handleIncomingPacket(packet);
                }
            }
        } catch (IOException | SecurityException e) {
            // Ngắt kết nối
        } finally {
            if (isConnected) {
                isConnected = false;
                if (listener != null) {
                    listener.onConnectionLost();
                }
            }
        }
    }

    private synchronized void handleIncomingPacket(Packet packet) {
        if (listener == null) return;

        switch (packet.getType()) {
            case AUTH_LOGIN_SUCCESS:
                currentUser = packet.deserializePayload(User.class);
                listener.onLoginSuccess(currentUser);
                break;
            case AUTH_LOGIN_FAIL:
                listener.onLoginFail(packet.getPayload());
                break;
            case AUTH_REGISTER_SUCCESS:
                listener.onRegisterResult(true, packet.getPayload());
                break;
            case AUTH_REGISTER_FAIL:
                listener.onRegisterResult(false, packet.getPayload());
                break;
            case USER_LIST_UPDATE:
                List<User> userList = gson.fromJson(packet.getPayload(), new TypeToken<List<User>>(){}.getType());
                onlineUsers = userList;
                listener.onUserListUpdated(userList);
                break;
            case ROOM_LIST_UPDATE:
                List<ChatRoom> roomList = gson.fromJson(packet.getPayload(), new TypeToken<List<ChatRoom>>(){}.getType());
                rooms = roomList;
                listener.onRoomListUpdated(roomList);
                break;
            case SYSTEM_ERROR:
                listener.onServerError(packet.getPayload());
                break;
            case HISTORY_RESPONSE:
                ChatMessage[] history = packet.deserializePayload(ChatMessage[].class);
                if (history != null) {
                    for (ChatMessage old : history) {
                        if (old.isEncrypted()) old.setContent(AESUtil.decrypt(old.getContent()));
                    }
                    listener.onHistoryReceived(packet.getRecipient(), java.util.Arrays.asList(history));
                }
                break;
            case MSG_DIRECT:
            case MSG_GROUP:
                ChatMessage message = packet.deserializePayload(ChatMessage.class);
                if (message != null) {
                    // Tự động giải mã nội dung nếu có cờ mã hóa AES
                    if (message.isEncrypted()) {
                        message.setContent(AESUtil.decrypt(message.getContent()));
                    }
                    listener.onMessageReceived(message);

                    // Phản hồi đã nhận tin (DELIVERED) cho tin nhắn 1-1
                    if (!message.isGroup() && currentUser != null) {
                        sendMessageDelivered(message.getSender(), message.getMessageId());
                    }
                }
                break;
            case MSG_DELIVERED:
                listener.onMessageStatusUpdated(packet.getPayload(), ChatMessage.Status.DELIVERED);
                break;
            case MSG_READ:
                listener.onMessageStatusUpdated(packet.getPayload(), ChatMessage.Status.READ);
                break;
            case USER_TYPING:
                listener.onUserTyping(packet.getSender(), packet.getRecipient(), Boolean.parseBoolean(packet.getPayload()));
                break;
            case FILE_REQUEST:
                FileTransferRequest req = packet.deserializePayload(FileTransferRequest.class);
                listener.onFileTransferRequested(req);
                break;
            case FILE_ACCEPT:
                FileTransferRequest acceptedReq = packet.deserializePayload(FileTransferRequest.class);
                listener.onFileTransferAccepted(acceptedReq);
                break;
            case FILE_REJECT:
                FileTransferRequest rejectedReq = packet.deserializePayload(FileTransferRequest.class);
                listener.onFileTransferRejected(rejectedReq);
                break;
            default:
                break;
        }
    }

    public synchronized void sendPacket(Packet packet) {
        if (!isConnected || out == null) return;
        try {
            out.write(AESUtil.encrypt(packet.toJson()));
            out.newLine();
            out.flush();
        } catch (IOException | SecurityException e) {
            disconnect();
        }
    }

    // Các hàm nghiệp vụ
    public void login(String username, String password) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);
        sendPacket(new Packet(PacketType.AUTH_LOGIN, username, json.toString()));
    }

    public void register(String username, String password, String fullName) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);
        json.addProperty("fullName", fullName);
        sendPacket(new Packet(PacketType.AUTH_REGISTER, username, json.toString()));
    }

    public ChatMessage sendDirectMessage(String recipient, String content) {
        return sendMessage(recipient, content, false);
    }

    public ChatMessage sendGroupMessage(String roomId, String content) {
        return sendMessage(roomId, content, true);
    }

    private ChatMessage sendMessage(String target, String content, boolean group) {
        if (currentUser == null || !isConnected) throw new IllegalStateException("Not connected");
        ChatMessage msg = new ChatMessage(currentUser.getUsername(), target, content, group);
        msg.setSenderFullName(currentUser.getFullName());
        sendPacket(new Packet(group ? PacketType.MSG_GROUP : PacketType.MSG_DIRECT,
                currentUser.getUsername(), target, Packet.serializePayload(msg)));
        return msg;
    }

    public void sendTypingStatus(String target, boolean isTyping) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.USER_TYPING, currentUser.getUsername(), target, String.valueOf(isTyping)));
    }

    public void sendMessageDelivered(String sender, String messageId) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.MSG_DELIVERED, currentUser.getUsername(), sender, messageId));
    }

    public void sendMessageRead(String sender, String messageId) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.MSG_READ, currentUser.getUsername(), sender, messageId));
    }

    public void createRoom(String roomName, String description) {
        if (currentUser == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("roomName", roomName);
        json.addProperty("description", description);
        sendPacket(new Packet(PacketType.ROOM_CREATE, currentUser.getUsername(), json.toString()));
    }

    public void requestHistory(String target, boolean group) {
        sendPacket(new Packet(PacketType.HISTORY_REQUEST, currentUser.getUsername(), target, String.valueOf(group)));
    }

    public void joinRoom(String roomId) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.ROOM_JOIN, currentUser.getUsername(), roomId));
    }

    public void leaveRoom(String roomId) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.ROOM_LEAVE, currentUser.getUsername(), roomId));
    }

    public void sendFileRequest(String receiver, File file) throws IOException {
        if (currentUser == null) return;
        FileTransferRequest req = new FileTransferRequest(currentUser.getUsername(), receiver, file.getName(), file.length(), com.chatapp.common.security.HashUtil.hashFile(file));
        sendPacket(new Packet(PacketType.FILE_REQUEST, currentUser.getUsername(), receiver, Packet.serializePayload(req)));
    }

    public List<FileTransferRequest> prepareFileRequests(String target, boolean group, File file) throws IOException {
        if (currentUser == null || !isConnected) throw new IOException("Chưa kết nối máy chủ.");
        if (!file.isFile() || !file.canRead()) throw new IOException("Không thể đọc tệp đã chọn.");
        java.util.Set<String> recipients = new java.util.LinkedHashSet<>();
        if (group) {
            ChatRoom room = rooms.stream().filter(r -> target.equals(r.getRoomId())).findFirst().orElse(null);
            if (room == null || !room.hasMember(currentUser.getUsername())) throw new IOException("Bạn chưa tham gia nhóm.");
            for (User user : onlineUsers) {
                if (!user.getUsername().equals(currentUser.getUsername()) && room.hasMember(user.getUsername()))
                    recipients.add(user.getUsername());
            }
        } else recipients.add(target);
        if (recipients.isEmpty()) throw new IOException("Không có thành viên khác đang online trong nhóm.");
        String checksum = com.chatapp.common.security.HashUtil.hashFile(file);
        List<FileTransferRequest> requests = new java.util.ArrayList<>();
        for (String receiver : recipients) {
            FileTransferRequest request = new FileTransferRequest(currentUser.getUsername(), receiver, file.getName(), file.length(), checksum);
            if (group) request.setRoomId(target);
            requests.add(request);
        }
        return requests;
    }

    public void sendFileRequest(FileTransferRequest request) {
        sendPacket(new Packet(PacketType.FILE_REQUEST, currentUser.getUsername(), request.getReceiver(), Packet.serializePayload(request)));
    }

    public void acceptFileRequest(FileTransferRequest request, int localP2pPort) {
        if (currentUser == null) return;
        request.setReceiverPort(localP2pPort);
        sendPacket(new Packet(PacketType.FILE_ACCEPT, currentUser.getUsername(), request.getSender(), Packet.serializePayload(request)));
    }

    public void rejectFileRequest(FileTransferRequest request) {
        if (currentUser == null) return;
        sendPacket(new Packet(PacketType.FILE_REJECT, currentUser.getUsername(), request.getSender(), Packet.serializePayload(request)));
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isConnected() {
        return isConnected;
    }
}

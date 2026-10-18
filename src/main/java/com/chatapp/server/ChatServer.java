/**
 * ChatServer.java — Điều phối máy chủ chat, kết nối người dùng, phòng chat và chuyển tiếp gói tin.
 */
package com.chatapp.server;

import com.chatapp.common.model.ChatRoom;
import com.chatapp.common.model.PacketType;
import com.chatapp.common.model.User;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.protocol.Packet;
import com.chatapp.common.config.AppConfig;
import com.chatapp.server.db.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lớp điều khiển chính của TCP Socket Server.
 * Quản lý đa luồng kết nối, điều phối tin nhắn 1-1, chat nhóm và báo hiệu truyền tệp tin P2P.
 */
public class ChatServer {
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;
    final Map<String, FileTransferRequest> pendingTransfers = new ConcurrentHashMap<>();
    private ExecutorService threadPool;
    private volatile FileRelayServer fileRelay;

    FileRelayServer getFileRelay() { return fileRelay; }

    /** Loại yêu cầu truyền tệp hết hạn và thông báo cho các bên. */
    private void expireFileRequest(String id) {
        FileTransferRequest request = pendingTransfers.remove(id);
        if (request == null) return;
        request.setStatus(FileTransferRequest.Status.FAILED);
        for (String user : new String[]{request.getSender(), request.getReceiver()})
            sendDirectPacket(user, new Packet(PacketType.FILE_REJECT, "SERVER", user, Packet.serializePayload(request)));
    }

    // Quản lý các Client đang online: Map<Username, ClientHandler>
    private final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    private final DatabaseManager dbManager;
    private final RoomManager roomManager;
    private Consumer<String> logListener;
    private volatile Runnable stateListener;

    /** Đăng ký hàm nhận thông báo khi trạng thái Server thay đổi. */
    public void setStateListener(Runnable listener) { stateListener = listener; }

    /** Thông báo trạng thái Server đã thay đổi cho bộ lắng nghe. */
    public void notifyStateChanged() {
        Runnable listener = stateListener;
        if (listener != null) listener.run();
    }

    /** Khởi tạo đối tượng ChatServer với dữ liệu và tài nguyên ban đầu. */
    public ChatServer(int port) {
        this.port = port;
        this.dbManager = DatabaseManager.getInstance();
        this.roomManager = new RoomManager();
    }

    /** Đăng ký nơi tiếp nhận nhật ký hoạt động của Server. */
    public void setLogListener(Consumer<String> logListener) {
        this.logListener = logListener;
    }

    /** Khởi động máy chủ và tiếp nhận các kết nối mới. */
    public synchronized void start() throws IOException {
        if (isRunning) return;

        try { roomManager.restoreRooms(dbManager.loadRooms()); }
        catch (java.sql.SQLException e) { throw new IOException("Cannot load saved rooms", e); }
        String fileMode = AppConfig.fileMode();
        serverSocket = new ServerSocket(port);
        try {
            if (fileMode.equals("relay")) fileRelay = new FileRelayServer(AppConfig.relayPort(), this::expireFileRequest);
        } catch (IOException | RuntimeException e) {
            serverSocket.close();
            throw e;
        }
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();

        log("[SERVER] Máy chủ TCP Socket đã khởi động tại cổng: " + port);
        log("[FILE] Chế độ " + fileMode + (fileRelay == null ? " (LAN / VPN)" : ", cổng TCP " + fileRelay.getPort()));

        // Luồng chấp nhận kết nối từ các Client
        threadPool.execute(() -> {
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    log("[KẾT NỐI] Client mới kết nối từ IP: " + clientIp + ":" + clientSocket.getPort());

                    ClientHandler handler = new ClientHandler(this, clientSocket);
                    threadPool.execute(handler);
                } catch (IOException e) {
                    if (isRunning) {
                        log("[LỖI] Lỗi chấp nhận kết nối: " + e.getMessage());
                    }
                }
            }
        });
    }

    /** Dừng máy chủ và giải phóng các kết nối đang hoạt động. */
    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (fileRelay != null) { fileRelay.close(); fileRelay = null; }
        pendingTransfers.clear();
        log("[SERVER] Đang dừng máy chủ...");

        // Ngắt tất cả các client đang kết nối
        for (ClientHandler handler : onlineClients.values()) {
            handler.closeConnection();
        }
        onlineClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("[LỖI] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        log("[SERVER] Máy chủ đã dừng hoạt động hoàn toàn.");
    }

    // Đăng ký người dùng online
    /** Đăng ký kết nối của tài khoản vào danh sách trực tuyến. */
    public boolean registerOnlineUser(String username, ClientHandler handler) {
        if (onlineClients.putIfAbsent(username, handler) != null) {
            return false; // Đã đăng nhập trên một thiết bị khác
        }
        roomManager.joinRoom("lobby", username);
        log("[ONLINE] Người dùng [" + username + "] đã đăng nhập thành công.");
        broadcastOnlineUserList();
        broadcastRoomList();
        return true;
    }

    // Hủy đăng ký người dùng khi ngắt kết nối
    /** Gỡ tài khoản khỏi danh sách trực tuyến và cập nhật các bên liên quan. */
    public void unregisterOnlineUser(String username) {
        if (username != null && onlineClients.remove(username) != null) {
            FileRelayServer relay = fileRelay;
            if (relay != null) relay.removeUser(username);
            log("[OFFLINE] Người dùng [" + username + "] đã thoát khỏi hệ thống.");
            for (FileTransferRequest request : pendingTransfers.values()) {
                if ((username.equals(request.getSender()) || username.equals(request.getReceiver()))
                        && pendingTransfers.remove(request.getTransferId(), request)) {
                    request.setStatus(FileTransferRequest.Status.FAILED);
                    String peer = username.equals(request.getSender()) ? request.getReceiver() : request.getSender();
                    sendDirectPacket(peer, new Packet(PacketType.FILE_REJECT, "SERVER", peer, Packet.serializePayload(request)));
                }
            }
            broadcastOnlineUserList();
        }
    }

    // Gửi gói tin cho 1 người nhận cụ thể (Chat 1-1 / Signaling)
    /** Chuyển gói tin đến kết nối của người dùng đích. */
    public boolean sendDirectPacket(String targetUsername, Packet packet) {
        ClientHandler handler = onlineClients.get(targetUsername);
        if (handler != null) {
            handler.sendPacket(packet);
            return true;
        }
        return false;
    }

    // Gửi broadcast cho tất cả client online
    /** Gửi gói tin đến các Client đang trực tuyến. */
    public void broadcastPacket(Packet packet) {
        for (ClientHandler handler : onlineClients.values()) {
            handler.sendPacket(packet);
        }
    }

    // Gửi tin nhắn cho tất cả thành viên trong một phòng (Group Chat Multicast)
    /** Gửi gói tin đến thành viên phòng, có thể loại trừ người gửi. */
    public void broadcastToRoom(String roomId, Packet packet, String excludeSender) {
        Set<String> members = roomManager.getRoomMembers(roomId);
        for (String member : members) {
            if (excludeSender != null && excludeSender.equalsIgnoreCase(member)) {
                continue; // Không gửi ngược lại cho chính người gửi
            }
            ClientHandler handler = onlineClients.get(member);
            if (handler != null) {
                handler.sendPacket(packet);
            }
        }
    }

    // Phát sóng danh sách người dùng online (Lấy trực tiếp từ RAM, tránh N+1 Query trên SQLite)
    /** Phát danh sách người dùng trực tuyến đến các Client. */
    public void broadcastOnlineUserList() {
        List<User> list = new ArrayList<>();
        for (ClientHandler handler : onlineClients.values()) {
            User user = handler.getCurrentUser();
            if (user != null) {
                user.setOnline(true);
                user.setIpAddress(handler.getClientIp());
                list.add(user);
            }
        }

        Packet packet = new Packet(PacketType.USER_LIST_UPDATE, "SERVER", Packet.serializePayload(list));
        broadcastPacket(packet);
        notifyStateChanged();
    }

    // Phát sóng danh sách phòng chat hiện có
    /** Phát danh sách phòng chat đến các Client. */
    public void broadcastRoomList() {
        List<ChatRoom> rooms = roomManager.getAllRooms();
        Packet packet = new Packet(PacketType.ROOM_LIST_UPDATE, "SERVER", Packet.serializePayload(rooms));
        broadcastPacket(packet);
        notifyStateChanged();
    }

    /** Ghi nhật ký hoạt động và chuyển đến bộ lắng nghe. */
    public void log(String message) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String fullMsg = "[" + time + "] " + message;
        System.out.println(fullMsg);
        if (logListener != null) {
            logListener.accept(fullMsg);
        }
    }

    // Getters
    /** Cho biết trạng thái hoạt động của Server. */
    public boolean isRunning() {
        return isRunning;
    }

    /** Lấy cổng đang lắng nghe. */
    public int getPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    /** Lấy bộ quản lý cơ sở dữ liệu. */
    public DatabaseManager getDbManager() {
        return dbManager;
    }

    /** Lấy bộ quản lý phòng chat. */
    public RoomManager getRoomManager() {
        return roomManager;
    }

    public Map<String, ClientHandler> getOnlineClients() {
        return onlineClients;
    }
}

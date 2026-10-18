package com.chatapp.server.db;

import com.chatapp.common.model.User;
import com.chatapp.common.security.HashUtil;

import java.io.File;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý cơ sở dữ liệu xác thực người dùng sử dụng SQLite.
 * Lưu trữ mật khẩu đã băm SHA-256 kết hợp Salt.
 */
public class DatabaseManager {
    private static final String DB_DIR = "database";
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR + File.separator + "chatapp.db";
    private static DatabaseManager instance;

    // Bộ nhớ đệm dự phòng (In-memory fallback) nếu driver SQLite gặp lỗi môi trường
    private final ConcurrentHashMap<String, UserCredentials> memoryDb = new ConcurrentHashMap<>();
    private boolean useSqlite = true;

    private static class UserCredentials {
        String username;
        String passwordHash;
        String salt;
        String fullName;

        UserCredentials(String username, String passwordHash, String salt, String fullName) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.fullName = fullName;
        }
    }

    private DatabaseManager() {
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Khởi tạo kết nối SQLite và tạo bảng nếu chưa có.
     */
    private void initDatabase() {
        try {
            File dir = new File(DB_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {

                String sql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL, " +
                        "password_hash TEXT NOT NULL, " +
                        "salt TEXT NOT NULL, " +
                        "full_name TEXT NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ");";
                stmt.execute(sql);
                stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, message_id TEXT UNIQUE NOT NULL, sender TEXT NOT NULL, recipient TEXT NOT NULL, is_group INTEGER NOT NULL, payload TEXT NOT NULL)");
                stmt.execute("CREATE INDEX IF NOT EXISTS messages_conversation ON messages(is_group, recipient, sender, id)");
                stmt.execute("CREATE TABLE IF NOT EXISTS rooms (room_id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
                System.out.println("[DB] Khởi tạo cơ sở dữ liệu SQLite thành công.");
                useSqlite = true;
            }
        } catch (Exception e) {
            System.err.println("[DB] Không thể dùng SQLite (" + e.getMessage() + "). Chuyển sang cơ chế bộ nhớ đệm.");
            useSqlite = false;
        }

        // Tự động tạo 3 tài khoản mặc định cho 3 thành viên nhóm
        seedInitialUsers();
    }

    private void seedInitialUsers() {
        registerUser("giahuy", "123456", "Trần Gia Huy (Nhóm Trưởng)");
        registerUser("sonlam", "123456", "Trần Sỷ Sơn Lâm");
        registerUser("ngochuy", "123456", "Đặng Ngọc Huy");
    }

    /**
     * Đăng ký người dùng mới với mật khẩu được băm SHA-256 kèm Salt.
     */
    public synchronized boolean registerUser(String username, String password, String fullName) {
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            return false;
        }

        String salt = HashUtil.generateSalt();
        String passwordHash = HashUtil.hashPassword(password, salt);

        if (useSqlite) {
            String checkSql = "SELECT username FROM users WHERE username = ?";
            String insertSql = "INSERT INTO users(username, password_hash, salt, full_name) VALUES(?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL)) {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, username);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        return false; // Đã tồn tại username
                    }
                }

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, username);
                    insertStmt.setString(2, passwordHash);
                    insertStmt.setString(3, salt);
                    insertStmt.setString(4, fullName);
                    insertStmt.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi đăng ký SQLite: " + e.getMessage());
            }
        }

        // Fallback bộ nhớ
        if (memoryDb.containsKey(username)) {
            return false;
        }
        memoryDb.put(username, new UserCredentials(username, passwordHash, salt, fullName));
        return true;
    }

    /**
     * Xác thực thông tin đăng nhập của người dùng.
     */
    public synchronized User authenticateUser(String username, String password) {
        if (username == null || password == null) return null;

        if (useSqlite) {
            String sql = "SELECT username, password_hash, salt, full_name FROM users WHERE username = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String passwordHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    String fullName = rs.getString("full_name");

                    if (HashUtil.verifyPassword(password, salt, passwordHash)) {
                        return new User(username, fullName, true);
                    }
                }
                return null;
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi xác thực SQLite: " + e.getMessage());
            }
        }

        // Fallback bộ nhớ
        UserCredentials creds = memoryDb.get(username);
        if (creds != null && HashUtil.verifyPassword(password, creds.salt, creds.passwordHash)) {
            return new User(creds.username, creds.fullName, true);
        }

        return null;
    }

    /**
     * Lấy thông tin người dùng theo tên tài khoản.
     */
    public synchronized User getUser(String username) {
        if (useSqlite) {
            String sql = "SELECT username, full_name FROM users WHERE username = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return new User(rs.getString("username"), rs.getString("full_name"), false);
                }
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi truy vấn getUser: " + e.getMessage());
            }
        }

        UserCredentials creds = memoryDb.get(username);
        if (creds != null) {
            return new User(creds.username, creds.fullName, false);
        }
        return null;
    }

    /**
     * Lấy danh sách tất cả người dùng đã đăng ký trong cơ sở dữ liệu.
     */
    public synchronized java.util.List<User> getAllRegisteredUsers() {
        java.util.List<User> list = new java.util.ArrayList<>();
        if (useSqlite) {
            String sql = "SELECT username, full_name FROM users ORDER BY id ASC";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(new User(rs.getString("username"), rs.getString("full_name"), false));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi lấy danh sách tài khoản: " + e.getMessage());
            }
        }

        for (UserCredentials cred : memoryDb.values()) {
            list.add(new User(cred.username, cred.fullName, false));
        }
        return list;
    }

    public synchronized void saveMessage(com.chatapp.common.model.ChatMessage m) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL); PreparedStatement s = c.prepareStatement("INSERT INTO messages(message_id,sender,recipient,is_group,payload) VALUES(?,?,?,?,?)")) {
            s.setString(1,m.getMessageId()); s.setString(2,m.getSender()); s.setString(3,m.getRecipient()); s.setBoolean(4,m.isGroup()); s.setString(5,com.chatapp.common.protocol.Packet.serializePayload(m)); s.executeUpdate();
        }
    }
    public synchronized java.util.List<com.chatapp.common.model.ChatMessage> loadHistory(String user, String target, boolean group) throws SQLException {
        java.util.List<com.chatapp.common.model.ChatMessage> result = new java.util.ArrayList<>();
        String filter = group ? "is_group=1 AND recipient=?" : "is_group=0 AND ((sender=? AND recipient=?) OR (sender=? AND recipient=?))";
        // shortcut: latest 200 messages; add pagination for older history.
        try (Connection c = DriverManager.getConnection(DB_URL); PreparedStatement s = c.prepareStatement("SELECT payload FROM (SELECT id,payload FROM messages WHERE " + filter + " ORDER BY id DESC LIMIT 200) ORDER BY id")) {
            if(group) s.setString(1,target);
            else { s.setString(1,user); s.setString(2,target); s.setString(3,target); s.setString(4,user); }
            try(ResultSet rs=s.executeQuery()) {
                while(rs.next()) result.add(new com.google.gson.Gson().fromJson(rs.getString(1),com.chatapp.common.model.ChatMessage.class));
            }
        }
        return result;
    }
    public synchronized void saveRoom(com.chatapp.common.model.ChatRoom room) throws SQLException {
        try(Connection c=DriverManager.getConnection(DB_URL); PreparedStatement s=c.prepareStatement("INSERT OR REPLACE INTO rooms(room_id,payload) VALUES(?,?)")) {
            s.setString(1,room.getRoomId()); s.setString(2,com.chatapp.common.protocol.Packet.serializePayload(room)); s.executeUpdate();
        }
    }
    public synchronized java.util.List<com.chatapp.common.model.ChatRoom> loadRooms() throws SQLException {
        java.util.List<com.chatapp.common.model.ChatRoom> result=new java.util.ArrayList<>();
        try(Connection c=DriverManager.getConnection(DB_URL); Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT payload FROM rooms")) {
            while(rs.next()) result.add(new com.google.gson.Gson().fromJson(rs.getString(1),com.chatapp.common.model.ChatRoom.class));
        }
        return result;
    }
}

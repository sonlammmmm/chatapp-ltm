/**
 * AppConfig.java — Đọc và kiểm tra cấu hình dùng chung từ thuộc tính JVM, biến môi trường và tệp .env.
 */
package com.chatapp.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Runtime settings shared by the client, server and file transfers. */
public final class AppConfig {
    /** Khởi tạo đối tượng AppConfig với dữ liệu và tài nguyên ban đầu. */
    private AppConfig() {}

    /** Đọc cấu hình theo thứ tự JVM, môi trường, .env rồi giá trị mặc định. */
    public static String get(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) value = System.getenv(name);
        if (value == null || value.isEmpty()) value = readDotEnv(Path.of(".env"), name);
        return value == null ? fallback : value;
    }

    /** Đọc cấu hình số nguyên và kiểm tra giới hạn cho phép. */
    public static int integer(String name, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(get(name, String.valueOf(fallback)));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new SecurityException(name + " phải là số nguyên từ " + min + " đến " + max + ".");
        }
    }

    /** Lấy địa chỉ máy chủ chat từ cấu hình. */
    public static String serverHost() { return get("CHATAPP_SERVER_HOST", "127.0.0.1"); }
    /** Lấy cổng TCP của máy chủ chat. */
    public static int serverPort() { return integer("CHATAPP_SERVER_PORT", 8888, 1024, 65535); }
    /** Lấy thời gian chờ kết nối TCP tính bằng mili giây. */
    public static int connectTimeout() { return integer("CHATAPP_CONNECT_TIMEOUT_MS", 10000, 1, 300000); }
    /** Lấy thời gian chờ phản hồi yêu cầu tính bằng mili giây. */
    public static int requestTimeout() { return integer("CHATAPP_REQUEST_TIMEOUT_MS", 15000, 1, 300000); }
    /** Lấy thời gian chờ kết nối và thao tác truyền tệp tính bằng mili giây. */
    public static int fileTimeout() { return integer("CHATAPP_P2P_TIMEOUT_MS", 30000, 1, 300000); }
    /** Đọc chế độ truyền tệp và kiểm tra giá trị p2p hoặc relay. */
    public static String fileMode() {
        String mode = get("CHATAPP_FILE_MODE", "p2p").trim();
        if (!mode.equals("p2p") && !mode.equals("relay"))
            throw new SecurityException("CHATAPP_FILE_MODE phải là p2p hoặc relay.");
        return mode;
    }
    /** Lấy cổng máy chủ chuyển tiếp tệp. */
    public static int relayPort() { return integer("CHATAPP_FILE_RELAY_PORT", 8889, 1024, 65535); }
    /** Lấy đường dẫn tệp cơ sở dữ liệu SQLite. */
    public static Path databasePath() { return Path.of(get("CHATAPP_DB_PATH", "database/chatapp.db")); }

    /** Kiểm tra các giá trị cấu hình trước khi chạy ứng dụng. */
    public static void validate() {
        if (serverHost().trim().isEmpty()) throw new SecurityException("CHATAPP_SERVER_HOST không được để trống.");
        serverPort(); connectTimeout(); requestTimeout(); fileTimeout(); fileMode(); relayPort();
        try {
            if (databasePath().getFileName() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new SecurityException("CHATAPP_DB_PATH không hợp lệ."); }
    }

    // Literal values only; no shell expansion or variable interpolation.
    /** Đọc giá trị nguyên văn trong .env, kiểm tra khai báo trùng và giá trị rỗng. */
    public static String readDotEnv(java.nio.file.Path file, String name) {
        if (!java.nio.file.Files.exists(file)) return null;
        try {
            String value = null;
            for (String line : java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int equals = line.indexOf('=');
                if (equals < 0 || !line.substring(0, equals).trim().equals(name)) continue;
                if (value != null) throw new SecurityException(name + " bị khai báo nhiều lần trong .env.");
                value = line.substring(equals + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.isEmpty()) throw new SecurityException(name + " trong .env đang để trống.");
            }
            return value;
        } catch (java.io.IOException e) {
            throw new SecurityException("Không thể đọc .env. Kiểm tra quyền truy cập.");
        }
    }

}

package com.chatapp.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tiện ích băm mật khẩu bảo mật sử dụng thuật toán SHA-256 kết hợp Salt.
 */
public class HashUtil {

    /**
     * Tạo một chuỗi Salt ngẫu nhiên 16 bytes.
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Băm mật khẩu với SHA-256 kết hợp Salt.
     * @param password Mật khẩu gốc
     * @param salt Chuỗi Salt ngẫu nhiên
     * @return Chuỗi Hex đại diện cho giá trị băm
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashedPassword) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Thuật toán băm SHA-256 không được hỗ trợ", e);
        }
    }

    /**
     * Kiểm tra mật khẩu người dùng nhập có khớp với mật khẩu đã băm trong DB không.
     */
    public static boolean verifyPassword(String inputPassword, String salt, String expectedHash) {
        String calculatedHash = hashPassword(inputPassword, salt);
        return calculatedHash.equalsIgnoreCase(expectedHash);
    }
}

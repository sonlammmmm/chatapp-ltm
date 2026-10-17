package com.chatapp.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Tiện ích mã hóa đối xứng AES (Advanced Encryption Standard).
 * Bảo vệ nội dung tin nhắn và tệp tin khi truyền qua Socket.
 */
public class AESUtil {

    // Khóa bí mật mặc định dùng cho mã hóa gói tin (có thể tùy biến theo từng phiên trao đổi)
    private static final String DEFAULT_SECRET = "LTM_NHOM03_CHAT_SECRET_KEY_2026";
    private static SecretKeySpec secretKeySpec;

    static {
        setKey(DEFAULT_SECRET);
    }

    /**
     * Khởi tạo khóa AES 128-bit từ chuỗi bí mật bằng SHA-1/SHA-256.
     */
    public static void setKey(String myKey) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Lấy 16 bytes = 128 bit AES
            secretKeySpec = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Mã hóa chuỗi văn bản bằng AES, trả về chuỗi Base64.
     */
    public static String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] encrypted = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            System.err.println("Lỗi khi mã hóa AES: " + e.getMessage());
            return strToEncrypt; // Trả về chuỗi gốc nếu lỗi
        }
    }

    /**
     * Giải mã chuỗi Base64 đã mã hóa AES thành chuỗi ban đầu.
     */
    public static String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] decoded = Base64.getDecoder().decode(strToDecrypt);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Nếu chuỗi chưa được mã hóa hoặc lỗi giải mã thì giữ nguyên
            return strToDecrypt;
        }
    }
}

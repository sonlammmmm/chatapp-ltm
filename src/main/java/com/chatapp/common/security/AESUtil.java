package com.chatapp.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Tiện ích mã hóa đối xứng AES (Advanced Encryption Standard).
 * Sử dụng thuật toán AES/CBC/PKCS5Padding với Vector khởi tạo (IV) ngẫu nhiên để chống mẫu rò rỉ dữ liệu.
 */
public class AESUtil {

    // Khóa bí mật mặc định dùng cho mã hóa gói tin (có thể tùy biến theo từng phiên trao đổi)
    private static final String DEFAULT_SECRET = "LTM_NHOM03_CHAT_SECRET_KEY_2026";
    private static SecretKeySpec secretKeySpec;
    private static final SecureRandom secureRandom = new SecureRandom();

    static {
        setKey(DEFAULT_SECRET);
    }

    /**
     * Khởi tạo khóa AES 128-bit từ chuỗi bí mật bằng SHA-256.
     */
    public static void setKey(String myKey) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Lấy 16 bytes = 128 bit AES
            secretKeySpec = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cấu hình khóa AES", e);
        }
    }

    /**
     * Mã hóa chuỗi văn bản bằng AES/CBC/PKCS5Padding với IV ngẫu nhiên 16 bytes.
     * Dữ liệu trả về gồm: [16 bytes IV] + [Bản mã], được bọc trong Base64.
     */
    public static String encrypt(String strToEncrypt) {
        if (strToEncrypt == null) return null;
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

            // Ghép IV và bản mã lại với nhau
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            System.err.println("[AES] Lỗi khi mã hóa dữ liệu: " + e.getMessage());
            throw new SecurityException("Mã hóa tin nhắn thất bại, chặn rò rỉ dữ liệu qua mạng", e);
        }
    }

    /**
     * Giải mã chuỗi Base64 đã mã hóa.
     * Tự động nhận diện định dạng AES-CBC (kèm IV) hoặc fallback về AES-ECB cho tương thích ngược.
     */
    public static String decrypt(String strToDecrypt) {
        if (strToDecrypt == null || strToDecrypt.trim().isEmpty()) {
            return strToDecrypt;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(strToDecrypt);

            // Thử giải mã bằng AES/CBC/PKCS5Padding (yêu cầu tối thiểu 16 bytes IV + ít nhất 1 block 16 bytes)
            if (combined.length >= 32) {
                try {
                    byte[] iv = Arrays.copyOfRange(combined, 0, 16);
                    byte[] cipherBytes = Arrays.copyOfRange(combined, 16, combined.length);

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));
                    return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // Fallback thử giải mã ECB cũ nếu đây là bản mã cũ
                }
            }

            // Fallback AES/ECB/PKCS5Padding cho dữ liệu cũ
            Cipher ecbCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            ecbCipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return new String(ecbCipher.doFinal(combined), StandardCharsets.UTF_8);

        } catch (Exception e) {
            // Nếu chuỗi không phải Base64 hoặc là tin nhắn văn bản thường chưa mã hóa
            return strToDecrypt;
        }
    }
}

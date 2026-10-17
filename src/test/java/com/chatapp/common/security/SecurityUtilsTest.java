package com.chatapp.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityUtilsTest {

    @Test
    @DisplayName("Kiểm tra băm và xác thực mật khẩu SHA-256 kèm Salt ngẫu nhiên")
    void testPasswordHashingAndVerification() {
        String password = "StrongPassword@2026";
        String salt = HashUtil.generateSalt();

        assertNotNull(salt);
        assertFalse(salt.isEmpty());

        String hash = HashUtil.hashPassword(password, salt);
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hash phải có độ dài 64 ký tự hex");

        // Kiểm tra xác thực đúng
        assertTrue(HashUtil.verifyPassword(password, salt, hash), "Mật khẩu đúng phải xác thực thành công");

        // Kiểm tra xác thực sai
        assertFalse(HashUtil.verifyPassword("WrongPassword", salt, hash), "Mật khẩu sai phải bị từ chối");
        assertFalse(HashUtil.verifyPassword(password, HashUtil.generateSalt(), hash), "Salt khác phải không khớp");
    }

    @Test
    @DisplayName("Kiểm tra mã hóa và giải mã AES-CBC với IV ngẫu nhiên")
    void testAESEncryptionAndDecryption() {
        String plainText = "Xin chào, đây là tin nhắn mật kiểm thử đồ án Lập Trình Mạng 2026!";

        String cipherText1 = AESUtil.encrypt(plainText);
        assertNotNull(cipherText1);
        assertNotEquals(plainText, cipherText1, "Bản mã không được trùng với bản rõ");

        // Hai lần mã hóa cùng một bản rõ phải sinh ra bản mã khác nhau do có IV ngẫu nhiên (chống mẫu ECB)
        String cipherText2 = AESUtil.encrypt(plainText);
        assertNotEquals(cipherText1, cipherText2, "Hai lần mã hóa phải có bản mã khác nhau nhờ dynamic IV");

        // Giải mã thành công về chuỗi ban đầu
        String decrypted1 = AESUtil.decrypt(cipherText1);
        String decrypted2 = AESUtil.decrypt(cipherText2);
        assertEquals(plainText, decrypted1);
        assertEquals(plainText, decrypted2);
    }

    @Test
    @DisplayName("Kiểm tra AES xử lý an toàn với chuỗi rỗng và chuỗi chưa mã hóa")
    void testAESSafeHandling() {
        assertNull(AESUtil.encrypt(null));
        assertNull(AESUtil.decrypt(null));
        assertEquals("", AESUtil.decrypt(""));

        // Khi nhận chuỗi văn bản thông thường chưa mã hóa, decrypt trả về nguyên bản thay vì crash
        String rawText = "Plain message without encryption";
        assertEquals(rawText, AESUtil.decrypt(rawText));
    }
}

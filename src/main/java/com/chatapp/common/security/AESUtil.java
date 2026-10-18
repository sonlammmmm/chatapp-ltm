package com.chatapp.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AESUtil {
    // shortcut: shared demo key does not authenticate peers; use TLS certificates for deployment.
    private static volatile SecretKeySpec key;
    private static final SecureRandom RANDOM = new SecureRandom();
    static { setKey("LTM_NHOM03_CHAT_SECRET_KEY_2026"); }

    public static void setKey(String secret) {
        try {
            key = new SecretKeySpec(Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), 16), "AES");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static byte[] encryptBytes(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (Exception e) { throw new SecurityException("Encryption failed", e); }
    }

    public static byte[] decryptBytes(byte[] encrypted) {
        try {
            if (encrypted.length < 28) throw new SecurityException("Invalid encrypted frame");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, encrypted, 0, 12));
            return cipher.doFinal(encrypted, 12, encrypted.length - 12);
        } catch (Exception e) { throw new SecurityException("Invalid or modified encrypted frame", e); }
    }

    public static String encrypt(String plain) {
        return plain == null ? null : Base64.getEncoder().encodeToString(encryptBytes(plain.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            return new String(decryptBytes(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) { throw new SecurityException("Invalid ciphertext", e); }
    }
}

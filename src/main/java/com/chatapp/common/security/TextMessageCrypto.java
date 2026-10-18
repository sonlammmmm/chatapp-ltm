package com.chatapp.common.security;

import com.chatapp.common.model.ChatMessage;
import com.chatapp.common.protocol.Packet;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encryption for message bodies; separate from the legacy packet and file framing. */
public final class TextMessageCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TextMessageCrypto() {}

    public static void validateKey() { key(); }

    public static String encrypt(ChatMessage message) { return crypt(message, Cipher.ENCRYPT_MODE); }
    public static String decrypt(ChatMessage message) { return crypt(message, Cipher.DECRYPT_MODE); }

    private static String crypt(ChatMessage message, int mode) {
        try {
            if (message == null || empty(message.getMessageId()) || empty(message.getSender())
                    || empty(message.getRecipient()) || message.getContent() == null)
                throw new SecurityException("Message encryption requires an ID, sender, recipient, and content.");
            byte[] aad = Packet.serializePayload(new Object[]{message.getMessageId(), message.getSender(),
                    message.getRecipient(), message.isGroup()}).getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (mode == Cipher.ENCRYPT_MODE) {
                byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
                cipher.init(mode, key(), new GCMParameterSpec(128, iv));
                cipher.updateAAD(aad);
                byte[] encrypted = cipher.doFinal(message.getContent().getBytes(StandardCharsets.UTF_8));
                byte[] envelope = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, envelope, 0, iv.length);
                System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
                return "v1:" + Base64.getEncoder().encodeToString(envelope);
            }
            String value = message.getContent();
            if (!value.startsWith("v1:")) throw new SecurityException("Unsupported encrypted message version.");
            byte[] envelope = Base64.getDecoder().decode(value.substring(3));
            if (envelope.length < 28) throw new SecurityException("Invalid encrypted message.");
            byte[] iv = java.util.Arrays.copyOfRange(envelope, 0, 12);
            cipher.init(mode, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(envelope, 12, envelope.length - 12), StandardCharsets.UTF_8);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Cannot process encrypted message. Check the shared CHATAPP_TEXT_KEY configuration.");
        }
    }

    private static SecretKeySpec key() {
        String value = System.getProperty("chatapp.textKey");
        if (value == null || value.isEmpty()) value = com.chatapp.common.config.AppConfig.get("CHATAPP_TEXT_KEY", null);
        if (value == null || value.isEmpty()) {
            java.nio.file.Path file = java.nio.file.Path.of(System.getProperty("user.home"), ".chatapp", "text-key");
            if (java.nio.file.Files.exists(file)) {
                try { value = java.nio.file.Files.readString(file, StandardCharsets.UTF_8).trim(); }
                catch (java.io.IOException e) {
                    throw new SecurityException("Không thể đọc tệp khóa " + file + ". Kiểm tra quyền truy cập.");
                }
            }
        }
        try {
            byte[] bytes = value == null ? null : Base64.getDecoder().decode(value);
            if (bytes == null || bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (RuntimeException e) {
            throw new SecurityException("Thiếu khóa hoặc khóa không hợp lệ. Đặt chatapp.textKey, CHATAPP_TEXT_KEY, .env hoặc tệp ~/.chatapp/text-key bằng khóa Base64 32 byte dùng chung cho Server và Client.");
        }
    }
    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}

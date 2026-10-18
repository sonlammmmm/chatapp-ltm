package com.chatapp.common.security;

import com.chatapp.common.model.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Base64;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TextMessageCryptoTest {
    @org.junit.jupiter.api.io.TempDir Path keyHome;
    private String previous;

    @BeforeEach void configure() {
        previous = System.getProperty("chatapp.textKey");
        System.setProperty("chatapp.textKey", TextEncryptionFixture.KEY);
    }

    @AfterEach void restore() {
        if (previous == null) System.clearProperty("chatapp.textKey");
        else System.setProperty("chatapp.textKey", previous);
    }

    @Test void unicodeEmptyAndRandomizedAuthenticatedRoundTrip() {
        for (String content : new String[]{"Xin chào 🔒\nDòng thứ hai", ""}) {
            ChatMessage message = new ChatMessage("giahuy", "sonlam", content, false);
            String first = TextMessageCrypto.encrypt(message);
            String second = TextMessageCrypto.encrypt(message);
            assertTrue(first.startsWith("v1:"));
            assertNotEquals(first, second);
            assertEquals(content, message.getContent(), "helper must not mutate UI object");
            message.setContent(first);
            assertEquals(content, TextMessageCrypto.decrypt(message));
            assertEquals(first, message.getContent());
        }
    }

    @Test void rejectsTamperWrongKeyMalformedFramesAndMetadataChanges() {
        ChatMessage message = new ChatMessage("giahuy", "sonlam", "bí mật", false);
        String encrypted = TextMessageCrypto.encrypt(message);
        message.setContent(encrypted);
        message.setRecipient("ngochuy");
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        message.setRecipient("sonlam");
        message.setSender("ngochuy");
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        message.setSender("giahuy");
        String id = message.getMessageId();
        message.setMessageId("different");
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        message.setMessageId(id);
        message.setGroup(true);
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        message.setGroup(false);
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[bytes.length - 1] ^= 1;
        message.setContent("v1:" + Base64.getEncoder().encodeToString(bytes));
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        for (String invalid : new String[]{"plaintext", "v2:" + encrypted.substring(3), "v1:%%%", "v1:AA=="}) {
            message.setContent(invalid);
            assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
        }
        message.setContent(encrypted);
        System.setProperty("chatapp.textKey", Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(SecurityException.class, () -> TextMessageCrypto.decrypt(message));
    }

    @Test void requiresRealKeyAndCompleteMessageContext() {
        for (String invalid : new String[]{"not base64", Base64.getEncoder().encodeToString(new byte[16])}) {
            System.setProperty("chatapp.textKey", invalid);
            assertThrows(SecurityException.class, TextMessageCrypto::validateKey);
        }
        System.setProperty("chatapp.textKey", TextEncryptionFixture.KEY);
        assertDoesNotThrow(TextMessageCrypto::validateKey);
        ChatMessage message = new ChatMessage("giahuy", "sonlam", null, false);
        assertThrows(SecurityException.class, () -> TextMessageCrypto.encrypt(message));
        message.setContent("text");
        message.setMessageId(null);
        assertThrows(SecurityException.class, () -> TextMessageCrypto.encrypt(message));
    }

    @Test void missingEnvironmentFailsAndPropertyOverridesEnvironment() throws Exception {
        for (boolean configured : new boolean[]{false, true}) {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-Duser.home=" + keyHome);
            if (configured) command.add("-Dchatapp.textKey=" + TextEncryptionFixture.KEY);
            command.add("-cp"); command.add(System.getProperty("java.class.path"));
            command.add(KeyProbe.class.getName());
            ProcessBuilder builder = new ProcessBuilder(command).directory(keyHome.toFile()).redirectErrorStream(true);
            builder.environment().remove("CHATAPP_TEXT_KEY");
            if (configured) builder.environment().put("CHATAPP_TEXT_KEY", "invalid-environment-key");
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                assertEquals(configured ? 0 : 7, child.exitValue());
            } finally { child.destroyForcibly(); }
        }
    }

    @Test void localKeyWorksWithoutEnvironmentAndInvalidOverrideStillFails() throws Exception {
        java.nio.file.Files.createDirectories(keyHome.resolve(".chatapp"));
        java.nio.file.Files.writeString(keyHome.resolve(".chatapp/text-key"), TextEncryptionFixture.KEY + "\n");
        for (boolean invalidOverride : new boolean[]{false, true}) {
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            args.add("-Duser.home=" + keyHome);
            if (invalidOverride) args.add("-Dchatapp.textKey=invalid");
            args.add("-cp"); args.add(System.getProperty("java.class.path")); args.add(KeyProbe.class.getName());
            ProcessBuilder builder = new ProcessBuilder(args).directory(keyHome.toFile()).redirectErrorStream(true);
            builder.environment().remove("CHATAPP_TEXT_KEY");
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                assertEquals(invalidOverride ? 7 : 0, child.exitValue());
            } finally { child.destroyForcibly(); }
        }
    }

    @Test void dotenvParsesQuotesCommentsAndBase64PaddingAndRejectsAmbiguity() throws Exception {
        Path env = keyHome.resolve(".env");
        for (String quote : new String[]{"", "'", "\""}) {
            java.nio.file.Files.writeString(env, "# local config\nOTHER=value\nCHATAPP_TEXT_KEY = " + quote + TextEncryptionFixture.KEY + quote + "\n");
            assertEquals(TextEncryptionFixture.KEY, com.chatapp.common.config.AppConfig.readDotEnv(env, "CHATAPP_TEXT_KEY"));
        }
        java.nio.file.Files.writeString(env, "CHATAPP_TEXT_KEY=\n");
        assertThrows(SecurityException.class, () -> com.chatapp.common.config.AppConfig.readDotEnv(env, "CHATAPP_TEXT_KEY"));
        java.nio.file.Files.writeString(env, "CHATAPP_TEXT_KEY=first\nCHATAPP_TEXT_KEY=second\n");
        assertThrows(SecurityException.class, () -> com.chatapp.common.config.AppConfig.readDotEnv(env, "CHATAPP_TEXT_KEY"));
    }

    @Test void dotenvIsLoadedFromWorkingDirectoryAndEnvironmentOverridesIt() throws Exception {
        java.nio.file.Files.writeString(keyHome.resolve(".env"), "CHATAPP_TEXT_KEY='" + TextEncryptionFixture.KEY + "'\n");
        for (boolean invalidEnvironment : new boolean[]{false, true}) {
            ProcessBuilder builder = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Duser.home=" + keyHome, "-cp", System.getProperty("java.class.path"), KeyProbe.class.getName())
                    .directory(keyHome.toFile()).redirectErrorStream(true);
            builder.environment().remove("CHATAPP_TEXT_KEY");
            if (invalidEnvironment) builder.environment().put("CHATAPP_TEXT_KEY", "invalid");
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                assertEquals(invalidEnvironment ? 7 : 0, child.exitValue());
            } finally { child.destroyForcibly(); }
        }
    }

    public static class KeyProbe {
        public static void main(String[] args) {
            try { TextMessageCrypto.validateKey(); }
            catch (SecurityException expected) { System.exit(7); }
        }
    }
}

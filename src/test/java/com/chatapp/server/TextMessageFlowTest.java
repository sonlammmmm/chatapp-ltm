package com.chatapp.server;

import com.chatapp.client.ChatClient;
import com.chatapp.client.ClientListener;
import com.chatapp.common.model.ChatMessage;
import com.chatapp.common.security.TextEncryptionFixture;
import com.chatapp.common.security.TextMessageCrypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TextMessageFlowTest {
    @TempDir Path directory;

    @Test void actualClientsDecryptLiveAndRestartedHistoryWithoutPlaintextInNewRows() throws Exception {
        for (String phase : new String[]{"write", "read"}) {
            Path log = directory.resolve("text-" + phase + ".log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Dchatapp.textKey=" + TextEncryptionFixture.KEY, "-cp", System.getProperty("java.class.path"),
                    Probe.class.getName(), phase).directory(directory.toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Text integration probe timeout");
                assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
            } finally { process.destroyForcibly(); }
        }
    }

    public static class Session implements AutoCloseable {
        public final ChatClient client = new ChatClient();
        public final CountDownLatch login = new CountDownLatch(1);
        public final BlockingQueue<ChatMessage> live = new LinkedBlockingQueue<>();
        public final BlockingQueue<List<ChatMessage>> history = new LinkedBlockingQueue<>();
        public final BlockingQueue<String> receipts = new LinkedBlockingQueue<>();
        public Session(int port, String user) throws Exception {
            client.setListener((ClientListener) Proxy.newProxyInstance(Session.class.getClassLoader(),
                    new Class<?>[]{ClientListener.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "onLoginSuccess": login.countDown(); break;
                            case "onMessageReceived": live.add((ChatMessage) args[0]); break;
                            case "onHistoryReceived":
                                @SuppressWarnings("unchecked") List<ChatMessage> messages = (List<ChatMessage>) args[1];
                                history.add(messages); break;
                            case "onMessageStatusUpdated": receipts.add((String) args[0]); break;
                            default: break;
                        }
                        return null;
                    }));
            client.connect("127.0.0.1", port);
            client.login(user, "123456");
            if (!login.await(5, TimeUnit.SECONDS)) throw new AssertionError("Login failed: " + user);
        }
        public void close() { client.disconnect(); }
    }

    public static class Probe {
        static final String DIRECT = "Riêng tư tiếng Việt 🔒\nDòng hai";
        static final String GROUP = "Nội dung nhóm 🐈";
        public static void main(String[] args) throws Exception {
            ChatServer server = new ChatServer(0);
            server.start();
            try (Session a = new Session(server.getPort(), "giahuy"); Session b = new Session(server.getPort(), "sonlam")) {
                if (args[0].equals("write")) {
                    ChatMessage shown = a.client.sendDirectMessage("sonlam", DIRECT);
                    require(DIRECT.equals(shown.getContent()) && !shown.isEncrypted(), "Sender echo is not plaintext");
                    ChatMessage received = b.live.poll(5, TimeUnit.SECONDS);
                    require(received != null && DIRECT.equals(received.getContent()) && !received.isEncrypted()
                            && shown.getMessageId().equals(received.getMessageId()), "Live direct decrypt failed");
                    require(shown.getMessageId().equals(a.receipts.poll(5, TimeUnit.SECONDS)), "Direct receipt failed");
                    a.client.sendGroupMessage("lobby", GROUP);
                    ChatMessage group = b.live.poll(5, TimeUnit.SECONDS);
                    require(group != null && group.isGroup() && GROUP.equals(group.getContent()) && !group.isEncrypted(),
                            "Live group decrypt failed");
                    List<ChatMessage> stored = server.getDbManager().loadHistory("giahuy", "sonlam", false);
                    require(stored.size() == 1 && stored.get(0).isEncrypted()
                            && !DIRECT.equals(stored.get(0).getContent())
                            && DIRECT.equals(TextMessageCrypto.decrypt(stored.get(0))), "Encrypted persistence failed");
                    try (java.sql.Connection connection = DriverManager.getConnection("jdbc:sqlite:database/chatapp.db");
                         java.sql.Statement statement = connection.createStatement();
                         java.sql.ResultSet rows = statement.executeQuery("SELECT payload FROM messages")) {
                        while (rows.next()) require(!rows.getString(1).contains("Riêng tư tiếng Việt")
                                && !rows.getString(1).contains(GROUP), "New SQLite row contains plaintext");
                    }
                    // A pre-upgrade row remains readable; no automatic migration is promised.
                    server.getDbManager().saveMessage(new ChatMessage("giahuy", "sonlam", "legacy plaintext", false));
                } else {
                    b.client.requestHistory("giahuy", false);
                    List<ChatMessage> direct = b.history.poll(5, TimeUnit.SECONDS);
                    require(direct != null && direct.size() == 2 && DIRECT.equals(direct.get(0).getContent())
                            && "legacy plaintext".equals(direct.get(1).getContent())
                            && direct.stream().noneMatch(ChatMessage::isEncrypted), "Client restart/legacy history failed");
                    b.client.requestHistory("lobby", true);
                    List<ChatMessage> group = b.history.poll(5, TimeUnit.SECONDS);
                    require(group != null && group.size() == 1 && GROUP.equals(group.get(0).getContent())
                            && !group.get(0).isEncrypted(), "Restarted group history failed");
                }
            } finally { server.stop(); }
        }
        static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    }
}

package com.chatapp.server;

import com.chatapp.client.ChatClient;
import com.chatapp.client.ui.MainChatWindow;
import com.chatapp.client.ClientMain;
import com.chatapp.common.security.TextMessageCrypto;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Manual UI evidence fixture: real isolated server and clients, API-authenticated test accounts. */
public final class TextEncryptionUiFixture {
    public static void main(String[] args) throws Exception {
        TextMessageCrypto.validateKey();
        ChatServer server = new ChatServer(0);
        server.start();
        TextMessageFlowTest.Session a = new TextMessageFlowTest.Session(server.getPort(), "giahuy");
        TextMessageFlowTest.Session b = new TextMessageFlowTest.Session(server.getPort(), "sonlam");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { a.close(); b.close(); server.stop(); }));
        ClientMain.initTheme();
        SwingUtilities.invokeAndWait(() -> {
            show(a.client, "TEXT-UI-A | giahuy", 15, 25);
            show(b.client, "TEXT-UI-B | sonlam", 600, 65);
        });
        Files.writeString(Path.of("ui-ready.txt"), "port=" + server.getPort());
        new CountDownLatch(1).await();
    }
    private static void show(ChatClient client, String title, int x, int y) {
        MainChatWindow window = new MainChatWindow(client, client.getCurrentUser());
        window.setTitle(title);
        window.setSize(1050, 720);
        window.setLocation(x, y);
        window.setVisible(true);
    }
}

package com.chatapp.server.ui;

import com.chatapp.server.ChatServer;
import com.chatapp.client.ClientMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ServerWindowTest {
    @TempDir Path directory;
    @Test void serverControlsStateAndLogInIsolatedDatabase() throws Exception {
        Path log = directory.resolve("ui-test.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-DCHATAPP_DB_PATH=" + directory.resolve("test.db"),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName(),
                Path.of("target/server-ui.png").toAbsolutePath().toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(25, TimeUnit.SECONDS), "UI test timed out");
            assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8));
        } finally { child.destroyForcibly(); }
    }
    public static class Probe {
        static class Window extends ServerWindow {
            boolean allowStop;
            int confirmations;
            @Override protected boolean confirmStop() { confirmations++; return allowStop; }
        }
        static Window window;
        static <T> T field(String name) {
            try {
                Field f = ServerWindow.class.getDeclaredField(name); f.setAccessible(true);
                return (T)f.get(window);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        public static void main(String[] args) throws Exception {
            try {
                int port;
                try (java.net.ServerSocket free = new java.net.ServerSocket(0)) { port = free.getLocalPort(); }
                final int chosenPort = port;
                SwingUtilities.invokeAndWait(() -> {
                    ClientMain.initTheme(); window = new Window();
                    String version = field("appVersion");
                    assertFalse(version.contains("${"));
                    assertNotEquals("không xác định", version);
                    assertTrue(window.getTitle().contains("v" + version));
                    window.setSize(850, 520); window.addNotify(); window.validate();
                    JTextField ip = field("serverIpField");
                    assertFalse(ip.isEditable());
                    assertFalse(ip.getText().isEmpty());
                    assertFalse(ip.getText().contains("0.0.0.0"));
                    ip.selectAll(); assertEquals(ip.getText(), ip.getSelectedText());
                    assertTrue(ip.getWidth() > 0);
                    assertEquals("Trực tuyến (0)", ((JTabbedPane)field("tabbedPane")).getTitleAt(0));
                    assertTrue(((JLabel)field("usersEmpty")).isVisible());
                    ((JTextField)field("portField")).setText(String.valueOf(chosenPort));
                    ((JButton)field("btnToggle")).doClick();
                    ChatServer server = field("server"); assertTrue(server.isRunning());
                    server.getRoomManager().createRoom("Phòng thử nghiệm", "tester", "");
                    server.broadcastRoomList();
                });
                SwingUtilities.invokeAndWait(() -> {
                    JTabbedPane tabs = field("tabbedPane");
                    assertEquals("Phòng chat (3)", tabs.getTitleAt(1));
                    DefaultListModel<String> rooms = field("roomListModel");
                    assertTrue(java.util.Collections.list(rooms.elements()).stream().anyMatch(s -> s.contains("Phòng thử nghiệm")));
                    ChatServer server = field("server");
                    server.getDbManager().registerUser("new_user", "password", "Test user");
                    server.notifyStateChanged();
                });
                SwingUtilities.invokeAndWait(() -> {
                    assertEquals("Tài khoản (4)", ((JTabbedPane)field("tabbedPane")).getTitleAt(2));
                    ((JButton)field("btnToggle")).doClick();
                    assertTrue(((ChatServer)field("server")).isRunning(), "Cancel stop must keep server running");
                    window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    assertTrue(window.isDisplayable(), "Cancel close must keep window open");
                    assertEquals(2, window.confirmations);
                    JCheckBox follow = field("followLog"); follow.setSelected(false);
                    window.appendLog("Log line\n".repeat(250));
                    window.validate();
                    JScrollPane scroll = field("scrollLog");
                    scroll.getViewport().setViewPosition(new Point(0, 100));
                    int old = scroll.getVerticalScrollBar().getValue();
                    window.appendLog("New log while reading");
                    assertEquals(old, scroll.getVerticalScrollBar().getValue());
                });
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        window.validate();
                        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(850, 520, java.awt.image.BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = image.createGraphics(); window.getRootPane().printAll(g); g.dispose();
                        javax.imageio.ImageIO.write(image, "png", new java.io.File(args[0]));
                    } catch (Exception e) { throw new RuntimeException(e); }
                    window.allowStop = true;
                    ((JButton)field("btnToggle")).doClick();
                    assertFalse(((ChatServer)field("server")).isRunning());
                    assertEquals("Trực tuyến (0)", ((JTabbedPane)field("tabbedPane")).getTitleAt(0));
                    assertEquals("Phòng chat (0)", ((JTabbedPane)field("tabbedPane")).getTitleAt(1));
                    window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    assertFalse(window.isDisplayable());
                });
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (window != null) {
                        ChatServer server = field("server");
                        if (server != null) server.stop();
                        window.dispose();
                    }
                });
            }
        }
    }
}

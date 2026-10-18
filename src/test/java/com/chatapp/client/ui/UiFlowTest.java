package com.chatapp.client.ui;

import com.chatapp.client.ChatClient;
import com.chatapp.client.ClientMain;
import com.chatapp.common.model.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UiFlowTest {
    static class Client extends ChatClient {
        int creates;
        @Override public void joinRoom(String id) {}
        @Override public void requestHistory(String id, boolean group) {}
        @Override public boolean isConnected() { return true; }
        @Override public void createRoom(String name, String description) { creates++; }
    }
    static <T> T field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true);
        return (T) f.get(object);
    }
    @Test void disconnectKeepsDraftAndOfflineConversationsAndUnread() throws Exception {
        MainChatWindow[] window = new MainChatWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            ClientMain.initTheme();
            window[0] = new MainChatWindow(new Client(), new User("alice", "Alice", true));
            window[0].onUserListUpdated(Arrays.asList(new User("bob", "Bob", true)));
            window[0].onMessageReceived(new ChatMessage("bob", "alice", "Hello", false));
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                Map<String,Integer> unread = field(window[0], "unread");
                assertEquals(1, unread.get("bob"));
                JTextField input = field(window[0], "messageInputField"); input.setText("Draft");
                window[0].onConnectionLost();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertEquals("Draft", ((JTextField)field(window[0], "messageInputField")).getText());
                assertTrue(((JButton)field(window[0], "reconnect")).isVisible());
                assertFalse(((JButton)field(window[0], "btnSend")).isEnabled());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                DefaultListModel<User> model = field(window[0], "userListModel");
                assertEquals(1, model.size()); assertFalse(model.get(0).isOnline());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { window[0].dispose(); }
        });
    }
    @Test void createRoomWaitsForAcknowledgmentAndRetainsInputOnError() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Client client = new Client();
            CreateRoomDialog dialog = new CreateRoomDialog(null, client);
            try {
                JTextField name = field(dialog, "txtRoomName"); name.setText("Study group");
                JButton create = field(dialog, "btnCreate"); create.doClick();
                assertEquals(1, client.creates); assertFalse(create.isEnabled());
                assertTrue((Boolean)field(dialog, "pending"));
                dialog.complete(null, "Try again");
                assertEquals("Study group", name.getText()); assertTrue(create.isEnabled());
                create.doClick();
                dialog.complete(new ChatRoom("room_1", "Study group", "alice", ""), null);
                assertFalse((Boolean)field(dialog, "pending"));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { dialog.dispose(); }
        });
    }
    @Test void authenticationRemainsBusyUntilResponseAndCanTimeout() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LoginRegisterDialog dialog = new LoginRegisterDialog(null, new Client());
            try {
                java.lang.reflect.Method busy = LoginRegisterDialog.class.getDeclaredMethod("setBusy", boolean.class);
                busy.setAccessible(true); busy.invoke(dialog, true);
                assertFalse(((JButton)field(dialog, "btnLogin")).isEnabled());
                assertFalse(((JButton)field(dialog, "btnRegister")).isEnabled());
                javax.swing.Timer timer = field(dialog, "requestTimer");
                timer.getActionListeners()[0].actionPerformed(new java.awt.event.ActionEvent(timer, 0, "timeout"));
                assertTrue(((JButton)field(dialog, "btnLogin")).isEnabled());
                assertFalse((Boolean)field(dialog, "busy"));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { dialog.dispose(); }
        });
    }

    @Test void incomingMessageDoesNotPullReaderAwayFromHistory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainChatWindow window = new MainChatWindow(new Client(), new User("alice", "Alice", true));
            try {
                JScrollPane pane = field(window, "messagesScrollPane");
                JPanel body = field(window, "chatBodyPanel");
                ((java.awt.CardLayout)body.getLayout()).show(body, "MESSAGES");
                JPanel messages = field(window, "messagesPanel");
                messages.setPreferredSize(new java.awt.Dimension(400, 2000));
                window.addNotify(); window.validate();
                pane.getViewport().setViewSize(new java.awt.Dimension(400, 2000));
                pane.getViewport().setViewPosition(new java.awt.Point(0, 100));
                java.lang.reflect.Method append = MainChatWindow.class.getDeclaredMethod("appendMessageBubble", ChatMessage.class);
                append.setAccessible(true);
                append.invoke(window, new ChatMessage("bob", "lobby", "New message", true));
                assertEquals(100, pane.getVerticalScrollBar().getValue());
                assertTrue(((JButton)field(window, "newMessages")).isVisible());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { window.dispose(); }
        });
    }

    @Test void renderSmallWindowAndResizableForms() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainChatWindow chat = new MainChatWindow(new Client(), new User("alice", "Nguyễn Minh Anh", true));
            LoginRegisterDialog login = new LoginRegisterDialog(null, new Client());
            CreateRoomDialog room = new CreateRoomDialog(null, new Client());
            try {
                assertTrue(login.isResizable()); assertTrue(room.isResizable());
                chat.setSize(850, 550);
                for (java.awt.Window window : Arrays.asList(chat, login, room)) {
                    window.addNotify(); window.validate();
                    java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(window.getWidth(), window.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D graphics = image.createGraphics();
                    ((javax.swing.RootPaneContainer)window).getRootPane().printAll(graphics); graphics.dispose();
                    javax.imageio.ImageIO.write(image, "png", new java.io.File("target/" + window.getClass().getSimpleName() + ".png"));
                }
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { chat.dispose(); login.dispose(); room.dispose(); }
        });
    }

    @Test void roomHeaderTracksUpdatesAndConversationRowsStayCompact() throws Exception {
        MainChatWindow[] holder = new MainChatWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            ClientMain.initTheme();
            MainChatWindow window = new MainChatWindow(new Client(), new User("alice", "Alice", true));
            holder[0] = window;
            ChatRoom room = new ChatRoom("lobby", "Lập Trình Mạng", "alice", "Mô tả phòng rất dài ".repeat(20));
            room.addMember("bob");
            window.onRoomListUpdated(Arrays.asList(room));
            window.onUserListUpdated(Arrays.asList(new User("bob", "Nguyễn Minh Anh", true)));
        });
        SwingUtilities.invokeAndWait(() -> {
            MainChatWindow window = holder[0];
            try {
                assertTrue(((JLabel)field(window, "lblTargetSub")).getText().startsWith("2 thành viên"));
                JList<ChatRoom> rooms = field(window, "roomList");
                assertTrue(rooms.getScrollableTracksViewportWidth());
                window.setSize(850, 550); window.addNotify(); window.validate();
                JTabbedPane sidebar = field(window, "sidebarTabs");
                sidebar.setSelectedIndex(1); window.validate();
                assertTrue(rooms.getWidth() > 0);
                assertEquals(rooms.getParent().getWidth(), rooms.getWidth());
                JScrollPane roomScroll = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, rooms);
                assertFalse(roomScroll.getHorizontalScrollBar().isVisible());
                sidebar.setSelectedIndex(0);
                java.lang.reflect.Method select = MainChatWindow.class.getDeclaredMethod("selectUser", User.class);
                select.setAccessible(true); select.invoke(window, new User("bob", "Nguyễn Minh Anh", true));
                java.lang.reflect.Method append = MainChatWindow.class.getDeclaredMethod("appendMessageBubble", ChatMessage.class);
                append.setAccessible(true);
                for (int index = 0; index < 4; index++) {
                    ChatMessage message = new ChatMessage(index % 2 == 0 ? "bob" : "alice", "bob", "Xin chào " + index, false);
                    message.setSenderFullName("Nguyễn Minh Anh"); message.setStatus(ChatMessage.Status.DELIVERED);
                    append.invoke(window, message);
                }
                append.invoke(window, new ChatMessage("bob", "alice", "Tin dài cần xuống dòng: ".repeat(12), false));
                window.validate();
                JPanel messages = field(window, "messagesPanel");
                for (java.awt.Component row : messages.getComponents()) {
                    assertEquals(row.getPreferredSize().height, row.getHeight(), "Rows must not stretch into empty space");
                }
                assertTrue(messages.getComponent(0).getHeight() < 100);
                assertEquals(40, ((JTextField)field(window, "messageInputField")).getHeight());
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(850, 550, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = image.createGraphics(); window.getRootPane().printAll(g); g.dispose();
                javax.imageio.ImageIO.write(image, "png", new java.io.File("target/client-conversation.png"));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { window.dispose(); }
        });
    }

}

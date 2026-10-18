package com.chatapp.client.ui.components;

import com.chatapp.common.model.FileTransferRequest;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FileCardPanelTest {
    private List<Component> descendants(Container parent) {
        List<Component> all = new ArrayList<>();
        for (Component c : parent.getComponents()) {
            all.add(c);
            if (c instanceof Container) all.addAll(descendants((Container)c));
        }
        return all;
    }
    private boolean button(FileCardPanel card, String text) {
        return descendants(card).stream().anyMatch(c -> c instanceof JButton && text.equals(((JButton)c).getText()));
    }
    @Test void receiveActionsProgressCompletionAndTerminalState() throws Exception {
        FileCardPanel[] holder = new FileCardPanel[1];
        AtomicBoolean cancelled = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            FileCardPanel card = new FileCardPanel(new FileTransferRequest("alice", "bob", "report.zip", 1024, "0".repeat(64)), false);
            holder[0] = card;
            card.offer(() -> card.transferring(() -> cancelled.set(true)), () -> card.onError("Rejected"));
            assertTrue(button(card, "Nhận file"));
            assertTrue(button(card, "Từ chối"));
            assertFalse(button(card, "Mở file"));
            descendants(card).stream().filter(c -> c instanceof JButton && "Nhận file".equals(((JButton)c).getText()))
                    .forEach(c -> ((JButton)c).doClick());
            assertTrue(button(card, "Hủy truyền"));
            assertFalse(button(card, "Nhận file"));
        });
        holder[0].onProgress(512, 1024, 25, 50);
        SwingUtilities.invokeAndWait(() -> assertTrue(descendants(holder[0]).stream().anyMatch(c -> c instanceof JProgressBar && ((JProgressBar)c).getValue() == 50)));
        holder[0].onCompleted("report.zip");
        holder[0].onError("Late error must not replace completion");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(button(holder[0], "Mở file"));
            assertFalse(button(holder[0], "Hủy truyền"));
            holder[0].cancelTransfer();
            assertFalse(cancelled.get());
        });
    }

    @Test void failureRemovesReceiveAndOpenActions() throws Exception {
        FileCardPanel[] holder = new FileCardPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new FileCardPanel(new FileTransferRequest("alice", "bob", "file.txt", 0, "0".repeat(64)), false);
            holder[0].offer(() -> {}, () -> {});
        });
        holder[0].onError("Rejected");
        holder[0].onProgress(0, 0, 0, 100);
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(button(holder[0], "Nhận file"));
            assertFalse(button(holder[0], "Mở file"));
            assertTrue(descendants(holder[0]).stream().anyMatch(c -> c instanceof JLabel && "Rejected".equals(((JLabel)c).getText())));
        });
    }
}

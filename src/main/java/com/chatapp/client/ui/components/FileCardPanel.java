package com.chatapp.client.ui.components;

import com.chatapp.client.file.FileTransferListener;
import com.chatapp.common.model.FileTransferRequest;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/** A local transfer entry; all transfer callbacks update Swing on the EDT. */
public class FileCardPanel extends JPanel implements FileTransferListener {
    private final JLabel status = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private Runnable cancel;
    private File completedFile;
    private boolean terminal;

    public FileCardPanel(FileTransferRequest request, boolean outgoing) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(6, outgoing ? 70 : 12, 6, outgoing ? 12 : 70));
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 80, 100)), new EmptyBorder(12, 14, 12, 14)));
        card.setBackground(new Color(30, 38, 50));
        JPanel details = new JPanel(new GridLayout(0, 1, 0, 5));
        details.setOpaque(false);
        JLabel name = new JLabel();
        name.putClientProperty("html.disable", true);
        name.setText(request.getFileName());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        details.add(name);
        details.add(new JLabel(request.getFormattedSize() + " • " + (outgoing ? "Gửi tới " + request.getReceiver() : "Từ " + request.getSender())));
        details.add(status);
        progress.setStringPainted(true);
        details.add(progress);
        actions.setOpaque(false);
        card.add(details, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        add(card);
        status.setText(outgoing ? "Chờ người nhận chấp nhận" : "Bạn có muốn nhận file này?");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        setPreferredSize(new Dimension(460, 180));
    }

    private void button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        actions.add(button);
    }

    public void offer(Runnable accept, Runnable reject) {
        button("Nhận file", accept);
        button("Từ chối", reject);
    }

    public void transferring(Runnable cancelAction) {
        cancel = cancelAction;
        actions.removeAll();
        status.setText("Đang kết nối truyền tệp...");
        button("Hủy truyền", () -> {
            cancel.run();
            onError("Đã hủy; gửi lại cùng file để tiếp tục.");
        });
        revalidate();
        repaint();
    }

    public void cancelTransfer() { if (!terminal && cancel != null) cancel.run(); }

    @Override public void onProgress(long bytes, long total, double speed, int percent) {
        SwingUtilities.invokeLater(() -> {
            if (terminal) return;
            progress.setValue(percent);
            status.setText(String.format("Đang truyền %d%% • %.1f KB/s", percent, speed));
        });
    }

    @Override public void onCompleted(String path) {
        SwingUtilities.invokeLater(() -> {
            if (terminal) return;
            terminal = true;
            completedFile = new File(path);
            status.setText("Hoàn tất • Đã kiểm tra SHA-256");
            progress.setValue(100);
            actions.removeAll();
            button("Mở file", () -> {
                try {
                    if (!completedFile.isFile()) throw new java.io.IOException("File đã bị xóa hoặc di chuyển.");
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                        throw new java.io.IOException("Máy không hỗ trợ mở file trực tiếp.");
                    Desktop.getDesktop().open(completedFile);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Không thể mở file", JOptionPane.ERROR_MESSAGE);
                }
            });
            revalidate();
            repaint();
        });
    }

    @Override public void onError(String error) {
        SwingUtilities.invokeLater(() -> {
            if (terminal) return;
            terminal = true;
            status.setText(error);
            status.setToolTipText(error);
            actions.removeAll();
            revalidate();
            repaint();
        });
    }
}

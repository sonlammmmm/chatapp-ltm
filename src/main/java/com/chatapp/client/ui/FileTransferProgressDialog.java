package com.chatapp.client.ui;

import com.chatapp.client.file.FileTransferListener;
import com.chatapp.client.file.P2PFileReceiver;
import com.chatapp.client.file.P2PFileSender;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Hộp thoại hiển thị tiến độ truyền tệp tin P2P thời gian thực (Progress Bar, Tốc độ, Resume).
 */
public class FileTransferProgressDialog extends JDialog implements FileTransferListener {
    private final String fileName;
    private final long totalBytes;
    private final boolean isSender;
    private final Runnable cancelCallback;

    private JProgressBar progressBar;
    private JLabel lblStatus;
    private JLabel lblSpeed;
    private JLabel lblResumeNotice;
    private JButton btnCancel;

    public FileTransferProgressDialog(Frame owner, String fileName, long totalBytes, boolean isSender, Runnable cancelCallback) {
        super(owner, isSender ? "Đang gửi tệp tin P2P..." : "Đang nhận tệp tin P2P...", false);
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.isSender = isSender;
        this.cancelCallback = cancelCallback;

        setSize(520, 240);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        initUI();
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(10, 15));
        content.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Header info
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        JLabel titleLabel = new JLabel((isSender ? "🚀 Đang gửi tệp: " : "📥 Đang nhận tệp: ") + fileName);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        lblResumeNotice = new JLabel("⚡ Hỗ trợ Resume Transfer: Truyền theo khối 64KB không nghẽn RAM");
        lblResumeNotice.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblResumeNotice.setForeground(new Color(120, 180, 240));

        headerPanel.add(titleLabel);
        headerPanel.add(lblResumeNotice);
        content.add(headerPanel, BorderLayout.NORTH);

        // Center progress
        JPanel centerPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(460, 25));

        lblStatus = new JLabel("Đang chuẩn bị kết nối P2P...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        lblSpeed = new JLabel("Tốc độ: 0 KB/s");
        lblSpeed.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSpeed.setForeground(new Color(150, 160, 175));

        centerPanel.add(progressBar);
        centerPanel.add(lblStatus);
        centerPanel.add(lblSpeed);
        content.add(centerPanel, BorderLayout.CENTER);

        // Bottom button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnCancel = new JButton("Hủy bỏ");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancel.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn hủy truyền tệp? Dữ liệu dở dang sẽ được lưu lại (.part) để truyền tiếp sau.",
                    "Xác nhận hủy", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                if (cancelCallback != null) {
                    cancelCallback.run();
                }
                dispose();
            }
        });
        bottomPanel.add(btnCancel);
        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public void onProgress(long bytesTransferred, long totalBytes, double speedKBps, int percent) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressBar.setString(percent + "%");

            lblStatus.setText("Đã truyền: " + formatBytes(bytesTransferred) + " / " + formatBytes(totalBytes) + " (" + percent + "%)");

            if (speedKBps > 1024) {
                lblSpeed.setText(String.format("Tốc độ: %.2f MB/s", speedKBps / 1024.0));
            } else {
                lblSpeed.setText(String.format("Tốc độ: %.2f KB/s", speedKBps));
            }
        });
    }

    @Override
    public void onCompleted(String filePath) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            progressBar.setString("100% Hoàn tất");
            lblStatus.setText("✓ Quá trình truyền tải hoàn thành thành công!");
            lblStatus.setForeground(new Color(40, 167, 69));
            btnCancel.setText("Đóng");
            btnCancel.removeActionListener(btnCancel.getActionListeners()[0]);
            btnCancel.addActionListener(e -> dispose());

            JOptionPane.showMessageDialog(this,
                    (isSender ? "Đã gửi tệp thành công:\n" : "Đã nhận tệp thành công và lưu tại:\n") + filePath,
                    "Truyền tệp hoàn tất", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("✗ " + errorMessage);
            lblStatus.setForeground(new Color(220, 53, 69));
            btnCancel.setText("Đóng");
            btnCancel.removeActionListener(btnCancel.getActionListeners()[0]);
            btnCancel.addActionListener(e -> dispose());
        });
    }
}

/**
 * CreateRoomDialog.java — Hiển thị biểu mẫu tạo phòng chat và kết quả phản hồi từ Server.
 */
package com.chatapp.client.ui;

import com.chatapp.common.config.AppConfig;
import com.chatapp.client.ChatClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Hộp thoại tạo phòng chat nhóm mới (Group Chat).
 */
public class CreateRoomDialog extends JDialog {
    private final ChatClient client;
    private JTextField txtRoomName;
    private JTextField txtDesc;
    private JButton btnCreate;
    private final JLabel status = new JLabel(" ");
    private boolean pending;
    private javax.swing.Timer timeout;

    /** Khởi tạo đối tượng CreateRoomDialog với dữ liệu và tài nguyên ban đầu. */
    public CreateRoomDialog(Frame owner, ChatClient client) {
        super(owner, "Tạo phòng chat nhóm mới", true);
        this.client = client;

        setSize(420, 220);
        setLocationRelativeTo(owner);
        setResizable(true);

        initUI();
    }

    /** Hiển thị kết quả tạo phòng hoặc lỗi phản hồi từ Server. */
    public void complete(com.chatapp.common.model.ChatRoom room, String error) {
        if (!pending) return;
        pending = false;
        if (timeout != null) timeout.stop();
        if (room != null) { dispose(); return; }
        status.setText("<html>" + error.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        btnCreate.setEnabled(true);
    }

    /** Giải phóng cửa sổ và xử lý tài nguyên giao diện liên quan. */
    @Override public void dispose() {
        if (timeout != null) timeout.stop();
        super.dispose();
    }

    /** Tạo thành phần giao diện, bố cục và đăng ký các sự kiện thao tác. */
    private void initUI() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 12));

        JLabel lblName = new JLabel("Tên phòng chat:");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txtRoomName = new JTextField();

        JLabel lblDesc = new JLabel("Mô tả phòng:");
        lblDesc.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txtDesc = new JTextField();

        formPanel.add(lblName);
        formPanel.add(txtRoomName);
        formPanel.add(lblDesc);
        formPanel.add(txtDesc);

        panel.add(formPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnCancel = new JButton("Hủy");
        btnCancel.addActionListener(e -> dispose());

        btnCreate = new JButton("Tạo phòng");
        btnCreate.setBackground(new Color(0, 78, 160));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.addActionListener(e -> {
            String name = txtRoomName.getText().trim();
            String desc = txtDesc.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng chat!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (pending) return;
            if (!client.isConnected()) { status.setText("Chưa kết nối máy chủ."); return; }
            pending = true;
            btnCreate.setEnabled(false);
            status.setText("Đang tạo phòng…");
            timeout = new javax.swing.Timer(AppConfig.requestTimeout(), event -> complete(null, "Chưa nhận được phản hồi. Kiểm tra danh sách phòng trước khi thử lại."));
            timeout.setRepeats(false);
            timeout.start();
            client.createRoom(name, desc);
        });

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnCreate);
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(status, BorderLayout.NORTH);
        footer.add(buttonPanel, BorderLayout.SOUTH);
        panel.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btnCreate);

        setContentPane(new JScrollPane(panel));
        setSize(560, 240);
    }
}

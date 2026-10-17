package com.chatapp.client.ui;

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

    public CreateRoomDialog(Frame owner, ChatClient client) {
        super(owner, "Tạo phòng chat nhóm mới", true);
        this.client = client;

        setSize(420, 220);
        setLocationRelativeTo(owner);
        setResizable(false);

        initUI();
    }

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

        panel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnCancel = new JButton("Hủy");
        btnCancel.addActionListener(e -> dispose());

        JButton btnCreate = new JButton("Tạo phòng");
        btnCreate.setBackground(new Color(0, 122, 255));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.addActionListener(e -> {
            String name = txtRoomName.getText().trim();
            String desc = txtDesc.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng chat!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            client.createRoom(name, desc);
            dispose();
        });

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnCreate);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }
}

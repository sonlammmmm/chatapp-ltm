/**
 * FileRelayProtocol.java — Thực hiện bắt tay xác thực phiên truyền tệp với máy chủ relay.
 */
package com.chatapp.common.protocol;

import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.security.AESUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/** Authenticate a separate file socket before passing the existing encrypted file stream. */
public final class FileRelayProtocol {
    /** Khởi tạo đối tượng FileRelayProtocol với dữ liệu và tài nguyên ban đầu. */
    private FileRelayProtocol() { }

    /** Gửi mã phiên, token và vai trò để tham gia kết nối relay. */
    public static void join(Socket socket, FileTransferRequest request, boolean sender) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeUTF(AESUtil.encrypt(request.getTransferId() + "\n" + request.getRelayToken()
                + "\n" + (sender ? "sender" : "receiver")));
        out.flush();
        if (!new DataInputStream(socket.getInputStream()).readBoolean())
            throw new IOException("Máy chủ từ chối phiên truyền tệp hoặc phiên đã hết hạn.");
    }
}

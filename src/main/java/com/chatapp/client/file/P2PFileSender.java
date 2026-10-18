package com.chatapp.client.file;

import com.chatapp.common.config.AppConfig;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.protocol.FileRelayProtocol;

import java.io.*;
import java.net.Socket;
import com.chatapp.common.security.AESUtil;
import java.util.Arrays;

/**
 * Xử lý gửi tệp tin qua kết nối TCP P2P trực tiếp tới máy người nhận.
 * Hỗ trợ Resume Transfer qua cơ chế Offset và chia nhỏ khối dữ liệu 64KB.
 */
public class P2PFileSender implements Runnable {
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB
    private final FileTransferRequest request;
    private final File fileToSend;
    private final FileTransferListener listener;
    private Socket socket;
    private volatile boolean isCancelled = false;

    public P2PFileSender(FileTransferRequest request, File fileToSend, FileTransferListener listener) {
        this.request = request;
        this.fileToSend = fileToSend;
        this.listener = listener;
    }

    public void cancel() {
        this.isCancelled = true;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        DataInputStream dis = null;
        DataOutputStream dos = null;
        RandomAccessFile raf = null;

        try {
            if (isCancelled) return;
            // Kết nối trực tiếp tới IP và Port P2P của người nhận
            String targetIp = request.getReceiverIp();
            if (targetIp == null || targetIp.trim().isEmpty()) throw new IOException("Thiếu địa chỉ máy nhận tệp.");
            int targetPort = request.getReceiverPort();

            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(targetIp, targetPort), AppConfig.fileTimeout());
            socket.setSoTimeout(AppConfig.fileTimeout());
            if (isCancelled) return;
            if (request.isRelayed()) FileRelayProtocol.join(socket, request, true);
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Nhận offset resume từ phía người nhận
            long resumeOffset = dis.readLong();
            if (resumeOffset < 0 || resumeOffset > fileToSend.length() || fileToSend.length() != request.getFileSize())
                throw new IOException("Invalid resume offset or changed source file");
            request.setResumeOffset(resumeOffset);

            raf = new RandomAccessFile(fileToSend, "r");
            if (resumeOffset > 0 && resumeOffset < fileToSend.length()) {
                raf.seek(resumeOffset); // Nhảy tới vị trí cần truyền tiếp
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesSent = resumeOffset;
            long totalBytes = fileToSend.length();

            long startTime = System.currentTimeMillis();
            long lastCalcTime = startTime;
            long bytesSinceLastCalc = 0;
            double speedKBps = 0;

            int bytesRead;
            while (!isCancelled && bytesSent < totalBytes) {
                int toRead = (int) Math.min(BUFFER_SIZE, totalBytes - bytesSent);
                bytesRead = raf.read(buffer, 0, toRead);
                if (bytesRead == -1) break;

                byte[] encrypted = AESUtil.encryptBytes(Arrays.copyOf(buffer, bytesRead));
                dos.writeInt(encrypted.length);
                dos.write(encrypted);
                bytesSent += bytesRead;
                bytesSinceLastCalc += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastCalcTime >= 500) {
                    double elapsedSec = (now - lastCalcTime) / 1000.0;
                    speedKBps = (bytesSinceLastCalc / 1024.0) / elapsedSec;
                    int percent = (int) ((bytesSent * 100) / totalBytes);

                    if (listener != null) {
                        listener.onProgress(bytesSent, totalBytes, speedKBps, percent);
                    }
                    lastCalcTime = now;
                    bytesSinceLastCalc = 0;
                }
            }

            dos.flush();

            if (isCancelled) {
                if (listener != null) listener.onError("Quá trình gửi đã bị hủy.");
                return;
            }

            if (bytesSent == totalBytes) {
                if (!dis.readBoolean()) throw new IOException("Receiver did not verify the file");
                if (listener != null) {
                    listener.onProgress(totalBytes, totalBytes, 0, 100);
                    listener.onCompleted(fileToSend.getAbsolutePath());
                }
            } else {
                if (listener != null) {
                    listener.onError("Quá trình truyền bị đứt đoạn giữa chừng tại " + bytesSent + "/" + totalBytes + " bytes.");
                }
            }
        } catch (Exception e) {
            if (!isCancelled && listener != null) {
                listener.onError("Lỗi gửi tệp " + (request.isRelayed() ? "qua Server: " : "P2P: ") + e.getMessage());
            }
        } finally {
            try {
                if (raf != null) raf.close();
                if (dis != null) dis.close();
                if (dos != null) dos.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }
}

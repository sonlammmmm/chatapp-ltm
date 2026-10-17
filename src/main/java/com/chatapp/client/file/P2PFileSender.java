package com.chatapp.client.file;

import com.chatapp.common.model.FileTransferRequest;

import java.io.*;
import java.net.Socket;

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
            // Kết nối trực tiếp tới IP và Port P2P của người nhận
            String targetIp = request.getReceiverIp();
            if (targetIp == null || targetIp.isEmpty() || targetIp.equals("0:0:0:0:0:0:0:1")) {
                targetIp = "127.0.0.1";
            }
            int targetPort = request.getReceiverPort();

            socket = new Socket(targetIp, targetPort);
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Nhận offset resume từ phía người nhận
            long resumeOffset = dis.readLong();
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

                dos.write(buffer, 0, bytesRead);
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
                listener.onError("Lỗi gửi file P2P: " + e.getMessage());
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

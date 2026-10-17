package com.chatapp.client.file;

import com.chatapp.common.model.FileTransferRequest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Xử lý tiếp nhận tệp tin qua kết nối TCP P2P trực tiếp từ Client gửi.
 * Sử dụng luồng chia nhỏ 64KB, BufferedOutputStream và hỗ trợ Resume Transfer.
 */
public class P2PFileReceiver implements Runnable {
    private static final int BUFFER_SIZE = 64 * 1024; // Khối 64KB
    private final FileTransferRequest request;
    private final File saveDirectory;
    private final FileTransferListener listener;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private volatile boolean isCancelled = false;

    public P2PFileReceiver(FileTransferRequest request, File saveDirectory, FileTransferListener listener) throws IOException {
        this.request = request;
        this.saveDirectory = saveDirectory;
        this.listener = listener;

        // Mở ServerSocket tại cổng tự do do hệ điều hành cấp
        this.serverSocket = new ServerSocket(0);
        this.request.setReceiverPort(serverSocket.getLocalPort());
    }

    public int getListeningPort() {
        return serverSocket.getLocalPort();
    }

    public void cancel() {
        this.isCancelled = true;
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        DataInputStream dis = null;
        DataOutputStream dos = null;
        BufferedOutputStream fileOut = null;

        try {
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }

            File targetFile = new File(saveDirectory, request.getFileName());
            File partFile = new File(saveDirectory, request.getFileName() + ".part");

            // Kiểm tra truyền tiếp (Resume Transfer) nếu đã có file .part dở dang
            long resumeOffset = 0;
            if (partFile.exists()) {
                resumeOffset = partFile.length();
                if (resumeOffset >= request.getFileSize()) {
                    partFile.delete(); // Nếu file dở dang bị sai kích thước
                    resumeOffset = 0;
                }
            }

            // Chờ người gửi kết nối tới
            serverSocket.setSoTimeout(30000); // 30s timeout chờ kết nối
            clientSocket = serverSocket.accept();

            dis = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));

            // Gửi vị trí byte cần resume cho người gửi
            dos.writeLong(resumeOffset);
            dos.flush();

            // Mở file ở chế độ append (tiếp tục ghi từ offset)
            fileOut = new BufferedOutputStream(new FileOutputStream(partFile, true));

            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesReceived = resumeOffset;
            long totalBytes = request.getFileSize();

            long startTime = System.currentTimeMillis();
            long lastCalcTime = startTime;
            long bytesSinceLastCalc = 0;
            double speedKBps = 0;

            int bytesRead;
            while (!isCancelled && bytesReceived < totalBytes) {
                int toRead = (int) Math.min(BUFFER_SIZE, totalBytes - bytesReceived);
                bytesRead = dis.read(buffer, 0, toRead);
                if (bytesRead == -1) break;

                fileOut.write(buffer, 0, bytesRead);
                bytesReceived += bytesRead;
                bytesSinceLastCalc += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastCalcTime >= 500) { // Cập nhật tốc độ mỗi 0.5s
                    double elapsedSec = (now - lastCalcTime) / 1000.0;
                    speedKBps = (bytesSinceLastCalc / 1024.0) / elapsedSec;
                    int percent = (int) ((bytesReceived * 100) / totalBytes);

                    if (listener != null) {
                        listener.onProgress(bytesReceived, totalBytes, speedKBps, percent);
                    }
                    lastCalcTime = now;
                    bytesSinceLastCalc = 0;
                }
            }

            fileOut.flush();

            if (isCancelled) {
                if (listener != null) listener.onError("Đã hủy quá trình nhận file. Dữ liệu dở dang được giữ lại để tiếp tục (Resume) lần sau.");
                return;
            }

            if (bytesReceived == totalBytes) {
                // Đã nhận đầy đủ toàn bộ file: đổi tên từ .part thành tên file gốc
                fileOut.close();
                fileOut = null;

                if (targetFile.exists()) {
                    targetFile.delete();
                }
                boolean renamed = partFile.renameTo(targetFile);
                if (renamed) {
                    if (listener != null) {
                        listener.onProgress(totalBytes, totalBytes, 0, 100);
                        listener.onCompleted(targetFile.getAbsolutePath());
                    }
                } else {
                    if (listener != null) listener.onError("Không thể đổi tên file hoàn tất!");
                }
            } else {
                if (listener != null) {
                    listener.onError("Kết nối truyền tải bị ngắt quãng giữa chừng (" + bytesReceived + "/" + totalBytes + " bytes). Có thể truyền tiếp sau.");
                }
            }
        } catch (Exception e) {
            if (!isCancelled && listener != null) {
                listener.onError("Lỗi truyền file P2P: " + e.getMessage());
            }
        } finally {
            try {
                if (fileOut != null) fileOut.close();
                if (dis != null) dis.close();
                if (dos != null) dos.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (IOException ignored) {}
        }
    }
}

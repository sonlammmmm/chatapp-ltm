package com.chatapp.client.file;

import com.chatapp.common.config.AppConfig;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.protocol.FileRelayProtocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import com.chatapp.common.security.AESUtil;
import com.chatapp.common.security.HashUtil;

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
        if (!request.isRelayed()) {
            this.serverSocket = new ServerSocket(0);
            this.request.setReceiverPort(serverSocket.getLocalPort());
        }
    }

    public int getListeningPort() {
        return request.isRelayed() ? request.getReceiverPort() : serverSocket.getLocalPort();
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

            // Ngăn chặn tấn công Path Traversal (OWASP CWE-22)
            String rawFileName = request.getFileName() != null ? request.getFileName() : "received_file";
            String safeFileName = new File(rawFileName).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeFileName.trim().isEmpty()) {
                safeFileName = "download_" + System.currentTimeMillis();
            }

            File targetFile = new File(saveDirectory, safeFileName);
            if (request.getFileSize() < 0 || request.getFileChecksum() == null
                    || !request.getFileChecksum().matches("[0-9a-f]{64}")) throw new IOException("Invalid file metadata");
            File partFile = new File(saveDirectory, safeFileName + "." + request.getFileChecksum() + ".part");
            if (Files.isSymbolicLink(partFile.toPath())) throw new IOException("Unsafe partial file");
            int suffix = 1;
            while (targetFile.exists()) targetFile = new File(saveDirectory, safeFileName + " (" + suffix++ + ")");

            if (!targetFile.getCanonicalPath().startsWith(saveDirectory.getCanonicalPath())) {
                throw new SecurityException("Phát hiện đường dẫn tệp tin không an toàn (Path Traversal Attempt)!");
            }

            // Kiểm tra truyền tiếp (Resume Transfer) nếu đã có file .part dở dang
            long resumeOffset = 0;
            if (partFile.exists()) {
                resumeOffset = partFile.length();
                if (resumeOffset > request.getFileSize()) {
                    Files.delete(partFile.toPath());
                    resumeOffset = 0;
                }
            }

            // Chờ người gửi kết nối tới
            if (isCancelled) return;
            if (request.isRelayed()) {
                clientSocket = new Socket();
                clientSocket.connect(new java.net.InetSocketAddress(request.getReceiverIp(), request.getReceiverPort()), AppConfig.fileTimeout());
            } else {
                serverSocket.setSoTimeout(AppConfig.fileTimeout());
                clientSocket = serverSocket.accept();
            }
            clientSocket.setSoTimeout(AppConfig.fileTimeout());
            if (isCancelled) return;
            if (request.isRelayed()) FileRelayProtocol.join(clientSocket, request, false);

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
                int frameSize = dis.readInt();
                if (frameSize < 29 || frameSize > BUFFER_SIZE + 28) throw new IOException("Invalid chunk size");
                byte[] frame = new byte[frameSize];
                dis.readFully(frame);
                byte[] plain = AESUtil.decryptBytes(frame);
                bytesRead = plain.length;
                if (bytesRead > toRead) throw new IOException("File exceeds announced size");
                System.arraycopy(plain, 0, buffer, 0, bytesRead);

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

                if (!HashUtil.hashFile(partFile).equals(request.getFileChecksum())) {
                    Files.delete(partFile.toPath());
                    throw new IOException("File checksum mismatch; retry from the beginning");
                }
                Files.move(partFile.toPath(), targetFile.toPath());
                dos.writeBoolean(true);
                dos.flush();
                if (listener != null) {
                    listener.onProgress(totalBytes, totalBytes, 0, 100);
                    listener.onCompleted(targetFile.getAbsolutePath());
                }
            } else {
                if (listener != null) {
                    listener.onError("Kết nối truyền tải bị ngắt quãng giữa chừng (" + bytesReceived + "/" + totalBytes + " bytes). Có thể truyền tiếp sau.");
                }
            }
        } catch (Exception e) {
            if (!isCancelled && listener != null) {
                listener.onError("Lỗi nhận tệp " + (request.isRelayed() ? "qua Server: " : "P2P: ") + e.getMessage());
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

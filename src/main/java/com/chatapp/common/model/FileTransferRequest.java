/**
 * FileTransferRequest.java — Chứa thông tin thương lượng truyền tệp, checksum, vị trí truyền tiếp và cấu hình relay.
 */
package com.chatapp.common.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Thông tin yêu cầu truyền tệp tin (hỗ trợ P2P và Resume transfer).
 */
public class FileTransferRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED,
        TRANSFERRING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String transferId;
    private String sender;
    private String receiver;
    private String fileName;
    private long fileSize;
    private String fileChecksum; // SHA-256 hoặc MD5 để kiểm tra tính toàn vẹn
    private String receiverIp;   // Địa chỉ IP của người nhận cho P2P
    private int receiverPort;    // Cổng P2P Socket của người nhận
    private long resumeOffset;   // Vị trí byte bắt đầu truyền tiếp (0 nếu truyền mới)
    private Status status;
    private String roomId;
    private boolean relayed;
    private String relayToken;

    /** Cho biết cờ truyền tệp qua Server relay. */
    public boolean isRelayed() { return relayed; }
    /** Cập nhật cờ truyền tệp qua Server relay. */
    public void setRelayed(boolean relayed) { this.relayed = relayed; }
    /** Lấy token xác thực phiên relay. */
    public String getRelayToken() { return relayToken; }
    /** Cập nhật token xác thực phiên relay. */
    public void setRelayToken(String relayToken) { this.relayToken = relayToken; }

    /** Lấy mã phòng chat. */
    public String getRoomId() { return roomId; }
    /** Cập nhật mã phòng chat. */
    public void setRoomId(String roomId) { this.roomId = roomId; }

    /** Khởi tạo đối tượng FileTransferRequest với dữ liệu và tài nguyên ban đầu. */
    public FileTransferRequest() {
        this.transferId = UUID.randomUUID().toString();
        this.status = Status.PENDING;
        this.resumeOffset = 0;
    }

    /** Khởi tạo đối tượng FileTransferRequest với dữ liệu và tài nguyên ban đầu. */
    public FileTransferRequest(String sender, String receiver, String fileName, long fileSize, String fileChecksum) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileChecksum = fileChecksum;
    }

    /** Lấy dung lượng tệp đã định dạng để hiển thị. */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // Getters and Setters
    /** Lấy mã phiên truyền tệp. */
    public String getTransferId() {
        return transferId;
    }

    /** Cập nhật mã phiên truyền tệp. */
    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    /** Lấy tên tài khoản người gửi. */
    public String getSender() {
        return sender;
    }

    /** Cập nhật tên tài khoản người gửi. */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /** Lấy tài khoản nhận tệp. */
    public String getReceiver() {
        return receiver;
    }

    /** Cập nhật tài khoản nhận tệp. */
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    /** Lấy tên tệp. */
    public String getFileName() {
        return fileName;
    }

    /** Cập nhật tên tệp. */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** Lấy kích thước tệp tính bằng byte. */
    public long getFileSize() {
        return fileSize;
    }

    /** Cập nhật kích thước tệp tính bằng byte. */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /** Lấy giá trị băm kiểm tra toàn vẹn tệp. */
    public String getFileChecksum() {
        return fileChecksum;
    }

    /** Cập nhật giá trị băm kiểm tra toàn vẹn tệp. */
    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    /** Lấy địa chỉ IP kết nối truyền tệp. */
    public String getReceiverIp() {
        return receiverIp;
    }

    /** Cập nhật địa chỉ IP kết nối truyền tệp. */
    public void setReceiverIp(String receiverIp) {
        this.receiverIp = receiverIp;
    }

    /** Lấy cổng kết nối truyền tệp. */
    public int getReceiverPort() {
        return receiverPort;
    }

    /** Cập nhật cổng kết nối truyền tệp. */
    public void setReceiverPort(int receiverPort) {
        this.receiverPort = receiverPort;
    }

    /** Lấy vị trí byte bắt đầu truyền tiếp. */
    public long getResumeOffset() {
        return resumeOffset;
    }

    /** Cập nhật vị trí byte bắt đầu truyền tiếp. */
    public void setResumeOffset(long resumeOffset) {
        this.resumeOffset = resumeOffset;
    }

    /** Lấy trạng thái xử lý. */
    public Status getStatus() {
        return status;
    }

    /** Cập nhật trạng thái xử lý. */
    public void setStatus(Status status) {
        this.status = status;
    }
}

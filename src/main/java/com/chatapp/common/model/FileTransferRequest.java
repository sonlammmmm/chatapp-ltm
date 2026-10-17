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

    public FileTransferRequest() {
        this.transferId = UUID.randomUUID().toString();
        this.status = Status.PENDING;
        this.resumeOffset = 0;
    }

    public FileTransferRequest(String sender, String receiver, String fileName, long fileSize, String fileChecksum) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileChecksum = fileChecksum;
    }

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
    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    public String getReceiverIp() {
        return receiverIp;
    }

    public void setReceiverIp(String receiverIp) {
        this.receiverIp = receiverIp;
    }

    public int getReceiverPort() {
        return receiverPort;
    }

    public void setReceiverPort(int receiverPort) {
        this.receiverPort = receiverPort;
    }

    public long getResumeOffset() {
        return resumeOffset;
    }

    public void setResumeOffset(long resumeOffset) {
        this.resumeOffset = resumeOffset;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}

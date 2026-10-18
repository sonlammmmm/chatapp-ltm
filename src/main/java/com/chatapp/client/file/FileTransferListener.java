/**
 * FileTransferListener.java — Định nghĩa thông báo tiến độ, hoàn tất và lỗi khi truyền tệp.
 */
package com.chatapp.client.file;

/**
 * Interface lắng nghe sự kiện tiến độ truyền tải tệp tin P2P.
 */
public interface FileTransferListener {
    /**
     * Cập nhật tiến độ truyền file
     * @param bytesTransferred Số byte đã truyền/nhận được
     * @param totalBytes Tổng dung lượng file
     * @param speedKBps Tốc độ truyền (KB/s)
     * @param percent Phần trăm hoàn thành (0 - 100)
     */
    void onProgress(long bytesTransferred, long totalBytes, double speedKBps, int percent);

    /**
     * Hoàn thành truyền tải thành công
     * @param filePath Đường dẫn file đã lưu
     */
    void onCompleted(String filePath);

    /**
     * Bị lỗi hoặc gián đoạn giữa chừng (có thể Resume sau)
     * @param errorMessage Thông điệp lỗi
     */
    void onError(String errorMessage);
}

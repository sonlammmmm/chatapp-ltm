/**
 * ClientListener.java — Định nghĩa các sự kiện mạng mà giao diện Client có thể tiếp nhận.
 */
package com.chatapp.client;

import com.chatapp.common.model.ChatMessage;
import com.chatapp.common.model.ChatRoom;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.model.User;

import java.util.List;

/**
 * Interface lắng nghe các sự kiện mạng TCP từ Server gửi về Client.
 */
public interface ClientListener {
    /** Tiếp nhận sự kiện đăng nhập thành công. */
    void onLoginSuccess(User user);
    /** Tiếp nhận lý do đăng nhập thất bại. */
    void onLoginFail(String reason);
    /** Tiếp nhận kết quả đăng ký tài khoản. */
    void onRegisterResult(boolean success, String message);

    /** Tiếp nhận danh sách người dùng mới từ Server. */
    void onUserListUpdated(List<User> users);
    /** Tiếp nhận danh sách phòng chat mới từ Server. */
    void onRoomListUpdated(List<ChatRoom> rooms);

    /** Tiếp nhận lịch sử tin nhắn của hội thoại; mặc định không xử lý thêm trong lớp này. */
    default void onHistoryReceived(String target, List<ChatMessage> messages) {}
    /** Tiếp nhận thông báo lỗi từ Server; mặc định không xử lý thêm trong lớp này. */
    default void onServerError(String message) {}

    /** Tiếp nhận tin nhắn mới. */
    void onMessageReceived(ChatMessage message);
    /** Tiếp nhận thay đổi trạng thái đã nhận hoặc đã đọc tin nhắn. */
    void onMessageStatusUpdated(String messageId, ChatMessage.Status status);
    /** Tiếp nhận trạng thái đang nhập của người dùng. */
    void onUserTyping(String sender, String target, boolean typing);

    /** Tiếp nhận đề nghị truyền tệp. */
    void onFileTransferRequested(FileTransferRequest request);
    /** Tiếp nhận phản hồi đồng ý và chuẩn bị gửi tệp. */
    void onFileTransferAccepted(FileTransferRequest request);
    /** Tiếp nhận phản hồi từ chối truyền tệp. */
    void onFileTransferRejected(FileTransferRequest request);

    /** Tiếp nhận kết quả tạo phòng chat; mặc định không xử lý thêm trong lớp này. */
    default void onRoomCreated(ChatRoom room, String error) {}

    /** Tiếp nhận sự kiện mất kết nối tới Server. */
    void onConnectionLost();
}

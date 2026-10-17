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
    void onLoginSuccess(User user);
    void onLoginFail(String reason);
    void onRegisterResult(boolean success, String message);

    void onUserListUpdated(List<User> users);
    void onRoomListUpdated(List<ChatRoom> rooms);

    void onMessageReceived(ChatMessage message);
    void onMessageStatusUpdated(String messageId, ChatMessage.Status status);
    void onUserTyping(String sender, String target);

    void onFileTransferRequested(FileTransferRequest request);
    void onFileTransferAccepted(FileTransferRequest request);
    void onFileTransferRejected(FileTransferRequest request);

    void onConnectionLost();
}

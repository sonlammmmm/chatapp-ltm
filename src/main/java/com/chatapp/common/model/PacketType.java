package com.chatapp.common.model;

/**
 * Định nghĩa các loại gói tin trong hệ thống Chat Socket TCP.
 */
public enum PacketType {
    // Xác thực & Tài khoản
    AUTH_LOGIN,
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAIL,
    AUTH_REGISTER,
    AUTH_REGISTER_SUCCESS,
    AUTH_REGISTER_FAIL,
    AUTH_LOGOUT,

    // Cập nhật danh sách
    USER_LIST_UPDATE,
    ROOM_LIST_UPDATE,

    // Tin nhắn văn bản
    HISTORY_REQUEST,
    HISTORY_RESPONSE,
    MSG_DIRECT,         // Chat 1-1
    MSG_GROUP,          // Chat nhóm
    MSG_DELIVERED,      // Thông báo trạng thái đã nhận tin
    MSG_READ,           // Thông báo trạng thái đã xem tin

    // Trạng thái người dùng
    USER_TYPING,        // Thông báo "Đang soạn thảo..."

    // Quản lý phòng chat (Group Chat)
    ROOM_CREATE,
    ROOM_CREATE_SUCCESS,
    ROOM_CREATE_FAIL,
    ROOM_JOIN,
    ROOM_LEAVE,

    // Quản lý truyền file (P2P File Transfer Signaling)
    FILE_REQUEST,       // Client A muốn gửi file cho Client B
    FILE_ACCEPT,        // Client B chấp nhận nhận file
    FILE_REJECT,        // Client B từ chối nhận file
    FILE_P2P_INFO,      // Client B gửi IP & Port ServerSocket cho Server
    FILE_P2P_READY,     // Server chuyển tiếp IP & Port cho Client A để kết nối trực tiếp
    FILE_STATUS_UPDATE, // Cập nhật trạng thái truyền file

    // Lỗi hệ thống
    SYSTEM_ERROR
}

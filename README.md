# ỨNG DỤNG CHAT CLIENT-SERVER TÍCH HỢP TRUYỀN TỆP TIN P2P 1-1 VÀ CHAT NHÓM
> **Đồ án môn học: Lập trình mạng (Network Programming)**  
> **Nhóm thực hiện: Nhóm 03** (Trần Gia Huy - Trần Sỷ Sơn Lâm - Lưu Phong Dư - Đặng Ngọc Huy)  
> **Môi trường phát triển:** NetBeans IDE / Java JDK 11+ / Maven  

---

## 🚀 Tính năng nổi bật
1. **Mô hình Client-Server & TCP Socket Đa Luồng**:
   - Máy chủ quản lý các kết nối đồng thời với `ExecutorService` và `ServerSocket`.
   - Giao thức định dạng JSON qua lớp `Packet`.
2. **Chat Nhóm & Chat 1-1 (Group Chat & Direct Message)**:
   - Chat 1-1 với bạn bè trực tuyến.
   - Tạo phòng chat, tham gia và trò chuyện nhóm bằng cơ chế định tuyến Multicast / Broadcast.
3. **Truyền tệp tin ngang hàng P2P (Peer-to-Peer File Transfer)**:
   - Server chỉ đóng vai trò báo hiệu (Signaling: IP, Port).
   - 2 Client tự mở kết nối TCP Socket trực tiếp với nhau trong mạng LAN, giải phóng hoàn toàn băng thông cho Server.
   - Luồng truyền file chạy trên Socket và Thread riêng biệt, không làm nghẽn luồng nhắn tin văn bản.
4. **Truyền tải file lớn chống tràn RAM & Resume Transfer**:
   - Chia nhỏ dữ liệu thành các khối 64KB kết hợp `BufferedOutputStream`, cho phép truyền file hàng trăm MB đến nhiều GB mà không sợ tràn bộ nhớ JVM.
   - Hỗ trợ truyền tiếp tại điểm đứt gãy (**Resume Transfer**) dựa trên cơ chế Byte Offset và file tạm `.part`.
5. **Bảo mật và Xác thực**:
   - Mật khẩu tài khoản được băm **SHA-256** kết hợp chuỗi ngẫu nhiên **Salt** lưu trong SQLite (`chatapp.db`).
   - Mã hóa đối xứng **AES-128** cho toàn bộ nội dung tin nhắn và tệp tin qua Socket.
6. **Trạng thái người dùng thời gian thực**:
   - Trạng thái đang gõ phím (`Typing...`).
   - Trạng thái tin nhắn: `✓ Đã gửi` -> `✓✓ Đã nhận` -> `✓✓ Đã xem`.
7. **Giao diện người dùng Java Swing hiện đại**:
   - Tích hợp giao diện **FlatLaf** Dark Theme sang trọng.
   - Bong bóng tin nhắn Chat Bubble bo góc mượt mà.
   - Thanh tiến trình truyền file (Progress Bar), hiển thị tốc độ (KB/s, MB/s) và phần trăm hoàn thành.

---

## 📂 Cấu trúc thư mục dự án
```
ChatApp_LTM_NetBeans/
├── pom.xml                                   # Cấu hình Maven (NetBeans nhận diện tự động)
├── README.md                                 # Giới thiệu dự án
├── HUONG_DAN_CAI_DAT_NETBEANS.md             # Hướng dẫn chi tiết mở và chạy trong NetBeans
├── BAO_CAO_DO_AN_LTM.md                      # Báo cáo đồ án đầy đủ nộp cho Giảng viên
├── run_server.sh / run_server.bat            # Script khởi chạy Server nhanh
├── run_client.sh / run_client.bat            # Script khởi chạy Client nhanh
└── src/
    └── main/
        └── java/
            └── com/
                └── chatapp/
                    ├── common/               # Dùng chung Client & Server
                    │   ├── model/            # User, ChatMessage, ChatRoom, FileTransferRequest, PacketType
                    │   ├── protocol/         # Packet (JSON Serialization)
                    │   └── security/         # AESUtil (Mã hóa AES), HashUtil (Băm SHA-256 + Salt)
                    ├── server/               # Mã nguồn Máy chủ TCP
                    │   ├── ServerMain.java   # Điểm chạy Server trong NetBeans
                    │   ├── ChatServer.java   # TCP Server core
                    │   ├── ClientHandler.java# Xử lý kết nối từng Client
                    │   ├── RoomManager.java  # Quản lý phòng chat nhóm
                    │   ├── db/               # DatabaseManager (SQLite)
                    │   └── ui/               # ServerWindow (GUI Quản trị)
                    └── client/               # Mã nguồn Máy khách TCP
                        ├── ClientMain.java   # Điểm chạy Client trong NetBeans
                        ├── ChatClient.java   # TCP Client core & AES
                        ├── ClientListener.java # Interface lắng nghe sự kiện mạng
                        ├── file/             # P2PFileSender, P2PFileReceiver, FileTransferListener
                        └── ui/               # Giao diện chính (MainChatWindow, Login, Dialogs, Bubbles)
```

---

## ⚡ Hướng dẫn chạy nhanh trên NetBeans
1. Mở NetBeans -> **File** -> **Open Project** -> Chọn thư mục `ChatApp_LTM_NetBeans`.
2. Chạy Server: Chuột phải vào [ServerMain.java](file:///Users/user/Downloads/ChatApp_LTM_NetBeans/src/main/java/com/chatapp/server/ServerMain.java) -> **Run File** (`Shift + F6`) -> Nhấn **"Khởi động Server"**.
3. Chạy Client: Chuột phải vào [ClientMain.java](file:///Users/user/Downloads/ChatApp_LTM_NetBeans/src/main/java/com/chatapp/client/ClientMain.java) -> **Run File** (`Shift + F6`).
4. Bấm các nút đăng nhập nhanh của 4 thành viên nhóm:
   - **Gia Huy** (user: `giahuy`, pass: `123456`)
   - **Sơn Lâm** (user: `sonlam`, pass: `123456`)
   - **Phong Dư** (user: `phongdu`, pass: `123456`)
   - **Ngọc Huy** (user: `ngochuy`, pass: `123456`)
5. Xem chi tiết các kịch bản test tại file [HUONG_DAN_CAI_DAT_NETBEANS.md](file:///Users/user/Downloads/ChatApp_LTM_NetBeans/HUONG_DAN_CAI_DAT_NETBEANS.md).

# HƯỚNG DẪN MỞ VÀ CHẠY ĐỒ ÁN TRÊN NETBEANS IDE

## 1. Yêu cầu môi trường
- **IDE**: Apache NetBeans (phiên bản bất kỳ: NetBeans 8, 12, 17, 20, 21, 31,...).
- **JDK**: Java JDK 11 trở lên (Khuyến nghị JDK 17, JDK 21 hoặc JDK 22).
- **Maven**: Đã được tích hợp sẵn trong Apache NetBeans.

---

## 2. Các bước mở đồ án trong NetBeans
1. Mở phần mềm **Apache NetBeans**.
2. Trên thanh menu trên cùng, chọn: **File** -> **Open Project...** (hoặc phím tắt `Ctrl + Shift + O` / `Cmd + Shift + O`).
3. Điều hướng đến thư mục dự án:
   ```
   ChatApp_LTM_NetBeans
   ```
4. NetBeans sẽ tự động nhận diện biểu tượng thư mục có chữ **"m"** (Maven Project). Nhấn **Open Project**.
5. Đợi khoảng 5 - 10 giây để NetBeans tự động đồng bộ thư viện Maven (`pom.xml` gồm: `gson`, `sqlite-jdbc`, `flatlaf`).

---

## 3. Hướng dẫn chạy chương trình (Thực thi)

### Bước 1: Khởi động Server (Máy chủ TCP)
1. Trong cửa sổ **Projects** bên trái của NetBeans, mở cây thư mục:
   `Source Packages` -> `com.chatapp.server` -> [ServerMain.java](file:///Users/user/Downloads/ChatApp_LTM_NetBeans/src/main/java/com/chatapp/server/ServerMain.java).
2. **Nhấp chuột phải** vào file `ServerMain.java` -> Chọn **Run File** (hoặc nhấn phím tắt `Shift + F6`).
3. Cửa sổ **Máy chủ TCP Socket** sẽ xuất hiện:
   - Cổng mặc định là `8888`.
   - Nhấn nút **"Khởi động Server"**.
   - Trạng thái sẽ chuyển sang màu xanh lá: `● Trạng thái: ĐANG CHẠY (Port: 8888)`.

---

### Bước 2: Khởi động Client (Máy khách)
1. Mở cây thư mục:
   `Source Packages` -> `com.chatapp.client` -> [ClientMain.java](file:///Users/user/Downloads/ChatApp_LTM_NetBeans/src/main/java/com/chatapp/client/ClientMain.java).
2. **Nhấp chuột phải** vào file `ClientMain.java` -> Chọn **Run File** (hoặc nhấn `Shift + F6`).
3. Cửa sổ Đăng nhập sẽ hiện lên. Để kiểm thử đồ án nhanh chóng, hệ thống đã tích hợp sẵn 4 nút đăng nhập nhanh cho 4 thành viên nhóm:
   - Nút **"1. Gia Huy"** (user: `giahuy` / pass: `123456`)
   - Nút **"2. Sơn Lâm"** (user: `sonlam` / pass: `123456`)
   - Nút **"3. Phong Dư"** (user: `phongdu` / pass: `123456`)
   - Nút **"4. Ngọc Huy"** (user: `ngochuy` / pass: `123456`)
4. **Mở thêm Client thứ 2, thứ 3...**:
   - Tiếp tục nhấp chuột phải vào `ClientMain.java` -> Chọn **Run File** lần nữa.
   - Đăng nhập tài khoản thứ hai (Ví dụ: `sonlam`).
   - Lúc này bạn đã có 2 cửa sổ chat để test tin nhắn và truyền file qua lại!

---

## 4. Kịch bản kiểm thử các tính năng nâng cao (Để báo cáo với Giảng viên)

### A. Kiểm thử Chat 1-1 và Chat Nhóm (Group Chat)
1. **Chat 1-1**:
   - Ở máy Client 1 (Gia Huy), nhìn vào mục **"BẠN BÈ TRỰC TUYẾN"** bên trái, nhấp chọn **"Trần Sỷ Sơn Lâm"**.
   - Gõ tin nhắn và gửi. Bên máy Sơn Lâm sẽ lập tức nhận được tin nhắn trong bong bóng chat.
   - Khi máy nhận xem tin, trạng thái tin nhắn bên người gửi sẽ chuyển từ `✓ Đã gửi` -> `✓✓ Đã nhận` -> `✓✓ Đã xem`.
2. **Chat Nhóm (Room Chat / Multicast)**:
   - Nhấp vào **"Sảnh Chung"** hoặc phòng **"Nhóm 03 - Đồ án LTM"** ở mục **PHÒNG CHAT NHÓM**.
   - Mọi tin nhắn gửi trong phòng sẽ được máy chủ Server Broadcast đến tất cả thành viên có mặt trong phòng.
   - Bạn có thể nhấn nút **"+ Tạo"** để tạo thêm phòng thảo luận mới.

---

### B. Kiểm thử Trạng thái người dùng ("Typing...")
1. Khi một người bắt đầu gõ phím vào ô nhập liệu, người đang chat ở đầu bên kia sẽ thấy dòng thông báo hiển thị thời gian thực:
   ```
   ✎ [Tên người dùng] đang nhập tin...
   ```
2. Dòng chữ sẽ tự động biến mất khi người dùng ngừng gõ sau 2.5 giây.

---

### C. Kiểm thử Bảo mật: Băm SHA-256 và Mã hóa đối xứng AES
1. **Mã hóa AES tin nhắn**:
   - Mọi nội dung tin nhắn và gói tin truyền trên đường truyền Socket TCP đều được mã hóa bằng thuật toán `AES-128`.
   - Nếu dùng công cụ bắt gói tin như Wireshark, gói tin chỉ là các chuỗi Base64 Ciphertext vô nghĩa, đảm bảo an toàn tuyệt đối.
2. **Băm mật khẩu SHA-256 + Salt**:
   - Mật khẩu lưu trữ trong SQLite (`database/chatapp.db`) không lưu dạng rõ (Plaintext) mà được băm bằng SHA-256 kết hợp chuỗi ngẫu nhiên Salt, chống tấn công bảng Rainbow Table.

---

### D. Kiểm thử Truyền tệp tin P2P 1-1 (Peer-to-Peer File Transfer)
1. Chọn một người bạn online để chat 1-1.
2. Nhấn nút **"📎 Gửi File P2P"** ở góc dưới bên trái.
3. Chọn một file bất kỳ (ảnh, video, file nén zip, file exe, pdf...).
4. Phía người nhận sẽ xuất hiện một hộp thoại xác nhận:
   - Hiển thị tên file và dung lượng tệp.
   - Nhấn **"Yes" (Chấp nhận)** và chọn thư mục lưu (mặc định là thư mục Downloads của máy).
5. **Cơ chế P2P hoạt động**:
   - Người nhận mở một cổng Socket trực tiếp (`ServerSocket`).
   - Server chỉ đóng vai trò chuyển tiếp IP và Port.
   - Người gửi kết nối trực tiếp đến IP và Port của người nhận qua một Socket TCP riêng biệt (không đi qua Server, không làm nghẽn luồng chat văn bản).
   - Thanh tiến trình **Progress Bar** hiển thị: Phần trăm (%), tốc độ truyền (MB/s hoặc KB/s), số byte đã truyền.

---

### E. Kiểm thử Tính năng Tiếp tục truyền khi bị ngắt kết nối (Resume Transfer)
1. Trong khi file đang truyền dở (ví dụ một file lớn khoảng 50MB - 100MB), hãy nhấn nút **"Hủy bỏ"** trên hộp thoại tiến trình.
2. Kiểm tra thư mục nhận: Bạn sẽ thấy file đang lưu tạm dưới đuôi `.part` (ví dụ: `video.mp4.part`), lưu giữ chính xác số byte đã tải về trước khi ngắt mạng.
3. Tiến hành gửi lại chính file đó:
   - Hệ thống tự động phát hiện file `.part` dở dang.
   - Client nhận gửi `resumeOffset` (vị trí byte đã tải được) cho Client gửi.
   - Client gửi dùng `RandomAccessFile.seek(offset)` để **nhảy thẳng đến vị trí bị đứt và truyền tiếp phần còn lại**, không phải truyền lại từ đầu!

---

## 5. Chạy qua mạng LAN (Nhiều máy tính trong cùng phòng hoặc trường)
- Trên máy chạy Server: Xem địa chỉ IP của máy (ví dụ `192.168.1.10`).
- Trên các máy Client khác: Khi mở form Đăng nhập, tại ô **"Địa chỉ IP Server"**, nhập `192.168.1.10`, cổng `8888`.
- Bấm Đăng nhập để kết nối mạng LAN thực tế!

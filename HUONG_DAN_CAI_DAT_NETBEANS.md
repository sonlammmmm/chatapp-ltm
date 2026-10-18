# HƯỚNG DẪN MỞ VÀ CHẠY ĐỒ ÁN TRÊN NETBEANS IDE

## 1. Yêu cầu môi trường
- **IDE**: Apache NetBeans có hỗ trợ dự án Maven.
- **JDK**: Source/target dự án là Java 11. Môi trường hiện đã kiểm tra với JDK 21.0.11; chưa xác nhận chạy thực tế trên JDK 11.
- **Maven**: NetBeans tích hợp Maven; môi trường dòng lệnh đã kiểm tra hiện tại là Maven 3.9.16.

### Stack trong dự án

Java Swing/AWT dùng `Socket`/`ServerSocket` TCP blocking I/O; server dùng `ExecutorService` cached thread pool và `ConcurrentHashMap`. Gson 2.10.1 chuyển Packet qua JSON rồi lớp ngoài TCP mã hóa AES-128-GCM/Base64 theo dòng newline; thân tin nhắn mới được mã hóa AES-256-GCM riêng bằng JCA/JCE. P2P dùng TCP riêng với khối 64 KiB, resume theo byte offset và SHA-256. Mật khẩu dùng SHA-256 với salt 16 byte, không dùng bcrypt. SQLite truy cập qua JDBC driver `sqlite-jdbc` 3.45.1.0 (không phải phiên bản SQLite engine). Compiler/JAR/Exec Maven plugins lần lượt là 3.11.0/3.3.0/3.1.0; JUnit Jupiter 5.10.2 chỉ dành cho test. NetBeans là IDE, không phải dependency runtime.

---

## 2. Các bước mở đồ án trong NetBeans
1. Mở phần mềm **Apache NetBeans**.
2. Trên thanh menu trên cùng, chọn: **File** -> **Open Project...** (hoặc phím tắt `Ctrl + Shift + O` / `Cmd + Shift + O`).
3. Điều hướng đến thư mục dự án:
   ```
   ChatApp_LTM_NetBeans
   ```
4. NetBeans sẽ tự động nhận diện biểu tượng thư mục có chữ **"m"** (Maven Project). Nhấn **Open Project**.
5. Đợi NetBeans đồng bộ dependencies trong `pom.xml`: Gson 2.10.1, SQLite JDBC driver 3.45.1.0 và FlatLaf 3.4.1.

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
3. Cửa sổ Đăng nhập sẽ hiện lên. Để kiểm thử đồ án nhanh chóng, hệ thống đã tích hợp sẵn 3 nút đăng nhập nhanh cho 3 thành viên nhóm:
   - Nút **"1. Gia Huy"** (user: `giahuy` / pass: `123456`)
   - Nút **"2. Sơn Lâm"** (user: `sonlam` / pass: `123456`)
   - Nút **"3. Ngọc Huy"** (user: `ngochuy` / pass: `123456`)
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

### C. Cấu hình khóa và kiểm thử bảo mật
Trước khi chạy server/client, tạo khóa Base64 32 byte một lần trong PowerShell:

```powershell
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$key = [Convert]::ToBase64String($bytes)
$rng.Dispose()
$env:CHATAPP_TEXT_KEY = $key
```

Cất khóa bên ngoài repository và dùng cùng giá trị cho server cùng mọi client. PowerShell env var chỉ được tiến trình con kế thừa: đặt khóa rồi khởi chạy NetBeans từ cùng cửa sổ PowerShell, ví dụ `$env:CHATAPP_TEXT_KEY = $key; & 'D:\NetBeans-22\netbeans\bin\netbeans64.exe'` (đổi đường dẫn nếu cần). Nếu IDE đang chạy từ Start Menu, đóng hẳn trước khi chạy lệnh; cửa sổ PowerShell mới cần đặt lại khóa. Linux/macOS: `export CHATAPP_TEXT_KEY='…'`. Java property `-Dchatapp.textKey=…` được ưu tiên hơn biến môi trường. Ứng dụng dừng ở bước khởi động với hướng dẫn nếu thiếu khóa hoặc Base64 không giải mã thành đúng 32 byte. Không commit khóa.

Mỗi tin nhắn mới được mã hóa riêng AES-256-GCM trước khi gửi; server xác thực trước khi lưu/chuyển tiếp và SQLite giữ bản mã. Client giải mã khi nhận và đọc lịch sử. Tin nhắn lịch sử plaintext cũ vẫn đọc được, không tự chuyển đổi. Đây không phải mã hóa đầu cuối: server và mọi client cùng dùng một khóa, nên người có khóa đọc được mọi tin nhắn mới. Mất khóa khiến tin nhắn mới trong lịch sử không thể đọc được. Lớp bọc gói TCP và P2P file vẫn dùng cơ chế AES demo hiện có; nội dung file không thay đổi.

1. **Băm mật khẩu SHA-256 + Salt**: mật khẩu không lưu dạng rõ; salt ngẫu nhiên 16 byte được lưu cùng hash trong SQLite. Cơ chế này không phải bcrypt.

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

# Gửi tệp giữa hai máy khác mạng

Ứng dụng có hai chế độ truyền tệp. Chế độ được chọn ở **Server**, áp dụng cho mọi yêu cầu gửi tệp mới. Cả hai máy phải dùng cùng phiên bản Client mới và cùng khóa `CHATAPP_TEXT_KEY` đang dùng. Không đổi khóa nếu muốn đọc lịch sử đã mã hóa.

## 1. Laptop chạy Server, hai máy dùng Radmin VPN

Trong `.env` ở thư mục gốc dự án **trên máy Server**, thêm hoặc sửa dòng (không khai báo trùng):

```dotenv
CHATAPP_FILE_MODE=p2p
CHATAPP_SERVER_PORT=8888
```

1. Máy A dùng Wi-Fi trường, máy B dùng hotspot 4G. Cài Radmin VPN trên cả hai máy, tham gia cùng mạng riêng và kiểm tra hai máy online.
2. Máy A chạy Server trước. Khởi động lại Server nếu vừa đổi `.env`.
3. Trên cả hai Client, nhập **IP Radmin VPN của máy A** vào ô Server và cổng `8888`. Đăng nhập hai tài khoản khác nhau. Có thể đặt `CHATAPP_SERVER_HOST` trong `.env` trên từng máy để điền sẵn IP này.
4. Trên cả hai máy, cho phép đúng `java.exe`/`javaw.exe` đang chạy dự án nhận kết nối trong Windows Firewall. Trong Advanced Security → Inbound Rules → New Rule → Program, chọn đúng file Java, Allow connection, chọn profile ứng với kết nối VPN. Giới hạn Scope → Remote IP theo IP Radmin của máy còn lại. Không tắt Firewall.
5. Gửi tin nhắn rồi thử tệp A → B và B → A. Chế độ P2P mở cổng nhận TCP động trên từng Client; chỉ mở `8888` không đủ. Quy tắc theo chương trình Java cho phép cổng động mà vẫn giới hạn máy được truy cập.

Server lấy IP người nhận từ kết nối chat thực tế, không dùng IP do Client tự khai báo. Nếu Client ngay trên laptop Server đăng nhập bằng localhost, Client gửi ở máy khác sẽ dùng địa chỉ Server mà nó đang kết nối để tới máy nhận. Nên dùng IP VPN cho cả hai Client để dễ giải thích trong buổi demo.

Đây là truyền tệp trực tiếp qua mạng riêng ảo. Hai nguồn Internet khác nhau, nhưng có VPN; cần phù hợp yêu cầu giảng viên.

## 2. Server chuyển tiếp tệp, không cần Client nhận kết nối vào

Trên máy chạy Server, cấu hình:

```dotenv
CHATAPP_FILE_MODE=relay
CHATAPP_SERVER_PORT=8888
CHATAPP_FILE_RELAY_PORT=8889
```

Hai cổng phải khác nhau. Khởi động lại Server sau khi đổi chế độ. Client tự nhận chế độ và cổng truyền tệp qua báo hiệu; không cần chỉnh chế độ ở từng Client.

```text
Client A -- TCP ra --> Server <-- TCP ra -- Client B
                         |
                Chat: 8888; Tệp: 8889
```

- Client nhập cùng địa chỉ Server truy cập được và cổng chat `8888`. Cổng truyền tệp `8889` phải truy cập được **ở cùng hostname/IP với cổng chat** và được ánh xạ giữ nguyên số cổng.
- Windows Firewall/firewall VPS phải cho phép hai cổng TCP trên Server. Client chỉ cần quyền kết nối ra Server; không cần mở cổng nhận tệp trên Client.
- Nếu Server chạy trên laptop sau router, cần IP public nhận kết nối được và chuyển tiếp cả hai cổng tới laptop. Wi-Fi trường hoặc mạng có CGNAT thường không cho tự thực hiện việc này.
- Nếu dùng VPS, mở cả hai cổng trong firewall nhà cung cấp và firewall hệ điều hành.
- Hosting chỉ cung cấp URL HTTPS sẽ chưa chạy được cơ chế TCP này. Việc bật `relay` không tự mở cổng của Mắt Bão, không tự tạo IP public và không vượt CGNAT cho máy Server.

Có thể thử relay qua Radmin trước: vẫn nhập IP Radmin máy Server ở cả hai Client, nhưng chọn `relay`. Khi đó chỉ cần Server nhận kết nối chat và relay. Sau khi kiểm tra ổn mới chuyển sang địa chỉ public.

Server chuyển tiếp luồng tệp đã mã hóa, không lưu tệp lên ổ đĩa; hai Client vẫn phân khối, resume qua `.part`, kiểm tra SHA-256 và chờ xác nhận nhận thành công. Kết nối truyền tệp tách khỏi socket chat. Mỗi yêu cầu có token ngẫu nhiên do Server tạo, chỉ một kết nối cho mỗi vai trò gửi/nhận. Phiên chờ hết hạn sau khoảng 10–11 phút; tối đa 128 phiên đăng ký và 32 kết nối relay đồng thời. Chọn lại tệp để gửi yêu cầu mới khi phiên đã hết hạn hoặc thất bại.

Khi một tài khoản mất kết nối chat, Server đóng phiên relay của tài khoản đó; tệp dở được giữ trên máy nhận để thử resume lần gửi tiếp. Tệp hiện có không bị ghi đè.

## 3. Chạy Server không giao diện

Trên laptop vẫn chạy `ServerMain` trong NetBeans như trước. Với máy không có màn hình:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.chatapp.server.ServerMain" "-Dexec.args=--headless"
```

Chạy tại thư mục có `.env`; Server ghi log chế độ và cổng truyền tệp khi khởi động. Dừng bằng Ctrl+C. Không dùng chế độ này để thay đổi cấu hình TCP của hosting.

## 4. Kiểm tra trước khi demo

Trên máy Client, thay `SERVER_IP` bằng IP hoặc hostname thật, không nhập chữ giữ chỗ:

```powershell
Test-NetConnection SERVER_IP -Port 8888
# Chỉ cần kiểm tra dòng này khi Server chọn relay:
Test-NetConnection SERVER_IP -Port 8889
```

`TcpTestSucceeded : True` xác nhận đường tới cổng Server; chưa chứng minh toàn bộ truyền tệp thành công. Tiếp theo thử:

1. Chat cả hai chiều, tiếp tục chat trong lúc gửi tệp.
2. Tệp nhỏ, tệp vài MB, tệp rỗng và gửi ngược chiều.
3. Từ chối và hủy; gửi lại cùng tệp vào cùng thư mục để kiểm tra resume.
4. So sánh `Get-FileHash` trên tệp nguồn và tệp nhận, dùng SHA256.

Nếu P2P chat được nhưng tệp lỗi, kiểm tra Firewall máy nhận và việc hai Client dùng đường VPN. Nếu relay chat được nhưng tệp lỗi, kiểm tra cổng `8889` của Server. Có thể tăng `CHATAPP_P2P_TIMEOUT_MS=120000` trên cả Server và hai Client nếu mạng chậm; giới hạn cấu hình là 300000 ms.

Kiểm thử tự động chạy qua socket loopback trên máy phát triển, không thay thế kiểm thử hai máy thật trên Wi-Fi/4G. Mã hóa socket/tệp hiện vẫn dùng khóa demo dùng chung của dự án, không phải TLS xác thực máy chủ; chỉ mở Server cho phạm vi demo có kiểm soát, không coi đây là bản đã gia cố để phục vụ Internet công cộng.

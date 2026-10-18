# ỨNG DỤNG CHAT CLIENT-SERVER TÍCH HỢP TRUYỀN TỆP TIN P2P 1-1 VÀ CHAT NHÓM
> **Đồ án môn học: Lập trình mạng (Network Programming)**  
> **Nhóm thực hiện: Nhóm 03 — 3 thành viên** (Trần Gia Huy - Trần Sỷ Sơn Lâm - Đặng Ngọc Huy)  
> **Môi trường phát triển:** NetBeans IDE / Java JDK 11+ / Maven  

---

## Nội dung

- [Tính năng](#-tính-năng-nổi-bật)
- [Cấu trúc dự án](#-cấu-trúc-thư-mục-dự-án)
- [Chạy trên NetBeans](#-hướng-dẫn-chạy-nhanh-trên-netbeans)
- [Cài đặt và chạy bằng Maven](#cài-đặt-và-chạy-bằng-maven)
- [Kết nối trong mạng LAN](#kết-nối-trong-mạng-lan)
- [Sử dụng và kiểm tra nhanh](#sử-dụng-và-kiểm-tra-nhanh)
- [Dữ liệu và xử lý lỗi](#dữ-liệu-và-xử-lý-lỗi)
- [Đóng góp thành viên](#bảng-đóng-góp-thành-viên)
- [Quy trình Git/GitHub](#quy-trình-làm-việc-gitgithub)
- [Kiểm tra và tương thích](#kiểm-tra-và-tương-thích-giao-thức)

## 🚀 Tính năng nổi bật
1. **Mô hình Client-Server & TCP Socket Đa Luồng**:
   - Máy chủ quản lý các kết nối đồng thời với `ExecutorService` và `ServerSocket`.
   - Giao thức định dạng JSON qua lớp `Packet`.
2. **Chat Nhóm & Chat 1-1 (Group Chat & Direct Message)**:
   - Chat 1-1 với bạn bè trực tuyến.
   - Tạo phòng chat, tham gia và trò chuyện nhóm bằng cơ chế định tuyến phát tin tới thành viên phòng qua từng kết nối TCP.
3. **Truyền tệp tin ngang hàng P2P (Peer-to-Peer File Transfer)**:
   - Server chỉ đóng vai trò báo hiệu (Signaling: IP, Port).
   - 2 Client tự mở kết nối TCP Socket trực tiếp với nhau trong mạng LAN, giải phóng hoàn toàn băng thông cho Server.
   - Luồng truyền file chạy trên Socket và Thread riêng biệt, không làm nghẽn luồng nhắn tin văn bản.
   - File xuất hiện dưới dạng thẻ trong khung chat: tên, dung lượng, tiến độ, nhận/từ chối, hủy và mở file sau khi hoàn tất kiểm tra SHA-256.
   - Gửi file trong nhóm tạo một yêu cầu P2P riêng cho từng thành viên đang online tại lúc gửi. Mỗi người tự nhận/từ chối; máy gửi thấy trạng thái riêng theo người nhận. Người offline không được xếp hàng tải sau.
   - Thẻ file và đường dẫn mở file chỉ giữ trong phiên Client hiện tại; chưa lưu file đính kèm vào lịch sử SQLite. Máy gửi cần giữ ứng dụng và file nguồn trong suốt quá trình truyền; băng thông gửi tăng theo số người nhận.
4. **Truyền tải file lớn chống tràn RAM & Resume Transfer**:
   - Chia nhỏ dữ liệu thành các khối 64KB kết hợp `BufferedOutputStream`, cho phép truyền file hàng trăm MB đến nhiều GB mà không sợ tràn bộ nhớ JVM.
   - Hỗ trợ truyền tiếp tại điểm đứt gãy (**Resume Transfer**) dựa trên Byte Offset và file tạm `<tên>.<SHA-256>.part`; kiểm tra SHA-256 trước khi hoàn tất. File đích trùng tên được lưu với hậu tố, không ghi đè file cũ.
5. **Bảo mật và Xác thực**:
   - Mật khẩu tài khoản được băm **SHA-256** kết hợp chuỗi ngẫu nhiên **Salt** lưu trong SQLite (`chatapp.db`).
   - Mã hóa đối xứng **AES-128-GCM** cho toàn bộ gói tin Client–Server (bao gồm đăng nhập) và từng khối file P2P. Bản demo dùng khóa chung; chưa xác thực danh tính máy chủ bằng chứng chỉ và không phải mã hóa đầu cuối theo từng người dùng.
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
├── README.md                                 # Giới thiệu và hướng dẫn sử dụng
├── database/chatapp.db                       # SQLite tự tạo khi chạy Server
├── slides/                                   # Tài liệu trình chiếu
├── src/test/java/                            # Kiểm thử JUnit
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
2. Chạy Server: Chuột phải vào [ServerMain.java](src/main/java/com/chatapp/server/ServerMain.java) -> **Run File** (`Shift + F6`) -> Nhấn **"Khởi động Server"**.
3. Chạy Client: Chuột phải vào [ClientMain.java](src/main/java/com/chatapp/client/ClientMain.java) -> **Run File** (`Shift + F6`).
4. Bấm các nút đăng nhập nhanh của 3 thành viên nhóm:
   - **Gia Huy** (user: `giahuy`, pass: `123456`)
   - **Sơn Lâm** (user: `sonlam`, pass: `123456`)
   - **Ngọc Huy** (user: `ngochuy`, pass: `123456`)
5. Xem chi tiết các kịch bản test tại file [HUONG_DAN_CAI_DAT_NETBEANS.md](HUONG_DAN_CAI_DAT_NETBEANS.md).


---

## Cài đặt và chạy bằng Maven

### Yêu cầu

- **JDK 11 trở lên**, có lệnh `java` và `javac` trong `PATH`; `JAVA_HOME` trỏ tới JDK.
- **Maven 3.6.3 trở lên**, có lệnh `mvn` trong `PATH`.
- Môi trường desktop hỗ trợ Java Swing; NetBeans chỉ cần khi chạy bằng IDE.
- Internet ở lần build đầu để Maven tải thư viện. SQLite được tích hợp qua JDBC, không cần cài máy chủ cơ sở dữ liệu hay import SQL.

Mở terminal tại thư mục chứa `pom.xml`, kiểm tra môi trường rồi biên dịch:

```sh
java -version
javac -version
mvn -version
mvn clean compile
```

Mở **hai terminal riêng** trong cùng thư mục dự án. Chạy Server trước:

```sh
mvn exec:java "-Dexec.mainClass=com.chatapp.server.ServerMain"
```

Nhấn **Khởi động Server** trên cửa sổ quản trị. Sau đó chạy Client ở terminal còn lại:

```sh
mvn exec:java "-Dexec.mainClass=com.chatapp.client.ClientMain"
```

Lặp lại lệnh Client ở terminal mới để mở thêm người dùng. Cách đặt dấu nháy trên dùng được trong PowerShell và các shell thông dụng.

Sau khi biên dịch, có thể dùng script có sẵn:

| Hệ điều hành | Server | Client |
| --- | --- | --- |
| Windows PowerShell | `./run_server.bat` | `./run_client.bat` |
| Linux / macOS | `bash run_server.sh` | `bash run_client.sh` |

Các script chỉ gọi `exec:java`, không tự biên dịch. Sau khi sửa mã nguồn, chạy lại `mvn compile`. Lệnh `mvn package` tạo JAR trong `target/`, nhưng JAR hiện **không đóng gói kèm các thư viện phụ thuộc**; dùng Maven hoặc NetBeans để chạy theo hướng dẫn trên.

## Kết nối trong mạng LAN

| Thiết lập | Giá trị / cách dùng |
| --- | --- |
| Cổng Server mặc định | TCP `8888`, có thể đổi trên cửa sổ Server trước khi khởi động |
| Client trên cùng máy | Host `127.0.0.1`, cổng trùng với Server |
| Client trên máy khác | Host là IPv4 LAN của máy Server, ví dụ `192.168.1.10` |
| Cổng nhận file P2P | TCP động do hệ điều hành cấp trên máy nhận; không cố định là `8888` |

1. Cho các máy vào cùng mạng LAN; trên Windows dùng `ipconfig` để xem IPv4 của máy Server.
2. Khởi động Server, cho phép Java nhận kết nối trên mạng riêng trong firewall.
3. Nhập IP và cổng ở màn hình đăng nhập Client, dùng tài khoản riêng cho từng người.
4. Để gửi file, máy gửi phải kết nối trực tiếp được tới máy nhận. Cho phép Java nhận kết nối trên máy nhận; chỉ mở cổng Server `8888` là chưa đủ cho P2P.

Ứng dụng chưa có NAT traversal hoặc máy chủ chuyển tiếp file; kết nối qua Internet hay mạng Wi-Fi cô lập các thiết bị không được bảo đảm.

## Sử dụng và kiểm tra nhanh

Các tài khoản demo `giahuy`, `sonlam`, `ngochuy` được tự tạo khi khởi tạo database, cùng mật khẩu `123456`. Nút chọn nhanh chỉ điền thông tin; nhấn đăng nhập để kết nối. Có thể dùng tab đăng ký để tạo tài khoản mới. Khi dùng lại database từ phiên bản trước, các tài khoản và lịch sử đã lưu vẫn được giữ nguyên; danh sách thành viên nhóm không tự động xóa tài khoản cũ.

1. Mở hai Client, đăng nhập bằng hai tài khoản khác nhau; kiểm tra danh sách người dùng trực tuyến.
2. Chọn người dùng để chat riêng, gửi tin và kiểm tra trạng thái nhận/xem khi đối phương mở cuộc trò chuyện.
3. Vào **Sảnh Chung** hoặc **Nhóm 03 - Đồ án LTM**; tạo phòng mới và cho Client còn lại tham gia để thử chat nhóm.
4. Chọn cuộc trò chuyện, nhấn **Gửi File P2P** và chọn file. Bên nhận chọn nhận và thư mục lưu; đợi hoàn tất kiểm tra SHA-256 rồi mở file. Thử thêm từ chối và hủy truyền.
5. Thử gửi file trong nhóm với các thành viên đang online. Kiểm tra trạng thái riêng từng người nhận.
6. Đóng rồi mở lại Server và Client từ cùng thư mục dự án; kiểm tra lịch sử tin nhắn và phòng đã tạo.
7. Để thử resume, ngắt một lượt truyền, giữ file `.part`, gửi lại đúng file và chọn lại thư mục nhận cũ.

## Dữ liệu và xử lý lỗi

Server tự tạo `database/chatapp.db` tương đối với **thư mục làm việc**, chứa tài khoản, tin nhắn và phòng chat. Các bảng chính là `users`, `messages`, `rooms`. Dừng Server trước khi sao lưu database; không xóa file này nếu cần giữ tài khoản và lịch sử.

Nếu khởi tạo SQLite thất bại, ứng dụng chuyển sang lưu tài khoản trong bộ nhớ; dữ liệu tài khoản mới không bền vững và không có lưu lịch sử/phòng qua SQLite. Kiểm tra log có dòng `[DB] Khởi tạo cơ sở dữ liệu SQLite thành công.` trước khi demo tính năng lưu trữ.

| Hiện tượng | Cách xử lý |
| --- | --- |
| Không tìm thấy `java`, `javac` hoặc `mvn` | Kiểm tra cài JDK/Maven, `JAVA_HOME` và `PATH`; mở lại terminal sau khi cấu hình |
| `ClassNotFoundException` khi chạy script | Chạy `mvn compile` thành công trước, đứng tại thư mục có `pom.xml` |
| `Address already in use` | Dừng Server đang chiếm cổng hoặc chọn cổng khác và cập nhật Client tương ứng |
| Client không kết nối được | Nhấn khởi động Server; kiểm tra IP, cổng và firewall; máy khác không dùng `127.0.0.1` để trỏ tới Server |
| Chat được nhưng gửi file thất bại | Kiểm tra firewall máy nhận và khả năng kết nối trực tiếp giữa hai Client; P2P dùng cổng TCP động |
| Không thấy lịch sử sau khi chạy lại | Kiểm tra thư mục làm việc, database đang dùng và log SQLite; chỉ tin đã lưu mới được tải lại |
| File truyền dở không tiếp tục | Giữ đúng file `.part`, gửi lại cùng nội dung nguồn và chọn cùng thư mục nhận |
| Maven không tải được dependency | Kiểm tra Internet, proxy hoặc mirror Maven, rồi chạy lại lệnh build |

Đây là bản demo học phần: dùng khóa AES chung và tài khoản mẫu, chưa phù hợp triển khai công khai. Nội dung lịch sử trong SQLite không được mã hóa bởi cơ chế AES trên đường truyền; giữ database ở nơi chỉ người được phép truy cập.

## Bảng đóng góp thành viên

Nhóm gồm 3 thành viên, tổng tỷ lệ đóng góp **100%**: Trần Gia Huy **38%**, Trần Sỷ Sơn Lâm **38%**, Đặng Ngọc Huy **24%**.

Nội dung công việc, vai trò và tỷ lệ đóng góp được tổng hợp theo mục **Thông tin nhóm sinh viên thực hiện (Nhóm 03)** trong [báo cáo đồ án](BAO_CAO_DO_AN_LTM.md).

| MSSV | Họ và Tên | Vai trò | Công việc phụ trách | Tỷ lệ đóng góp |
| --- | --- | --- | --- | --- |
| 2280601246 | Trần Gia Huy | Nhóm trưởng | Thiết kế kiến trúc Client-Server, đặc tả giao thức mạng TCP Packet, Core Server đa luồng, module bảo mật AES & SHA-256 | 38% |
| 2280601697 | Trần Sỷ Sơn Lâm | Thành viên | Cơ sở dữ liệu SQLite, module xác thực người dùng, kiến trúc Chat Nhóm (Group Chat Room Manager) và định tuyến TCP | 38% |
| 2380600794 | Đặng Ngọc Huy | Thành viên | Module truyền file: phân khối byte 64KB, BufferedOutputStream chống tràn RAM, Resume Transfer; cơ chế truyền file ngang hàng P2P Signaling, thiết kế giao diện Client Swing (FlatLaf), trạng thái Typing, Delivered, Read | 24% |


## Quy trình làm việc Git/GitHub

1. Mỗi thành viên tự cấu hình Git bằng thông tin đúng danh sách lớp trên máy của mình:
   ```sh
   git config --global user.name "MSSV_HoVaTen"
   git config --global user.email "email_sinh_vien@domain"
   ```
   Thay các giá trị mẫu bằng thông tin thật trước khi chạy. Kiểm tra thông tin có hiệu lực trong repository bằng `git config --get user.name` và `git config --get user.email`.
2. Tạo Issue trước khi viết code; mỗi Issue có đúng một người chịu trách nhiệm, nhãn và milestone.
3. Tạo nhánh từ nhánh chính đã cập nhật, theo mẫu `feature/<MSSV>-issue-<So_Issue>-<ten-ngan-gon>`. Nhánh chính phải được bảo vệ trên GitHub; không push trực tiếp.
4. Commit theo dạng `feat: ...`, `fix: ...`, `refactor: ...` hoặc `docs: ...`. Kiểm tra `git status` trước khi commit; tuân thủ `.gitignore`, không đưa thư mục build, cấu hình IDE cá nhân hoặc tệp nhị phân lớn vào kho mã nguồn.
5. Mở PR mô tả công việc, liên kết Issue, ảnh hưởng tới module khác, kết quả kiểm tra và ảnh chụp chứng minh chạy thành công. Cần ít nhất một thành viên khác nhận xét và Approve trước khi merge; không tự Approve.
6. Sau khi merge, cập nhật bảng đóng góp bằng công việc thực tế và liên kết PR. Mỗi người tự commit phần việc của mình, duy trì tiến độ đều đặn.

## Kiểm tra và tương thích giao thức

- Tin nhắn văn bản chat riêng và chat nhóm được lưu tại `database/chatapp.db` trên máy Server. Mở cuộc trò chuyện sẽ tải 200 tin gần nhất, theo thứ tự thời gian; chưa có phân trang tin cũ hơn. Phòng tự tạo cũng được lưu để có thể mở lại sau khi Server khởi động lại.
- Chỉ các tin gửi từ phiên bản có tính năng lưu lịch sử mới được lưu; không khôi phục được tin đã mất từ các phiên trước. Chạy Server từ cùng thư mục dự án để dùng đúng database cũ.
- Client và Server cần cập nhật cùng phiên bản để dùng gói yêu cầu/trả lịch sử. Lịch sử chat riêng chỉ truy vấn giữa tài khoản đang đăng nhập và đối phương; lịch sử nhóm yêu cầu đã tham gia nhóm. Các nhóm hiện là nhóm mở, người dùng có thể tham gia từ danh sách phòng.
- Kiểm tra thủ công: gửi tin trong nhóm và chat riêng, đóng Client và Server, chạy lại từ cùng thư mục, đăng nhập rồi mở lại cuộc trò chuyện. Tin cũ phải xuất hiện một lần. Lưu lịch sử hiện không bao gồm file đính kèm hay trạng thái đã xem bền vững.

- Chạy `mvn test` và kiểm tra tổng số test thực sự được chạy trong kết quả Maven. Dự án dùng JUnit 5 nhưng chưa ghim phiên bản Maven Surefire; nếu Maven báo `Tests run: 0`, dùng lệnh dưới để chạy với provider hỗ trợ JUnit 5:

  ```sh
  mvn test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test
  ```

- Bộ kiểm thử bao phủ giao thức, mã hóa, Client/Server, phòng và lịch sử chat, thẻ file và truyền file P2P qua loopback.
- Client và Server phải cùng phiên bản: gói JSON trên dây được bọc Base64 của AES-GCM; file P2P dùng từng khối mã hóa có trường độ dài.
- File `.part` cũ chỉ chứa tên file không được tự động dùng tiếp vì không xác định được nội dung gốc. Gửi lại từ đầu hoặc giữ bản cũ để kiểm tra thủ công.
- Resume yêu cầu người gửi gửi lại cùng file và người nhận chọn cùng thư mục; không tự kết nối lại sau mất mạng.
- Các kiểm tra tự động chưa thay thế demo nhiều máy LAN, thử firewall, file nhiều GB hoặc đo mức dùng RAM.
- Chat nhóm dùng định tuyến ở tầng ứng dụng qua TCP, không sử dụng IP multicast/UDP.

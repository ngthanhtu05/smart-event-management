# SEMS – Quản lý sự kiện thông minh

Website quản lý sự kiện và vé QR, check-in kèm Dashboard trực tiếp, đánh giá sau sự kiện. Backend dùng Java Spring Boot, MySQL; giao diện dùng HTML, CSS và JavaScript.

## 1. Chuẩn bị MySQL

Cần JDK 17 trở lên và MySQL 8. Chạy toàn bộ file `smart_event_management.sql` trong MySQL Workbench. File sẽ xóa database `smart_event_management` cũ, tạo lại bảng và thêm dữ liệu mẫu.

## 2. Chạy chương trình

Mở `src/main/resources/application.properties`, sửa cấu hình MySQL cho đúng máy của bạn. Ví dụ:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_event_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh
spring.datasource.username=root
spring.datasource.password=MAT_KHAU_MYSQL_CUA_BAN
app.ticket-secret=CHUOI_BI_MAT_RIENG_DAI_IT_NHAT_32_KY_TU
```

Nếu dùng đúng địa chỉ `localhost:3306` và tài khoản `root`, bạn chỉ cần sửa dòng mật khẩu MySQL và đặt khóa ký vé riêng. Khóa ký vé cần giữ cố định: đổi khóa sau khi đã phát vé sẽ làm mã QR cũ không còn xác thực được.

Mở PowerShell tại thư mục dự án và chạy `.\mvnw.cmd spring-boot:run`. Mở `http://localhost:8080`. Không cần cài Maven riêng. Sau khi nhập lại SQL, khởi động lại ứng dụng để ký mã QR cho các vé mẫu.

## 3. Tài khoản mẫu

| Vai trò | Tài khoản | Mật khẩu |
|---|---|---|
| Quản trị viên | `admin` | `Admin@123456` |
| Ban tổ chức | `organizer` | `Organizer@123456` |
| Nhân viên check-in | `staff` | `Staff@123456` |
| Người tham dự chưa có vé | `khach1` | `Khach1@123456` |
| Người tham dự đã check-in, chưa đánh giá | `khach18` | `Khach1@123456` |

SQL còn có `organizer2`, `staff2` và các tài khoản `khach2`–`khach29`. Có thể đăng ký tài khoản người tham dự mới trên website.

Mọi tài khoản mẫu có email riêng dạng `ten-dang-nhap@sems.example` và được đánh dấu đã xác minh để demo. Địa chỉ này không nhận thư; hãy đổi sang email thật của bạn để thử gửi mail.

## 4. Chức năng chính

- **Ban tổ chức:** tạo địa điểm và sự kiện, quản lý đăng ký, theo dõi Dashboard và phản hồi.
- **Quản trị viên:** quản lý tài khoản và duyệt sự kiện.
- **Người tham dự:** sau đăng nhập trở lại trang khám phá, xem chi tiết sự kiện, đăng ký vé, xem vé QR, hủy vé hợp lệ và đánh giá sau check-in. Menu tên/avatar có Thông tin cá nhân và Vé của tôi.
- **Nhân viên:** quét QR hoặc tra cứu khách để check-in.

Các bước trình diễn nằm trong [KICH_BAN_DEMO.md](KICH_BAN_DEMO.md).

Vé được gửi đến email tài khoản và lưu trong **Vé của tôi**. Đơn vị/công ty/trường và chức vụ/vai trò là tùy chọn, được đưa vào danh sách và CSV đăng ký.

## 5. Cấu hình SMTP của Admin từ A–Z bằng Gmail

SMTP là cách SEMS kết nối đến máy chủ email để gửi thư. Admin cấu hình một tài khoản gửi chung cho hệ thống; người tham dự nhận thư bằng email của tài khoản SEMS của họ.

Trong dự án, email được dùng cho:

- Gửi mã xác minh khi đăng ký tài khoản hoặc đổi email.
- Gửi vé sau khi đăng ký sự kiện, kèm mã vé, liên kết xem vé và ảnh QR.
- Gửi liên kết xác minh email cho tài khoản chưa xác minh, liên kết đặt lại mật khẩu và thông báo đã đổi mật khẩu.
- Thông báo cho khách có vé khi sự kiện bị hủy.

Chỉ **Admin** được cấu hình SMTP. Ban tổ chức, Staff và người tham dự không phải nhập thông tin SMTP riêng.

### Bước 1. Chuẩn bị tài khoản và chạy SEMS

Chuẩn bị một Gmail bạn sở hữu để gửi thư, một hộp thư thật để nhận thử, máy tính có Internet và SEMS kết nối được MySQL. Có thể dùng cùng một Gmail để gửi và nhận thử; hai hộp thư khác nhau giúp dễ đối chiếu hơn.

Phân biệt các thông tin sau để tránh nhập nhầm:

| Thông tin | Dùng ở đâu? |
|---|---|
| Tài khoản SEMS `admin` và mật khẩu SEMS | Đăng nhập website để mở màn hình Cấu hình email. |
| Gmail gửi thư và mật khẩu ứng dụng Google | Điền vào màn hình Cấu hình email để SEMS gửi thư. |
| Tài khoản MySQL như `root` và mật khẩu MySQL | Kết nối cơ sở dữ liệu trong `.env`. |
| Email của người tham dự | Hộp thư nhận mã xác minh, vé và liên kết đặt lại mật khẩu. |

Bật MySQL, mở Terminal tại thư mục dự án rồi chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Mở `http://localhost:8080`. Nếu dự án đang chạy thì dùng luôn phiên hiện tại. Không cần nhập lại SQL để cấu hình SMTP.

### Bước 2. Bật Xác minh 2 bước cho Gmail gửi thư

1. Đăng nhập đúng Gmail dự định dùng để gửi.
2. Mở [Bảo mật và đăng nhập của Tài khoản Google](https://myaccount.google.com/security).
3. Trong **Cách bạn đăng nhập vào Google**, chọn **Xác minh 2 bước**.
4. Làm theo hướng dẫn trên màn hình đến khi tính năng được bật.

Nếu đã bật thì chuyển sang bước 3. Xem [hướng dẫn Xác minh 2 bước của Google](https://support.google.com/accounts/answer/185839?hl=vi).

### Bước 3. Tạo mật khẩu ứng dụng Google

1. Mở [Mật khẩu ứng dụng](https://myaccount.google.com/apppasswords), đăng nhập lại nếu được yêu cầu.
2. Đặt tên ứng dụng là `SEMS`, chọn **Tạo / Create**.
3. Sao chép mật khẩu 16 ký tự được cấp. Khi điền vào SEMS, nhập liền, bỏ khoảng trắng phân nhóm.

Mật khẩu này khác mật khẩu Gmail thông thường và khác mã xác minh 6 chữ số của SEMS. Nếu không thấy tùy chọn tạo, kiểm tra đúng Gmail và Xác minh 2 bước. Tài khoản trường học/công ty, chế độ chỉ dùng khóa bảo mật hoặc Advanced Protection có thể bị hạn chế. Xem [trợ giúp mật khẩu ứng dụng của Google](https://support.google.com/mail/answer/185833?hl=en).

### Bước 4. Mở Cấu hình email bằng Admin

1. Trên SEMS, mở trang đăng nhập.
2. Đăng nhập `admin` / `Admin@123456` nếu chưa đổi mật khẩu mẫu.
3. Chọn **Cấu hình email** trong menu. Hoặc mở trực tiếp:

```text
http://localhost:8080/pages/mail-settings.html
```

Nếu đang dùng điện thoại/ngrok, thay `http://localhost:8080` bằng tên miền HTTPS đang hoạt động ở mục 7.

Trong khung **Máy chủ SMTP**, điền như sau. Các địa chỉ Gmail trong bảng là ví dụ, phải thay bằng địa chỉ thật của bạn:

| Trường trên giao diện | Giá trị cần điền | Ý nghĩa |
|---|---|---|
| Bật gửi email | Đánh dấu chọn | Cho phép hệ thống gửi thư. |
| Máy chủ | `smtp.gmail.com` | Máy chủ gửi thư Gmail; nhập tên máy chủ, không thêm `https://`. |
| Cổng | `587` | Cổng dùng STARTTLS trong phiên bản SEMS hiện tại. |
| Tài khoản SMTP | `gmail-gui-cua-ban@gmail.com` | Địa chỉ Gmail đầy đủ vừa tạo mật khẩu ứng dụng. |
| Địa chỉ người gửi | Cùng Gmail ở dòng trên | Địa chỉ gửi xuất hiện trong thư nhận. |
| Mật khẩu ứng dụng | Mật khẩu tạo tại bước 3 | Dùng mật khẩu ứng dụng của đúng Gmail gửi thư. |

SEMS đã bật xác thực SMTP và STARTTLS trong mã nguồn, nên màn hình không có ô chọn TLS riêng. Với hướng dẫn này, dùng **587**; cổng **465** cần chế độ SSL khác với cách dự án hiện đang cấu hình. Các cổng Gmail được mô tả trong [tài liệu SMTP của Google](https://developers.google.com/workspace/gmail/imap/imap-smtp).

Bấm **Lưu cấu hình**. Thông báo dự kiến là **Đã lưu cấu hình email.** Việc lưu thành công chỉ xác nhận thông tin đã được lưu; chưa kiểm tra kết nối Gmail. Cấu hình trên giao diện có hiệu lực cho các lần gửi tiếp theo, không cần khởi động lại SEMS. Không cần thêm các dòng `spring.mail.*` vào `.env` cho cách cấu hình này.

### Bước 5. Gửi email thử và kiểm tra hộp thư

1. Trong khung **Kiểm tra gửi thư**, nhập một hộp thư thật bạn kiểm soát vào **Email nhận thử**.
2. Bấm **Gửi email thử** một lần.
3. Khi SEMS báo **Đã gửi email thử. Hãy kiểm tra hộp thư và thư rác.**, mở hộp thư nhận.
4. Tìm thư có tiêu đề **Kiểm tra cấu hình email SEMS**. Kiểm tra cả Spam/Thư rác nếu chưa thấy trong Hộp thư đến.
5. Đối chiếu địa chỉ người gửi và nội dung xác nhận SEMS đã kết nối bằng cấu hình SMTP hiện tại.

Thư thử không có vé hay ảnh QR. Nhận được thư thử chứng minh đường gửi SMTP hoạt động; bước 7 bên dưới mới kiểm tra việc gửi vé thật.

SEMS chỉ cần gửi thư qua SMTP; hướng dẫn này không yêu cầu bật POP/IMAP để đọc hộp thư từ ứng dụng. Khi dùng ngrok, email vẫn được gửi từ máy tính chạy SEMS.

### Bước 6. Chuẩn bị người tham dự có email thật

Các tài khoản mẫu như `khach1@sems.example` không có hộp thư thật. Chọn một trong hai cách sau; email dùng cho tài khoản phải chưa được tài khoản SEMS khác sử dụng.

**Cách A – Đổi email cho tài khoản mẫu, thuận tiện khi dùng `khach1`:**

1. Giữ SMTP đang bật. Đăng xuất Admin, đăng nhập `khach1` / `Khach1@123456` nếu chưa đổi mật khẩu mẫu.
2. Mở menu tên/avatar → **Thông tin cá nhân** → **Đổi email tài khoản**.
3. Nhập email thật và **mật khẩu hiện tại của tài khoản SEMS**, bấm **Lưu email mới**.
4. Mở hộp thư mới, lấy mã trong thư **Mã xác minh email SEMS**.
5. Nhập mã 6 chữ số, bấm **Xác minh và đổi email**. Email cũ vẫn được dùng cho đến khi xác minh thành công.

**Cách B – Đăng ký tài khoản người tham dự mới:**

1. Đăng xuất và mở `/signup.html` trên website SEMS.
2. Nhập họ tên, tên đăng nhập chưa dùng, email thật và mật khẩu SEMS ít nhất 12 ký tự; xác nhận mật khẩu.
3. Bấm **Tạo tài khoản**, mở hộp thư lấy mã, sau đó bấm **Xác minh và tạo tài khoản**.
4. Đăng nhập bằng tên đăng nhập và mật khẩu SEMS vừa tạo.

Mã đăng ký/đổi email có hiệu lực **10 phút**. Sau khi yêu cầu mã, cần chờ **60 giây** trước khi gửi lại; dùng mã mới nhất. Nếu mã hết hạn, bắt đầu lại yêu cầu. Một email không thể gắn với hai tài khoản SEMS.

### Bước 7. Kiểm tra gửi vé thật – ca gốc UC23-03

1. Đăng nhập bằng người tham dự có email thật đã xác minh ở bước 6; Admin không phải vai trò đăng ký vé.
2. Chọn một sự kiện đã duyệt, còn chỗ, đang mở đăng ký và chưa kết thúc. Tài khoản, email và số điện thoại dùng thử chưa có vé đang hiệu lực tại sự kiện đó.
3. Mở chi tiết, chọn đăng ký, điền họ tên, số điện thoại và xác nhận. Email nhận vé được lấy từ **email tài khoản**, không phải email gửi thử của Admin.
4. Website chuyển sang trang vé. Ghi lại tên sự kiện và mã vé, mở **Vé của tôi** để đối chiếu.
5. Mở hộp thư người tham dự, tìm thư có tiêu đề bắt đầu bằng **Vé tham dự:** và tên sự kiện.
6. Kiểm tra thư có tên sự kiện, mã vé khớp website, liên kết **Xem vé** và ảnh đính kèm **ve-qr.png**.
7. Mở ảnh QR, đối chiếu với QR của vé trên website. Mở liên kết xem vé bằng tài khoản sở hữu vé; nếu cần dùng điện thoại, thiết lập địa chỉ công khai ở bước 8 trước khi đăng ký.

Vé được lưu trước khi gửi email. Nếu gửi thư thất bại, vé vẫn có thể tồn tại trong **Vé của tôi**; thấy vé trên website chưa đủ để kết luận `UC23-03` Pass. Bật SMTP cũng không tự gửi lại email cho các vé đã tạo trước đó: hãy dùng một lần đăng ký mới hợp lệ.

Khi đã nhận và đối chiếu đủ thư, ghi kết quả thực tế của `UC23-03` vào **file Excel 36 ca gốc đang dùng**, kèm người kiểm thử, ngày thực tế và ảnh minh chứng. Chỉ ghi **Pass** khi kết quả mong đợi đã được kiểm tra. Ảnh nộp bài nên thể hiện thư và vé, tránh để lộ mật khẩu ứng dụng.

### Bước 8. Kiểm tra liên kết email, ngrok và quên mật khẩu

Các liên kết xem vé, xác minh email và đặt lại mật khẩu được tạo từ `PUBLIC_BASE_URL` trong `.env`.

Nếu chỉ mở liên kết trên chính máy tính chạy SEMS, dùng:

```dotenv
PUBLIC_BASE_URL=http://localhost:8080
```

Nếu mở trên điện thoại hoặc máy khác, chạy ngrok theo mục 7 rồi điền đúng địa chỉ HTTPS được cấp, không thêm dấu `/` ở cuối. Ví dụ:

```dotenv
PUBLIC_BASE_URL=https://ten-cua-ban.ngrok-free.app
```

Sau khi sửa `.env`, lưu file, nhấn `Ctrl+C` ở Terminal chạy SEMS rồi chạy lại:

```powershell
.\mvnw.cmd spring-boot:run
```

Giữ ngrok hoạt động và tạo yêu cầu email mới. Liên kết trong email đã gửi trước khi đổi cấu hình vẫn giữ địa chỉ cũ. `localhost` trên điện thoại chỉ chính điện thoại nên không mở được SEMS trên máy tính.

Để thử quên mật khẩu:

1. Dùng tài khoản kiểm thử đang hoạt động, có email thật đã xác minh. Ghi nhớ tên đăng nhập của tài khoản.
2. Đăng xuất, mở **Quên mật khẩu**, nhập email tài khoản rồi bấm **Gửi liên kết**.
3. Mở thư **Đặt lại mật khẩu SEMS**, bấm liên kết trong vòng **20 phút**.
4. Nhập và xác nhận mật khẩu SEMS mới ít nhất 12 ký tự, hoàn tất rồi thử đăng nhập lại.

Liên kết đặt lại mật khẩu chỉ dùng một lần. Thông báo chung trên trang quên mật khẩu không chứng minh thư đã gửi: tài khoản không tồn tại, bị khóa hoặc email chưa xác minh sẽ không được gửi liên kết.

### Bước 9. Lưu cấu hình và tắt gửi thư khi không dùng

Cấu hình được lưu trong bảng `mail_settings` của MySQL; mật khẩu ứng dụng được mã hóa trước khi lưu. Khởi động lại SEMS với cùng database và khóa cấu hình vẫn giữ thông tin đã lưu. Không cần cấu hình lại Gmail mỗi lần chạy dự án.

- **Tắt gửi thư:** Admin mở **Cấu hình email**, bỏ chọn **Bật gửi email**, bấm **Lưu cấu hình**. Mật khẩu đã lưu vẫn được giữ.
- **Bật lại:** đánh dấu **Bật gửi email**, lưu rồi gửi thử. Để trống ô mật khẩu khi lưu sẽ giữ mật khẩu đã có; lần cấu hình đầu tiên phải nhập mật khẩu để bật gửi.
- **Đổi Gmail gửi:** cập nhật cả tài khoản SMTP, địa chỉ người gửi và mật khẩu ứng dụng tương ứng, lưu rồi gửi thử.
- **Khóa mã hóa:** dự án dùng `MAIL_CONFIG_KEY` nếu đã đặt, nếu chưa thì dùng `TICKET_SECRET`. Giữ nguyên khóa đang sử dụng. Đổi khóa có thể khiến mật khẩu SMTP cũ không giải mã được; ưu tiên khôi phục đúng khóa cũ, không xóa database để xử lý.

Khi SMTP tắt, hệ thống bỏ qua bước nhập mã lúc đăng ký/đổi email theo quy ước demo; vé vẫn xem trên website nhưng không có email vé mới. Chức năng quên mật khẩu không gửi được thư khi SMTP tắt.

### Lỗi SMTP thường gặp và cách xử lý

| Hiện tượng | Kiểm tra và xử lý |
|---|---|
| Không thấy mục Mật khẩu ứng dụng Google | Xem điều kiện tài khoản ở bước 3 và liên kết trợ giúp Google. |
| `Cấu hình SMTP không hợp lệ.` | Máy chủ chỉ là `smtp.gmail.com`, cổng `587`; nhập đầy đủ hai địa chỉ email hợp lệ, không thêm `https://` vào máy chủ. |
| `Cần nhập mật khẩu SMTP để bật gửi thư.` | Lần lưu đầu tiên chưa có mật khẩu; nhập mật khẩu ứng dụng rồi lưu lại. |
| `Hãy bật gửi email và lưu cấu hình trước.` | Chọn Bật gửi email và Lưu cấu hình trước khi bấm Gửi email thử. |
| Đã lưu nhưng gửi thử thất bại | Lưu không kiểm tra kết nối. Đối chiếu Gmail, mật khẩu ứng dụng và cổng theo bước 4; xem lỗi cụ thể trong Terminal chạy SEMS. |
| Log có `AuthenticationFailedException`, `535` hoặc `BadCredentials` | Kiểm tra đúng tài khoản SMTP và mật khẩu ứng dụng của tài khoản đó; không nhập mật khẩu SEMS hay MySQL. |
| Trước đây gửi được, sau khi đổi mật khẩu Google thì lỗi | Tạo mật khẩu ứng dụng mới, cập nhật trong SEMS rồi gửi thử. Google thu hồi mật khẩu ứng dụng khi đổi mật khẩu Google; xem [trợ giúp Google](https://support.google.com/mail/answer/185833?hl=en). |
| Log có timeout, không kết nối được máy chủ | Kiểm tra Internet và mạng đang dùng có cho phép kết nối tới `smtp.gmail.com:587`; thử mạng khác nếu cần. |
| SEMS báo đã gửi nhưng chưa thấy thư | Kiểm tra đúng Email nhận thử/email tài khoản người tham dự, mục Spam và các thư mới; không dùng địa chỉ `@sems.example`. |
| Gửi thử được nhưng không nhận email vé | Kiểm tra SMTP vẫn bật, email của người tham dự là email thật và đã tạo một đăng ký vé mới hợp lệ sau khi bật. Xem log gửi vé. |
| Đổi email báo đã được sử dụng | Email đang thuộc tài khoản SEMS khác; chọn một hộp thư thật khác chưa gắn tài khoản. |
| Nhập mã báo sai, hết hạn hoặc gửi lại quá nhanh | Dùng mã mới nhất, đúng 6 chữ số; mã có hiệu lực 10 phút; chờ 60 giây để gửi lại. Hết hạn thì bắt đầu lại. |
| Email có link `localhost`, điện thoại không mở được | Cập nhật `PUBLIC_BASE_URL` theo bước 8, khởi động lại SEMS rồi tạo yêu cầu gửi thư mới. |
| `Không đọc được cấu hình SMTP. Kiểm tra MAIL_CONFIG_KEY.` | Kiểm tra có thay `MAIL_CONFIG_KEY` hoặc `TICKET_SECRET` sau khi lưu SMTP không; khôi phục khóa trước đó. |

Khi gặp lỗi, xem Terminal đang chạy SEMS. Tìm các dòng như **Không gửi được email thử**, **Không gửi được email vé** hoặc **Không gửi được mã xác minh** và phần nguyên nhân ngay bên dưới. Không đưa mật khẩu ứng dụng hay khóa cấu hình vào ảnh log/README.

## 6. Kiểm thử

```powershell
.\mvnw.cmd test
```

Chạy riêng 36 ca gốc:

```powershell
.\mvnw.cmd "-Dtest=Original36CasesIntegrationTest" test
```

Lượt chạy ngày 05/11/2026 có 33 ca **Pass** và 3 ca bị chặn: `UC23-03` cần SMTP và hộp thư thật; `UC29-07`, `UC29-08` cần điện thoại, HTTPS và quyền camera. Phạm vi của 33 ca Pass là kiểm thử API/backend; chưa xác nhận toàn bộ thao tác giao diện. Hướng dẫn chuẩn bị điện thoại nằm ở mục 7; cấu hình SMTP nằm ở mục 5.

## 7. Mở SEMS trên điện thoại bằng ngrok

Ngrok tạo đường link HTTPS chuyển tiếp đến SEMS trên máy tính. Điện thoại chỉ cần trình duyệt và Internet, có thể dùng Wi-Fi hoặc 4G/5G. MySQL và SEMS vẫn chạy trên máy tính.

### Bước 1. Chạy SEMS trên máy tính

Bật MySQL. Trong VS Code, mở **Terminal → New Terminal** tại thư mục chứa `mvnw.cmd` rồi chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Mở `http://localhost:8080` trên máy tính và thử đăng nhập. Nếu ứng dụng đang chạy thì giữ nguyên Terminal đó. Với database đã có dữ liệu, không cần nhập lại SQL để dùng ngrok.

### Bước 2. Cài ngrok trên Windows

Mở Terminal PowerShell thứ hai và chạy:

```powershell
winget install ngrok -s msstore
```

Cũng có thể cài qua Microsoft Store từ [trang tải ngrok cho Windows](https://ngrok.com/download/windows). Sau khi cài, đóng rồi mở lại Terminal và kiểm tra:

```powershell
ngrok version
```

### Bước 3. Kết nối tài khoản ngrok

Đăng ký hoặc đăng nhập tại [ngrok Dashboard](https://dashboard.ngrok.com/), mở [Your Authtoken](https://dashboard.ngrok.com/get-started/your-authtoken), sao chép token rồi chạy:

```powershell
ngrok config add-authtoken "DAN_TOKEN_CUA_BAN_VAO_DAY"
```

Thay phần trong dấu ngoặc kép bằng token thật. Giữ token riêng, không đưa vào README hoặc commit lên Git.

### Bước 4. Tạo đường link HTTPS

Trong Terminal thứ hai, chạy:

```powershell
ngrok http http://127.0.0.1:8080
```

Ngrok sẽ hiển thị dòng tương tự:

```text
Forwarding  https://ten-cua-ban.ngrok-free.app -> http://127.0.0.1:8080
```

Sao chép đúng địa chỉ HTTPS ngrok hiển thị trên máy của bạn. Địa chỉ trên chỉ là ví dụ. Cấu hình `.env` có thể giữ:

```dotenv
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
```

Giữ cả hai Terminal hoạt động. Máy tính cần bật, có Internet và không chuyển sang chế độ ngủ trong lúc dùng điện thoại. Xem thêm [hướng dẫn Agent CLI của ngrok](https://ngrok.com/docs/gateway/endpoints/agent-cli-quickstart).

### Bước 5. Đăng nhập từ điện thoại

Gửi link HTTPS sang điện thoại, mở bằng Chrome hoặc Safari. Nếu xuất hiện trang thông báo của ngrok, bấm **Visit Site / Visit** để tiếp tục. Đây là [trang thông báo của gói miễn phí](https://ngrok.com/docs/pricing-limits/free-plan-limits).

Mở đường dẫn `/login.html` trên tên miền ngrok, ví dụ:

```text
https://ten-cua-ban.ngrok-free.app/login.html
```

Đăng nhập bằng tài khoản SEMS ở mục 3, chẳng hạn `staff` / `Staff@123456` để check-in hoặc `khach1` / `Khach1@123456` để xem và đăng ký sự kiện, nếu chưa đổi mật khẩu. Phiên đăng nhập trên điện thoại độc lập với phiên trên máy tính.

### Bước 6. Cập nhật liên kết trong email khi cần

Để liên kết xem vé và đặt lại mật khẩu trong email mở được trên điện thoại, sửa dòng `PUBLIC_BASE_URL` trong `.env` thành địa chỉ HTTPS thật:

```dotenv
PUBLIC_BASE_URL=https://ten-cua-ban.ngrok-free.app
```

Lưu file, quay lại Terminal chạy SEMS, nhấn `Ctrl+C` rồi chạy lại:

```powershell
.\mvnw.cmd spring-boot:run
```

Giữ Terminal ngrok chạy. Mỗi khi địa chỉ truy cập thay đổi, cập nhật lại `PUBLIC_BASE_URL` rồi khởi động lại SEMS. Đăng nhập trực tiếp qua ngrok có thể thực hiện trước bước này.

### Thực hành hai ca camera trên điện thoại

Chuẩn bị Staff đã được Admin phân công vào sự kiện được duyệt, còn hiệu lực, đang trong khung giờ check-in hiển thị trên trang. Chuẩn bị hai vé khác nhau còn trạng thái `ISSUED`; vé đã check-in không dùng lại để kiểm tra thành công.

**UC29-07 – Cho phép camera và quét QR:**

1. Trên máy tính, đăng nhập tài khoản sở hữu vé thứ nhất và mở QR trong **Vé của tôi**.
2. Trên điện thoại, mở link ngrok bằng HTTPS, đăng nhập `staff`, vào **Check-in** và chọn đúng sự kiện.
3. Bật quét camera, bấm **Cho phép**, chọn camera sau nếu có nhiều camera.
4. Quét QR trên màn hình máy tính. Kiểm tra tên khách, mã vé, thời điểm và trạng thái check-in.
5. Mở Dashboard của sự kiện trên máy tính bằng Admin hoặc Organizer quản lý sự kiện trước khi quét. Đối chiếu số đã check-in tăng một và số chờ giảm một. Quét lại cùng vé phải báo đã check-in và không tăng số lần nữa.

**UC29-08 – Từ chối quyền camera:**

1. Dùng vé thứ hai còn `ISSUED`. Trong cài đặt quyền của trang ngrok trên điện thoại, đặt lại quyền camera về hỏi lại để có thể chọn **Không cho phép**. Nếu cần, đăng nhập lại sau khi đặt lại quyền.
2. Mở trang Check-in, chọn sự kiện, bật camera rồi chọn **Không cho phép**.
3. Kiểm tra có thông báo camera không truy cập được; riêng việc từ chối quyền không làm vé được check-in hoặc thay đổi số liệu Dashboard.
4. Nhập mã vé thứ hai vào ô check-in thủ công, xác nhận. Vé hợp lệ phải được check-in đúng một lần; nhập lại phải báo đã check-in.

Camera trình duyệt cần ngữ cảnh bảo mật; hãy dùng link HTTPS của ngrok. Xem [yêu cầu của API camera](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).

Sau khi thực hiện, ghi loại điện thoại, hệ điều hành, trình duyệt vào **Build**, ghi ngày thực tế vào **Tested date**, lưu ảnh kết quả và cập nhật **Result** trong file Excel 36 ca gốc. Chỉ đổi sang **Đạt** khi đã đối chiếu đủ kết quả mong đợi. Cài ngrok xong chưa chứng minh hai ca đã đạt. `UC23-03` vẫn cần kiểm tra SMTP và hộp thư thật theo mục 5.

### Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| PowerShell không nhận lệnh `ngrok` | Mở lại Terminal sau khi cài. Nếu vẫn lỗi, kiểm tra cài đặt qua Microsoft Store. |
| Ngrok báo không kết nối được ứng dụng | Kiểm tra SEMS còn chạy và mở được `http://localhost:8080` trên máy tính. Cổng ngrok phải trùng cổng SEMS. |
| Điện thoại không mở được `localhost:8080` | Trên điện thoại dùng link HTTPS ngrok hiển thị, vì `localhost` của điện thoại là chính điện thoại đó. |
| Camera không mở | Dùng HTTPS, cấp quyền camera cho trang và cho trình duyệt trong cài đặt điện thoại. Mở link trực tiếp bằng Chrome/Safari. |
| Không thấy sự kiện hoặc bị từ chối check-in | Kiểm tra phân công Staff, đúng sự kiện của vé, trạng thái vé và khung giờ check-in. |
| Email vẫn có link `localhost` | Sửa `PUBLIC_BASE_URL`, lưu `.env`, khởi động lại SEMS rồi tạo yêu cầu gửi email mới. |

Kết thúc buổi kiểm thử, nhấn `Ctrl+C` trong Terminal ngrok để ngừng chia sẻ website. Trong thời gian tunnel chạy, người có link có thể truy cập trang đăng nhập SEMS.

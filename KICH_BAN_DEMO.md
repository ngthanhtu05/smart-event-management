# Kịch bản demo SEMS

## Chuẩn bị

1. Nhập `smart_event_management.sql`, khởi động ứng dụng theo [README.md](README.md). Nên nhập SQL gần giờ demo vì sự kiện mẫu **Ngày hội Công nghệ và Khởi nghiệp** có thời gian tính từ lúc nhập dữ liệu.
2. Mở các phiên trình duyệt độc lập cho `organizer`, `admin`, `staff` và `khach1`. Các tab ẩn danh trong cùng một cửa sổ thường dùng chung phiên đăng nhập; hãy dùng cửa sổ/hồ sơ trình duyệt riêng.
3. Chuẩn bị webcam để quét mã QR hiển thị trên điện thoại hoặc màn hình khác. Có thể dùng mã vé để thử check-in thủ công.

## 1. Sự kiện và vé QR

1. **Organizer:** mở danh sách sự kiện, xem chi tiết sự kiện mẫu và danh sách đăng ký. Tạo sự kiện mới với địa điểm, thời gian, mô tả và lịch trình nếu muốn trình diễn quy trình duyệt.
2. **Admin:** mở sự kiện mới đang chờ, xem nội dung rồi phê duyệt. Nếu từ chối, nhập lý do để Organizer xem và sửa trước khi gửi lại.
3. **Khách (`khach1`):** đăng nhập và trở lại trang chủ, mở chi tiết **Ngày hội Công nghệ và Khởi nghiệp**, bấm đăng ký vé. Vé dùng email tài khoản; thêm đơn vị/chức vụ tùy chọn. Vào **Vé của tôi** xem mã QR; đăng ký lại cùng sự kiện để thấy hệ thống ngăn trùng.

## 2. Check-in và Dashboard

1. **Organizer:** mở Dashboard của sự kiện mẫu, ghi nhận số đăng ký và số đã đến trước khi quét.
2. **Staff:** chọn đúng sự kiện, bật camera và quét QR của `khach1`; nếu không dùng camera, nhập mã vé để check-in thủ công.
3. **Organizer:** xem số đã đến và tỷ lệ tham dự tự cập nhật trên Dashboard; xem khách vừa check-in trong danh sách.
4. **Staff:** quét lại cùng vé để thấy thông báo vé đã check-in. Có thể dùng ô tìm tên/email khi khách quên vé.

## 3. Đánh giá và báo cáo

1. **Khách (`khach1`):** mở vé đã check-in, chấm 1–5 sao và viết nhận xét. Thử đánh giá lần nữa để thấy giới hạn một lần cho mỗi vé.
2. **Organizer:** mở **Phản hồi** của sự kiện, xem điểm trung bình, phân bố sao, biểu đồ và danh sách nhận xét; thử lọc/tìm kiếm.
3. **Organizer:** xem đơn vị/chức vụ trong danh sách đăng ký và CSV; tải báo cáo check-in, đánh giá. Có thể đăng nhập `khach18` để thử gửi đánh giá bằng một vé mẫu đã check-in.

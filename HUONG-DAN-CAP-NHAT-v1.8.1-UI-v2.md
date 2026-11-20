# MiniSocial v1.8.1 — Áp dụng UI Assets v2.0 cho Java ME

## Kết quả

Bản này được chỉnh **trực tiếp trên source MiniSocial v1.8.0 người dùng gửi**. Phần Java ME đã thay đổi, không thay đổi API, cấu trúc Supabase, secret, xác thực hoặc backend Java.

**Màn hình được làm lại / tích hợp thành phần v2:**

- **Messenger:** tab Chat, danh sách bạn, bong bóng tin nhắn gửi/nhận, thời gian tin, ảnh đại diện mặc định, icon đính kèm và gửi tin. Gửi tin nhắn giữ thao tác **phím 2 hoặc softkey trái** và TextBox gốc; gửi ảnh vẫn phím **8**.
- **Social Feed:** tab Bảng tin, thẻ bài viết bo góc, icon thích, màn rỗng, nút đăng bài (phím 2) và ảnh (phím 8).
- **Profile:** ảnh bìa trích từ bộ v2, avatar mặc định, tên/@handle/bio động, danh sách bài cá nhân.
- **Notifications:** màn lời mời kết bạn (từ tab Bạn bè, phím 7), icon và trạng thái rỗng. **Chưa có hệ thống push notification**.
- **Settings:** danh sách cài đặt, icon và toggle thông báo / giao diện tối sáng; lưu tùy chọn cũ bằng RMS.
- **System:** icon tải dữ liệu, hiển thị trạng thái, thẻ, màu nền và giao diện xác nhận chung. Màn đăng nhập/đăng ký/OTP có palette xanh mới và được xử lý lại tương phản khi bật Dark Mode.

**Độ phân giải:**

| Chiều rộng × chiều cao | Pack PNG nạp tự động |
| --- | --- |
| 320 × 240 (Nokia E72) | `assets/e72/` |
| 640 × 360 (màn ngang) | `assets/wide/` |
| 640 × 480 (Nokia E6) | `assets/e6/` |
| 240 × 320, 360 × 640 | Bố cục responsive + icon e72, có thể cần tinh chỉnh thêm trên máy thật |

JAR hiện chứa **288 PNG runtime** tối ưu thay vì nhúng toàn bộ 1.833 PNG của bộ thiết kế. Tài nguyên chia theo profile; chỉ giải mã khi cần, cache tối đa 14 ảnh. Các thành phần cần co giãn (thẻ, bong bóng tin, text) vẫn được vẽ bằng `Canvas` để giữ RAM thấp. Bộ thiết kế **đầy đủ** nằm ở `design/MiniSocial_UI_Assets_JavaME_v2_Updated.zip`, không được đóng vào JAR.

**Giới hạn tính năng:** UI v2 không tự tạo thêm API chat nhóm, video call, voice chat, hệ thống tin nhắn đã xem, thông báo đẩy hay upload video. Các mục này mới có asset thiết kế, còn ứng dụng giữ tính năng vốn có của v1.8.0. Biểu tượng đã xem chỉ hiện nếu phản hồi chat thực tế có trường thứ 7 `read` hoặc `seen`; không hiển thị trạng thái giả.

## Cài trên điện thoại

1. Sao lưu MiniSocial v1.8.0 đang chạy.
2. Chép `client/dist/MiniSocial-Responsive.jar` (v1.8.1) vào Nokia để cài. Nếu cài qua JAD, chép thêm `client/dist/MiniSocial-Responsive.jad` cùng thư mục với JAR.
3. Kiểm tra đăng nhập, Bảng tin, Chat, Bạn bè, Tùy chọn → Cá nhân / Cài đặt, chế độ Sáng/Tối.
4. Với Nokia E72, ưu tiên kiểm tra thực tế sau 10–20 phút sử dụng để phát hiện thiếu RAM hoặc lỗi Java PNG (chưa kiểm trên điện thoại vật lý).

## Cập nhật website tải ứng dụng trên Xubi / Pi 5

1. Giải nén ZIP full source; **copy các thư mục con bên trong `MiniSocial/` vào gốc repo MiniSocial**. Tránh lồng hai cấp `MiniSocial/MiniSocial`.
2. Commit/push lên GitHub. Nếu repo lớn, có thể **không đẩy `design/`**: chỉ cần `client/resources/`, `client/src/`, `client/dist/`, `client/build_j2me.py`, `web/index.html` và các file dự án gốc.
3. Deploy lại Docker qua Xubi với Dockerfile gốc `server/Dockerfile`; trang `/download/MiniSocial-Responsive.jar` sẽ trả bản JAR mới.
4. Truy cập `/health`, `/admin` và `/download/MiniSocial-Responsive.jad`. **Không cần chạy SQL migration mới** và không cần thay biến môi trường Supabase nếu server cũ vẫn chạy tốt.

## Build lại JAR

Cần JDK 17/21 và Python 3; chạy tại thư mục gốc dự án:

```sh
python3 client/build_j2me.py
```

Để tái tạo PNG runtime từ bộ v2 gốc, cần cài Pillow và giải nén file thiết kế vào `design/MiniSocial_UI_Assets_JavaME_v2/`, rồi chạy:

```sh
python3 client/prepare_ui_v2.py design/MiniSocial_UI_Assets_JavaME_v2
python3 client/build_j2me.py
```

`client/build_j2me.py` vẫn dùng bộ chuyển bytecode `StackMap` thử nghiệm vốn có của source v1.8.0; không tương đương với preverify chuẩn từ WTK. Nếu Nokia thật từ chối JAR, cần kiểm tra bằng emulator hoặc trình preverify chuẩn trước khi phát hành.

## Phạm vi kiểm thử

- Build JAR/JAD v1.8.1 với JDK 21: thành công.
- Kiểm tra các PNG trong JAR và khớp `MIDlet-Jar-Size`: thành công.
- `ResponsiveLayoutSmoke`, `LucideMenuSmoke` đã bổ sung 640×360 và qua trên các kích thước 240×320, 320×240, 360×640, 640×360, 640×480.
- Các test form đăng nhập, đăng ký, đổi mật khẩu, onboarding, chọn server: qua.
- **Chưa kiểm thử:** Nokia E72/E6 vật lý, TLS/HTTPS thực với máy Nokia cũ, tích hợp Supabase production và việc build/deploy Docker trên Pi 5.

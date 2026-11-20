# MiniSocial v1.9.0 — Adaptive UI

## Những phần đã cập nhật

- Tab Chat lấy danh sách người đã kết bạn cùng nội dung/thời gian tin gần nhất từ endpoint mới `/m/conversations`; sắp xếp người có tin gần đây lên trước.
- Trên màn 640×360 và 640×480: bố cục hai cột khi mở hội thoại (trái: danh sách, phải: nội dung). Trên màn 240×320, 320×240, 360×640, 480×640: một cột.
- Khi mở chat, ẩn thanh tab chính. Bong bóng tin dài cuộn theo pixel, không cắt tại 4–5 dòng. Hỗ trợ lên/xuống và vuốt chạm (khi máy hỗ trợ).
- Màn chat có nút quay lại trên header; bấm `#` không đăng xuất ngay, đăng xuất qua menu Cài đặt.
- Giữ màu Messenger Light/Dark, icon PNG từ bộ asset v2 và kết nối cũ.

## Phím và thao tác

| Phím | Danh sách chat | Đang chat |
|---|---|---|
| Lên/xuống | Chọn hội thoại | Cuộn lên đọc tin cũ / cuộn xuống tin mới |
| OK | Mở hội thoại | Xem ảnh gần nhất nếu có |
| 2 | — | Soạn tin |
| 8 | — | Gửi ảnh từ đường dẫn file:/// |
| 9 | Tải lại | Tải lại 40 tin mới nhất |
| Trái/phải ở màn hai cột | — | Chuyển tiêu điểm giữa danh sách và nội dung |
| Phím mềm phải | Trở về | Trở về danh sách |

Trên thiết bị cảm ứng, chạm hội thoại để mở; kéo danh sách hoặc nội dung tin nhắn để cuộn.

## Backend và Supabase

API bổ sung: `GET /m/conversations` với token đăng nhập, trả về các dòng gồm 5 trường: `friend_id`, `display_name`, `handle`, `last_message_preview`, `last_message_timestamp` (URL-encoded, phân cách bởi `|`). Dữ liệu được lấy từ view `friend_list` và bảng `messages` hiện có; **không cần SQL migration**. Có giới hạn truy vấn 300 tin gần đây để nhẹ trên Raspberry Pi. Nếu server vẫn ở bản 1.8, client tự dùng danh sách bạn bè nhưng sẽ thiếu preview/thời gian.

API không tự tạo số tin chưa đọc, trạng thái online, đang nhập, dấu đã xem. Những tính năng đó cần schema/server riêng.

## Triển khai Xubi / Raspberry Pi 5

1. Sao lưu repo và database trước khi cập nhật. Giải nén ZIP Full Source, lấy toàn bộ nội dung trong thư mục `MiniSocial/` đưa vào repository MiniSocial hiện có.
2. Giữ nguyên `deploy/.env` chứa Supabase key; không commit key hay ghi đè biến môi trường. Không thay đổi Gunny.
3. Push GitHub, deploy Docker lại qua Xubi. Hoặc tại thư mục gốc của dự án chạy `docker compose -f deploy/docker-compose.yml up -d --build`.
4. Kiểm tra `https://minisocial.xubi.org/health`, tải lại JAR/JAD từ `/download/` và thử hai tài khoản đã kết bạn.

Các cổng Docker `18087→8080`, HTTPS và schema vẫn như trước. **Cần nâng cấp cả server để có preview hội thoại**.

## Giới hạn kiểm thử

- Mỗi hội thoại hiện chỉ tải tối đa 40 tin mới; cuộn có thể xem toàn bộ 40 tin đã tải nhưng chưa có phân trang đọc tin cũ hơn.
- Không tự ép màn hình xoay nếu điện thoại không báo thay đổi kích thước.
- JAR biên dịch bằng bộ chuyển CLDC StackMap thử nghiệm của repo; chưa thử trên Nokia thật hay backend Supabase production. Cần test cài đặt, TLS và tốc độ trên E72/E6 trước khi phát hành rộng.

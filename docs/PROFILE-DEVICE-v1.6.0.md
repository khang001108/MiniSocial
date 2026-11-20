# MiniSocial 1.6.0 — Hồ sơ và thiết bị

## Nguyên nhân lỗi đang thấy

`duplicate key value violates unique constraint "profiles_handle_key"` là handle đã có người dùng; backend cũ gửi thẳng lỗi SQL. Bản mới kiểm tra handle trước khi tạo auth user và trả HTTP 409. Không xóa database hoặc tài khoản khác.

## Dùng trên Nokia

1. Đăng ký 3 ô: tên tài khoản 3–24 ký tự a-z, số hoặc dấu gạch dưới; email; mật khẩu 8+ ký tự.
2. Đăng nhập: nếu tài khoản mới, xuất hiện form **Thiết lập hồ sơ**, gồm Tên hiển thị, Model điện thoại và đường dẫn file avatar. Tên là bắt buộc, ảnh tùy chọn.
3. Model được đọc từ `System.getProperty("microedition.platform")`. Nếu máy hoặc J2ME Loader trả nhãn chung, sửa ô Model thủ công; nhãn không xác minh phần cứng.
4. Avatar JPEG/PNG tối đa 180 KiB (nên thu nhỏ dưới 512×512 pixel, tốt nhất 240×240) (ví dụ `file:///E:/Images/avatar.jpg`). Trên máy thực tế đường dẫn ổ có thể khác. App cần quyền đọc file (JSR-75).
5. Nhấn **Hoàn tất**, MiniSocial lưu hồ sơ vào Supabase. Bài đăng, chat và bình luận sau đó nhận nhãn từ máy gửi. Khi xem hồ sơ của chính mình, phím **9** cho phép đổi lại tên, ảnh và nhãn máy.
6. Nếu muốn tải ảnh từ camera, hãy chụp bằng ứng dụng Máy ảnh của Nokia trước rồi chọn file ảnh; bản này chưa tích hợp camera trực tiếp trong JAR.

## Cấu hình server

- Supabase project **MiniSocial riêng**: chạy `deploy/UPDATE-v1.6.0.sql` một lần trong SQL Editor. Giữ nguyên `deploy/supabase.sql` (schema cơ sở) nếu database đã tồn tại.
- Xubi → Servers: Repo GitHub `khang001108/MiniSocial`, Dockerfile `server/Dockerfile`, Host `18087`, Container `8080`.
- Environment: dùng ba biến `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY` hiện có; không đưa secret vào GitHub hay JAR.
- Commit/Push mã nguồn, Deploy lại. `/health` trả `{"ok":true,"service":"MiniSocial"}`.
- Từ ngoài mạng: JAR kết nối `https://minisocial.xubi.org`, chứng chỉ và Caddy cần hoạt động.

## Tính tương thích

- Tài khoản cũ có `onboarding_complete=true` mặc định và không phải thiết lập lại bắt buộc.
- Không phá dữ liệu trong bảng profiles/posts/messages hiện có; các trường device mới có giá trị mặc định chuỗi rỗng với sự kiện cũ.
- Nhãn model có thể giả mạo; chỉ dùng để hiển thị, không phải để xác thực hay chống gian lận.
- Không kiểm thử với Supabase production hoặc Nokia thật trong gói phát hành này.

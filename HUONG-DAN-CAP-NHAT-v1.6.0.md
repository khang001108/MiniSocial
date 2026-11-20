# MINI SOCIAL v1.6.0 — Hướng dẫn nâng cấp Xubi

Lỗi `profiles_handle_key` ở ảnh là **tên tài khoản đã tồn tại**; KHÔNG xóa database.

## THỨ TỰ RẤT QUAN TRỌNG

1. Sao lưu schema/dữ liệu Supabase MiniSocial (nếu có dữ liệu người chơi).
2. Vào Supabase project `minisocial` (KHÔNG PHẢI `gunny-java`) → SQL Editor → New query.
3. Mở `MiniSocial/deploy/UPDATE-v1.6.0.sql`, dán toàn bộ → Run. Query cập nhật bảng/vùng view cũ; không xóa tài khoản hiện có. Chạy lại có hỗ trợ `IF EXISTS`, nhưng chỉ nên chạy khi cần.
4. Giải nén bản PATCH. Copy NỘI DUNG thư mục `MiniSocial/` đè tương ứng vào thư mục GỐC repo GitHub `khang001108/MiniSocial` (không tạo thư mục `MiniSocial/MiniSocial/`). Commit/Push `main`.
5. Mở web Xubi → Servers → `minisocial-xubi`: giữ `server/Dockerfile`, Host `18087`, Container `8080`, TCP, Environment Supabase hiện tại.
6. Xubi → Deployments → chọn `minisocial-xubi` → Deploy → Docker/Portainer xem Running và log.
7. Mở `http://192.168.1.127:18087/health` (LAN) phải thấy `{"ok":true,"service":"MiniSocial"}`.
8. Cài JAR/JAD v1.6.0 mới trên Nokia/J2ME Loader. Đăng ký tài khoản 3 ô, đăng nhập, rồi thấy trang hoàn thiện hồ sơ. Tài khoản đã có vẫn đăng nhập và có thể chỉnh hồ sơ bằng phím 9.

## NHẬN DIỆN MODEL

- Đọc `System.getProperty("microedition.platform")`, so khớp một số mẫu Nokia như E72/E6/Asha 302; máy giả lập thường chỉ báo tên chung.
- Có thể sửa thủ công tên model trên màn hình thiết lập hồ sơ.
- Model đi theo từng yêu cầu bài đăng, bình luận, chat và upload ảnh. Dữ liệu này được client tự khai báo, có thể sai/giả mạo; không dùng để xác thực.
- Ảnh đại diện hiện lấy qua đường dẫn `file:///...`, JSR-75, JPEG/PNG tối đa 180KB; nên dùng ảnh 240×240. Chưa tích hợp chọn file bằng GUI hay chụp trực tiếp bằng camera.
- Để các người dùng từ 4G đăng nhập, HTTPS `minisocial.xubi.org` vẫn phải hoạt động; cần DNS và Caddy riêng, không dùng HTTP công khai.

## KẾT QUẢ KIỂM THỬ

- Java 17 backend build; Java ME CLDC class v45 build.
- Kiểm tra bốn độ phân giải 240x320, 320x240, 360x640, 640x480; 168 khung hình.
- Test mock Supabase: tên trùng, đăng ký mới, đăng nhập, form hồ sơ, upload avatar, nhãn thiết bị trên bài đăng, quên/đổi mật khẩu.
- CHƯA chạy migration trên Supabase production, CHƯA thử E72/E6/Asha 302 thật, CHƯA tự Deploy lên Pi 5.

# Cập nhật MiniSocial v1.5.0 trên Xubi Pi 5

1. Sao lưu repository và cấu hình Xubi nếu cần.
2. Giải nén gói PATCH, chép vào thư mục gốc của `khang001108/MiniSocial`, giữ `server/Dockerfile` và cấu trúc thư mục hiện tại. Commit/Push nhánh `main`.
3. Trong Supabase MiniSocial, mở **Authentication → Email Templates → Reset Password**; thay template để chứa `{{ .Token }}`. Lưu lại. (Rất quan trọng: nếu chỉ có link, Nokia không có OTP để nhập.)
4. Xubi → Deployments → minisocial-xubi → Deploy. Giữ `18087:8080`, Environment Supabase không đổi. Không có SQL migration mới.
5. Thử `/health`, tải JAR/JAD v1.5.0 ở trang `/download/`.
6. Thử quên mật khẩu với email thử nghiệm do bạn kiểm soát: gửi mã → nhập OTP → đổi mật khẩu → đăng nhập mới; thử Đổi MK ở hồ sơ (phím 8).

Không chia sẻ OTP, API key hay mật khẩu. Hướng dẫn đầy đủ: `docs/PASSWORD-OTP-v1.5.0.md`.

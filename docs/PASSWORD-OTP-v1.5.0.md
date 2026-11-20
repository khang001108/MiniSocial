# MiniSocial v1.5.0 — Thiết lập OTP và đổi mật khẩu

## 1. Cập nhật mã nguồn / Docker qua Xubi

1. Đưa toàn bộ nội dung bản PATCH vào repository `khang001108/MiniSocial` (ghi đè các tệp cùng đường dẫn), Commit lên nhánh `main`.
2. Trong Xubi → Servers chọn `minisocial-xubi`, giữ `server/Dockerfile`, Host `18087`, Container `8080`.
3. Xubi → Deployments → `minisocial-xubi` → Deploy. Giữ Environment Supabase hiện có; không cần chạy SQL migration.
4. Kiểm tra `https://minisocial.xubi.org/health` trả `{"ok":true,"service":"MiniSocial"}`. Đường dẫn `/health` chỉ xác nhận backend, không xác nhận email đã gửi.
5. Dùng `/download/MiniSocial-Responsive.jar` và `/download/MiniSocial-Responsive.jad` để cài JAR/JAD v1.5.0. Gỡ bản cũ nếu máy Nokia vẫn dùng cache JAR/JAD.

## 2. BẮT BUỘC: Supabase Recovery email phải gửi mã OTP

Trên **Supabase project MiniSocial**, vào **Authentication → Email Templates → Reset Password / Recovery**. Đổi nội dung email khôi phục thành:

```html
<h2>Khôi phục mật khẩu MiniSocial</h2>
<p>Mã OTP của bạn: <strong>{{ .Token }}</strong></p>
<p>Nhập mã 6 chữ số này trong ứng dụng MiniSocial để đặt mật khẩu mới.</p>
<p>Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này. Không chia sẻ mã OTP.</p>
```

Lưu template. `{{ .Token }}` là biến Supabase Auth sinh ra (không thay bằng mã cố định). Nếu bạn vẫn dùng template mặc định chỉ chứa liên kết `{{ .ConfirmationURL }}`, ứng dụng Nokia sẽ không có mã để nhập.

**Email gửi thực tế:** Supabase Auth cần có dịch vụ gửi thư hoạt động. Mặc định dịch vụ gửi thư thử nghiệm của Supabase có giới hạn thấp (thường 2 email/giờ cho project và có thể hạn chế người nhận). Muốn dùng cho nhiều người dùng cần cấu hình **Custom SMTP** (SendGrid/Resend/SMTP hợp lệ) và kiểm tra giới hạn hiện hành trong Dashboard. Không cài SMTP vào Docker MiniSocial; Supabase gửi email.

Email đăng ký: nếu muốn không cần xác nhận, tắt `Confirm email` trong **Authentication → Providers → Email**. Việc này KHÔNG chứng minh email thuộc về người đăng ký; đặt lại mật khẩu qua OTP vẫn cần truy cập email thật. Nếu cần bảo vệ danh tính người dùng, cân nhắc xác minh email khi đăng ký.

## 3. Dùng trên Nokia E72 / E6

**Quên mật khẩu** tại màn hình đăng nhập → chọn "Quên mật khẩu?" cạnh chữ "Mật khẩu" → nhập Email → "Gửi mã OTP qua email" → kiểm tra hộp thư Inbox/Spam → nhập OTP 6 số, mật khẩu mới và xác nhận → "Xác nhận OTP và đặt mật khẩu". Mã OTP bị vô hiệu/ hết hạn do Supabase quy định; nếu mã quá hạn, yêu cầu mã mới.

**Đổi mật khẩu** sau đăng nhập → Hồ sơ cá nhân → phím **8** (Đổi MK), hoặc bấm vào dòng này bằng cảm ứng → nhập mật khẩu hiện tại, mật khẩu mới và xác nhận → bấm "Đổi mật khẩu" → đăng nhập lại.

Phím ↑/↓ điều hướng; OK mở từng ô để nhập bằng bàn phím hệ thống. Password không được lưu trong RMS. Khi bấm Quay lại, các ô nhạy cảm bị xóa.

## 4. Bảo mật / giới hạn

- OTP được Supabase phát hành và xác minh (`/auth/v1/recover`, `/auth/v1/verify`, `/auth/v1/user`). Pi không lưu OTP và không có khóa bí mật trong JAR/JAD.
- Backend yêu cầu OTP chính xác để reset; đổi mật khẩu cần phiên đăng nhập hợp lệ và mật khẩu hiện tại.
- Cooldown gửi mã theo email 60 giây; 6 lần thử OTP/email trong 10 phút; thêm giới hạn rộng theo IP vì Caddy có thể che IP thật. Supabase có giới hạn riêng.
- Đổi mật khẩu không đảm bảo đăng xuất tất cả thiết bị đang đăng nhập; phải cấu hình thêm thu hồi phiên nếu yêu cầu.
- Nếu Supabase bật `Secure password change` hoặc yêu cầu re-authentication bổ sung, có thể cần điều chỉnh luồng bằng OTP thay vì chỉ mật khẩu cũ.
- Không mở Port 18087 qua NAT cho công khai HTTP, ưu tiên HTTPS qua Caddy và domain.
- Chưa thử gửi email OTP bằng Supabase thật hoặc thiết bị Nokia thật; các bài kiểm thử dùng mock Supabase.

# MiniSocial v1.7.0 — Đăng ký bằng tên tài khoản, web admin, giới hạn dữ liệu

## Đã triển khai trong mã nguồn

- Java ME đăng ký **Tên tài khoản + Mật khẩu + Nhập lại mật khẩu**, không nhập email, không xác nhận OTP. Đăng nhập bằng tên tài khoản và mật khẩu. Tài khoản cũ vẫn có thể đăng nhập bằng email cũ, sau đó thiết lập tên hiển thị, avatar và nhận diện model điện thoại như v1.6.0.
- Backend Java tạo người dùng bằng **Supabase Auth Admin API** (`POST /auth/v1/admin/users`), `email_confirm=true` và email ảo nội bộ `handle@noemail.minisocial.invalid`. Không gửi thư xác nhận. Khóa secret chỉ ở máy chủ, không nằm trong JAR/web.
- **Web admin tại `https://minisocial.xubi.org/admin`**: xác thực riêng bằng `MINISOCIAL_ADMIN_USER`/`MINISOCIAL_ADMIN_PASSWORD`, phiên 2 giờ qua cookie Secure/HttpOnly/SameSite, CSRF, giới hạn đăng nhập sai; tìm tài khoản, khóa/mở, đặt lại mật khẩu, xóa vĩnh viễn, chỉnh quota tổng và quota từng người, bật/tắt đăng ký.
- Quên mật khẩu: **liên hệ admin**, vì tài khoản mới không có email/số điện thoại thật. Khi admin đặt mật khẩu mới, backend chặn các phiên cũ bằng mốc thời gian JWT.
- SQL trigger đảm bảo hạn mức nội dung gồm **tin nhắn, bài viết, bình luận, ảnh**. Mặc định 512 MiB tổng và 50 MiB/tài khoản, admin có thể đổi. Chỉ tính payload có kiểm soát, không tính row/index, WAL, PostgreSQL internals, Storage overhead hoặc băng thông. Quota gói Supabase Free vẫn áp dụng độc lập.

## Triển khai trên Pi 5 và Xubi — đúng thứ tự

1. **Sao lưu Supabase MiniSocial trước** (database + auth + ảnh). Không áp dụng SQL lên Supabase của Gunny. Đảm bảo ứng dụng trước đó đã được nâng lên schema **v1.6.0** bằng `deploy/UPDATE-v1.6.0.sql`.
2. Mở **Supabase MiniSocial → SQL Editor**, dán toàn bộ `deploy/UPDATE-v1.7.0-ADMIN.sql` rồi Run. SQL này **không xóa tài khoản**, bổ sung cột và trigger; có thể gây khóa ghi ngắn trong lúc migration. Với dữ liệu lớn, lên kế hoạch downtime.
3. Đưa toàn bộ source MiniSocial v1.7.0 lên repo `khang001108/MiniSocial` (không đưa file `.env` thực tế lên GitHub). Trong Xubi chọn deployment **MiniSocial**, build context **repo root**, Dockerfile **`server/Dockerfile`**, cổng **host 18087 → container 8080**.
4. Trong **Xubi → MiniSocial → Environment** giữ `PORT`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY` hiện có và **thêm**:
   ```env
   MINISOCIAL_ADMIN_USER=admin
   MINISOCIAL_ADMIN_PASSWORD=TAO_MAT_KHAU_NGAU_NHIEN_DAI_IT_NHAT_16_KY_TU
   ```
   **Không đăng mật khẩu admin lên chat hoặc GitHub.** Mật khẩu nằm trong biến môi trường server, không nằm trong JAR.
5. Deploy/rebuild MiniSocial trên Xubi. Xác nhận `/health` trả `{"ok":true,"service":"MiniSocial"}`. Vào **`https://minisocial.xubi.org/admin`**, đăng nhập bằng tài khoản admin riêng, kiểm tra hạn mức. **Admin chỉ đăng nhập qua HTTPS** vì cookie luôn dùng `Secure`.
6. Cài **JAR/JAD v1.7.0**. Thử đăng ký `khazg_test` (không email/OTP), đăng nhập, hoàn thiện hồ sơ, chat, rồi thử khóa/mở, đặt lại mật khẩu trong web admin. Kiểm tra tài khoản email cũ vẫn đăng nhập được bằng email cũ.

## Điều kiện bảo mật

- Giới hạn truy cập `/admin` qua Tailscale hoặc VPN ở Caddy/firewall; không mở cổng Xubi admin 8787 ra Internet. Dùng mật khẩu mạnh >= 16 ký tự và đổi định kỳ. Chỉ dùng HTTPS, không đăng nhập admin bằng HTTP LAN vì Secure Cookie sẽ không hoạt động.
- **Hạn chế tạo tài khoản ảo:** do bỏ OTP, backend giới hạn đăng ký theo IP (20 lượt/10 phút; sau Caddy có thể nhiều người chung một địa chỉ) và admin có thể đóng đăng ký.
- **Xóa vĩnh viễn:** xóa tài khoản khỏi Supabase Auth (cascade tới `profiles`, bài/tin nhắn), sau đó xóa file Storage của chủ tài khoản; có thể cần làm sạch tệp mồ côi nếu một bước lỗi. Hãy backup trước.
- **Lưu ý:** Email ảo `.invalid` được dùng làm định danh kỹ thuật cho Auth; Supabase vẫn có thể thay đổi quy tắc xác thực tên miền. Phải kiểm thử 1 tài khoản mới trên Supabase thật trước khi mở đăng ký rộng rãi.
- **Ảnh cũ:** migration ước lượng kích thước từ `storage.objects.metadata.size`. Các file cũ thiếu metadata sẽ không được tính chính xác trong `used_bytes`; cần đối soát dung lượng thật với dashboard Supabase.
- **Phạm vi thử nghiệm:** backend + JAR đã biên dịch và qua mock integration, **chưa thử Supabase production, Nokia thật, Caddy/Tailscale trên Pi 5**. Chưa triển khai tự động.

## Khi cần khôi phục phiên bản cũ

Lưu trước ZIP/commit hiện hành; rollback **mã nguồn và Docker image** trên Xubi. Không tự chạy `DROP` bảng/trigger hoặc rollback SQL bằng cách xóa cột khi vẫn còn tài khoản v1.7. Có thể tắt đăng ký trong web admin trong lúc xử lý. SQL migration v1.7 chủ yếu thêm cấu trúc, không xóa dữ liệu cũ.

# MiniSocial v1.8.0 — Lucide-style UI, 3 tab và Tùy chọn Nokia

## Có gì mới

- 3 tab chính: **Trang chủ**, **Chat**, **Bạn bè**. Màn hình cá nhân vẫn còn nhưng chuyển vào **Tùy chọn → Cá nhân**.
- Các icon dạng nét mảnh vẽ trực tiếp bằng `javax.microedition.lcdui.Graphics` (`drawLine`, `drawArc`, `drawRoundRect`) lấy cảm hứng từ phong cách Lucide, **không tải SVG/PNG trên Nokia**, không kéo thư viện JS/web vào JAR.
- Phím mềm trái (`-6` trên máy kiểm thử):
  - Trang chủ/Bạn bè/hộp thư Chat chưa mở cuộc hội thoại: mở menu 4 mục.
  - Trong cuộc hội thoại: mở ô nhập **Gửi tin nhắn**; xác nhận bằng phím OK native.
  - Cài đặt/Lịch sử/Dung lượng: **Chọn** thao tác đang tô sáng.
- Phím mềm phải (`-7`): **Trở về**; tại Trang chủ hiển thị xác nhận **Thoát**; tại Đăng nhập thoát ứng dụng.
- Menu góc trái: **Cá nhân / Cài đặt / Lịch sử / Dung lượng**.
- Cài đặt: bật/tắt thông báo **bên trong ứng dụng** (lời mời kết bạn khi mở tab), theme Sáng/Tối lưu RMS, đổi tên hiển thị, đổi mật khẩu, xóa tài khoản và đăng xuất. Chưa có push notification qua mạng khi app tắt.
- Lịch sử: bài đã thả like, bài do mình đăng (tối đa 30 bài mỗi nhóm/truy vấn).
- Dung lượng: hiển thị số byte đã dùng và hạn mức tài khoản Supabase, thông thường 0/50 MB. Giới hạn này là lượng dữ liệu nội dung MiniSocial v1.7 theo dõi; **không phải tổng toàn bộ dung lượng project Supabase**.
- Xóa cache: xóa ảnh tạm trong RAM điện thoại, **không xóa phiên đăng nhập**.
- Xóa sạch ảnh: xóa toàn bộ ảnh **thuộc người dùng hiện tại** trên Supabase, có xác nhận. Không xóa bài chữ hay tin nhắn chữ; các ảnh đã gắn vào bài/chat sẽ biến mất, avatar cũng bị xóa.
- Xóa tài khoản: yêu cầu **mật khẩu hiện tại và xác nhận riêng**, xóa vĩnh viễn tài khoản và dữ liệu theo quan hệ DB. **Không khôi phục được**.
- Chat là tab riêng: khi chưa chọn người chat sẽ hiển thị danh sách bạn bè. Chọn bạn để vào cuộc hội thoại.

## Điều kiện Supabase

Bản này tiếp tục sử dụng Supabase và backend xác thực bằng tên tài khoản của v1.7.

- **Không cần chạy SQL v1.8 mới** nếu đã triển khai `deploy/UPDATE-v1.6.0.sql` và `deploy/UPDATE-v1.7.0-ADMIN.sql`.
- Nếu server hiện đang dùng schema v1.5 hoặc v1.6, phải **sao lưu Supabase và chạy migration v1.7** trước khi dùng v1.8. KHÔNG chạy trên project Gunny.
- Không cần bật xác nhận email/OTP cho tài khoản mới. Giữ nguyên secret key trong Environment Xubi, không đưa vào JAR/JAD.

## Cách cập nhật Xubi

1. Sao lưu dữ liệu và đảm bảo Supabase MiniSocial đã chạy SQL đến v1.7.
2. Giải nén `MiniSocial-v1.8.0-PATCH-GitHub.zip`, copy các thư mục/tệp bên trong `MiniSocial/` đè vào **gốc repository** MiniSocial của bạn (không lồng thêm một cấp MiniSocial).
3. Commit/Push repo `khang001108/MiniSocial` lên branch đang deploy.
4. Xubi → MiniSocial → Deploy lại Docker (Dockerfile: `server/Dockerfile`, context: gốc repo, host `18087` → container `8080`).
5. Truy cập `https://minisocial.xubi.org/health` và trang `/admin`; kiểm tra lại tải JAR/JAD bằng domain trên hoặc tải JAR trực tiếp từ file trong gói.
6. Trên Nokia, cài JAR/JAD v1.8.0, đăng nhập thử, kiểm tra phím mềm, 3 tab, menu và dung lượng.

## API backend mới (đều yêu cầu Bearer token của chính tài khoản)

- `GET /m/history?type=liked` — các bài đã like, tối đa 30.
- `GET /m/history?type=posted` — các bài của chính mình, tối đa 30.
- `GET /m/storage` — `OK|used_bytes|limit_bytes`.
- `POST /m/storage/clear` với `confirm=CLEAR` — xóa ảnh thuộc người đang đăng nhập, **không xóa bài viết/tin nhắn văn bản**.
- `POST /m/account/delete` với `password=<current>&confirm=DELETE` — xác minh mật khẩu trước khi xóa tài khoản.
- `POST /m/profile` với `name=...` hoặc `bio=...` — cập nhật từng trường, không xóa trường còn lại.

**Cảnh báo:** Không chạy thử thao tác Xóa tài khoản hoặc Xóa sạch ảnh trên tài khoản có dữ liệu quan trọng. Sao lưu trước và thử trên tài khoản phụ.

## Kiểm thử đã chạy

- Biên dịch Java ME CLDC1.1/MIDP2 JAR/JAD trên JDK; bản build sử dụng bộ chuyển StackMap nội bộ, **chưa xác nhận trên Nokia vật lý**.
- Biên dịch backend Java 17.
- `ResponsiveLayoutSmoke`: 168 trường hợp qua 4 kích thước 240×320, 320×240, 360×640, 640×480.
- `LucideMenuSmoke`: 36 màn hình mới, menu, theme, phím mềm.
- `LoginFormSmoke`, `RegistrationFormSmoke`, `OnboardingSmoke`, `PasswordFormsSmoke`, `ServerSelectionSmoke`.
- `test_admin_username.py`, `test_onboarding_device.py`: test API với **Supabase giả lập**, không gọi production.

**Chưa được kiểm thử:** TLS cũ của Nokia trên domain HTTPS thực tế, hiện diện phần cứng thông báo, thiết bị Nokia thật, Supabase production và Docker trên Pi 5.

# MiniSocial v1.1.0 - trang web tải JAR/JAD cho Xubi trên Pi 5

## Cài vào source GitHub hiện tại

Trang tải được tích hợp vào chính Java backend. Không tạo máy chủ mới, không đổi cổng và không chạm tới Gunny.

CÁCH 1 (dễ quản lý nhất): lấy thư mục MiniSocial trong ZIP này làm bản source dự án, copy các file thay đổi vào repo MiniSocial bằng VS Code/GitHub Desktop rồi commit/push nhánh `main`:

- `server/src/minisocial/Server.java` (đã thêm web và 2 API tải tệp)
- `server/Dockerfile` (đã copy web + JAR/JAD vào container)
- `web/index.html` (trang tải responsive mới)
- `.dockerignore` (loại .env và .git khỏi Docker build)
- `deploy/Caddyfile.example` (đã trỏ về Host Port 18087)

Hoặc dùng ZIP PATCH riêng: đưa đúng 5 đường dẫn này vào repo, không xóa các file còn lại.

## Deploy trong Xubi

1. Mở Xubi → Servers → `minisocial-xubi`.
2. Giữ GitHub `https://github.com/khang001108/MiniSocial.git`, nhánh `main`.
3. Giữ Dockerfile `server/Dockerfile`.
4. Giữ ánh xạ cổng Host `18087` → Container `8080` (TCP).
5. Environment: giữ `PORT=8080`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`. Không cần nhập lại các key đã lưu.
6. Vào Deployments → `minisocial-xubi` → **Deploy**.
7. Khi trạng thái container là Running, mở:
   - Web: http://192.168.1.127:18087/
   - JAR: http://192.168.1.127:18087/download/MiniSocial-Responsive.jar
   - JAD: http://192.168.1.127:18087/download/MiniSocial-Responsive.jad
   - Health: http://192.168.1.127:18087/health

Nếu IP Pi thay đổi, thay `192.168.1.127` bằng IPv4 LAN hiện tại.

## Đưa ra Internet

Cấu hình `minisocial.xubi.org` trên nhà cung cấp DDNS/DNS trỏ về Public IPv4; trong Xubi Network/NAT/Caddy tạo website proxy đến `127.0.0.1:18087`. Chỉ cần NAT WAN TCP 443 tới Pi khi HTTPS Caddy đang hoạt động; **không cần NAT port 18087**. Đừng công khai HTTP 18087 vì API có đăng nhập; nên xóa rule NAT 18087 nếu đã thêm và dùng HTTPS 443.

Trang tải sẽ có địa chỉ: https://minisocial.xubi.org/

JAR/JAD trong source này vẫn trỏ API về `https://minisocial.xubi.org` (giữ nguyên như gói gốc). Nếu chỉ mở trang HTTP LAN, bạn vẫn tải JAR/JAD được, nhưng tính năng đăng nhập/chat trên Nokia chưa kết nối được trước khi cấu hình DNS/Caddy và TLS tương thích thiết bị.

## Kiểm thử đã thực hiện

- Biên dịch Java backend bằng `javac --release 17`: PASS.
- GET `/` HTML 200; GET `/health` JSON 200; GET `/m/feed` không token trả 401: PASS.
- GET / HEAD JAR và JAD trả đúng MIME, kích thước; SHA256 JAR tải ra khớp file gốc: PASS.
- Chưa kiểm thử Docker build trên Pi 5 hoặc tải/đăng ký thực tế trên Nokia E72/E6.

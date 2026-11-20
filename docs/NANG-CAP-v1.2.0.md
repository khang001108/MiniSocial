# MiniSocial 1.2.0 — giao diện và máy chủ

### Giao diện mới
- Màu chính xanh lá, xanh ngọc và điểm nhấn xanh biển; vùng được chọn viền xanh dễ nhìn.
- Màn hình đăng nhập có 3 mục: Đăng nhập, Đăng ký, Máy chủ.
- Phím ↑/↓ để chuyển mục, OK hoặc phím Enter để mở. Trên trang chủ đăng nhập vẫn dùng 1/2/3.
- Khi đã đăng nhập, phím ←/→ chọn tab Bảng tin/Hồ sơ/Bạn bè/Chat; OK để mở.

### Chọn máy chủ như Gunny (nhưng dùng HTTP/HTTPS)
- **Internet / 4G:** `https://minisocial.xubi.org`.
- **Wi-Fi cùng Pi:** `http://192.168.1.127:18087` (sửa nếu IP Pi thay đổi).
- **Tùy chỉnh:** địa chỉ API khác, phải là HTTPS khi truy cập Internet.
- Trong màn Máy chủ: ↑/↓ đổi mục, **OK** chọn, **# hoặc softkey trái** sửa địa chỉ, **5** kiểm tra `/health`, **0** quay về. Nhấn chạm vùng **Sửa** bên phải để nhập URL.
- Từ bảng tin nhấn **0** hoặc chạm thanh tiêu đề MiniSocial để mở máy chủ.
- Địa chỉ/nguồn đang dùng được lưu bằng RMS. Khi chuyển địa chỉ, app xóa session cũ (cần đăng nhập lại).
- MiniSocial **không dùng** `socket://gunnyjava.xubi.org:22123` vì backend phục vụ HTTP/HTTPS trên cổng web.

### Triển khai từ GitHub lên Xubi
1. Chép `client/src/minisocial/MiniSocialMidlet.java`, `client/dist/MiniSocial-Responsive.jar`, `client/dist/MiniSocial-Responsive.jad`, `client/build_j2me.py` lên GitHub đúng vị trí; commit vào nhánh `main`.
2. Đảm bảo Dockerfile của MiniSocial có `COPY client/dist/MiniSocial-Responsive.jar /app/download/` và dòng tương tự cho JAD như bản web download trước đó.
3. Mở Xubi → Deployments → `minisocial-xubi` → **Deploy**.
4. Kiểm tra: `/health`, sau đó tải JAR/JAD qua `/download/MiniSocial-Responsive.jar` và `/download/MiniSocial-Responsive.jad`.
5. Trên Nokia hoặc emulator, cập nhật ứng dụng mới (v1.2.0). Có thể cần gỡ bản cũ nếu điện thoại không cập nhật, khi đó RMS cũ có thể bị mất.

Lưu ý: Bản build Java ME dùng bộ chuyển đổi stack map experimental từ v1.1.0; chưa chứng nhận trên Nokia vật lý, chưa kiểm chứng TLS của Symbian và Supabase thật.

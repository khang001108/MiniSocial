# MiniSocial v1.4.0 — cập nhật giao diện trên GitHub/Xubi

1. Dùng ZIP PATCH: mở ZIP, chép nội dung **bên trong** thư mục `MiniSocial/` đè lên **thư mục gốc** của repository `khang001108/MiniSocial`. Không tạo lồng `MiniSocial/MiniSocial`.
2. Giữ nguyên `server/Dockerfile`, `server/src`, `deploy/supabase.sql`, `.env` và các secrets trong Xubi.
3. Commit và Push các thay đổi lên nhánh `main`.
4. Xubi → Servers → `minisocial-xubi`: `Dockerfile` = `server/Dockerfile`, Host 18087 → Container 8080/TCP.
5. Xubi → Deployments → `minisocial-xubi` → Deploy.
6. Test `http://192.168.1.127:18087/health`, sau đó vào `/download/MiniSocial-Responsive.jar` và JAD.
7. Gỡ ứng dụng cũ trên Nokia (sao lưu dữ liệu nếu cần) rồi cài lại JAR/JAD v1.4.0.

Lưu ý: màn hình đăng ký là form giữ 5 trường; nhấn OK ở một trường có thể mở editor gốc của Nokia để nhập dữ liệu, nhưng editor không tự bật tiếp sang trường khác. Nokia E72 có thể gõ trực tiếp bằng bàn phím QWERTY khi trường đang được chọn.

Chưa kiểm thử trên Nokia thật hay Supabase production.

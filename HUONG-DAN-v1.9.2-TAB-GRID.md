# MiniSocial v1.9.2 — Tab Grid & Contrast Fix

## Lỗi nền sáng đã được sửa
`SocialCanvas.grad()` trước đây tô các dải màu lặp vào cùng vị trí `y`, chỉ làm đậm 1–2 pixel ở phía trên. Bản này tô tại `y + j` nên toàn bộ header và footer xanh đậm trong chế độ sáng. Chế độ tối giữ màu xanh navy đồng nhất. Chữ softkey trắng vì thế có tương phản rõ ràng.

## Menu Tab: lưới 2 × 3 từ dưới lên
Nhấn phím mềm trái **Tab** để mở bảng 6 ô:

| Cá nhân | Bạn bè |
|:---:|:---:|
| **Tải lại** | **Lịch sử** |
| **Cài đặt** | **Bộ nhớ** |

- Trái/Phải: chuyển cột.
- Lên/Xuống: chuyển hàng, quấn vòng.
- OK hoặc phím mềm trái: mở ô đang chọn.
- Phím mềm phải, phím `0`: đóng menu.
- Phím `1`–`6`: truy cập nhanh ô tương ứng.
- Cảm ứng: chạm đúng ô để mở, chạm vùng bên trên bảng để đóng.

Vị trí và kích thước tự tính theo Canvas; không cố định theo model. Test các chế độ 240×320, 320×240, 360×640, 640×360, 480×640 và 640×480.

## Các cập nhật phụ
- Icon Thông báo sử dụng asset `bell.png` đã có, thay vì tên asset không tồn tại `notify.png`.
- Lớp thông báo tạm thời không chèn lên lưới khi menu Tab đang mở.
- Giữ nguyên backend v1.9.0 và toàn bộ API, Supabase, Docker, địa chỉ/cổng mạng.

## Xubi / Docker
1. Giải nén file **Full Source** và đưa nội dung thư mục `MiniSocial/` vào repo MiniSocial hiện tại (không thay file `.env` production).
2. Commit/push mã nguồn lên GitHub.
3. Trong Xubi, redeploy container. `server/Dockerfile` đã COPY tự động `client/dist/MiniSocial-Responsive.jar` và `.jad` vào `/app/download/`.
4. Tải bản mới tại đường dẫn `/download/MiniSocial-Responsive.jad` hoặc `/download/MiniSocial-Responsive.jar` sau khi deploy.
5. Nếu website vẫn trả bản cũ, kiểm tra Docker image thực tế đã được rebuild, không chỉ restart container.

## Kiểm tra
- Build Java ME thành công (JAR xấp xỉ 498 KB).
- PASS: 6 độ phân giải, 36 hitbox lưới, che phủ nền header/footer, phím điều hướng và cảm ứng.
- PASS: bộ smoke test cũ về login, đăng ký, onboarding, chat adaptive và orientation.
- **Chưa chạy thử trên điện thoại Nokia vật lý**. Hãy kiểm tra text layout/phông chữ thực trên E72/E6/Asha vì hệ thống font máy có thể khác giả lập.

Lưu ý công cụ `client/build_j2me.py` của repo chuyển bytecode để chạy CLDC theo cơ chế thử nghiệm; để phát hành ổn định, kiểm tra JAR trên emulator hoặc thiết bị thật, và nên dùng preverifier chuẩn của WTK khi có thể.

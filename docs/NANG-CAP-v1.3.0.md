# MiniSocial v1.3.0 – Màn hình đăng nhập hiện đại

- Trang đầu tiên: khung đăng nhập phong cách web xanh lá/trắng, thay danh sách 3 ô cũ.
- Có tiêu đề, ô tài khoản/email và mật khẩu, nút Đăng nhập lớn.
- Ba liên kết nhỏ: Máy chủ, Tạo tài khoản, Thông tin.
- ↑/↓ chuyển vùng, OK chọn, bấm chạm trên máy cảm ứng.
- Nhập liệu qua TextBox của Java ME, không phải HTML form.
- Không lưu mật khẩu vào RMS; đổi máy chủ thì xóa mật khẩu.
- Hỗ trợ 240×320, 320×240, 360×640, 640×480 ở mức layout.
- `client/dist` chứa JAR/JAD 1.3.0; trang tải `web/index.html` cập nhật nhãn phiên bản.
- CI cập nhật lại đường dẫn tên JAR đang sử dụng.

## Triển khai Xubi

Đưa các file sửa vào GitHub MiniSocial trên nhánh main rồi Deploy server minisocial-xubi trong Xubi. Host/Container vẫn 18087 → 8080. Không chỉnh NAT hoặc biến Supabase.

**Kiểm thử:** các bài desktop smoke test và kiểm tra định dạng bytecode; chưa xác nhận chạy trên Nokia thực và TLS trên Symbian.

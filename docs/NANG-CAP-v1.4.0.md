# MiniSocial Java ME v1.4.0 — Form đăng ký và giao diện đăng nhập

- Form đăng ký trong Canvas gồm 5 trường, xác nhận mật khẩu, nút Đăng ký và liên kết trở lại.
- Không còn chuỗi TextBox nhập tuần tự; chỉ mở editor cho ô được chọn khi nhấn OK.
- Phím lên/xuống, FIRE/OK, cảm ứng; cho phép gõ trực tiếp phím QWERTY vào trường đang chọn.
- Bố cục đăng nhập rộng hơn trên màn hình lớn; form đăng ký cuộn theo focus trên 320x240.
- Lỗi kiểm tra email, handle, mật khẩu xuất hiện trong form; không gửi đăng ký sai định dạng.
- Không đụng đến backend/server Supabase hoặc cấu hình Xubi.
- Mã Java ME được biên dịch bằng chain offline thử nghiệm CLDC, cần kiểm tra trên Nokia thật.

# MiniSocial 1.8.2 — Responsive Portrait / Landscape

## Thay đổi đã triển khai

Từ v1.8.1 UI v2.0, bản 1.8.2 sửa trực tiếp Java ME Canvas của client. **Không đổi backend Java, API, Supabase, database hay cấu hình mạng**.

- Mỗi lần render đều lấy `getWidth()` / `getHeight()` của Canvas. Khi máy hỗ trợ đổi hướng, `sizeChanged(w, h)` cập nhật kích thước và vẽ lại, giữ nguyên tab, người đang chat và lựa chọn hiện tại.
- Các kích thước hỗ trợ: **240×320, 320×240, 360×640, 640×360, 480×640, 640×480**. Một số kích thước tương tự cũng được xử lý tự động, nhưng chưa được thử trên máy thật.
- PNG chọn tự động theo **cạnh ngắn của màn**: dưới 340 px = `assets/e72/`; 340–439 px = `assets/wide/`; từ 440 px = `assets/e6/`. Chế độ ngang 640×360 và dọc 360×640 cùng dùng bộ `wide`.
- Bổ sung ảnh `light/cover_portrait.png` và `dark/cover_portrait.png` cho từng nhóm E72/Wide/E6, giúp ảnh bìa hồ sơ không vượt chiều ngang ở màn dọc.
- Màn ngang chiều cao <=400 px dùng thanh điều hướng thấp hơn, thẻ bài viết/bạn bè nhỏ gọn hơn, dành thêm diện tích nội dung.
- Tin nhắn có **bề rộng và chiều cao tự co theo chữ**, xếp từ đáy khung chat lên, cắt số dòng để tin nhắn mới nhất luôn vừa màn. Nút gửi, icon đính kèm và vùng chạm ô nhập dùng cùng kích thước đã tính.
- Ảnh xem toàn màn được thu nhỏ khi xoay sang hướng màn hình hẹp hơn (không tự phóng to lại nếu xoay ngược). Hạn chế ảnh lớn để bảo vệ RAM của máy Java cũ.
- Font dùng cỡ `Font.SIZE_*` do MIDP cung cấp, không dùng phép scale bitmap toàn màn; text, nút và khung được bố cục lại độc lập.

## Bản đồ kích thước/asset

| Thiết bị / chế độ | Canvas | PNG runtime |
| --- | --- | --- |
| Nokia màn nhỏ dọc | 240×320 | `e72` + ảnh bìa portrait |
| Nokia E72 ngang | 320×240 | `e72` |
| Wide dọc | 360×640 | `wide` + ảnh bìa portrait |
| Wide ngang | 640×360 | `wide` |
| E6/thiết bị tương tự dọc | 480×640 | `e6` + ảnh bìa portrait |
| Nokia E6 ngang | 640×480 | `e6` |

**Lưu ý:** Đây là responsive tự động theo kích thước mà máy/hệ điều hành cung cấp. Không phải tính năng ép Nokia E72 xoay ngang/dọc: những máy có bàn phím và màn hình cố định sẽ giữ hướng mặc định.

## Build và cài đặt

- JAR/JAD đã build sẵn nằm ở `client/dist/`; giữ **hai file cùng thư mục** nếu tải qua JAD.
- Build lại: `python3 client/build_j2me.py` (JDK 17+ và Python 3).
- Nếu tái tạo PNG runtime: giải nén `design/MiniSocial_UI_Assets_JavaME_v2_Updated.zip`, sau đó chạy `python3 client/prepare_ui_v2.py <đường_dẫn_thư_mục_giải_nén>` và `python3 client/build_j2me.py`. Script đã bổ sung xử lý `cover_portrait.png`.
- Cập nhật repo chạy trên Xubi: **sao lưu trước**, chép các thư mục bên trong `MiniSocial/` vào gốc repo, commit/push, triển khai lại Docker. Trang web sẽ phục vụ phiên bản 1.8.2 tại `/download/MiniSocial-Responsive.jar` và `.jad`. Không cần chạy SQL mới.

## Phạm vi test

- Build client: thành công.
- `ResponsiveLayoutSmoke`: 210 trường hợp layout.
- `LucideMenuSmoke`: 45 trường hợp + phím Nokia và chế độ tối/sáng.
- `OrientationSmoke`: cùng một Canvas xoay qua 9 trạng thái khung hình (320×240, 240×320, 640×360, 360×640, 640×480, 480×640 và ngược lại), giữ tab/chat và kiểm lỗi vẽ vượt khung.
- Smoke test form đăng nhập, đăng ký, mật khẩu, onboarding, chọn máy chủ: qua.
- Đã đối chiếu JAD/JAR, các đường dẫn PNG trong JAR và các phiên bản.

**Chưa kiểm thử trên thiết bị Nokia thật**, trình xác thực CLDC, HTTPS/certificate trên Symbian, hoặc việc hệ điều hành thực tế có phát sự kiện `sizeChanged` khi xoay máy. Bộ chuyển bytecode/preverify tích hợp của dự án là thử nghiệm, chưa thay thế WTK chuẩn.

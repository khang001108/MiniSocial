# MiniSocial 1.0 line-protocol reference

This API is intentionally tiny for Java ME (no JSON parser needed on the handset). All POST forms except `/m/upload` use `Content-Type: application/x-www-form-urlencoded; charset=UTF-8`.

- `GET /health` → JSON (`{"ok":true,...}`), no authorization.
- `POST /m/register` → `handle, email, password`, no authorization.
- `POST /m/login` → `email, password`, returns `OK|user_id|supabase_access_token`.
- All other `/m/` routes require header `Authorization: Bearer <supabase_access_token>`.
- Response lines use `|`-separated, URL-encoded UTF-8 fields (space encoded as `+`, literal pipe as `%7C`); row 1 of a collection is `OK`, one subsequent line per record.
- Errors use HTTP 4xx or 5xx with `ERR|url_encoded_message`.

## Feed & profiles

- `GET /m/feed` → `post_id|author_id|display_name|created_at|likes_count|comments_count|body|media_id|device_model|avatar_media_id` (up to 15 newest).
- `GET /m/profile?user=<uuid>` → `OK|user_id|display_name|handle|bio|created_at|avatar_media_id`.
- `GET /m/profileposts?user=<uuid>` → same feed rows, only user's posts.
- `POST /m/profile` → `name,bio` (own profile only).
- `POST /m/post` → `body`, optional `media` UUID (own public-post upload).
- `POST /m/like` → `post` UUID (toggle).
- `GET /m/comments?post=<uuid>` → `id|display_name|body|created_at|device_model`.
- `POST /m/comments` → `post,body`.

## Friends & chat

- `GET /m/friends` → `friend_id|display_name|handle` (accepted only).
- `GET /m/requests` → `requester_id|display_name|handle` (incoming pending).
- `GET /m/search?handle=<username>` → `OK|user_id|display_name|handle`.
- `POST /m/friend` → `handle`.
- `POST /m/accept` → `user` (incoming requester UUID).
- `GET /m/chat?user=<friend_uuid>` → `message_id|sender_id|body|media_id|created_at|device_model` (latest 40, chronological output).
- `POST /m/message` → `user`, optional `body`, optional `media` UUID (own chat upload).

## Images

- `POST /m/upload?scope=post` or `/m/upload?scope=chat&user=<friend_uuid>` with a **raw JPEG or PNG request body** and matching `Content-Type` header. Limit **180 KiB**, returns `OK|media_uuid`.
- `GET /m/photo?id=<media_uuid>` → image binary. Public-post images are visible to logged-in users only if attached to an existing post. Private-chat images are visible only to sender/recipient.
- Storage objects are private (`minisocial-private` bucket). The server uses the Supabase secret key only from its own environment.

Server access tokens are checked against `/auth/v1/user`, with at most 30 seconds of successful authentication cache for scalability. There is no refresh-token endpoint in this MVP; users log in again after expiry.


## Xác thực tài khoản v1.5.0 — OTP và đổi mật khẩu

Các endpoint nhận form `application/x-www-form-urlencoded` (URL-encoded UTF-8), trả `OK|...` hoặc `ERR|...` theo giao thức hiện có.

- `POST /m/password/recover` — `email=...`; KHÔNG cần đăng nhập; gửi yêu cầu Supabase Recovery email. Luôn dùng thông điệp chung để không lộ địa chỉ có tài khoản hay không; giới hạn tối thiểu 60 giây giữa hai lần cùng email.
- `POST /m/password/reset` — `email=...&otp=123456&password=...`; KHÔNG cần đăng nhập nhưng bắt buộc mã recovery OTP do Supabase cấp. Mã OTP được xác minh bằng `/auth/v1/verify` loại `recovery`; sau đó backend dùng JWT recovery để đặt mật khẩu mới. 6 lần thử/email/10 phút trên cùng một instance.
- `POST /m/password/change` — `current=...&password=...`; YÊU CẦU `Authorization: Bearer <access_token>`. Backend kiểm tra phiên JWT và mật khẩu hiện tại bằng Supabase Auth trước khi đổi. Ứng dụng đăng xuất sau khi thành công.

Không truyền mật khẩu qua HTTP công khai; chỉ dùng `https://minisocial.xubi.org` ngoài mạng LAN. Các giới hạn trong RAM reset khi server khởi động, ngoài giới hạn của Supabase. Không gửi/ghi log email, OTP, password hoặc Supabase secret key.


## v1.6.0 — onboarding và device attribution

- `POST /m/register` — `handle,email,password` (không gửi `name`). Handle trùng trả HTTP 409 cùng thông báo dễ hiểu.
- `GET /m/onboarding` — cần Bearer token; trả `OK|onboarding_complete(0/1)|display_name|handle|avatar_media_id`.
- `POST /m/onboarding` — `name` bắt buộc, `avatar` (UUID) tùy chọn và phải thuộc người dùng, `scope=avatar`.
- `POST /m/upload?scope=avatar` — upload JPEG/PNG <=180 KiB, private bucket, nhận `OK|media_id`.
- `GET /m/profile` — thêm `avatar_media_id` ở cuối kết quả. `GET /m/photo?id=<avatar_id>` cho phép ảnh avatar đã gắn hồ sơ, vẫn yêu cầu đăng nhập.
- `X-MiniSocial-Device: Nokia Asha 302` — HTTP header tùy chọn, chỉ là nhãn không tin cậy do client cung cấp, không được dùng để phân quyền. Server giới hạn <=64 ký tự ASCII an toàn, lưu trên `posts`, `comments`, `messages`, `media_files`.
- `GET /m/feed` thêm `device_model`, `avatar_media_id` ở cuối dòng; tương tự `GET /m/chat` và `GET /m/comments`.
- **Triển khai:** chạy migration `deploy/UPDATE-v1.6.0.sql` TRƯỚC khi deploy backend mới.


## v1.7.0 — Accounts/admin/quotas

- `POST /m/register`: form-encoded `handle`, `password` (no email / OTP).
- `POST /m/login`: `handle`, `password`; legacy `email`, `password` remains accepted.
- `POST /m/password/recover` and `/m/password/reset`: respond HTTP 410. Recovery is admin-managed.
- `GET /admin`: HTML admin console; `GET /admin/assets/admin.js`: bundled JavaScript.
- `POST /admin/api/login`: `username`, `password`. Returns an HTTPS-only Secure/HttpOnly session cookie.
- `GET /admin/api/session`: auth state and CSRF token.
- `GET /admin/api/data?offset=0&q=handle`: user list and global quota.
- `POST /admin/api/suspend`: `id`, `suspended` (`true|false`).
- `POST /admin/api/reset-password`: `id`, `password`. Invalidates earlier backend sessions.
- `POST /admin/api/delete`: `id`, `confirm=DELETE` (irreversible).
- `POST /admin/api/account-quota`: `id`, `bytes`.
- `POST /admin/api/global-quota`: `bytes`.
- `POST /admin/api/registration`: `enabled` (`true|false`).
- `POST /admin/api/logout`: closes admin session.

Except login, admin mutations require cookie and `X-MiniSocial-CSRF` header. Admin API requires HTTPS to send Secure cookie. Supabase project secret stays exclusively in server Environment. SQL trigger quota counts body bytes + uploaded image bytes, not raw database/index overhead.

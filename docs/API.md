# MiniSocial 1.0 line-protocol reference

This API is intentionally tiny for Java ME (no JSON parser needed on the handset). All POST forms except `/m/upload` use `Content-Type: application/x-www-form-urlencoded; charset=UTF-8`.

- `GET /health` → JSON (`{"ok":true,...}`), no authorization.
- `POST /m/register` → `name, handle, email, password`, no authorization.
- `POST /m/login` → `email, password`, returns `OK|user_id|supabase_access_token`.
- All other `/m/` routes require header `Authorization: Bearer <supabase_access_token>`.
- Response lines use `|`-separated, URL-encoded UTF-8 fields (space encoded as `+`, literal pipe as `%7C`); row 1 of a collection is `OK`, one subsequent line per record.
- Errors use HTTP 4xx or 5xx with `ERR|url_encoded_message`.

## Feed & profiles

- `GET /m/feed` → `post_id|author_id|display_name|created_at|likes_count|comments_count|body|media_id` (up to 15 newest).
- `GET /m/profile?user=<uuid>` → `OK|user_id|display_name|handle|bio|created_at`.
- `GET /m/profileposts?user=<uuid>` → same feed rows, only user's posts.
- `POST /m/profile` → `name,bio` (own profile only).
- `POST /m/post` → `body`, optional `media` UUID (own public-post upload).
- `POST /m/like` → `post` UUID (toggle).
- `GET /m/comments?post=<uuid>` → `id|display_name|body|created_at`.
- `POST /m/comments` → `post,body`.

## Friends & chat

- `GET /m/friends` → `friend_id|display_name|handle` (accepted only).
- `GET /m/requests` → `requester_id|display_name|handle` (incoming pending).
- `GET /m/search?handle=<username>` → `OK|user_id|display_name|handle`.
- `POST /m/friend` → `handle`.
- `POST /m/accept` → `user` (incoming requester UUID).
- `GET /m/chat?user=<friend_uuid>` → `message_id|sender_id|body|media_id|created_at` (latest 40, chronological output).
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

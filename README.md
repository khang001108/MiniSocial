## MiniSocial v1.7.0 — username/password + web admin + storage quota

**New:** Register without real email/OTP; username login; `https://minisocial.xubi.org/admin` for administrator management and global/per-user content quotas. **Read [HUONG-DAN-CAP-NHAT-v1.7.0.md](HUONG-DAN-CAP-NHAT-v1.7.0.md) before deploying.** Back up MiniSocial Supabase, apply v1.6 migration first if needed, then `deploy/UPDATE-v1.7.0-ADMIN.sql`. The v1.7 admin `MINISOCIAL_ADMIN_PASSWORD` environment variable is mandatory. Existing email accounts are retained.

# MiniSocial v1.6.0 — Đăng ký 3 ô, hồ sơ sau đăng nhập và nhãn thiết bị

**BẮT BUỘC trước khi Deploy:** chạy `deploy/UPDATE-v1.6.0.sql` trong Supabase project MiniSocial riêng; script có tính nâng cấp giữ dữ liệu hiện tại. Sau đó cập nhật GitHub `main`, Deploy Xubi server `minisocial-xubi` (`18087 → 8080`). Người chơi dùng JAR/JAD v1.6.0 mới.

- Đăng ký chỉ còn **Tên tài khoản / Email / Mật khẩu**. Trùng handle trả lỗi dễ hiểu.
- Đăng nhập lần đầu phải nhập **Tên hiển thị**; có thể chọn **avatar từ file** (JSR-75, giới hạn 180 KiB) và xem/chỉnh **model điện thoại** do ứng dụng đọc từ `microedition.platform`.
- Nhãn model được gửi kèm tin nhắn, bình luận, bài đăng và metadata ảnh; nhãn là **tự khai báo từ client**, không phải chứng minh chính xác phần cứng. Không thu IMEI.
- Tài khoản cũ giữ nguyên hồ sơ; mở **Hồ sơ → phím 9** để cập nhật tên, avatar và model trên máy.
- `HTTPS` public vẫn cần Caddy/Dynu; chỉ cho `HTTP` khi ở LAN tin cậy.
- Mã nguồn J2ME dùng một ô đường dẫn ảnh (`file:///...`); chưa có trình duyệt thư mục hay nút chụp ảnh trực tiếp.

# MiniSocial v1.5.0 — Email OTP & đổi mật khẩu

- **Quên mật khẩu:** Email → OTP 6 chữ số → mật khẩu mới, Supabase tạo và xác minh OTP. Ứng dụng không lưu OTP/mật khẩu vào RMS.
- **Đổi mật khẩu sau đăng nhập:** Vào Hồ sơ → nhấn phím **8**, nhập mật khẩu hiện tại, mật khẩu mới và xác nhận.
- Backend bổ sung `/m/password/recover`, `/m/password/reset`, `/m/password/change`. Không thêm SQL, không thêm Environment.
- **BẮT BUỘC:** Supabase → Authentication → Email Templates → **Reset Password/Recovery**: thêm `{{ .Token }}` để email có mã OTP. Xem `docs/PASSWORD-OTP-v1.5.0.md`.
- Không cần email xác nhận để đăng ký nếu bạn đã tắt Confirm email; OTP khôi phục vẫn yêu cầu nhận email thật.

---

# MiniSocial 1.5.0 — Modern Login / Signup Forms + Green UI + Xubi + Supabase

Standalone MVP, created from four supplied Nokia-style mockups (feed, user profile, chat and photo viewer).
The interface is **code-rendered**, not a screenshot, so it can respond to the E72 keyboard and a responsive Canvas.

**IMPORTANT TEST LIMITATION:** The Java ME client has been compiled, converted to CLDC 1.1 class version 45.3, and structurally checked using OpenJDK's verifier. The build includes converted CLDC `StackMap` attributes. The v1.2 layout has been exercised with a desktop-only Canvas geometry smoke test for all four target sizes (not a Java ME emulator). **It has NOT been installed on a real Nokia E72 or run against a real Supabase project.** Nokia Java ME HTTPS certificate/TLS support may require additional work. Do not claim E72 installation or end-to-end operation is already confirmed.


## Form đăng nhập / đăng ký v1.4.0

- Màn hình đăng nhập dùng một thẻ rộng, hai ô tài khoản/email và mật khẩu, ba liên kết nhỏ **Máy chủ · Tạo tài khoản · Thông tin**.
- Mục **Tạo tài khoản** mở **một form duy nhất** gồm: tên hiển thị, tên tài khoản (handle), email, mật khẩu và nhập lại mật khẩu; nút **Đăng ký** ở cuối. Không còn chuỗi 4 hộp thoại tự mở liên tiếp.
- Phím **↑/↓** thay đổi ô chọn; **OK** chỉnh sửa ô hoặc gửi form; phím chữ/số QWERTY có thể gõ trực tiếp vào Canvas khi chọn trường. Với trình giả lập hoặc thiết bị yêu cầu IME, OK mở trình biên tập văn bản gốc cho **một trường**, rồi trở lại form với các trường khác vẫn giữ nguyên.
- Màn hình ngang 320×240 chỉ hiển thị một phần các trường và tự cuộn theo ô đang chọn. 240×320, 360×640, 640×480 dùng bố cục phù hợp.
- Form kiểm tra dữ liệu trên thiết bị trước khi gửi API; thông báo lỗi ở đầu form, chỉ rõ trường cần sửa. Xác nhận mật khẩu chỉ dùng ở client, không gửi lên server.
- Mật khẩu đăng nhập và đăng ký giữ tạm trong RAM, không lưu RMS; mật khẩu đăng ký bị xóa sau khi gửi hoặc rời form.
- Giữ nguyên backend Java 17, Supabase, Dockerfile và Docker Host/Container port 18087→8080.

**Giới hạn Java ME:** Không thể nhúng `TextField` của MIDP vào `Canvas` tùy biến như DOM của trình duyệt. Khi cần bàn phím hệ thống có IME, người dùng vẫn có thể thấy trình soạn thảo văn bản của điện thoại **chỉ cho ô đang chọn**. Không còn quy trình hỏi từng mục một cách tự động.

## Giao diện đăng nhập mới (v1.3.0)

Màn hình đầu mở thẳng vào biểu mẫu theo phong cách web: tiêu đề MiniSocial, ô **Tài khoản / Email**, ô **Mật khẩu**, nút **Đăng nhập** màu xanh lá và ba liên kết nhỏ bên dưới: **Máy chủ · Tạo tài khoản · Thông tin**.

- **↑/↓** đổi vùng chọn giữa 6 phần, **OK/Enter** để sửa/chọn; phím mềm trái chọn, phím mềm phải mở Máy chủ. Có thể chạm vào ô/liên kết trên thiết bị cảm ứng.
- Dữ liệu email/mật khẩu được nhập qua `TextBox` MIDP hệ thống; đây là cách phù hợp bàn phím Nokia, vì `Canvas` không có trường nhập HTML thật. Mật khẩu hiển thị dạng dấu `*` và không lưu vào RMS.
- Chọn máy chủ giữ nguyên 3 chế độ Internet HTTPS, Wi-Fi LAN và tùy chỉnh. Đổi máy chủ xóa phiên đăng nhập trước và mật khẩu đang nhập.
- Mục Thông tin có một trang riêng, có thể quay lại.
- Bản đổi giao diện này **không thay đổi** backend Java 17 hay Supabase.

## Responsive screen support (v1.2.0)

The same JAR reads `Canvas.getWidth()` and `Canvas.getHeight()` on every paint and when its size changes. It automatically lays out these four target screen sizes: **240×320 portrait, 320×240 landscape, 360×640 portrait, and 640×480 landscape**. Other screen sizes receive a best-effort flexible layout. The 240-pixel-wide portrait screen uses a 2×2 navigation grid; wider screens use a single row. Lists scroll by Up/Down using a selection-based window rather than only drawing the first items. Touch-capable MIDP devices can tap navigation and rows; keypad actions remain the primary controls.

On compact 320×240 and 240×320 screens, the profile screen uses **5** to toggle **Information / Posts** so posts remain accessible when its header consumes the limited space. On 360×640 and 640×480, the header and posts can be shown together.

**Resolution support means adaptive layout code, not device-certified compatibility.** Test Java ME verification, TLS, touch, fonts and image memory on each real handset. No emulator or handset was connected here.

## Files

- `client/dist/MiniSocial-Responsive.jar` — actual app binary (not an Android APK).
- `client/dist/MiniSocial-Responsive.jad` — descriptor, points at JAR in the same directory.
- `client/src/` — full Java ME source code.
- `client/build_j2me.py` — offline experimental CLDC preverify converter (does not need external libraries); inspect and validate on device before release.
- `client/lib-stubs/` — minimal **compile-only** J2ME API declarations, **not** bundled inside the app JAR.
- `server/src/` — Java 17 backend source, with built-in HTTP server and Supabase REST API client.
- `server/build.sh` / `server/Dockerfile` — PC build or ARM64 Docker build.
- `deploy/supabase.sql` — all tables, views, permissions, Auth trigger and private Storage bucket.
- `deploy/docker-compose.yml` — service on `127.0.0.1:8087` on the Raspberry Pi, outside not directly exposed.
- `deploy/Caddyfile.example` — HTTPS virtual host + `.jar`/`.jad` download page.
- `deploy/git-pull-deploy.sh` — Git fast-forward deploy script for Xubi.

## Feature status (MVP)

| Feature | Status |
| --- | --- |
| Create account (email/password/@handle) | Server + J2ME UI implemented; requires Supabase confirmation setting |
| Login, local session (RMS) | Implemented |
| Home feed, publish a post, like/unlike, comments | Implemented |
| Profile view, name and bio update | Implemented |
| Send/accept friend requests, list friends | Implemented |
| Private 1-to-1 messaging **between friends** | Implemented, **manual refresh with key 9** |
| JPEG/PNG post and message upload; private photo viewer | Implemented; requires device file permission, manual path, 180 KB maximum |
| Share/repost, group chat, push notifications, voice/video calls | Not implemented |
| Native camera capture or gallery file chooser | Not implemented; file URL input is used |
| Auto-scroll, smart pagination, unread badges, live online presence | Not implemented |
| Modern smartphone app | Not included; this is the Java ME client only |

## Prerequisites

1. Raspberry Pi 5 running Ubuntu 24.04 with Docker Compose and an active Xubi management UI. Pi can serve MiniSocial and Gunny as separate containers.
2. An active Supabase project. **Keep the existing Gunny database separate if possible**; a dedicated Supabase project is recommended to avoid affecting Gunny.
3. A Git repository (new repo recommended, e.g. `minisocial`) containing **this project root**. Never commit `.env`, Supabase service keys, session tokens or database backups.
4. DNS A record `minisocial.xubi.org` → your WAN public IPv4. Keep existing NAT forwarding for HTTPS 443 → your Pi. You don't need a new WAN TCP port for MiniSocial because Caddy serves multiple domains on the same 443 port.

## Supabase setup (once)

1. Create a **new** Supabase project or carefully select the intended project.
2. Open SQL Editor and run `deploy/supabase.sql` in the intended database. It creates `profiles`, `media_files`, `posts`, `post_likes`, `comments`, `friendships`, `messages`, and read-only views. The SQL includes a trigger on `auth.users`.
3. Under Authentication, configure your desired email confirmation behavior. With email confirmation enabled, users must confirm before login; this client does not implement a deep-link confirmation screen.
4. Get the project URL, **publishable** API key (`SUPABASE_PUBLISHABLE_KEY`, begins `sb_publishable_`) and **secret** API key (`SUPABASE_SECRET_KEY`, begins `sb_secret_`). The legacy `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` pair is also supported as fallback. Keep the secret key **only on Pi** in `deploy/.env`. Do not copy the service key into the JAR, JAD, Git, screenshot or support chat.
5. SQL creates a private Storage bucket `minisocial-private`. All images are served by the authorized backend and are not public direct links.

## Deploy on Raspberry Pi 5 using Git + Docker/Xubi

```bash
# Once: clone the NEW MiniSocial repo onto Pi (example path)
sudo mkdir -p /opt/minisocial
sudo chown "$USER":"$USER" /opt/minisocial
git clone https://github.com/YOUR_USERNAME/minisocial.git /opt/minisocial
cd /opt/minisocial/deploy
cp .env.example .env
chmod 600 .env
# Fill in SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY and SUPABASE_SECRET_KEY in .env using Xubi environment editor or an SSH editor.
docker compose up -d --build
curl http://127.0.0.1:8087/health
```

Then merge `deploy/Caddyfile.example` into the existing Caddyfile (do **not** overwrite Gunny's domains); validate/reload Caddy:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
curl -I https://minisocial.xubi.org/health
```

In the Xubi UI, create a project named `MiniSocial`, link the Git repo and configure Git auto-deploy to execute `deploy/git-pull-deploy.sh` in the checked-out `/opt/minisocial` workspace. **Xubi versions have different UI controls; this project only supplies the Git/Docker integration, not a claim that it was installed into your Xubi interface.** Enable auto-deploy only after the first deployment succeeds.

When `minisocial.xubi.org` and Caddy are active, download the client using:

- `https://minisocial.xubi.org/download/MiniSocial-Responsive.jad`
- `https://minisocial.xubi.org/download/MiniSocial-Responsive.jar`

Copy both to the phone through USB/Bluetooth/memory card if the old Nokia browser does not negotiate modern HTTPS.

## MiniSocial 1.2.0 — Green/teal and server choice

- Canvas UI now uses dark forest green and sea-teal highlights on white/mint surfaces with stronger selection outlines; still entirely code-rendered (no extra PNG memory).
- Welcome screen has 3 selectable cards: **Login**, **Register**, **Server**. Use **Up/Down + OK/Enter**, or 1/2/3 directly. Touch works for each card.
- Server screen: **4G/Internet** (`https://minisocial.xubi.org`), **Wi-Fi/LAN** (`http://192.168.1.127:18087`), **Custom** (user-editable). All three URL values and the active choice are saved in RMS. No Supabase secret is stored here.
- Server screen: **Up/Down to choose**, **OK/Enter to activate**, **# or left softkey to edit URL**, **5 to test `/health`**, **0 or right softkey to go back**. On touch screens, tap a row to activate or the **Sua** area at its right to edit it.
- From the logged-in home feed press **0** to change server, or tap the green app header from any screen. **Changing the active server invalidates the old login session** to prevent using its token on a different endpoint.
- After login, **Left/Right moves focus across the four top tabs**; OK selects the focused tab. Up/Down resumes scrolling, OK selects items.
- HTTP is restricted to numeric RFC1918 LAN addresses/localhost; external domains require HTTPS. This is an HTTP/HTTPS application, **not a raw `socket://` Gunny client**.
- The original backend API, database and Docker ports are unchanged; rebuild/redeploy only to publish the new JAR/JAD on the existing download website.
- **Warning:** Nokia E72/E6 Java TLS/certificate support requires device testing. A successful `/health` check does not prove login or Supabase integration.

## Nokia E72 controls

- First screen: **Up/Down + OK** choose login, register, server; shortcuts **1/2/3**.
- From app: **1** feed, **3** profile, **4** friends, **9** refresh, **#** logout, **0 on home feed** server settings. **Up/Down** scroll list selection; **5** toggles Info/Posts on short landscape profile screens.
- Feed: **2** create post, **8** enter photo path, **Up/Down** select post, **OK/Enter** comments, **\*** like/unlike, **5** open post picture.
- Profile: **6** update display name; **7** update bio.
- Friends: **6** request friend by handle, **7** incoming requests, **OK/Enter** enter chat.
- Chat: **2** write message, **8** enter photo path to send, **5** view latest image, **9** refresh.
- Comments: **2** write a comment. In incoming requests, **OK/Enter** accepts.
- **0 / right softkey** return/back.

Picture path example: `file:///E:/Images/anh.jpg` (actual memory card path varies by device). Use 320×240 JPEG quality ~60%, ideally under 60 KB for responsiveness and less mobile traffic; hard upload cap 180 KB. Java ME will request file and network permissions according to handset policy.

## Building client

Run `python3 client/build_j2me.py` with Java 21 or Java 17 and the included compile-time stubs. It compiles the MIDlet, substitutes CLDC-available `StringBuffer` for `StringBuilder`, converts Java 8 StackMapTable entries to CLDC StackMap entries, and packages JAR/JAD.

This is a **best-effort** offline build chain, not a certified Sun Wireless Toolkit installation. For a production release, independently preverify against CLDC 1.1/MIDP 2.0 APIs using Oracle WTK/Java ME SDK or an equivalent proven tool, and test on the actual E72. Do **not** put the compile-only stub classes into the client JAR. Background on mandatory preverification: Oracle's WTK documentation describes its `preverify` stage.

After updating the domain, change `MiniSocial-Api-Url` in both the generated JAR manifest and JAD, not just the JAD: Java ME `getAppProperty` values may come from the suite properties. The current default is `https://minisocial.xubi.org`.

## Test status, known risks

- Java 17 backend: `javac` passed; built `minisocial-server.jar`.
- Server `/health` returned HTTP 200 with `{"ok":true}` and `/m/feed` without token returned HTTP 401 during local smoke testing.
- Java ME client: source compiled against compile-only stubs, generated 16 classes with version 45.3 and StackMap; 96 desktop-only layout configurations across the four target resolutions passed (5,638 draw operations before final minor profile adjustment). **Real Java ME runtime verification is not guaranteed**.
- Supabase SQL/Auth/Storage requests: **not exercised with a real project**, as no credentials were provided.
- TLS: modern Caddy server and older Nokia E72 HTTPS stacks may be incompatible (certificate chain, SNI, TLS version). Never use unencrypted public HTTP for passwords/session tokens merely to bypass this.
- Session token stored in RMS: no secure hardware storage; users with physical access to phone storage may retrieve it. Logout clears the stored session reference.
- No push messaging; conversation refreshes only on key 9. Private chat is designed for friends only.
- For wider public operation add rate limits, abuse control, audit logs, password recovery, account deletion, antivirus/image reencoding, pagination, token refresh, alerting and backups.

## Important server safety

- `SUPABASE_SECRET_KEY` (or legacy `SUPABASE_SERVICE_ROLE_KEY`) remains on Pi and bypasses RLS. The server validates each access token against Supabase Auth, then explicitly checks the actor's ownership/peer relationship on all writes and private photo reads.
- Database tables have RLS enabled and direct access is revoked for `anon` and `authenticated`; only `service_role` can query via this backend.
- Docker publishes only loopback `127.0.0.1:8087`. Keep only Caddy's 443 exposed publicly.
- If a Supabase key was compromised in a previous repository, rotate/revoke it and use a new key on Pi. New-format `sb_*` keys are used only in the `apikey` header, never as `Bearer` JWTs. Do not reuse revoked credentials.

## License

User-created MiniSocial prototype source code provided for modification. All visual source screenshots are reference material supplied by the user; the JAR uses shapes/fonts drawn by code and does not bundle those images.

## Website tải ứng dụng qua Xubi (bổ sung)

Sau khi triển khai qua Xubi, backend Java phục vụ trang tải JAR/JAD trên **cùng cổng với API**:

- `http://192.168.1.127:18087/` — trang tải MiniSocial (địa chỉ chỉ là ví dụ cho Pi LAN)
- `http://192.168.1.127:18087/download/MiniSocial-Responsive.jar` — file JAR
- `http://192.168.1.127:18087/download/MiniSocial-Responsive.jad` — file JAD
- `http://192.168.1.127:18087/health` — kiểm tra HTTP backend

Dockerfile `server/Dockerfile` copy `web/index.html` và `client/dist` vào image. Xubi giữ `server/Dockerfile`, Host `18087` → Container `8080`/TCP, không cần mở thêm cổng NAT. Đẩy source mới lên GitHub rồi Deploy lại từ Xubi. **Không đổi cấu hình Gunny.**

Để có website tải ngoài Internet an toàn: Caddy route `minisocial.xubi.org` đến `127.0.0.1:18087` và DNS trỏ về WAN IPv4 hiện tại. Nếu dùng Docker publish trên mọi interface (`0.0.0.0:18087`) thì chỉ mở NAT 443; không cần mở NAT 18087 khi Caddy reverse proxy. Cần bảo vệ quyền truy cập backend production. JAR hiện có API hard-coded `https://minisocial.xubi.org` trong manifest/JAD; trang tải trong LAN **không tự thay đổi URL API**.

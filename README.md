# MiniSocial 1.1.0 — Responsive Java ME + Xubi + Supabase

Standalone MVP, created from four supplied Nokia-style mockups (feed, user profile, chat and photo viewer).
The interface is **code-rendered**, not a screenshot, so it can respond to the E72 keyboard and a responsive Canvas.

**IMPORTANT TEST LIMITATION:** The Java ME client has been compiled, converted to CLDC 1.1 class version 45.3, and structurally checked using OpenJDK's verifier. The build includes converted CLDC `StackMap` attributes. The v1.1 layout has been exercised with a desktop-only Canvas geometry smoke test for all four target sizes (not a Java ME emulator). **It has NOT been installed on a real Nokia E72 or run against a real Supabase project.** Nokia Java ME HTTPS certificate/TLS support may require additional work. Do not claim E72 installation or end-to-end operation is already confirmed.

## Responsive screen support (v1.1.0)

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

## Nokia E72 controls

- First screen: **1** login, **2** create account.
- From app: **1** feed, **3** profile, **4** friends, **9** refresh, **#** logout. **Up/Down** scroll list selection; **5** toggles Info/Posts on short landscape profile screens.
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

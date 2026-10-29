# Phát hành Google Play — v3.4.10017

Bản này gồm: logo/icon/splash mới (#21), sửa màn Player trống (#22), sửa crash thiếu `libsecuredtoken.so` (#19), chuyển sang AdMob Next-Gen + UMP (#24), sửa tab Radio trống (TSP-19).

## 1. Build AAB đã ký

Keystore (`KeyStore` ở gốc repo) và mật khẩu nằm trong GitHub secrets `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

- GitHub → Actions → **Build signed release bundle** → Run workflow (nhánh `main`).
- Tải artifact `release-bundle`: `TSPodcast.v3.4.10017_R-release.aab` + `mapping.txt`.
- Hoặc build máy local: thêm `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` vào `local.properties` rồi `./gradlew :app:bundleRelease`.

Workflow **Release to Google Play Console** đẩy thẳng lên track internal (cần secret `PLAY_STORE_SERVICE_ACCOUNT_KEY_BASE64` trong environment).

## 2. Asset cho Store listing

Tất cả nằm trong `fastlane/metadata/android/<vi-VN|en-US>/` (cấu trúc `fastlane supply`, cũng dùng để tải lên tay):

| Mục Play Console | File | Giới hạn |
|---|---|---|
| Tên ứng dụng | `title.txt` | ≤ 30 ký tự |
| Mô tả ngắn | `short_description.txt` | ≤ 80 ký tự |
| Mô tả đầy đủ | `full_description.txt` | ≤ 4000 ký tự |
| Biểu tượng | `images/icon.png` | 512×512 PNG |
| Hình ảnh nổi bật (banner) | `images/featureGraphic.png` | 1024×500 |
| Ảnh chụp màn hình điện thoại | `images/phoneScreenshots/01–06.png` | 1080×1920, 6 ảnh |
| Ghi chú phát hành | `changelogs/10017.txt` (và `distribution/whatsnew/`) | ≤ 500 ký tự |

Nguồn để sinh lại: `brand/store/` (icon, feature graphic), `brand/store/screenshots/` (`node brand/scripts/frame.js --batch brand/store/screenshots/examples.json`).

Video demo: `brand/video/out/tspodcast-demo-1080x1920.mp4` (không commit, 107 giây). Play chỉ nhận video qua **link YouTube**: tải video lên YouTube (Unlisted/Public, tắt quảng cáo) rồi dán link vào mục *Video*. Play ưu tiên video ngang; bản dọc vẫn dùng tốt cho YouTube Shorts / mạng xã hội.

## 3. Việc bắt buộc trong Play Console trước khi gửi duyệt

1. **Nội dung ứng dụng → Quảng cáo:** "Có, ứng dụng có quảng cáo".
2. **Advertising ID:** khai báo có dùng (AdMob), mục đích *Quảng cáo hoặc tiếp thị* và *Phân tích*.
3. **Data safety:** bỏ Meta (Facebook SDK) và AppLovin; thêm Google AdMob: Device or other IDs (Advertising ID), App interactions, Diagnostics, Approximate location (từ IP) — thu thập, chia sẻ cho quảng cáo; mã hoá khi truyền; người dùng không yêu cầu xoá được qua app (không có tài khoản). Giữ Firebase Analytics/Crashlytics như cũ.
4. **Chính sách quyền riêng tư:** URL phải nhắc tới AdMob và đối tác quảng cáo của Google, cách rút lại đồng ý (link "Tuỳ chọn quyền riêng tư" ở cuối màn Khám phá, chỉ hiện khi luật yêu cầu).
5. **Phân loại nội dung:** trả lời lại bảng câu hỏi nếu Play yêu cầu (nội dung bên thứ ba, có quảng cáo).
6. **Target audience:** không nhắm trẻ em (tránh chính sách Families với quảng cáo).

## 4. Quảng cáo trong bản release này

- Bản release **chưa có ID AdMob thật** → build dùng ID test của Google, và quảng cáo **tắt** cho tới khi Remote Config `ads_enabled=true`. Bản này lên store sẽ không hiện quảng cáo.
- Khi có ID thật: tạo secrets `ADMOB_APP_ID`, `ADMOB_UNIT_APP_OPEN`, `ADMOB_UNIT_BANNER`, `ADMOB_UNIT_NATIVE` (tuỳ chọn `ADMOB_UNIT_INTERSTITIAL`, `ADMOB_UNIT_REWARDED`) trong GitHub → build lại → rồi mới bật `ads_enabled` trên Remote Config.
- Đã kiểm tra trên emulator (bản debug, ID test): form UMP hiện (EEA debug), banner adaptive + native hiển thị, native validator "No implementation issues found", App Open **không** hiện khi đang phát, hiện sau khi tạm dừng + ra nền ≥ 30 giây.

## 5. Kiểm thử đã làm (emulator Pixel 7, Android 17)

Onboarding → chọn chủ đề → Khám phá (thịnh hành, Kho nhạc) → chi tiết podcast → phát tập → Player → yêu thích → Radio (VOV + VOH, phát VOV1) → Yêu thích → Tìm kiếm "kinh doanh". Không crash. Splash mới hiển thị.

## 6. Rollout đề xuất

Internal testing → Closed testing (nếu cần) → Production **staged rollout 20%**, theo dõi Crashlytics 48 giờ (đặc biệt non-fatal `App.missingNativeLib`, `RadioRepository`, `GetRadioList`) rồi tăng 50% → 100%.

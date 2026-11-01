# Rà soát chính sách Google Play và Apple App Store: TSPodcast

> **Trạng thái: Đang làm** (2026-11-01) — checklist §11 đã xử lý: #4 link chính sách + Settings, #6 bỏ ANDROID_ID, #7 gỡ Facebook SDK, #8–#11 UMP và quy tắc quảng cáo, #13 lọc explicit (PR #24, #38, #39).
> Còn lại: #18 `network_security_config`, #19 `PlayerSessionService`, #17 thư VOV/VOH, #25 tư vấn luật BVDLCN, khai báo Play Console (#3, #5, #14).

> Ngày lập: 29/10/2026 · App: `tss.t.podcast` (Android, targetSdk 36). iOS và web đang lên kế
> hoạch. · Đọc kèm: `docs/done/research/competitor-analysis.md`,
> `docs/in-progress/research/feature-roadmap-ai-growth.md`.
>
> **Đây không phải tư vấn pháp lý.** Các điểm về luật Việt Nam (bản quyền phát sóng, Nghị định
> 147/2024, Luật BVDLCN) cần luật sư xác nhận trước khi ra quyết định kinh doanh. Chỗ ghi
> **(chưa xác minh)** là thông tin từ nguồn thứ cấp.

---

## 0. App đang thực sự làm gì (bằng chứng từ code)

| Hạng mục | Thực tế trong code | File |
|---|---|---|
| Quyền (permissions) | `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Không có quyền vị trí, micro, danh bạ hay bộ nhớ | `app/src/main/AndroidManifest.xml`, `sharedFirebase/.../AndroidManifest.xml` |
| Mạng | `android:usesCleartextTraffic="true"` cho toàn app | `app/src/main/AndroidManifest.xml` |
| Foreground service | `PlayerSessionService` (Media3 `MediaSessionService`), `foregroundServiceType="mediaPlayback"`, `exported=true` | `sharedPlayer/.../PlayerSessionService.kt` |
| Analytics | Firebase Analytics, kèm các thuộc tính mặc định `os`, `os_version`, `app_version` và **`device_id` = `Settings.Secure.ANDROID_ID`** trên **mọi event** | `sharedFirebase/TSAnalytics.kt` (`initDefaultAttrs`), `TSFirebaseSharedPref.kt`, `sharedLibrary/utils/Utils.kt` |
| Crash | Firebase Crashlytics (có API `setUserId`, hiện không được gọi) | `sharedFirebase/FirebaseCrashReporter.kt` |
| Push | Firebase Cloud Messaging (`TSFcmServices`) | `sharedFirebase` |
| Remote Config | Bật/tắt quảng cáo (`KEY_ADS_ENABLE`), blacklist nội dung | `ads/`, `podcasts/BlacklistRepositoryImpl.kt` |
| Quảng cáo | AppLovin MAX 13.6.4: **app-open**, banner, native (một nhánh đang gỡ; dự kiến chuyển sang AdMob). **Không có luồng consent** (không có UMP/CMP, không gọi `setHasUserConsent` / `setDoNotSell`) | `ads/` |
| SDK khác | **Facebook Android SDK 18.3.0** (`com.facebook.sdk.ApplicationId` trong manifest). Không thấy cấu hình tắt auto-log app events hay thu thập advertiser ID | `app/build.gradle.kts`, `AndroidManifest.xml` |
| Tài khoản | **Không có** đăng nhập hay tạo tài khoản | `ProfileScreen.kt` (khung trống) |
| Nội dung podcast/nhạc | PodcastIndex API (gồm `medium=music`). Phát audio trực tiếp từ enclosure URL của publisher. Metadata có TTL 7 ngày (theo điều 5.5(1) ToS PodcastIndex). Có dòng ghi công (7.2) | `podcasts/`, `coreApi/`, commit `dddd818` |
| Radio | **Scrape HTML/JS** của `vovmedia.vn` và `voh.com.vn` bằng Jsoup để lấy link stream | `coreRadio/repository/RadioRepository.kt`, `VOHRepository.kt` |
| Nội dung explicit | Model có `explicit` nhưng **UI không lọc hay gắn nhãn** | `coreApi/models/Feed.kt`, `Podcast.kt` |
| Chính sách quyền riêng tư trong app | **Không có** link hay màn hình nào | grep `privacy` không có kết quả trong `app/` |
| Backup | `allowBackup=true`, có `data_extraction_rules` (loại trừ `JsoupExt.xml`) | `app/src/main/res/xml/` |

---

## 1. Yêu cầu nền tảng về kỹ thuật

### 1.1 Google Play: target API level
- Từ **31/08/2026**: app mới và bản cập nhật phải target **Android 16 (API 36)**. App hiện có phải
  target ≥ API 35 để tiếp tục hiển thị với người dùng mới trên Android mới hơn. Có thể xin gia
  hạn tới **01/11/2026**
  ([developer.android.com](https://developer.android.com/google/play/requirements/target-sdk)).
- TSPodcast: `targetSdk = 36`, `compileSdk = 36` (commit `2261ef4`). **Pass.** Mốc tiếp theo
  dự kiến là API 37 vào khoảng 8/2027 (chưa công bố).
- Android 14+ yêu cầu khai báo loại foreground service. App đã khai `mediaPlayback` và quyền
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Cần **điền khai báo foreground service trong Play Console
  (App content)** đúng mục đích phát media. **Cần kiểm tra lại trong Console.**

### 1.2 Apple: chuẩn bị cho iOS
- **Background audio (2.5.4):** chỉ được dùng background mode `audio` cho phát âm thanh do người
  dùng khởi động. Không dùng để giữ app sống cho việc khác (ví dụ tải AI nền). Tải tập nền nên
  dùng `URLSession` background.
- **Privacy manifest (`PrivacyInfo.xcprivacy`) và Required Reason APIs:** App Store Connect
  **từ chối** bản build không khai lý do dùng các API trong danh sách (từ 01/05/2024). Mỗi SDK bên
  thứ ba phải có manifest riêng
  ([Apple docs](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api.md),
  [Bitrise](https://bitrise.io/blog/post/enforcement-of-apple-privacy-manifest-starting-from-may-1-2024)).
  Với bản iOS dùng KMP:
  - `UserDefaults` (lưu cài đặt hoặc `multiplatform-settings`): khai `NSPrivacyAccessedAPICategoryUserDefaults`.
  - Tính năng tải offline kiểm tra dung lượng trống: khai `NSPrivacyAccessedAPICategoryDiskSpace`.
  - Đọc thời gian sửa file (dọn cache): khai `NSPrivacyAccessedAPICategoryFileTimestamp`.
  - Firebase, AdMob, Facebook SDK: dùng phiên bản có sẵn privacy manifest và chữ ký SDK.
- **Phân loại tuổi mới (4+/9+/13+/16+/18+):** phải trả lời bộ câu hỏi mới trong App Store Connect
  (hạn ban đầu 31/01/2026; một nguồn nói từ 9/2026 bắt buộc khi submit, **chưa xác minh**)
  ([MacRumors](https://www.macrumors.com/2025/07/25/apple-overhauls-app-store-age-ratings/),
  [trysonar](https://trysonar.app/blog/app-store-age-rating)). App podcast hay radio phát nội dung
  không qua kiểm duyệt, kể cả explicit, nên dự kiến xếp **16+ hoặc 18+**, trừ khi lọc explicit
  mặc định.

---

## 2. Nội dung và sở hữu trí tuệ (IP)

### 2.1 Podcast từ feed RSS bên thứ ba (qua PodcastIndex)
- **Mô hình hiện tại** (đọc metadata + phát audio thẳng từ máy chủ của publisher) là mô hình
  chuẩn của mọi podcast client (Apple, Pocket Casts, Podcast Addict). Rủi ro thấp **khi**:
  - Không **re-host / proxy / cache vĩnh viễn** file audio trên server của mình.
  - Không **chỉnh sửa** audio (cắt ghép, chèn quảng cáo vào audio, lồng tiếng) nếu chưa có phép.
  - Hiển thị đúng tên show, tác giả, ảnh bìa, và có đường dẫn về nguồn.
  - Có **quy trình gỡ bỏ** khi chủ sở hữu yêu cầu. Hiện đã có blacklist qua Remote Config, cần
    thêm kênh tiếp nhận (email hoặc form) công khai trong app và trên trang web.
- **Điều khoản PodcastIndex:** đã áp dụng 5.5(1) (không lưu bản sao vĩnh viễn, tôn trọng cache
  header, TTL 7 ngày, ngoại lệ cho mục yêu thích) và 7.2 (ghi công) trong commit `dddd818`. Trang
  `podcastindex.org/terms` trả 403 với công cụ tự động khi viết tài liệu này, nên **cần đọc lại
  toàn văn thủ công**, nhất là các điều về dùng thương mại, giới hạn tần suất (rate limit) và
  User-Agent bắt buộc
  ([podcast-index-api README](https://cdn.jsdelivr.net/npm/podcast-index-api@2.0.0/README.md)).
  Lưu ý: PodcastIndex **không cấp quyền** với nội dung audio. Quyền vẫn thuộc publisher.
- **Tính năng tương lai làm tăng rủi ro:**
  - *Tải offline:* tải vào bộ nhớ riêng của app để nghe là chuẩn ngành. **Không** cho xuất file
    MP3 ra ngoài. Apple **5.2.3** cấm tạo điều kiện "save, convert, or download media from
    third-party sources" khi chưa có phép rõ ràng. Podcast RSS vốn được thiết kế để tải về, nhưng
    nhạc (`medium=music`) và radio thì không, nên **không cho tải nhạc hay radio**.
  - *Transcript, tóm tắt, dịch, lồng tiếng AI:* là **tác phẩm phái sinh**. Transcript và tóm tắt
    hiển thị kèm nguồn có rủi ro trung bình. Dịch toàn văn hay lồng tiếng thì **cần sự đồng ý
    của creator**.
  - *Clip chia sẻ:* giới hạn độ dài (≤60 giây), luôn có tên show và link gốc.

### 2.2 Feed nhạc (`medium=music`)
- Feed nhạc RSS do publisher tự khai báo (thường là nghệ sĩ độc lập hoặc Podcasting 2.0/Value4Value).
  Chính PodcastIndex cũng không xác minh quyền. Rủi ro: một feed chứa nhạc vi phạm bản quyền.
- Google Play: chính sách **Intellectual Property** cấm app tạo điều kiện vi phạm bản quyền.
  Apple: **5.2.1/5.2.2/5.2.3**.
- **Hành động:** (1) Nút "Báo cáo nội dung" ở trang show/track. (2) Blacklist từ xa (đã có) với
  SLA gỡ ≤ 24–48 giờ. (3) Không gợi ý nhạc là "Xu hướng/Top" khi API không có xếp hạng (đã làm
  đúng: "Kho nhạc"). (4) Mô tả trên store **không** quảng cáo là "nghe nhạc miễn phí" hay đặt
  cạnh tên nghệ sĩ hay hãng đĩa lớn, để tránh ấn tượng là dịch vụ nhạc có bản quyền.

### 2.3 Radio (VOV, VOH): **rủi ro cao nhất hiện nay**
- Code đang **scrape** trang web của đài để lấy link stream (`RadioRepository`, `VOHRepository`).
  Không thấy thoả thuận hay API chính thức.
- **Luật VN:** tổ chức phát sóng có quyền liên quan với chương trình phát sóng của mình (Luật Sở
  hữu trí tuệ, quyền của tổ chức phát sóng, gồm quyền phát sóng lại và phân phối tới công chúng,
  **cần luật sư xác nhận điều khoản cụ thể**). Dịch vụ phát thanh, truyền hình trên mạng chịu
  điều chỉnh của **Nghị định 06/2016 và 71/2022/NĐ-CP** (hiệu lực 01/01/2023; một nguồn cho biết
  đã được sửa đổi bởi Nghị định 116/2026, **chưa xác minh**)
  ([LuatVietnam](https://luatvietnam.vn/thong-tin/nghi-dinh-71-2022-nd-cp-230580-d1.html),
  [PLO](https://plo.vn/dich-vu-ung-dung-xuyen-bien-gioi-phai-duoc-quan-ly-theo-nghi-dinh-71-post702833.html)).
  Ứng dụng tổng hợp kênh của đài khác có thể bị coi là cung cấp dịch vụ phát thanh trên mạng mà
  không có giấy phép hoặc thoả thuận.
- **Store:**
  - Google Play có thể áp dụng **Intellectual Property** (khi chủ sở hữu khiếu nại) và **Device and
    Network Abuse** (truy cập dịch vụ bên thứ ba trái điều khoản của họ).
  - Apple **5.2.3** ghi rõ: "streaming of audio/video content may also violate Terms of Use, so be
    sure to check before your app accesses those services."
  - Dùng tên hoặc logo "VOV", "VOH" trong tên app, icon hay screenshot mà không có phép thì vi
    phạm **chính sách mạo danh / thương hiệu** (Play Impersonation; Apple 5.2.1).
- **Thực tế thị trường:** nhiều app bên thứ ba (radiome có khoảng 80 kênh VOV, Viet Radio, "Radio
  Việt Nam") đang làm tương tự
  ([App Store radiome](https://apps.apple.com/us/app/id1131513842),
  [thegioididong](https://www.thegioididong.com/game-app/radio-viet-nam-nghe-dai-vov-truc-tuyen-225460)).
  Điều này cho thấy rủi ro bị gỡ ngay là thấp, **nhưng không làm việc đó hợp pháp**. Khi lên iOS,
  reviewer có thể yêu cầu bằng chứng quyền.
- **Hành động (theo thứ tự):**
  1. Gửi văn bản xin phép hoặc đề xuất hợp tác với **VOV (VOVmedia)** và **VOH**. Lợi ích cho đài:
     thêm người nghe, có thống kê, có ghi nguồn. Ưu tiên dùng link hoặc API chính thức nếu có.
  2. Trong lúc chờ: ghi nguồn rõ ràng ("Phát từ vovmedia.vn"), không chèn quảng cáo **vào** luồng
     radio, không ghi âm hay lưu lại, có nút "Mở trang của đài", và gỡ ngay khi đài yêu cầu.
  3. Chuẩn bị phương án B qua Remote Config (đã có hạ tầng) để tắt tab Radio theo quốc gia hoặc
     theo store (ví dụ tắt ở bản iOS đầu tiên nếu chưa có thoả thuận).
  4. Giới hạn cleartext: thay `usesCleartextTraffic="true"` bằng `network_security_config` chỉ cho
     phép HTTP với các domain stream cụ thể.

---

## 3. Nội dung người dùng tạo (UGC), nếu thêm bình luận

| Yêu cầu | Google Play (UGC policy) | Apple (1.2, cập nhật 06/2026) | Việt Nam (Nghị định 147/2024/NĐ-CP) |
|---|---|---|---|
| Điều khoản sử dụng cấm nội dung xấu | Bắt buộc, người dùng phải đồng ý trước khi đăng | Bắt buộc | Có |
| Kiểm duyệt / lọc | Kiểm duyệt "robust, effective, ongoing" | Lọc nội dung phản cảm. **Từ 06/2026 Apple nói rõ developer có trách nhiệm gỡ**, nếu không đưa ra được kế hoạch tuân thủ thì app có thể bị gỡ ([MacRumors](https://macrumors.com/2026/06/09/app-store-guidelines-low-quality-apps/)) | Gỡ nội dung vi phạm trong **24 giờ** khi cơ quan có thẩm quyền yêu cầu |
| Báo cáo + chặn người dùng | Bắt buộc | Bắt buộc (report, block, liên hệ) | — |
| Xác thực người đăng | — | — | Chỉ tài khoản **đã xác thực bằng số điện thoại VN** (hoặc số định danh) mới được đăng bài, bình luận ([VOV](https://vov.vn/xa-hoi/tu-hom-nay-nguoi-dung-mxh-phai-xac-thuc-moi-duoc-dang-bai-binh-luan-post1144421.vov), [Vietnamnet](https://vietnamnet.vn/xac-thuc-tai-khoan-bang-so-dien-thoai-moi-duoc-dang-livestream-facebook-2341051.html)) |
| Đăng ký / cấp phép | — | — | Có thể bị coi là "mạng xã hội", nên có nghĩa vụ thông báo hoặc xin giấy phép tuỳ quy mô (**cần luật sư xác nhận ngưỡng**) |

**Khuyến nghị:** giai đoạn đầu chỉ làm **reaction không có văn bản** theo mốc thời gian. Bình
luận văn bản chỉ làm sau khi có đăng nhập, xác thực số điện thoại (OTP), kiểm duyệt (bộ lọc từ
khoá tiếng Việt + hàng đợi duyệt) và tư vấn pháp lý.

---

## 4. Quảng cáo

### 4.1 Phát hiện quan trọng: logic app-open ad hiện tại
```
MainActivity.onResume() → ApplovinSdkWrapper.loadOpenAds()
  → AppOpenAdManager.showAdIfReady()   // giới hạn: 10 phút/lần, bật/tắt qua Remote Config
  → MaxAppOpenAd.loadAd() → onAdLoaded() → showAd()   // hiện NGAY khi tải xong
```
Hệ quả:
1. Quảng cáo được **tải sau khi màn hình đã hiển thị nội dung**, rồi bật lên khi tải xong.
   Google xếp cách làm này vào nhóm *discouraged*: "The app open ad should appear before the user
   can interact with the app's content". Làm như vậy có thể bị coi là *invalid activity* hoặc **bị
   tắt phân phối quảng cáo**
   ([AdMob help 9341964](https://support.google.com/admob/answer/9341964?hl=en)).
2. `onResume` chạy cả khi người dùng **chạm thông báo media** để mở player, khi quay lại từ hộp
   thoại quyền, hay từ màn hình khác. Quảng cáo toàn màn hình bật lên bất ngờ có thể bị xếp vào
   **Disruptive Ads** (quảng cáo hiển thị theo cách người dùng không lường trước, cản trở việc
   dùng app) của Google Play
   ([Google Security Blog](https://security.googleblog.com/2020/02/disruptive-ads-enforcement-and-our-new.html?hl=hr)).
3. Có thể hiện **chồng lên banner hay native ad** đang có trên Discover hoặc Player, trong khi
   AdMob yêu cầu không làm vậy.
4. Có thể hiện **trong lúc đang phát audio**: quảng cáo video có tiếng chồng lên podcast.

**Hành động (P0):**
- Chỉ hiện app-open ở **cold start** (hoặc khi quay lại sau ≥ 4 giờ ở nền), **trên màn hình
  splash/loading**. Đặt thời gian chờ tối đa khoảng 3–5 giây, quá thì bỏ qua.
- **Không** hiện khi mở từ notification hay deep link, khi đang phát, hoặc trong 2–3 lần mở đầu
  tiên (onboarding).
- Không hiện ngay trước hoặc sau một quảng cáo toàn màn hình khác. Ẩn banner khi app-open đang hiện.
- Áp dụng cho cả AppLovin hiện tại và AdMob sau khi chuyển.

### 4.2 Chuyển sang AdMob
- **Consent:** từ 01/2024, phục vụ quảng cáo ở EEA, UK và Thuỵ Sĩ cần **CMP được Google chứng
  nhận** (UMP SDK đáp ứng). TSPodcast ưu tiên VN nhưng phát hành toàn cầu, nên **tích hợp UMP**
  (form GDPR + thông báo các bang của Mỹ), hoặc giới hạn quốc gia phát hành.
- **Families:** app **không được** khai đối tượng gồm trẻ em. App-open ad **bị cấm** trong app thuộc
  chương trình Designed for Families
  ([AdMob](https://support.google.com/admob/answer/9341964?hl=en)). Khi khai Target audience trong
  Play Console, chọn **13+ hoặc 18+**.
- **Nội dung quảng cáo:** chặn các danh mục nhạy cảm (cờ bạc, hẹn hò…) trong AdMob blocking
  controls cho phù hợp với rating.
- **AD_ID:** app target 33+ cần quyền `com.google.android.gms.permission.AD_ID` (thường được merge
  từ SDK quảng cáo) và khai báo Advertising ID trong Console cho khớp với manifest
  ([sdk-best-practices](https://developer.android.com/guide/practices/sdk-best-practices)).

### 4.3 Luật Quảng cáo VN
- **Luật Quảng cáo sửa đổi 2025 (75/2025/QH15)** có hiệu lực **01/01/2026**. Quảng cáo trên mạng
  phải có **dấu hiệu nhận diện** để phân biệt với nội dung, kiểm soát đường dẫn vi phạm (Điều 23)
  ([LuatVietnam](https://luatvietnam.vn/linh-vuc-khac/diem-moi-cua-luat-quang-cao-sua-doi-2025-883-102813-article.html),
  [Thư viện pháp luật](https://thuvienphapluat.vn/van-ban/Thuong-mai/Luat-Quang-cao-sua-doi-2025-so-75-2025-QH15-555035.aspx)).
  **Hành động:** native ad phải có nhãn "Quảng cáo" rõ ràng bằng tiếng Việt, không giả dạng thẻ
  podcast.

---

## 5. Quyền riêng tư: Data safety (Play) và App Privacy (Apple)

### 5.1 Bảng khai báo đề xuất (dựa trên SDK có trong code)

| Loại dữ liệu (Play) | Thu thập? | Chia sẻ? | Nguồn | Mục đích |
|---|---|---|---|---|
| Device or other IDs | **Có** | **Có** (mạng quảng cáo, Meta) | Firebase (app instance ID, FCM token), **ANDROID_ID trong event Analytics**, Advertising ID (AppLovin/AdMob, Facebook SDK) | Analytics, quảng cáo, chống gian lận |
| App interactions / App activity | Có | Có (Meta nếu bật auto app events) | Firebase Analytics (màn hình, sự kiện), Facebook SDK | Analytics, quảng cáo |
| Crash logs, Diagnostics | Có | Không | Crashlytics | Ổn định app |
| Approximate location (suy ra từ IP) | Có thể có, do SDK quảng cáo | Có | AppLovin/AdMob | Quảng cáo |
| Lịch sử nghe, yêu thích | **Chỉ lưu trên máy** (Room/SharedPreferences), nên **không** tính là "thu thập" | — | — | — |
| Tìm kiếm trong app | Gửi tới PodcastIndex để trả kết quả, không gửi Analytics | Xử lý tạm thời bởi bên thứ ba: đánh giá có cần khai hay không | — | Chức năng |

- **Mã hoá khi truyền:** dữ liệu Analytics, Crash và Ads đi qua HTTPS. Tuy nhiên app đang bật
  `usesCleartextTraffic=true` cho toàn app. Cần thu hẹp lại trước khi khai "All data encrypted in
  transit".
- **Yêu cầu xoá dữ liệu:** app không có tài khoản. Vẫn nên có email hoặc form xoá dữ liệu
  analytics và nút "Đặt lại ID quảng cáo/analytics" trong Cài đặt.
- **Chính sách quyền riêng tư:** bắt buộc có link trên store **và trong app** (Play User Data
  policy, Apple 5.1.1(i)). Hiện **chưa có trong app**.

### 5.2 Rủi ro cụ thể
1. **ANDROID_ID gửi kèm mọi event Firebase Analytics** (`device_id`). Đây là định danh bền vững
   của thiết bị. Chính sách User Data của Play yêu cầu khai đúng và không liên kết định danh
   bền vững với Advertising ID cho mục đích quảng cáo khi chưa có đồng ý. Firebase cũng khuyến
   cáo không gửi định danh cá nhân vào event. **Hành động:** bỏ `device_id`, dùng app instance ID
   hoặc Firebase Installation ID có sẵn.
2. **Facebook SDK** mặc định có thể tự log app events và thu advertiser ID. Play SDK Index ghi
   hướng dẫn Data safety của SDK này là "Not provided"
   ([Play SDK Index](https://play.google.com/sdks/details/com-facebook-android-facebook-android-sdk)).
   Nếu không dùng (chưa thấy Facebook Login hay App Events trong code) thì **gỡ SDK**. Nếu dùng
   cho đo lường quảng cáo thì đặt `AutoLogAppEventsEnabled=false` và
   `AdvertiserIDCollectionEnabled=false` cho tới khi có consent, rồi khai Data safety.
3. **Không có consent** cho quảng cáo cá nhân hoá (GDPR/US states), xem 4.2.

### 5.3 Luật Bảo vệ dữ liệu cá nhân VN
- **Luật BVDLCN 91/2025/QH15** và **Nghị định 356/2025/NĐ-CP** có hiệu lực **01/01/2026**, thay Nghị
  định 13/2023. Luật xác lập quyền được biết, đồng ý, truy cập, chỉnh sửa và xoá. Mức phạt có thể
  lên tới **1–5% doanh thu năm trước** (theo một nguồn, **cần xác minh**)
  ([LuatVietnam](https://luatvietnam.vn/dan-su/luat-bao-ve-du-lieu-ca-nhan-2025-co-hieu-luc-khi-nao-568-103652-article.html),
  [EY Legal Alert 03/2026](https://www.ey.com/content/dam/ey-unified-site/ey-com/vi-vn/technical/tax/documents/ey-vietnam-legal-alert-march-2026-decree-no356-2025-nd-cp-providing-detailed-guidance-for-implementation-of-personal-data-protection-law-viet.pdf),
  [PwC](https://www.pwc.com/vn/vn/publications/legal-news-brief/250811-personal-data-protection-law.html)).
- **Hành động:** chính sách quyền riêng tư tiếng Việt nêu rõ dữ liệu, mục đích, bên nhận (Google,
  AppLovin/AdMob, Meta, nhà cung cấp AI) và **chuyển dữ liệu ra nước ngoài**. Màn đồng ý khi mở
  app lần đầu. Đầu mối liên hệ về dữ liệu. Đánh giá nghĩa vụ hồ sơ đánh giá tác động xử lý dữ
  liệu và chuyển dữ liệu xuyên biên giới với luật sư, nhất là khi gửi câu hỏi người dùng tới AI.

### 5.4 iOS: ATT và App Privacy
- Nếu bản iOS dùng IDFA để quảng cáo cá nhân hoá (AdMob, Meta) thì **bắt buộc dùng App Tracking
  Transparency**: gọi `requestTrackingAuthorization` trước khi truy cập IDFA, viết chuỗi
  `NSUserTrackingUsageDescription` bằng tiếng Việt rõ ràng, và không ép người dùng hay đổi
  quyền lợi lấy sự đồng ý. Thứ tự hiển thị: UMP (GDPR) trước, ATT sau (UMP hỗ trợ "IDFA
  explainer").
- App Privacy "nutrition label" phải khớp với bảng 5.1 (Identifiers, Usage Data, Diagnostics,
  Location suy ra từ IP) và đánh dấu "Used to Track You" nếu bật quảng cáo cá nhân hoá.

---

## 6. Xoá tài khoản
- **Play:** nếu app cho tạo tài khoản thì phải có xoá tài khoản **trong app** và **link web** (không
  bắt cài lại app), và khai trong Data safety. Sai khai báo thì bản cập nhật bị từ chối
  ([Play Help 13327111](https://support.google.com/googleplay/android-developer/answer/13327111)).
- **Apple 5.1.1(v):** app có tạo tài khoản thì phải cho **khởi tạo xoá tài khoản ngay trong app**.
- **Apple 4.8 Login Services:** nếu dùng đăng nhập bên thứ ba (Google, Facebook) làm phương thức
  chính thì phải có thêm một lựa chọn đăng nhập bảo vệ quyền riêng tư (ví dụ Sign in with Apple).
- **TSPodcast hiện:** chưa có tài khoản, nên **Pass (N/A)**. Khi làm tính năng M7 (đăng nhập + đồng
  bộ) thì xoá tài khoản phải ra **cùng lúc**.

---

## 7. Nội dung do AI tạo

### 7.1 Google Play: AI-Generated Content policy
- Developer phải đảm bảo app AI tạo sinh **không tạo nội dung xúc phạm** (theo các chính sách
  Inappropriate Content, trẻ em, lừa đảo).
- **Trong phạm vi:** chatbot text-to-text mà chat AI là tính năng trung tâm; tạo ảnh; **tạo
  giọng nói hoặc video AI của người thật**.
- **Ngoài phạm vi (hiện tại):** app **chỉ tóm tắt nội dung không do AI tạo**; app năng suất dùng AI
  để cải thiện tính năng có sẵn
  ([Play Help 14094294](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en)).
- Áp dụng cho TSPodcast:

| Tính năng | Phạm vi | Yêu cầu tối thiểu |
|---|---|---|
| Transcript, tóm tắt, chapters | Ngoài phạm vi | Vẫn nên có nhãn "Do AI tạo" và nút báo lỗi |
| "Hỏi tập này" (chat) | **Có thể trong phạm vi** | Lọc prompt độc hại, chỉ trả lời theo transcript, cơ chế báo cáo câu trả lời trong app, lưu bằng chứng kiểm thử an toàn |
| Bản tin AI (TTS giọng tổng hợp) | Giáp ranh | Nhãn rõ, không giả giọng người thật, biên tập trước khi phát |
| Lồng tiếng bằng giọng host | **Trong phạm vi (giọng người thật)** | Sự đồng ý bằng văn bản của host, watermark hoặc nhãn, chống lạm dụng |

### 7.2 Apple
- **5.1.2(i) (11/2025):** phải **công bố rõ** và **xin phép rõ ràng** trước khi chia sẻ dữ liệu cá
  nhân với "third-party AI"
  ([TechCrunch](https://techcrunch.com/2025/11/13/apples-new-app-review-guidelines-clamp-down-on-apps-sharing-personal-data-with-third-party-ai)).
  Với "Hỏi tập này", câu hỏi của người dùng gửi tới nhà cung cấp LLM, nên cần màn đồng ý lần đầu
  ghi rõ nhà cung cấp.
- **1.1 / 1.2:** nội dung AI vẫn phải tuân thủ quy định nội dung phản cảm. Cần có cơ chế báo cáo.
- **Age rating mới** có câu hỏi về tính năng AI và nội dung không kiểm soát.

---

## 8. Đăng ký trả phí và thanh toán trong app

- **Google Play Payments:** Premium (bỏ quảng cáo, AI không giới hạn) là **hàng hoá số**, nên phải
  dùng **Google Play Billing**. Cần có màn quản lý gói, khôi phục giao dịch, minh bạch giá và chu
  kỳ, huỷ dễ dàng (Subscriptions policy).
- **Apple 3.1.1:** mở khoá tính năng hoặc nội dung trong app phải dùng **IAP**. Tại **storefront
  Mỹ**, sau phán quyết ngày 30/04/2025, app được đặt link mua bên ngoài. Mức phí Apple thu với
  giao dịch qua link đang tranh chấp (vụ kiện còn ở giai đoạn phúc thẩm năm 2026, **chưa xác
  minh**). Ngoài Mỹ (gồm VN) vẫn cấm dẫn ra ngoài
  ([iClarified](https://www.iclarified.com/97192/apple-updates-app-store-rules-to-allow-external-purchase-links-in-us),
  [FKKS](https://fkks.com/news/court-finds-apple-violated-order-resulting-in-key-changes-for-ios-external-purchase-methods)).
- **Apple 3.1.2:** subscription phải mang lại giá trị liên tục. "Bỏ quảng cáo + AI" là hợp lệ.
- **Ủng hộ creator** (tip / Value4Value): Apple 3.1.1 cho phép dùng IAP để tip cho người tạo nội
  dung số. 3.2.1(vii) cho phép người dùng tặng tiền cho cá nhân khác **không qua IAP**, nhưng chỉ
  khi việc tặng hoàn toàn tự nguyện, 100% tiền tới người nhận, và **không gắn với việc nhận nội
  dung hay dịch vụ số**. Nếu có gắn thì phải dùng IAP. Cần đọc toàn văn guideline trước khi làm.

---

## 9. Apple 4.2 (Minimum Functionality) và 4.3 (Spam) với app podcast trên iOS

- **4.3(b), cập nhật 06/2026:** "Don't submit apps that are indistinguishable from what's already
  widely available". Apple có thể từ chối app mới trong các danh mục bão hoà và **gỡ app không được
  cập nhật hoặc không thu hút người dùng**. Nộp lặp lại kiểu này có thể bị loại khỏi Developer
  Program ([MacRumors 06/2026](https://macrumors.com/2026/06/09/app-store-guidelines-low-quality-apps/)).
  Podcast player chưa nằm trong danh sách ví dụ, nhưng là danh mục rất đông (Apple Podcasts có
  sẵn, Overcast, Pocket Casts, Castbox…).
- **4.2.2:** app chủ yếu là trình tổng hợp nội dung hoặc tập hợp link có nguy cơ bị coi là thiếu
  chức năng ([Capgo](https://capgo.app/blog/how-to-pass-app-store-review-2026/)).
- **Cách giảm rủi ro khi nộp bản iOS đầu tiên:**
  1. Nêu rõ khác biệt trong **App Review notes**: radio Việt Nam (kèm văn bản cho phép của đài
     nếu có), AI tiếng Việt (transcript, tóm tắt, hỏi đáp), Kho nhạc RSS, giao diện thuần Việt.
  2. Tính năng native đầy đủ: tải offline, CarPlay, widget, Live Activity/Now Playing, Siri/App
     Intents. **Không** chỉ là WebView.
  3. Không nộp nhiều app gần giống nhau (ví dụ app "Radio VN" riêng và "Podcast VN" riêng dùng
     chung code). Gộp thành một app.
  4. Có tài khoản demo hoặc hướng dẫn nếu có tính năng cần đăng nhập. Để tab Radio hoạt động khi
     review (reviewer ở Mỹ có thể gặp chặn địa lý từ stream VN, nên chuẩn bị video demo).

---

## 10. Các điểm luật Việt Nam khác

| Văn bản | Liên quan khi nào | Ghi chú |
|---|---|---|
| **Nghị định 147/2024/NĐ-CP** (Internet, thông tin trên mạng; hiệu lực 25/12/2024) | Có bình luận, đăng bài hay livestream; tổng hợp tin tức (bản tin AI) | Xác thực số điện thoại VN để đăng hay bình luận; gỡ nội dung trong 24 giờ theo yêu cầu; nghĩa vụ với trang tổng hợp và mạng xã hội. **Chưa áp dụng** cho tính năng hiện tại (chỉ phát nội dung, không có UGC) |
| **Luật BVDLCN 91/2025 + Nghị định 356/2025** | Ngay bây giờ (analytics, quảng cáo), càng quan trọng khi có tài khoản và AI | Xem 5.3 |
| **Luật Quảng cáo sửa đổi 75/2025** | Ngay bây giờ | Nhãn quảng cáo (4.3) |
| **Nghị định 06/2016, 71/2022** (phát thanh, truyền hình) | Tab Radio | Xem 2.3 |
| **Luật Sở hữu trí tuệ** | Radio, nhạc, transcript, dịch, lồng tiếng | Quyền của tổ chức phát sóng; tác phẩm phái sinh |

---

## 11. Checklist tuân thủ (dựa trên code hiện tại)

Ký hiệu trạng thái: **Pass** = đạt · **Risk** = có rủi ro, cần theo dõi hoặc cải thiện ·
**Action** = phải sửa trước release tiếp theo hoặc trước khi ra tính năng liên quan.

| # | Hạng mục | Trạng thái | Bằng chứng | Hành động |
|---|---|---|---|---|
| 1 | Target API 36 (Play, hạn 31/08/2026) | **Pass** | `libs.versions.toml`: `targetSdk = "36"` | Theo dõi API 37 (khoảng 8/2027) |
| 2 | Quyền tối thiểu | **Pass** | Chỉ có INTERNET, POST_NOTIFICATIONS, FGS, FGS_MEDIA_PLAYBACK | Xin POST_NOTIFICATIONS theo ngữ cảnh, không xin lúc mở app |
| 3 | Khai báo foreground service `mediaPlayback` trong Play Console | **Risk** | Manifest đúng; chưa xác minh khai báo trong Console | Kiểm tra mục App content → Foreground service permissions |
| 4 | Chính sách quyền riêng tư trên store **và trong app** | **Action** | Không có link trong app | Thêm màn Cài đặt, link chính sách tiếng Việt + Anh |
| 5 | Data safety khai đúng SDK | **Action** | Firebase, AppLovin, Facebook SDK, ANDROID_ID | Khai theo bảng 5.1; đối chiếu SDK Index |
| 6 | ANDROID_ID trong Analytics | **Action** | `TSAnalytics.initDefaultAttrs()` gửi `device_id` | Bỏ; dùng app instance ID |
| 7 | Facebook SDK không rõ mục đích | **Action** | Có dependency + App ID, không thấy chỗ dùng | Gỡ, hoặc tắt auto-log/advertiser ID tới khi có consent |
| 8 | Consent quảng cáo (GDPR/UMP, US states) | **Action** | Không có CMP | Tích hợp UMP khi chuyển sang AdMob, hoặc giới hạn quốc gia |
| 9 | App-open ad đúng chuẩn (cold start, trên splash) | **Action** | Gọi ở `onResume`, hiện ngay khi load xong | Viết lại theo mục 4.1 |
| 10 | Không hiện quảng cáo chồng lên quảng cáo hay khi đang phát | **Action** | Banner/native có trên Discover, Player; app-open có thể đè lên | Thêm điều kiện chặn |
| 11 | Nhãn "Quảng cáo" cho native ad (Luật QC 75/2025) | **Risk** | Cần kiểm tra UI native ad | Thêm nhãn tiếng Việt rõ ràng |
| 12 | Families / đối tượng trẻ em | **Pass*** | Không nhắm tới trẻ em | *Khai Target audience ≥13 trong Console; không dùng app-open nếu vào Families |
| 13 | Nội dung explicit được lọc hoặc gắn nhãn | **Action** | Có trường `explicit`, UI không dùng | Mặc định ẩn explicit ở Discover, gắn nhãn "E", có công tắc |
| 14 | Content rating (IARC) phản ánh nội dung không kiểm duyệt | **Risk** | — | Trả lời bảng câu hỏi trung thực (nội dung bên thứ ba, explicit) |
| 15 | Điều khoản PodcastIndex 5.5(1) (cache) và 7.2 (ghi công) | **Pass** | Commit `dddd818` (TTL 7 ngày, dòng ghi công) | Đọc lại toàn văn ToS (trang chặn bot); kiểm tra rate limit và User-Agent |
| 16 | Quy trình gỡ nội dung vi phạm (podcast/nhạc) | **Risk** | Blacklist qua Remote Config có sẵn, chưa có kênh tiếp nhận công khai | Thêm "Báo cáo nội dung" + email; SLA 24–48 giờ |
| 17 | Radio VOV/VOH: quyền phát lại | **Action** | Scrape web đài (`RadioRepository`, `VOHRepository`) | Xin phép hoặc hợp tác; ghi nguồn; công tắc tắt theo store/quốc gia; không dùng logo đài trên store |
| 18 | Cleartext traffic | **Risk** | `usesCleartextTraffic="true"` | `network_security_config` chỉ cho phép domain stream cần thiết |
| 19 | Service `PlayerSessionService` exported | **Risk** | `exported=true` + `android:permission="FOREGROUND_SERVICE"` ở app manifest | Kiểm tra lại: Media3 khuyến nghị exported không kèm permission để controller hệ thống (Auto, Wear, Assistant) kết nối; xác thực controller bằng `onConnect` |
| 20 | Xoá tài khoản | **Pass (N/A)** | Không có tài khoản | Bắt buộc ra cùng lúc khi có đăng nhập (Play + Apple 5.1.1(v)) |
| 21 | Không cho tải hay xuất audio của bên thứ ba ra ngoài | **Pass** | Chưa có tính năng tải | Khi làm offline: lưu trong bộ nhớ riêng, không tải nhạc/radio (Apple 5.2.3) |
| 22 | UGC (bình luận) | **Pass (N/A)** | Chưa có | Nếu làm: ToS, lọc, báo cáo, chặn, xác thực SĐT (Nghị định 147) |
| 23 | Chính sách AI (Play AI content, Apple 5.1.2(i)) | **Pass (N/A)** | Chưa có AI | Khi làm: nhãn AI, báo cáo, màn đồng ý gửi dữ liệu cho AI, kiểm thử an toàn prompt |
| 24 | Billing cho Premium | **Pass (N/A)** | Chưa có | Dùng Play Billing / StoreKit IAP |
| 25 | Luật BVDLCN 91/2025 | **Action** | Không có màn đồng ý hay chính sách VN | Chính sách VN, màn đồng ý, đầu mối liên hệ dữ liệu, tư vấn luật về chuyển dữ liệu ra nước ngoài |
| 26 | Backup dữ liệu nhạy cảm | **Pass** | `data_extraction_rules` loại trừ `JsoupExt.xml` | Rà soát khi thêm token đăng nhập |
| 27 | iOS: privacy manifest + Required Reason API | **Pass (N/A)** | Chưa có iOS | Khai UserDefaults/DiskSpace/FileTimestamp; dùng SDK có manifest |
| 28 | iOS: ATT trước khi dùng IDFA | **Pass (N/A)** | Chưa có iOS | UMP rồi đến ATT; mô tả tiếng Việt |
| 29 | iOS: 4.2/4.3 khác biệt | **Risk** | Danh mục podcast bão hoà | Review notes nêu khác biệt; tính năng native; một app duy nhất |
| 30 | iOS: Age rating mới | **Pass (N/A)** | Chưa có iOS | Trả lời bảng câu hỏi; dự kiến 16+/18+ trừ khi lọc explicit |

### Thứ tự ưu tiên xử lý
1. **Trước release Android tiếp theo:** #9, #10, #4, #5, #6, #7, #13.
2. **Khi chuyển sang AdMob:** #8, #11, #12.
3. **Trong 1–3 tháng:** #17 (radio), #16, #18, #19, #25, #3.
4. **Trước khi ra tính năng mới:** #20 (đăng nhập), #22 (bình luận), #23 (AI), #24 (Premium), #21 (offline).
5. **Trước khi nộp iOS:** #27–#30.

---

## 12. Nguồn

- Play target SDK: https://developer.android.com/google/play/requirements/target-sdk
- Play AI-Generated Content: https://support.google.com/googleplay/android-developer/answer/14094294?hl=en
- Play account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- AdMob app open guidance: https://support.google.com/admob/answer/9341964?hl=en
- Disruptive ads (bối cảnh thực thi): https://security.googleblog.com/2020/02/disruptive-ads-enforcement-and-our-new.html?hl=hr
- Apple guideline 06/2026 (4.3, 1.2): https://macrumors.com/2026/06/09/app-store-guidelines-low-quality-apps/
- Apple 5.1.2(i) AI: https://techcrunch.com/2025/11/13/apples-new-app-review-guidelines-clamp-down-on-apps-sharing-personal-data-with-third-party-ai
- Apple Required Reason API: https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api.md
- Apple age rating: https://www.macrumors.com/2025/07/25/apple-overhauls-app-store-age-ratings/
- Apple US external links: https://www.iclarified.com/97192/apple-updates-app-store-rules-to-allow-external-purchase-links-in-us
- Nghị định 147/2024: https://vov.vn/xa-hoi/tu-hom-nay-nguoi-dung-mxh-phai-xac-thuc-moi-duoc-dang-bai-binh-luan-post1144421.vov
- Luật BVDLCN 2025: https://luatvietnam.vn/dan-su/luat-bao-ve-du-lieu-ca-nhan-2025-co-hieu-luc-khi-nao-568-103652-article.html
- Luật Quảng cáo 2025: https://luatvietnam.vn/linh-vuc-khac/diem-moi-cua-luat-quang-cao-sua-doi-2025-883-102813-article.html
- Nghị định 71/2022: https://luatvietnam.vn/thong-tin/nghi-dinh-71-2022-nd-cp-230580-d1.html
- Facebook SDK trên Play SDK Index: https://play.google.com/sdks/details/com-facebook-android-facebook-android-sdk

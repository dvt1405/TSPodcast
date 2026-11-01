# Kế hoạch đưa TSPodcast iOS qua App Store Review

> **Trạng thái: Đang làm** (2026-11-01) — đã có: `PrivacyInfo.xcprivacy`, script kiểm required reason API, review notes cho hub tin (PR #30), metadata App Store 15 ngôn ngữ (PR #46, #48).
> Còn lại: tài khoản Apple Developer, bản ghi App Store Connect, TestFlight, nộp duyệt (§13, §15, §16).

> Thông tin đã kiểm tra ngày 2026-10-29. Apple thay đổi yêu cầu thường xuyên, nên trước mỗi lần nộp phải đọc lại:
> - https://developer.apple.com/news/upcoming-requirements/
> - https://developer.apple.com/app-store/review/guidelines/

Tài liệu này là **kế hoạch thực thi** cho iOS. Phân tích chính sách nền tảng (cả Google Play lẫn Apple, kèm luật Việt Nam) nằm trong [../policy/store-policy-review.md](../policy/store-policy-review.md); khi hai tài liệu khác nhau thì cập nhật cả hai.

## 0. Tóm tắt rủi ro (đọc trước)

| # | Rủi ro | Guideline | Mức | Cách giảm |
|---|---|---|---|---|
| R1 | Tab **Radio** scrape website VOV/VOH và phát lại stream mà chưa có văn bản cho phép | 5.2.1, 5.2.2 | **Cao** | Không đưa vào v1 (D1), **hoặc** có thư cho phép và cung cấp khi Apple yêu cầu |
| R2 | App podcast "na ná" app khác, ít tính năng | 4.2, 4.3(b) | Trung bình–cao | Nhóm tính năng khác biệt ở §9, UI native, CarPlay, curation cho người Việt |
| R3 | Thu thập dữ liệu (Firebase, device id, Facebook SDK, ads) không khai đúng; tracking mà không có ATT | 5.1.1, 5.1.2 | Trung bình | v1 không ads, không FB SDK, bỏ `device_id`; nhãn App Privacy và `PrivacyInfo.xcprivacy` khai đúng (§4, §5) |
| R4 | Màn hình trống hoặc lỗi khi reviewer ở Mỹ test (mạng IPv6, API chậm), placeholder Lorem ipsum | 2.1 | Trung bình | Trạng thái error/empty, test với NAT64, xoá placeholder (§13) |
| R5 | Nội dung bên thứ ba: explicit, cờ bạc, nhạc không rõ bản quyền ("Kho nhạc") | 1.1, 5.2.3, 2.3.6 | Trung bình | Blacklist qua Remote Config, mặc định lọc explicit, age rating trung thực, kênh tiếp nhận takedown (§8, §10) |
| R6 | Push mang tính quảng cáo | 4.5.4 | Thấp (nếu làm theo D4) | Chỉ gửi `new_podcasts` cho người đã opt-in |
| R7 | Dùng API không công khai để set header cho AVPlayer (`AVURLAssetHTTPHeaderFieldsKey`) | 2.5.1 | Thấp | Không dùng (audio-playback-ios.md §6.1) |

## 1. Tài khoản Apple Developer

| | **Individual** | **Organization** (khuyến nghị nếu có pháp nhân) |
|---|---|---|
| Phí | 99 USD/năm (https://developer.apple.com/programs/enroll/) | 99 USD/năm |
| Tên seller trên App Store | Họ tên cá nhân | Tên pháp nhân đã đăng ký (không chấp nhận DBA hay tên thương mại) |
| D-U-N-S | Không cần | **Bắt buộc**. Tra cứu bằng công cụ của Apple; nếu chưa có, D&B cấp trong khoảng tối đa 5 ngày làm việc, cộng tối đa 2 ngày để Apple nhận dữ liệu (https://developer.apple.com/support/D-U-N-S/) |
| Website | Không bắt buộc | Cần website công khai thuộc domain của tổ chức |
| Thành viên team | Một người | Nhiều người, phân quyền được |
| Ảnh hưởng tới review | — | Với app phát nội dung bên thứ ba, tên công ty giúp tăng độ tin cậy khi xin giấy phép nội dung (R1) |

Việc cần làm:

1. Quyết định loại tài khoản ở **tuần 1**. Với Organization, bắt đầu xin D-U-N-S ngay vì có thể mất 1–2 tuần.
2. Bật 2FA cho Apple ID. Ghi nhận Account Holder.
3. App Store Connect → Business:
   - ký **Free Apps Agreement** (tự động);
   - nếu có IAP hoặc app trả phí: **Paid Apps Agreement**, thông tin ngân hàng và thuế (W-8BEN/W-8BEN-E cho cá nhân hoặc tổ chức ngoài Mỹ).
4. **EU DSA trader status** (bắt buộc từ 17/2/2025 với app phân phối ở EU):
   - khai trader hoặc non-trader;
   - nếu là trader thì địa chỉ, email và số điện thoại sẽ hiển thị công khai ở EU;
   - nếu chưa sẵn sàng thì bỏ chọn các storefront EU ở v1.
5. Nếu làm CarPlay: nộp form xin entitlement **CarPlay Audio** ở tuần 1 (audio-playback-ios.md §9).

## 2. Bundle ID và bản ghi App Store Connect

| Trường | Giá trị đề xuất |
|---|---|
| Bundle ID | `tss.t.podcast` (Explicit App ID). Capabilities: Push Notifications, CarPlay Audio (khi được cấp) |
| Tên app (≤ 30 ký tự, 2.3.7) | `TSPodcast – Podcast tiếng Việt` (29 ký tự), hoặc `TSPodcast`. Không dùng tên thương hiệu khác (VOV, VOH, Spotify, Apple Podcasts) |
| Subtitle (≤ 30) | vi: `Nghe podcast, nhạc indie` · en: `Vietnamese podcasts & more` |
| Primary language | Vietnamese |
| SKU | `TSPODCAST-IOS-001` |
| Primary category | **Entertainment** (giống nhiều app podcast) hoặc News. Secondary: Music, chỉ khi giữ "Kho nhạc" |
| Content Rights | "Does your app contain, show, or access third-party content?" → **Yes**. Phải xác nhận có quyền (xem §8) |
| Pricing | Free. Availability: tất cả storefront, hoặc loại EU nếu chưa khai DSA |

## 3. Privacy Policy, Support URL và Marketing URL

- **Privacy Policy URL bắt buộc** với mọi app (5.1.1(i)), và phải truy cập được (không 404, không đòi đăng nhập).
  - Host trên website của dự án ([seo-web-dashboard.md](../plans/seo-web-dashboard.md)), hoặc tạm thời GitHub Pages / Firebase Hosting. Có hai bản vi và en.
  - Bảng dữ liệu thu thập phải thống nhất với [store-policy-review §5](../policy/store-policy-review.md), vì cùng một chính sách phục vụ cả Play Data safety lẫn App Privacy.
  - Thêm link vào màn **Settings/Giới thiệu** trong app.
- Nội dung tối thiểu:
  1. Dữ liệu thu thập: định danh cài đặt ngẫu nhiên (Install ID), sự kiện sử dụng (Firebase Analytics), dữ liệu crash và chẩn đoán (Crashlytics), FCM token, cấu hình (Remote Config).
  2. Mục đích: vận hành app, phân tích, sửa lỗi; nếu có ads thì thêm quảng cáo.
  3. Bên thứ ba: Google/Firebase; PodcastIndex (nhận truy vấn tìm kiếm khi người dùng tìm); máy chủ của publisher podcast (nhận IP khi tải audio hoặc ảnh).
  4. Thời gian lưu và cách xoá dữ liệu: liên hệ email. Dữ liệu cục bộ mất khi gỡ app. Cache API tự xoá sau 7 ngày.
  5. Không bán dữ liệu, không tracking (v1).
  6. Trẻ em: app không nhắm tới trẻ dưới 13 tuổi.
  7. Email liên hệ và địa chỉ (đối với Organization).
- **Support URL**: trang có email hỗ trợ và FAQ, kèm **mục "Báo cáo nội dung / Yêu cầu gỡ bỏ (takedown)"**. Mục này rất quan trọng cho 5.2 (§8).
- Marketing URL: tuỳ chọn. Nơi đặt `app-ads.txt` nếu sau này có AdMob.

## 4. Nhãn App Privacy ("Nutrition Labels")

Định nghĩa (https://developer.apple.com/app-store/app-privacy-details/):

- **Tracking** là liên kết dữ liệu từ app với dữ liệu của bên thứ ba để quảng cáo nhắm mục tiêu hoặc đo lường quảng cáo, hoặc chia sẻ cho data broker.
- Dữ liệu được coi là **linked** trừ khi đã được khử định danh.
- Dữ liệu từ SDK analytics/crash/ads gần như **không bao giờ** thuộc diện "optional disclosure".

### 4.1 Kịch bản v1 (không ads, không FB SDK, không `device_id`)

| Apple data type | Có thu? | Nguồn | Mục đích | Linked to user | Tracking |
|---|---|---|---|---|---|
| Identifiers → **Device ID** | Có | Install ID (UUID ngẫu nhiên, dùng làm Crashlytics user id), Firebase Installation ID, FCM token | App Functionality, Analytics | **Có** (khai thận trọng vì Install ID gắn vào crash report). Nếu bỏ `Crash.userId` thì có thể khai "Không" | Không |
| Usage Data → **Product Interaction** | Có | Firebase Analytics (screen, onboarding, play…) | Analytics | Có (cùng lý do) | Không |
| Diagnostics → **Crash Data** | Có | Crashlytics | App Functionality | Có | Không |
| Diagnostics → **Performance Data** | Có (nếu bật Performance Monitoring hoặc Analytics thu) | Firebase | Analytics | Có | Không |
| Diagnostics → **Other Diagnostic Data** | Có | Crashlytics custom key/log (`report_tag`, `radio_repo`…) | App Functionality | Có | Không |
| Location → **Coarse Location** | **Kiểm tra** | Google Analytics có thể suy vị trí gần đúng từ IP. Đối chiếu tài liệu "Google Analytics data disclosure for Apple" | Analytics | — | Không |
| Search History | **Không**, nếu không log chuỗi tìm kiếm vào Analytics. Truy vấn gửi tới PodcastIndex chỉ để phục vụ yêu cầu ngay lúc đó | — | — | — | — |
| Contact info, Health, Financial, Contacts, User Content, Browsing History, Purchases | Không | — | — | — | — |

### 4.2 Kịch bản có ads (AdMob/AppLovin, v1.1)

Thêm vào bảng trên:

- **Device ID**: thêm mục đích **Third-Party Advertising**, **Tracking = Có** (IDFA khi người dùng đồng ý ATT).
- **Advertising Data** (Usage Data): Third-Party Advertising, Tracking = Có.
- **Product Interaction**: thêm Third-Party Advertising.
- **Coarse Location**: Third-Party Advertising, theo khai báo của ad SDK.
- Bắt buộc có **ATT prompt** (§6) và `NSPrivacyTracking = true` trong manifest; tracking domain lấy từ privacy manifest của SDK.

> Thông tin bổ sung về SDK: Firebase công bố dữ liệu từng SDK thu tại https://firebase.google.com/docs/ios/app-store-data-collection. Trong đó: Crashlytics thu stack trace, trạng thái app và thông tin thiết bị/OS; FCM thu APNs token và model, ngôn ngữ, múi giờ; Remote Config thu quốc gia, ngôn ngữ, OS.

## 5. `PrivacyInfo.xcprivacy`

- Từ 1/5/2024, mọi bản upload phải khai **approved reasons** cho các API thuộc danh sách "required reason", **kể cả** API mà SDK bên thứ ba dùng.
- Framework Kotlin `Shared` được link **static**, nên manifest của **app** phải bao luôn các API mà Kotlin/Native runtime và các thư viện KMP (Ktor, Okio, DataStore, Room/SQLite) dùng.
- JetBrains có script quét binary và hướng dẫn tại https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html. Theo hướng dẫn đó, `stat`/`fstat` thuộc FileTimestamp và `mach_absolute_time` thuộc SystemBootTime.
- **Chạy script này trên bản archive** và đối chiếu email cảnh báo `ITMS-91053` sau mỗi lần upload TestFlight.

Mẫu cho v1 (điều chỉnh theo kết quả quét):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>NSPrivacyTracking</key><false/>
  <key>NSPrivacyTrackingDomains</key><array/>
  <key>NSPrivacyCollectedDataTypes</key>
  <array>
    <dict>
      <key>NSPrivacyCollectedDataType</key><string>NSPrivacyCollectedDataTypeDeviceID</string>
      <key>NSPrivacyCollectedDataTypeLinked</key><true/>
      <key>NSPrivacyCollectedDataTypeTracking</key><false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string>
             <string>NSPrivacyCollectedDataTypePurposeAnalytics</string></array>
    </dict>
    <dict>
      <key>NSPrivacyCollectedDataType</key><string>NSPrivacyCollectedDataTypeProductInteraction</string>
      <key>NSPrivacyCollectedDataTypeLinked</key><true/>
      <key>NSPrivacyCollectedDataTypeTracking</key><false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array><string>NSPrivacyCollectedDataTypePurposeAnalytics</string></array>
    </dict>
    <dict>
      <key>NSPrivacyCollectedDataType</key><string>NSPrivacyCollectedDataTypeCrashData</string>
      <key>NSPrivacyCollectedDataTypeLinked</key><true/>
      <key>NSPrivacyCollectedDataTypeTracking</key><false/>
      <key>NSPrivacyCollectedDataTypePurposes</key>
      <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
    </dict>
    <!-- PerformanceData, OtherDiagnosticData tương tự -->
  </array>
  <key>NSPrivacyAccessedAPITypes</key>
  <array>
    <dict> <!-- @AppStorage / UserDefaults cho cài đặt của app -->
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryUserDefaults</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>CA92.1</string></array>
    </dict>
    <dict> <!-- metadata file trong container: download, DB, DataStore (Okio/K/N stat) -->
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>C617.1</string></array>
    </dict>
    <dict> <!-- đo thời gian trôi (Kotlin TimeSource/mach_absolute_time) -->
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategorySystemBootTime</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>35F9.1</string></array>
    </dict>
    <dict> <!-- kiểm tra dung lượng trước khi download; hiển thị dung lượng -->
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryDiskSpace</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>E174.1</string><string>85F4.1</string></array>
    </dict>
  </array>
</dict></plist>
```

- Firebase và Google SDK (qua SPM) tự mang manifest riêng; Xcode gộp lại khi archive. Kiểm tra bằng Xcode → Organizer → archive → **Generate Privacy Report**.
- `NSPrivacyCollectedDataTypes` trong manifest và nhãn trên App Store Connect phải **khớp nhau**.

## 6. ATT (chỉ khi có ads hoặc tracking)

- Không có tracking (v1) thì **không** gọi ATT và **không** khai `NSUserTrackingUsageDescription`. Gọi ATT khi không tracking cũng dễ bị hỏi lại trong review.
- Khi có ads (v1.1):
  - `NSUserTrackingUsageDescription` (vi): *"TSPodcast dùng định danh quảng cáo để hiển thị quảng cáo phù hợp hơn và giữ ứng dụng miễn phí. Bạn vẫn dùng đầy đủ tính năng nếu từ chối."*
  - (en): *"TSPodcast uses your advertising identifier to show more relevant ads and keep the app free. All features remain available if you decline."*
  - Thứ tự: UMP → (màn giải thích tuỳ chọn) → ATT → khởi tạo ad SDK (setup-and-build.md §7).
  - Không gọi ATT trong onboarding đầu tiên khi người dùng chưa hiểu app. Không thưởng hay phạt theo lựa chọn (5.1.2).
  - Hành vi "dark pattern" (nút giả, màn chặn) sẽ bị reject.

## 7. Export compliance

- App chỉ dùng HTTPS/TLS của hệ thống và hash SHA-1 cho header auth (không phải mã hoá dữ liệu), nên được miễn trừ. Đặt `ITSAppUsesNonExemptEncryption = NO` trong Info.plist để App Store Connect không hỏi lại mỗi build.
- Nếu sau này thêm thuật toán mã hoá riêng (ví dụ mã hoá file download bằng thuật toán tự viết), phải xem lại và có thể cần nộp tài liệu.

## 8. Bản quyền nội dung (guideline 5.2)

App **không sở hữu** nội dung, nên đây là phần cần chuẩn bị kỹ nhất.

| Nguồn | Cơ sở pháp lý / điều khoản | Việc phải làm | Rủi ro |
|---|---|---|---|
| **PodcastIndex API** (metadata, tìm kiếm) | Điều khoản PodcastIndex cho phép app dùng API, kèm nghĩa vụ attribution và không lưu bản sao lâu hơn cache header cho phép (đã thể hiện trong code: `attribution_podcastindex`, `PruneApiCache`) | Giữ dòng attribution trên iOS. Dùng API key riêng cho iOS. Lưu bản điều khoản hiện hành (PDF) để gửi Apple khi được hỏi theo 5.2.2 ("Authorization must be provided upon request") | Thấp |
| **Audio podcast** (enclosure RSS của publisher) | Publisher tự phát hành công khai qua RSS cho mọi podcast app. Đây là mô hình ngành, Apple Podcasts cũng vận hành như vậy | Luôn hiển thị tên podcast/publisher và link tới feed/website gốc. Không chỉnh sửa, không gắn quảng cáo đè lên audio. Có **kênh takedown**: email trên Support URL, và chặn feed bằng Remote Config `ts_podcast_black_list` (không cần nộp lại app) | Thấp–trung bình |
| **"Kho nhạc"** (feed `podcast:medium=music`) | Feed do nghệ sĩ hoặc publisher tự phát hành theo chuẩn Podcasting 2.0 | Ghi rõ trong UI: "Feed nhạc do nghệ sĩ tự phát hành qua RSS" (đã có phụ đề). Cùng cơ chế takedown như trên. **Không** gọi là "nghe nhạc miễn phí" hay "top nhạc" trong metadata. Không có tính năng tải xuống cho nhạc nếu không chắc về quyền | Trung bình: reviewer có thể hỏi theo 5.2.1/5.2.3. Chuẩn bị câu trả lời ở §14 |
| **Radio VOV/VOH** | Hiện **không có** thỏa thuận. Code scrape HTML (`RadioRepository`, `VOHRepository`) và phát lại stream. Về quyền của tổ chức phát sóng và Nghị định 71/2022/NĐ-CP, xem [store-policy-review §2.3](../policy/store-policy-review.md) (cần luật sư xác nhận) | **Không đưa vào v1** (D1). Nếu muốn làm: xin văn bản cho phép từ VOV/VOH, hoặc dùng nguồn có giấy phép. Đưa danh sách kênh lên server. Cung cấp văn bản trong App Review Information → Attachment. Không dùng logo hay tên đài làm hình ảnh chính trong screenshot/metadata nếu chưa có phép (5.2.1) | **Cao**: thường bị reject 5.2.2 và có thể bị chủ sở hữu khiếu nại |
| Ảnh cover | Thuộc publisher, hiển thị kèm nội dung | Không dùng ảnh cover của podcast nổi tiếng làm hình chính trong screenshot App Store. Dùng podcast cho phép, nội dung demo, hoặc làm mờ | Trung bình (2.3.3, 5.2.1) |

Lưu ý thêm:

- **Cờ bạc:** dữ liệu PodcastIndex có các feed spam cờ bạc tiếng Việt, và `blacklist.json` đã lọc. Reviewer có thể tìm "casino", "bet". Hãy kiểm tra trước khi nộp. Nếu Remote Config chưa fetch xong, asset mặc định phải lọc được ngay từ lần mở đầu tiên.
- **Explicit:** mặc định gửi `clean=true` cho search và lọc `explicit` ở danh sách. Người dùng tự bật lại trong Settings (D6).

## 9. Guideline 4.2 (Minimum Functionality) và 4.3 (Spam): làm app khác biệt

App podcast là thể loại "saturated". Theo 4.3(b), app không được "indistinguishable from what's already widely available"; theo 4.2, app phải "app-like" và hữu ích.

[store-policy-review §9](../policy/store-policy-review.md) ghi nhận đợt cập nhật 4.3(b) tháng 06/2026: Apple có thể từ chối app mới trong danh mục bão hoà và gỡ app không được cập nhật. Danh sách tính năng khác biệt dưới đây lấy từ gap analysis trong [competitor-analysis.md §5–6](../../done/research/competitor-analysis.md). TSPodcast nên làm nổi bật những điểm sau:

| Điểm khác biệt | Đã có trên Android | iOS v1 |
|---|---|---|
| Tập trung người nghe Việt: trending và recent theo `lang=vi`, onboarding chọn danh mục yêu thích, gợi ý cá nhân "Có thể bạn yêu thích" | ✔ | ✔ |
| Lọc spam cờ bạc và nội dung độc hại cập nhật từ xa (blacklist) | ✔ | ✔ (nêu trong review notes) |
| Kho feed nhạc độc lập theo chuẩn Podcasting 2.0 (`podcast:medium`) | ✔ | ✔ / tuỳ R5 |
| Tốc độ phát, sleep timer, nhớ vị trí nghe, skip ± | ✘ | **✔ phải có** |
| Download offline | ✘ | ✔ khuyến nghị |
| CarPlay, Now Playing đầy đủ, AirPlay | (Android Auto ✘) | ✔ (CarPlay tuỳ entitlement) |
| App Intents / Siri ("tiếp tục nghe") | ✘ | Nên có |
| Giao diện iOS 26 native (Liquid Glass, Dynamic Type, VoiceOver, Dark Mode) | — | ✔ |

Quy tắc chống 4.3(a):

- **Một** bundle ID duy nhất. Không nộp nhiều biến thể (ví dụ app radio riêng, app nhạc riêng) dùng chung code.
- Không clone template app podcast có sẵn.

## 10. Age rating (hệ thống mới)

Từ 2025, Apple dùng các mức **4+, 9+, 13+, 16+, 18+**. Bộ câu hỏi mới gồm in-app controls, capabilities, medical/wellness, violent themes. Hạn trả lời cho các app cũ là 31/1/2026 (https://developer.apple.com/news/?id=ks775ehf và trang upcoming requirements).

Cách trả lời đề xuất cho TSPodcast:

| Nhóm câu hỏi | Trả lời | Ghi chú |
|---|---|---|
| Profanity / crude humor | Infrequent/Mild | Podcast của bên thứ ba có thể có |
| Mature / suggestive themes | Infrequent/Mild | Có lọc explicit mặc định |
| Horror / fear, violence | Infrequent/Mild hoặc None | Có podcast truyện kinh dị, true crime |
| Alcohol, tobacco, drugs | Infrequent/Mild | |
| Gambling (simulated/real) | None | Đã chặn bằng blacklist |
| Medical / wellness | None (không đưa ra lời khuyên y tế) | |
| User-generated content | **No** | App không cho người dùng đăng nội dung |
| Messaging / chat | No | |
| Unrestricted web access | **No**, nếu link mở bằng Safari/`SFSafariViewController`. Không nhúng trình duyệt tự do | |
| Advertising | No (v1) / Yes (v1.1) | |
| In-app parental controls | Có, nếu làm toggle explicit có khoá | Có thể giúp đạt mức thấp hơn |

- Kết quả dự kiến: **16+**. Nếu bỏ lọc explicit thì nên chọn **18+**.
- Chọn mức thấp hơn thực tế có rủi ro bị reject theo 2.3.6 (age rating không chính xác), hoặc bị reviewer tự nâng mức.
- Có thể đặt "minimum age" cao hơn nếu chính sách nội bộ yêu cầu.

## 11. Account deletion và IAP

- **5.1.1(v), account deletion:** **không áp dụng**, vì app không có tài khoản (`grep` không thấy FirebaseAuth hay login).
  - Nếu sau này thêm đăng nhập (đồng bộ favourite): phải có **xoá tài khoản ngay trong app** (xoá cả dữ liệu trên server), và có **Sign in with Apple** nếu có login bằng Google hoặc Facebook (4.8).
  - Khi đó cần cung cấp demo account trong review notes.
- **IAP (nếu có gói Premium, ví dụ bỏ quảng cáo hoặc download không giới hạn):**
  - Dùng **StoreKit 2** với auto-renewable subscription (3.1.1).
  - Paywall phải hiện rõ giá, chu kỳ, gia hạn tự động, link **Terms of Use (EULA)** và **Privacy Policy**, cùng nút **Restore Purchases** (3.1.2).
  - Ký Paid Apps Agreement. Đăng ký **App Store Small Business Program** (hoa hồng 15%).
  - Không dẫn người dùng ra web để mua, trừ ngoại lệ mà guideline 3.1.1(a)/3.1.3 hiện hành cho phép theo storefront. Quy định này thay đổi theo phán quyết pháp lý (đặc biệt ở Mỹ và EU), nên phải đọc lại trước khi làm.
  - Tạo sẵn subscription và nộp kèm build đầu tiên. Ghi trong review notes cách mở paywall.
  - Cấu hình **sandbox tester** và StoreKit Configuration file để test.

## 12. Metadata và screenshot

### 12.1 Văn bản (vi + en-US)

- **Promotional text** (≤ 170), **Description** (≤ 4000), **Keywords** (≤ 100 byte, phân tách bằng dấu phẩy, không lặp tên app, không dùng tên thương hiệu khác), **What's New**.
- Description phải mô tả đúng tính năng **đang có trong build**. Không nhắc "Android", không ghi "sắp có" (2.3.1, 2.3.10).
- Gợi ý keywords (vi): `podcast,nghe,tiếng việt,radio,audio,sách nói,tin tức,kể chuyện,nhạc,offline`. Nếu bỏ Radio thì bỏ từ "radio".

### 12.2 Screenshot (https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)

| Nhóm thiết bị | Kích thước (portrait) | Yêu cầu theo trang Apple (10/2026) | Kế hoạch |
|---|---|---|---|
| iPhone Dynamic Island, **large** (6.9") | 1320×2868 (hoặc 1290×2796, 1260×2736) | Không bắt buộc riêng; nếu thiếu thì Apple dùng ảnh scale từ nhóm Face ID large | **Cung cấp** 1320×2868, 5–8 ảnh |
| iPhone Face ID, large (6.5") | 1284×2778 hoặc 1242×2688 | Bắt buộc nếu không có nhóm Dynamic Island large | Bỏ qua (đã có 6.9") |
| iPhone Dynamic Island, **medium** (6.1"/6.3") | 1206×2622 hoặc 1179×2556 | **"At least one screenshot"** theo mục Required device sizes | **Cung cấp** 1206×2622 |
| iPad 13" | 2064×2752 hoặc 2048×2732 | Bắt buộc nếu app chạy trên iPad | Chỉ khi bật iPad |

- Định dạng JPEG hoặc PNG, **không có alpha channel**. Tối đa 10 ảnh mỗi nhóm.
- Kiểm tra lại yêu cầu trên App Store Connect lúc upload, vì Apple vừa đổi nhóm "required".
- Nội dung đề xuất (theo thứ tự):
  1. Home/Discover;
  2. Player kèm Lock Screen hoặc CarPlay;
  3. Chọn danh mục yêu thích;
  4. Tìm kiếm;
  5. Tốc độ phát và sleep timer;
  6. Download offline;
  7. Favourites.
- Dùng **ảnh chụp từ app thật** (fastlane snapshot). Được thêm caption và khung thiết bị **Apple**, không dùng khung Android.
- Không dùng artwork của podcast nổi tiếng hoặc logo đài khi chưa có phép (§8).
- App Preview video là tuỳ chọn: 15–30 giây, quay từ thiết bị.

## 13. TestFlight

1. **Internal testing** (tối đa 100 thành viên trong App Store Connect, không cần review):
   - upload từ lane `beta`;
   - mời team và QA;
   - chạy checklist audio (audio-playback-ios.md §11) và checklist parity (features-spec.md §7).
2. **External testing** (tối đa 10.000 tester, qua Beta App Review, thường mất 1–2 ngày):
   - dùng như **buổi diễn tập review**: điền đầy đủ "What to Test", Beta App Description, email feedback và privacy policy;
   - nếu bị từ chối thì đây là tín hiệu sớm trước khi nộp chính thức.
3. **Thử trong điều kiện của reviewer:**
   - **mạng IPv6-only (NAT64)**: macOS → System Settings → Sharing → Internet Sharing, tích "Create NAT64 Network". Apple review trên mạng IPv6, nên không được hardcode IPv4;
   - region và ngôn ngữ **English (US)**: app vẫn có nội dung (trending `lang=vi` vẫn có kết quả) và chuỗi UI hiển thị tiếng Anh;
   - lần chạy đầu tiên khi **Remote Config chưa fetch** và khi **mạng tắt**;
   - thiết bị cũ nhất hỗ trợ (iOS 17) và mới nhất (iOS 26.x); nếu bật iPad thì test iPad.
4. Theo dõi Crashlytics với crash-free users ≥ 99.5% trước khi nộp. Tham khảo các nhóm crash đã gặp trên Android tại [../../done/crashlytics/2026-10-crash-triage.md](../../done/crashlytics/2026-10-crash-triage.md): null/lifecycle, thiếu native lib. Shared code chuyển sang iOS mang theo những bản sửa đó, nên cần viết unit test chặn hồi quy.

## 14. App Review Information: review notes và demo account

- **Demo account:** không cần (không có login). Ghi rõ điều này.
- **Contact:** tên, số điện thoại và email của người phản hồi được trong 24 giờ.
- **Attachment:** điều khoản PodcastIndex (PDF). Nếu có Radio thì thêm thư cho phép của VOV/VOH. Khi đã có, kèm email xác nhận của các toà soạn trong "Thời sự & Bản tin" (M6 §7 mục 1) — chỉ gửi khi Apple hỏi nếu owner muốn giữ kín.
- **"Thời sự & Bản tin" bật trong bản review** (M6 §3.4): Release chỉ hiện nguồn có `confirmedAt`, nên trước khi archive phải chạy `iosApp/scripts/check-news-hub-gate.sh` (§16) và đạt ngưỡng; dưới ngưỡng thì owner quyết, bỏ hub thì **gỡ khỏi binary**, không ẩn trong bản review. Đoạn "News & Bulletins" dưới đây chỉ giữ khi hub có trong build.
- **Notes** (tiếng Anh):

```
TSPodcast is a podcast player focused on Vietnamese listeners. No account or login is required.

Content source: podcast metadata comes from the Podcast Index API (podcastindex.org), used under its
terms of service (attribution shown at the bottom of the Discover tab; cached data expires after 7 days).
Audio is streamed directly from each publisher's public RSS enclosure URL. We do not host, modify, or
re-encode any audio. The "Music library" row lists RSS feeds that artists publish themselves using the
podcast:medium=music tag.

Content safety: we filter spam and gambling feeds with a remotely-updated blocklist, and explicit
episodes are hidden by default (Settings > Content). Rights holders can request removal via
<support URL>; removals are applied server-side without an app update.

News & Bulletins ("Thời sự & Bản tin", on the Discover tab): a curated list of on-demand podcast
episodes that Vietnamese news organisations publish themselves through their own public podcast
RSS feeds. The app finds those feeds through the Podcast Index API and plays each episode from the
publisher's own audio URL; we do not stream live broadcasts, and we do not copy, re-host or modify
any audio. The "Morning brief" only queues the newest episodes of those feeds in a fixed order;
titles and descriptions are the publishers' own. Written confirmation from the publishers is
available on request. Note: the description of the VOV "Thời sự" feed contains the word "trực
tiếp" ("live"); its episodes are recordings published after broadcast, not a live stream. Every
screen of the section has a "Report / request removal" link, and a source can be removed remotely
without an app update. The section is enabled in this build, so it is visible during review.

Background audio: used only to continue playback of the podcast the user started (Lock Screen /
Control Center controls).

How to review:
1. Complete the 4-page onboarding and pick any 3 categories. A privacy choice appears first:
   either button continues into the app.
2. Discover tab: tap any podcast > tap an episode to play. Lock the device to verify background playback.
3. Player: playback speed, sleep timer, skip back 15 s / forward 30 s.
4. Discover tab > "Thời sự & Bản tin" (right below the trending row): "Play all" plays the
   morning brief; "See all" opens the section with Latest, Sources and Bedtime stories (these set
   a 30-minute sleep timer).
5. Search tab: try "technology" or "history".
The UI language follows the device language (Vietnamese / English).

Privacy: Firebase Analytics and Crashlytics run only after the user agrees on the first-launch
privacy screen (both are off by default and can be changed in Settings > Privacy). They use a
random per-install ID. No tracking, no advertising SDKs, no IDFA access in this version.
```

Ghi chú cho bản notes trên (M6 T4):

- Bỏ bước "Download an episode": v1 không có tải tập (features-spec §3.5 xếp Download vào [iOS+], ngoài phạm vi), reviewer bấm không thấy sẽ ghi 2.1. Bỏ "CarPlay" khỏi câu background audio vì v1 không có app CarPlay (chưa xin entitlement); thêm lại khi có.
- Đoạn "trực tiếp": mô tả feed VOV Thời sự trên PodcastIndex có chữ "trực tiếp" (chữ của nhà phát hành, app hiển thị nguyên văn trên Podcast detail) — nói trước để reviewer không hiểu nhầm là phát live (5.2.3). Chữ của ta trong hub không có Radio/Đài/Phát thanh/Trực tiếp/Live/Kênh (`iosApp/scripts/check-news-strings.sh`, `NewsHubStringsTests`).
- "Written confirmation … on request": chỉ để câu này khi owner đã có email xác nhận của các toà soạn có `confirmedAt` (cổng §16). Chưa có thì bỏ câu và không bật nguồn đó.
- "Report / request removal" chỉ hiện khi `CONTENT_REPORT_EMAIL` có giá trị (handoff); thiếu thì bỏ câu này hoặc điền email trước khi archive.
- Dòng Privacy theo consent lite (M6 §3.6): trước đây ghi như thể Analytics/Crashlytics luôn chạy.

### Các lý do reject thường gặp và câu trả lời chuẩn bị sẵn

| Lý do (guideline) | Phòng ngừa | Trả lời nếu bị hỏi |
|---|---|---|
| 2.1 Crash, màn hình trống, nút không hoạt động (ví dụ nút "More" ở Player) | Error/empty state cho mọi section; ẩn nút chưa có chức năng; test IPv6 | Gửi build sửa kèm mô tả bước kiểm tra |
| 2.1 Placeholder (Lorem ipsum `detail_placeholder`, `Podcast.default`) | Không ship chuỗi giả; chỉ dùng trong Preview | — |
| 2.3.3 Screenshot không giống app | Chụp từ build thật | — |
| 2.3.10 Nhắc tới nền tảng khác | Không ghi "Android" trong metadata hay UI | — |
| 2.5.1 API không công khai | Không dùng `AVURLAssetHTTPHeaderFieldsKey`; chạy kiểm tra symbol | — |
| 2.5.4 Lạm dụng background mode | Chỉ dùng `audio` | "Background audio is used solely for user-initiated podcast playback." |
| 4.2 / 4.3(b) Ít chức năng hoặc trùng lặp | §9 | Liệt kê tính năng khác biệt, nhấn mạnh tập trung người Việt và lọc nội dung |
| 4.5.4 Push quảng cáo | Chỉ push `new_podcasts` cho người đã opt-in, có toggle tắt | — |
| 5.1.1 Thiếu privacy policy, nhãn sai | §3, §4, §5 khớp nhau | — |
| 5.1.2 Tracking không có ATT (do SDK) | Không đưa FB SDK; kiểm tra Privacy Report | — |
| 5.2.1 / 5.2.2 Nội dung bên thứ ba | §8, tài liệu đính kèm | "Metadata via Podcast Index API under its ToS (attached); audio streamed from publishers' public RSS enclosures; takedown process at <URL>." |
| 5.2.3 Tải media | Chỉ tải enclosure podcast (publisher phát hành để tải), không tải hoặc ghi radio | "Downloads are limited to RSS enclosures that publishers distribute for offline listening, identical to the standard podcast ecosystem." |
| ITMS-91053 thiếu required reason | §5, quét binary | — |
| Guideline 1.1 / 1.4 nội dung phản cảm | Blacklist, lọc explicit | Gỡ feed qua Remote Config trong vài giờ |

Nếu bị reject:

- Trả lời trong **Resolution Center**: lịch sự, dẫn đúng guideline, đính kèm bằng chứng.
- Nếu cần sửa thì nộp build mới.
- Nếu cho rằng reviewer hiểu sai thì gửi **appeal** tới App Review Board.

## 15. Timeline và milestone (bắt đầu 02/11/2026)

| Tuần | Thời gian | Milestone | Đầu ra |
|---|---|---|---|
| T1 | 02–06/11 | **M0: Khởi động** | Đăng ký Developer Program (D-U-N-S nếu Organization); chốt D1–D7; nộp form CarPlay entitlement; xin API key PodcastIndex riêng cho iOS; viết nháp privacy policy |
| T1–T4 | 02–27/11 | **M1: Shared KMP** | Module `shared` (model, Ktor, Room KMP, DataStore, use case, store); Android chạy trên shared; `iosSimulatorArm64Test` xanh |
| T3–T5 | 16/11–04/12 | Khung iOS | Xcode project, DI bridge, SKIE, Firebase, CI GitHub Actions, fastlane `test`/`beta` |
| T5–T8 | 30/11–25/12 | **M2: Alpha nội bộ** | Onboarding, Home, Detail, Player (background, Now Playing, remote command), MiniPlayer → **TestFlight internal** trước 18/12 |
| — | ~23–27/12 | Nghỉ lễ của Apple | App Store Connect thường tạm dừng nhận nộp review vào dịp Giáng sinh (kiểm tra thông báo hằng năm). TestFlight internal vẫn hoạt động |
| T9–T11 | 28/12–15/01 | **M3: Feature complete** | Search, Favourite, tốc độ phát, sleep timer, resume position, download, CarPlay (nếu đã được cấp), localization vi/en, Dark Mode, accessibility |
| T12 | 18–22/01 | **M4: Compliance** | `PrivacyInfo.xcprivacy` + Privacy Report, nhãn App Privacy, age rating, export compliance, privacy policy và Support URL online, screenshot + metadata (fastlane `metadata`) |
| T13 | 25–29/01 | **M5: External TestFlight + nộp review** | Beta App Review (diễn tập). Nếu ổn thì **Submit for Review** chậm nhất 29/01, kèm "Manual release" |
| — | ~04–12/02/2027 | **Tết Nguyên Đán** (mùng 1 khoảng 06/02/2027) | Không đặt release vào dịp này. Người trực Resolution Center phải sẵn sàng phản hồi trong 24 giờ |
| T14–T15 | 01–12/02 | Đệm review | Xử lý reject (nếu có); review thường mất 24–48 giờ mỗi lượt |
| T16 | 15–19/02 | **M6: Phát hành** | Bật **Phased Release** (7 ngày); theo dõi Crashlytics và đánh giá; chuẩn bị 1.0.1 |

Lưu ý:

- Mốc SDK tối thiểu của Apple thường đổi vào tháng 4. Nếu release trễ sang khoảng tháng 4/2027, có thể phải build bằng Xcode/SDK mới hơn (dự đoán theo chu kỳ, **cần xác minh**).
- Mỗi lần nộp lại sau reject tốn thêm 1–3 ngày.
- **Cổng "Thời sự & Bản tin" (M6):** trước mọi lần archive bản nộp review/TestFlight external, chạy `iosApp/scripts/check-news-hub-gate.sh` (thêm đường dẫn JSON nếu Remote Config `news_hub_v1` đang có giá trị). Phải in `PASS`; hiện tại in `confirmed news=0 night=0` và thoát 1 vì chưa nguồn nào có `confirmedAt`. Hạn: email xác nhận của toà soạn + ý kiến luật sư (L4) trước code freeze 16/12/2026.

## 16. Checklist cuối trước khi bấm "Submit for Review"

**Tài khoản và bản ghi**

- [ ] Developer Program còn hạn; Free/Paid Apps Agreement đã ký; DSA trader status đã khai (hoặc đã loại EU)
- [ ] Bundle ID `tss.t.podcast`, capabilities đúng; CarPlay chỉ bật khi đã có entitlement

**Build**

- [ ] Build bằng **Xcode 26.x trở lên** với iOS 26 SDK; deployment target iOS 17.0
- [ ] `ITSAppUsesNonExemptEncryption = NO`; `UIBackgroundModes` chỉ gồm `audio` (và `remote-notification` nếu cần)
- [ ] Không có secret trong repo; `Secrets.xcconfig` được inject từ CI; key PodcastIndex dành riêng cho iOS
- [ ] Không có Facebook SDK, không có ad SDK (v1); không link `AdSupport`
- [ ] Crashlytics nhận được crash Swift và Kotlin (CrashKiOS); dSYM đã upload
- [ ] Không còn Lorem ipsum, "Opps!", "Kết qủa"; không còn nút không có chức năng

**Privacy**

- [ ] Privacy Policy URL (vi/en) hoạt động và có link trong app (Settings)
- [ ] Nhãn App Privacy khớp `PrivacyInfo.xcprivacy` và hành vi thực tế (§4)
- [ ] Organizer → Generate Privacy Report không có mục lạ; không còn email ITMS-91053
- [ ] Notification permission chỉ xin theo ngữ cảnh; push quảng cáo đã tắt

**Nội dung**

- [ ] Radio đã bị loại khỏi v1, **hoặc** đã có thư cho phép đính kèm
- [ ] Attribution PodcastIndex hiển thị; cache prune 7 ngày chạy trên iOS
- [ ] Blacklist lọc được các từ khoá cờ bạc ngay ở lần chạy đầu (asset fallback)
- [ ] Explicit filter mặc định bật; age rating đã trả lời theo §10
- [ ] `iosApp/scripts/check-news-hub-gate.sh` in `PASS` (≥ 2 nguồn tin + kệ đêm, hoặc ≥ 3 nguồn tin có `confirmedAt`); không đạt thì hub đã được gỡ khỏi binary và đoạn "News & Bulletins" đã bỏ khỏi review notes
- [ ] `iosApp/scripts/check-news-strings.sh` không vi phạm (không Radio/Đài/Phát thanh/Trực tiếp/Live/Kênh trong chữ của hub)
- [ ] Support URL có mục takedown

**Chất lượng**

- [ ] Test IPv6/NAT64, offline, English (US) region, iOS 17 và iOS 26, (iPad nếu bật)
- [ ] Checklist audio §11 và parity §7 đạt 100%
- [ ] Crash-free ≥ 99.5% trên TestFlight

**Metadata**

- [ ] Tên ≤ 30 ký tự, subtitle, description, keywords (vi + en-US), What's New
- [ ] Screenshot: iPhone Dynamic Island large (1320×2868) và medium (1206×2622), cùng iPad 13" nếu bật; không alpha; ảnh chụp từ build thật
- [ ] Content Rights: Yes, có quyền; category Entertainment
- [ ] Review notes (§14), contact và attachment đầy đủ
- [ ] Release option: Manual hoặc Phased

## Nguồn

- Apple — Upcoming requirements: https://developer.apple.com/news/upcoming-requirements/
- Apple — App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- Apple — App privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple — Describing use of required reason API: https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api
- Apple — Screenshot specifications: https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/
- Apple — Updated age ratings: https://developer.apple.com/news/?id=ks775ehf
- Apple — D-U-N-S: https://developer.apple.com/support/D-U-N-S/
- Apple — Requesting CarPlay entitlements: https://developer.apple.com/documentation/carplay/requesting-carplay-entitlements
- Kotlin — Privacy manifest for iOS apps: https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html
- Firebase — App Store data collection: https://firebase.google.com/docs/ios/app-store-data-collection

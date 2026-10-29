# Handoff iOS — việc cần owner

Danh sách những việc agent không làm thay được, hoặc đã phải tự quyết thay owner trong lúc xây app iOS. Mỗi mục ghi rõ: việc gì, vì sao bị chặn, trong lúc chờ đã làm gì, file liên quan. Agent cập nhật file này sau mỗi milestone; mục nào đã xử lý thì xoá.

Tiến độ chi tiết: [progress.md](progress.md). Kế hoạch và biên bản nghiệm thu từng milestone: [milestones/](milestones/).

## Cần owner xử lý / quyết định

### Tài khoản và cloud resource

- **Firebase iOS app** (bundle `tss.t.podcast`) và file `GoogleService-Info.plist` (setup-and-build §4).
  - Bị chặn vì: agent không được tạo cloud resource.
  - Trong lúc chờ: Firebase trên iOS là tuỳ chọn. Remote Config chưa có nên blacklist dùng asset đóng gói (`shared` sinh từ `coreApi/src/main/assets/blacklist.json`). Analytics, Crashlytics, FCM chưa chạy.
  - Khi có: implement `ConfigAPI` bằng Firebase và gọi `graph.blacklistRepository.reload()` sau `fetchAndActivate` (progress.md, mục M0 T3).
- **Key PodcastIndex riêng cho iOS** (architecture §5 mục 3).
  - Hiện tại: `iosApp/Config/Secrets.xcconfig` (gitignored) dùng chung key/secret với Android, chép từ `securedtoken.cpp`.
  - Nên cấp key riêng để thu hồi độc lập và tách quota trước khi lên TestFlight.

### Quyết định agent đã tự chọn (owner xác nhận hoặc đổi)

- **Search gửi ít tham số hơn Android:** E7/E8 không gửi `pretty=false`, E8 không gửi `aponly=false`; E11 không retry `SerializationException`. PO M0 đánh giá không đổi dữ liệu trả về (progress.md, M0 T2).
- **Kotlin giữ 2.2.0, không dùng SKIE**, tích hợp framework bằng cách A (`embedAndSignAppleFrameworkForXcode`). Hệ quả: mỗi lần build Xcode đều chạy lại bước Gradle, build tăng dần chậm hơn (M0 review).
- **(M1) D6 chỉ lọc được một phần trên Home.**
  - Vì sao: E1 `podcasts/trending` và E5 `recent/feeds` không trả field `explicit`, nên chỉ lọc được Live (E2) và Kho nhạc (E6). Gọi E11 cho từng feed để biết cờ explicit thì quá tốn quota.
  - Đã làm (M1 T1): `HomeFeed` lọc explicit sau blacklist khi cờ `AllowExplicitContent` (mặc định `false`) chưa bật. Chưa có toggle vì chưa có màn Settings.
  - File: `docs/ios/milestones/M1.md` §4.2, §7; `shared/src/commonMain/kotlin/tss/t/shared/home/HomeFeed.kt`.
- **(M1) Nhãn tab và nút onboarding được localize.** Dùng "Yêu thích / Khám phá / Tìm kiếm" (en: "Favorites / Discover / Search") và "Tiếp tục" (en: "Continue"), thay vì chuỗi tiếng Anh hardcode của Android ("Favorite / TSPodcast / Search", "Continue"). File: `docs/ios/milestones/M1.md` §3, §4.5.
- **(M1) Ảnh cover `http://`.**
  - Đã làm: nâng lên `https://`, lỗi thì hiện placeholder; không bật `NSAllowsArbitraryLoads`.
  - Số liệu M1 T2 (30/10/2026): một lần tải Home có 179 URL ảnh khác nhau, 0 % `http://`, 0 lỗi; trên mẫu 269 ảnh có cả 17 URL `http://` của Kho nhạc, lỗi 1,9 % (1/17 ảnh `http://` lỗi sau khi nâng lên https). Dưới ngưỡng 10 %, nên **chưa cần** image proxy.
  - Owner cần quyết chỉ khi tỷ lệ lỗi về sau vượt 10 %: dựng image proxy HTTPS (cloud resource, agent không tạo).
  - File: `docs/ios/milestones/M1.md` §4.4, `iosApp/TSPodcast/Images/ArtworkLoader.swift`, số liệu trong `progress.md` mục M1 T2.
- **(M1) Analytics onboarding chỉ ghi log** (`trackScreen("Onboarding", CurrentPage=1..4)` và event `OnboardingFinished`, cùng tên với Android) cho tới khi có Firebase iOS app. Seam `AnalyticsTracker` nằm trong `shared`; khi có Firebase chỉ cần thay `LogAnalyticsTracker`. File: `docs/ios/milestones/M1.md` §4.2–§4.3, `iosApp/TSPodcast/App/LogAnalyticsTracker.swift`, `iosApp/TSPodcast/Onboarding/OnboardingModel.swift`.
- **(M1) Home: section rỗng/lỗi đều có "Tải lại"; pull-to-refresh lỗi thì giữ nội dung cũ.**
  - Vì sao: features-spec §3.4 [Sửa] chỉ yêu cầu empty/error state có "Tải lại". Android khi refresh xoá trắng mọi section rồi tải lại; nếu lỗi thì list rỗng (Live/Recent shimmer mãi).
  - Đã làm (M1 T4): trong lúc refresh, section đang có nội dung giữ nguyên; refresh lỗi thì vẫn giữ nội dung cũ, không hiện lỗi; section chưa có nội dung thì shimmer rồi ra nội dung / "Chưa có nội dung" / "Không tải được nội dung", cả hai trường hợp sau đều có "Tải lại". Owner muốn báo lỗi khi refresh thất bại (ví dụ toast) thì đổi `HomeModel.next`.
  - File: `iosApp/TSPodcast/Home/HomeModel.swift`, `iosApp/TSPodcast/Home/HomeView.swift`, `docs/ios/progress.md` mục M1 T4.
- **(M1) XCUITest gọi PodcastIndex thật** bằng key dùng chung với Android, nên tốn quota của key này. Đây thêm một lý do để cấp key riêng cho iOS (mục ở phần trên).

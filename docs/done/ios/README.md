# TSPodcast cho iOS — Bộ tài liệu

> **Trạng thái: Hoàn thành** (2026-11-01) — PR #20 (bộ tài liệu), PR #30 (app iOS M0–M6 theo hướng (a): KMP `shared` + SwiftUI). Câu "hiện app chỉ có trên Android" bên dưới đã cũ.
> Việc iOS còn lại nằm ở [progress.md](../../in-progress/ios/progress.md), [handoff.md](../../in-progress/ios/handoff.md) và [app-store-submission-plan.md](../../in-progress/ios/app-store-submission-plan.md).

> Trạng thái: bản nháp kiến trúc, viết ngày 2026-10-29, dựa trên `main` @ `c2e46d0`. Phần đọc code thực hiện ở `47fc4f4`; giữa hai commit chỉ có thay đổi `NativeLib.isAvailable` (màn "bản cài đặt chưa đầy đủ") cùng các tài liệu trong `docs/`.
> Đối tượng đọc: iOS developer xây app iOS, Android/KMP developer chuẩn bị shared code, và người phụ trách phát hành (release owner).

TSPodcast (`tss.t.podcast`) là app nghe podcast và radio. Hiện app chỉ có trên Android: Kotlin, Jetpack Compose, Gradle multi-module. Dữ liệu podcast lấy từ **PodcastIndex API**. Radio lấy từ **VOV** (vovmedia.vn) và **VOH** (voh.com.vn) bằng cách scrape HTML. App dùng Firebase (Analytics, Crashlytics, Remote Config, Cloud Messaging). Quảng cáo dùng AppLovin MAX; nhánh `origin/tuandv/TSP-5-remove-applovin` đang gỡ AppLovin và để lại module `:ads` làm no-op seam.

Bộ tài liệu này trả lời hai câu hỏi:

1. **Xây app iOS thế nào** cho đạt mức tính năng tương đương (parity) với Android, đồng thời dùng chung tối đa code Kotlin hiện có.
2. **Đưa app qua Apple App Review thế nào** ngay lần nộp đầu, hoặc ít lần bị reject nhất.

## Mục lục

| # | Tài liệu | Nội dung |
|---|---|---|
| 1 | [README.md](README.md) | Mục lục, hiện trạng, so sánh 3 hướng tiếp cận, khuyến nghị và ước lượng effort |
| 2 | [architecture.md](architecture.md) | Kiến trúc đích; ánh xạ module Android → shared → iOS; code nào chuyển sang `commonMain`; ranh giới expect/actual; Swift interop (SKIE); bảng version |
| 3 | [features-spec.md](features-spec.md) | Spec từng màn hình của app Android hiện tại: API endpoint, state, cache, analytics, edge case. Là chuẩn để kiểm tra parity |
| 4 | [audio-playback-ios.md](audio-playback-ios.md) | AVPlayer/AVQueuePlayer, AVAudioSession, background audio, Now Playing, Remote Commands, interruption/route change, HLS/ICY radio, download nền, CarPlay, sleep timer, tốc độ phát |
| 5 | [setup-and-build.md](setup-and-build.md) | Cài Mac/Xcode, tạo project, Gradle → XCFramework/SPM, Firebase iOS (dSYM), AdMob + UMP + ATT, signing, CI (GitHub Actions / Xcode Cloud), fastlane |
| 6 | [app-store-submission-plan.md](../../in-progress/ios/app-store-submission-plan.md) | Kế hoạch từng bước để qua review: tài khoản, App Privacy, PrivacyInfo.xcprivacy, ATT, age rating, bản quyền nội dung (5.2), 4.2/4.3, metadata/screenshot, TestFlight, review notes, timeline và checklist |

### Tài liệu liên quan đã có trong `docs/`

| Tài liệu | Dùng cho iOS ở đâu |
|---|---|
| [../../in-progress/policy/store-policy-review.md](../../in-progress/policy/store-policy-review.md) | Rà soát chính sách Play và App Store. Đặc biệt xem §1.2 (chuẩn bị cho iOS), §2.3 (Radio VOV/VOH: luật Việt Nam, Nghị định 71/2022), §5 (Data safety / App Privacy), §9 (4.2/4.3, cập nhật 06/2026). Kế hoạch nộp app ở đây kế thừa các kết luận đó |
| [../../in-progress/plans/admob-migration.md](../../in-progress/plans/admob-migration.md) | Kiến trúc seam `:ads` + `:ads-admob`, chính sách tần suất, quy tắc "không hiện quảng cáo full-screen khi audio đang phát", UMP. iOS v1.1 áp dụng cùng chính sách (setup-and-build.md §7) |
| [../../in-progress/plans/seo-web-dashboard.md](../../in-progress/plans/seo-web-dashboard.md) | Website, nơi host privacy policy, Support URL và `app-ads.txt` (§A.9); Universal Links và `apple-app-site-association`, Smart App Banner (§A.10); ASO (§C.1) |
| [../research/competitor-analysis.md](../research/competitor-analysis.md) | Gap analysis và danh sách quick win (Q2 tốc độ phát, Q3 sleep timer, Q4 nhớ vị trí, Q5 màn Cài đặt, Q6 lọc explicit…). Đây là nguồn cho các mục **[iOS+]** trong features-spec.md |
| [../../in-progress/research/feature-roadmap-ai-growth.md](../../in-progress/research/feature-roadmap-ai-growth.md) | Lộ trình AI (transcript, tóm tắt…). Tính năng AI cần backend; khi đưa lên iOS phải khai thêm trong App Privacy |
| [../crashlytics/2026-10-crash-triage.md](../crashlytics/2026-10-crash-triage.md) | Crash thực tế trên Android, 10/2026. Các lỗi null/lifecycle đã sửa là bài học cho shared code |

## Hiện trạng codebase (tóm tắt)

| Hạng mục | Android hiện tại | Ghi chú cho iOS |
|---|---|---|
| Ngôn ngữ / build | Kotlin 2.2.0, AGP 8.13.2, compileSdk/targetSdk 36, minSdk 24 | Muốn dùng KMP + SKIE thì phải nâng Kotlin (xem architecture.md §7) |
| DI | Hilt / Dagger | Không chạy trên iOS. Chuyển sang Koin, hoặc DI thủ công trong shared |
| Network | Retrofit 3 + Gson + OkHttp 5, `TSDataStateCallAdapterFactory` | Ktor + kotlinx.serialization |
| Auth API | `NativeLib` (module `securedToken`, JNI C++) trả về base URL, API key, `Authorization` (SHA-1), `User-Agent` | Secret **không** được nằm trong tài liệu. Xem architecture.md §5 |
| DB | Room 2.8.4, schema v6, 5 bảng | Room ≥ 2.7 đã hỗ trợ KMP nên giữ được Room, chỉ đổi TypeConverter từ Gson sang kotlinx.serialization |
| Key-value | `SharedPreferences` + Gson (`SharedPref`) | DataStore Preferences KMP, hoặc `multiplatform-settings` |
| HTML scraping (radio) | Jsoup | Ksoup (`com.fleeksoft.ksoup`). Nên cân nhắc chuyển việc scrape lên server |
| Player | Media3 ExoPlayer + `MediaSessionService` + custom notification | Viết native: AVFoundation + MediaPlayer framework (audio-playback-ios.md) |
| UI | Jetpack Compose, Material3, shared element transition, `hazeAndroid` (fork của Haze để làm blur) | SwiftUI, `Material`/Liquid Glass của iOS 26 |
| Firebase | Analytics, Crashlytics, Remote Config, FCM. Không có Auth | Firebase iOS SDK qua SPM |
| Ads | AppLovin MAX (banner, native, app-open). Đang có nhánh gỡ bỏ | iOS v1 đề xuất **không có ads** (xem §4) |
| Ngôn ngữ UI | Chỉ tiếng Việt (`values/strings.xml`), lẫn vài chuỗi tiếng Anh hardcode | String Catalog: `vi` (gốc) + `en` |
| Tài khoản người dùng | Không có | Không phải làm account deletion (5.1.1(v)), trừ khi sau này thêm login |
| KMP | `sharedKmp/src/commonMain/.../TSDataState.kt` là file duy nhất; **không có** `build.gradle.kts`; **chưa** được `include` trong `settings.gradle.kts` | Bước 0: biến `sharedKmp` thành module KMP thật |
| Nhánh KMP | `origin/tuandv/TSP-0-migrate-with-kmp` có tip là `41237f7`, **đã nằm trong lịch sử `main`** (0 commit chưa merge) | Không có công việc KMP nào đang dở để tái sử dụng. `KMP_MIGRATION_PLAN.md` (Kotlin 2.0, Retrofit) đã lỗi thời |

## Ba hướng tiếp cận

### (a) Kotlin Multiplatform cho shared logic + UI native SwiftUI

Đưa model, network, repository, use case, persistence và (tuỳ chọn) presentation state (ViewModel/Store) vào module KMP `shared`. Module này build ra XCFramework. UI viết bằng SwiftUI. Player viết native bằng AVFoundation.

- **Ưu điểm**
  - Dùng lại phần logic khó và dễ sai lệch giữa hai nền tảng: ký request PodcastIndex, blacklist (lọc feed cờ bạc), cache TTL theo điều khoản PodcastIndex (`PruneApiCache`), scrape radio VOV/VOH, mô hình favourite/history. Đây đúng là những chỗ `git log` cho thấy đã phải sửa nhiều lần (xem các commit `fix(crash)` và `feat(music)`).
  - Room 2.8.4 đang dùng đã hỗ trợ KMP, nên schema DB giữ nguyên.
  - UI và player native cho trải nghiệm iOS chuẩn: Now Playing, CarPlay, Dynamic Island, Liquid Glass, VoiceOver. Điều này giảm rủi ro guideline **4.2** (minimum functionality, "app-like") và các lời phàn nàn kiểu "không giống app iOS".
  - Khớp với Phase 4 của `KMP_MIGRATION_PLAN.md` ("Create SwiftUI implementations for iOS").
- **Nhược điểm**
  - Phải viết lại toàn bộ khoảng 25 file Compose UI.
  - Cần người biết cả Kotlin lẫn Swift để duy trì ranh giới interop.
  - Phải refactor Android: Hilt → Koin (hoặc giữ Hilt ở `androidApp` và chỉ dùng Koin trong shared), Retrofit → Ktor, Gson → kotlinx.serialization.

### (b) Compose Multiplatform cho iOS UI

Chuyển cả UI Compose sang `commonMain`. Compose Multiplatform cho iOS đã stable từ bản 1.8.0 (5/2025).

- **Ưu điểm**
  - Dùng chung được nhiều code nhất, có thể tới 80–90% nếu bỏ qua player.
  - Đội Android-only vẫn làm được.
- **Nhược điểm**, cụ thể cho codebase này:
  - UI hiện tại phụ thuộc nhiều thứ chỉ có trên Android: `hazeAndroid` (fork; bản upstream Haze có KMP nên phải migrate), Coil 2.7 (không có KMP, phải lên Coil 3), `HtmlCompat`, `AndroidView` cho ads, `LocalContext.assets`, Material3 dynamic color, `SharedTransitionLayout` (có trong CMP nhưng phải kiểm thử lại).
  - Player, Now Playing, CarPlay, AVAudioSession **vẫn phải viết native**. CMP không giải quyết được phần khó nhất của một app audio.
  - Cảm giác UI kiểu Material trên iOS (scroll physics, navigation bar, tab bar chưa phải Liquid Glass) dễ bị đánh giá là "không giống app iOS". Đây là rủi ro mềm với 4.2 và điểm review của người dùng.
  - App phải kèm Skia/runtime nên kích thước binary lớn hơn.

### (c) Viết hoàn toàn native bằng Swift

- **Ưu điểm**: stack iOS thuần, tooling đơn giản nhất, không phải học interop.
- **Nhược điểm**
  - Phải nhân đôi mọi logic nghiệp vụ, rồi duy trì hai bản: blacklist, cache TTL, ký request, parser radio. Parser radio thì vỡ mỗi khi VOV/VOH đổi markup, nên sẽ vỡ ở **cả hai** app.
  - Không tận dụng được kế hoạch KMP và module `sharedKmp`.
  - Về lâu dài sẽ lệch hành vi giữa hai nền tảng.

### Bảng so sánh

| Tiêu chí | (a) KMP + SwiftUI | (b) Compose MP | (c) Native Swift |
|---|---|---|---|
| Code dùng chung | ~35–45% (data/domain/presentation state) | ~75–85% (trừ player/CarPlay) | 0% |
| Effort tới bản App Store đầu tiên* | **15–17 tuần** | 11–14 tuần | 16–19 tuần |
| Chất lượng UX trên iOS | Cao nhất | Trung bình–khá | Cao nhất |
| Rủi ro App Review 4.2 / UX | Thấp | Trung bình | Thấp |
| Chi phí bảo trì 2 nền tảng | Thấp–trung bình | Thấp | Cao |
| Tận dụng `KMP_MIGRATION_PLAN.md` | Có (Phase 2–4) | Có, vượt kế hoạch | Không |
| Kỹ năng cần | Kotlin + Swift | Kotlin (+ ít Swift cho player) | Swift |

\* Ước lượng cho 1 iOS dev full-time cộng 1 Android/KMP dev khoảng 50%, tính cả 2 tuần đệm cho review/reject. Chi tiết ở §5.

## Khuyến nghị: chọn (a) — KMP shared logic + SwiftUI native

Lý do, dựa trên code thực tế:

1. **Phần đáng chia sẻ là logic, không phải UI.** Các use case trong `podcasts/usecase/*`, `BlacklistRepositoryImpl`, `PruneApiCache`, `GetPlayableLink`/`GetRadioList`, cache in-memory 15 phút và cache đĩa của trending đều là Kotlin thuần hoặc gần như thuần. Chúng chỉ dính Android qua `Context.assets`, `org.json` và `SharedPreferences`, cả ba đều thay được bằng thư viện KMP.
2. **Phần khó nhất của app iOS là audio, và phần này buộc phải native** dù chọn hướng nào. Đó là AVAudioSession, Now Playing, CarPlay, interruption. CMP không giảm được effort ở đây.
3. **Seam đã có sẵn.** `CrashReporter` (sharedLibrary), `ConfigAPI` (Remote Config), `IPodcastRepository`, `IFavouriteRepository`, `IHistoryRepository` và `RadioApi` đều là interface. Có thể đưa nguyên các interface này vào `commonMain`, rồi để Swift cung cấp implementation Firebase native.
4. **Room đã là KMP.** Không phải chuyển sang SQLDelight. Schema `core/schemas/.../6.json` dùng lại được.
5. **App Review.** App podcast thuộc nhóm "saturated" (guideline 4.3(b)). UI native cùng các tính năng hệ thống của iOS (CarPlay, Now Playing, widget, Siri/App Intents) là cách rẻ nhất để chứng minh app "app-like" và khác biệt.

Lối thoát: nếu thiếu iOS dev, có thể làm một số màn hình ít tương tác (Onboarding, chọn danh mục) bằng Compose Multiplatform nhúng qua `ComposeUIViewController` trong cùng kiến trúc (a). Kiến trúc không cần đổi.

## 4. Các quyết định sản phẩm cần chốt **trước khi** code

| # | Quyết định | Khuyến nghị | Lý do |
|---|---|---|---|
| D1 | Có đưa tab **Radio** (VOV/VOH) vào iOS v1 không? | **Không**, trừ khi đã có văn bản cho phép từ VOV/VOH. Tắt bằng Remote Config theo platform, đúng phương án B trong [store-policy-review §2.3](../../in-progress/policy/store-policy-review.md) | App đang scrape website và phát lại stream của đài nhà nước mà chưa có giấy phép. Đây là rủi ro reject lớn nhất theo guideline 5.2.1/5.2.2 (xem app-store-submission-plan.md §8) |
| D2 | Ads trong iOS v1? | **Không** (dùng no-op seam như nhánh `TSP-5-remove-applovin` và [admob-migration.md](../../in-progress/plans/admob-migration.md)) | Bỏ được ATT prompt, App Privacy label "Tracking" và SKAdNetwork, nên rủi ro review giảm mạnh. Có thể thêm AdMob + UMP ở v1.1, theo đúng chính sách placement và tần suất của bản Android |
| D3 | Facebook SDK? | **Không đưa sang iOS** | Android chỉ đưa vào để sửa một crash lúc khởi động. Trên iOS, FB SDK tự log App Events nên có thể bị coi là tracking và kéo theo ATT |
| D4 | Push "ads" (`banner_ads`, `video_ads`, `full_screen_ads`)? | Không dùng trên iOS. Riêng `new_podcasts` chỉ gửi khi người dùng đã opt-in trong app | Guideline 4.5.4 |
| D5 | Gửi `device_id` (Android ID) vào Analytics? | Không. Trên iOS chỉ dùng Install ID ngẫu nhiên (đã có `getOrCreateInstallId`) | Giảm số mục khai báo trong App Privacy |
| D6 | Nội dung explicit | Mặc định lọc explicit (`clean=true` khi search, lọc `explicit` cho trending/feed). Có toggle để bật lại | Giúp có age rating thấp hơn (xem §11 của kế hoạch nộp app) |
| D7 | Thêm tính năng khác biệt cho iOS | Sleep timer, tốc độ phát, nhớ vị trí nghe, download offline, CarPlay, màn Cài đặt (privacy policy, báo cáo nội dung) | Giảm rủi ro 4.2/4.3. Android hiện **chưa có** các tính năng này. Chúng trùng với quick win Q2–Q6 trong [competitor-analysis §6.1](../research/competitor-analysis.md), nên nên làm logic trong shared để Android dùng chung |

## 5. Ước lượng effort (hướng a)

| Hạng mục | Người | Effort |
|---|---|---|
| Bước 0: dựng `shared` KMP (Gradle, Koin, Ktor, serialization, Room KMP, DataStore), chuyển model/use case, test | KMP dev | 3–4 tuần |
| Chuyển Android sang dùng `shared` (giữ Compose UI) | KMP dev | 2 tuần (làm song song) |
| Xcode project, DI bridge, SKIE, CI, Firebase | iOS dev | 1 tuần |
| SwiftUI: Onboarding, chọn danh mục, TabView, Discover, Search, Favourite, Podcast detail, Player, MiniPlayer | iOS dev | 5–6 tuần |
| Audio engine: AVPlayer, Now Playing, remote commands, interruption, queue, resume position, speed, sleep timer | iOS dev | 2–3 tuần |
| Download offline (URLSession nền) | iOS dev | 1 tuần |
| CarPlay (sau khi được cấp entitlement) | iOS dev | 1 tuần |
| Localization vi/en, accessibility, polish, privacy manifest, metadata, screenshot | iOS dev + PM | 1 tuần |
| TestFlight, sửa bug, nộp review, đệm cho reject | Cả nhóm | 2 tuần |
| **Tổng (lịch)** | | **~15–17 tuần**: khoảng 75–85 dev-day iOS và 25–30 dev-day KMP |

Không có Radio và Ads (D1, D2): đã tính sẵn trong ước lượng trên. Nếu làm Radio: +1 tuần, cộng thời gian xin giấy phép (không kiểm soát được). Nếu làm Ads (AdMob + UMP + ATT): +1 tuần.

## Quy ước

- Tài liệu viết bằng tiếng Việt, thuật ngữ kỹ thuật giữ tiếng Anh.
- **Không** chép bất kỳ secret nào vào tài liệu: API key/secret PodcastIndex, AppLovin SDK key, ad unit ID, Firebase config. Chỉ nhắc theo tên, ví dụ `NativeLib.getApiKey()`. Trong repo hiện vẫn có secret commit trực tiếp (ví dụ `applovin { apiKey = ... }` trong `app/build.gradle.kts`, và các chuỗi trong `securedToken/src/main/cpp/securedtoken.cpp`). Xem architecture.md §5 về việc xoay vòng (rotate) key.
- Version thư viện ghi trong architecture.md §7 là mốc kiểm tra lúc viết (10/2026). Luôn đối chiếu lại release notes trước khi khoá version.

## Nguồn chính (đã kiểm tra 2026-10-29)

- Apple — Upcoming requirements (Xcode 26/iOS 26 SDK từ 28/4/2026; target iOS 13+ từ 9/9/2026; age rating mới từ 31/1/2026): https://developer.apple.com/news/upcoming-requirements/
- Apple — App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- Kotlin releases (2.4.21 ngày 8/10/2026): https://kotlinlang.org/docs/releases.html
- Compose Multiplatform iOS stable (1.8.0): https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- Room KMP: https://developer.android.com/kotlin/multiplatform/room

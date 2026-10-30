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
  - Mỗi lần launch có `-uitest-skip-onboarding` xoá store (kể cả cache trending), nên mỗi UI test mở Home gửi lại 5 request Home (E1 × 2, E2, E5, E6); test onboarding/danh mục gửi thêm E9. Test Home fail nếu API chậm hơn 30 s hoặc trả rỗng, dù app đúng.
  - M2 thêm UI test cho Detail (E10 + E11 mỗi lần mở) và Search (E7, E8, E9, E1 theo danh mục). Đo ở M2 T4 (30/10/2026, từ log app): một lần chạy toàn bộ suite (161 test, ~12 phút) gửi khoảng 550–600 request, phần lớn là Home (~430 lời gọi section), Detail 30 × 2, Search ~33, E9 ~50.
  - File: `iosApp/TSPodcastUITests/`, `docs/ios/progress.md` mục M1 "Giới hạn đã biết".
- **(M2) D6 ở Podcast detail:** bỏ episode `explicit = 1`; nếu E11 `podcasts/byfeedid` cho biết cả feed `explicit = true` thì ẩn toàn bộ episode và hiện thông báo "Podcast này được đánh dấu có nội dung người lớn (explicit) nên các tập đã bị ẩn.".
  - Vì sao: E1/E5 ở Home không có field `explicit` (bullet D6 của M1), nên Detail là chỗ đầu tiên biết feed có explicit hay không. Android không lọc gì.
  - Owner muốn chỉ ẩn từng episode (không ẩn cả feed) thì bỏ nhánh `isExplicitFeedHidden` trong `PodcastDetailFeed`.
  - Hệ quả thấy được (M2 T2, 30/10/2026): podcast **đứng đầu** "Podcasts thịnh hành" ("RADIO NÔNG DÂN", feed 4201720) có `explicit = true` ở E11, nên bấm vào chỉ thấy thông báo explicit, không có tập nào. Home không lọc trước được vì E1 không trả `explicit`. Owner cân nhắc: chấp nhận; hoặc chỉ ẩn từng tập `explicit = 1` (số tập bị ẩn khi đó tuỳ feed; một feed khác trong lần chạy thử bị ẩn 22/100 tập); hoặc gọi E11 cho các item trending đầu để ẩn khỏi Home (tốn quota).
  - Trong lúc chờ: giữ đúng plan; UI test cần danh sách tập tự chọn item trending đầu tiên có tập (progress.md, M2 T2 "Giới hạn đã biết").
  - File: `docs/ios/milestones/M2.md` §4.1, §7; `shared/src/commonMain/kotlin/tss/t/shared/detail/PodcastDetailFeed.kt` (M2 T1); `iosApp/TSPodcast/Detail/PodcastDetailView.swift`, `iosApp/TSPodcastUITests/TSPodcastUITestCase.swift` (`openDetailWithEpisodes`).
- **(M2) D6 ở Search:** E7/E8 gửi `clean=true` và bỏ feed `explicit = true` sau blacklist (Android không gửi `clean`). Đo 30/10/2026: "radio hay" 60 → 57 kết quả khi có `clean=true`. File: `docs/ios/milestones/M2.md` §4.1; `shared/.../search/SearchFeed.kt`.
- **(M2) Search có chip danh mục, tap mở podcast thịnh hành của danh mục** (E1 `cat=<id>`, `lang=vi`, 7 ngày). Android tải E9 ở Search nhưng không hiện, và tap danh mục chưa có hành động (features-spec §3.9 [Sửa]). Owner muốn kiểu khác (ví dụ tìm theo tên danh mục bằng E7) thì đổi `SearchFeed.categoryPodcasts`. Đã làm ở M2 T3: chip ở trạng thái idle của tab Tìm kiếm, màn danh sách có loading/rỗng/lỗi + "Tải lại", tap mở Detail. Tên danh mục giữ tiếng Anh từ API. File: `docs/ios/milestones/M2.md` §3.2, §7; `iosApp/TSPodcast/Search/SearchView.swift`, `CategoryPodcastsView.swift`.
- **(M2) Gợi ý tìm kiếm "Podcast hay" thay cho "Radio hay"** của Android, vì iOS v1 không có Radio và chữ "Radio" trong UI dễ khiến reviewer đi tìm tính năng radio. File: `docs/ios/milestones/M2.md` §4.8, §7.
- **(M2) Lỗi ở Detail và Search:** Detail dùng alert "Rất tiếc!" (en "Oops!") với nội dung đã localize, nút "Tải lại" + "Huỷ", huỷ thì có lỗi inline + "Tải lại" (Android: "Opps!", message thô, shimmer mãi); episode rỗng có empty state. Search tách lỗi khỏi "không có kết quả", có loading, query rỗng không gửi request. File: `docs/ios/milestones/M2.md` §3, §7.
- **(M4) DB iOS là `version = 1`, 4 bảng `Podcast`, `Feed`, `Episode`, `FavouriteDTO`, không `RadioChannel`** (plan M4 §7 mục 1). Cột, affinity, NOT NULL, khoá chính giống hệt Android v6 (`DatabaseSchemaParityTest`), để sau này Android chuyển sang `shared` chỉ cần thêm bảng radio + migration. Không có migration vì iOS cài mới. File: `shared/src/commonMain/kotlin/tss/t/shared/storage/`, `shared/schemas/`.
- **(M4) `PruneApiCache` giữ cả podcast của episode đã favourite** ([Sửa], plan M4 §7 mục 2). Android xoá podcast đó sau 7 ngày nên favourite episode không mở được khi offline. Các episode khác cùng feed (không favourite) vẫn hết hạn sau 7 ngày như Android. File: `shared/.../storage/Daos.kt` (`PodcastDao.deleteExpired`), `shared/.../usecase/PruneApiCache.kt`.
- **(M4) Danh sách favourite mới nhất trước** (`ORDER BY rowid DESC`; favourite lại một mục thì mục đó lên đầu), plan M4 §7 mục 3. Android `getAll()` không `ORDER BY` (thực tế cũ nhất trước). File: `shared/.../storage/Daos.kt` (`FavouriteDao.observeAll`).
- **(M4) Crash Kotlin dùng hook tự viết, không dùng CrashKiOS** (plan M4 §7 mục 7). `installKotlinCrashHook` (`setUnhandledExceptionHook`) gửi throwable + địa chỉ stack tới `CrashReporter.recordFatal`; Crashlytics symbolicate bằng dSYM của app. Lý do: framework `Shared` là static và test của shared không link được Crashlytics. Shared xong ở T2; nối vào app (Crashlytics `ExceptionModel`) là T4. Nếu owner muốn CrashKiOS thì chỉ cần thay phần thân hook. File: `shared/src/iosMain/kotlin/tss/t/shared/crash/KotlinCrashHook.kt`, `shared/.../crash/CrashReporter.kt`.
- **(M4) Analytics iOS không có tham số định danh thiết bị** (D5): mặc định chỉ `os="iOS"`, `os_version`, `app_version`, `event_time_stamp`, `screen`; Install ID chỉ dùng làm user id của Crashlytics. Dashboard nào trên Firebase đang lọc theo tham số thiết bị của Android sẽ không thấy dữ liệu iOS. File: `shared/.../analytics/AnalyticsDefaults.kt`.
- **(M4) Mục favourite không mở được có alert "Bỏ yêu thích"** ([Sửa], plan M4 §7 mục 4). Android: tap mục không còn dữ liệu trong DB thì không có phản hồi và chỉ bỏ được từ Player. iOS hiện alert "Không mở được mục này" với "Bỏ yêu thích" / "Đóng". Tab Yêu thích observe bảng nên favourite ở Player hiện ngay ([Sửa] features-spec §3.8). File: `iosApp/TSPodcast/Favourite/FavouriteModel.swift`, `FavouriteRootView.swift`.
- **(M4) Detail dùng episode đã lưu khi E10 lỗi** (`isFromCache`, D6 vẫn lọc), plan M4 §7 mục 5. Shared ở T1, banner `detail.offlineBanner` ở Detail ở T3. File: `shared/.../detail/PodcastDetailFeed.kt`, `iosApp/TSPodcast/Detail/PodcastDetailView.swift`.
- **(M3) Player không có nút "More".** Android vẽ icon nhưng không gắn hành động; nút không làm gì là rủi ro review. File: `docs/ios/milestones/M3.md` §3.2, §7 mục 2.
- **(M3) Lỗi phát có thông báo + "Tải lại", buffering hiện spinner** (Android không báo gì khi ExoPlayer lỗi). File: `docs/ios/milestones/M3.md` §3.1, §7 mục 3.
- **(M3) Nhớ vị trí nghe ([Sửa] features-spec §3.6):** lưu mỗi 10 s, khi pause, khi đổi episode, khi vào background; còn < 30 s coi là đã nghe xong (lần sau phát từ đầu); 5 s đầu không lưu; live không lưu. Owner muốn ngưỡng khác thì đổi hằng số trong `PlaybackPositions`. File: `docs/ios/milestones/M3.md` §4.3.
- **(M3) Prev/Next cũng lưu `current_item_id` và bị disable ở hai đầu playlist** (Android chỉ lưu khi tap từ Detail/Live; nút ở hai đầu bấm được nhưng không làm gì). File: `docs/ios/milestones/M3.md` §7 mục 5.
- **(M3) Remote command chỉ có play/pause/toggle, next/previous track và kéo thanh seek** trên Lock Screen; skip ±, tốc độ, seek forward/backward bị tắt vì app chưa có các tính năng [iOS+] đó. File: `docs/ios/milestones/M3.md` §4.5, §7 mục 6.
- **(M3) MiniPlayer hiện tên podcast ở dòng phụ** (Android hiện mô tả episode); Live dùng `feedTitle` làm tên podcast; stream live (không có duration) hiện "LIVE" và không seek được. File: `docs/ios/milestones/M3.md` §3.3, §7 mục 7.
- **(M3) Không gửi User-Agent tuỳ chỉnh tới host audio; bật `NSAllowsArbitraryLoadsForMedia`** để phát enclosure `http://` (chỉ áp dụng cho AVFoundation). File: `iosApp/TSPodcast/Info.plist`, `docs/ios/milestones/M3.md` §7 mục 9.
- **(M3) Player chỉ đóng bằng nút chevron, chưa vuốt xuống để đóng** (plan M3 §3.2 ghi "vuốt xuống hoặc chevron"). `.fullScreenCover` không có gesture này sẵn; chưa tự thêm vì gesture cần kiểm bằng UI run, mà milestone chỉ chạy unit test. Trong lúc chờ: chevron `player.close` hoạt động, có XCUITest (để QC). Owner/QC quyết có cần vuốt xuống không; nếu cần thì thêm `DragGesture` gọi `environment.isPlayerPresented = false` ở `PlayerView`. File: `iosApp/TSPodcast/Player/PlayerView.swift`, `iosApp/TSPodcast/Main/MainTabView.swift`, progress.md mục M3 "Giới hạn đã biết".

### Môi trường test

- **(M1) Chưa có simulator iOS 17/18 để chạy nhánh MiniPlayer cũ.**
  - Vì sao bị chặn: deployment target là iOS 17.0, nhưng máy dev chỉ có runtime iOS 26.3. Nhánh `safeAreaInset` (iOS 17–18) và nhánh iOS 26.0 của `MiniPlayerSlot` mới chỉ compile, chưa chạy. Tải runtime (vài GB) là việc của owner trên máy này.
  - Trong lúc chờ: nhánh iOS 26.1+ (`tabViewBottomAccessory(isEnabled:)`) đã qua `MainShellUITests.testMiniPlayerSlotSitsAboveTabBar` / `testNoMiniPlayerWhenNothingPlays` trên iOS 26.3.
  - (M2) Cùng lý do: nhánh iOS 17 của zoom transition Home → Detail (push thường) và giao diện `.searchable` + hàng chip chế độ trên iOS 17–18 cũng mới compile, chưa chạy/chưa xem.
  - (M3) Hai nhánh cũ của `MiniPlayerSlot` giờ chứa MiniPlayer thật (`MiniPlayerView`), vẫn mới compile; trên iOS 26.3 cũng chưa chạy UI test nào của M3 (theo luật test, để QC).
  - Owner chọn một trong hai: cài runtime iOS 17 hoặc 18 (Xcode › Settings › Components) rồi chạy lại toàn bộ `xcodebuild test` trên simulator đó (ít nhất `MainShellUITests`, `PodcastDetailUITests`, `SearchUITests`); hoặc nâng deployment target lên 26 (bỏ nhánh cũ).
  - File: `iosApp/TSPodcast/Main/MiniPlayerSlot.swift`, `iosApp/TSPodcast/Design/ZoomTransition.swift`, `iosApp/TSPodcast/Search/SearchView.swift`, `iosApp/project.yml` (`deploymentTarget`).
- **(M3) Checklist audio trên máy thật** (simulator không mô phỏng được): AirPods (nút double tap, tháo tai → pause), rút tai nghe dây → pause, cuộc gọi/FaceTime → pause rồi tự resume khi trước đó đang phát, Siri, báo thức, điều khiển từ Lock Screen/Control Center, khoá màn hình 30 phút vẫn phát.
  - Bị chặn vì: agent chỉ có simulator; không có máy thật và không ký được bản cài máy thật.
  - Trong lúc chờ: logic interruption/route change/media services reset có unit test bằng notification tổng hợp (M3-AC4); remote command và Now Playing có unit test (M3-AC5, AC6).
  - Owner: chạy checklist audio-playback-ios.md §11 (các mục trong scope M3) trên iPhone thật trước TestFlight.
  - File: `docs/ios/milestones/M3.md` §7 mục 8, `docs/ios/audio-playback-ios.md` §11.

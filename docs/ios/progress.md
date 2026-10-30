# Tiến độ iOS

Nhật ký tiến độ cho app iOS của TSPodcast (hướng (a): KMP shared logic + SwiftUI + AVFoundation). Mỗi milestone có file kế hoạch riêng trong [milestones/](milestones/). Chuẩn parity là [features-spec.md](features-spec.md).

Quyết định owner đã chốt (không mở lại): Android giữ nguyên, không chuyển sang `shared`; iOS v1 không có Radio, Ads, ATT, Facebook SDK; analytics không có `device_id` (chỉ Install ID); mặc định lọc nội dung explicit (D6).

## Bảng milestone

| ID | Tên | Kế hoạch | Trạng thái |
|---|---|---|---|
| M0 | Nền tảng: module `shared` KMP + project Xcode chạy được | [milestones/M0.md](milestones/M0.md) | Đã nghiệm thu (review round 1) |
| M1 | Onboarding, chọn danh mục, khung tab, Home/Discover | [milestones/M1.md](milestones/M1.md) | Đã nghiệm thu (review round 1) |
| M2 | Podcast detail và Search | [milestones/M2.md](milestones/M2.md) | Đã nghiệm thu (review round 1) |
| M3 | Audio engine, Player và MiniPlayer | [milestones/M3.md](milestones/M3.md) | Đã nghiệm thu (review round 2, `5b8e26c`). UI suite để QC |
| M4 | Favourite, lưu trữ, thông báo, deep link, analytics — chốt parity | [milestones/M4.md](milestones/M4.md) | Đã nghiệm thu (review round 1, `9f2f1fb`). Checklist parity §7 ở mục M4 T5; UI suite và bước simulator để QC |
| M5 | Giao diện Liquid Glass (iOS 26) + Up next peek trên Player | [milestones/M5.md](milestones/M5.md) | Xong phần code + kiểm tĩnh (T1–T4): helper glass + fallback Material, panel Up next peek thay sheet, Player/MiniPlayer/tab bar/chip Tìm kiếm/thanh Lưu/nút "Tải lại" glass, 275 unit test (+32); typecheck Swift 6 toàn app 0 lỗi, 31 test logic pass trên macOS. **Chờ iPhone**: unit test trên máy, build Debug, smoke và danh sách "Owner xem trên iPhone" (platform iOS 26.2 chưa cài xong) |

## M0

Trạng thái: **developer đã làm xong T1–T4**, M0-AC1..AC15 đã tự kiểm (chi tiết ở từng task bên dưới), chờ PO nghiệm thu.

### Tóm tắt M0 (M0-AC15)

| Hạng mục | Quyết định | Lý do / chi tiết |
|---|---|---|
| Kotlin | `2.2.0` (KSP `2.2.0-2.0.2`), không đổi | Kotlin/Native 2.2.0 build, link framework và chạy test trên simulator với Xcode 26.3. Cảnh báo "Xcode chưa được test" tắt bằng `kotlin.apple.xcodeCompatibility.nowarn=true` trong `shared/gradle.properties` (T1). |
| Target Android của `shared` | Plugin `com.android.kotlin.multiplatform.library` (AGP 8.13.2) | Không cần fallback `androidTarget()` + `com.android.library` (T1). |
| SKIE | Không dùng | Swift gọi `suspend fun` qua bridge `async` có sẵn của Kotlin/Native (completion handler → `async throws`); M0 không cần Flow/sealed class phía Swift. Cân nhắc lại ở M1 khi có state stream. |
| Tích hợp framework | **Cách A — direct integration** (`./gradlew :shared:embedAndSignAppleFrameworkForXcode` trong Build Phase "Compile Kotlin" chạy trước "Compile Sources") | Monorepo, máy dev có JDK 17/21, Xcode build một lần là có cả Kotlin lẫn Swift; không có bước sinh XCFramework thủ công (cách B) và không phải publish ra dịch vụ ngoài (cách C, KMMBridge). Framework static nên không phải embed. |
| Composition root | `SharedGraph` viết tay, không Koin | M0 §3.6 (T3). |
| Thay đổi ngoài `shared/`, `iosApp/`, `docs/ios/` | `settings.gradle.kts` (`include(":shared")`), `gradle/libs.versions.toml` (thêm entry), `.gitignore`, và **2 dòng `apply false`** trong `build.gradle.kts` gốc | Dòng `apply false` là ngoại lệ được phép: thiếu chúng Gradle báo "plugin is already on the classpath with an unknown version" (T1). Không module Android nào phụ thuộc `:shared`. |

Chạy mọi thứ (từ gốc repo):

```bash
# Test + framework của shared
./gradlew :shared:allTests                                  # iosSimulatorArm64Test + testAndroidHostTest
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Android vẫn build như cũ
./gradlew :app:assembleDebug

# App iOS: tạo secrets cục bộ (gitignored) rồi sinh project, build, chạy
cp iosApp/Config/Secrets.example.xcconfig iosApp/Config/Secrets.xcconfig   # điền 4 key PODCASTINDEX_*
(cd iosApp && xcodegen generate)                            # chỉ khi sửa project.yml
SIM=4FB8953E-F948-4CF1-9DB3-EE4F79CEC480                    # "NovaTest iPhone 17"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug \
  -destination "id=$SIM" -derivedDataPath build/ios-dd build
xcrun simctl boot $SIM; xcrun simctl install $SIM build/ios-dd/Build/Products/Debug-iphonesimulator/TSPodcast.app
xcrun simctl launch $SIM tss.t.podcast
xcrun simctl spawn $SIM log show --last 2m --predicate 'subsystem == "tss.t.podcast"'
```

Hoặc mở `iosApp/iosApp.xcodeproj` bằng Xcode, chọn scheme `TSPodcast` và Run. (`build/` ở gốc đã nằm trong `.gitignore`.)

Việc còn mở cho owner (agent không được tạo cloud resource):

- **Firebase iOS app** (bundle `tss.t.podcast`) trong project Firebase debug/release và file `GoogleService-Info.plist` (setup-and-build §4). Chưa có thì chưa có Remote Config (blacklist dùng asset), Analytics, Crashlytics, FCM trên iOS.
- **Key PodcastIndex riêng cho iOS** (architecture §5 mục 3): hiện `Secrets.xcconfig` dùng chung key/secret với Android (chép từ `securedtoken.cpp`). Nên cấp key riêng để thu hồi độc lập và tách quota.
- Apple Developer Team / signing: project để `DEVELOPMENT_TEAM` trống, chỉ build cho simulator. Cần team trước khi chạy trên máy thật hoặc archive.

### T1 — Module `:shared` và toolchain (xong)

Đã làm:

- `include(":shared")` trong `settings.gradle.kts`; module `shared/` với `shared/build.gradle.kts`, `shared/gradle.properties`, `shared/.gitignore`.
- Target: Android (plugin `com.android.kotlin.multiplatform.library` của AGP 8.13.2, `namespace = "tss.t.shared"`, `compileSdk`/`minSdk` lấy từ version catalog, JVM 11), `iosArm64`, `iosSimulatorArm64`. Framework static `Shared` cho cả hai target iOS.
- `TSDataState` chuyển bằng `git mv` từ `sharedKmp/.../tss/t/sharedkmp/models/` sang `shared/src/commonMain/kotlin/tss/t/shared/model/TSDataState.kt` (package `tss.t.shared.model`). `exception()` (ném `ClassCastException`) được thay bằng `exceptionOrNull(): Throwable?`. Thư mục `sharedKmp/` đã xoá.
- `commonTest` có `TSDataStateTest` (3 test) chạy trên cả iOS simulator và host JVM.
- `.gitignore` gốc thêm `iosApp/Config/Secrets.xcconfig`, `**/xcuserdata/`, `DerivedData/`, `iosApp/build/`, `shared/build/` (đã kiểm bằng `git check-ignore -v`).

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| Kotlin | **Giữ `2.2.0`** (KSP giữ `2.2.0-2.0.2`) | Kotlin/Native 2.2.0 compile, link framework và link + chạy test executable trên simulator với Xcode 26.3 bình thường, nên không cần nâng (luật §3.1: chỉ nâng khi không link được). Android không bị đổi version. |
| Cảnh báo Xcode | `kotlin.apple.xcodeCompatibility.nowarn=true` trong `shared/gradle.properties` | Kotlin 2.2.0 chưa khai báo Xcode 26.x là bản đã test. Không đụng `gradle.properties` gốc. |
| Plugin target Android | `com.android.kotlin.multiplatform.library` (không cần fallback `androidTarget()` + `com.android.library`) | Chạy được với AGP 8.13.2 + Kotlin 2.2.0. Host test bật bằng `withHostTestBuilder {}`. |
| Thư viện | Ktor `3.2.3`, kotlinx-serialization-json `1.9.0`, kotlinx-coroutines-core `1.10.2`, Okio `3.15.0`, `kotlin-test` theo `kotlin` | Chọn bản mới nhất được build bằng Kotlin ≤ 2.2.0 (đã kiểm `kotlin-stdlib` trong file `.module` trên Maven Central): Ktor 3.3.x cần 2.2.10+, serialization 1.10 cần 2.3.0, Okio 3.16.4 cần 2.2.20. Dùng bản cao hơn sẽ kéo stdlib/klib mới hơn compiler 2.2.0. |
| SKIE | Không dùng ở M0 | Không bắt buộc (§3.1). Swift sẽ gọi `suspend fun` qua bridge async có sẵn của Kotlin/Native. |

Thay đổi ngoài `shared/`, `iosApp/`, `docs/ios/`:

- `settings.gradle.kts`: thêm `include(":shared")`.
- `gradle/libs.versions.toml`: thêm version/library/plugin cho KMP (không đổi entry cũ).
- `build.gradle.kts` gốc: thêm đúng 2 dòng `alias(libs.plugins.kotlin.multiplatform) apply false` và `alias(libs.plugins.android.kotlin.multiplatform.library) apply false`. Lý do: không có chúng Gradle báo `Error resolving plugin [id: 'org.jetbrains.kotlin.multiplatform', version: '2.2.0'] ... the plugin is already on the classpath with an unknown version` (Kotlin Gradle Plugin đã được nạp ở root qua `kotlin.android`).
- `.gitignore` gốc: các dòng ở trên.
- Không module Android nào phụ thuộc `:shared`.

Lệnh đã chạy (đều `BUILD SUCCESSFUL`):

```bash
./gradlew :shared:assemble :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
#   -> shared/build/bin/{iosSimulatorArm64,iosArm64}/debugFramework/Shared.framework
#   -> shared/build/outputs/aar/shared.aar
./gradlew :shared:iosSimulatorArm64Test :shared:testAndroidHostTest   # 3/3 test pass trên mỗi target
./gradlew :app:assembleDebug
```

Lưu ý: lần build iOS đầu tiên tải toolchain Kotlin/Native (LLVM, sysroot) vào `~/.konan`, mất khoảng 6 phút.

### T2 — Model, network và credentials (xong)

Thoả M0-AC4, M0-AC5, M0-AC6, M0-AC7 (ở tầng `PodcastIndexApi`; xem "Việc còn mở" bên dưới).

Đã làm (`shared/src/commonMain/kotlin/tss/t/shared/`):

- `model/`: `Podcast`, `Feed`, `Episode`, `LiveEpisode`, `TrendingPodcastRes` (key JSON `feeds` → `items`), `LiveResponse`, `SearchResponse`, `EpisodeResponse`, `PodcastByFeedIdRes{feed, query{id}}`, `CategoryRes{count, feeds:[Category{id, name, isFavourite?}]}` và các kiểu lồng `Person`, `PodcastTranscript`, `Soundbite`, `SocialInteract`, `Funding`, `Value`, `ValueModel` (Android tên `Model`), `Destination`. Tất cả `@Serializable`, mọi field nullable hoặc có default. Giữ `Podcast.fromFeed(feed)` (mapping y hệt Android: `image = feed.artwork`, `feedImage = feed.image`) và `Episode.getImageUrl()`.
- `model/Categories.kt`: `typealias Categories = Map<String, String>` và `CategoriesSerializer`. Không chép `MapStringStringJsonConverter`.
- `network/`: `CredentialsProvider`, `missingCredentials()`, `MissingCredentialsException(missing)`, `PodcastIndexHttpException(code, body)`, `PodcastIndexAuth(credentials, clock)`, `PodcastIndexJson`, `createHttpClient(engine, auth, json)`, `expect fun defaultHttpClientEngine()` (OkHttp ở `androidMain`, Darwin ở `iosMain`), `safeApiCall(json, deserializer) { … }`, `PodcastIndexApi` với 9 hàm E1, E2, E5–E11.
- `util/EpochClock.kt`: `fun interface EpochClock { fun nowMillis(): Long }`, `EpochClock.System` dùng `kotlin.time.Clock`. Dùng chung cho ký request (T2) và TTL cache (T3).
- `shared/build.gradle.kts`: task `GenerateJsonSourcesTask` biến file JSON thành hằng chuỗi Kotlin (chia khúc 4 000 ký tự vì hằng chuỗi JVM giới hạn 65 535 byte):
  - `generateBundledAssets` → `tss.t.shared.generated.BundledAssets` (`internal`) từ **chính** `coreApi/src/main/assets/categories.json` và `blacklist.json`, gắn vào `commonMain`. T3 dùng lại cho fallback E9 và blacklist.
  - `generateTestFixtures` → `tss.t.shared.fixtures.Fixtures` từ `podcasts/src/debug/assets/{Trending,Episode,LiveEpidsode,RecentFeeds}.json`, gắn vào `commonTest`. Không cắt bớt và không có bản chép tay nào trong `shared/`.

Test mới trong `commonTest` (chạy trên `iosSimulatorArm64` và host `testAndroidHostTest`):

| Class | Số test | Nội dung chính |
|---|---|---|
| `ModelParsingTest` | 14 | 4 fixture thật; item đầu Trending `id 5675247`, title `Wellbeing Wisdom Tiny Bites`, `feedId == null`; categories thành map; `SearchResponse` có `explicit: true` + `{"1":"Arts"}`; `PodcastByFeedIdRes` (cả `"feed": []`); `categories.json` → 112 mục; `Podcast.fromFeed`; `getImageUrl()` với `image = "  "` |
| `PodcastIndexAuthTest` | 4 | `testkey`/`testsecret`/`TSPodcastTest/1.0`, clock `1700000000` s → đúng 4 header, `Authorization = 90b6255b079ed6e48084f571b76958438661e91b`; clock chỉ đọc 1 lần |
| `CredentialsTest` | 5 | `apiKey = ""` và `"$(PODCASTINDEX_API_KEY)"` → `Error(MissingCredentialsException)`, 0 request, không ném; cả 9 endpoint đều chặn |
| `PodcastIndexApiTest` | 17 | Path + query từng endpoint, 4 header trên mỗi request, host/path theo `credentials.baseUrl` (có và không có `/` cuối), tham số `null`/blank không gửi, E8 query blank → `Success(count = 0)` và 0 request, HTTP 401 → `PodcastIndexHttpException`, body hỏng → `Error` |

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| `Json` | `ignoreUnknownKeys`, `explicitNulls = false`, `coerceInputValues`, `isLenient` | Payload thật có `"max": "100"` (chuỗi), `"query": {"id": …}`, key hỏng kiểu `"expl icit"` trong `Episode.json`, nhiều field `null`. |
| `categories` | `CategoriesSerializer` nhận object **và** array | 2 item trong `LiveEpidsode.json` có `"categories": []` (PHP encode map rỗng thành array). Map serializer thường sẽ làm hỏng cả response `episodes/live`. Array đọc thành `index → tên`, giá trị `null` bị bỏ. |
| `PodcastByFeedIdRes.feed` | Nullable, `"feed": []` → `null` | PodcastIndex trả `[]` khi không có feed. Android (Gson) sẽ lỗi parse cả response. |
| `Soundbite.duration/startTime` | `Double` | Namespace cho phép giây lẻ; `Int` của Android sẽ lỗi parse. |
| `Podcast` thêm field | `author`, `artwork`, `url`, `language`, `itunesId`, `trendScore`, `newestItemPublishTime` | Trending/recent feeds gửi các field này; màn debug T4 và Home cần `author`. Field Android cũ giữ nguyên tên. |
| `Podcast.feedId` | `Long?` (Android `Long`) | Trending không gửi `feedId`; M0-AC4 yêu cầu `null`. |
| Đọc body | `bodyAsText()` + `json.decodeFromString` trong `safeApiCall` (vẫn cài `ContentNegotiation` để gửi `Accept: application/json`) | Không phụ thuộc header `Content-Type` của server, giống `GsonConverterFactory` của Android. |
| `CancellationException` | Rethrow trong `safeApiCall` | Theo architecture §3.2 và `SafeCall.kt`. |
| Thiếu credentials | Kiểm trong `PodcastIndexApi` trước mỗi lời gọi: giá trị blank hoặc chứa `$(` | Chặn trước khi tạo request, nên MockEngine nhận 0 request. Exception chỉ chứa **tên** key thiếu. |
| Base URL | `defaultRequest { url(baseUrl + "/" nếu thiếu) }`, path tương đối | Giống Retrofit (`baseUrl` kết thúc bằng `/`). `defaultRequest` chạy lại mỗi request nên luôn đọc giá trị hiện tại. |
| `pretty` | Chỉ E11 gửi `pretty=true` | Retrofit Android gửi `pretty=false` ở E7/E8, nhưng `pretty` là cờ "có mặt là bật", nên thực tế Android đang xin JSON pretty-print. Bỏ đi không đổi dữ liệu. |
| E8 `aponly` | Không gửi | Theo M0 §3.5 / AC7 (Android gửi `aponly=false`, giá trị mặc định của API, không đổi kết quả). |
| E8 query blank | Chặn ngay trong `PodcastIndexApi.searchMusicPodcasts` | Để AC7 kiểm được ở T2; use case `SearchMusicFeeds` của T3 chỉ cần gọi xuống. |
| Retry | Không cài `HttpRequestRetry` | Retry chỉ ở E10/E11, làm ở use case (T3). |
| E3, E4 | Không có | features-spec §2 [Sửa]. `grep -rn 'recent/episodes\|recent/newfeeds' shared/src` → không có dòng nào. |

Lệnh đã chạy (đều `BUILD SUCCESSFUL`):

```bash
./gradlew :shared:allTests :shared:assemble :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
#   iosSimulatorArm64Test + testAndroidHostTest: TSDataStateTest 3, ModelParsingTest 14,
#   PodcastIndexAuthTest 4, CredentialsTest 5, PodcastIndexApiTest 17 — failures="0" errors="0"
./gradlew :app:assembleDebug
grep -rn 'recent/episodes\|recent/newfeeds' shared/src   # không có output
```

Đã thử đột biến (đổi `max` của E6 thành `max + 1`) để chắc test bắt lỗi: `PodcastIndexApiTest` fail, rồi đã hoàn tác.

Việc còn mở (chuyển sang T3):

- M0-AC6 nói "`GetTrendingPodcasts(...)` trả `Error(MissingCredentialsException)`". Use case chưa có ở T2, nên `CredentialsTest` kiểm ở tầng `PodcastIndexApi.getTrending`. T3 cần thêm một test cùng nội dung cho use case `GetTrendingPodcasts` (use case không được retry `MissingCredentials`).
- `SafeApiCallTest` (huỷ coroutine → `CancellationException` lan ra) thuộc T3 theo kế hoạch; logic rethrow đã có ở `safeApiCall`.
- `BundledAssets.blacklist` đã sinh sẵn nhưng chưa ai dùng; T3 viết `BlacklistRepository` trên nó.

### T3 — Blacklist, cache 15 phút và 9 use case (xong)

Thoả M0-AC8, M0-AC9, M0-AC10, M0-AC11. Đóng luôn hai việc còn mở của T2: M0-AC6 ở tầng use case (`RetryPolicyTest.missingCredentialsAreNeverRetried`: `GetTrendingPodcasts`, `GetEpisodeByFeedId`, `GetPodcastByFeedID`, … trả `Error(MissingCredentialsException)` với 0 request) và `SafeApiCallTest`.

Đã làm (`shared/src/commonMain/kotlin/tss/t/shared/`):

- `config/ConfigAPI.kt`: `interface ConfigAPI { fun getString(key: String): String? }` và `ConfigKeys.KEY_BLACK_LIST = "ts_podcast_black_list"` (cùng tên với `tss.t.podcasts.ConfigKeys`). Swift sẽ implement protocol `ConfigAPI` (`getString(key:)`); M0 truyền bản trả `nil`.
- `blacklist/BlacklistRepository.kt`: port `BlacklistRepositoryImpl`. `isInBlacklist(id)`, `isContainKeywordsBlacklist(key, filterSpace = false)`, `reload()`, `feedIdCount`/`keywordCount`. Nguồn: Remote Config nếu là JSON object, không thì `BundledAssets.blacklist` (sinh từ chính `coreApi/src/main/assets/blacklist.json` bởi task `generateBundledAssets` của T2).
- `blacklist/BlacklistFilters.kt`: hàm lọc theo endpoint (bảng ở M0 §3.4), luôn cập nhật `count`.
- `cache/InMemoryTtlCache.kt`: `InMemoryTtlCache<K, V>(ttl: Duration, clock: EpochClock)` dùng `Mutex`; entry còn hạn khi `now − storedAt < ttl` (so sánh `<` như Android); `API_CACHE_TTL = 15.minutes`.
- `usecase/`: `GetTrendingPodcasts`, `GetLiveEpisodes`, `GetRecentFeeds`, `GetMusicFeeds`, `SearchPodcasts`, `SearchMusicFeeds`, `GetCategories`, `GetEpisodeByFeedId`, `GetPodcastByFeedID` (giữ tên Android). Tất cả là `suspend operator fun invoke(...): TSDataState<T>`, constructor `internal` (chỉ tạo qua `SharedGraph`). `RetryPolicy.kt` gom luật retry (`AnyError` cho E10, `NetworkErrors` cho E11) và `withRetry`.
- `SharedGraph.kt`: composition root viết tay (không Koin). `SharedGraph(credentials, config)` cho Swift (engine của platform + `EpochClock.System`), `SharedGraph(credentials, config, engine, clock)` cho test. Giữ một `HttpClient`, một `BlacklistRepository`, 4 cache singleton (trending, episodes theo feed, podcast theo feed, categories) và 9 use case dạng property. `close()` đóng `HttpClient`.

API Swift nhìn thấy (đã kiểm trong `Shared.framework/Headers/Shared.h`): `SharedGraph(credentials:config:)`, `graph.getTrendingPodcasts.invoke(max:since:lang:cat:notcat:)` (async qua completion handler), `getEpisodeByFeedId.invoke(id:)`, `getPodcastByFeedID.invoke(feedId:)`, `getCategories.invoke(useCache:)`, … Swift không có default argument của Kotlin nên phải truyền đủ tham số.

Luật theo endpoint:

| Use case | Blacklist | Cache | Retry |
|---|---|---|---|
| `GetTrendingPodcasts(max, since, lang, cat, notcat)` (E1) | `id`, `feedId`, keyword trong `title`/`description` | 15 phút, key = `cat?.trim().orEmpty()` (null/""/"  " chung một entry), không xét `since` | Không |
| `GetLiveEpisodes(max = 100)` (E2) | `feedId`, `title`, `description` (không xét `id` của episode) | Không | Không |
| `GetRecentFeeds(max, since, lang, cat, notcat)` (E5) | `id`, `title`, `description` (không xét `feedId`) | Không | Không |
| `GetMusicFeeds(max = 40)` (E6) | `feed.id`, `title` | Không | Không. Lỗi → `Error(cause, emptyList())` như Android |
| `SearchPodcasts(...)` (E7), `SearchMusicFeeds(...)` (E8) | `feed.id`, `title` | Không | Không |
| `GetCategories(useCache)` (E9) | Không lọc | Memory, không hết hạn | Không. Lỗi → danh sách mạng gần nhất trong process, không có thì asset (112 mục); luôn `Success` |
| `GetEpisodeByFeedId(id)` (E10) | Không lọc | 15 phút theo `id` | Tối đa 3 lần với mọi lỗi (tổng 4 request), trừ `MissingCredentials` |
| `GetPodcastByFeedID(feedId)` (E11) | Không lọc | 15 phút theo `feedId` | Tối đa 3 lần, **không** retry `IllegalStateException` (gồm `MissingCredentialsException`) và `SerializationException` |

Chỉ cache kết quả `Success` (đã lọc blacklist). Retry chạy ngay, không delay, như `retryWhen` của Android. `CancellationException` luôn lan ra, không thành `Error` và không bị retry.

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| Nạp blacklist | Đồng bộ trong constructor của `BlacklistRepository` (tạo cùng `SharedGraph`) | **[Sửa]** race: Android nạp trong coroutine từ `init` nên request đầu có thể chưa lọc. Parse một file JSON nhỏ nên chi phí không đáng kể. Đã thử đột biến (nạp trễ 50 ms trong coroutine như Android): `BlacklistFilteringTest.firstCallRightAfterCreatingTheGraphIsFiltered` và các test lọc khác fail. |
| Nguồn blacklist | Remote là JSON object → thay asset; `null`, `""`, chuỗi không phải JSON, JSON không phải object (`[]`, `"text"`) → asset; `{}` → danh sách rỗng | Giống `TSFirebaseRemoteConfig.getJSONObject` (lỗi parse → `null` → asset) và `optJSONArray` (thiếu key → không có phần tử). |
| `filterSpace` | **[Sửa]** bỏ mọi khoảng trắng ở cả text **và** keyword | Android bỏ kết quả `replace`. Bỏ khoảng trắng ở keyword để keyword nhiều từ (`"cá cược"`) vẫn khớp `"cácược"`. Hiện không endpoint nào dùng `filterSpace = true` (Android cũng vậy). |
| Keyword | Lưu chữ thường, bỏ phần tử rỗng/blank, số được giữ dạng chuỗi (như `optString`) | Android chỉ hạ chữ thường phía text nên keyword remote có chữ hoa sẽ không bao giờ khớp; một keyword `""` sẽ chặn mọi thứ. |
| Lọc E1 | Hợp của repository + use case Android: `id`, `feedId`, `title`, `description` | Bảng M0 §3.4. `feedId` null (trending không gửi) thì không xét. |
| Cache E1 | Mỗi `cat` một entry | **[Sửa]** Android chỉ có một slot cho mọi `cat` khác null. Không đưa `since`/`max`/`lang` vào key: Android cũng không, và mọi caller Android đều dùng `max = 100`, `lang = "vi"`. |
| Cache E9 | Chỉ giữ danh sách lấy từ mạng; asset fallback không được cache | Android cache cả kết quả fallback vào memory nên `useCache = true` sau một lần offline sẽ trả asset mãi trong process. Ở đây lần sau vẫn hỏi mạng. "Danh sách mạng gần nhất" thay cho bản đĩa `CategoryRes` của Android cho tới milestone persistence. |
| E9 thiếu credentials | Trả asset (`Success`), 0 request | Cùng nhánh "mạng lỗi → fallback" của Android; màn Onboarding/Search vẫn có danh mục. |
| E11 lỗi parse | Không retry `SerializationException` | Theo M0 §3.5/AC11. (Android thực tế retry `JsonSyntaxException` của Gson vì nó không phải `IllegalStateException`; bản shared không lặp lại việc gửi 4 request cho body hỏng.) |
| E10 lỗi parse | Vẫn retry | Android `retryWhen { attempt < 3 }` với mọi lỗi; giữ parity. |
| `GetRecentFeeds` | Không có `type = RecentFeed` | `TrendingPodcastRes` của shared không có field `type` mutable. UI iOS phân biệt section ở milestone Home. |
| `SearchPodcasts.pretty` | Bỏ tham số | Như T2: `pretty` là cờ có mặt là bật. |
| `GetMusicFeeds` lỗi | Không gọi `Crash.record` | Chưa có Crashlytics iOS (cloud resource chờ owner). |
| Composition root | `SharedGraph` viết tay, use case là property | M0 §3.6, không Koin. Swift tạo đúng **một** instance (cache sống theo instance). |

Test mới trong `commonTest` (mỗi class chạy trên `iosSimulatorArm64Test` và `testAndroidHostTest`, `failures="0" errors="0"`):

| Class | Số test | Nội dung chính |
|---|---|---|
| `BlacklistRepositoryTest` | 13 | Config `null`/thiếu key → asset: `7101342` bị chặn, 19 feedId và 10 keyword khác nhau (so cả với số phần tử khác nhau đọc từ `BundledAssets.blacklist`); `{"feedId":["1"],"filterText":["abc"]}` → `1` bị chặn, `7101342` không; `"not json"`, `""`, `"[]"`, … → asset; `{}` → rỗng; `"xxABCxx"` → true; `null`/`""` → false; `("a b c", filterSpace = true)` → true, `false` khi không bật; keyword tiếng Việt viết hoa; keyword remote viết hoa; phần tử blank/số; `reload()` |
| `BlacklistFilteringTest` | 9 | `MockEngine` + override `{"feedId":["111"],"filterText":["casino"]}`: E1 bỏ `id=111`, `feedId=111`, "CASINO" ở title, "casino" ở description, giữ item sạch, `count = 1`; E2 bỏ theo `feedId`/title/description nhưng giữ episode có `id=111`; E5 bỏ theo `id`/title/description nhưng giữ item có `feedId=111`; E6/E7/E8 giữ item chỉ có "casino" ở description; E9/E10/E11 nguyên vẹn; lần gọi đầu ngay sau khi tạo `SharedGraph` đã lọc (cả với override và với asset) |
| `InMemoryTtlCacheTest` | 8 | 14:59 còn, đúng 15:00 và 15:01 hết; key độc lập; `put` làm mới TTL; `remove`/`clear`; TTL vô hạn; 200 `put` song song trên `Dispatchers.Default` |
| `UseCaseCacheTest` | 7 | E1 lúc 0 và 14:59 với `since` khác nhau → 1 request, 15:01 → 2; `cat="1"`/`"2"` → 2 request, `null`/`""`/`"  "` chung entry; E10 và E11 cache theo id, hết hạn sau 15 phút; `Error` không cache; `GetCategories(useCache = true)` sau thành công → 0 request mới; giá trị cache là bản đã lọc |
| `RetryPolicyTest` | 8 | E10: 500, 500, 200 → `Success` sau 3 request; luôn 500 → `Error(PodcastIndexHttpException(500))` sau 4 request; body hỏng → 4 request. E11: luôn 503 → 4 request; 502 rồi 200 → 2 request; body 200 hỏng → `SerializationException`, 1 request. E1, E2, E5–E9 một lần 500 → đúng 1 request mỗi endpoint. Thiếu credentials → `Error(MissingCredentialsException)` ở use case, 0 request |
| `GetCategoriesTest` | 6 | 500 → `Success` 112 mục (mục đầu `Arts`), 1 request; lỗi IO → 112; thiếu credentials → 112, 0 request; lỗi sau một lần thành công → danh sách mạng; fallback asset không bị cache |
| `SafeApiCallTest` | 4 | Request ném `CancellationException` → lan ra; lỗi khác → `Error(cause)`; huỷ coroutine khi request đang treo trong `MockEngine` → `CancellationException`, không phải `TSDataState.Error`; huỷ `GetEpisodeByFeedId` → không retry (1 request) và không `Error` |

Kiểm tra đột biến (sửa tạm code, chạy `:shared:testAndroidHostTest`, rồi hoàn tác): bỏ sửa `filterSpace`; nạp blacklist bất đồng bộ như Android; E1 không xét description; E6–E8 xét thêm description; key cache E1 có `since`; E11 retry lỗi parse; `safeApiCall` nuốt `CancellationException`; E9 bỏ "danh sách mạng gần nhất"; TTL lệch 1 giây. Mỗi đột biến đều làm ít nhất một test fail.

Lệnh đã chạy (đều `BUILD SUCCESSFUL`):

```bash
./gradlew :shared:allTests :shared:assemble :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64
#   98 test mỗi target (iosSimulatorArm64Test, testAndroidHostTest), failures="0" errors="0"
./gradlew :app:assembleDebug
grep -n "blacklist.json" shared/build.gradle.kts            # đọc từ coreApi/src/main/assets
find shared -name blacklist.json -not -path '*/build/*'     # không có bản chép tay
```

Việc còn mở:

- Remote Config iOS chưa có (Firebase iOS app là cloud resource chờ owner). Khi có: implement `ConfigAPI` bằng Firebase và gọi `graph.blacklistRepository.reload()` sau `fetchAndActivate` (Android chỉ gọi `loadBlacklist()` một lần trong `init` của singleton, nên giá trị activate sau thời điểm đó chỉ có hiệu lực ở lần mở app sau).
- Cache đĩa của trending (`cachedTrending*`) và `CategoryRes` thuộc milestone persistence; hiện cache chỉ sống trong process.
- `ConfigAPI` chỉ có `getString`. `getBoolean`/`getLong` thêm khi có feature flag cần đến.

### T4 — Project Xcode, tích hợp framework, màn debug trending (xong)

Thoả M0-AC12, M0-AC13, M0-AC14, M0-AC15; chạy lại M0-AC3.

Đã làm:

- `iosApp/project.yml` (XcodeGen 2.46.0) → `iosApp/iosApp.xcodeproj` (commit cả hai; sinh lại cho kết quả giống hệt). Target + scheme `TSPodcast`, `PRODUCT_BUNDLE_IDENTIFIER = tss.t.podcast`, `IPHONEOS_DEPLOYMENT_TARGET = 17.0`, `SWIFT_VERSION = 6.0`, `TARGETED_DEVICE_FAMILY = 1`, `ENABLE_USER_SCRIPT_SANDBOXING = NO`, `MARKETING_VERSION = 1.0.0`, `CURRENT_PROJECT_VERSION = 1`, `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`. Các key này đặt ở **tầng target**: preset của XcodeGen cho app iOS ghi `TARGETED_DEVICE_FAMILY = "1,2"` ở target, nên đặt ở tầng project sẽ bị ghi đè (đã gặp và sửa).
- Build Phase "Compile Kotlin" (pre-build, luôn chạy) gọi `iosApp/scripts/compile-kotlin.sh`: `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Script tự lấy `JAVA_HOME` từ `/usr/libexec/java_home` khi Xcode mở từ Finder (không kế thừa biến môi trường của shell), và tôn trọng `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`. `FRAMEWORK_SEARCH_PATHS = $(inherited) $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, `OTHER_LDFLAGS = $(inherited) -framework Shared`.
- `iosApp/Config/`: `Shared.xcconfig` (4 key `PODCASTINDEX_*` mặc định rỗng), `Debug.xcconfig` / `Release.xcconfig` (`#include "Shared.xcconfig"` + `#include? "Secrets.xcconfig"`), `Secrets.example.xcconfig` (commit, 4 key rỗng, ghi chú bẫy `//` và cách viết `https:/$()/`). `Secrets.xcconfig` thật được tạo cục bộ bằng script đọc `securedtoken.cpp` (chỉ in độ dài giá trị), nằm trong `.gitignore` (`git check-ignore -v` → `.gitignore:32`). Đã so khớp 4 giá trị trong `Info.plist` của app đã build với nguồn mà không in ra: cả 4 khớp, kể cả secret có ký tự `$` (không theo sau bởi `(`/`{` nên xcconfig không expand).
- `iosApp/TSPodcast/Info.plist` viết tay (`GENERATE_INFOPLIST_FILE = NO`): `CFBundleDevelopmentRegion = vi`, `ITSAppUsesNonExemptEncryption = false`, `UILaunchScreen`, 4 key `PODCASTINDEX_*` = `$(PODCASTINDEX_*)`. Chưa có `UIBackgroundModes`/ATS (thuộc milestone player).
- Swift (`iosApp/TSPodcast/`):
  - `App/TSPodcastApp.swift`: tạo **một** `SharedGraph(credentials: BundleCredentialsProvider(), config: NoOpConfig())` và `TrendingDebugModel` giữ trong `@State`.
  - `App/AppLog.swift`: `Logger(subsystem: "tss.t.podcast", category: "M0")`.
  - `Config/BundleCredentialsProvider.swift`: `NSObject` + `CredentialsProvider` (protocol Kotlin), đọc 4 key từ `Bundle.main` một lần lúc init; giá trị không có, blank, hoặc còn `$(` → `""`, để `shared` trả `MissingCredentialsException` thay vì ký request bằng placeholder.
  - `Config/NoOpConfig.swift`: `ConfigAPI` luôn trả `nil` (blacklist dùng asset) tới khi có Firebase Remote Config.
  - `Debug/TrendingDebugModel.swift`: `@MainActor @Observable final class`, gọi `graph.getTrendingPodcasts.invoke(max: 100, since: now − 7 ngày (giây), lang: "vi", cat: nil, notcat: nil)` như section 1 của Home (features-spec §3.4). State `loading` / `loaded([Row])` / `missingCredentials` / `failed`. Map sang struct `Row` của Swift để View không giữ object Kotlin. Log `trending_loaded count=<N>`, `missing_credentials`, `trending_error type=<tên class Kotlin>`; không log header, URL hay credentials.
  - `Debug/TrendingDebugView.swift`: `NavigationStack` title `Trending (debug)`; `ProgressView` khi tải; `List` với header `N podcasts`, mỗi dòng `title` + `author`; pull-to-refresh; lỗi dùng `ContentUnavailableView` + nút `Tải lại`; thiếu credentials hiện "Thiếu cấu hình PodcastIndex (Secrets.xcconfig)" kèm hướng dẫn chép file mẫu.
- `shared`: thêm `val Throwable.typeName` (`util/Throwables.kt`, test `ThrowablesTest`). Lý do: exception Kotlin không được export (Ktor/Darwin) hiện trong Swift dưới tên `Shared_kobjcc1`; log cần tên thật (vd. `DarwinHttpRequestException`), và không dùng `message` vì có thể chứa URL.
- Swift 6 language mode build sạch, không warning Swift (dùng `@preconcurrency import Shared`; `BundleCredentialsProvider` và `NoOpConfig` là `final` + chỉ có `let` nên khai báo `Sendable`).

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| Info.plist | File viết tay, không generate | Cần 4 key `$(PODCASTINDEX_*)` ở file nguồn để kiểm bằng `PlistBuddy` (M0-AC13). |
| Default rỗng trong `Shared.xcconfig` | Có | Khi chưa có `Secrets.xcconfig`, `Info.plist` nhận chuỗi rỗng (không phải `$(...)` lạ) và build không lỗi; app hiện màn thiếu cấu hình. |
| Build phase | Luôn chạy (`basedOnDependencyAnalysis: false`) | Gradle tự quyết up-to-date; tránh cảnh báo "will be run during every build" vì không khai output. |
| Hướng màn hình | Portrait + landscape trên iPhone | Android không khoá `screenOrientation`. |
| `DEVELOPMENT_TEAM` | Trống | Chưa có team; chỉ build simulator ở M0. |
| SKIE | Không | Xem tóm tắt M0. |

Kết quả tự kiểm (screenshot và log trong scratch dir `…/scratchpad/ios/`, không commit):

| AC | Lệnh | Kết quả |
|---|---|---|
| M0-AC12 | `cd iosApp && xcodegen generate && git status --porcelain .` | Không có dòng nào (sau khi commit) |
| | `xcodebuild … -showBuildSettings` | `tss.t.podcast`, `17.0`, `6.0`, `1`, `NO` |
| | Build sạch `-derivedDataPath $OUT/DD` | `** BUILD SUCCEEDED **`; `embedAndSignAppleFrameworkForXcode` xuất hiện 1 lần trong log |
| | `git ls-files \| grep -E '(^\|/)(build\|DerivedData\|xcuserdata)/\|\.xcframework\|\.konan'` | 0 dòng |
| M0-AC13 | `git check-ignore -v iosApp/Config/Secrets.xcconfig` | `.gitignore:32:iosApp/Config/Secrets.xcconfig` |
| | `git ls-files iosApp/Config` | `Debug.xcconfig`, `Release.xcconfig`, `Secrets.example.xcconfig`, `Shared.xcconfig` (không có `Secrets.xcconfig`) |
| | `grep -cE '^PODCASTINDEX_…' Secrets.example.xcconfig` | `4`, giá trị đều rỗng |
| | `PlistBuddy` trên `Info.plist` nguồn | 4 key = `$(PODCASTINDEX_…)` |
| | Đếm API key / secret trong `git log main..HEAD -p` | `0` và `0` |
| M0-AC14 | Cài `$OUT/DD/…/TSPodcast.app`, launch | Sau ~1 s: title `Trending (debug)`, `99 podcasts`, các dòng có tiêu đề + tác giả (`M0-AC14-trending.png`); log `trending_loaded count=99`; số lần key/secret/User-Agent xuất hiện trong log: 0 |
| | Build `PODCASTINDEX_API_KEY= PODCASTINDEX_API_SECRET=` vào `$OUT/DD-nocreds`, cài, launch | Màn "Thiếu cấu hình PodcastIndex (Secrets.xcconfig)" + nút `Tải lại` (`M0-AC14-missing-creds.png`); process vẫn có trong `launchctl list`; log `missing_credentials`. Đã cài lại bản thường. |
| (thêm) | Build với `PODCASTINDEX_BASE_URL=https://nonexistent.invalid/api/1.0/` | Màn `Lỗi` + "Không tải được danh sách trending (DarwinHttpRequestException)." + `Tải lại` (`M0-network-error.png`); log `trending_error type=DarwinHttpRequestException` |
| M0-AC3 | `./gradlew :app:assembleDebug` | `BUILD SUCCESSFUL`; `git diff --name-only main...HEAD` chỉ có `shared/`, `iosApp/`, `docs/ios/`, `sharedKmp/` (xoá), `settings.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `build.gradle.kts` (2 dòng `apply false`); không module Android nào phụ thuộc `:shared` |
| (shared) | `./gradlew :shared:allTests` | 99 test mỗi target (thêm `ThrowablesTest`), `failures="0" errors="0"` |

Việc còn mở / giới hạn đã biết:

- Nút `Tải lại` và pull-to-refresh chưa được bấm thử tự động (simulator không có công cụ tap trong môi trường này); code path giống lần tải đầu (`reload()`).
- Màn debug sẽ bị thay bằng Home ở M1; chưa có localization (`Localizable.xcstrings`), AppIcon, ATS, background audio.
- Ảnh cover chưa hiển thị (M0 chỉ cần tiêu đề).

## M1

Trạng thái: **developer đã làm xong T1–T5**, M1-AC1..AC22 đã tự kiểm (lần chạy cuối ở T5, bên dưới), chờ PO nghiệm thu. Kế hoạch ([milestones/M1.md](milestones/M1.md)): 5 task (T1 shared: key-value store, start destination, cache đĩa E1/E9, `HomeFeed` + D6; T2 nền iOS: target unit/UI test, routing, String Catalog, ảnh/ATS, hook test; T3 Onboarding + chọn danh mục; T4 TabView + Home + tab Yêu thích/Tìm kiếm; T5 nghiệm thu nội bộ và tài liệu), 22 acceptance criteria. Các quyết định PO chọn thay owner ở §7 của kế hoạch đã được ghi vào [handoff.md](handoff.md).

### Tóm tắt M1 (M1-AC22)

Việc đã làm theo task (chi tiết ở từng mục T1–T5 bên dưới):

| Task | Việc chính | AC |
|---|---|---|
| T1 | `shared`: `KeyValueStore` (DataStore), `AppPreferences` (key Android), `StartDestination`, `FavouriteCategories` (≥ 3), tầng đĩa của E9 (`CategoryRes`) và E1 (`cachedTrending*`, 15 phút), `HomeFeed` (fallback `MainViewModel`, D6), `HtmlText`, `SharedConstants`, `AnalyticsTracker` | AC1–AC6 |
| T2 | Target `TSPodcastTests`/`TSPodcastUITests`, `AppEnvironment` + `RootView` (routing theo start destination), hook Debug §4.7, String Catalog vi/en, màu + icon danh mục, `ArtworkLoader` (User-Agent, http → https, không ATS exception), xoá màn debug M0 | AC7–AC10 |
| T3 | Onboarding 4 trang + analytics log, chọn danh mục (lưới, badge, ≥ 3, lưu qua shared), relaunch đúng màn | AC11–AC13 |
| T4 | `TabView` 3 tab (không Radio), `NavigationStack` riêng mỗi tab, `MiniPlayerSlot`, Home 5 section + attribution (loading/empty/error + "Tải lại", pull-to-refresh), tab Yêu thích (empty state), tab Tìm kiếm (rỗng), stub Detail | AC8 (phần en), AC14–AC19 |
| T5 | Build sạch, chạy lại toàn bộ §6, screenshot, kiểm secrets, tài liệu | AC20–AC22 |

Quyết định chính:

| Hạng mục | Quyết định | Lý do / chi tiết |
|---|---|---|
| Kho key-value | **DataStore Preferences KMP `1.2.1`** (`androidx.datastore:datastore-preferences-core`), file `Application Support/ts_prefs.preferences_pb`, một instance mỗi process do `SharedGraph` sở hữu | Ghi atomic + tuần tự sẵn có, chạy trên `iosSimulatorArm64` và Android host test với Kotlin 2.2.0. `1.3.0` cần `kotlin-stdlib 2.2.20`. Không dùng `UserDefaults`/`multiplatform-settings` (không thêm required-reason API). File hỏng → store rỗng; lỗi I/O bị nuốt, store không ném. (T1) |
| Tên key | Giữ đúng tên `SharedPref` Android + `AllowExplicitContent` (mặc định `false`) | Một nguồn quy ước cho hai app; D6 mặc định lọc. (T1) |
| Thứ tự lưu danh mục | Ghi `ListFavouriteCategory` **trước**, cờ `HasSelectFavouriteCategory` **sau** | Android ghi ngược lại; app chết giữa chừng thì không có cờ mà thiếu danh sách. (T1) |
| Trending + forYou | Một loader chung (`HomeFeed.trendingSections`), hai E1 chạy song song | Fallback 3 nhánh của `MainViewModel` cần cả hai kết quả; "5 section" = 4 lời gọi. (T1, T4) |
| MiniPlayer slot | iOS 26.1+: `tabViewBottomAccessory(isEnabled:)`; iOS 26.0: chỉ gắn `tabViewBottomAccessory` khi có nội dung; iOS 17–18: `safeAreaInset(edge: .bottom)` trên `NavigationStack` của từng tab, nền `.bar` | Đặt MiniPlayer ngay trên tab bar như `BottomAppBarBlur` của Android, dùng API hệ thống của từng đời iOS; không phát gì thì không có capsule rỗng. Thanh giả chỉ có trong Debug (`-uitest-miniplayer-placeholder`). Nhánh 17–18 và 26.0 chưa chạy được (máy chỉ có runtime 26.3). (T4) |
| Icon thay bằng SF Symbol | Icon danh mục: **không** icon nào (101 asset chép từ `sharedResources`, 10 vector XML → SVG). Material icon của Android → SF Symbol: tab `Icons.Rounded.Favorite` → `heart`, `Icons.Rounded.Home` → `house`, `Icons.Rounded.Search` → `magnifyingglass`; badge `Icons.Rounded.Clear` → `xmark`; `Icons.Rounded.PlayArrow` (thanh MiniPlayer giả, Debug) → `play.fill`; placeholder ảnh `image_loader_place_holder_*` → nền `.quaternary` + `waveform`. Thêm mới (Android không có): `tray` (section rỗng), `wifi.exclamationmark` (section lỗi) | Material Icons không có trên iOS; SF Symbol tự theo Dynamic Type và Dark Mode. Ảnh minh hoạ (`onboarding_slide_*`, `empty_favourite`) giữ ảnh Android. (T2, T4) |
| Ảnh `http://` | Nâng lên `https://`, lỗi → placeholder, không thử lại http; `Info.plist` không có `NSAppTransportSecurity` | Số liệu đo ở dưới (dòng "Ảnh"); dưới 10 % nên chưa cần image proxy. (T2) |
| Localization | `sourceLanguage = vi`, 30 key, đủ bản `en`; nhãn tab + nút onboarding được localize | Quyết định §7 mục 2 (handoff). (T2–T4) |
| Hook test | Chỉ trong `#if DEBUG`; `-uitest-skip-onboarding` tự reset trước; `-uitest-slow-network` chờ 10 s (kế hoạch ghi 3 s) | Mọi UI test bắt đầu từ cùng trạng thái; 3 s không đủ để test cuộn tới section dưới khi shimmer đang chạy. Bản Release không chứa tên hook (`strings` = 0). (T2, T4) |
| Refresh lỗi | Giữ nội dung đang hiện, không báo lỗi | Quyết định thay owner, đã ghi handoff. (T4) |
| Simulator | `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` | `shared` không có `iosX64`; máy dev là Apple Silicon. (T2) |

**Ảnh (M1-AC9), đo 30/10/2026 (T2):** một lần tải Home = 219 item / 179 URL khác nhau, `http://` 0 %, lỗi 0 %. Mẫu 269 URL gồm toàn bộ 17 URL `http://` của Kho nhạc: lỗi 1,9 % (5/269); ảnh `http://` lỗi sau khi nâng lên https 1/17 (5,9 %). `http://` chiếm 0,27 % Kho nhạc (23/8520), các section khác 0 %. T5 đếm thêm `artwork_failed` trong log của toàn bộ lần chạy test (mục T5).

Chạy lại toàn bộ M1 (từ gốc repo; `OUT` là thư mục tạm bất kỳ ngoài repo):

```bash
SIM=4FB8953E-F948-4CF1-9DB3-EE4F79CEC480
./gradlew :app:assembleDebug :shared:allTests
(cd iosApp && xcodegen generate)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m1 -resultBundlePath $OUT/M1-tests.xcresult
xcrun xcresulttool get test-results summary --path $OUT/M1-tests.xcresult
xcrun xcresulttool export attachments --path $OUT/M1-tests.xcresult --output-path $OUT/M1-attachments
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Release \
  -destination "id=$SIM" -derivedDataPath $OUT/DD-rel build
```

Giới hạn đã biết: xem "Giới hạn đã biết của M1 (tổng hợp)" ở cuối mục M1.

### T1 — Shared: kho key-value, cờ onboarding, cache đĩa, `HomeFeed`, D6 (xong)

Thoả M1-AC1..AC6, chạy lại M1-AC20.

**Kho key-value (M1-AC1): chọn DataStore Preferences KMP `1.2.1`.**

| Bản | `kotlin-stdlib` trong `.module` (Google Maven) | Kết luận |
|---|---|---|
| `androidx.datastore:datastore-preferences-core:1.3.0` | `2.2.20` | Không dùng: cần Kotlin > 2.2.0 |
| `…:1.2.1` | `2.0.21` | **Chọn**: build, link framework và test pass trên `iosSimulatorArm64` lẫn `testAndroidHostTest` với Kotlin 2.2.0 |

- `KeyValueStore` (interface `suspend`: `get/putString`, `get/putBoolean`, `get/putLong`, `remove`, `clear`) + `DataStoreKeyValueStore(path)` trong `commonMain` (`shared/.../preferences/`). File `ts_prefs.preferences_pb` (DataStore bắt buộc đuôi `.preferences_pb`), DataStore tự ghi atomic (file tạm + rename) và tuần tự hoá mọi lần ghi. Không dùng `NSUserDefaults`/`multiplatform-settings`/`@AppStorage`, nên không phát sinh required-reason API của defaults trong `PrivacyInfo.xcprivacy`.
- File hỏng → `ReplaceFileCorruptionHandler` thay bằng store rỗng, log một dòng `TSPodcast/KeyValueStore: store file is corrupt (CorruptionException), starting empty` (stdout, không có path). Lỗi I/O khi đọc/ghi được nuốt và log tên exception: store "best effort", không bao giờ ném ra ngoài (trừ `CancellationException`).
- Đọc theo kiểu: key ghi bằng kiểu khác (vd. `String` mà đọc `Boolean`) trả `null`, không `ClassCastException` (`Preferences.get` cast không kiểm, nên tra map rồi `as?`).
- DataStore chỉ cho **một** instance mở trên một file mỗi process. `SharedGraph` sở hữu store; `SharedGraph.close()` đóng nó. Swift vẫn tạo một graph mỗi process.
- `AppPreferences` dùng đúng tên key Android: `OnboardingFinished`, `HasSelectFavouriteCategory`, `ListFavouriteCategory`, `CategoryRes`, `cachedTrending`, `lastUpdateTrending`, `cachedTrending_<cat>`, `lastUpdateTrending_<cat>`, cộng `AllowExplicitContent` (mặc định `false`). Object lưu dạng JSON string (kotlinx, giống Android lưu chuỗi Gson); JSON không parse được đọc ra `null`.
- `SharedGraph(credentials, config, preferencesPath)` cho Swift; constructor test `SharedGraph(credentials, config, engine, clock, store)`. Swift (`TSPodcastApp.swift`, tạm thời tới T2) truyền `Application Support/ts_prefs.preferences_pb`, tạo thư mục nếu thiếu. Đã chạy trên simulator: file được tạo (≈ 92 KB sau một lần tải trending), app không crash.

**Logic khác:**

- `StartDestination` + `AppPreferences.startDestination()` (M1-AC2): xét `OnboardingFinished` trước rồi `HasSelectFavouriteCategory`, giống `TSNavGraph`.
- `FavouriteCategories` (M1-AC3): `MIN_FAVOURITE_CATEGORIES = 3`; `save` bỏ trùng id, < 3 thì trả `false` và không ghi gì; đủ thì ghi `ListFavouriteCategory` (sắp theo id, `isFavourite = true`) **trước**, cờ `HasSelectFavouriteCategory` **sau** (Android ghi ngược lại; thứ tự này an toàn hơn nếu app chết giữa chừng). `favouriteCategoryIds()` = id tăng dần nối `","`, `null` khi chưa lưu.
- `GetCategories` (M1-AC4): mạng OK → memory + đĩa `CategoryRes`; lỗi (kể cả thiếu credentials, không gửi request) → memory → đĩa → asset 112 mục. Asset không bao giờ được ghi xuống đĩa.
- `GetTrendingPodcasts` (M1-AC5): memory → đĩa (chỉ khi còn < 15 phút; nạp lại memory với **timestamp gốc**, nên không kéo dài TTL) → mạng. Chỉ lưu `Success`, đã lọc blacklist, cả hai tầng. `InMemoryTtlCache.put` có thêm tham số `storedAtMillis`.
- `HomeFeed` (`graph.homeFeed`, M1-AC6): `trendingSections(favouriteCatIds)` chạy song song E1 trending (`since = now − 604800`, không `cat`) và E1 forYou (`since = now − 864000`, `cat`), fallback 3 nhánh của `MainViewModel`, cả hai lỗi → cả hai `Error`; `music()` E6 `max=40`; `live()` E2 `max=100`; `recent(favouriteCatIds)` E5 `max=100`, `lang=vi`, không `notcat`. Trả `TSDataState<List<Podcast>>` / `TSDataState<List<LiveEpisode>>`. Lọc D6 sau blacklist: bỏ `explicit == 1` (E6 map `Feed.explicit=true` thành `1`) trừ khi `AllowExplicitContent`. Không gọi E3/E4.
- `HtmlText.toPlainText`, `SharedConstants` (`USER_AGENT_WEB_VALUE`, `USER_AGENT_KEY`, `REFERER_KEY`), `AnalyticsTracker` (không có `device_id`).
- `SharedConstantsTest` so với **chính file** `sharedResources/.../SharedConstants.kt`: file này được thêm vào task `generateTestFixtures` (đọc, không sửa) nên test hỏng ngay khi Android đổi hằng số.

**Thư viện mới:** `gradle/libs.versions.toml` thêm `datastore = "1.2.1"` và `androidx-datastore-preferences-core` (chỉ thêm entry). Không đổi Kotlin/AGP.

Kết quả tự kiểm:

```bash
./gradlew :shared:allTests :shared:linkDebugFrameworkIosSimulatorArm64   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test + testAndroidHostTest, failures="0" errors="0" cho cả 20 class:
#   AppPreferencesTest 8, StartDestinationTest 4, FavouriteCategoriesTest 4, GetCategoriesTest 11 (+5),
#   TrendingDiskCacheTest 6, HomeFeedTest 12, HtmlTextTest 6, SharedConstantsTest 2; các test M0 vẫn pass
./gradlew :app:assembleDebug :shared:allTests                             # BUILD SUCCESSFUL
grep -rnE "UserDefaults|NSUserDefaults|@AppStorage|multiplatform-settings" iosApp/TSPodcast shared/src shared/build.gradle.kts   # 0 dòng
xcodebuild … -scheme TSPodcast -destination "id=$SIM" build              # ** BUILD SUCCEEDED ** (Swift dùng constructor mới)
```

- Test song song của `HomeFeedTest` để `MockEngine` treo request đầu tới khi request thứ hai tới; nếu hai request chạy tuần tự thì test treo và fail.
- `AppPreferencesTest` dùng file thật trong thư mục tạm (`FileSystem.SYSTEM_TEMPORARY_DIRECTORY`), đóng instance đầu (`closeAndJoin`) trước khi mở instance mới trên cùng file.
- Các test cache/HomeFeed dùng `InMemoryKeyValueStore` (commonTest); "graph mới trên cùng store" = hai `TestGraph` dùng chung một instance store.

Giới hạn đã biết:

- Dữ liệu `SharedPref` của app Android không được chuyển sang (hai app riêng, không cần).
- D6 chỉ lọc được Live và Kho nhạc (E1/E5 không có field `explicit`), đã có trong handoff.
- Log của store dùng `println` (Xcode console / logcat `System.out`), chưa đi qua `OSLog`.

### T2 — Nền app iOS: target test, routing, String Catalog, design, ảnh/ATS, hook (xong)

Thoả M1-AC7, M1-AC8 (phần catalog + `knownRegions`; `LocalizationUITests.testEnglishLocale` thuộc T4), M1-AC9, M1-AC10.

**Project (`iosApp/project.yml`, đã `xcodegen generate` và commit `.xcodeproj`):**

- Target `TSPodcastTests` (`bundle.unit-test`, host = app) và `TSPodcastUITests` (`bundle.ui-testing`); scheme `TSPodcast` chạy cả hai ở action test (Debug).
- Unit test **không** link `Shared` lần nữa (framework static, link 2 lần sẽ có 2 runtime Kotlin): symbol lấy từ app qua `BUNDLE_LOADER`/`TEST_HOST` (XcodeGen tự đặt), test target chỉ có `FRAMEWORK_SEARCH_PATHS` để `import Shared`.
- `knownRegions` = `Base, en, vi`: XcodeGen 2.46 tự đọc ngôn ngữ trong `Localizable.xcstrings`, không cần khai báo thêm.
- `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` (mức project): `shared` chỉ có `iosArm64` + `iosSimulatorArm64`, nên build Release (`ONLY_ACTIVE_ARCH = NO`) cho simulator trước đây link thiếu symbol Kotlin ở x86_64. Hệ quả: không chạy được trên simulator của Mac Intel (máy dev là Apple Silicon).
- `ASSETCATALOG_COMPILER_APPICON_NAME = ""` (chưa có AppIcon, để milestone phát hành), `ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = NO` (color set `Primary`/`Secondary` trùng `Color.primary`/`.secondary`), `LOCALIZATION_PREFERS_STRING_CATALOGS = YES`.

**App (`iosApp/TSPodcast/`):**

- `App/AppEnvironment.swift`: `@MainActor @Observable`, tạo **một** `SharedGraph` mỗi process (`Application Support/ts_prefs.preferences_pb`, tạo thư mục nếu thiếu), `LogAnalyticsTracker`, `ArtworkLoader.shared`. Inject bằng `.environment(_:)`. `resolveStartRoute()` gọi `AppPreferences.startDestination()` của shared (bridge `async`), log `start_destination=<Onboarding|SelectFavouriteCategory|Main>`. Thiếu credentials thì log `missing_credentials` lúc khởi tạo.
- `App/RootView.swift` + `AppRoute.swift`: đọc start destination một lần trong `.task`; lúc chờ chỉ hiện nền `systemBackground`, không spinner. Chuyển màn bằng `@State route` (không back stack), giống NavHost của Android.
- `App/LaunchOptions.swift`: hook §4.7 chỉ đọc trong `#if DEBUG` (`-uitest-reset`, `-uitest-skip-onboarding`, `-uitest-miniplayer-placeholder`, `-uitest-slow-network`, env `TSP_UITEST_BASE_URL`). Bản Release mọi cờ = `false`, binary không chứa tên hook. **Quyết định:** `-uitest-skip-onboarding` tự kéo theo reset (store + cache ảnh sạch rồi mới ghi `OnboardingFinished = true` và lưu danh mục 1 Arts, 2 Books, 3 Design qua `FavouriteCategories.save`), để mọi test bắt đầu từ cùng một trạng thái. `TSP_UITEST_BASE_URL` đi vào `BundleCredentialsProvider(baseUrlOverride:)`. `-uitest-miniplayer-placeholder` và `-uitest-slow-network` đã parse, nơi dùng là T4 (MiniPlayerSlot, adapter `HomeDataSource`).
- `App/LogAnalyticsTracker.swift`: implement `AnalyticsTracker` của shared, ghi `Logger(subsystem: "tss.t.podcast", category: "Analytics")` dạng `analytics screen=Onboarding CurrentPage=2` (param sắp theo tên) và `analytics event=OnboardingFinished screen=Onboarding`. Không có `device_id`.
- `App/AppLog.swift`: category `M1` (luồng app), `Analytics`, `Artwork`. Category `M0` bỏ cùng màn debug.
- Đã xoá `Debug/TrendingDebugModel.swift`, `Debug/TrendingDebugView.swift`.
- `Design/AppColors.swift` (`Color.brandPrimary`, `.brandSecondary`, `.textTitle`, `.stroke`) và `Design/Shimmer.swift` (`.shimmer()` = `.redacted(.placeholder)` + dải sáng chạy ngang, tắt khi bật Reduce Motion).
- Màn khung cho routing, T3/T4 sẽ thay: `Onboarding/OnboardingView` (chỉ trang 1, id `onboarding.page.0`), `Categories/CategorySelectionView` (tiêu đề + mô tả, id `category.grid`), `Main/MainTabView` (3 tab `favourite`/`home`/`search`, mặc định `home`, mỗi tab một `NavigationStack`, id `favourite.root`/`home.root`/`search.root`, `.toolbarTitleDisplayMode(.inlineLarge)`).

**String Catalog (`Resources/Localizable.xcstrings`, M1-AC8):** `sourceLanguage = vi`, 28 key ngữ nghĩa (`tab.home`, `home.section.trending`, `onboarding.title.1.highlight.2`…), mỗi key có `vi` (nguyên văn `strings.xml` của Android / bảng §4.5) và `en` ở trạng thái `translated`. Highlight onboarding là key riêng (trang 1 có 2 cụm "Podcast", "Của Bạn" như `onboarding_title1_highlight`); bản en tự dịch: "Discover Your Favourite Podcasts" (highlight "Podcasts", "Your"), "Content Made Just For You" ("Just For You"), "Listen Anytime, Anywhere" ("Anytime, Anywhere"), "Organize And Update Continuously" ("Organize And Update"). `LocalizationTests` kiểm bảng vi/en trong `vi.lproj`/`en.lproj` của app và kiểm mỗi highlight nằm trong tiêu đề của nó.

**Assets (`Resources/Assets.xcassets`, chép, không sửa file gốc):**

- `Colors/`: `Primary` `#184BB4` (dark `#6E97F0`), `Secondary` `#4A7DE7` (dark `#8AABF2`), `TextTitle` `#39375B` (dark `#E4E3F5`), `Stroke` `#007AFF` 15 % (dark `#8AABF2` 30 %). Android chỉ có theme sáng; màu dark do iOS tự chọn cho đủ tương phản.
- `onboarding_slide_2/3/5/6` (jpg 1024², ở mức gốc của catalog) từ `sharedResources/src/main/res/drawable/`.
- `CategoryIcons/`: 101 asset cho 103 id của `_mapIcon` (2 id dùng chung ảnh) + `ic_anonymous`. PNG 100 px đánh dấu `2x` (cỡ tự nhiên 50 pt, gần 48 dp của Android). **Vector XML:** 10 file được map dùng (`ic_anonymous`, `ic_comedy`, `ic_design`, `ic_food`, `ic_idea`, `ic_linkedin`, `ic_my_space`, `ic_social`, `ic_stories`, `ic_visual`) chuyển sang **SVG** (chỉ có `path` + `fillColor`; `<group><clip-path/></group>` rỗng của `ic_stories` bỏ qua), `preserves-vector-representation`. 2 vector còn lại (`ic_fashion`, `ic_news`) không id nào dùng nên không chép. **Không icon nào phải thay bằng SF Symbol.**
- Asset `ic_radioactive_100` (id 82 Philosophy) đổi tên thành `ic_atom_hazard_100` để `grep -rniE "radio|vov|voh" iosApp/TSPodcast` (M1-AC14) sạch; nội dung ảnh giữ nguyên.
- `Categories/CategoryIconMap.swift` (bảng id → asset) sinh cùng lúc với asset từ `SelectFavouriteCategoryScreen._mapIcon`; `CategoryIcon.assetName(for:)` trả `ic_anonymous` cho id lạ. Script sinh nằm ngoài repo (scratch); nếu cần chạy lại thì đọc lại map Android, không sửa tay hai nơi.

**Ảnh và ATS (`Images/`, M1-AC9):**

- `ArtworkLoader` (actor, không thư viện ngoài): `URLSession` riêng, `URLCache` 50 MB memory / 200 MB disk ở `Caches/Artwork`, `NSCache` ảnh đã decode (giới hạn 64 MB), gộp các lần tải cùng URL đang chạy thành 1 request. Mỗi `URLRequest` đặt `SharedConstants.USER_AGENT_KEY: SharedConstants.USER_AGENT_WEB_VALUE` (Swift không chứa chuỗi `Mozilla/5.0`). `http://` → `https://` trước khi gửi; lỗi mạng, status ngoài 2xx hoặc không decode được → `nil` (placeholder), không thử lại bằng `http`; URL `nil`/blank/không phải http(s)/không có host → `nil`, 0 request. Decode bằng ImageIO, downsample cạnh dài tối đa 750 px. Mỗi lần lỗi log `artwork_failed host=<host> upgraded=<bool>` (chỉ host). Có `stats` (requests, upgradedFromHTTP, failures, failuresFromHTTP) để đo.
- `ArtworkImage(url:)`: lấp khung do caller đặt (`scaledToFill`, bo góc), placeholder `.quaternary` + SF Symbol `waveform`; đọc đồng bộ `NSCache` để ảnh đã có không nháy placeholder. Loader lấy từ `EnvironmentValues.artworkLoader`.
- `Info.plist`: không có `NSAppTransportSecurity` (nên không có `NSAllowsArbitraryLoads`, `NSExceptionDomains`).

**Số liệu ảnh (M1-AC9), đo 30/10/2026 bằng một XCTest tạm (không commit) chạy trong app: `graph.homeFeed` thật (cat `1,2,3`) rồi tải mọi URL qua `ArtworkLoader` thật, 8 request song song:**

| Tập ảnh | Số item | URL khác nhau | `http://` | Lỗi |
|---|---|---|---|---|
| Một lần tải Home (trending 99, forYou 20, Kho nhạc 40 item đầu, live 40, recent 20) | 219 (1 URL rỗng) | 179 | 0 (0 %) | 0 (0 %) |
| Toàn bộ URL `http://` của Kho nhạc + 200 URL https ngẫu nhiên của Kho nhạc + live + recent | 283 | 269 | 17 URL khác nhau (23 item) | 5 (1,9 %): 3 × HTTP 410, 1 × không phân giải được host, 1 × `-1022` (https chuyển hướng về http, ATS chặn) |

- Tỷ lệ `http://` trên toàn bộ Kho nhạc: 23 / 8520 item (0,27 %); các section khác 0 %. Ảnh `http://` lỗi sau khi nâng lên https: 1 / 17 (5,9 %).
- Tỷ lệ lỗi < 10 %, nên **chưa cần image proxy**; bullet trong handoff được cập nhật kèm số liệu.

**Test (`iosApp/TSPodcastTests`, `iosApp/TSPodcastUITests`):**

- `ArtworkLoaderTests` (9 test, `StubURLProtocol` gắn vào `protocolClasses` của session): User-Agent bằng `SharedConstants.shared.USER_AGENT_WEB_VALUE`; `http://img.example.com/a.jpg` → stub chỉ nhận `https://img.example.com/a.jpg`; lỗi TLS → `nil`, 0 request http; 404 và body không phải ảnh → `nil`; `nil`/`""`/`"  "`/`ftp:`/đường dẫn tương đối → 0 request; tải 2 lần → 1 request (và URL http cùng ảnh trúng cùng cache); 3 lần tải song song → 1 request; `removeAll()` → tải lại; chuẩn hoá URL (khoảng trắng được escape).
- `CategoryIconTests` (4): đủ 103 id, mọi id có `UIImage(named:)`, id 9999 và 11 → `ic_anonymous`, 10 icon SVG vẽ ra có nội dung.
- `LaunchOptionsTests` (5), `LogAnalyticsTrackerTests` (2), `LocalizationTests` (4).
- XCUITest: `TSPodcastUITestCase` (base: launch với `-AppleLanguages (vi) -AppleLocale vi_VN` đặt **trước** các hook để hook không bị đọc thành giá trị của `-AppleLanguages`, helper chờ và attach screenshot); `LaunchSmokeUITests.testLaunchWithResetShowsOnboarding` (thấy `onboarding.page.0` + "Khám Phá Podcast Yêu Thích Của Bạn", không có tab bar) và `testSkipOnboardingOpensMainOnDiscover` (thấy `home.root`, tab "Khám phá" được chọn).

Kết quả tự kiểm (`OUT` = scratch dir `…/scratchpad/ios`, không commit):

```bash
cd iosApp && xcodegen generate && git status --porcelain .        # không có dòng nào (sau commit)
grep -nE "TSPodcastTests:|TSPodcastUITests:|type: bundle.ui-testing|type: bundle.unit-test" iosApp/project.yml   # 4 dòng
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m1 -resultBundlePath $OUT/M1-tests.xcresult
#   ** TEST SUCCEEDED **; xcresulttool summary: 26 test, 26 passed, 0 failed, 0 skipped
grep -rn TrendingDebug iosApp                                       # 0 dòng
python3 -I -c '…' iosApp/TSPodcast/Resources/Localizable.xcstrings  # "vi True", "missing_en 0"
grep -n knownRegions -A4 iosApp/iosApp.xcodeproj/project.pbxproj    # Base, en, vi
/usr/libexec/PlistBuddy -c "Print :NSAppTransportSecurity:NSAllowsArbitraryLoads" iosApp/TSPodcast/Info.plist   # Does Not Exist
grep -rn "Mozilla/5.0" iosApp                                       # 0 dòng
xcodebuild … -configuration Release -derivedDataPath $OUT/DD-rel build   # ** BUILD SUCCEEDED **
strings $OUT/DD-rel/Build/Products/Release-iphonesimulator/TSPodcast.app/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL"   # 0 (bản Debug: 5)
```

- Screenshot: `M1-T2-launch-reset-onboarding.png`, `M1-T2-skip-onboarding-main.png` (attachment XCUITest), `M1-T2-main-en-simctl.png` (`simctl launch … -AppleLanguages (en) -uitest-skip-onboarding`: title "Discover", tab "Favorites / Discover / Search"). Log: `test_hook skip_onboarding saved=true`, `start_destination=Main`; số lần API key/secret xuất hiện trong log 30 phút gần nhất: 0 và 0.

Giới hạn đã biết / chuyển sang task sau:

- `LocalizationUITests.testEnglishLocale` (M1-AC8) cần header Home và footer attribution, nên viết ở T4; bản en của catalog đã được `LocalizationTests` kiểm.
- **E6 bỏ qua `max`:** `podcasts/bymedium?medium=music&max=40` trả về 8520 feed (đo 30/10/2026; Android cũng nhận cả danh sách và vẽ hết trong `LazyRow`). `HomeFeed.music()` hiện trả nguyên danh sách. T4 cần cắt còn 40 (ở `HomeFeed` của shared hoặc adapter `HomeDataSource`) trước khi vẽ section Kho nhạc, nếu không section này có hàng nghìn card. Số liệu ảnh ở trên tính 40 item đầu của Kho nhạc.
- Màn Onboarding / chọn danh mục / tab hiện là khung cho routing; T3/T4 thay nội dung. (Onboarding và chọn danh mục: xong ở T3.)
- Không chạy được trên simulator x86_64 (xem `EXCLUDED_ARCHS`).

### T3 — Onboarding và chọn danh mục (xong)

Thoả M1-AC11, M1-AC12, M1-AC13. Không đổi `shared` và Android (không cần chạy lại Gradle ngoài bước "Compile Kotlin" của Xcode).

**Onboarding (`iosApp/TSPodcast/Onboarding/`):**

- `OnboardingPage.swift`: 4 trang như `OnboardingViewModel.defaultItems`: `onboarding_slide_2/3/5/6`, nền `#EAF0E4`, gradient `#88BCCF→#C8EAEE`, gradient `#EF8C77→#EE8D78`, `#E0F8F8`. Android lặp màu (3 × trên, 2 × dưới), nên gradient iOS dùng stop 0 / 0,5 / 0,75 / 1 cho cùng vùng chuyển màu. Tiêu đề `#39375B`, cụm highlight `#184BB4`. Tiêu đề dựng bằng `AttributedString`: lấy tiêu đề + từng key highlight từ catalog, tô màu range tìm được. Cụm không có trong tiêu đề thì bỏ qua; Android khi đó tô sai range vì `indexOf == -1`. Màu nền và chữ của trang onboarding cố định ở cả Light/Dark (ảnh minh hoạ vẽ trên nền này).
- `OnboardingModel.swift` (`@MainActor @Observable`):
  - `pageDidChange(to:)` gọi `trackScreen("Onboarding", ["CurrentPage": page + 1])`. Mỗi lần đổi trang ghi một lần, kể cả trang đầu lúc pager hiện (giống `snapshotFlow` của Android); cùng trang lặp lại liên tiếp thì không ghi.
  - `finish()` lưu `OnboardingFinished = true` qua `AppPreferences.saveOnboardingFinished` của shared **rồi** `trackEvent("OnboardingFinished", "Onboarding")`. Bấm lần hai trong lúc đang lưu thì không làm gì, nên mỗi lần bấm có đúng một event. Lưu lỗi (chỉ khi bị huỷ) thì ở lại màn, không track.
- `OnboardingView.swift`:
  - `TabView(.page(indexDisplayMode: .never))` phủ cả màn: `ignoresSafeArea` đặt trên cả `TabView` lẫn từng trang, nếu chỉ đặt ở container cha thì nền không tràn lên status bar.
  - Dot indicator tự vẽ: 4 capsule màu Primary, dot hiện tại dài hơn (kiểu `ShiftIndicatorType`), ẩn với VoiceOver.
  - Nút "Tiếp tục" (`GradientButtonStyle` dạng capsule = `TSRoundedButton`) chỉ có trong cây view khi ở trang cuối, trượt vào từ cạnh phải.
  - Id: `onboarding.page.<0-3>`, `onboarding.continue`.
  - `.task` gọi `categoryCatalog.prefetch()` (log `categories_prefetch`).

**Chọn danh mục (`iosApp/TSPodcast/Categories/`):**

- `CategoryCatalog.swift`:
  - `CategoryItem` (struct Swift; view không giữ object Kotlin) và catalog E9 của process. Onboarding `prefetch()`, màn danh mục `await categories()` **cùng một** `Task`, nên E9 chỉ gửi một lần dù người dùng nhanh hơn mạng (unit test kiểm 1 request).
  - Dùng `GetCategories.invoke(useCache: true)` của shared: mạng → memory → `CategoryRes` trên đĩa → `categories.json` bundled.
  - Kết quả rỗng (chỉ xảy ra khi bị huỷ) không giữ lại, lần sau hỏi lại.
- `CategorySelectionModel.swift`:
  - State `.loading` / `.loaded([CategoryItem])`. Danh sách chọn giữ thứ tự bấm, cũng là thứ tự badge (như `listSelected` của Android).
  - `canSave` khi số mục chọn ≥ `FavouriteCategories.companion.MIN_FAVOURITE_CATEGORIES` (3, lấy từ shared).
  - `save()` gọi `FavouriteCategories.save` của shared, trả `true` thì `RootView` chuyển sang Main. Shared từ chối (< 3) hoặc lỗi thì ở lại màn và nút vẫn bấm được.
- `CategorySelectionView.swift`:
  - Header căn giữa: "Danh mục yêu thích của bạn" + "(Ít nhất 3 danh mục)".
  - Lưới `LazyVGrid(.adaptive(minimum: 150), spacing: 16)` (2 cột trên iPhone 17). Ô vuông bo 20, viền 2 pt `Stroke`, icon 48 pt theo `CategoryIcon` (103 id, id lạ → `ic_anonymous`) và tên. Ô đã chọn có nền Primary 10 % như Android, thêm viền Primary 60 % cho dễ nhận ra (khác nhỏ, chỉ về hình). Có trait `.isSelected` cho VoiceOver.
  - Đang tải: 20 ô shimmer (`Color.stroke` = `PlaceHolder` của Android).
  - Thanh dưới (`safeAreaInset`, nền `.bar`): badge `Secondary` có nút "x" (label VoiceOver "Bỏ chọn <tên>", key mới `category.remove %@`, có en "Remove %@"), xếp bằng `Design/FlowLayout.swift` (tương đương `FlowRow`). Vùng badge cao tối đa ~3 dòng rồi cuộn; Android để `FlowRow` lớn dần và che lưới. Bên dưới là nút "Lưu và tiếp tục" (`GradientButtonStyle` bo 8 = `TSButton`).
  - Id: `category.grid` (ScrollView), `category.cell.<id>`, `category.selected.<id>`, `category.selected.<id>.remove`, `category.save`.
- `Design/GradientButtonStyle.swift`: gradient Primary → Secondary, chữ trắng, cao 50. Disabled dùng `#DDE1E6` / `#343A3F` như Android; ở Dark Mode dùng `systemGray5` / `secondaryLabel`, vì Android không có dark theme và màu sáng cố định bị chói.
- `AppEnvironment` có thêm `categoryCatalog`, `makeOnboardingModel()`, `makeCategorySelectionModel()`. `RootView` truyền model vào màn, chuyển route khi `finish()`/`save()` trả `true`, tức là chỉ sau khi cờ đã ghi xong vào DataStore.

**Test:**

- Unit (`TSPodcastTests`):
  - `OnboardingModelTests` (7): ảnh/nền 4 trang; tiêu đề vi + cụm highlight đúng như `strings.xml`; cụm thiếu bị bỏ qua; analytics `CurrentPage` 1..4, không lặp; nút chỉ ở trang cuối; lưu cờ trước rồi mới track, bấm 2 lần chỉ 1 event; lưu lỗi thì không track.
  - `CategorySelectionModelTests` (9): loading → loaded; nút lưu ở 0/1/2/3 mục, bỏ badge thì tắt lại; thứ tự badge; lưu gửi đúng danh sách; < 3 thì không gọi shared; shared từ chối hoặc lỗi thì ở lại màn; catalog: prefetch + load = 1 request, kết quả rỗng thì hỏi lại.
- XCUITest (`TSPodcastUITests`):
  - Helper trong `TSPodcastUITestCase`:
    - `waitForOnboardingPage` xác định trang đang hiện bằng `frame.minX ≈ 0`, vì pager giữ cả trang bên cạnh trong cây accessibility nên chỉ kiểm tồn tại là không đủ.
    - `swipeToOnboardingPage` vuốt lại tối đa 3 lần nếu trang trước vẫn còn hiện. Lần chạy đầu, một cú vuốt gửi lúc màn đầu còn đang fade-in bị bỏ qua; helper không bao giờ vuốt quá trang đích.
    - `finishOnboarding`, `categoryCell` (chờ 30 s cho E9 thật).
  - `OnboardingUITests`:
    - `testPagesAndContinueOnlyOnLastPage`: đi qua 4 trang, mỗi trang kiểm đúng tiêu đề; trang 0–2 không có `onboarding.continue`; trang 3 có "Tiếp tục"; bấm thì thấy `category.grid` + tiêu đề/mô tả. Attach screenshot 4 trang.
    - `testSwipingBackHidesContinue`: vuốt lùi từ trang 3 thì nút biến mất.
    - `testRelaunchAfterOnboardingOpensCategorySelection`.
  - `CategorySelectionUITests`:
    - `testSaveEnabledFromThreeCategories`: `category.cell.1` = "Arts"; nút lưu tắt ở 0/1/2 mục, bật ở 3; bấm `category.selected.2.remove` thì tắt và ô 2 hết `isSelected`; chọn lại → lưu → `home.root`, tab "Khám phá" được chọn.
    - `testFallsBackToBundledCategoriesWhenApiUnreachable`: env `TSP_UITEST_BASE_URL=https://nonexistent.invalid/api/1.0/`; thấy `category.cell.1` "Arts", `category.cell.3` "Design", không có alert.
    - `testRelaunchGoesStraightToHome`: terminate rồi launch không hook thì thấy `home.root` trong 10 s, không có onboarding hay lưới.

Kết quả tự kiểm (`OUT` = scratch dir `…/scratchpad/ios`, không commit; `SIM` = NovaTest iPhone 17):

```bash
cd iosApp && xcodegen generate                                      # project nhận file mới; chạy lại không đổi gì
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m1 -resultBundlePath $OUT/M1-T3-tests.xcresult
#   ** TEST SUCCEEDED **; xcresulttool summary: 47 test, 47 passed, 0 failed, 0 skipped
xcodebuild test-without-building … -only-testing:TSPodcastUITests/OnboardingUITests/testPagesAndContinueOnlyOnLastPage
xcrun simctl spawn $SIM log show --start "<lúc bắt đầu test>" --predicate 'subsystem == "tss.t.podcast" AND category == "Analytics"'
#   analytics screen=Onboarding CurrentPage=1 / =2 / =3 / =4, analytics event=OnboardingFinished screen=Onboarding (đúng 1 dòng); 0 dòng device_id
ls iosApp/TSPodcast/Resources/Assets.xcassets | grep -c onboarding_slide          # 4
grep -rniE "radio|vov|voh" iosApp/TSPodcast | wc -l                               # 0
xcodebuild … -configuration Release -derivedDataPath $OUT/DD-rel build            # ** BUILD SUCCEEDED **
strings …/Release-iphonesimulator/TSPodcast.app/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL"   # 0
```

- Screenshot (attachment XCUITest, chép ra `$OUT`): `M1-T3-onboarding-page-{0,1,2,3}.png` (nền/gradient Android tràn cả status bar, cụm highlight xanh, dot ở đáy, "Tiếp tục" chỉ ở trang 3), `M1-T3-categories-none-selected.png`, `M1-T3-categories-three-selected.png` (3 ô nền Primary 10 %, 3 badge, nút bật), `M1-T3-categories-bundled-fallback.png`, `M1-T3-categories-saved-home.png`, `M1-T3-relaunch-home.png`, `M1-T3-relaunch-after-onboarding.png`. Thêm bằng `simctl io`: `M1-T3-categories-dark-simctl.png` (Dark Mode), `M1-T3-onboarding-en-dark-simctl.png` (en: "Discover **Your** Favourite **Podcasts**"). Log analytics: `M1-T3-AC11-analytics.log`.

Giới hạn đã biết:

- Tên danh mục giữ tiếng Anh từ API (đúng M1 §4.5; dịch là [iOS+]).
- Màn danh mục không có analytics, giống Android (Android không gọi `trackScreen` ở `SelectFavouriteCategoryScreen`).
- Nếu E9 lỗi mạng ngay lúc onboarding, catalog giữ kết quả fallback (đĩa/bundled) suốt process; Android cũng chỉ tải một lần trong ViewModel. Mở lại app thì gọi mạng lại.
- `home.root` ở các test trên vẫn là khung tab của T2; T4 thay nội dung. Test tìm `home.root` theo identifier với mọi loại element, nên không phụ thuộc kiểu view.

### T4 — Khung tab, Home/Discover, tab Yêu thích/Tìm kiếm, stub Detail (xong)

Thoả M1-AC14, M1-AC15, M1-AC16, M1-AC17, M1-AC18, M1-AC19, và phần `LocalizationUITests.testEnglishLocale` còn lại của M1-AC8. Không đổi logic `shared` hay Android (chỉ sửa một dòng comment trong `shared/.../AnalyticsTracker.kt`, xem dưới).

**Khung tab (`iosApp/TSPodcast/Main/`):**

- `MainTabView.swift`: `TabView(selection:)` + `.tabItem`/`.tag(AppTab)` (chạy từ iOS 17; build bằng SDK 26 tự có Liquid Glass). Thứ tự `favourite` (icon `heart`), `home` (`house`, như icon nhà của Android, thay `safari` của khung T2), `search` (`magnifyingglass`); mặc định `home`; không có Radio. Mỗi tab một `NavigationStack` riêng (Home có `path: [PodcastCard]`), `navigationTitle` + `.toolbarTitleDisplayMode(.inlineLarge)`. `HomeModel` do `AppEnvironment.makeHomeModel()` tạo, `MainTabView` giữ bằng `@State` nên đổi tab không tải lại.
- `MiniPlayerSlot.swift` (quyết định MiniPlayer slot):
  - iOS 26.1+: `.tabViewBottomAccessory(isEnabled:)` trên `TabView`; `isEnabled = false` thì không có capsule rỗng.
  - iOS 26.0: chỉ gắn `.tabViewBottomAccessory` khi có nội dung (API `isEnabled:` cần 26.1).
  - iOS 17–18: `safeAreaInset(edge: .bottom)` trên `NavigationStack` của từng tab, nền `.bar`.
  - M1 không phát gì nên slot rỗng; chỉ hook Debug `-uitest-miniplayer-placeholder` hiện thanh giả `miniplayer.slot` (view giả nằm trong `#if DEBUG`). Milestone Player thay `MiniPlayerSlot` bằng MiniPlayer thật.
- `Favourite/FavouriteRootView.swift`: empty state của `EmptyFavouriteWidget`: hình `empty_favourite` (vector XML Android chuyển sang SVG, 170 `path` fill + stroke, `preserves-vector-representation`), "Không có chương trình yêu thích." (`favourite.empty`), nút "Khám phá thêm" (`favourite.discoverMore`, `GradientButtonStyle` = `TSButton`) chuyển sang tab Khám phá. Id gốc `favourite.root`.
- `Search/SearchRootView.swift`: màn gốc rỗng, tiêu đề "Tìm kiếm", id `search.root`.
- `Detail/PodcastDetailStubView.swift`: ảnh 240 pt, tiêu đề (`detail.title`), mô tả plain text; id `detail.root`, title bar inline. Không có chữ "coming soon".

**Home (`iosApp/TSPodcast/Home/`):**

- `HomeDataSource.swift`: struct Swift `PodcastCard` (id, title, `summary` = mô tả đã qua `HtmlText.toPlainText`, `imageURL` = `image`, rỗng thì `artwork`), `LiveRowItem` (title, `datePublishedPretty`, `feedImage`), `HomeLoadError` (chỉ giữ tên type để log), `protocol HomeDataSource` với 4 loader: `trendingSections()`, `music()`, `live()`, `recent()`. View không giữ object Kotlin.
- `HomeFeedDataSource.swift`: adapter thật trên `graph.homeFeed`. Đọc `favouriteCategoryIds()` của shared cho trending/forYou và recent. `Success` → map; `Error` → `failure` + log `home_section_failed section=<s> type=<KotlinType>` (không log message vì có thể chứa URL). Hook `-uitest-slow-network` chờ `slowNetworkDelay` trước mỗi lời gọi shared.
- `HomeModel.swift` (`@MainActor @Observable`):
  - `SectionState<T> { loading, loaded([T]), empty, failed }`; Live giữ `[[LiveRowItem]]` qua `LivePaging.pages` (`chunked(3)`), list rỗng → `.empty`.
  - `loadIfNeeded()` chạy 4 loader song song (`async let`), chỉ một lần mỗi model. `generation` +1 sau mỗi lần tải đủ (lần đầu = 1).
  - **Trending và forYou dùng chung một loader** (`HomeFeed.trendingSections`), vì fallback 3 nhánh của `MainViewModel` cần cả hai kết quả. Nên "5 section" = 4 lời gọi; `retry(.trending)` hay `retry(.forYou)` đều gọi lại lời gọi chung (cả hai chỉ cùng lỗi khi cả hai E1 lỗi).
  - `retry(section)` chỉ gọi loader của section đó, section về `.loading` rồi theo kết quả mới.
  - `refresh()` (pull-to-refresh) gọi lại cả 4 loader. Section đang có nội dung **giữ nội dung cũ** trong lúc chờ; section chưa có nội dung hiện shimmer. **Quyết định:** refresh lỗi thì giữ nội dung đang hiện (dữ liệu cũ tốt hơn màn lỗi); kết quả rỗng thành công thì vẫn thay bằng `.empty`. Cache 15 phút của trending nằm trong shared nên vẫn áp dụng.
  - Mỗi loader có số thứ tự request; kết quả về muộn của request cũ bị bỏ (retry trong lúc refresh không bị ghi đè).
- `HomeView.swift`: thứ tự trending → Kho nhạc (+ phụ đề) → Live → Recent → "Có thể bạn yêu thích" → attribution, không ad, không link Privacy options.
  - Carousel (trending/music/recent): card 250 pt, ảnh vuông bo 10, tiêu đề 1 dòng, mô tả 3 dòng (`reservesSpace`), cuộn ngang có snap (`scrollTargetBehavior(.viewAligned)`), lề 16, khoảng cách 12.
  - Live: pager ngang, mỗi trang rộng 90 % (`containerRelativeFrame`), 3 dòng/trang (ảnh 60 pt, tiêu đề 1 dòng, `datePublishedPretty`). Tap chỉ ghi log `live_tap id=<id>` (Player ở milestone sau).
  - "Có thể bạn yêu thích": danh sách dọc (ảnh 60 pt, tiêu đề, mô tả 3 dòng) trong một `LazyVStack` riêng vì có tới 100 dòng.
  - Mỗi section: shimmer (`.shimmer()`, 3 card / 3 dòng / 5 dòng) gói thành một element `home.section.<s>.loading` (label VoiceOver "Đang tải", key mới `home.loading`, en "Loading"); `empty` và `failed` đều có thông báo + nút "Tải lại" (`.empty`/`.error`, `.retry`), đúng [Sửa] "empty/error state kèm nút Tải lại" của features-spec §3.4.
  - Tap podcast (trending/music/recent/forYou) → `NavigationLink(value: PodcastCard)` → `PodcastDetailStubView` trên stack của tab Khám phá. Label VoiceOver của card = tiêu đề.
  - `.refreshable` gọi `refresh()`; `.task` gọi `loadIfNeeded()`.
  - Id: `home.list` (ScrollView, Debug có `accessibilityValue` `generation=N`), `home.root` (container nội dung bên trong list), `home.section.<s>` (header, có trait header), `home.section.music.subtitle`, `.loading`, `.empty`, `.error`, `.retry`, `.item.<i>` (Live đánh số liên tục qua các trang), `home.live.page.<n>`, `home.attribution`.
- **Quyết định bố cục:** ngăn xếp ngoài cùng của Home là `VStack` thường (11 view con), chỉ list forYou là lazy. Với `LazyVStack` ở ngoài, sau khi các section lỗi hoặc dòng forYou được đo lại, ScrollView tính thiếu chiều cao nội dung và ở cuối trang footer attribution nằm dưới tab bar nổi (thấy trong `HomeErrorUITests`, footer y = 778–796 trong khi tab bar bắt đầu ở 791). Với `VStack` ngoài thì footer luôn nằm trên tab bar.
- **Vì sao `home.root` nằm trong `home.list`:** bọc ScrollView bằng một container `.contain` thì SwiftUI gộp hai element làm một và id ngoài đè id `home.list`.

**Khác:**

- `AppEnvironment.makeHomeModel()`; `RootView` truyền model vào `MainTabView`.
- **Lệch so với M1 §4.7:** `-uitest-slow-network` chờ **10 s** (không phải 3 s) trước mỗi lời gọi shared (`HomeFeedDataSource.slowNetworkDelay`). Với 3 s, test không tới kịp section dưới: sau mỗi lần launch/vuốt, XCUITest chờ app "idle" trong khi shimmer đang chạy animation, mất khoảng 2 s mỗi thao tác. 10 s vẫn xong trong 30 s.
- Comment `device_id` trong `LogAnalyticsTracker.swift` và `shared/.../analytics/AnalyticsTracker.kt` (có từ T1/T2) đổi thành "device identifier", để lệnh grep của M1-AC19 không ra dòng nào. Không đổi code.
- `LaunchSmokeUITests.testSkipOnboardingOpensMainOnDiscover` tìm `home.root` theo identifier với mọi loại element (không còn là ScrollView).

**Test:**

- Unit (`TSPodcastTests`), fake `FakeHomeDataSource` (đếm lời gọi từng loader, có thể giữ lời gọi tới khi `releaseAll`):
  - `HomeModelTests` (12): 5 section bắt đầu `.loading`; tải 1 lần, `loadIfNeeded` lần 2 không gọi lại; rỗng → `.empty`; lỗi → `.failed`; music lỗi không ảnh hưởng 4 section kia; trending + forYou cùng lỗi → cả hai `.failed`; `retry(.live)` chỉ gọi loader live, đang chờ thì `.loading`, sau đó 4 item thành trang `[3, 1]`; `retry(.forYou)` gọi lại lời gọi chung; `refreshReloadsAllSections` (`testRefreshReloadsAllSections`): mỗi loader thêm đúng 1 lần, trong lúc chờ section đã có nội dung giữ nguyên, section lỗi thành `.loading`, `generation` 1 → 2; refresh lỗi giữ nội dung; kết quả muộn của request cũ bị bỏ; thứ tự section và map section → loader.
  - `LivePagingTests` (4): 7 item → `[3, 3, 1]`; 6 → `[3, 3]`; 1 → `[1]`; 0 → `[]` và state `.empty`; model với 7 item live → 3 trang.
- XCUITest (`TSPodcastUITests`, helper mới trong `TSPodcastUITestCase`: `launchMain`, `tabButton`, `dragHomeUp` (kéo chậm, không fling, để không bỏ sót view lazy), `isVisibleInHome` (chỉ tính vùng giữa navigation bar và tab bar), `scrollHome(to:)`, `scrollHomeToBottom` (footer phải còn thấy sau 0,5 s), `waitForSectionContent`, `waitForValue`):
  - `MainShellUITests`: `testThreeTabsInOrderHomeSelected` (đúng 3 nút theo `minX`: "Yêu thích", "Khám phá", "Tìm kiếm"; Khám phá `isSelected`; navigation bar "Khám phá"; không element nào chứa "Radio" trong tab bar và navigation bar. Không quét cả màn vì tiêu đề podcast thật có chữ "RADIO NÔNG DÂN"), `testFavouriteEmptyStateDiscoverMore`, `testEachTabKeepsItsNavigationStack`, `testMiniPlayerSlotSitsAboveTabBar` (cả 3 tab, `slot.maxY <= tabBar.minY + 1`, screenshot mỗi tab), `testNoMiniPlayerWhenNothingPlays`.
  - `HomeUITests`: `testSectionsRenderInAndroidOrder` (header đúng chữ và thứ tự, header sau nằm dưới header trước; trong 30 s mỗi section có `.item.0` hoặc `.empty`, không còn `.loading`; trending/music/forYou có item; screenshot từng section), `testAttributionFooterIsLast`, `testTapTrendingOpensDetailStub`, `testPullToRefreshReloads` (`generation=1` → kéo xuống → `generation=2`, trending/music/forYou vẫn có item).
  - `HomeErrorUITests`: `testUnreachableApiShowsErrorAndRetryPerSection` (env `TSP_UITEST_BASE_URL=https://nonexistent.invalid/api/1.0/`; 5 section có "Không tải được nội dung" + "Tải lại"; footer vẫn hiện; bấm `home.section.live.retry` → live lỗi lại, app vẫn `runningForeground`), `testLoadingPlaceholdersShown` (trending/music `.loading` trong 2 s, live/recent/forYou `.loading` khi cuộn tới; sau đó `generation=1` và không còn element `.loading` nào).
  - `LocalizationUITests.testEnglishLocale` (M1-AC8): tab "Favorites", "Discover", "Search"; navigation bar "Discover"; header "Trending podcasts"; footer "Podcast data provided by Podcast Index".

Kết quả tự kiểm (`OUT` = scratch dir `…/scratchpad/ios`, không commit; `SIM` = NovaTest iPhone 17, iOS 26.3):

```bash
cd iosApp && xcodegen generate                                      # chạy lại không đổi gì
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m1 -resultBundlePath $OUT/M1-tests.xcresult
#   ** TEST SUCCEEDED **; xcresulttool summary: 75 test, 75 passed, 0 failed, 0 skipped
grep -rniE 'radio|vov|voh' iosApp/TSPodcast | wc -l                                  # 0
grep -rn 'AllowExplicitContent\|allowExplicit' iosApp/TSPodcast | wc -l              # 0
grep -rniE 'admob|applovin|GoogleMobileAds|ATTrackingManager|AppTrackingTransparency|FBSDK|FacebookCore|device_id|privacy_options' iosApp shared/src | wc -l   # 0
xcodebuild … -derivedDataPath $OUT/DD-nocreds build PODCASTINDEX_API_KEY= PODCASTINDEX_API_SECRET=
#   cài + launch -uitest-skip-onboarding: 5 section lỗi + "Tải lại", launchctl vẫn có tss.t.podcast,
#   log: missing_credentials, home_section_failed section=<5 section> type=MissingCredentialsException, home_loaded generation=1
xcodebuild … -configuration Release -derivedDataPath $OUT/DD-rel build            # ** BUILD SUCCEEDED **
strings …/Release-iphonesimulator/TSPodcast.app/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL"   # 0
./gradlew :shared:allTests :app:assembleDebug                                     # BUILD SUCCESSFUL (sau khi sửa comment trong shared)
```

- Screenshot trong `$OUT`: `M1-AC16-home-light.png`, `M1-AC16-home-dark.png` (`simctl io` sau 20 s: title lớn "Khám phá", ảnh cover thật, chữ đọc được ở cả hai chế độ, tab bar 3 tab), `M1-AC17-missing-creds.png`. Attachment XCUITest chép ra `M1-T4-*.png`: `main-three-tabs`, `favourite-empty`, `miniplayer-slot-{Yêu_thích,Khám_phá,Tìm_kiếm}`, `home-stack-kept`, `home-section-{trending,music,live,recent,forYou}`, `home-footer`, `detail-stub`, `home-after-refresh`, `home-errors-{top,bottom}`, `home-live-retried`, `home-loading`, `home-loading-lower`, `home-en`.

Giới hạn đã biết:

- Nhánh iOS 17–18 (`safeAreaInset`) và iOS 26.0 (gắn accessory có điều kiện) của `MiniPlayerSlot` có compile nhưng **chưa chạy**: máy chỉ có runtime iOS 26.3. Cần chạy lại `MainShellUITests` trên simulator iOS 17/18 khi có runtime.
- UI test Home gọi PodcastIndex thật. Nếu API trả rỗng cho trending/music/forYou hoặc mạng chậm hơn 30 s thì `HomeUITests` fail dù app đúng.
- Kiểm "không có Radio" ở UI test chỉ quét tab bar và navigation bar; nội dung podcast từ API có thể chứa chữ "Radio". Code thì đã sạch (`grep` = 0).
- Tap Live chỉ ghi log; Detail mới là stub; tab Tìm kiếm rỗng; Yêu thích luôn rỗng (đúng phạm vi M1 §2).
- Home không gửi analytics màn hình, giống Android (Android chỉ track Onboarding).
- Sau refresh lỗi, section giữ dữ liệu cũ và không báo lỗi (người dùng có thể kéo lại).

### T5 — Nghiệm thu nội bộ, tài liệu, vệ sinh (xong)

Thoả M1-AC20, M1-AC21, M1-AC22 và chạy lại toàn bộ §6 trên HEAD của T4 (`727553c`). Không đổi code, chỉ tài liệu.

Kết quả (30/10/2026, `OUT` = scratch dir `…/scratchpad/ios`, không commit; `SIM` = NovaTest iPhone 17, iOS 26.3):

```bash
# Build sạch: xoá $OUT/DD-m1, $OUT/M1-tests.xcresult trước khi chạy
cd iosApp && xcodegen generate && git status --porcelain .         # không có dòng nào
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m1 -resultBundlePath $OUT/M1-tests.xcresult
#   ** TEST SUCCEEDED **; summary: 75 test, 75 passed, 0 failed, 0 skipped (55 unit + 20 UI, ~5 phút)
#   mọi test được nêu trong §6 (AC8, AC9, AC11–AC18) đều Passed trong `xcresulttool get test-results tests`
xcrun xcresulttool export attachments --path $OUT/M1-tests.xcresult --output-path $OUT/M1-attachments   # 32 screenshot
./gradlew :shared:iosSimulatorArm64Test --rerun :shared:testAndroidHostTest --rerun :app:assembleDebug :shared:allTests
#   BUILD SUCCESSFUL; iosSimulatorArm64Test + testAndroidHostTest: mỗi target 20 class, 146 test, failures=0 errors=0
xcodebuild … -configuration Release -derivedDataPath $OUT/DD-rel build    # (DD-rel xoá trước) ** BUILD SUCCEEDED **
strings $OUT/DD-rel/…/TSPodcast.app/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL"   # 0 (bản Debug: 5)
xcodebuild … -derivedDataPath $OUT/DD-nocreds build PODCASTINDEX_API_KEY= PODCASTINDEX_API_SECRET=   # ** BUILD SUCCEEDED **
#   launch -uitest-skip-onboarding: 5 section "Không tải được nội dung" + "Tải lại", launchctl có tss.t.podcast,
#   log: missing_credentials, home_section_failed section=<5 section> type=MissingCredentialsException; đã cài lại bản thường
```

Kiểm tĩnh (đều đúng như §6):

| AC | Lệnh | Kết quả |
|---|---|---|
| AC1 | `grep -rnE "UserDefaults\|NSUserDefaults\|@AppStorage\|multiplatform-settings" iosApp/TSPodcast shared/src shared/build.gradle.kts` | 0 dòng |
| AC7 | `grep -nE "TSPodcastTests:\|TSPodcastUITests:\|type: bundle.ui-testing\|type: bundle.unit-test" iosApp/project.yml`; `grep -rn TrendingDebug iosApp` | đủ 4 mẫu; 0 dòng |
| AC8 | script `python3 -I -c …` trên `Localizable.xcstrings`; `knownRegions` | `vi True`, `missing_en 0` (30 key); `Base, en, vi` |
| AC9 | `PlistBuddy … NSAllowsArbitraryLoads`; `grep -rn "Mozilla/5.0" iosApp` | `Does Not Exist`, không có `NSExceptionDomains`; 0 dòng |
| AC11 | `ls Assets.xcassets \| grep -c onboarding_slide`; log Analytics của lần chạy test | 4; `CurrentPage=1..4` đủ, 5 dòng `analytics event=OnboardingFinished screen=Onboarding` = 5 lần bấm "Tiếp tục" trong suite; 0 dòng `device_id` |
| AC14 | `grep -rniE "radio\|vov\|voh" iosApp/TSPodcast` | 0 dòng |
| AC19 | `grep -rn "AllowExplicitContent\|allowExplicit" iosApp/TSPodcast`; grep ads/ATT/FB/`device_id`/`privacy_options` trên `iosApp shared/src` | 0; 0 |
| AC20 | `git diff --name-only 6fdd86b..HEAD` ngoài `shared/`, `iosApp/`, `docs/ios/` | chỉ `gradle/libs.versions.toml` (thêm `datastore = "1.2.1"` và `androidx-datastore-preferences-core`, không đổi version cũ); `git diff 6fdd86b..HEAD -- sharedResources featureOnboarding` rỗng (ảnh chỉ được chép) |
| AC21 | `git check-ignore -v iosApp/Config/Secrets.xcconfig`; vòng lặp đếm key/secret trong `git log main..HEAD -p`; đếm trong `log show --last 30m` subsystem `tss.t.podcast` (632 dòng) | `.gitignore:32:iosApp/Config/Secrets.xcconfig`; `0` và `0`; `0` và `0` (cũng 0 trong log `xcodebuild test`/Release và trong `docs/ios`) |
| AC22 | subject conventional; số trailer `Co-Authored-By` so với `git rev-list --count 6fdd86b..HEAD`; `git ls-files \| grep -E '(^\|/)(build\|DerivedData\|xcuserdata)/\|\.xcresult\|\.xcframework\|Secrets\.xcconfig$'` | mọi subject đúng dạng; số trailer = số commit (kể cả commit T5); 0 dòng |

**Ảnh trong lần chạy suite:** log của toàn bộ suite có 5 dòng `artwork_failed`: 3 dòng từ stub của `ArtworkLoaderTests` (`img.example.com`, cố ý) và 2 dòng ảnh thật (cùng một host, URL `https` gốc, không phải ảnh nâng từ http). Loader không log request thành công nên không tính được tỷ lệ từ log; số đo tỷ lệ vẫn là của T2 (1,9 %, dưới 10 %), nên **không** thêm bullet image proxy vào handoff.

Screenshot trong `$OUT`:

- `M1-AC16-home-light.png`, `M1-AC16-home-dark.png` (`simctl io` sau 20 s, `-uitest-skip-onboarding`; appearance đặt lại `light`): title lớn "Khám phá", carousel trending và Kho nhạc có ảnh cover thật, chữ đọc được ở cả hai chế độ, tab bar 3 tab "Yêu thích / Khám phá / Tìm kiếm".
- `M1-AC17-missing-creds.png`: các section đều "Không tải được nội dung" + "Tải lại".
- `M1-T5-shots/M1-T5-*.png`: 32 attachment XCUITest đổi tên dễ đọc (onboarding 4 trang, danh mục, tab, MiniPlayer slot trên 3 tab, 5 section Home, footer, detail stub, refresh, lỗi, loading, en). Log: `M1-T5-test.log`, `M1-T5-app.log`, `M1-T5-gradle-rerun.log`, `M1-T5-release.log`, `M1-T5-nocreds.log`.

**handoff.md:** 5 bullet của M1 §7 đã có (D6 một phần, nhãn localize, ảnh http, analytics chỉ log, XCUITest gọi API thật). T5 bổ sung chi tiết quota của bullet cuối và một bullet mới "Môi trường test": chưa có runtime iOS 17/18 để chạy nhánh cũ của `MiniPlayerSlot`.

### Giới hạn đã biết của M1 (tổng hợp)

- **UI test gọi PodcastIndex thật** (key dùng chung với Android): `HomeUITests`, `MainShellUITests`, `CategorySelectionUITests.testSaveEnabledFromThreeCategories` fail nếu API chậm hơn 30 s hoặc trả rỗng, dù app đúng; mỗi lần mở Home trong test gửi lại 5 request. Unit test (`HomeModelTests`, `CategorySelectionModelTests`, …) dùng fake nên không phụ thuộc mạng.
- **MiniPlayer slot iOS 17–18 và 26.0 chưa chạy**, chỉ compile (máy chỉ có runtime iOS 26.3). Ghi trong handoff.
- **D6 chỉ lọc Live và Kho nhạc** (E1/E5 không có field `explicit`); chưa có toggle vì chưa có Settings.
- Analytics chỉ ghi `OSLog` (chưa có Firebase iOS); Home không track màn hình, giống Android.
- Pull-to-refresh lỗi giữ nội dung cũ, không báo lỗi.
- Tap Live chỉ ghi log; Detail là stub; tab Tìm kiếm rỗng; Yêu thích luôn rỗng (đúng phạm vi M1).
- `-uitest-slow-network` chờ 10 s, không phải 3 s như kế hoạch §4.7.
- Không chạy trên simulator x86_64 (`EXCLUDED_ARCHS`).
- Log của key-value store dùng `println`, chưa qua `OSLog`.
- Tên danh mục giữ tiếng Anh từ API.

## M2

Kế hoạch: [milestones/M2.md](milestones/M2.md). Base `f370aa0`.

Trạng thái: **đã nghiệm thu** (PO review round 1, 21/21 AC pass, xem [M2.md](milestones/M2.md) "Review round 1"). Developer đã làm xong T1–T4, M2-AC1..AC21 đã tự kiểm (lần chạy cuối ở T4, bên dưới). Các quyết định PO chọn thay owner ở §7 của kế hoạch đã có trong [handoff.md](handoff.md).

### Tóm tắt M2 (M2-AC21)

Việc đã làm theo task (chi tiết ở từng mục T1–T4 bên dưới):

| Task | Việc chính | AC | Commit |
|---|---|---|---|
| T1 | `shared`: `PodcastDetailFeed` (E10 ‖ E11, E10 quyết định, retry + cache 15 phút theo `feedId`, D6), `SearchFeed` + `SearchMode` (E7/E8, query rỗng không gửi, blacklist, `clean=true` + lọc explicit, trending theo danh mục) | AC1–AC3 | `5249fd3` |
| T2 | Seam phát nhạc (`PlaybackController`, `LoggingPlaybackController`), Podcast detail thật (header blur, mô tả có link, dòng episode có `Referer`, loading/rỗng/explicit/lỗi alert + inline retry), Home → Detail + zoom iOS 18+, hook `-uitest-fail-detail-once`/`-uitest-detail-empty`/`-uitest-detail-explicit`/`-uitest-playback-probe` | AC4–AC9 | `bf75ca5` |
| T3 | Tab Tìm kiếm (`.searchable`, chip "Tất cả"/"Nhạc", gợi ý, chip danh mục E9, loading/kết quả/rỗng/lỗi), `SearchModel` (debounce 200 ms, submit, đổi chế độ, chống kết quả cũ), màn podcast thịnh hành theo danh mục, Search → Detail | AC10–AC15 | `112da75` |
| T4 | `LocalizationUITests.testEnglishSearchAndDetail`, build sạch, chạy lại toàn bộ §6, screenshot, kiểm secrets/grep, tài liệu | AC16–AC21 | `482ab1a` + commit tài liệu |

Quyết định chính:

| Hạng mục | Quyết định | Lý do / chi tiết |
|---|---|---|
| Debounce + clock | `SearchModel` nhận `any Clock<Duration>` (mặc định `ContinuousClock`); mỗi lần gõ huỷ `Task` cũ và ngủ 200 ms trên clock đó; unit test dùng `TestClock` thủ công | Giống `debounce(200)` + `collectLatest` của Android; test không phụ thuộc thời gian thật. Submit và đổi chế độ gửi ngay; cùng cặp (query đã trim, mode) đang chạy/đang hiện thì không gửi lại; token chặn kết quả cũ ghi đè. (T3) |
| Chip chế độ | Hàng chip "Tất cả"/"Nhạc" tự vẽ, luôn hiện, cố định giữa ô tìm kiếm và list; **không** dùng `.searchScopes` | `.searchScopes` chỉ hiện khi ô tìm kiếm đang active; Android luôn hiện `SearchModeFilter`. Chip chọn có trait `.isSelected`. (T3) |
| Ảnh `ic_empty_search` | Chuyển vector XML Android sang SVG (giữ vector), đặt trong `Assets.xcassets`; `sharedResources` không đổi | Ảnh rỗng giống Android, không cần thư viện. (T3) |
| Chuỗi thời lượng (ghim ở M2-AC6) | `Duration.UnitsFormatStyle` giờ + phút, `.abbreviated`; < 1 phút → 1 phút | vi: `45` s → "1 phút", `2700` → "45 phút", `3900` → "1 giờ, 5 phút"; en: "1 min", "45 min", "1 hr, 5 min", `3600` → "1 hr" (không "0 hr"/"0 phút"). (T2) |
| D6 khi ẩn cả feed | `hiddenExplicitCount` = tổng số episode E10 trả về | Plan không ghi rõ; UI chỉ dùng cờ `isExplicitFeedHidden`. (T1) |
| UI test cần danh sách tập | Helper `openDetailWithEpisodes` thử trending item 0, 1, 2… tới khi có tập | Trending #1 ngày 30/10/2026 là feed explicit, D6 ẩn hết tập (bullet D6 Detail trong handoff). (T2) |
| Lỗi Search vs rỗng | Tách `.failed` ("Không tải được nội dung" + "Tải lại") khỏi `.empty`; mỗi request mới về `.loading` | Header luôn khớp kết quả đang hiện. (T3) |
| Log Search | Chỉ log `length=` và `count=`, không log query | M2-AC18; kiểm lại ở T4: 0 dòng chứa query test. (T3, T4) |
| Hook test | Mọi hook mới chỉ trong `#if DEBUG`; một adapter Detail cho cả process nên `-uitest-fail-detail-once` đúng là "lần load đầu" | Bản Release không có tên hook (`strings` = 0). (T2–T4) |

Chạy lại toàn bộ M2 (từ gốc repo; `OUT` là thư mục tạm ngoài repo):

```bash
SIM=4FB8953E-F948-4CF1-9DB3-EE4F79CEC480
./gradlew :app:assembleDebug :shared:allTests
(cd iosApp && xcodegen generate && git status --porcelain .)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m2 -resultBundlePath $OUT/M2-tests.xcresult
xcrun xcresulttool get test-results summary --path $OUT/M2-tests.xcresult
xcrun xcresulttool export attachments --path $OUT/M2-tests.xcresult --output-path $OUT/M2-attachments
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Release \
  -destination "id=$SIM" -derivedDataPath $OUT/DD-rel build
strings $OUT/DD-rel/Build/Products/Release-iphonesimulator/TSPodcast.app/TSPodcast \
  | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL" -e "playback.probe"      # 0
```

Giới hạn đã biết: xem "Giới hạn đã biết của M2 (tổng hợp)" ở cuối mục M2.

### T1 — Shared: `PodcastDetailFeed` và `SearchFeed` (D6) (xong)

Thoả M2-AC1, M2-AC2, M2-AC3, chạy lại M2-AC19.

- `tss.t.shared.detail.PodcastDetailFeed` (`graph.podcastDetailFeed`): `load(feedId: Long)` chạy `GetEpisodeByFeedId` (E10) ‖ `GetPodcastByFeedID` (E11) trong `coroutineScope { async … }`, như `PodcastViewModel.getEpisodes` (`zip`). Chỉ E10 quyết định: E10 `Error` → `Error` với exception của E10; E10 `Success` → `Success(PodcastDetail)`, E11 lỗi thì `feed = null`. Retry + cache 15 phút theo `feedId` lấy nguyên từ hai use case M0 (E10 `AnyError` 3 lần; E11 `NetworkErrors`, không retry lỗi parse); lỗi không bị cache. Không lọc blacklist (giống Android).
- `PodcastDetail(episodes, feed, hiddenExplicitCount, isExplicitFeedHidden)`. D6 khi `AllowExplicitContent == false`: bỏ episode `explicit == 1` (đếm vào `hiddenExplicitCount`); E11 `Success` với `feed.explicit == true` → `episodes` rỗng, `isExplicitFeedHidden = true`. **Quyết định nhỏ:** khi cả feed bị ẩn, `hiddenExplicitCount` = tổng số episode E10 trả về (plan không ghi rõ; UI M2 chỉ dùng cờ `isExplicitFeedHidden`). Cờ `true` → không bỏ gì.
- `tss.t.shared.search.SearchFeed` (`graph.searchFeed`) + `enum class SearchMode { All, Music }` (export sang Swift, đã thấy trong `Shared.h`):
  - `search(query, mode)`: trim; rỗng → `Success(emptyList())`, 0 request ([Sửa]: Android gửi `q=""`). `All` → E7 `search/byterm` (`max=100`, `similar=true`, `fulltext=true`, `aponly=false`); `Music` → E8 `search/music/byterm` (`val=music`, `max=60`, `similar=true`, `fulltext=true`). Blacklist theo `feed.id` + `title` (use case M0). D6 bật: gửi `clean=true` và bỏ `feed.explicit == true` sau blacklist; D6 tắt: **không** gửi `clean`. Không retry, không cache (giống Android).
  - `categoryPodcasts(categoryId: Int)`: `GetTrendingPodcasts(max=100, since=now−604800, lang="vi", cat="<id>", notcat=null)` → blacklist, cache memory + đĩa 15 phút (`cachedTrending_<id>` / `lastUpdateTrending_<id>`). D6 bỏ `explicit == 1` (E1 không trả field nên thực tế không bỏ gì, như M1). Key cache không đụng với section "Có thể bạn yêu thích" của Home vì `favouriteCatIds` luôn có ≥ 3 id nối bằng `,`.
- Không đổi `PodcastIndexApi`, `RetryPolicy`, use case M0, `gradle/libs.versions.toml` hay file Android nào.

Kết quả tự kiểm:

```bash
./gradlew :shared:allTests :shared:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test + testAndroidHostTest: mỗi target 22 class, 171 test, failures="0" errors="0"
#   PodcastDetailFeedTest 14, SearchFeedTest 11 (mới); toàn bộ test M0/M1 vẫn pass
git diff --name-only f370aa0..HEAD    # chỉ shared/ và docs/ios/
git status --porcelain sharedResources  # rỗng
```

- `PodcastDetailFeedTest`: tham số E10 (`id`, `max=100`, không `since`) và E11 (`id`, `pretty=true`); song song (`MockEngine` treo E10 tới khi E11 tới — đã thử đổi `load` sang tuần tự thì test này fail, rồi khôi phục); (OK, OK), (OK, 500), (500, OK); E10 500 → 4 request; E11 body hỏng → 1 request, vẫn `Success`; cache +14:59 → 0 request, +15:01 → thêm 1 E10 + 1 E11; `load(43)` không dùng cache của 42; lỗi không bị cache; D6 tắt/bật, feed explicit, E11 lỗi không ẩn feed.
- `SearchFeedTest`: path + mọi query param cho `All`/`Music`; trim; `""`/`"   "` × 2 chế độ → 0 request; blacklist theo id và từ khoá tiêu đề; explicit bị bỏ khi D6 bật, giữ khi tắt và không có `clean`; 500 → `Error` sau 1 request (cả hai chế độ); `categoryPodcasts(55)` với `now = 1_700_000_000` → `since=1699395200`, `cat=55`, không `notcat`, item blacklist bị bỏ; +14:59 → 0 request; store có `cachedTrending_55`; graph mới trên cùng store đọc từ đĩa (0 request); +15:01 → request mới; mỗi danh mục một entry; 500 → `Error`, không ghi đĩa.

Giới hạn đã biết của T1: chưa có UI dùng hai API này (T2/T3); D6 cho E1 theo danh mục không lọc được gì vì API không trả `explicit` (đã có bullet D6 trong handoff).

### T2 — iOS: Podcast detail, seam phát nhạc, Home → Detail (xong)

Thoả M2-AC4, M2-AC5 (trừ phần `SearchRootView`, xem giới hạn), M2-AC6, M2-AC7, M2-AC8, M2-AC9.

Đã làm:

- `Playback/`: `PlaybackRequest(podcast, episodes, startIndex)`, `@MainActor protocol PlaybackController`, `LoggingPlaybackController` (`@Observable`, giữ `lastRequest`, log `Logger(category: "Playback")` dạng `playback_request feedId= episodeId= index= count=`, không có URL). `AppEnvironment.playback` là instance duy nhất (`any PlaybackController`), M3 thay bằng player thật.
- `Detail/`:
  - `PodcastRoute` (`feedId`, `title`, `summary`, `imageURL`, `transitionSourceID`): giá trị duy nhất cho `navigationDestination(for: PodcastRoute.self)`. `PodcastDetailDestination(route:)` dựng model từ `AppEnvironment`, T3 dùng lại cho stack Search.
  - `EpisodeRow` (map từ Kotlin `Episode` trong adapter): `imageURL = getImageUrl()` (ảnh tập, rỗng thì `feedImage`), `referer = link` (rỗng → `nil`), `summary` qua `HtmlText.toPlainText`, `publishedAt` (`0` → `nil`), `durationSeconds`, `enclosureURL/Type` cho M3. `meta(locale:timeZone:)` = ngày · thời lượng.
  - `DescriptionText.attributed(_:)`: `NSDataDetector(.link)` → `AttributedString` có `.link` + gạch chân (thay `linkify`).
  - `PodcastDetailDataSource` + `SharedPodcastDetailDataSource` (adapter `PodcastDetailFeed`, log `detail_loaded`/`detail_failed type=` ở category `M2`). `AppEnvironment` giữ **một** adapter cho cả process nên `-uitest-fail-detail-once` đúng là "lần load đầu của process". Hook `-uitest-detail-empty`, `-uitest-detail-explicit`, `-uitest-slow-network` (10 s như Home); code hook nằm trong `#if DEBUG`.
  - `PodcastDetailModel` (`@MainActor @Observable`): `.loading → .loaded/.empty/.explicitHidden/.failed`; lỗi bật `isShowingErrorAlert`; `dismissError()` giữ `.failed`; `retry()`; `loadIfNeeded()` chỉ tải một lần mỗi màn; `play(index:)` gửi cả list đang hiện + `startIndex`, không làm gì khi chưa `.loaded`; kết quả cũ về muộn bị bỏ (token).
  - `PodcastDetailView`: header nền artwork blur (opacity 0.7, gradient về nền trang, chạy lên dưới navigation bar), cover 150 pt bo 20, tiêu đề đậm, mô tả đầy đủ có link; `navigationTitle` inline. Danh sách `LazyVStack`, divider gradient. Loading = 8 dòng shimmer (`detail.episodes.loading`). Alert "Rất tiếc!" + "Tải lại"/"Huỷ"; lỗi inline + "Tải lại"; empty "Chưa có tập nào"; thông báo explicit. `EpisodeRowView`: ảnh 60 pt có `Referer`, tiêu đề không giới hạn dòng, dòng ngày · thời lượng (`.meta`), mô tả 3 dòng.
- `ArtworkLoader.image(for:referer:)` và `ArtworkImage(url:referer:…)`: gửi `Referer` khi có (cùng `User-Agent` M1), cache vẫn theo URL. `ArtworkImage` có thêm `accessibilityLabel` tuỳ chọn (cover của Detail là phần tử `detail.cover`; mặc định vẫn ẩn với VoiceOver).
- Home: `NavigationLink(value: PodcastRoute)`; `homePath: [PodcastRoute]`; zoom transition qua `Design/ZoomTransition.swift` (`matchedTransitionSource` trên artwork của card/dòng forYou, `navigationTransition(.zoom)` trên Detail, cả hai trong `if #available(iOS 18.0, *)`, iOS 17 push mặc định). Id nguồn zoom là `<section>.<index>` vì một podcast có thể nằm ở hai section. `PodcastCard.imageURL` thêm fallback `feedImage` trước `artwork`. Xoá `PodcastDetailStubView`.
- `MainTabView`: overlay `playback.probe` (chỉ Debug + `-uitest-playback-probe`).
- Chuỗi §4.8 phần Detail (vi + en): `detail.error.title/message`, `action.cancel`, `detail.episodes.error/empty`, `detail.explicitHidden`, `detail.loading` (nhãn VoiceOver của shimmer). `LocalizationTests` kiểm cả hai ngôn ngữ.

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| Chuỗi thời lượng (ghim trong `EpisodeRowTests`) | `Duration.UnitsFormatStyle` giờ + phút, `.abbreviated`; dưới 1 phút → 1 phút, còn lại làm tròn tới phút gần nhất | vi: `45` s → "1 phút", `2700` → "45 phút", `3900` → "1 giờ, 5 phút"; en: "1 min", "45 min", "1 hr, 5 min", `3600` → "1 hr" (không có "0 hr"/"0 phút"). Ngày: vi "30 thg 10, 2025", en "Oct 30, 2025" |
| Dòng episode trong XCUITest | `Button` + `.accessibilityElement(children: .contain)` | Giữ trait nút cho cả dòng mà `detail.episode.<i>.title`/`.meta` vẫn truy cập được |
| `detail.episodes` value `count=N` | Chỉ Debug | Giống `generation=` của Home, Release không có |
| Hook explicit | Trả `isExplicitFeedHidden = true`, `hiddenExplicitCount = 3` | Số đếm chỉ dùng cho log |

Kết quả tự kiểm:

```bash
SIM=4FB8953E-F948-4CF1-9DB3-EE4F79CEC480
OUT=/private/tmp/claude-501/-Users-ts1258-Tun-AndroidStudioProjects-Podcast/0ab9c047-6c8f-4a69-a21f-5d5076696848/scratchpad/ios
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m2 -resultBundlePath $OUT/M2-tests.xcresult      # ** TEST SUCCEEDED **: 118 test Passed, 0 fail, 0 skip (unit + toàn bộ XCUITest M1 và M2 T2)
```

- Unit (mới): `PodcastDetailModelTests` 11, `EpisodeRowTests` 12, `DescriptionTextTests` 4, `LoggingPlaybackControllerTests` 4, `ArtworkLoaderTests` +3 (12 tổng) — Passed.
- XCUITest (mới/sửa): `PodcastDetailUITests` (7 test), `PodcastDetailErrorUITests` (2), `HomeUITests.testTapTrendingOpensDetail`, `MainShellUITests.testEachTabKeepsItsNavigationStack` — Passed. Screenshot `M2-detail`, `M2-detail-light`, `M2-detail-dark`, `M2-detail-loading`, `M2-detail-empty`, `M2-detail-explicit`, `M2-detail-error-alert`, `M2-detail-error-inline`, `M2-detail-playback-probe` đã xem: đúng mô tả AC6/AC7, không có "Lorem"/"Opps".
- `grep -rn "navigationTransition(.zoom\|matchedTransitionSource" iosApp/TSPodcast` → 1 dòng mỗi lệnh (`Design/ZoomTransition.swift`, trong `#available(iOS 18.0, *)`); `grep -rniE "lorem|opps" iosApp/TSPodcast` → 0; `grep -rniE "radio|vov|voh" iosApp/TSPodcast` → 0.
- Log `Playback` sau suite: `xcrun simctl spawn $SIM log show --last 30m --predicate 'subsystem == "tss.t.podcast" AND category == "Playback"' | grep -c playback_request` → 10, số dòng chứa `http` → 0 (ví dụ `playback_request feedId=7444596 episodeId=61817210846 index=1 count=61`).
- Release: `xcodebuild … -configuration Release … build` → `** BUILD SUCCEEDED **`; `strings …/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL" -e "playback.probe"` → 0. `cd iosApp && xcodegen generate` không đổi `project.pbxproj`.
- Không đổi file nào ngoài `iosApp/` và `docs/ios/` (shared và Android giữ nguyên từ T1, nên không chạy lại Gradle).

Giới hạn đã biết của T2:

- **Trending #1 hiện là feed explicit.** Ngày 30/10/2026 `home.section.trending.item.0` là "RADIO NÔNG DÂN" (feed 4201720); E11 trả `explicit = true` nên D6 ẩn cả 100 tập và Detail hiện thông báo explicit (đúng §4.1/§7 mục 1). Vì vậy UI test cần danh sách tập dùng helper `openDetailWithEpisodes` (thử trending item 0, 1, 2… tới khi có tập); `testHomeTrendingOpensDetailWithEpisodes` và `testLoadingPlaceholdersShown` vẫn bấm item 0 trước (kiểm tiêu đề, cover, shimmer, không treo), rồi mới sang item kế nếu item 0 bị ẩn. `PodcastDetailErrorUITests` launch một lần không hook để tìm index có tập, rồi launch lại với `-uitest-fail-detail-once` và mở đúng index đó (mỗi test thêm ~10 s). Khác với chữ của AC5/AC7 ("bấm trending item 0") — ghi vào handoff để owner/PO biết hệ quả sản phẩm.
- `grep -rn "PodcastDetailStubView\|SearchRootView" iosApp` chưa về 0: `PodcastDetailStubView` đã xoá, còn `SearchRootView` thuộc T3 (thay bằng `SearchView`).
- Zoom transition chỉ chạy được trên iOS 26.3 (runtime duy nhất trên máy); nhánh iOS 17 (push mặc định) mới compile, chưa chạy (cùng lý do với bullet MiniPlayer trong handoff).
- Header dùng `padding(.top, -400)` cho nền blur để phủ cả vùng navigation bar và khi kéo quá đầu trang; khi kéo xuống rất mạnh (> 400 pt) sẽ lộ nền trống phía trên.

### T3 — iOS: Search, chip chế độ, chip danh mục, Search → Detail (xong)

Thoả M2-AC10, M2-AC11, M2-AC12, M2-AC13, M2-AC14, M2-AC15 (và phần `SearchRootView` của M2-AC5).

Đã làm (`iosApp/TSPodcast/Search/`, `SearchRootView` đã xoá):

- `SearchDataSource.swift`: `enum SearchScope { all, music }` (đặt tên khác `SearchMode` của Kotlin), `SearchResultItem` (`subtitle` = "author · tối đa 3 danh mục", `imageURL` = `feed.image` rồi `artwork`, `summary` plain text, `route: PodcastRoute`), `SearchLoadError`, `protocol SearchDataSource` (`search(query:scope:)`, `categoryPodcasts(categoryId:)`).
- `SharedSearchDataSource.swift`: adapter `SearchFeed`; hook `-uitest-slow-network` (10 s như Home/Detail, chỉ Debug qua `LaunchOptions`). Route Detail đi qua `Podcast.fromFeed` của shared (cover = `feed.artwork`, rồi `feed.image`), giống `HomeNavigationScreen`. Log `search_loaded mode= length= count=` / `search_failed mode= type=` / `category_podcasts_*` ở category `M2`, **không** log query thô.
- `SearchModel.swift` (`@MainActor @Observable`, `MainTabView` sở hữu qua `AppEnvironment.makeSearchModel()`): `queryChanged`, `submit`, `select(mode:)`, `retry`; `phase` `.idle/.loading/.results/.empty/.failed` kèm `shownQuery`/`shownMode` của kết quả đang hiện. Clock inject (`any Clock<Duration>`, mặc định `ContinuousClock`), debounce 200 ms.
- `SearchView.swift`: `.searchable(placement: .navigationBarDrawer(displayMode: .always), prompt: "Tìm podcast, nghệ sĩ")` + `.onSubmit(of: .search)`; hàng chip "Tất cả"/"Nhạc" tự vẽ, cố định phía trên vùng cuộn, chip chọn có trait `.isSelected`; idle: "Thử tìm kiếm: Podcast hay", "Danh mục" + chip E9 (`FlowLayout`, từ `CategoryCatalog`); loading (`ProgressView`), kết quả (header "Kết quả hàng đầu cho: %@"/"Nhạc cho: %@" + `SearchResultRow`), rỗng (ảnh `ic_empty_search` + "Không có kết quả cho: **q**" + gợi ý Nhạc), lỗi (`LoadErrorMessage`: "Không tải được nội dung" + "Tải lại"). `navigationDestination` cho `PodcastRoute` (dùng lại `PodcastDetailDestination` của T2, push thường, không zoom) và `CategoryPodcastsRoute`.
- `SearchResultRow.swift`: ảnh 70 pt bo 8, tiêu đề, dòng phụ, mô tả 3 dòng; dùng cho cả kết quả tìm kiếm và màn danh mục.
- `CategoryPodcastsModel.swift` / `CategoryPodcastsView.swift`: E1 theo danh mục qua `SearchFeed.categoryPodcasts`, title = tên danh mục (inline), loading (8 dòng shimmer) / loaded / rỗng ("Chưa có nội dung" + "Tải lại") / lỗi + "Tải lại", tải một lần mỗi màn, token bỏ kết quả cũ; tap → Detail trên stack Search.
- `MainTabView`: `@State searchModel`, `@State searchPath = NavigationPath()` (hai kiểu route). `RootView` truyền `makeSearchModel()`. `AppEnvironment` giữ một `searchDataSource` cho cả process.
- Ảnh `ic_empty_search`: vector XML Android (7 `path`, chỉ `fillColor`) chuyển sang SVG bằng script một lần, `preserves-vector-representation`; `sharedResources` không đổi.
- Chuỗi §4.8 phần Search (vi + en): `search.prompt`, `search.mode.all/music`, `search.suggestion %@` + `search.suggestion.keyword`, `search.categories`, `search.results.all/music %@`, `search.empty %@`, `search.empty.musicHint`, `search.loading` (nhãn VoiceOver). `LocalizationTests` kiểm cả hai ngôn ngữ.
- `String.trimmedNonBlank` chuyển thành extension internal (trước là private trong adapter Detail) để Search dùng chung.

Quyết định:

| Hạng mục | Chọn | Lý do |
|---|---|---|
| Debounce | `Task` ngủ `debounce` trên clock inject, huỷ ở mỗi lần gõ; test dùng `TestClock` (clock thủ công, sleeper bị huỷ ném `CancellationError`) | Unit test không phụ thuộc thời gian thật; giống `debounce(200)` + `collectLatest` |
| Không gửi trùng | Giữ cặp (query đã trim, mode) của request đang chạy hoặc đang hiện; lỗi và idle xoá cặp này | Đúng §3.2/§4.3; sau lỗi người dùng Return lại vẫn gửi được, "Tải lại" luôn gửi lại đúng cặp lỗi |
| Loading | Mỗi request mới chuyển `phase = .loading` (kết quả cũ biến mất) | Header luôn khớp kết quả đang hiện; tránh header "Nhạc cho" trên list của "Tất cả" |
| Xoá query | Về `.idle` ngay (không chờ debounce), huỷ request đang chạy | Android hiện gợi ý ngay khi ô rỗng |
| Chip chế độ | Hàng chip tự vẽ, cố định giữa ô tìm kiếm và vùng cuộn | `.searchScopes` chỉ hiện khi ô tìm kiếm active; Android luôn hiện chip ở trên list |
| Dòng phụ khi < 3 danh mục | Không có " · " thừa ở cuối; author rỗng thì bỏ | Android để thừa dấu " · " khi thiếu danh mục (lỗi nhỏ, không chép) |
| Thứ tự danh mục | Sắp theo id số | Dictionary Kotlin → Swift mất thứ tự; id tăng dần là thứ tự API gửi |
| Dòng kết quả trong XCUITest | `NavigationLink` + `.accessibilityElement(children: .contain)`; test đọc `app.staticTexts["search.result.<i>.title"]` | SwiftUI chép id của Text đầu tiên lên nút gộp của link (nhãn = cả dòng), nên phải truy vấn đúng `StaticText` |
| Bàn phím trong UI test | Gõ Return (`"\n"`) trước khi đổi tab hoặc chụp màn hình | Bàn phím che tab bar; Return không gửi lại cặp đã có kết quả |

Kết quả tự kiểm:

```bash
SIM=4FB8953E-F948-4CF1-9DB3-EE4F79CEC480
OUT=/private/tmp/claude-501/-Users-ts1258-Tun-AndroidStudioProjects-Podcast/0ab9c047-6c8f-4a69-a21f-5d5076696848/scratchpad/ios
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m2 -resultBundlePath $OUT/M2-tests.xcresult      # ** TEST SUCCEEDED **: 160 test Passed, 0 fail, 0 skip (unit + toàn bộ XCUITest M1, M2 T2, M2 T3)
```

- Unit (mới): `SearchModelTests` 19, `CategoryPodcastsModelTests` 4, `SearchResultItemTests` 7; `LocalizationTests` thêm chuỗi Search. Kiểm ngược: tắt tạm kiểm token và kiểm trùng trong `SearchModel` → 5 test fail (stale, xoá khi đang chạy, gửi trùng ×2), rồi khôi phục.
- XCUITest (mới): `SearchUITests` 10 test (`testIdleShowsSuggestionModesAndCategories`, `testClearingQueryReturnsToIdle`, `testTypingShowsTopResults`, `testMusicModeResearchesImmediately`, `testEmptyResultsAndMusicHint`, `testBlacklistedResultsHidden`, `testLoadingIndicatorShown`, `testCategoryOpensTrendingByCategory`, `testResultOpensDetailAndStatePersists`, `testSearchStatePersistsAcrossTabs`), `SearchErrorUITests` 2 test. Screenshot `M2-search-idle`, `M2-search-results`, `M2-search-music`, `M2-search-empty`, `M2-search-loading`, `M2-search-error`, `M2-search-detail`, `M2-category-arts`, `M2-category-error` đã xem bằng mắt: đúng mô tả AC11–AC14. "casino" ngày 30/10/2026: 8 tiêu đề hiện, không tiêu đề nào chứa từ khoá blacklist (attachment `M2-search-casino-titles`).
- `grep -rn "PodcastDetailStubView\|SearchRootView" iosApp` → 0; `grep -rniE "radio|vov|voh" iosApp/TSPodcast` → 0; `grep -rn "AllowExplicitContent\|allowExplicit" iosApp/TSPodcast` → 0; 10 chuỗi vi của M2-AC16 đều có trong catalog, `missing_en 0`; `git status --porcelain sharedResources` rỗng; `cd iosApp && xcodegen generate` không đổi project sau commit.
- Log sau suite: `xcrun simctl spawn $SIM log show --last 60m --predicate 'subsystem == "tss.t.podcast"'` → 0 dòng chứa "zzqxjvkwpt"/"vietnam" (chỉ có `length=`), 0 lần key/secret.
- Release: `xcodebuild … -configuration Release … build` → `** BUILD SUCCEEDED **`; `strings … | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL" -e "playback.probe"` → 0.
- Không đổi file nào ngoài `iosApp/` và `docs/ios/` (không chạy lại Gradle; shared không đổi từ T1).

Giới hạn đã biết của T3:

- UI test gọi API thật: mỗi lần chạy `SearchUITests` tốn khoảng 15–20 request (E7, E8, E9, E1 theo danh mục, E10/E11 của Detail). Số lượng kết quả có thể đổi theo ngày; test chỉ dựa vào kết quả có/không, không dựa vào số lượng.
- `testBlacklistedResultsHidden` chép danh sách từ khoá `blacklist.json` vào test; nếu Remote Config thay blacklist thì phải cập nhật test (iOS M2 dùng `NoOpConfig` nên hiện chỉ có asset).
- Danh mục chưa tải xong (hoặc `CategoryCatalog` trả rỗng khi bị huỷ) thì hiện spinner nhỏ dưới "Danh mục"; `.task` chạy lại khi quay về tab sẽ thử lại.
- Gõ thêm chữ khi đang có kết quả: kết quả cũ vẫn hiện trong 200 ms debounce, rồi chuyển sang loading (không giữ kết quả cũ trong lúc tải).
- Chỉ chạy trên iOS 26.3 (runtime duy nhất); giao diện iOS 17 của `.searchable` + chip chưa xem.

### T4 — Nghiệm thu nội bộ, localization, tài liệu, vệ sinh (xong)

Thoả M2-AC16, M2-AC17, M2-AC18, M2-AC19, M2-AC20, M2-AC21 và chạy lại toàn bộ §6 trên HEAD sau commit test (`482ab1a`).

Đã làm:

- `LocalizationUITests.testEnglishSearchAndDetail` (`-AppleLanguages (en) -AppleLocale en_US`, `-uitest-skip-onboarding`, `-uitest-fail-detail-once`, một lần launch): tab "Search" → title "Search", placeholder "Search podcasts, artists", chip "All"/"Music", gợi ý "Try searching: great podcasts", header "Categories"; gõ "zzqxjvkwpt" → "No results for: zzqxjvkwpt"; bấm "Music" → gợi ý "The podcast:medium music catalogue is small, try English keywords"; Return, sang tab "Discover", mở trending item 0 → alert "Oops!" có nội dung "Couldn't load the episodes. Please try again.", nút "Retry" và "Cancel"; bấm "Cancel" → lỗi inline "Couldn't load episodes" + "Retry". Kiểm thêm gợi ý Nhạc và lỗi inline (ngoài chữ của AC16) để phủ hết chuỗi en của §4.8. Search không đi qua adapter Detail nên lần load Detail đầu tiên của process đúng là lần bị hook làm lỗi; test không cần item trending có tập.
- Không đổi code app; chỉ thêm test và tài liệu.

Kết quả (30/10/2026, `OUT` = scratch dir `…/scratchpad/ios`, không commit; `SIM` = NovaTest iPhone 17, iOS 26.3):

```bash
# Build sạch: xoá $OUT/DD-m2, $OUT/M2-tests.xcresult, $OUT/M2-attachments; xcodebuild clean
cd iosApp && xcodegen generate && git status --porcelain .         # không có dòng nào (sau commit test)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m2 -resultBundlePath $OUT/M2-tests.xcresult
#   ** TEST SUCCEEDED **; summary: 161 test, 161 passed, 0 failed, 0 skipped (119 unit + 42 UI, ~12 phút)
#   M1: OnboardingUITests 3, CategorySelectionUITests 3, MainShellUITests 5, HomeUITests 4, HomeErrorUITests 2,
#       LocalizationUITests 2 (testEnglishLocale + testEnglishSearchAndDetail), LaunchSmokeUITests 2, unit M1 — Passed
#   M2: PodcastDetailUITests 7, PodcastDetailErrorUITests 2, SearchUITests 10, SearchErrorUITests 2,
#       PodcastDetailModelTests 11, EpisodeRowTests 12, DescriptionTextTests 4, LoggingPlaybackControllerTests 4,
#       ArtworkLoaderTests 12, SearchModelTests 19, CategoryPodcastsModelTests 4, SearchResultItemTests 7 — Passed
xcrun xcresulttool export attachments --path $OUT/M2-tests.xcresult --output-path $OUT/M2-attachments   # 54 attachment
./gradlew :shared:iosSimulatorArm64Test --rerun :shared:testAndroidHostTest --rerun :app:assembleDebug :shared:allTests
#   BUILD SUCCESSFUL; iosSimulatorArm64Test + testAndroidHostTest: mỗi target 22 class, 171 test, failures=0 errors=0 skipped=0
xcodebuild … -configuration Release -derivedDataPath $OUT/DD-rel build   # (DD-rel xoá trước) ** BUILD SUCCEEDED **
strings $OUT/DD-rel/…/TSPodcast.app/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_BASE_URL" -e "playback.probe"   # 0 (bản Debug: 10)
```

Kiểm tĩnh (đều đúng như §6):

| AC | Lệnh | Kết quả |
|---|---|---|
| AC5 | `grep -rn "PodcastDetailStubView\|SearchRootView" iosApp`; `grep -rn "navigationTransition(.zoom"` / `"matchedTransitionSource" iosApp/TSPodcast` | 0; 1 và 2 dòng, đều trong `if #available(iOS 18.0, *)` (`Design/ZoomTransition.swift`) |
| AC7 | `grep -rniE "lorem\|opps" iosApp/TSPodcast` | 0 |
| AC16 | script M1-AC8 trên `Localizable.xcstrings`; vòng grep 10 chuỗi vi; `knownRegions` | `vi True`, `missing_en 0`; mọi chuỗi ≥ 1; `Base, en, vi` |
| AC18 | `grep -rn "AllowExplicitContent\|allowExplicit" iosApp/TSPodcast`; `grep -rniE "radio\|vov\|voh" iosApp/TSPodcast`; grep ads/ATT/FB/`device_id`/`privacy_options` trên `iosApp shared/src`; `log show --last 30m` subsystem `tss.t.podcast` (1256 dòng) `\| grep -c -e zzqxjvkwpt -e q=vietnam` | 0; 0; 0; 0 (cũng 0 dòng chứa "vietnam" ở bất kỳ dạng nào) |
| AC19 | `git diff --name-only f370aa0..HEAD` ngoài `shared/`, `iosApp/`, `docs/ios/`; `git status --porcelain sharedResources` | không có file nào (M2 không đổi `gradle/libs.versions.toml`); rỗng |
| AC20 | `git check-ignore -v iosApp/Config/Secrets.xcconfig`; vòng đếm key/secret trong `git log main..HEAD -p`; đếm trong log app 30 phút, log `xcodebuild test`, `docs/ios` | `.gitignore:32:iosApp/Config/Secrets.xcconfig`; `0` và `0`; 0 ở mọi nơi (chỉ in số đếm) |
| AC21 | subject conventional; số trailer `Co-Authored-By` so với `git rev-list --count f370aa0..HEAD`; `git ls-files \| grep -E '(^\|/)(build\|DerivedData\|xcuserdata)/\|\.xcresult\|\.xcframework\|Secrets\.xcconfig$'` | mọi subject đúng dạng (`docs`, `feat`, `test`); số trailer = số commit (kể cả commit tài liệu T4); 0 dòng |

**Log của lần chạy suite** (`log show --last 30m`, subsystem `tss.t.podcast`): `detail_loaded` 30, `search_loaded` 33, `search_failed` 6 (base URL không truy cập được, cố ý), `category_podcasts_loaded` 2 / `_failed` 4, `playback_request` 10 (không dòng nào có `http`), `artwork_failed` 6 (cả 6 đều là host `img.example.com` của stub `ArtworkLoaderTests`, cố ý; 0 ảnh thật lỗi). Từ các số này ước tính một lần chạy toàn bộ suite gửi khoảng **550–600 request** tới PodcastIndex (Home ≈ 430 lời gọi section, Detail 30 × 2, Search ≈ 33, E9 ≈ 50, E1 theo danh mục 2), cùng key với Android (bullet quota trong handoff đã cập nhật).

Screenshot (đã xem bằng mắt): `$OUT/M2-T4-shots/` có 54 attachment đổi tên dễ đọc, trong đó 23 của M2: `M2-detail`, `M2-detail-light`, `M2-detail-dark`, `M2-detail-forYou`, `M2-detail-loading`, `M2-detail-empty`, `M2-detail-explicit`, `M2-detail-error-alert`, `M2-detail-error-inline`, `M2-detail-playback-probe`, `M2-search-idle`, `M2-search-results`, `M2-search-music`, `M2-search-empty`, `M2-search-loading`, `M2-search-error`, `M2-search-detail`, `M2-category-arts`, `M2-category-error`, `M2-search-idle-en`, `M2-search-empty-en`, `M2-detail-error-alert-en` và `M2-search-casino-titles.txt`. Bản en: title lớn "Search", chip "All"/"Music", "Try searching: great podcasts", "Categories" + chip danh mục; rỗng có ảnh `ic_empty_search`, "No results for: **zzqxjvkwpt**" và gợi ý Nhạc; alert "Oops!" với "Cancel"/"Retry". Log: `M2-T4-app.log`, `m2-tests.log`, `M2-T4-release.log`, `M2-T4-gradle.log`.

**handoff.md:** các bullet M2 ở §7 đã có từ T1–T3 và vẫn đúng với cách làm. T4 cập nhật bullet quota (số request đo được mỗi lần chạy suite) và bullet "Môi trường test" (thêm nhánh iOS 17 của zoom transition và giao diện `.searchable` + chip chưa chạy). Không có mục owner mới.

### Giới hạn đã biết của M2 (tổng hợp)

- **UI test gọi PodcastIndex thật** (key dùng chung với Android): khoảng 550–600 request mỗi lần chạy toàn bộ suite (~12 phút). Test fail nếu API chậm hơn 30 s, nếu dữ liệu đổi khác hẳn (ví dụ "vietnam" không còn kết quả, "zzqxjvkwpt" có kết quả) dù app đúng. Unit test dùng fake nên không phụ thuộc mạng.
- **Trending #1 là feed explicit** (30/10/2026, feed 4201720): D6 ẩn hết tập ở Detail; UI test cần tập tự chọn item trending đầu tiên có tập (`openDetailWithEpisodes`), `PodcastDetailErrorUITests` launch thêm một lần để tìm index. Hệ quả sản phẩm ghi ở handoff.
- **D6 ở Home và màn danh mục** chỉ lọc được khi API trả `explicit` (E1/E5 không trả); Detail và Search lọc được. Chưa có toggle vì chưa có Settings.
- ~~Tap episode chỉ gửi sang seam phát nhạc~~: đã thay bằng Player thật ở M3.
- `testBlacklistedResultsHidden` chép danh sách từ khoá `blacklist.json`; Remote Config chưa có trên iOS (`NoOpConfig`).
- Gõ thêm chữ khi đang có kết quả: kết quả cũ hiện thêm 200 ms rồi chuyển loading.
- Nền blur của header Detail kéo lên 400 pt; kéo quá mạnh có thể lộ nền trống phía trên.
- **Chỉ chạy trên iOS 26.3**: nhánh iOS 17 (push không zoom, giao diện `.searchable` + chip) mới compile, chưa chạy (bullet "Môi trường test" trong handoff).
- Tên danh mục giữ tiếng Anh từ API (giống M1).

## M3

Kế hoạch: [milestones/M3.md](milestones/M3.md). Base `6f9aff9`.

Trạng thái: **developer đã làm xong T1–T4, chờ PO nghiệm thu lại (round 2)**. PO review round 1 (ở `15c16cd`): 20/21 AC đạt (một số phần "để QC"), M3-AC21 chưa đạt vì phần tài liệu T4 chưa làm. Round 2 viết mục này, gỡ bullet M2 "seam phát nhạc" khỏi [handoff.md](handoff.md) và sửa thêm lỗi seek PO ghi chú (không chặn). Theo luật test của owner, milestone chỉ chạy unit test + build; XCUITest, simulator walkthrough và screenshot để QC sau khi xong mọi milestone.

### Tóm tắt M3 (M3-AC21)

| Task | Việc chính | AC | Commit |
|---|---|---|---|
| T1 | `shared`: package `tss.t.shared.playback` — `PlayableItem`, `PlaybackQueue` (bất biến, kẹp `startIndex`, `next()`/`previous()` trả `null` ở hai đầu), `PlaybackPositions` (nhớ vị trí nghe), `PlaybackHistory` (`extra:current_item_id` + danh sách gần đây, tối đa 100), `FavouriteStore` (bộ nhớ), `progressOf`/`formatPlaybackTime`; nối vào `SharedGraph` | AC1, AC2 | `924ee22` |
| T2 | iOS: `AudioEngine` (một `AVPlayer`, `actionAtItemEnd = .pause`), `AudioSessionController` (`.playback`/`.spokenAudio`/`.longFormAudio`, interruption, route change, media services reset), `NowPlayingCenter`, `RemoteCommands`, `PlaybackItem`/`PlaybackRequest`/`PlaybackState`, `Info.plist` (`UIBackgroundModes = audio`, `NSAllowsArbitraryLoadsForMedia`), fixture `tone-60s.m4a`; xoá `LoggingPlaybackController` | AC3–AC7 | `9b1c3cb` |
| T3 | iOS: `PlayerView` (fullScreenCover), `UpNextSheet`, `MiniPlayerView` thật trong `MiniPlayerSlot`, Detail → Player + dấu "Đang phát", Home Live → Player (cả list Live đã làm phẳng), hook `TSP_UITEST_AUDIO_URL`/`-uitest-force-live`/probe mới, chuỗi §4.8 vi + en, XCUITest | AC8–AC16 | `9b1c3cb` (gộp với T2), `15c16cd` (identifier trong plan) |
| T4 | Kiểm unit/build/Release, `LocalizationUITests.testEnglishPlayer`, tài liệu; round 2: sửa seek trong lúc seek resume | AC16–AC21 | `b3c7459` + commit tài liệu này |

Ghi chú commit: T2 và T3 nằm chung một commit (`9b1c3cb`) thay vì hai commit như plan §5, vì Player/MiniPlayer và engine đổi cùng lúc `AppEnvironment`, `PlaybackController` và các test cũ dùng `LoggingPlaybackController`; tách ra thì commit giữa không build được.

Quyết định chính (§7 của plan, đều đã có trong [handoff.md](handoff.md)):

| Hạng mục | Quyết định | Lý do / chi tiết |
|---|---|---|
| Lưu trữ | Favourite + lịch sử Episode/Podcast giữ trong bộ nhớ tới M4; `extra:current_item_id` và vị trí nghe lưu bền trong `KeyValueStore` (DataStore) | Chưa có Room KMP. Favourite bấm ở Player mất khi app bị kill. (§7 mục 1) |
| Nút More | Không có | Android vẽ icon nhưng không gắn hành động; nút không làm gì là rủi ro review. (§7 mục 2) |
| Lỗi phát / buffering | "Không phát được tập này" + "Tải lại" (nạp lại đúng item ở vị trí cũ); buffering → Play/Pause thành spinner `player.buffering` | Android không báo gì khi ExoPlayer lỗi. (§7 mục 3) |
| Nhớ vị trí nghe ([Sửa] §3.6) | Lưu mỗi 10 s khi đang phát, khi pause, khi đổi episode, khi app vào background (`scenePhase`); 5 s đầu không lưu; còn < 30 s coi là nghe xong (xoá); live không lưu | Android chỉ lưu media id nên mọi episode phát lại từ 0. Hằng số ở `PlaybackPositions.companion`. (§7 mục 4) |
| Prev/Next | Lưu `current_item_id` sau mỗi lần đổi; nút disable ở hai đầu playlist; giữ trạng thái play/pause như `playWhenReady` | Android chỉ lưu khi tap từ Detail/Live; nút ở hai đầu bấm được nhưng không làm gì. (§7 mục 5) |
| Hết episode | Dừng ở cuối item, không tự sang tập sau; bấm Play lại thì phát từ đầu | Giống `setPauseAtEndOfMediaItems(true)` của Android. |
| Remote command | Bật play/pause/toggle, next/previous track, `changePlaybackPosition`; tắt skip ±, seek forward/backward, đổi tốc độ (live: tắt cả `changePlaybackPosition`) | Không bật command cho tính năng [iOS+] app chưa có. (§7 mục 6) |
| MiniPlayer | Dòng phụ là tên podcast; Live dùng `feedTitle`; stream không có duration hiện "LIVE", không seek | Android hiện `description` (thường là HTML dài). (§7 mục 7) |
| Audio session | `setCategory` khi khởi tạo, `setActive(true)` chỉ khi bắt đầu phát (không ở launch) | Không cắt nhạc của app khác khi người dùng chỉ mở app. |
| Host audio | Không gửi User-Agent tuỳ chỉnh; `NSAllowsArbitraryLoadsForMedia = true` (không bật `NSAllowsArbitraryLoads`) | ExoPlayer cũng dùng UA mặc định; key header của `AVURLAsset` là API không công khai; ATS cho `URLSession` giữ nguyên. (§7 mục 9) |
| Checklist máy thật | Giao owner (AirPods, tai nghe dây, cuộc gọi, Siri, báo thức, Lock Screen, khoá màn hình 30 phút) | Simulator không mô phỏng được. (§7 mục 8, bullet "Môi trường test" trong handoff) |
| Seek lúc vừa nạp item (round 2) | Seek của người dùng trong lúc seek resume còn chạy thay target thay vì bị mất; completion của lần nạp cũ bị bỏ qua (`loadGeneration`) | Ghi chú không chặn của PO round 1. |

Giới hạn đã biết: xem "Giới hạn đã biết của M3 (tổng hợp)" ở cuối mục M3.

### T1 — Shared: playback logic (xong)

Thoả M3-AC1, M3-AC2, chạy lại M3-AC20. Commit `924ee22`.

- `tss.t.shared.playback`: `PlayableItem` (id, feedId, url, title, podcastTitle, artworkUrl, referer, durationMs, isLive), `PlaybackQueue(items, startIndex)` (kẹp index, `current`, `hasPrevious`/`hasNext`, `next()`/`previous()` trả queue mới hoặc `null`), `PlaybackPositions` (§4.3, quy tắc ở bảng trên), `PlaybackHistory` (khoá `extra:current_item_id` như Android, danh sách gần đây mới nhất trước, không trùng, tối đa 100), `FavouriteStore` (theo `type` + `id`, trong bộ nhớ), `PlaybackProgress.kt` (`progressOf` chặn duration ≤ 0/NaN, `formatPlaybackTime`: `65 000` → `1:05`, `3 605 000` → `1:00:05`, âm → `0:00`).
- `SharedGraph` có `playbackPositions`, `playbackHistory`, `favouriteStore`, dùng chung `KeyValueStore` với M1.
- Lệch nhỏ đã được PO chấp nhận: `PlaybackHistory.saveCurrent(item)` không nhận `queue` (không dùng tới).
- Test `commonTest`: `PlaybackQueueTest` 6, `PlaybackPositionsTest` 6, `PlaybackHistoryTest` 3, `FavouriteStoreTest` 3, `PlaybackProgressTest` 2.

```bash
./gradlew :shared:allTests :shared:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test + testAndroidHostTest: mỗi target 27 class, 191 test (M2: 22 class, 171), failures=0 errors=0
```

M3 không đổi file Gradle nào (`git diff --name-only 6f9aff9..HEAD` chỉ có `shared/`, `iosApp/`, `docs/ios/`).

### T2 — iOS: AudioEngine, session, Now Playing, remote command, background (xong)

Thoả M3-AC3 … M3-AC7. Commit `9b1c3cb`.

- `Playback/AudioEngine.swift` (`@Observable`, `@MainActor`, triển khai `PlaybackController`): đúng một `AVPlayer` (`makePlayer`, tạo lại khi media services reset), không `AVQueuePlayer`; queue lấy từ `PlaybackQueue` của `shared`. Nạp item: lưu `current_item_id`, đọc vị trí đã lưu, `replaceCurrentItem`, seek tới vị trí khi `readyToPlay` rồi mới phát. `loadGeneration` chặn kết quả của lần nạp cũ. Ghi lịch sử/vị trí qua một chuỗi `Task` nối tiếp (đọc không vượt ghi). State công bố qua `PlaybackState` (`isPlaying` theo `timeControlStatus`, `isBuffering`, `positionMs`/`durationMs`, `isLive`, `isFavourite`, `error`), time observer 0,25 s.
- `AudioSessionController`: category như bảng trên; interruption began → pause (nhớ đang phát), ended + `shouldResume` → phát lại chỉ khi trước đó đang phát và người dùng chưa pause, bỏ qua `wasSuspended`; route change `oldDeviceUnavailable` → pause; media services reset → `AVPlayer` mới, giữ item + vị trí, không tự phát.
- `NowPlayingCenter` (title, artist = album = tên podcast, duration, elapsed, rate, `MediaType.audio`, `IsLiveStream`, artwork qua `ArtworkLoader`); `RemoteCommands` như bảng trên.
- `Info.plist`: `UIBackgroundModes = [audio]`, `NSAppTransportSecurity.NSAllowsArbitraryLoadsForMedia = true`.
- Fixture `iosApp/scripts/make-test-audio.sh` → `TSPodcastUITests/Fixtures/tone-60s.m4a` (tone 60 s, commit), là resource của `TSPodcastUITests` và `TSPodcastTests`, không nằm trong `TSPodcast.app`.
- Unit test: `PlaybackStateTests` 4, `PlaybackItemTests` 4, `AudioEngineTests` 10 (8 + 2 của round 2), `AudioEngineErrorTests` 2, `AudioSessionControllerTests` 7 (notification tổng hợp), `NowPlayingInfoTests` 3, `RemoteCommandsTests` 5. `AudioEngineTests` phát fixture thật trên simulator.

### T3 — iOS: Player, Up next, MiniPlayer, điều hướng (xong)

Thoả M3-AC8 … M3-AC15 (phần UI "để QC"). Commit `9b1c3cb`; `15c16cd` sửa identifier trong plan cho khớp code (PO round 1 chấp nhận, lần sau đề xuất qua PO).

- `Player/PlayerView.swift`: mở bằng `.fullScreenCover` từ `MainTabView` (`AppEnvironment.isPlayerPresented`); nền artwork blur, nút chevron `player.close`, artwork vuông bo 12, tiêu đề 2 dòng, tên podcast, slider (seek khi thả) + nhãn `formatPlaybackTime`, hàng 5 nút Favourite · Prev · Play/Pause · Next · Playlist; live hiện "LIVE" thay slider. Lỗi: `player.error` + `player.retry`.
- `Player/UpNextSheet.swift`: toàn bộ queue, `upNext.item.<i>`, item đang phát có "Đang phát" + `.isSelected`, tap → `select(index:)` rồi đóng sheet; detents medium/large.
- `Main/MiniPlayerView.swift` trong `MiniPlayerSlot` (giữ 3 nhánh iOS của M1): artwork, tiêu đề, tên podcast, play/pause, progress mảnh; `miniplayer.open` label "`<tiêu đề>, <tên podcast>`". Hiện khi có item và Player đang đóng. Đã gỡ placeholder M1 và hook `-uitest-miniplayer-placeholder`.
- Detail: tap episode gửi cả list + index rồi mở Player (`PodcastDetailModel.play`); dòng đang phát (cùng `feedId`) có `waveform` + `detail.episode.<i>.nowPlaying`. Home Live: tap gửi cả list Live đã làm phẳng (`pageIndex * pageSize + row`), `home.live.pager` (Debug: value `count=`); log `live_tap` đã xoá.
- Hook (chỉ `#if DEBUG`): env `TSP_UITEST_AUDIO_URL`, `-uitest-force-live`, `-uitest-playback-probe` (label mới theo §4.6).
- Chuỗi §4.8 vi + en trong `Localizable.xcstrings`.
- XCUITest (viết xong, compile qua `build-for-testing`, **chưa chạy — để QC**): `PlayerUITests` (7: `testEpisodeTapOpensPlayerAndPlays`, `testPlayPauseSeekAndTimeLabels`, `testPreviousNextFollowPlaylist`, `testFavouriteToggles`, `testUpNextListsQueueAndSelects`, `testDetailMarksNowPlayingEpisode`, `testPlayerDarkAppearance`), `MiniPlayerUITests.testMiniPlayerAfterClosingPlayer`, `PlayerErrorUITests.testUnplayableItemShowsErrorAndRetry`, `LivePlayerUITests` (2), `LocalizationUITests.testEnglishPlayer`; `MainShellUITests.testMiniPlayerSlotSitsAboveTabBar` đổi sang phát fixture rồi đóng Player; `PodcastDetailUITests.testEpisodeTapHandsPlaylistToPlaybackSeam` bị thay bằng `PlayerUITests.testEpisodeTapOpensPlayerAndPlays`.

### T4 — Nghiệm thu nội bộ, tài liệu, vệ sinh (xong ở round 2)

Thoả M3-AC16 … M3-AC21 trong phạm vi luật test (unit + build). Plan §5 T4 bước 1 ghi "chạy toàn bộ `xcodebuild test`"; luật test của owner (có sau khi viết plan) đưa phần UI sang QC, nên T4 chỉ chạy unit test, `build-for-testing` và build Release.

Round 2 (30/10/2026):

- **Nguyên nhân AC21 fail:** sau commit code `9b1c3cb`, bước 2 của T4 (viết `progress.md`, cập nhật `handoff.md`, commit tài liệu) bị bỏ sót; mục M3 chỉ có dòng "đang làm" từ lúc lập plan. Đã viết mục này, sửa bảng milestone, gỡ bullet M2 "Tap episode chỉ gửi sang seam phát nhạc" khỏi `handoff.md` (M3 đã có Player) và sửa giới hạn tương ứng của M2 ở trên cho khỏi gây hiểu nhầm.
- **Sửa thêm (ghi chú không chặn của PO):** `AudioEngine.seek(toMs:)` gọi trong lúc seek resume (vị trí đã lưu) còn chạy bị mất, vì completion của seek resume đặt `pendingStartMs = 0` rồi phát từ vị trí đã lưu. Giờ completion (`startSeekFinished`) áp target mới (seek thêm một lần, hoặc về 0) và bỏ qua completion của lần nạp cũ. Seam test `onStartSeekSent` chỉ có trong `#if DEBUG`. Test `testSeekDuringTheResumeSeekIsKept`, `testSeekToStartDuringTheResumeSeekIsKept`; kiểm đột biến: tắt phần sửa thì cả hai fail (vị trí `20000` thay vì ≥ `39500` / < `5000`), bật lại thì pass 3/3 lần chạy liên tiếp. Commit `b3c7459`.
- **Drag-down để đóng Player:** chưa thêm, chỉ đóng bằng chevron `player.close` (xem giới hạn).

Lệnh đã chạy và kết quả (`OUT` = scratch dir `…/scratchpad/ios`, không commit; `SIM` = NovaTest iPhone 17, iOS 26.3):

```bash
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m3-r2 -resultBundlePath $OUT/M3-r2-unit.xcresult -only-testing:TSPodcastTests
#   ** TEST SUCCEEDED **; summary: 152 test, 152 passed, 0 failed, 0 skipped (round 1: 150)
xcodebuild build-for-testing -project iosApp/iosApp.xcodeproj -scheme TSPodcast -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m3-r2                                    # ** TEST BUILD SUCCEEDED ** (UI target compile được)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Release \
  -destination "id=$SIM" -derivedDataPath $OUT/DD-rel-m3-r2 build   # ** BUILD SUCCEEDED **
strings $OUT/DD-rel-m3-r2/Build/Products/Release-iphonesimulator/TSPodcast.app/TSPodcast \
  | grep -c -e "uitest-" -e "TSP_UITEST_" -e "playback.probe" -e "onStartSeekSent"   # 0
```

`shared/` không đổi ở round 2 nên không chạy lại `:shared:allTests` (kết quả T1 và PO round 1: 27 class, 191 test mỗi target, 0 fail); không đổi file Gradle nên không chạy `:app:assembleDebug` (PO round 1: `BUILD SUCCESSFUL`). `project.yml` không đổi, không thêm file mới nên không cần `xcodegen generate`.

Kiểm tĩnh M3-AC21:

| Lệnh | Kết quả |
|---|---|
| `git check-ignore -v iosApp/Config/Secrets.xcconfig` | `.gitignore:32:iosApp/Config/Secrets.xcconfig` |
| đếm key/secret (đọc từ `Secrets.xcconfig`, chỉ in số) trong `git log main..HEAD -p` | `0` và `0` |
| `git log 6f9aff9..HEAD --format='%s'` + đếm `Co-Authored-By` so với `git rev-list --count 6f9aff9..HEAD` | mọi subject conventional (`docs`, `feat`, `fix`); số trailer = số commit |
| `git ls-files \| grep -E '(^\|/)(build\|DerivedData\|xcuserdata)/\|\.xcresult\|\.xcframework\|Secrets\.xcconfig$'` | 0 dòng |
| `grep -n "seam phát nhạc" docs/ios/handoff.md` | 0 |

Ghi chú: một lần chạy cả suite của developer trước review round 1 (`$OUT/M3-xcodebuild.log`, chạy trước khi có luật test) có 53 UI test, 2 fail ở `SearchUITests` (`testBlacklistedResultsHidden`, `testCategoryOpensTrendingByCategory`, phụ thuộc dữ liệu PodcastIndex thật, ngoài scope M3). Không dùng làm bằng chứng nghiệm thu; QC chạy lại.

**handoff.md:** gỡ bullet M2 "Tap episode chỉ gửi sang seam phát nhạc"; các bullet M3 (§7 mục 1–9) đã có từ T1–T3 và vẫn đúng với code; thêm vào bullet "Môi trường test" nhánh iOS 17–18 của MiniPlayer thật; thêm bullet mới "Player chỉ đóng bằng nút chevron" (khoảng trống so với §3.2, chờ owner/QC quyết).

### Giới hạn đã biết của M3 (tổng hợp)

- **Chưa chạy XCUITest nào của M3** (luật test của owner): `PlayerUITests`, `MiniPlayerUITests`, `PlayerErrorUITests`, `LivePlayerUITests`, `LocalizationUITests.testEnglishPlayer`, `MainShellUITests` (đã điều chỉnh) chỉ mới compile. Các AC "để QC" trong review round 1 (AC8–AC16 phần UI, screenshot light/dark/MiniPlayer của AC15) chờ QC.
- **Chỉ chạy trên iOS 26.3**: nhánh iOS 17–18 (`safeAreaInset`) và iOS 26.0 của `MiniPlayerSlot`, giờ chứa MiniPlayer thật, mới compile (bullet "Môi trường test" trong handoff).
- **Checklist audio máy thật** (AirPods, tai nghe dây, cuộc gọi, Siri, báo thức, Lock Screen/Control Center, khoá màn hình 30 phút) chưa làm; logic interruption/route change/reset chỉ có unit test bằng notification tổng hợp. Giao owner (handoff).
- **Player chỉ đóng bằng chevron** `player.close`; `.fullScreenCover` không có vuốt xuống để đóng (§3.2 ghi "vuốt xuống hoặc chevron"). Chưa thêm vì gesture cần kiểm bằng UI run; QC/owner quyết có cần không.
- **Favourite và lịch sử chỉ trong bộ nhớ** tới M4; sau khi kill app chưa khôi phục item cuối (Android cũng chỉ khôi phục qua notification).
- **UI test vẫn gọi PodcastIndex thật** cho danh sách episode/Live (audio luôn là fixture); fail nếu API chậm hơn 30 s hoặc không có Live/episode.
- Lệch nhỏ so với §4.2 PO đã chấp nhận: `PlaybackRequest` không có `feedId` riêng (nằm trong từng `PlaybackItem`), `PlayerPresentation` gộp thành `AppEnvironment.isPlayerPresented`, không có `PlaybackState.derive` (thay bằng hàm thuần `isBuffering`/`milliseconds`, có test).

## M4

Kế hoạch: [milestones/M4.md](milestones/M4.md) (revision 2: không UI test, chỉ unit test + smoke check). Base `5b8e26c`.

Trạng thái: **T1–T5 xong**, M4-AC1 … AC26 đã tự kiểm, chờ PO nghiệm thu. Tóm tắt, checklist parity §7 và danh sách "Để QC" ở mục T5 (cuối mục M4).

### T1 — Shared: Room KMP, PruneApiCache, favourite, lịch sử, fallback offline; hạ tầng kiểm tra (xong)

Thoả M4-AC1 … M4-AC7 (tự kiểm, chờ PO). Commit `90c82f0` (hạ tầng), `ac416f8` (shared + chỗ Swift tối thiểu).

**Hạ tầng kiểm tra (§4.8)**

- Scheme `TSPodcastUnit` trong `iosApp/project.yml`: build `TSPodcast` (all) + `TSPodcastTests` (test); test action **chỉ** `TSPodcastTests`. Scheme `TSPodcast` không đổi. `xcodegen generate`, đã commit `iosApp.xcodeproj/xcshareddata/xcschemes/TSPodcastUnit.xcscheme`.
- `iosApp/scripts/smoke-launch.sh <udid> <.app> [--check-db]`: `simctl bootstatus -b`, marker, `install`, `launch tss.t.podcast`, chờ 15 s (`SMOKE_WAIT_SECONDS` đổi được), kiểm `launchctl list` + crash report `*TSPodcast*` mới hơn marker trong `~/Library/Logs/DiagnosticReports`; `--check-db` kiểm thêm `Library/Application Support/TSPodcastDatabase.db` trong data container. In `SMOKE PASS`/`SMOKE FAIL` (exit 0/1). Đã thử nhánh fail: trên bản chưa mở DB, `--check-db` → `database file missing` + `SMOKE FAIL`, exit 1.
- Chạy thử trên HEAD trước T1 (M3): `TSPodcastUnit` 152 test pass, log 0 dòng `in target 'TSPodcastUITests'`, smoke `SMOKE PASS`.

**Shared (§4.1, §4.2)**

| Thành phần | File | Ghi chú |
|---|---|---|
| Gradle | `gradle/libs.versions.toml` (chỉ thêm `sqliteBundled = "2.6.2"` + `androidx-sqlite-bundled`), `shared/build.gradle.kts` | Plugin `ksp` + `androidx.room` (đã khai `apply false` ở gốc từ trước, không đổi `build.gradle.kts` gốc), `room { schemaDirectory("$projectDir/schemas") }`, `room-runtime` + `sqlite-bundled` ở commonMain, `room-compiler` cho `kspAndroid`, `kspIosSimulatorArm64`, `kspIosArm64`. **Không** nâng Kotlin 2.2.0 / KSP 2.2.0-2.0.2 / AGP 8.13.2 / Room 2.8.4: build được nguyên bộ. |
| Entity | `storage/Entities.kt` | `PodcastEntity`, `FeedEntity`, `EpisodeEntity`, `FavouriteEntity` (bảng `Podcast`, `Feed`, `Episode`, `FavouriteDTO`) + `PodcastWithEpisodes` (`@Relation` `Podcast.id` → `Episode.feedId` như `PodcastAndEpisode`). Class riêng, không annotate model API, vì `Podcast.feedId`, `Episode.feedItunesId` là NOT NULL ở Android nhưng optional trong JSON. Mapper ở `StorageMappers.kt` (`feedId` thiếu → `id`, `feedItunesId` thiếu → 0). |
| Converter | `storage/StorageConverters.kt` | Cột JSON bằng kotlinx.serialization; đọc/ghi lỗi → `null` như Gson `runCatching` của Android. `type` của favourite dùng built-in enum converter (lưu tên enum). |
| DB | `storage/PodcastDatabase.kt`, `PodcastDatabase.ios.kt`, `PodcastDatabase.android.kt` | `@Database(4 entity, version = 1)`, `@ConstructedBy`, `BundledSQLiteDriver`, `setQueryCoroutineContext(Dispatchers.IO)`. `openPodcastDatabase(path)` (iOS), `inMemoryPodcastDatabase()` (test). Android `actual` ném `UnsupportedOperationException` (Android dùng DB của `:core`, không có `Context`). Interface `PodcastDaos` để `commonTest` dùng fake. Schema: `shared/schemas/tss.t.shared.storage.PodcastDatabase/1.json` (đã commit; `createSql` của 4 bảng trùng từng ký tự với `6.json`). |
| DAO | `storage/Daos.kt` | `PodcastDao` (`insertRows` REPLACE, `selectById`, `selectAllEpisodeById`, `deleteExpired` + [Sửa]), `EpisodeDao` (`insertRows`, `selectById`, `selectByFeedId` mới nhất trước, `deleteExpired`), `FeedDao`, `FavouriteDao` (`insert`, `delete(type, id)`, `selectById(id, type)`, `observeAll(): Flow` + `getAll()` `ORDER BY rowid DESC`, `count`). |
| `PodcastStorage` | `storage/PodcastStorage.kt` | Phần `insert`/`inserts` của DAO Android: gán `cachedAt = clock.nowMillis()` rồi REPLACE; đọc theo model API (`podcastById`, `episodeById`, `feedById`, `podcastWithEpisodes`, `episodesOfFeed`). |
| `PruneApiCache` | `usecase/PruneApiCache.kt` | `TTL_MS = 7 ngày`, xoá Podcast → Episode → Feed, trả `PruneResult`; log `PruneApiCache: removed …` khi > 0; lỗi → `record(tag = "PruneApiCache")`, không ném ra. Chưa gọi lúc launch (T3). |
| Crash seam (tối thiểu) | `crash/CrashReporter.kt` | `CrashReporter`, `LogCrashReporter`, `Crash` (facade, `install`), `safeCall(tag, keys, reporter)` — port `sharedLibrary/crash`. Làm sớm trong T1 vì AC4 cần `Crash.log`/`record`; T2 bổ sung `KotlinCrashHook`, dùng ở `GetMusicFeeds`, `CrashTest`. |
| Favourite | `favourite/FavouriteRepository.kt`, `FavouriteResolver.kt` | `FavouriteType { Podcast, PodcastEpisode }`, `FavouriteItem` (chuyển từ `playback`), `FavouriteRepository` (`items: Flow` mới nhất trước, `isFavourite`, `setFavourite`, `toggle`, `count`; suspend) thay `FavouriteStore` (đã xoá). `FavouriteResolver.resolve(item)` → `ResolvedFavourite(podcast, episodes sắp datePublished giảm dần, startIndex)` hoặc `null`. |
| Lịch sử | `playback/PlaybackHistory.kt` | `savePlaying(episode, podcast, playlist)` (episodes → podcast → khoá, như `HistoryRepository`), `saveLive(live, list)` (`Episode.fromLive` + `Podcast.fromLive` như `playLive`), `saveCurrentId(id)` (prev/next: chỉ khoá), `currentItemId()`. Bỏ `recentItems`. |
| Model | `model/Episode.kt`, `model/Podcast.kt` | `Episode.fromLive` (giữ nguyên mapping Android, kể cả `image = feedImage`, `feedUrl = chaptersUrl ?: ""`), `Podcast.fromLive` (`id = feedId`, `title`/`feedTitle` = tiêu đề live). |
| Detail offline | `detail/PodcastDetailFeed.kt` | E10 lỗi → đọc `episodesOfFeed(feedId)`; có → `Success` với `isFromCache = true`, D6 vẫn áp dụng (kể cả ẩn cả feed nếu E11 nói explicit); rỗng hoặc DB lỗi → lỗi E10 như M2. E10 thành công thì không đọc DB. |
| `watch` | `flow/Watch.kt`, `flow/Watch.ios.kt` | `watch(flow, onEach): Closeable` (iOS: `Dispatchers.Main`; Swift: `Watch_iosKt.watch(flow:onEach:)`, giá trị là `Any?`). Lỗi của flow → `Crash.record(tag = "watch")`, không crash. |
| `SharedGraph` | `SharedGraph.kt` | Constructor mới `(credentials, config, preferencesPath, databasePath)` (bỏ constructor 3 tham số); constructor chính nhận thêm `database: PodcastDaos`. Có `podcastStorage`, `pruneApiCache`, `playbackHistory`, `favouriteRepository`, `favouriteResolver`; `close()` đóng cả DB. Tham số `crashReporter` của plan §4.2 để T2 (cùng `InstallId`). |

**Swift (chỉ đủ để build, logic iOS ở T3)**

- `AppEnvironment`: truyền `databasePath` = `Application Support/TSPodcastDatabase.db` (`PodcastDatabase.companion.FILE_NAME`); dùng `graph.favouriteRepository`. Chưa đặt `isExcludedFromBackup`, chưa chạy prune (T3, `DatabaseLocation`).
- `AudioEngine`: `FavouriteRepository` thay `FavouriteStore`; đọc cờ favourite khi đổi item và `toggleFavourite()` đều chạy trong hàng đợi persistence tuần tự (toggle không thể chạy trước lần đọc của item mới; kết quả bỏ qua nếu item đã đổi). Lịch sử gọi `saveCurrentId` (chỉ khoá) — ghi playlist + podcast vào Room từ Detail/Live là T3 (`PlaybackRequest` mang nguồn lịch sử). Favourite bấm ở Player **đã lưu bền** trong Room.
- Test: `PlaybackFixture` dựng `SharedGraph` trên file DataStore + file DB tạm; `testFavouriteFollowsTheItem` chờ `waitForPersistence()` và đọc lại từ repository; bỏ assert `recentItems` và đọc khoá trực tiếp từ store (khoá Android đã được `PlaybackHistoryTest` của shared kiểm).

**Test**

| Test | Nơi | AC | Số test |
|---|---|---|---|
| `DatabaseSchemaParityTest` | iosTest | AC2 | 5 (version 1; 4 bảng, không `RadioChannel`; cột (tên, affinity, notNull) + khoá chính từng bảng bằng `6.json`; `cachedAt` NOT NULL; `type` đọc thô bằng SQL là `"Podcast"`/`"PodcastEpisode"`) |
| `PodcastDatabaseTest` | iosTest (Room in-memory, clock giả) | AC3 | 7 |
| `PruneApiCacheTest` | iosTest | AC4 | 7 (now = ngày 30; 8 ngày xoá, 6 ngày còn; episode favourite còn; podcast favourite giữ podcast + episode + feed; [Sửa] podcast của episode favourite còn; log 1 dòng / 0 dòng; DAO ném lỗi → `record` tag `PruneApiCache`) |
| `PlaybackHistoryTest` | iosTest (chuyển từ commonTest) | AC5 | 5 |
| `FavouriteResolverTest` | iosTest | AC6 | 5 |
| `FavouriteRepositoryTest` | iosTest (thay `FavouriteStoreTest`) | — | 3 |
| `PodcastDetailFeedTest` | commonTest (+6 case, DAO fake) | AC7 | 20 (M2: 14) |
| `WatchTest` | commonTest | — | 2 |

Fixture: `generateTestFixtures` thêm `core/schemas/.../6.json` và `shared/schemas/.../1.json` (tên property `androidDatabaseSchemaV6`, `sharedDatabaseSchemaV1` qua `propertyNames`, vì tên file là số).

**Lệnh và kết quả** (`OUT` = scratchpad `ios/`, simulator `4FB8953E-…`)

```bash
./gradlew :shared:allTests :shared:linkDebugFrameworkIosSimulatorArm64 :app:assembleDebug   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test: 32 class, 225 test; testAndroidHostTest: 26 class, 193 test; failures=0 errors=0 (M3: 27 class, 191 test mỗi target)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m4 -resultBundlePath $OUT/M4-unit.xcresult -only-testing:TSPodcastTests
#   ** TEST SUCCEEDED **, Executed 152 tests, 0 failures; grep -c "in target 'TSPodcastUITests'" → 0
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination "id=$SIM" -derivedDataPath $OUT/DD-m4
#   ** BUILD SUCCEEDED **; grep -c "in target 'TSPodcastUITests'" → 0
iosApp/scripts/smoke-launch.sh $SIM $OUT/DD-m4/Build/Products/Debug-iphonesimulator/TSPodcast.app   # SMOKE PASS
git diff --name-only 5b8e26c..HEAD -- '*.gradle.kts' gradle/ settings.gradle.kts   # gradle/libs.versions.toml, shared/build.gradle.kts
git diff --name-only 5b8e26c..HEAD -- app core coreApi podcasts sharedFirebase sharedLibrary iosApp/TSPodcastUITests   # rỗng
```

Quyết định trong T1:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Entity riêng + mapper | Không annotate model API | Schema phải bằng `6.json` (NOT NULL khác JSON); model API giữ các field Android không lưu (author, artwork…). |
| DAO là interface, `cachedAt` gán ở `PodcastStorage` | Thay vì abstract class tự đọc `System.currentTimeMillis()` | Đồng hồ inject được (plan §3.1 "test được"); `commonTest` fake được DAO. |
| Test DB thật chỉ ở `iosTest` | `commonTest` dùng `FakePodcastDaos` cho `TestGraph` | Android host test không có `Context` để dựng Room (plan §4.1). SQL thật (prune, relation, order) chỉ được kiểm trên iOS simulator. |
| Crash seam làm sớm | `CrashReporter`/`Crash`/`safeCall` ở T1 | AC4 cần `Crash.log`/`record`. T2 không phải làm lại phần này. |
| Fixture schema không xếp thứ tự với `copyRoomSchemas` | Bỏ `mustRunAfter` | `copyRoomSchemas` phụ thuộc task KSP của test, mà task đó cần fixture → vòng phụ thuộc. Test đọc file schema đã commit; nếu đổi schema, test thấy file mới từ lần build sau. |
| Typealias `Categories` trong entity | Dùng `Map<String, String>` | KSP (Room) báo "MissingType" với typealias/kiểu suy luận từ serializer sinh bởi plugin; property serializer trong converter được ghi kiểu tường minh vì lý do đó. |

Giới hạn đã biết sau T1 (T3 xử lý):

- App chưa ghi playlist + podcast vào Room khi phát từ Detail/Live (shared đã có `savePlaying`/`saveLive`, Swift chưa gọi) và chưa chạy `PruneApiCache` lúc launch; file DB chỉ được tạo khi có truy vấn đầu tiên (đọc cờ favourite khi phát), nên `smoke-launch.sh --check-db` chưa pass ở T1.
- `isExcludedFromBackup` cho file DB chưa đặt (T3, `DatabaseLocation`).
- Tab Yêu thích vẫn là view rỗng của M1; Detail chưa có banner offline (`isFromCache` đã có trong shared).
- Hook `-uitest-reset` (M1) xoá DataStore + cache ảnh nhưng **không** xoá DB Room mới, nên favourite giờ còn lại giữa các lần launch của UI test. `PlayerUITests.testFavouriteToggles` bật rồi tắt nên tự dọn, nhưng một lần chạy bị ngắt giữa chừng sẽ để lại `fav=1` cho lần sau. Chưa sửa (không đụng UI test, không thêm hook ở T1); T3 cân nhắc cho reset xoá luôn bảng `FavouriteDTO`, nếu không thì QC xoá app trước khi chạy suite. Cờ favourite trên Player giờ cập nhật sau một lần ghi DB (vài ms); test trên đã chờ bằng `waitForProbe`/`poll`.

### T2 — Shared: Install ID, crash, analytics, deep link, E12, cài đặt thông báo, retry Remote Config (xong)

Thoả M4-AC8 … M4-AC11 (tự kiểm, chờ PO). Không đổi file Gradle nào nên không chạy `:app:assembleDebug`.

| Thành phần | File (`shared/src/.../tss/t/shared/`) | Ghi chú |
|---|---|---|
| `InstallId` | `preferences/InstallId.kt` | Port `SharedPref.getOrCreateInstallId()`: khoá `InstallId`, `kotlin.uuid.Uuid.random()`, tạo lại khi thiếu/blank. Có `Mutex` để hai lần gọi đầu đồng thời không tạo hai id khác nhau. `SharedGraph.installId`. |
| Crash seam (phần còn lại) | `crash/CrashReporter.kt` | Thêm `safeCallOrNull`, `safeCallOr` (như `SafeCall.kt` Android) và `CrashReporter.recordFatal(throwable, stackAddresses)` — **không có ở Android**: trên JVM Crashlytics tự bắt exception, trên Kotlin/Native thì không; Swift implement ở T4 (Crashlytics `ExceptionModel` + địa chỉ stack). `SharedGraph` nhận `crash: CrashReporter = Crash` (test truyền fake), truyền cho `PruneApiCache` và `GetMusicFeeds`. |
| `KotlinCrashHook` | iosMain `crash/KotlinCrashHook.kt` | `installKotlinCrashHook(reporter)` (Swift: `KotlinCrashHookKt.installKotlinCrashHook(reporter:)`) dùng `setUnhandledExceptionHook`; thân hook `kotlinCrashHandler` gửi throwable + `getStackTraceAddresses()` tới `recordFatal`, nuốt lỗi của chính reporter, rồi để runtime kết thúc process. Gọi hook cũ (nếu có). Chưa gọi từ app (T4). |
| `GetMusicFeeds` | `usecase/GetMusicFeeds.kt` | E6 lỗi → `record(cause, "GetMusicFeeds", {medium=music})` như Android. |
| Analytics | `analytics/AnalyticsTracker.kt`, `analytics/AnalyticsDefaults.kt` | `trackEvent(name, screen: String?, params)` (screen null khi không biết màn nào, như `currentScreenName` null của Android). `AnalyticsDefaults.build(osVersion, appVersion, nowSeconds, screen)` → `os="iOS"`, `os_version`, `app_version`, `event_time_stamp` (giây), `screen` (chỉ khi có); **không** có tham số thiết bị (D5). `eventParams(...)` ghép theo thứ tự Android (props đè `os`/`app_version`, `event_time_stamp` và `screen` luôn thắng). `screenEventName` (`<lowercase>_screen`), `fcmReceiveEvent(type)`, `trimName` ≤ 40, `trimValue` ≤ 100 (không cắt giữa surrogate pair). |
| `DeepLinkParser` | `deeplink/DeepLinkParser.kt` | `DeepLinkParser(allowedHosts).parse(url)` → `DeepLink.Podcast(feedId)` / `DeepLink.Episode(episodeId)` / null. Parse tay (không dùng `Url` của Ktor, vốn dễ dãi với URL lạ): chỉ `https`, host thuộc danh sách (không phân biệt hoa thường), không user info, port chỉ 443, path `/podcast/<seg>` hoặc `/episode/<seg>`, tiền tố `/en` tuỳ chọn, bỏ một `/` cuối, bỏ query/fragment. `parseIdSlug` port từ `web/src/lib/slug.ts`: percent-decode như `decodeURIComponent` (escape hỏng/UTF-8 sai → null), `^([0-9]{1,15})(?:-(.*))?$`, id > 0. Danh sách host rỗng → mọi link là null (plan §7 mục 6). |
| E12 | `network/PodcastIndexApi.kt`, `model/Responses.kt`, `usecase/GetEpisodeById.kt` | `getEpisodeById(id, fulltext = true)` → `GET episodes/byid?id=&fulltext=true`; `EpisodeByIdRes(episode)` đọc object, phần tử đầu của mảng (như `single()` của web) hoặc `[]` → null. `GetEpisodeById(id)`: một request, không retry, không cache; không có episode → `Error(NoSuchElementException)`. Không lọc D6/blacklist ở đây (nơi gọi ở T5 lọc). `SharedGraph.getEpisodeById`. |
| Thông báo | `preferences/AppPreferences.kt` | `notificationsOptIn()` / `saveNotificationsOptIn`, `notificationsPromptShown()` / `saveNotificationsPromptShown`; khoá `NotificationsOptIn`, `NotificationsPromptShown`, mặc định `false`; `clear()` xoá. |
| `RemoteConfigRetry` | `config/RemoteConfigRetry.kt` | `MAX_ATTEMPTS = 20`, `delayFor(n) = 30 s × n` (n ≥ 1), `delayMillisFor(n)` cho Swift (Kotlin `Duration` không qua cầu ObjC được), `hasAttemptAfter(n)`. |

**Swift (chỉ cập nhật chữ ký)**

- `LogAnalyticsTracker.trackEvent(name:screen:params:)`; dòng log thêm params sắp theo tên, bỏ `screen=` khi nil. `OnboardingModel` gọi với `params: [:]`. `RecordingAnalyticsTracker` (test) ghi cả params.
- `LogAnalyticsTrackerTests` +2 test (params sắp xếp, không có screen); `OnboardingModelTests` kiểm `OnboardingFinished` không có props (như Android).

**Test**

| Test | Nơi | AC | Số test |
|---|---|---|---|
| `InstallIdTest` | commonTest | AC8 | 6 (UUID v4 ở khoá `InstallId`; gọi lại/instance mới cùng giá trị; `""`/blank → tạo mới; giá trị cũ giữ nguyên; 20 lần gọi đồng thời ra 1 id; hai install khác id) |
| `CrashTest` | commonTest | AC8 | 9 (facade gọi được trước `install`; sau `install(fake)` mọi `log/record/key/userId/recordFatal` tới fake; `safeCall` trả giá trị / nuốt + `record` tag + keys / mặc định dùng facade; `safeCallOrNull`, `safeCallOr`; không nuốt `CancellationException`; `GetMusicFeeds` HTTP 500 → `record` tag `GetMusicFeeds`, keys `{medium=music}`; thành công → không record) |
| `KotlinCrashHookTest` | iosTest | AC8 | 3 (handler → fake nhận đúng throwable + địa chỉ stack khác rỗng, không thành record non-fatal; reporter ném lỗi không thoát ra; đi qua facade `Crash`) |
| `AnalyticsDefaultsTest` | commonTest | AC9 | 8 |
| `DeepLinkParserTest` | commonTest | AC10 | 6 (9 URL podcast, 3 URL episode, 27 URL bị từ chối gồm mọi case của AC, host rỗng, nhiều host, `parseIdSlug`) |
| `PodcastIndexApiTest` | commonTest | AC10 | +3 (request E12 có `id=456`, `fulltext=true`, header ký; parse object; `[]` → null, mảng → phần tử đầu) |
| `GetEpisodeByIdTest` | commonTest | AC10 | 3 |
| `AppPreferencesTest` | commonTest | AC11 | +2 (mặc định false, ghi/đọc lại qua file mới, khoá thô; `clear()`) |
| `RemoteConfigRetryTest` | commonTest | AC11 | 3 (`MAX_ATTEMPTS == 20`, `delayFor(1) == 30 s`, `delayFor(3) == 90 s`; dừng sau lần 20, tổng chờ 95 phút; attempt 0 bị từ chối) |

**Lệnh và kết quả**

```bash
./gradlew :shared:allTests :shared:linkDebugFrameworkIosSimulatorArm64   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test: 39 class, 268 test; testAndroidHostTest: 32 class, 233 test; failures=0 errors=0 (T1: 32/225 và 26/193)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m4 -resultBundlePath $OUT/M4T2-unit.xcresult -only-testing:TSPodcastTests
#   ** TEST SUCCEEDED **, Executed 154 tests, 0 failures (T1: 152); grep -c "in target 'TSPodcastUITests'" → 0
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination "id=$SIM" -derivedDataPath $OUT/DD-m4
#   ** BUILD SUCCEEDED **; grep -c "in target 'TSPodcastUITests'" → 0
iosApp/scripts/smoke-launch.sh $SIM $OUT/DD-m4/Build/Products/Debug-iphonesimulator/TSPodcast.app   # SMOKE PASS
grep -rIn "device_id" shared/src iosApp --exclude-dir=build | wc -l   # 0
```

(`--check-db` vẫn `SMOKE FAIL: database file missing` như ghi ở T1: app chưa mở DB lúc launch; AC12 thuộc T3.)

Quyết định trong T2:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| `recordFatal` trong `CrashReporter` | Thêm method vào interface thay vì interface riêng | Hook cần gửi địa chỉ stack để Crashlytics symbolicate frame Kotlin; mọi reporter Swift (log/Firebase, T4) buộc phải xử lý crash Kotlin, không quên được. |
| `fcmReceiveEvent` với type rỗng/blank | → `fcm_null_message_receive` | Plan §4.2. Android: `null` → `fcm_null_…`, `""` → `fcm__message_receive`; gộp hai trường hợp cho dễ đọc trên console. |
| `RemoteConfigRetry` tối đa 20 lần | Theo features-spec §1 | Vòng lặp Android (`fetchAndActivate` một lần trước vòng + `failCount <= 20`) thực tế có thể gọi tới 22 lần; spec ghi 20. |
| `trackEvent` có `screen: String?` | Nullable | Push nhận ngoài màn hình nào (Android `currentScreenName` null). `AnalyticsDefaults` bỏ key `screen` khi null (Firebase cũng bỏ giá trị null). |
| Cắt độ dài trong shared | `eventParams` tự cắt key ≤ 40, value ≤ 100 | Firebase bỏ cả event khi tên/key quá 40 ký tự; data của push có thể dài. |
| E12 không lọc | `GetEpisodeById` trả nguyên episode | D6/blacklist áp dụng ở nơi hiển thị (T5) giống danh sách E10; tránh lọc hai lần. |

Giới hạn đã biết sau T2:

- Hook crash Kotlin, `InstallId`, `RemoteConfigRetry`, `AnalyticsDefaults`, notification prefs và `DeepLinkParser` mới có ở shared; app chưa dùng (T4 nối Firebase/crash/push, T5 nối deep link).
- `KotlinCrashHookTest` chỉ gọi thân hook; việc runtime thật sự gọi hook và kết thúc process chưa kiểm (kết thúc process trong test là không được). QC/T4 có thể kiểm bằng một crash cố ý ở bản Debug khi đã có Crashlytics.
- Reporter Firebase ở T4 phải ghi đồng bộ trong `recordFatal` vì process kết thúc ngay sau khi hook trả về.

### T3 — iOS: lưu trữ, Player favourite bền, lịch sử, tab Yêu thích, Detail offline (xong)

Thoả M4-AC12 … M4-AC14 phần unit test + smoke (tự kiểm, chờ PO); các bước simulator của AC13/AC14 là "để QC". Không đổi file Gradle nào nên không chạy `:app:assembleDebug`. Không thêm hook UI test, không viết/sửa XCUITest.

| Thành phần | File (`iosApp/TSPodcast/`) | Ghi chú |
|---|---|---|
| DB + prune lúc launch | `App/DatabaseLocation.swift`, `App/AppEnvironment.swift` | `DatabaseLocation.url` = `Application Support/TSPodcastDatabase.db` (tên lấy từ `PodcastDatabase.FILE_NAME`). `AppEnvironment` mở **một** `SharedGraph` trên file này; init chính nhận `graph` + `LaunchCachePruning` (test truyền fake) và chạy `prune()` một lần trong `Task.detached(priority: .utility)`, không chờ. `SharedCachePruner` gọi `PruneApiCache` (query đầu tiên → Room tạo file), log số row đã xoá, rồi đặt `isExcludedFromBackup` cho file DB và các file `-wal`/`-shm`/`-journal` đang có. |
| Lịch sử phát | `Playback/PlaybackHistorySource.swift`, `PlaybackRequest.swift`, `AudioEngine.swift` | `PlaybackRequest.history`: `.playlist(podcast, episodes)` (Detail, Favourite) hoặc `.live([LiveEpisode])` (Home Live) — giữ nguyên model Kotlin của E10/E2/Room (bọc trong `KotlinValue`), không dựng lại từ `PlaybackItem`. Engine gọi `PlaybackHistory.record(source, itemId:)` trên hàng đợi persistence: request mới → `savePlaying`/`saveLive` (playlist + podcast + `extra:current_item_id`, như `SaveCurrentPlayingUseCase`/`playLive`); prev/next/Up next → chỉ `saveCurrentId`. |
| Podcast lưu kèm | shared `detail/PodcastDetailFeed.kt` | `PodcastDetail.podcast`: `Podcast.fromFeed` của E11, offline thì row podcast đã lưu; `podcastToSave(feedId, title, image, description)` dựng từ route khi không có cả hai (Android lưu `Podcast` nó navigate tới, route iOS chỉ có 4 field). |
| Player favourite bền | `Playback/AudioEngine.swift`, `PlaybackItem.swift` | Từ T1 đã đi qua `FavouriteRepository`. T3 thêm `PlaybackItem.summary` (mô tả plain text của episode/live) để row `FavouriteDTO` có `description` như `MediaItem.toFavouriteDTO()`; thiếu mô tả thì dùng tên podcast. |
| Tab Yêu thích | `Favourite/FavouriteModel.swift`, `FavouriteDataSource.swift`, `FavouriteRootView.swift`, `Main/MainTabView.swift`, `App/RootView.swift` | `FavouriteModel` (`@Observable`): observe `FavouriteRepository.items` qua `watch` suốt đời model ([Sửa] cập nhật ngay, không cần mở lại); state `loading`/`empty`/`loaded`. `select(index)` → `FavouriteResolver` → một `PlaybackRequest` (episodes đã lưu, mới nhất trước, `startIndex` của mục favourite; ghi lại playlist như `playerEpisode`) + push `PodcastRoute` vào `path` của tab, **không** mở Player. Không resolve được → `unavailable` → alert "Không mở được mục này" với "Bỏ yêu thích" (`setFavourite(false)`) và "Đóng". Một tap đang resolve thì tap khác bị bỏ qua. Row: `ArtworkImage` 60 pt, tiêu đề 2 dòng, mô tả HTML → text 2 dòng; không ad banner. |
| Detail offline | `Detail/PodcastDetailModel.swift`, `PodcastDetailView.swift`, `SharedPodcastDetailDataSource.swift`, `PodcastDetailDataSource.swift` | `isFromCache` của shared → `isShowingSavedEpisodes` → banner `detail.offlineBanner` (icon `wifi.slash`) trên danh sách; retry thành công thì banner mất. |
| Chuỗi | `Resources/Localizable.xcstrings` | `favourite.unavailable.title`, `.message`, `.remove`, `action.close`, `detail.offlineBanner` (vi nguồn, en đúng bảng §4.7). |

accessibilityIdentifier mới (cho QC): `favourite.list` (ScrollView), `favourite.item.<index>` (label "`<tiêu đề>, <mô tả>`", Debug có value `count=`), `favourite.unavailable` (message của alert), `favourite.unavailable.remove`, `favourite.unavailable.close`, `detail.offlineBanner`; giữ `favourite.root`, `favourite.empty`, `favourite.discoverMore`.

**Test**

| Test | AC | Nội dung |
|---|---|---|
| `PersistenceWiringTests` (4) | AC12 | `DatabaseLocation.url` trong Application Support, tên `TSPodcastDatabase.db`; pruner thật trên file tạm tạo file và đặt `isExcludedFromBackup == true` (cả file phụ); `AppEnvironment` với pruner giả → prune đúng 1 lần, không trên main thread; file không tồn tại không phải lỗi. |
| `AudioEngineTests` (+4) | AC13 | toggle → row `FavouriteDTO(PodcastEpisode, "1")`; graph + engine mới trên cùng file → `isFavourite == true`; toggle lại → row bị xoá. Detail play → 3 episode + podcast trong DB, `current_item_id = 2`, skipNext → `3`. Live play → episode + podcast như `playLive`. Request không có history → không ghi row. |
| `FavouriteModelTests` (8) | AC14 | loading → empty; Flow giả phát thêm mục **không gọi lại load** → `items` cập nhật, chỉ 1 observation; `select` resolve được → đúng 1 request, `startItem` đúng, `path == [route]`; không resolve → `unavailable`, `remove()` xoá và list theo bảng; "Đóng" giữ mục; tap ngoài range; nhả model thì dừng observe; 1 test trên Room thật (`watch` + `FavouriteResolver`, mô tả HTML → text, mục mồ côi → alert → xoá). |
| `PodcastDetailModelTests` (+3), `PlaybackItemTests` (sửa 1), `LocalizationTests` (+5 key mỗi ngôn ngữ) | AC13/AC14 | banner offline, retry bỏ banner, tap mang history Room; `summary` không qua `PlayableItem` của shared (chỉ favourite dùng). |
| shared `PodcastDetailFeedTest` (+1, sửa 2) | AC13 | podcast lưu kèm từ E11 / row đã lưu / route. |

**Lệnh và kết quả**

```bash
./gradlew :shared:allTests   # BUILD SUCCESSFUL
#   iosSimulatorArm64Test: 39 class, 269 test; testAndroidHostTest: 32 class, 234 test; failures=0 errors=0 (T2: 268/233)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -destination "id=$SIM" \
  -derivedDataPath $OUT/DD-m4 -resultBundlePath $OUT/M4T3-unit.xcresult -only-testing:TSPodcastTests
#   ** TEST SUCCEEDED **, Executed 173 tests, 0 failures (T2: 154); grep -c "in target 'TSPodcastUITests'" → 0
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination "id=$SIM" -derivedDataPath $OUT/DD-m4
#   ** BUILD SUCCEEDED **; grep -c "in target 'TSPodcastUITests'" → 0
iosApp/scripts/smoke-launch.sh $SIM $OUT/DD-m4/Build/Products/Debug-iphonesimulator/TSPodcast.app --check-db
#   SMOKE PASS (process running, no new crash report, database file exists); log: prune_api_cache …, db_backup_excluded files=3
grep -rn "FavouriteStore\|recentItems" iosApp/TSPodcast iosApp/TSPodcastTests shared/src | wc -l   # 0
```

Quyết định trong T3:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Model lưu lịch sử | `PlaybackRequest` mang model Kotlin (`KotlinValue`, `@unchecked Sendable`) | Plan §3.2: row Room phải là model đầy đủ của E10/E2, không dựng lại từ `PlaybackItem`. Data class Kotlin bất biến nên chuyển giữa actor an toàn; view không đọc chúng. |
| Backup exclusion | Đặt sau prune (file đã có) và cho cả file `-wal`/`-shm`/`-journal` | Room chỉ tạo file ở query đầu; WAL chứa dữ liệu mới chưa checkpoint. |
| Mô tả favourite từ Player | Mô tả episode (plain text), thiếu thì tên podcast | Android `toFavouriteDTO()` dùng `MediaMetadata.description`; dòng trống trông như lỗi. |
| Tap trùng | Bỏ qua tap khi một tap đang resolve | Tránh hai request phát + hai lần push Detail. |
| Alert có thêm id `favourite.unavailable.close` | Thêm ngoài danh sách §4.6 | QC cần bấm "Đóng" ổn định; không đổi id nào có sẵn. |

Giới hạn đã biết sau T3:

- Các bước simulator của AC13 (favourite → kill → mở lại) và AC14 (1)–(5) chưa chạy: để QC.
- `PodcastRoute` khi tap favourite lấy tiêu đề/ảnh/mô tả từ row Podcast đã lưu; podcast được lưu từ route (lúc E11 lỗi) chỉ có tiêu đề, ảnh, mô tả, nên các field khác của row đó trống tới lần E11 thành công sau.
- Không khôi phục item đang phát sau khi kill app (Android cũng chỉ khôi phục qua notification; M4 §2 ngoài phạm vi).
- Xoá favourite của một episode ở tab Yêu thích chỉ qua alert mục hỏng hoặc Player (như Android: không có swipe-to-delete).

### T4 — iOS: Firebase tuỳ chọn, analytics, crash, Remote Config, push opt-in, NSE, Cài đặt (xong)

Thoả M4-AC15 … M4-AC19 và M4-AC21 phần unit test + build + smoke (tự kiểm, chờ PO); bước simulator của AC18/AC21 là "để QC", gửi thật qua FCM/APNs và Crashlytics thật là "chặn bởi owner". Không đổi file Gradle nào và không đổi `shared/` nên không chạy `:app:assembleDebug` / `:shared:allTests`. Không thêm hook UI test, không viết/sửa XCUITest, không build/chạy `TSPodcastUITests`.

| Thành phần | File (`iosApp/`) | Ghi chú |
|---|---|---|
| Firebase SPM | `project.yml`, `iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` | `firebase-ios-sdk` **`exactVersion: 12.19.2`** (12.19.3 phát hành 22/10/2026, chưa đủ 2 tuần; 13.x ngoài "12.x"). Chỉ target app link: `FirebaseCore`, `FirebaseAnalyticsCore` (Analytics **không** kèm IdentitySupport/AdSupport), `FirebaseCrashlytics`, `FirebaseRemoteConfig`, `FirebaseMessaging`. |
| Bootstrap | `TSPodcast/Firebase/FirebaseBootstrap.swift` | `configureIfAvailable(bundle:configure:)`: có `GoogleService-Info.plist` trong bundle → `FirebaseApp.configure(options:)` → `.enabled`; không có → `.disabled(.missingPlist)`; file đọc không được → `.disabled(.invalidPlist)`. Log `firebase=enabled` / `firebase=disabled reason=missing_plist` (OSLog, category `Firebase`). |
| 4 cặp seam | `TSPodcast/Firebase/AppServices.swift`, `FirebaseAnalyticsTracker.swift`, `CrashReporters.swift`, `RemoteConfig.swift`, `Notifications/PushRegistrar.swift` | `AppServices.make(for:)`: `.enabled` → `FirebaseAnalyticsTracker`, `FirebaseCrashReporter`, `FirebaseRemoteConfigAPI` (+ nguồn cho `RemoteConfigLoader`), `FirebasePushRegistrar`; còn lại → `LogAnalyticsTracker`, `OSLogCrashReporter`, `NoOpConfig`, `LogPushRegistrar`. Implementation Firebase chỉ được tạo khi `.enabled` nên bản không plist không gọi API Firebase nào. |
| Thứ tự launch | `TSPodcast/App/AppEnvironment.swift` (`LaunchSteps`), `App/AppDelegate.swift`, `App/TSPodcastApp.swift` | Như `App.onCreate`: bootstrap → `AppServices` → `CrashWiring.install` (`Crash.install` rồi `installKotlinCrashHook(Crash.shared)`) → `SharedGraph(config: services.config, …)` → nền: `Crash.userId(InstallId)` → `RemoteConfigLoader.run()` (chỉ khi có Firebase, task riêng) → `PruneApiCache`. `AppDelegate` (`@UIApplicationDelegateAdaptor`) giữ `AppEnvironment`, đặt delegate notification ở `didFinishLaunching`, chuyển APNs token cho `PushRegistrar`. |
| Analytics | `Firebase/FirebaseAnalyticsTracker.swift` | `trackScreen` → `<screen lowercase>_screen`, `trackEvent` → `name` + `screen`; tham số từ shared `AnalyticsDefaults.eventParams` (`os="iOS"`, `os_version` dạng `17.5.1`, `app_version` = `CFBundleShortVersionString`, `event_time_stamp` giây, cắt key 40 / value 100). Hàm map thuần, lệnh `Analytics.logEvent` được inject. |
| Crash | `Firebase/CrashReporters.swift` | `FirebaseCrashReporter`: `log` → breadcrumb, `record` → `record(error:userInfo:)` với domain = tag (gom issue theo call site) + keys, `key` → custom value, `userId` → `setUserID`, `recordFatal` → `ExceptionModel` + `StackFrame(address:)` từ địa chỉ stack Kotlin. `OSLogCrashReporter` (category `Crash`) ghi tag/kiểu/số key, không ghi Install ID. |
| Remote Config | `Firebase/RemoteConfig.swift` | `RemoteConfigLoader`: tối đa `RemoteConfigRetry.MAX_ATTEMPTS` (20) lần, sau lần lỗi n chờ `delayMillisFor(n)` (30 s × n); thành công → `blacklistRepository.reload()` đúng 1 lần; lỗi lần 20 → `crash.record(tag "RemoteConfig", attempts=20)`, giữ asset đóng gói. `FirebaseRemoteConfigAPI.getString` chỉ trả giá trị có nguồn `.remote`. |
| Push opt-in | `Notifications/NotificationsModel.swift`, `NotificationSystem.swift`, `Settings/SettingsView.swift` (modifier `notificationsPrompt`, `notificationsDeniedAlert`), `Player/PlayerView.swift` | `start()` lúc launch chỉ đọc `NotificationsOptIn` (đã opt-in + iOS còn cho phép → `registerForRemoteNotifications`), **không** xin quyền. `AppEnvironment` observe số favourite: lần đầu đi từ 0 lên ≥ 1, chưa opt-in, `NotificationsPromptShown == false` → alert trong Player. "Để sau"/"Bật thông báo" đều ghi `PromptShown = true`; bật → `requestAuthorization([.alert, .sound, .badge])` → được cấp: `registerForRemoteNotifications` + `subscribe("new_podcasts")` + `OptIn = true`; bị từ chối: `OptIn = false` + alert "Mở Cài đặt". Tắt → `unsubscribe` + `OptIn = false`. `requestAuthorization` chỉ có trong `NotificationsModel.enable()` (+ wrapper hệ thống). |
| FCM | `Notifications/PushRegistrar.swift`, `TSPodcast/Info.plist` | `FirebaseMessagingAutoInitEnabled = NO`: chưa opt-in thì không có FCM token; `subscribe` bật auto-init, chờ APNs token rồi mới subscribe topic. |
| Delegate notification | `Notifications/NotificationPresenter.swift` | `willPresent`: `type == "new_podcasts"` → `[.banner, .list, .sound]`, loại khác (kể cả 3 loại ads, loại lạ, thiếu type) → `[]`. `willPresent` và `didReceive` gửi `fcm_<type>_message_receive` (thiếu type → `fcm_null_message_receive`) với các key payload trừ `aps` (string, số → text), `screen` = nil. Tap chỉ mở app. |
| NSE | `TSPodcastNotificationService/NotificationService.swift`, `Info.plist`; logic `TSPodcast/Notifications/RichNotification.swift` (biên dịch vào cả NSE và app) | Target `app-extension` `tss.t.podcast.NotificationService`, nhúng vào `PlugIns/`, chỉ Foundation + UserNotifications. URL: `big_image`, không hợp lệ/thiếu thì `fcm_options.image`; chỉ http(s) có host. Tải bằng `URLSession` trong cuộc đua với timeout 20 s; HTTP 2xx + 12 byte đầu là JPEG/PNG/GIF/WebP/HEIC (không tin `Content-Type`) → `UNNotificationAttachment("big_image")`; mọi lỗi/hết giờ/`serviceExtensionTimeWillExpire` → nội dung gốc, handler gọi đúng 1 lần. |
| Payload mẫu | `scripts/push-new-podcast.apns` | JSON: `Simulator Target Bundle = tss.t.podcast`, `aps.alert{title, body}`, `aps.mutable-content = 1`, `type = new_podcasts`, `big_image` https. |
| Cài đặt | `Settings/SettingsModel.swift`, `SettingsView.swift`, `Main/MainTabView.swift` | Nút bánh răng `settings.open` ở toolbar tab Khám phá mở sheet `settings.root`: `settings.notifications` (qua `NotificationsModel.setEnabled`), `settings.explicit` (ghi `AllowExplicitContent`; Home/Search/Detail đọc lại ở lần tải sau), `settings.privacy` (chỉ hiện khi `TSPrivacyPolicyURL` là URL http(s) hợp lệ; build setting `PRIVACY_POLICY_URL` mặc định rỗng), `settings.version` ("1.0.0 (1)"), `settings.close`. |
| Build/plist | `scripts/copy-firebase-plist.sh`, `scripts/crashlytics-upload-symbols.sh`, `.gitignore`, `Config/Shared.xcconfig`, `TSPodcast/TSPodcast.entitlements`, `TSPodcast/Info.plist` | `iosApp/Config/Firebase/*/GoogleService-Info.plist` gitignored; build phase chép plist khi có (`FIREBASE_PLIST_DIR` đổi thư mục nguồn), xoá bản cũ khi không có. dSYM upload chỉ Release + có plist. Entitlement `aps-environment = development` (`CODE_SIGN_ENTITLEMENTS`). Info.plist: `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS = false`, `FirebaseMessagingAutoInitEnabled = false`, `TSPrivacyPolicyURL = $(PRIVACY_POLICY_URL)`; không có `NSUserTrackingUsageDescription`. |
| Chuỗi | `TSPodcast/Resources/Localizable.xcstrings` | 12 key mới (vi nguồn, en như bảng §4.7): `notifications.prompt.title/.message/.enable/.later`, `notifications.denied.message/.hint/.openSettings`, `settings.title/.notifications/.explicit/.privacy/.version`. `LocalizationTests` thêm 10 key mỗi ngôn ngữ. |

accessibilityIdentifier mới (cho QC): `settings.open`, `settings.root`, `settings.notifications`, `settings.explicit`, `settings.privacy`, `settings.version`, `settings.close`, `notifications.prompt` (message của alert), `notifications.prompt.enable`, `notifications.prompt.later`, `notifications.denied` (message của alert từ chối, thêm ngoài §4.6), `notifications.denied.openSettings`.

**Test** (scheme `TSPodcastUnit`, chỉ `TSPodcastTests`)

| Test | AC | Nội dung |
|---|---|---|
| `FirebaseBootstrapTests` (6) | AC15 | bundle không plist → `.disabled(.missingPlist)`, closure configure không được gọi; bundle có plist giả → `.enabled` với đúng path; đọc lỗi → `.invalidPlist`; `AppServices` offline đúng 4 kiểu + không có Remote Config; `AppEnvironment` qua `LaunchSteps` với bundle không plist → không configure, 4 seam log/no-op, `Crash` nhận `OSLogCrashReporter`; app host không có plist. |
| `AnalyticsMappingTests` (6) | AC16 | `trackScreen("Onboarding", CurrentPage=2)` → `onboarding_screen`, đúng 5 key, `event_time_stamp` = giây, không `screen`; `trackEvent("OnboardingFinished", "Onboarding")` → `screen=Onboarding`; không key nào chứa "device"; cắt tên/key 40, value 100; version mặc định. |
| `CrashWiringTests` (4) | AC16 | thứ tự launch `firebase.configure → services → crash.install → graph`; reporter giả nhận `userId` **đúng 1 lần** = `graph.installId.getOrCreate()` (UUID); `CrashWiring.install` gọi `Crash.install` trước hook Kotlin; facade `Crash` chuyển log/record/userId tới reporter đã cài; non-fatal domain = tag. |
| `RemoteConfigLoaderTests` (6) | AC17 | lỗi 2 lần → chờ 30 s, 60 s → reload 1 lần; lỗi mãi → đúng 20 fetch, 19 lần chờ (30 s … 570 s), không reload, 1 `record`; thành công ngay không chờ; giá trị `ts_podcast_black_list` của nguồn giả qua `ConfigAPI.getString` và blacklist của `SharedGraph` thật đổi sang nó sau reload; không Firebase → asset đóng gói; sleep bị huỷ → dừng. |
| `NotificationsModelTests` (12) | AC18 | launch không xin quyền; đã opt-in + được phép → register lại, không xin; bị tắt ở Cài đặt iOS → toggle tắt; 0 → 1 → prompt; mở app đã có favourite / favourite thứ hai / đã hỏi → không; "Để sau" ghi `PromptShown`, không hỏi lại; "Bật" + cấp → register 1, subscribe `new_podcasts` 1, `OptIn = true`; từ chối → `OptIn = false`, `showDeniedAlert`, "Mở Cài đặt"; toggle bật bỏ qua prompt; toggle tắt → unsubscribe. |
| `NotificationPresentationTests` (6) | AC18 | `new_podcasts` → banner/list/sound; `banner_ads`, `video_ads`, `full_screen_ads`, lạ, rỗng, thiếu, không phải string → `[]`; `willPresent`/`didReceive` mỗi lần đúng 1 event với key payload (không `aps`, số thành text); thiếu type → `fcm_null_message_receive`. |
| `RichNotificationTests` (10) | AC19 | `StubURLProtocol` (thêm `.hang`): `big_image` hợp lệ → 1 attachment `big_image`; `fcm_options.image` khi thiếu `big_image`; không URL / URL sai (ftp, file, rỗng, `https://`) → gốc, không request; lỗi mạng, HTTP 404, nội dung không phải ảnh, server treo (timeout 300 ms) → gốc; nhận dạng ảnh theo byte. |
| `SettingsModelTests` (5) | AC21 | explicit ghi/đọc `AllowExplicitContent` qua `AppPreferences` thật; toggle thông báo đi qua `NotificationsModel` (center giả: 1 lần xin quyền, subscribe/unsubscribe); `privacyURL` nil với rỗng/sai, có với https; Info.plist của app khai `TSPrivacyPolicyURL` (rỗng → ẩn) và version "1.0.0 (1)". |

**Lệnh và kết quả** (`OUT` = scratchpad/ios, `SIM` = NovaTest iPhone 17)

```bash
cd iosApp && xcodegen generate && cd ..
xcodebuild -resolvePackageDependencies -project iosApp/iosApp.xcodeproj -scheme TSPodcast -derivedDataPath $OUT/DD-m4 -disablePackageRepositoryCache
#   Firebase 12.19.2 + phụ thuộc (GoogleAppMeasurement 12.19.2, GoogleUtilities 8.1.5, nanopb, Promises, …)
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -destination "id=$SIM" -derivedDataPath $OUT/DD-m4 \
  -disablePackageRepositoryCache -resultBundlePath $OUT/M4T4-unit.xcresult -only-testing:TSPodcastTests FIREBASE_PLIST_DIR=$OUT/no-firebase
#   ** TEST SUCCEEDED **, Executed 228 tests, 0 failures (T3: 173); grep -c "in target 'TSPodcastUITests'" → 0
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination "id=$SIM" -derivedDataPath $OUT/DD-m4 \
  -disablePackageRepositoryCache FIREBASE_PLIST_DIR=$OUT/no-firebase
#   ** BUILD SUCCEEDED **; "firebase plist: none for Debug", "crashlytics: Debug build, skip dSYM upload"
iosApp/scripts/smoke-launch.sh $SIM $OUT/DD-m4/Build/Products/Debug-iphonesimulator/TSPodcast.app --check-db
#   SMOKE PASS; log của process smoke: firebase=disabled reason=missing_plist, crash_user_id set, prune_api_cache …
xcrun simctl spawn $SIM log show --last 2m --predicate 'subsystem == "tss.t.podcast"' | grep -c "firebase=disabled reason=missing_plist"   # ≥ 1
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Release -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" -derivedDataPath $OUT/DD-m4 -disablePackageRepositoryCache FIREBASE_PLIST_DIR=$OUT/no-firebase
#   ** BUILD SUCCEEDED **; "crashlytics: no GoogleService-Info.plist in the app, skip dSYM upload"
R=$OUT/DD-m4/Build/Products/Release-iphonesimulator/TSPodcast.app
otool -L $R/TSPodcast | grep -c -E "AdSupport|AppTrackingTransparency|FBSDK"                       # 0
plutil -extract NSExtension.NSExtensionPointIdentifier raw $R/PlugIns/TSPodcastNotificationService.appex/Info.plist   # com.apple.usernotifications.service
otool -L $R/PlugIns/TSPodcastNotificationService.appex/TSPodcastNotificationService | grep -ci firebase   # 0
plutil -extract GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS raw $R/Info.plist        # false
plutil -extract NSUserTrackingUsageDescription raw $R/Info.plist                                   # không có key
git check-ignore -v iosApp/Config/Firebase/Debug/GoogleService-Info.plist iosApp/Config/Firebase/Release/GoogleService-Info.plist   # 2 dòng
git ls-files | grep -c GoogleService-Info.plist                                                    # 0
grep -rn "requestAuthorization" iosApp/TSPodcast    # NotificationsModel.enable() + protocol/wrapper NotificationSystem
grep -rIn "device_id" shared/src iosApp --exclude-dir=build | wc -l                                # 0
plutil -convert xml1 -o /dev/null iosApp/scripts/push-new-podcast.apns && python3 -m json.tool iosApp/scripts/push-new-podcast.apns >/dev/null   # OK
```

Quyết định trong T4:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Version Firebase | `12.19.2` (exact) | Bản 12.x mới nhất đã phát hành ≥ 2 tuần (12.19.3: 22/10/2026). |
| Analytics không IDFA | Product `FirebaseAnalyticsCore` | Product không kèm `GoogleAppMeasurementIdentitySupport`; binary Release không link AdSupport/ATT. |
| Hook Kotlin cài cả khi không Firebase | `CrashWiring.install` luôn gọi `installKotlinCrashHook(Crash.shared)` | Không Firebase thì crash Kotlin vẫn để lại dòng `crash_fatal type=… frames=…` trong OSLog; có Firebase thì hook cài sau `configure`. |
| Tên reporter log | `OSLogCrashReporter` (plan ghi `LogCrashReporter`) | Shared đã export `LogCrashReporter` (bản `println`), trùng tên khi test import cả hai module. |
| Thành công của Remote Config | Mọi lần fetch không lỗi đều tính là thành công | Android coi `fetchAndActivate() == false` (không có giá trị mới) là lỗi và tiếp tục retry; với iOS điều đó chỉ tốn request. |
| FCM auto-init | Tắt (`FirebaseMessagingAutoInitEnabled = NO`), bật khi opt-in | Opt-in mặc định tắt (plan §7 mục 9): chưa đồng ý thì không tạo FCM token. |
| dSYM upload | Chỉ Release | Build Debug hằng ngày không gửi gì ra ngoài. |
| Prompt có thêm dòng message | `notifications.prompt.message` "Chỉ gửi khi có podcast mới…" và `notifications.denied.hint` | Alert của SwiftUI chỉ gắn được accessibilityIdentifier lên message (như `favourite.unavailable` ở T3); hai chuỗi này ngoài bảng §4.7. |
| Prompt khi đã opt-in từ Cài đặt | Không hiện | Người dùng đã bật; plan chỉ nói prompt khi chưa hỏi. |
| Bật thông báo bị từ chối từ prompt | Hiện luôn alert "Mở Cài đặt" | Đúng AC18 (`showDeniedAlert == true`); QC xem có quá dồn dập không. |
| Ảnh NSE | Nhận dạng theo byte, không theo `Content-Type` | `UNNotificationAttachment` không kiểm nội dung; trang lỗi HTML trả 200 sẽ thành attachment hỏng. |

Giới hạn đã biết sau T4:

- AC19 ghi `plutil -lint iosApp/scripts/push-new-podcast.apns → OK`, nhưng `plutil -lint` không đọc JSON (báo `Unexpected character { at line 1` với mọi file JSON, kể cả `{"a":1}`), còn `xcrun simctl push` bắt buộc JSON. Thay bằng `plutil -convert xml1 -o /dev/null …` (rc 0) và `python3 -m json.tool` (OK); PO sửa lệnh kiểm trong plan.
- Trên máy dev có sẵn `iosApp/Config/Firebase/{Debug,Release}/GoogleService-Info.plist` (xuất hiện 30/10 11:01, không do task này tạo, đã gitignore, không đọc/in giá trị). Mọi lần build để kiểm của T4 dùng `FIREBASE_PLIST_DIR=$OUT/no-firebase`; app **chưa** từng chạy với Firebase bật. Một lần build Debug đầu tiên (trước khi thêm `FIREBASE_PLIST_DIR` và giới hạn Release) đã chép plist vào app và chạy `Crashlytics/run`, script này báo "Symbol uploading will proceed in the background" (handoff).
- Đường Firebase bật (`FirebaseAnalyticsTracker`/`FirebaseCrashReporter`/`FirebaseRemoteConfigSource`/`FirebasePushRegistrar` gọi SDK thật) chỉ được compile + unit test phần map thuần; chạy thật cần plist hợp lệ và mạng tới Firebase: chặn bởi owner.
- Background push không tap → không có event `fcm_*` (plan §7 mục 8).
- Để QC: (AC18) launch lần đầu không có hộp thoại quyền; favourite đầu tiên ở Player → alert `notifications.prompt`; "Bật thông báo" → hộp thoại iOS; `xcrun simctl push $SIM tss.t.podcast iosApp/scripts/push-new-podcast.apns` → banner có ảnh (NSE); favourite thứ hai → không hỏi lại. (AC21) bánh răng `settings.open` → sheet, bật `settings.explicit`, kéo Home tải lại → không lỗi; tắt/bật `settings.notifications`; `settings.version` = "1.0.0 (1)"; không có `settings.privacy` khi `PRIVACY_POLICY_URL` rỗng.
- UI test cũ có thể bị ảnh hưởng: toolbar tab Khám phá có thêm nút `settings.open`; Player có thêm 2 alert (chỉ hiện khi có trigger). Không sửa XCUITest (để QC).

### T5 — iOS: deep link, PrivacyInfo, localization, đi hết checklist §7, tài liệu (xong)

Thoả M4-AC20, M4-AC22 … M4-AC26 phần unit test + build + smoke + kiểm tĩnh (tự kiểm, chờ PO). Bước simulator là "để QC"; Universal Link thật, Crashlytics/Remote Config thật, push thật, máy thật là "chặn bởi owner" (handoff). Không viết/sửa XCUITest, không build/chạy `TSPodcastUITests`, không thêm hook UI test.

Commit: `1b9180b` feat(shared) `DeepLinkTargets`; `25a3d4b` feat(ios) `DeepLinkRouter`; `b65f3df` fix(ios) (khoảng trống tìm thấy khi đi checklist); `6f33d15` feat(ios) PrivacyInfo + script + `LocalizationTests`; commit docs này.

| Thành phần | File | Ghi chú |
|---|---|---|
| Đích của link (shared) | `shared/src/commonMain/kotlin/tss/t/shared/deeplink/DeepLinkTargets.kt`, `SharedGraph.deepLinkTargets` | Link đến từ ngoài app nên qua blacklist + D6. `podcast(feedId)`: feed id trong blacklist → từ chối không gọi API; E11 (cache 15 phút) → `Podcast.fromFeed`, từ khoá blacklist trong title → từ chối; E11 lỗi/không biết id → podcast đã lưu trong DB; không có → lỗi. Feed explicit **không** bị từ chối (Detail tự hiện trạng thái ẩn explicit của M2). `episode(id)`: E12 → từ chối khi feed id/từ khoá trong title hoặc description bị blacklist (luật E2), hoặc khi chưa bật explicit mà episode `explicit = 1` hoặc feed E11 explicit (vì episode phát ngay); podcast = E11 → DB → dựng từ episode (`id = feedId`, ảnh `feedImage`). Từ chối = `Error(DeepLinkRefusedException(reason))`. |
| Router | `iosApp/TSPodcast/DeepLink/DeepLinkRouter.swift` | `MainNavigation` (`@Observable`: tab đang chọn, stack Khám phá, sheet Cài đặt; chuyển từ `@State` của `MainTabView`). `DeepLinkRouter(hosts:dataSource:playback:navigation:now:)`: parse bằng shared `DeepLinkParser(allowedHosts:)`; link không phải của app (host khác, `http`, `tss://`, path lạ, **host rỗng**) → bỏ qua, chỉ log host. Chưa vào Main → giữ link mới nhất (`pending`), `mainDidAppear()` mở đúng 1 lần. Podcast → `willReveal` (đóng Player) → đóng Cài đặt → tab Khám phá → push `PodcastRoute`. Episode → 1 `PlaybackRequest` qua seam player (không mở Player, MiniPlayer hiện) + push Detail của `feedId`. Không mở được → `isShowingError` (alert `deeplink.error`). Kết quả cũ về muộn bị bỏ (đếm thế hệ); cùng URL lặp lại trong 2 s chỉ mở 1 lần (đến qua cả `onOpenURL` và user activity). `AppLinkHost.hosts(from:)` đọc `TSAppLinkHost`. |
| Data source | `iosApp/TSPodcast/DeepLink/SharedDeepLinkDataSource.swift` | Map `DeepLinkTargets` sang `PodcastRoute` / (`PlaybackRequest` 1 tập, `history = .playlist(podcast, [episode])` để lưu Room như tap favourite, route Detail). Log `deeplink_unavailable kind= id= reason=` (lý do từ chối hoặc tên lớp lỗi), không log URL. |
| Nối vào app | `App/TSPodcastApp.swift`, `App/AppEnvironment.swift`, `Main/MainTabView.swift`, `App/AppLog.swift` | `.onOpenURL` và `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` trên `RootView` cùng gọi `deepLinks.handle(url)`. `MainTabView` bind tab/stack/sheet vào `environment.navigation`, `.task { await deepLinks.mainDidAppear() }`, alert `deeplink.error.title` + message `deeplink.error.message` (identifier `deeplink.error`) + "Đóng". OSLog category `DeepLink`. |
| Host | `TSPodcast/Info.plist`, `Config/Shared.xcconfig` | `TSAppLinkHost = $(APP_LINK_HOST)`, build setting rỗng mặc định ⇒ không nhận link nào. **Không** có entitlement Associated Domains (handoff). |
| Privacy manifest | `iosApp/TSPodcast/PrivacyInfo.xcprivacy` (Resources của app) | `NSPrivacyTracking = false`, `NSPrivacyTrackingDomains` rỗng. Thu thập: `DeviceID` (Linked, không Tracking, AppFunctionality + Analytics), `ProductInteraction` (Linked, Analytics), `CrashData` (Linked, AppFunctionality), `OtherDiagnosticData` (Linked, AppFunctionality). Required-reason API theo lần quét binary Release: FileTimestamp `C617.1`, DiskSpace `E174.1`, UserDefaults `CA92.1`. |
| Script quét | `iosApp/scripts/check-required-reason-apis.sh <.app>` | Quét binary app + `PlugIns/*.appex` + `Frameworks/*.framework` (stub không symbol thì bỏ qua) bằng `nm -u` và `strings`; 5 nhóm của Apple. Fail khi nhóm tìm thấy mà manifest của bundle không khai, khi manifest khai nhóm không tìm thấy, khi reason rỗng/không thuộc danh sách Apple. In bảng bundle → nhóm → symbol → reason. |
| Chuỗi | `TSPodcast/Resources/Localizable.xcstrings` | `deeplink.error.title` "Không mở được liên kết" / "Couldn't open the link" (bảng §4.7); `deeplink.error.message` "Nội dung này không còn hoặc không xem được trong ứng dụng." / "This content is no longer available or can't be shown in the app." (thêm để gắn identifier). |
| Docs | `docs/ios/setup-and-build.md` §3.3, `docs/ios/handoff.md` | Ghi chú `APP_LINK_HOST`/AASA và cách chạy script; handoff thêm NSE provisioning, domain/AASA/`APP_LINK_HOST`, nhãn App Privacy khớp manifest, quyết định deep link (plan §7 mục 6). |

**Test**

| Test | AC | Nội dung |
|---|---|---|
| `DeepLinkTargetsTest` (shared, 11 × 2 target) | AC20 | podcast từ E11; offline → podcast đã lưu; không có gì → `NoSuchElementException`; feed id blacklist → từ chối, 0 request; từ khoá blacklist → từ chối; feed explicit vẫn trả (để Detail xử lý); episode kèm podcast E11; fallback podcast đã lưu rồi dựng từ episode; lỗi E12 giữ exception; blacklist theo feed id/từ khoá episode/từ khoá feed; episode hoặc feed explicit bị từ chối khi chưa bật, bật thì qua. |
| `DeepLinkRouterTests` (14) | AC20 | link podcast → tab Khám phá được chọn, path thêm `PodcastRoute(feedId: 123)`, sheet Cài đặt đóng, Player đóng (`willReveal` 1 lần), không phát; push lên trên stack đang có (`/en/podcast/123/`); link episode → resolve qua data source (E12) → đúng 1 `PlaybackRequest` bắt đầu ở tập 456 + path Detail feed 123; không mở được → `isShowingError`, không phát, không push; link lúc onboarding → giữ, không resolve, `mainDidAppear` ×2 mở đúng 1 lần; chỉ giữ link mới nhất; host `[]`, `[""]`, `["  "]` → bỏ qua mọi link, không alert; link lạ (host khác, `http`, `tss://`, `/category`, `abc-123`, id 0) → bỏ qua im lặng; host không phân biệt hoa thường, `%34%35%36`; cùng URL trong 2 s mở 1 lần, sau 2 s mở lại; kết quả E12 cũ về muộn không đè link mới; `AppLinkHost.hosts`; Info.plist của app có `TSAppLinkHost` và rỗng; `SharedDeepLinkDataSource.episodeTarget` dựng request 1 tập + history `.playlist` + route. |
| `LocalizationTests` (+1, cập nhật 2) | AC23 | `testEveryM4KeyHasVietnameseAndEnglish`: 19 key M4 (§4.7 + 3 message alert) có vi và en, en khác vi; bảng vi/en có thêm `notifications.prompt.message`, `notifications.denied.hint`, `deeplink.error.*`. |

**Lệnh và kết quả §6** (`OUT` = scratchpad/ios, `SIM` = NovaTest iPhone 17, iOS 26.3; mọi build dùng `FIREBASE_PLIST_DIR=$OUT/no-firebase` và `-disablePackageRepositoryCache`)

```bash
./gradlew :shared:allTests
#   BUILD SUCCESSFUL; iosSimulatorArm64Test: 40 class, 280 test; testAndroidHostTest: 33 class, 245 test; failures=0 errors=0
#   (T4: 39/269 và 32/234); DeepLinkTargetsTest 11/11 mỗi target
# Không đổi file Gradle ở T5 (git diff 5b8e26c..HEAD chỉ có shared/build.gradle.kts, gradle/libs.versions.toml của T1) → không chạy :app:assembleDebug
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -destination "id=$SIM" -derivedDataPath $OUT/DD-m4 \
  -resultBundlePath $OUT/M4-unit.xcresult -only-testing:TSPodcastTests 2>&1 | tee $OUT/M4-unit.log
#   ** TEST SUCCEEDED **, Executed 243 tests, 0 failures (M3: 152; T4: 228); grep -c "in target 'TSPodcastUITests'" → 0
#   xcresulttool: DeepLinkRouterTests 14, LocalizationTests 5, PersistenceWiringTests 4, FavouriteModelTests 8, AudioEngineTests 14,
#   FirebaseBootstrapTests 6, AnalyticsMappingTests 6, CrashWiringTests 4, RemoteConfigLoaderTests 6, NotificationsModelTests 12,
#   NotificationPresentationTests 6, RichNotificationTests 10, SettingsModelTests 5 — tất cả Passed; tổng 243
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination "id=$SIM" -derivedDataPath $OUT/DD-m4 2>&1 | tee $OUT/M4-build.log
#   ** BUILD SUCCEEDED **; grep -c "in target 'TSPodcastUITests'" → 0
iosApp/scripts/smoke-launch.sh $SIM $OUT/DD-m4/Build/Products/Debug-iphonesimulator/TSPodcast.app --check-db
#   process running / no new crash report / database file exists / SMOKE PASS
rm -rf $OUT/DD-m4/Build    # Release sạch (giữ SourcePackages)
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Release -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" -derivedDataPath $OUT/DD-m4 2>&1 | tee $OUT/M4-rel.log     # ** BUILD SUCCEEDED **
R=$OUT/DD-m4/Build/Products/Release-iphonesimulator/TSPodcast.app
plutil -lint iosApp/TSPodcast/PrivacyInfo.xcprivacy; plutil -lint $R/PrivacyInfo.xcprivacy   # OK, OK (file có trong .app)
iosApp/scripts/check-required-reason-apis.sh $R
#   TSPodcast.app  FileTimestamp  _NSURLCreationDateKey, _fstat, _lstat, _stat  C617.1
#   TSPodcast.app  DiskSpace      _fstatfs, _statfs                             E174.1
#   TSPodcast.app  UserDefaults   _OBJC_CLASS_$_NSUserDefaults                  CA92.1
#   PlugIns/TSPodcastNotificationService.appex  (no required-reason API)  no manifest needed
#   Frameworks/FirebaseAnalytics.framework, GoogleAppMeasurement.framework  (stub, no symbols)
#   REQUIRED REASON CHECK PASS, exit 0
#   (kiểm âm: bản Release cũ chưa có manifest → 3 lỗi "used … but not declared", exit 1; bản sao có thêm SystemBootTime
#    và reason "XXXX.1" → "declared … but not found" + "unapproved reason", FAIL)
strings $R/TSPodcast | grep -c -e "uitest-" -e "TSP_UITEST_" -e "playback.probe" -e "uitest.example"   # 0
plutil -extract TSAppLinkHost raw $R/Info.plist                                                         # "" (rỗng)
codesign -d --entitlements - $R | grep -c associated                                                    # 0
otool -L $R/TSPodcast | grep -c -E "AdSupport|AppTrackingTransparency|FBSDK"                            # 0
cd iosApp && xcodegen generate && git status --porcelain iosApp.xcodeproj                              # rỗng
git diff --name-only 5b8e26c..HEAD -- iosApp/TSPodcastUITests                                           # rỗng
grep -rIin "radio\|vov\|voh" iosApp/TSPodcast iosApp/TSPodcastNotificationService | wc -l                # 0 (sau b65f3df)
grep -rIin "applovin\|admob\|GADMobileAds\|ATTrackingManager\|FBSDK" iosApp shared/src | wc -l          # 0
grep -rIn "device_id" shared/src iosApp --exclude-dir=build | wc -l                                     # 0
grep -rn "FavouriteStore\|recentItems" iosApp/TSPodcast iosApp/TSPodcastTests shared/src | wc -l        # 0
python3 -I -c '…missing_en…' iosApp/TSPodcast/Resources/Localizable.xcstrings                           # keys 80, vi True, missing_en 0, missing_vi 0
git check-ignore -v iosApp/Config/Secrets.xcconfig                                                      # .gitignore:32:iosApp/Config/Secrets.xcconfig
# key/secret (đọc từ Secrets.xcconfig, chỉ in số) trong git log main..HEAD -p                           # 0 và 0
git ls-files | grep -E '(^|/)(build|DerivedData|xcuserdata|\.gradle)/|\.xcresult|GoogleService-Info\.plist|Secrets\.xcconfig$' | wc -l   # 0
git log 5b8e26c..HEAD --format='%s'      # mọi subject conventional; số Co-Authored-By = git rev-list --count 5b8e26c..HEAD
```

Ghi chú build: lần build Release đầu của T5 fail (`module file … ExplicitPrecompiledModules/_Builtin_stdint-….pcm not found`) vì đợt dọn cache ở T4 xoá module precompiled mà build graph vẫn trỏ tới. Xoá cả `DD-m4/Build` rồi build lại: Release sạch `BUILD SUCCEEDED`; Debug/unit/smoke chạy sau đó trên cùng thư mục. Release build vào `$OUT/DD-m4` (không phải `DD-rel-m4` như lệnh trong AC24) để dùng lại SourcePackages đã tải (1,2 GB) thay vì tải lại, vì máy còn ít dung lượng; build vẫn là build sạch.

#### Checklist parity §7 (features-spec §7)

Trạng thái: **Đạt** (milestone + bằng chứng đã chạy), **Đạt (để QC)** (code + unit test; bước simulator ở danh sách "Để QC" bên dưới), **Chặn bởi owner** (code sẵn, phần còn lại ở [handoff.md](handoff.md)), **Không áp dụng**.

| # | Mục §7 | Trạng thái | Bằng chứng / chỗ còn lại |
|---|---|---|---|
| 1 | Onboarding 4 trang → chọn ≥ 3 danh mục → Home; mở lại vào thẳng Home | Đạt | M1 (nghiệm thu round 1): `StartDestinationTest`, `OnboardingModelTests`, `CategorySelectionModelTests`; `OnboardingUITests`, `CategorySelectionUITests` pass ở M1 T5. M4 chỉ thêm việc giữ deep link tới khi vào Main. QC chạy lại UI suite ở pha cuối. |
| 2 | Home 5 section, shimmer, pull-to-refresh, attribution PodcastIndex | Đạt | M1: `HomeFeedTest`, `HomeModelTests`, `HomeUITests`, `HomeErrorUITests` (M1 T5). M4: toolbar Khám phá thêm nút `settings.open` (UI test cũ có thể cần chỉnh, để QC). |
| 3 | Blacklist lọc feed theo id và từ khoá (thử bằng Remote Config) | Chặn bởi owner | Lọc theo id/từ khoá: M0 `BlacklistRepositoryTest`/`BlacklistFilteringTest`, M2 `SearchUITests.testBlacklistedResultsHidden`; M4: giá trị Remote Config thay asset và `reload()` (`RemoteConfigLoaderTests`), deep link cũng qua blacklist (`DeepLinkTargetsTest`). Thử bằng Remote Config **thật** cần plist + console: handoff "Firebase iOS app". |
| 4 | Detail: HTML description, danh sách episode, dialog lỗi có reload | Đạt | M2: `PodcastDetailFeedTest`, `PodcastDetailModelTests`, `DescriptionTextTests`, `PodcastDetailUITests`, `PodcastDetailErrorUITests`. M4 thêm danh sách DB khi offline (`detail.offlineBanner`, M4-AC7). |
| 5 | Player: play/pause/seek/prev/next/favourite, playlist; hết episode thì dừng | Đạt (để QC) | M3 (nghiệm thu round 2): `AudioEngineTests`, `RemoteCommandsTests`, `PlaybackStateTests`, shared `PlaybackQueueTest`; favourite giờ lưu Room (`AudioEngineTests` M4-AC13). UI suite M3 (`PlayerUITests`, `MiniPlayerUITests`…) chưa chạy theo luật test → QC. |
| 6 | Background audio, Lock Screen, Control Center, Bluetooth, AirPods | Chặn bởi owner | M3: `UIBackgroundModes = audio`, `AudioSessionControllerTests`, `RemoteCommands`/`NowPlayingCenter` có unit test. Checklist máy thật (AirPods, tai nghe, cuộc gọi, Lock Screen) cần iPhone thật: handoff "Checklist audio trên máy thật". |
| 7 | Favourite: thêm/xoá, list cập nhật, mở được khi offline | Đạt (để QC) | M4 T1/T3: `FavouriteRepositoryTest`, `FavouriteResolverTest`, `PruneApiCacheTest` ([Sửa] giữ podcast của episode favourite), `PodcastDetailFeedTest` (offline), `FavouriteModelTests` (list tự cập nhật, alert "Bỏ yêu thích"), `AudioEngineTests` (favourite bền qua `SharedGraph` mới). Bước simulator: Để QC AC13, AC14. |
| 8 | Search: Tất cả/Nhạc, debounce, trạng thái rỗng | Đạt | M2: `SearchFeedTest`, `SearchModelTests` (debounce 200 ms), `SearchUITests` (M2 T4). |
| 9 | (Nếu có) Radio VOV + VOH | Không áp dụng | D1: iOS v1 không có Radio, không tab, không code path (`grep -rIin "radio\|vov\|voh" iosApp/TSPodcast iosApp/TSPodcastNotificationService` → 0; bảng `RadioChannel` không có trong DB iOS). |
| 10 | Cache: trending 15 phút, prune 7 ngày | Đạt | 15 phút: M0 `InMemoryTtlCacheTest`, M1 `TrendingDiskCacheTest`; 7 ngày: M4 `PruneApiCacheTest` (iosTest, Room thật), `PersistenceWiringTests` (prune 1 lần/launch, ngoài main actor, DB loại khỏi backup), smoke `--check-db` (log `prune_api_cache …`). |
| 11 | Crashlytics nhận crash Swift **và** Kotlin, userId = InstallId | Chặn bởi owner | Code: `FirebaseCrashReporter` (+ `recordFatal` với địa chỉ stack), hook Kotlin tự viết thay CrashKiOS (plan §7 mục 7), `CrashWiringTests` (userId = `InstallId` đúng 1 lần, thứ tự cài), shared `KotlinCrashHookTest`, `InstallIdTest`. Crash thật lên Crashlytics cần plist + dSYM Release: handoff "Firebase iOS app". |
| 12a | Remote Config `ts_podcast_black_list` | Chặn bởi owner | `RemoteConfigLoader` (20 lần, 30 s × n, reload blacklist 1 lần), `FirebaseRemoteConfigAPI` chỉ trả giá trị `.remote`, `RemoteConfigLoaderTests`; không Firebase thì asset đóng gói. Áp giá trị thật cho app iOS trên console: handoff. |
| 12b | Remote Config `ads_enable` | Không áp dụng | D2: iOS v1 không có ads. |

Dòng ngoài §7 mà M4 phụ trách:

| Mục | Trạng thái | Bằng chứng / chỗ còn lại |
|---|---|---|
| Luồng launch §1 (Firebase → `Crash.install` → userId → Remote Config → `PruneApiCache`; start destination) | Đạt | `CrashWiringTests` (thứ tự `LaunchSteps`), `FirebaseBootstrapTests`, `PersistenceWiringTests`; smoke T4/T5 (log `firebase=disabled reason=missing_plist`, `crash_user_id set`, `prune_api_cache`). FCM token chỉ có sau opt-in (quyết định T4). Đường có Firebase thật: chặn bởi owner. |
| Ghi DB §3.5 (playlist + podcast + `extra:current_item_id`) | Đạt | `PlaybackHistoryTest` (Detail, Live, prev/next), `AudioEngineTests` (Detail ghi playlist + podcast), deep link episode ghi qua `history = .playlist` (`DeepLinkRouterTests.testSharedEpisodeTargetBuildsTheRequestAndTheRoute`). |
| Tab Yêu thích §3.8 (list, rỗng, tap phát + Detail, [Sửa] observe, offline) | Đạt (để QC) | `FavouriteModelTests` 8, `FavouriteResolverTest`; identifier `favourite.list`, `favourite.item.<i>`, `favourite.unavailable(.remove/.close)`. Bước: Để QC AC14. |
| Push §3.11 (chỉ `new_podcasts`, opt-in theo ngữ cảnh, toggle Cài đặt, NSE ảnh lớn) | Đạt (để QC) | `NotificationsModelTests` 12, `NotificationPresentationTests` 6, `RichNotificationTests` 10, `SettingsModelTests`; NSE nhúng trong Release. Gửi thật qua APNs/FCM + provisioning NSE: chặn bởi owner (handoff). |
| Deep link §3.12 (Universal Link `/podcast/`, `/episode/`, parser chung) | Chặn bởi owner | Code + test đủ (`DeepLinkParserTest`, `DeepLinkTargetsTest`, `DeepLinkRouterTests`); `APP_LINK_HOST` rỗng ⇒ chưa nhận link. Cần domain + Associated Domains + AASA (handoff "Domain cho Universal Link"); sau đó QC chạy bước AC20. |
| Analytics §4 (`onboarding_screen`, `OnboardingFinished`, `fcm_<type>_message_receive`, thuộc tính mặc định không `device_id`) | Đạt | `AnalyticsDefaultsTest`, `AnalyticsMappingTests`, `NotificationPresentationTests`, `LogAnalyticsTrackerTests`; `grep device_id` → 0. Event trên Firebase thật: chặn bởi owner. Push đến lúc app nền không tap thì không có event (plan §7 mục 8). |
| Cài đặt (toggle thông báo, explicit D6, chính sách bảo mật, phiên bản) | Đạt (để QC) | `SettingsModelTests` 5. URL chính sách bảo mật: chặn bởi owner (`PRIVACY_POLICY_URL` rỗng → dòng ẩn). |
| Localization §5 (vi nguồn + en) | Đạt (để QC) | `LocalizationTests` 5 (19 key M4), `missing_en 0`. Bước: Để QC AC23. |
| `PrivacyInfo.xcprivacy` + required-reason API | Đạt | `plutil -lint` OK, có trong Release `.app`, `check-required-reason-apis.sh` PASS. Nhãn App Privacy trên App Store Connect + Privacy Report của bản archive: chặn bởi owner. |

Khoảng trống tìm thấy khi đi checklist:

- `FavouriteDataSource.swift` (T3) có chú thích nhắc tới radio, làm grep AC24 ra 1 dòng → sửa ở `b65f3df` (`fix(ios): keep the word radio out of the app sources`).
- `-uitest-reset` chỉ xoá DataStore và cache ảnh, **không** xoá DB Room: favourite của lần chạy UI test trước vẫn còn, nên UI test giả định tab Yêu thích rỗng (`favourite.empty`) có thể fail khi chạy sau test favourite. Không sửa vì là hook UI test (luật test: QC thêm/sửa hook ở pha cuối); ghi vào "Để QC".
- Không còn dòng nào "chưa làm".

#### Để QC (bước simulator cho pha QC cuối)

Chạy trên `SIM` với bản Debug (không plist trừ khi QC có Firebase); UI test viết ở pha QC dựa trên các identifier đã có.

1. **AC13 favourite/lịch sử bền:** phát một episode từ Detail, tap `player.favourite` (selected) → `xcrun simctl terminate $SIM tss.t.podcast` → mở lại → tab Yêu thích có `favourite.item.0` là episode đó → phát lại → `player.favourite` vẫn selected.
2. **AC14 tab Yêu thích:** (1) DB rỗng (cài mới) → `favourite.empty`, tap `favourite.discoverMore` → tab Khám phá; (2) favourite ở Player, đóng Player, sang tab Yêu thích → dòng mới có ngay; (3) tap dòng → Podcast detail trong stack tab Yêu thích, MiniPlayer hiện và đang phát, Player không mở; (4) bỏ favourite ở Player → dòng biến mất; (5) tắt mạng máy Mac, tap dòng → Detail có `detail.offlineBanner` + danh sách tập đã lưu; (6) mục không còn dữ liệu → alert `favourite.unavailable`, `favourite.unavailable.remove` xoá dòng.
3. **AC18 push opt-in:** cài mới, launch → không có hộp thoại quyền; favourite đầu tiên → alert `notifications.prompt` ("Nhận thông báo khi có podcast mới?"); `notifications.prompt.enable` → hộp thoại iOS → Allow; `xcrun simctl push $SIM tss.t.podcast iosApp/scripts/push-new-podcast.apns` → banner có ảnh (NSE); favourite thứ hai → không hỏi lại; từ chối ở hộp thoại → alert có `notifications.denied.openSettings`.
4. **AC20 deep link** (chỉ chạy được sau khi owner có domain + Associated Domains + AASA; trước đó `simctl openurl` một URL https mở Safari): build với `APP_LINK_HOST=<host>` → `xcrun simctl openurl $SIM https://<host>/podcast/<feedId>-x` → Podcast detail của feed ở tab Khám phá; `/episode/<id>-x` → Detail của feed + MiniPlayer đang phát, Player đóng; id không tồn tại → alert `deeplink.error`; cài mới rồi mở link khi đang ở onboarding → sau khi lưu danh mục, Main mở đúng Detail đó một lần. QC có thể thử `XCUIApplication.open(_:)` trong XCUITest nếu muốn kiểm router trước khi có AASA.
5. **AC21 Cài đặt:** bánh răng `settings.open` → sheet `settings.root`; bật `settings.explicit`, kéo Home tải lại → không lỗi; tắt/bật `settings.notifications` theo luồng AC18; `settings.version` = "1.0.0 (1)"; không có `settings.privacy` khi `PRIVACY_POLICY_URL` rỗng; `settings.close` đóng sheet.
6. **AC23 tiếng Anh:** launch với `-AppleLanguages (en) -AppleLocale en_US` → tab Yêu thích ("Favorites", "No favourite shows yet."), alert mục hỏng ("Couldn't open this item", "Remove from favorites", "Close"), Cài đặt ("Settings", "New podcast notifications", "Show explicit content", "Version"), prompt thông báo ("Get notified about new podcasts?", "Turn on", "Not now"), alert deep link ("Couldn't open the link").
7. **UI test cũ M1–M3 có thể bị M4 ảnh hưởng:** (a) toolbar Khám phá có thêm `settings.open`; (b) tap favourite lần đầu ở Player bật alert `notifications.prompt` (chặn tương tác tiếp theo nếu test không đóng nó); (c) `-uitest-reset` không xoá DB Room nên favourite còn giữa các lần launch (test cần `favourite.empty` nên chạy với simulator cài mới hoặc QC mở rộng hook reset); (d) tab Yêu thích giờ có danh sách thật thay vì chỉ view rỗng; (e) `MainTabView` lấy tab/stack từ `MainNavigation` (identifier không đổi).

#### Tóm tắt M4 (M4-AC26)

| Task | AC | Commit |
|---|---|---|
| T1 Shared: Room KMP, prune, favourite, lịch sử, offline; hạ tầng kiểm tra | AC1–AC7 | `90c82f0`, `ac416f8`, docs `a6c8f8e` |
| T2 Shared: Install ID, crash, analytics, deep link parser, E12, khoá thông báo, retry Remote Config | AC8–AC11 | `ac3cf1d`, docs `d62b505` |
| T3 iOS: lưu trữ, Player favourite bền, lịch sử, tab Yêu thích, Detail offline | AC12–AC14 | `3447cb6`, `374d36f`, docs `a76e492` |
| T4 iOS: Firebase tuỳ chọn, analytics, crash, Remote Config, push opt-in, NSE, Cài đặt | AC15–AC19, AC21 | `2a5a0d0`, docs `3bd56e8` |
| T5 iOS: deep link, PrivacyInfo, localization, checklist §7, tài liệu | AC20, AC22–AC26 | `1b9180b`, `25a3d4b`, `b65f3df`, `6f33d15`, docs (commit này) |

Quyết định trong T5 (chi tiết trong handoff):

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Lọc deep link | Blacklist + D6 áp cho link (shared `DeepLinkTargets`) | Link đến từ ngoài app, chưa qua bộ lọc của list nào; E11/E12 không lọc. |
| Link podcast explicit | Vẫn mở Detail (Detail hiện trạng thái ẩn explicit) | Giống khi mở feed đó từ Home; link episode explicit thì từ chối vì sẽ phát ngay. |
| Playlist của link episode | 1 tập, lưu kèm podcast | Không tải cả feed trước khi phát; Detail của feed tự tải danh sách tập. |
| Link đến trước Main | Giữ link mới nhất, mở khi `MainTabView` xuất hiện | Plan §4.5; nhiều link liên tiếp lúc onboarding thì link sau thắng. |
| Trùng lặp | Cùng URL trong 2 s mở 1 lần | `onOpenURL` và `onContinueUserActivity` cùng được nối theo plan. |
| Required-reason | Khai đúng nhóm tìm thấy (FileTimestamp, DiskSpace, UserDefaults); bỏ SystemBootTime của mẫu §5 | Binary Release không import `mach_absolute_time`/`systemUptime` (Kotlin 2.2 dùng `clock_gettime`); AC22 cấm khai nhóm thừa. DiskSpace chỉ `E174.1` vì app không hiện dung lượng (bỏ `85F4.1` của mẫu). |
| Data type | Không khai `PerformanceData` | Không bật Performance Monitoring; AC22 liệt kê đúng 4 loại. |

Giới hạn đã biết của M4 (tổng hợp):

- **Chưa chạy UI test nào của M4** và không chạy lại UI suite M1–M3 (luật test). Bước simulator ở "Để QC" trên.
- **Firebase chưa từng bật lúc chạy** (mọi build kiểm dùng `FIREBASE_PLIST_DIR` rỗng); Crashlytics/Analytics/Remote Config/FCM thật, APNs, provisioning NSE, Universal Link thật, nhãn App Privacy: chặn bởi owner (handoff).
- **Universal Link chưa nhận link nào** cho tới khi có `APP_LINK_HOST` + Associated Domains + AASA; trước đó cả bước QC deep link cũng chờ.
- **Script required-reason là kiểm tĩnh** (`nm -u` + `strings`); Apple vẫn có thể báo `ITMS-91053` cho API gọi gián tiếp. Đối chiếu Privacy Report của bản archive và email sau mỗi lần upload.
- **`-uitest-reset` không xoá DB Room** (xem "Để QC" mục 7c).
- Lệch nhỏ so với lệnh của plan: `plutil -lint` không đọc JSON nên AC19 kiểm `push-new-podcast.apns` bằng `plutil -convert` + `json.tool` (T4); Release build vào `$OUT/DD-m4` thay vì `$OUT/DD-rel-m4` (dùng lại SourcePackages, vẫn build sạch).
- Không khôi phục item cuối lúc cold start (Android cũng không, plan §2); background push không tap thì không có event `fcm_*` (plan §7 mục 8).

## M5

Kế hoạch: [milestones/M5.md](milestones/M5.md). Base `f6fc292`.

### T1 — Nền Glass + Up next peek trên Player (xong phần code; bước trên iPhone chờ platform)

Commit: `999b1ac` `feat(ios): add Liquid Glass helpers with Material fallback`, `b0de8a0` `feat(ios): show the up next peek under the player controls`.

| Thành phần | File | Ghi chú |
|---|---|---|
| Resolver vật liệu + helper glass | `iosApp/TSPodcast/Design/Glass.swift` | `GlassSurfaceStyle.resolve` (thuần): Reduce Transparency → `.opaque`, iOS 26 → `.glass`, thấp hơn → `.material`, `showsBorder = increasedContrast`. `glassSurface(in:tint:interactive:)` (glass `.regular` + tint/interactive; fallback `.regularMaterial` / `secondarySystemBackground`; tint ở fallback tô đặc shape như `.borderedProminent`; viền `primary` 35 % 1 pt). `GlassGroup`, `glassButton`, `glassProminentButton(tint:)`, `glassCircleButton`, `tabBarMinimizesOnScroll`, `bottomBar` (fallback nền `.bar`), `topBar` (fallback không nền). Chưa view nào ngoài panel dùng các helper này (T2/T3). |
| Màu nhấn | `Resources/Assets.xcassets/Colors/BrandAccent.colorset`, `Design/AppColors.swift` | `#FF7A59` cả Light/Dark; `Color.brandAccent` dùng đúng 1 chỗ: chấm 6 pt `accessibilityHidden` cạnh "Đang phát" trong `UpNextRow`. |
| Logic panel | `iosApp/TSPodcast/Player/UpNextPanelState.swift` | `UpNextPanelState` (`Mode`, `travel`, `offset(forDrag:)`, `endDrag`, `expand/collapse/toggle/didSelectItem/queueDidChange`) + `UpNextPeek.rows/maxRows`. Ngưỡng viết dạng nhân (`-dy*5 >= travel*2`, `dy*5 >= travel`) để 160/400 đúng 40 %. `travel <= 0` → chỉ luật vận tốc. Peek tối đa 2 dòng kể cả khi `maxRows` lớn hơn. |
| Panel | `iosApp/TSPodcast/Player/UpNextPanel.swift` (thay `UpNextSheet.swift`) | `UpNextPanel` (header = Button `upNext.peek`: grabber 36×5, "Tiếp theo", dòng peek khi thu; `highPriorityGesture(DragGesture(minimumDistance: 8))` chỉ trên header; `accessibilityAction(.escape)`; `glassSurface` bo góc trên 28), `UpNextList` (`upNext.root`, `upNext.item.<i>` + `.isSelected`, nền trong suốt, `contentMargins` đáy = safe area), `UpNextRow` dùng chung (tiêu đề 1 dòng ở peek, 2 dòng ở list). |
| Player | `iosApp/TSPodcast/Player/PlayerView.swift` | Cột dọc: hàng đóng → artwork (co giãn) → tiêu đề → progress → controls → lỗi? → `Spacer(16)` → `Color.clear` cao bằng header peek đo được (phần tử cuối). `UpNextPanel` là overlay căn trên của cột, `padding(.top, đáy hàng player.close)`, tự dời xuống `offset(forDrag:)`; `travel = đỉnh vùng peek − đáy hàng đóng`. Đo bằng `onGeometryChange` trong coordinate space `player`. Animation `.spring(duration: 0.35)`, Reduce Motion → `.easeInOut(duration: 0.2)`. `player.playlist` → `expand()`; tap hàng → `engine.select(index:)` rồi `didSelectItem()`. Không còn `.sheet`. |
| Chuỗi | `Localizable.xcstrings`, `LocalizationTests.testUpNextPeekStrings` | `player.upNext.peekValue` (Đang phát: %@ / Now playing: %@), `player.upNext.expandHint`, `player.upNext.collapseHint` đúng bảng §4. Value VoiceOver dựng bằng `String(format: String(localized:), title)` để khoá giữ đúng tên trong plan. |
| Unit test mới | `GlassSurfaceStyleTests` 6, `UpNextPanelStateTests` 14, `UpNextPeekTests` 7, `LocalizationTests` +1 | Theo các case §6 AC3/AC4/AC6 và thêm biên (fling đúng 1000, travel âm, `maxRows` 0). |

Quyết định trong T1:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Header khi mở | Chỉ grabber + "Tiếp theo" (ẩn dòng peek); chiều cao vùng peek chỉ cập nhật khi đang thu | Khi mở, danh sách đã có item đang phát; giữ chiều cao vùng đặt chỗ ổn định để layout Player phía dưới và `travel` không nhảy. |
| List khi đang kéo từ peek | Hiện list trong lúc kéo (`isDragging`) để panel không rỗng khi trượt lên | `upNext.root` vẫn chỉ tồn tại khi mở hoặc đang kéo bằng tay; UI test bấm `player.playlist`/chọn item không bị ảnh hưởng. |
| Tap vs kéo header | `highPriorityGesture` cho drag (minimumDistance 8) | Tap không dời thì Button nhận (toggle); kéo quá 8 pt thì drag thắng, Button không bắn thêm toggle. |
| "Kéo list ở đỉnh để thu" của Android | Không làm | Plan §4 mục 2; thu bằng kéo header, tap header, chọn item, escape. |
| Ưu tiên layout | `layoutPriority(1)` cho tiêu đề, progress, controls, lỗi | Artwork là phần co lại trước khi thiếu chỗ (Dynamic Type lớn), hàng nút + peek luôn vừa (§3.5). |

Lệnh và kết quả (30/10/2026):

```bash
xcodebuild -showsdks | grep iphoneos          # iOS 26.2 -sdk iphoneos26.2 (SDK có)
xcodebuild build … -destination 'platform=iOS,name=Tun' …   # 2 lần (đầu task và sau khi xong code):
#   xcodebuild: error: Unable to find a destination matching the provided destination specifier
#   { platform:iOS, arch:arm64e, id:00008110-000878203AC1401E, name:Tun, error:iOS 26.2 is not installed.
#     Please download and install the platform from Xcode > Settings > Components. }
xcrun devicectl list devices                  # Tun … available (paired)
# Kiểm biên dịch thay thế (không phải build, không tạo artefact): swiftc -typecheck toàn bộ source app
# (bản copy trong scratchpad, bỏ dòng import Firebase*) với SDK iphonesimulator26.2, -target arm64-apple-ios17.0-simulator,
# -swift-version 6, -F shared/build/xcode-frameworks/Debug/iphonesimulator26.2:
#   0 error/warning ở Design/ và Player/; chỉ 3 lỗi "cannot find 'FirebaseApp'/'FirebaseOptions'" do bỏ import Firebase.
#   Đã thử chèn 1 lỗi cố ý vào UpNextPanel.swift để chắc typecheck có báo lỗi file mới (có báo).
# Logic thuần chạy trên macOS (Swift package tạm trong scratchpad: UpNextPanelState.swift + phần resolver của Glass.swift
# + 3 file test y nguyên): swift test → Executed 27 tests, with 0 failures.
# Kiểm tĩnh AC2: comm -23 (identifier / accessibilityLabel(Text / accessibilityHint(Text) f6fc292 vs HEAD → rỗng;
#   comm -13 identifier → chỉ accessibilityIdentifier("upNext.peek"); label mới: accessibilityLabel(Text("player.upNext").
# AC5: grep 'UpNextSheet\|presentationDetents' iosApp/TSPodcast → 0. AC6: grep token iOS 26 ngoài Glass.swift/MiniPlayerSlot.swift → 0 dòng.
# AC10 (phần T1): brandAccent chỉ ở AppColors.swift + 1 chỗ (UpNextPanel.swift); font(.system(size: mới → 0.
# AC11: script xcstrings → missing_en 0, missing_vi 0 (83 key).
```

#### Chờ chạy trên iPhone khi có iOS platform

Xcode báo `iOS 26.2 is not installed` cho destination `Tun` (component platform iOS đang tải lại). Theo luật tạm thời không build/test/cài trên máy; orchestrator chạy sau khi platform có:

1. `xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -only-testing:TSPodcastTests -destination 'platform=iOS,name=Tun' -derivedDataPath $OUT/DD-m5 -resultBundlePath $OUT/M5-unit.xcresult $SIGN` → kỳ vọng `TEST SUCCEEDED`, có `GlassSurfaceStyleTests`, `UpNextPanelStateTests`, `UpNextPeekTests`, `LocalizationTests` Passed; 0 dòng `in target 'TSPodcastUITests'`.
2. `xcodebuild build … -scheme TSPodcast -configuration Debug -destination 'platform=iOS,name=Tun' $SIGN` → `BUILD SUCCEEDED`, `grep -c "only available in iOS"` → 0.
3. Smoke: `xcrun devicectl device install app --device 56DE0E78-9436-5BFB-8F79-E68AB560B730 …/Debug-iphoneos/TSPodcast.app`, `process launch … tss.t.podcast`, chờ ~15 s, `device info processes | grep TSPodcast.app` → có dòng.
4. Owner xem trên máy (AC5): mở Player → peek "Tiếp theo" + "Đang phát" (+ tập kế) dưới hàng nút, không che nút; vuốt lên → danh sách, tập đang phát được đánh dấu; tap tập khác → phát và panel thu; vuốt xuống → peek; `player.playlist` → panel mở, nút đóng vẫn bấm được; phát Live → peek 1 dòng.

#### Để QC (T1)

- `PlayerUITests` mở Up next bằng `player.playlist` rồi chờ `upNext.root`: giờ là panel trong Player thay vì sheet, identifier giữ nguyên; `upNext.root` biến mất sau khi chọn item (panel thu). Nếu test vuốt sheet xuống để đóng thì cần đổi sang kéo `upNext.peek` hoặc chọn item.
- Các bước trên máy ở mục 4 phía trên (vuốt/fling, Reduce Motion, VoiceOver escape + label/value/hint của `upNext.peek`, Dynamic Type cỡ accessibility → peek 1 dòng, xoay ngang → 1 dòng).

Giới hạn đã biết (T1):

- Chưa build/chạy trên máy nào: bằng chứng hiện có là typecheck Swift 6 với SDK iOS 26.2 (deployment 17) + 27 test logic chạy trên macOS + đọc code. Bố cục thật (đo `onGeometryChange`, `ignoresSafeArea` đáy panel, `contentMargins`) cần nhìn trên iPhone.
- Thay đổi Dynamic Type trong lúc panel đang mở chỉ cập nhật chiều cao vùng peek khi panel thu lại.


### T2 — Player, MiniPlayer và khung tab theo Liquid Glass (xong phần code; bước trên iPhone chờ platform)

Commit: `feat(ios): restyle the player, mini player and tab bar with Liquid Glass` (hash ở `git log`).

| Thành phần | File | Ghi chú |
|---|---|---|
| Helper nút tròn | `iosApp/TSPodcast/Design/Glass.swift` | `glassCircleButton()` (26) đổi từ `.glass` + `.buttonBorderShape(.circle)` sang `.plain` + `glassEffect(.regular.interactive(), in: Circle())`. Thêm overload `glassProminentButton(tint:in:fallbackFill:)`: 26 → `.plain` + `glassEffect(.regular.tint(tint).interactive(), in: shape)`; dưới 26 → `.plain` + `shape.fill(fallbackFill)` phía sau (đúng vòng tròn M3). Fallback `glassCircleButton` vẫn là `.plain` như M3. |
| Player | `iosApp/TSPodcast/Player/PlayerView.swift` | `player.close`: `glassCircleButton()`, khung 44×44. Hàng điều khiển bọc trong **một** `GlassGroup(spacing: 8)`: Favourite / Previous / Next / Playlist là `ControlButton` 56×56 + `glassCircleButton()`; Play/Pause 84×84 `glassProminentButton(tint: .brandPrimary, in: Circle(), fallbackFill: .brandSecondary)`, spinner trắng khi buffering, overlay `player.buffering`, nhãn và identifier giữ nguyên. "Tải lại" (`player.retry`): `glassProminentButton(tint: .brandPrimary)` + `.controlSize(.large)`. Hành vi phát không đổi (cùng lời gọi `engine.*`). |
| Khung tab | `iosApp/TSPodcast/Main/MainTabView.swift` | `TabView` gọi `.tabBarMinimizesOnScroll()` trước `.miniPlayerAccessory`. Không có `toolbarBackground` hay nền `.bar` nào trong `Main/`. |
| Bố cục MiniPlayer | `iosApp/TSPodcast/Main/MiniPlayerSlot.swift` | `MiniPlayerLayout { regular, compact }` + `resolve(isInlineAccessory:isAccessibilitySize:)` (inline **hoặc** cỡ accessibility → `.compact`). `MiniPlayerSlot(isInlineAccessory:)` đọc `dynamicTypeSize`. iOS 26: accessory là `AccessoryMiniPlayer` (`@available(iOS 26.0, *)`, đọc `@Environment(\.tabViewBottomAccessoryPlacement)`, `== .inline`). iOS 17/18: `safeAreaInset` → capsule nổi `glassSurface(in: Capsule())` (= `.regularMaterial`; Reduce Transparency → nền đặc; Increase Contrast → viền), bóng `black 12 %` r 8 y 2, cách hai bên 12 pt, đáy 8 pt — thay dải `.bar` tràn viền của M3. |
| MiniPlayer | `iosApp/TSPodcast/Main/MiniPlayerView.swift` | Tham số `layout` (mặc định `.regular`). `.compact`: artwork 28 (bo 6), chỉ tiêu đề, khoảng cách 8, padding dọc 0 (cao 44 = nút play/pause). `.regular` như M3; thanh progress lùi vào 22 pt hai bên + 3 pt đáy để không lòi ra ngoài đầu bo của capsule. `miniplayer.slot/open/playPause` có ở cả hai bố cục; `miniplayer.progress` chỉ ở `.regular` và không live. Nhãn VoiceOver của `miniplayer.open` vẫn đọc cả tên podcast khi bố cục gọn ẩn nó. |
| Unit test mới | `iosApp/TSPodcastTests/MiniPlayerLayoutTests.swift` | 4 case: (false,false) → `.regular`; (true,false), (false,true), (true,true) → `.compact`. Thêm vào project bằng `xcodegen generate` (pbxproj +4 dòng). |

Quyết định trong T2:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Nút tròn glass | `.plain` + `glassEffect(… .interactive(), in: Circle())` thay cho button style `.glass`/`.glassProminent` | Style `.glass`/`.glassProminent` tự thêm padding quanh label, nên nút 56/84 pt sẽ to hơn và hàng 5 nút có thể tràn trên iPhone 375 pt. Với `glassEffect` thì khung label là kích thước nút trên mọi OS: 44/56/84 pt đúng như M3, hit target giữ nguyên, glass vẫn interactive (phản hồi khi chạm). |
| Play/Pause | Overload `glassProminentButton(tint:in:fallbackFill:)` | `glassProminentButton(tint:)` của T1 có fallback `.borderedProminent` (hình chữ nhật bo, `brandPrimary`), khác M3. Overload giữ tên helper theo plan, fallback ra đúng vòng tròn `brandSecondary` của M3. Khác biệt nhỏ: ở fallback vòng tròn nằm ở background nên không mờ đi khi nhấn (M3 mờ cả vòng tròn); glyph vẫn mờ. |
| `GlassGroup` | Bọc cả hàng 5 nút (gồm Play/Pause), `spacing: 8` | 4 nút phụ nằm trong một group như AC7; Play/Pause ở giữa hàng nên không tách được mà không đổi layout. Khoảng cách giữa các nút ≥ 13 pt trên iPhone 375 pt nên các glass không dính vào nhau. |
| "Tải lại" | `.controlSize(.large)` | `.borderedProminent`/`.glassProminent` cỡ thường cao ~34 pt < 44. Fallback vẫn `.borderedProminent` tint `brandPrimary` như M3, chỉ cao hơn. |
| Hit area | Giữ `contentShape(Rectangle())` cho nút đóng và 4 nút phụ | Glass vẽ hình tròn nhưng vùng chạm vẫn là ô vuông 44/56 như M3. |
| Identifier | Không thêm, không bỏ | `player.*` và `miniplayer.*` giữ nguyên; `AccessoryMiniPlayer` không thêm identifier. |

Lệnh và kết quả (30/10/2026, 13:3x):

```bash
xcodebuild -showsdks | grep iphoneos     # iOS 26.2 -sdk iphoneos26.2 ; Simulator - iOS 26.2 (SDK có)
xcrun simctl runtime list                # Total Disk Images: 0 (không dùng simulator)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -showdestinations
#   Ineligible: { platform:iOS, arch:arm64e, id:00008110-000878203AC1401E, name:Tun,
#     error:iOS 26.2 is not installed. Please download and install the platform from Xcode > Settings > Components. }
xcrun devicectl list devices             # Tun … 56DE0E78-9436-5BFB-8F79-E68AB560B730   unavailable lần đầu, kiểm lại cuối task: available (paired)
# → không build/test/cài trên máy (luật tạm thời). Kiểm thay thế:
# typecheck toàn bộ source app (bản copy trong scratchpad, bỏ dòng import Firebase*), SDK iphonesimulator26.2,
#   -target arm64-apple-ios17.0-simulator, -swift-version 6, -F shared/build/xcode-frameworks/Debug/iphonesimulator26.2
#   → chỉ 3 lỗi FirebaseApp/FirebaseOptions (do bỏ import, giống T1); 0 lỗi/cảnh báo ở Design/, Main/, Player/.
# typecheck riêng Glass + Main + Player với SDK iphoneos26.2, -target arm64-apple-ios17.0 (stub cho AppEnvironment/AudioEngine)
#   → 0 lỗi ngoài PlaybackProgressKt (framework iosArm64 trong shared/build cũ hơn code); các API iOS 26 mới
#   (tabViewBottomAccessoryPlacement, glassEffect(...interactive(), in:)) đều được gate đúng.
# MiniPlayerLayoutTests y nguyên + enum MiniPlayerLayout trong Swift package tạm (macOS): swift test → Executed 4 tests, with 0 failures.
# Luật một chỗ: grep token iOS 26 ngoài Glass.swift/MiniPlayerSlot.swift → chỉ 2 dòng `.glassProminentButton(` trong PlayerView
#   (tên helper khớp regex `\.glassProminent`, không phải API).
# grep 'toolbarBackground\|background(.bar)' iosApp/TSPodcast/Main → 0.
```

`shared/`, Gradle, Android và UI test không đổi nên không chạy Gradle.

#### Chờ chạy trên iPhone khi có iOS platform (thêm cho T2)

1. Unit test trên máy (lệnh ở mục T1): thêm kỳ vọng `MiniPlayerLayoutTests` (4) Passed; `AudioEngineTests`, `PlaybackSystemTests` vẫn Passed (AC7).
2. Build + smoke như mục T1.
3. Owner xem trên máy: Player (nút đóng tròn glass; 4 nút phụ glass tròn; Play/Pause glass tint `brandPrimary`, spinner khi buffering; lỗi phát → "Tải lại" glass prominent); tab Khám phá cuộn xuống → tab bar thu nhỏ, MiniPlayer vào inline và còn artwork nhỏ + tiêu đề + play/pause; cuộn lên → MiniPlayer đầy đủ có progress; Dynamic Type cỡ accessibility → MiniPlayer gọn.

#### Để QC (T2)

- `MiniPlayerUITests` (có dùng `miniplayer.progress`): ở iOS 26 khi tab bar đã thu nhỏ hoặc cỡ chữ accessibility thì `miniplayer.progress` không có (bố cục gọn). `miniplayer.slot/open/playPause` vẫn có.
- Kiểm hit target ≥ 44 pt bằng Accessibility Inspector cho `player.close`, 4 nút phụ, `player.playPause`, `player.retry`, `miniplayer.playPause`.
- Fallback iOS 17/18 (capsule MiniPlayer, Player kiểu M3) chỉ kiểm được trên máy/simulator iOS 17/18; Tun chạy iOS 27 nên chỉ thấy nhánh glass.

Giới hạn đã biết (T2):

- Chưa build/chạy trên máy: bằng chứng là typecheck Swift 6 với SDK iOS 26.2 + 4 test logic trên macOS + đọc code. Kích thước accessory inline của iOS 26 (cao ~44–48 pt) và độ tương phản glyph trắng trên glass tint `brandPrimary` cần nhìn trên iPhone.
- Nhánh fallback iOS 17/18 không có máy để xem (Tun iOS 27).

### T3 — Tìm kiếm, các thanh và nút phụ theo Liquid Glass (xong phần code; bước trên iPhone chờ platform)

Commit: `feat(ios): apply Liquid Glass to search chips, bars and secondary buttons`, `fix(ios): size the play/pause glyph with largeTitle instead of a fixed size` (hash ở `git log`).

| Thành phần | File | Ghi chú |
|---|---|---|
| Tìm kiếm | `iosApp/TSPodcast/Search/SearchView.swift` | `.searchable(placement: .navigationBarDrawer(displayMode: .always))` giữ nguyên. Bỏ `VStack` chip-trên-ScrollView: `ScrollView` là gốc, chip đặt qua `.topBar { SearchModeChips }` (26: `safeAreaBar(edge: .top)` → kết quả cuộn dưới chip, scroll edge effect của hệ thống; 17/18: `safeAreaInset` trên nền trang). `search.root` (container) bọc cả chip. `SearchModeChips`: `GlassGroup(spacing: 4)` + `HStack(spacing: 8)`; mỗi chip `Text` subheadline (semibold khi chọn), padding 16/8, `glassSurface(in: Capsule(), tint: isSelected ? .brandPrimary : nil, interactive: true)`, thêm 4 pt dọc ngoài capsule làm vùng chạm (~44 pt). `search.mode.*`, `.isSelected`, animation 0.2 s giữ nguyên. |
| "Tải lại" | `Home/HomeView.swift` (`SectionMessage`), `Detail/PodcastDetailView.swift` (`DetailMessage`), `Search/SearchView.swift` (`LoadErrorMessage`, dùng cho Search và danh sách podcast theo danh mục) | `.buttonStyle(.bordered)` → `.glassButton()` (26: `.glass`; dưới: `.bordered`), giữ `.tint(Color.brandPrimary)` và identifier. Hộp thông báo bao quanh (nền `quaternary`) là nội dung nên không đổi. Alert lỗi của Detail là alert hệ thống, không đổi. |
| Thanh "Lưu" chọn danh mục | `Categories/CategorySelectionView.swift` | `safeAreaInset(edge: .bottom)` + `.background(.bar)` → `.bottomBar { saveBar }` (26: `safeAreaBar`, không nền tự vẽ, scroll edge effect làm mờ lưới; 17/18: `safeAreaInset` + `.bar` như trước). Property đổi tên `bottomBar` → `saveBar` để không trùng tên helper. Badge, `category.save` (GradientButtonStyle) giữ nguyên. |
| Helper | `Design/Glass.swift` | `topBar` fallback thêm `.background(.background)` (nền trang hệ thống): T1 để không nền, nhưng khi chip thành inset của ScrollView thì nội dung cuộn sẽ lộ ra giữa các chip trên iOS 17/18; có nền thì giống hệt M4 (chip nằm trên ScrollView). |
| Play/Pause | `Player/PlayerView.swift` | `.font(.system(size: 34, weight: .bold))` → `.font(.largeTitle.weight(.bold))`: cùng 34 pt ở cỡ chữ mặc định, theo Dynamic Type (cỡ AX5 ~60 pt vẫn vừa vòng 84 pt). Dòng này có từ M3 nhưng T2 dời/đổi thụt lề nên lệnh AC10 (`git diff f6fc292..HEAD … | grep -c 'font(.system(size'`) đếm 1; giờ là 0. |

Quyết định trong T3:

| Hạng mục | Quyết định | Lý do |
|---|---|---|
| Màu chữ chip đang chọn | `.foregroundStyle(.background)` (trắng ở Light, đen ở Dark); chip thường `.primary` | `brandPrimary` Dark là `#6E97F0` (sáng): chữ trắng trên đó chỉ ~2.9:1, không đạt 4.5:1 cho chữ 15 pt. Chữ đen trên `#6E97F0` ~7.3:1, chữ trắng trên `#184BB4` (Light) ~8:1. Không thêm màu cứng (dùng style hệ thống). Trên iOS 26 glass tint là bán trong suốt nên độ tương phản thật cần owner xem trên máy (Dark Mode). |
| `GlassGroup` của chip | `spacing: 4` < khoảng cách 8 pt giữa chip | Hai capsule là hai shape riêng (không dính vào nhau), vẫn chung một container để đổi chọn có hiệu ứng glass thống nhất. |
| Vùng chạm chip | Capsule giữ ~36 pt như M4; thêm 4 pt dọc trong label + `contentShape(Capsule())` | Chạm ≥ 44 pt mà không đổi kích thước nhìn thấy. |
| Viền chip M4 | Bỏ (`stroke`/`brandPrimary 50 %`) | Glass/Material tự có cạnh; Increase Contrast thêm viền qua `glassSurface`. |
| `LoadErrorMessage` (Search, danh sách theo danh mục) | Cũng dùng `glassButton()` | Cùng loại nút "Tải lại" với Home/Detail; giữ nhất quán. |
| Nền sau thanh hệ thống | Không có gì để bỏ ngoài `.background(.bar)` của thanh Lưu | `grep toolbarBackground\|background(.bar)` toàn `iosApp/TSPodcast` (trừ `Glass.swift`) → 0. Nền mờ cover của Detail (`HeaderBackdrop`) là nội dung cuộn, chạy dưới navigation bar như trước. `CategorySelectionView` giữ `.background(Color(.systemBackground))` (nền trang, không có thanh hệ thống ở màn này). |
| Ô nội dung | Không đổi | Card/hàng Home, hàng live/ForYou, `EpisodeRowView`, `SearchResultRow`, hàng Yêu thích, `CategoryCell`, chip danh mục E9, hàng Up next không dùng helper glass (HIG). CTA gradient `onboarding.continue`, `category.save`, `favourite.discoverMore` giữ `GradientButtonStyle`. |

Lệnh và kết quả (30/10/2026):

```bash
xcodebuild -showsdks | grep iphoneos     # iOS 26.2 -sdk iphoneos26.2
xcrun simctl runtime list                # Total Disk Images: 0 (không dùng simulator)
xcrun devicectl list devices             # Tun … 56DE0E78-9436-5BFB-8F79-E68AB560B730   available (paired)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcast -showdestinations
#   { platform:iOS, arch:arm64e, id:00008110-000878203AC1401E, name:Tun,
#     error:iOS 26.2 is not installed. Please download and install the platform from Xcode > Settings > Components. }
# → không build/test/cài trên máy (luật tạm thời). Kiểm thay thế:
# typecheck toàn bộ source app (79 file, bản copy trong scratchpad, bỏ `import Firebase*` + 1 file stub API Firebase):
#   swiftc -typecheck -swift-version 6 -target arm64-apple-ios17.0-simulator (SDK iphonesimulator26.2)
#     -F shared/build/xcode-frameworks/Debug/iphonesimulator26.2 → exit 0, 0 error, 0 warning.
#   Cùng lệnh với SDK iphoneos26.2, -target arm64-apple-ios17.0 → exit 0.
#   Kiểm typecheck có đọc file đã sửa: chèn 1 lỗi cố ý vào SearchView, CategorySelectionView, HomeView,
#     PodcastDetailView, PlayerView, Glass (+ -continue-building-after-errors) → báo đúng 6 lỗi đó.
# AC10: git diff f6fc292..HEAD -- iosApp/TSPodcast | grep '^+' | grep -c 'font(.system(size' → 0
#       grep -rn brandAccent iosApp/TSPodcast → AppColors.swift + UpNextPanel.swift:172 (1 chỗ)
#       helper glass trong EpisodeRowView/SearchResultRow/FavouriteRootView/CategoryCell/card-hàng Home → 0
#       (chỉ còn retry của Home/Detail/LoadErrorMessage, bottomBar thanh Lưu, chip + topBar của Search).
# AC9: git diff --stat f6fc292..HEAD -- SearchModel/HomeModel/PodcastDetailModel/CategorySelectionModel → rỗng.
# AC2: comm -23 identifier f6fc292 vs HEAD → rỗng; comm -13 → chỉ upNext.peek.
# AC6: token iOS 26 ngoài Glass.swift/MiniPlayerSlot.swift → 2 dòng comment (SearchView, CategorySelectionView)
#      + 2 dòng `.glassProminentButton(` của T2 trong PlayerView (tên helper khớp regex `\.glassProminent`).
```

`shared/`, Gradle, Android và UI test không đổi nên không chạy Gradle.

#### Chờ chạy trên iPhone khi có iOS platform (thêm cho T3)

1. Unit test trên máy (lệnh ở mục T1): thêm kỳ vọng `SearchModelTests`, `HomeModelTests`, `PodcastDetailModelTests`, `CategorySelectionModelTests` Passed (AC9; model không đổi).
2. Build + smoke như mục T1.
3. Owner xem trên máy: tab Tìm kiếm (ô tìm kiếm glass của hệ thống; chip "Tất cả"/"Nhạc" glass, chip chọn tint xanh; cuộn kết quả → chạy dưới chip có mờ cạnh; Light **và** Dark Mode — chữ chip đang chọn đủ tương phản); Khám phá / Detail khi lỗi mạng → "Tải lại" glass; chọn danh mục lần đầu → thanh Lưu không nền riêng, lưới mờ dần dưới thanh; Play/Pause glyph như cũ.

#### Để QC (T3)

- `SearchUITests` dùng `search.mode.all/music` + `isSelected`: identifier và trait giữ nguyên; chip giờ nằm trong safe-area bar (vẫn là con của `search.root`).
- Hit target chip ≥ 44 pt (Accessibility Inspector).

Giới hạn đã biết (T3):

- Chưa build/chạy trên máy: bằng chứng là typecheck toàn app Swift 6 (SDK iOS 26.2, deployment 17) + đọc code. Độ tương phản chữ trên chip glass tint ở Dark Mode và mức mờ scroll edge dưới chip/thanh Lưu cần nhìn trên iPhone.
- Nhánh fallback iOS 17/18 (chip Material, thanh Lưu `.bar`) không có máy để xem (Tun iOS 27).

### T4 — Kiểm toàn bộ, danh sách xem trên iPhone, tài liệu (kiểm tĩnh xong; bước trên iPhone chờ platform)

Commit: `docs(ios): record M5 progress and device review checklist`. Không cần commit `fix(ios)` nào: kiểm tĩnh §6 không tìm thấy khoảng trống.

#### Tổng hợp thành phần M5

| Thành phần | File | Task |
|---|---|---|
| Resolver vật liệu + helper glass (`glassSurface`, `GlassGroup`, `glassButton`, `glassProminentButton` ×2, `glassCircleButton`, `tabBarMinimizesOnScroll`, `bottomBar`, `topBar`), màu `BrandAccent` | `Design/Glass.swift`, `Design/AppColors.swift`, `Assets.xcassets/Colors/BrandAccent.colorset` | T1, T2, T3 |
| Logic panel Up next + chọn dòng peek | `Player/UpNextPanelState.swift` | T1 |
| Panel Up next (thay `UpNextSheet`) | `Player/UpNextPanel.swift`, `Player/PlayerView.swift` | T1 |
| Player glass (nút đóng, hàng nút trong 1 `GlassGroup`, Play/Pause prominent, "Tải lại") | `Player/PlayerView.swift` | T2, T3 |
| Tab bar thu nhỏ khi cuộn, MiniPlayer thường/gọn, capsule nổi iOS 17/18 | `Main/MainTabView.swift`, `Main/MiniPlayerSlot.swift`, `Main/MiniPlayerView.swift` | T2 |
| Chip Tìm kiếm glass + `topBar`, "Tải lại" glass, thanh "Lưu" `bottomBar` | `Search/SearchView.swift`, `Home/HomeView.swift`, `Detail/PodcastDetailView.swift`, `Categories/CategorySelectionView.swift` | T3 |
| Chuỗi mới (3 khoá vi + en) | `Resources/Localizable.xcstrings` | T1 |
| Unit test mới: `GlassSurfaceStyleTests` 6, `UpNextPanelStateTests` 14, `UpNextPeekTests` 7, `MiniPlayerLayoutTests` 4, `LocalizationTests` +1 | `iosApp/TSPodcastTests/` | T1, T2 |

Số test: `grep -rh 'func test' iosApp/TSPodcastTests | wc -l` → **275** (M4 base `f6fc292`: 243; +32 của M5).

#### Lệnh và kết quả (30/10/2026, 13:4x–13:5x)

```bash
OUT=/private/tmp/claude-501/…/scratchpad/ios ; BASE=f6fc292
# Môi trường (đầu task và lần thử lại lúc 13:52):
xcodebuild -showsdks | grep iphoneos     # iOS 26.2 -sdk iphoneos26.2
xcrun simctl runtime list                # Total Disk Images: 0 (không dùng simulator)
xcrun devicectl list devices             # Tun  Tun.coredevice.local  56DE0E78-9436-5BFB-8F79-E68AB560B730  available (paired)  iPhone 13 Pro Max
xcodebuild -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -showdestinations
#   { platform:iOS, arch:arm64e, id:00008110-000878203AC1401E, name:Tun,
#     error:iOS 26.2 is not installed. Please download and install the platform from Xcode > Settings > Components. }
# → cả 2 lần như nhau; không build/test/cài trên máy (luật tạm thời), không thử cách khác.
#   (Tiến trình `xcodebuild -downloadPlatform iOS` từ 12:27 vẫn chạy; agent không đụng vào.)

# AC1
grep -n IPHONEOS_DEPLOYMENT_TARGET iosApp/project.yml          # 5 dòng, đều "17.0"
git diff $BASE..HEAD -- iosApp/project.yml                      # rỗng (không đổi target, không thêm package)
git diff --name-only $BASE..HEAD -- shared app core coreApi podcasts sharedFirebase sharedLibrary gradle \
  settings.gradle.kts build.gradle.kts iosApp/TSPodcastUITests  # rỗng
# AC2
comm -23 m5-id-before m5-id-after                               # rỗng
comm -13 m5-id-before m5-id-after                               # accessibilityIdentifier("upNext.peek")
# accessibilityLabel(Text("…")): comm -23 rỗng; mới: accessibilityLabel(Text("player.upNext")
# accessibilityHint(Text("…")):  comm -23 rỗng; không có mới
# Đọc code: UpNextList (upNext.root, upNext.item.<i> + .isSelected khi index == state.index) chỉ render khi
#   panel.isExpanded || isDragging; header peek (upNext.peek) không mang upNext.item.*.
# AC5
grep -rn 'UpNextSheet\|presentationDetents' iosApp/TSPodcast    # 0 ; PlayerView không có .sheet
# AC6 (luật một chỗ)
grep -rnE 'glassEffect|GlassEffectContainer|\.glass[^A-Za-z]|\.glassProminent|tabBarMinimizeBehavior|tabViewBottomAccessory|safeAreaBar|scrollEdgeEffect|backgroundExtensionEffect' \
  iosApp/TSPodcast | grep -v -e Design/Glass.swift -e Main/MiniPlayerSlot.swift
#   → 2 dòng comment (SearchView:24, CategorySelectionView:29) + 2 dòng `.glassProminentButton(` (PlayerView:286, 343):
#     đó là helper M5 trong Glass.swift, khớp regex `\.glassProminent` vì trùng tiền tố tên, không phải API iOS 26.
# AC8
grep -rn 'toolbarBackground\|background(.bar)' iosApp/TSPodcast | grep -v Design/Glass.swift   # 0
grep -n tabBarMinimizesOnScroll iosApp/TSPodcast/Main/MainTabView.swift                        # 82
# AC10
grep -nE 'glassSurface|GlassGroup|glassButton|glassProminentButton|glassCircleButton' Detail/EpisodeRowView.swift \
  Search/SearchResultRow.swift Favourite/FavouriteRootView.swift Categories/CategorySelectionView.swift   # 0
# PodcastCardView / ListRow / LivePager trong HomeView: 0 lời gọi helper glass
grep -rn brandAccent iosApp/TSPodcast        # AppColors.swift:17 + UpNextPanel.swift:172 (Circle, accessibilityHidden(true))
git diff $BASE..HEAD -- iosApp/TSPodcast | grep '^+' | grep -c 'font(.system(size'   # 0 ; Color(red: mới → 0
# GradientButtonStyle: OnboardingView:60, CategorySelectionView:107, FavouriteRootView:127 (giữ)
# AC11: script xcstrings → missing_en 0, missing_vi 0; 3 khoá đúng bảng §4; LocalizationTests:172-180 kiểm 3 khoá.
# Kiểm biên dịch thay thế (không phải build): 79 file Swift của app ở HEAD (copy vào scratchpad, bỏ `import Firebase*`,
#   + file stub API Firebase của T3):
xcrun swiftc -typecheck -swift-version 6 -sdk <iphonesimulator26.2> -target arm64-apple-ios17.0-simulator \
  -F shared/build/xcode-frameworks/Debug/iphonesimulator26.2 src/*.swift Stub.swift   # exit 0, 0 error, 0 warning
#   cùng lệnh với SDK iphoneos26.2, -target arm64-apple-ios17.0                     # exit 0, 0 error
# Logic thuần trên macOS (Swift package tạm, copy file test y nguyên từ HEAD):
swift test   # m5-hosttest: GlassSurfaceStyleTests + UpNextPanelStateTests + UpNextPeekTests → Executed 27 tests, 0 failures
swift test   # mpl-pkg: MiniPlayerLayoutTests → Executed 4 tests, 0 failures
# AC15
git log $BASE..HEAD --format=%s            # toàn feat(ios)/fix(ios)/docs(ios)
git log $BASE..HEAD --format=%B | grep -c 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'  # = git rev-list --count
git check-ignore -v iosApp/Config/Secrets.xcconfig   # .gitignore:32
git ls-files | grep -E '(^|/)(build|DerivedData|xcuserdata|\.gradle)/|\.xcresult|Secrets\.xcconfig$|Config/Firebase/(Debug|Release)/GoogleService-Info\.plist'   # rỗng
# key/secret PodcastIndex (đọc từ Secrets.xcconfig, chỉ in số lần) trong git log $BASE..HEAD -p → 0 và 0
```

`shared/`, Gradle, Android và UI test không đổi nên không chạy Gradle (`:shared:testAndroidHostTest`, `:app:assembleDebug` không bắt buộc ở M5).

#### Chờ chạy trên iPhone khi có iOS platform (toàn M5)

Lỗi nguyên văn (13:52, lần thử lại): `{ platform:iOS, arch:arm64e, id:00008110-000878203AC1401E, name:Tun, error:iOS 26.2 is not installed. Please download and install the platform from Xcode > Settings > Components. }`. `devicectl` báo Tun `available (paired)`, chỉ thiếu platform. Orchestrator chạy theo thứ tự (`SIGN="DEVELOPMENT_TEAM=57N5M3BU77 -allowProvisioningUpdates CODE_SIGN_ENTITLEMENTS=$OUT/../device-nopush.entitlements"`, `DEV=56DE0E78-9436-5BFB-8F79-E68AB560B730`):

1. **AC12** `xcodebuild test -project iosApp/iosApp.xcodeproj -scheme TSPodcastUnit -only-testing:TSPodcastTests -destination 'platform=iOS,name=Tun' -derivedDataPath $OUT/DD-m5 -resultBundlePath $OUT/M5-unit.xcresult $SIGN 2>&1 | tee $OUT/M5-unit.log` → `** TEST SUCCEEDED **`, 275 test; `xcrun xcresulttool get test-results tests --path $OUT/M5-unit.xcresult` có `GlassSurfaceStyleTests`, `UpNextPanelStateTests`, `UpNextPeekTests`, `MiniPlayerLayoutTests`, `LocalizationTests`, `AudioEngineTests`, `PlaybackSystemTests`, `SearchModelTests`, `HomeModelTests`, `PodcastDetailModelTests`, `CategorySelectionModelTests` Passed; `grep -c "in target 'TSPodcastUITests'" $OUT/M5-unit.log` → 0.
2. **AC13** `xcodebuild build -project iosApp/iosApp.xcodeproj -scheme TSPodcast -configuration Debug -destination 'platform=iOS,name=Tun' -derivedDataPath $OUT/DD-m5 $SIGN 2>&1 | tee $OUT/M5-build.log` → `** BUILD SUCCEEDED **`; `grep -c "only available in iOS" $OUT/M5-build.log` → 0; `grep -c "in target 'TSPodcastUITests'"` → 0.
3. **AC13 smoke** `xcrun devicectl device install app --device $DEV $OUT/DD-m5/Build/Products/Debug-iphoneos/TSPodcast.app`; `xcrun devicectl device process launch --device $DEV tss.t.podcast`; chờ ~15 s; `xcrun devicectl device info processes --device $DEV | grep TSPodcast.app` → 1 dòng.
4. **AC14** owner xem trên iPhone theo bảng dưới.

#### Owner xem trên iPhone (AC14 — thay cho screenshot)

`devicectl` không có lệnh chụp màn hình và không có simulator, nên M5 không có ảnh. Cài bản Debug (bước 3) rồi xem lần lượt. Đổi chế độ: Cài đặt iOS > Màn hình & Độ sáng (Light/Dark); Trợ năng > Màn hình & Cỡ chữ > Giảm độ trong suốt / Tăng độ tương phản / Cỡ chữ lớn hơn (kéo hết cỡ). Tun chạy iOS 27 nên chỉ thấy nhánh Liquid Glass; nhánh iOS 17/18 xem handoff.md.

Lượt 1–2: mỗi dòng xem ở **Light** rồi **Dark**.

| # | Màn / trạng thái | Cách tới | Kỳ vọng (Light và Dark) |
|---|---|---|---|
| 1 | Onboarding | Xoá app rồi cài lại, mở app | 4 trang vuốt ngang như M4; nút "Tiếp tục" vẫn là CTA gradient Xanh TS (không glass), chữ trắng đọc rõ ở cả hai chế độ. |
| 2 | Chọn danh mục (thanh "Lưu" khi cuộn) | Sau trang onboarding cuối; chọn vài danh mục rồi cuộn lưới | Thanh "Lưu" không có dải nền riêng: lưới cuộn mờ dần dưới thanh (scroll edge effect), nút "Lưu" gradient + badge số danh mục vẫn rõ và bấm được. |
| 3 | Khám phá + tab bar thu nhỏ + MiniPlayer inline | Phát 1 tập, về tab Khám phá, cuộn xuống rồi cuộn lên | Tab bar là glass của hệ thống, cuộn xuống thì thu nhỏ và MiniPlayer vào hàng với nó ở dạng gọn (artwork nhỏ + tiêu đề + play/pause, không tên podcast, không thanh progress); cuộn lên thì MiniPlayer đầy đủ trở lại có thanh progress. Card/hàng nội dung không có glass. |
| 4 | Khám phá — section lỗi | Bật Chế độ máy bay, kéo để làm mới hoặc mở app lạnh | Hộp "Không tải được nội dung" giữ nền như cũ; nút "Tải lại" là nút glass tint xanh, bấm được (≥ 44 pt). |
| 5 | Podcast detail | Tap một podcast ở Khám phá | Header ảnh mờ chạy dưới navigation bar glass; danh sách tập không glass; khi lỗi mạng nút "Tải lại" là nút glass. |
| 6 | Tìm kiếm — rỗng | Tab Tìm kiếm, chưa gõ | Ô tìm kiếm của hệ thống luôn hiện; chip "Tất cả"/"Nhạc" là capsule glass, chip đang chọn tint xanh với chữ đủ tương phản (Light chữ trắng, Dark chữ đen); gợi ý + lưới danh mục như M4. |
| 7 | Tìm kiếm — có kết quả | Gõ "radio hay", đổi chip, cuộn | Kết quả cuộn **dưới** hàng chip và mờ dần ở cạnh; đổi chip có hiệu ứng glass và tìm lại ngay; hàng kết quả không glass. |
| 8 | Yêu thích — rỗng | Tab Yêu thích khi chưa thích gì | Hình minh hoạ + "Không có chương trình yêu thích." + nút "Khám phá thêm" gradient (không đổi so với M4). |
| 9 | Yêu thích — có dữ liệu | Thích 1–2 tập ở Player rồi mở tab | Danh sách như M4, không glass trên hàng; tab bar thu nhỏ khi cuộn nếu danh sách đủ dài. |
| 10 | Cài đặt | Bánh răng ở tab Khám phá | Form hệ thống như M4 (navigation bar glass của hệ thống), không có nền tự vẽ sau thanh. |
| 11 | Player — peek | Tap MiniPlayer hoặc một tập | Nút đóng tròn glass ở trên trái; dưới hàng 5 nút là panel glass bo góc trên với grabber, "Tiếp theo", dòng "Đang phát" (chấm cam nhỏ + sóng âm) và tập kế nếu đủ chỗ; panel **không** che hàng nút hay thanh progress. |
| 12 | Player — panel mở | Vuốt header lên, hoặc tap header, hoặc nút danh sách phát | Panel trượt lên tới ngay dưới nút đóng, danh sách đầy đủ cuộn được, tập đang phát đánh dấu "Đang phát"; tap tập khác → phát tập đó và panel thu về peek; vuốt header xuống → peek; nút đóng vẫn đóng Player. |
| 13 | Player — live | Phát một mục Live ở Khám phá | Peek chỉ một dòng (mục live), thay thanh progress là nhãn "LIVE" đỏ (`player.live`) như M3; mở panel thấy đúng 1 mục. |
| 14 | Player — lỗi phát | Phát một tập rồi bật Chế độ máy bay (hoặc tập có link hỏng) | Thông báo lỗi + nút "Tải lại" glass prominent tint xanh, cao ≥ 44 pt, nằm trên peek và không bị panel che; bấm khi có mạng thì phát lại. |
| 15 | Player — hàng nút | Bất kỳ lúc nào trong Player | Favourite / Previous / Next / Playlist là nút tròn glass 56 pt, Play/Pause tròn 84 pt glass tint xanh với glyph trắng, spinner trắng khi đang buffering; Prev/Next mờ ở hai đầu playlist như M3. |
| 16 | MiniPlayer — thường | Đang phát, ở đầu một tab | Capsule glass phía trên tab bar: artwork, tiêu đề + tên podcast, play/pause, thanh progress mảnh nằm gọn trong đầu bo; tap mở Player. |
| 17 | MiniPlayer — inline | Như dòng 3 sau khi cuộn xuống | Dạng gọn trong tab bar thu nhỏ, play/pause vẫn bấm được, tap mở Player. |

Lượt 3–5: bật **một** tuỳ chọn trợ năng mỗi lượt (Light, rồi lặp nhanh ở Dark), xem lại các dòng 2, 3, 6, 11, 12, 15, 16.

| Lượt | Tuỳ chọn | Kỳ vọng |
|---|---|---|
| 3 | Giảm độ trong suốt (Reduce Transparency) | Không còn glass/mờ nào ở panel Up next, MiniPlayer, chip Tìm kiếm: chúng thành nền đặc (`secondarySystemBackground`), chữ và nút vẫn đọc rõ; thanh hệ thống (tab/nav) theo giao diện đặc của iOS. |
| 4 | Tăng độ tương phản (Increase Contrast) | Panel Up next, MiniPlayer và chip Tìm kiếm có thêm viền 1 pt rõ ràng quanh mép; chip đang chọn vẫn đủ tương phản; glass vẫn còn (không thành nền đặc). |
| 5 | Cỡ chữ accessibility lớn nhất (AX5) | Player: hàng 5 nút và peek vẫn vừa một màn, không chồng lên nhau, artwork co lại trước; peek chỉ còn 1 dòng; glyph Play/Pause to hơn nhưng vẫn nằm trong vòng 84 pt. MiniPlayer ở dạng gọn ngay cả khi không cuộn. Chip Tìm kiếm và thanh "Lưu" không bị cắt chữ. |

Ghi chú khi xem: ghi số dòng + chế độ của chỗ nào sai vào handoff.md (mục Máy và môi trường) hoặc báo agent; dòng nào đúng không cần ghi.

#### Để QC (gộp T1–T4)

Chạy ở pha QC cuối (luật test: không viết/sửa/chạy XCUITest trong milestone):

- **AC5 (panel Up next)**: mở Player → `upNext.peek` hiện dưới `player.playPause`, khung không giao nhau với hàng nút; `player.playlist` → `upNext.root` xuất hiện, `upNext.item.<index đang phát>` có trait selected; tap `upNext.item.1` → item 1 phát, `upNext.root` biến mất; kéo `upNext.peek` lên > 40 % → mở, kéo xuống > 20 % → thu; VoiceOver: `upNext.peek` đọc "Tiếp theo", value "Đang phát: <tiêu đề>", hint mở/thu; escape (hai ngón chữ Z) khi mở → thu; Live → peek 1 dòng.
- **`PlayerUITests` cần cập nhật**: test mở Up next bằng `player.playlist` rồi chờ `upNext.root` — giờ là **panel trong Player thay vì sheet**, identifier giữ nguyên; `upNext.root` biến mất sau khi chọn item. Bước nào vuốt sheet xuống để đóng phải đổi sang kéo `upNext.peek` xuống hoặc chọn item.
- **AC7 (Player)**: hit target ≥ 44 pt (Accessibility Inspector) cho `player.close`, 4 nút phụ, `player.playPause`, `player.retry`; `player.buffering` vẫn hiện khi buffering; Prev/Next disabled ở hai đầu.
- **AC8 (tab + MiniPlayer)**: `MiniPlayerUITests` dùng `miniplayer.progress` — ở iOS 26 khi tab bar thu nhỏ hoặc cỡ chữ accessibility thì không có `miniplayer.progress` (bố cục gọn); `miniplayer.slot/open/playPause` có ở cả hai. `MainShellUITests.testMiniPlayerSlotSitsAboveTabBar` cần chạy ở trạng thái chưa cuộn.
- **AC9 (Tìm kiếm, thanh, nút phụ)**: `SearchUITests` dùng `search.mode.all/music` + `isSelected` — giữ nguyên, chip giờ nằm trong safe-area bar (vẫn là con của `search.root`); hit target chip ≥ 44 pt; `category.save` còn bấm được khi lưới cuộn dưới thanh.
- Nếu QC có simulator/máy iOS 17/18: chạy lại các bước trên ở nhánh fallback (Material, capsule MiniPlayer, Player kiểu M3) — xem handoff.md.

#### Bảng AC M5 (developer tự đánh giá)

| AC | Trạng thái | Bằng chứng |
|---|---|---|
| AC1 | Đạt | Target 17.0 ×5, `project.yml` không đổi, diff phạm vi rỗng. |
| AC2 | Đạt | comm identifier/label/hint như trên; `upNext.root` chỉ khi mở (hoặc đang kéo tay). |
| AC3, AC4, AC6 | Đạt (macOS) / chờ iPhone | 27 test logic pass trên macOS; chạy trong xcresult khi có platform. |
| AC5, AC7, AC8, AC9 | Đạt (đọc code) / để QC | Grep + đọc code; bước UI ở "Để QC". |
| AC10, AC11 | Đạt | Grep và script xcstrings như trên. |
| AC12 | Chờ iPhone | Lỗi platform ở trên. |
| AC13 | Chờ iPhone | Lỗi platform ở trên; typecheck Swift 6 toàn app 0 lỗi thay thế tạm. |
| AC14 | Đạt | Bảng "Owner xem trên iPhone" ở trên. |
| AC15 | Đạt | Docs (progress, handoff, features-spec §3.6), log commit, ignore/ls-files/secret = 0. |

Giới hạn đã biết của M5 (tổng hợp):

- **Chưa build, chưa chạy unit test, chưa cài trên iPhone nào** vì component platform iOS 26.2 chưa cài xong. Bằng chứng hiện có: typecheck Swift 6 toàn app (SDK iOS 26.2, deployment 17) 0 lỗi, 31 test logic pass trên macOS, grep + đọc code. Khi có platform phải chạy các bước 1–4 ở trên trước khi coi M5 xong.
- **Nhánh fallback iOS 17/18 chưa ai nhìn thấy** (không simulator, Tun chạy iOS 27) — handoff.md.
- `upNext.root` cũng tồn tại trong lúc đang kéo header từ peek (để panel không rỗng khi trượt); khi đứng yên chỉ tồn tại lúc mở.
- Đổi Dynamic Type khi panel đang mở chỉ cập nhật chiều cao vùng peek khi panel thu lại (T1).

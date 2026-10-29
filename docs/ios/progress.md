# Tiến độ iOS

Nhật ký tiến độ cho app iOS của TSPodcast (hướng (a): KMP shared logic + SwiftUI + AVFoundation). Mỗi milestone có file kế hoạch riêng trong [milestones/](milestones/). Chuẩn parity là [features-spec.md](features-spec.md).

Quyết định owner đã chốt (không mở lại): Android giữ nguyên, không chuyển sang `shared`; iOS v1 không có Radio, Ads, ATT, Facebook SDK; analytics không có `device_id` (chỉ Install ID); mặc định lọc nội dung explicit (D6).

## Bảng milestone

| ID | Tên | Kế hoạch | Trạng thái |
|---|---|---|---|
| M0 | Nền tảng: module `shared` KMP + project Xcode chạy được | [milestones/M0.md](milestones/M0.md) | Đã nghiệm thu (review round 1) |
| M1 | Onboarding, chọn danh mục, khung tab, Home/Discover | [milestones/M1.md](milestones/M1.md) | Đang làm: T1, T2, T3 xong; T4, T5 chưa |

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

Trạng thái: **PO đã viết kế hoạch** ([milestones/M1.md](milestones/M1.md)): 5 task (T1 shared: key-value store, start destination, cache đĩa E1/E9, `HomeFeed` + D6; T2 nền iOS: target unit/UI test, routing, String Catalog, ảnh/ATS, hook test; T3 Onboarding + chọn danh mục; T4 TabView + Home + tab Yêu thích/Tìm kiếm; T5 nghiệm thu nội bộ và tài liệu), 22 acceptance criteria. Các quyết định PO chọn thay owner ở §7 của kế hoạch đã được ghi vào [handoff.md](handoff.md).

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

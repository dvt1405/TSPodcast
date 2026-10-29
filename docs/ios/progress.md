# Tiến độ iOS

Nhật ký tiến độ cho app iOS của TSPodcast (hướng (a): KMP shared logic + SwiftUI + AVFoundation). Mỗi milestone có file kế hoạch riêng trong [milestones/](milestones/). Chuẩn parity là [features-spec.md](features-spec.md).

Quyết định owner đã chốt (không mở lại): Android giữ nguyên, không chuyển sang `shared`; iOS v1 không có Radio, Ads, ATT, Facebook SDK; analytics không có `device_id` (chỉ Install ID); mặc định lọc nội dung explicit (D6).

## Bảng milestone

| ID | Tên | Kế hoạch | Trạng thái |
|---|---|---|---|
| M0 | Nền tảng: module `shared` KMP + project Xcode chạy được | [milestones/M0.md](milestones/M0.md) | Đang làm (T1, T2, T3 xong) |

## M0

Trạng thái: đang làm. T1, T2, T3 xong; T4 chưa làm.

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

<!-- Developer điền khi làm M0, theo M0-AC15: Kotlin version và lý do, plugin target Android, SKIE, cách tích hợp framework và lý do, thay đổi ngoài danh sách được phép, lệnh test/build/chạy app, việc còn mở cho owner. -->

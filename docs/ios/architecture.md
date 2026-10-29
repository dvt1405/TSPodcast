# Kiến trúc đích: KMP shared + SwiftUI

Tài liệu này mô tả kiến trúc cho hướng (a) đã chọn ở [README.md](README.md): Kotlin Multiplatform dùng cho data, domain và presentation state; SwiftUI cho UI; AVFoundation cho player.

## 1. Sơ đồ tổng thể

```
┌──────────────────────────── iosApp (Xcode, Swift) ─────────────────────────────┐
│ SwiftUI Views  ──observe──►  Swift wrapper (@Observable) ──► shared Store/VM   │
│ AudioEngine (AVPlayer, AVAudioSession, MPNowPlayingInfoCenter, CarPlay)        │
│ Firebase iOS SDK (Analytics, Crashlytics, RemoteConfig, Messaging)             │
│ Implement Kotlin interfaces: CrashReporter, ConfigAPI, AnalyticsTracker,       │
│                              PlaybackController, CredentialsProvider           │
└───────────────────────────────────▲────────────────────────────────────────────┘
                                    │ Shared.xcframework (+ SKIE Swift API)
┌───────────────────────────── shared (KMP) ─────────────────────────────────────┐
│ commonMain: models · api (Ktor) · db (Room KMP) · prefs (DataStore) ·          │
│             repositories · use cases · blacklist · radio parsers (Ksoup) ·     │
│             presentation (Store/ViewModel + UiState) · DI (Koin)               │
│ androidMain: OkHttp engine, Room builder(Context), DataStore path, Credentials │
│ iosMain:     Darwin engine, Room builder(NSFileManager), DataStore path        │
└───────────────────────────────────▼────────────────────────────────────────────┘
┌──────────────────────────── androidApp (hiện tại: :app) ───────────────────────┐
│ Compose UI, Media3 PlayerSessionService, Hilt → (bridge) Koin                  │
└────────────────────────────────────────────────────────────────────────────────┘
```

Nguyên tắc:

1. **`commonMain` không biết gì về UI framework.** Không đưa `LazyListState`, `MediaItem`, `Color` hay `@StringRes` vào UiState. Ở Android hiện tại, `PodcastViewModel.PodcastUIState` đang giữ `LazyListState`, còn `PlayerControlState` giữ `MediaItem` của Media3. Phải tách các thứ này ra.
2. **Player là platform service.** Shared chỉ định nghĩa interface `PlaybackController` và model `PlayableItem`. Android implement bằng Media3, iOS implement bằng AVFoundation (xem [audio-playback-ios.md](audio-playback-ios.md)).
3. **Firebase không đi vào `commonMain`.** Shared chỉ thấy các interface `CrashReporter`, `ConfigAPI` và `AnalyticsTracker`. Mỗi app native tiêm implementation của mình vào. Đây chính là pattern `Crash.install(...)` đang dùng trong `sharedLibrary/crash/CrashReporter.kt`.

## 2. Ánh xạ module Android → shared → iOS

| Module Android | Nội dung chính | Đích trong `shared` | Phía iOS |
|---|---|---|---|
| `coreApi` | `API.kt` (Retrofit), models (`Podcast`, `Feed`, `Episode`, `LiveEpisode`, `CategoryRes`, `SearchResponse`, `TrendingPodcastRes`, `EpisodeResponse`, `LiveResponse`, `PodcastByFeedIdRes`, `Person`, `Soundbite`, `Value`, `Funding`, …), DAO (`PodcastDao`, `EpisodeDao`, `FeedDao`), Gson converters, assets `categories.json` và `blacklist.json` | `shared/commonMain/.../api` (Ktor `PodcastIndexApi`), `.../model` (DTO `@Serializable`), `.../db/dao`, resources (JSON) | — |
| `core` | `PodcastDatabase` (Room v6), `SharedPref`, `NetworkModule`, `FavouriteDTO`, các repository interface, `MediaType`, `PruneApiCache`, `TSDataState` adapter | `.../db` (Room KMP), `.../prefs` (DataStore), `.../network` (`HttpClient` factory, `safeApiCall`), `.../repository` | Đường dẫn file DB, gọi `PruneApiCache` khi app launch |
| `podcasts` | `PodcastsRepositoryImpl`, `FavouriteRepository`, `HistoryRepository`, `BlacklistRepositoryImpl`, toàn bộ use case | `.../repository/impl`, `.../usecase` (giữ nguyên tên) | — |
| `coreRadio` | `RadioApi`, `RadioRepository` (VOV), `VOHRepository`, `RadioChannel`, `RadioChannelDao`, `GetRadioList`, `GetPlayableLink` | `.../radio` (Ksoup thay Jsoup, Ktor thay `Jsoup.connect`) | Có bật hay không tuỳ quyết định D1 |
| `featureRadio` | `RadioViewModel`, `RadioScreen` | `RadioStore` (presentation) | `RadioView.swift` |
| `featureOnboarding` | `OnboardingViewModel`, 2 screen | `OnboardingStore` (logic: step, category chọn ≥ 3) | `OnboardingView`, `SelectCategoryView` |
| `app` | `MainViewModel`, `PlayerViewModel`, `PodcastViewModel`, `FavouriteViewModel`, `SearchViewModel`, nav graph, screens | `HomeStore`, `PodcastDetailStore`, `FavouriteStore`, `SearchStore`, `PlayerStore` (state + intent, gọi `PlaybackController`) | Toàn bộ SwiftUI views, `TabView`, `NavigationStack` |
| `sharedPlayer` | `PlayerManager` (ExoPlayer), `PlayerSessionService`, `TSNotificationProvider`, `TSMediaController`, `progressOf` | `PlaybackController` interface, `PlayableItem`, `progressOf` (thuần Kotlin) | `AudioEngine.swift`, `NowPlayingCenter.swift`, `RemoteCommands.swift`, `CarPlaySceneDelegate.swift` |
| `sharedFirebase` | `TSAnalytics`, `TSFirebaseRemoteConfig`, `FirebaseCrashReporter`, `TSFcmServices` | Chỉ có interface: `AnalyticsTracker`, `ConfigAPI`, `CrashReporter` | `FirebaseAnalyticsTracker.swift`, `FirebaseRemoteConfig.swift`, `FirebaseCrashReporter.swift`, `AppDelegate` cho Messaging |
| `sharedLibrary` | `CrashReporter`/`Crash`/`safeCall`, `ConfigAPI`, `JsoupExt`, `TimeUtils`, theme Compose, widgets | `crash`, `config`, `html` (Ksoup + cookie store), `time` | `DesignSystem.swift` (màu lấy từ `Colors`) |
| `sharedResources` | Strings tiếng Việt, drawable, `SharedConstants` (User-Agent web) | Hằng số → `commonMain` | `Localizable.xcstrings`, `Assets.xcassets` |
| `securedToken` | JNI: `getApiUrl`, `getApiKey`, `getAuthHeader`, `getTime`, `getUserAgent`, ad unit IDs | `CredentialsProvider` (interface) + `PodcastIndexAuth` (tính header bằng Okio `sha1`) | `CredentialsProvider.swift` đọc từ build config (xem §5) |
| `hazeAndroid` | Fork Haze (blur) | — | SwiftUI `.background(.ultraThinMaterial)` / iOS 26 `glassEffect` |
| `ads` | AppLovin MAX (hoặc no-op seam) | `AdsPolicy` (đọc Remote Config `ads_enable`) | v1: không có. v1.1: AdMob (setup-and-build.md §7) |
| `samples` | Demo UI | — | — |
| `sharedKmp` | `TSDataState` | **Đổi tên thành `shared`** hoặc dùng làm module gốc | — |

### Cấu trúc thư mục đề xuất

```
TSPodcast/
├── app/                      # androidApp hiện tại (giữ tên :app để không phá CI)
├── shared/                   # = sharedKmp đổi tên (hoặc giữ sharedKmp)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/tss/t/shared/
│       │   ├── model/  api/  db/  prefs/  repository/  usecase/
│       │   ├── radio/  presentation/  di/  crash/  config/  analytics/  player/
│       ├── commonMain/composeResources/ hoặc resources/  (categories.json, blacklist.json)
│       ├── commonTest/
│       ├── androidMain/
│       └── iosMain/
├── iosApp/
│   ├── iosApp.xcodeproj
│   ├── TSPodcast/            # App target (SwiftUI)
│   ├── TSPodcastCarPlay/     # (nếu tách) – thường cùng target, khác scene
│   ├── Config/  Debug.xcconfig  Release.xcconfig  Secrets.xcconfig (gitignored)
│   └── fastlane/
└── docs/ios/
```

## 3. Những gì chuyển sang `commonMain`

### 3.1 Model và serialization

- Đổi Gson sang `kotlinx.serialization`. Các field đang có comment `//@SerializedName("x")` cho thấy JSON key trùng tên property, nên chỉ cần `@Serializable` và `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`.
- **Tách DTO và Entity.** Hiện `Podcast`, `Feed` và `Episode` vừa là response model vừa là `@Entity` Room, và có field `cachedAt`. Nên tách thành:
  - `*Dto` (`@Serializable`) cho API;
  - `*Entity` (Room) cho DB;
  - domain model thuần (`Podcast`, `Episode`) cho UI.

  Cách này tránh lỗi đã gặp ở `GetMusicFeeds`: `Feed.explicit` là `Boolean` còn `Podcast.explicit` là `Int`, khiến Gson ném lỗi `Expected an int but was BOOLEAN`.
- `Categories` (`HashMap<String,String>` với deserializer tuỳ biến `MapStringStringJsonConverter`) chuyển thành `Map<String, String>?` cộng một `JsonTransformingSerializer` nếu cần.
- `@Parcelize`/`Parcelable` (`LiveEpisode`, `Destination`, `Categories`) bỏ khỏi common. Nếu Android vẫn cần thì dùng `expect annotation` hoặc `kotlin-parcelize` cho `androidMain`.
- `Podcast.default`, `Episode.default`, `LiveEpisode.default` dùng `SimpleDateFormat`/`Calendar`. Chuyển sang `kotlin.time.Clock` + `kotlinx-datetime`, hoặc chỉ giữ ở test/preview.
- `TSDataState`: dùng bản trong `sharedKmp`. Nên **bỏ** `exception()` vì hàm này ném `ClassCastException` khi state không phải `Error`; thay bằng `(this as? Error)?.exception`.

### 3.2 Network (Ktor)

```kotlin
// commonMain
fun createHttpClient(engine: HttpClientEngine, auth: PodcastIndexAuth, json: Json) =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 2); exponentialDelay() }
        defaultRequest { url(auth.baseUrl) }               // = NativeLib.getApiUrl()
        install(createClientPlugin("PodcastIndexAuth") {
            onRequest { req, _ -> auth.headers().forEach { (k, v) -> req.headers[k] = v } }
        })
    }
```

`PodcastIndexAuth.headers()` phải tạo **đúng 4 header** như `NetworkModule.provideClient()` hiện tại:

| Header | Giá trị (mô tả, không phải secret) |
|---|---|
| `X-Auth-Date` | Unix epoch tính bằng **giây**, dạng chuỗi |
| `X-Auth-Key` | API key PodcastIndex (`NativeLib.getApiKey()`) |
| `Authorization` | `sha1(apiKey + apiSecret + X-Auth-Date)` ở dạng hex chữ thường. `X-Auth-Date` phải là **cùng một** timestamp với header trên (bản C++ gọi `now()` hai lần, nên có rủi ro lệch 1 giây đúng lúc chuyển giây) |
| `User-Agent` | `NativeLib.getUserAgent()`. PodcastIndex yêu cầu một UA nhận diện được app |

SHA-1 trong common: `okio.ByteString.encodeUtf8(s).sha1().hex()` (Okio là KMP), hoặc `org.kotlincrypto.hash:sha1`.

Engine theo nền tảng: `OkHttp` ở `androidMain`, `Darwin` ở `iosMain`.

`safeApiCall {}` thay cho `TSDataStateCallAdapterFactory`. Nó bọc response thành `TSDataState.Success/Error`, map HTTP error sang `TSExceptions(errorCode, description)` và **rethrow `CancellationException`** (cùng tinh thần với `safeCall` trong `SafeCall.kt`).

Danh sách endpoint xem [features-spec.md §2](features-spec.md#2-podcastindex-api).

### 3.3 Persistence

**DB: Room KMP (giữ Room 2.8.x)**

- `PodcastDatabase` thêm `@ConstructedBy(PodcastDatabaseConstructor::class)` và dùng `BundledSQLiteDriver` (`androidx.sqlite:sqlite-bundled`) để có SQLite giống nhau trên hai nền tảng.
- Builder viết theo expect/actual:
  - Android: `Room.databaseBuilder<PodcastDatabase>(context, context.getDatabasePath("TSPodcastDatabase").absolutePath)`.
  - iOS: đường dẫn `NSApplicationSupportDirectory/TSPodcastDatabase.db`, đặt cờ `NSURLIsExcludedFromBackupKey = true` vì đây là cache theo điều khoản PodcastIndex.
- Migration 1→4, 4→5 và 5→6 viết lại bằng API `Migration.migrate(connection: SQLiteConnection)`. iOS cài mới ở v6 nên không chạy migration, nhưng Android vẫn cần.
- `PodcastTypeConverters` hiện dùng Gson cho `Categories`, `Funding`, `Value`, `List<PodcastTranscript>`, `List<Person>`, `List<Soundbite>`, `Soundbite`, `List<SocialInteract>`, `List<RadioChannel.Link>`, `List<String>` và `MediaType`. Viết lại bằng `Json.encodeToString`. Định dạng JSON giữ nguyên nên dữ liệu cũ trên Android vẫn đọc được.
- Các DAO là `abstract class` có method non-abstract (`insert` gán `cachedAt`). Room KMP hỗ trợ được, nhưng mọi method DAO phải là `suspend` hoặc trả về `Flow`. `System.currentTimeMillis()` thay bằng `Clock.System.now().toEpochMilliseconds()`.
- `PruneApiCache` (TTL 7 ngày, giữ lại bản ghi đã favourite) chuyển sang common và chạy mỗi lần app launch trên **cả hai** nền tảng. Đây là nghĩa vụ theo điều khoản PodcastIndex.

**Key-value: DataStore Preferences KMP** (`androidx.datastore:datastore-preferences-core`), file `ts_prefs.preferences_pb`.

| Key Android (`SharedPref`) | Kiểu | Dùng cho |
|---|---|---|
| `OnboardingFinished` | Boolean | Đã qua onboarding |
| `HasSelectFavouriteCategory` | Boolean | Đã chọn danh mục |
| `ListFavouriteCategory` | JSON `Set<Category>` | Danh mục yêu thích, dùng làm `cat=` cho trending/recent |
| `CategoryRes` | JSON | Cache `categories/list` |
| `InstallId` | String UUID | Crashlytics user id |
| `cachedTrending`, `lastUpdateTrending`, `cachedTrending_<cat>`, `lastUpdateTrending_<cat>` | JSON / Long | Cache trending 15 phút |
| `extra:current_item_id` | String | Item đang phát gần nhất |

Trên iOS, DataStore lưu file (không dùng `UserDefaults`), nên **không** phát sinh required-reason `UserDefaults`. Nếu dùng `multiplatform-settings` (NSUserDefaults) thì phải khai `CA92.1` trong `PrivacyInfo.xcprivacy`. Firebase SDK cũng dùng UserDefaults nhưng đã tự khai trong privacy manifest của nó.

Cookie store của `JsoupExt` (dùng cho radio) chuyển thành Ktor `HttpCookies` với `CookiesStorage` tự viết, lưu qua DataStore.

### 3.4 Domain và use case

Chuyển nguyên (đổi `@Inject` sang Koin `factory {}`):

`GetTrendingPodcasts`, `GetCategories`, `GetEpisodeByFeedId`, `GetPodcastByFeedID`, `GetLiveEpisodes`, `GetRecentEpisodes`, `GetRecentFeeds`, `GetRecentNewFeeds`, `GetMusicFeeds`, `SearchPodcasts`, `SearchMusicFeeds`, `SaveFavouriteUseCase`, `DeleteFavouriteUseCase`, `IsFavouriteUseCase`, `SelectAllFavourite`, `SelectPodcastAndEpisodeByFavourite`, `SaveCurrentPlayingUseCase`, `GetEpisodeLocalUseCase`, `BlacklistFilterUseCase`, `PruneApiCache`, `GetRadioList`, `GetPlayableLink`.

Cần sửa khi chuyển:

- `ConcurrentHashMap`/`synchronized`/`@Volatile` trong companion object thay bằng một `InMemoryTtlCache<K,V>` dùng `kotlinx.coroutines.sync.Mutex` hoặc `atomicfu`, inject dạng singleton thay vì để static.
- Bỏ các overload nhận `androidx.media3.common.MediaItem` (`SaveFavouriteUseCase(mediaItem)`, `IsFavouriteUseCase(mediaItem)`, `SaveCurrentPlayingUseCase(mediaItem)`), thay bằng `PlayableItem` của shared.
- `BlacklistRepositoryImpl`:
  - `org.json.JSONObject` thay bằng `Json.parseToJsonElement`;
  - `context.assets` thay bằng resource đọc từ common;
  - **sửa bug**: nhánh `filterSpace` hiện không có tác dụng vì kết quả `replace` bị bỏ đi.
- Remote Config key giữ nguyên: `ts_podcast_black_list` (JSON `{feedId:[], filterText:[]}`) và `ads_enable` (Boolean).

### 3.5 Presentation (Store/ViewModel dùng chung)

Dùng `androidx.lifecycle:lifecycle-viewmodel` bản KMP (đã có `ViewModel` trong common). Có thể dùng thêm `kmp-observableviewmodel`, hoặc một `Store` tự viết với `CoroutineScope`. Mỗi màn hình có cấu trúc:

```kotlin
class HomeStore(/* use cases */) : ViewModel() {
    val state: StateFlow<HomeUiState>          // data class thuần
    val events: Flow<HomeEvent>                // one-shot
    fun onIntent(intent: HomeIntent)           // Refresh, OpenPodcast(id), PlayLive(id)...
}
```

Phía Swift bọc `StateFlow` thành `@Observable` qua SKIE (`for await state in store.state`). Xem §6.

## 4. Ranh giới expect/actual (và interface tiêm từ native)

Ưu tiên dùng **interface + DI** thay cho `expect class`, để Swift tự implement được.

| Khái niệm | Kiểu | commonMain | androidMain | iosMain / Swift |
|---|---|---|---|---|
| HTTP engine | `expect fun httpEngine(): HttpClientEngine` | — | `OkHttp.create()` | `Darwin.create()` |
| DB builder | `expect fun dbBuilder(): RoomDatabase.Builder<PodcastDatabase>` | — | `Context` | `NSFileManager` Application Support |
| DataStore path | `expect fun dataStorePath(): String` | — | `filesDir` | `NSApplicationSupportDirectory` |
| Credentials | `interface CredentialsProvider { baseUrl; apiKey; apiSecret; userAgent }` | dùng trong `PodcastIndexAuth` | đọc `NativeLib` | **Swift** đọc build setting (§5) |
| Crash | `interface CrashReporter` (đã có) | `Crash` facade, `safeCall` | `FirebaseCrashReporter` | **Swift** dùng `Crashlytics.crashlytics()` |
| Remote config | `interface ConfigAPI` (đã có, đổi `JSONObject` sang `JsonElement`) | | Firebase Android | **Swift** dùng `RemoteConfig.remoteConfig()` |
| Analytics | `interface AnalyticsTracker { trackScreen; trackEvent }` | | `TSAnalytics` | **Swift** dùng `Analytics.logEvent` |
| Player | `interface PlaybackController` (§4.1) | Stores gọi qua interface | Media3 | **Swift** `AudioEngine` |
| HTML → text | `expect fun htmlToPlainText(html: String): String` | | `HtmlCompat` | `NSAttributedString(data:options:.html)` (chỉ gọi trên main thread), hoặc dùng `Ksoup.parse(html).text()` cho cả hai nền tảng |
| Bundled JSON | resources | `Res.readBytes("files/categories.json")` (compose-resources) hoặc `expect fun readResource()` | assets | `Bundle.main` |
| UUID / time | — | `kotlin.uuid.Uuid.random()`, `kotlin.time.Clock` | | |
| Logging | — | Kermit (`co.touchlab:kermit`) | Logcat | OSLog |

### 4.1 `PlaybackController`

```kotlin
// commonMain
data class PlayableItem(
    val id: String, val url: String, val title: String,
    val artist: String?, val album: String?, val artworkUrl: String?,
    val durationMs: Long?, val type: MediaType,       // PodcastEpisode | Radio | Podcast
    val radioSource: String? = null                    // "VOV" | "VOH" -> GetPlayableLink
)

data class PlaybackState(
    val current: PlayableItem?, val isPlaying: Boolean, val isBuffering: Boolean,
    val positionMs: Long, val durationMs: Long, val rate: Float,
    val queue: List<PlayableItem>, val error: String?
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun play(item: PlayableItem, queue: List<PlayableItem>)
    fun togglePlayPause(); fun seekTo(positionMs: Long)
    fun skipNext(); fun skipPrevious(); fun setRate(rate: Float)
}
```

Phía iOS, `AudioEngine.swift` conform `PlaybackController` (Kotlin interface được export thành Obj-C protocol). Nó phát `state` bằng cách giữ một `MutableStateFlow` do shared tạo sẵn: shared cung cấp class `PlaybackStateHolder` có `fun update(state)` để Swift gọi. Tránh để Swift tự implement `StateFlow`.

## 5. Secrets và module `securedToken`

Hiện trạng: secret PodcastIndex (key và secret), AppLovin SDK key và ad unit ID nằm dạng chuỗi trong `securedToken/src/main/cpp/securedtoken.cpp`, còn AppLovin API key nằm trong `app/build.gradle.kts`. JNI chỉ là obfuscation: chạy `strings` trên `libsecuredtoken.so` là lấy được.

Cho iOS:

1. **Không** commit secret vào repo iOS. Dùng `iosApp/Config/Secrets.xcconfig` (gitignored) với các biến `PODCASTINDEX_API_KEY`, `PODCASTINDEX_API_SECRET`, `PODCASTINDEX_BASE_URL`, `PODCASTINDEX_USER_AGENT`. Trên CI, inject từ GitHub Secrets hoặc biến môi trường của Xcode Cloud.
2. Đọc qua `Info.plist` (`$(PODCASTINDEX_API_KEY)`), rồi `Bundle.main.object(forInfoDictionaryKey:)`. Có thể XOR hoặc obfuscate nhẹ như bản Android; đừng tưởng đó là bảo mật.
3. **Khuyến nghị mạnh:** tạo key PodcastIndex **riêng cho iOS** để có thể thu hồi độc lập. Về trung hạn, chuyển sang một proxy ký request (Cloudflare Worker hoặc Firebase Function) giữ secret phía server; app chỉ gửi App Check token. Khi đó `CredentialsProvider` chỉ còn `baseUrl`.
4. Rotate AppLovin key, vì nó đã nằm trong lịch sử git (commit message của nhánh `TSP-5-remove-applovin` cũng ghi nhận điều này).
5. **Lỗi thiếu credentials phải "mềm".** Trên Android, `main` đã thêm `NativeLib.isAvailable`/`loadError` (commit `baba08f`): nếu thiếu `libsecuredtoken.so` thì app hiện màn "Bản cài đặt chưa đầy đủ" thay vì crash trong `Application.onCreate`. iOS không gặp lỗi ABI split, nhưng `CredentialsProvider` vẫn phải trả lỗi có kiểu (`AppError.MissingCredentials`, ví dụ khi CI quên inject `Secrets.xcconfig`) thay vì `fatalError`. Thêm một test ở CI để chặn build release có giá trị rỗng.

Seam quảng cáo: [admob-migration.md §3](../plans/admob-migration.md) thiết kế `:ads` (interface + no-op) và `:ads-admob` (implementation). Shared nên định nghĩa interface `AdsProvider` và chính sách tần suất (§3.3 của tài liệu đó, viết bằng Kotlin thuần nên đưa được vào `commonMain`). Swift sẽ implement bằng Google Mobile Ads iOS SDK khi bật D2.

## 6. Swift interop

### 6.1 SKIE (khuyến nghị)

[SKIE](https://skie.touchlab.co/) (Touchlab) sinh Swift API đẹp hơn cho framework Kotlin:

- `suspend fun` thành `async throws`;
- `Flow`/`StateFlow` thành `AsyncSequence`;
- `sealed class` (`TSDataState`, `HomeIntent`) thành Swift enum, dùng được `switch onEnum(of:)` có kiểm tra exhaustiveness;
- default argument và enum Kotlin thành Swift enum.

```kotlin
// shared/build.gradle.kts
plugins { id("co.touchlab.skie") version "<skie-version>" }
```

```swift
@MainActor @Observable
final class HomeModel {
    private(set) var state: HomeUiState
    private let store: HomeStore
    init(store: HomeStore = KoinIos.homeStore()) {
        self.store = store; self.state = store.state.value
        Task { for await s in store.state { self.state = s } }
    }
    func refresh() { store.onIntent(intent: HomeIntentRefresh()) }
}
```

**Ràng buộc version:** SKIE thường hỗ trợ bản Kotlin mới vài ngày sau khi bản đó ra, nhưng vẫn có độ trễ. Tại thời điểm viết, Kotlin mới nhất là **2.4.21** (8/10/2026). Thảo luận trên kotlinlang Slack giữa tháng 9/2026 cho thấy SKIE **chưa chính thức hỗ trợ 2.4.20**. SKIE 0.10.12 đã được dùng với Kotlin 2.3.21. Do đó:

- Khoá Kotlin theo **bản mới nhất mà SKIE changelog liệt kê là hỗ trợ**. Hiện tại đó là 2.3.21; nếu changelog đã có 2.4.x thì dùng 2.4.x.
- Không dùng Kotlin pre-release khi có SKIE.

### 6.2 Phương án thay thế

- **KMP-NativeCoroutines** (rickclephas): dùng annotation `@NativeCoroutines`, rồi trong Swift gọi `asyncSequence(for:)`. Ít "phép thuật" hơn SKIE và theo kịp version Kotlin nhanh hơn.
- **Swift export** (Kotlin, experimental): export trực tiếp sang Swift không qua Obj-C. Chưa nên dùng cho production ở thời điểm viết; theo dõi lại khi lên stable.

### 6.3 Quy tắc ranh giới

- Chỉ export (`export(...)`) những module/package mà Swift cần, để header nhỏ và build nhanh.
- Không truyền `Throwable` qua biên giới. Map sang `sealed class AppError` có message đã localize ở Swift.
- Mọi hàm `suspend` export ra Swift đều gắn `@Throws(CancellationException::class, AppErrorException::class)` nếu không dùng SKIE.
- Kotlin exception không bị bắt sẽ làm app iOS crash với stack trace khó đọc. Cài [CrashKiOS](https://github.com/touchlab/CrashKiOS) để gửi stack trace Kotlin lên Crashlytics (setup-and-build.md §6).

## 7. Dependency và version (mốc 10/2026, phải kiểm tra lại trước khi khoá)

| Thành phần | Version đề xuất | Trạng thái kiểm chứng |
|---|---|---|
| Kotlin | 2.3.21 (hoặc 2.4.2x khi SKIE hỗ trợ) | 2.4.21 là bản mới nhất (kotlinlang.org/docs/releases.html, 8/10/2026). Repo đang dùng 2.2.0 |
| KSP | khớp với Kotlin | — |
| AGP | 8.13.x (đang dùng) | Plugin `com.android.kotlin.multiplatform.library` cho module shared |
| SKIE | bản mới nhất hỗ trợ Kotlin đã chọn (≥ 0.10.12 cho 2.3.21) | Xem skie.touchlab.co/changelog |
| kotlinx.coroutines | 1.10.2+ | Repo đang dùng 1.10.2 |
| kotlinx.serialization | 1.9.x+ | Kiểm tra lại |
| Ktor client | 3.x mới nhất (core, content-negotiation, okhttp, darwin) | Kiểm tra lại |
| Room KMP | 2.8.4 (đang dùng, stable 19/11/2025) + `androidx.sqlite:sqlite-bundled` 2.6.2 | developer.android.com/kotlin/multiplatform/room. Room 3.0 còn alpha, **không** dùng |
| DataStore Preferences KMP | 1.1.x+ | Kiểm tra lại |
| Koin | 4.x (`koin-core`, `koin-android`) | Kiểm tra lại |
| Ksoup | `com.fleeksoft.ksoup` mới nhất | Chỉ cần nếu làm Radio |
| Okio | 3.x (sha1) | — |
| Kermit | 2.x | — |
| CrashKiOS | bản tương thích Kermit | — |
| Compose Multiplatform | ≥ 1.8.0 (chỉ nếu dùng lối thoát CMP) | iOS stable từ 1.8.0 (5/2025) |
| Xcode | **26.x trở lên** (bắt buộc từ 28/4/2026) | developer.apple.com/news/upcoming-requirements |
| iOS deployment target | **iOS 17.0** đề xuất | Apple chỉ yêu cầu ≥ iOS 13. Chọn 17 để có `@Observable`, `NavigationStack` ổn định và `AVPlayer.defaultRate`; Firebase iOS SDK hiện yêu cầu iOS ≥ 15 |
| Firebase iOS SDK | 12.x qua SPM | Kiểm tra lại |
| Google Mobile Ads + UMP (nếu có ads) | qua SPM | Kiểm tra lại |

Kotlin/Native target: `iosArm64()` và `iosSimulatorArm64()`. Bỏ `iosX64()` trừ khi còn Mac Intel; JetBrains đang hạ cấp hỗ trợ các target Apple x86_64.

## 8. Dependency Injection

```kotlin
// commonMain
val sharedModule = module {
    single { Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true } }
    single { createHttpClient(httpEngine(), PodcastIndexAuth(get()), get()) }
    single { PodcastIndexApi(get()) }
    single { dbBuilder().setDriver(BundledSQLiteDriver()).build() }
    single { get<PodcastDatabase>().podcastDao() }   // ... các DAO khác
    single<IPodcastRepository> { PodcastsRepositoryImpl(get(), get(), get()) }
    single { BlacklistRepository(get(), get()) }
    factory { GetTrendingPodcasts(get(), get(), get()) }  // ...
    viewModel { HomeStore(get(), get(), get()) }          // ...
}

// iosMain – gọi từ Swift khi app launch
fun initKoin(
    credentials: CredentialsProvider, crash: CrashReporter,
    config: ConfigAPI, analytics: AnalyticsTracker, player: PlaybackController
) = startKoin {
    modules(sharedModule, module {
        single { credentials }; single { crash }; single { config }
        single { analytics }; single { player }
    })
}.also { Crash.install(crash) }
```

Android: trong giai đoạn chuyển tiếp có thể giữ Hilt ở `:app` và bridge sang Koin bằng `@Provides fun x() = getKoin().get<X>()`, rồi bỏ dần Hilt sau.

## 9. Lộ trình migration (cập nhật cho `KMP_MIGRATION_PLAN.md`)

1. **Bước 0, hạ tầng (tuần 1):**
   - nâng Kotlin;
   - tạo `shared/build.gradle.kts` (KMP và Android library);
   - `include(":shared")`;
   - chuyển `TSDataState`;
   - CI chạy `:shared:allTests`.
2. **Model và serialization (tuần 1–2):** DTO `@Serializable`, test parse bằng các file JSON sẵn có trong `podcasts/src/debug/assets/*.json` (Trending, Episode, LiveEpisode, NewFeed, RecentEpisodes, RecentFeeds).
3. **Network (tuần 2):** Ktor `PodcastIndexApi` cho các endpoint ở features-spec §2; test header auth bằng `MockEngine`.
4. **Persistence (tuần 2–3):** Room KMP, DataStore, `PruneApiCache`.
5. **Repository và use case (tuần 3):** chuyển nguyên, kèm unit test cho blacklist, TTL cache và favourite.
6. **Presentation stores (tuần 3–4):** Home, Search, Detail, Favourite, Onboarding, Player (state), Radio.
7. **Android dùng shared (tuần 4–5):** thay Retrofit/Gson, giữ Compose UI. Không đổi hành vi; QA hồi quy.
8. **iOS (tuần 3 trở đi, song song):** xem [setup-and-build.md](setup-and-build.md).

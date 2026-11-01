# Kế hoạch: Gỡ toàn bộ ad SDK hiện tại và chuyển sang Google AdMob

> **Trạng thái: Đang làm** (2026-11-01) — đã xong: Phase 0–1 (gỡ AppLovin, Facebook SDK, IMA; seam `:ads` + `:ads-admob`; UMP) ở PR #24; id thật, build guard, quy tắc interstitial ở PR #38, #45, #54.
> Còn lại: rollout có guardrail trên Play (chờ bản lên production), A/B Phase 3, rewarded, mediation Phase 4.

> Ticket: TSP-13 · Ngày lập: 2026-10-29 · Trạng thái: **Owner đã duyệt, Phase 0 + Phase 1 đã code (TSP-16)**, xem mục "Trạng thái triển khai" ngay dưới.
> Mốc code khảo sát: `main` @ `47fc4f4`. Có tham chiếu nhánh chưa merge `origin/tuandv/TSP-5-remove-applovin` (`806dbe2`).

---

## Trạng thái triển khai (Implementation status) — TSP-16, 2026-10-29

Nhánh `tuandv/TSP-16-admob-nextgen` (tách từ `main` @ `c2e46d0`, đã có PR #19). Quyết định của owner: dùng **GMA Next-Gen SDK**; **chưa có** AdMob app id / ad unit id thật nên toàn bộ dùng **id TEST của Google**, đổi sang id thật không cần sửa code; bỏ hẳn AppLovin.

### Đã làm

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Phase 0: gỡ AppLovin | Xong | Cherry-pick `806dbe2` (TSP-5). Conflict với #19: xoá `ApplovinSdkWrapper.kt`; phần xử lý thiếu `libsecuredtoken.so` trong `App`/`MainActivity` giữ nguyên. Block `applovin { apiKey }`, plugin quality-service, maven repo AppLovin đã đi cùng TSP-5. |
| Phase 0: gỡ Facebook SDK, IMA, ad id trong NDK | Xong | Đã grep trước khi gỡ: không có `com.facebook`/`ImaAdsLoader`/`interactivemedia` nào được dùng. Gỡ `facebook-android-sdk`, meta-data `com.facebook.sdk.ApplicationId`, string `facebook_app_id` (không có client token), `media3-exoplayer-ima` ở `:app` và `:sharedPlayer`, và `getApplovinKey`/6 hàm ad unit trong `NativeLib` + `securedtoken.cpp`. Bản release (R8) không còn class `com.facebook`/`com.applovin`/IMA. |
| Seam `:ads` (§3) | Xong | Không phụ thuộc ad SDK nào. `AdsApi.kt` (AdPlacement, AdsController, AdRenderer, AdsHostSignals, slot composable), `AdsModule.kt` (`@BindsOptionalOf @AdsImplementation` → không có `:ads-admob` thì tự về no-op), `AdsConfig.kt` (mọi key §6 + default in-app), `AdPolicy.kt` (§2.1/§2.3), `SharedPrefsAdCounterStore`, `interleaveNativeSlots`. 21 unit test JVM. |
| `:ads-admob` | Xong | `ads-mobile-sdk:1.6.0` + `user-messaging-platform:4.0.0` khai báo `implementation`, nên `:app` không import được type AdMob. Loại `play-services-ads(-lite)` ở `:ads-admob` và `:app` theo hướng dẫn Next-Gen. Init trên `Dispatchers.IO`; callback nền chỉ ghi StateFlow/atomic/store có `@Synchronized`, thao tác View/Activity chạy trên main. |
| Consent UMP (§5) | Xong | `requestConsentInfoUpdate` mỗi lần mở app, form nếu cần, privacy options. Debug ép debug geography (mặc định EEA). Link "Tùy chọn quyền riêng tư và quảng cáo" ở cuối màn Discover, chỉ hiện khi `privacyOptionsRequired` (app chưa có màn Settings). |
| Banner / native | Xong | Inline adaptive banner (cao tối đa 100dp) và native template small/medium dựng bằng code, có nhãn "Quảng cáo"/"Ad", video mute. Ad đã load được cache theo placement+slot nên cuộn `LazyColumn` không tạo request mới; không có vòng retry (tối đa 1 lần/phút sau lỗi). |
| App Open warm start | Xong | `ProcessLifecycleOwner` + quyết định ở `onResume` của `MainActivity` (sau `onNewIntent`). Chỉ show ad **đã preload**. Chặn khi đang phát/buffer/`playWhenReady`, khi ở màn Player, khi mở từ media notification hoặc deep link, khi chưa xong onboarding, cộng các cap §6. |
| Interstitial / Rewarded | Scaffold | Có trong controller + policy + preloader, **tắt mặc định** và **chưa có call site** trong UI. Khi bật rewarded cần thêm nút opt-in và pause player trước khi show (§2.2). |
| Analytics | Xong | `ads_request`, `ads_load_failed`, `ads_show`, `ads_impression`, `ads_click`, `ads_blocked` (sample 10%), `ads_paid`, `ads_rewarded_earned`, `consent_status`. |

Vị trí placement (`AdPlacement`) trong app:

| Placement | Màn hình | Format | Mặc định release |
|---|---|---|---|
| `discover_inline` | Discover, sau "Kho nhạc" | Native medium | bật |
| `discover_fav_inline` | Discover, sau podcast yêu thích thứ 2 | Banner | **tắt** |
| `favourite_top` | Yêu thích, đầu danh sách | Banner | bật |
| `player_episode_list` | Player, panel danh sách tập | Banner | **tắt** |
| `podcast_detail_header` | Podcast Detail, dưới header | Banner | bật |
| `podcast_detail_episodes` | Podcast Detail, chèn vào danh sách tập (sau tập 3, rồi mỗi 8 tập, tối đa 3) | Native small | bật |
| `search_results` | Search, dưới bộ lọc (giữ vị trí slot cũ) | Banner | bật |
| `app_open_warm` | Quay lại app sau ≥ 30s ở nền | App Open | **tắt** |
| `interstitial_nav`, `rewarded_ad_free`, `discover_collapsible`, `app_open_cold` | — | — | **tắt** |

Toàn bộ release còn nằm sau kill switch: `ads_enabled` mặc định **`false`** ở release, **`true`** ở debug. Debug còn bật `app_open_warm`, bỏ grace period và bỏ "skip 2 session đầu" để test được.

Khác với plan:
- `search_results` vẫn là banner ở đúng slot cũ (dưới filter), chưa chuyển thành native sau kết quả thứ 5.
- "Session" cho `ads_app_open_skip_first_sessions` được đếm **1 lần mỗi process** (cold start), không đếm warm start.
- Không thêm `setDefaultsAsync`: `AdsConfig` đọc mọi key dưới dạng string rồi parse, key chưa set (Firebase trả `""`) sẽ rơi về default in-app. Kết quả tương đương, mà không phải đụng `TSFirebaseRemoteConfig`.
- Key cũ `ads_enable` không còn được đọc (key mới là `ads_enabled`), nên giá trị cũ trên console không vô tình bật ad.

### Kiểm tra đã chạy

- `./gradlew :app:assembleDebug`: pass.
- Unit test: `:ads` (21 test mới), `:securedToken`, `:sharedPlayer`, `:app`: pass.
- `:app:assembleRelease`: compile + `minifyReleaseWithR8` + shrink resource pass. Dừng ở `packageRelease` vì máy local không có `KEYSTORE_PASSWORD` (đúng như dự kiến). Manifest release có `APPLICATION_ID` = test app id. Lưu ý: task `uploadCrashlyticsMappingFileRelease` có chạy và upload mapping file.
- Thiết bị (Pixel 3 XL, `tss.t.podcast.debug`): UMP nhận debug geography EEA (`IABTCF_gdprApplies=1`) và load xong form consent (`load_complete ok`), không có crash. Phần xem form, banner/native và App Open trên màn hình **chưa kiểm tra được**: máy bị khoá PIN.

### Cách đưa id thật vào (không sửa code)

1. Gradle property, đặt trong `~/.gradle/gradle.properties` hoặc truyền `-P...`. Ở CI dùng biến môi trường `ORG_GRADLE_PROJECT_<TÊN>` lấy từ GitHub secret:
   ```properties
   ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   ADMOB_UNIT_APP_OPEN=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ADMOB_UNIT_BANNER=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ADMOB_UNIT_NATIVE=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ADMOB_UNIT_INTERSTITIAL=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ADMOB_UNIT_REWARDED=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
   ```
   Chỉ bản **release** dùng các giá trị này. Bản debug luôn dùng test id. App id vừa được ghi vào manifest (UMP cần) vừa vào `InitializationConfig`.
2. Muốn mỗi placement có unit riêng thì set Remote Config `ads_unit_ids` (JSON), ví dụ `{"favourite_top":"ca-app-pub-…/…","podcast_detail_episodes":"ca-app-pub-…/…"}`. Id sai định dạng bị bỏ qua. Bản debug bỏ qua key này.
3. Bật dần bằng Remote Config: `ads_enabled=true`, rồi điều chỉnh `ads_placements_enabled` (JSON) và các cap ở §6.
4. Test device cho debug (chỉ cần khi debug dùng id thật, hiện không có trường hợp này): `ADMOB_TEST_DEVICE_IDS`, `UMP_TEST_DEVICE_HASHED_IDS` (cách nhau bằng dấu phẩy; id được SDK in ra logcat). Đổi vùng consent khi debug: `UMP_DEBUG_GEOGRAPHY=EEA|US|OTHER|OFF`.

### Việc owner cần làm

1. **Tạo app trên AdMob** (link tới listing Play `tss.t.podcast`) và tạo ad unit: App Open, Banner (adaptive), Native, cùng Interstitial/Rewarded nếu định dùng. Đưa id vào CI theo hướng dẫn ở trên. **Không bật `ads_enabled` ở release khi chưa có id thật**, nếu bật thì user sẽ thấy quảng cáo "Test Ad".
2. AdMob → **Privacy & messaging**: tạo message *European regulations* (GDPR) và *US state regulations*. Nếu không có message, UMP sẽ không hiện form ở EEA. Bật consent mode để Firebase Analytics đồng bộ.
3. **Link app AdMob với Firebase** (để GA4 tự log `ad_impression` có doanh thu).
4. **`app-ads.txt`**: khi đã có domain, khai báo domain ở mục Website của Play listing, rồi phục vụ `https://<domain>/app-ads.txt` bằng snippet lấy từ AdMob (§7.1).
5. **Play Console → App content**: Ads = "Contains ads". Advertising ID = có dùng. **Data safety**: thêm phần dữ liệu AdMob thu và chia sẻ (Device or other IDs, Approximate location, App interactions, Diagnostics), **gỡ Meta/Facebook và AppLovin**. Cập nhật Privacy policy (§7.2).
6. **Revoke AppLovin API key** đang nằm trong lịch sử git, và xem có cần xoá/thu hồi Meta app `1177427693996135` không.
7. Quyết định các câu hỏi còn mở ở §11 (consent riêng cho user VN, rewarded, Remove-ads), trước khi sang Phase 3.

---

## 0. Tóm tắt (TL;DR)

1. **Hiện trạng:** app dùng **AppLovin MAX 13.6.4** (App Open, 6 vị trí banner, native trong danh sách tập) và **Facebook Android SDK 18.3.0** (không có code gọi, nhưng SDK tự khởi tạo và tự log app event + Advertising ID sang Meta). Ngoài ra còn kéo theo **`media3-exoplayer-ima`** (Google IMA SDK) mà không dùng ở đâu. Theo commit message của TSP-5, debug build log ra *"SDK key … is rejected by AppLovin"*. Vậy doanh thu quảng cáo hiện tại **thực tế ≈ 0**, và rủi ro "mất doanh thu khi chuyển" gần như không có.
2. **Đích đến:** Google AdMob qua **GMA Next-Gen SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0`). Lý do: Google đã chuyển SDK cũ (`play-services-ads` 25.5.0) sang **maintenance mode**. Consent dùng **Google UMP 4.0.0**.
3. **Kiến trúc:** giữ `:ads` làm *seam* (interface + no-op, **không** phụ thuộc SDK nào), đúng tinh thần TSP-5. Thêm module mới `:ads-admob` chứa implementation, bind qua Hilt. `:app` không bao giờ import type của SDK.
4. **Nguyên tắc UX bất di bất dịch:** không bao giờ hiện quảng cáo full-screen (App Open, Interstitial) khi audio đang phát, đang buffer, hoặc khi user vừa bấm Play / mở từ media notification. Không load-rồi-show ngay (lỗi của bản AppLovin hiện tại).
5. **Rollout:** Phase 0 gỡ SDK cũ (merge TSP-5, rồi gỡ thêm Facebook + IMA) → Phase 1 seam + AdMob sau kill switch → Phase 2 staged rollout banner + native → Phase 3 A/B App Open / Interstitial / Rewarded qua Remote Config → Phase 4 mediation bidding.

---

## 1. Kiểm kê hiện trạng (inventory)

### 1.1 Các vị trí đặt quảng cáo (placement) đang có trên `main`

| # | File:line | Định dạng | Trigger | Tần suất / logic hiện tại | Nhận xét |
|---|---|---|---|---|---|
| P1 | `app/src/main/java/tss/t/podcast/App.kt:59` → `ads/.../ApplovinSdkWrapper.kt:43-80` | Khởi tạo SDK | `Application.onCreate` | 1 lần/process. Đăng ký 6 ad unit (`NativeLib.get*Id()`), `setExceptionHandlerEnabled(true)`, test device cứng `TEST_DEVICE_1` (`ApplovinSdkWrapper.kt:118`) | Init trên main thread. Ad unit id + SDK key nằm trong `securedToken/src/main/cpp/securedtoken.cpp:82-145`. |
| P2 | `app/src/main/java/tss/t/podcast/MainActivity.kt:195` (`onResume`) → `ApplovinSdkWrapper.kt:88` → `AppOpenAdManager.kt:41-53` | **App Open** (`MaxAppOpenAd`) | Mỗi lần `MainActivity.onResume` | Chặn khi: chưa xong onboarding / chưa chọn category (`AppOpenAdManager.kt:27-28`), `BuildConfig.DEBUG`, `ads_enable=false`. Throttle 10 phút **trong bộ nhớ** (`lastShowAd`, mất khi process chết). **Load xong là show ngay** (`onAdLoaded → showAd()`, `AppOpenAdManager.kt:55-56`). | Vi phạm guideline App Open: ad có thể bật lên *sau khi* user đã thao tác. `onResume` còn bắn sau dialog permission, share sheet, khi quay về từ chính ad, khi bấm media notification. Không kiểm tra audio đang phát. `onAdDisplayFailed → loadAd() → onAdLoaded → show` có thể tạo vòng lặp. |
| P3 | `app/.../ui/screens/discorver/DiscoverPodcastsScreen.kt:224-227` (`ITEM_AD_BANNER_1`) | Banner 320x50 (`MaxAdViewComposable`) | Item trong `LazyColumn` Discover, ngay sau hàng "Kho nhạc" | Auto-refresh theo cấu hình MAX. Tạo `MaxAdView` mới mỗi lần item vào composition và `destroy()` khi ra (`BannerAdsManager.kt:114-138`) | Cuộn lên xuống liên tục sẽ tạo request mới mỗi lần. |
| P4 | `DiscoverPodcastsScreen.kt:304-308` | Banner 320x50 | Inline sau podcast yêu thích thứ 2 (`it == 1`) | Như P3 | Hai banner trên cùng một màn hình, mật độ cao. |
| P5 | `app/.../ui/screens/favourite/FavouriteScreen.kt:84` | Banner 320x50 | Item đầu danh sách Yêu thích | Như P3 | |
| P6 | `app/.../ui/screens/player/widgets/SlideArea.kt:262` | Banner 320x50 | Trong panel danh sách tập ở **màn Player**, phía trên `LazyColumn` | Như P3, hiển thị **trong lúc đang phát** | Banner không chiếm âm thanh nên không vi phạm, nhưng đây là màn hình UX lõi. |
| P7 | `app/.../ui/screens/podcastsdetail/PodcastDetailScreen.kt:418-424` (`ITEM_AD_BANNER`) | Banner 320x50 | Item ngay dưới header Podcast Detail | Như P3 | |
| P8 | `app/.../ui/screens/search/SearchScreen.kt:173` | Banner 320x50 | Cố định dưới `SearchModeFilter`, trên kết quả | Như P3 | |
| P9 | `app/.../ui/screens/podcastsdetail/PodcastViewModel.kt:71-112` (tạo) + `PodcastDetailScreen.kt:486,500-501` (render) | **Native** template MAX (Medium 300dp / Small 150dp) | Chèn vào danh sách tập của Podcast Detail | Khoảng 1 ad / 8 tập, **vị trí ngẫu nhiên**, kích thước ngẫu nhiên 50/50 (`Random.nextInt(0,2)`). Load trong `LaunchedEffect` khi item hiện. Destroy khi đổi podcast (`:53`, `:121`) và `onCleared` (`:194-198`) | Đây là gốc của ANR (vòng retry vô hạn, đã fix ở P1 #13) và crash `item as Episode` (P0). `listRenderItems: List<Any>`. |
| P10 | `ads/src/main/java/tss/t/ads/NativeAdsManager.kt:56-105` | Native (manual binder) | Không ai gọi | Dead code | Xóa. |
| P11 | `NativeLib.getAdInterstitialId()` (`securedToken/.../NativeLib.kt:21`) | Interstitial | Chỉ đăng ký lúc init, **không có placement** | n/a | Hiện **không có interstitial**. |

### 1.2 SDK, build, manifest, cấu hình

| Hạng mục | Vị trí | Ghi chú |
|---|---|---|
| AppLovin SDK | `gradle/libs.versions.toml:7,65`; `app/build.gradle.kts:172`; `ads/build.gradle.kts:59` | `com.applovin:applovin-sdk:13.6.4` |
| AppLovin Quality Service plugin (SafeDK) | `build.gradle.kts:4` (classpath); `app/build.gradle.kts:24` (`id("applovin-quality-service")`); `gradle/libs.versions.toml:6,66` | Instrument bytecode lúc build |
| **AppLovin API key hard-code trong git** | `app/build.gradle.kts:27-29` (`applovin { apiKey = "avJk…" }`) | **Phải revoke/rotate trong dashboard AppLovin.** Key vẫn nằm trong lịch sử git kể cả sau TSP-5. |
| Maven repo AppLovin | `settings.gradle.kts:12` | |
| R8 rule riêng cho AppLovin | `app/proguard-rules.pro:37-40` (`-dontwarn com.amazon.privacypass.**`) | |
| **Facebook Android SDK** (bản full: core + login + share + gaming + applinks) | `gradle/libs.versions.toml:10,67`; `app/build.gradle.kts:173` | Được thêm ở commit `2cea6ee` ("Fix crash"). **Không có code Kotlin nào gọi.** Mặc định SDK tự khởi tạo, tự log app event (`AutoLogAppEventsEnabled`) và thu Advertising ID (`AdvertiserIDCollectionEnabled`). Tức là đang gửi dữ liệu sang Meta mà không có consent. **Không có Meta Audience Network** (`audience-network-sdk`) trong repo. |
| Meta app id | `app/src/main/AndroidManifest.xml:62-64` (`com.facebook.sdk.ApplicationId`); `app/src/main/res/values/strings.xml:2` (`facebook_app_id = 1177427693996135`) | |
| Google IMA (qua Media3) | `gradle/libs.versions.toml:52`; `app/build.gradle.kts:138`; `sharedPlayer/build.gradle.kts:77` | `media3-exoplayer-ima`, không có `ImaAdsLoader` nào được dùng. Đây là một ad SDK thừa, nên gỡ. |
| Ad unit id / SDK key | `securedToken/src/main/cpp/securedtoken.cpp:82-145`, `NativeLib.kt:18-24` | Ad unit id của AdMob **không phải bí mật** (xuất hiện trong mọi request). Không cần giấu trong NDK. |
| Remote Config | `ads/.../AdsConstants.kt:29` `ads_enable` | Key duy nhất. Đọc ở `AppOpenAdManager.kt:42`, `BannerAdsManager.kt:57` (chỉ đọc 1 lần trong `remember`), `NativeAdLoader.kt:141`. **Không có `setDefaultsAsync`** (`sharedFirebase/.../TSFirebaseRemoteConfig.kt`), nên giá trị là `false` cho tới lần fetch thành công đầu tiên. Thực tế session đầu tiên không có ad. |
| Analytics quảng cáo | `AdsConstants.kt:5-27` (event `applovin_*`) | Không log doanh thu: `MaxAdRevenueListener {}` rỗng (`NativeAdLoader.kt:55`). Không có `ad_impression` kèm value. |
| Consent / UMP | — | **Không có.** Không có CMP, không có màn hình privacy options. |
| `app-ads.txt` | — | **Không có** (repo chưa có `web/`, chưa có website developer). |
| Manifest `AD_ID` | — | Không khai báo tường minh. Được merge vào từ `play-services-measurement` (Firebase Analytics) và Facebook SDK. |
| Retry banner | `BannerAdsManager.kt:158-194` | `errorHandle(..., callback = {})`: callback mặc định rỗng, nên "retry" thực ra không làm gì. |

Đã grep `podcasts/`, `featureRadio/`, `coreRadio/`, `featureOnboarding/`, `sharedPlayer/`, `core*/`: **không có call site quảng cáo nào khác** ngoài các mục trên.

### 1.3 Nhánh `origin/tuandv/TSP-5-remove-applovin` (`806dbe2`, chưa merge)

`git diff main...origin/tuandv/TSP-5-remove-applovin`: 28 file, +87/−1089.

- Gỡ AppLovin SDK, plugin quality service, classpath, block `applovin { apiKey }`, maven repo, entry version catalog, rule R8, `.safedk/`. Xóa 10 file implementation trong `:ads`.
- Giữ `:ads` thành **no-op seam** `ads/src/main/java/tss/t/ads/AdsSeam.kt`: `AdsManager { initSdk(); loadOpenAds() }`, `BannerAdsManager { destroy() }`, `LocalBannerAdsManagerScope`, `@Composable AdBannerSlot(modifier)`, `@Composable AdNativeSlot(modifier)`. Cả hai composable đều không render gì.
- Đổi `MaxAdViewComposable` thành `AdBannerSlot()` ở 6 call site. Bỏ 4 chỗ `LocalAnalyticsScope.current!!`.
- `PodcastViewModel.generateItemList` thành passthrough (chỉ còn episode), `listRenderItems` vẫn để `List<Any>`.
- **Không đụng tới Facebook SDK và IMA.** Kế hoạch này gỡ nốt hai thứ đó.

**Kết luận:** TSP-5 là bước 0 hợp lý. Plan dưới đây **xây tiếp trên TSP-5**, không viết lại.

---

## 2. Thiết kế placement AdMob mục tiêu

### 2.1 Nguyên tắc chung (policy + UX)

Nguồn chính sách:
- Google Play: chính sách Ads / Better Ads Experiences. Cấm interstitial full-screen bất ngờ, cấm full-screen trước splash, interstitial phải đóng được sau 15 giây (trừ opt-in) — https://support.google.com/googleplay/android-developer/answer/9857753 , https://support.google.com/googleplay/android-developer/answer/12271244
- AdMob: interstitial chỉ đặt ở *điểm chuyển tự nhiên*, không đặt khi app load hay thoát, không đặt khi app chạy nền — https://support.google.com/admob/answer/6201362 , https://support.google.com/admob/answer/6066980
- AdMob: không để ad đè lên hoặc xuất hiện bất ngờ trên nội dung — https://support.google.com/admob/answer/6293636
- App Open: chỉ khi mở app hoặc quay lại app, phải xuất hiện **trước** khi user tương tác, không đặt cạnh ad khác, không dùng cho app thuộc Designed for Families — https://support.google.com/admob/answer/9341964

Quy tắc cứng cho TSPodcast. Mỗi quy tắc sẽ thành một unit test của `AdPolicy`:

1. **Audio-first:** không hiện App Open / Interstitial khi `TSMediaController.sessionController?.isPlaying == true`, khi đang buffering, trong vòng 10 giây sau khi user bấm Play, khi đang ở màn Player, hoặc khi Activity được mở từ media notification (`tss.t.action_from_notification`) hay deep link.
2. **Không load-then-show:** full-screen chỉ show nếu ad **đã preload sẵn** tại thời điểm trigger. Không có ad sẵn thì bỏ qua, không chờ.
3. **Không có ad trong N giờ đầu sau cài đặt** (`ads_grace_period_hours`, mặc định 24h) và không có full-screen trước khi xong onboarding + chọn category (giữ logic `AppOpenAdManager.kt:27-28`).
4. Không bao giờ có 2 full-screen liên tiếp: khoảng cách tối thiểu giữa App Open và Interstitial là 60 giây.
5. Banner/native **không đặt sát nút điều khiển** (Play/Pause, MiniPlayer, bottom nav) để tránh click nhầm (invalid traffic).
6. Native phải có nhãn "Quảng cáo"/"Ad" và AdChoices hiển thị rõ.
7. Chỉ request ad khi `ConsentInformation.canRequestAds() == true`.
8. Video ad trong native: `startMuted = true`.

### 2.2 Bảng placement đích

| Placement key | Định dạng AdMob | Thay cho | Trigger | Tần suất mặc định (Remote Config) | Ghi chú |
|---|---|---|---|---|---|
| `app_open_warm` | App Open | P2 | `ProcessLifecycleOwner` `ON_START` sau khi app ở nền ≥ `ads_app_open_min_background_sec` (30s) | ≥ 60 phút giữa 2 lần, tối đa 3/ngày. Bỏ qua 2 session đầu. Ad hết hạn sau 4 giờ (Google). | Dùng process lifecycle thay `Activity.onResume`, nên không bắn sau dialog permission, share sheet hay khi đóng ad. Áp dụng quy tắc Audio-first. |
| `app_open_cold` | App Open | — | Cold start, chỉ khi có màn loading/splash | **Tắt mặc định** (`ads_app_open_cold_enabled=false`) | App **chưa có** SplashScreen API (không có `installSplashScreen`). Muốn bật phải thêm `androidx.core:core-splashscreen` + `setKeepOnScreenCondition` tối đa 3s. Hết 3s mà chưa có ad thì vào thẳng app (Google: user đã thấy nội dung thì không show). Không bao giờ show ở lần mở đầu tiên. |
| `discover_inline` | Native (medium) | P3 | Item sau hàng "Kho nhạc" | 1 slot | Native hợp feed hơn banner. Banner inline adaptive là fallback. |
| `discover_fav_inline` | — (bỏ) | P4 | — | — | Bỏ để giảm mật độ (2 ad/màn hình). Có thể bật lại qua RC nếu A/B cho thấy retention không đổi. |
| `favourite_top` | Inline adaptive banner | P5 | Item đầu danh sách | Refresh 60s (cấu hình trên AdMob UI) | |
| `player_episode_list` | Inline adaptive banner | P6 | Panel danh sách tập trong Player | **Tắt mặc định**, A/B ở Phase 3 | Banner không có âm thanh nên hợp lệ, nhưng màn Player là UX lõi. |
| `podcast_detail_header` | Inline adaptive banner | P7 | Dưới header | Refresh 60s | |
| `podcast_detail_episodes` | Native (small/medium cố định theo RC) | P9 | Chèn **xác định** (không random) vào danh sách tập | Vị trí đầu `ads_native_first_index`=3, sau đó mỗi `ads_native_interval`=8 tập, tối đa `ads_native_max_per_list`=3 | Preload theo pool (`NativeAdLoader.load(request, numberOfAds ≤ 5)`). Item list có kiểu sealed (xem §3.4), hết `List<Any>`. |
| `search_results` | Native small | P8 | Sau kết quả thứ 5 | 1 slot / trang kết quả | Không đặt trên ô search hay filter. |
| `discover_collapsible` | Collapsible banner (`collapsible=bottom`) | — | 1 lần / session ở Discover | **Tắt mặc định** | Chỉ có Google demand (mediation hiển thị như banner thường). Refresh tự động sẽ không collapsible nữa (theo docs). |
| `interstitial_nav` | Interstitial | — (mới) | Back từ Podcast Detail về Discover/Search, **sau khi** user xem ≥ `ads_interstitial_every_n`=3 podcast | Tối thiểu 180s giữa 2 lần, ≤ 2/session, ≤ 6/ngày, 0 ở session đầu | **Tắt mặc định**, chỉ bật qua A/B. Tuyệt đối không show khi bấm Play, khi đang phát, khi mở app hay thoát app. |
| `rewarded_ad_free` | Rewarded | — (mới) | User chủ động bấm "Xem 1 quảng cáo để nghe không quảng cáo 2 giờ" (Settings + CTA nhỏ cạnh banner) | ≤ 3/ngày | Opt-in nên được miễn quy tắc 15 giây. Nếu đang phát: hiện dialog "Phát sẽ tạm dừng trong khi xem quảng cáo", pause, xem xong thì resume. Reward = tắt banner/native/interstitial/app open trong `ads_rewarded_ad_free_minutes`=120. |
| `rewarded_feature` | Rewarded | — (tương lai) | Mở khóa tính năng "premium" (vd. tải offline, hẹn giờ ngủ nâng cao, tốc độ > 2x) | Theo tính năng | App **chưa có** tính năng premium/billing. Owner cần quyết danh sách (xem §11). Nếu reward có giá trị kinh tế thì bật Server-Side Verification. |

### 2.3 Luồng App Open (warm start)

```
ProcessLifecycleOwner ON_STOP  -> lưu backgroundAt
ProcessLifecycleOwner ON_START ->
   if !consent.canRequestAds            -> skip
   if adFree(rewarded) active           -> skip
   if now - installAt < grace           -> skip
   if session < 3                       -> skip
   if now - backgroundAt < 30s          -> skip
   if now - lastFullScreen < 60m        -> skip
   if todayCount >= 3                   -> skip
   if player.isPlaying || buffering     -> skip
   if launchedFromNotificationOrDeepLink-> skip
   if currentRoute == Player            -> skip
   ad = AppOpenAdPreloader.pollAd(id) ?: skip   // chỉ dùng ad đã preload
   ad.show(activity)
```

Đọc `isPlaying` từ `TSMediaController.sessionController` (`sharedPlayer/.../TSMediaController.kt:22`). Seam nhận một `PlaybackStateProvider` để `:ads` không phụ thuộc `:sharedPlayer`.

---

## 3. API của module `:ads` (seam) và `:ads-admob` (implementation)

### 3.1 Cấu trúc module

```
:ads              (Android library, KHÔNG phụ thuộc ad SDK nào)
  - AdsController (interface), AdPlacement, AdsState, RewardResult
  - AdBannerSlot / AdNativeSlot composables -> uỷ quyền cho LocalAdRenderer
  - NoOpAdsController, NoOpAdRenderer (mặc định = hành vi của TSP-5)
  - AdPolicy (pure Kotlin: frequency cap, grace, audio-first)  <- unit test
  - AdsConfig (đọc Remote Config qua ConfigAPI)
:ads-admob        (Android library, implementation(ads-mobile-sdk), implementation(ump))
  - AdMobAdsController : AdsController
  - AdMobAdRenderer   : AdRenderer
  - ConsentManager (UMP)
  - Hilt module @Binds AdsController -> AdMobAdsController
:app -> implementation(project(":ads")), implementation(project(":ads-admob"))
```

Vì `:ads-admob` khai báo SDK bằng `implementation` (không phải `api`), `:app` **không thể** import `com.google.android.libraries.ads.*`. Ranh giới này do compiler kiểm tra. Sau này nếu muốn một flavor không quảng cáo (vd. bản F-Droid), chỉ cần bỏ dependency `:ads-admob` và Hilt sẽ dùng binding no-op.

### 3.2 Seam (`:ads`). Tương thích ngược với `AdsSeam.kt` của TSP-5

```kotlin
// ads/src/main/java/tss/t/ads/AdsApi.kt
package tss.t.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

enum class AdPlacement(val key: String, val format: AdFormat) {
    AppOpenWarm("app_open_warm", AdFormat.AppOpen),
    AppOpenCold("app_open_cold", AdFormat.AppOpen),
    DiscoverInline("discover_inline", AdFormat.Native),
    FavouriteTop("favourite_top", AdFormat.Banner),
    PlayerEpisodeList("player_episode_list", AdFormat.Banner),
    PodcastDetailHeader("podcast_detail_header", AdFormat.Banner),
    PodcastDetailEpisodes("podcast_detail_episodes", AdFormat.Native),
    SearchResults("search_results", AdFormat.Native),
    DiscoverCollapsible("discover_collapsible", AdFormat.CollapsibleBanner),
    InterstitialNav("interstitial_nav", AdFormat.Interstitial),
    RewardedAdFree("rewarded_ad_free", AdFormat.Rewarded),
}

enum class AdFormat { AppOpen, Banner, CollapsibleBanner, Native, Interstitial, Rewarded }

sealed interface AdsState {
    data object Idle : AdsState
    data object GatheringConsent : AdsState
    data class Ready(val canRequestAds: Boolean) : AdsState
    data class Disabled(val reason: String) : AdsState      // kill switch, ad-free, ...
}

sealed interface RewardResult {
    data class Earned(val type: String, val amount: Int) : RewardResult
    data object Dismissed : RewardResult
    data object NotAvailable : RewardResult
}

/** Abstraction duy nhất mà :app được phép biết. Không có type nào của SDK. */
interface AdsController {
    val state: StateFlow<AdsState>
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Gọi 1 lần từ Activity đầu tiên: UMP -> init SDK -> preload. Idempotent. */
    fun start(activity: Activity)

    /** Interstitial ở điểm chuyển tự nhiên. Trả false nếu bị policy chặn hoặc chưa có ad. */
    fun maybeShowInterstitial(activity: Activity, placement: AdPlacement): Boolean

    suspend fun showRewarded(activity: Activity, placement: AdPlacement): RewardResult

    fun showPrivacyOptions(activity: Activity)

    // --- Tương thích TSP-5 (AdsManager.initSdk/loadOpenAds). Xoá sau khi migrate call site. ---
    @Deprecated("Dùng start(activity)") fun initSdk() = Unit
    @Deprecated("App Open do ProcessLifecycleOwner điều khiển") fun loadOpenAds() = Unit
}

/** Cho phép :ads hỏi trạng thái phát mà không phụ thuộc :sharedPlayer. :app cung cấp impl. */
fun interface PlaybackStateProvider { fun isPlayingOrBuffering(): Boolean }

/** Renderer cho composable. :ads-admob cung cấp impl thật. */
interface AdRenderer {
    @Composable fun Banner(placement: AdPlacement, modifier: Modifier)
    @Composable fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier)
}

object NoOpAdRenderer : AdRenderer {
    @Composable override fun Banner(placement: AdPlacement, modifier: Modifier) = Unit
    @Composable override fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier) = Unit
}

val LocalAdRenderer = staticCompositionLocalOf<AdRenderer> { NoOpAdRenderer }

// Giữ tên của TSP-5, thêm tham số placement. Mặc định null -> không render (an toàn).
@Composable
fun AdBannerSlot(modifier: Modifier = Modifier, placement: AdPlacement? = null) {
    if (placement != null) LocalAdRenderer.current.Banner(placement, modifier)
}

@Composable
fun AdNativeSlot(modifier: Modifier = Modifier, placement: AdPlacement? = null, slotKey: String = "0") {
    if (placement != null) LocalAdRenderer.current.Native(placement, slotKey, modifier)
}
```

`AdsManager` của TSP-5 được thay bằng `AdsController` (binding mặc định `NoOpAdsController`). Call site trong `App.kt` và `MainActivity.kt` đổi thành `adsController.start(this)` gọi từ `MainActivity.onCreate`, vì UMP cần một Activity.

### 3.3 Chính sách tần suất (pure Kotlin, test được)

```kotlin
// ads/src/main/java/tss/t/ads/AdPolicy.kt
class AdPolicy(
    private val config: AdsConfig,
    private val store: AdCounterStore,          // DataStore/SharedPref: installAt, sessionCount, lastShown, dailyCount
    private val playback: PlaybackStateProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun canShowFullScreen(placement: AdPlacement, ctx: TriggerContext): Decision {
        val now = clock()
        return when {
            !config.adsEnabled || !config.isEnabled(placement) -> Decision.Block("kill_switch")
            store.adFreeUntil > now -> Decision.Block("rewarded_ad_free")
            now - store.installAt < config.graceHours.hours.inWholeMilliseconds -> Decision.Block("grace")
            playback.isPlayingOrBuffering() -> Decision.Block("audio_playing")
            ctx.fromNotificationOrDeepLink -> Decision.Block("deeplink")
            ctx.currentRoute == "Player" -> Decision.Block("player_screen")
            now - store.lastFullScreenAt < config.minGapBetweenFullScreenMs -> Decision.Block("gap")
            store.todayCount(placement) >= config.dailyCap(placement) -> Decision.Block("daily_cap")
            store.sessionCount(placement) >= config.sessionCap(placement) -> Decision.Block("session_cap")
            else -> Decision.Allow
        }
    }
}
```

Mỗi lần `Block` sẽ log event `ad_blocked{placement, reason}` với sampling 10% để biết cap nào đang cắt bao nhiêu inventory.

### 3.4 Native trong danh sách: thay `List<Any>`

```kotlin
// app: PodcastViewModel
sealed interface EpisodeRow {
    data class Item(val episode: Episode) : EpisodeRow
    data class Ad(val slotKey: String) : EpisodeRow           // key ổn định "ad-<index>"
}

fun buildRows(episodes: List<Episode>, cfg: NativeInsertConfig): List<EpisodeRow> = buildList {
    var adCount = 0
    episodes.forEachIndexed { i, ep ->
        add(EpisodeRow.Item(ep))
        val pos = i + 1
        val isSlot = pos == cfg.firstIndex ||
            (pos > cfg.firstIndex && (pos - cfg.firstIndex) % cfg.interval == 0)
        if (cfg.enabled && isSlot && adCount < cfg.maxPerList) add(EpisodeRow.Ad("ad-${adCount++}"))
    }
}
// Screen: when (row) { is Item -> EpisodeWidget(...); is Ad -> AdNativeSlot(placement = PodcastDetailEpisodes, slotKey = row.slotKey) }
```

Vị trí xác định nên `LazyColumn` key ổn định. Không còn trường hợp `item as Episode` gặp phải ad. Khi không có fill, slot render chiều cao 0.

### 3.5 Implementation AdMob (`:ads-admob`). Phác thảo theo GMA Next-Gen SDK 1.6.0

> Lưu ý: Next-Gen SDK **gọi callback trên background thread**, nên mọi thao tác UI phải chuyển sang Main. Tên API dưới đây lấy theo docs Next-Gen (quick-start, banner, native, app-open, interstitial, rewarded). Khi implement cần đối chiếu lại reference vì SDK còn mới.

**Consent + init**

```kotlin
// ads-admob/src/main/java/tss/t/ads/admob/ConsentManager.kt
class ConsentManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val info = UserMessagingPlatform.getConsentInformation(context)
    val canRequestAds: Boolean get() = info.canRequestAds()
    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gather(activity: Activity, onDone: (FormError?) -> Unit) {
        val params = ConsentRequestParameters.Builder().apply {
            if (BuildConfig.DEBUG) setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    // EEA | REGULATED_US_STATE | OTHER, đổi qua Remote Config debug hoặc build param
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASHED_ID)
                    .build()
            )
        }.build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err -> onDone(err) }
        }, { err -> onDone(err) })   // Lỗi mạng: UMP dùng trạng thái consent của phiên trước
    }

    fun showPrivacyOptions(activity: Activity, onDone: (FormError?) -> Unit) =
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDone(it) }
}
```

```kotlin
// ads-admob/src/main/java/tss/t/ads/admob/AdMobAdsController.kt
@Singleton
class AdMobAdsController @Inject constructor(
    @ApplicationContext private val app: Context,
    private val consent: ConsentManager,
    private val config: AdsConfig,
    private val policy: AdPolicy,
    private val analytics: AdsAnalytics,          // map -> TSAnalytics, không phụ thuộc SDK type
    @AdsScope private val scope: CoroutineScope,  // SupervisorJob + Dispatchers.Default
) : AdsController, DefaultLifecycleObserver {

    private val _state = MutableStateFlow<AdsState>(AdsState.Idle)
    override val state = _state.asStateFlow()
    private val _privacy = MutableStateFlow(false)
    override val privacyOptionsRequired = _privacy.asStateFlow()
    private val initialized = AtomicBoolean(false)
    private var currentActivity: WeakReference<Activity>? = null

    override fun start(activity: Activity) {
        currentActivity = WeakReference(activity)
        if (!config.adsEnabled) { _state.value = AdsState.Disabled("kill_switch"); return }
        _state.value = AdsState.GatheringConsent
        consent.gather(activity) { err ->
            if (err != null) analytics.consentError(err.errorCode)
            _privacy.value = consent.privacyOptionsRequired
            // Khởi tạo khi canRequestAds (có thể đã true từ phiên trước, trước cả khi form hiện xong)
            if (consent.canRequestAds) initSdkOnce()
            _state.value = AdsState.Ready(consent.canRequestAds)
        }
        if (consent.canRequestAds) initSdkOnce()
    }

    private fun initSdkOnce() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {                 // Bắt buộc background (tránh ANR)
            val initConfig = InitializationConfig.Builder(config.admobAppId)
                .setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(BuildConfig.ADMOB_TEST_DEVICE_IDS.toList())
                        .build()
                )
                .build()
            MobileAds.initialize(app, initConfig) {
                startPreloaders()
                ProcessLifecycleOwner.get().lifecycle.let { lc ->
                    scope.launch(Dispatchers.Main) { lc.addObserver(this@AdMobAdsController) }
                }
            }
        }
    }

    private fun startPreloaders() {
        config.unitId(AdPlacement.AppOpenWarm)?.let { id ->
            AppOpenAdPreloader.start(id, PreloadConfiguration(AdRequest.Builder(id).build()))
        }
        if (config.isEnabled(AdPlacement.InterstitialNav)) config.unitId(AdPlacement.InterstitialNav)?.let { id ->
            InterstitialAdPreloader.start(id, PreloadConfiguration(AdRequest.Builder(id).build()))
        }
        if (config.isEnabled(AdPlacement.RewardedAdFree)) config.unitId(AdPlacement.RewardedAdFree)?.let { id ->
            RewardedAdPreloader.start(id, PreloadConfiguration(AdRequest.Builder(id).build()))
        }
    }

    // ----- App Open (warm start) -----
    private var backgroundAt = 0L
    override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
    override fun onStart(owner: LifecycleOwner) {
        val activity = currentActivity?.get() ?: return
        val ctx = TriggerContext.from(activity, backgroundAt)
        if (policy.canShowFullScreen(AdPlacement.AppOpenWarm, ctx) !is Decision.Allow) return
        val id = config.unitId(AdPlacement.AppOpenWarm) ?: return
        val ad = AppOpenAdPreloader.pollAd(id) ?: return          // không chờ load
        ad.adEventCallback = fullScreenCallback(AdPlacement.AppOpenWarm)
        scope.launch(Dispatchers.Main) { ad.show(activity) }
    }

    // ----- Interstitial -----
    override fun maybeShowInterstitial(activity: Activity, placement: AdPlacement): Boolean {
        if (policy.canShowFullScreen(placement, TriggerContext.from(activity)) !is Decision.Allow) return false
        val ad = config.unitId(placement)?.let(InterstitialAdPreloader::pollAd) ?: return false
        ad.adEventCallback = fullScreenCallback(placement)
        scope.launch(Dispatchers.Main) { ad.show(activity) }
        return true
    }

    // ----- Rewarded -----
    override suspend fun showRewarded(activity: Activity, placement: AdPlacement): RewardResult {
        val ad = config.unitId(placement)?.let(RewardedAdPreloader::pollAd) ?: return RewardResult.NotAvailable
        return suspendCancellableCoroutine { cont ->
            var earned: RewardResult = RewardResult.Dismissed
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() { if (cont.isActive) cont.resume(earned) {} }
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    if (cont.isActive) cont.resume(RewardResult.NotAvailable) {}
                }
                override fun onAdPaid(value: AdValue) = analytics.paid(placement, value.valueMicros, value.currencyCode, value.precisionType.name)
            }
            scope.launch(Dispatchers.Main) {
                ad.show(activity) { item -> earned = RewardResult.Earned(item.type, item.amount) }
            }
        }
    }

    override fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity) {
        _privacy.value = consent.privacyOptionsRequired
    }

    private fun fullScreenCallback(p: AdPlacement) = object : AppOpenAdEventCallback /* hoặc InterstitialAdEventCallback */ {
        override fun onAdShowedFullScreenContent() { policy.recordShown(p) }
        override fun onAdImpression() = analytics.impression(p)
        override fun onAdClicked() = analytics.click(p)
        override fun onAdPaid(value: AdValue) = analytics.paid(p, value.valueMicros, value.currencyCode, value.precisionType.name)
    }
}
```

**Banner adaptive (Compose)**

```kotlin
// ads-admob/src/main/java/tss/t/ads/admob/AdMobAdRenderer.kt
class AdMobAdRenderer @Inject constructor(
    private val config: AdsConfig,
    private val consent: ConsentManager,
    private val analytics: AdsAnalytics,
    private val nativePool: NativeAdPool,
) : AdRenderer {

    @Composable
    override fun Banner(placement: AdPlacement, modifier: Modifier) {
        if (!config.isEnabled(placement) || !consent.canRequestAds) return
        val adUnitId = config.unitId(placement) ?: return
        val context = LocalContext.current
        val widthDp = LocalConfiguration.current.screenWidthDp
        var loaded by remember { mutableStateOf(false) }
        val adView = remember(placement) { AdView(context) }

        LaunchedEffect(placement, widthDp) {
            val size = if (placement.format == AdFormat.Banner)
                AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)   // inline trong list
            else AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)           // anchored / collapsible
            val req = BannerAdRequest.Builder(adUnitId, size).apply {
                if (placement.format == AdFormat.CollapsibleBanner)
                    setGoogleExtrasBundle(Bundle().apply { putString("collapsible", "bottom") })
            }.build()
            adView.loadAd(req, object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdImpression() = analytics.impression(placement)
                        override fun onAdClicked() = analytics.click(placement)
                        override fun onAdPaid(value: AdValue) =
                            analytics.paid(placement, value.valueMicros, value.currencyCode, value.precisionType.name)
                    }
                    loaded = true          // State snapshot là thread-safe; Compose sẽ recompose trên Main
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    analytics.loadFailed(placement, adError.code)  // không tự retry: refresh do AdMob UI quản lý
                }
            })
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }

        AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth().then(if (loaded) Modifier.wrapContentHeight() else Modifier.height(0.dp)),
        )
    }

    @Composable
    override fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier) {
        if (!config.isEnabled(placement) || !consent.canRequestAds) return
        val ad by nativePool.adFor(placement, slotKey).collectAsState(initial = null)
        ad?.let { NativeAdCard(it, modifier) }   // NativeAdView + registerNativeAd(ad, mediaView); nhãn "Quảng cáo"
    }
}
```

> Banner inline nằm trong `LazyColumn` sẽ bị dispose khi cuộn khỏi màn hình. Để tránh request lặp như P3–P8 hiện tại, giữ `AdView` trong một `BannerCache` theo `placement`, scope theo `NavBackStackEntry` (ViewModel), và chỉ `destroy()` khi rời màn hình.

**Native pool**

```kotlin
@ActivityRetainedScoped
class NativeAdPool @Inject constructor(private val config: AdsConfig, private val analytics: AdsAnalytics) {
    private val ads = ConcurrentHashMap<String, MutableStateFlow<NativeAd?>>()

    fun adFor(p: AdPlacement, slot: String): StateFlow<NativeAd?> =
        ads.getOrPut("${p.key}/$slot") { MutableStateFlow<NativeAd?>(null).also { load(p, it) } }

    private fun load(p: AdPlacement, sink: MutableStateFlow<NativeAd?>) {
        val id = config.unitId(p) ?: return
        val req = NativeAdRequest.Builder(id, listOf(NativeAd.NativeAdType.NATIVE))
            // video trong native: startMuted = true (VideoOptions)
            .build()
        NativeAdLoader.load(req, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) { sink.value = nativeAd }
            override fun onAdFailedToLoad(adError: LoadAdError) = analytics.loadFailed(p, adError.code)
        })
    }

    fun clear() { ads.values.forEach { it.value?.destroy() }; ads.clear() }   // gọi khi ViewModel onCleared
}
```

Dùng helper Compose chính thức (`JetpackComposeDemo/.../compose_util` trong `googleads/googleads-mobile-android-examples`) cho `NativeAdView`, `NativeAdAttribution` và `MediaView`, thay vì tự viết binder XML.

### 3.6 Analytics quảng cáo

- **Bỏ** toàn bộ event `applovin_*` (`AdsConstants.kt`).
- **Link app AdMob với Firebase** (AdMob UI → App settings → Link to Firebase). GA4 sẽ tự log `ad_impression` kèm `value`/`currency` cho AdMob. Đây là nguồn ARPDAU/LTV cho dashboard (xem `docs/in-progress/plans/seo-web-dashboard.md`).
- Event bổ sung (tên snake_case, ≤ 40 ký tự, param `placement`, `format`): `ads_request`, `ads_load_failed{code}`, `ads_show`, `ads_click`, `ads_blocked{reason}` (sample 10%), `ads_rewarded_earned`, `consent_status{status,geo}`.
- `onAdPaid` (`AdValue`): log thêm event riêng `ads_paid{value_micros,currency,precision}` để đối soát với AdMob API.

---

## 4. Thay đổi Gradle / Manifest chính xác

Phiên bản đã kiểm tra ngày 2026-10-29:
- GMA Next-Gen SDK **1.6.0** (phát hành 2026-10-22): https://developers.google.com/admob/android/next-gen/quick-start , https://developers.google.com/admob/android/next-gen/rel-notes
- GMA SDK legacy `play-services-ads` **25.5.0** (2026-09-17, đang **maintenance mode**): https://developers.google.com/admob/android/rel-notes , https://developers.google.com/admob/android/quick-start
- UMP **4.0.0** (2025-10-31, minSdk 23): https://developers.google.com/admob/android/privacy/release-notes
- Yêu cầu Next-Gen: minSdk 24, compileSdk ≥ 35, Kotlin ≥ 1.9. Repo đang dùng minSdk 24, compileSdk 36, Kotlin 2.2.0, nên đã thỏa.

> Quyết định: dùng **Next-Gen 1.6.0**. Nếu tới lúc implement mà 1.6.0 lộ bug (bản mới ~1 tuần), lùi về 1.5.0 (2026-09-24). Phương án dự phòng là legacy `play-services-ads:25.5.0`. Seam giữ nguyên, chỉ `:ads-admob` thay đổi.

### 4.1 `gradle/libs.versions.toml`

```diff
 [versions]
-applovinqualityservicegradleplugin = "5.12.9"
-applovinSdk = "13.6.4"
-facebookAndroidSdk = "18.3.0"
+gmaNextGen = "1.6.0"
+ump = "4.0.0"
+coreSplashscreen = "1.0.1"   # chỉ khi bật App Open cold start (Phase 3)

 [libraries]
-androidx-media3-exoplayer-ima = { module = "androidx.media3:media3-exoplayer-ima", version.ref = "media3Exoplayer" }
-applovin-sdk = { module = "com.applovin:applovin-sdk", version.ref = "applovinSdk" }
-applovinqualityservicegradleplugin = { module = "com.applovin.quality:AppLovinQualityServiceGradlePlugin", version.ref = "applovinqualityservicegradleplugin" }
-facebook-android-sdk = { module = "com.facebook.android:facebook-android-sdk", version.ref = "facebookAndroidSdk" }
+google-ads-mobile-sdk = { module = "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk", version.ref = "gmaNextGen" }
+google-ump = { module = "com.google.android.ump:user-messaging-platform", version.ref = "ump" }
+androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
```

### 4.2 `settings.gradle.kts`, `build.gradle.kts` (root)

```diff
 // settings.gradle.kts (pluginManagement.repositories)
-        maven { url = uri("https://artifacts.applovin.com/android") }
 // build.gradle.kts (buildscript.dependencies)
-        classpath(libs.applovinqualityservicegradleplugin)
```

(Hai dòng này TSP-5 đã gỡ.)

### 4.3 `settings.gradle.kts`: thêm module

```diff
 include(":ads")
+include(":ads-admob")
```

### 4.4 `ads-admob/build.gradle.kts` (mới)

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "tss.t.ads.admob"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true; buildConfig = true }
}
// Next-Gen: mediation adapter vẫn kéo SDK legacy theo, nên phải loại để tránh trùng symbol
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
dependencies {
    implementation(project(":ads"))
    implementation(libs.google.ads.mobile.sdk)      // implementation: KHÔNG lộ type sang :app
    implementation(libs.google.ump)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
```

### 4.5 `ads/build.gradle.kts`

```diff
-    implementation(libs.applovin.sdk)        # TSP-5 đã gỡ
     # :ads giữ dependency thuần (compose, hilt, sharedLibrary/ConfigAPI). Không thêm SDK nào.
```

### 4.6 `app/build.gradle.kts`

```diff
 plugins {
-    id("applovin-quality-service")
 }
-applovin {
-    apiKey = "avJk…"            # và REVOKE key này trên dashboard AppLovin
-}
 android {
     defaultConfig {
+        // Test app id của Google; release ghi đè bên dưới
+        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
     }
     buildTypes {
         release {
+            manifestPlaceholders["admobAppId"] = providers.gradleProperty("ADMOB_APP_ID").get() // ca-app-pub-XXXXXXXX~YYYYYYYY
         }
     }
 }
+configurations.configureEach {
+    exclude(group = "com.google.android.gms", module = "play-services-ads")
+    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
+}
 dependencies {
-    implementation(libs.androidx.media3.exoplayer.ima)
-    implementation(libs.applovin.sdk)
-    implementation(libs.facebook.android.sdk)
+    implementation(project(":ads-admob"))
 }
```

`sharedPlayer/build.gradle.kts:77`: xóa `implementation(libs.androidx.media3.exoplayer.ima)`.

`ADMOB_APP_ID` lấy từ `gradle.properties` hoặc biến môi trường CI (GitHub Actions secret). Hiện CI đã inject keystore theo cách tương tự (`KEYSTORE_PASSWORD_INJECTION.md`).

### 4.7 `app/src/main/AndroidManifest.xml`

```diff
     <application ...>
-        <meta-data
-            android:name="com.facebook.sdk.ApplicationId"
-            android:value="@string/facebook_app_id" />
+        <!-- Next-Gen SDK nhận app id trong code, nhưng UMP vẫn bắt buộc tag này -->
+        <meta-data
+            android:name="com.google.android.gms.ads.APPLICATION_ID"
+            android:value="${admobAppId}" />
     </application>
```

- `app/src/main/res/values/strings.xml:2`: xóa `facebook_app_id`.
- `com.google.android.gms.permission.AD_ID`: **giữ** (được merge từ SDK). Khai báo "Advertising ID" trong Play Console.
- `app/proguard-rules.pro`: xóa rule AppLovin (TSP-5 đã làm). SDK Google tự mang consumer rules nên không cần thêm.

### 4.8 `securedToken`

Xóa `getApplovinKey`, `getAdAppOpenId`, `getAdBannerId`, `getAdInterstitialId`, `getNativeId`, `getNativeSmallId`, `getNativeMediumId` khỏi `NativeLib.kt` và `securedtoken.cpp`. Ad unit id mới đi qua `BuildConfig` (giá trị mặc định) và Remote Config (`ads_unit_ids`, có thể override).

---

## 5. Consent: Google UMP

| Khu vực | Cấu hình trong AdMob → Privacy & messaging | Hành vi trong app |
|---|---|---|
| EEA + UK + Thụy Sĩ | Message **European regulations** (GDPR, TCF v2.2, Google-certified CMP) | Form hiện lần đầu. `canRequestAds` false cho tới khi user chọn. Có nút "Tùy chọn quyền riêng tư" trong Settings khi `privacyOptionsRequirementStatus == REQUIRED`. |
| Các bang Mỹ có luật (CA, VA, CO, CT, …) | Message **US state regulations** | Opt-out "Do not sell/share". Nút privacy options trong Settings. |
| Việt Nam | — (UMP không có message riêng cho VN) | **Cần quyết định pháp lý:** Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 và Nghị định 356/2025/NĐ-CP có hiệu lực từ 01/01/2026, lấy **consent** làm cơ sở chính và không có "lợi ích hợp pháp" (https://www.rajahtannasia.com/viewpoints/new-decree-implementing-the-law-on-personal-data-protection-passed/ , https://vietanlaw.com/new-personal-data-protection-law-2025-in-vietnam). Phương án: màn consent riêng trong onboarding. Nếu user từ chối thì gửi request **non-personalized** (`extras npa=1`) hoặc bật Limited Ads. |
| Khác | — | `canRequestAds` = true sau `requestConsentInfoUpdate`. |

- Gọi `requestConsentInfoUpdate` **mỗi lần mở app** (theo Google). `canRequestAds()` trả false cho tới lần gọi đầu.
- Nếu đã cấu hình consent mode trong Privacy & messaging, UMP ≥ 3.2.0 tự gọi Firebase consent API, nên Firebase Analytics được đồng bộ (https://developers.google.com/admob/android/privacy/release-notes).
- Firebase Analytics: đặt `google_analytics_default_allow_ad_personalization_signals` theo consent ở VN.
- **Test:** `ConsentDebugSettings.DebugGeography` có các giá trị `DEBUG_GEOGRAPHY_EEA`, `DEBUG_GEOGRAPHY_REGULATED_US_STATE`, `DEBUG_GEOGRAPHY_OTHER` (UMP ≥ 3.1.0), cộng với `addTestDeviceHashedId(...)` (hash in ra logcat lần chạy đầu). Từ UMP 2.2.0, emulator mặc định là test device. `consentInformation.reset()` chỉ dùng khi test.
- Test device cho ad: `RequestConfiguration.setTestDeviceIds(...)` (id in ra logcat). Giữ danh sách trong `BuildConfig.ADMOB_TEST_DEVICE_IDS`, chỉ có trong debug.
- Settings → "Quyền riêng tư & quảng cáo": hiển thị khi `privacyOptionsRequired`, gọi `adsController.showPrivacyOptions(activity)`.

---

## 6. Remote Config keys

Hiện chỉ có `ads_enable`. Đổi tên thành `ads_enabled` và **thêm in-app defaults** (`res/xml/remote_config_defaults.xml` + `setDefaultsAsync` trong `TSFirebaseRemoteConfig.setup()`), để tránh tình trạng "false cho tới khi fetch".

| Key | Kiểu | Default in-app | Mục đích |
|---|---|---|---|
| `ads_enabled` | bool | `false` lúc rollout, chuyển `true` khi đạt 100% | **Kill switch toàn cục** |
| `ads_unit_ids` | JSON | id production từ BuildConfig | `{"app_open_warm":"ca-app-pub-…/…","favourite_top":"…",…}` để đổi unit không cần release |
| `ads_placements_enabled` | JSON | `{"favourite_top":true,"podcast_detail_header":true,"podcast_detail_episodes":true,"discover_inline":true,"search_results":true,"player_episode_list":false,"discover_collapsible":false,"interstitial_nav":false,"rewarded_ad_free":false,"app_open_warm":false,"app_open_cold":false}` | Kill switch từng placement |
| `ads_grace_period_hours` | long | 24 | Không có ad sau khi cài |
| `ads_app_open_min_background_sec` | long | 30 | |
| `ads_app_open_min_interval_min` | long | 60 | |
| `ads_app_open_daily_cap` | long | 3 | |
| `ads_app_open_skip_first_sessions` | long | 2 | |
| `ads_min_gap_fullscreen_sec` | long | 60 | Khoảng cách giữa 2 full-screen bất kỳ |
| `ads_interstitial_every_n` | long | 3 | Số podcast detail đã xem trước khi được show |
| `ads_interstitial_min_interval_sec` | long | 180 | |
| `ads_interstitial_session_cap` / `ads_interstitial_daily_cap` | long | 2 / 6 | |
| `ads_native_first_index` / `ads_native_interval` / `ads_native_max_per_list` | long | 3 / 8 / 3 | |
| `ads_native_template` | string | `"small"` | `small`/`medium`, dùng cho A/B |
| `ads_rewarded_ad_free_minutes` / `ads_rewarded_daily_cap` | long | 120 / 3 | |
| `ads_min_app_version` | long | 0 | Tắt ad cho versionCode cũ có bug |

Dùng **Firebase A/B Testing** trên các key này (xem §9). Mọi key đọc qua `AdsConfig`, có giá trị mặc định an toàn. Không gọi `ConfigAPI.getBoolean` trực tiếp trong composable nữa.

---

## 7. `app-ads.txt` và Play Data safety

### 7.1 `app-ads.txt`

- AdMob crawl theo domain "Website" trong **Play Store listing** (Store presence → Store settings → Contact details). File phải nằm ở root, `https://<domain>/app-ads.txt` — https://support.google.com/admob/answer/9363762
- Nội dung: copy snippet cá nhân hóa từ AdMob (Apps → app-ads.txt → How to set up). Dạng thường gặp: `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (`f08c47fec0942fa0` là TAG ID của Google; cứ lấy đúng theo snippet AdMob đưa).
- Khi thêm mediation (Phase 4), nối thêm dòng của từng network.
- Website `web/` (Next.js, agent khác đang làm) phục vụ file này ở `app/app-ads.txt/route.ts` hoặc `public/app-ads.txt` (xem `docs/in-progress/plans/seo-web-dashboard.md` §A.9). Hiện **chưa có domain**: đây là blocker, vì thiếu file thì AdMob có thể giới hạn phân phát.
- AdMob mất tới 24h để crawl, có nút "Check for updates".

### 7.2 Play Console → App content

| Mục | Trước (AppLovin + Facebook) | Sau (AdMob + UMP) |
|---|---|---|
| Ads declaration | "Contains ads" | Giữ "Contains ads" |
| Advertising ID declaration | (cần kiểm tra lại) | **Có dùng**, mục đích: Advertising/marketing, Analytics, Fraud prevention |
| Data safety: *Device or other IDs* | AppLovin + **Meta** (collected + shared) | AdMob: collected + **shared**, mục đích Advertising, Analytics, Fraud prevention |
| Data safety: *Approximate location* (qua IP) | AppLovin | AdMob: collected + shared |
| Data safety: *App interactions* | AppLovin + Meta (app events) | AdMob: collected + shared |
| Data safety: *Diagnostics* | AppLovin | AdMob: collected + shared |
| Encrypted in transit | | Có (TLS) |
| Meta / Facebook | Có chia sẻ cho Meta | **Xóa** khỏi khai báo (sau khi gỡ SDK) |

Nguồn: https://developers.google.com/admob/android/privacy/play-data-disclosure. Kết hợp với phần khai báo sẵn có của Firebase Analytics/Crashlytics/FCM.

- **Target audience:** không chọn nhóm trẻ em / Designed for Families. App Open không được phép trong Families.
- **Privacy policy:** cập nhật, ghi rõ Google AdMob, UMP, quyền opt-out, và bỏ phần Meta/AppLovin.

---

## 8. Kế hoạch test

| Loại | Nội dung |
|---|---|
| Unit (JVM) | `AdPolicy`: grace, audio-first, gap, daily/session cap, ad-free. Clock và store đều giả lập. `buildRows` (vị trí native xác định, cap). `AdsConfig` parse JSON lỗi thì dùng default. |
| Test ad unit id (chỉ dùng trong debug, **cấm** dùng id thật khi test) | App ID `ca-app-pub-3940256099942544~3347511713` · App Open `ca-app-pub-3940256099942544/9257395921` · Adaptive banner `ca-app-pub-3940256099942544/9214589741` · Interstitial `ca-app-pub-3940256099942544/1033173712` · Rewarded `ca-app-pub-3940256099942544/5224354917` · Native `ca-app-pub-3940256099942544/2247696110` · Native video `ca-app-pub-3940256099942544/1044960115` |
| Build guard | Task Gradle hoặc unit test fail nếu bản `release` chứa `3940256099942544`, hoặc nếu bản `debug` chứa pub id thật. |
| Consent | Ma trận DebugGeography EEA / REGULATED_US_STATE / OTHER × (đồng ý / từ chối / đóng form) × (online / offline). Kiểm tra `canRequestAds`, nút privacy options, và không có request ad nào trước consent ở EEA (xem bằng Ad Inspector và logcat). |
| Ad Inspector | `MobileAds.openAdInspector(activity)` trong menu debug: xem request, fill, mediation. |
| Audio-first | Script thủ công + instrumented: (1) đang phát → Home → quay lại sau 60s: **không** App Open; (2) pause → nền 60s → quay lại: có App Open (khi đủ điều kiện); (3) bấm media notification: không có ad; (4) back từ Podcast Detail khi đang phát: không có interstitial; (5) rewarded khi đang phát: pause, xem, resume. |
| Lifecycle / leak | LeakCanary (debug) cho native/banner. Xoay màn hình, đổi dark mode, process death. StrictMode bắt disk/network trên main thread lúc init. |
| Performance | Macrobenchmark cold start trước và sau (ngân sách: +≤ 50 ms TTID). Init SDK chạy ở `Dispatchers.IO`. |
| Pre-launch report | Play Console, xem crash/ANR/accessibility. |
| Regression | Monkey (đã dùng ở P0) trên Podcast Detail có native để bắt `ClassCastException`. |

---

## 9. Rollout và KPI

| Phase | Thời gian (ước tính) | Việc | Điều kiện qua phase |
|---|---|---|---|
| **0. Dọn dẹp** | Tuần 1 | Merge TSP-5. PR mới: gỡ Facebook SDK + meta-data + `facebook_app_id`, gỡ `media3-exoplayer-ima`, xóa hàm ad trong `securedToken`. **Revoke AppLovin API key.** Release "không quảng cáo" lên production. | Crash-free ≥ baseline. Đo **baseline 2 tuần** (retention, phút nghe/DAU, rating) khi không có ad. |
| **1. Seam + AdMob ẩn** | Tuần 2–3 | Tạo AdMob app, ad units, link Firebase, cấu hình Privacy & messaging, `app-ads.txt` (cần domain). Implement §3 với `ads_enabled=false`. Internal testing track, test ads. | Mọi test ở §8 pass. Ad Inspector sạch. |
| **2. Banner + Native** | Tuần 4–6 | Staged rollout Play 5% → 20% → 50% → 100% (mỗi bước ≥ 3 ngày). Bật `favourite_top`, `podcast_detail_header`, `podcast_detail_episodes`, `discover_inline`, `search_results`. | Guardrail ở bảng dưới không bị vượt. |
| **3. A/B full-screen** | Tuần 7–12 | Firebase A/B Testing (Remote Config). Exp-1: App Open warm (off vs on 60 phút vs on 30 phút). Exp-2: native density (interval 8 vs 12, small vs medium). Exp-3: interstitial_nav (off vs every_n=3). Exp-4: rewarded ad-free. Mỗi experiment ≥ 14 ngày, metric chính tính theo cohort D7. | Chọn variant có ARPU D7 cao nhất **mà không** làm D7 retention giảm quá 2% tương đối. |
| **4. Mediation** | Sau khi ổn định ≥ 1 tháng | AdMob bidding: Meta Audience Network, AppLovin, Pangle, Mintegral, Unity, Liftoff, … (Next-Gen có hỗ trợ adapter). Thêm dòng `app-ads.txt` và cập nhật Data safety cho từng network. | eCPM tăng ≥ 10% mà không tăng ANR. |

**KPI chính:** ARPDAU (ad), eCPM theo format/placement, impressions/DAU, fill rate, show rate, ARPU D7/D30 theo cohort.

**Guardrail (dừng rollout hoặc tắt bằng kill switch nếu vi phạm):**
- D1 / D7 retention giảm > 2% tương đối so với control.
- Phút nghe trung bình / DAU giảm > 3%.
- Crash-free users < 99.5%. User-perceived ANR rate > 0.47% (ngưỡng bad behavior của Play).
- Rating trung bình 7 ngày giảm > 0.1, hoặc review nhắc "quảng cáo" tăng đột biến.
- Tỷ lệ gỡ cài đặt trong 7 ngày tăng > 10%.
- Bất kỳ cảnh báo policy hay invalid traffic nào từ AdMob.

---

## 10. Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| AdMob account mới bị giới hạn phân phát / chưa duyệt app | Cao | Tạo account sớm (Phase 1), có `app-ads.txt` + store listing đầy đủ, link Play Store. |
| Invalid traffic (tự click, test bằng id thật) dẫn tới bị khóa account | Cao | Build guard ở §8, test device id, cấm QA click ad thật. Không đặt ad sát nút điều khiển. |
| Vi phạm policy App Open / interstitial | Cao | `AdPolicy` + unit test. Mặc định full-screen **tắt** và chỉ bật qua A/B. |
| Video ad có tiếng chồng lên podcast | Trung bình | Audio-first. Native video `startMuted`. Rewarded thì pause trước khi show. |
| Next-Gen SDK còn mới (1.6.0 mới 1 tuần) | Trung bình | Pin version, soak ≥ 2 tuần ở internal track. Phương án dự phòng là legacy 25.5.0 sau seam. |
| ANR do init trên main thread / callback chạy nền | Trung bình | Init trong `Dispatchers.IO`, UI chuyển về `Main`. Theo dõi ANR trong Play Vitals. |
| Rò bộ nhớ native/banner trong `LazyColumn` | Trung bình | Pool/cache theo ViewModel, `destroy()` ở `onCleared`, LeakCanary. |
| eCPM Việt Nam thấp, doanh thu không bù được churn | Trung bình | Guardrail + A/B. Rewarded ad-free cho nhóm nhạy cảm với quảng cáo. |
| Gỡ Facebook SDK làm mất đo lường cho chiến dịch Meta | Trung bình | Xem quyết định ở §11 và `seo-web-dashboard.md` §B.3: nếu chạy Meta Ads, thêm lại **chỉ** `facebook-core` (app events), gate theo consent, tắt auto-log; hoặc dùng MMP. |
| AppLovin API key đã lộ trong lịch sử git | Trung bình | Revoke trên dashboard AppLovin. Không cần rewrite history nếu key đã vô hiệu. |
| Luật dữ liệu VN (PDP Law 2026) | Trung bình | Màn consent VN + non-personalized fallback. Cần tư vấn pháp lý. |
| Remote Config fetch chậm nên dùng default | Thấp | In-app defaults an toàn (`ads_enabled=false` cho tới khi rollout xong). |

---

## 11. Câu hỏi cần owner quyết định

1. **Tài khoản AdMob:** dùng account nào (cá nhân/công ty, thông tin thanh toán và thuế)? Cần **AdMob App ID** + **publisher id (`pub-…`)** và tạo ad units theo §2.2.
2. **Domain website** cho Play listing và `app-ads.txt` (vd. `tspodcast.app`?). Đây là blocker của Phase 1.
3. **Next-Gen SDK (khuyến nghị)** hay legacy `play-services-ads`?
4. **Danh sách placement bật ban đầu** (§2.2), và có bỏ banner ở màn Player (P6) cùng banner thứ 2 ở Discover (P4) không?
5. **Rewarded:** chỉ làm "nghe không quảng cáo X giờ", hay khóa thêm tính năng (tải offline, hẹn giờ ngủ, tốc độ phát)? Hiện app chưa có tính năng premium.
6. **Gói trả phí "Remove ads" (Play Billing)** có nằm trong roadmap không? Nếu có thì seam cần `AdsState.Disabled("premium")`.
7. **Consent cho người dùng Việt Nam:** làm màn consent riêng (khuyến nghị), hay chỉ dùng non-personalized ads cho mọi user VN?
8. **Facebook/Meta:** gỡ hẳn (khuyến nghị), hay giữ `facebook-core` cho Meta Ads sau này? Ai có quyền với Meta app `1177427693996135`?
9. **Mediation** (Phase 4): có muốn đưa AppLovin quay lại làm bidder không?
10. Xác nhận đã **revoke AppLovin API key** đang nằm trong lịch sử git.

---

## Phụ lục: Nguồn đã tra cứu (2026-10-29)

- GMA SDK release notes (25.5.0, maintenance mode): https://developers.google.com/admob/android/rel-notes
- GMA quick start (legacy): https://developers.google.com/admob/android/quick-start
- GMA Next-Gen quick start / release notes / migration: https://developers.google.com/admob/android/next-gen/quick-start · https://developers.google.com/admob/android/next-gen/rel-notes · https://developers.google.com/admob/android/next-gen/migration
- Next-Gen App Open / Banner / Collapsible / Native / Interstitial / Rewarded: https://developers.google.com/admob/android/next-gen/app-open · https://developers.google.com/admob/android/next-gen/banner · https://developers.google.com/admob/android/next-gen/banner/collapsible · https://developers.google.com/admob/android/next-gen/native · https://developers.google.com/admob/android/next-gen/native/advanced · https://developers.google.com/admob/android/next-gen/interstitial · https://developers.google.com/admob/android/next-gen/rewarded
- UMP: https://developers.google.com/admob/android/privacy · https://developers.google.com/admob/android/privacy/release-notes
- Play Data safety cho GMA SDK: https://developers.google.com/admob/android/privacy/play-data-disclosure
- app-ads.txt: https://support.google.com/admob/answer/9363762
- App Open guidance: https://support.google.com/admob/answer/9341964
- AdMob interstitial / placement policy: https://support.google.com/admob/answer/6201362 · https://support.google.com/admob/answer/6066980 · https://support.google.com/admob/answer/6293636
- Google Play Ads policy / Better Ads Experiences: https://support.google.com/googleplay/android-developer/answer/9857753 · https://support.google.com/googleplay/android-developer/answer/12271244
- Luật BVDLCN Việt Nam 2025/2026: https://www.rajahtannasia.com/viewpoints/new-decree-implementing-the-law-on-personal-data-protection-passed/ · https://vietanlaw.com/new-personal-data-protection-law-2025-in-vietnam

## Cập nhật 2026-11-01: bật AdMob từ bản 3.5 và quy tắc interstitial

Owner cấp ad unit thật (App Open, Interstitial, Banner, Native) cho bản release tiếp theo. ID nằm trong GitHub secrets `ADMOB_*` (workflow *Build signed release bundle*), không ghi vào repo.

Mặc định trong app (`AdsSettings.defaults(debug = false)`): `ads_enabled = true`, banner/native như cũ, **App Open khi quay lại app** và **interstitial khi rời màn chi tiết show** bật; App Open lúc mở nguội và Rewarded tắt. Remote Config `ads_enabled = false` vẫn là công tắc tắt khẩn cấp; mọi ngưỡng dưới đây chỉnh được qua Remote Config.

Interstitial (`AdPolicy`, có unit test) chỉ hiện khi **tất cả** điều kiện đúng:

| Quy tắc | Mặc định | Key Remote Config |
|---|---|---|
| Chỉ ở điểm ngắt tự nhiên do người dùng chọn: rời màn chi tiết show (không lúc mở/thoát app, không giữa thao tác) | — | — |
| Mỗi N lần ngắt trong phiên (lần 3, 6, 9…) | 3 | `ads_interstitial_every_n` |
| Người dùng đã ở màn đó đủ lâu (không hiện sau khi lướt qua) | 20 giây | `ads_interstitial_min_dwell_sec` |
| Không khi đang phát, không trên Player, không khi mở từ thông báo/deep link | luôn | — |
| Không ngay sau khi vừa nghe (có thể nghe tiếp) | 60 giây | `ads_interstitial_after_playback_sec` |
| Không trong 24 giờ đầu sau cài, không ở phiên đầu, không trước khi xong onboarding | 24 h | `ads_grace_period_hours` |
| Cách quảng cáo toàn màn hình trước (kể cả App Open) | 90 giây | `ads_min_gap_fullscreen_sec` |
| Cách lần interstitial trước | 4 phút | `ads_interstitial_min_interval_sec` |
| Tối đa mỗi phiên / mỗi ngày | 2 / 4 | `ads_interstitial_session_cap` / `ads_interstitial_daily_cap` |
| Chỉ ad đã preload (không bắt người dùng chờ tải), cần consent UMP, tôn trọng thời gian không quảng cáo | luôn | — |

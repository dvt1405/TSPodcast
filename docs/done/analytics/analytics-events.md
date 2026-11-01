# Analytics Android: danh sách event, tham số và cách kiểm tra

> **Trạng thái:** đã làm (PR "analytics đầy đủ, kể cả quảng cáo"). Còn việc owner: đăng ký custom dimension trên GA4 (§8), bật liên kết AdMob ↔ Firebase nếu muốn xem báo cáo AdMob trong GA4, theo dõi trùng `ad_impression` (§5.3).

Mọi event của app Android đi qua **một API**: `AnalyticsTracker` (`sharedLibrary/.../analytics/`). Không chỗ nào gọi thẳng `FirebaseAnalytics` nữa, trừ `TSAnalytics` cũ còn dùng cho event push FCM.

## 1. Kiến trúc

| Thành phần | Ở đâu | Việc |
|---|---|---|
| `AnalyticsTracker` | `sharedLibrary/.../analytics/AnalyticsTracker.kt` | Interface: `log(event)`, `setUserProperty`, `screen(name)`, `currentScreen` |
| `AnalyticsEvent.of(...)` | `AnalyticsEvent.kt` | Dựng event theo giới hạn GA4: tên snake_case ≤ 40 ký tự, ≤ 25 tham số, chuỗi ≤ 100 ký tự (không cắt đôi emoji), bỏ tham số null, Boolean → `"true"`/`"false"` |
| `AppEvents` | `AppEvents.kt` | Danh mục mọi event của app (tên, tham số, giá trị `section`, `screen_name`, user property) |
| `Analytics` | `AnalyticsTracker.kt` | Facade toàn cục (như `Crash`), cài trong `App.onCreate`; dùng ở Composable, Player listener, OkHttp interceptor |
| `FirebaseAnalyticsTracker` | `sharedFirebase/.../FirebaseAnalyticsTracker.kt` | Bản Firebase, bind bằng Hilt (`AbsFirebaseModule`) |
| `RecordingAnalyticsTracker`, `NoOpAnalyticsTracker` | `AnalyticsTracker.kt` | Fake cho unit test / preview |
| `AdsAnalytics`, `AdRevenue` | `ads/.../AdsAnalytics.kt` | Event quảng cáo, đổi doanh thu micros → GA4 `ad_impression` |
| `PlaybackAnalytics` | `sharedPlayer/.../player/PlaybackAnalytics.kt` | Listener trên ExoPlayer của session: phát, dừng, tua, tiến độ, hết bài, lỗi |
| `ApiErrorAnalyticsInterceptor` | `core/.../network/` | `api_error` cho mọi call API lỗi |
| `SettingsAnalytics` | `app/.../analytics/SettingsAnalytics.kt` | User property + event đổi cài đặt, theo dõi thẳng các flow `*Prefs` |
| `TrackScreens`, `TrackScreen` | `app/.../analytics/ScreenTracking.kt` | `screen_view` cho mọi route của hai NavHost và cho bottom sheet |
| `ContentAnalytics` | `app/.../analytics/ContentAnalytics.kt` | `select_content` cho các lần bấm ở Home, Search, Favourites, Radio, Detail |
| `SettledSearchLogger` | `app/.../analytics/SettledSearchLogger.kt` | Một `search` cho mỗi truy vấn đã dừng gõ 1,5 s |

### Đồng ý thu thập (consent)

- Manifest (`sharedFirebase`) tắt sẵn `firebase_analytics_collection_enabled`; `PrivacyConsent` bật/tắt Firebase theo lựa chọn ở sheet lần đầu hoặc **Cài đặt → Thống kê sử dụng**.
- `FirebaseAnalyticsTracker` **tự bỏ** mọi event và user property khi chưa đồng ý (log debug ghi `drop (no consent)`). User property được nhớ lại và gửi ngay khi người dùng bật thống kê sau này.
- Hệ quả: các trang onboarding trước bước quyền riêng tư không có số liệu (chưa có consent). Event đầu tiên của người dùng mới là `tutorial_complete`.
- Tắt thống kê thì không ghi được event "đã tắt" (không gửi gì sau đó).
- Tự động `screen_view` của Firebase đã tắt (`google_analytics_automatic_screen_reporting_enabled=false`): app chỉ có một Activity nên nó luôn ghi "MainActivity".

### Quyền riêng tư

Không gửi chữ người dùng gõ khi tìm kiếm, email, tiêu đề hay mã thiết bị. Chỉ gửi id công khai của catalog (feed id, episode id, id kênh radio, id chủ đề), mã vùng, ngôn ngữ, số lượng. Đúng với chính sách quyền riêng tư §3.1 và quyết định iOS M6 §3.8.

## 2. Màn hình (`screen_view`)

Tham số: `screen_name`, `screen_class` (bằng nhau).

| `screen_name` | Khi nào |
|---|---|
| `discover` | Tab Khám phá (Home) |
| `favourites` | Tab Yêu thích |
| `search` | Tab Tìm kiếm |
| `radio` | Tab Radio |
| `podcast_detail` | Màn chi tiết show |
| `player` | Màn Player |
| `settings` | Cài đặt |
| `onboarding_1` … `onboarding_n` | Từng trang giới thiệu (thường bị bỏ vì chưa consent) |
| `onboarding_categories` | Chọn chủ đề yêu thích sau onboarding |
| `podcast_location_sheet` | Sheet "Vị trí podcast"; đóng sheet thì ghi lại `screen_view` của màn bên dưới |

`currentScreen` được gắn vào event quảng cáo, `api_error` và `play_start` dưới tham số `screen`.

## 3. Nội dung và phát

### 3.1 Chọn nội dung — `select_content` (GA4 recommended)

| Tham số | Giá trị |
|---|---|
| `content_type` | `podcast`, `episode`, `live`, `news`, `radio` |
| `item_id` | feed id / episode id / live id / id kênh radio |
| `section` | `trending`, `news`, `location`, `music`, `live`, `recent`, `favourite_categories`, `search_results`, `category_results` (show của chủ đề mở ở Search), `favourites`, `radio`, `podcast_detail` |
| `region` | Mã vùng (VN, US…) cho `location` và `news` |
| `position` | Thứ tự trong danh sách (location, favourite_categories, search_results) |

Nơi bắn: `DiscoverPodcastsScreen` (các hàng Home), `LocationSection` (bảng xếp hạng theo vị trí, thời sự theo vị trí), `HomeNavigationScreen` (kết quả tìm kiếm, tab Radio), `FavouriteScreen`, `PodcastDetailScreen` (bấm tập).

`location_chip_select` (`region`, `section` = `location` | `news`): đổi chip vị trí trong hai khu đó.

### 3.2 Phát (từ ExoPlayer của session — tính cả notification, Android Auto, shortcut)

Mọi event phát có `content_type` (`episode` | `live` | `radio`), `item_id`, `feed_id` (nếu có).

| Event | Tham số thêm | Khi nào |
|---|---|---|
| `play_start` | `start_position_sec`, `resumed`, `screen` | Lần đầu một item thật sự phát (sau buffer) |
| `playback_pause` | `position_sec`, `percent` | Người dùng dừng (không tính dừng do hẹn giờ / hết bài) |
| `playback_resume` | `position_sec` | Người dùng phát tiếp |
| `playback_skip` | `direction` = `back` (−15 s) / `forward` (+30 s) / `next` / `previous` | Nút ±, chuyển tập trong hàng đợi |
| `playback_seek` | `from_sec`, `to_sec`, `delta_sec` | Kéo thanh tiến độ (bỏ qua cú tua tới vị trí đã nghe trong 3 s đầu sau khi đổi item) |
| `playback_progress` | `percent` (25/50/75), `duration_sec` | Mỗi mốc một lần cho mỗi lần phát; không áp dụng live/radio |
| `playback_complete` | `duration_sec` | Hết tập (không áp dụng live) |
| `playback_error` | `error_code` (vd `io_bad_http_status`), `error_code_num` | `PlaybackException`; một lần mỗi mã lỗi + item, tối đa 20/process |
| `speed_change` | `rate` (`"1.5"`), `scope` (`show` \| `all`) | `PlayerManager.setSpeed` (giống iOS) |
| `sleep_timer_set` | `option` (`5`…`60` \| `end_of_episode` \| `off`), `origin` (`player` \| `shortcut`) | `PlayerManager.setSleepTimer` (giống iOS) |

### 3.3 Hành động khác

| Event | Tham số | Nơi |
|---|---|---|
| `favourite_add` / `favourite_remove` | `content_type` (`podcast` \| `episode` \| `radio`), `item_id` | `FavouriteRepository.saveFavourite` — mọi nút tim |
| `share` (GA4) | `content_type`, `item_id`, `method` = `system_sheet` | Nút chia sẻ ở Detail (podcast) và menu Player (episode) |
| `show_hidden` / `show_unhidden` | `feed_id` | Ẩn / bỏ ẩn show (theo dõi `ContentPrefs.hiddenShows`) |
| `content_report` | `kind` (`feed` \| `episode`), `feed_id` | Mở mail báo cáo |
| `shortcut_invoked` | `intent` (`continue_listening`, `pause`, `sleep_timer_30`) | Launcher shortcut |
| `tutorial_complete` (GA4) | `analytics_allowed`, `crash_reports_allowed` | Xong onboarding |

Download: app chưa có tải về nên chưa có event.

## 4. Khám phá và cài đặt

| Event | Tham số | Khi nào |
|---|---|---|
| `search` (GA4) | `term_length`, `word_count`, `search_mode` (`all` \| `music`), `result_count` | Một lần cho mỗi truy vấn đã dừng gõ 1,5 s; không gửi chữ |
| `search_no_results` | `term_length`, `search_mode` | Kèm `search` khi 0 kết quả |
| `category_select` | `category_id`, `category_name`, `origin` (`search` \| `onboarding`) | Mở chủ đề ở Search (dải chủ đề, sheet tất cả chủ đề), chọn chủ đề ở onboarding |
| `onboarding_categories_saved` | `count` | Lưu chủ đề yêu thích |
| `podcast_locations_change` | `count`, `primary_location`, `locations` (`VN,US`), `primary_changed`, `change` (`add` \| `remove` \| `reorder`) | Đổi vị trí podcast (Cài đặt hoặc sheet ở Home) |
| `app_language_change` | `language`, `previous_language` | Đổi ngôn ngữ app |
| `liquid_glass_toggle` | `enabled` | Bật/tắt Liquid Glass |
| `explicit_toggle` | `allowed` | Bật/tắt nội dung 18+ |
| `crash_reports_toggle` | `enabled` | Bật/tắt báo lỗi (khi thống kê đang bật) |

### User property

| Tên | Giá trị |
|---|---|
| `podcast_locations` | `VN,US,JP` (theo thứ tự ưu tiên) |
| `primary_location` | `VN` |
| `app_language` | `vi`, `en`, `pt-BR`… |
| `liquid_glass` | `true` / `false` |
| `explicit_allowed` | `true` / `false` |

## 5. Quảng cáo

### 5.1 Phễu

Mọi event có `placement` (vd `discover_inline`), `ad_format` (`banner`, `native`, `interstitial`, `app_open`, `rewarded`), `screen`.

| Event | Tham số thêm | Khi nào |
|---|---|---|
| `ads_request` | | Banner / native bắt đầu tải (full-screen do preloader tự tải: request ≈ loaded + load_failed) |
| `ads_loaded` | `latency_ms` (banner/native), `ad_source` | Tải xong |
| `ads_load_failed` | `code` (`NO_FILL`, `NETWORK_ERROR`…), `code_num`, `latency_ms` | Tải lỗi |
| `ads_show` | | Full-screen mở |
| `ads_show_failed` | `code` | Full-screen không mở được (trước đây ghi `ads_load_failed` với `show_…`) |
| `ads_dismiss` | | Full-screen đóng |
| `ads_impression` | | SDK báo impression |
| `ads_click` | | Bấm quảng cáo |
| `ads_blocked` | `reason` | Chính sách chặn (lấy mẫu 10 %) |
| `ads_rewarded_earned` | | Nhận thưởng |
| `ads_paid` | `value_micros`, `currency`, `precision` | Paid event, để đối chiếu với AdMob API |
| `consent_status` | `status`, `can_request_ads`, `error_code` | Kết quả UMP |

### 5.2 Doanh thu — `ad_impression` (GA4 recommended)

Bắn từ `onAdPaid` của mọi định dạng (banner, native, app open, interstitial, rewarded):

| Tham số | Giá trị |
|---|---|
| `ad_platform` | `AdMob` |
| `ad_source` | Mạng đã trả quảng cáo (`AdMob Network`…), mặc định `AdMob` |
| `ad_format` | như trên |
| `ad_unit_name` | khoá placement (1 placement = 1 ad unit) |
| `value` | `value_micros / 1 000 000` (đơn vị tiền tệ) |
| `currency` | ISO 4217, vd `USD`. Không có currency thì bỏ cả `value` (GA4 từ chối value thiếu currency) |
| `precision` | `estimated`, `publisher_provided`, `precise`, `unknown` |
| `placement`, `screen` | như trên |

GA4 cộng `value` vào **Doanh thu quảng cáo** (Kiếm tiền → Quảng cáo cho nhà xuất bản).

### 5.3 Có bị đếm trùng không

GMA SDK cũ (`play-services-ads`) kéo theo `play-services-measurement-sdk-api` và tự ghi `ad_impression` khi AdMob liên kết Firebase. SDK Next-Gen 1.6.0 mà app dùng **không** phụ thuộc thư viện đó (đã xem POM), nên app tự ghi. Nếu sau này DebugView thấy hai `ad_impression` cho một quảng cáo (một cái `origin=auto`), tắt bản của app bằng Remote Config **`ads_ga4_ad_impression` = `false`** (`ads_paid` vẫn ghi).

## 6. Lỗi

| Event | Tham số | Ghi chú |
|---|---|---|
| `api_error` | `endpoint` (vd `podcasts/byfeedid`, số dài → `:id`), `http_code` (0 = không có phản hồi), `error_type` (`http` \| `timeout` \| `network` \| `other`), `screen` | `ApiErrorAnalyticsInterceptor`; một lần mỗi endpoint + mã, tối đa 30/process; bỏ qua call bị huỷ (gõ tìm kiếm tiếp) |
| `playback_error` | xem §3.2 | |

## 7. Kiểm tra bằng DebugView

1. Cài bản debug, mở thống kê: lần đầu bấm "Đồng ý" ở sheet quyền riêng tư, hoặc **Cài đặt → Thống kê sử dụng**.
2. Bật chế độ debug cho app và log chi tiết:
   ```
   adb shell setprop debug.firebase.analytics.app tss.t.podcast
   adb shell setprop log.tag.FA VERBOSE
   adb shell setprop log.tag.FA-SVC VERBOSE
   ```
3. Mở Firebase Console → Analytics → **DebugView**, chọn thiết bị. Event hiện trong vài giây.
4. Không cần Console: `adb logcat -s TSAnalytics` (bản debug in mọi event app gửi, kể cả `drop (no consent)` khi chưa đồng ý), hoặc `adb logcat | grep "Logging event: origin=app"` để thấy Firebase đã nhận.
5. Tắt debug: `adb shell setprop debug.firebase.analytics.app .none.`

Kết quả thử trên emulator-5554 khi làm PR: có `user property` (5 cái) gửi tới Firebase, `screen_view discover`, `consent_status`, `ads_load_failed` (app_open_warm, `NETWORK_ERROR`, `screen=discover`); khi tắt thống kê, tracker ghi `consent=false` và không gửi. Lưu ý Firebase bỏ `screen_view` khi app đang ở nền ("Cannot log screen view event when the app is in the background").

Unit test: `AnalyticsEventTest`, `AppEventsTest` (sharedLibrary), `AdsAnalyticsTest` (ads: micros → value, `ad_impression`, công tắc Remote Config), `ApiErrorAnalyticsInterceptorTest` (core), `ScreenTrackingTest` (app).

## 8. Việc của owner trên GA4

- **Admin → Custom definitions**: đăng ký custom dimension (event scope) cho `section`, `region`, `placement`, `ad_format` (đã có sẵn cho `ad_impression`), `screen`, `search_mode`, `error_code`, `endpoint`, `direction`, `percent`, `content_type`/`item_id` (GA4 có sẵn); custom metric cho `result_count`, `latency_ms`, `position_sec`. User-scoped: 5 user property ở §4.
- Đánh dấu key event: `play_start`, `playback_complete`, `favourite_add`, `share`, `tutorial_complete`.
- Nếu cần BigQuery: bật liên kết BigQuery để nối `item_id` / `feed_id` với tên show.

## 9. Thêm event mới

1. Thêm hàm vào `AppEvents` (hoặc `AdRevenue` cho quảng cáo), dùng `AnalyticsEvent.of`.
2. Gọi `Analytics.log(...)` (hoặc inject `AnalyticsTracker`).
3. Thêm dòng vào tài liệu này và test trong `AppEventsTest` (test đã kiểm mọi tên hằng là snake_case hợp lệ).

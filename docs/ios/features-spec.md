# Spec tính năng — chuẩn parity cho iOS

Tài liệu mô tả **hành vi hiện tại** của app Android (`main` @ `47fc4f4`) theo từng màn hình, để bản iOS đạt tương đương. Mỗi mục ghi rõ:

- **[Parity]**: bắt buộc giống Android;
- **[Sửa]**: hành vi Android đang lỗi hoặc thừa, iOS **không** nên sao chép;
- **[iOS+]**: tính năng iOS nên có thêm để khác biệt (giảm rủi ro guideline 4.2/4.3). Phần lớn trùng với quick win Q2–Q9 trong [../research/competitor-analysis.md §6.1](../research/competitor-analysis.md). Nên viết logic trong shared để Android có cùng lúc.

## 1. Luồng tổng thể

```
App launch
  ├─ Firebase init → Crash.install → Crashlytics userId = InstallId
  ├─ FCM token, Remote Config fetchAndActivate (retry, tối đa 20 lần, backoff 30s × n)
  ├─ Ads SDK init (Android) → iOS v1: bỏ
  └─ PruneApiCache (xoá Podcast/Feed/Episode có cachedAt < now − 7 ngày, trừ mục đã favourite)

Start destination:
  !OnboardingFinished          → Onboarding
  !HasSelectFavouriteCategory  → SelectFavouriteCategory
  else                         → Main (TabView)

Main: Tab [0 Favourite] [1 Home = mặc định] [2 Radio] [3 Search]
  + MiniPlayer nổi phía trên tab bar khi có item đang phát
  ├─ Home → PodcastDetail → Player
  ├─ Home (Live) → Player
  ├─ Search → PodcastDetail
  ├─ Favourite → PodcastDetail (+ tự phát) | Player (radio)
  └─ Radio → Player
```

## 2. PodcastIndex API

- Base URL: `NativeLib.getApiUrl()`. Đây là API v1.0 của PodcastIndex; giá trị cụ thể lấy từ cấu hình, không ghi ở đây.
- Auth header: xem [architecture.md §3.2](architecture.md#32-network-ktor).
- Tất cả request là `GET`.

| # | Endpoint | Tham số app gửi | Response model | Dùng ở |
|---|---|---|---|---|
| E1 | `podcasts/trending` | `max=100`, `since=<epoch giây>`, `lang="vi"` (use case đặt mặc định "vi"; interface Retrofit đặt mặc định "vi,en"), `cat=<id1,id2,…>` (tuỳ chọn), `notcat` (repo mặc định `"sexuality"`, nhưng use case truyền `null`) | `TrendingPodcastRes{count, items: [Podcast], max}` | Home: "Podcasts thịnh hành" và "Có thể bạn yêu thích" |
| E2 | `episodes/live` | `max=100` | `LiveResponse{count, items:[LiveEpisode], max}` | Home: "Live Podcasts" |
| E3 | `recent/episodes` | `max=100`, `before=<now−10 ngày, giây>` | `EpisodeResponse` | Home. **[Sửa]** Kết quả bị bỏ qua (nhánh `is EpisodeResponse -> {}` rỗng). iOS không gọi endpoint này |
| E4 | `recent/newfeeds` | `max=100`, `since=<now−10 ngày, giây>` | `TrendingPodcastRes` | Home. **[Sửa]** Lưu vào `recentNewFeeds` nhưng không render. iOS không gọi |
| E5 | `recent/feeds` | `max=100`, `lang="vi"`, `cat=<fav ids>` | `TrendingPodcastRes` | Home: "Podcasts xu hướng gần đây" |
| E6 | `podcasts/bymedium` | `medium=music`, `max=40` | `SearchResponse{count, feeds:[Feed], query}` (**dạng Feed**, map qua `Podcast.fromFeed`) | Home: "Kho nhạc" |
| E7 | `search/byterm` | `q`, `max=100`, `similar=true`, `fulltext=true`, `aponly=false` | `SearchResponse` | Search, chế độ "Tất cả" |
| E8 | `search/music/byterm` | `q`, `val=music`, `max=60`, `similar=true`, `fulltext=true` | `SearchResponse` | Search, chế độ "Nhạc" |
| E9 | `categories/list` | — | `CategoryRes{count, feeds:[{id,name}]}` | Onboarding chọn danh mục, Search (chip) |
| E10 | `episodes/byfeedid` | `id=<feedId>`, `max=100` | `EpisodeResponse{count, items:[Episode], liveItems?}` | Podcast detail |
| E11 | `podcasts/byfeedid` | `id=<feedId>`, `pretty=true` | `PodcastByFeedIdRes{feed: Feed, query}` | Podcast detail (gọi song song với E10, kết quả hiện chưa dùng để render) |
| — | `search/bytitle`, `search/byperson`, `stats/current`, `episodes/random` | — | — | Đã khai báo nhưng **không dùng**. `getRandomEpisodes` là `TODO()` |

Lưu ý khi implement:

- **Đơn vị thời gian:** `since`/`before` tính bằng **giây**. Giá trị mặc định trong `API.getTrending` là `(System.currentTimeMillis() - A_DAY).toInt()`, tức mili-giây ép sang `Int` nên bị tràn số. Chỉ `MainViewModel` truyền đúng giá trị giây. **[Sửa]** Trong shared, không đặt mặc định cho `since`.
- **Retry:**
  - E10 và E11 retry 3 lần (`retryWhen attempt < 3`). E11 không retry khi gặp `IllegalStateException`.
  - Radio: `retry(2)`.
- **Cache:**

  | Dữ liệu | Loại | TTL |
  |---|---|---|
  | E1 không có `cat` | memory + đĩa (`cachedTrending`) | 15 phút |
  | E1 có `cat` | memory + đĩa (`cachedTrending_<cat>`) | 15 phút |
  | E10, E11 | memory theo `feedId` | 15 phút |
  | E9 | memory (`GetCategories(useCache=true)`) + đĩa (`CategoryRes`) + fallback asset `categories.json` | — |
  | Bảng Podcast/Feed/Episode | Room | 7 ngày (`PruneApiCache`) |

- **Blacklist** (`BlacklistRepositoryImpl`):
  - Nguồn: Remote Config `ts_podcast_black_list`, fallback asset `blacklist.json` với dạng `{feedId: [..], filterText: [..]}`. Từ khoá hiện có gồm các từ về cá cược và nhà cái.
  - Lọc theo `id`/`feedId` nằm trong danh sách, **hoặc** `title` chứa từ khoá (so chữ thường).
  - Trending và live lọc thêm theo `description`.
  - Áp dụng cho: E1, E2, E6, E7, E8. **[Parity]**. Đây là lớp phòng vệ cho App Review (nội dung cờ bạc).
- **Điều khoản PodcastIndex:**
  - hiển thị dòng attribution "Dữ liệu podcast cung cấp bởi Podcast Index" (`attribution_podcastindex`) ở cuối Home;
  - không giữ cache lâu hơn mức cho phép (`PruneApiCache`).

  Cả hai đều **[Parity]**.
- **Ảnh:** Coil gửi header `User-Agent` dạng trình duyệt (`SharedConstants.USER_AGENT_WEB_VALUE`) khi tải cover ở màn detail. Trên iOS, đặt cùng header này trong `URLRequest` của image loader (Nuke/Kingfisher hoặc `AsyncImage` với `URLSession` tuỳ biến). Nhiều URL ảnh là `http://` nên cần xử lý ATS (setup-and-build.md §3.3).

## 3. Màn hình

### 3.1 Onboarding (`featureOnboarding/OnboardingScreen`)

| Thuộc tính | Chi tiết |
|---|---|
| UI | `HorizontalPager` gồm 4 trang. Mỗi trang có ảnh minh hoạ (`onboarding_slide_2/3/5/6`), nền màu hoặc gradient riêng, tiêu đề tiếng Việt có tô đậm cụm từ (`onboarding_titleN` + `onboarding_titleN_highlight`). Có dot indicator và nút bo tròn ở cuối |
| Nội dung | "Khám Phá Podcast Yêu Thích Của Bạn" / "Nội Dung Dành Riêng Cho Bạn" / "Nghe Mọi Lúc, Mọi Nơi" / "Sắp Xếp Và Cập Nhật Liên Tục" |
| State | `OnboardingStep{isOnboardingDone, isSelectedFavourite}` |
| Hành động | Đổi trang thì gọi `trackScreen("Onboarding", CurrentPage=n)`. Bấm nút thì `saveOnboardingFinished(true)` và `trackEvent("OnboardingFinished","Onboarding")` |
| API | Load trước E9 (categories) |
| iOS | `TabView(.page)` với `.indexViewStyle`. Ảnh export sang `Assets.xcassets` (PDF/SVG nếu có, nếu không thì @2x/@3x) |

### 3.2 Chọn danh mục yêu thích (`SelectFavouriteCategoryScreen`)

| Thuộc tính | Chi tiết |
|---|---|
| UI | `CenterAlignedTopAppBar` với tiêu đề "Danh mục yêu thích của bạn" và mô tả "(Ít nhất 3 danh mục)". `LazyVerticalGrid` chia cột theo chiều rộng màn hình, mỗi ô là chip có thể toggle. Nút "Lưu và tiếp tục" chỉ bật khi đã chọn **≥ 3** danh mục |
| Dữ liệu | E9 → `CategoryRes.feeds`. Nếu lỗi thì dùng cache `CategoryRes`, rồi tới asset `categories.json` (khoảng 110 danh mục tiếng Anh: Arts, Books, Design…) |
| Lưu | `saveFavouriteCategory(Set<Category>)` ghi `HasSelectFavouriteCategory=true` và `ListFavouriteCategory`, sau đó điều hướng tới Main |
| States | Loading (shimmer), Success, Error (vẫn hiện được danh sách từ asset) |
| **[iOS+]** | Bản địa hoá tên danh mục (hiện chỉ có tiếng Anh). Cho phép sửa danh mục sau này trong màn Settings (Android chưa có) |

### 3.3 Main shell (`HomeNavigationScreen`, `BottomAppBarBlur`)

- Tab bar dạng nổi, có blur (`hazeAndroid` `hazeChild`). Bốn tab theo thứ tự: **Favorite** (icon trái tim), **TSPodcast/Home** (icon nhà, mặc định), **Radio**, **Search**.
- Top app bar hiện tiêu đề theo tab ("Khám phá", "Yêu thích", "Tìm kiếm", "Radio"), có hiệu ứng khi đổi; nội dung cuộn phía sau được blur.
- Back (Android): nếu đang không ở Home thì quay về Home; nếu ở Home thì phải bấm 2 lần mới thoát. iOS **không** có hành vi này.
- iOS:
  - `TabView` với `Tab(...)` của iOS 18+. Khi build bằng Xcode 26 SDK, tab bar tự có kiểu Liquid Glass.
  - Mỗi tab có `NavigationStack` riêng.
  - MiniPlayer đặt bằng `.tabViewBottomAccessory` (iOS 26) hoặc `safeAreaInset(edge: .bottom)` (iOS 17–18).
  - Tiêu đề lớn dùng `navigationTitle` + `.toolbarTitleDisplayMode(.inlineLarge)`.

### 3.4 Home / Discover (`DiscoverPodcastsScreen`, `MainViewModel`)

Nội dung xếp dọc, có pull-to-refresh. Mỗi section có trạng thái loading riêng (`isDataPartLoading[HomepageDataPart]`), hiển thị bằng shimmer placeholder.

| Thứ tự | Section (tiêu đề) | Dữ liệu | Item UI | Tap |
|---|---|---|---|---|
| 1 | "Podcasts thịnh hành" | E1, `since = now − 7 ngày`, không có `cat` | Carousel ngang (`TrendingRow`) gồm ảnh vuông và tiêu đề | `setCurrentPodcast` → Podcast detail (shared element transition cho ảnh, tiêu đề, nền) |
| 2 | "Kho nhạc" + phụ đề "Feed nhạc theo chuẩn podcast:medium" | E6 (`GetMusicFeeds`, lỗi thì không chặn các section khác) | Carousel như trên | Podcast detail |
| — | (Banner ad) | `ads_enable` | | iOS v1: bỏ |
| 3 | "Live Podcasts" | E2, chia nhóm 3 item mỗi cột (`chunked(3)`) | Pager ngang, mỗi trang 3 dòng (`LiveRow`/`LiveEpisodeWidgets`) | `playLive(liveEpisode, list)` → **Player** (phát luôn) |
| 4 | "Podcasts xu hướng gần đây" | E5 với `cat` = id danh mục yêu thích | `RecentRow` dạng carousel | Podcast detail |
| 5 | "Có thể bạn yêu thích" | E1, `since = now − 10 ngày`, `cat` = id yêu thích. Nếu lỗi thì dùng lại list trending | Danh sách dọc `FavouriteWidget` (ảnh, tiêu đề, mô tả). Có ad sau item thứ 2 (iOS: bỏ) | Podcast detail |
| 6 | Attribution "Dữ liệu podcast cung cấp bởi Podcast Index" | — | Text nhỏ màu xám, căn giữa | — |

State (`UIState`): `listTrending`, `listFav`, `liveEpisode: List<List<LiveEpisode>>`, `recentFeeds`, `musicFeeds`, `isDataPartLoading`, `showLoadingView`, `error`, `currentPodcast`, `from`.

Xử lý lỗi:

- Nếu cả hai lời gọi trending đều lỗi, `error` được set và list rỗng. Hiện Android không có UI lỗi rõ ràng.
- **[Sửa]** iOS phải có empty/error state kèm nút "Tải lại" cho từng section, vì section rỗng mãi sẽ trông giống shimmer treo. Reviewer ở Mỹ có thể gặp mạng chậm tới API, và một màn hình trống dễ bị reject theo **2.1**.

Pull-to-refresh: `reload()` đặt lại toàn bộ cờ loading rồi gọi lại tất cả API (cache 15 phút của trending vẫn áp dụng).

**[iOS+] "Thời sự & Bản tin" (M6 N2, owner đưa vào scope; Android chưa có):**

- Mục `home.section.news` **ngay sau** "Podcasts thịnh hành": thẻ "Điểm tin sáng · <thứ dd/MM>" (số tập, tổng phút làm tròn lên, "Phát tất cả"), hàng ngang 10 tập mới nhất (artwork của feed gốc), "Xem tất cả" → màn hub. Loading/lỗi/"Tải lại" như các section khác. Hub tắt hoặc không có tập tin nào → **không có mục** (không màn rỗng). Pull-to-refresh hỏi lại công tắc hub.
- Màn hub (`newsHub.root`): Điểm tin sáng → "Mới nhất" (30 tập, mới nhất trước) → "Nguồn" (mở Podcast detail thường) → "Truyện đêm khuya" (tap = phát + hẹn giờ 30 phút + toast "Đã hẹn giờ tắt 30 phút · Đổi"). Footer nguyên văn: "Nội dung do các cơ quan báo chí phát hành qua RSS và được phát từ địa chỉ gốc của nhà xuất bản." + "Báo cáo / yêu cầu gỡ" (chỉ khi có `CONTENT_REPORT_EMAIL`).
- Nguồn: đúng 8 nguồn trong `shared/assets/news_hub_default.json` (RC `news_hub_v1` thay được), mỗi nguồn cần `confirmedAt` mới hiện ở Release (cổng L4, `iosApp/scripts/check-news-hub-gate.sh`). Resolve qua PodcastIndex (`byitunesid` khớp host, không thì `byfeedurl`; cache 7 ngày), tập qua E10 `max=20`; **không bao giờ tải RSS trên máy**. Lọc show đã ẩn, D6, blocklist host.
- Điểm tin sáng dựng trong shared (`MorningBrief`): theo thứ tự config, mỗi nguồn tập mới nhất trong 24 h (VnExpress 48 h, Tuổi Trẻ 2 tập), VOV Thời sự ưu tiên "Thời sự 6h" trước 11:00 giờ VN (UTC+7 cố định). "Phát tất cả" là **danh sách duy nhất tự phát tiếp** (`autoAdvance`); mọi danh sách khác giữ hành vi Android (dừng cuối tập).
- Không chữ "radio/đài/phát thanh/trực tiếp/live/kênh" trong chuỗi của hub (`iosApp/scripts/check-news-strings.sh`). Chi tiết: [milestones/M6.md](milestones/M6.md) §3.4.

**[iOS+] Ẩn show (M6 N1 lite):** long-press thẻ/hàng trên Home và kết quả Search → "Ẩn show này" (`show.hide`), gỡ ngay khỏi Home/Search/hub; danh sách chỉ lưu trên máy (`HiddenShowsRepository`), bỏ ẩn ở Cài đặt → "Show đã ẩn". Blocklist mở rộng theo host (feed, original, enclosure, cả subdomain) và author (`shared/assets/blacklist_extra.json`, RC `ts_podcast_black_list` khoá `host`/`author`), seed `ngocbaomedia.com`.

### 3.5 Podcast detail (`PodcastDetailScreen`, `PodcastViewModel`)

| Thuộc tính | Chi tiết |
|---|---|
| Input | `Podcast` (truyền qua `MainViewModel.setCurrentPodcast`, serialize JSON vào `SavedStateHandle`). Nếu bị null sau process death thì pop về màn trước |
| UI | Nút Back nổi (nền đen 30%). Ảnh cover lớn (shared element), tiêu đề, mô tả là HTML render thành text (`HtmlCompat.FROM_HTML_MODE_COMPACT`). Bên dưới là danh sách episode (`EpisodeWidget`: ảnh, tiêu đề, ngày, thời lượng); item đang phát được đánh dấu. Có ad banner và native ad chèn ngẫu nhiên khoảng mỗi 8 item (iOS: bỏ) |
| API | E10 zip E11 (song song), retry 3, cache 15 phút. Chỉ E10 quyết định thành công hay lỗi |
| States | `Init`, `Loading` (shimmer), `Success{episodes, liveItems, listRenderItems}`, `Error{exception}` (dialog "Opps!" với nút "Tải lại" / "Huỷ") |
| Tap episode | `playerEpisode(episode, podcast, playlist = toàn bộ episodes)` → điều hướng tới **Player** |
| Ghi DB | Khi phát: `SaveCurrentPlayingUseCase` insert `Episode` (cả playlist) và `Podcast` vào Room, lưu `extra:current_item_id` |
| **[Sửa]** | Sửa chính tả "Opps!" thành "Oops!" hoặc dịch sang tiếng Việt. Không hiển thị `detail_placeholder` (Lorem ipsum) ở bất kỳ đâu. Lorem ipsum trong bản build là lý do reject 2.1 kinh điển |
| **[iOS+]** | Nút Favourite podcast ngay ở header (Android chỉ favourite được từ Player). Nút Share (`ShareLink`) cho link podcast. Download episode. Sắp xếp mới/cũ |
| iOS (M6) | **Tìm trong show** (N5): `.searchable` khi show > 20 tập; so khớp bỏ dấu (gồm đ/Đ, `VietnameseFold` + `EpisodeFilter` trong shared), mọi từ của query phải có trong tiêu đề hoặc mô tả; query đầu tải tối đa 1.000 tập một lần (cache 15 phút, Detail thường vẫn `max=100`), debounce 200 ms; "Không tìm thấy tập phù hợp"; chú thích khi show có nhiều tập hơn số đã tải; tap kết quả phát danh sách kết quả. **Menu `…`** (`detail.menu`): "Ẩn show này" (hỏi xác nhận, ẩn rồi đóng Detail) và "Báo cáo nội dung" (mail điền sẵn kind/feedId/episodeId/phiên bản app tới `CONTENT_REPORT_EMAIL`; không có email thì ẩn; không mở được Mail thì alert có địa chỉ + sao chép). Event `show_search`, `show_hidden`, `content_report` |

### 3.6 Player full screen (`PlayerScreen`, `PlayerViewModel`)

| Thuộc tính | Chi tiết |
|---|---|
| Vào từ | Detail (tap episode), Home Live, Radio, Favourite (radio), MiniPlayer |
| UI | Nền là artwork blur. Nút đóng (mũi tên xuống) và nút "More" (hiện chưa có hành động). Artwork lớn, tiêu đề, mô tả hoặc tên podcast. Thanh progress kéo được (`PlayerProgress`) hiển thị thời gian đã nghe / tổng. Hàng nút: **Favourite**, **Previous**, **Play/Pause** (đang buffer thì hiện spinner), **Next**, **Playlist**. Có `SlideArea` (bottom sheet kéo lên) chứa danh sách "Up next" (playlist hiện tại) và một banner ad |
| State | `PlayerControlState{isLoading, isPlaying, currentMediaItem, mediaType, currentDuration, totalDuration, currentProgress, isFavourite, podcast, playList, exception}` |
| Hành vi phát | Đặt toàn bộ playlist rồi `seek` tới item được chọn. `setPauseAtEndOfMediaItems(true)`: **hết một episode thì dừng, không tự phát tiếp**. Progress cập nhật mỗi 200 ms |
| Prev/Next | Chỉ chạy khi có item trước hoặc sau trong playlist |
| Favourite | Toggle → `SaveFavouriteUseCase`/`DeleteFavouriteUseCase` với `FavouriteDTO{id, type, title, image, description}`. `type` là `PodcastEpisode` hoặc `Radio` |
| Radio | Trước khi phát phải resolve link bằng `GetPlayableLink` (scrape). Nếu lỗi thì set `exception` |
| Progress an toàn | `progressOf(pos, dur)` trả về 0 khi `dur ≤ 0` hoặc chưa xác định (stream live). **[Parity]** Trên iOS phải xử lý `CMTime.indefinite` và `NaN` tương tự |
| **[Sửa]** | Android **không lưu vị trí nghe**: chỉ lưu `mediaId`, nên mở lại là phát từ đầu. iOS nên lưu `positionMs` theo episode (mỗi 10 giây và khi pause) rồi resume |
| **[iOS+]** | Tốc độ 0.5–3x, sleep timer, nút skip ±15 s / +30 s, AirPlay (`AVRoutePickerView`), chọn "tự phát tiếp" (bật hoặc tắt; mặc định giữ hành vi Android là tắt) |
| iOS (M6, đã làm) | Hàng nút: Favourite · **−15 s** · Play/Pause · **+30 s** · Playlist; Previous/Next chuyển vào đầu danh sách Up next (giữ `player.previous`/`player.next`). Hàng phụ: **tốc độ** (0,5–3,0× bước 0,1, chip 0,8/1,0/1,2/1,5/2,0, nhớ theo show + mặc định chung + "Áp dụng cho mọi show", giữ cao độ `.timeDomain`, live luôn 1,0×) và **hẹn giờ ngủ** (5/10/15/30/45/60 phút / hết tập / tắt; fade âm lượng 10 s cuối; huỷ khi người dùng tự pause, không huỷ khi cuộc gọi/rút tai nghe; "Hết tập" dừng ở cuối tập). Màn khoá: skip −15/+30 và danh sách rate thay next/previous. Logic `SleepTimer`, `EndOfItemPolicy`, `PlaybackSpeed`, `PlaybackProfileRepository` trong shared (Android dùng lại được). Nút `…` (`player.more`) chỉ có "Báo cáo nội dung", chỉ hiện khi có email. Chưa có: AirPlay picker, tự phát tiếp toàn cục, DSP, download |
| iOS (M6, App Shortcuts) | Trong target app (không extension, không App Group): "Tiếp tục nghe" (`AudioPlaybackIntent`: item đang có, không thì tập cuối từ `extra:current_item_id` qua `FavouriteResolver`, từ vị trí đã lưu), "Tạm dừng" (`AudioPlaybackIntent`, như pause của người dùng), "Hẹn giờ tắt" (`minutes`, mặc định 30, 1–180). Câu Siri vi + en trong `AppShortcuts.xcstrings`; tiếng Việt cần Siri tiếng Việt (iOS 18.4+). Event `shortcut_invoked` |
| iOS | Up next là panel peek dưới hàng nút (owner 30/10/2026), không phải sheet: peek "Tiếp theo" + "Đang phát" (+ tập kế nếu đủ chỗ), kéo/tap header hoặc nút danh sách phát để mở cả danh sách, chọn tập thì thu lại; Liquid Glass trên iOS 26, Material ở iOS 17/18. Xem [milestones/M5.md](milestones/M5.md) |

Chi tiết engine: [audio-playback-ios.md](audio-playback-ios.md).

### 3.7 MiniPlayer (`MainActivity.MiniPlayer`, `PlayerWidgetMain`)

- Hiện khi `currentMediaItem != null`, nằm phía trên tab bar (khi ở Main) hoặc sát đáy (khi ở các màn khác), có animation.
- Gồm artwork, tiêu đề, nút play/pause và progress mảnh. Tap thì mở Player.

### 3.8 Favourite tab (`FavouriteScreen`, `FavouriteViewModel`)

| Thuộc tính | Chi tiết |
|---|---|
| Dữ liệu | `SelectAllFavourite` → tất cả `FavouriteDTO` trong Room (bảng `FavouriteDTO`, khoá chính `(type, id)`) |
| UI | Danh sách `FavouriteItemWidget` (ảnh, tiêu đề, mô tả). Khi rỗng: hình minh hoạ + "Không có chương trình yêu thích." + nút "Khám phá thêm" (về Home) |
| Tap | **Episode/Podcast**: lấy `PodcastAndEpisode` từ Room (`podcastDao.selectAllEpisodeById(feedId)`), phát episode tương ứng (hoặc episode đầu tiên), mở **Podcast detail**. **Radio**: lấy danh sách radio từ DB, phát kênh đó, mở **Player** |
| Lưu ý | Mục favourite vẫn mở được offline vì `PruneApiCache` không xoá dữ liệu đã favourite |
| **[Sửa]** | List không tự refresh sau khi favourite ở Player (chỉ load lúc `init`). iOS phải observe `Flow` từ DAO |
| **[iOS+]** | Vuốt để xoá (swipe to delete). Tách section Podcast / Episode / Radio |

### 3.9 Search (`SearchScreen`, `SearchViewModel`)

| Thuộc tính | Chi tiết |
|---|---|
| UI | Ô tìm kiếm với placeholder "Tìm podcast, nghệ sĩ", debounce **200 ms**, IME "Done". Chip chế độ **Tất cả** / **Nhạc**. Khi chưa nhập: gợi ý "Thử tìm kiếm: Radio hay" và lưới danh mục (E9, dùng cache). Khi có kết quả: tiêu đề "Kết qủa hàng đầu cho: %s" (iOS sửa chính tả thành "Kết quả") hoặc "Nhạc cho: %s", kèm danh sách `SearchItem` (ảnh, tiêu đề, tác giả). Khi rỗng: hình + "Không có kết quả cho: <q>". Ở chế độ Nhạc có thêm gợi ý "Kho nhạc theo chuẩn podcast:medium còn ít, thử từ khoá tiếng Anh" |
| API | E7 (Tất cả) hoặc E8 (Nhạc), sau đó lọc blacklist. Đổi chế độ thì tìm lại ngay. Job cũ bị huỷ |
| Tap | `Podcast.fromFeed(feed)` → detail |
| **[Sửa]** | Bấm vào danh mục hiện chưa có hành động. iOS: tap danh mục thì gọi E1 với `cat=<id>` (trending theo danh mục) |
| iOS | `.searchable(text:)` + `.searchScopes` (Tất cả/Nhạc) + `.task(id: query)` để debounce |
| Khuyến nghị | Gửi `clean=true` mặc định (lọc explicit), xem README D6 |

### 3.10 Radio (`featureRadio/RadioScreen`, `RadioViewModel`, `coreRadio`) — phụ thuộc quyết định D1

| Thuộc tính | Chi tiết |
|---|---|
| Nguồn | **VOV**: GET trang chủ vovmedia.vn, parse `.row .col` → link kênh, tên kênh (lấy từ slug), logo (`source[srcset]`). **VOH**: GET voh.com.vn/radio, đi theo `a.btn-more-radio`, parse `script#__NEXT_DATA__` (JSON Next.js: `props.pageProps.pageData.blockList[].chanelList[]{slug,title,url,logoPath}`). Fallback sang 2 parser HTML khác |
| Resolve link | VOV: GET trang kênh (gửi cookie và referer), regex `(?<=url: ").*?(?=")` trên các thẻ `<script>` → URL stream (thường là HLS `.m3u8`). VOH: dùng link `Playable` có sẵn |
| Lưu | Upsert `RadioChannel` vào Room. Khi mạng lỗi thì đọc lại từ DB |
| UI | Danh sách kênh có logo, tên và nhóm (VOV/VOH). Nút play/pause ngay trên từng dòng, phản ánh trạng thái player. Tap dòng thì phát và mở Player. Loading hiện 20 dòng shimmer |
| MediaItem | `mediaType = RADIO_STATION`, `album/artist = "VOV"/"VOH"`, MIME `application/x-mpegURL` nếu URL chứa `m3u8` |
| Rủi ro | Parser vỡ khi website đổi markup (đã có `Crash.record` cho trường hợp này). **Rủi ro pháp lý và App Review**, xem app-store-submission-plan.md §8 |
| **[iOS+]** | Nếu làm: chuyển scrape lên server và trả danh sách kênh dạng JSON qua Remote Config hoặc API riêng. Khi parser vỡ thì sửa server, không phải nộp lại app |

### 3.11 Push notification (`TSFcmServices`)

FCM **data message** có các field `type`, `title`, `content`, `big_image`.

| `type` | Hành vi Android | iOS |
|---|---|---|
| `new_podcasts` | Hiện notification (có ảnh lớn) | Dùng notification payload (`alert`) + Notification Service Extension để tải ảnh. Chỉ gửi khi người dùng đã opt-in (4.5.4) |
| `banner_ads`, `video_ads`, `full_screen_ads` | Hiện notification | **Không dùng** (quảng cáo qua push vi phạm 4.5.4 nếu không opt-in) |

Mọi message đều log analytics `fcm_<type>_message_receive`.

iOS:

- Xin quyền notification **theo ngữ cảnh**, ví dụ sau khi người dùng favourite podcast đầu tiên, không xin ngay lúc launch.
- Có toggle trong Settings.

### 3.12 Deep link

- Android: `tss://ts.podcast/playing?mediaId=<id>` (mở Player từ notification) và `tss://ts.podcast/home`. Chỉ nhận từ `PendingIntent` nội bộ.
- iOS: không cần cho Now Playing, vì hệ thống tự mở app. Nếu muốn link chia sẻ thì dùng **Universal Links** (`https://<domain>/podcast/<feedId>`), không dùng custom scheme. Khi đó cần file `apple-app-site-association`.
  - Cấu trúc URL, `apple-app-site-association`, Smart App Banner và việc thay thế Firebase Dynamic Links (đã ngừng) đều đã được thiết kế trong [seo-web-dashboard.md §A.3, §A.10](../plans/seo-web-dashboard.md). iOS phải route cùng pattern URL với Android, dùng chung parser ID đặt trong shared.
  - Theo [admob-migration.md §2.1](../plans/admob-migration.md): mở app từ deep link thì không hiện App Open ad.

## 4. Analytics events (Firebase)

| Event | Thuộc tính | Nơi phát |
|---|---|---|
| `onboarding_screen` (`trackScreen`) | `CurrentPage` | Onboarding đổi trang |
| `OnboardingFinished` | `screen=Onboarding` | Bấm nút cuối onboarding |
| `fcm_<type>_message_receive` | data của message | Nhận push |
| `applovin_*` (khoảng 20 event về ad) | thông tin ad | Ads. iOS v1: không có |
| Thuộc tính mặc định | `os`, `os_version`, `app_version`, `device_id` (Android ID), `event_time_stamp`, `screen` | Mọi event. **[Sửa]** Bỏ `device_id` trên iOS |

**[iOS+]** Nên thêm các event `play_start`, `play_complete`, `favourite_add`, `search` (không gửi chuỗi query thô nếu có thể chứa thông tin cá nhân). Nhớ khai báo trong App Privacy.

**[iOS+] Event M6** (tên là hằng trong shared `AnalyticsEvents`, Android dùng lại được; mọi event iOS đi qua `ConsentGatedAnalyticsTracker`, không gửi query, tiêu đề hay email):

| Event | Tham số | Nơi phát (iOS) |
|---|---|---|
| `news_hub_open` | `from` (`home_seeAll` \| `home_brief`) | Mở màn hub |
| `news_brief_play` | `count` | "Phát tất cả" của Điểm tin sáng |
| `news_source_unresolved` | `source_key` | Nguồn hub không resolve được (1 lần/process/key) |
| `speed_change` | `rate` (`"1.5"`), `scope` (`show` \| `all`) | Sheet tốc độ, đổi rate từ màn khoá |
| `sleep_timer_set` | `option` (`5`…`60` \| `end_of_episode` \| `off`), `origin` (`player` \| `night_shelf` \| `shortcut`) | Sheet hẹn giờ, kệ đêm khuya, App Shortcut |
| `show_search` | `result_count`, `truncated` | Query đầu tiên trong một lần mở Detail |
| `show_hidden` | `feed_id` | Ẩn show |
| `content_report` | `kind` (`feed` \| `episode` \| `hub`) | Mở mail báo cáo |
| `shortcut_invoked` | `intent` (`continue_listening` \| `pause` \| `set_sleep_timer`) | Mỗi lần App Shortcut chạy |

## 5. Bản địa hoá và giao diện

- **Ngôn ngữ:** chỉ có tiếng Việt (`values/strings.xml`, không có `values-*`). Vài chuỗi tiếng Anh bị hardcode: tên tab "Favorite", "TSPodcast", "Search", "Radio"; "Live Podcasts"; "Opps!".
  - iOS: tạo `Localizable.xcstrings` với **`vi` là development language** và thêm **`en`**. Reviewer của Apple đọc tiếng Anh; bản `en` giúp họ hiểu app và giảm rủi ro 2.1/4.2.
  - Metadata App Store: `vi` + `en-US`.
  - M6: mọi chuỗi mới có vi + en (155 khoá trong `Localizable.xcstrings`); câu Siri của App Shortcuts nằm riêng trong `AppShortcuts.xcstrings` (vi + en, mỗi câu chứa `${applicationName}`). N7: không câu chữ nào gợi ý nghe khi chạy xe máy/lái xe (String Catalog và `fastlane/metadata`).
- **Quyền riêng tư (M6 N11 consent lite):** lần đầu mở app có sheet "Quyền riêng tư" (`consent.sheet`) không chặn app: "Cần thiết" luôn bật (chạy app, Remote Config cho danh sách chặn, ID cài đặt ngẫu nhiên), "Phân tích sử dụng" và "Báo cáo sự cố" tắt mặc định; hai nút "Đồng ý tất cả" / "Chỉ cần thiết" ngang hàng, cả hai đều vào app. Firebase Analytics/Crashlytics tắt trong Info.plist, chỉ bật lúc chạy sau khi đồng ý; đổi lại trong Cài đặt (`settings.analytics`, `settings.crash`). Lưu version + thời điểm quyết định (`ConsentRepository`, shared). Văn bản hiện là placeholder chờ luật sư (handoff.md). Android chưa có.
- **Màu:** lấy từ `sharedLibrary/theme/ColorsTheme.kt`, ví dụ `Primary #184BB4`, `Secondary #4A7DE7`, `TextColor #1F1F1F`, `TextTitleColor #39375B`, `SubTitleColor #999999`, các tông xám `Gray*`.
  - App Android chỉ có **light mode** (dark mode đang bị comment).
  - iOS v1: hoặc khoá `UIUserInterfaceStyle = Light` cho giống Android, hoặc (khuyến nghị) định nghĩa Color Set có biến thể Dark.
- **Font:** dùng font mặc định của hệ thống (`TextStyles` trong sharedLibrary). Trên iOS dùng SF Pro và hỗ trợ **Dynamic Type**.
- **Blur:** `hazeAndroid` → `.ultraThinMaterial` / Liquid Glass.
- **Shimmer:** `Placeholder`/`Shimmer` → `.redacted(reason: .placeholder)` kèm shimmer modifier tự viết.
- **Shared element:** Home → Detail (ảnh, tiêu đề, nền) → `.matchedTransitionSource` + `.navigationTransition(.zoom(sourceID:in:))` (iOS 18+).

## 6. Mô hình dữ liệu cần có trong shared

| Model | Field chính dùng trong UI |
|---|---|
| `Podcast` | `id`, `feedId`, `title`, `feedTitle`, `image`, `feedImage`, `description`, `categories`, `explicit`, `feedLanguage`, `datePublished` |
| `Feed` | `id`, `title`, `author`, `ownerName`, `image`, `artwork`, `description`, `categories`, `explicit (Bool)`, `language`, `medium`, `episodeCount`, `itunesId`, `url`, `link` |
| `Episode` | `id`, `feedId`, `title`, `description`, `enclosureUrl`, `enclosureType`, `enclosureLength`, `duration (giây)`, `image`/`feedImage` (`getImageUrl()` = image trim, nếu rỗng thì dùng feedImage), `datePublished`, `persons`, `explicit`, `transcripts`, `chaptersUrl` |
| `LiveEpisode` | như Episode, thêm `startTime`, `endTime`, `status`, `contentLink` |
| `CategoryRes.Category` | `id`, `name`, `isFavourite?` |
| `RadioChannel` | `channelId`, `channelName`, `logo`, `category ("VOV"/"VOH")`, `categories`, `links[{type: Playable/Browsable, link, id, source}]` |
| `FavouriteDTO` | `id: String`, `type: MediaType {Podcast, Radio, PodcastEpisode, TVChannel}`, `title`, `image`, `description` |

Bảng Room (v6): `Podcast`, `Feed`, `Episode`, `FavouriteDTO`, `RadioChannel`. Schema xem `core/schemas/tss.t.core.storage.PodcastDatabase/6.json`.

## 7. Checklist parity (dùng cho QA)

- [ ] Onboarding 4 trang → chọn ≥ 3 danh mục → Home; mở lại app thì vào thẳng Home
- [ ] Home có đủ 5 section, có shimmer, pull-to-refresh, attribution PodcastIndex
- [ ] Blacklist lọc được feed theo id và từ khoá (thử bằng Remote Config)
- [ ] Detail: hiển thị HTML description đúng, danh sách episode, dialog lỗi có reload
- [ ] Player: play/pause/seek/prev/next/favourite, playlist; hết episode thì dừng
- [ ] Background audio, Lock Screen, Control Center, tai nghe Bluetooth, AirPods
- [ ] Favourite: thêm/xoá, list cập nhật, mở được khi offline
- [ ] Search: Tất cả/Nhạc, debounce, trạng thái rỗng
- [ ] (Nếu có) Radio: VOV + VOH phát được, có fallback DB khi offline
- [ ] Cache: trending 15 phút, prune 7 ngày
- [ ] Crashlytics nhận được crash Swift **và** Kotlin (CrashKiOS), userId = InstallId
- [ ] Remote Config `ts_podcast_black_list`, `ads_enable` hoạt động

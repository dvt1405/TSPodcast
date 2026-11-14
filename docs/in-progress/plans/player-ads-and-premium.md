# Quảng cáo trong Player + danh sách "Tiếp theo", và kế hoạch gói Premium (Android + iOS)

> **Trạng thái: Kế hoạch, chưa code** (2026-11-14). Mọi placement mới ở đây **mặc định tắt** qua Remote
> Config; chưa đổi app, Remote Config, App Store Connect hay Play Console.

> Yêu cầu owner (2026-11-14): "Thêm cả phần ads vào màn player và list podcast trong player luôn, tham
> khảo cách cho vào của các app nhạc để tham khảo, planning cho các gói chuẩn bị bán."
> Mốc code: `main` @ `d1182cf` (sau PR #71).
> Đọc cùng: [`monetization-ads-iap.md`](monetization-ads-iap.md) (contract ads + IAP hiện tại),
> [`admob-migration.md`](admob-migration.md) (quy tắc audio-first, §2), `remoteconfig/ads-iap.params.json`,
> [`roadmap-backlog.md`](roadmap-backlog.md), [`../research/feature-roadmap-ai-growth.md`](../research/feature-roadmap-ai-growth.md).

---

## Owner đã quyết (2026-11-14)

- **Placement Player:** làm `player_up_next` (native trong Tiếp theo), `player_now_playing` (dải native dưới ảnh bìa) và `player_pause` (A/B), cả Android lẫn iOS, **mặc định tắt** trên Remote Config. Chưa làm mục "Gỡ quảng cáo / Báo cáo quảng cáo" trong menu Player (lưu ý: App Store 2.5.18 yêu cầu có cách báo cáo quảng cáo — phải có trước khi bật placement Player trên iOS).
- **Giá:** 29.000₫/tháng, 249.000₫/năm (dùng thử 7 ngày), 499.000₫ trọn đời. Đã đặt trên App Store Connect (VN là nước gốc, nước khác theo quy đổi của Apple). Play Console: tạo sản phẩm cùng giá khi bật IAP.
- **Bật IAP:** chờ bật **cùng lúc** Android và iOS (sau Paid Apps Agreement); `iap_enabled` giữ `false`.

## 0. Tóm tắt khuyến nghị

1. **Không bao giờ** đặt quảng cáo toàn màn hình (interstitial, app open) trên Player hay ngay khi bấm
   Play / Next. Quy tắc này `AdPolicy` đã có và giữ nguyên. Cũng không chèn quảng cáo **âm thanh**
   (AdMob không có định dạng này cho app, và ta không sở hữu audio podcast).
2. **Up next (danh sách tập trong Player), làm trước:** chèn **native nhỏ** như một hàng trong danh
   sách, sau mục thứ 3, rồi cách 8 mục, tối đa 2 hàng. Chỉ hiện khi panel **mở hẳn**, không bao giờ ở
   phần peek và không bao giờ là hàng đầu (sát nút Trước/Tiếp). Placement mới `player_up_next`.
3. **Player, làm sau:** một **thẻ native nhỏ (strip ~72–88 dp/pt) ngay dưới ảnh bìa, trên tên tập**.
   Ảnh bìa thu nhỏ lại để nhường chỗ, cách thanh tua ít nhất 48 dp/pt. Chỉ đổi quảng cáo khi đổi tập
   hoặc sau ≥ 60 giây lúc Player đang hiện. Ẩn khi màn hình thấp, iPhone xoay ngang hoặc chữ cỡ trợ
   năng. Placement mới `player_now_playing`.
4. **Thẻ lúc tạm dừng** (native medium thế chỗ ảnh bìa khi người dùng tự bấm dừng ≥ 5 giây) chỉ chạy
   **A/B test**, mặc định tắt. Placement `player_pause`.
5. **Banner `player_episode_list` đang có trên Android thì tắt hẳn và gỡ khỏi `SlideArea`.** Nó nằm
   ngay dưới tiêu đề "Tiếp theo" nên khi panel thu gọn có thể lộ ra sát hàng tốc độ / hẹn giờ ngủ
   (§1). Native trong danh sách thay thế nó.
6. **Lối thoát quảng cáo ngay trong Player:** menu "…" có mục "Gỡ quảng cáo", mở sheet gồm Premium
   và "Xem 1 video để nghe 2 giờ không quảng cáo" (`rewarded_ad_free` đã có sẵn, chỉ thiếu call site).
   Không đặt nút "gỡ quảng cáo" sát ad.
7. **Remote Config:** 3 placement mới trong `ads_placements_enabled` (đều `false`), 8 key `ads_player_*`
   và 3 key `iap_upsell_*` (§3.6). **Ad unit:** giai đoạn đầu dùng lại unit Native hiện có. Owner tạo
   thêm 3 unit Native riêng cho Player trên mỗi nền tảng để đo và đặt sàn giá riêng (§3.7).
8. **Gói bán:** giữ 3 sản phẩm đã tạo trên ASC nhưng **hạ giá lúc ra mắt**, vì hiện quyền lợi chỉ có
   "không quảng cáo": **29.000₫/tháng, 249.000₫/năm (dùng thử 7 ngày), 499.000₫ trọn đời "Bỏ quảng
   cáo"** (đang đặt 49.000 / 399.000 / 599.000₫). Khi có tính năng Premium thật (tải tự động, AI, đồng
   bộ) thì nâng giá cho **người đăng ký mới**, người đang dùng giữ giá cũ (§5).
9. **Bật IAP trên Android trước** (Play không cần Paid Apps Agreement), iOS bật sau khi Apple duyệt
   agreement. Dùng condition Remote Config theo nền tảng.
10. Rollout từng placement theo 10% → 50% → 100%. Dừng nếu **thời gian nghe / DAU giảm > 3%** hoặc CTR
    của placement Player cao gấp 2 lần trung bình app (dấu hiệu click nhầm).

---

## 1. Hiện trạng (đối chiếu code trên `main`)

| Phần | Android | iOS |
|---|---|---|
| Player | `app/.../ui/screens/player/PlayerScreen.kt` + `widgets/PlayerArea.kt`: hàng đóng/"…", ảnh bìa vuông (`fillMaxWidth`, padding 30 dp, `aspectRatio(1f)`), tên tập, tên show, `PlayerProgress`, thời gian, lỗi, `PlayerControl` (Yêu thích · −15 · Play · +30 · Danh sách), `PlayerExtrasRow` (tốc độ, hẹn giờ ngủ) | `iosApp/TSPodcast/Player/PlayerView.swift`: thứ tự giống Android. Ảnh bìa là phần co giãn ("shrinks first so controls + peek always fit"). Ở chiều cao compact (iPhone ngang), peek vẽ ngay trong Player |
| Up next | `widgets/SlideArea.kt`: panel trượt. Lúc `Hidden`, panel bị đẩy xuống bằng chiều cao `PlayerArea` nên chỉ còn lộ tiêu đề "Tiếp theo" ở đáy. **`AdBannerSlot(PlayerEpisodeList)` nằm ngay dưới tiêu đề, trên `LazyColumn`**, nên lúc thu gọn có thể lộ ra sát hàng điều khiển (tuỳ chiều cao máy) | `Player/UpNextPanel.swift`: system sheet 2 detent (peek và open). `UpNextList` luôn mounted, trong suốt lúc thu gọn. **Hàng đầu là nút Trước / Tiếp**. Chưa có ad |
| Placement | `ads/src/main/java/tss/t/ads/AdsApi.kt`: `PlayerEpisodeList("player_episode_list", Banner)`, tắt trong `AdsConfig.kt` | `shared/src/commonMain/kotlin/tss/t/shared/ads/AdsSettings.kt`: cùng enum, tắt. `ads_unit_ids` iOS map sang unit Banner |
| Chính sách | `AdPolicy.canShowFullScreen` chặn `audio_playing`, `player_screen`, `external_launch`… `canShowInline` chỉ xét kill switch, placement, consent và `ad_free`, **không xét grace / phiên đầu** | Bản port 1:1 trong `shared/.../ads/AdPolicy.kt` |
| Native trong list | `ads/.../NativeSlots.kt` `interleaveNativeSlots` (dùng ở `PodcastViewModel`) | `AdsController.nativeSlots(after:placement:)` (dùng ở `PodcastDetailView`) |
| Render | `ads-admob/.../AdMobAdRenderer.kt`: banner inline adaptive tối đa 100 dp, template native theo placement. `AdSlotCache`: mỗi slot chỉ load 1 lần; refresh banner đặt trên AdMob | `Ads/AdSlotViews.swift` (`AdBannerSlot`, `AdNativeSlot`, `NativeAdTemplateView` small/medium có nhãn "Quảng cáo"), `Ads/AdsController.swift` |
| Paywall | `ui/screens/paywall/*`, `billing/` (`PaywallSource.SETTINGS`), lối vào duy nhất là Cài đặt | `Store/PaywallView.swift`, `PaywallModel(source: "settings")`, lối vào duy nhất là Cài đặt |
| IAP | `premium_monthly`, `premium_yearly` (trial 7 ngày), `remove_ads_lifetime`, đều chỉ có benefit `ad_free`. `iap_enabled=false` | Đã tạo trên ASC: 49.000₫ / 399.000₫ / 599.000₫. Play chưa tạo |

---

## 2. Các app nhạc / podcast đặt quảng cáo thế nào

| App | Trong màn Now Playing | Trong queue / danh sách | Đổi quảng cáo lấy gì | Ghi chú |
|---|---|---|---|---|
| **Spotify Free** | Display ad (một ảnh hero) và carousel tới 6 thẻ trong Now Playing View. Audio ad 15–30 s có **companion banner 1:1** hiện trong lúc phát. Video takeover có tiếng | Display ad cả ở Browse/Search và playlist của Spotify | **Sponsored Session:** xem video 15–30 s để nghe 30 phút không quảng cáo (opt-in, mobile) | [Spotify Ads – display](https://ads-web.spotify.com/ad-formats/display-ads), [TechCrunch 2014](https://techcrunch.com/2014/09/08/the-music-streaming-revolution-will-be-televised/), [Spotify Ads](https://ads.spotify.com/en-US/news-and-insights/building-a-platform-for-fans-and-brands/) |
| **YouTube Music** | Từ 06/2025 thử banner **giữa ảnh bìa và cụm điều khiển**, ảnh bìa thu nhỏ để nhường chỗ (ảnh, 1 dòng chữ, nhãn "Sponsored", nút CTA) | — | Premium | [9to5Google](https://9to5google.com/2025/06/06/youtube-music-now-playing-ads/) |
| **Pocket Casts** | 09/2025 bật banner **trong khu now playing, cạnh ảnh bìa**, kèm lời mời Plus ngay trên ảnh bìa | Có cờ quảng cáo riêng cho màn Podcasts | Plus | **Bị phản ứng mạnh** vì "đổi luật": người dùng cũ (Champion) cũng thấy ads, CEO phải gọi là bug. [Blog](https://blog.matthewbrunelle.com/podcasts-you-altered-the-deal-so-i-will-alter-your-app/), [forum](https://forums.pocketcasts.com/forums/topic/the-new-advertisements-seriously-suck) |
| **Castbox** | Theo review, quảng cáo hiện **thay chỗ ảnh tập** khi phát | Có | Premium bỏ quảng cáo hình/video, bỏ giới hạn 100 kênh, hiệu ứng phát riêng theo show | [Castbox Help](https://helpcenter.castbox.fm/portal/kb/articles/castbox-premium), [App Store](https://apps.apple.com/us/app/podcast-player-app-castbox/id1243410543) |
| **Podcast Addict** | Banner Google ở đáy màn hình (theo FAQ cũ) | — | Premium bỏ quảng cáo hình. Người mua bản "ad-free" trọn đời cũ được giữ quyền | [FAQ](https://podcastaddict.com/faqs) |
| **Pandora** | — | — | **Video Plus:** xem video để có thêm lượt skip / replay (hết hạn sau 1 giờ) hoặc một phiên on-demand Premium | [TechCrunch 2017](https://techcrunch.com/2017/12/14/pandora-listeners-can-now-watch-video-ads-to-access-on-demand-music), [Marketing Dive](https://www.marketingdive.com/news/pandora-expands-reward-based-video-plus-to-all-advertisers/505314/) |
| **SoundCloud** | Banner display trong app, interstitial tĩnh, video ad trên mobile | Trang track | Go / Go+ | [SoundCloud advertise](https://soundcloud.com/company/advertise), [Help](https://help.soundcloud.com/hc/en-us/articles/115003447287-Ads-on-SoundCloud) |
| **Audiomack** | Có banner (Audiomack+ quảng bá "không gián đoạn, không banner") | — | **Rewarded:** xem quảng cáo ngắn để có thời gian nghe không quảng cáo (icon hộp quà) | [Audiomack+](https://audiomack.com/world/post/welcome-to-audiomack-plus), [App Store](https://apps.apple.com/ng/app/id921765888) |
| **Zing MP3 / NhacCuaTui** | Bản miễn phí có quảng cáo. VIP bỏ quảng cáo. Zing bắt xem quảng cáo trước khi tải Lossless nếu không có VIP | — | VIP / Plus | [Zing MP3 App Store VN](https://apps.apple.com/vn/app/id992357547), [NCT App Store VN](https://apps.apple.com/vn/app/nct-nhaccuatui-nghe-mp3/id1210763463?l=vi) |

**Điểm chung:**
- Chỗ quảng cáo trong Now Playing gần như luôn là **vùng ảnh bìa**: thay ảnh bìa, đặt cạnh ảnh bìa, hoặc
  chen giữa ảnh bìa và cụm điều khiển rồi thu nhỏ ảnh. Không app nào đặt quảng cáo **giữa các nút điều
  khiển**.
- Quảng cáo trong danh sách là một hàng "Sponsored" trông giống hàng nội dung nhưng có nhãn rõ.
- Hầu hết đều có **lựa chọn đổi quảng cáo lấy thời gian không quảng cáo** (Spotify, Audiomack, Pandora),
  vừa là thử nghiệm Premium vừa xả bớt khó chịu.

**Người dùng chịu được:** banner/native tĩnh, không tiếng, ở vùng ảnh bìa; rewarded tự chọn; quảng cáo
trong danh sách có nhãn.

**Làm hại giữ chân:**
- Đổi luật với người đã trả tiền (Pocket Casts).
- Quảng cáo âm thanh/video có tiếng chen vào lúc nghe.
- Quảng cáo khiến nút điều khiển nhảy chỗ.
- Interstitial lúc bấm Play.

TSPodcast không có audio ad nên lợi thế lớn nhất là **giữ audio sạch**, và nói rõ điều đó trên paywall.

---

## 3. Vị trí quảng cáo đề xuất

### 3.1 Quy tắc cứng (mỗi quy tắc thành một unit test của `AdPolicy` / test UI)

1. **Không quảng cáo toàn màn hình trên Player**, không interstitial khi bấm Play / Next / chọn tập trong
   Up next. Giữ `player_screen`, `audio_playing` của `canShowFullScreen`. Rewarded là ngoại lệ duy nhất:
   người dùng tự bấm, và player được tạm dừng trước khi hiện.
2. **Không đặt sát hay xen giữa nút điều khiển.** Mọi ad trong Player cách vùng chạm của thanh tua, cụm
   transport, hàng tốc độ/hẹn giờ và tiêu đề "Tiếp theo" **≥ 48 dp/pt**. Không làm được thì không hiện.
3. **Không xê dịch layout khi Player đang hiện.** Chỉ chèn hoặc gỡ ad ở hai thời điểm: lúc Player vừa
   mở (ad đã preload) và lúc đổi tập. Ad tải xong muộn thì để lần sau. Nút Play không bao giờ di chuyển
   vì ad.
4. **Video trong native luôn tắt tiếng.** `VideoOptions` để `startMuted = true` (mặc định của SDK, không
   được override). Không bao giờ phát tiếng đè podcast.
5. **Chỉ load / tính impression khi ad thật sự hiện:** Player đang ở foreground và Up next đang mở hẳn.
   Không mount ad trong view trong suốt (iOS `UpNextList` lúc thu gọn có opacity 0).
6. **Không quảng cáo ngoài app:** media notification, màn hình khoá, Now Playing của hệ thống, widget,
   CarPlay / Android Auto, Live Activity (Apple 2.5.18, Play lockscreen monetization).
7. **Premium và cửa sổ `ad_free` (rewarded) = không quảng cáo nào**, kể cả hàng trống. Đã có
   `Disabled("premium")` và `commonBlock → "ad_free"`.
8. **Grace cho Player:** các placement Player tuân theo `ads_grace_period_hours` (24 h) và bỏ qua phiên
   đầu. Lần đầu vào màn chính phải sạch. Đây là quy tắc mới, thêm vào `canShowInline` cho riêng nhóm
   Player.
9. Ad phải **nhìn khác nội dung**: nền thẻ riêng, nhãn "Quảng cáo" ≥ 15 px ở đầu thẻ, AdChoices không bị
   che, nền không clickable (AdMob native policy).

### 3.2 Bảng placement

| Key (mới/cũ) | Format | Vị trí | Khi hiện | Đổi quảng cáo | Cap | Mặc định |
|---|---|---|---|---|---|---|
| `player_up_next` (mới) | Native **small** | Hàng trong danh sách Up next: sau mục 3, cách 8, tối đa 2 | Chỉ khi panel **mở hẳn**. Không ở peek, không là hàng đầu, không ngay sau nút Trước/Tiếp. Queue < 4 mục thì không có | Load lần đầu mở panel, giữ trong phiên (cache theo `ad-0`, `ad-1`). Tải lại nếu ad đã giữ > 60 phút | `ads_player_up_next_max` = 2 / danh sách | `false` |
| `player_now_playing` (mới) | Native **small** (strip ~72–88 dp/pt) | **Dưới ảnh bìa, trên tên tập**. Ảnh bìa thu nhỏ để giữ nguyên vị trí cụm điều khiển | Player visible, cả lúc phát lẫn lúc dừng. Ẩn ở chiều cao compact, cỡ chữ trợ năng, màn thấp hơn `ads_player_min_height_dp`, khi có lỗi phát (`PlayerErrorView`) | Khi đổi tập (nếu ≥ refresh), và mỗi `ads_player_native_refresh_sec` (60 s) khi Player đang hiện. Không refresh ở nền | 1 ad / màn | `false` |
| `player_pause` (mới, A/B) | Native **medium** (MediaView, CTA) | **Thế chỗ ảnh bìa** | Người dùng tự bấm dừng (không phải buffering, lỗi, hết hẹn giờ ngủ) và Player hiện ≥ `ads_player_pause_delay_sec` (5 s). Lúc này ẩn strip. Biến mất ngay khi phát lại bằng bất kỳ đường nào (nút, tai nghe, notification) | Mỗi lần dừng là một ad mới (nếu đủ khoảng cách) | `ads_player_pause_session_cap` = 3 / phiên, cách nhau ≥ 5 phút | `false` |
| `rewarded_ad_free` (đã có) | Rewarded | Lối vào: menu "…" của Player → "Gỡ quảng cáo" → "Xem 1 video để nghe 2 giờ không quảng cáo", và cuối paywall | Chỉ khi người dùng bấm. Dialog opt-in ghi rõ phần thưởng. Tạm dừng audio trước, phát lại sau nếu trước đó đang phát | — | `ads_rewarded_daily_cap` = 3 (đã có) | `false` → bật cùng P3 |
| `player_episode_list` (đã có) | Banner | — | **Giữ `false`, gỡ slot khỏi `SlideArea`** (§1). iOS chưa từng dùng | — | — | `false` |

**Không làm:** interstitial/app open trên Player; audio ad; ad giữa các nút điều khiển; ad trong peek Up
next; collapsible banner trong Player (đè nội dung khi mở rộng); banner neo đáy Player (sát nút
điều khiển và tiêu đề Up next).

### 3.3 Phác thảo bố cục (portrait)

```
┌──────────────────────────────┐
│ ⌄                          … │  hàng đóng (… có "Gỡ quảng cáo")
│                              │
│      ┌──────────────┐        │
│      │   ảnh bìa    │        │  co lại khi có strip; player_pause thế chỗ khi dừng
│      └──────────────┘        │
│ ┌──────────────────────────┐ │
│ │[Quảng cáo] icon  tiêu đề │ │  player_now_playing (native small)
│ │            mô tả   [CTA] │ │
│ └──────────────────────────┘ │
│ Tên tập (2 dòng)             │  ≥ 48 dp từ đáy thẻ ad tới vùng chạm thanh tua
│ Tên show                     │
│ ━━━━━━━━●────────────        │  thanh tua
│ ♡    −15    ▶    +30    ☰    │  KHÔNG BAO GIỜ có ad ở đây
│      [1.0×]   [☾]            │
│──────── Tiếp theo ───────────│  peek: không ad
└──────────────────────────────┘

Up next (mở hẳn):
  [⏮ Trước] [Tiếp ⏭]            hàng nút (iOS)
  Tập 1
  Tập 2
  Tập 3
  [Quảng cáo] native small       player_up_next, sau mục 3
  Tập 4 … Tập 11
  [Quảng cáo] native small       cách 8 mục, tối đa 2
```

### 3.4 Thay đổi code theo nền tảng

**Chung (contract):**
- `remoteconfig/ads-iap.params.json` + `remoteconfig/README.md`: key mới §3.6, `ads_unit_ids` iOS thêm
  3 placement.
- `docs/in-progress/plans/monetization-ads-iap.md`: thêm §Player.
- `docs/done/analytics/analytics-events.md`: `source` mới của paywall.

**Android:**
- `ads/src/main/java/tss/t/ads/AdsApi.kt`: thêm `PlayerNowPlaying("player_now_playing", Native)`,
  `PlayerUpNext("player_up_next", Native)`, `PlayerPause("player_pause", Native)`.
- `ads/.../AdsConfig.kt`: `AdsKeys` + `AdsSettings` (default, parse) cho các key `ads_player_*`.
- `ads/.../AdPolicy.kt`: `canShowPlayerInline(placement, settings, ctx)` (grace, phiên đầu, chiều cao,
  trạng thái dừng, cap phiên của `player_pause`).
- `ads/.../NativeSlots.kt`: overload nhận first/interval/max riêng cho Up next.
- `ads-admob/.../AdMobAdRenderer.kt`: map template (now playing, up next là small; pause là medium),
  giữ `startMuted`. `AdSlotCache.kt`: `reload(slot)` cho refresh và hạn 60 phút, chỉ khi slot đang hiện.
- `app/.../player/widgets/PlayerArea.kt`: ảnh bìa thành phần co giãn (`weight(1f, fill = false)` + giới
  hạn vuông) để strip không đẩy cụm điều khiển. Chèn `AdNativeSlot(PlayerNowPlaying)` dưới ảnh bìa.
  Hoán đổi ảnh bìa và `PlayerPause` khi dừng.
- `app/.../player/widgets/SlideArea.kt`: **xoá `AdBannerSlot(PlayerEpisodeList)`**. Trong `LazyColumn`,
  chỉ khi `SlideAreaState.Expanded`, dùng `interleaveNativeSlots(playList, PlayerUpNext, …)` và render
  `AdNativeSlot` cho `AdSlotRow` (key ổn định `placement/ad-n`).
- `app/.../player/PlayerScreen.kt`: menu "…" thêm "Gỡ quảng cáo" (sheet: Premium / rewarded). Truyền
  `slideState` xuống để biết đang mở hay thu gọn.
- `app/.../player/PlayerViewModel.kt`: trạng thái "người dùng tự dừng từ lúc T" cho `player_pause`, sự
  kiện đổi tập cho refresh. Preload native Player khi bắt đầu phát (MiniPlayer hiện).
- `billing/.../BillingApi.kt` `PaywallSource`: thêm `PLAYER_MENU`, `AD_UPSELL`, `REWARDED_CAP`.

**iOS:**
- `shared/src/commonMain/kotlin/tss/t/shared/ads/AdsSettings.kt` + `AdPolicy.kt`: port y hệt Android,
  cập nhật `AdsKeys.ALL`, `AdsSettingsTemplateTest`, `AdPolicyTest`.
- `iosApp/TSPodcast/Ads/AdsController.swift`: preload cho Player, `reload` có giới hạn tần suất, hạn 60
  phút, `nativeTemplate(for:)` cho 3 placement. Không đổi `VideoOptions` (mặc định đã tắt tiếng).
- `iosApp/TSPodcast/Ads/AdSlotViews.swift`: biến thể strip (viền / nền tách khỏi blur của Player) và thẻ
  pause (medium, media 1:1 hoặc 16:9 vừa khung ảnh bìa).
- `iosApp/TSPodcast/Player/PlayerView.swift`: `AdNativeSlot(.playerNowPlaying)` giữa `ArtworkImage` và
  khối tiêu đề (ảnh bìa đã là phần co giãn). Ẩn khi `isCompactHeight` hoặc
  `dynamicTypeSize.isAccessibilitySize`. `moreMenu` hiện cả khi không có `contentReporter`, thêm mục "Gỡ
  quảng cáo".
- `iosApp/TSPodcast/Player/UpNextPanel.swift`: `UpNextList` chỉ chèn hàng ad khi `isAccessible`
  (`panel.isExpanded`). **Không** chèn trong `UpNextHeader` (peek). Dùng
  `ads.nativeSlots(after:placement:)` theo mẫu `PodcastDetailView`.
- `iosApp/TSPodcast/Store/PaywallModel.swift` / `PaywallView.swift`: source mới, dòng lợi ích "Audio
  không bao giờ bị chèn quảng cáo".

### 3.5 Hành vi theo trạng thái

| Trạng thái | `player_now_playing` | `player_pause` | `player_up_next` |
|---|---|---|---|
| Đang phát / buffering | Hiện (nếu đã preload) | Không | Khi panel mở |
| Người dùng tự dừng ≥ 5 s | Ẩn khi thẻ pause hiện | Hiện (A/B) | Khi panel mở |
| Dừng do hết hẹn giờ ngủ, mất mạng, lỗi phát | Lỗi: ẩn. Còn lại: giữ | Không | Khi panel mở |
| Live stream | Hiện | Không (live không "dừng" theo nghĩa tập) | Khi panel mở |
| Up next đang mở (che Player) | Không refresh (không hiện) | Không | Hiện |
| iPhone ngang (compact height), Android cao < `ads_player_min_height_dp` | Ẩn | Ẩn | Hiện (full sheet / list) |
| Cỡ chữ trợ năng (AX1+), TalkBack/VoiceOver | Ẩn strip (tránh đẩy nội dung) | Ẩn | Hiện, ad có label trợ năng "Quảng cáo" |
| App ở nền, màn hình khoá | Không load, không refresh | Không | Không |
| Premium, cửa sổ `ad_free`, kill switch, chưa consent UMP | Không | Không | Không |
| 24 h đầu sau cài / phiên đầu | Không | Không | Không |

### 3.6 Remote Config

`ads_placements_enabled` thêm `"player_now_playing": false, "player_up_next": false, "player_pause":
false` (giữ `player_episode_list: false`). `ads_unit_ids` (iOS) thêm 3 key, ban đầu trỏ vào unit Native
hiện có `ca-app-pub-8696388065064767/2538245333`. Android `{}` dùng id build-time
(`ADMOB_UNIT_NATIVE`) hoặc override bằng key này.

Key mới (nhóm "Ads"):

| Key | Kiểu | Default | Ý nghĩa |
|---|---|---|---|
| `ads_player_respect_grace` | BOOLEAN | `true` | Placement Player tuân theo `ads_grace_period_hours` và bỏ phiên đầu |
| `ads_player_min_height_dp` | NUMBER | `640` | Cửa sổ thấp hơn mức này (dp/pt) thì không có `player_now_playing` / `player_pause` |
| `ads_player_native_refresh_sec` | NUMBER | `60` | Khoảng tối thiểu giữa hai ad của `player_now_playing` khi Player đang hiện. `0` = chỉ đổi khi đổi tập. Dưới 30 thì coi là 30 |
| `ads_player_up_next_first_index` | NUMBER | `3` | Hàng ad đầu tiên sau mục thứ N (≥ 2, không bao giờ là hàng đầu) |
| `ads_player_up_next_interval` | NUMBER | `8` | Khoảng cách giữa hai hàng ad |
| `ads_player_up_next_max` | NUMBER | `2` | Số hàng ad tối đa trong Up next |
| `ads_player_pause_delay_sec` | NUMBER | `5` | Dừng bao lâu thì thẻ pause mới hiện |
| `ads_player_pause_session_cap` | NUMBER | `3` | Số thẻ pause tối đa mỗi phiên (cách nhau ≥ 5 phút, cố định trong code) |

Key mới (nhóm "IAP"):

| Key | Kiểu | Default | Ý nghĩa |
|---|---|---|---|
| `iap_upsell_enabled` | BOOLEAN | `false` | Lời mời mềm "Gỡ quảng cáo" sau N quảng cáo |
| `iap_upsell_after_impressions` | NUMBER | `30` | Số impression (mọi placement) kể từ lần mời trước |
| `iap_upsell_min_interval_days` | NUMBER | `7` | Khoảng cách tối thiểu giữa hai lần mời |

Condition mới `platform_android` (`device.os == 'android'`) để bật `iap_enabled` riêng Android trước.

### 3.7 Ad unit

| Placement | Giai đoạn đầu (không chặn) | Unit mới owner nên tạo |
|---|---|---|
| `player_up_next` | iOS `ca-app-pub-8696388065064767/2538245333` (Native). Android: `ADMOB_UNIT_NATIVE` | Native: `TSP_Android_Native_PlayerUpNext`, `TSP_iOS_Native_PlayerUpNext` |
| `player_now_playing` | Như trên | Native: `TSP_Android_Native_PlayerNowPlaying`, `TSP_iOS_Native_PlayerNowPlaying` |
| `player_pause` | Như trên | Native (cho phép video): `TSP_Android_Native_PlayerPause`, `TSP_iOS_Native_PlayerPause` |
| `rewarded_ad_free` | iOS `ca-app-pub-8696388065064767/8512626743` | Android: **kiểm tra secret `ADMOB_UNIT_REWARDED`**. Owner mới cấp unit thật cho App Open, Interstitial, Banner, Native (`admob-migration.md`, cập nhật 2026-11-01). Nếu chưa có thì tạo `TSP_Android_Rewarded_AdFree` |

Tách unit riêng để xem doanh thu, CTR và tỉ lệ click nhầm của từng chỗ trong Player, và đặt sàn eCPM
riêng (ad trong Player nên có sàn cao hơn, ít mà chất lượng). Unit mới đưa vào bằng `ads_unit_ids`,
không cần build mới (Android release đọc override, debug luôn dùng unit test).

### 3.8 Đo lường và rollout

- Event có sẵn `ads_*` (load, impression, click, paid) mang `placement` = key mới. Thêm
  `paywall_view` / `purchase_*` với `source` = `player_menu`, `ad_upsell`, `rewarded_cap`.
- Rollout từng placement bằng Remote Config theo % (hoặc Firebase A/B): 10% → 50% → 100%, mỗi bước ≥ 7
  ngày. Thứ tự: `player_up_next` → `player_now_playing` → (A/B) `player_pause`.
- Guardrail, dừng nếu chạm:
  - phút nghe / DAU giảm > 3%;
  - D7 giảm > 1 điểm;
  - crash-free < 99,5%;
  - rating mới giảm;
  - CTR placement Player > 2 lần CTR trung bình app (click nhầm, rủi ro invalid traffic).

---

## 4. Rủi ro chính sách và cách tránh

- **Ad sát nút điều khiển / navigation** dễ bị coi là click nhầm (invalid traffic), có thể bị khoá
  serving hoặc áp "Confirmed Click". Cách tránh: §3.1 #2, khoảng cách ≥ 48 dp/pt, không ad trong peek Up
  next, gỡ banner `SlideArea`. Nguồn: [AdMob – banner placement](https://support.google.com/admob/answer/6275345),
  [Publisher policies – Ads interfering](https://support.google.com/publisherpolicies/answer/11035030)
  ("adjacent to navigational or other action items", play buttons, video players).
- **Ad đè hoặc trôi trên nội dung** (vi phạm, không chỉ "không khuyến khích"). Cách tránh: không overlay
  ad lên ảnh bìa blur, không sticky, không collapsible trong Player. Nguồn:
  [AdMob 6275345](https://support.google.com/admob/answer/6275345),
  [AdMob 6293636](https://support.google.com/admob/answer/6293636).
- **Thẻ pause**: AdMob chấp nhận ad ở màn tạm dừng nếu người dùng **không bị dẫn tới chỗ phải bấm ad để
  tiếp tục**. Cách tránh: nút Play giữ nguyên vị trí và luôn bấm được, ad không chứa chữ "Tiếp tục". Nguồn:
  [AdMob 6293636](https://support.google.com/admob/answer/6293636).
- **Interstitial bất ngờ / lúc vào nội dung**: Play cấm full-screen "khi người dùng đã chọn làm việc
  khác" và ở **đầu một đoạn nội dung**. Bấm Play tập mới chính là đầu một đoạn nội dung. AdMob cũng cấm
  interstitial lúc load/thoát app và sau mỗi thao tác. Cách tránh: giữ chặn `player_screen` /
  `audio_playing`, không thêm call site interstitial nào ở Player / Up next. Nguồn:
  [Play Ads policy](https://support.google.com/googleplay/android-developer/answer/9857753),
  [Better Ads Experiences](https://support.google.com/googleplay/android-developer/answer/12271244),
  [AdMob interstitial](https://support.google.com/admob/answer/6201362).
- **Rewarded** phải opt-in rõ ràng, nói rõ hành động và phần thưởng trước mỗi lần, bỏ qua được, phần
  thưởng chỉ dùng trong app (thời gian không quảng cáo là hợp lệ). Không dùng rewarded interstitial
  trong Player (tự hiện). Nguồn: [AdMob rewarded policy](https://support.google.com/admob/answer/7313578),
  [Better Ads](https://support.google.com/googleplay/android-developer/answer/12271244) (rewarded opt-in
  không thuộc diện "bất ngờ").
- **Native**: nhãn "Quảng cáo" / "Ad" ≥ 15 px, hiển thị đủ asset bắt buộc, nền không clickable, không giả
  làm hàng tập (nền và nhãn khác hẳn), không che AdChoices. Nguồn:
  [AdMob native policy](https://support.google.com/admob/answer/6329638). `NativeAdTemplateView` (iOS) và
  template Android đã có nhãn. Strip và thẻ pause phải giữ đủ.
- **Âm thanh**: không phát tiếng quảng cáo đè podcast. Native video mặc định tắt tiếng, không override
  ([iOS](https://developers.google.com/admob/ios/native/options),
  [Android Next-Gen](https://developers.google.com/admob/android/next-gen/native/options)). Không chèn
  quảng cáo vào luồng audio của creator (`store-policy-review.md`).
- **Ngoài app / màn hình khoá**: Play cấm kiếm tiền trên màn hình khoá và cấm ad ngoài app. Apple 2.5.18
  giới hạn display ad trong binary chính, không có trong widget, notification, extension. Cách tránh: §3.1
  #6. Nguồn: [Play Ads policy](https://support.google.com/googleplay/android-developer/answer/9857753),
  [App Review Guidelines 2.5.18](https://developer.apple.com/app-store/review/guidelines/).
- **Apple 2.5.18** cũng yêu cầu có cách **báo cáo quảng cáo không phù hợp**. AdChoices của Google đáp
  ứng một phần. Nên thêm mục "Báo cáo quảng cáo" vào menu "…" (dùng `contentReporter` sẵn có) để chắc
  chắn qua review.
- **Apple 3.2.2(iii)**: app không được "designed predominantly for the display of ads". Giữ mật độ
  thấp: tối đa 1 ad trên màn Player cùng lúc, 2 trong Up next.
- **Apple 3.1.1 / 3.1.2**: bỏ quảng cáo và mở tính năng phải qua IAP. Subscription phải có **giá trị liên
  tục**, chu kỳ ≥ 7 ngày, dùng được trên mọi thiết bị của người dùng (Restore theo Apple ID là đủ).
  Trước khi mời đăng ký phải nói rõ người dùng nhận được gì. Không "bait-and-switch". Nguồn:
  [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/).
- **Play subscriptions policy**: hiện rõ giá, chu kỳ, tự gia hạn, thời gian trial và giá sau trial, cách
  huỷ. Có nút đóng rõ ràng. **Gói năm không được hiển thị nổi bật bằng giá quy ra tháng**, nên paywall và
  lời mời phải ghi "249.000₫/năm" là chính, "≈ 20.750₫/tháng" chỉ là phụ. Không đặt tên SKU là "Free
  Trial". Nguồn: [Play subscriptions](https://support.google.com/googleplay/android-developer/answer/9900533).
- **Không lấy lại thứ đang miễn phí** để bán (tốc độ, hẹn giờ ngủ cơ bản, nhớ vị trí, phát nền). Vừa là
  bài học Pocket Casts, vừa tránh bị coi là bait-and-switch.

---

## 5. Kế hoạch gói bán (Premium)

### 5.1 Mốc giá thị trường (giá trong app / VN khi có)

| App | Gói | Giá | Quyền lợi chính | Nguồn |
|---|---|---|---|---|
| Spotify | Individual | 65.000₫/tháng. Trả trước 1 năm 650.000₫. Người mới: 2 tháng đầu 65.000₫ | Không quảng cáo, tải offline, nghe theo thứ tự | [spotify.com/vn-vi/premium](https://www.spotify.com/vn-vi/premium/) |
| Spotify | Student / Duo / Family | 33.000₫ (chính thức) / 79.000₫ / 89.000₫ (nguồn phụ, chưa kiểm chứng) | | [Spotify VN](https://www.spotify.com/vn-vi/premium/), [Sforum](https://cellphones.com.vn/sforum/huong-dan-dang-ky-dung-thu-spotify-premium-trong-30-ngay-hoan-toan-mien-phi) |
| YouTube | Premium Lite (từ 11/08/2026) | 49.000₫ web / 65.000₫ trong app | Không quảng cáo phần lớn video, phát nền, tải video. Không gồm nhạc | [FPT Shop](https://fptshop.com.vn/tin-tuc/for-gamers/youtube-premium-lite-211535) |
| YouTube | Premium | 149.000₫ web / 199.000₫ trong app (FPT Shop). Giá ra mắt 2023 là 79.000₫ | + YouTube Music | [FPT Shop](https://fptshop.com.vn/tin-tuc/for-gamers/youtube-premium-lite-211535), [GearVN](https://gearvn.com/blogs/cong-nghe/youtube-premium-chinh-thuc-mo-cua-tai-viet-nam-xem-video-khong-quang-cao-voi-gia-tu-79-000-thang) |
| Zing MP3 | Plus | 19.000₫/tháng, 99.000₫/6 tháng, 169.000₫/năm | Không quảng cáo (gói thấp) | [App Store VN](https://apps.apple.com/vn/app/id992357547) |
| Zing MP3 | Premium | 279.000₫/6 tháng, 499.000₫/năm | + tải, chất lượng cao | [App Store VN](https://apps.apple.com/vn/app/id992357547) |
| NhacCuaTui | VIP | 49.000₫ hoặc 59.000₫/tháng (App Store VN liệt kê cả hai) | Không quảng cáo, tải không giới hạn | [App Store VN](https://apps.apple.com/vn/app/nct-nhaccuatui-nghe-mp3/id1210763463?l=vi), [Điện Máy Xanh](https://www.dienmayxanh.com/kinh-nghiem-hay/cach-nhan-1-nam-nhaccuatui-vip-mien-phi-1585669) |
| Castbox | Premium | $3.99/tháng, $27.99/năm (US). Giá VN chưa thấy | Không quảng cáo hình/video, > 100 kênh, hiệu ứng phát riêng theo show | [Castbox Help](https://helpcenter.castbox.fm/portal/kb/articles/castbox-premium), [App Store](https://apps.apple.com/us/app/podcast-player-app-castbox/id1243410543) |
| Pocket Casts | Plus / Patron | $3.99/tháng, $39.99/năm / $9.99, $99.99 (US) | Folders, bookmarks, cloud storage, wearables, themes. Nghe cơ bản miễn phí | [Engadget](https://www.engadget.com/entertainment/streaming/pocket-casts-makes-its-web-player-and-desktop-apps-usable-without-a-subscription-193035046.html), [Support](https://support.pocketcasts.com/knowledge-base/becoming-a-plus-or-patron-member-how-to-and-faqs/) |

**Nhận xét:**
- 49.000₫/tháng (giá đang đặt) **ngang YouTube Premium Lite và NCT VIP**, trong khi hai gói đó có tải
  offline và kho nội dung. Gói chỉ "bỏ quảng cáo" sát nhất là **Zing MP3 Plus 19.000₫/tháng,
  169.000₫/năm**.
- YouTube bán **đắt hơn trong app** để bù phí store. Ta bán chỉ trong app nên giá đã gồm phí.
- Phí store: Apple 15% nếu đăng ký
  [App Store Small Business Program](https://developer.apple.com/app-store/small-business-program/).
  Google Play 15% cho subscription và cho 1 triệu USD đầu mỗi năm
  ([Play service fees](https://support.google.com/googleplay/android-developer/answer/112622)).
- **Ước lượng thiệt hại quảng cáo khi một người lên Premium** (giả định, thay bằng số thật từ AdMob sau
  rollout): một người nghe nặng thấy khoảng 300 impression/tháng. Với eCPM VN giả định $0,3, doanh thu
  là ≈ $0,09 ≈ 2.300₫/tháng. Gói 29.000₫ còn ≈ 24.650₫ sau phí, gấp hơn 10 lần. Vậy **ưu tiên giá dễ
  mua để tăng tỉ lệ chuyển đổi**, không cần giá cao để "bù" quảng cáo.

### 5.2 Đề xuất gói và giá

Một tên duy nhất **"TSPodcast Premium"**, khớp id `premium.*` và chuỗi paywall hiện có. Thêm một lựa chọn
mua đứt cho người không thích subscription. Không làm Family, Duo, Student vì chưa có tài khoản và không
xác minh được sinh viên.

| id nội bộ | Store | Giá ra mắt đề xuất | Đang đặt trên ASC | Quyền lợi | Ghi chú |
|---|---|---|---|---|---|
| `premium_monthly` | Android `premium`/`monthly`, iOS `tss.t.podcast.premium.monthly` | **29.000₫/tháng** | 49.000₫ | Mọi quyền lợi Premium (hiện tại và sau này) | Trên Zing Plus, dưới YouTube Lite / NCT VIP / Spotify |
| `premium_yearly` | `premium`/`yearly`, `tss.t.podcast.premium.yearly` | **249.000₫/năm**, dùng thử 7 ngày | 399.000₫ | Như trên | Rẻ hơn ≈ 28% so với 12 tháng. Badge `best_value`, là gói chọn sẵn |
| `remove_ads_lifetime` | `remove_ads_lifetime`, `tss.t.podcast.remove_ads.lifetime` | **499.000₫ một lần** | 599.000₫ | **Chỉ `ad_free`** | ≈ 2 năm gói năm. Paywall ghi rõ "không gồm tính năng Premium cần máy chủ (AI, đồng bộ)" |

- **Phương án B (không đổi giá):** giữ 49.000 / 399.000 / 599.000₫ và chỉ bật khi đã có ít nhất 2–3
  quyền lợi ngoài "không quảng cáo" (§5.3). Ít việc trên store nhưng tỉ lệ mua sẽ thấp hơn.
- **Lộ trình giá:** khi có tải tự động / AI / đồng bộ (2027), nâng giá cho **người đăng ký mới** lên
  khoảng 39.000₫/tháng, 349.000₫/năm (khớp mức "Plus 39–59k / 299–399k" ở
  `docs/planned/plans/ke-hoach-backend-quickwin-cuoc-lon.md`). Người đang dùng giữ giá cũ (ASC cho phép
  giữ giá cho subscriber hiện tại. Play có legacy price cohort).
- Trên ASC, chọn price point VND gần nhất có sẵn, đặt **Việt Nam làm quốc gia gốc** để giá VND tròn, các
  nước khác tự quy đổi.

### 5.3 Quyền lợi ngoài "không quảng cáo"

Nguyên tắc: **không chuyển tính năng đang miễn phí vào Premium**. Chỉ thêm phần nâng cao.

| Quyền lợi | Free / Premium | Công | Phụ thuộc | Khi nào |
|---|---|---|---|---|
| Không quảng cáo mọi nơi, kể cả Player và Up next. Audio không bao giờ có quảng cáo chèn | Premium + trọn đời | Đã có | — | Ra mắt |
| Nhớ tốc độ riêng theo từng show (hiện chỉ có "áp dụng cho tất cả") | Premium | S | `PlaybackExtrasState` / `SpeedSheet` | Ra mắt hoặc ngay sau |
| Hẹn giờ ngủ nâng cao: số phút tuỳ chọn, giảm dần âm lượng, lắc máy để gia hạn, "hết N tập" (các lựa chọn hiện có vẫn miễn phí) | Premium | S–M | `SleepTimerOption`, `SleepTimerSheet` | Ra mắt hoặc ngay sau |
| Bỏ khoảng lặng, tăng âm lượng giọng nói | Premium | Android S (Media3 `skipSilence`), iOS M–L (AVAudioEngine) | Player | Sau ra mắt, Android trước |
| Icon app thay thế, theme | Premium | S (iOS alternate icons), M (Android activity-alias) | — | Sau ra mắt |
| Huy hiệu "Ủng hộ TSPodcast", thử tính năng sớm | Premium | S | Remote Config | Ra mắt |
| Tải offline | **Cơ bản miễn phí** (theo `de-xuat-tinh-nang-dot-pha.md` "phát nền và offline cho podcast giữ miễn phí"). Premium: tự động tải tập mới của show yêu thích, tải hàng loạt | L | Roadmap 2027-02 (Media3 Download, URLSession background) | 2027 |
| Tóm tắt AI, transcript, "Hỏi tập này" | Miễn phí có hạn mức (vd. 3 tập/tháng), Premium hạn mức cao | L | Backend AI (`roadmap-backlog.md` §3.6) | 2027 |
| Đồng bộ nhiều thiết bị, sao lưu | Premium | L | Đăng nhập + backend | 2027 |

Mâu thuẫn cần owner chốt: `feature-roadmap-ai-growth.md` để "tải không giới hạn" trong Premium, còn
`de-xuat-tinh-nang-dot-pha.md` giữ offline miễn phí. Đề xuất: offline thủ công miễn phí, tự động / hàng
loạt cho Premium.

### 5.4 Dùng thử

- **Gói năm: 7 ngày miễn phí**, mỗi người một lần. Play: offer "free trial" trên base plan `yearly`, điều
  kiện "chưa từng có subscription này". Apple: intro offer "Free, 1 week" cho người chưa dùng nhóm
  `premium`. App đã đọc trial từ store (`ProductDetails` / `isEligibleForIntroOffer`).
- **Gói tháng: không trial** (tránh vòng thử rồi huỷ). Sau này có thể A/B "tháng đầu 9.000₫" bằng intro
  price.
- **Rewarded 2 giờ không quảng cáo** là bản dùng thử miễn phí, không cần thẻ. Sau lần rewarded thứ 2
  trong tuần, paywall nhắc "Bạn đã gỡ quảng cáo 2 lần tuần này".
- Paywall phải ghi: số ngày trial, ngày bắt đầu tính tiền, giá sau trial, tự gia hạn, cách huỷ (Play
  policy, Apple 3.1.2(c)).
- Sau này: Apple win-back offer / offer code, Play promo code cho giới thiệu bạn bè (G9 trong
  `feature-roadmap-ai-growth.md`).

### 5.5 Lối vào paywall

| Lối vào | `source` | Khi nào | Quy tắc |
|---|---|---|---|
| Cài đặt (đã có) | `settings` | Luôn, khi `iap_enabled` | — |
| Menu "…" của Player → "Gỡ quảng cáo" | `player_menu` | Khi người dùng đang thấy quảng cáo (không Premium) | Mở sheet: Premium / "Xem 1 video, 2 giờ không quảng cáo". Không đặt cạnh ad |
| Lời mời mềm sau N quảng cáo | `ad_upsell` | Ở điểm ngắt tự nhiên: đóng Player, rời màn show. ≥ `iap_upsell_after_impressions` impression từ lần trước, ≥ `iap_upsell_min_interval_days` ngày | Thẻ nhỏ dưới đáy có nút đóng rõ. Không ngay sau quảng cáo toàn màn hình, không lúc vừa bấm Play, không trong grace |
| Hết lượt rewarded trong ngày | `rewarded_cap` | Bấm "2 giờ không quảng cáo" khi đã đạt `ads_rewarded_daily_cap` | Chỉ khi người dùng bấm |
| Bấm vào tính năng Premium (tốc độ theo show, hẹn giờ nâng cao…) | `feature_<tên>` | Khi bấm | Hiện tính năng đó trước, rồi tới giá |
| **Không:** onboarding lần đầu, lúc mở app, chen giữa luồng phát | — | — | Giữ trải nghiệm lõi sạch |

### 5.6 Việc trên store và Remote Config (owner)

**App Store Connect** (đã có 3 sản phẩm):
1. Hoàn tất **Paid Apps Agreement**, thuế, ngân hàng (blocker). Đăng ký **Small Business Program**.
2. Đổi giá nếu chọn phương án A. Đặt VN làm quốc gia gốc.
3. Nhóm `premium`: monthly và yearly **cùng level** (đổi chu kỳ là crossgrade). Nếu sau này có gói cao
   hơn thì đặt gói đó ở level 1.
4. Intro offer: yearly, "Free, 1 week", người mới, mọi lãnh thổ.
5. Tên hiển thị / mô tả ít nhất `vi` và `en` (nên đủ các locale đang có metadata). Ảnh chụp paywall cho
   review. Review notes ghi cách mở paywall (Cài đặt, menu "…" của Player).
6. Gửi IAP **cùng một bản app mới** (IAP đầu tiên phải đi kèm version).
7. Metadata: link Điều khoản (EULA chuẩn của Apple hoặc `/terms`) và Chính sách quyền riêng tư.
8. Sandbox tester. App Privacy: thêm "Purchases".

**Play Console** (chưa tạo gì):
1. Kiểm tra payments profile / merchant account. Phải có một bản build dùng Play Billing đã upload lên
   một track thì mới tạo được sản phẩm.
2. Subscription `premium`: base plan `monthly` (P1M, tự gia hạn, 29.000₫) và `yearly` (P1Y, 249.000₫) +
   offer free trial 7 ngày trên `yearly` (người chưa từng có subscription này).
3. One-time product `remove_ads_lifetime` 499.000₫.
4. Giá VND, để Play quy đổi các nước khác. Giữ mặc định grace period / account hold.
5. License tester, thử trên internal track.
6. Data safety: khai báo "Purchase history".

**Remote Config:**
1. Publish key §3.6 (đều tắt) cùng bản app có code.
2. `iap_enabled=true` cho `platform_android` khi Play xong. iOS bật sau khi Apple duyệt agreement.
3. Bật placement theo §3.8.

---

## 6. Phân pha triển khai

| Pha | Nội dung | Cỡ | Tiêu chí nghiệm thu |
|---|---|---|---|
| **P0 – Nền và chặn rủi ro** | 3 placement mới + key `ads_player_*` / `iap_upsell_*` trên Android (`ads/`) và `shared` (iOS), template RC, `canShowPlayerInline`. **Gỡ banner `player_episode_list` khỏi `SlideArea`** | S | Test parity template (Android `AdsConfig`, `AdsSettingsTemplateTest`) pass. Unit test: grace, phiên đầu, compact/height, Premium, `ad_free`, cap pause. Với default RC, app không đổi gì trên màn hình |
| **P1 – Up next native** | Android `SlideArea` + iOS `UpNextList` | M | Hàng ad chỉ có khi panel mở hẳn, không bao giờ ở peek, hàng đầu hay ngay sau nút Trước/Tiếp. Không có impression khi panel thu gọn (kiểm bằng Ad Inspector / log `ads_impression`). Cuộn mượt (không jank mới trong Macrobenchmark / Instruments). Premium: không hàng trống |
| **P2 – Now playing strip** | Ảnh bìa co giãn trên Android, slot dưới ảnh bìa ở cả 2 app, preload khi bắt đầu phát, refresh có giới hạn | M | Ảnh chụp trước/sau khi ad tải: cụm điều khiển không đổi vị trí. Khoảng cách tới vùng chạm thanh tua ≥ 48 dp/pt trên máy nhỏ nhất được hỗ trợ. Ẩn khi compact / AX / thấp. ≤ 1 request mỗi `ads_player_native_refresh_sec` khi visible, 0 ở nền. Video tắt tiếng |
| **P3 – Rewarded + paywall trong Player** | Menu "…" → "Gỡ quảng cáo", dialog opt-in, call site `rewarded_ad_free`, `PaywallSource` mới, "Báo cáo quảng cáo" | S | Dialog nói rõ phần thưởng. Audio tạm dừng rồi phát lại. Thưởng xong mọi ad trong Player biến mất ngay, 120 phút. Đạt cap thì mở paywall `rewarded_cap`. Event có `source` |
| **P4 – IAP lên store** | Đổi giá, tạo sản phẩm Play, bật `iap_enabled` Android → iOS, copy paywall (giá năm là chính, trial, huỷ) | S code, owner M | Mua / khôi phục được trên sandbox cả 2 nền tảng. Ad tắt ngay sau khi mua. Paywall qua checklist Play subscriptions + Apple 3.1.2 |
| **P5 – Rollout + A/B** | 10% → 50% → 100% từng placement. A/B `player_pause` và `ads_player_native_refresh_sec` (0 / 60 / 120). Bật `iap_upsell_enabled` | S | Không chạm guardrail §3.8 trong 7 ngày mỗi bước. Có báo cáo doanh thu / DAU và chuyển đổi Premium theo `source` |
| **P6 – Quyền lợi Premium nhỏ** | Tốc độ theo show, hẹn giờ nâng cao, icon / huy hiệu, bỏ khoảng lặng (Android) | M (mỗi mục S–M) | Tính năng free hiện có không đổi. Paywall liệt kê đúng quyền lợi đã có |
| **P7 – Quyền lợi lớn** | Tải tự động, AI, đồng bộ (theo `roadmap-backlog.md`). Nâng giá cho người mới | L | Theo kế hoạch riêng của từng tính năng |

---

## 7. Cần owner quyết định

1. Duyệt thứ tự: Up next native (P1) → strip dưới ảnh bìa (P2). Thẻ pause chỉ A/B (P5).
2. Strip dưới ảnh bìa dùng **native** (đề xuất: đẹp hơn, nhãn rõ) hay **banner adaptive** (fill cao hơn,
   ít code)?
3. Gỡ banner `player_episode_list` khỏi Android `SlideArea` (đề xuất: có).
4. Tạo 3 unit Native Player mới trên mỗi nền tảng (tên ở §3.7), hay dùng tạm unit Native hiện có?
   Android `ADMOB_UNIT_REWARDED` đã là unit thật chưa?
5. Giá: **A** 29.000 / 249.000 / 499.000₫ (đề xuất) hay **B** giữ 49.000 / 399.000 / 599.000₫?
6. Giữ gói trọn đời "Bỏ quảng cáo" (đề xuất: giữ, chỉ `ad_free`)?
7. Tên gói: "Premium" (khớp id hiện có, đề xuất) hay "Plus" (như `ke-hoach-backend-quickwin-cuoc-lon.md`)?
8. Tải offline: miễn phí cơ bản + Premium tự động (đề xuất), hay toàn bộ trong Premium?
9. Bật IAP Android trước iOS (đề xuất: có)?
10. Lời mời mềm sau N quảng cáo: bật không, N = 30?
11. Áp grace 24 h + bỏ phiên đầu cho placement Player (đề xuất: có)?
12. Đăng ký Apple Small Business Program (phí 15%).

---

## Nguồn (tra cứu 2026-11-14)

**Cách app nhạc / podcast đặt quảng cáo**
- Spotify display ads (Now Playing, Browse, playlist): https://ads-web.spotify.com/ad-formats/display-ads
- Spotify Sponsored Sessions: https://techcrunch.com/2014/09/08/the-music-streaming-revolution-will-be-televised/ · https://ads.spotify.com/en-US/news-and-insights/building-a-platform-for-fans-and-brands/
- YouTube Music banner trong Now Playing: https://9to5google.com/2025/06/06/youtube-music-now-playing-ads/
- Pocket Casts banner 09/2025 và phản ứng: https://blog.matthewbrunelle.com/podcasts-you-altered-the-deal-so-i-will-alter-your-app/ · https://forums.pocketcasts.com/forums/topic/the-new-advertisements-seriously-suck
- Castbox Premium: https://helpcenter.castbox.fm/portal/kb/articles/castbox-premium · https://apps.apple.com/us/app/podcast-player-app-castbox/id1243410543
- Podcast Addict FAQ: https://podcastaddict.com/faqs
- Pandora Video Plus / Premium Access: https://techcrunch.com/2017/12/14/pandora-listeners-can-now-watch-video-ads-to-access-on-demand-music · https://www.marketingdive.com/news/pandora-expands-reward-based-video-plus-to-all-advertisers/505314/
- SoundCloud: https://soundcloud.com/company/advertise · https://help.soundcloud.com/hc/en-us/articles/115003447287-Ads-on-SoundCloud
- Audiomack: https://audiomack.com/world/post/welcome-to-audiomack-plus · https://apps.apple.com/ng/app/id921765888

**Chính sách**
- AdMob banner placement / click nhầm: https://support.google.com/admob/answer/6275345 · https://support.google.com/admob/answer/6293636
- Publisher policies, Ads interfering: https://support.google.com/publisherpolicies/answer/11035030
- AdMob interstitial: https://support.google.com/admob/answer/6201362
- AdMob native policy: https://support.google.com/admob/answer/6329638
- AdMob rewarded policy: https://support.google.com/admob/answer/7313578
- AdMob refresh: https://support.google.com/admob/answer/3245199
- Native video tắt tiếng: https://developers.google.com/admob/ios/native/options · https://developers.google.com/admob/android/next-gen/native/options
- Google Play Ads policy: https://support.google.com/googleplay/android-developer/answer/9857753
- Google Play Better Ads Experiences: https://support.google.com/googleplay/android-developer/answer/12271244
- Google Play subscriptions policy: https://support.google.com/googleplay/android-developer/answer/9900533
- Google Play service fees: https://support.google.com/googleplay/android-developer/answer/112622
- App Store Review Guidelines (2.5.18, 3.1.1, 3.1.2, 3.2.2): https://developer.apple.com/app-store/review/guidelines/
- App Store Small Business Program: https://developer.apple.com/app-store/small-business-program/

**Giá**
- Spotify VN: https://www.spotify.com/vn-vi/premium/ · Duo/Family (nguồn phụ): https://cellphones.com.vn/sforum/huong-dan-dang-ky-dung-thu-spotify-premium-trong-30-ngay-hoan-toan-mien-phi
- YouTube Premium / Lite VN: https://fptshop.com.vn/tin-tuc/for-gamers/youtube-premium-lite-211535 · https://gearvn.com/blogs/cong-nghe/youtube-premium-chinh-thuc-mo-cua-tai-viet-nam-xem-video-khong-quang-cao-voi-gia-tu-79-000-thang
- Zing MP3: https://apps.apple.com/vn/app/id992357547
- NhacCuaTui: https://apps.apple.com/vn/app/nct-nhaccuatui-nghe-mp3/id1210763463?l=vi · https://www.dienmayxanh.com/kinh-nghiem-hay/cach-nhan-1-nam-nhaccuatui-vip-mien-phi-1585669
- Pocket Casts: https://www.engadget.com/entertainment/streaming/pocket-casts-makes-its-web-player-and-desktop-apps-usable-without-a-subscription-193035046.html · https://support.pocketcasts.com/knowledge-base/becoming-a-plus-or-patron-member-how-to-and-faqs/

Giá lấy từ trang store / bài báo tại thời điểm tra cứu, có thể đã đổi. Giá của Zing, NCT, Castbox VN và
Spotify Duo/Family chưa đối chiếu được với trang chính thức. eCPM ở §5.1 là giả định.

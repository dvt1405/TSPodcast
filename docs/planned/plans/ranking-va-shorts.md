# Kế hoạch: Bảng xếp hạng podcast và Shorts (lướt nghe/xem)

> **Trạng thái: Chưa làm** (2026-11-16) — research + kế hoạch, chưa có code. Mục tiêu: kéo người dùng mới (chia sẻ, SEO, onboarding). Owner đã chốt §8 ngày 16/11/2026: "Thịnh hành" trước, tab riêng, không crawl nền tảng khác, **không có chương trình creator**, ngân sách AI **$45/tháng**. Owner tự trả lời 6 câu pháp lý ở §9 (chấp nhận rủi ro thay cho ý kiến luật sư): có quảng cáo, AI chọn đoạn + phụ đề AI được, người dùng tự chịu trách nhiệm khi chia sẻ video, chỉ dùng tên "Thịnh hành", gỡ trong 24 h.
> Xây trên: [ke-hoach-backend-quickwin-cuoc-lon.md](ke-hoach-backend-quickwin-cuoc-lon.md) (§2.1 nguồn, §2.4 trending, §2.8 mốc BE), [N12/N13/N14 trong de-xuat-tinh-nang-dot-pha.md](../../in-progress/research/de-xuat-tinh-nang-dot-pha.md), [feature-roadmap-ai-growth.md](../../in-progress/research/feature-roadmap-ai-growth.md) (A4, G1).
> Số liệu "đo 16/11/2026" do agent tự fetch. **"chưa kiểm chứng"** = chưa đọc được nguồn gốc. Phần đánh dấu [S] là tóm tắt, cần đọc lại nguyên văn trước khi dựa vào.

---

## 0. Tóm tắt

- **Ranking:** không có nguồn bảng xếp hạng bên ngoài nào vừa hợp lệ vừa có VN. Spotify không có API và trang chart không có VN; YouTube chart chỉ có Mỹ và cũng không có API; Apple chart VN dùng được nhưng điều khoản chưa rõ. **Bảng xếp hạng phải là của mình.** Ra 2 bước:
  - **R0 (ngay, không cần backend):** màn "Thịnh hành" có số thứ hạng, lọc theo ngôn ngữ/chủ đề, dữ liệu PodcastIndex `podcasts/trending` gọi live như Home đang làm.
  - **R1 (sau backend lite):** vẫn tên **"Thịnh hành"** nhưng xếp theo **người nghe thật trong app** (play ping), lưu lịch sử hằng ngày/tuần, mũi tên lên/xuống, nhãn "Mới", thẻ chia sẻ thứ hạng, trang web `/thinh-hanh` cho SEO.
- **Shorts:** **không crawl TikTok/YouTube Shorts/Spotify/Instagram** (vi phạm ToS, host lại nội dung có bản quyền, rủi ro bị gỡ khỏi store, và trái ràng buộc dự án). **Kho shorts tự dựng từ RSS** mà crawler backend đã định tải: mỗi short chỉ là **con trỏ `[episode, start, end]`**, phát thẳng từ file gốc của creator, không cắt hay host lại.
  - Có video (enclosure `video/*` hoặc `podcast:alternateEnclosure` HLS/MP4) → phát đoạn video.
  - Chỉ có audio → **ảnh tác giả/ảnh bìa ở giữa, nền là chính ảnh đó phóng to + blur**, phụ đề chạy, sóng âm, nút Theo dõi / Nghe cả tập từ đây.
  - Chọn đoạn theo thứ tự: `podcast:soundbite` của creator → đầu chương (`podcast:chapters`) → mốc thời gian trong show notes → highlight AI. Không có chương trình creator, nên ở VN **AI là nguồn chính**. AI trả về mốc thời gian, tiêu đề ngắn và phụ đề của riêng đoạn; **không lưu transcript cả tập**, trần $45/tháng (~30–40 tập/ngày).
- **Vòng thu hút người dùng mới:** xuất short thành **video dọc có watermark + link** (người dùng tự chịu trách nhiệm khi đăng, theo điều khoản) hoặc thẻ ảnh + link lên TikTok/Reels/Zalo; trang "Thịnh hành" công khai cho SEO; creator khoe thứ hạng; onboarding bằng 5 short.
- **Effort (1 dev + agent):** R0 ~3–4 dev-day/nền tảng; S0 (prototype shorts, không backend) ~7–9 dev-day/nền tảng; phần backend thêm cho ranking + shorts ~12–16 dev-day, **chạy trên backend lite đã lên kế hoạch** (production ~15/01/2027).

---

## 1. Hiện trạng liên quan trong code

| Có sẵn | Ở đâu | Dùng cho |
|---|---|---|
| Trending PodcastIndex (E1) | `shared/.../network/PodcastIndexApi.kt` `getTrending`, Android `core/.../IPodcastRepository.kt` `getTrending`, `discorver/components/TrendingRow.kt`, iOS `Home/HomeDataSource.swift` | R0 |
| `getRandomEpisodes`, `getRecentEpisodes` | `IPodcastRepository.kt` | S0 |
| Media3 1.10.1 (ExoPlayer, HLS, session) | `gradle/libs.versions.toml` | Phát đoạn, preload, video |
| Blur: module `hazeAndroid`, `BottomAppBarBlur.kt`; iOS Liquid Glass (M5) | | Nền blur của thẻ |
| Remote Config, AdMob native, Analytics | `remoteconfig/`, `ads*`, `docs/done/analytics/` | Cờ bật/tắt, quảng cáo xen thẻ, đo |
| Link chia sẻ + App Links/Universal Links | PR #76, `common/WebLinks.kt` | Deep link `?t=`, link trong video chia sẻ |
| Backend | **Chưa có code**; chỉ có khung `deploy/docker-compose.yml` profile `backend` | R1, S1 |

Tab Android: Discover, Favourite, Search, Radio (`HomeNavigationScreen.kt`). iOS: không có Radio.

---

## 2. Research: nguồn xếp hạng (đo 16/11/2026)

| Nguồn | Có VN? | API / điều khoản | Kết luận |
|---|---|---|---|
| **Apple charts RSS v2** `rss.marketingtools.apple.com/api/v2/vn/...` | Chart **tập**: 200, 25 mục, cập nhật trong ngày. Chart **show** top 50: **504** (giống lần đo 30/10) | Trang generator chỉ có dòng "Copyright © 2026 Apple Inc.", không có điều khoản; tối đa 50 | Chỉ dùng **nội bộ** để khám phá feed mới và làm tín hiệu phụ. **Không** hiển thị "BXH Apple" (giữ quyết định §2.4 plan backend) |
| **Apple legacy** `itunes.apple.com/vn/rss/toppodcasts/limit=200/genre=G/json` | 200, có dữ liệu (genre 1489 Tin tức) | Không có tài liệu, có thể bị tắt | Như trên, chỉ khám phá |
| **Spotify podcast charts** | Trang chỉ có United States; `/vn` trả 404 | **Không có API**. Diễn đàn dev Spotify (trả lời không chính thức): chỉ được cache tạm, cấm dùng nội dung cho AI | **Loại**. Scrape trang chart = vi phạm điều khoản |
| **YouTube podcast charts** | Ra mắt 05/2025, chỉ có Mỹ; bản mở rộng sang nước khác **chưa kiểm chứng** | Không có API; dev policy cấm tạo chỉ số dẫn xuất từ dữ liệu YouTube [S] | **Loại** |
| **Taddy API** | Chart Apple theo nước/genre, cập nhật ~9h sáng PST mỗi ngày; VN có trong enum nước không: **chưa kiểm chứng** | Free 500 request/tháng, Pro $75 (100k), Business $150 (350k). Developer Policy không có điều khoản riêng cho chart | Dữ liệu vẫn là của Apple → cùng rủi ro hiển thị. **Không cần** nếu đã tự gọi Apple RSS |
| **PodEngine API** | Có `apple` + `spotify` chart theo nước | Bên thứ ba, nguồn Spotify gốc không có giấy phép công khai | Loại (rủi ro dây chuyền) |
| **PodcastIndex trending** | Có `lang=vi`, `cat` | Gọi live hợp lệ (đang dùng). ToS §5.5 cấm dựng DB/bản sao → **không lưu** | **R0** |
| **Tín hiệu trong app** (play ping, follow, nghe xong) | Có, khi có người dùng | Dữ liệu của mình, theo consent analytics (N11) | **R1, nguồn chính** |

**Vì sao "Thịnh hành" của mình lại là lợi thế để kéo người dùng:** không ai công bố danh sách podcast Việt theo người nghe thật. Một danh sách công khai, cập nhật hằng tuần, là nội dung để creator chia sẻ ("#2 Kinh doanh trên TSPodcast"), để báo viết bài, và để Google index.

---

## 3. Tính năng Ranking

### 3.1 R0 — "Thịnh hành" (client, không backend)

- **Lối vào:** "Xem tất cả" trên `TrendingRow` (Android) / hàng Trending (iOS) + một chip "Top" ở Discover.
- **Màn hình:** tab ngôn ngữ (Việt / Toàn cầu theo `LanguagePrefs`), dải chủ đề (dùng lại `SearchCategories`), danh sách 1–100 có **số thứ hạng lớn**, ảnh bìa, tên, tác giả, nút Theo dõi; 3 hạng đầu dạng thẻ lớn. Kéo để làm mới.
- **Dữ liệu:** `podcasts/trending?lang=vi&cat=<id>&max=100&since=<now-7d>` gọi live, cache theo header như hiện nay; **không** lưu snapshot (ToS PodcastIndex §5.5) → R0 **không có** mũi tên lên/xuống.
- **Ghi nguồn:** dòng "Dữ liệu: Podcast Index" cuối danh sách (ToS §7.2). Tên là **"Thịnh hành"** ở cả R0 và R1 (owner 16/11/2026), không dùng chữ "Bảng xếp hạng/BXH".
- **Lọc:** chạy qua `ts_podcast_black_list` + ẩn show như Home.
- **Analytics:** `chart_view {scope, cat}`, `chart_item_click {rank}`, `chart_follow {rank}`.
- **Effort:** Android 3–4, iOS 3–4 dev-day (logic dựng URL/ghép danh sách đặt trong `shared/` để iOS dùng, Android có thể dùng lại).

### 3.2 R1 — "Thịnh hành" theo người nghe thật (backend)

Nối vào schema §2.3 plan backend (thêm cột, không thêm hệ mới):

```sql
chart_snapshots(period /* day|week */, period_start date, scope /* vi|global|cat:<id> */,
  rank int, feed_id, score real, unique_listeners int /* làm tròn, chỉ công khai bucket ≥ 20 */,
  prev_rank int, weeks_on_chart int, peak_rank int, PRIMARY KEY(period, period_start, scope, rank))
episode_chart_snapshots(...)          -- "Tập hot tuần này", cùng cấu trúc
```

- **Điểm tuần** (thứ Hai 03:00 ICT): `unique_listeners_7d` (play ping ≥ 30 s, một lần/tập/ngày/thiết bị, chỉ khi bật "Phân tích sử dụng") + `follows_7d` + `completion_rate`; Bayesian smoothing cho show ít người nghe; mỗi publisher tối đa 3 slot/top 20. Trước khi đủ dữ liệu (≥ 4 tuần play ping) dùng công thức "Đang lên v0" của plan backend §2.4 .
- **Danh sách:** Thịnh hành Việt Nam (100), theo chủ đề (50), **Đang lên** (momentum tuần này/tuần trước), **Mới** (lần đầu vào danh sách), **Tập thịnh hành** (50 tập). Không hiển thị số người nghe tuyệt đối, chỉ thứ hạng.
- **UI thêm:** ▲▼ và số bậc, "MỚI", "x tuần trong Thịnh hành", hạng cao nhất; trang lịch sử thứ hạng của từng show (sparkline) trong Podcast detail.
- **Chống gian lận:** App Check bắt buộc cho `POST /v1/events/play`, giới hạn theo thiết bị/ngày, loại thiết bị có > N lượt/ngày, cờ cho show tăng bất thường (> 5× tuần trước) → duyệt tay trước khi công khai.
- **API:** `GET /v1/charts/{scope}?period=week&date=` (cache 1 h), `GET /v1/charts/history?feedId=`.
- **Tăng trưởng:**
  - **Thẻ chia sẻ thứ hạng** (ảnh 1080×1920 + 1200×630): "#3 Thịnh hành Kinh doanh tuần 47 trên TSPodcast", có link về show. Người dùng chia sẻ từ app; creator tự tải từ trang `/thinh-hanh/<show>` trên web. **Không** gửi email chủ động cho creator (không có chương trình creator).
  - **Trang web công khai** `tspodcast.web.app/thinh-hanh` và `/thinh-hanh/<chủ-đề>`: HTML tĩnh do worker xuất lên Firebase Hosting mỗi tuần (không cần web Next.js đã bỏ), có schema `ItemList` → SEO cho "podcast thịnh hành", "top podcast Việt Nam", "podcast hay nhất". Trang có mục "Cách xếp hạng" ngắn và link yêu cầu gỡ.
  - **Thông báo tuần** "Thịnh hành tuần này đã có" (nằm trong kế hoạch thông báo).
- **Effort:** backend 6–8 dev-day (sau BE-M4b play ping), client 3 dev-day/nền tảng, trang web tĩnh 2 dev-day.

---

## 4. Tính năng Shorts

### 4.1 Vì sao không crawl kho shorts của nền tảng khác

| Nguồn | Vấn đề |
|---|---|
| YouTube Shorts | Dev policy: cấm tải/cache nội dung audiovisual, cấm scrape [S]; cấm gộp dữ liệu từ nhiều chủ kênh khác nhau (III.E.2) [S]; player nhúng **không được vẽ gì đè lên** (Required Minimum Functionality "Overlays and frames"), tối đa **một** player tự phát trên màn hình và chỉ khi hiện > 50%, cấm phát nền và tách audio [S]. Một feed dọc trộn nhiều kênh vừa trái III.E.2, vừa không làm được UI nút đè lên video |
| TikTok, Instagram Reels, Facebook | Không có API đọc nội dung công khai cho bên thứ ba; scrape vi phạm điều khoản; video có bản quyền của người đăng |
| Spotify Clips (ra 27/05/2026) | Không có API; trang creators.spotify.com `Disallow: /` |
| Tải video rồi host lại | Vi phạm bản quyền + Apple 5.2.2/Google Play IP policy → nguy cơ app bị gỡ |

→ "Crawl" hợp lệ duy nhất là **crawl RSS** (backend đã có kế hoạch). Trên YouTube chỉ làm **nút "Xem trên YouTube"** cho show đã có (BE-M9), không đưa vào feed.

### 4.2 Nguồn nội dung cho kho shorts

| Tầng | Nguồn | Media | Độ phủ VN (ước) | Rủi ro |
|---|---|---|---|---|
| T1 | `podcast:soundbite` creator khai | audio/video gốc | Rất thấp (2/120 feed VN, đo 30/10) | Thấp: creator đã chọn đoạn |
| T2 | Đầu chương `podcast:chapters` (bỏ chương "Intro/Quảng cáo/Tài trợ") | gốc | Thấp | Thấp |
| T3 | Mốc thời gian trong show notes (regex `(\d{1,2}:)?\d{1,2}:\d{2}` + tiêu đề dòng) | gốc | Trung bình (show phỏng vấn hay có) | Thấp |
| ~~T4~~ | ~~Creator tự đăng short~~ — **bỏ** (owner 16/11/2026: không có chương trình creator vì ngân sách) | | | |
| T5 | **Highlight AI** (nguồn chính ở VN): gửi audio cho Gemini, nhận về 1–3 cặp `[start, end]` 30–60 s + tiêu đề ≤ 80 ký tự + **phụ đề có mốc từng từ của riêng đoạn đó**; không lưu transcript cả tập, không lưu audio | gốc | Cao, giới hạn bởi ngân sách (~30–40 tập/ngày) | Owner chấp nhận (§9 câu 2–3); gỡ trong 24 h; chỉ show `vetted` |
| T6 (chỉ S0) | Heuristic: offset = max(90 s, 12% thời lượng) | gốc | 100% | Hay rơi vào quảng cáo đọc/lời chào (phản biện N13) → chỉ dùng trong prototype, điểm chất lượng thấp |

**Video:** trong RSS, video nằm ở `<enclosure type="video/mp4">` hoặc `podcast:alternateEnclosure` (HLS/MP4). Theo Podder (08/2026), Transistor, RSS.com, Captivate, Podbean, Omny Studio, Podigee… đã xuất HLS qua `alternateEnclosure`; Fountain và Pocket Casts phát được. Media3 và AVPlayer đều phát HLS, nên làm được. Tỉ lệ feed VN có video: **chưa đo** → BE-M0 thêm chỉ số này.

**Ảnh tác giả:** `podcast:person img` (hiếm ở VN) → ảnh bìa tập (`itunes:image` của item) → ảnh bìa show. **Không** lấy ảnh người từ Google/MXH (bản quyền + dữ liệu cá nhân theo PDPL). Không có chương trình creator nên thực tế đa số thẻ dùng **ảnh bìa**. Lưu `person img` URL là câu hỏi L5, giống bảng `people` đã hoãn.

### 4.3 Kho shorts (backend)

```sql
shorts(id bigint PK, feed_id, episode_id, source /* soundbite|chapter|shownote|ai|heuristic */,
  media_kind /* audio|video */, media_url /* enclosure gốc hoặc alternateEnclosure, không host lại */,
  start_ms int, end_ms int /* 15–90 s */, title text /* ≤ 80 ký tự */,
  captions_url text NULL /* WebVTT của riêng đoạn này */, cover_url, person_img_url NULL,
  dominant_color int, lang, category_ids int[], quality real, status /* candidate|approved|blocked */,
  created_at, expires_at /* 60 ngày */, UNIQUE(episode_id, start_ms))
short_stats_daily(day, short_id, impressions, plays_3s, completes, follows, full_listens, shares)
```

- Crawler (BE-M0/M1) parse thêm `soundbite`, `chapters` URL, `alternateEnclosure`, `person` → job `shorts:extract` sau mỗi tập mới của feed `vetted`.
- `quality` = trọng số tầng (T1 cao nhất, rồi T5) × độ tin start (có `duration`/byte offset chính xác?) × hiệu suất thật (`completes/plays_3s`, sau ≥ 50 lượt).
- **API:** `GET /v1/shorts/pool?lang&cat&cursor` trả ~200 ứng viên/ngày/ngôn ngữ (cache 15 phút, xuất JSON tĩnh R2 dự phòng); `POST /v1/events/short` gộp (App Check, chỉ khi có consent).
- **Cá nhân hoá trên máy** (không gửi hồ sơ người dùng lên server): client chấm `quality + 0.3·affinity(chủ đề từ onboarding, show đã theo dõi, lịch sử nghe) + 0.2·rank show trong Thịnh hành + 0.1·độ mới`, đa dạng (một show không lặp trong 8 thẻ), 20% thẻ khám phá.
- **Kiểm duyệt:** chỉ feed `vetted`, `ts_podcast_black_list`, nút "Báo cáo"/"Không quan tâm show này" trên mỗi thẻ.
- **Ngân sách T5: $45/tháng (owner 16/11/2026).** Ước tính, cần kiểm lại trên trang giá Google: audio ~32 token/giây ≈ 115k token/giờ, Gemini Flash audio $0,30–1,00/1M token → ~$0,03–0,12/giờ audio → **$45 ≈ 375–1.300 giờ/tháng**. Lấy mức giá cao để an toàn:
  - Phụ đề lấy **trong cùng một lần gọi** (output ~600 token/tập, không đáng kể) rồi lưu WebVTT của đoạn lên R2 → `captions_url`.
  - Mỗi tập chỉ gửi **20 phút** (bỏ 2 phút đầu thường là intro/quảng cáo) ≈ $0,04/tập → **~30–40 tập/ngày**.
  - Thứ tự ưu tiên: tập mới nhất của show trong "Thịnh hành" → show có lượt nghe trong app → còn lại.
  - Trần cứng hai lớp: (1) worker đếm token theo tháng, dừng job khi chạm $40; (2) budget alert + giới hạn quota trên Google Cloud Billing ở $45.
  - Tận dụng cache: một tập chỉ phân tích một lần; short tồn tại 60 ngày.
- **Gỡ trong 24 h (owner 16/11/2026):** trang `tspodcast.web.app/go-bo` + email liên hệ; khi nhận yêu cầu: (1) thêm feedId vào khoá RC chặn (mở rộng `ts_podcast_black_list` hoặc khoá mới `ts_blocked_feed_ids`) → client ẩn ngay khi fetch RC; (2) backend đặt `feeds.status=blocked`, `shorts.status=blocked`, xoá `captions_url` trên R2, loại khỏi Thịnh hành và trang web ở lần xuất kế tiếp (≤ 1 h). Có cảnh báo Telegram khi có yêu cầu mới để kịp hạn 24 h. Ghi `audit_log`.
  - Hết ngân sách giữa tháng thì feed vẫn chạy bằng T1–T3 + short cũ.

### 4.4 Trải nghiệm trên app

**Thẻ có video:** video full màn hình; video 16:9 thì đặt giữa, phía sau là bản phóng to + blur của chính khung hình (hoặc ảnh bìa nếu không lấy được frame).

**Thẻ chỉ có audio (đúng yêu cầu):**
- Nền: ảnh tác giả/ảnh bìa phóng to phủ màn hình, blur 40–60, phủ gradient tối theo `dominant_color`.
- Giữa: ảnh tác giả tròn (nếu có) hoặc ảnh bìa vuông bo góc ~70% chiều rộng, zoom chậm kiểu Ken Burns; vòng sóng âm động. Không dùng `Visualizer` Android vì cần quyền ghi âm → dùng biên độ tính sẵn hoặc animation.
- Phụ đề chạy theo từ: từ `podcast:transcript` của creator nếu có, nếu không thì phụ đề AI của đoạn (`captions_url`). Có nhãn nhỏ "AI" cạnh tiêu đề/phụ đề do AI tạo (rẻ, giảm rủi ro Luật AI 134/2025). S0 chưa có backend nên chỉ hiện tiêu đề tập.
- Dưới: tên show, tiêu đề tập, thanh tiến độ của đoạn, nút **Theo dõi**, **Nghe cả tập từ mm:ss** (giao sang player chính đúng offset), Lưu (vào "Đoạn đã lưu" N12), Chia sẻ, `…` (Báo cáo, Không quan tâm).

**Kỹ thuật phát:**
- Android: `MediaItem.ClippingConfiguration(start, end)` + **một** ExoPlayer riêng cho feed (không đụng `PlayerSessionService`); `DefaultPreloadManager` của Media3 preload **1 thẻ kế** (không hơn, để giữ data người dùng và không làm sai lượt tải của creator). MP3 VBR không có header Xing seek lệch → bật `Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING` cho đoạn ngắn, hoặc ưu tiên đoạn ở đầu tập. Blur: `RenderEffect` (API 31+) / Haze; dưới 31 dùng bitmap thu nhỏ đã blur sẵn (Coil transformation).
- iOS: `AVPlayer` + `seek(to:)` + `forwardPlaybackEndTime`, preload bằng `AVPlayerItem` thứ hai; `ScrollView` dọc + `.scrollTargetBehavior(.paging)`; blur bằng `.blur()`/material.
- Mở tab Shorts thì tạm dừng player chính; rời tab thì trả lại. Tôn trọng chế độ tiết kiệm dữ liệu (chỉ preload trên Wi-Fi nếu bật).
- **Quảng cáo (owner 16/11/2026: có):** thẻ native AdMob toàn màn hình mỗi 8 short, cờ RC `shorts_ad_interval` (mặc định 8, `0` = tắt), cho phép vuốt qua ngay; không phát quảng cáo âm thanh đè lên audio của creator. Premium không có. Dùng ad unit riêng `shorts_native` để đo doanh thu riêng.

**Vị trí trong app:** tab mới **"Lướt"** (Android: 5 tab; iOS: 4 tab), cờ RC `shorts_enabled` + rollout %. Thêm điểm vào từ Discover ("Nghe thử 30 giây") và **onboarding**: lướt 5 short → gợi ý theo dõi (G10).

### 4.5 Chia sẻ short thành video (đòn bẩy kéo người dùng mới)

- Render **trên máy** video dọc 1080×1920 ≤ 60 s: nền blur + ảnh + phụ đề + sóng âm + **watermark TSPodcast + tên show + link/QR** (Android `Media3 Transformer`, iOS `AVAssetWriter`/`AVVideoComposition`). Không render trên server, nên không phát sinh bản sao trên hạ tầng của mình.
- Đích: TikTok (Share Kit), Reels, Zalo, lưu vào máy. Link `tspodcast.web.app/e/<id>?t=<start>` mở thẳng app tại đoạn đó (đã có hạ tầng PR #76; trang web hiện nút tải app khi chưa cài).
- **Trách nhiệm (owner 16/11/2026):** người dùng tự chịu trách nhiệm về nội dung họ đăng. Để điều này có hiệu lực:
  - Thêm điều khoản vào `site/` (trang terms) và màn Điều khoản trong app: người dùng chịu trách nhiệm khi đăng lại nội dung của bên thứ ba, app chỉ cung cấp công cụ.
  - Lần đầu xuất video: hộp thoại xác nhận ngắn ("Đoạn này thuộc về <show>. Bạn chịu trách nhiệm khi đăng lên mạng xã hội.") + checkbox, lưu đã đồng ý trên máy.
  - Video luôn ≤ 60 s, ghi tên show và link về tập gốc; show bị gỡ (24 h) thì không xuất được nữa.
- Mọi short đều xuất được video và thẻ ảnh + link.

---

## 5. Lộ trình

| Mốc | Phạm vi | Phụ thuộc | Dev-day |
|---|---|---|---|
| **R0** | Màn "Thịnh hành" (PodcastIndex live), Android + iOS | — | 6–8 |
| **S0** | Prototype tab "Lướt", **không backend**: pool từ PodcastIndex live (`recent/soundbites`, tập mới nhất của show trending `lang=vi`, `episodes/random?lang=vi`), tầng T1 + T6, thẻ audio blur + thẻ video khi enclosure là `video/*`, preload 1 thẻ, analytics, cờ RC 10%. Không lưu gì phía server | — | 14–18 (2 nền tảng) |
| *Cổng quyết định* | Sau 3–4 tuần S0: giữ/bỏ dựa trên chỉ số §6 | Số liệu S0 | — |
| BE lite | BE-M0 → M3 như plan backend + parse `soundbite/chapters/alternateEnclosure/person` | Đã có trong plan | (đã tính) + 2 |
| **S1** | Bảng `shorts`, job extract T1–T3, **highlight AI T5 với trần $45/tháng**, `/v1/shorts/pool`, cá nhân hoá trên máy, chuyển client sang pool backend (cờ `ds_shorts`) | BE-M2 | 10–12 + 2/nền tảng (gồm phụ đề, trang gỡ 24 h) |
| **R1** | Play ping (BE-M4b), `chart_snapshots`, Thịnh hành 100/chủ đề/Đang lên/Tập thịnh hành, ▲▼, lịch sử, trang web `/thinh-hanh`, thẻ chia sẻ thứ hạng | BE-M4b, ≥ 4 tuần dữ liệu | 6–8 + 3/nền tảng + 2 |
| **S2** | Xuất video dọc + thẻ ảnh + link, hộp thoại trách nhiệm, điều khoản mới, "Đoạn đã lưu" | S1 (phụ đề) | 8–12 |
| ~~S3~~ | ~~Creator đăng short~~ — bỏ (không có chương trình creator) | | |

Thứ tự: **R0 + S0 song song ngay** (không phụ thuộc backend, kiểm chứng nhanh nhu cầu) → backend lite theo kế hoạch cũ → S1 → R1 → S2.

---

## 6. Đo thành công

| Tính năng | Chỉ số | Ngưỡng giữ |
|---|---|---|
| Thịnh hành | CTR từ màn Thịnh hành vào show; % người xem Thịnh hành theo dõi ≥ 1 show | CTR ≥ 15% |
| Shorts | Số thẻ/phiên; % thẻ nghe ≥ 3 s và nghe hết; **follow/100 thẻ**; % thẻ dẫn tới "Nghe cả tập" ≥ 2 phút | follow ≥ 1/100 thẻ, nghe cả tập ≥ 3% |
| Shorts (giữ chân) | D1/D7 của người dùng mới dùng tab Lướt so với nhóm không có cờ | D7 +2 điểm % |
| Kéo người dùng mới | Lượt cài từ link chia sẻ short/Thịnh hành (UTM, Play Install Referrer, App Store campaign); lượt truy cập organic vào `/thinh-hanh` | ≥ 10% lượt cài mới từ chia sẻ sau 3 tháng |
| Không phá cái cũ | Thời lượng nghe/DAU, crash-free | Không giảm > 3% |

Event mới: `chart_view`, `chart_item_click`, `chart_share`, `short_impression`, `short_play_3s`, `short_complete`, `short_follow`, `short_full_episode`, `short_share {target}`, `short_hide`, `short_report`. Cập nhật `docs/done/analytics/analytics-events.md` khi làm.

---

## 7. Rủi ro

- **Pháp lý:** owner đã tự chốt 6 câu ở §9 và chấp nhận rủi ro; chưa có ý kiến luật sư. Biện pháp giảm rủi ro giữ lại: không host lại audio, không lưu transcript cả tập, nhãn "AI", gỡ trong 24 h, điều khoản trách nhiệm khi chia sẻ. L3 và L5 của plan backend vẫn còn treo.
- **Chất lượng đoạn:** T6 hay rơi vào quảng cáo đọc → S0 phải đo `short_complete` theo tầng và tắt T6 nếu thấp.
- **Seek và data:** MP3 VBR seek lệch; enclosure qua podtrac/op3 redirect chậm → timeout 3 s rồi bỏ thẻ. Preload nhiều làm sai lượt tải theo chuẩn IAB (ngưỡng cụ thể chưa kiểm chứng) → chỉ preload 1 thẻ.
- **Nguồn cung VN mỏng:** soundbite/chapters gần như không có và không có chương trình creator → S1 dựa vào T3 + **AI (T5) trong trần $45**. ~30–40 tập/ngày × 1–3 đoạn ≈ 1.500–3.000 short mới/tháng, đủ cho feed nhưng chỉ phủ show đầu bảng.
- **Store:** feed nội dung bên thứ ba cần nút báo cáo + chặn trên mỗi thẻ.
- **Gian lận thứ hạng:** công khai thứ hạng dễ thành đích bị cày → App Check + duyệt tay đột biến.

---

## 8. Quyết định owner (16/11/2026)

1. **Tên gọi:** chỉ dùng **"Thịnh hành"**, cả R0 lẫn R1; không dùng "Bảng xếp hạng/BXH".
2. **Tab "Lướt" riêng**: tab thứ 5 trên Android, thứ 4 trên iOS, dưới cờ RC `shorts_enabled`, rollout 10% trước.
3. **Không crawl** TikTok/YouTube/Spotify/Instagram; YouTube chỉ là nút link-out.
4. 6 câu pháp lý: owner tự trả lời, xem §9.
5. **Không có chương trình creator** (ngân sách hạn hẹp): bỏ T4 và S3, bỏ email/claim cho creator trong R1; AI (T5) thành nguồn short chính.
6. **Ngân sách AI: $45/tháng**, trần cứng như §4.3.

## 9. Câu hỏi pháp lý: owner tự trả lời (16/11/2026)

Owner trả lời thay cho luật sư và chấp nhận rủi ro. Nếu sau này thuê luật sư, gửi kèm bảng này để rà lại.

| # | Câu hỏi | Owner trả lời | Làm gì |
|---|---|---|---|
| 1 | Phát đoạn 30–90 s từ file gốc trong feed có quảng cáo xen thẻ | **Có quảng cáo** | Thẻ native mỗi 8 short (§4.4) |
| 2 | AI (Gemini) nghe audio để chọn đoạn, lưu mốc thời gian + tiêu đề | **Không cần xin phép**: nội dung công khai, chỉ trích đoạn | T5 là nguồn chính (§4.2) |
| 3 | Lưu và hiện phụ đề AI của riêng đoạn | **Có** | `captions_url` trong S1 (§4.3) |
| 4 | Người dùng xuất đoạn thành video đăng MXH | **Người dùng chịu trách nhiệm** | Điều khoản + hộp thoại xác nhận (§4.5) |
| 5 | Công khai bảng xếp hạng theo lượt nghe | **Chỉ giữ tên "Thịnh hành"** | Không dùng chữ BXH, không hiện số người nghe (§3) |
| 6 | Thời hạn gỡ khi creator yêu cầu | **24 h** | Trang `/go-bo`, khoá RC chặn, cảnh báo Telegram (§4.3) |

---

## Nguồn

- Apple RSS generator: https://rss.marketingtools.apple.com/ (đo 16/11/2026: chart tập VN 200, chart show VN 504)
- Spotify podcast charts: https://podcastcharts.byspotify.com/ · https://support.spotify.com/us/creators/article/podcast-charts/ · diễn đàn dev: https://community.spotify.com/t5/Spotify-for-Developers/Clarification-internal-business-DB-AI-workflow-using-Web-API/td-p/7505249
- YouTube podcast charts: https://blog.youtube/news-and-events/youtube-charts-top-us-podcasts/ · https://podnews.net/update/youtube-podcast-charts
- YouTube API: https://developers.google.com/youtube/terms/developer-policies · https://developers.google.com/youtube/terms/required-minimum-functionality
- Taddy: https://taddy.org/developers/podcast-api/get-top-charts · PodEngine: https://www.podengine.ai/api-docs/get-podcast-charts.md
- Video podcast/HLS: https://www.podderapp.com/post/hls-video-podcast-hosting-support · https://transistor.fm/apple-podcasts-video/ · https://livewire.io/hls-video-in-podcasts/ · https://podnews.net/article/podcast-apps-supporting-video
- Spotify Clips: https://newsroom.spotify.com/2026-05-27/podcast-clips-save-share-moments/ · https://reap.video/blog/spotify-podcast-clips
- Giá Gemini (cần kiểm lại trên ai.google.dev): https://deploybase.ai/articles/gemini-api-pricing-2026

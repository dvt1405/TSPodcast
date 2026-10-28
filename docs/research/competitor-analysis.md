# Phân tích đối thủ cạnh tranh: TSPodcast (podcast + radio)

> Ngày lập: 29/10/2026 · Phạm vi: Android trước (`tss.t.podcast`), sau đó iOS và web ·
> Thị trường: Việt Nam trước, quốc tế sau.
>
> Cách đọc: số liệu có link là từ nguồn công khai năm 2025–2026. Chỗ nào ghi
> **(chưa xác minh)** là nguồn thứ cấp, nguồn cũ, hoặc nhận định tổng hợp. Cần kiểm tra lại
> trước khi đưa vào tài liệu gọi vốn hay tài liệu marketing.

---

## 1. TSPodcast hiện có gì (đọc từ code, nhánh `main` @ 47fc4f4)

| Mảng | Đã có | Chưa có |
|---|---|---|
| Nguồn nội dung | PodcastIndex API: trending, recent, live, category, search; `medium=music` (bộ lọc "Nhạc" trong Search, hàng "Kho nhạc"); có blacklist qua Remote Config; dòng ghi công "Dữ liệu podcast cung cấp bởi Podcast Index" | Theo dõi (subscribe) feed RSS tùy ý, nhập OPML, feed riêng tư |
| Radio | Lấy danh sách kênh VOV (vovmedia.vn) và VOH (voh.com.vn) bằng **Jsoup scraping** (`coreRadio/RadioRepository`, `VOHRepository`) | Danh mục kênh có bản quyền, lịch phát sóng, hẹn giờ báo thức |
| Player | Media3 ExoPlayer, `MediaSessionService`, thông báo media, lịch sử nghe, playlist, yêu thích | Tốc độ phát, hẹn giờ tắt, bỏ khoảng lặng, chapters, transcript, tải về offline, hàng đợi thông minh, Android Auto (`MediaLibraryService`), Wear OS, widget |
| Khám phá | Onboarding chọn thể loại yêu thích, Discover, tìm kiếm | Gợi ý cá nhân hoá thật, tìm kiếm ngữ nghĩa, bảng xếp hạng VN |
| Tài khoản / xã hội | Không có đăng nhập (`ProfileScreen` chỉ là khung 15 dòng) | Đồng bộ đa thiết bị, chia sẻ, bình luận, giới thiệu bạn bè |
| Kiếm tiền | AppLovin MAX: app-open, banner, native (một nhánh đang gỡ, dự kiến chuyển sang AdMob) | Gói trả phí, IAP |
| Hạ tầng | Firebase Analytics, Crashlytics, Remote Config, FCM; Facebook SDK; targetSdk 36 | Backend riêng (mọi tính năng AI đều cần backend) |

Tóm lại, TSPodcast đang là một **trình duyệt podcast + radio miễn phí có quảng cáo**. Các tính
năng cơ bản mà người nghe podcast mặc định phải có (tải offline, tốc độ, hẹn giờ ngủ, theo
dõi show) vẫn còn thiếu. Lợi thế khác biệt hiện có: radio VN và podcast trong cùng một app,
giao diện tiếng Việt, kho nhạc RSS.

---

## 2. Bức tranh thị trường 2026

- **YouTube** là nền tảng podcast lớn nhất: YouTube nói có hơn 1 tỷ người dùng podcast hằng
  tháng, và người dùng Premium đã xem hơn 800 triệu giờ podcast trong tháng 4/2026
  ([chatai.com](https://www.chatai.com/posts/youtube-adds-ai-powered-podcast-discovery-and-smart-playback-controls-for-premium-users),
  [phonearena](https://www.phonearena.com/news/youtube-is-trying-to-win-the-podcast-war-and-premium-is-its-weapon_id180719)).
  Tại Mỹ, YouTube dẫn đầu về nền tảng nghe chính (31% so với 27% của Spotify, Edison)
  ([TheWrap](https://thewrap.com/youtube-podcasts-bigger-spotify-apple-data)).
- **Video podcast trở thành dòng chính**: hơn 500 triệu người dùng Spotify đã xem video
  podcast, tăng gần 50% so với cùng kỳ
  ([Spotify newsroom](https://newsroom.spotify.com/tag/creator-memberships/),
  [advanced-television](https://www.advanced-television.com/2026/03/03/report-youtube-leads-as-video-podcasts-go-mainstream/)).
- **AI chuyển từ "transcript" sang "hội thoại với nội dung"**. Spotify có Q&A trong lúc nghe và
  "Personal Podcasts" do AI tạo. YouTube có gợi ý podcast bằng AI và "Auto speed". Snipd có
  "chat với tập". Transcript đã thành tính năng mặc định: Apple Podcasts có từ 2024, Pocket
  Casts có từ 2025, Overcast có từ 4/2026.
- **Việt Nam**: không tìm được khảo sát công khai năm 2025–2026 về thói quen nghe podcast của
  người Việt. Các nền tảng audio nội địa (Fonos, Voiz FM) đi theo hướng sách nói + podcast
  độc quyền, thu phí hội viên. Vietcetera khép lại show chủ lực *Have A Sip* vào 5/2026
  ([Vietcetera](https://vietcetera.com/amp/vn/a-good-goodbye-hanh-trinh-have-a-sip-khep-lai-nhung-nhung-cau-chuyen-la-con-mai-)).
  Điều này cho thấy podcast Việt sống chủ yếu nhờ YouTube và Spotify, còn app chuyên podcast
  thuần Việt gần như chưa có. Đây vừa là khoảng trống vừa là rủi ro, vì cầu có thể chưa đủ.
  **(Nhận định, chưa xác minh)**

---

## 3. Hồ sơ từng đối thủ

### 3.1 Nền tảng lớn

#### Spotify (podcast)
- **Tính năng chính:** audio và video podcast, Partner Program, chương (chapters), bình luận,
  hỏi đáp, bình chọn. Năm 2026 có thêm **Prompted Playlists cho podcast** (Premium ở
  US/CA/UK/IE/AU/SE/NZ; "hơn một nửa người dùng thử đã khám phá show mới")
  ([TechCrunch](https://techcrunch.com/2026/04/07/spotifys-prompted-playlist-feature-will-now-work-for-podcasts-too/)).
- **AI:** "Ask questions" trong lúc nghe hoặc xem (Premium mobile ở US, SE, IE).
  **Personal Podcasts** là audio cá nhân do AI tạo, dùng credit hằng tháng, có công cụ CLI
  "Save to Spotify" để agent AI (Codex, Claude Code) tạo audio vào thư viện
  ([MacRumors](https://www.macrumors.com/2026/05/08/spotify-personal-podcasts-ai-agent/),
  [Tubefilter](https://www.tubefilter.com/2026/05/21/spotify-investor-day-creator-memberships-personal-podcasts/)).
  Có tích hợp Claude để gợi ý nhạc và podcast (4/2026).
- **Kiếm tiền:** Premium, quảng cáo (SAI), Creator Memberships (sắp ra), Creator Sponsorships.
  Podcast đã có lãi năm thứ hai
  ([podcastnewsdaily](https://www.podcastnewsdaily.com/news/spotify-s-podcast-bet-ai-audio-paid-memberships-and-personalized-content/article_b026ccda-0196-4f7d-9bda-512852454063.html)).
- **Người dùng thích:** một app cho cả nhạc lẫn podcast, gợi ý tốt, video.
- **Người dùng phàn nàn:** giao diện bị video và gợi ý lấn át, khó quản lý thư viện podcast,
  thiếu tính năng "power user" như tải tự động hay bỏ khoảng lặng. **(Nhận định tổng hợp,
  chưa xác minh)**
- **Bài học cho TSPodcast:** AI hỏi đáp và tạo playlist bằng prompt hiện chỉ dành cho Premium ở
  vài thị trường phương Tây. Ở VN, tiếng Việt, chưa ai làm điều này. Đây là cửa sổ cơ hội.

#### YouTube / YouTube Music (podcast)
- **Tính năng:** podcast video và audio, nghe nền (Premium), tab Podcast trong YouTube Music.
  Từ khoảng 28/5/2026 có công cụ gợi ý podcast bằng AI dựa trên sở thích và tâm trạng (mở
  rộng từ "Ask Music"), **Auto speed** và chế độ nghe khi di chuyển. Cả ba chỉ cho Premium, ra
  Android trước. Creator Mỹ sắp được AI gợi ý clip ngắn từ podcast dài
  ([chatai.com](https://www.chatai.com/posts/youtube-adds-ai-powered-podcast-discovery-and-smart-playback-controls-for-premium-users),
  [techeconomy.ng](https://techeconomy.ng/youtube-ai-tools-for-podcasters-clips-shorts-video)).
- **Quy mô:** hơn 1 tỷ người dùng podcast mỗi tháng.
- **Kiếm tiền:** quảng cáo, Premium.
- **Phàn nàn:** nghe nền và tắt màn hình cần Premium, tốn data vì là video, không có
  RSS/OPML, khó tìm tập cũ. **(Nhận định tổng hợp)**
- **Bài học:** người Việt nghe podcast Việt chủ yếu trên YouTube. TSPodcast nên làm tốt những
  việc YouTube miễn phí làm kém: **nghe nền miễn phí, tiết kiệm data, tải offline, hẹn giờ
  ngủ**.

#### Apple Podcasts
- **Tính năng:** transcript tự động (từ iOS 17.4, sau mở rộng thêm nhiều ngôn ngữ, chưa có
  tiếng Việt theo các nguồn đã thấy). **Chapters tự tạo bằng AI** có nhãn "Automatically
  created" (iOS 26.2)
  ([MacRumors](https://www.macrumors.com/2024/10/02/apple-podcasts-transcriptions-more-languages/),
  [MacRumors iOS 26.2](https://www.macrumors.com/2025/11/04/ios-26-2-podcasts-app-update/),
  [BGR](https://bgr.com/2076040/ios-26-2-fixed-apple-podcasts-biggest-problem)).
- **Kiếm tiền:** Apple Podcasts Subscriptions (creator thu phí, Apple lấy phần trăm).
- **Ý nghĩa với TSPodcast:** trên iOS, Apple Podcasts có sẵn trong máy. Khi lên iOS, TSPodcast
  phải có khác biệt rõ ràng (tiếng Việt, radio VN, AI tiếng Việt), nếu không sẽ gặp rủi ro
  guideline 4.3 (xem `docs/policy/store-policy-review.md`).

#### Audible
- **Tính năng:** sách nói, podcast, Audible Originals.
- **AI:** từ 5/2025 có dịch vụ đọc bằng AI cho nhà xuất bản (hơn 60.000 tựa đọc bằng AI) và
  dịch thuật AI (text-to-text, và speech-to-speech giữ giọng người đọc, các cặp EN sang
  ES/FR/IT/DE, beta)
  ([TechCrunch](https://techcrunch.com/2025/05/13/audible-is-expanding-its-ai-narrated-audiobook-library/),
  [Publishers Weekly](https://www.publishersweekly.com/pw/print/20250519/97756-audible-expands-catalog-with-ai-narration-and-translation-services.html)).
  Không tìm thấy cập nhật năm 2026.
- **Kiếm tiền:** hội viên và credit.
- **Phàn nàn:** một bộ phận người nghe phản đối giọng đọc AI. Đây là tín hiệu cho tính năng
  "dịch và lồng tiếng AI" của TSPodcast: phải ghi nhãn rõ và để người dùng tự chọn bật.

### 3.2 App podcast chuyên dụng (đối thủ trực tiếp về trải nghiệm)

#### Pocket Casts (Automattic)
- **Tính năng:** đa nền tảng (Android, iOS, web, Wear, Auto, CarPlay), bộ lọc, bỏ khoảng lặng,
  tăng âm lượng giọng nói, chapters, đồng bộ, web player miễn phí.
- **AI:** **Generated transcripts** cho thuê bao Plus/Patron (từ v7.85, 2025). Transcript do
  creator cung cấp vẫn miễn phí. Tháng 3/2026 cải thiện khả năng phát hiện transcript
  ([9to5Google](https://9to5google.com/2025/04/28/pocket-casts-generated-transcripts/),
  [releasebot](https://releasebot.io/updates/automattic/pocket-casts-android)).
- **Kiếm tiền:** Plus $3.99/tháng hoặc $39.99/năm, Patron $9.99/tháng. Gói miễn phí không có
  quảng cáo xen ngang.
- **Người dùng thích:** giao diện đẹp, đồng bộ tốt, nhiều tính năng cho người nghe nhiều.
- **Phàn nàn:** các đợt tăng giá và việc chuyển tính năng vào gói trả phí. **(Nhận định tổng
  hợp)**
- **Bài học:** mô hình "transcript của creator miễn phí, transcript AI trả phí" rất hợp với
  TSPodcast về chi phí.

#### Castbox
- **Tính năng:** podcast, radio internet, sách nói. Có Android, iOS, web, Auto, CarPlay, Alexa,
  Google Home. Nhập OPML. Kho khoảng 95 triệu mục.
- **Quy mô:** hơn 10 triệu lượt cài trên Play, khoảng 367 nghìn đánh giá, cập nhật 9/2026
  ([marlvel.ai, ước tính bên thứ ba](https://marlvel.ai/api/llm/apps/news/fm-castbox-audiobook-radio-podcast),
  [AppGoblin](https://appgoblin.info/apps/1243410543)). **(Ước tính bên thứ ba)**
- **Kiếm tiền:** quảng cáo, Premium.
- **Phàn nàn:** quảng cáo dày, lỗi tải về và đăng nhập (một số review 7/2026). **(Nguồn thứ
  cấp)**
- **Bài học:** đây là mô hình gần TSPodcast nhất (podcast + radio + quảng cáo). Lỗi phổ biến
  nhất của nhóm này là **quảng cáo gây khó chịu**, nên TSPodcast cần tránh.

#### Podcast Addict (Android)
- **Tính năng:** podcast, **radio**, audiobook, YouTube/Twitch, RSS. Cực nhiều tuỳ chỉnh.
  Bản v2026.12.1 (15/10/2026) có lịch sử nghe radio, cân bằng âm lượng, cập nhật danh sách
  lớn ổn định hơn ([changelog](https://podcastaddict.com/changelog/2026_12_1)).
- **Kiếm tiền:** quảng cáo, có bản Donate để gỡ quảng cáo.
- **Quy mô:** khoảng hơn 10 triệu lượt cài trên Play. **(Chưa xác minh 2026)**
- **Thích:** mạnh, miễn phí, có radio. **Phàn nàn:** giao diện cũ, khó với người mới.
  **(Nhận định tổng hợp)**
- **Bài học:** đây là đối thủ trực tiếp nhất về **podcast + radio trên Android**. TSPodcast
  khác biệt bằng UI hiện đại (Compose, Haze) và nội dung VN, nhưng cần bù khoảng cách tính
  năng: tải offline, tốc độ, hẹn giờ.

#### Overcast (chỉ iOS)
- **AI/transcript:** bản 2026.04 có transcript (tạo cho mọi tập, kể cả feed riêng tư; có cảnh
  báo về sai sót). Bản 2026.5 cho chọn văn bản trong transcript. Chưa có tóm tắt AI
  ([9to5Mac](https://9to5mac.com/2026/04/08/overcast-launches-podcast-transcripts-in-new-app-update-for-iphone/),
  [iCulture](https://www.iculture.nl/nieuws/overcast-podcast-transcripties/)).
- **Nổi tiếng với:** Smart Speed (bỏ khoảng lặng), Voice Boost.
- **Kiếm tiền:** quảng cáo nhẹ và Premium.
- **Bài học:** một developer một mình vẫn làm được transcript cho mọi tập. Chi phí của tính
  năng này đã giảm đủ để app nhỏ làm được (xem tài liệu roadmap).

#### Snipd ("AI podcast player")
- **Tính năng AI:** transcript, chapters AI, **snip** (bấm tai nghe để lưu đoạn hay thành
  ghi chú có tóm tắt), **chat với tập**, AI DJ dẫn qua các khoảnh khắc hay nhất, xuất sang
  Notion/Readwise, nhập audio và YouTube.
- **Kiếm tiền:** miễn phí giới hạn 2 tập AI mỗi tuần. Premium khoảng $6.99/tháng (giá năm
  khác nhau tuỳ nguồn), giới hạn khoảng 900 phút xử lý AI mỗi tháng cho tập chưa được xử lý
  ([App Store](https://apps.apple.com/us/app/id1557206126),
  [toolradar](https://toolradar.com/tools/snipd/pricing)).
- **Ngôn ngữ AI:** EN, DE, FR, ES, IT, PT và một số ngôn ngữ châu Âu. **Không có tiếng Việt.**
- **Bài học:** Snipd chứng minh người dùng chịu trả tiền cho "học từ podcast". Snipd giới hạn
  theo **số tập hoặc số phút AI**, vì chi phí nằm ở số tập cần xử lý chứ không ở số người
  dùng.

#### Podbean
- **Hướng đi:** chủ yếu là nền tảng hosting. AI dành cho creator (giảm nhiễu, tạo tiêu đề,
  show notes, transcript), tính theo credit (5/2026). App nghe có khoảng 16 triệu lượt tải.
  Dừng dịch vụ livestream từ 18/6/2026
  ([podbean.com/new-features](https://www.podbean.com/new-features)).
- **Bài học:** không có AI cho người nghe. Bình luận theo tập trên website là tín hiệu cho
  tính năng xã hội.

### 3.3 Người chơi Việt Nam

| App | Mô hình | Điểm đáng chú ý | Nguồn |
|---|---|---|---|
| **Fonos** (Sách nói & PodCourse) | Hội viên trả phí (Lite/Premium) | Hơn 13.000 nội dung, hơn 1.000 sách nói bán chạy, hơn 150 podcast. Từng chiếm phần lớn doanh thu sách nói VN (data.ai). Dữ liệu người dùng mới nhất tìm được là năm 2022 (hơn 130 nghìn tài khoản/tháng) | [App Store VN](https://apps.apple.com/vn/app/fonos-s%C3%A1ch-n%C3%B3i-podcourse/id1491002000), [Vietcetera](https://vietcetera.com/vn/fonos-nhan-von-dau-tu-18-trieu-usd-vong-pre-series-a-len-ke-hoach-phat-trien-podcast) |
| **Voiz FM** | Freemium và VIP | Hơn 15.000 đầu sách nói, truyện, podcast. 4.7★ với khoảng 26,1 nghìn đánh giá (App Store). Từng phát triển giọng đọc AI (2020) | [XTmobile](https://www.xtmobile.vn/app-nghe-truyen-audio-tren-dien-thoai), [App Pricing Lab](https://apppricinglab.com/app/apple/1447395824), [SGGP](https://www.sggp.org.vn/voiz-fm-tien-phong-trong-linh-vuc-ban-quyen-post545170.html) |
| **Vietcetera** (Have A Sip, Vietnam Innovators…) | Nhà sản xuất nội dung, phát qua YouTube, Spotify, Apple | *Have A Sip* 4.7★ (3.715 đánh giá) đã khép lại 5/2026 | [Podnews](https://podnews.net/podcast/i9qpx) |
| **Spiderum** | Nhà sản xuất nội dung | Không tìm thấy dữ liệu podcast năm 2026 | — |
| **Zing MP3 / NhacCuaTui** | Nhạc streaming | Không xác minh được mục podcast năm 2026. **(Chưa xác minh)** | — |
| **App radio bên thứ ba** (radiome, Viet Radio, "Radio Việt Nam") | Quảng cáo | radiome phát khoảng 80 kênh VOV có replay. Nhiều app lấy link từ web VOV/VOH, có hẹn giờ ngủ và ghi âm | [App Store radiome](https://apps.apple.com/us/app/id1131513842), [thegioididong](https://www.thegioididong.com/game-app/radio-viet-nam-nghe-dai-vov-truc-tuyen-225460) |
| **App chính chủ VOV ("VOV Go")** | — | Không xác nhận được có app chính thức tên này năm 2026 | — |

**Nhận xét về thị trường VN:**
1. App audio nội địa thu tiền bằng **nội dung độc quyền có bản quyền** (sách nói), không bằng
   tính năng player. TSPodcast không có nội dung độc quyền, nên phải cạnh tranh bằng
   **trải nghiệm, AI tiếng Việt và tổng hợp nội dung (aggregation)**.
2. Mảng radio VN đang bị chiếm bởi các app nhỏ cùng lấy link VOV/VOH, có rủi ro bản quyền
   giống TSPodcast (xem tài liệu policy). Ai có **thoả thuận chính thức với đài** trước sẽ có
   lợi thế bền vững.
3. Chưa thấy app nào làm **transcript, tóm tắt hay hỏi đáp tiếng Việt** cho podcast.

---

## 4. Bảng so sánh tính năng

✅ có · ◐ một phần / trả phí · ❌ không · ? chưa rõ

| Tính năng | Spotify | YouTube | Apple | Pocket Casts | Castbox | Podcast Addict | Overcast | Snipd | Fonos/Voiz | **TSPodcast** |
|---|---|---|---|---|---|---|---|---|---|---|
| Radio trực tiếp | ❌ | ◐ (live) | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | **✅ (VOV/VOH)** |
| Tải offline | ✅ | ◐ Premium | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **❌** |
| Tốc độ / bỏ khoảng lặng | ✅/❌ | ✅/AI auto | ✅/❌ | ✅/✅ | ✅/✅ | ✅/✅ | ✅/✅ | ✅/✅ | ✅/? | **❌/❌** |
| Hẹn giờ ngủ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **❌** |
| Theo dõi show + thông báo tập mới | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **◐ (yêu thích, chưa có thông báo)** |
| OPML / RSS tuỳ ý | ❌ | ❌ | ◐ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | **❌** |
| Android Auto / CarPlay | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ? | **❌** |
| Transcript | ✅ | ◐ phụ đề | ✅ | ◐ Plus | ? | ? | ✅ | ✅ | ? | **❌** |
| Chapters (gồm AI) | ✅ | ✅ | ✅ AI | ✅ | ? | ✅ | ✅ | ✅ AI | ? | **❌** |
| Hỏi đáp / chat với tập | ✅ (US/SE/IE) | ◐ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | **❌** |
| Clip / chia sẻ trích đoạn | ✅ | ✅ | ◐ | ✅ | ? | ◐ | ✅ | ✅ | ? | **❌** |
| Thống kê nghe / Wrapped | ✅ | ◐ | ❌ | ✅ | ? | ✅ | ◐ | ◐ | ? | **❌** |
| Giao diện + AI tiếng Việt | ◐ UI | ◐ UI | ◐ UI | ◐ UI | ◐ UI | ◐ UI | ❌ | ❌ | ✅ UI | **✅ UI, chưa có AI** |
| Mô hình thu tiền | Premium + ads | Ads + Premium | Sub creator | Plus | Ads + Premium | Ads + donate | Ads + Premium | Premium | Hội viên | **Ads** |

---

## 5. Phân tích khoảng trống (gap analysis)

### 5.1 Khoảng trống "giữ chân": thiếu là người dùng gỡ app
1. **Tải offline**: người dùng VN đi xe máy, đi xe buýt, data hạn chế. Mọi đối thủ đều có.
2. **Tốc độ phát, hẹn giờ ngủ, bỏ khoảng lặng**: mức tối thiểu của app podcast. Media3 hỗ trợ
   sẵn (`setPlaybackSpeed`, `setSkipSilenceEnabled`).
3. **Theo dõi show và thông báo tập mới**: hiện chỉ có "yêu thích" và chưa có cơ chế kéo
   người dùng quay lại. FCM đã tích hợp (`TSFcmServices`) nhưng chưa dùng cho việc này.
4. **Android Auto**: hiện dùng `MediaSessionService`, chưa có `MediaLibraryService` nên không
   duyệt được nội dung trên xe. Radio trên xe ô tô là use case tự nhiên.

### 5.2 Khoảng trống "khác biệt": cơ hội thắng
1. **AI tiếng Việt cho podcast**: transcript, tóm tắt, hỏi đáp tiếng Việt. Snipd, Spotify và
   Apple chưa hỗ trợ hoặc chưa ưu tiên tiếng Việt.
2. **Radio VN và podcast trong một app** với trải nghiệm hiện đại (lịch phát sóng, báo thức
   bằng radio, nghe lại chương trình). Cần giải quyết bản quyền trước.
3. **Cầu nối VN↔EN**: tóm tắt hoặc dịch tập podcast tiếng Anh sang tiếng Việt cho người học
   và người đi làm. Đây là "big bet" có rủi ro bản quyền, xem tài liệu policy.
4. **Tiết kiệm data / chế độ chỉ âm thanh** so với YouTube.

### 5.3 Khoảng trống "niềm tin": đang gây hại
1. **Quảng cáo app-open** hiện được gọi ở mọi lần `MainActivity.onResume()` và hiện ngay khi
   tải xong (`AppOpenAdManager.onAdLoaded → showAd()`). Nghĩa là quảng cáo có thể bật lên
   **sau khi nội dung đã hiển thị**, kể cả khi người dùng chạm vào thông báo media để mở
   player. Google xếp cách làm này vào nhóm "không khuyến khích" và có thể dẫn tới bị tắt
   phân phối quảng cáo
   ([AdMob app open guidance](https://support.google.com/admob/answer/9341964?hl=en)).
   Đây cũng là lý do số 1 khiến người dùng ghét các app cùng phân khúc (Castbox). Chi tiết ở
   `docs/policy/store-policy-review.md`.
2. **Không có màn hình quyền riêng tư hay link chính sách trong app**, không có cơ chế đồng ý
   (consent) quảng cáo.
3. **Nội dung explicit chưa được lọc hay gắn nhãn**: có trường `explicit` nhưng UI không dùng.

---

## 6. Danh sách cải tiến ưu tiên

Thang điểm: Tác động (T) và Công sức (C) từ 1 đến 5. Ưu tiên = T / C, có điều chỉnh theo rủi ro
policy.

### 6.1 Quick wins (0–6 tuần, mỗi việc dưới 1–2 tuần/dev)

| # | Việc | T | C | Ghi chú kỹ thuật |
|---|---|---|---|---|
| Q1 | **Sửa app-open ad**: chỉ hiện ở cold start, trên splash, không hiện khi đang phát hay mở từ notification, chặn trong 2–3 lần mở đầu | 5 | 1 | Bắt buộc trước khi chuyển sang AdMob, xem tài liệu policy |
| Q2 | Tốc độ phát (0.5–3x), bỏ khoảng lặng, tăng âm lượng giọng | 4 | 1 | Media3 `PlaybackParameters`, `setSkipSilenceEnabled` |
| Q3 | Hẹn giờ ngủ (phút / hết tập) cho cả podcast và radio | 4 | 1 | Coroutine timer trong `PlayerSessionService`, fade-out |
| Q4 | Nhớ vị trí nghe, nút "Tiếp tục nghe" trên Discover | 4 | 1 | Đã có `HistoryRepository`, cần lưu thêm vị trí |
| Q5 | Màn Hồ sơ/Cài đặt: chính sách quyền riêng tư, điều khoản, liên hệ/báo cáo nội dung, phiên bản, tắt cá nhân hoá quảng cáo | 3 | 1 | Cần cho store review |
| Q6 | Gắn nhãn hoặc lọc nội dung explicit (mặc định ẩn ở Discover) | 3 | 1 | Trường `explicit` đã có |
| Q7 | Chia sẻ tập/show bằng deep link (App Links) có dấu thời gian | 4 | 2 | Cần domain và trang web nhẹ (sau dùng làm bản web) |
| Q8 | Đọc transcript và chapters **do creator cung cấp** (`podcast:transcript`, `podcast:chapters`, chapters ID3) | 4 | 2 | Miễn phí, không có rủi ro AI. Hiển thị transcript đồng bộ theo thời gian |
| Q9 | Widget màn hình chính (Glance): đang phát + radio yêu thích | 3 | 2 | |

### 6.2 Nền tảng trung hạn (1–4 tháng)

| # | Việc | T | C |
|---|---|---|---|
| M1 | **Tải offline** (Media3 `DownloadManager`) + tự tải tập mới khi có Wi-Fi | 5 | 3 |
| M2 | **Theo dõi show + thông báo tập mới** (WorkManager poll `episodes/byfeedid` hoặc webhook PodcastIndex, FCM) | 5 | 3 |
| M3 | **Android Auto** (`MediaLibraryService`, cây duyệt: Đang nghe / Theo dõi / Radio), kèm điều khiển bằng giọng nói | 4 | 3 |
| M4 | Thống kê nghe, streak, "TSPodcast Wrapped" cuối năm (chia sẻ dạng ảnh) | 4 | 2 |
| M5 | Nhập/xuất OPML, thêm RSS bằng URL | 3 | 2 |
| M6 | Radio: danh mục kênh hợp pháp (thoả thuận với đài hoặc nguồn có giấy phép), lịch phát sóng, báo thức bằng radio | 4 | 3 |
| M7 | Đăng nhập tuỳ chọn (Credential Manager / Sign in with Apple) + đồng bộ đám mây, kèm **xoá tài khoản** trong app và trên web | 3 | 3 |

### 6.3 Big bets (3–12 tháng)

| # | Việc | Vì sao | Rủi ro chính |
|---|---|---|---|
| B1 | **Transcript AI tiếng Việt và tiếng Anh** theo yêu cầu, tóm tắt, chapters AI | Khác biệt rõ ở VN | Chi phí, bản quyền khi lưu transcript |
| B2 | **"Hỏi tập này"** và tìm kiếm ngữ nghĩa trên transcript | Ngang Spotify/Snipd, lại có tiếng Việt | Ảo giác (hallucination), chính sách AI của store |
| B3 | **Clip thông minh**: tự gợi ý đoạn hay, xuất video dọc kèm sóng âm và phụ đề để chia sẻ TikTok/Facebook | Kênh tăng trưởng tự nhiên | Bản quyền đoạn trích, cần ghi nguồn |
| B4 | **Bản tin AI hằng ngày** theo chủ đề (dùng chung cho mọi người trong một chủ đề) | Tạo thói quen mở app mỗi ngày | Nguồn tin, nhãn nội dung AI |
| B5 | **Dịch và lồng tiếng VN↔EN** (chỉ với creator đồng ý) | Câu chuyện "cầu nối" độc đáo | Chi phí rất cao, quyền tác phẩm phái sinh |
| B6 | iOS + web (KMP + Compose Multiplatform / SwiftUI) | Mở rộng thị trường | Guideline 4.2/4.3 của Apple |
| B7 | Gói Premium (không quảng cáo + AI không giới hạn) | Đa dạng doanh thu, bù chi phí AI | Apple 3.1.1, Play Billing |
| B8 | Cộng đồng: bình luận theo mốc thời gian, playlist công khai | Hiệu ứng mạng | Nghị định 147/2024, chính sách UGC của store |

Chi tiết kỹ thuật, chi phí và KPI của các mục B1–B5 nằm ở
`docs/research/feature-roadmap-ai-growth.md`.

---

## 7. Nguồn tham khảo chính

- Spotify: [TechCrunch 04/2026](https://techcrunch.com/2026/04/07/spotifys-prompted-playlist-feature-will-now-work-for-podcasts-too/) · [MacRumors 05/2026](https://www.macrumors.com/2026/05/08/spotify-personal-podcasts-ai-agent/) · [Tubefilter 05/2026](https://www.tubefilter.com/2026/05/21/spotify-investor-day-creator-memberships-personal-podcasts/) · [Spotify newsroom](https://newsroom.spotify.com/tag/creator-memberships/) · [podcastnewsdaily](https://www.podcastnewsdaily.com/news/spotify-s-podcast-bet-ai-audio-paid-memberships-and-personalized-content/article_b026ccda-0196-4f7d-9bda-512852454063.html)
- YouTube: [chatai.com](https://www.chatai.com/posts/youtube-adds-ai-powered-podcast-discovery-and-smart-playback-controls-for-premium-users) · [PhoneArena](https://www.phonearena.com/news/youtube-is-trying-to-win-the-podcast-war-and-premium-is-its-weapon_id180719) · [TheWrap](https://thewrap.com/youtube-podcasts-bigger-spotify-apple-data)
- Apple Podcasts: [MacRumors](https://www.macrumors.com/2024/10/02/apple-podcasts-transcriptions-more-languages/) · [MacRumors iOS 26.2](https://www.macrumors.com/2025/11/04/ios-26-2-podcasts-app-update/)
- Pocket Casts: [9to5Google](https://9to5google.com/2025/04/28/pocket-casts-generated-transcripts/) · [releasebot](https://releasebot.io/updates/automattic/pocket-casts-android)
- Overcast: [9to5Mac 04/2026](https://9to5mac.com/2026/04/08/overcast-launches-podcast-transcripts-in-new-app-update-for-iphone/)
- Snipd: [App Store](https://apps.apple.com/us/app/id1557206126) · [toolradar](https://toolradar.com/tools/snipd/pricing)
- Podcast Addict: [changelog 2026.12.1](https://podcastaddict.com/changelog/2026_12_1)
- Castbox: [AppGoblin](https://appgoblin.info/apps/1243410543)
- Podbean: [new features](https://www.podbean.com/new-features)
- Audible: [TechCrunch](https://techcrunch.com/2025/05/13/audible-is-expanding-its-ai-narrated-audiobook-library/)
- VN: [Fonos App Store](https://apps.apple.com/vn/app/fonos-s%C3%A1ch-n%C3%B3i-podcourse/id1491002000) · [Voiz FM](https://apppricinglab.com/app/apple/1447395824) · [Vietcetera Have A Sip](https://vietcetera.com/amp/vn/a-good-goodbye-hanh-trinh-have-a-sip-khep-lai-nhung-nhung-cau-chuyen-la-con-mai-) · [radiome](https://apps.apple.com/us/app/id1131513842)

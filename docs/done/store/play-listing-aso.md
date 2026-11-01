# Google Play: nội dung store listing chuẩn ASO

> **Trạng thái: Hoàn thành** (2026-11-01) — PR #35 (vi/en), PR #48 (15 thị trường, v3.5.10018). CI không tự đẩy listing: dán tay hoặc chạy `fastlane supply`.

Cập nhật 2026-11-01. Listing hiện tại viết cho bản v3.5.10018 (player mới, 15 ngôn ngữ). Mục 9
liệt kê 17 listing Play và 17 listing App Store cho 15 thị trường.

File nguồn nằm trong `fastlane/metadata/android/<locale>/` (Play) và
`iosApp/fastlane/metadata/<locale>/` (App Store). Thư mục tiếng Việt của Play đổi từ `vi-VN`
thành `vi`, vì đó là mã Play và `fastlane supply` dùng cho tiếng Việt. CI **không** tự đẩy listing
(workflow chỉ đẩy "What's new" từ `distribution/whatsnew/whatsnew-<locale>`), nên phải dán tay
hoặc chạy `fastlane supply` theo mục 1 và mục 9.

Quy tắc của chủ app, áp cho mọi ngôn ngữ: mô tả **không nhắc quảng cáo** (kể cả "có quảng cáo",
"không quảng cáo toàn màn hình"), chỉ nói tính năng và "miễn phí"; không từ so sánh tuyệt đối,
không emoji, không viết HOA, không tên app hay thương hiệu khác (trừ Podcast Index; VOV, VOH chỉ
trong mô tả đầy đủ tiếng Việt). Kiểm tra bằng `python fastlane/check_store_metadata.py`.

## 1. Việc cần làm trong Play Console

Play Console → **Tăng trưởng → Trang thông tin trên Cửa hàng → Trang thông tin chính**:

1. Tiếng Việt (vi, ngôn ngữ mặc định): dán `title.txt`, `short_description.txt`, `full_description.txt`.
2. Thay ảnh điện thoại số 2 bằng `vi/images/phoneScreenshots/02.png` (đổi chú thích "Radio VOV, VOH" thành "Radio online").
3. Bản dịch English (en-US): dán 3 file chữ của `en-US`, thay **cả 6** ảnh bằng `en-US/images/phoneScreenshots/01–06.png` (chú thích tiếng Anh, trước đây dùng ảnh tiếng Việt).
4. Lưu, gửi duyệt cùng hoặc tách khỏi bản app (thay đổi listing được duyệt riêng).
5. Ghi ngày đổi vào mục 8 để so số liệu trước/sau.

## 2. Nội dung mới

| Trường | Giới hạn | vi | en-US |
|---|---|---|---|
| Tên ứng dụng | 30 | `TSPodcast: Nghe podcast, radio` (30) | `TSPodcast: Podcast Player` (25) |
| Mô tả ngắn | 80 | `Nghe podcast tiếng Việt và quốc tế, radio online, kho nhạc mở trong một app.` (76) | `Discover trending podcasts by topic, with sleep timer, speed control and resume.` (80) |
| Mô tả đầy đủ | 4.000 | 2.418 ký tự | 2.336 ký tự |

Thay đổi so với listing cũ và lý do:

| Trước | Sau | Lý do |
|---|---|---|
| Tên vi: `TSPodcast: Podcast & Radio` | `TSPodcast: Nghe podcast, radio` | Tên là trường có trọng số tìm kiếm cao nhất. "nghe podcast" là cách người Việt gõ khi tìm app; dùng đủ 30 ký tự. |
| Mô tả ngắn nhắc "VOV, VOH" và "miễn phí" | Bỏ tên đài và "miễn phí" | Tên đài là thương hiệu của bên khác, chưa có văn bản cho phép (blocker B4); đặt ở mô tả ngắn dễ bị hiểu là app chính thức. "miễn phí" là từ khuyến mại, Google khuyên không dùng ở tên và các trường nổi bật. |
| Mô tả đầy đủ mở đầu bằng 1 câu | 2 câu đầu nói rõ app làm gì, cho ai, "không cần tài khoản" | Người dùng thường chỉ đọc vài câu đầu trước khi bấm "Xem thêm". |
| Tiêu đề mục viết HOA | `<b>Tiêu đề</b>` | Play hiển thị chữ đậm; tránh viết hoa toàn bộ (chính sách Metadata). |
| Thiếu một số tính năng đã có | Thêm Podcast live, "Có thể bạn yêu thích", điều khiển tai nghe Bluetooth, lọc chương trình rác/cờ bạc (dòng "không quảng cáo toàn màn hình" đã bỏ theo quy tắc không nhắc quảng cáo) | Mô tả đúng và đủ chức năng là yêu cầu của chính sách, đồng thời thêm từ khoá tự nhiên. |
| Không ghi tính độc lập | "TSPodcast là ứng dụng độc lập, không liên kết với các đài phát thanh hay nhà sản xuất podcast." | Giảm rủi ro mạo danh/thương hiệu khi nhắc VOV, VOH trong mô tả. |

Mọi dòng trong mô tả đã đối chiếu với code Android trên `main` ngày 2026-11-01. Không ghi tính
năng chưa có: tìm kiếm không dấu, tải offline, "Thời sự & Bản tin". Bản 10018 thêm các dòng ở mục 6.

Tên en đổi từ `TSPodcast: Podcasts & Radio` thành `TSPodcast: Podcast Player`: radio trong app
chỉ có kênh Việt Nam, nên "Radio" trong tên dễ khiến người dùng nước khác cài để nghe đài địa
phương rồi gỡ.

## 3. Từ khoá và vị trí đặt

Chưa có số liệu lượt tìm thật. Danh sách dưới đây lấy theo cách người dùng mô tả nhu cầu và theo
tính năng của app; sau 2–4 tuần cần đối chiếu với báo cáo từ khoá ở mục 8.

| Nhóm | Từ khoá (vi) | Nằm ở | Từ khoá (en) |
|---|---|---|---|
| Lõi | nghe podcast, podcast tiếng Việt | Tên, mô tả ngắn, câu đầu | podcasts, Vietnamese podcasts |
| Radio | radio online, nghe radio, kênh phát thanh, nghe đài | Tên ("radio"), mô tả ngắn, mục Radio | live radio, radio channels |
| Nhạc | kho nhạc, nhạc | Mô tả ngắn, mục Kho nhạc | open music, music feeds |
| Thói quen nghe | phát nền, tắt màn hình vẫn nghe, màn hình khoá, tai nghe Bluetooth | Mục "Nghe thoải mái" | background playback, lock screen |
| Chủ đề | tin tức, kinh doanh, công nghệ, giáo dục, lịch sử, sức khoẻ, hài, thể thao, truyện | Mục "Khám phá" | news, business, technology… |
| Sắp có (mục 6) | hẹn giờ tắt, tốc độ phát, nghe tiếp | Khi phát hành player mới | sleep timer, playback speed |

Quy tắc khi sửa: mỗi từ khoá xuất hiện trong câu có nghĩa, không liệt kê từ khoá thành dãy,
không lặp một cụm quá 3–4 lần. "podcast" hiện xuất hiện khoảng 16 lần trong ~280 từ (tính cả tên
app và "Podcast Index"), mức tự nhiên.

## 4. Chính sách áp dụng

Theo [chính sách Metadata của Google Play](https://support.google.com/googleplay/android-developer/answer/9898842)
và [hướng dẫn store listing](https://support.google.com/googleplay/android-developer/answer/13393723):

- [x] Tên ≤ 30 ký tự, không emoji, không viết HOA toàn bộ, không "miễn phí"/"free", không xếp hạng hay khuyến mại.
- [x] Không từ so sánh tuyệt đối ("nhất", "số 1", "hàng đầu"), cũng theo Luật Quảng cáo VN (đã áp ở #28).
- [x] Không lời chứng thực ẩn danh, không so sánh với app khác, không nhắc tên app khác.
- [x] Mô tả đúng chức năng hiện có; không lặp từ khoá vô nghĩa.
- [x] Ảnh chụp phản ánh đúng trải nghiệm trong app; không "#1", "Popular", giảm giá.
- [x] Mỗi ngôn ngữ có ảnh với chữ của ngôn ngữ đó (vi và en đã tách).
- [ ] Tên và logo của bên khác: ảnh số 1 đang có bìa podcast của bên thứ ba (ví dụ logo SBS). Nên chụp lại màn Khám phá khi hàng đầu là các chương trình tiếng Việt, bìa không mang logo tổ chức lớn.
- [ ] Radio VOV/VOH: chỉ nhắc tên trong mô tả đầy đủ, đúng sự thật, kèm câu "ứng dụng độc lập". Khi có văn bản cho phép (B4) có thể đưa lại vào mô tả ngắn và ảnh.

## 5. Ảnh, feature graphic, video

| # | Màn | Chú thích vi | Chú thích en |
|---|---|---|---|
| 1 | Khám phá | Podcast Việt và thế giới mỗi ngày | Podcasts from Vietnam and the world |
| 2 | Radio | Radio online, chạm là nghe | Live radio, tap to listen |
| 3 | Đang phát | Tắt màn hình vẫn nghe tiếp | Keep listening with the screen off |
| 4 | Tìm kiếm | Tìm đúng chủ đề bạn quan tâm | Find the topics you care about |
| 5 | Kho nhạc | Kho nhạc mở, nghe thả ga | Open music, straight from artists |
| 6 | Yêu thích | Gom tập yêu thích về một chỗ | Your favourite episodes in one place |

Sinh lại: `cd brand/scripts && node frame.js --batch ../store/screenshots/examples.json` (vi, cả Play
và App Store) và `--batch ../store/screenshots/examples-en.json` (en, Play). Chép
`out*/NN-*-play.png` vào `fastlane/.../phoneScreenshots/NN.png`.

Thứ tự giữ nguyên: 3 ảnh đầu hiện ngay trên trang kết quả nên dành cho 3 lý do chính để cài
(podcast, radio, nghe nền). Khi phát hành player mới, thay ảnh 3 bằng màn Player có chip tốc độ
và nút hẹn giờ (chú thích gợi ý ở mục 6).

Video: Play chỉ nhận link YouTube. Tải `brand/video/out/tspodcast-demo-1080x1920.mp4` lên
YouTube (Unlisted, tắt quảng cáo) rồi dán link. Video tăng tỉ lệ chuyển đổi khi người dùng xem
ảnh đầu tiên; nếu không có video, ảnh 1 là thứ người dùng thấy đầu tiên.

## 6. Bản Android có player mới (đã áp dụng cho v3.5.10018)

Các dòng dưới đây đã được đưa vào `full_description.txt` và `changelogs/10018.txt` của mọi ngôn
ngữ (mục 9); phần này giữ lại làm bản nháp gốc. "What's new" thật của 10018 nằm ở
`distribution/whatsnew/whatsnew-<locale>`. Chỉ đẩy listing mới **sau khi** 10018 đã lên production.

**Mô tả đầy đủ, vi: thêm vào đầu mục "Nghe thoải mái, không gián đoạn"**

```
• Chỉnh tốc độ phát từ 0.5× đến 3.0×, nhớ riêng cho từng chương trình
• Hẹn giờ tắt: 5 đến 60 phút hoặc khi hết tập, nhỏ dần âm lượng trước khi dừng
• Tua lùi 15 giây, tua tới 30 giây, ngay cả trên màn hình khoá
• Nhớ vị trí đang nghe, mở lại là nghe tiếp
```

**en: thêm vào đầu "Listen your way"**

```
• Playback speed from 0.5× to 3.0×, remembered for each show
• Sleep timer: 5 to 60 minutes or at the end of the episode, with a gentle fade-out
• Skip back 15 seconds and forward 30 seconds, also on the lock screen
• Picks up where you left off
```

**Mô tả ngắn, phương án B để thử nghiệm (mục 7)**

- vi: `Nghe podcast tiếng Việt, radio online. Hẹn giờ tắt, chỉnh tốc độ, nghe tiếp.` (76)
- en: `Vietnamese podcasts and live radio, with sleep timer and speed control.` (71)

**What's new (≤ 500 ký tự)**, đặt vào `distribution/whatsnew/` và `changelogs/<versionCode>.txt`:

```
• Chỉnh tốc độ phát 0.5×–3.0×, nhớ riêng từng chương trình
• Hẹn giờ tắt: theo phút hoặc khi hết tập
• Nút tua lùi 15 giây, tua tới 30 giây trên trình phát và màn hình khoá
• Mở lại tập đang nghe dở là nghe tiếp từ chỗ cũ
• Báo lỗi rõ hơn khi không phát được, có nút Thử lại
```

```
• Playback speed 0.5×–3.0×, remembered per show
• Sleep timer by minutes or at the end of the episode
• Skip back 15 s and forward 30 s in the player and on the lock screen
• Resume an episode where you left off
• Clearer message with a Retry button when playback fails
```

**Ảnh 3 (Đang phát)**: "Nghe nhanh, nghe chậm,\nhẹn giờ tắt" / "Tốc độ 0.5×–3.0×, tự tắt khi bạn ngủ";
en "Your speed,\nyour sleep timer" / "0.5×–3.0×, stops when you fall asleep".

## 7. Thử nghiệm store listing (A/B)

Play Console → Tăng trưởng → **Thử nghiệm trang thông tin trên Cửa hàng**. Mỗi lần một thử
nghiệm, chỉ cho vi-VN, chia 50/50, chạy tối thiểu 7 ngày và tới khi Play báo đủ độ tin cậy.
Chỉ số chính: **người cài đặt giữ lại sau 1 ngày** (retained first-time installers).

| # | Giả thuyết | Biến thể | Khi nào |
|---|---|---|---|
| 1 | Ảnh đầu nói về radio thu hút hơn podcast (người tìm "nghe đài" nhiều) | Ảnh 1 = Radio, ảnh 2 = Khám phá | Ngay sau khi listing mới chạy ổn 2 tuần |
| 2 | Nêu tính năng nghe tiện (hẹn giờ, tốc độ) tăng chuyển đổi | Mô tả ngắn phương án B (mục 6) | Sau khi phát hành player mới |
| 3 | Ảnh nền sáng nổi bật hơn giữa các app nền tối | Ảnh 1–3 theme `light` | Sau #2 |

Không chạy thử nghiệm cùng lúc với chiến dịch quảng cáo trả phí mới hoặc thay đổi lớn về quảng
cáo trong app (`docs/in-progress/plans/seo-web-dashboard.md` §C.4).

## 8. Đo lường

- Play Console → **Thống kê → Thu nạp người dùng → Báo cáo**: lượt truy cập trang, tỉ lệ chuyển
  đổi theo nguồn (Tìm kiếm trên Google Play, Khám phá), và **cụm từ tìm kiếm** đưa người dùng tới
  trang. Đây là nguồn chính để sửa bảng từ khoá ở mục 3.
- Ghi mốc so sánh: tỉ lệ chuyển đổi 28 ngày trước khi đổi listing, và 28 ngày sau.
- Trả lời đánh giá trong 48 giờ (mẫu bên dưới); điểm đánh giá và tỉ lệ ANR/crash ảnh hưởng tới
  thứ hạng.

| Ngày | Thay đổi | Tỉ lệ chuyển đổi 28 ngày trước | 28 ngày sau |
|---|---|---|---|
| | Listing ASO (tài liệu này) | | |

**Mẫu trả lời đánh giá**

- Lỗi phát: "Cảm ơn bạn đã báo. Bạn cho mình xin tên chương trình/tập bị lỗi qua email hỗ trợ
  trên trang này nhé, mình sẽ kiểm tra và sửa trong bản tới."
- Muốn thêm tính năng: "Cảm ơn góp ý của bạn! Mình đã ghi lại [tính năng] vào danh sách cần làm."
- Nội dung không phù hợp: "Cảm ơn bạn. Mình đã chuyển chương trình này vào danh sách kiểm tra
  và sẽ chặn nếu vi phạm."
- Đánh giá tốt: "Cảm ơn bạn đã nghe TSPodcast! Nếu có chương trình hay, bạn chia sẻ cho bạn bè
  cùng nghe nhé."

## 9. Thị trường và ngôn ngữ

App có 15 ngôn ngữ, nên store có 17 listing (tiếng Anh và tiếng Tây Ban Nha mỗi thứ tách 2 vùng).
Danh sách podcast trong app luôn là "ngôn ngữ của app + tiếng Anh", nên mô tả ở mọi nơi nói
"podcast bằng ngôn ngữ của bạn và tiếng Anh".

| Play | App Store | Tên (Play) | Mô tả ngắn (Play) | Từ khoá chính |
|---|---|---|---|---|
| `vi` (mặc định) | `vi` | TSPodcast: Nghe podcast, radio | Nghe podcast tiếng Việt và quốc tế, radio online, kho nhạc mở trong một app. | nghe podcast, podcast tiếng Việt, radio online, nghe đài, hẹn giờ tắt |
| `en-US` | `en-US` | TSPodcast: Podcast Player | Discover trending podcasts by topic, with sleep timer, speed control and resume. | podcast player, podcasts, sleep timer, playback speed |
| `en-GB` | `en-GB` | TSPodcast: Podcast Player | (như en-US) | như en-US, chính tả Anh (favourite, personalise) |
| `es-ES` | `es-ES` | TSPodcast: Escuchar podcasts | Podcasts en tu idioma y en inglés, con temporizador, velocidad y reanudación. | escuchar podcasts, podcasts en español, temporizador |
| `es-419` | `es-MX` | TSPodcast: Escuchar podcasts | (như es-ES) | như es-ES; "en vivo", "audífonos", "Configuración", "reportar" |
| `pt-BR` | `pt-BR` | TSPodcast: Ouvir podcasts | Podcasts no seu idioma e em inglês, com timer de desligamento e velocidade. | ouvir podcasts, podcasts em português, timer |
| `fr-FR` | `fr-FR` | TSPodcast: Lecteur de podcasts | Podcasts dans votre langue et en anglais, avec minuterie de sommeil et vitesse. | lecteur de podcasts, écouter podcasts, minuterie de sommeil |
| `de-DE` | `de-DE` | TSPodcast: Podcasts hören | Podcasts in deiner Sprache und auf Englisch, mit Sleep-Timer und Tempo. | Podcasts hören, Podcast App, Sleep-Timer |
| `it-IT` | `it` | TSPodcast: Ascolta podcast | Podcast nella tua lingua e in inglese, con timer di spegnimento e velocità. | ascolta podcast, podcast in italiano, timer di spegnimento |
| `nl-NL` | `nl-NL` | TSPodcast: Podcasts luisteren | Podcasts in je eigen taal en in het Engels, met slaaptimer en afspeelsnelheid. | podcasts luisteren, Nederlandse podcasts, slaaptimer |
| `sv-SE` | `sv` | TSPodcast: Lyssna på poddar | Poddar på ditt språk och på engelska, med insomningstimer och hastighet. | lyssna på poddar, podd, insomningstimer |
| `pl-PL` | `pl` | TSPodcast: Słuchaj podcastów | Podcasty w Twoim języku i po angielsku, z wyłącznikiem czasowym i prędkością. | słuchaj podcastów, podcasty po polsku, wyłącznik czasowy |
| `tr-TR` | `tr` | TSPodcast: Podcast dinle | Kendi dilinde ve İngilizce podcastler; uyku zamanlayıcısı ve hız ayarı. | podcast dinle, Türkçe podcast, uyku zamanlayıcısı |
| `ja-JP` | `ja` | TSPodcast: ポッドキャストを聴く | 日本語と英語のポッドキャストを、再生速度やスリープタイマーで快適に。 | ポッドキャスト, 聴く, スリープタイマー, 倍速 |
| `ko-KR` | `ko` | TSPodcast: 팟캐스트 듣기 | 한국어와 영어 팟캐스트를 재생 속도, 수면 타이머와 함께 편하게 들으세요. | 팟캐스트, 팟캐스트 듣기, 수면 타이머, 배속 |
| `id` | `id` | TSPodcast: Dengar Podcast | Podcast dalam bahasamu dan bahasa Inggris, dengan timer tidur dan kecepatan. | dengar podcast, podcast Indonesia, timer tidur |
| `hi-IN` | `hi` | TSPodcast: पॉडकास्ट सुनें | हिंदी और अंग्रेज़ी पॉडकास्ट, स्लीप टाइमर और प्लेबैक स्पीड के साथ। | पॉडकास्ट, पॉडकास्ट सुनें, podcast hindi, स्लीप टाइमर |

Tên App Store giống tên Play, trừ `vi` (`TSPodcast: Nghe podcast`, vì bản iOS chưa có radio).
Subtitle App Store là "podcast bằng <ngôn ngữ> và tiếng Anh" (en: `Trending shows, sleep timer`);
promotional text mở đầu bằng câu khẩu hiệu dịch từ "Thế giới trong tai bạn".

**Radio ở thị trường khác Việt Nam.** Tab Radio hiện với mọi ngôn ngữ, nên mô tả Play của mọi
ngôn ngữ có một dòng "Radio Việt Nam: nghe trực tiếp các kênh từ Việt Nam" ở mục "Nhạc và radio"
(mô tả đúng và đủ chức năng). Không đưa "radio" vào tên, mô tả ngắn hay ảnh của các thị trường
này, để người dùng không cài vì tưởng có đài địa phương. Không nêu tên VOV, VOH ngoài listing
tiếng Việt. Bản iOS v1 không có radio nên không nhắc ở đâu.

**Ảnh.** Mỗi listing Play ngoài `vi` dùng bản sao ảnh tiếng Anh (`en-US/images/**`: icon, feature
graphic, 6 ảnh điện thoại); nếu thiếu, Play hiển thị ảnh tiếng Việt của listing mặc định. Ảnh 2
có chú thích "Live radio" và ảnh 1 nói "Podcasts from Vietnam and the world": nên làm bộ ảnh theo
từng ngôn ngữ (chạy `brand/scripts/frame.js` với file examples riêng) trước khi chạy quảng bá ở
các thị trường đó.

**Kiểm tra.** `python fastlane/check_store_metadata.py` đếm ký tự các trường Play (tên 30, mô tả
ngắn 80, mô tả đầy đủ 4.000, changelog 500), đếm byte UTF-8 của keywords App Store (100), và báo
lỗi khi gặp từ về quảng cáo, từ so sánh tuyệt đối, emoji, chữ HOA, tên app khác, "Thời sự/Bản tin",
VOV/VOH ngoài mô tả vi, HTML trong App Store, hoặc nhắc radio/Android trong listing iOS.

### 9.1 Play Console: thêm bản dịch

1. **Tăng trưởng → Trang thông tin trên Cửa hàng → Trang thông tin chính → Quản lý bản dịch →
   Thêm bản dịch của riêng bạn**, chọn 16 ngôn ngữ ở bảng trên (tiếng Việt là ngôn ngữ mặc định).
   Không bật "dịch máy của Google" cho các ngôn ngữ này.
2. Với từng ngôn ngữ: dán `title.txt`, `short_description.txt`, `full_description.txt` trong
   `fastlane/metadata/android/<locale>/`; tải icon, feature graphic và ảnh điện thoại trong
   `images/`. Hoặc đẩy một lần bằng
   `fastlane supply --package_name tss.t.podcast --metadata_path fastlane/metadata/android --skip_upload_apk --skip_upload_aab --skip_upload_changelogs`
   (cần service account như CI; thêm `--skip_upload_images --skip_upload_screenshots` nếu chỉ đổi chữ).
3. Gửi duyệt sau khi 10018 lên production, vì mô tả nói về player mới và 15 ngôn ngữ.
4. "What's new" của 10018: workflow `release-playstore.yml` (r0adkll/upload-google-play) đọc
   `distribution/whatsnew/whatsnew-<locale>`; tên file dùng đúng mã Play ở bảng (`whatsnew-vi`,
   `whatsnew-id`, `whatsnew-hi-IN`…). Lưu ý workflow tự tăng versionCode từ giá trị trong
   `app/build.gradle.kts`, nên nếu chạy CI khi file đang ghi 10018 thì bản tải lên là 10019; khi
   đó đổi tên `changelogs/10018.txt` cho khớp (file `whatsnew-*` không phụ thuộc số bản).

### 9.2 App Store Connect: thêm bản địa hoá

1. **Ứng dụng → App Information → Localizable Information**: thêm 16 ngôn ngữ ngoài tiếng Việt
   (ngôn ngữ chính). Tên và subtitle nằm ở đây; Privacy Policy URL cũng là trường theo ngôn ngữ.
2. **Phiên bản 1.0 → mỗi ngôn ngữ**: dán Description, Keywords, Promotional Text, Support URL
   (`https://podcast-25c93.web.app/`); ảnh chụp iOS làm riêng (mục 12.2 của
   `docs/in-progress/ios/app-store-submission-plan.md`).
3. Hoặc đẩy bằng lane `metadata` (`deliver`, `metadata_path: "fastlane/metadata"` trong `iosApp/`).
   Tên thư mục là mã của deliver: `vi, en-US, en-GB, es-ES, es-MX, pt-BR, fr-FR, de-DE, it, nl-NL,
   sv, pl, tr, ja, ko, id, hi`. `support_url.txt` và `privacy_url.txt` để trong từng thư mục ngôn
   ngữ vì đó là trường theo ngôn ngữ.
4. `release_notes.txt` ("What's New") chỉ dùng từ bản 1.0.1 trở đi: App Store Connect không cho
   nhập What's New cho phiên bản đầu tiên, nên khi đẩy bản 1.0 bằng deliver hãy tạm bỏ các file
   này ra khỏi thư mục (chưa kiểm chứng deliver có tự bỏ qua hay báo lỗi). Nội dung đã viết theo
   kiểu "phiên bản đầu tiên", sửa lại khi có bản 1.0.1.
5. Trước khi nộp: bản iOS phải có giao diện đủ 15 ngôn ngữ (trên `main` hiện `Localizable.xcstrings`
   mới có vi và en), nếu không thì bỏ dòng "App in 15 languages" ở các bản địa hoá hoặc chỉ nộp vi
   và en-US.

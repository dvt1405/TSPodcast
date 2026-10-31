# Google Play: nội dung store listing chuẩn ASO

Cập nhật 2026-11-01. Bộ nội dung này dùng được ngay cho bản đang có trên Play (v3.4.10017). Mục 6
là phần thêm vào khi phát hành bản Android có player mới (tốc độ, hẹn giờ tắt, tua ±, nhớ vị trí).

File nguồn nằm trong `fastlane/metadata/android/<vi-VN|en-US>/`. CI **không** tự đẩy listing
(workflow chỉ đẩy "What's new"), nên phải dán tay vào Play Console theo mục 1.

## 1. Việc cần làm trong Play Console

Play Console → **Tăng trưởng → Trang thông tin trên Cửa hàng → Trang thông tin chính**:

1. Tiếng Việt (vi-VN, ngôn ngữ mặc định): dán `title.txt`, `short_description.txt`, `full_description.txt`.
2. Thay ảnh điện thoại số 2 bằng `vi-VN/images/phoneScreenshots/02.png` (đổi chú thích "Radio VOV, VOH" thành "Radio online").
3. Bản dịch English (en-US): dán 3 file chữ của `en-US`, thay **cả 6** ảnh bằng `en-US/images/phoneScreenshots/01–06.png` (chú thích tiếng Anh, trước đây dùng ảnh tiếng Việt).
4. Lưu, gửi duyệt cùng hoặc tách khỏi bản app (thay đổi listing được duyệt riêng).
5. Ghi ngày đổi vào mục 8 để so số liệu trước/sau.

## 2. Nội dung mới

| Trường | Giới hạn | vi-VN | en-US |
|---|---|---|---|
| Tên ứng dụng | 30 | `TSPodcast: Nghe podcast, radio` (30) | `TSPodcast: Podcasts & Radio` (27) |
| Mô tả ngắn | 80 | `Nghe podcast tiếng Việt và quốc tế, radio online, kho nhạc mở trong một app.` (76) | `Vietnamese and world podcasts, live radio and open music, all in one app.` (73) |
| Mô tả đầy đủ | 4.000 | 1.692 ký tự | 1.675 ký tự |

Thay đổi so với listing cũ và lý do:

| Trước | Sau | Lý do |
|---|---|---|
| Tên vi: `TSPodcast: Podcast & Radio` | `TSPodcast: Nghe podcast, radio` | Tên là trường có trọng số tìm kiếm cao nhất. "nghe podcast" là cách người Việt gõ khi tìm app; dùng đủ 30 ký tự. |
| Mô tả ngắn nhắc "VOV, VOH" và "miễn phí" | Bỏ tên đài và "miễn phí" | Tên đài là thương hiệu của bên khác, chưa có văn bản cho phép (blocker B4); đặt ở mô tả ngắn dễ bị hiểu là app chính thức. "miễn phí" là từ khuyến mại, Google khuyên không dùng ở tên và các trường nổi bật. |
| Mô tả đầy đủ mở đầu bằng 1 câu | 2 câu đầu nói rõ app làm gì, cho ai, "không cần tài khoản" | Người dùng thường chỉ đọc vài câu đầu trước khi bấm "Xem thêm". |
| Tiêu đề mục viết HOA | `<b>Tiêu đề</b>` | Play hiển thị chữ đậm; tránh viết hoa toàn bộ (chính sách Metadata). |
| Thiếu một số tính năng đã có | Thêm Podcast live, "Có thể bạn yêu thích", điều khiển tai nghe Bluetooth, lọc chương trình rác/cờ bạc, không quảng cáo toàn màn hình khi đang nghe | Mô tả đúng và đủ chức năng là yêu cầu của chính sách, đồng thời thêm từ khoá tự nhiên. |
| Không ghi tính độc lập | "TSPodcast là ứng dụng độc lập, không liên kết với các đài phát thanh hay nhà sản xuất podcast." | Giảm rủi ro mạo danh/thương hiệu khi nhắc VOV, VOH trong mô tả. |

Mọi dòng trong mô tả đã đối chiếu với code Android trên `main` ngày 2026-11-01. Không ghi tính
năng chưa có trên Android: duyệt theo chủ đề, tìm kiếm không dấu, tải offline, tốc độ phát (chờ
bản ở mục 6).

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

## 6. Chuẩn bị cho bản Android có player mới

Chỉ dùng **sau khi** bản có tốc độ phát, hẹn giờ tắt, tua −15/+30 và nhớ vị trí nghe đã lên
production. Nếu bản đó thiếu tính năng nào thì bỏ dòng tương ứng.

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
cáo trong app (`docs/plans/seo-web-dashboard.md` §C.4).

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

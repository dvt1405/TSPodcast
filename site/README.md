# Trang giới thiệu TSPodcast (`podcast-25c93.web.app`)

Trang tĩnh giới thiệu ứng dụng TSPodcast, chạy trên Firebase Hosting, site `podcast-25c93`
(project `podcast-25c93`). Trang chỉ giới thiệu sản phẩm; mọi nút "Nghe" dẫn sang bản web
nghe nhạc trên Vercel (`https://tspodcast.vercel.app`), nút tải dẫn sang Google Play.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `index.html` | Trang chủ (trang đích chính cho SEO và quảng cáo) |
| `l/radio.html`, `l/podcast.html` | Biến thể trang đích cho chiến dịch: `noindex`, canonical về trang chủ |
| `privacy.html`, `en/privacy.html` | Chính sách quyền riêng tư (vi, en) |
| `terms.html`, `en/terms.html` | Điều khoản sử dụng (vi, en) |
| `delete-data.html`, `en/delete-data.html` | Hướng dẫn xoá dữ liệu (URL khai trong Data safety của Play) |
| `404.html`, `robots.txt`, `sitemap.xml`, `site.webmanifest`, favicon | |
| `app-ads.txt` | **Không sửa.** AdMob kiểm tra file này; phải giữ nguyên từng byte (LF, xem `.gitattributes`). |
| `assets/` | Font Be Vietnam Pro (subset Latin + tiếng Việt, OFL 1.1), ảnh chụp màn hình webp, logo, ảnh OG |
| `_src/` | Nguồn sinh trang: `build.mjs`, `site.css`, `content/*.html`. Không được deploy. |

Các file `.html` được **sinh ra** từ `_src/` và đã commit sẵn, nên deploy không cần bước build.
Khi sửa nội dung, sửa trong `_src/` rồi chạy:

```bash
node site/_src/build.mjs
```

Script inline CSS vào từng trang, sinh thẻ SEO, JSON-LD, `sitemap.xml`, và kiểm tra độ dài
`title` (≤ 60) và `description` (≤ 155). Trang đích có hai script inline (mỗi cái < 1 KB):
`CAMPAIGN_JS` (tham số chiến dịch) và `REVEAL_JS` (hiệu ứng hiện dần khi cuộn). Sửa một trong hai
thì script in ra hash mới và báo lỗi nếu `script-src` trong Content-Security-Policy ở
`firebase.json` chưa có hash đó.

Mục lục của các trang chính sách được sinh tự động từ các thẻ `<h2 id="...">` trong
`_src/content/*.html`, không viết tay.

Thêm trang đích mới cho chiến dịch: thêm một mục vào `LANDINGS` trong `build.mjs` (đường dẫn
`l/<tên>.html`, tiêu đề, mô tả, ảnh hero, đường dẫn web), rồi chạy lại script.

## Bố cục và hiệu ứng (`_src/site.css`)

- Mọi khoảng cách lấy từ thang `--s-1` … `--s-9` (4/8/12/16/24/32/48/64/96 px). Một khung
  `.wrap` (tối đa 1200 px) với lề `--gutter` (20/32/40 px), nhịp section `--section-y`
  (64/80/96 px). Cỡ chữ dùng `--fs-*` với `clamp()`; tiêu đề có `text-wrap: balance`.
- Trang chính sách: cột chữ tối đa `--measure` (khoảng 70 ký tự/dòng), mục lục dính bên phải
  từ 1024 px.
- Hiệu ứng chỉ đổi `transform` và `opacity`. Phần hiện dần khi cuộn chỉ bật khi script đã chạy
  (`html.js`), nên tắt JavaScript vẫn thấy đủ nội dung. Với `prefers-reduced-motion: reduce`, mọi
  chuyển động bị tắt, chỉ còn mờ dần. Trang chính sách không có hiệu ứng trên phần nội dung.

## Đường dẫn cũ vẫn chạy

`firebase.json` (site `podcast-25c93`) dùng `cleanUrls` và `redirects`:

- `/privacy.html` → 301 `/privacy`; `/privacy-policy`, `/policy` (và bản `.html`) → 301 `/privacy`
- `/terms.html` → 301 `/terms`; `/data-deletion` → 301 `/delete-data`
- `/app-ads.txt` phục vụ nguyên trạng

## Tham số chiến dịch

Mọi nút hành động có `id` và `data-cta` cố định (`hero-play`, `hero-web`, `header-play`,
`free-play`, `footer-play`, `footer-web`, `web-trending`, `web-radio`, `web-search`, ...).
Một đoạn script inline (< 1 KB, không thư viện ngoài) chép `utm_source`, `utm_medium`,
`utm_campaign`, `utm_content`, `utm_term`, `gclid` từ URL trang vào:

- link Google Play, dạng tham số `referrer` (chuẩn Play Install Referrer);
- link `tspodcast.vercel.app`, dạng query bình thường.

Mặc định khi không có: `utm_source=website`, `utm_medium=landing` (đã có sẵn trong `href`,
nên không có JavaScript vẫn chạy). Trong `<head>` có chỗ trống được đánh dấu cho thẻ Google /
consent mode sau này. Hiện **không** có mã theo dõi nào; khi thêm phải cập nhật CSP và
mục "Trang web này" trong chính sách quyền riêng tư.

## Deploy

```bash
firebase deploy --only hosting:podcast-25c93 --project podcast-25c93
```

Lệnh này chỉ deploy site này; site `tspodcast` (bản Next.js trong `web/`) không bị đụng tới.

Kiểm tra cục bộ: `firebase emulators:start --only hosting:podcast-25c93`. Lưu ý trên Windows,
emulator (superstatic) bỏ qua `redirects` và `headers` do lỗi dấu `\` trong đường dẫn glob;
trên Firebase Hosting thật thì không bị.

## Quy tắc nội dung (bắt buộc)

- **Không nhắc tới quảng cáo** trong nội dung giới thiệu sản phẩm ("quảng cáo", "ads", AdMob...).
  Chỉ nói về tính năng và việc app **miễn phí**. Riêng chính sách quyền riêng tư thì phải
  khai báo SDK quảng cáo đúng sự thật.
- **Không dùng từ so sánh nhất, xếp hạng**: "nhất", "số 1", "hàng đầu", "best", "#1", "top"
  (Luật Quảng cáo Việt Nam và chính sách Google Play).
- **Chỉ nói tính năng có thật** trong app Android: podcast thịnh hành ưu tiên tiếng Việt (Podcast
  Index), hơn 100 chủ đề cho "Có thể bạn yêu thích", tìm kiếm, bộ lọc nhạc và kho nhạc mở
  (Podcasting 2.0), podcast live, radio online / kênh phát thanh trực tiếp, phát nền, điều khiển
  màn hình khoá và tai nghe Bluetooth, trình phát thu nhỏ, yêu thích, tốc độ 0,5×–3,0× nhớ theo
  chương trình, hẹn giờ tắt, lùi 15 giây / tới 30 giây, nghe tiếp, ẩn nội dung người lớn, ẩn
  chương trình, báo cáo nội dung, không cần tài khoản, giao diện tiếng Việt.
  **Không** nói tải offline, tính năng AI, hay có trên iOS (kể cả "sắp có").
- Tên đài (VOV, VOH) không đưa lên tiêu đề; nhắc trong thân bài thì được.
- Luôn ghi rõ TSPodcast là ứng dụng độc lập, không liên kết với đài phát thanh hay nhà sản xuất
  podcast; nội dung thuộc về người tạo; dữ liệu thư mục từ Podcast Index.
- Tagline: "Thế giới trong tai bạn". Màu, logo, font: xem `brand/BRAND.md`.

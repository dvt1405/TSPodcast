# Kế hoạch: Website chuẩn SEO, dashboard tăng trưởng và chiến dịch quảng cáo

> Ticket: TSP-13 · Ngày lập: 2026-10-29 · Trạng thái: **Bản nháp chờ owner duyệt**
> Liên quan: `docs/plans/admob-migration.md` (doanh thu quảng cáo in-app, `app-ads.txt`, consent).
> Website thực tế đang được một agent khác dựng trong `web/` (Next.js). Tài liệu này là **hợp đồng yêu cầu** (route, SEO, deep link, tracking) mà `web/` cần đáp ứng, **không** áp đặt chi tiết implementation. Nếu `web/` đã chọn khác (vd. cấu trúc URL), ưu tiên giữ cái đã có và cập nhật lại tài liệu này.

---

## 0. Tóm tắt (TL;DR)

- **(A) Website công khai** `https://<domain>`: Next.js App Router (16.x), trang podcast / tập / danh mục / thịnh hành render server-side (SSG + ISR), có JSON-LD `PodcastSeries`/`PodcastEpisode`, sitemap phân mảnh, hreflang `vi`/`en`, ngân sách Core Web Vitals (LCP ≤ 2.5s, INP ≤ 200ms, CLS ≤ 0.1 ở p75). Website cũng là nơi đặt **App Links** (`assetlinks.json`) và **`app-ads.txt`** (bắt buộc cho AdMob).
- **(B) Dashboard nội bộ**: kho dữ liệu là BigQuery (Firebase/GA4 export, Google Ads Data Transfer, AdMob API, Play Console export, Search Console bulk export), mô hình hóa bằng Dataform/dbt, hiển thị bằng Looker Studio (v1, miễn phí) rồi trang `/internal` (v2). KPI gồm installs, CPI/CAC, ROAS D7/D30, ARPDAU, retention D1/D7/D30, LTV.
- **(C) Chiến dịch**: ASO trước, sau đó Google App Campaigns (Install rồi chuyển sang In-app action), Meta/TikTok ở giai đoạn sau và có điều kiện. Ngân sách chia theo phase, có ngưỡng dừng.
- Chi phí hạ tầng ước tính: **~$0–45/tháng** ở quy mô < 50k MAU (không tính ngân sách quảng cáo).

---

## A. Website công khai chuẩn SEO

### A.1 Mục tiêu

1. Thu traffic organic cho truy vấn "podcast <chủ đề>", "nghe podcast <tên show>", "<tên tập>", rồi chuyển đổi sang cài app (Play Store) hoặc mở app (App Links).
2. Làm "developer website" cho Play listing. Đây là điều kiện để AdMob verify `app-ads.txt`.
3. Làm landing page có thể đo lường cho chiến dịch (UTM → Play Install Referrer).

### A.2 Nguồn dữ liệu và ràng buộc

- Dữ liệu podcast lấy từ **PodcastIndex API** (giống app: `coreApi/src/main/java/tss/t/coreapi/API.kt`, các endpoint `podcasts/trending`, `podcasts/byfeedid`, `episodes/byfeedid`, `categories/list`). API key/secret **chỉ dùng phía server** (Route Handler / Server Component), không bao giờ gửi xuống client.
- **Phải hiển thị attribution PodcastIndex** trên mọi trang có dữ liệu (ToS mục 7.2; app đã làm ở `DiscoverPodcastsScreen` / `ITEM_KEY_ATTRIBUTION`).
- Áp dụng **blacklist** sẵn có (`podcasts/src/main/java/tss/t/podcasts/BlacklistRepositoryImpl.kt`, đọc từ Remote Config) cho web để đồng nhất với app. Cần có trang/địa chỉ liên hệ gỡ nội dung (DMCA/takedown).
- Cache server-side (Next fetch cache / ISR) để tôn trọng rate limit của PodcastIndex.

### A.3 Cấu trúc URL đề xuất

| Route | Nội dung | Render | Index? |
|---|---|---|---|
| `/` (vi), `/en` | Trang chủ: trending, danh mục, CTA tải app | SSG + ISR 1h | Có |
| `/podcast/[feedId]-[slug]` | Podcast: mô tả, artwork, danh sách tập (phân trang), podcast liên quan, nút "Nghe trên app" | SSG top N + ISR on-demand (revalidate 6–24h) | Có, khi đạt ngưỡng chất lượng (A.5) |
| `/podcast/[feedId]-[slug]/tap/[episodeId]-[slug]` (en: `/en/podcast/…/episode/…`) | Tập: tiêu đề, ngày, thời lượng, show notes đã sanitize, transcript nếu feed có `podcast:transcript`, player HTML5 lazy | ISR on-demand | Có, khi đạt ngưỡng |
| `/danh-muc/[slug]` (en: `/en/category/[slug]`) | Danh mục PodcastIndex | SSG + ISR 6h | Có |
| `/thinh-hanh` (en: `/en/trending`), `/nhac` (Kho nhạc, `medium=music`) | Bảng xếp hạng | ISR 1h | Có |
| `/radio`, `/radio/[stationId]-[slug]` | Đài radio (từ `coreRadio`) | ISR | Có, nếu có quyền hiển thị |
| `/tim-kiem?q=` | Kết quả tìm kiếm | SSR/CSR | **`noindex, follow`** |
| `/tai-app` (en: `/en/app`) | Landing cài app (dùng cho ads) | SSG | Có |
| `/gioi-thieu`, `/chinh-sach-bao-mat`, `/dieu-khoan`, `/lien-he` | Trang tĩnh. Privacy policy phải khớp Data safety (xem `admob-migration.md` §7) | SSG | Có |
| `/app-ads.txt`, `/robots.txt`, `/sitemap.xml`, `/.well-known/assetlinks.json`, `/.well-known/apple-app-site-association` | File hạ tầng | static / route handler | — |

**Route đã triển khai trong `web/` (TSP-14, 2026-10-29).** Bản đầu dùng route tiếng Anh theo brief của ticket, khác bảng đề xuất ở trên:

| Đề xuất | Đã làm | Ghi chú |
|---|---|---|
| `/podcast/[feedId]-[slug]` | `/podcast/[feedId]-[slug]` | Giống đề xuất. Slug sai thì chuyển hướng **308** (Next `permanentRedirect`; Google xử lý như 301). Revalidate 1h, ngắn hơn đề xuất 6–24h, để bám PodcastIndex ToS 5.5(1). |
| `/podcast/…/tap/[episodeId]-[slug]` | `/episode/[episodeId]-[slug]` | URL phẳng. Tập vẫn liên kết tới series qua `partOfSeries.@id`. |
| `/danh-muc/[slug]` | `/category/[slug]` | |
| `/thinh-hanh`, `/nhac` | `/trending`, Kho nhạc ở `/search?mode=music` | Kho nhạc là thư viện, không xếp hạng (commit `dddd818`), nên chưa có trang riêng để index. |
| `/tim-kiem?q=` | `/search?q=&mode=podcast\|music\|ai` | `noindex, follow`. robots chặn `/search` và `/api/`. |
| `/radio/[stationId]-[slug]` | Chỉ có `/radio` | Chưa có trang riêng cho từng đài. |
| `/en/...` | Chưa có | Từ điển `en` đã sẵn. Hiện chọn ngôn ngữ theo deployment (`NEXT_PUBLIC_SITE_LOCALE`). |
| `/tai-app`, trang tĩnh | Chưa có | |

Muốn đổi sang slug tiếng Việt thì đổi tên thư mục route và thêm redirect 301 từ đường dẫn cũ.

- Slug: chuyển tiếng Việt không dấu, chỉ `[a-z0-9-]`. **ID là nguồn chân lý**: nếu slug sai thì 301 về URL canonical.
- Không dùng query string cho nội dung cần index (phân trang tập: `?page=2` để `index`, nhưng canonical trỏ về chính nó, không trỏ về trang 1).

### A.4 i18n và hreflang

- Thị trường chính là Việt Nam, nên **`vi` ở root không prefix**, `en` dưới `/en`. `x-default` trỏ về bản `vi`. **Không** auto-redirect theo `Accept-Language` vì Googlebot crawl chủ yếu từ US.
- Chỉ đặt hreflang cho trang có **UI và nội dung thực sự dịch**: trang chủ, danh mục, thịnh hành, landing app, trang tĩnh.
- Trang **podcast / tập**: nội dung là của feed (một ngôn ngữ). Nên **chỉ có một URL canonical** theo ngôn ngữ feed (`inLanguage`): podcast tiếng Việt nằm ở root, tiếng Anh ở `/en`. **Không** tạo bản song song chỉ khác UI, vì dễ bị coi là trùng lặp hoặc thin content.
- Khai báo bằng `alternates.languages` trong `generateMetadata` (Next.js) **hoặc** `xhtml:link` trong sitemap. Chọn một cách và giữ nhất quán.

### A.5 Chất lượng nội dung và crawl budget

PodcastIndex có hàng triệu feed. Index hết sẽ sinh hàng loạt trang mỏng hoặc trùng với Apple/Spotify. Quy tắc:

- **Ngưỡng index podcast:** `language ∈ {vi, en}` **và** ≥ 3 tập **và** có tập trong 12 tháng gần nhất **và** không bị blacklist / `explicit` (tùy chính sách). Trang không đạt vẫn render để người dùng xem được, nhưng gắn `noindex, follow` và không đưa vào sitemap.
- **Ngưỡng index tập:** mô tả ≥ 200 ký tự sau khi bỏ HTML, hoặc có transcript.
- **Giá trị thêm** để tránh nội dung sao chép: danh sách tập có cấu trúc, podcast liên quan cùng danh mục, thống kê (tần suất ra tập, thời lượng trung bình), breadcrumb, mục "Nghe tiếp trên app". Về sau có thể thêm tóm tắt tiếng Việt (cần quyết định về chi phí và bản quyền).
- `robots.txt`: chặn `/tim-kiem`, `/api/`, `/_next/` không cần thiết. Khai báo `Sitemap:`.

### A.6 Structured data (JSON-LD)

> Lưu ý thực tế: Google **không còn** rich result hay carousel riêng cho podcast (Google Podcasts đã đóng, carousel podcast trên Search bị gỡ: https://www.seroundtable.com/amp/google-confirms-podcast-carousels-going-away-february-13-34870.html). JSON-LD vẫn nên có để các công cụ tìm kiếm và AI crawler hiểu entity, nhưng **đừng kỳ vọng rich snippet**. Rich result còn hiệu lực và nên làm: `BreadcrumbList`, `Organization`/logo. Không dùng `SearchAction` cho sitelinks search box (Google đã bỏ).

```tsx
// web/app/podcast/[slug]/page.tsx (phác thảo)
const series = {
  "@context": "https://schema.org",
  "@type": "PodcastSeries",
  "@id": `${SITE}/podcast/${feed.id}-${slug}#series`,
  name: feed.title,
  description: plain(feed.description).slice(0, 500),
  url: `${SITE}/podcast/${feed.id}-${slug}`,
  image: feed.artwork,
  webFeed: feed.url,
  inLanguage: feed.language,
  author: { "@type": "Person", name: feed.author },
  genre: Object.values(feed.categories ?? {}),
  potentialAction: { "@type": "ListenAction", target: `${SITE}/podcast/${feed.id}-${slug}` },
};
const episode = {
  "@context": "https://schema.org",
  "@type": "PodcastEpisode",
  name: ep.title,
  url: epUrl,
  datePublished: new Date(ep.datePublished * 1000).toISOString(),
  timeRequired: isoDuration(ep.duration),               // "PT45M12S"
  description: plain(ep.description).slice(0, 500),
  associatedMedia: { "@type": "AudioObject", contentUrl: ep.enclosureUrl, encodingFormat: ep.enclosureType },
  partOfSeries: { "@id": `${SITE}/podcast/${feed.id}-${slug}#series` },
};
// <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(series) }} />
```

Thêm `BreadcrumbList` trên mọi trang con, `Organization` + `WebSite` ở layout, và `MobileApplication` trên `/tai-app` (`operatingSystem: "ANDROID"`, `applicationCategory: "MultimediaApplication"`, `offers.price: 0`). Validate bằng Rich Results Test và Schema Markup Validator trong CI (snapshot test).

### A.7 Sitemap

- `sitemap.xml` là **sitemap index**, trỏ tới các file con: `sitemap-static.xml`, `sitemap-categories.xml`, `sitemap-podcasts-{n}.xml`, `sitemap-episodes-{n}.xml`. Mỗi file ≤ 50.000 URL / 50 MB (dùng `generateSitemaps` của Next.js).
- `lastmod` = thời điểm nội dung thực sự đổi (`newestItemPubdate` của feed, `datePublished` của tập). **Không** đặt `now()`.
- Chỉ đưa URL **đạt ngưỡng index** (A.5) và trả về 200 canonical.
- Submit trong Google Search Console (Domain property) và Bing Webmaster Tools. Bật IndexNow cho Bing/Yandex (tùy chọn).

### A.8 Core Web Vitals và ngân sách hiệu năng

Ngưỡng "Good" ở p75 (field data, tách mobile/desktop): **LCP ≤ 2.5s, INP ≤ 200ms, CLS ≤ 0.1**. INP thay FID từ 12/03/2024 (https://corewebvitals.io/core-web-vitals, https://web.dev/articles/vitals).

| Ngân sách (mobile, 4G chậm) | Mục tiêu |
|---|---|
| LCP | ≤ 2.0s (dư 0.5s so với ngưỡng) |
| INP | ≤ 150ms |
| CLS | ≤ 0.05 |
| TTFB | ≤ 600ms (ISR/edge cache) |
| JS first-load trang podcast/tập | ≤ 130 KB gzip |
| Ảnh LCP (artwork) | `next/image` có `priority`, `sizes` chuẩn, AVIF/WebP, ≤ 60 KB |

- Artwork từ host bất kỳ: dùng loader ảnh (Next Image Optimization hoặc Cloudflare Images), giới hạn `remotePatterns`, luôn đặt `width`/`height` để tránh CLS.
- Font: `next/font` có **subset `vietnamese`**, `display: swap`.
- Player audio: lazy-load khi user tương tác, không autoplay.
- Smart banner (A.10) phải giữ chỗ cố định hoặc là overlay không đẩy layout, để không gây CLS.
- Đo lường: thư viện `web-vitals` gửi lên GA4, CrUX, Search Console CWV report, và Lighthouse CI chặn PR nếu vượt ngân sách.

### A.9 Open Graph, social, `app-ads.txt`

- `generateMetadata`: `title` (≤ 60 ký tự, dạng "<Tên podcast> | Nghe podcast trên TSPodcast"), `description` (≤ 155 ký tự), `alternates.canonical`, `openGraph` (`type: "website"`, trang tập dùng `"music.song"` hoặc `"website"`, `og:audio` = enclosure URL nếu là https), `twitter: summary_large_image`.
- Ảnh OG động: `opengraph-image.tsx` (`ImageResponse`) ghép artwork + tên show + logo, 1200×630.
- **`/app-ads.txt`**: route handler trả `text/plain`. Nội dung lấy từ biến môi trường hoặc file (`google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`, copy đúng snippet AdMob). Domain này phải trùng với "Website" trong Play listing (https://support.google.com/admob/answer/9363762).

### A.10 Deep link: App Links, Universal Links, smart app banner

**Android App Links** (app chưa có: manifest hiện chỉ có scheme nội bộ `tss://ts.podcast` và không có `BROWSABLE`, xem `app/src/main/AndroidManifest.xml:41-51`).

```xml
<!-- app/src/main/AndroidManifest.xml (thay đổi trong ticket riêng, KHÔNG nằm trong commit này) -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="<domain>" />
    <data android:pathPrefix="/podcast/" />
    <data android:pathPrefix="/episode/" />   <!-- route tập thực tế của web/ (A.3) -->
    <data android:pathPrefix="/en/podcast/" />
</intent-filter>
```

```json
// web: /.well-known/assetlinks.json  (Content-Type: application/json, không redirect)
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": { "namespace": "android_app", "package_name": "tss.t.podcast",
              "sha256_cert_fingerprints": ["<SHA-256 của Play App Signing key>", "<SHA-256 upload key (cho internal/debug)>"] }
}]
```

- App phải route `https://<domain>/podcast/{feedId}-…` → `TSRouter.PodcastDetail`, `/episode/{episodeId}-…` → Player (`app/src/main/java/tss/t/podcast/ui/navigations/TSNavGraph.kt`). Trên web và app nên dùng chung một parser ID.
- Theo `admob-migration.md` §2.1: mở app từ deep link thì **không** hiện App Open ad.

**iOS:** hiện **không có app iOS** (repo chỉ có Android, KMP còn đang là kế hoạch, xem `KMP_MIGRATION_PLAN.md`). Khi có iOS: thêm `/.well-known/apple-app-site-association` (`applinks.details[].appIDs`, `components`) và thẻ Smart App Banner của Safari `<meta name="apple-itunes-app" content="app-id=…, app-argument=…">`.

**Smart banner cho Android:** Chrome không có banner native kiểu Safari. Dùng banner tự làm, gọn (thanh mỏng ở đầu trang, đóng được, nhớ trạng thái đóng 14 ngày). **Không** dùng interstitial che nội dung vì Google phạt intrusive interstitials trên mobile. Nút trỏ tới:
`https://play.google.com/store/apps/details?id=tss.t.podcast&referrer=utm_source%3Dweb%26utm_medium%3Dsmart_banner%26utm_campaign%3Dseo%26utm_content%3Dpodcast_<feedId>`

**Deferred deep link:** Firebase Dynamic Links đã ngừng hoạt động từ 25/08/2025 (https://blog.pragmaticengineer.com/google-shutting-down-firebase-dynamic-links/). Thay bằng: trong `referrer` gắn thêm `deeplink%3D%2Fpodcast%2F<feedId>`. Lần mở đầu tiên app đọc qua **Play Install Referrer API** (`com.android.installreferrer:installreferrer:2.2`, https://developer.android.com/google/play/installreferrer/library) rồi điều hướng tới podcast đó sau onboarding. iOS (sau này) cần MMP hoặc giải pháp riêng.

### A.11 Analytics web

- Một **GA4 property** chung cho app Android (Firebase) + web stream, để có funnel web → install.
- **Consent Mode v2** + CMP cho EEA (Google yêu cầu CMP được chứng nhận cho EEA/UK). VN: banner consent theo PDP Law 2026 (xem `admob-migration.md` §5).
- Event web: `view_podcast`, `view_episode`, `play_preview`, `click_install` (kèm `utm_*` + `feed_id`), `click_open_app`.
- Search Console: Domain property, bật **bulk data export sang BigQuery**.

### A.12 Hosting và chi phí

| Lựa chọn | Ưu | Nhược | Chi phí ước tính |
|---|---|---|---|
| **Vercel Pro** | Hỗ trợ Next.js tốt nhất (ISR, OG image, Image Optimization) | Gói Hobby không cho dùng thương mại; vượt quota thì tính thêm | ~$20/user/tháng + phần vượt |
| Firebase App Hosting (Blaze) | Cùng project Firebase/GCP với app và BigQuery, IAM thống nhất | Hỗ trợ Next.js chưa đủ mọi tính năng (cần kiểm tra ISR on-demand) | Pay-as-you-go, thường < $10/tháng ở traffic thấp |
| Cloudflare (OpenNext) | Edge rẻ, CDN mạnh ở châu Á | Cấu hình phức tạp hơn | ~$5/tháng (Workers Paid) |

Khuyến nghị: **Vercel Pro** cho giai đoạn đầu để ra nhanh; cân nhắc Firebase App Hosting khi muốn gom về GCP. Chọn theo stack mà `web/` đang dùng. Domain khoảng $10–40/năm.

### A.13 Checklist tương thích cho `web/` (agent khác)

Trạng thái sau TSP-14 (2026-10-29):

- [x] Next.js **App Router** (16.x là bản stable hiện hành; kiểm tra bằng `npm view next version` trước khi pin), dữ liệu lấy trong Server Components / Route Handlers. *(Next 16.4.3.)*
- [x] Route theo A.3. Nếu khác thì cập nhật bảng A.3 và intent-filter ở A.10 cho khớp. *(Khác: đã ghi bảng đối chiếu ở A.3 và thêm `/episode/` vào A.10.)*
- [ ] `generateMetadata` có canonical, hreflang (A.4), OG (A.9). Có `sitemap.ts` (+ `generateSitemaps`) và `robots.ts`. *(Đã có canonical, OG, `sitemap.ts` (một file, chưa dùng `generateSitemaps`) và `robots.ts`. Còn thiếu hreflang và `/en`.)*
- [x] JSON-LD (A.6) render phía server. *(PodcastSeries có `@id` và ListenAction; PodcastEpisode dùng AudioObject; BreadcrumbList; Organization và WebSite; không dùng SearchAction.)*
- [x] `/app-ads.txt`, `/.well-known/assetlinks.json` phục vụ trực tiếp với HTTP 200, không redirect. *(Lấy từ env `APP_ADS_TXT` / `ANDROID_CERT_SHA256`. Chưa cấu hình thì trả 404.)*
- [x] Attribution PodcastIndex trên mọi trang dữ liệu. Áp dụng blacklist.
- [x] Nút cài app có UTM + referrer theo A.10. *(Có `deeplink=/podcast/<id>`. Banner overlay, ẩn 14 ngày sau khi đóng.)*
- [ ] Lighthouse CI + `web-vitals` gửi lên GA4.
- [ ] Route nội bộ `/internal/*` (nếu làm dashboard v2 trong cùng app) đặt sau auth (B.5) với `noindex`.

---

## B. Dashboard nội bộ cho chiến dịch và tăng trưởng

### B.1 Nguồn dữ liệu

| Nguồn | Cách lấy | Tần suất | Ghi chú |
|---|---|---|---|
| Firebase Analytics (app) + GA4 web | **BigQuery export** (daily + streaming) | Streaming / ngày | Property chuẩn giới hạn **1 triệu event/ngày** cho daily export; streaming không giới hạn (https://firebase.blog/posts/2022/12/bigquery-and-firebase-integration). Spark dùng BigQuery sandbox (bảng hết hạn sau 60 ngày), nên **cần Blaze** cho dữ liệu lâu dài. |
| Crashlytics | BigQuery export | Ngày | Crash-free theo phiên bản / chiến dịch |
| AdMob | **AdMob API v1** `accounts.networkReport:generate` (sau này thêm `mediationReport:generate` khi có mediation) qua Cloud Run job + Cloud Scheduler | Ngày (D-1, cập nhật lại 3 ngày gần nhất) | Timezone chỉ có giờ mặc định của account hoặc `America/Los_Angeles` (https://developers.google.com/admob/api/v1/reporting). Ngoài ra `ad_impression` kèm value đã có trong Firebase export khi link AdMob ↔ Firebase. |
| Google Ads | **BigQuery Data Transfer Service – Google Ads** | Ngày | Miễn phí connector, chỉ trả phí lưu trữ và query |
| Play Console | Báo cáo thống kê (installs, uninstalls, ratings, crashes) tự export ra **Cloud Storage bucket** → load vào BigQuery. Vitals qua **Play Developer Reporting API** | Ngày / tháng | Installs "chính thức" lấy ở đây, `first_open` lấy từ Firebase |
| Search Console | **Bulk data export → BigQuery** (chính thức) | Ngày | Click/impression organic theo trang và query |
| App Store Connect | Analytics Reports API | Ngày | **Khi có app iOS** |
| Meta Ads / TikTok Ads | Marketing API qua connector (Airbyte OSS tự host trên Cloud Run, hoặc Supermetrics/Windsor ~$30–100/tháng) | Ngày | Chỉ khi chạy hai kênh này |

### B.2 KPI và định nghĩa (dùng chung cho mọi báo cáo)

| KPI | Định nghĩa | Nguồn |
|---|---|---|
| Installs | Play Console "first-time installers"; đối chiếu `first_open` (Firebase) | Play / Firebase |
| CPI | Chi phí / installs được attribute cho kênh | Google Ads / Meta / TikTok + Firebase |
| CAC | Chi phí / **người dùng kích hoạt** (định nghĩa kích hoạt = `first_open` + nghe ≥ 30s trong 24h, event `podcast_play_30s`) | |
| ARPDAU | (Doanh thu ad + IAP nếu có) / DAU | Firebase `ad_impression.value` + AdMob API |
| ARPU Dn (cohort) | Doanh thu luỹ kế n ngày đầu / số user của cohort | Firebase |
| ROAS D7 / D30 | Doanh thu cohort D7 (D30) / chi phí tạo ra cohort đó | Ads + Firebase |
| Retention D1/D7/D30 | % user của cohort `first_open` có `user_engagement` vào **đúng** ngày n (classic) | Firebase |
| LTV (dự báo) | Σ ARPDAU_t × retention_t. Fit đường retention dạng power-law từ D1/D7/D30 để ngoại suy D180 | Firebase |
| Phút nghe / DAU | Tổng thời lượng phát / DAU (event player `listen_progress`) | Firebase |
| eCPM, fill rate, impressions/DAU | Theo placement/format | AdMob API |
| Organic search | Clicks, impressions, CTR, vị trí; số trang được index | Search Console |
| Web → install CVR | `click_install` (web) / installs có `utm_source=web` | GA4 + referrer |
| Chất lượng | Crash-free users, ANR rate, rating 7 ngày | Crashlytics / Play |

Kiểm tra event app trước khi chạy dashboard: hiện `TSAnalytics` (`sharedFirebase/src/main/java/tss/t/sharedfirebase/TSAnalytics.kt`) mới log screen và event ad. Cần bổ sung `podcast_play_30s`, `listen_progress` (mỗi 60s, có sampling), `subscribe_podcast` (yêu thích), `onboarding_complete`, `share`. Đánh dấu `podcast_play_30s` là **conversion** trong GA4/Firebase để import vào Google Ads.

### B.3 Attribution

- **Google Ads App Campaigns (Android):** link Firebase ↔ Google Ads và import conversion `first_open` + `podcast_play_30s`. Firebase SDK tự đọc Play Install Referrer để gán `firebase_campaign`. Không cần MMP cho kênh Google.
- **UTM / referrer** cho mọi link không phải từ Ads (web, social, KOL, email): `…/details?id=tss.t.podcast&referrer=utm_source%3D…%26utm_medium%3D…%26utm_campaign%3D…`. Có thể kèm `deeplink=` (A.10).
- **Play Install Referrer:** `installreferrer:2.2`. Đọc một lần ở lần mở đầu, lưu `utm_*` vào user property, gửi event `install_referrer_received`.
- **Meta / TikTok:** để tối ưu theo install hoặc in-app event, cần SDK của họ hoặc MMP. Các phương án:
  1. Thêm lại **chỉ** `com.facebook.android:facebook-core` (không bản full), tắt `AutoLogAppEventsEnabled`, chỉ bật sau khi có consent, log một vài event chọn lọc. TikTok: TikTok Business SDK theo cách tương tự.
  2. **MMP** (AppsFlyer / Adjust / Singular / Branch): đo được đa kênh, có deferred deep link cho iOS, nhưng tốn ~$0.03–0.07 mỗi install có attribution hoặc theo gói. Hợp lý khi chi tiêu ≥ ~$3k/tháng trên ≥ 2 kênh.
  - Khuyến nghị: **chưa** chạy Meta/TikTok ở Phase 1–2. Khi chạy thì dùng phương án (1) cho chi phí thấp.
- **iOS (tương lai):** SKAdNetwork + **AdAttributionKit** (Apple, iOS 17.4+, chạy song song với SKAN). Google dùng ICM qua Firebase SDK (https://developer-mdn.apple.com/app-store/ad-attribution/, https://www.singular.net/blog/adattributionkit-the-new-skadnetwork/).
- **Privacy Sandbox trên Android:** một số nguồn cho rằng Attribution Reporting API trên Android đang bị loại dần (chưa có xác nhận chính thức), nên **không đầu tư** vào nó.

### B.4 Mô hình dữ liệu

```
raw (export tự động)              staging (Dataform/dbt)            marts (dashboard đọc)
analytics_<id>.events_*     ->    stg_events (flatten params)   ->  fct_daily_kpi (date, channel, campaign, installs, spend, cpi, dau, arpdau)
google_ads.*                ->    stg_ads_spend                 ->  fct_cohort_retention (cohort_date, channel, d1, d7, d30)
admob_network_report        ->    stg_admob_revenue             ->  fct_cohort_revenue (cohort_date, channel, arpu_d7, arpu_d30, roas_d7, roas_d30)
play_stats (GCS)            ->    stg_play_installs             ->  fct_ad_placement (date, placement, impressions, ecpm, revenue)
searchconsole.*             ->    stg_gsc                       ->  fct_seo (date, page_type, clicks, impressions, ctr, position)
```

Dùng **Dataform** (có sẵn trong BigQuery, miễn phí) hoặc dbt Core chạy trên Cloud Run job. Chạy theo lịch hằng ngày lúc 06:00 ICT, sau khi các export D-1 đã về.

### B.5 Giao diện dashboard

- **v1 (tuần 1–3): Looker Studio** (miễn phí) đọc các bảng `marts.*`. Năm trang: Tổng quan · Chiến dịch (spend/CPI/ROAS theo kênh và campaign) · Cohort (retention + ARPU) · Quảng cáo in-app (placement/eCPM) · SEO (Search Console + web → install).
- **v2 (khi cần thao tác hoặc alert): trang `/internal`** trong `web/`, hoặc app Next.js riêng. Đọc BigQuery bằng service account phía server. Auth bằng Google OAuth chỉ cho phép email trong allowlist (hoặc Google Cloud IAP nếu host trên GCP). Thêm `noindex` và chặn trong `robots.txt`. Alert qua Cloud Monitoring hoặc Slack khi CPI vượt 130% mục tiêu, ROAS D7 giảm 20%, hoặc crash-free < 99.5%.

### B.6 Chi phí hạ tầng dữ liệu (ước tính, < 50k MAU)

| Hạng mục | Ước tính/tháng |
|---|---|
| Firebase Blaze (Analytics/Crashlytics vẫn miễn phí; chỉ trả phần BigQuery) | $0 |
| BigQuery storage (~5–20 GB) | < $1 |
| BigQuery query (Looker Studio + Dataform, có partition + cluster) | $0–15 (1 TiB/tháng miễn phí, sau đó tính theo TiB, xem bảng giá hiện hành) |
| Cloud Run jobs + Scheduler (AdMob API, Play GCS loader) | ~$0 (nằm trong free tier) |
| Looker Studio | $0 |
| Connector Meta/TikTok (nếu dùng SaaS) | $0–100 |
| Hosting web (A.12) | $5–20 |
| **Tổng** | **~$5–45** (chưa tính connector SaaS) |

Đặt **budget alert GCP** ở mức $50/tháng và giới hạn bytes billed theo từng query.

---

## C. Kế hoạch chiến dịch quảng cáo và ASO

### C.0 Điều kiện tiên quyết (không chạy ads trước khi xong)

1. Bản app sau Phase 0–2 của `admob-migration.md` (không còn SDK rác, crash-free ≥ 99.5%).
2. Event conversion (`podcast_play_30s`) đã chạy ổn ≥ 7 ngày. Firebase ↔ Google Ads ↔ AdMob đã link.
3. Website + `/tai-app` + `app-ads.txt` + privacy policy đã online.
4. Dashboard v1 đã có số liệu baseline organic.

### C.1 ASO (làm trước và chạy song song)

- **Tiêu đề (≤ 30 ký tự)**: vd. "TSPodcast: Podcast & Radio". Short description (≤ 80 ký tự) chứa từ khóa "nghe podcast", "radio online", "kho nhạc". Long description có cấu trúc, lặp từ khóa tự nhiên.
- Screenshot có chú thích tiếng Việt và tiếng Anh, feature graphic, video 30s.
- **Store listing experiments** (Play Console): A/B icon, screenshot đầu, short description. Mỗi test ≥ 7 ngày.
- **Custom store listings** cho từng chiến dịch hoặc quốc gia. Trả lời review dưới 48h.
- Lưu ý: chỉ số ANR/crash ảnh hưởng tới khả năng được hiển thị trên Play.

### C.2 Kênh

| Kênh | Vai trò | Thời điểm |
|---|---|---|
| **Google App Campaigns – Install (ACi)** | Lấy volume, học thuật toán | Phase 1 |
| **Google App Campaigns – In-app actions (ACe/tCPA)** theo `podcast_play_30s` | Tối ưu chất lượng user | Phase 2, khi có ≥ 10 conversion/ngày |
| **Google App Campaigns – tROAS** | Tối ưu doanh thu ad | Phase 3, khi đủ dữ liệu doanh thu theo cohort |
| **Meta (FB/IG) App Promotion** | Mở rộng audience, creative video ngắn | Phase 3, có điều kiện (B.3) |
| **TikTok Ads (App)** | Creative native, nhóm 18–30 | Phase 3, có điều kiện |
| **SEO / content** | Kênh dài hạn, CAC ≈ 0 | Liên tục từ Phase 0 |
| Cộng đồng / KOL podcast | Link UTM tới `/tai-app` | Thử nghiệm nhỏ |

Quy tắc ngân sách của Google: với chiến dịch Install, ngân sách ngày **≥ 50× CPI mục tiêu**; với in-app action, **≥ 10× (đến 15×) CPA mục tiêu** (https://support.google.com/google-ads/answer/6167156, https://support.google.com/google-ads/answer/9234105). Không chỉnh mục tiêu hoặc ngân sách quá 20% mỗi lần, và không chỉnh liên tục trong 7 ngày đầu (giai đoạn học).

### C.3 Phase và ngân sách (đề xuất, thị trường Việt Nam, Android)

> Con số CPI chỉ là **giả định** cần kiểm chứng ở Phase 1. Ước lượng ban đầu cho app audio Android ở VN: ~3.000–8.000 đ/install (~$0.12–0.32).

| Phase | Thời gian | Ngân sách | Cấu hình | Tiêu chí qua phase / dừng |
|---|---|---|---|---|
| **0. Chuẩn bị** | 2–3 tuần | $0 (công sức) | ASO, website, tracking, dashboard v1, creative (10 ảnh, 5 video, 5 headline vi/en) | C.0 hoàn tất |
| **1. Thử nghiệm** | 4 tuần | ~**10–15 triệu đ/tháng** (~$400–600): 350–500k đ/ngày | 1 chiến dịch ACi VN, CPI mục tiêu ~7.000 đ (ngân sách ngày ≥ 50× là thỏa) | Dừng nếu CPI > 2× mục tiêu sau 14 ngày, hoặc D1 retention của paid < 70% organic. Qua phase khi D7 retention paid ≥ 80% organic và có ≥ 10 `podcast_play_30s`/ngày |
| **2. Tối ưu** | 6–8 tuần | ~**25–40 triệu đ/tháng** (~$1.000–1.600) | ACe tCPA theo `podcast_play_30s`. Làm mới creative mỗi 2 tuần. Thêm EN (Philippines/Ấn Độ nếu muốn khai thác nội dung tiếng Anh, tách campaign theo geo) | ROAS D30 ≥ 40% (ad revenue thuần), LTV/CAC dự báo ≥ 1 trong 180 ngày |
| **3. Mở rộng** | Từ tháng 4 | Theo ROAS: tăng 20%/tuần khi ROAS D7 ổn định | tROAS; Meta/TikTok mỗi kênh thử $300–500 trong 3 tuần (tùy quyết định B.3) | Giữ kênh có ROAS D30 ≥ mục tiêu; cắt kênh kém nhất mỗi tháng |

Kinh tế đơn vị cần xác nhận sau khi có dữ liệu thật: với ARPDAU ad ~200–500 đ và ~15 ngày active trong 90 ngày đầu, LTV 90 ngày khoảng ~3.000–7.500 đ. Mức này **ngang CPI giả định**, nên quảng cáo trả phí chỉ có lãi nếu retention tốt hoặc có thêm IAP/"Remove ads". Đây là lý do SEO và ASO được ưu tiên trước.

### C.4 Đo lường thử nghiệm

- Mỗi thay đổi lớn (creative, mục tiêu, kênh) phải có giả thuyết, metric và thời hạn, ghi vào sheet hoặc dashboard.
- Cohort hóa theo `channel × campaign × week` và so với organic cùng tuần.
- Tránh nhiễu: không thay đổi monetization in-app (A/B ở `admob-migration.md` §9) **cùng lúc** với việc tăng mạnh ngân sách ads, hoặc phải tách bằng cohort/experiment id.

---

## D. Lộ trình tổng hợp

| Tuần | Web/SEO (A) | Dashboard (B) | Campaign (C) | App (liên quan) |
|---|---|---|---|---|
| 1–2 | Chốt domain, route, khung Next.js trong `web/`, `app-ads.txt`, privacy | Blaze, link BigQuery export, Search Console | ASO v1 | Phase 0 gỡ SDK (`admob-migration.md`) |
| 3–4 | Trang podcast/tập/danh mục, JSON-LD, sitemap, CWV | Dataform staging, Looker Studio v1 | Creative | Event `podcast_play_30s`, Install Referrer, seam AdMob |
| 5–6 | App Links + `assetlinks.json`, smart banner | AdMob API job, Play GCS loader | **Phase 1** ACi | App Links trong app; Phase 2 ads |
| 7–12 | Tối ưu nội dung, internal linking, hreflang | Cohort/LTV marts, alert | **Phase 2** ACe | Phase 3 A/B ads |
| 13+ | Mở rộng nội dung | Dashboard v2 `/internal` (nếu cần) | **Phase 3** | Mediation |

---

## E. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Thin/duplicate content từ RSS dẫn tới index kém hoặc bị đánh giá chất lượng thấp | Ngưỡng index (A.5), noindex trang yếu, thêm giá trị riêng |
| Bản quyền / nội dung nhạy cảm từ feed | Blacklist dùng chung với app, quy trình takedown, lọc `explicit` |
| Rate limit / ToS PodcastIndex | Cache ISR, key chỉ ở server, giữ attribution |
| Vượt quota export Firebase (1M event/ngày) | Bật streaming, giảm event nhiễu, sampling `listen_progress` |
| CPI ở VN cao hơn LTV | Phase 1 nhỏ, có ngưỡng dừng; ưu tiên SEO/ASO; cân nhắc IAP "Remove ads" |
| Attribution Meta/TikTok thiếu khi đã gỡ Facebook SDK | Quyết định ở B.3 trước khi chạy hai kênh này |
| Consent (EEA/US/VN) chặn bớt dữ liệu, làm số liệu thấp hơn thực tế | Consent Mode v2 (modeling), ghi chú trên dashboard |
| Lệch cấu trúc với `web/` do agent khác làm | Checklist A.13. Tài liệu này điều chỉnh theo `web/` |

---

## F. Câu hỏi cần owner quyết định

1. **Domain chính thức** cho website (dùng cho Play listing, `app-ads.txt`, App Links).
2. **Hosting:** Vercel Pro, Firebase App Hosting hay Cloudflare? Ai trả phí?
3. Chiến lược URL: `vi` ở root + `/en` (khuyến nghị), hay prefix cả hai? Có cần tiếng Anh ngay từ đầu không?
4. Có index trang **tập** (episode) không, hay chỉ index trang podcast ở giai đoạn đầu (an toàn hơn về thin content)?
5. **Firebase lên Blaze** (bắt buộc để BigQuery lưu > 60 ngày và có streaming)?
6. **Ngân sách quảng cáo** Phase 1/2 có khớp đề xuất C.3 không? Thị trường ngoài VN?
7. **Meta/TikTok:** có chạy không? Nếu có thì dùng `facebook-core` hay MMP (B.3)?
8. Có kế hoạch **app iOS** không (ảnh hưởng tới AASA, SKAN/AdAttributionKit, App Store Connect)?
9. Có làm **IAP "Remove ads"/premium** không (thay đổi LTV và ROAS đáng kể)?
10. Ai sở hữu và vận hành dashboard (quyền BigQuery, service account, allowlist email)?

---

## Phụ lục: Nguồn đã tra cứu (2026-10-29)

- Next.js phiên bản (16.x stable, cần xác minh bằng npm khi pin): https://nextjs.org/blog · https://versionlog.com/nextjs/
- Core Web Vitals: https://web.dev/articles/vitals · https://corewebvitals.io/core-web-vitals
- Google bỏ podcast carousel: https://www.seroundtable.com/amp/google-confirms-podcast-carousels-going-away-february-13-34870.html · Google bỏ thêm structured data types: https://www.seroundtable.com/google-drops-support-structured-data-types-40386.html
- app-ads.txt: https://support.google.com/admob/answer/9363762
- Firebase → BigQuery (giới hạn 1M event/ngày, streaming, sandbox): https://firebase.blog/posts/2022/12/bigquery-and-firebase-integration · https://support.google.com/firebase/answer/6318765
- AdMob Reporting API: https://developers.google.com/admob/api/v1/reporting · https://developers.google.com/admob/api/v1/report-overview
- Play Install Referrer: https://developer.android.com/google/play/installreferrer/library · https://developer.android.com/google/play/installreferrer/release-notes
- Firebase Dynamic Links ngừng hoạt động: https://blog.pragmaticengineer.com/google-shutting-down-firebase-dynamic-links/
- AdAttributionKit / SKAdNetwork: https://developer-mdn.apple.com/app-store/ad-attribution/ · https://www.singular.net/blog/adattributionkit-the-new-skadnetwork/
- Ngân sách Google App Campaigns: https://support.google.com/google-ads/answer/6167156 · https://support.google.com/google-ads/answer/9234105

# TSPodcast Web

Trang web nghe podcast của TSPodcast: podcast xu hướng, kho nhạc (`podcast:medium=music`), radio VOV/VOH trực tiếp, và các tính năng AI (tóm tắt tập, hỏi đáp về tập, tìm kiếm bằng ngôn ngữ tự nhiên). Dữ liệu lấy từ [Podcast Index](https://podcastindex.org), giống ứng dụng Android trong repo này.

- Next.js 16 (App Router, TypeScript), Tailwind CSS 4, React 19.
- Mọi lời gọi Podcast Index và Claude đều chạy **phía máy chủ**. Khoá API không bao giờ xuống trình duyệt (`src/lib/podcastindex.ts` và `src/lib/ai.ts` import `server-only`).
- Giao diện mặc định tiếng Việt, có sẵn bản tiếng Anh (`NEXT_PUBLIC_SITE_LOCALE=en`).

## Chạy thử ở máy

Yêu cầu Node.js 20.9 trở lên (đã thử với Node 24).

```bash
cd web
npm install
cp .env.example .env.local   # rồi điền khoá
npm run dev                  # http://localhost:3000
```

Bản production:

```bash
npm run build
npm run start                # cổng 3000, đổi bằng: npx next start -p 3123
npm run lint
```

Thiếu khoá Podcast Index thì web **vẫn chạy**: các trang hiện hướng dẫn cấu hình thay vì báo lỗi. Thiếu `ANTHROPIC_API_KEY` thì các khối AI bị ẩn và `/api/ai/*` trả 503.

## Biến môi trường

| Biến | Bắt buộc | Ý nghĩa |
|---|---|---|
| `PODCASTINDEX_API_KEY` | Có | Khoá Podcast Index (đăng ký miễn phí tại https://api.podcastindex.org/) |
| `PODCASTINDEX_API_SECRET` | Có | Secret tương ứng |
| `PODCASTINDEX_USER_AGENT` | Không | User-Agent gửi tới Podcast Index, mặc định `TSSPodcast/Web` (app Android gửi `TSSPodcast/Android`) |
| `ANTHROPIC_API_KEY` | Không | Bật tính năng AI |
| `ANTHROPIC_MODEL` | Không | Mặc định `claude-sonnet-5-5` |
| `ANTHROPIC_FALLBACKS` | Không | Đặt `off` nếu đi qua gateway không hỗ trợ beta `server-side-fallback-2026-07-01` |
| `NEXT_PUBLIC_SITE_URL` | Có ở production | Origin chuẩn, dùng cho canonical, Open Graph, sitemap, robots |
| `NEXT_PUBLIC_SITE_LOCALE` | Không | `vi` (mặc định) hoặc `en` |
| `SITE_NOINDEX` | Không | `1` cho môi trường preview/staging để chặn index toàn bộ |
| `GOOGLE_SITE_VERIFICATION` | Không | Mã xác minh Search Console (thẻ meta) |
| `APP_ADS_TXT` | Không | Nội dung `/app-ads.txt` cho AdMob, nhiều dòng thì nối bằng `\n` |
| `ANDROID_CERT_SHA256` | Không | Vân tay SHA-256 cho `/.well-known/assetlinks.json`, nhiều giá trị cách nhau bởi dấu phẩy |

**Lưu ý với file `.env*`:** Next.js tự thay `$TEN_BIEN` trong file env. Nếu secret có ký tự `$`, hãy viết thành `\$`. Nếu không, secret bị cắt ngắn mà không báo lỗi và mọi request tới Podcast Index trả **401**. Nhập trên dashboard của Vercel/Cloudflare/Firebase thì không cần escape.

Khoá của app Android hiện được hard-code trong `securedToken/src/main/cpp/securedtoken.cpp` (file này đang được commit). Web **không** đọc từ đó. Nên cấp một cặp khoá riêng cho web để có thể thu hồi độc lập.

## Cấu trúc

```
src/
  app/
    page.tsx                   Trang chủ: xu hướng, chủ đề, kho nhạc, tập mới, radio
    trending/                  Xu hướng (tiếng Việt + toàn cầu)
    category/[slug]/           Xu hướng theo chủ đề
    podcast/[idSlug]/          /podcast/123-ten-podcast (slug sai thì chuyển hướng 308 về URL chuẩn)
    episode/[idSlug]/          /episode/456-ten-tap, có khối AI
    search/                    ?q=&mode=podcast|music|ai (noindex)
    radio/                     VOV + VOH
    api/ai/{summary,chat,search}/route.ts
    sitemap.ts, robots.ts, manifest.ts, opengraph-image.tsx, icon.svg
    app-ads.txt/route.ts, .well-known/assetlinks.json/route.ts
  components/
    player/                    Trình phát (store ngoài React + useSyncExternalStore)
    ai/                        Tóm tắt, hỏi đáp, tìm kiếm AI (client)
  lib/
    podcastindex.ts            Client Podcast Index (xác thực, cache, chuẩn hoá dữ liệu)
    radio.ts                   Đọc danh sách kênh VOV/VOH
    ai.ts, ai-episode.ts, ai-search.ts, transcript.ts
    seo.ts                     Ngưỡng index, breadcrumb, tiêu đề
    i18n.ts                    Từ điển vi/en
```

### Lấy dữ liệu như app Android

- Xác thực Podcast Index giống `core/.../di/NetworkModule.kt`: `X-Auth-Key`, `X-Auth-Date` (giây Unix), `Authorization = sha1(key + secret + date)`, `User-Agent`.
- Endpoint dùng: `podcasts/trending` (7 ngày, `lang=vi` rồi bổ sung toàn cầu), `podcasts/bymedium?medium=music`, `categories/list`, `recent/episodes`, `podcasts/byfeedid`, `episodes/byfeedid`, `episodes/byid`, `search/byterm`, `search/music/byterm`.
- Áp dụng blacklist của app (`coreApi/src/main/assets/blacklist.json`). Từ khoá ngắn như `bet` được so khớp theo nguyên từ để không chặn nhầm "Alphabet".
- `podcasts/bymedium` **bỏ qua tham số `max`** và trả về khoảng 8.600 feed (vài MB) mỗi lần gọi. Web tự cắt danh sách trước khi cache. App Android cũng nhận cả khối dữ liệu này cho hàng "Kho nhạc".
- "Kho nhạc" là **thư viện**, không phải bảng xếp hạng, vì API không có xếp hạng cho nhạc (xem commit `dddd818`). Danh sách được sắp theo feed cập nhật gần nhất.

### Tuân thủ điều khoản Podcast Index

- Dòng ghi nguồn "Dữ liệu podcast cung cấp bởi Podcast Index" có ở footer mọi trang và trên trang podcast/tập (điều 7.2).
- Cache ngắn và tự hết hạn (điều 5.5(1)): xu hướng 15 phút, tập mới 10 phút, chi tiết podcast/tập 1 giờ, danh mục 1 ngày. Không lưu bản sao lâu dài.

### Cache và ISR

Web dùng mô hình cache cổ điển (route `revalidate` + `unstable_cache`). **Không** bật `cacheComponents`: chế độ đó yêu cầu `generateStaticParams` trả về id thật lúc build, tức là cần khoá API ngay khi build. Trang podcast/tập/chủ đề được render ở lần truy cập đầu (blocking ISR, nên crawler nhận HTML đầy đủ) rồi được cache.

Lời gọi Podcast Index đi qua `unstable_cache` thay vì cache của `fetch`, vì header xác thực đổi mỗi giây và nằm trong khoá cache của `fetch`.

### Trình phát

- Một `HTMLAudioElement` duy nhất nằm trong store ở cấp module, nên khi chuyển trang trình phát vẫn tiếp tục.
- Phát/tạm dừng, tua, tốc độ 0.75–2x, lùi 15 giây, tới 30 giây, danh sách chờ, nhớ vị trí nghe trong `localStorage` (`tsp.positions.v1`), điều khiển trên màn hình khoá qua Media Session API.
- Radio dùng HLS: trình duyệt hỗ trợ sẵn thì phát trực tiếp, không thì tải động `hls.js`.

### AI (tuỳ chọn)

- Dùng SDK chính thức `@anthropic-ai/sdk`, model `claude-sonnet-5-5`, trả kết quả dạng stream.
- **Tóm tắt tập** (`POST /api/ai/summary`): đọc mô tả/ghi chú tập, và cả bản chép lời nếu feed có `podcast:transcript` (JSON/VTT/SRT/HTML/text). Kết quả được cache 24 giờ theo tập.
- **Hỏi đáp về tập** (`POST /api/ai/chat`): tài liệu về tập được đặt trong system prompt có `cache_control` (prompt caching).
- **Tìm kiếm AI** (`POST /api/ai/search`): dùng structured output để chuyển câu như "podcast về khởi nghiệp dưới 30 phút" thành từ khoá, loại nội dung, ngôn ngữ và giới hạn thời lượng, rồi lọc tập theo thời lượng.
- Bật server-side refusal fallback (`fallbacks: "default"`). Có giới hạn tần suất theo IP (10–20 request/phút). Nội dung feed được coi là dữ liệu không tin cậy, không phải chỉ dẫn.
- Cache tóm tắt và giới hạn tần suất hiện **chỉ nằm trong bộ nhớ của từng instance**. Trên serverless, muốn dùng chung giữa các instance thì cần thêm store dùng chung (Upstash Redis, Vercel KV, Firestore...).

### SEO

Làm theo `docs/plans/seo-web-dashboard.md` §A:

- `generateMetadata` có canonical, Open Graph, Twitter. Tiêu đề dạng "<Tên> | Nghe podcast trên TSPodcast", cắt cho vừa khoảng 60 ký tự.
- JSON-LD render phía server: `Organization` + `WebSite` (layout), `PodcastSeries` (có `@id`, `ListenAction`), `PodcastEpisode` (`AudioObject`, liên kết `partOfSeries` qua `@id`), `BreadcrumbList`, `ItemList` (xu hướng). Không dùng `SearchAction`.
- Ngưỡng index (§A.5): podcast phải là tiếng vi/en, có ít nhất 3 tập và có tập trong 12 tháng gần đây. Tập phải có ghi chú dài từ 200 ký tự hoặc có bản chép lời. Không đạt thì gắn `noindex, follow`. Trang tìm kiếm luôn `noindex`.
- `sitemap.xml` chỉ gồm trang tĩnh, chủ đề và podcast xu hướng đạt ngưỡng. `lastmod` lấy theo ngày ra tập mới nhất.
- `robots.txt` chặn `/search` và `/api/`.
- Banner ứng dụng Android hiện dạng overlay (không gây CLS), ẩn 14 ngày sau khi đóng. Link cài app có `referrer` chứa UTM và `deeplink=/podcast/<id>` (§A.10).

## Deploy

### Vercel (khuyến nghị)

1. Import repo, đặt **Root Directory = `web`**. Framework sẽ tự nhận là Next.js.
2. Thêm biến môi trường như bảng trên (Production và Preview). Ở Preview, đặt `SITE_NOINDEX=1`.
3. Deploy. ISR và route handler chạy sẵn. Function cho `/api/ai/*` đã khai báo `maxDuration = 60`.

Gói Hobby không cho dùng thương mại. Có quảng cáo hoặc kiếm tiền thì cần gói Pro.

### Cloudflare Workers (OpenNext)

```bash
cd web
npm install -D @opennextjs/cloudflare wrangler
npx opennextjs-cloudflare build
npx opennextjs-cloudflare deploy
```

- Bật `nodejs_compat` trong `wrangler.jsonc`. Code dùng `node:crypto`.
- Đặt secret bằng `npx wrangler secret put PODCASTINDEX_API_SECRET` (tương tự cho các biến khác).
- Muốn ISR/`unstable_cache` bền giữa các request thì cấu hình incremental cache của OpenNext (R2 hoặc KV).

### Firebase App Hosting

1. `firebase init apphosting`, chọn repo, đặt **root directory = `web`**.
2. Khai báo biến trong `apphosting.yaml`, secret lấy từ Cloud Secret Manager:

   ```yaml
   env:
     - variable: NEXT_PUBLIC_SITE_URL
       value: https://tspodcast.example
     - variable: PODCASTINDEX_API_KEY
       secret: podcastindex-api-key
     - variable: PODCASTINDEX_API_SECRET
       secret: podcastindex-api-secret
     - variable: ANTHROPIC_API_KEY
       secret: anthropic-api-key
   ```

3. `firebase apphosting:secrets:set podcastindex-api-secret` rồi push lên nhánh đã kết nối.

Cùng project Firebase với app Android nên tiện gom về GCP. Nên kiểm tra lại hành vi ISR on-demand trên App Hosting.

## Việc còn để ngỏ

- Đường dẫn `/en` và hreflang chưa có. Ngôn ngữ đang chọn theo từng deployment.
- Các trang tĩnh (giới thiệu, chính sách bảo mật, điều khoản, liên hệ/gỡ nội dung) và landing `/tai-app` chưa làm (§A.3).
- GA4 + Consent Mode, `web-vitals`, Lighthouse CI chưa làm (§A.8, §A.11).
- Ảnh OG riêng cho từng podcast chưa có (đang dùng artwork). Ảnh dùng `<img>` thay vì `next/image` để máy chủ không thành proxy ảnh mở cho mọi host.
- App Links: web đã phục vụ `assetlinks.json`, nhưng app Android chưa có intent-filter `autoVerify` (§A.10).

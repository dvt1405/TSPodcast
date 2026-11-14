# Kế hoạch phase tiếp theo (sau iOS 1.1, từ 14/11/2026)

> **Trạng thái: Kế hoạch** (2026-11-14). Gom mọi việc còn lại sau đợt phát hành 13–14/11/2026: iOS 1.0 lên App Store, iOS 1.1 chờ duyệt, Android 10020 đang rollout, ads + IAP qua Remote Config, landing `tspodcast.web.app`. Việc owner cần làm ở [../ios/handoff.md](../ios/handoff.md); nhật ký ở [../ios/progress.md](../ios/progress.md).

## 0. Hiện trạng (14/11/2026)

| Mảng | Trạng thái |
|---|---|
| iOS | 1.0 (build 4) **Ready for Sale** ở 147/175 nước (27 nước EU chờ xác minh trader DSA, Afghanistan bị chặn theo độ tuổi). 1.1 (build 5: chọn vị trí podcast) **Waiting for Review**, tự phát hành khi duyệt. TestFlight: nhóm Internal + Public Beta (`testflight.apple.com/join/65pPR7ku`, build 4 chờ Beta Review). |
| Android | v3.5.10020 (ads qua RC, Premium ẩn) **production 20%**, Internal và Firebase App Distribution đủ. |
| Remote Config (`podcast-25c93`) | v6: ads bật, `iap_enabled=false`, iOS ad unit dưới condition `platform_ios`, `ts_podcast_black_list` 68 từ khoá cờ bạc. |
| IAP | 3 sản phẩm trên App Store Connect: `premium.monthly` 29.000₫, `premium.yearly` 249.000₫ (thử 7 ngày), `remove_ads.lifetime` 499.000₫; thiếu ảnh review, chờ Paid Apps Agreement. Chưa tạo trên Play Console. Owner chọn **bật cùng lúc** 2 nền tảng. |
| Quảng cáo trong Player | Code xong cả 2 nền tảng, **PR #74 chưa merge** (chờ chạy unit test iOS trên iPhone). Placement `player_up_next`, `player_now_playing`, `player_pause` mặc định tắt. Kế hoạch: [player-ads-and-premium.md](player-ads-and-premium.md). |
| Web | Web nghe trực tuyến (`web/`, Next.js) **đã bỏ** (owner 14/11). `tspodcast.web.app` và `podcast-25c93.web.app` cùng phục vụ landing `site/` (privacy, terms, delete-data, app-ads.txt, nút Google Play + App Store). Canonical: `tspodcast.web.app`. |
| Parity iOS ↔ Android | Pha 1 (vị trí podcast) xong trong 1.1. Còn pha 2–3 (§2). Radio vẫn ẩn trên iOS. |

## 1. P0 — hoàn tất việc đang dở (tuần này)

| # | Việc | Ai | Cỡ | Xong khi |
|---|---|---|---|---|
| 1.1 | PR #74 (ads trong Player): chạy `TSPodcastTests` trên iPhone "Tun", cài + mở thử, merge | Agent (cần iPhone mở khoá, cắm cáp) | S | Test iOS pass, app chạy ổn, PR merged |
| 1.2 | Thêm "Báo cáo quảng cáo" trên iOS (App Store 2.5.18) trước khi bật bất kỳ placement Player nào trên iOS | Agent | S | Mục trong menu `…` của Player gửi mail/route báo cáo; test |
| 1.3 | Rollout placement Player: `player_up_next` → `player_now_playing` → `player_pause` (A/B), mỗi bước 10% → 50% → 100% | Owner bật RC, agent theo dõi | S | Thời lượng nghe/DAU giảm < 3%, CTR Player không bất thường (§3.8 kế hoạch ads) |
| 1.4 | Android 10020: theo dõi Crashlytics/ANR, nâng production 20% → 50% → 100% | Agent (Play API) | S | Crash-free ≥ bản 10019, rollout 100% |
| 1.5 | iOS 1.1: khi phát hành, chụp lại ảnh store có màn "Vị trí podcast"/Home mới (`iosApp/scripts/store-screenshots.sh`) và upload | Agent | S | 17 locale có ảnh mới |

## 2. P1 — iOS parity pha 2 và 3

Bảng chênh lệch đầy đủ: báo cáo rà soát ngày 14/11 (tóm tắt dưới). Logic mới đặt trong `shared/` để Android dùng lại khi cần.

| # | Việc | Android tham chiếu | Cỡ |
|---|---|---|---|
| 2.1 | **Lịch sử nghe** (nhóm Hôm nay/Hôm qua/ngày, xoá từng mục/xoá hết, tiến độ, "Đã nghe xong"). Cần bảng `ListeningHistory` + migration Room trong `shared` | `ui/screens/history/*`, `core/.../ListeningHistory.kt`, `sharedPlayer/.../ListeningRecorder.kt` | L |
| 2.2 | **Hàng "Nghe tiếp"** trên Home (≤ 10, "Xem tất cả" → Lịch sử) + lối vào Lịch sử ở Yêu thích và Cài đặt | `discorver/components/ContinueListeningSection.kt` | M (sau 2.1) |
| 2.3 | Nút Yêu thích ở header Podcast detail | `PodcastDetailActions.kt` | S |
| 2.4 | Tìm kiếm: lọc chủ đề trên kết quả, dải chủ đề gọn + sheet "Xem tất cả", chủ đề phổ biến trước | `search/widgets/SearchCategories.kt` | S |
| 2.5 | Tên danh mục theo ngôn ngữ app (112 id × 15 ngôn ngữ, chuyển từ `res/values-*/categories.xml` sang xcstrings), "Phổ biến" trước, ô tìm trong màn chọn | `featureOnboarding/.../CategoryNames.kt` | M |
| 2.6 | Chọn ngôn ngữ trong app + tuỳ chọn "Theo khu vực" | `core/.../LanguagePrefs.kt` | S–M |
| 2.7 | Cài đặt: "Góp ý và báo lỗi" (mail), Điều khoản, "Xoá dữ liệu của bạn" (`tspodcast.web.app/terms`, `/delete-data`) | `SettingsScreen.kt` | S |
| 2.8 | "Báo cáo" trong menu nhấn giữ ở Home/Tìm kiếm | `common/ShowActions.kt` | S |
| 2.9 | Chia sẻ podcast/tập + mở link chia sẻ trong app — **chờ quyết định §4.1** | `PodcastDetailActions.kt`, `common/WebLinks.kt` | S + M |
| 2.10 | CarPlay (cần entitlement Apple) | `MediaLibraryCatalog.kt` | L, để sau |

Thứ tự đề xuất: 2.7, 2.3, 2.8 (nhanh, cùng một bản 1.2) → 2.1 + 2.2 (bản 1.3) → 2.4–2.6 → 2.9 khi có quyết định link.

## 3. P1 — kiếm tiền

| # | Việc | Ai | Cỡ |
|---|---|---|---|
| 3.1 | **Bật IAP cùng lúc 2 nền tảng**: (a) owner xong Paid Apps Agreement + thuế; (b) ảnh review paywall cho 3 sản phẩm ASC (bật paywall trong bản Debug để chụp); (c) tạo trên Play Console: subscription `premium` (base plan `monthly` 29.000₫, `yearly` 249.000₫ + offer dùng thử 7 ngày) và one-time `remove_ads_lifetime` 499.000₫; (d) mua thử sandbox/test track; (e) RC `iap_enabled=true` | Owner (a), agent (b–e) | M |
| 3.2 | Lối vào paywall và nhắc nâng cấp (`iap_upsell_*` trong kế hoạch ads §5.5), phễu analytics `paywall_view` → `purchase` | Agent | S–M |
| 3.3 | Ad unit riêng cho từng placement Player (báo cáo, price floor) — tuỳ chọn | Owner tạo trên AdMob, agent điền RC | S |
| 3.4 | Đưa bộ lọc cờ bạc theo thương hiệu + host (đang có ở web cũ, đã bỏ) vào `shared/BlacklistFilters` và Android, vì RC chỉ so khớp `contains` từ khoá | Agent | S |
| 3.5 | Tăng giá cho người đăng ký mới khi có quyền lợi mới (tải offline, AI, đồng bộ) — theo kế hoạch ads §5.2 | Owner quyết | — |

## 4. Cần owner quyết định

1. **Link chia sẻ / App Links.** Android đang chia sẻ `https://tspodcast.vercel.app/podcast/<id>-<slug>` (web đã chết) và khai App Link cho host đó. Web nghe trực tuyến đã bỏ. Đề xuất: link `https://tspodcast.web.app/podcast/<id>-<slug>`, Hosting rewrite `/podcast/**`, `/episode/**` về một trang tĩnh "Mở trong app" (nút Play/App Store), thêm `/.well-known/assetlinks.json` + `apple-app-site-association` vào `site/`, Android đổi `WEB_HOST`, iOS điền `APP_LINK_HOST` + Associated Domains. Hoặc: bỏ chia sẻ link, chỉ chia sẻ text + link store.
2. **Website trên Play Console** đổi từ `podcast-25c93.web.app` sang `tspodcast.web.app` (app-ads.txt có ở cả hai; landing giống nhau).
3. Giữ hay xoá nhóm TestFlight Public Beta sau khi 1.1 phát hành.
4. Khi nào bật IAP (§3.1) — phụ thuộc Paid Apps Agreement.
5. Thứ tự parity §2 (đề xuất ở trên).

## 5. Tài liệu đã cũ sau đợt này

- [seo-web-dashboard.md](seo-web-dashboard.md), [web-vps-deploy.md](web-vps-deploy.md), [../../done/deploy/README.md](../../done/deploy/README.md): mô tả web Next.js và deploy VPS — web đã bỏ ngày 14/11/2026 (`web/` và `deploy-web.yml` đã xoá; thư mục `deploy/` còn giữ cho backend tương lai).
- [../ios/app-store-submission-plan.md](../ios/app-store-submission-plan.md): đã nộp và phát hành 1.0; giữ làm tài liệu tham khảo cho lần nộp sau.

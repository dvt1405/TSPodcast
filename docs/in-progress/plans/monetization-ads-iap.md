# Ads + IAP qua Remote Config (Android + iOS)

Owner, 2026-11-13: dùng Remote Config để setup IAP và quảng cáo, **mặc định bật trên cả 2 nền
tảng**, tích hợp đầy đủ (AdMob iOS, Play Billing, StoreKit 2, paywall, Premium = không quảng cáo).

Nguồn key và giá trị: `remoteconfig/ads-iap.params.json` (publish bằng `remoteconfig/publish.sh`,
xem `remoteconfig/README.md`). Tài liệu này là contract chung cho 2 app.

## 1. Quy tắc chung

1. **Default trong app = giá trị trong template.** App chạy đúng kể cả khi chưa từng fetch Remote
   Config (offline, project chưa publish). Key thiếu / parse lỗi → dùng default, không crash.
2. `ads_enabled` mặc định `true` trên cả 2 nền tảng. `iap_enabled` mặc định **`false`** ở phase này
   (owner 2026-11-13: chờ Apple duyệt Paid Apps Agreement); bật lại bằng Remote Config.
3. **Premium (benefit `ad_free`) tắt mọi quảng cáo**: banner, native, interstitial, app open,
   rewarded (placement `rewarded_ad_free` ẩn luôn). Đây là trạng thái `Disabled("premium")`, không
   phải `ads_enabled=false`.
4. `iap_enabled=false` chỉ ẩn lối vào paywall; quyền Premium đã mua vẫn giữ và vẫn tắt quảng cáo.
5. Bản debug: chỉ dùng ad unit **test** của Google, bỏ qua `ads_unit_ids` từ Remote Config. Mua hàng
   debug dùng tài khoản test (Play license tester) / StoreKit Configuration file (iOS).
6. Không có backend xác thực receipt (chưa có server). Entitlement lấy từ store trên máy:
   Android `queryPurchasesAsync`, iOS `Transaction.currentEntitlements`. Cache trạng thái Premium
   cục bộ để lúc mở app không nháy quảng cáo trước khi store trả lời.

## 2. Remote Config keys

### Ads (đã có trên Android, iOS đọc y hệt)

`ads_enabled`, `ads_unit_ids`, `ads_placements_enabled`, `ads_grace_period_hours`,
`ads_app_open_*`, `ads_min_gap_fullscreen_sec`, `ads_interstitial_*`, `ads_native_*`,
`ads_rewarded_*`, `ads_min_app_version`, `ads_ga4_ad_impression`. Ngữ nghĩa và default:
`ads/src/main/java/tss/t/ads/AdsConfig.kt` (`AdsKeys`, `AdsSettings.defaults`, `AdsSettings.parse`),
chính sách tần suất: `ads/src/main/java/tss/t/ads/AdPolicy.kt`.

- `ads_unit_ids`: `{ "<placement key>": "ca-app-pub-…/…" }`, chỉ bản release đọc. Giá trị iOS là
  conditional `platform_ios`. Key thêm `rewarded_interstitial` chỉ có trên iOS.
- `ads_min_app_version`: Android so với `versionCode`, iOS so với `CFBundleVersion` (số nguyên).

### IAP (mới, cả 2 nền tảng)

| Key | Kiểu | Default | Ý nghĩa |
|---|---|---|---|
| `iap_enabled` | bool | `false` (phase này) | Hiện lối vào paywall |
| `iap_default_product` | string | `premium_yearly` | `id` nội bộ của gói chọn sẵn |
| `iap_products` | JSON | xem dưới | Danh sách gói, theo nền tảng |

```jsonc
{
  "version": 1,
  "products": [{
    "id": "premium_yearly",          // id nội bộ, giống nhau 2 nền tảng (analytics, default product)
    "type": "subscription",          // subscription | one_time
    "store_product_id": "premium",   // Android productId | iOS product id
    "base_plan_id": "yearly",        // chỉ Android subscription
    "subscription_group": "premium", // chỉ iOS, thông tin
    "period": "P1Y",                 // thông tin; UI hiển thị chu kỳ theo dữ liệu của store
    "benefits": ["ad_free"],
    "badge": "best_value",           // null | "best_value"
    "order": 2
  }]
}
```

Client bỏ qua sản phẩm store không trả về (chưa tạo / chưa duyệt) và bỏ qua field lạ. Giá, chu kỳ,
dùng thử lấy **từ store**: Android `ProductDetails` (pricing phases; offer có giá 0 = trial, Play chỉ
trả offer người dùng đủ điều kiện), iOS `Product` (`subscription.introductoryOffer` +
`isEligibleForIntroOffer`).

Product id (đề xuất, owner tạo trên store):

| id | Android | iOS |
|---|---|---|
| premium_monthly | subs `premium`, base plan `monthly` | auto-renewable `tss.t.podcast.premium.monthly` (group `premium`) |
| premium_yearly | subs `premium`, base plan `yearly` (+ offer trial 7 ngày) | auto-renewable `tss.t.podcast.premium.yearly` (intro offer 7 ngày) |
| remove_ads_lifetime | one-time `remove_ads_lifetime` | non-consumable `tss.t.podcast.remove_ads.lifetime` |

## 3. Paywall

- Lối vào: hàng "Premium / Bỏ quảng cáo" trong Cài đặt (ẩn khi `iap_enabled=false`; khi đã Premium
  hiện trạng thái "Đang dùng Premium" + quản lý gói).
- Nội dung: lợi ích (không quảng cáo), danh sách gói theo `order` với giá bản địa hoá, chu kỳ,
  badge, trial nếu đủ điều kiện; nút mua; **Khôi phục giao dịch**; link **Điều khoản**
  (`/terms` trên site) và **Chính sách quyền riêng tư** (`/privacy`); câu tự động gia hạn, huỷ bất
  cứ lúc nào trong cài đặt store (Apple 3.1.2, Play subscription policy).
- iOS: dùng EULA chuẩn của Apple nếu không có Terms riêng. Quản lý gói:
  `AppStore.showManageSubscriptions` / Android deep link
  `https://play.google.com/store/account/subscriptions?sku=<productId>&package=tss.t.podcast`.
- Mua xong: tắt quảng cáo ngay (không cần mở lại app). Android phải **acknowledge** mọi purchase
  (sub và one-time) trong 3 ngày; iOS `transaction.finish()` và nghe `Transaction.updates`.
- Analytics (theo tracker có sẵn của từng app): `paywall_view`, `purchase_start`,
  `purchase_success`, `purchase_cancel`, `purchase_fail`, `purchase_restore` với `product_id` (id
  nội bộ) và `source`.

## 4. iOS ads

AdMob app `ca-app-pub-8696388065064767~2921388712` (`GADApplicationIdentifier` trong Info.plist,
release; debug dùng test app id `ca-app-pub-3940256099942544~1458002511`).

| Format | Unit id (release) | Placement |
|---|---|---|
| Banner | `ca-app-pub-8696388065064767/5141512895` | favourite_top, podcast_detail_header, search_results, discover_fav_inline, player_episode_list, discover_collapsible |
| Interstitial | `ca-app-pub-8696388065064767/4518696367` | interstitial_nav |
| Rewarded interstitial | `ca-app-pub-8696388065064767/2729817023` | (chưa có placement) |
| Rewarded | `ca-app-pub-8696388065064767/8512626743` | rewarded_ad_free |
| Native | `ca-app-pub-8696388065064767/2538245333` | discover_inline, podcast_detail_episodes, player_now_playing, player_up_next, player_pause |
| App Open | `ca-app-pub-8696388065064767/9264415720` | app_open_warm, app_open_cold |

Consent: UMP trước, rồi ATT (`NSUserTrackingUsageDescription`), chỉ request ad khi UMP
`canRequestAds`. `SKAdNetworkItems` theo danh sách của Google. Chính sách tần suất và placement bật
mặc định giống Android (`AdsSettings.defaults`, `AdPolicy`): không quảng cáo đè audio đang phát,
không trên màn Player, grace 24 h, bỏ qua 2 phiên đầu.

## 5. Việc owner phải làm

- Tạo sản phẩm trên Play Console và App Store Connect đúng id ở §2 (hoặc sửa template), Paid Apps
  Agreement + thuế/ngân hàng trên App Store Connect, license tester trên Play.
- Publish Remote Config (`remoteconfig/publish.sh <project>`) bằng tài khoản có quyền.
- Cập nhật App Privacy (App Store) và Data safety (Play): quảng cáo, IDFA/Advertising ID, Purchases.
- Tài khoản Apple Developer trả phí để test sandbox / TestFlight (team hiện tại là personal).

# Remote Config: Ads + IAP

`ads-iap.params.json` chứa các key quảng cáo và gói mua trong app cho cả Android và iOS. Giá trị
riêng cho iOS nằm trong `conditionalValues.platform_ios` (condition `device.os == 'ios'`). Android
dùng `defaultValue`.

## Publish

```sh
firebase login:use <tài-khoản-có-quyền-Remote-Config>
remoteconfig/publish.sh podcast-25c93 --dry-run   # xem template sau khi merge
remoteconfig/publish.sh podcast-25c93             # Android prod
remoteconfig/publish.sh ts-podcast-uat            # iOS (UAT)
```

Deploy một template sẽ thay **cả** template, nên `publish.sh` không đẩy file này một mình: nó tải
template đang chạy, thay đúng các condition/key trong file params (`merge.sh`) và giữ nguyên mọi key
khác (`ts_podcast_black_list`, news hub, ...). File trung gian nằm ở `remoteconfig/build/` (gitignored).
Lỡ tay thì rollback trong Console (Remote Config → Change history) hoặc
`firebase remoteconfig:rollback -v <version> --project <id>`.

Không nên publish lên project debug `tspodcast-4d735`: các giá trị ở đây là của bản release (grace
24 h, bỏ qua 2 phiên đầu, interstitial bật) và sẽ đè default riêng của bản debug.

## Ads

Key và cách parse: `ads/src/main/java/tss/t/ads/AdsConfig.kt` (`AdsKeys`). `defaultValue` khớp với
`AdsSettings.defaults(debug = false)`, nên publish lên không đổi hành vi hiện tại của Android.

- `ads_enabled`: mặc định `true` trên cả 2 nền tảng (kill switch). Premium luôn tắt quảng cáo.
- `ads_unit_ids`: Android `{}` = dùng id build-time (GitHub secrets `ADMOB_UNIT_*`). iOS map từng
  placement sang unit theo format, thêm `rewarded_interstitial` (Android không có format này).

AdMob iOS (app `ca-app-pub-8696388065064767~2921388712`):

| Format | Unit id | Placement |
|---|---|---|
| Banner | `ca-app-pub-8696388065064767/5141512895` | favourite_top, podcast_detail_header, search_results, discover_fav_inline, player_episode_list, discover_collapsible |
| Interstitial | `ca-app-pub-8696388065064767/4518696367` | interstitial_nav |
| Rewarded interstitial | `ca-app-pub-8696388065064767/2729817023` | rewarded_interstitial |
| Rewarded | `ca-app-pub-8696388065064767/8512626743` | rewarded_ad_free |
| Native | `ca-app-pub-8696388065064767/2538245333` | discover_inline, podcast_detail_episodes |
| App Open | `ca-app-pub-8696388065064767/9264415720` | app_open_warm, app_open_cold |

App ID **không** lấy được từ Remote Config: Google Mobile Ads SDK đọc `GADApplicationIdentifier`
trong `Info.plist` lúc khởi động, nên khi tích hợp SDK trên iOS phải đặt App ID ở đó.

## IAP

Android (Play Billing) và iOS (StoreKit 2) đọc nhóm này; `iap_enabled` mặc định `false` ở phase này (chờ Paid Apps Agreement). Contract đầy đủ:
`docs/in-progress/plans/monetization-ads-iap.md`.

`iap_products`:

```jsonc
{
  "version": 1,
  "products": [{
    "id": "premium_yearly",           // id nội bộ, giống nhau trên 2 nền tảng
    "type": "subscription",           // subscription | one_time
    "store_product_id": "premium",    // Android: productId; iOS: product id App Store Connect
    "base_plan_id": "yearly",         // chỉ Android subscription
    "subscription_group": "premium",  // chỉ iOS subscription
    "period": "P1Y",                  // ISO 8601
    "benefits": ["ad_free"],
    "badge": "best_value",            // null | best_value | ...
    "order": 2
  }]
}
```

Giá không ghi ở đây: app phải lấy giá đã bản địa hoá từ store. Các product id hiện tại là **đề
xuất**; phải tạo đúng các id này trên Play Console / App Store Connect (hoặc sửa file cho khớp) trước
khi phát hành; sản phẩm store không trả về sẽ tự bị ẩn khỏi paywall. Giá, chu kỳ, dùng thử lấy từ store.

| id | Android (product / base plan) | iOS product id |
|---|---|---|
| premium_monthly | `premium` / `monthly` | `tss.t.podcast.premium.monthly` |
| premium_yearly | `premium` / `yearly` (trial 7 ngày) | `tss.t.podcast.premium.yearly` (trial 7 ngày) |
| remove_ads_lifetime | `remove_ads_lifetime` (one-time) | `tss.t.podcast.remove_ads.lifetime` (non-consumable) |

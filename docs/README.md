# Tài liệu TSPodcast theo trạng thái

Cập nhật 2026-11-14 (sau PR #73). Mọi tài liệu kế hoạch, spec và hướng dẫn được xếp vào ba thư mục theo việc đã làm tới đâu trên `main`, không theo lời ghi trong tài liệu:

- [`done/`](done/): đã làm xong, hoặc hướng dẫn còn dùng được. Hướng dẫn mô tả cách làm cũ được ghi "đã cũ" ở đầu file.
- [`in-progress/`](in-progress/): một phần đã vào `main`, phần còn lại ghi ở đầu file.
- [`planned/`](planned/): chưa bắt đầu phần chính.

Mỗi file có một khối "Trạng thái" ngay dưới tiêu đề. Khi chuyển trạng thái: `git mv` sang thư mục mới (giữ nguyên thư mục con), sửa khối trạng thái, sửa bảng dưới đây và các tham chiếu tới đường dẫn cũ (`git grep -n <tên file>`).

Không nằm ở đây: `README.md` gốc, [`brand/BRAND.md`](../brand/BRAND.md), `brand/web/README.md`, `site/README.md`, metadata fastlane.

## Hoàn thành

| Tài liệu | Nội dung | Bằng chứng |
|---|---|---|
| [analytics/analytics-events.md](done/analytics/analytics-events.md) | Mọi event Analytics Android (màn hình, phát, khám phá, cài đặt, quảng cáo + doanh thu `ad_impression`, lỗi), user property, consent, cách kiểm tra DebugView | PR analytics; còn đăng ký custom dimension trên GA4 |
| [brand/concepts/README.md](done/brand/concepts/README.md) | Ba phương án logo, chọn "Lên sóng" | PR #21 |
| [crashlytics/2026-10-crash-triage.md](done/crashlytics/2026-10-crash-triage.md) | Phân loại crash Crashlytics 08–10/2026 | PR #19; còn đóng issue trên Crashlytics sau bản mới |
| [deploy/README.md](done/deploy/README.md) | Hướng dẫn deploy web lên VPS (Docker, Caddy, GHCR, SSH) | Đã cũ: web nghe trực tuyến bỏ ngày 14/11/2026 (PR #72); `deploy/` giữ cho backend |
| [handoff/handoff-2026-11-01.pdf](done/handoff/handoff-2026-11-01.pdf) | Bản bàn giao dự án ngày 2026-11-01 (PDF, ảnh chụp tại thời điểm đó) | PR #29 |
| [ios/README.md](done/ios/README.md) | Mục lục bộ tài liệu iOS, quyết định D1–D7 | PR #20, #30 |
| [ios/architecture.md](done/ios/architecture.md) | Kiến trúc KMP `shared` + SwiftUI | PR #20; dựng ở PR #30 |
| [ios/audio-playback-ios.md](done/ios/audio-playback-ios.md) | Phát audio trên iOS (AVFoundation, Now Playing) | PR #20; dựng ở PR #30 (M3, M6) |
| [ios/features-spec.md](done/ios/features-spec.md) | Spec parity iOS theo hành vi Android | PR #20; dùng cho M1–M6 và PR #36, #38 |
| [ios/setup-and-build.md](done/ios/setup-and-build.md) | Cài máy Mac và build app iOS | PR #20, #30, #48 |
| [ios/milestones/M0.md](done/ios/milestones/M0.md) | M0: module `shared` + project Xcode | PR #30, accepted |
| [ios/milestones/M1.md](done/ios/milestones/M1.md) | M1: onboarding, tab, Home | PR #30, accepted |
| [ios/milestones/M2.md](done/ios/milestones/M2.md) | M2: Podcast detail, Search | PR #30, accepted |
| [ios/milestones/M3.md](done/ios/milestones/M3.md) | M3: audio engine, Player, MiniPlayer | PR #30, accepted (round 2) |
| [ios/milestones/M4.md](done/ios/milestones/M4.md) | M4: favourite, lưu trữ, push, deep link | PR #30, accepted |
| [ios/milestones/M5.md](done/ios/milestones/M5.md) | M5: Liquid Glass, Up next peek | PR #30, accepted |
| [ios/milestones/M6.md](done/ios/milestones/M6.md) | M6: hub tin, player cơ bản, App Shortcuts | PR #30, accepted; còn việc owner |
| [research/competitor-analysis.md](done/research/competitor-analysis.md) | Phân tích đối thủ | PR #17; khuyến nghị chuyển vào roadmap-backlog |
| [store/play-listing-aso.md](done/store/play-listing-aso.md) | Listing Play/App Store chuẩn ASO, 15 thị trường | PR #35, #48 |
| [plans/KMP_MIGRATION_PLAN.md](done/plans/KMP_MIGRATION_PLAN.md) | Kế hoạch KMP ban đầu (07/2025) | Đã cũ, được thay: `shared` có ở PR #30, Android không chuyển sang `shared` |
| [ci/FIREBASE_SERVICE_ACCOUNT_SETUP.md](done/ci/FIREBASE_SERVICE_ACCOUNT_SETUP.md) | Service account cho Firebase App Distribution | Còn đúng (`FIREBASE_SERVICE_ACCOUNT_JSON`) |
| [ci/GITHUB_ENVIRONMENTS_GUIDE.md](done/ci/GITHUB_ENVIRONMENTS_GUIDE.md) | Dùng GitHub Environments cho secret theo môi trường | Còn đúng |
| [ci/GITHUB_ACTIONS_RELEASE.md](done/ci/GITHUB_ACTIONS_RELEASE.md) | Phát APK tester qua `android.yml` | Cũ một phần: thiếu `ADMOB_*`, nhóm `tspodcastrelease` (PR #54) |
| [ci/KEYSTORE_PASSWORD_INJECTION.md](done/ci/KEYSTORE_PASSWORD_INJECTION.md) | Đưa mật khẩu keystore vào Gradle | Cũ một phần: đường dẫn keystore, `release.yml` |
| [ci/RELEASE_PROCESS.md](done/ci/RELEASE_PROCESS.md) | Build và phát APK/AAB bằng task Gradle | Cũ một phần: đường dẫn keystore, nhóm tester |
| [ci/GOOGLE_PLAY_DEPLOYMENT.md](done/ci/GOOGLE_PLAY_DEPLOYMENT.md) | Đẩy AAB lên Play qua workflow | Đã cũ: workflow đổi ở PR #51 |
| [ci/FIREBASE_TOKEN_SETUP.md](done/ci/FIREBASE_TOKEN_SETUP.md) | Tạo `FIREBASE_TOKEN` cho CI | Đã cũ: thay bằng service account |

## Đang làm

| Tài liệu | Nội dung | Đã xong / còn lại |
|---|---|---|
| [plans/admob-migration.md](in-progress/plans/admob-migration.md) | Chuyển quảng cáo sang AdMob Next-Gen | Xong Phase 0–1, id thật, quy tắc interstitial (PR #24, #38, #45, #54). Còn rollout có guardrail trên Play, A/B, rewarded, mediation |
| [plans/seo-web-dashboard.md](in-progress/plans/seo-web-dashboard.md) | Web chuẩn SEO, dashboard tăng trưởng, chiến dịch | Web Next.js đã bỏ 14/11/2026 (PR #72); landing `site/` chạy ở `tspodcast.web.app`. Còn ASO, Search Console, chiến dịch |
| [plans/web-vps-deploy.md](in-progress/plans/web-vps-deploy.md) | Đưa web lên VPS | Dừng: web nghe trực tuyến đã bỏ 14/11/2026 |
| [plans/monetization-ads-iap.md](in-progress/plans/monetization-ads-iap.md) | Contract ads + IAP qua Remote Config (Android + iOS), khoá RC, sản phẩm IAP | Đã vào app (PR #67), RC v6 đã publish; IAP ẩn chờ Paid Apps Agreement |
| [plans/next-phase-2026-11.md](in-progress/plans/next-phase-2026-11.md) | Kế hoạch phase tiếp theo sau iOS 1.1: hoàn tất ads Player, parity pha 2–3, bật IAP, link chia sẻ | Kế hoạch (14/11/2026) |
| [plans/player-ads-and-premium.md](in-progress/plans/player-ads-and-premium.md) | Quảng cáo trong Player và Up next (tham khảo app nhạc), rủi ro chính sách, gói Premium và giá | Owner đã chốt (PR #73): 3 placement, giá 29k/249k/499k, IAP bật cùng lúc. Code ở PR #74 (chưa merge), mặc định tắt |
| [plans/roadmap-backlog.md](in-progress/plans/roadmap-backlog.md) | Backlog tổng các việc đã research | Nhiều mục P1 đã làm (PR #30, #35, #36, #38, #39, #45, #48). Còn `network_security_config`, `PlayerSessionService`, tên miền, dashboard, AI, offline, Android Auto, backend |
| [policy/store-policy-review.md](in-progress/policy/store-policy-review.md) | Rà soát chính sách Play và App Store, checklist §11 | Đã xử lý #4, #6–#11, #13, #19 (PR #56). Còn #17, #18, #25 và khai báo Play Console |
| [research/de-xuat-tinh-nang-dot-pha.md](in-progress/research/de-xuat-tinh-nang-dot-pha.md) | Đề xuất tính năng N1–N26 | Đã làm N1 lite, N2, N4, N5, N11 lite, App Shortcuts (PR #30, #36, #38, #39). Còn các mục cần backend hoặc tên miền |
| [research/feature-roadmap-ai-growth.md](in-progress/research/feature-roadmap-ai-growth.md) | Lộ trình AI và tăng trưởng | Đã làm G4, G12, G13, player cơ bản, iOS, AI trên web. Còn AI trong app, offline, widget, Android Auto, Premium |
| [ios/app-store-submission-plan.md](in-progress/ios/app-store-submission-plan.md) | Kế hoạch nộp App Store | 1.0 phát hành 13/11/2026, 1.1 chờ duyệt (PR #67, #68, #71). Giữ làm tham khảo cho lần nộp sau |
| [ios/progress.md](in-progress/ios/progress.md) | Nhật ký tiến độ iOS | M0–M6, phát hành 1.0, parity pha 1 (PR #30, #67–#71). Còn parity pha 2–3 |
| [ios/handoff.md](in-progress/ios/handoff.md) | Việc owner cần làm cho iOS | Bảng "Cần owner (14/11/2026)": Paid Apps Agreement, DSA, quyền Firebase, email báo cáo, link chia sẻ |
| [release/play-release-v3.4.10017.md](in-progress/release/play-release-v3.4.10017.md) | Checklist phát hành Google Play | Xong AAB, asset, listing, id AdMob (PR #27, #45, #48, #54). Còn upload bản production (thiếu secret Play) và khai báo Play Console |

## Chưa làm

| Tài liệu | Nội dung | Còn lại |
|---|---|---|
| [Kế hoạch thông báo kéo người dùng quay lại](https://claude.ai/artifact/5HDLbL9DPfgoQ4uCUi4VvA) (Claude Docs) | 9 loại thông báo (nghe tiếp, tập mới, trending, bản tin), xin quyền đúng lúc, lịch theo vòng đời, Android + iOS | Toàn bộ 4 giai đoạn; chờ owner chốt nơi chạy server push |
| [plans/android-auto.md](planned/plans/android-auto.md) | TSPodcast trên ô tô: Android Auto trước, Android Automotive OS sau. Cây duyệt, khai báo, Play Console, thử bằng DHU, các mốc | Đã có nền móng M0 (`MediaLibraryService`, cây duyệt, giọng nói, khai báo; PR #56). Còn thử DHU/xe thật, ảnh bìa, radio, opt-in Play Console, AAOS |
| [plans/ke-hoach-backend-quickwin-cuoc-lon.md](planned/plans/ke-hoach-backend-quickwin-cuoc-lon.md) | Backend podcast riêng, quick wins, cược lớn | Backend (Postgres + crawler), chỉ mục Việt, N23, tầng AI, "Nhà của podcast Việt". Riêng quick win §3 đã làm trong iOS M6 và Android |

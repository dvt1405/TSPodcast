# Phân loại crash Crashlytics — 10/2026

- Ngày: 2026-10-29
- Nhánh: `tuandv/TSP-10-crashlytics-fixes` (tách từ `main` @ 47fc4f4)
- Nguồn: Firebase project `podcast-25c93`, app release `1:522103746332:android:561c9f81fd5a7cc501d4d9`,
  đọc qua Firebase CLI 15.30.1 MCP (`crashlytics_get_report`, `crashlytics_batch_get_events`, `crashlytics_list_events`).
  Chỉ đọc, không đổi trạng thái issue.
- Khoảng thời gian: 2026-08-01 → 2026-10-29 (giới hạn 90 ngày của API).

## Tổng quan

| Phiên bản | Sự kiện | Người dùng bị ảnh hưởng | Crash-free users |
|---|---|---|---|
| v3.3.10016 (10016) | 5 | 4 | 75% |
| v2.3.10024 (10024) | 1 | 1 | 66,7% |
| v1.4.10010 (10010) | 1 | 1 | 85,7% |

- FATAL: 4 issue (bên dưới).
- NON_FATAL: không có kết quả.
- ANR: không có kết quả.
- App debug (`1:187551572250:android:e952fdb02a12421bd60ca3`) thuộc project khác; tài khoản CLI bị HTTP 403 nên không đọc được.

## Bảng phân loại

| Issue id | Exception | Sự kiện / người dùng | Phiên bản | Nguyên nhân gốc | Trạng thái |
|---|---|---|---|---|---|
| `23322637b369c9bf5c2c9c89e0572eaa` | `UnsatisfiedLinkError: dlopen failed: library "libsecuredtoken.so" not found` tại `NativeLib.<clinit>` ← `ApplovinSdkWrapper.initSdk` ← `App.onCreate` | 2 / 1 | v3.3.10016 | AAB bật ABI split nên `.so` chỉ nằm trong split `config.<abi>`. Bản cài chỉ có base APK (sideload, trích xuất APK, chuyển máy) không có thư viện. `NativeLib` gọi `System.loadLibrary` vô điều kiện trong `init` nên crash ở `Application.onCreate` ngay lần mở đầu tiên (2 lần trong 7 giây, cùng một bản cài, Pixel 6 Pro arm64, Android 12). | **Đã sửa** ở commit `baba08f`: `NativeLib` lưu lỗi nạp vào `loadError`/`isAvailable` thay vì ném; quảng cáo tắt khi thiếu thư viện; `App` ghi non-fatal kèm split, installer và ABI; `MainActivity` hiện màn hình yêu cầu cài lại từ Google Play. |
| `c49b19cb7773f446a2b69b0bfefe54a8` | `NullPointerException: null cannot be cast to non-null type Episode` tại `PodcastDetailScreen.kt:510` (lambda `clickable`) | 3 / 3 | v3.3.10016 | `.clickable { onItemClick(item as Episode) }`: khi danh sách rỗng màn hình vẽ 20 placeholder shimmer với `item == null`, ô quảng cáo là `Int`. Bấm vào placeholder khi đang tải thì NPE/ClassCastException. | **Đã sửa từ trước** ở `c9f4a1d` (đã có trên `main`): dùng `item as? Episode` và `clickable(enabled = episode != null)`. Mã hiện tại không còn ép kiểu không an toàn (dòng 491 đã được chặn bằng `is Episode`). |
| `a2b0bd4b92822aa18552afe807a84636` | `NullPointerException: null cannot be cast to non-null type Episode` tại `E3.l.b` (bị obfuscate) | 1 / 1 | v2.3.10024 | Cùng lỗi trên. Build của revision `7be7202` vẫn chứa `onItemClick(item as Episode)` ở `PodcastDetailScreen.kt:510`; stack đi qua dispatch chạm của Compose và là cách ép kiểu `Episode` không an toàn duy nhất trong source của build đó. Stack chưa được deobfuscate (thiếu mapping cho build này). | **Đã sửa từ trước** ở `c9f4a1d`. |
| `a53aa8efa6162974a10af91de629c017` | `NullPointerException: null cannot be cast to non-null type Episode` tại `PodcastDetailScreen.kt:510` (`PodcastDetailBody$1$1$4`) | 1 / 1 | v1.4.10010 | Cùng lỗi trên (revision `4ab38dd`, Compose cũ hơn nên tên lambda khác, Crashlytics tách thành issue riêng). | **Đã sửa từ trước** ở `c9f4a1d`. |

## Ghi chú

- Mọi sự kiện NPE `Episode` đều từ cùng một thiết bị (`device_id` 0c7f1054bd55f270 trong breadcrumbs, "OnePlus8Pro" kiến trúc x86_64, Android 11), nhiều khả năng là máy ảo kiểm thử trước khi ra mắt / duyệt của Google Play. Lỗi vẫn là thật: monkey test đã tái hiện được (xem `c9f4a1d`).
- Issue `23322637…`: cần theo dõi non-fatal mới có tag `App.missingNativeLib`. Các key `split_names`, `installer` và `supported_abis` sẽ cho biết đây là sideload hay lỗi đóng gói. Nếu `installer` là `com.android.vending` mà vẫn thiếu split thì cần xem lại cấu hình `bundle { abi { enableSplit } }`.
- Không đóng hay tắt tiếng issue nào trên Crashlytics. Hãy đóng `c49b19cb…`, `a2b0bd4b…`, `a53aa8ef…` sau khi bản có `c9f4a1d` được phát hành, và `23322637…` sau khi bản có `baba08f` được phát hành.

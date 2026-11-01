# Kế hoạch: TSPodcast chạy trên ô tô (Android Auto, sau đó Android Automotive OS)

> **Trạng thái: Chưa làm phần chính** (2026-11-01) — đã có nền móng M0 trên nhánh `tuandv/android-auto`: `PlayerSessionService` thành `MediaLibraryService`, cây duyệt cơ bản (Tiếp tục nghe, Yêu thích, Thịnh hành, show → tập), tìm kiếm, phát bằng giọng nói, playback resumption, khai báo `automotive_app_desc.xml` và meta-data Android Auto.
> Còn lại: thử trên DHU và xe thật (M1), ảnh bìa trong danh sách + đồng bộ UI điện thoại (M2), radio (M3), opt-in Android Auto trên Play Console và qua review (M4), Android Automotive OS (M5).

> Ngày lập: 2026-11-01 · Mốc code: `main` @ `ff429e9` (sau PR #55) · Liên quan: roadmap G5 (`docs/in-progress/research/feature-roadmap-ai-growth.md`), backlog "Android Auto `MediaLibraryService` + `onPlayFromSearch`" và policy #19 (`docs/in-progress/policy/store-policy-review.md`).

---

## 1. Tóm tắt

- **Android Auto trước, Android Automotive OS (AAOS) sau.** Android Auto chạy trên điện thoại và chiếu lên màn hình xe, dùng chính APK/AAB hiện tại. AAOS là hệ điều hành cài sẵn trong xe (Volvo, Polestar, GM, Renault...), cần build và track phát hành riêng nên để sau.
- **App podcast không phải tự vẽ giao diện trên xe.** Android Auto tự dựng giao diện từ "cây duyệt" (browse tree) mà app cung cấp qua `MediaLibraryService` của Media3, và điều khiển cùng `MediaSession` mà màn khoá/thông báo đang dùng. Không cần Car App Library (thư viện đó dành cho app điều hướng, sạc xe, đỗ xe...).
- **M0 đã code xong (nhánh này):** service đổi sang `MediaLibraryService`, cây duyệt lấy dữ liệu từ các repository sẵn có (Room + PodcastIndex), phát tập từ xe dùng chung player, vị trí nghe, tốc độ theo show và "Tiếp tục nghe" với điện thoại.
- **Muốn có trên Play cho người dùng thật:** bật form factor Android Auto trong Play Console, qua review chất lượng ô tô của Google (an toàn lái xe, giọng nói, thông báo lỗi). Review có thể trả về lỗi theo từng tiêu chí, nên phải thử kỹ trên DHU trước khi gửi.

## 2. Phạm vi

| Hạng mục | Trong phạm vi | Ghi chú |
|---|---|---|
| Android Auto (điện thoại chiếu lên xe) | Có, ưu tiên 1 | Dùng chung AAB hiện tại |
| Điều khiển bằng giọng nói ("Ok Google, phát ... trên TSPodcast") | Có | Bắt buộc với app media khi review |
| Radio VOV/VOH trên xe | Có, ở M3 | Use case mạnh ở VN, nhưng cần lấy link phát theo kênh (coreRadio) |
| Android Automotive OS | Có, ở M5 | Cần build riêng, track riêng, có thể cần màn đăng nhập/cài đặt riêng |
| CarPlay (iOS) | Không | Kế hoạch iOS riêng, sau khi iOS lên App Store |
| Video, quảng cáo trên xe | Không | Cấm khi đang lái. Quảng cáo AdMob chỉ ở UI điện thoại, không chạm tới service |

## 3. Hiện trạng code

### 3.1 Trước nhánh này

- `sharedPlayer/.../service/PlayerSessionService.kt` là `MediaSessionService`: chỉ có session để màn khoá, thông báo, tai nghe điều khiển. Không có cây duyệt, nên Android Auto không liệt kê app.
- Manifest app khai báo service `exported="true"` kèm `android:permission="android.permission.FOREGROUND_SERVICE"`, và `onGetSession` chỉ nhận app mình, `android`, `com.android.systemui`. Android Auto (`com.google.android.projection.gearhead`) bị từ chối. Đây cũng là mục policy #19.
- Không có `res/xml/automotive_app_desc.xml`, không có meta-data `com.google.android.gms.car.application`.
- Player: `PlayerManager` (singleton Hilt) giữ một `ExoPlayer`. UI điện thoại (`PlayerViewModel`) gọi thẳng `playerManager.playMedia(...)`, không đi qua `MediaController`. Hàng đợi = các tập của show đang mở, vị trí nghe lưu theo `mediaId` (`PlaybackPositions`), tốc độ theo show (`PlaybackProfiles`, extra `tss.extra.feed_id`).
- "Tiếp tục nghe" chỉ có một tập: id tập cuối ở `SharedPref` key `extra:current_item_id` (`HistoryRepository`), hàng đợi và show nằm trong Room (`EpisodeDao`, `PodcastDao`).
- `TSMediaController` đã tạo cả `MediaController` và `MediaBrowser` tới service. `MediaBrowser` trước đây luôn lỗi kết nối (service không phải library) và bị nuốt bởi `safeCallOrNull`; giờ kết nối được.

### 3.2 Sau nhánh này (M0)

| File | Thay đổi |
|---|---|
| `sharedPlayer/.../service/PlayerSessionService.kt` | `MediaLibraryService` + `MediaLibrarySession.Callback`: root, children, item, search, `onSetMediaItems`/`onAddMediaItems` (biến id thành hàng đợi phát được), `onPlaybackResumption`. Giữ nguyên nút −15s/+30s, notification provider, session activity. Danh sách caller được phép thêm Android Auto, AAOS media center, Google app/Assistant và controller "trusted" của hệ thống |
| `sharedPlayer/.../library/MediaLibraryCatalog.kt` | Nội dung cây duyệt, inject bằng Hilt: `IFavouriteRepository`, `IHistoryRepository`, `EpisodeDao`, `PodcastDao`, `SharedPref`, `GetTrendingPodcasts`, `GetEpisodeByFeedId`, `SearchPodcasts`, `PlayerManager` |
| `sharedPlayer/.../library/LibraryIds.kt` | Quy ước media id + phân trang (có unit test `LibraryIdsTest`) |
| `sharedPlayer/.../library/LibraryMediaItems.kt` | `Episode.toPlayableMediaItem()` (chuyển từ app sang, app gọi lại qua `toMediaItem`), item duyệt cho xe, node show, node thư mục |
| `sharedPlayer/.../player/PlayerManager.kt` | Thêm `resumePositionMs(mediaId)` để hàng đợi do session dựng cũng bắt đầu từ vị trí đã nghe |
| `app/src/main/AndroidManifest.xml`, `sharedPlayer/src/main/AndroidManifest.xml` | Intent-filter `androidx.media3.session.MediaLibraryService` + `android.media.browse.MediaBrowserService`; bỏ `android:permission`; meta-data `com.google.android.gms.car.application` và `com.google.android.gms.car.notification.SmallIcon` |
| `app/src/main/res/xml/automotive_app_desc.xml` | `<uses name="media"/>` |
| `sharedResources/.../values*/strings.xml` | 5 chuỗi cho xe (tab, lỗi), đủ 15 ngôn ngữ |

Hành vi trên điện thoại không đổi: UI vẫn gọi `PlayerManager` như cũ, `Episode.toMediaItem` trả về đúng item như trước (cùng id, extras, metadata).

## 4. Kiến trúc cây duyệt

```
root  (TSPodcast)
├── continue      "Tiếp tục nghe"   dạng list
│   ├── <episodeId>               tập nghe gần nhất (phát được, có vạch tiến độ)
│   └── podcast:<feedId>          show của tập đó (duyệt được)
├── favourites    "Yêu thích"       dạng grid
│   ├── podcast:<feedId>          show đã thích → các tập
│   └── <episodeId>               tập đã thích (phát được)
└── trending      "Thịnh hành"      dạng grid
    └── podcast:<feedId>          50 show thịnh hành 7 ngày, theo ngôn ngữ nội dung trong app
        └── <episodeId>...        các tập, mới nhất trước, dạng list
```

- **Id:** tập dùng đúng id điện thoại đang dùng (`"${episode.id}"`), nên vị trí nghe, yêu thích, tốc độ theo show dùng chung. Node duyệt luôn có tiền tố (`podcast:`) hoặc là hằng (`root`, `continue`, ...), nên một chuỗi số luôn là tập.
- **Tab:** Android Auto hiển thị các con duyệt được của root thành tab (tối đa 4). Hiện có 3 tab. Chưa có icon tab (Auto cần icon vector đơn sắc dạng `android.resource://`), để ở M2.
- **Kiểu hiển thị (content style):** gốc đặt mặc định show = grid ảnh bìa, tập = list. Từng tab đặt lại kiểu cho con của nó (`EXTRAS_KEY_CONTENT_STYLE_BROWSABLE/PLAYABLE`).
- **Tiến độ nghe:** item tập có `EXTRAS_KEY_COMPLETION_STATUS` (chưa nghe / đang nghe dở) và `EXTRAS_KEY_COMPLETION_PERCENTAGE` lấy từ `PlaybackPositions`.
- **Phát:** khi người dùng chạm một tập, Auto gửi id không có URI. `onSetMediaItems` gọi `MediaLibraryCatalog.resolve`: tìm tập (cache → Room), dựng hàng đợi = các tập của show (mạng, cache 15 phút → Room), vị trí bắt đầu = vị trí đã lưu, rồi ghi lịch sử giống điện thoại (`HistoryRepository.saveCurrentPlaying`). Item đã có URI (controller của chính app) đi thẳng, không đổi.
- **Tìm kiếm:** `onSearch`/`onGetSearchResult` dùng `SearchPodcasts` (có lọc blacklist), trả về các show (duyệt được). Media3 tự báo `android.media.browse.SEARCH_SUPPORTED` cho Auto.
- **Giọng nói:** "Ok Google, phát <tên show> trên TSPodcast" đến dưới dạng item có `requestMetadata.searchQuery`: tìm show đầu tiên khớp, phát tập mới nhất. Câu rỗng ("Ok Google, phát TSPodcast") = tiếp tục tập cuối.
- **Tiếp tục phát (playback resumption):** khi xe kết nối và người dùng bấm play mà player trống, `onPlaybackResumption` dựng lại tập cuối + hàng đợi + vị trí. Root "recent" (`params.isRecent`) trả về đúng tập đó cho System UI/Auto.
- **Lỗi:** lỗi mạng trả `SessionError.ERROR_IO` kèm câu "Không tải được danh sách. Kiểm tra kết nối mạng."; id cũ/không còn trả `ERROR_BAD_VALUE` "Không tìm thấy nội dung này". Danh sách rỗng (chưa nghe gì, chưa thích gì) trả về list rỗng, Auto tự hiện trạng thái trống.
- **Luồng thread:** callback Media3 chạy trên main thread; mỗi request chạy trong `CoroutineScope(SupervisorJob + Main.immediate)` của service, `MediaLibraryCatalog` tự chuyển sang `Dispatchers.IO`. Huỷ future thì huỷ coroutine; service huỷ scope ở `onDestroy`.

## 5. Khai báo cần có (đã làm ở M0)

`app/src/main/AndroidManifest.xml`:

```xml
<service
    android:name="tss.t.sharedplayer.service.PlayerSessionService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>

<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
<meta-data
    android:name="com.google.android.gms.car.notification.SmallIcon"
    android:resource="@drawable/ic_stat_tspodcast" />
```

`app/src/main/res/xml/automotive_app_desc.xml`:

```xml
<automotiveApp>
    <uses name="media" />
</automotiveApp>
```

- Đã bỏ `android:permission="android.permission.FOREGROUND_SERVICE"` khỏi service: permission đó là "normal" nên không bảo vệ gì, và có thể chặn host trên xe bind vào. Thay bằng lọc caller trong `onGetSession` (app mình, `android`, `com.android.systemui`, `com.google.android.projection.gearhead`, `com.android.car.media`, `com.google.android.carassistant`, `com.google.android.googlequicksearchbox`, hoặc `ControllerInfo.isTrusted`). Việc này xử lý luôn policy #19.
- Không cần thêm permission mới. `FOREGROUND_SERVICE_MEDIA_PLAYBACK` đã có.
- AAOS (M5) sẽ cần thêm: `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>` ở một build riêng (flavor hoặc module `automotive`), `android:appCategory="audio"`, và activity cài đặt/đăng nhập nếu có. Không thêm vào APK điện thoại.

## 6. Quy tắc chất lượng và an toàn lái xe

Google review app media trên xe theo "Car app quality guidelines" (developer.android.com/docs/quality-guidelines/car-app-quality). Những điểm áp dụng cho TSPodcast:

1. **Không gây xao nhãng:** không video, không hoạt ảnh, không quảng cáo, không chữ dài. Item tập trên xe chỉ có tiêu đề + tên show (không đưa mô tả HTML vào danh sách xe).
2. **Giọng nói:** phải xử lý "phát X trên TSPodcast" và câu rỗng. Đã có ở M0, cần thử trên DHU và xe thật.
3. **Thông báo lỗi rõ ràng, bằng ngôn ngữ người dùng:** mất mạng, nội dung không còn, không phát được (lỗi player đi qua `PlaybackException` của session). Không yêu cầu người dùng thao tác trên điện thoại khi đang lái.
4. **Không đăng nhập trên xe.** TSPodcast không cần tài khoản nên không có vấn đề này.
5. **Điều khiển phát:** play/pause, −15s/+30s, chuyển tập trong hàng đợi. Nút tuỳ chỉnh (tốc độ, hẹn giờ ngủ) nếu thêm phải có icon và tên ngắn.
6. **Ảnh bìa:** nên có ảnh cho show và tập. Android Auto không tự tải ảnh `https://` trong danh sách duyệt; cần content provider (M2). Màn "Đang phát" thì Media3 tự tải bitmap nên vẫn có ảnh.
7. **Độ sâu cây:** giữ nông (root → tab → show → tập = 3 cấp dưới root). Danh sách dài nên giới hạn (trending 50, search 30).
8. **Nội dung:** đã lọc blacklist và explicit theo bộ lọc hiện tại của app. Nội dung trên xe phải theo cùng chính sách nội dung của bản điện thoại.

## 7. Các bước trên Play Console

Tên menu có thể thay đổi theo thời gian, kiểm tra lại khi làm:

1. Phát hành một bản có code M0 (đủ M1–M2 càng tốt) lên track **Internal testing** như thường lệ.
2. Play Console → app TSPodcast → **Test and release → Advanced settings → Form factors** → **Add form factor → Android Auto**.
3. Đọc và chấp nhận **Android Auto terms** (điều khoản riêng cho app trên xe), xác nhận app tuân thủ car app quality guidelines.
4. Gửi bản phát hành (internal → closed → production). Lần đầu bật Android Auto, Google review thêm phần ô tô; thời gian review có thể lâu hơn bản thường. Nếu bị từ chối, email sẽ ghi mã tiêu chí bị vi phạm; sửa rồi gửi lại.
5. Sau khi duyệt, người dùng thấy TSPodcast trong launcher Android Auto khi cài từ Play. **Trước khi duyệt**, chỉ thấy được khi bật "Unknown sources" trong developer settings của Android Auto (xem §8).
6. Cập nhật Data safety nếu có thay đổi (M0 không thu thêm dữ liệu). Cập nhật listing: có thể thêm ảnh chụp màn hình Android Auto (tuỳ chọn).
7. **AAOS (M5):** Form factors → **Android Automotive OS**, AAB riêng có `android.hardware.type.automotive`, track riêng cho Automotive, ảnh chụp màn hình xe; review riêng.

Lưu ý: hiện Play production vẫn chờ secret service account (xem `docs/in-progress/release/play-release-v3.4.10017.md`). Opt-in Android Auto làm cùng lúc với bản production kế tiếp là hợp lý.

## 8. Thử bằng Desktop Head Unit (DHU) trên Windows

DHU giả lập màn hình xe trên máy tính, nối với điện thoại thật qua adb. Không dùng emulator điện thoại cho Android Auto (không chạy được app Android Auto).

**Cài một lần:**

1. Android Studio → **Tools → SDK Manager → SDK Tools** → tick **Android Auto Desktop Head Unit Emulator** → Apply. File chạy nằm ở `%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe`.
2. Điện thoại thật (Android 10+ có Android Auto sẵn trong Cài đặt; máy cũ hơn cài app Android Auto từ Play).
3. Mở cài đặt Android Auto: **Cài đặt → Thiết bị đã kết nối → Lựa chọn ưu tiên kết nối → Android Auto** (hoặc mở app Android Auto).
4. Kéo xuống mục **Phiên bản (Version)**, chạm liên tục khoảng 10 lần, xác nhận bật **chế độ nhà phát triển**.
5. Menu ba chấm → **Developer settings** → bật **Unknown sources** (bắt buộc với bản debug cài bằng adb, nếu không Auto sẽ ẩn app).
6. Menu ba chấm → **Start head unit server**.

**Mỗi lần thử:**

```bash
# Git Bash
./gradlew :app:installDebug
adb forward tcp:5277 tcp:5277
"$LOCALAPPDATA/Android/Sdk/extras/google/auto/desktop-head-unit.exe"
```

PowerShell: `& "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"`.

- Trên DHU, mở launcher → TSPodcast. Kiểm tra 3 tab, mở một show trong Thịnh hành, phát một tập, bấm −15s/+30s, tạm dừng/tiếp tục.
- Giọng nói: gõ `mic begin` trong cửa sổ console của DHU rồi nói (hoặc dùng mic máy tính), ví dụ "Play Tâm sự cùng người lạ on TSPodcast".
- Chế độ ngày/đêm: gõ `day` / `night` trong console DHU để xem ảnh và chữ.
- Lỗi mạng: bật chế độ máy bay trên điện thoại rồi mở tab Thịnh hành, phải thấy câu báo lỗi.
- Log: `adb logcat -s PlayerSessionService MediaSessionService MediaLibraryService` (thêm tag `CarMedia`/`GH.` của Android Auto nếu cần).
- Công cụ phụ: app **Media Controller Test** (mã nguồn trên GitHub `googlesamples/android-media-controller`) để duyệt cây và gọi lệnh mà không cần DHU. Cần cấp quyền Notification access cho nó (khi đó `isTrusted` = true nên session nhận).
- Kiểm thử AAOS (M5): SDK Manager → system image **Automotive with Play Store** → tạo AVD Automotive. Không dùng chung emulator đang được agent khác sử dụng.

## 9. Ghi chú kỹ thuật và phát hiện

1. **Thời lượng tập:** PodcastIndex trả `duration` bằng **giây**, app đang `setDurationMs(duration)` (coi là mili giây). Player lấy thời lượng thật từ luồng nên phát vẫn đúng; giữ nguyên để không đổi hành vi điện thoại. Item duyệt trên xe đã nhân 1000 khi tính phần trăm đã nghe. Nên sửa luôn ở `toPlayableMediaItem` sau khi kiểm UI điện thoại (M2).
2. **UI điện thoại không biết khi xe phát:** `PlayerViewModel` chỉ cập nhật state khi chính nó gọi `playMedia`. Khi tập được phát từ xe rồi mở app trên điện thoại, MiniPlayer có thể chưa hiện đúng tập. Cần lắng nghe `onMediaItemTransition` của player (hoặc chuyển UI sang `MediaController`) — M2.
3. **Ảnh bìa trong danh sách duyệt:** URI `https://` không được Auto tải. Cần `ContentProvider` ánh xạ `content://<applicationId>.artwork/<token>` → tải ảnh qua Coil/OkHttp vào cache, chỉ phục vụ URL do app đã đăng ký (tránh bị app khác dùng để tải URL tuỳ ý) — M2.
4. **Radio:** item radio cần gọi `GetPlayableLink` (coreRadio) để lấy link HLS trước khi phát; `sharedPlayer` chưa phụ thuộc `coreRadio`. Thêm tab "Radio" (VOV/VOH) và resolve trong `MediaLibraryCatalog` — M3. Yêu thích dạng radio hiện bị bỏ qua trong cây.
5. **Hàng đợi khi phát từ xe** cần mạng để lấy danh sách tập (cache 15 phút), fallback Room. Không có mạng và show chưa từng mở → báo lỗi mạng.
6. **"Tiếp tục nghe" chỉ có một tập** vì app chỉ lưu id tập cuối. Muốn danh sách nhiều tập đang nghe dở cần bảng lịch sử trong Room (cũng có ích cho tab Lịch sử trên điện thoại).
7. **`TSMediaController`** giờ kết nối `MediaBrowser` thành công; hiện chưa dùng, không ảnh hưởng.

## 10. Các mốc

| Mốc | Nội dung | Trạng thái |
|---|---|---|
| **M0** Nền móng | `MediaLibraryService`, cây duyệt 3 tab, phát từ xe, tìm kiếm, giọng nói, resumption, khai báo manifest + `automotive_app_desc`, chuỗi 15 ngôn ngữ, unit test id/phân trang | Xong (nhánh `tuandv/android-auto`) |
| **M1** Thử trên DHU + xe thật | Chạy checklist §11 trên DHU và một xe/đầu Android Auto thật; sửa lỗi; thêm test cho `MediaLibraryCatalog` (fake repository) | Chưa làm |
| **M2** Hoàn thiện trải nghiệm | Content provider ảnh bìa; icon tab; đồng bộ `PlayerViewModel` với tập phát từ xe; sửa đơn vị `duration`; nút tuỳ chỉnh tốc độ phát trên xe (tuỳ chọn); lịch sử nhiều tập | Chưa làm |
| **M3** Radio trên xe | Tab Radio (VOV, VOH), resolve link phát, yêu thích radio, câu lệnh "phát VOV1" | Chưa làm |
| **M4** Phát hành Android Auto | Opt-in form factor Android Auto, gửi review, xử lý phản hồi, cập nhật listing/tài liệu | Chưa làm |
| **M5** Android Automotive OS | Build/flavor automotive, emulator AAOS, track riêng, review AAOS | Chưa làm |

## 11. Checklist

**Khai báo và build**
- [x] `PlayerSessionService` là `MediaLibraryService`, intent-filter `MediaLibraryService` + `MediaBrowserService`
- [x] `automotive_app_desc.xml` với `<uses name="media"/>` + meta-data `com.google.android.gms.car.application`
- [x] Icon thông báo cho Auto (`com.google.android.gms.car.notification.SmallIcon`)
- [x] Caller Android Auto/AAOS/Assistant được nhận trong `onGetSession`
- [x] `./gradlew :app:assembleDebug` và `:sharedPlayer:testDebugUnitTest` qua

**Thử trên DHU (M1)**
- [ ] TSPodcast hiện trong launcher Android Auto (đã bật Unknown sources)
- [ ] 3 tab hiện đúng tên theo ngôn ngữ máy
- [ ] Tab Thịnh hành: grid show, mở show ra list tập, phát được
- [ ] Tập đang nghe dở có vạch tiến độ; phát tiếp đúng vị trí
- [ ] −15s/+30s, pause/play, chuyển tập trong hàng đợi
- [ ] Tìm kiếm bằng bàn phím (khi xe dừng) ra danh sách show
- [ ] "Ok Google, play <show> on TSPodcast" phát tập mới nhất
- [ ] "Ok Google, play TSPodcast" tiếp tục tập cuối
- [ ] Kết nối xe khi chưa mở app, bấm play → tiếp tục tập cuối
- [ ] Mất mạng → câu báo lỗi, không treo
- [ ] Phát trên xe xong, mở app điện thoại: tập và vị trí khớp (biết là M2 mới đồng bộ MiniPlayer)
- [ ] Chế độ ngày/đêm đọc rõ
- [ ] Phát trên điện thoại vẫn như cũ (Home → show → tập, thông báo, màn khoá, tai nghe)

**Phát hành (M4)**
- [ ] Ảnh bìa trong danh sách (M2)
- [ ] Opt-in Android Auto trong Play Console, chấp nhận điều khoản
- [ ] Qua review Android for Cars
- [ ] Cập nhật `docs/README.md` và chuyển file này sang `docs/in-progress/` hoặc `docs/done/`

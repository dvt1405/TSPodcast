# Audio playback trên iOS

Đây là phần duy nhất **bắt buộc viết native**, dù chọn hướng kiến trúc nào. Tài liệu ánh xạ từng thành phần Media3 trên Android sang AVFoundation và MediaPlayer framework, kèm code mẫu Swift.

## 1. Ánh xạ Android → iOS

| Android (`sharedPlayer`) | iOS |
|---|---|
| `ExoPlayer` (`PlayerManager.buildPlayer`, `DefaultHttpDataSource`, `setPauseAtEndOfMediaItems(true)`) | `AVPlayer` và một queue tự quản lý (§3) |
| `MediaSessionService` (`PlayerSessionService`), foreground service `mediaPlayback` | `UIBackgroundModes = audio` + `AVAudioSession` category `.playback` |
| `TSNotificationProvider` (notification có prev/play/pause/next) | `MPNowPlayingInfoCenter` (Lock Screen, Control Center, Dynamic Island, CarPlay, Apple Watch) |
| `MediaSession` command | `MPRemoteCommandCenter` |
| `MediaController`/`MediaBrowser` (`TSMediaController`) | Không cần. UI gọi thẳng `AudioEngine`; CarPlay dùng `CPTemplate` |
| `PlayerViewModel` (`Player.Listener.onEvents`, timer 200 ms) | `AudioEngine` publish `PlaybackState` (KVO + periodic time observer) → `PlaybackStateHolder` của shared |
| `MediaItem` + `MediaMetadata` (title, artist, artworkUri, durationMs, mediaType RADIO/PODCAST_EPISODE, extras) | `PlayableItem` của shared (architecture.md §4.1) |
| `progressOf()` chống `C.TIME_UNSET`/NaN | Kiểm tra `CMTime.isNumeric`, `duration.seconds.isFinite` |
| HLS (`media3-exoplayer-hls`) | AVPlayer hỗ trợ HLS sẵn |
| `usesCleartextTraffic=true` | ATS: `NSAllowsArbitraryLoadsForMedia = YES` (setup-and-build.md §3.3) |

## 2. Capabilities và Info.plist

```xml
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
    <string>remote-notification</string> <!-- chỉ khi dùng silent push -->
</array>
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoadsForMedia</key><true/>
</dict>
```

Guideline 2.5.4: background mode `audio` chỉ được dùng để phát audio cho người dùng. Không dùng nó để giữ app sống nhằm sync hay download; download nền dùng `URLSession` background (§8).

## 3. AVPlayer hay AVQueuePlayer?

- **AVQueuePlayer** tự động chuyển sang item kế tiếp và preload, nhưng **không lùi lại được**: item đã phát bị xoá khỏi queue. Nó cũng khó chèn hoặc đảo thứ tự.
- App có nút **Previous** và danh sách "Up next" trong `SlideArea`. Android mặc định dừng ở cuối mỗi episode (`setPauseAtEndOfMediaItems(true)`).

**Khuyến nghị:** một `AVPlayer` duy nhất cùng `PlaybackQueue` tự viết (mảng `PlayableItem` + `currentIndex`). Khi chuyển bài thì gọi `replaceCurrentItem(with:)`. Nếu bật "tự phát tiếp", có thể preload item kế tiếp bằng cách tạo sẵn `AVURLAsset` và gọi `loadValues(forKeys: ["playable","duration"])` khoảng 30 giây trước khi episode hiện tại kết thúc.

```swift
@MainActor
final class AudioEngine: NSObject, PlaybackController {   // PlaybackController: Kotlin protocol
    static let shared = AudioEngine()
    private let player = AVPlayer()
    private var queue: [PlayableItem] = []
    private var index = 0
    private var timeObserver: Any?
    private var observations: [NSKeyValueObservation] = []
    let stateHolder = PlaybackStateHolder()                 // từ shared: expose StateFlow

    var autoAdvance = false                                  // parity: Android dừng cuối item
    var rate: Float = 1.0 { didSet { applyRate() } }

    override init() {
        super.init()
        player.automaticallyWaitsToMinimizeStalling = true
        observations.append(player.observe(\.timeControlStatus, options: [.new]) { [weak self] _, _ in
            Task { @MainActor in self?.publish() }
        })
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] _ in self?.publish(); self?.persistPositionThrottled() }
        NotificationCenter.default.addObserver(self, selector: #selector(itemDidEnd),
            name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(itemFailed),
            name: .AVPlayerItemFailedToPlayToEndTime, object: nil)
        AudioSessionController.shared.configure()
        RemoteCommands.install(engine: self)
    }

    func play(item: PlayableItem, queue: [PlayableItem]) {
        self.queue = queue.isEmpty ? [item] : queue
        self.index = self.queue.firstIndex { $0.id == item.id } ?? 0
        Task { await load(self.queue[index], autoplay: true) }
    }

    private func load(_ item: PlayableItem, autoplay: Bool) async {
        var url = URL(string: item.url)
        if item.type == .radio, let source = item.radioSource {
            url = await RadioResolver.resolve(item: item, source: source) // gọi GetPlayableLink (shared)
        }
        guard let url else { return publishError("invalid_url") }
        let asset = AVURLAsset(url: url, options: [
            AVURLAssetHTTPUserAgentKey: AppConfig.userAgent   // public API, iOS 16+
        ])
        let playerItem = AVPlayerItem(asset: asset)
        playerItem.audioTimePitchAlgorithm = .timeDomain        // hợp với giọng nói khi tăng tốc
        if item.type == .radio { playerItem.preferredForwardBufferDuration = 10 }
        player.replaceCurrentItem(with: playerItem)
        if let resume = PositionStore.position(for: item.id), item.type != .radio {
            await player.seek(to: CMTime(seconds: resume, preferredTimescale: 600))
        }
        AudioSessionController.shared.activate()
        if autoplay { player.playImmediately(atRate: rate) }   // tôn trọng tốc độ đã chọn
        NowPlaying.update(item: item, player: player)
        publish()
    }

    @objc private func itemDidEnd(_ n: Notification) {
        PositionStore.markFinished(queue[index].id)
        if autoAdvance, index + 1 < queue.count { skipNext() } else { player.pause(); publish() }
    }
    // togglePlayPause, seekTo, skipNext, skipPrevious, setRate, publish(), publishError() ...
}
```

### Trạng thái cần publish (khớp `PlayerControlState` của Android)

| Field | Nguồn trên iOS |
|---|---|
| `isPlaying` | `player.timeControlStatus == .playing` |
| `isBuffering` (`isLoading`) | `.waitingToPlayAtSpecifiedRate`, hoặc `item.status == .unknown` |
| `positionMs` | `player.currentTime()` (khi `isNumeric`) |
| `durationMs` | `item.duration`. Nếu `.indefinite` thì đây là live, đặt 0 (giống `progressOf`) |
| `error` | `item.status == .failed`, `item.error`, `AVPlayerItemFailedToPlayToEndTime`, `item.errorLog()` |

### Nhớ vị trí nghe (Android chưa có)

`PositionStore` lưu `episodeId → seconds` mỗi 10 giây, khi pause, và khi app chuyển sang background (`scenePhase == .background`). Lưu vào DataStore hoặc Room qua shared (nên thêm cột `positionMs` hoặc một bảng `PlaybackHistory`). Khi episode còn dưới 30 giây thì coi là đã nghe xong.

## 4. AVAudioSession

```swift
final class AudioSessionController {
    static let shared = AudioSessionController()
    private let session = AVAudioSession.sharedInstance()

    func configure() {
        do {
            // Podcast: .spokenAudio. Hệ thống sẽ pause (không duck) app khác khi có lời nói
            // chen vào, ví dụ chỉ đường. Radio âm nhạc có thể đổi mode sang .default.
            // .longFormAudio: route AirPlay 2 cho nội dung dài, giống Apple Podcasts.
            try session.setCategory(.playback, mode: .spokenAudio, policy: .longFormAudio)
        } catch { Crash.shared.record(error, tag: "AudioSession.configure") }
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(onInterruption),
                       name: AVAudioSession.interruptionNotification, object: session)
        nc.addObserver(self, selector: #selector(onRouteChange),
                       name: AVAudioSession.routeChangeNotification, object: session)
        nc.addObserver(self, selector: #selector(onMediaServicesReset),
                       name: AVAudioSession.mediaServicesWereResetNotification, object: session)
    }

    func activate() { try? session.setActive(true) }   // chỉ gọi ngay trước khi play
}
```

- Chỉ `setActive(true)` khi người dùng bấm play, không gọi lúc launch, để không cắt nhạc của app khác.
- Khi dừng hẳn (người dùng đóng player), gọi `setActive(false, options: .notifyOthersOnDeactivation)` để app khác phát tiếp.

### 4.1 Interruption (cuộc gọi, Siri, báo thức)

```swift
@objc func onInterruption(_ n: Notification) {
    guard let info = n.userInfo,
          let raw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
          let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
    switch type {
    case .began:
        // Hệ thống đã pause player. Chỉ cần cập nhật UI và lưu vị trí.
        AudioEngine.shared.didPauseBySystem()
    case .ended:
        let opts = AVAudioSession.InterruptionOptions(
            rawValue: info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
        if opts.contains(.shouldResume) { AudioEngine.shared.resumeIfWasPlaying() }
    @unknown default: break
    }
}
```

Lưu ý: iOS 14.5+ có thể gửi `.began` kèm `AVAudioSessionInterruptionReasonKey == .appWasSuspended` khi app bị suspend; trường hợp này không phải ngắt quãng thật. Lỗi hay gặp: không resume sau cuộc gọi, hoặc tự resume khi người dùng đã chủ động pause trước đó. Phải ghi nhớ `wasPlayingBeforeInterruption`.

### 4.2 Route change (rút tai nghe, ngắt Bluetooth)

```swift
@objc func onRouteChange(_ n: Notification) {
    guard let raw = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
          let reason = AVAudioSession.RouteChangeReason(rawValue: raw) else { return }
    if reason == .oldDeviceUnavailable { AudioEngine.shared.pause() }   // chuẩn HIG: rút tai nghe thì pause
}
```

`mediaServicesWereReset`: tạo lại `AVPlayer`, cấu hình lại session và remote commands, rồi load lại item hiện tại ở vị trí cũ.

## 5. Now Playing và Remote Commands

### 5.1 `MPNowPlayingInfoCenter`

```swift
enum NowPlaying {
    static func update(item: PlayableItem, player: AVPlayer) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: item.title,
            MPMediaItemPropertyArtist: item.artist ?? item.album ?? "TSPodcast",
            MPMediaItemPropertyAlbumTitle: item.album ?? "",
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyIsLiveStream: item.type == .radio,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: player.currentTime().seconds.finiteOrZero,
            MPNowPlayingInfoPropertyPlaybackRate: player.rate,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: AudioEngine.shared.rate,
        ]
        if let d = player.currentItem?.duration, d.isNumeric, item.type != .radio {
            info[MPMediaItemPropertyPlaybackDuration] = d.seconds
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        ArtworkLoader.load(item.artworkUrl) { image in           // tải async, có User-Agent
            var i = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? info
            i[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = i
        }
    }
}
```

- Cập nhật `ElapsedPlaybackTime` và `PlaybackRate` khi play, pause, seek hoặc đổi rate. **Không** cập nhật mỗi 0,5 giây; hệ thống tự nội suy từ rate.
- Với radio: `IsLiveStream = true` và không đặt duration, khi đó Lock Screen không hiện thanh seek. Nếu có ICY/ID3 metadata ("StreamTitle") thì cập nhật `MPMediaItemPropertyTitle` theo bài đang phát (§6.2).
- Artwork: tạo `MPMediaItemArtwork` từ ảnh đã resize (khoảng 600 px) để tránh tốn bộ nhớ.

### 5.2 `MPRemoteCommandCenter`

| Command | Podcast episode | Radio |
|---|---|---|
| `playCommand`, `pauseCommand`, `togglePlayPauseCommand` | ✔ | ✔ |
| `skipForwardCommand` (`preferredIntervals = [30]`) | ✔ | ✘ (`isEnabled = false`) |
| `skipBackwardCommand` (`preferredIntervals = [15]`) | ✔ | ✘ |
| `changePlaybackPositionCommand` (kéo thanh seek trên Lock Screen) | ✔ | ✘ |
| `nextTrackCommand` / `previousTrackCommand` | Tuỳ chọn. Apple Podcasts ưu tiên skip ±; bật cả hai sẽ khiến Lock Screen hiện next/prev thay vì skip | ✔ chuyển kênh (parity với Android: prev/next trong danh sách radio) |
| `changePlaybackRateCommand` (`supportedPlaybackRates = [0.5,0.75,1,1.25,1.5,1.75,2,2.5,3]`) | ✔ | ✘ |

```swift
enum RemoteCommands {
    static func install(engine: AudioEngine) {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { _ in engine.resume(); return .success }
        c.pauseCommand.addTarget { _ in engine.pause(); return .success }
        c.togglePlayPauseCommand.addTarget { _ in engine.togglePlayPause(); return .success }
        c.skipForwardCommand.preferredIntervals = [30]
        c.skipForwardCommand.addTarget { _ in engine.skip(by: 30); return .success }
        c.skipBackwardCommand.preferredIntervals = [15]
        c.skipBackwardCommand.addTarget { _ in engine.skip(by: -15); return .success }
        c.changePlaybackPositionCommand.addTarget { e in
            guard let e = e as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            engine.seek(toSeconds: e.positionTime); return .success
        }
        c.changePlaybackRateCommand.supportedPlaybackRates = [0.5,0.75,1,1.25,1.5,1.75,2,2.5,3]
        c.changePlaybackRateCommand.addTarget { e in
            guard let e = e as? MPChangePlaybackRateCommandEvent else { return .commandFailed }
            engine.rate = e.playbackRate; return .success
        }
    }
    static func configure(for type: MediaType) { /* bật/tắt command theo bảng trên */ }
}
```

## 6. Radio stream: HLS và ICY

### 6.1 HLS (`.m3u8`)

- Link VOV/VOH resolve được thường là HLS. AVPlayer hỗ trợ sẵn, kể cả live sliding window.
- Live HLS có `duration == .indefinite`. Muốn hiện "LIVE" thì dùng `item.seekableTimeRanges`.
- Nếu stream yêu cầu header `Referer` hoặc cookie (link VOV được resolve sau khi gửi cookie và referer trong `JsoupExt.connect`):
  - `AVURLAssetHTTPCookiesKey` là API public, dùng để truyền cookie.
  - **Không có key public cho header tuỳ ý.** Key `AVURLAssetHTTPHeaderFieldsKey` không có trong tài liệu chính thức, nên tránh dùng vì là rủi ro review (2.5.1, dùng API không công khai).
  - Nếu bắt buộc phải có Referer, dùng `AVAssetResourceLoaderDelegate` với custom scheme (chỉ phù hợp cho file/progressive, rất phức tạp với HLS), hoặc tốt hơn là **proxy phía server**.
- Thử nghiệm với Charles hoặc Proxyman: so sánh request Android (ExoPlayer `DefaultHttpDataSource`, UA mặc định) với iOS.

### 6.2 ICY / Shoutcast / Icecast (MP3/AAC progressive)

- AVPlayer phát được stream HTTP MP3/AAC liên tục.
- Metadata ICY (`StreamTitle`): dùng `AVPlayerItemMetadataOutput` (thuộc tính `timedMetadata` đã deprecated):

```swift
let output = AVPlayerItemMetadataOutput(identifiers: nil)
output.setDelegate(self, queue: .main)
playerItem.add(output)
// delegate: metadataOutput(_:didOutputTimedMetadataGroups:from:) → tìm item có key "StreamTitle"
```

- Một số server Shoutcast v1 trả status line `ICY 200 OK` thay vì `HTTP/1.1 200 OK`, và AVPlayer sẽ báo lỗi. Cách xử lý: thử URL thay thế (thường có `/;` hoặc cổng HTTP khác), hoặc đi qua proxy.
- Stream `http://` cần `NSAllowsArbitraryLoadsForMedia`. Key này chỉ áp dụng cho AVFoundation, không mở ATS cho `URLSession`. Việc scrape bằng Ktor tới `http://vovmedia.vn/` vẫn bị chặn, nên phải dùng `https` (kiểm tra xem site có hỗ trợ không) hoặc khai exception riêng cho domain này (`NSExceptionDomains`).

### 6.3 Xử lý lỗi radio

| Tình huống | Hành vi |
|---|---|
| `GetPlayableLink` thất bại (parser vỡ) | Hiện toast/banner "Kênh tạm thời không phát được", ghi `Crash.record` (đã có ở shared) |
| `item.status == .failed` | Retry 1 lần với link vừa resolve lại, sau đó báo lỗi |
| Mạng chập chờn | `automaticallyWaitsToMinimizeStalling`; quan sát `AVPlayerItemPlaybackStalled` để hiện trạng thái buffering |

## 7. Tốc độ phát và sleep timer (iOS+)

**Tốc độ phát**

- Cài đặt:
  - iOS 16+: `player.defaultRate = r`, sau đó nếu đang phát thì `player.rate = r`;
  - phiên bản thấp hơn: `playImmediately(atRate:)`.
- `audioTimePitchAlgorithm = .timeDomain` cho giọng nói (giữ cao độ); `.spectral` cho chất lượng cao hơn khi phát nhạc.
- Lưu tốc độ theo từng podcast (`feedId → rate`) hoặc một giá trị chung.
- Cập nhật `MPNowPlayingInfoPropertyPlaybackRate` và `DefaultPlaybackRate`.

**Sleep timer**

- Tuỳ chọn: 5/10/15/30/45/60 phút và "Hết tập này".
- Dùng `Task.sleep` hoặc `DispatchSourceTimer` trên main. Khi audio đang phát, app vẫn chạy ở background nên timer hoạt động bình thường.
- Fade-out 5–10 giây cuối bằng `player.volume`, sau đó `pause()` rồi khôi phục volume.
- "Hết tập này": đặt cờ, khi `itemDidEnd` thì không tự chuyển bài.
- Nếu người dùng tương tác (play/pause) trong lúc fade thì huỷ fade.

## 8. Download offline bằng URLSession background (iOS+)

Guideline 5.2.3 cấm "save, convert, or download media from third-party sources without explicit authorization". Enclosure RSS của podcast được publisher phát hành để tải về (đó là cách RSS podcast vận hành), nên nằm trong phạm vi chấp nhận được. **Tuyệt đối không** cho phép ghi lại hoặc tải stream radio.

```swift
final class DownloadManager: NSObject, URLSessionDownloadDelegate {
    static let shared = DownloadManager()
    static let identifier = "tss.t.podcast.downloads"
    lazy var session: URLSession = {
        let cfg = URLSessionConfiguration.background(withIdentifier: Self.identifier)
        cfg.isDiscretionary = false
        cfg.sessionSendsLaunchEvents = true
        cfg.allowsCellularAccess = Settings.allowCellularDownloads
        cfg.httpAdditionalHeaders = ["User-Agent": AppConfig.userAgent]
        return URLSession(configuration: cfg, delegate: self, delegateQueue: nil)
    }()
    var backgroundCompletion: (() -> Void)?

    func download(_ ep: PlayableItem) {
        guard hasFreeSpace(bytes: ep.expectedBytes) else { return notifyNoSpace() }
        let task = session.downloadTask(with: URL(string: ep.url)!)
        task.taskDescription = ep.id               // map ngược lại khi hoàn tất
        task.resume()
    }

    func urlSession(_ s: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo tmp: URL) {
        // PHẢI move file ngay trong callback này: tmp sẽ bị xoá khi hàm return
        var dest = Paths.downloads.appendingPathComponent("\(downloadTask.taskDescription!).audio")
        try? FileManager.default.moveItem(at: tmp, to: dest)
        var rv = URLResourceValues(); rv.isExcludedFromBackup = true
        try? dest.setResourceValues(rv)
        // ghi trạng thái vào shared DB (bảng Download)
    }
    func urlSessionDidFinishEvents(forBackgroundURLSession s: URLSession) {
        DispatchQueue.main.async { self.backgroundCompletion?(); self.backgroundCompletion = nil }
    }
}

// AppDelegate (qua @UIApplicationDelegateAdaptor)
func application(_ app: UIApplication, handleEventsForBackgroundURLSession id: String,
                 completionHandler: @escaping () -> Void) {
    DownloadManager.shared.backgroundCompletion = completionHandler
    _ = DownloadManager.shared.session               // khởi tạo lại session cùng identifier
}
```

- Thư mục: `Library/Application Support/Downloads`, đặt `isExcludedFromBackup = true`. Không lưu vào `Caches`, vì hệ thống có thể xoá file ở đó khi thiếu dung lượng.
- Kiểm tra dung lượng trống bằng `volumeAvailableCapacityForImportantUsageKey`. Đây là **required reason API** (Disk space): khai `E174.1` trong `PrivacyInfo.xcprivacy`, và `85F4.1` nếu hiển thị dung lượng cho người dùng.
- Hỗ trợ resume: lưu `resumeData` khi bị huỷ hoặc lỗi.
- Phát file đã tải: `AVURLAsset(url: localFileURL)`. Ưu tiên file local nếu có.
- Dọn dẹp: xoá file sau khi nghe xong (tuỳ chọn), có màn quản lý dung lượng.
- **Điều khoản PodcastIndex:** file audio đến từ máy chủ của publisher, không phải dữ liệu API. Metadata đi kèm vẫn tuân theo `PruneApiCache` (đánh dấu là "đã tải" để không bị prune).

## 9. CarPlay

1. **Xin entitlement:** điền form CarPlay trên developer.apple.com và đồng ý CarPlay Entitlement Addendum, chọn hạng mục **Audio**. Apple duyệt rồi thêm entitlement `com.apple.developer.carplay-audio` (Boolean) vào account dưới dạng managed capability. **Nộp đơn ngay từ tuần đầu** vì thời gian duyệt không cố định. Key cũ `com.apple.developer.playable-content` đã deprecated.
2. **Provisioning:** bật capability trong App ID, tạo lại profile, rồi thêm vào `.entitlements`.
3. **Scene:**

```xml
<key>UIApplicationSceneManifest</key>
<dict>
  <key>UIApplicationSupportsMultipleScenes</key><true/>
  <key>UISceneConfigurations</key>
  <dict>
    <key>CPTemplateApplicationSceneSessionRoleApplication</key>
    <array><dict>
      <key>UISceneConfigurationName</key><string>CarPlay</string>
      <key>UISceneDelegateClassName</key><string>$(PRODUCT_MODULE_NAME).CarPlaySceneDelegate</string>
    </dict></array>
    <!-- UIWindowSceneSessionRoleApplication cho app chính -->
  </dict>
</dict>
```

```swift
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    var ui: CPInterfaceController?
    func templateApplicationScene(_ scene: CPTemplateApplicationScene,
                                  didConnect ui: CPInterfaceController) {
        self.ui = ui
        let tabs = CPTabBarTemplate(templates: [
            CarPlayLists.favourites(), CarPlayLists.trending(), CarPlayLists.downloads()
            // + CarPlayLists.radio() nếu D1 = có
        ])
        ui.setRootTemplate(tabs, animated: false, completion: nil)
    }
}
// Item: CPListItem(text:detailText:image:) với handler { _, done in AudioEngine.shared.play(...);
//        ui.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil); done() }
```

- Giới hạn: số item trong một list (`CPListTemplate.maximumItemCount`), số tab, độ sâu điều hướng. Phải kiểm tra lúc runtime, không hardcode.
- `CPNowPlayingTemplate.shared` lấy dữ liệu từ `MPNowPlayingInfoCenter` và `MPRemoteCommandCenter`, nên §5 phải đúng trước. Bật `isUpNextButtonEnabled` để mở playlist.
- Không cho phép tìm kiếm bằng bàn phím khi đang lái xe. Dữ liệu lấy từ shared (`SelectAllFavourite`, `GetTrendingPodcasts`) và đã có cache.
- Test bằng **CarPlay Simulator** (trong "Additional Tools for Xcode") hoặc Simulator → I/O → External Displays → CarPlay.

## 10. Tích hợp hệ thống khác (khuyến nghị)

- **AirPlay:** đặt `AVRoutePickerView` (bọc qua `UIViewRepresentable`) trên Player. `policy: .longFormAudio` giúp route đúng.
- **Siri và App Intents / Shortcuts:** `PlayPodcastIntent` ("Mở TSPodcast phát tiếp"). Tăng giá trị "app-like" cho review.
- **Widget / Live Activity:** không bắt buộc. Now Playing đã hiện trên Lock Screen và Dynamic Island.
- **Handoff / Continuity:** không cần cho v1.

## 11. Checklist test audio

- [ ] Phát → khoá màn hình → audio vẫn chạy 30 phút; điều khiển được từ Lock Screen và Control Center
- [ ] Cuộc gọi đến hoặc FaceTime → pause → kết thúc cuộc gọi → tự resume (chỉ khi trước đó đang phát)
- [ ] Siri, báo thức, video app khác → xử lý interruption đúng
- [ ] Rút tai nghe dây hoặc tắt AirPods → pause
- [ ] Nút trên AirPods (double tap = next/skip) và nút trên tai nghe có dây
- [ ] Seek trên Lock Screen; skip ±; đổi tốc độ từ Control Center
- [ ] Radio live: không có thanh seek, hiện "LIVE", chuyển kênh bằng next/prev
- [ ] Mất mạng giữa chừng → buffering → có mạng lại thì tiếp tục
- [ ] Mở lại app sau khi bị kill → khôi phục item và vị trí cuối
- [ ] CarPlay: duyệt danh sách, phát, Now Playing, Up Next
- [ ] Sleep timer có fade; "Hết tập này"
- [ ] Download nền: kill app trong lúc tải → hoàn tất → file phát được offline
- [ ] VoiceOver đọc đúng các nút (accessibility label tiếng Việt và tiếng Anh)

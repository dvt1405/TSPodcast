import AppTrackingTransparency
import Foundation
import GoogleMobileAds
import Observation
import UIKit
import UserMessagingPlatform
@preconcurrency import Shared

/// Where the ad layer stands (Android `AdsState`). `effectiveState` adds `disabled("premium")`.
enum AdsState: Equatable, Sendable {
    /// `start()` not called yet.
    case idle
    /// UMP consent info update / form in progress.
    case gatheringConsent
    /// Consent flow done; `canRequestAds` false = regulated region without the consent ads need.
    case ready(canRequestAds: Bool)
    /// Ads off: `kill_switch`, `premium`, …
    case disabled(reason: String)
}

/// What the ad layer reads from the app (Android `AdsHostSignals`). Main actor.
struct AdsHost {
    /// Audio is playing or buffering.
    var isPlaybackActive: @MainActor () -> Bool
    /// The full-screen Player is shown.
    var isOnPlayerScreen: @MainActor () -> Bool

    static let none = AdsHost(isPlaybackActive: { false }, isOnPlayerScreen: { false })
}

/// AdMob for iOS (Google Mobile Ads + UMP), the counterpart of Android `AdMobAdsController`
/// (contract docs/in-progress/plans/monetization-ads-iap.md §4).
///
/// Start (once Main is on screen and the app's own privacy sheet is closed): UMP consent info
/// update + form when required → ATT when still undetermined (never blocks the app, 5.1.2) →
/// `MobileAds.shared.start` → preload the enabled full-screen ads. Nothing is requested while UMP's
/// `canRequestAds` is false. Every ad is also gated by `ads_enabled`, `ads_placements_enabled`,
/// `ads_min_app_version`, shared `AdPolicy` (grace, first sessions, caps, gaps, never over audio
/// or on the Player) and Premium, which switches everything off at once (`disabled("premium")`).
///
/// App Open (`app_open_warm`): only on a return to the foreground after ≥ 30 s (policy), only an
/// ad that is already loaded, never on a deep-link / notification launch. Interstitial
/// (`interstitial_nav`): when the user leaves a show, every Nth time (policy), never load-then-show.
/// Banners and natives load per slot and are kept, so scrolling back does not request again.
@MainActor
@Observable
final class AdsController {
    private(set) var state: AdsState = .idle
    /// UMP wants a "privacy options" entry point (Settings row).
    private(set) var privacyOptionsRequired = false
    /// Premium (`ad_free`): set by `StoreModel`, starts from its cached value.
    private(set) var isPremium: Bool
    /// The SDK finished `start`.
    private(set) var isSDKReady = false
    private(set) var canRequestAds = false

    @ObservationIgnored let config: MonetizationConfig
    @ObservationIgnored let policy: AdPolicy
    @ObservationIgnored let analytics: AdsAnalytics
    @ObservationIgnored var host: AdsHost
    @ObservationIgnored private let counters: any AdCounterStore
    @ObservationIgnored private var sessionStarted = false
    @ObservationIgnored private var consentGathered = false
    @ObservationIgnored private var consentInFlight = false
    @ObservationIgnored private var sdkStartRequested = false
    @ObservationIgnored private var backgroundAt: Date?
    @ObservationIgnored private var externalLaunch = false
    @ObservationIgnored private var lastPlaybackAt: Date?
    @ObservationIgnored private var naturalBreaks: Int32 = 0
    @ObservationIgnored private var fullScreenShowing = false
    @ObservationIgnored private var interstitial: (ad: InterstitialAd, loadedAt: Date)?
    @ObservationIgnored private var appOpen: (ad: AppOpenAd, loadedAt: Date)?
    @ObservationIgnored private var loadingFullScreen: Set<String> = []
    @ObservationIgnored private var fullScreenEvents: [String: FullScreenEvents] = [:]
    @ObservationIgnored private var slots: [String: InlineAdSlot] = [:]

    init(
        config: MonetizationConfig,
        counters: any AdCounterStore = UserDefaultsAdCounterStore(),
        clock: any AdClock = SystemAdClock(),
        analytics: AdsAnalytics,
        isPremium: Bool,
        host: AdsHost = .none
    ) {
        self.config = config
        self.counters = counters
        self.policy = AdPolicy(store: counters, clock: clock)
        self.analytics = analytics
        self.isPremium = isPremium
        self.host = host
    }

    /// `state`, with Premium winning over everything.
    var effectiveState: AdsState { isPremium ? .disabled(reason: "premium") : state }

    /// Consent allows requests, the SDK is up, ads are on and the user is not Premium.
    var canLoadAds: Bool {
        isSDKReady && canRequestAds && !isPremium && config.ads().adsEnabled
    }

    /// Banner / native gate: `canLoadAds` + placement switch + an active ad-free reward.
    func allowsInline(_ placement: AdPlacement) -> Bool {
        guard canLoadAds else { return false }
        return policy.canShowInline(placement: placement, settings: config.ads(), canRequestAds: true) is DecisionAllow
    }

    // MARK: - Start and consent

    /// Idempotent: counts the session once, then consent → ATT → SDK.
    func start() {
        if !sessionStarted {
            sessionStarted = true
            counters.startSession()
        }
        guard !isPremium else { return }
        guard config.ads().adsEnabled else {
            state = .disabled(reason: "kill_switch")
            AppLog.ads.notice("ads_disabled reason=kill_switch")
            return
        }
        // Consent from an earlier launch already allows requests: start the SDK alongside this
        // launch's consent refresh (Google's recommended order).
        if ConsentInformation.shared.canRequestAds {
            canRequestAds = true
            startSDK()
        }
        guard !consentGathered, !consentInFlight else { return }
        consentInFlight = true
        if case .ready = state {} else { state = .gatheringConsent }
        Task { await gatherConsent() }
    }

    private func gatherConsent() async {
        var errorCode: Int?
        do {
            try await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
            try await ConsentForm.loadAndPresentIfRequired(from: nil)
        } catch {
            // UMP keeps the previous session's consent on a network failure.
            errorCode = (error as NSError).code
        }
        consentInFlight = false
        consentGathered = true
        applyConsent(errorCode: errorCode)
        if canRequestAds {
            await requestTrackingIfNeeded()
            startSDK()
        }
    }

    private func applyConsent(errorCode: Int?) {
        let info = ConsentInformation.shared
        canRequestAds = info.canRequestAds
        privacyOptionsRequired = info.privacyOptionsRequirementStatus == .required
        let status = Self.consentStatusName(info.consentStatus)
        analytics.consent(status: status, canRequestAds: canRequestAds, errorCode: errorCode)
        AppLog.ads.notice("consent status=\(status, privacy: .public) can_request_ads=\(self.canRequestAds, privacy: .public) error=\(errorCode ?? 0, privacy: .public)")
        state = .ready(canRequestAds: canRequestAds)
    }

    /// ATT after UMP (UMP's IDFA explainer may already have asked). Only while the app is
    /// active: a request from the background is dropped without a prompt.
    private func requestTrackingIfNeeded() async {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined,
              UIApplication.shared.applicationState == .active else { return }
        let status = await ATTrackingManager.requestTrackingAuthorization()
        AppLog.ads.notice("att status=\(status.rawValue, privacy: .public)")
    }

    private func startSDK() {
        guard !sdkStartRequested, !isPremium else { return }
        sdkStartRequested = true
        MobileAds.shared.start(completionHandler: { [weak self] _ in
            Task { @MainActor in self?.sdkDidStart() }
        })
    }

    private func sdkDidStart() {
        isSDKReady = true
        AppLog.ads.notice("mobile_ads_started debug=\(self.config.debug, privacy: .public)")
        preloadFullScreen()
    }

    /// Settings → "Tuỳ chọn quyền riêng tư quảng cáo" (UMP).
    func showPrivacyOptions() {
        Task {
            do {
                try await ConsentForm.presentPrivacyOptionsForm(from: nil)
            } catch {
                AppLog.ads.error("privacy_options_failed code=\((error as NSError).code, privacy: .public)")
            }
            applyConsent(errorCode: nil)
            if canRequestAds { startSDK() } else { dropLoadedAds() }
        }
    }

    // MARK: - Premium

    /// Premium on: every ad goes at once (views read `allowsInline`), nothing loads any more.
    func setPremium(_ premium: Bool) {
        guard premium != isPremium else { return }
        isPremium = premium
        AppLog.ads.notice("premium=\(premium, privacy: .public)")
        if premium {
            dropLoadedAds()
        } else {
            start()
            if isSDKReady { preloadFullScreen() }
        }
    }

    private func dropLoadedAds() {
        interstitial = nil
        appOpen = nil
        slots.removeAll()
    }

    // MARK: - App signals

    /// The foreground was opened by a deep link or a notification: no App Open this time.
    func onExternalLaunch() {
        externalLaunch = true
    }

    func didEnterBackground() {
        backgroundAt = Date()
        externalLaunch = false
    }

    /// Return to the foreground. Waits a moment so a deep link / notification tap of this same
    /// foreground arrives first (`onExternalLaunch`).
    func didBecomeActive() {
        guard let backgroundAt else { return }
        self.backgroundAt = nil
        let backgroundMs = Int64(Date().timeIntervalSince(backgroundAt) * 1000)
        Task {
            try? await Task.sleep(for: .milliseconds(500))
            maybeShowAppOpen(backgroundMs: backgroundMs)
        }
    }

    /// Audio started or stopped: `ads_interstitial_after_playback_sec` counts from the last audio.
    func playbackChanged(isActive: Bool) {
        lastPlaybackAt = Date()
    }

    private func triggerContext(backgroundMs: Int64? = nil, breakIndex: Int32 = 0, dwellMs: Int64? = nil) -> TriggerContext {
        TriggerContext(
            canRequestAds: canLoadAds,
            playbackActive: host.isPlaybackActive(),
            onPlayerScreen: host.isOnPlayerScreen(),
            externalLaunch: externalLaunch,
            // Ads start only on Main, i.e. after onboarding and the category choice.
            onboardingCompleted: true,
            backgroundDurationMs: backgroundMs.map { KotlinLong(value: $0) },
            naturalBreakIndex: breakIndex,
            screenDwellMs: dwellMs.map { KotlinLong(value: $0) },
            sincePlaybackMs: lastPlaybackAt.map { KotlinLong(value: Int64(Date().timeIntervalSince($0) * 1000)) }
        )
    }

    // MARK: - Full screen

    /// Google: an interstitial should be shown within an hour of loading, App Open within 4 hours.
    static let interstitialMaxAge: TimeInterval = 3_600
    static let appOpenMaxAge: TimeInterval = 4 * 3_600

    private func preloadFullScreen() {
        guard canLoadAds else { return }
        let settings = config.ads()
        if settings.isEnabled(placement: .interstitialnav) { loadInterstitial() }
        if settings.isEnabled(placement: .appopenwarm) { loadAppOpen() }
    }

    private func loadInterstitial() {
        let placement = AdPlacement.interstitialnav
        if let interstitial, Date().timeIntervalSince(interstitial.loadedAt) < Self.interstitialMaxAge { return }
        guard canLoadAds, !loadingFullScreen.contains(placement.key) else { return }
        loadingFullScreen.insert(placement.key)
        analytics.request(placement)
        let unitID = config.adUnitID(for: placement)
        let startedAt = Date()
        Task {
            do {
                let ad = try await InterstitialAd.load(with: unitID, request: Request())
                loadingFullScreen.remove(placement.key)
                analytics.loaded(placement, latencyMs: Self.ms(since: startedAt), adSource: ad.responseInfo.loadedAdNetworkResponseInfo?.adSourceName)
                guard canLoadAds else { return }
                interstitial = (ad, Date())
            } catch {
                loadingFullScreen.remove(placement.key)
                analytics.loadFailed(placement, code: (error as NSError).code, latencyMs: Self.ms(since: startedAt))
            }
        }
    }

    private func loadAppOpen() {
        let placement = AdPlacement.appopenwarm
        if let appOpen, Date().timeIntervalSince(appOpen.loadedAt) < Self.appOpenMaxAge { return }
        guard canLoadAds, !loadingFullScreen.contains(placement.key) else { return }
        loadingFullScreen.insert(placement.key)
        analytics.request(placement)
        let unitID = config.adUnitID(for: placement)
        let startedAt = Date()
        Task {
            do {
                let ad = try await AppOpenAd.load(with: unitID, request: Request())
                loadingFullScreen.remove(placement.key)
                analytics.loaded(placement, latencyMs: Self.ms(since: startedAt), adSource: ad.responseInfo.loadedAdNetworkResponseInfo?.adSourceName)
                guard canLoadAds else { return }
                appOpen = (ad, Date())
            } catch {
                loadingFullScreen.remove(placement.key)
                analytics.loadFailed(placement, code: (error as NSError).code, latencyMs: Self.ms(since: startedAt))
            }
        }
    }

    private func maybeShowAppOpen(backgroundMs: Int64) {
        let placement = AdPlacement.appopenwarm
        guard !isPremium, !fullScreenShowing else { return }
        let decision = policy.canShowFullScreen(placement: placement, settings: config.ads(), ctx: triggerContext(backgroundMs: backgroundMs))
        if let block = decision as? DecisionBlock {
            analytics.blocked(placement, reason: block.reason)
            return
        }
        guard let loaded = appOpen, Date().timeIntervalSince(loaded.loadedAt) < Self.appOpenMaxAge else {
            appOpen = nil
            analytics.blocked(placement, reason: "not_preloaded")
            loadAppOpen()
            return
        }
        appOpen = nil
        present(loaded.ad, placement: placement)
    }

    /// The user left a show (natural break). Shows a loaded interstitial when the policy allows,
    /// after the pop animation.
    func didLeaveShow(dwell: TimeInterval) {
        let placement = AdPlacement.interstitialnav
        guard !isPremium, !fullScreenShowing else { return }
        naturalBreaks += 1
        let ctx = triggerContext(breakIndex: naturalBreaks, dwellMs: Int64(dwell * 1000))
        let decision = policy.canShowFullScreen(placement: placement, settings: config.ads(), ctx: ctx)
        if let block = decision as? DecisionBlock {
            analytics.blocked(placement, reason: block.reason)
            return
        }
        guard let loaded = interstitial, Date().timeIntervalSince(loaded.loadedAt) < Self.interstitialMaxAge else {
            interstitial = nil
            analytics.blocked(placement, reason: "not_preloaded")
            loadInterstitial()
            return
        }
        interstitial = nil
        Task {
            try? await Task.sleep(for: .milliseconds(450))
            guard !isPremium, !fullScreenShowing, !host.isPlaybackActive(), !host.isOnPlayerScreen() else { return }
            present(loaded.ad, placement: placement)
        }
    }

    private func present(_ ad: some FullScreenPresentingAd & AnyObject, placement: AdPlacement) {
        let events = FullScreenEvents(placement: placement, controller: self)
        fullScreenEvents[placement.key] = events
        ad.fullScreenContentDelegate = events
        fullScreenShowing = true
        AppLog.ads.notice("full_screen_present placement=\(placement.key, privacy: .public)")
        if let interstitial = ad as? InterstitialAd {
            interstitial.present(from: nil)
        } else if let appOpen = ad as? AppOpenAd {
            appOpen.present(from: nil)
        }
    }

    fileprivate func fullScreenWillPresent(_ placement: AdPlacement) {
        policy.recordShown(placement: placement)
        analytics.show(placement)
    }

    fileprivate func fullScreenEnded(_ placement: AdPlacement, errorCode: Int?) {
        fullScreenShowing = false
        fullScreenEvents[placement.key] = nil
        if let errorCode {
            analytics.showFailed(placement, code: errorCode)
        } else {
            analytics.dismiss(placement)
        }
        // Load the next one for a later break.
        if placement == .interstitialnav { loadInterstitial() } else { loadAppOpen() }
    }

    // MARK: - Inline slots

    /// The kept slot of `placement` / `key` (`"0"`, `"ad-1"`, …).
    func slot(_ placement: AdPlacement, key: String = "0") -> InlineAdSlot {
        let id = "\(placement.key)/\(key)"
        if let slot = slots[id] { return slot }
        let slot = InlineAdSlot(placement: placement, key: key)
        slots[id] = slot
        return slot
    }

    /// Loads a banner into `slot` once (one retry a minute after a failure).
    func loadBanner(into slot: InlineAdSlot, width: CGFloat) {
        guard allowsInline(slot.placement), slot.banner == nil, slot.canRetry, !slot.isLoading, width > 0 else { return }
        slot.isLoading = true
        analytics.request(slot.placement)
        // Inline adaptive, capped so a tall creative cannot take over a list (Android: 100 dp).
        let banner = BannerView(adSize: inlineAdaptiveBanner(width: width, maxHeight: 100))
        banner.adUnitID = config.adUnitID(for: slot.placement)
        let events = BannerEvents(slot: slot, controller: self)
        slot.retained = events
        banner.delegate = events
        slot.pendingBanner = banner
        slot.requestedAt = Date()
        banner.load(Request())
    }

    /// Loads a native ad into `slot` when empty or older than an hour.
    func loadNative(into slot: InlineAdSlot) {
        guard allowsInline(slot.placement), slot.canRetry, !slot.isLoading else { return }
        if slot.nativeAd != nil, let loadedAt = slot.loadedAt, Date().timeIntervalSince(loadedAt) < Self.interstitialMaxAge { return }
        slot.isLoading = true
        analytics.request(slot.placement)
        let videoOptions = VideoOptions()
        // Never let a video ad make sound over a podcast.
        videoOptions.shouldStartMuted = true
        let loader = AdLoader(
            adUnitID: config.adUnitID(for: slot.placement),
            rootViewController: nil,
            adTypes: [.native],
            options: [videoOptions]
        )
        let events = NativeEvents(slot: slot, controller: self, loader: loader)
        slot.retained = events
        loader.delegate = events
        slot.requestedAt = Date()
        loader.load(Request())
    }

    /// Native rows of a list of `count` items: item index → slot key (`ad-0`, `ad-1`, …), from
    /// shared `NativeSlots` (after item 3, then every 8, at most 3, never last). Empty while the
    /// placement is not allowed, so the list keeps its plain layout.
    func nativeSlots(after count: Int, placement: AdPlacement) -> [Int: String] {
        guard allowsInline(placement) else { return [:] }
        let indices = NativeSlots.shared.afterIndices(itemCount: Int32(clamping: count), placement: placement, settings: config.ads())
        var slots: [Int: String] = [:]
        for (number, index) in indices.enumerated() {
            slots[index.intValue] = "ad-\(number)"
        }
        return slots
    }

    /// Template of a native placement: Discover is the medium one (Android), else Remote Config.
    func nativeTemplate(for placement: AdPlacement) -> NativeTemplate {
        placement == .discoverinline ? .medium : config.ads().nativeTemplate
    }

    fileprivate func paid(_ placement: AdPlacement, _ value: AdValue, source: String?) {
        analytics.paid(placement, value: value.value.decimalValue, currency: value.currencyCode, precision: Self.precisionName(value.precision), adSource: source)
    }

    // MARK: - Helpers

    static func ms(since date: Date) -> Int {
        Int(Date().timeIntervalSince(date) * 1000)
    }

    static func consentStatusName(_ status: ConsentStatus) -> String {
        switch status {
        case .notRequired: "not_required"
        case .required: "required"
        case .obtained: "obtained"
        default: "unknown"
        }
    }

    static func precisionName(_ precision: AdValuePrecision) -> String {
        switch precision {
        case .estimated: "estimated"
        case .publisherProvided: "publisher_provided"
        case .precise: "precise"
        default: "unknown"
        }
    }
}

/// A banner / native slot that outlives its view (Android `AdSlotCache.Slot`).
@MainActor
@Observable
final class InlineAdSlot {
    let placement: AdPlacement
    let key: String
    /// The loaded banner and its height (inline adaptive: known after the load).
    fileprivate(set) var banner: BannerView?
    fileprivate(set) var bannerHeight: CGFloat = 0
    fileprivate(set) var nativeAd: NativeAd?

    @ObservationIgnored fileprivate var isLoading = false
    @ObservationIgnored fileprivate var failedAt: Date?
    @ObservationIgnored fileprivate var loadedAt: Date?
    @ObservationIgnored fileprivate var requestedAt = Date()
    @ObservationIgnored fileprivate var pendingBanner: BannerView?
    /// The delegate (and native ad loader) of the current load: the SDK holds them weakly.
    @ObservationIgnored fileprivate var retained: AnyObject?

    init(placement: AdPlacement, key: String) {
        self.placement = placement
        self.key = key
    }

    /// No automatic retry loop: at most one new request a minute after a failure.
    fileprivate var canRetry: Bool {
        failedAt.map { Date().timeIntervalSince($0) > 60 } ?? true
    }
}

@MainActor
private final class FullScreenEvents: NSObject, FullScreenContentDelegate {
    let placement: AdPlacement
    weak var controller: AdsController?

    init(placement: AdPlacement, controller: AdsController) {
        self.placement = placement
        self.controller = controller
    }

    func adWillPresentFullScreenContent(_ ad: any FullScreenPresentingAd) {
        controller?.fullScreenWillPresent(placement)
    }

    func adDidRecordImpression(_ ad: any FullScreenPresentingAd) {
        controller?.analytics.impression(placement)
    }

    func adDidRecordClick(_ ad: any FullScreenPresentingAd) {
        controller?.analytics.click(placement)
    }

    func ad(_ ad: any FullScreenPresentingAd, didRecordPaidEventWith value: AdValue) {
        let info = (ad as? InterstitialAd)?.responseInfo ?? (ad as? AppOpenAd)?.responseInfo
        controller?.paid(placement, value, source: info?.loadedAdNetworkResponseInfo?.adSourceName)
    }

    func ad(_ ad: any FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: any Error) {
        controller?.fullScreenEnded(placement, errorCode: (error as NSError).code)
    }

    func adDidDismissFullScreenContent(_ ad: any FullScreenPresentingAd) {
        controller?.fullScreenEnded(placement, errorCode: nil)
    }
}

@MainActor
private final class BannerEvents: NSObject, BannerViewDelegate {
    weak var slot: InlineAdSlot?
    weak var controller: AdsController?

    init(slot: InlineAdSlot, controller: AdsController) {
        self.slot = slot
        self.controller = controller
    }

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        guard let slot else { return }
        controller?.analytics.loaded(slot.placement, latencyMs: AdsController.ms(since: slot.requestedAt), adSource: bannerView.responseInfo?.loadedAdNetworkResponseInfo?.adSourceName)
        let height = bannerView.adSize.size.height
        slot.bannerHeight = height > 0 ? height : bannerView.intrinsicContentSize.height
        slot.banner = bannerView
        slot.pendingBanner = nil
        slot.loadedAt = Date()
        slot.isLoading = false
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: any Error) {
        guard let slot else { return }
        controller?.analytics.loadFailed(slot.placement, code: (error as NSError).code, latencyMs: AdsController.ms(since: slot.requestedAt))
        slot.pendingBanner = nil
        slot.failedAt = Date()
        slot.isLoading = false
    }

    func bannerViewDidRecordImpression(_ bannerView: BannerView) {
        guard let slot else { return }
        controller?.analytics.impression(slot.placement)
    }

    func bannerViewDidRecordClick(_ bannerView: BannerView) {
        guard let slot else { return }
        controller?.analytics.click(slot.placement)
    }

    func bannerView(_ bannerView: BannerView, didRecordPaidEventWith value: AdValue) {
        guard let slot else { return }
        controller?.paid(slot.placement, value, source: bannerView.responseInfo?.loadedAdNetworkResponseInfo?.adSourceName)
    }
}

@MainActor
private final class NativeEvents: NSObject, NativeAdLoaderDelegate, NativeAdDelegate {
    weak var slot: InlineAdSlot?
    weak var controller: AdsController?
    /// Kept until the load ends (the slot keeps this object).
    var loader: AdLoader?

    init(slot: InlineAdSlot, controller: AdsController, loader: AdLoader) {
        self.slot = slot
        self.controller = controller
        self.loader = loader
    }

    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        loader = nil
        guard let slot else { return }
        controller?.analytics.loaded(slot.placement, latencyMs: AdsController.ms(since: slot.requestedAt), adSource: nativeAd.responseInfo.loadedAdNetworkResponseInfo?.adSourceName)
        nativeAd.delegate = self
        slot.nativeAd = nativeAd
        slot.loadedAt = Date()
        slot.isLoading = false
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: any Error) {
        loader = nil
        guard let slot else { return }
        controller?.analytics.loadFailed(slot.placement, code: (error as NSError).code, latencyMs: AdsController.ms(since: slot.requestedAt))
        slot.failedAt = Date()
        slot.isLoading = false
    }

    func nativeAdDidRecordImpression(_ nativeAd: NativeAd) {
        guard let slot else { return }
        controller?.analytics.impression(slot.placement)
    }

    func nativeAdDidRecordClick(_ nativeAd: NativeAd) {
        guard let slot else { return }
        controller?.analytics.click(slot.placement)
    }

    func nativeAd(_ nativeAd: NativeAd, didRecordPaidEventWith value: AdValue) {
        guard let slot else { return }
        controller?.paid(slot.placement, value, source: nativeAd.responseInfo.loadedAdNetworkResponseInfo?.adSourceName)
    }
}

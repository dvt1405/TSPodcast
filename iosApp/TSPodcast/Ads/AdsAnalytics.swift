import Foundation
@preconcurrency import Shared

/// Ad events with Android's names (`ads/AdsAnalytics.kt`, `AdRevenue`): the funnel `ads_request` →
/// `ads_loaded` / `ads_load_failed` → `ads_show` → `ads_impression` → `ads_click` → `ads_dismiss`,
/// `ads_blocked{reason}` (sampled 10 %), `consent_status`, and the paid event as `ads_paid` (micros).
/// Every event carries `placement` and `ad_format`. Goes through the consent-gated tracker.
struct AdsAnalytics: Sendable {
    static let adPlatform = "AdMob"

    let tracker: any AnalyticsTracker
    /// 10 % sampling of `ads_blocked`; tests pass `{ true }`.
    var sampleBlocked: @Sendable () -> Bool = { Int.random(in: 0..<10) == 0 }

    static func formatName(_ format: Shared.AdFormat) -> String {
        switch format {
        case .appopen: "app_open"
        case .banner, .collapsiblebanner: "banner"
        case .native: "native"
        case .interstitial: "interstitial"
        case .rewarded: "rewarded"
        default: "unknown"
        }
    }

    static func base(_ placement: AdPlacement) -> [String: String] {
        ["placement": placement.key, "ad_format": formatName(placement.format)]
    }

    private func log(_ name: String, _ placement: AdPlacement?, _ extra: [String: String?] = [:]) {
        var params = placement.map(Self.base) ?? [:]
        for (key, value) in extra { if let value { params[key] = value } }
        tracker.trackEvent(name: name, screen: nil, params: params)
    }

    func request(_ placement: AdPlacement) { log("ads_request", placement) }

    func loaded(_ placement: AdPlacement, latencyMs: Int?, adSource: String?) {
        log("ads_loaded", placement, ["latency_ms": latencyMs.map(String.init), "ad_source": adSource])
    }

    func loadFailed(_ placement: AdPlacement, code: Int, latencyMs: Int?) {
        log("ads_load_failed", placement, ["code_num": String(code), "latency_ms": latencyMs.map(String.init)])
    }

    func show(_ placement: AdPlacement) { log("ads_show", placement) }
    func showFailed(_ placement: AdPlacement, code: Int) { log("ads_show_failed", placement, ["code": String(code)]) }
    func dismiss(_ placement: AdPlacement) { log("ads_dismiss", placement) }
    func impression(_ placement: AdPlacement) { log("ads_impression", placement) }
    func click(_ placement: AdPlacement) { log("ads_click", placement) }

    func blocked(_ placement: AdPlacement, reason: String) {
        AppLog.ads.debug("ads_blocked placement=\(placement.key, privacy: .public) reason=\(reason, privacy: .public)")
        if sampleBlocked() { log("ads_blocked", placement, ["reason": reason]) }
    }

    func consent(status: String, canRequestAds: Bool, errorCode: Int?) {
        log("consent_status", nil, [
            "status": status, "can_request_ads": canRequestAds ? "true" : "false", "error_code": errorCode.map(String.init),
        ])
    }

    /// `ads_paid`: the revenue in micros, for reconciling with the AdMob API. No manual GA4
    /// `ad_impression` on iOS: the app's tracker sends string parameters, and GA4 needs a numeric
    /// `value`; the AdMob ↔ Firebase link logs `ad_impression` for AdMob itself (so
    /// `ads_ga4_ad_impression` has no effect here, and revenue is not counted twice).
    func paid(_ placement: AdPlacement, value: Decimal, currency: String, precision: String, adSource: String?) {
        let micros = NSDecimalNumber(decimal: value * 1_000_000).int64Value
        log("ads_paid", placement, [
            "value_micros": String(micros), "currency": currency.uppercased(), "precision": precision, "ad_source": adSource,
        ])
    }
}

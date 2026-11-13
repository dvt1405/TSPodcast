import Foundation
@preconcurrency import Shared

/// Ads and IAP settings: Remote Config (`ConfigAPI`, remote values only) over the in-app defaults,
/// which equal what iOS gets from `remoteconfig/ads-iap.params.json` (shared
/// `AdsSettingsTemplateTest` / `IapSettingsTemplateTest`, contract
/// docs/in-progress/plans/monetization-ads-iap.md §1). Parsing is shared (`AdsSettings.parse`,
/// `IapSettings.parse`); a snapshot is reused for 30 s like Android's `RemoteAdsConfig`.
///
/// Debug builds use `AdsSettings.defaults(debug: true)` (no grace, no first-session skip, no
/// interstitial) and Google's test units whatever `ads_unit_ids` says.
final class MonetizationConfig: @unchecked Sendable {
    static let cacheSeconds: TimeInterval = 30

    let debug: Bool
    /// `CFBundleVersion` as a number, compared with `ads_min_app_version`.
    let buildNumber: Int64

    private let config: any ConfigAPI
    private let now: @Sendable () -> Date
    private let lock = NSLock()
    private var cachedAds: (at: Date, settings: AdsSettings)?
    private var cachedIap: (at: Date, settings: IapSettings)?

    init(
        config: any ConfigAPI,
        buildNumber: Int64 = MonetizationConfig.buildNumber(from: Bundle.main.infoDictionary ?? [:]),
        debug: Bool = MonetizationConfig.isDebugBuild,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.config = config
        self.buildNumber = buildNumber
        self.debug = debug
        self.now = now
    }

    static var isDebugBuild: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }

    /// The ads snapshot (kill switch, placements, caps), with `ads_min_app_version` applied.
    func ads() -> AdsSettings {
        lock.lock()
        defer { lock.unlock() }
        let date = now()
        if let cachedAds, date.timeIntervalSince(cachedAds.at) < Self.cacheSeconds { return cachedAds.settings }
        let config = config
        let settings = AdsSettings.companion.parse(
            defaults: AdsSettings.companion.defaults(debug: debug),
            allowUnitIdOverrides: !debug,
            read: { key in config.getString(key: key) }
        ).forBuild(buildNumber: buildNumber)
        cachedAds = (date, settings)
        return settings
    }

    /// The IAP snapshot: paywall switch, preselected product, products in `order`.
    func iap() -> IapSettings {
        lock.lock()
        defer { lock.unlock() }
        let date = now()
        if let cachedIap, date.timeIntervalSince(cachedIap.at) < Self.cacheSeconds { return cachedIap.settings }
        let config = config
        let settings = IapSettings.companion.parse(
            defaults: IapSettings.companion.iosDefaults(),
            read: { key in config.getString(key: key) }
        )
        cachedIap = (date, settings)
        return settings
    }

    /// Unit id of `placement`: Google test unit in Debug, else `ads_unit_ids` or the baked one.
    func adUnitID(for placement: AdPlacement) -> String {
        IosAdUnitIds.shared.forPlacement(placement: placement, settings: ads(), debug: debug)
    }

    /// Store ids whose entitlement means "no ads": the configured products plus the in-app
    /// defaults, so a product dropped from Remote Config keeps a past purchase ad-free (§1.4).
    func adFreeStoreProductIDs() -> Set<String> {
        iap().adFreeStoreProductIds.union(IapSettings.companion.iosDefaults().adFreeStoreProductIds)
    }

    /// "12" → 12; "1.2" → 1; anything else → `Int64.max` (ads stay on, like Android's fallback).
    static func buildNumber(from info: [String: Any]) -> Int64 {
        guard let text = (info["CFBundleVersion"] as? String)?.trimmingCharacters(in: .whitespaces) else { return .max }
        if let value = Int64(text) { return value }
        if let head = text.split(separator: ".").first, let value = Int64(head) { return value }
        return .max
    }
}

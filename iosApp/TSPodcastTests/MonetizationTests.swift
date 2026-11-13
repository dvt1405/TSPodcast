import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// `ConfigAPI` returning fixed Remote Config values (nil = no remote value).
final class FakeConfigAPI: NSObject, ConfigAPI, @unchecked Sendable {
    var values: [String: String]

    init(_ values: [String: String] = [:]) {
        self.values = values
    }

    func getString(key: String) -> String? { values[key] }
}

/// Ads + IAP settings on iOS (docs/in-progress/plans/monetization-ads-iap.md §1, §2, §4).
final class MonetizationConfigTests: XCTestCase {
    func testNoRemoteValueGivesTheTemplateDefaultsAdsAndIapOn() {
        let config = MonetizationConfig(config: FakeConfigAPI(), buildNumber: 1, debug: false)
        let ads = config.ads()
        XCTAssertTrue(ads.adsEnabled)
        XCTAssertTrue(ads.isEnabled(placement: .favouritetop))
        XCTAssertTrue(ads.isEnabled(placement: .interstitialnav))
        XCTAssertFalse(ads.isEnabled(placement: .rewardedadfree))
        XCTAssertEqual(ads.gracePeriodHours, 24)
        let iap = config.iap()
        XCTAssertTrue(iap.enabled)
        XCTAssertEqual(iap.defaultProductId, "premium_yearly")
        XCTAssertEqual(iap.products.map(\.storeProductId), [
            "tss.t.podcast.premium.monthly", "tss.t.podcast.premium.yearly", "tss.t.podcast.remove_ads.lifetime",
        ])
    }

    func testReleaseUsesTheRealUnitsAndRemoteOverridesDebugAlwaysTestUnits() {
        let override = #"{"favourite_top":"ca-app-pub-1111111111111111/2222222222"}"#
        let release = MonetizationConfig(config: FakeConfigAPI(["ads_unit_ids": override]), buildNumber: 1, debug: false)
        XCTAssertEqual(release.adUnitID(for: .favouritetop), "ca-app-pub-1111111111111111/2222222222")
        XCTAssertEqual(release.adUnitID(for: .searchresults), "ca-app-pub-8696388065064767/5141512895")
        XCTAssertEqual(release.adUnitID(for: .interstitialnav), "ca-app-pub-8696388065064767/4518696367")
        XCTAssertEqual(release.adUnitID(for: .appopenwarm), "ca-app-pub-8696388065064767/9264415720")
        XCTAssertEqual(release.adUnitID(for: .discoverinline), "ca-app-pub-8696388065064767/2538245333")

        let debug = MonetizationConfig(config: FakeConfigAPI(["ads_unit_ids": override]), buildNumber: 1, debug: true)
        for placement in AdPlacement.entries {
            XCTAssertTrue(debug.adUnitID(for: placement).hasPrefix("ca-app-pub-3940256099942544/"), placement.key)
        }
        XCTAssertFalse(debug.ads().isEnabled(placement: .interstitialnav), "Debug: no interstitial (Android)")
        XCTAssertEqual(debug.ads().gracePeriodHours, 0)
    }

    func testKillSwitchAndMinAppVersion() {
        XCTAssertFalse(MonetizationConfig(config: FakeConfigAPI(["ads_enabled": "false"]), buildNumber: 1, debug: false).ads().adsEnabled)
        let gated = FakeConfigAPI(["ads_min_app_version": "5"])
        XCTAssertFalse(MonetizationConfig(config: gated, buildNumber: 4, debug: false).ads().adsEnabled)
        XCTAssertTrue(MonetizationConfig(config: gated, buildNumber: 5, debug: false).ads().adsEnabled)
    }

    func testSnapshotIsReusedForThirtySeconds() {
        final class Clock: @unchecked Sendable { var now = Date(timeIntervalSince1970: 1_000) }
        let remote = FakeConfigAPI()
        let clock = Clock()
        let config = MonetizationConfig(config: remote, buildNumber: 1, debug: false, now: { clock.now })
        XCTAssertTrue(config.ads().adsEnabled)
        remote.values["ads_enabled"] = "false"
        clock.now += 29
        XCTAssertTrue(config.ads().adsEnabled, "cached")
        clock.now += 2
        XCTAssertFalse(config.ads().adsEnabled)
    }

    func testIapSwitchAndBadJson() {
        let off = MonetizationConfig(config: FakeConfigAPI(["iap_enabled": "false", "iap_products": "{oops"]), buildNumber: 1, debug: false)
        XCTAssertFalse(off.iap().enabled)
        XCTAssertEqual(off.iap().products.count, 3, "bad JSON keeps the default products")
    }

    func testAdFreeIdsKeepDefaultProductsWhenRemoteDropsThem() {
        let json = #"{"version":1,"products":[{"id":"x","type":"one_time","store_product_id":"store.x","benefits":["ad_free"],"order":1}]}"#
        let config = MonetizationConfig(config: FakeConfigAPI(["iap_products": json]), buildNumber: 1, debug: false)
        XCTAssertEqual(config.adFreeStoreProductIDs(), [
            "store.x", "tss.t.podcast.premium.monthly", "tss.t.podcast.premium.yearly", "tss.t.podcast.remove_ads.lifetime",
        ])
    }

    func testBuildNumber() {
        XCTAssertEqual(MonetizationConfig.buildNumber(from: ["CFBundleVersion": "12"]), 12)
        XCTAssertEqual(MonetizationConfig.buildNumber(from: ["CFBundleVersion": "3.1"]), 3)
        XCTAssertEqual(MonetizationConfig.buildNumber(from: ["CFBundleVersion": "abc"]), .max)
        XCTAssertEqual(MonetizationConfig.buildNumber(from: [:]), .max)
    }
}

final class UserDefaultsAdCounterStoreTests: XCTestCase {
    private var defaults: UserDefaults!
    private let suite = "tss.t.podcast.tests.ads"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suite)
        defaults.removePersistentDomain(forName: suite)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suite)
        super.tearDown()
    }

    func testInstallTimeIsStoredOnceAndSessionsCountOncePerProcess() {
        let store = UserDefaultsAdCounterStore(defaults: defaults)
        XCTAssertEqual(store.installAt(now: 100), 100)
        XCTAssertEqual(store.installAt(now: 500), 100)
        store.startSession()
        store.startSession()
        XCTAssertEqual(store.sessionCount, 1)
        UserDefaultsAdCounterStore(defaults: defaults).startSession()
        XCTAssertEqual(store.sessionCount, 2, "a new process is a new session")
    }

    func testRecordShownCountsPerDayAndSession() {
        let store = UserDefaultsAdCounterStore(defaults: defaults)
        store.recordShown(placement: .appopenwarm, now: 1_000, day: 7)
        store.recordShown(placement: .appopenwarm, now: 2_000, day: 7)
        XCTAssertEqual(store.shownOnDay(placement: .appopenwarm, day: 7), 2)
        XCTAssertEqual(store.shownOnDay(placement: .appopenwarm, day: 8), 0)
        XCTAssertEqual(store.shownThisSession(placement: .appopenwarm), 2)
        XCTAssertEqual(store.lastShownAt(placement: .appopenwarm), 2_000)
        XCTAssertEqual(store.lastFullScreenAt, 2_000)
        store.recordShown(placement: .favouritetop, now: 3_000, day: 7)
        XCTAssertEqual(store.lastFullScreenAt, 2_000, "a banner is not full screen")

        let relaunched = UserDefaultsAdCounterStore(defaults: defaults)
        XCTAssertEqual(relaunched.shownOnDay(placement: .appopenwarm, day: 7), 2)
        XCTAssertEqual(relaunched.shownThisSession(placement: .appopenwarm), 0)
        store.grantAdFreeUntil(until: 9_999)
        XCTAssertEqual(relaunched.adFreeUntil, 9_999)
    }

    func testPolicyOverTheStoreBlocksDuringGrace() {
        let store = UserDefaultsAdCounterStore(defaults: defaults)
        let policy = AdPolicy(store: store, clock: SystemAdClock())
        let ctx = TriggerContext(
            canRequestAds: true, playbackActive: false, onPlayerScreen: false, externalLaunch: false,
            onboardingCompleted: true, backgroundDurationMs: KotlinLong(value: 600_000), naturalBreakIndex: 0,
            screenDwellMs: nil, sincePlaybackMs: nil
        )
        let decision = policy.canShowFullScreen(placement: .appopenwarm, settings: AdsSettings.companion.defaults(debug: false), ctx: ctx)
        XCTAssertEqual((decision as? DecisionBlock)?.reason, "grace", "first run: install time is now")
    }
}

@MainActor
final class AdsControllerTests: XCTestCase {
    private func makeController(premium: Bool, tracker: RecordingAnalyticsTracker = RecordingAnalyticsTracker()) -> AdsController {
        let suite = "tss.t.podcast.tests.adscontroller"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AdsController(
            config: MonetizationConfig(config: FakeConfigAPI(), buildNumber: 1, debug: true),
            counters: UserDefaultsAdCounterStore(defaults: defaults),
            analytics: AdsAnalytics(tracker: tracker, sampleBlocked: { true }),
            isPremium: premium
        )
    }

    func testNothingLoadsBeforeConsentAndTheSDK() {
        let ads = makeController(premium: false)
        XCTAssertEqual(ads.effectiveState, .idle)
        XCTAssertFalse(ads.canLoadAds)
        XCTAssertFalse(ads.allowsInline(.favouritetop))
        XCTAssertEqual(ads.nativeSlots(after: 30, placement: .podcastdetailepisodes), [:])
    }

    func testPremiumDisablesEverythingLive() {
        let ads = makeController(premium: true)
        XCTAssertEqual(ads.effectiveState, .disabled(reason: "premium"))
        ads.start()
        XCTAssertEqual(ads.state, .idle, "Premium: no consent flow, no SDK")
        XCTAssertFalse(ads.allowsInline(.searchresults))
        ads.didLeaveShow(dwell: 120)
        XCTAssertEqual(ads.effectiveState, .disabled(reason: "premium"))
    }

    func testLeavingAShowBeforeTheSDKLogsTheBlockReason() {
        let tracker = RecordingAnalyticsTracker()
        let ads = makeController(premium: false, tracker: tracker)
        ads.didLeaveShow(dwell: 120)
        let blocked = tracker.events.first { $0.name == "ads_blocked" }
        XCTAssertEqual(blocked?.params["placement"], "interstitial_nav")
        XCTAssertEqual(blocked?.params["reason"], "placement_disabled", "Debug defaults: no interstitial")
    }

    func testNativeTemplates() {
        let ads = makeController(premium: false)
        XCTAssertEqual(ads.nativeTemplate(for: .discoverinline), .medium)
        XCTAssertEqual(ads.nativeTemplate(for: .podcastdetailepisodes), .small)
    }
}

final class AdsAnalyticsTests: XCTestCase {
    func testEventNamesAndParametersMatchAndroid() {
        let tracker = RecordingAnalyticsTracker()
        let analytics = AdsAnalytics(tracker: tracker, sampleBlocked: { true })
        analytics.request(.favouritetop)
        analytics.loaded(.discoverinline, latencyMs: 120, adSource: "AdMob")
        analytics.blocked(.appopenwarm, reason: "grace")
        analytics.paid(.interstitialnav, value: Decimal(string: "0.0015")!, currency: "usd", precision: "estimated", adSource: nil)
        analytics.consent(status: "obtained", canRequestAds: true, errorCode: nil)
        XCTAssertEqual(tracker.events.map(\.name), ["ads_request", "ads_loaded", "ads_blocked", "ads_paid", "consent_status"])
        XCTAssertEqual(tracker.events[0].params, ["placement": "favourite_top", "ad_format": "banner"])
        XCTAssertEqual(tracker.events[1].params["ad_format"], "native")
        XCTAssertEqual(tracker.events[1].params["latency_ms"], "120")
        XCTAssertEqual(tracker.events[2].params["reason"], "grace")
        XCTAssertEqual(tracker.events[3].params["value_micros"], "1500")
        XCTAssertEqual(tracker.events[3].params["currency"], "USD")
        XCTAssertEqual(tracker.events[4].params, ["status": "obtained", "can_request_ads": "true"])
    }

    func testBlockedIsSampled() {
        let tracker = RecordingAnalyticsTracker()
        AdsAnalytics(tracker: tracker, sampleBlocked: { false }).blocked(.appopenwarm, reason: "gap")
        XCTAssertTrue(tracker.events.isEmpty)
    }
}

@MainActor
final class StoreModelTests: XCTestCase {
    private let suite = "tss.t.podcast.tests.store"

    private func makeStore(tracker: RecordingAnalyticsTracker = RecordingAnalyticsTracker()) -> (StoreModel, UserDefaults) {
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        let store = StoreModel(
            config: MonetizationConfig(config: FakeConfigAPI(), buildNumber: 1, debug: true),
            analytics: PurchaseAnalytics(tracker: tracker),
            defaults: defaults
        )
        return (store, defaults)
    }

    func testPremiumNeedsAnAdFreeEntitlement() {
        let adFree: Set = ["a", "b"]
        XCTAssertTrue(StoreModel.isPremium(activeProductIDs: ["b"], adFreeIDs: adFree))
        XCTAssertFalse(StoreModel.isPremium(activeProductIDs: ["c"], adFreeIDs: adFree))
        XCTAssertFalse(StoreModel.isPremium(activeProductIDs: [], adFreeIDs: adFree))
    }

    func testEntitlementChangeIsCachedAndTurnsAdsOffLive() {
        let (store, defaults) = makeStore()
        XCTAssertFalse(store.isPremium)
        var changes: [Bool] = []
        store.onPremiumChange = { changes.append($0) }

        store.apply(activeProductIDs: ["tss.t.podcast.premium.yearly"])
        XCTAssertTrue(store.isPremium)
        XCTAssertTrue(store.hasActiveSubscription)
        XCTAssertEqual(changes, [true])
        XCTAssertTrue(PremiumCache(defaults: defaults).isPremium)

        let relaunched = StoreModel(
            config: MonetizationConfig(config: FakeConfigAPI(), buildNumber: 1, debug: true),
            analytics: PurchaseAnalytics(tracker: RecordingAnalyticsTracker()),
            defaults: defaults
        )
        XCTAssertTrue(relaunched.isPremium, "the cache avoids ads flashing at launch")

        store.apply(activeProductIDs: ["tss.t.podcast.remove_ads.lifetime"])
        XCTAssertFalse(store.hasActiveSubscription, "lifetime: nothing to manage")
        store.apply(activeProductIDs: [])
        XCTAssertFalse(store.isPremium)
        XCTAssertEqual(changes, [true, false])
        XCTAssertFalse(PremiumCache(defaults: defaults).isPremium)
    }

    func testPurchaseAnalyticsCarryTheInternalProductIdAndSource() {
        let tracker = RecordingAnalyticsTracker()
        PurchaseAnalytics(tracker: tracker).log(PurchaseAnalytics.purchaseStart, productID: "premium_yearly", source: "settings")
        XCTAssertEqual(tracker.events.first?.name, "purchase_start")
        XCTAssertEqual(tracker.events.first?.screen, "Paywall")
        XCTAssertEqual(tracker.events.first?.params, ["product_id": "premium_yearly", "source": "settings"])
    }
}

final class PaywallOptionTests: XCTestCase {
    private let settings = IapSettings.companion.iosDefaults()
    private let month = StoreProductInfo.Period(unit: .month, value: 1)
    private let year = StoreProductInfo.Period(unit: .year, value: 1)
    private let week = StoreProductInfo.Period(unit: .week, value: 1)

    private var store: [String: StoreProductInfo] {
        [
            "tss.t.podcast.premium.monthly": StoreProductInfo(displayName: "Monthly", displayPrice: "$1.99", period: month),
            "tss.t.podcast.premium.yearly": StoreProductInfo(displayName: "Yearly", displayPrice: "$14.99", period: year, freeTrial: week),
            "tss.t.podcast.remove_ads.lifetime": StoreProductInfo(displayName: "Lifetime", displayPrice: "$29.99"),
        ]
    }

    func testRowsFollowOrderWithBadgeAndTrialOnlyWhenEligible() {
        let rows = PaywallOption.options(settings: settings, store: store, introEligible: ["tss.t.podcast.premium.yearly"])
        XCTAssertEqual(rows.map(\.id), ["premium_monthly", "premium_yearly", "remove_ads_lifetime"])
        XCTAssertEqual(rows.map(\.isBestValue), [false, true, false])
        XCTAssertEqual(rows[1].trial, week)
        XCTAssertEqual(rows[1].price, "$14.99")
        XCTAssertNil(rows[2].period, "lifetime has no period")

        let notEligible = PaywallOption.options(settings: settings, store: store, introEligible: [])
        XCTAssertNil(notEligible[1].trial)
    }

    func testProductsTheStoreDidNotReturnAreHidden() {
        var partial = store
        partial["tss.t.podcast.premium.yearly"] = nil
        let rows = PaywallOption.options(settings: settings, store: partial, introEligible: [])
        XCTAssertEqual(rows.map(\.id), ["premium_monthly", "remove_ads_lifetime"])
        XCTAssertEqual(PaywallOption.preselected(rows, defaultID: "premium_yearly"), "premium_monthly", "default missing: first row")
        XCTAssertEqual(PaywallOption.preselected(PaywallOption.options(settings: settings, store: store, introEligible: []), defaultID: "premium_yearly"), "premium_yearly")
        XCTAssertNil(PaywallOption.preselected([], defaultID: "premium_yearly"))
        XCTAssertTrue(PaywallOption.options(settings: settings, store: [:], introEligible: []).isEmpty)
    }

    func testTermsFallBackToAppleEula() {
        XCTAssertEqual(PaywallModel.termsURL(from: [:]), PaywallModel.appleEULA)
        XCTAssertEqual(PaywallModel.termsURL(from: ["TSTermsOfUseURL": ""]), PaywallModel.appleEULA)
        XCTAssertEqual(PaywallModel.termsURL(from: ["TSTermsOfUseURL": "https://example.com/terms"]).absoluteString, "https://example.com/terms")
    }

    func testPeriodTextIsLocalized() {
        XCTAssertEqual(PaywallModel.periodText(week, locale: Locale(identifier: "en")), "1 week")
        XCTAssertEqual(PaywallModel.periodText(StoreProductInfo.Period(unit: .month, value: 3), locale: Locale(identifier: "en")), "3 months")
        XCTAssertFalse(PaywallModel.periodText(year, locale: Locale(identifier: "vi")).isEmpty)
    }
}

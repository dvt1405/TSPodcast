import Foundation
import Observation
import StoreKit
@preconcurrency import Shared

/// A store product reduced to what the paywall shows, so the row logic is tested without StoreKit.
struct StoreProductInfo: Equatable, Sendable {
    enum PeriodUnit: Equatable, Sendable {
        case day, week, month, year
    }

    struct Period: Equatable, Sendable {
        var unit: PeriodUnit
        var value: Int
    }

    var displayName: String
    var displayPrice: String
    /// Renewal period; nil for a one-time purchase.
    var period: Period?
    /// The free-trial length of the intro offer; nil when there is none or it is not free.
    var freeTrial: Period?

    init(displayName: String, displayPrice: String, period: Period? = nil, freeTrial: Period? = nil) {
        self.displayName = displayName
        self.displayPrice = displayPrice
        self.period = period
        self.freeTrial = freeTrial
    }

    init(_ product: Product) {
        displayName = product.displayName
        displayPrice = product.displayPrice
        period = product.subscription.map { Self.period($0.subscriptionPeriod) }
        if let offer = product.subscription?.introductoryOffer, offer.paymentMode == .freeTrial {
            var trial = Self.period(offer.period)
            trial.value *= max(1, offer.periodCount)
            freeTrial = trial
        } else {
            freeTrial = nil
        }
    }

    static func period(_ period: Product.SubscriptionPeriod) -> Period {
        let unit: PeriodUnit = switch period.unit {
        case .day: .day
        case .week: .week
        case .month: .month
        case .year: .year
        @unknown default: .month
        }
        return Period(unit: unit, value: period.value)
    }
}

/// One row of the paywall.
struct PaywallOption: Identifiable, Equatable, Sendable {
    /// Internal id (`premium_yearly`): analytics and the preselection.
    let id: String
    let storeProductID: String
    let title: String
    let price: String
    /// Renewal period, nil for the lifetime purchase.
    let period: StoreProductInfo.Period?
    let isBestValue: Bool
    /// Free-trial length, only when this Apple ID is eligible for the intro offer.
    let trial: StoreProductInfo.Period?

    /// The configured products in `order` that the store returned (others are hidden, §2).
    static func options(
        settings: IapSettings,
        store: [String: StoreProductInfo],
        introEligible: Set<String>
    ) -> [PaywallOption] {
        settings.products.compactMap { product in
            guard let info = store[product.storeProductId] else { return nil }
            let isSubscription = product.type == .subscription
            return PaywallOption(
                id: product.id,
                storeProductID: product.storeProductId,
                title: info.displayName,
                price: info.displayPrice,
                period: isSubscription ? info.period : nil,
                isBestValue: product.badge == IapProduct.companion.BADGE_BEST_VALUE,
                trial: isSubscription && introEligible.contains(product.storeProductId) ? info.freeTrial : nil
            )
        }
    }

    /// `iap_default_product` when it is on screen, else the first row.
    static func preselected(_ options: [PaywallOption], defaultID: String) -> String? {
        options.first { $0.id == defaultID }?.id ?? options.first?.id
    }
}

/// The paywall (contract §3): opened from Settings, logs `paywall_view`, buys and restores
/// through `StoreModel`.
@MainActor
@Observable
final class PaywallModel {
    enum Message: Equatable {
        case pending
        case failed
        case restored
        case nothingToRestore
        case restoreFailed
    }

    var selectedID: String?
    private(set) var isWorking = false
    var message: Message?

    let store: StoreModel
    let source: String
    @ObservationIgnored private var didLogView = false

    init(store: StoreModel, source: String) {
        self.store = store
        self.source = source
    }

    var options: [PaywallOption] {
        PaywallOption.options(
            settings: store.config.iap(),
            store: store.products.mapValues(StoreProductInfo.init),
            introEligible: store.introEligibleProductIDs
        )
    }

    var selected: PaywallOption? {
        let options = options
        return options.first { $0.id == selectedID } ?? options.first
    }

    /// Whether any row is a subscription (the auto-renew disclosure then shows).
    var hasSubscription: Bool { options.contains { $0.period != nil } }

    func load() async {
        if !didLogView {
            didLogView = true
            store.analytics.log(PurchaseAnalytics.paywallView, productID: store.config.iap().defaultProductId, source: source)
        }
        if store.productsState != .loaded {
            await store.loadProducts()
        }
        if selectedID == nil {
            selectedID = PaywallOption.preselected(options, defaultID: store.config.iap().defaultProductId)
        }
    }

    func retry() async {
        await store.loadProducts()
        if selectedID == nil {
            selectedID = PaywallOption.preselected(options, defaultID: store.config.iap().defaultProductId)
        }
    }

    /// Buys the selected row. True when Premium is now on.
    @discardableResult
    func buy() async -> Bool {
        guard !isWorking, let option = selected, let product = store.products[option.storeProductID] else { return false }
        isWorking = true
        defer { isWorking = false }
        switch await store.purchase(product, productID: option.id, source: source) {
        case .purchased: return true
        case .cancelled: return false
        case .pending: message = .pending
        case .failed: message = .failed
        }
        return false
    }

    func restore() async {
        guard !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        switch await store.restore(source: source) {
        case .restored: message = .restored
        case .nothingToRestore: message = .nothingToRestore
        case .failed: message = .restoreFailed
        }
    }

    /// Apple's standard EULA unless `TERMS_OF_USE_URL` is set (`TSTermsOfUseURL`).
    nonisolated static let appleEULA = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!

    nonisolated static func termsURL(from info: [String: Any]) -> URL {
        guard let text = (info["TSTermsOfUseURL"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              let url = URL(string: text), let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              url.host() != nil
        else { return appleEULA }
        return url
    }

    /// "1 tuần", "3 months": a period named in the app language.
    nonisolated static func periodText(_ period: StoreProductInfo.Period, locale: Locale = AppLocale.current) -> String {
        var components = DateComponents()
        switch period.unit {
        case .day: components.day = period.value
        case .week: components.weekOfMonth = period.value
        case .month: components.month = period.value
        case .year: components.year = period.value
        }
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        formatter.maximumUnitCount = 1
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        formatter.calendar = calendar
        return formatter.string(from: components) ?? "\(period.value)"
    }
}

/// The app language (`Bundle.main.preferredLocalizations`), which may differ from the device's.
enum AppLocale {
    static var current: Locale {
        Locale(identifier: Bundle.main.preferredLocalizations.first ?? "vi")
    }
}

import Foundation
import Observation
import StoreKit
import UIKit
@preconcurrency import Shared

/// Premium (`ad_free`) cached on the device, so ads do not flash at launch before StoreKit
/// answers (contract §1.6). StoreKit's `currentEntitlements` stays the source of truth.
struct PremiumCache {
    static let key = "store.premium.adFree"

    let defaults: UserDefaults

    var isPremium: Bool { defaults.bool(forKey: Self.key) }

    func save(_ premium: Bool) {
        defaults.set(premium, forKey: Self.key)
    }
}

/// Analytics of the paywall and purchases (contract §3): `paywall_view`, `purchase_start`,
/// `purchase_success`, `purchase_cancel`, `purchase_fail`, `purchase_restore`, each with
/// `product_id` (internal id, e.g. `premium_yearly`) and `source` (where the paywall opened).
struct PurchaseAnalytics: Sendable {
    static let screen = "Paywall"
    static let paywallView = "paywall_view"
    static let purchaseStart = "purchase_start"
    static let purchaseSuccess = "purchase_success"
    static let purchaseCancel = "purchase_cancel"
    static let purchaseFail = "purchase_fail"
    static let purchaseRestore = "purchase_restore"

    let tracker: any AnalyticsTracker

    func log(_ name: String, productID: String?, source: String, extra: [String: String] = [:]) {
        var params = extra
        params["source"] = source
        if let productID { params["product_id"] = productID }
        tracker.trackEvent(name: name, screen: Self.screen, params: params)
    }
}

/// StoreKit 2 for Premium (contract §1, §3): products of `iap_products`, purchase, verified
/// transactions finished at once, `Transaction.updates` (renewals, Ask to Buy, refunds, purchases
/// on another device), entitlement from `Transaction.currentEntitlements`, Restore through
/// `AppStore.sync()`. No server: verification is StoreKit's `VerificationResult` on the device.
///
/// `isPremium` turns every ad off live (`onPremiumChange` → `AdsController.setPremium`).
@MainActor
@Observable
final class StoreModel {
    enum ProductsState: Equatable {
        case idle
        case loading
        case loaded
        case failed
    }

    enum PurchaseOutcome: Equatable {
        case purchased
        case cancelled
        case pending
        case failed
    }

    enum RestoreOutcome: Equatable {
        case restored
        case nothingToRestore
        case failed
    }

    private(set) var isPremium: Bool
    /// Store ids of the active ad-free entitlements (Settings shows "Đang dùng Premium").
    private(set) var activeProductIDs: Set<String> = []
    /// Store products keyed by store product id (only those the App Store returned).
    private(set) var products: [String: Product] = [:]
    private(set) var productsState: ProductsState = .idle
    /// Products whose subscription intro offer this Apple ID can still use.
    private(set) var introEligibleProductIDs: Set<String> = []

    @ObservationIgnored let config: MonetizationConfig
    @ObservationIgnored let analytics: PurchaseAnalytics
    @ObservationIgnored var onPremiumChange: (@MainActor (Bool) -> Void)?
    @ObservationIgnored private let cache: PremiumCache
    @ObservationIgnored private var updatesTask: Task<Void, Never>?

    init(config: MonetizationConfig, analytics: PurchaseAnalytics, defaults: UserDefaults = .standard) {
        self.config = config
        self.analytics = analytics
        cache = PremiumCache(defaults: defaults)
        isPremium = cache.isPremium
    }

    /// Launch: listen to `Transaction.updates` for the whole process, then read the entitlements.
    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task.detached(priority: .utility) { [weak self] in
            for await update in Transaction.updates {
                guard let transaction = Self.verified(update) else { continue }
                await transaction.finish()
                await self?.refreshEntitlements()
            }
        }
        Task { await refreshEntitlements() }
    }

    /// Premium = a verified, unrevoked current entitlement to a product whose benefits include
    /// `ad_free`. Expired subscriptions are not in `currentEntitlements`.
    func refreshEntitlements() async {
        var active: Set<String> = []
        for await result in Transaction.currentEntitlements {
            guard let transaction = Self.verified(result) else { continue }
            if transaction.revocationDate == nil { active.insert(transaction.productID) }
        }
        apply(activeProductIDs: active)
    }

    func apply(activeProductIDs active: Set<String>) {
        activeProductIDs = active
        let premium = Self.isPremium(activeProductIDs: active, adFreeIDs: config.adFreeStoreProductIDs())
        cache.save(premium)
        guard premium != isPremium else { return }
        isPremium = premium
        AppLog.store.notice("premium=\(premium, privacy: .public)")
        onPremiumChange?(premium)
    }

    nonisolated static func isPremium(activeProductIDs: Set<String>, adFreeIDs: Set<String>) -> Bool {
        !activeProductIDs.isDisjoint(with: adFreeIDs)
    }

    /// Loads the configured products; ids the store does not return are left out.
    func loadProducts() async {
        let ids = config.iap().products.map(\.storeProductId)
        guard !ids.isEmpty else {
            products = [:]
            productsState = .loaded
            return
        }
        productsState = .loading
        do {
            let loaded = try await Product.products(for: ids)
            products = Dictionary(loaded.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            var eligible: Set<String> = []
            for product in loaded {
                if let subscription = product.subscription, subscription.introductoryOffer != nil,
                   await subscription.isEligibleForIntroOffer {
                    eligible.insert(product.id)
                }
            }
            introEligibleProductIDs = eligible
            productsState = .loaded
            AppLog.store.notice("products_loaded requested=\(ids.count, privacy: .public) returned=\(loaded.count, privacy: .public)")
        } catch {
            productsState = .failed
            AppLog.store.error("products_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }

    func purchase(_ product: Product, productID: String, source: String) async -> PurchaseOutcome {
        analytics.log(PurchaseAnalytics.purchaseStart, productID: productID, source: source)
        do {
            let result: Product.PurchaseResult
            if let scene = Self.activeScene {
                result = try await product.purchase(confirmIn: scene)
            } else {
                result = try await product.purchase()
            }
            switch result {
            case .success(let verification):
                guard let transaction = Self.verified(verification) else {
                    analytics.log(PurchaseAnalytics.purchaseFail, productID: productID, source: source, extra: ["reason": "unverified"])
                    return .failed
                }
                await transaction.finish()
                await refreshEntitlements()
                analytics.log(PurchaseAnalytics.purchaseSuccess, productID: productID, source: source)
                return .purchased
            case .userCancelled:
                analytics.log(PurchaseAnalytics.purchaseCancel, productID: productID, source: source)
                return .cancelled
            case .pending:
                analytics.log(PurchaseAnalytics.purchaseFail, productID: productID, source: source, extra: ["reason": "pending"])
                return .pending
            @unknown default:
                analytics.log(PurchaseAnalytics.purchaseFail, productID: productID, source: source, extra: ["reason": "unknown"])
                return .failed
            }
        } catch {
            analytics.log(PurchaseAnalytics.purchaseFail, productID: productID, source: source, extra: ["reason": String(describing: type(of: error))])
            AppLog.store.error("purchase_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return .failed
        }
    }

    /// "Khôi phục giao dịch": `AppStore.sync()` (may ask for the Apple ID password), then the
    /// entitlements again.
    func restore(source: String) async -> RestoreOutcome {
        do {
            try await AppStore.sync()
        } catch {
            AppLog.store.error("restore_failed type=\(String(describing: type(of: error)), privacy: .public)")
            analytics.log(PurchaseAnalytics.purchaseRestore, productID: nil, source: source, extra: ["result": "failed"])
            return .failed
        }
        await refreshEntitlements()
        let restored = isPremium
        let productID = activeProductIDs.compactMap { config.iap().productForStoreId(storeProductId: $0)?.id }.sorted().first
        analytics.log(PurchaseAnalytics.purchaseRestore, productID: productID, source: source, extra: ["result": restored ? "restored" : "none"])
        return restored ? .restored : .nothingToRestore
    }

    /// Settings → "Quản lý gói": the system subscription sheet.
    func showManageSubscriptions() async {
        guard let scene = Self.activeScene else { return }
        do {
            try await AppStore.showManageSubscriptions(in: scene)
        } catch {
            AppLog.store.error("manage_subscriptions_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
        await refreshEntitlements()
    }

    /// Whether an active entitlement is an auto-renewable subscription (then "Quản lý gói").
    var hasActiveSubscription: Bool {
        activeProductIDs.contains { config.iap().productForStoreId(storeProductId: $0)?.type == .subscription }
    }

    nonisolated private static func verified<T>(_ result: VerificationResult<T>) -> T? {
        switch result {
        case .verified(let value): value
        case .unverified: nil
        }
    }

    private static var activeScene: UIWindowScene? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    }
}

import Foundation
import Observation
@preconcurrency import Shared

/// A hidden show as Settings → "Show đã ẩn" lists it (shared `HiddenShow`).
struct HiddenShowItem: Identifiable, Hashable, Sendable {
    let feedId: Int64
    let title: String
    let imageURL: String?
    let hiddenAt: Date

    var id: Int64 { feedId }
}

/// The hidden shows on this device, behind a protocol so models can be tested without Kotlin.
/// `SharedHiddenShowsStore` is the real one (shared `HiddenShowsRepository`).
protocol HiddenShowsStore: Sendable {
    func all() async -> [HiddenShowItem]
    func hide(feedId: Int64, title: String, imageURL: String?) async
    func unhide(feedId: Int64) async
}

/// "Ẩn show này" and "Bỏ ẩn" for the whole app (M6 §3.5, N1 lite). Home, Search and the news hub
/// already drop hidden shows when they load (shared); this model removes a show from the screens
/// that are already showing it, at once, through the `onHide` observers, and logs `show_hidden`
/// (`feed_id` only, never the title).
@MainActor
@Observable
final class HiddenShowsModel {
    /// Most recently hidden first.
    private(set) var items: [HiddenShowItem] = []
    private(set) var hiddenIds: Set<Int64> = []

    @ObservationIgnored private let store: any HiddenShowsStore
    @ObservationIgnored private let analytics: any AnalyticsTracker
    @ObservationIgnored private var observers: [@MainActor (Int64) -> Void] = []

    init(store: any HiddenShowsStore, analytics: any AnalyticsTracker) {
        self.store = store
        self.analytics = analytics
    }

    /// Called with the feed id of every show hidden from now on (Home and Search models).
    func onHide(_ observer: @escaping @MainActor (Int64) -> Void) {
        observers.append(observer)
    }

    func load() async {
        apply(await store.all())
    }

    /// Hides `feedId`: the screens drop it at once, then it is saved and logged.
    func hide(feedId: Int64, title: String, imageURL: String?, screen: String) async {
        hiddenIds.insert(feedId)
        observers.forEach { $0(feedId) }
        await store.hide(feedId: feedId, title: title, imageURL: imageURL)
        apply(await store.all())
        let events = AnalyticsEvents.shared
        analytics.trackEvent(name: events.SHOW_HIDDEN, screen: screen, params: [events.PARAM_FEED_ID: String(feedId)])
        AppLog.browse.notice("show_hidden feedId=\(feedId, privacy: .public)")
    }

    /// "Bỏ ẩn": the show comes back on the next load of Home and Search.
    func unhide(feedId: Int64) async {
        items.removeAll { $0.feedId == feedId }
        hiddenIds.remove(feedId)
        await store.unhide(feedId: feedId)
        apply(await store.all())
        AppLog.browse.notice("show_unhidden feedId=\(feedId, privacy: .public)")
    }

    func isHidden(_ feedId: Int64) -> Bool {
        hiddenIds.contains(feedId)
    }

    private func apply(_ shows: [HiddenShowItem]) {
        items = shows
        hiddenIds = Set(shows.map(\.feedId))
    }
}

/// `HiddenShowsStore` on shared `HiddenShowsRepository` (the store never throws; cancellation
/// reads as empty).
struct SharedHiddenShowsStore: HiddenShowsStore, @unchecked Sendable {
    let repository: HiddenShowsRepository

    func all() async -> [HiddenShowItem] {
        do {
            return try await repository.all().map { show in
                HiddenShowItem(
                    feedId: show.feedId,
                    title: show.title,
                    imageURL: show.imageUrl,
                    hiddenAt: Date(timeIntervalSince1970: TimeInterval(show.hiddenAtMillis) / 1000)
                )
            }
        } catch {
            AppLog.browse.error("hidden_shows_read_failed")
            return []
        }
    }

    func hide(feedId: Int64, title: String, imageURL: String?) async {
        do {
            try await repository.hide(feedId: feedId, title: title, imageUrl: imageURL)
        } catch {
            AppLog.browse.error("hidden_shows_write_failed")
        }
    }

    func unhide(feedId: Int64) async {
        do {
            try await repository.unhide(feedId: feedId)
        } catch {
            AppLog.browse.error("hidden_shows_write_failed")
        }
    }
}

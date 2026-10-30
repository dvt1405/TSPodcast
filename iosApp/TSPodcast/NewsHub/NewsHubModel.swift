import Foundation
import Observation
@preconcurrency import Shared

/// The "Thời sự & Bản tin" screen (M6 §3.4): morning brief, "Mới nhất", "Nguồn", "Truyện đêm
/// khuya" and the footer. Opened from the Home section, which hands over what it already shows
/// (`initial`), so the screen does not flash a spinner; without it the screen loads once.
@MainActor
@Observable
final class NewsHubModel {
    enum State: Hashable, Sendable {
        case loading
        case loaded(NewsHubContent)
        case failed
        /// The hub is off or has nothing (Remote Config turned it off while the screen was open).
        case unavailable
    }

    private(set) var state: State
    /// Minutes of the timer the night shelf just set: the toast "Đã hẹn giờ tắt %d phút · Đổi"
    /// shows while it is non-nil and hides itself after `toastDuration`.
    private(set) var toastMinutes: Int?

    let entry: NewsHubEntry

    @ObservationIgnored private let dataSource: any NewsHubDataSource
    @ObservationIgnored private let actions: NewsHubActions
    @ObservationIgnored private let analytics: any AnalyticsTracker
    @ObservationIgnored private let toastDuration: Duration
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var toastTask: Task<Void, Never>?

    /// Screen name of the hub in analytics.
    static let analyticsScreen = "NewsHub"
    /// §3.4: the toast hides after 4 s.
    static let defaultToastDuration: Duration = .seconds(4)

    init(
        entry: NewsHubEntry,
        initial: NewsHubContent? = nil,
        dataSource: any NewsHubDataSource,
        playback: any NewsHubPlayback,
        analytics: any AnalyticsTracker,
        toastDuration: Duration = NewsHubModel.defaultToastDuration
    ) {
        self.entry = entry
        self.dataSource = dataSource
        self.analytics = analytics
        self.toastDuration = toastDuration
        actions = NewsHubActions(playback: playback, analytics: analytics)
        state = initial.map(State.loaded) ?? .loading
    }

    var content: NewsHubContent? {
        if case .loaded(let content) = state { return content }
        return nil
    }

    /// First appearance: logs `news_hub_open` (`from`) once, then loads unless Home handed the
    /// content over.
    func loadIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        let events = AnalyticsEvents.shared
        analytics.trackEvent(name: events.NEWS_HUB_OPEN, screen: Self.analyticsScreen, params: [events.PARAM_FROM: entry.rawValue])
        guard content == nil else { return }
        await load()
    }

    /// "Tải lại" after a failure, and pull-to-refresh.
    func retry() async {
        hasStarted = true
        await load()
    }

    func playBrief() {
        guard let content else { return }
        actions.playBrief(content, screen: Self.analyticsScreen)
    }

    func playLatest(at index: Int) {
        guard let content else { return }
        actions.playLatest(content, at: index)
    }

    /// Plays night episode `index`, sets the timer and shows the toast.
    func playNight(at index: Int) {
        guard let content, let minutes = actions.playNight(content, at: index) else { return }
        showToast(minutes: minutes)
    }

    func dismissToast() {
        toastTask?.cancel()
        toastTask = nil
        toastMinutes = nil
    }

    /// "Ẩn show này" elsewhere: the show leaves the screen at once.
    func removeShow(feedId: Int64) {
        guard let content else { return }
        let kept = content.removing(feedId: feedId)
        state = kept.hasNews ? .loaded(kept) : .unavailable
    }

    private func load() async {
        guard dataSource.isEnabled else {
            state = .unavailable
            return
        }
        if content == nil {
            state = .loading
        }
        switch await dataSource.load() {
        case .success(let content?):
            state = .loaded(content)
        case .success(nil):
            state = .unavailable
        case .failure:
            // A failed refresh keeps what the screen shows.
            if content == nil {
                state = .failed
            }
        }
    }

    private func showToast(minutes: Int) {
        toastTask?.cancel()
        toastMinutes = minutes
        let duration = toastDuration
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            self?.toastMinutes = nil
        }
    }
}

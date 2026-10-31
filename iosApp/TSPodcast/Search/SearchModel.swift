import Foundation
import Observation

/// The Search tab, Android's `SearchViewModel` plus the `debounce(200)` of `SearchScreen`
/// (features-spec §3.9, M2 §4.3).
///
/// - Typing waits `debounce` on `clock` after the last change, then searches.
/// - Return/Search on the keyboard (`submit`) searches at once.
/// - Changing the mode searches again at once (no debounce) when there is a query.
/// - The query is trimmed; a blank one sends nothing and goes back to idle ([Sửa]: Android sent
///   `q=""`).
/// - A (query, mode) pair that is running or already shown is not sent again.
/// - Every request gets a token; only the latest one may write its result, so a slow older
///   response never replaces a newer one.
/// - A failure is its own state with "Tải lại" ([Sửa]: Android showed it as "no results").
///
/// `MainTabView` owns the model, so query, mode and results survive tab switches.
@MainActor
@Observable
final class SearchModel {
    enum Phase: Equatable, Sendable {
        /// No query: suggestion and category chips.
        case idle
        case loading
        case results([SearchResultItem])
        case empty
        case failed
    }

    /// What the search field shows, as typed.
    private(set) var query = ""
    /// Selected chip.
    private(set) var mode: SearchScope = .all
    private(set) var phase: Phase = .idle
    /// Query of the request behind `phase` (trimmed), so the "… cho: %@" header and the empty
    /// text describe the result on screen rather than what is being typed.
    private(set) var shownQuery = ""
    /// Mode of the request behind `phase`.
    private(set) var shownMode: SearchScope = .all

    /// A (trimmed query, mode) pair.
    struct Request: Hashable, Sendable {
        let query: String
        let mode: SearchScope
    }

    @ObservationIgnored private let dataSource: any SearchDataSource
    @ObservationIgnored private let clock: any Clock<Duration>
    @ObservationIgnored private let debounce: Duration
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// Request that is running or whose result is shown; nil when idle or after a failure.
    @ObservationIgnored private var current: Request?
    /// Increases with every request (and when going idle); a result is applied only when its
    /// token is still the latest.
    @ObservationIgnored private var latestToken = 0

    init(
        dataSource: any SearchDataSource,
        clock: any Clock<Duration> = ContinuousClock(),
        debounce: Duration = .milliseconds(200)
    ) {
        self.dataSource = dataSource
        self.clock = clock
        self.debounce = debounce
    }

    /// The search field changed. A blank query goes back to idle at once (no request); any other
    /// text searches once `debounce` has passed without another change.
    func queryChanged(_ text: String) {
        guard text != query else { return }
        query = text
        debounceTask?.cancel()
        debounceTask = nil
        guard !Self.trimmed(text).isEmpty else {
            goIdle()
            return
        }
        let clock = clock
        let debounce = debounce
        debounceTask = Task { [weak self] in
            do {
                try await clock.sleep(for: debounce)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            self?.debounceFired()
        }
    }

    /// Return/Search on the keyboard: no waiting for the debounce.
    func submit() {
        debounceTask?.cancel()
        debounceTask = nil
        searchCurrentQuery()
    }

    /// Mode chip. Another mode with a query searches again at once; with no query only the chip
    /// changes.
    func select(mode newMode: SearchScope) {
        guard newMode != mode else { return }
        mode = newMode
        guard !Self.trimmed(query).isEmpty else { return }
        debounceTask?.cancel()
        debounceTask = nil
        searchCurrentQuery()
    }

    /// "Tải lại" of the error state: the failed (query, mode) again.
    func retry() {
        guard phase == .failed, !shownQuery.isEmpty else { return }
        debounceTask?.cancel()
        debounceTask = nil
        start(Request(query: shownQuery, mode: shownMode))
    }

    /// "Ẩn show này" (M6 §3.5): drops `feedId` from the results on screen, without a request
    /// (shared `SearchFeed` already leaves it out of the next search).
    func removeShow(feedId: Int64) {
        guard case .results(let items) = phase else { return }
        let kept = items.filter { $0.id != feedId }
        guard kept.count != items.count else { return }
        phase = kept.isEmpty ? .empty : .results(kept)
    }

    // MARK: - Requests

    private func debounceFired() {
        debounceTask = nil
        searchCurrentQuery()
    }

    private func searchCurrentQuery() {
        let term = Self.trimmed(query)
        guard !term.isEmpty else {
            goIdle()
            return
        }
        let request = Request(query: term, mode: mode)
        if request == current { return }
        start(request)
    }

    private func start(_ request: Request) {
        searchTask?.cancel()
        latestToken += 1
        let token = latestToken
        current = request
        phase = .loading
        shownQuery = request.query
        shownMode = request.mode
        AppLog.browse.notice("search_start mode=\(request.mode.rawValue, privacy: .public) length=\(request.query.count, privacy: .public)")
        let dataSource = dataSource
        searchTask = Task { [weak self] in
            let result = await dataSource.search(query: request.query, scope: request.mode)
            self?.apply(result, of: request, token: token)
        }
    }

    private func apply(_ result: Result<[SearchResultItem], SearchLoadError>, of request: Request, token: Int) {
        guard token == latestToken else { return }
        searchTask = nil
        shownQuery = request.query
        shownMode = request.mode
        switch result {
        case .success(let items):
            phase = items.isEmpty ? .empty : .results(items)
        case .failure:
            phase = .failed
            current = nil
        }
    }

    private func goIdle() {
        searchTask?.cancel()
        searchTask = nil
        latestToken += 1
        current = nil
        phase = .idle
        shownQuery = ""
    }

    private static func trimmed(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

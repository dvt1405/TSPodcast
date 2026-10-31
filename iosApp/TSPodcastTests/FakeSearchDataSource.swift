import Foundation
@testable import TSPodcast

/// Scripted `SearchDataSource`: records every request, answers with the result set for its
/// query (default: one item named after the query) and can hold calls per query until released.
final class FakeSearchDataSource: SearchDataSource, @unchecked Sendable {
    struct Request: Equatable {
        let query: String
        let scope: SearchScope
    }

    private let lock = NSLock()
    private var _requests: [Request] = []
    private var resultsByQuery: [String: Result<[SearchResultItem], SearchLoadError>] = [:]
    private var heldQueries: Set<String> = []
    private var waiting: [String: [CheckedContinuation<Void, Never>]] = [:]

    private var categoryResults: [Result<[PodcastCard], SearchLoadError>]
    private var _categoryRequests: [Int] = []

    init(categoryResults: Result<[PodcastCard], SearchLoadError>...) {
        self.categoryResults = categoryResults.isEmpty ? [.success([])] : categoryResults
    }

    var requests: [Request] { lock.withLock { _requests } }
    var categoryRequests: [Int] { lock.withLock { _categoryRequests } }

    func setResult(_ result: Result<[SearchResultItem], SearchLoadError>, for query: String) {
        lock.withLock { resultsByQuery[query] = result }
    }

    /// Calls for `query` wait until `release(query)`.
    func hold(_ query: String) {
        lock.withLock { _ = heldQueries.insert(query) }
    }

    func release(_ query: String) {
        let continuations = lock.withLock {
            heldQueries.remove(query)
            return waiting.removeValue(forKey: query) ?? []
        }
        continuations.forEach { $0.resume() }
    }

    func heldCount(_ query: String) -> Int {
        lock.withLock { waiting[query]?.count ?? 0 }
    }

    func search(query: String, scope: SearchScope) async -> Result<[SearchResultItem], SearchLoadError> {
        let (mustWait, result) = lock.withLock {
            _requests.append(Request(query: query, scope: scope))
            return (heldQueries.contains(query), resultsByQuery[query] ?? .success([.sample(query: query, scope: scope)]))
        }
        if mustWait {
            await withCheckedContinuation { continuation in
                lock.withLock { waiting[query, default: []].append(continuation) }
            }
        }
        return result
    }

    func categoryPodcasts(categoryId: Int) async -> Result<[PodcastCard], SearchLoadError> {
        lock.withLock {
            _categoryRequests.append(categoryId)
            return categoryResults.count > 1 ? categoryResults.removeFirst() : categoryResults[0]
        }
    }
}

extension SearchResultItem {
    /// One result whose title tells which request produced it.
    static func sample(query: String, scope: SearchScope, id: Int64 = 1) -> SearchResultItem {
        SearchResultItem(
            id: id,
            title: "\(scope.rawValue):\(query)",
            subtitle: "Author",
            summary: "About \(query)",
            imageURL: nil,
            route: PodcastRoute(feedId: id, title: "\(scope.rawValue):\(query)", summary: "", imageURL: nil)
        )
    }
}

extension SearchLoadError {
    static let network = SearchLoadError(reason: "ResponseException")
}

/// Manual `Clock` for debounce tests: time only moves in `advance(by:)`, which first lets the
/// main actor run the tasks that were just started (so their `sleep` is registered at the
/// current time), then wakes every sleeper whose deadline has passed. A cancelled sleeper throws
/// `CancellationError` like the real clocks.
final class TestClock: Clock, @unchecked Sendable {
    struct Instant: InstantProtocol {
        var offset: Duration

        func advanced(by duration: Duration) -> Instant { Instant(offset: offset + duration) }
        func duration(to other: Instant) -> Duration { other.offset - offset }
        static func < (lhs: Instant, rhs: Instant) -> Bool { lhs.offset < rhs.offset }
    }

    private struct Sleeper {
        let deadline: Instant
        let continuation: CheckedContinuation<Void, any Error>
    }

    private let lock = NSLock()
    private var _now = Instant(offset: .zero)
    private var sleepers: [UUID: Sleeper] = [:]
    private var cancelled: Set<UUID> = []

    var now: Instant { lock.withLock { _now } }
    var minimumResolution: Duration { .zero }

    /// Sleepers not yet woken or cancelled.
    var sleeperCount: Int { lock.withLock { sleepers.count } }

    func sleep(until deadline: Instant, tolerance: Duration?) async throws {
        let id = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                let outcome: Result<Void, any Error>? = lock.withLock {
                    if cancelled.remove(id) != nil { return .failure(CancellationError()) }
                    if deadline <= _now { return .success(()) }
                    sleepers[id] = Sleeper(deadline: deadline, continuation: continuation)
                    return nil
                }
                if let outcome { continuation.resume(with: outcome) }
            }
        } onCancel: {
            let sleeper: Sleeper? = lock.withLock {
                if let sleeper = sleepers.removeValue(forKey: id) { return sleeper }
                cancelled.insert(id)
                return nil
            }
            sleeper?.continuation.resume(throwing: CancellationError())
        }
    }

    /// Runs pending main-actor work, moves time forward and wakes the sleepers that are due.
    @MainActor
    func advance(by duration: Duration) async {
        await Self.settle()
        let due: [Sleeper] = lock.withLock {
            _now = _now.advanced(by: duration)
            let ready = sleepers.filter { $0.value.deadline <= _now }
            ready.keys.forEach { sleepers.removeValue(forKey: $0) }
            return ready.values.sorted { $0.deadline < $1.deadline }
        }
        due.forEach { $0.continuation.resume() }
        await Self.settle()
    }

    /// Lets queued main-actor tasks run.
    @MainActor
    static func settle() async {
        for _ in 0..<50 {
            await Task.yield()
        }
    }
}

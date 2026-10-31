import Foundation
@testable import TSPodcast

/// Scripted `HomeDataSource`: counts calls per loader and can hold a loader until `release`.
final class FakeHomeDataSource: HomeDataSource, @unchecked Sendable {
    private let lock = NSLock()
    private var _trending: TrendingSectionsResult
    private var _music: Result<[PodcastCard], HomeLoadError>
    private var _live: Result<[LiveRowItem], HomeLoadError>
    private var _recent: Result<[PodcastCard], HomeLoadError>
    private var _calls: [HomeModel.Loader: Int] = [:]
    private var held: Set<HomeModel.Loader> = []
    private var waiting: [HomeModel.Loader: [CheckedContinuation<Void, Never>]] = [:]

    init(
        trending: Result<[PodcastCard], HomeLoadError> = .success([.sample(1)]),
        forYou: Result<[PodcastCard], HomeLoadError> = .success([.sample(2)]),
        music: Result<[PodcastCard], HomeLoadError> = .success([.sample(3)]),
        live: Result<[LiveRowItem], HomeLoadError> = .success([.sample(4)]),
        recent: Result<[PodcastCard], HomeLoadError> = .success([.sample(5)])
    ) {
        _trending = TrendingSectionsResult(trending: trending, forYou: forYou)
        _music = music
        _live = live
        _recent = recent
    }

    // MARK: Scripting

    func setTrending(_ trending: Result<[PodcastCard], HomeLoadError>, forYou: Result<[PodcastCard], HomeLoadError>) {
        lock.withLock { _trending = TrendingSectionsResult(trending: trending, forYou: forYou) }
    }

    func setMusic(_ result: Result<[PodcastCard], HomeLoadError>) { lock.withLock { _music = result } }
    func setLive(_ result: Result<[LiveRowItem], HomeLoadError>) { lock.withLock { _live = result } }
    func setRecent(_ result: Result<[PodcastCard], HomeLoadError>) { lock.withLock { _recent = result } }

    func calls(_ loader: HomeModel.Loader) -> Int { lock.withLock { _calls[loader] ?? 0 } }

    var allCalls: [HomeModel.Loader: Int] { lock.withLock { _calls } }

    /// Calls of `loaders` wait until `release` from now on.
    func hold(_ loaders: HomeModel.Loader...) { lock.withLock { held.formUnion(loaders) } }

    /// Lets the waiting and future calls of every loader go through.
    func releaseAll() {
        let continuations = lock.withLock {
            held.removeAll()
            defer { waiting.removeAll() }
            return waiting.values.flatMap { $0 }
        }
        continuations.forEach { $0.resume() }
    }

    /// Number of calls currently held.
    var heldCount: Int { lock.withLock { waiting.values.reduce(0) { $0 + $1.count } } }

    // MARK: HomeDataSource

    func trendingSections() async -> TrendingSectionsResult {
        await enter(.trendingSections)
        return lock.withLock { _trending }
    }

    func music() async -> Result<[PodcastCard], HomeLoadError> {
        await enter(.music)
        return lock.withLock { _music }
    }

    func live() async -> Result<[LiveRowItem], HomeLoadError> {
        await enter(.live)
        return lock.withLock { _live }
    }

    func recent() async -> Result<[PodcastCard], HomeLoadError> {
        await enter(.recent)
        return lock.withLock { _recent }
    }

    private func enter(_ loader: HomeModel.Loader) async {
        let mustWait = lock.withLock {
            _calls[loader, default: 0] += 1
            return held.contains(loader)
        }
        guard mustWait else { return }
        await withCheckedContinuation { continuation in
            lock.withLock { waiting[loader, default: []].append(continuation) }
        }
    }
}

extension PodcastCard {
    static func sample(_ id: Int64) -> PodcastCard {
        PodcastCard(id: id, title: "Podcast \(id)", summary: "Summary \(id)", imageURL: "https://img.example.com/\(id).jpg")
    }
}

extension LiveRowItem {
    static func sample(_ id: Int64) -> LiveRowItem {
        LiveRowItem(
            id: id,
            title: "Live \(id)",
            subtitle: "Today",
            imageURL: nil,
            feedId: 900 + id,
            feedTitle: "Feed \(id)",
            enclosureURL: "https://media.example.com/live\(id).mp3",
            referer: nil,
            durationSeconds: 0
        )
    }
}

extension HomeLoadError {
    static let network = HomeLoadError(reason: "ResponseException")
}

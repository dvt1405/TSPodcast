import Foundation
import Observation
@preconcurrency import Shared

/// M0 debug screen: E1 `podcasts/trending` loaded through `shared`, with the parameters of
/// Home section 1 (features-spec §3.4): max 100, since = now − 7 days (seconds), lang "vi".
@MainActor
@Observable
final class TrendingDebugModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let title: String
        let author: String?
    }

    enum State: Equatable {
        case loading
        case loaded([Row])
        case missingCredentials
        case failed(String)
    }

    private(set) var state: State = .loading

    @ObservationIgnored private let graph: SharedGraph
    @ObservationIgnored private var hasLoaded = false

    init(graph: SharedGraph) {
        self.graph = graph
    }

    /// First load only; `reload()` forces a new request.
    func loadIfNeeded() async {
        guard !hasLoaded else { return }
        hasLoaded = true
        await reload()
    }

    func reload() async {
        state = .loading
        let since = Int64(Date.now.addingTimeInterval(-7 * 24 * 60 * 60).timeIntervalSince1970)
        do {
            let result = try await graph.getTrendingPodcasts.invoke(
                max: 100,
                since: since,
                lang: "vi",
                cat: nil,
                notcat: nil
            )
            apply(result)
        } catch is CancellationError {
            // The view went away; keep the current state.
        } catch {
            AppLog.m0.error("trending_error type=\(String(describing: type(of: error)), privacy: .public)")
            state = .failed("Không tải được danh sách trending.")
        }
    }

    private func apply(_ result: TSDataState<TrendingPodcastRes>) {
        if let success = result as? TSDataStateSuccess<TrendingPodcastRes> {
            let rows = success.data.items.enumerated().map { index, podcast in
                Row(
                    id: "\(index)-\(podcast.id)",
                    title: podcast.title?.nonBlank ?? "(không có tiêu đề)",
                    author: podcast.author?.nonBlank
                )
            }
            AppLog.m0.notice("trending_loaded count=\(rows.count, privacy: .public)")
            state = .loaded(rows)
        } else if let failure = result as? TSDataStateError<TrendingPodcastRes> {
            if failure.exception is MissingCredentialsException {
                AppLog.m0.error("missing_credentials")
                state = .missingCredentials
            } else {
                let type = failure.exception.typeName
                AppLog.m0.error("trending_error type=\(type, privacy: .public)")
                state = .failed("Không tải được danh sách trending (\(type)).")
            }
        } else {
            state = .loading
        }
    }
}

private extension String {
    var nonBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

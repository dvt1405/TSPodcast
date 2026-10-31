import Foundation
import Observation

/// Navigation value of the "podcasts trending in a category" screen, pushed on the Search stack
/// by a category chip.
struct CategoryPodcastsRoute: Hashable, Sendable {
    let category: CategoryItem
}

/// Podcasts trending in one category (features-spec §3.9 [Sửa]: Android loads the E9 categories
/// on Search but never shows them). E1 with `cat=<id>` through shared `SearchFeed`, with its own
/// loading, empty and failed states and "Tải lại".
@MainActor
@Observable
final class CategoryPodcastsModel {
    enum State: Equatable, Sendable {
        case loading
        case loaded([PodcastCard])
        case empty
        case failed
    }

    let category: CategoryItem
    private(set) var state: State = .loading

    @ObservationIgnored private let dataSource: any SearchDataSource
    @ObservationIgnored private var hasStarted = false
    /// Latest request; an older result that arrives late is dropped.
    @ObservationIgnored private var latestRequest = 0

    init(category: CategoryItem, dataSource: any SearchDataSource) {
        self.category = category
        self.dataSource = dataSource
    }

    /// First load, once per pushed screen (`.task` runs again when the view reappears).
    func loadIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        await load()
    }

    /// "Tải lại" of the empty or failed state.
    func retry() async {
        hasStarted = true
        await load()
    }

    private func load() async {
        latestRequest += 1
        let request = latestRequest
        state = .loading
        let result = await dataSource.categoryPodcasts(categoryId: category.id)
        guard request == latestRequest else { return }
        switch result {
        case .success(let cards):
            state = cards.isEmpty ? .empty : .loaded(cards)
        case .failure:
            state = .failed
        }
    }
}

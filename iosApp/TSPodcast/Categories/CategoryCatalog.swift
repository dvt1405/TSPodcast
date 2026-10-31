import Foundation
@preconcurrency import Shared

/// A PodcastIndex category as the UI sees it (views never hold Kotlin objects).
struct CategoryItem: Identifiable, Hashable, Sendable {
    let id: Int
    let name: String
}

extension CategoryItem {
    init(_ category: CategoryRes.Category) {
        self.init(id: Int(category.id), name: category.name)
    }

    /// Kotlin value for `FavouriteCategories.save` (it sets `isFavourite` itself).
    var sharedCategory: CategoryRes.Category {
        CategoryRes.Category(id: Int32(id), name: name, isFavourite: nil)
    }
}

/// The E9 category list of this process. Onboarding calls `prefetch()` when it appears
/// (`OnboardingViewModel.loadCategory` runs in `init` on Android) and the category screen
/// awaits the same request, so E9 goes out once even when the user is quicker than the network.
///
/// Shared's `GetCategories` never fails: network, else last list in memory, else `CategoryRes` on
/// disk, else the bundled `categories.json`. An empty result (only possible on cancellation) is
/// not kept, so the next call asks again.
@MainActor
final class CategoryCatalog {
    private let fetch: @MainActor () async -> [CategoryItem]
    private var request: Task<[CategoryItem], Never>?

    init(fetch: @escaping @MainActor () async -> [CategoryItem]) {
        self.fetch = fetch
    }

    /// Catalog backed by `SharedGraph.getCategories`.
    convenience init(getCategories: GetCategories) {
        self.init {
            do {
                let state = try await getCategories.invoke(useCache: true)
                guard let success = state as? TSDataStateSuccess<CategoryRes> else { return [] }
                return success.data.feeds.map(CategoryItem.init)
            } catch {
                AppLog.app.error("categories_failed type=\(String(describing: type(of: error)), privacy: .public)")
                return []
            }
        }
    }

    /// Starts the request if none is running or done. Returns immediately.
    func prefetch() {
        _ = currentRequest(reason: "prefetch")
    }

    /// The categories, waiting for the running request when there is one.
    func categories() async -> [CategoryItem] {
        let request = currentRequest(reason: "load")
        let items = await request.value
        if items.isEmpty, self.request == request {
            self.request = nil
        }
        return items
    }

    private func currentRequest(reason: String) -> Task<[CategoryItem], Never> {
        if let request { return request }
        AppLog.app.notice("categories_\(reason, privacy: .public)")
        let fetch = fetch
        let request = Task { @MainActor in
            let items = await fetch()
            AppLog.app.notice("categories_loaded count=\(items.count, privacy: .public)")
            return items
        }
        self.request = request
        return request
    }
}

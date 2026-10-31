import Foundation
import Observation

/// State of the favourite category screen (`SelectFavouriteCategoryScreen` +
/// `OnboardingViewModel.putFavouriteItem/removeFavouriteItem/saveFavouriteItem` on Android).
///
/// The selection keeps the tap order, which is the order of the badges. Saving goes through
/// shared `FavouriteCategories.save`, which refuses fewer than 3 distinct ids.
@MainActor
@Observable
final class CategorySelectionModel {
    enum State: Equatable {
        case loading
        case loaded([CategoryItem])
    }

    private(set) var state: State = .loading
    private(set) var selected: [CategoryItem] = []
    private(set) var isSaving = false

    let minimumSelection: Int

    @ObservationIgnored private let load: @MainActor () async -> [CategoryItem]
    @ObservationIgnored private let persist: @MainActor ([CategoryItem]) async throws -> Bool

    init(
        minimumSelection: Int,
        load: @escaping @MainActor () async -> [CategoryItem],
        save: @escaping @MainActor ([CategoryItem]) async throws -> Bool
    ) {
        self.minimumSelection = minimumSelection
        self.load = load
        self.persist = save
    }

    /// "Lưu và tiếp tục" is enabled from `minimumSelection` categories.
    var canSave: Bool { selected.count >= minimumSelection && !isSaving }

    func isSelected(_ category: CategoryItem) -> Bool {
        selected.contains { $0.id == category.id }
    }

    /// Loads the grid once; later calls keep what is shown.
    func loadIfNeeded() async {
        guard state == .loading else { return }
        state = .loaded(await load())
    }

    /// Tap on a grid cell: select it (appended to the badges) or unselect it.
    func toggle(_ category: CategoryItem) {
        if isSelected(category) {
            remove(category)
        } else {
            selected.append(category)
        }
    }

    /// The "x" of a badge.
    func remove(_ category: CategoryItem) {
        selected.removeAll { $0.id == category.id }
    }

    /// Saves the selection. Returns true when it is stored and the app may open Main.
    func save() async -> Bool {
        guard canSave else { return false }
        isSaving = true
        defer { isSaving = false }
        do {
            let saved = try await persist(selected)
            AppLog.app.notice("favourite_categories_saved count=\(self.selected.count, privacy: .public) ok=\(saved, privacy: .public)")
            return saved
        } catch {
            AppLog.app.error("favourite_categories_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return false
        }
    }
}

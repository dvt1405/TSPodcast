@preconcurrency import Shared

/// Screen shown by `RootView`, from shared's `StartDestination`. Routes replace each other (no
/// back stack), like Android's NavHost when `startDestination` changes: once on category
/// selection there is no way back to onboarding.
enum AppRoute: Equatable, Sendable {
    case onboarding
    case selectCategories
    case main

    init(_ destination: StartDestination) {
        switch destination {
        case StartDestination.main: self = .main
        case StartDestination.selectfavouritecategory: self = .selectCategories
        default: self = .onboarding
        }
    }

    var logName: String {
        switch self {
        case .onboarding: "Onboarding"
        case .selectCategories: "SelectFavouriteCategory"
        case .main: "Main"
        }
    }
}

import Foundation
import Observation
@preconcurrency import Shared

/// State of the onboarding pager, `OnboardingViewModel` on Android: the current page, the
/// `Onboarding` screen analytics and the "finished" flag.
@MainActor
@Observable
final class OnboardingModel {
    let pages: [OnboardingPage]
    /// Selected page of the pager (bound to the `TabView`).
    var currentPage = 0
    private(set) var isFinishing = false

    @ObservationIgnored private let analytics: any AnalyticsTracker
    @ObservationIgnored private let saveFinished: @MainActor () async throws -> Void
    @ObservationIgnored private var lastTrackedPage: Int?

    init(
        pages: [OnboardingPage] = OnboardingPage.all,
        analytics: any AnalyticsTracker,
        saveFinished: @escaping @MainActor () async throws -> Void
    ) {
        self.pages = pages
        self.analytics = analytics
        self.saveFinished = saveFinished
    }

    /// "Tiếp tục" only shows on the last page.
    var showsContinue: Bool { currentPage == pages.count - 1 }

    /// `onPageChanged`: `trackScreen("Onboarding", CurrentPage = page + 1)`, once per page
    /// change, including the first page when the pager appears (Android's `snapshotFlow` emits
    /// the initial page too).
    func pageDidChange(to page: Int) {
        guard page != lastTrackedPage, pages.indices.contains(page) else { return }
        lastTrackedPage = page
        analytics.trackScreen(screen: Self.screenName, params: ["CurrentPage": String(page + 1)])
    }

    /// `onFinishOnboarding`: saves `OnboardingFinished = true` through shared, then tracks
    /// `OnboardingFinished`. Returns true when the flag is stored and the app may move on.
    /// A second tap while the first is still saving does nothing (one event per tap).
    func finish() async -> Bool {
        guard !isFinishing else { return false }
        isFinishing = true
        do {
            try await saveFinished()
        } catch {
            isFinishing = false
            AppLog.app.error("onboarding_save_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return false
        }
        analytics.trackEvent(name: Self.finishedEvent, screen: Self.screenName)
        AppLog.app.notice("onboarding_finished")
        return true
    }

    static let screenName = "Onboarding"
    static let finishedEvent = "OnboardingFinished"
}

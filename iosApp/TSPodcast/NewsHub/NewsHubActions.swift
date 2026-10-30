import Foundation
@preconcurrency import Shared

/// What the news hub asks of the player (M6 §3.4). `AppEnvironment` is the app's; unit tests
/// record the calls.
@MainActor
protocol NewsHubPlayback: AnyObject {
    /// Starts `request`; `presentsPlayer` also opens the full-screen Player (as a Detail tap does).
    func play(_ request: PlaybackRequest, presentsPlayer: Bool)
    /// `AudioEngine.setSleepTimer`, which logs `sleep_timer_set` with `origin`.
    func setSleepTimer(_ setting: SleepTimerSetting, origin: String)
}

/// The three ways the hub plays, shared by the Home section and the hub screen:
/// - "Phát tất cả" of the morning brief: the brief from its first item, **auto-advance on**
///   (the only list of the app that moves on by itself), Player opened, `news_brief_play`;
/// - a "Mới nhất" episode: the "Mới nhất" list from that item, auto-advance off, Player opened;
/// - a night shelf episode: the night list from that item, auto-advance off, the Player stays
///   closed (the toast "Đã hẹn giờ tắt 30 phút · Đổi" shows on the hub), and the sleep timer is
///   set to the section's minutes (`sleep_timer_set` origin `night_shelf`).
@MainActor
struct NewsHubActions {
    let playback: any NewsHubPlayback
    let analytics: any AnalyticsTracker

    /// Plays the brief; false when it is empty.
    @discardableResult
    func playBrief(_ content: NewsHubContent, screen: String) -> Bool {
        guard !content.brief.isEmpty else { return false }
        playback.play(PlaybackRequest(news: content.brief, startIndex: 0, autoAdvance: true), presentsPlayer: true)
        let events = AnalyticsEvents.shared
        analytics.trackEvent(
            name: events.NEWS_BRIEF_PLAY,
            screen: screen,
            params: [events.PARAM_COUNT: String(content.brief.count)]
        )
        AppLog.playback.notice("news_brief_play count=\(content.brief.count, privacy: .public)")
        return true
    }

    func playLatest(_ content: NewsHubContent, at index: Int) {
        guard content.latest.indices.contains(index) else { return }
        playback.play(PlaybackRequest(news: content.latest, startIndex: index), presentsPlayer: true)
    }

    /// Plays night episode `index` with the section's timer; returns the minutes set (the toast).
    @discardableResult
    func playNight(_ content: NewsHubContent, at index: Int) -> Int? {
        guard content.night.indices.contains(index) else { return nil }
        playback.play(PlaybackRequest(news: content.night, startIndex: index), presentsPlayer: false)
        let setting = SleepTimerSetting.clamped(minutes: content.nightSleepTimerMinutes)
        playback.setSleepTimer(setting, origin: AnalyticsEvents.shared.ORIGIN_NIGHT_SHELF)
        guard case let .minutes(minutes) = setting else { return nil }
        return minutes
    }
}

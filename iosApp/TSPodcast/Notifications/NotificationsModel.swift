import Foundation
import Observation

/// Push opt-in for new podcasts (features-spec §3.11, D4, M4 §4.4, plan §7 item 9).
///
/// - Nothing is asked at launch: `start()` only reads the saved choice and, when the user opted
///   in earlier and iOS still allows alerts, registers for the APNs token again.
/// - The in-app prompt "Nhận thông báo khi có podcast mới?" shows once, the first time the
///   number of favourites goes from 0 to 1 or more; `NotificationsPromptShown` is saved whatever
///   the answer.
/// - Turning on (prompt or Settings) asks iOS; granted → register for APNs, subscribe the FCM topic
///   `new_podcasts`, save `NotificationsOptIn = true`. Refused → stays off and offers the Settings
///   app. Turning off unsubscribes and saves false (the system permission stays as it is).
///
/// Android has no opt-in: every install gets FCM pushes (App Store guideline 4.5.4 needs consent).
@MainActor
@Observable
final class NotificationsModel {
    /// The Settings toggle: opted in and iOS still allows alerts.
    private(set) var isEnabled = false
    /// The in-app prompt is shown (an alert of the Player, where the first favourite is set).
    var showPrompt = false
    /// iOS refused the permission: alert with "Mở Cài đặt".
    var showDeniedAlert = false

    @ObservationIgnored private let preferences: any NotificationPreferences
    @ObservationIgnored private let system: any NotificationSystem
    @ObservationIgnored private let push: any PushRegistrar
    /// Favourite count last seen; nil before the first value (the launch value never prompts).
    @ObservationIgnored private var favouriteCount: Int?
    @ObservationIgnored private var promptAlreadyShown: Bool?

    init(preferences: any NotificationPreferences, system: any NotificationSystem, push: any PushRegistrar) {
        self.preferences = preferences
        self.system = system
        self.push = push
    }

    /// Launch: reads the saved opt-in. Never asks for the permission.
    func start() async {
        guard await preferences.optIn() else {
            isEnabled = false
            return
        }
        let authorization = await system.authorization()
        isEnabled = authorization == .authorized
        if isEnabled {
            // A new APNs token for FCM each launch (Android fetches the FCM token in App.onCreate).
            system.registerForRemoteNotifications()
        }
        AppLog.notifications.notice("notifications_start opt_in=true authorized=\(self.isEnabled, privacy: .public)")
    }

    /// The favourite list changed size. The first 0 → ≥ 1 change shows the prompt once.
    func favouritesChanged(count: Int) async {
        let previous = favouriteCount
        favouriteCount = count
        guard previous == 0, count > 0, !isEnabled, !showPrompt else { return }
        if promptAlreadyShown == nil {
            promptAlreadyShown = await preferences.promptShown()
        }
        guard promptAlreadyShown == false else { return }
        showPrompt = true
        AppLog.notifications.notice("notifications_prompt shown")
    }

    /// "Bật thông báo" of the prompt.
    func acceptPrompt() async {
        await closePrompt()
        await enable()
    }

    /// "Để sau" of the prompt: never asked again in the app (the Settings toggle stays).
    func declinePrompt() async {
        await closePrompt()
    }

    /// The Settings toggle. Turning on skips the in-app prompt and goes to the iOS dialog.
    func setEnabled(_ enabled: Bool) async {
        if enabled {
            await enable()
        } else {
            await disable()
        }
    }

    /// "Mở Cài đặt" of the denied alert.
    func openSystemSettings() {
        showDeniedAlert = false
        system.openSettings()
    }

    private func closePrompt() async {
        showPrompt = false
        promptAlreadyShown = true
        await preferences.setPromptShown(true)
    }

    private func enable() async {
        // The only place that asks for the permission (M4-AC18).
        let granted = await system.requestAuthorization()
        guard granted else {
            isEnabled = false
            await preferences.setOptIn(false)
            showDeniedAlert = true
            AppLog.notifications.notice("notifications_opt_in denied")
            return
        }
        system.registerForRemoteNotifications()
        await push.subscribe(topic: PushTopic.newPodcasts)
        await preferences.setOptIn(true)
        isEnabled = true
        AppLog.notifications.notice("notifications_opt_in on")
    }

    private func disable() async {
        await push.unsubscribe(topic: PushTopic.newPodcasts)
        await preferences.setOptIn(false)
        isEnabled = false
        AppLog.notifications.notice("notifications_opt_in off")
    }
}

import FirebaseAnalytics
import FirebaseCrashlytics
import Foundation

/// Firebase's collection switches (M6 §3.6). Info.plist starts both off; `ConsentController`
/// turns them on only after an opt-in, and off again when the user withdraws it. Created only
/// when Firebase is configured (both calls need a configured `FirebaseApp`).
struct FirebaseCollectionSwitch: CollectionSwitch {
    func setAnalyticsCollectionEnabled(_ enabled: Bool) {
        Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    func setCrashCollectionEnabled(_ enabled: Bool) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
    }
}

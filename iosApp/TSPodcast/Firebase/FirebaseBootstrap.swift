import FirebaseCore
import Foundation

/// Whether Firebase runs in this process (M4 §4.3). Firebase is optional: without the owner's
/// `GoogleService-Info.plist` the app uses the log/no-op seams and never touches a Firebase API.
enum FirebaseStatus: Equatable, Sendable {
    case enabled
    case disabled(FirebaseDisabledReason)

    var isEnabled: Bool { self == .enabled }
}

enum FirebaseDisabledReason: String, Equatable, Sendable {
    /// No `GoogleService-Info.plist` in the app bundle (the default until the owner adds one).
    case missingPlist = "missing_plist"
    /// The file is there but `FirebaseOptions` cannot read it.
    case invalidPlist = "invalid_plist"
}

/// Step 1 of the launch (Android `FirebaseApp.initializeApp`): configures Firebase only when the
/// bundle carries `GoogleService-Info.plist`. The build phase "Copy GoogleService-Info.plist"
/// copies `iosApp/Config/Firebase/<Debug|Release>/GoogleService-Info.plist` (gitignored) when
/// the owner has put it there.
enum FirebaseBootstrap {
    static let plistName = "GoogleService-Info"

    /// Configures Firebase from the plist of `bundle` and logs `firebase=enabled` or
    /// `firebase=disabled reason=<reason>`. `configure` is the Firebase call; it receives the
    /// plist path and returns false when the file cannot be read. Tests pass a recording one.
    @MainActor
    static func configureIfAvailable(
        bundle: Bundle = .main,
        configure: (String) -> Bool = FirebaseBootstrap.configureFirebase
    ) -> FirebaseStatus {
        let status: FirebaseStatus
        if let path = bundle.path(forResource: plistName, ofType: "plist") {
            status = configure(path) ? .enabled : .disabled(.invalidPlist)
        } else {
            status = .disabled(.missingPlist)
        }
        switch status {
        case .enabled:
            AppLog.firebase.notice("firebase=enabled")
        case let .disabled(reason):
            AppLog.firebase.notice("firebase=disabled reason=\(reason.rawValue, privacy: .public)")
        }
        return status
    }

    /// `FirebaseApp.configure(options:)` from the file at `path`. Never logs the file's values.
    static func configureFirebase(plistPath path: String) -> Bool {
        guard FirebaseApp.app() == nil else { return true }
        guard let options = FirebaseOptions(contentsOfFile: path) else { return false }
        FirebaseApp.configure(options: options)
        return true
    }
}

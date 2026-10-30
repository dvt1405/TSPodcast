import Foundation

/// What the user allowed (M6 §3.6, N11 lite), as plain Swift values. `needsPrompt`: never asked,
/// or asked for an older consent text; both choices then count as off.
struct ConsentSnapshot: Equatable, Sendable {
    var analytics = false
    var crash = false
    var needsPrompt = true
    /// When the choice was saved; nil before the first decision.
    var decidedAt: Date?

    /// A fresh install: nothing allowed, the sheet must show.
    static let undecided = ConsentSnapshot()
}

/// The two switches every gated seam reads on each call, from any thread. Starts closed: until
/// the saved choice is read at launch nothing optional goes out (the read happens before the
/// first screen, `AppEnvironment.resolveStartRoute`).
final class ConsentGate: @unchecked Sendable {
    private let lock = NSLock()
    private var analytics = false
    private var crash = false

    init(analytics: Bool = false, crash: Bool = false) {
        self.analytics = analytics
        self.crash = crash
    }

    var allowsAnalytics: Bool { lock.withLock { analytics } }
    var allowsCrashReports: Bool { lock.withLock { crash } }

    func update(analytics: Bool, crash: Bool) {
        lock.withLock {
            self.analytics = analytics
            self.crash = crash
        }
    }
}

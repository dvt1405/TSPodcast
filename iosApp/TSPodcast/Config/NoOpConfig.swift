import Foundation
@preconcurrency import Shared

/// `ConfigAPI` until Firebase Remote Config exists on iOS: every key is unknown, so shared
/// falls back to its bundled defaults (for example `blacklist.json`).
final class NoOpConfig: NSObject, ConfigAPI, Sendable {
    func getString(key: String) -> String? { nil }
}

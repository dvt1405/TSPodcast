import Foundation
@preconcurrency import Shared

/// `ConsentStore` on shared `ConsentRepository`. The store never throws; a cancelled read counts
/// as "not decided" (the sheet shows again, nothing is allowed).
struct SharedConsentStore: ConsentStore, @unchecked Sendable {
    let repository: ConsentRepository

    func state() async -> ConsentSnapshot {
        do {
            return Self.snapshot(try await repository.state())
        } catch {
            AppLog.app.error("consent_read_failed")
            return .undecided
        }
    }

    func save(analytics: Bool, crash: Bool) async -> ConsentSnapshot {
        do {
            return Self.snapshot(try await repository.update(analytics: analytics, crash: crash))
        } catch {
            AppLog.app.error("consent_save_failed")
            return ConsentSnapshot(analytics: analytics, crash: crash, needsPrompt: false, decidedAt: Date())
        }
    }

    static func snapshot(_ state: ConsentState) -> ConsentSnapshot {
        ConsentSnapshot(
            analytics: state.analytics,
            crash: state.crash,
            needsPrompt: state.needsPrompt,
            decidedAt: state.decidedAtMillis.map { Date(timeIntervalSince1970: TimeInterval($0.int64Value) / 1000) }
        )
    }
}

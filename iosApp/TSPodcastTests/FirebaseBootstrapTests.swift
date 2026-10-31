import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC15: Firebase is optional. Without `GoogleService-Info.plist` nothing calls Firebase and
/// the app runs on the log/no-op seams.
@MainActor
final class FirebaseBootstrapTests: XCTestCase {
    /// The test bundle has no `GoogleService-Info.plist`.
    private var bundleWithoutPlist: Bundle { Bundle(for: Self.self) }

    func testMissingPlistDisablesFirebaseWithoutConfiguring() {
        var configureCalls = 0
        let status = FirebaseBootstrap.configureIfAvailable(bundle: bundleWithoutPlist) { _ in
            configureCalls += 1
            return true
        }

        XCTAssertEqual(status, .disabled(.missingPlist))
        XCTAssertFalse(status.isEnabled)
        XCTAssertEqual(configureCalls, 0, "FirebaseApp.configure must not run without the plist")
    }

    func testPlistInBundleConfiguresWithItsPath() throws {
        let bundle = try makeBundle(withPlist: true)
        var paths: [String] = []
        let status = FirebaseBootstrap.configureIfAvailable(bundle: bundle) { path in
            paths.append(path)
            return true
        }

        XCTAssertEqual(status, .enabled)
        XCTAssertEqual(paths.count, 1)
        XCTAssertEqual(paths.first.map { URL(filePath: $0).lastPathComponent }, "GoogleService-Info.plist")
    }

    func testUnreadablePlistDisablesFirebase() throws {
        let bundle = try makeBundle(withPlist: true)
        let status = FirebaseBootstrap.configureIfAvailable(bundle: bundle) { _ in false }
        XCTAssertEqual(status, .disabled(.invalidPlist))
    }

    func testOfflineServicesAreTheLogAndNoOpOnes() {
        let services = AppServices.make(for: .disabled(.missingPlist))

        XCTAssertTrue(services.analytics is LogAnalyticsTracker)
        XCTAssertTrue(services.crashReporter is OSLogCrashReporter)
        XCTAssertTrue(services.config is NoOpConfig)
        XCTAssertTrue(services.push is LogPushRegistrar)
        XCTAssertNil(services.remoteConfig, "no Remote Config fetch without Firebase")
    }

    /// The whole launch over a bundle without the plist: no configure call, offline seams in
    /// `AppEnvironment`, and the shared `Crash` gets the OSLog reporter.
    func testAppEnvironmentWithoutPlistUsesOfflineSeams() async {
        let fixture = SharedGraphFixture()
        var configureCalls = 0
        var installed: [any CrashReporter] = []
        let bundle = bundleWithoutPlist
        let steps = LaunchSteps(
            configureFirebase: {
                FirebaseBootstrap.configureIfAvailable(bundle: bundle) { _ in
                    configureCalls += 1
                    return true
                }
            },
            makeServices: { AppServices.make(for: $0) },
            installCrashReporter: { installed.append($0) },
            makeGraph: { _, _ in (fixture.graph, NoOpPruner()) }
        )
        let environment = AppEnvironment(launchOptions: .none, artworkLoader: .shared, launch: steps)
        defer {
            environment.audioEngine.shutdown()
            fixture.close()
        }

        XCTAssertEqual(configureCalls, 0)
        XCTAssertEqual(environment.firebase, .disabled(.missingPlist))
        XCTAssertTrue((environment.analytics as? ConsentGatedAnalyticsTracker)?.inner is LogAnalyticsTracker)
        XCTAssertTrue(environment.services.crashReporter is OSLogCrashReporter)
        XCTAssertTrue(environment.services.config is NoOpConfig)
        XCTAssertTrue(environment.services.push is LogPushRegistrar)
        XCTAssertEqual(installed.count, 1)
        XCTAssertTrue((installed.first as? ConsentGatedCrashReporter)?.inner is OSLogCrashReporter)
        XCTAssertTrue(environment.services.collection is NoCollectionSwitch, "no Firebase API without the plist")
    }

    /// The test host app is built without the plist (`FIREBASE_PLIST_DIR` points to an empty
    /// directory in every verification build), so it runs without Firebase.
    func testHostAppHasNoFirebasePlist() {
        XCTAssertNil(Bundle.main.path(forResource: FirebaseBootstrap.plistName, ofType: "plist"))
    }

    /// A directory bundle, optionally with a dummy `GoogleService-Info.plist` (never a real one).
    private func makeBundle(withPlist: Bool) throws -> Bundle {
        let directory = FileManager.default.temporaryDirectory.appending(path: "bundle-\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        if withPlist {
            let plist: [String: Any] = ["PLIST_VERSION": "1"]
            let data = try PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0)
            try data.write(to: directory.appending(path: "GoogleService-Info.plist"))
        }
        return try XCTUnwrap(Bundle(url: directory))
    }
}

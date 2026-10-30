import XCTest
@testable import TSPodcast

/// The XCUITest hooks of M1 §4.7 (Debug build, which is what the test action runs).
final class LaunchOptionsTests: XCTestCase {
    func testNoArgumentsMeansNoHooks() {
        let options = LaunchOptions(arguments: ["TSPodcast"], environment: [:])
        XCTAssertEqual(options, .none)
        XCTAssertFalse(options.shouldReset)
    }

    func testArgumentsAreRecognised() {
        let options = LaunchOptions(
            arguments: ["TSPodcast", "-AppleLanguages", "(vi)", "-uitest-reset", "-uitest-force-live", "-uitest-slow-network"],
            environment: [:]
        )
        XCTAssertTrue(options.resetState)
        XCTAssertFalse(options.skipOnboarding)
        XCTAssertTrue(options.forceLive)
        XCTAssertTrue(options.slowNetwork)
        XCTAssertTrue(options.shouldReset)
    }

    func testSkipOnboardingImpliesReset() {
        let options = LaunchOptions(arguments: ["-uitest-skip-onboarding"], environment: [:])
        XCTAssertTrue(options.skipOnboarding)
        XCTAssertTrue(options.shouldReset)
    }

    func testBaseURLOverride() {
        XCTAssertEqual(
            LaunchOptions(arguments: [], environment: ["TSP_UITEST_BASE_URL": " https://nonexistent.invalid/api/1.0/ "]).baseURLOverride,
            "https://nonexistent.invalid/api/1.0/"
        )
        XCTAssertNil(LaunchOptions(arguments: [], environment: ["TSP_UITEST_BASE_URL": "  "]).baseURLOverride)
    }

    /// M3 §4.6: the audio fixture URL.
    func testAudioURLOverride() {
        XCTAssertEqual(
            LaunchOptions(arguments: [], environment: ["TSP_UITEST_AUDIO_URL": " file:///tmp/tone-60s.m4a "]).audioURLOverride,
            URL(string: "file:///tmp/tone-60s.m4a")
        )
        XCTAssertNil(LaunchOptions(arguments: [], environment: ["TSP_UITEST_AUDIO_URL": ""]).audioURLOverride)
        XCTAssertNil(LaunchOptions(arguments: [], environment: [:]).audioURLOverride)
    }

    func testOverrideReplacesBundleBaseURL() {
        let credentials = BundleCredentialsProvider(baseUrlOverride: "https://nonexistent.invalid/api/1.0/")
        XCTAssertEqual(credentials.baseUrl, "https://nonexistent.invalid/api/1.0/")
    }
}

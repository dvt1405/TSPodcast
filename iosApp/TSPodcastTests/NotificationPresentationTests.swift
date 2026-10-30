import UserNotifications
import XCTest
@testable import TSPodcast

/// M4-AC18: which pushes show in the foreground (only `new_podcasts`, D4) and the
/// `fcm_<type>_message_receive` event (Android `TSFcmServices.onMessageReceived`).
final class NotificationPresentationTests: XCTestCase {
    private let newPodcasts: [AnyHashable: Any] = [
        "aps": ["alert": ["title": "t", "body": "b"], "mutable-content": 1],
        "type": "new_podcasts",
        "big_image": "https://example.com/a.jpg",
        "gcm.message_id": "123",
        "count": 2,
    ]

    func testNewPodcastsShowsAsBannerInTheForeground() {
        let options = NotificationPresenter.presentationOptions(for: newPodcasts)
        XCTAssertTrue(options.contains(.banner))
        XCTAssertTrue(options.contains(.list))
        XCTAssertTrue(options.contains(.sound))
    }

    func testOtherTypesStayHidden() {
        for type in ["banner_ads", "video_ads", "full_screen_ads", "something_else", ""] {
            XCTAssertEqual(NotificationPresenter.presentationOptions(for: ["type": type]), [], type)
        }
        XCTAssertEqual(NotificationPresenter.presentationOptions(for: [:]), [], "no type")
        XCTAssertEqual(NotificationPresenter.presentationOptions(for: ["type": 1]), [], "type not a string")
    }

    func testWillPresentLogsOneReceiveEventWithThePayload() throws {
        let tracker = RecordingAnalyticsTracker()
        let presenter = NotificationPresenter(analytics: tracker)

        let options = presenter.willPresent(userInfo: newPodcasts)

        XCTAssertTrue(options.contains(.banner))
        XCTAssertEqual(tracker.events.count, 1)
        let event = try XCTUnwrap(tracker.events.first)
        XCTAssertEqual(event.name, "fcm_new_podcasts_message_receive")
        XCTAssertNil(event.screen)
        XCTAssertEqual(event.params, [
            "type": "new_podcasts",
            "big_image": "https://example.com/a.jpg",
            "gcm.message_id": "123",
            "count": "2",
        ])
        XCTAssertNil(event.params["aps"])
    }

    func testHiddenTypesAreStillLogged() throws {
        let tracker = RecordingAnalyticsTracker()
        let presenter = NotificationPresenter(analytics: tracker)

        let options = presenter.willPresent(userInfo: ["type": "banner_ads"])

        XCTAssertEqual(options, [])
        XCTAssertEqual(tracker.events.map(\.name), ["fcm_banner_ads_message_receive"])
    }

    func testTapLogsOneReceiveEvent() {
        let tracker = RecordingAnalyticsTracker()
        let presenter = NotificationPresenter(analytics: tracker)

        presenter.didReceive(userInfo: newPodcasts)

        XCTAssertEqual(tracker.events.map(\.name), ["fcm_new_podcasts_message_receive"])
        XCTAssertNil(tracker.events.first?.params["aps"])
    }

    func testMissingTypeLogsTheNullEventLikeAndroid() {
        let event = NotificationPresenter.receiveEvent(for: ["aps": ["alert": "x"]])
        XCTAssertEqual(event.name, "fcm_null_message_receive")
        XCTAssertEqual(event.params, [:])
    }
}

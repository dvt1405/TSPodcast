import XCTest
@testable import TSPodcast

final class LogAnalyticsTrackerTests: XCTestCase {
    func testScreenLineFormat() {
        XCTAssertEqual(
            LogAnalyticsTracker.screenLine(screen: "Onboarding", params: ["CurrentPage": "2"]),
            "analytics screen=Onboarding CurrentPage=2"
        )
        XCTAssertEqual(
            LogAnalyticsTracker.screenLine(screen: "Home", params: ["b": "2", "a": "1"]),
            "analytics screen=Home a=1 b=2"
        )
    }

    func testEventLineFormat() {
        XCTAssertEqual(
            LogAnalyticsTracker.eventLine(name: "OnboardingFinished", screen: "Onboarding"),
            "analytics event=OnboardingFinished screen=Onboarding"
        )
    }

    func testEventLineWithParamsSortedByName() {
        XCTAssertEqual(
            LogAnalyticsTracker.eventLine(
                name: "fcm_new_podcasts_message_receive",
                screen: "Home",
                params: ["type": "new_podcasts", "big_image": "x"]
            ),
            "analytics event=fcm_new_podcasts_message_receive screen=Home big_image=x type=new_podcasts"
        )
    }

    func testEventLineWithoutScreen() {
        XCTAssertEqual(
            LogAnalyticsTracker.eventLine(name: "fcm_null_message_receive", screen: nil, params: [:]),
            "analytics event=fcm_null_message_receive"
        )
    }
}

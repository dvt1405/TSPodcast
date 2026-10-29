import XCTest
@testable import TSPodcast

/// M2-AC8: the M2 player seam keeps the last request.
@MainActor
final class LoggingPlaybackControllerTests: XCTestCase {
    private func request(startIndex: Int) -> PlaybackRequest {
        PlaybackRequest(podcast: .sample, episodes: [.sample(1), .sample(2), .sample(3)], startIndex: startIndex)
    }

    func testStartsWithoutRequest() {
        XCTAssertNil(LoggingPlaybackController().lastRequest)
    }

    func testPlayKeepsTheRequest() {
        let controller = LoggingPlaybackController()
        let first = request(startIndex: 1)

        controller.play(first)

        XCTAssertEqual(controller.lastRequest, first)
        XCTAssertEqual(controller.lastRequest?.startEpisode, .sample(2))
    }

    func testSecondPlayReplacesTheFirst() {
        let controller = LoggingPlaybackController()

        controller.play(request(startIndex: 0))
        controller.play(request(startIndex: 2))

        XCTAssertEqual(controller.lastRequest, request(startIndex: 2))
    }

    func testIsUsableThroughTheProtocol() {
        let controller = LoggingPlaybackController()
        let seam: any PlaybackController = controller

        seam.play(request(startIndex: 0))

        XCTAssertEqual(controller.lastRequest?.startIndex, 0)
    }
}

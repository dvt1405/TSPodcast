import UIKit
import UserNotifications
import XCTest
@testable import TSPodcast

/// M4-AC19: the image attachment of the Notification Service Extension (`RichNotification`, the
/// same file the extension compiles). Requests go to `StubURLProtocol`, never the network.
final class RichNotificationTests: XCTestCase {
    private static let png: Data = {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4))
        return renderer.pngData { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
        }
    }()

    private let session = URLSession(configuration: StubURLProtocol.configuration())

    override func tearDown() {
        StubURLProtocol.reset { _ in .failure(.notConnectedToInternet) }
        super.tearDown()
    }

    private func content(_ userInfo: [AnyHashable: Any]) -> UNNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "Podcast mới"
        content.body = "body"
        content.userInfo = userInfo
        return content
    }

    private func enrich(_ userInfo: [AnyHashable: Any], timeout: Duration = .seconds(5)) async -> UNNotificationContent {
        await RichNotification(session: session, timeout: timeout).enrich(content(userInfo))
    }

    func testValidBigImageIsAttached() async {
        StubURLProtocol.reset { _ in .data(Self.png) }

        let result = await enrich(["type": "new_podcasts", "big_image": "https://example.com/cover.png"])

        XCTAssertEqual(result.attachments.count, 1)
        XCTAssertEqual(result.attachments.first?.identifier, RichNotification.attachmentIdentifier)
        XCTAssertEqual(result.title, "Podcast mới", "text unchanged")
        XCTAssertEqual(StubURLProtocol.requests.map { $0.url?.absoluteString }, ["https://example.com/cover.png"])
    }

    func testFcmOptionsImageIsUsedWithoutBigImage() async {
        StubURLProtocol.reset { _ in .data(Self.png) }

        let result = await enrich(["fcm_options": ["image": "https://example.com/fcm.png"]])

        XCTAssertEqual(result.attachments.count, 1)
        XCTAssertEqual(StubURLProtocol.requests.map { $0.url?.absoluteString }, ["https://example.com/fcm.png"])
    }

    func testNoImageGivesTheOriginalContent() async {
        StubURLProtocol.reset { _ in .data(Self.png) }

        let result = await enrich(["type": "new_podcasts"])

        XCTAssertEqual(result.attachments.count, 0)
        XCTAssertEqual(result.title, "Podcast mới")
        XCTAssertTrue(StubURLProtocol.requests.isEmpty, "nothing to download")
    }

    func testInvalidURLsAreIgnored() async {
        StubURLProtocol.reset { _ in .data(Self.png) }
        for value in ["", "   ", "not a url", "ftp://example.com/a.png", "file:///tmp/a.png", "https://"] {
            let result = await enrich(["big_image": value])
            XCTAssertEqual(result.attachments.count, 0, value)
        }
        XCTAssertTrue(StubURLProtocol.requests.isEmpty)
    }

    func testDownloadFailureGivesTheOriginalContent() async {
        StubURLProtocol.reset { _ in .failure(.notConnectedToInternet) }
        let result = await enrich(["big_image": "https://example.com/cover.png"])
        XCTAssertEqual(result.attachments.count, 0)
        XCTAssertEqual(result.body, "body")
    }

    func testHTTPErrorGivesTheOriginalContent() async {
        StubURLProtocol.reset { _ in .data(Data("missing".utf8), statusCode: 404) }
        let result = await enrich(["big_image": "https://example.com/cover.png"])
        XCTAssertEqual(result.attachments.count, 0)
    }

    func testNotAnImageGivesTheOriginalContent() async {
        StubURLProtocol.reset { _ in .data(Data("<html></html>".utf8)) }
        let result = await enrich(["big_image": "https://example.com/cover.png"])
        XCTAssertEqual(result.attachments.count, 0)
    }

    func testSlowServerTimesOutWithTheOriginalContent() async {
        StubURLProtocol.reset { _ in .hang }
        let started = ContinuousClock.now

        let result = await enrich(["big_image": "https://example.com/slow.png"], timeout: .milliseconds(300))

        XCTAssertEqual(result.attachments.count, 0)
        XCTAssertLessThan(ContinuousClock.now - started, .seconds(5))
    }

    func testImageTypeComesFromTheBytes() {
        XCTAssertEqual(RichNotification.imageExtension(header: Self.png), "png")
        XCTAssertEqual(RichNotification.imageExtension(header: Data([0xFF, 0xD8, 0xFF, 0xE0])), "jpg")
        XCTAssertEqual(RichNotification.imageExtension(header: Data("GIF89a".utf8)), "gif")
        XCTAssertEqual(RichNotification.imageExtension(header: Data("RIFF0000WEBP".utf8)), "webp")
        XCTAssertNil(RichNotification.imageExtension(header: Data("<html>".utf8)))
        XCTAssertNil(RichNotification.imageExtension(header: Data()))
    }

    func testImageURLRules() {
        XCTAssertEqual(RichNotification.imageURL(in: ["big_image": " https://a.example/x.jpg "])?.absoluteString, "https://a.example/x.jpg")
        XCTAssertEqual(RichNotification.imageURL(in: ["big_image": "http://a.example/x.jpg"])?.absoluteString, "http://a.example/x.jpg")
        XCTAssertEqual(
            RichNotification.imageURL(in: ["big_image": "nope", "fcm_options": ["image": "https://b.example/y.png"]])?.absoluteString,
            "https://b.example/y.png",
            "an invalid big_image falls back to fcm_options.image"
        )
        XCTAssertNil(RichNotification.imageURL(in: ["big_image": 42]))
    }
}

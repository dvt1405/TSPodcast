import XCTest
@testable import TSPodcast

/// M6-AC12: the pre-filled report mail (plain Swift, also run with `swift test` on macOS).
final class ContentReportMailTests: XCTestCase {
    private func components(_ url: URL?) throws -> (recipient: String, subject: String, body: String, raw: String) {
        let url = try XCTUnwrap(url)
        let raw = url.absoluteString
        XCTAssertEqual(url.scheme, "mailto")
        let parts = try XCTUnwrap(URLComponents(string: raw))
        let items = parts.queryItems ?? []
        return (
            parts.path,
            try XCTUnwrap(items.first { $0.name == "subject" }?.value),
            try XCTUnwrap(items.first { $0.name == "body" }?.value),
            raw
        )
    }

    func testEpisodeReportCarriesEveryId() throws {
        let mail = try components(ContentReportMail.url(
            to: "report@example.com",
            kind: .episode,
            feedId: 123,
            episodeId: 456,
            appVersion: "1.0.0 (7)"
        ))

        XCTAssertEqual(mail.recipient, "report@example.com")
        XCTAssertTrue(mail.subject.contains("episode 456"))
        XCTAssertTrue(mail.subject.contains("feed 123"))
        XCTAssertTrue(mail.body.contains("kind: episode"))
        XCTAssertTrue(mail.body.contains("feedId: 123"))
        XCTAssertTrue(mail.body.contains("episodeId: 456"))
        XCTAssertTrue(mail.body.contains("appVersion: 1.0.0 (7)"))
    }

    func testFeedReportHasNoEpisodeLine() throws {
        let mail = try components(ContentReportMail.url(to: "a@b.vn", kind: .feed, feedId: 9, episodeId: nil, appVersion: "1.0"))

        XCTAssertTrue(mail.body.contains("kind: feed"))
        XCTAssertFalse(mail.body.contains("episodeId"))
        XCTAssertFalse(mail.subject.contains("episode"))
    }

    func testHubReportNeedsNoIds() throws {
        let mail = try components(ContentReportMail.url(to: "a@b.vn", kind: .hub, feedId: nil, episodeId: nil, appVersion: "1.0"))

        XCTAssertTrue(mail.body.contains("kind: hub"))
        XCTAssertFalse(mail.body.contains("feedId"))
    }

    func testSubjectAndBodyArePercentEncoded() throws {
        let mail = try components(ContentReportMail.url(to: "a@b.vn", kind: .feed, feedId: 1, episodeId: nil, appVersion: "1.0 (2)"))

        XCTAssertFalse(mail.raw.contains(" "), "no raw space")
        XCTAssertFalse(mail.raw.contains("+"), "spaces are %20, never +")
        XCTAssertTrue(mail.raw.contains("%0D%0A"), "line breaks are CRLF")
        XCTAssertTrue(mail.raw.contains("%20"))
        XCTAssertFalse(mail.raw.contains("Báo"), "Vietnamese letters are encoded")
        XCTAssertEqual(mail.raw.components(separatedBy: "&").count, 2, "only the separator between subject and body")
        XCTAssertTrue(mail.subject.hasPrefix("[TSPodcast] Báo cáo nội dung"))
    }

    func testBadRecipientGivesNoURL() {
        for address in ["", "   ", "no-at-sign", "a@b", "@b.vn", "a b@c.vn", "$(CONTENT_REPORT_EMAIL)", "a@b.vn?cc=x@y.vn"] {
            XCTAssertNil(
                ContentReportMail.url(to: address, kind: .feed, feedId: 1, episodeId: nil, appVersion: "1"),
                "<\(address)>"
            )
        }
    }

    func testRecipientFromInfoPlist() {
        XCTAssertEqual(ContentReportMail.recipient(from: ["TSContentReportEmail": " report@example.com "]), "report@example.com")
        XCTAssertNil(ContentReportMail.recipient(from: ["TSContentReportEmail": ""]))
        XCTAssertNil(ContentReportMail.recipient(from: ["TSContentReportEmail": "$(CONTENT_REPORT_EMAIL)"]))
        XCTAssertNil(ContentReportMail.recipient(from: [:]))
    }
}

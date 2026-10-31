import XCTest
@testable import TSPodcast

/// M2-AC6: links in the Detail description (Android `linkify`).
final class DescriptionTextTests: XCTestCase {
    private func linkRuns(_ text: AttributedString) -> [(text: String, url: URL)] {
        text.runs.compactMap { run in
            run.link.map { (String(text[run.range].characters), $0) }
        }
    }

    func testURLBecomesOneLinkRun() {
        let text = DescriptionText.attributed("Xem https://example.com/a nhé")

        let links = linkRuns(text)
        XCTAssertEqual(links.count, 1)
        XCTAssertEqual(links.first?.url, URL(string: "https://example.com/a"))
        XCTAssertEqual(links.first?.text, "https://example.com/a")
        XCTAssertEqual(String(text.characters), "Xem https://example.com/a nhé", "text is unchanged")
    }

    func testSeveralLinksKeepTheTextAround() {
        let plain = "A https://a.example.com/x, B www.example.org và mail@example.com."
        let text = DescriptionText.attributed(plain)

        XCTAssertEqual(String(text.characters), plain)
        XCTAssertEqual(linkRuns(text).map(\.text), ["https://a.example.com/x", "www.example.org", "mail@example.com"])
    }

    func testTextWithoutURLHasNoLink() {
        let text = DescriptionText.attributed("Chỉ là chữ thường, không có liên kết.")

        XCTAssertTrue(linkRuns(text).isEmpty)
        XCTAssertEqual(String(text.characters), "Chỉ là chữ thường, không có liên kết.")
    }

    func testEmptyStringGivesEmptyText() {
        XCTAssertEqual(DescriptionText.attributed(""), AttributedString())
        XCTAssertTrue(DescriptionText.attributed("").characters.isEmpty)
    }
}

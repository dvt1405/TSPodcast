import XCTest
@testable import TSPodcast

/// M6-AC18: none of our own hub texts (every `news.*` key, vi and en, plural forms included)
/// says Radio, Đài, Phát thanh, Trực tiếp, Live or Kênh: the hub is on-demand podcasts, not a
/// station. Feed and episode titles are the publishers' words and are not checked here.
/// `iosApp/scripts/check-news-strings.sh` runs the same check on the source catalog.
final class NewsHubStringsTests: XCTestCase {
    static let forbidden = try! NSRegularExpression(
        pattern: #"(?i)\b(radio|đài|phát thanh|trực tiếp|live|kênh)\b"#,
        options: [.useUnicodeWordBoundaries]
    )

    /// Every `news.*` value of the compiled tables of `language` (`.strings` and the plural
    /// variants of `.stringsdict`).
    private func newsValues(_ language: String) throws -> [String: [String]] {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), "\(language).lproj missing")
        var values: [String: [String]] = [:]
        if let strings = NSDictionary(contentsOfFile: "\(path)/Localizable.strings") as? [String: Any] {
            for case let (key, value as String) in strings where key.hasPrefix("news.") {
                values[key, default: []].append(value)
            }
        }
        if let plurals = NSDictionary(contentsOfFile: "\(path)/Localizable.stringsdict") as? [String: Any] {
            for (key, entry) in plurals where key.hasPrefix("news.") {
                values[key, default: []].append(contentsOf: Self.leaves(entry))
            }
        }
        return values
    }

    private static func leaves(_ value: Any) -> [String] {
        switch value {
        case let text as String: [text]
        case let dictionary as [String: Any]: dictionary.values.flatMap(leaves)
        case let array as [Any]: array.flatMap(leaves)
        default: []
        }
    }

    func testNoStationWordsInTheHubTexts() throws {
        for language in ["vi", "en"] {
            let values = try newsValues(language)
            XCTAssertGreaterThanOrEqual(values.count, 10, "\(language): the news.* keys are in the table")
            for (key, texts) in values {
                for text in texts {
                    let range = NSRange(text.startIndex..., in: text)
                    XCTAssertNil(Self.forbidden.firstMatch(in: text, range: range), "\(language) \(key): '\(text)'")
                }
            }
        }
    }

    func testTheCheckCatchesEachWord() {
        for text in ["Radio VOV", "Đài Hà Nội", "phát thanh", "Trực tiếp", "LIVE now", "Kênh 1"] {
            XCTAssertNotNil(Self.forbidden.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)), text)
        }
        for text in ["Thời sự & Bản tin", "Delivered", "kênhx", "olive"] {
            XCTAssertNil(Self.forbidden.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)), text)
        }
    }
}

import Shared
import XCTest
@testable import TSPodcast

/// M2-AC6: mapping of shared `Episode` to the row (Android `EpisodeWidget`) and the
/// "date · duration" line.
final class EpisodeRowTests: XCTestCase {
    private let vi = Locale(identifier: "vi_VN")
    private let en = Locale(identifier: "en_US")
    private let utc = TimeZone(identifier: "UTC")!
    /// 2025-10-30 00:00:00 UTC.
    private let published: Int64 = 1_761_782_400

    private func episode(
        description: String? = nil,
        image: String = "",
        feedImage: String = "",
        link: String = "",
        datePublished: Int64 = 0,
        duration: Int64 = 0
    ) -> Episode {
        Episode(
            chaptersUrl: nil, dateCrawled: 0, datePublished: datePublished, datePublishedPretty: "",
            description: description, duration: duration, enclosureLength: 0, enclosureType: "audio/mpeg",
            enclosureUrl: "https://media.example.com/e.mp3", episode: nil, episodeType: nil, explicit: 0,
            feedDead: 0, feedDuplicateOf: nil, feedId: 42, feedImage: feedImage, feedItunesId: nil,
            feedLanguage: nil, feedUrl: "", guid: "", id: 7, image: image, link: link, persons: nil,
            podcastGuid: "", season: 0, socialInteract: nil, soundbite: nil, soundbites: nil, title: "Tập 7",
            transcriptUrl: nil, transcripts: nil, value: nil
        )
    }

    func testBlankImageFallsBackToFeedImage() {
        let row = EpisodeRow(episode(image: "  ", feedImage: "https://a/f.jpg"))
        XCTAssertEqual(row.imageURL, "https://a/f.jpg")
    }

    func testEpisodeImageWins() {
        let row = EpisodeRow(episode(image: "https://a/e.jpg", feedImage: "https://a/f.jpg"))
        XCTAssertEqual(row.imageURL, "https://a/e.jpg")
    }

    func testNoImageAtAllGivesNil() {
        XCTAssertNil(EpisodeRow(episode(image: "", feedImage: "")).imageURL)
    }

    func testLinkIsTheReferer() {
        XCTAssertNil(EpisodeRow(episode(link: "")).referer)
        XCTAssertNil(EpisodeRow(episode(link: "   ")).referer)
        XCTAssertEqual(EpisodeRow(episode(link: "https://site/ep")).referer, "https://site/ep")
    }

    func testHtmlDescriptionBecomesPlainText() {
        XCTAssertEqual(EpisodeRow(episode(description: "<p>A &amp; B</p>")).summary, "A & B")
        XCTAssertEqual(EpisodeRow(episode(description: nil)).summary, "")
    }

    func testOtherFieldsAreCopied() {
        let row = EpisodeRow(episode(datePublished: published, duration: 2700))
        XCTAssertEqual(row.id, 7)
        XCTAssertEqual(row.title, "Tập 7")
        XCTAssertEqual(row.publishedAt, Date(timeIntervalSince1970: 1_761_782_400))
        XCTAssertEqual(row.durationSeconds, 2700)
        XCTAssertEqual(row.enclosureURL, "https://media.example.com/e.mp3")
        XCTAssertEqual(row.enclosureType, "audio/mpeg")
        XCTAssertNil(EpisodeRow(episode(datePublished: 0)).publishedAt)
    }

    // MARK: - Meta line

    private func meta(datePublished: Int64 = 0, duration: Int64 = 0, _ locale: Locale) -> String {
        EpisodeRow(episode(datePublished: datePublished, duration: duration)).meta(locale: locale, timeZone: utc)
    }

    func testNoDateAndNoDurationGivesEmptyMeta() {
        XCTAssertEqual(meta(vi), "")
        XCTAssertEqual(meta(duration: -5, vi), "")
    }

    func testDateOnlyHasNoSeparator() {
        XCTAssertEqual(meta(datePublished: published, vi), "30 thg 10, 2025")
        XCTAssertEqual(meta(datePublished: published, en), "Oct 30, 2025")
    }

    func testDurationOnlyHasNoSeparator() {
        XCTAssertEqual(meta(duration: 2700, vi), "45 phút")
        XCTAssertEqual(meta(duration: 2700, en), "45 min")
    }

    func testUnderAMinuteCountsAsOneMinute() {
        XCTAssertEqual(meta(duration: 45, vi), "1 phút")
        XCTAssertEqual(meta(duration: 45, en), "1 min")
        XCTAssertEqual(meta(duration: 1, en), "1 min")
    }

    func testHoursAndMinutes() {
        XCTAssertEqual(meta(duration: 3900, vi), "1 giờ, 5 phút")
        XCTAssertEqual(meta(duration: 3900, en), "1 hr, 5 min")
        XCTAssertEqual(meta(duration: 3600, en), "1 hr", "no zero minutes")
    }

    func testDateAndDuration() {
        XCTAssertEqual(meta(datePublished: published, duration: 2700, vi), "30 thg 10, 2025 · 45 phút")
        XCTAssertEqual(meta(datePublished: published, duration: 3900, en), "Oct 30, 2025 · 1 hr, 5 min")
    }
}

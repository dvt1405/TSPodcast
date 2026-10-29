import XCTest

/// M2-AC5..AC8: Podcast detail against the real PodcastIndex API (Android
/// `PodcastDetailScreen`, `EpisodeWidget`): opening from Home, content, loading, empty and
/// explicit states, and the playlist handed to the player seam.
///
/// Tests that need episodes use `openDetailWithEpisodes`: a trending podcast can be an explicit
/// feed whose episodes D6 hides (item 0 was one on 30/10/2026).
final class PodcastDetailUITests: TSPodcastUITestCase {
    override func tearDown() {
        MainActor.assumeIsolated {
            XCUIDevice.shared.appearance = .light
        }
        super.tearDown()
    }

    @MainActor
    func testHomeTrendingOpensDetailWithEpisodes() {
        let app = launchMain()
        // Item 0, whatever it holds: title, cover, and a finished load (no endless shimmer).
        let title = openDetail(app)
        XCTAssertEqual(element(app, "detail.title").label, title)
        XCTAssertTrue(element(app, "detail.cover").exists, "cover shown")
        let outcome = waitForDetailContent(app)
        XCTAssertNotNil(outcome, "Detail still loading")
        XCTAssertNotEqual(outcome, "error")

        if outcome != "episode" {
            backToHome(app)
            let opened = openDetailWithEpisodes(app, start: 1)
            XCTAssertNotNil(opened, "no trending podcast with episodes")
            XCTAssertEqual(element(app, "detail.title").label, opened?.title)
            XCTAssertTrue(element(app, "detail.cover").exists, "cover shown")
        }
        XCTAssertTrue(element(app, "detail.episode.0").exists)
        XCTAssertFalse(element(app, "detail.episodes.loading").exists, "no shimmer left")
        XCTAssertFalse(element(app, "detail.episode.0.title").label.isEmpty)
        attachScreenshot("M2-detail")
    }

    @MainActor
    func testForYouOpensDetail() {
        let app = launchMain()
        let item = app.buttons["home.section.forYou.item.0"]
        XCTAssertEqual(waitForSectionContent(app, "trending"), "item")
        XCTAssertTrue(scrollHome(app, to: app.staticTexts["home.section.forYou"]), "forYou header not reached")
        XCTAssertEqual(waitForSectionContent(app, "forYou"), "item")
        XCTAssertTrue(scrollHome(app, to: item), "forYou item 0 not reached")

        let title = openDetail(app, from: "home.section.forYou.item.0")

        XCTAssertEqual(element(app, "detail.title").label, title)
        XCTAssertNotNil(waitForDetailContent(app), "Detail still loading")
        attachScreenshot("M2-detail-forYou")
    }

    @MainActor
    func testDetailShowsDescriptionAndEpisodeRows() {
        let app = launchMain()
        XCTAssertNotNil(openDetailWithEpisodes(app), "no trending podcast with episodes")

        let description = element(app, "detail.description")
        if description.exists {
            XCTAssertFalse(description.label.contains("<"), "HTML tags in the description")
            XCTAssertFalse(description.label.contains("&amp;"), "HTML entities in the description")
        }
        XCTAssertTrue(element(app, "detail.episode.0.title").exists)
        XCTAssertTrue(element(app, "detail.episode.0.meta").exists, "date · duration line")
        let count = detailEpisodeCount(app)
        XCTAssertNotNil(count, "detail.episodes value")
        XCTAssertGreaterThanOrEqual(count ?? 0, 1)
    }

    @MainActor
    func testDetailScreenshots() {
        XCUIDevice.shared.appearance = .light
        let app = launchMain()
        XCTAssertNotNil(openDetailWithEpisodes(app), "no trending podcast with episodes")
        // Let the covers fade in.
        _ = poll(timeout: 3) { false }
        attachScreenshot("M2-detail-light")

        XCUIDevice.shared.appearance = .dark
        _ = poll(timeout: 2) { false }
        XCTAssertTrue(element(app, "detail.root").exists)
        attachScreenshot("M2-detail-dark")
        XCUIDevice.shared.appearance = .light
    }

    @MainActor
    func testLoadingPlaceholdersShown() {
        let app = launchMain([.slowNetwork])
        openDetail(app)

        XCTAssertTrue(element(app, "detail.episodes.loading").waitForExistence(timeout: 2), "shimmer while loading")
        attachScreenshot("M2-detail-loading")
        let outcome = waitForDetailContent(app)
        XCTAssertNotNil(outcome, "still loading after \(Int(Self.networkTimeout)) s")
        XCTAssertFalse(element(app, "detail.episodes.loading").exists)
        if outcome != "episode" {
            backToHome(app)
            XCTAssertNotNil(openDetailWithEpisodes(app, start: 1), "no trending podcast with episodes")
        }
        XCTAssertTrue(element(app, "detail.episode.0").exists)
    }

    @MainActor
    func testEmptyAndExplicitStates() {
        var app = launchMain([.detailEmpty])
        openDetail(app)
        let empty = element(app, "detail.episodes.empty")
        XCTAssertTrue(empty.waitForExistence(timeout: 10), "empty state")
        XCTAssertEqual(empty.label, "Chưa có tập nào")
        _ = poll(timeout: 2) { false }
        XCTAssertFalse(element(app, "detail.episodes.loading").exists, "no endless shimmer")
        attachScreenshot("M2-detail-empty")
        app.terminate()

        app = launchMain([.detailExplicit])
        openDetail(app)
        let notice = element(app, "detail.explicitHidden")
        XCTAssertTrue(notice.waitForExistence(timeout: 10), "explicit notice")
        XCTAssertEqual(notice.label, "Podcast này được đánh dấu có nội dung người lớn (explicit) nên các tập đã bị ẩn.")
        XCTAssertFalse(element(app, "detail.episode.0").exists)
        XCTAssertFalse(element(app, "detail.episodes.loading").exists)
        attachScreenshot("M2-detail-explicit")
    }

    @MainActor
    func testEpisodeTapHandsPlaylistToPlaybackSeam() {
        let app = launchMain([.playbackProbe])
        XCTAssertNotNil(openDetailWithEpisodes(app), "no trending podcast with episodes")
        let count = detailEpisodeCount(app)
        XCTAssertNotNil(count, "detail.episodes value")
        guard let count else { return }
        let index = count >= 2 ? 1 : 0

        let row = element(app, "detail.episode.\(index)")
        XCTAssertTrue(revealInDetail(app, row), "episode \(index) not reachable")
        row.tap()

        let probe = element(app, "playback.probe")
        XCTAssertTrue(
            poll(timeout: 5) { probe.exists && probe.label.contains("index=\(index);") },
            "probe: \(probe.exists ? probe.label : "missing")"
        )
        XCTAssertTrue(probe.label.contains("count=\(count)"), probe.label)
        XCTAssertFalse(probe.label.contains("feedId=;"), probe.label)
        XCTAssertTrue(element(app, "detail.root").exists, "the tap stays on Detail")
        attachScreenshot("M2-detail-playback-probe")
    }
}

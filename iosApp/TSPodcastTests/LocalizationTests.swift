import XCTest
@testable import TSPodcast

/// M1-AC8: the String Catalog compiles into vi and en tables with the §4.5 texts; M2 §4.8
/// adds the Podcast detail and Search texts.
final class LocalizationTests: XCTestCase {
    private func bundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), "\(language).lproj missing")
        return try XCTUnwrap(Bundle(path: path))
    }

    private func text(_ key: String, _ language: String) throws -> String {
        try bundle(language).localizedString(forKey: key, value: "<missing>", table: "Localizable")
    }

    func testVietnameseIsTheDevelopmentLanguage() {
        XCTAssertEqual(Bundle.main.developmentLocalization, "vi")
        XCTAssertTrue(Bundle.main.localizations.contains("vi"))
        XCTAssertTrue(Bundle.main.localizations.contains("en"))
    }

    func testVietnameseTexts() throws {
        let expected = [
            "tab.favourite": "Yêu thích",
            "tab.home": "Khám phá",
            "tab.search": "Tìm kiếm",
            "home.section.trending": "Podcasts thịnh hành",
            "home.section.music": "Kho nhạc",
            "home.section.music.subtitle": "Feed nhạc theo chuẩn podcast:medium",
            "home.section.live": "Live Podcasts",
            "home.section.recent": "Podcasts xu hướng gần đây",
            "home.section.forYou": "Có thể bạn yêu thích",
            "home.attribution": "Dữ liệu podcast cung cấp bởi Podcast Index",
            "section.error": "Không tải được nội dung",
            "section.empty": "Chưa có nội dung",
            "action.retry": "Tải lại",
            "onboarding.title.1": "Khám Phá Podcast Yêu Thích Của Bạn",
            "onboarding.title.4": "Sắp Xếp Và Cập Nhật Liên Tục",
            "onboarding.continue": "Tiếp tục",
            "category.title": "Danh mục yêu thích của bạn",
            "category.subtitle": "(Ít nhất 3 danh mục)",
            "category.save": "Lưu và tiếp tục",
            "favourite.empty": "Không có chương trình yêu thích.",
            "favourite.discoverMore": "Khám phá thêm",
            "detail.error.title": "Rất tiếc!",
            "detail.error.message": "Không tải được danh sách tập. Vui lòng thử lại.",
            "action.cancel": "Huỷ",
            "detail.episodes.error": "Không tải được danh sách tập",
            "detail.episodes.empty": "Chưa có tập nào",
            "detail.explicitHidden": "Podcast này được đánh dấu có nội dung người lớn (explicit) nên các tập đã bị ẩn.",
            "search.prompt": "Tìm podcast, nghệ sĩ",
            "search.mode.all": "Tất cả",
            "search.mode.music": "Nhạc",
            "search.suggestion %@": "Thử tìm kiếm: %@",
            "search.suggestion.keyword": "Podcast hay",
            "search.categories": "Danh mục",
            "search.results.all %@": "Kết quả hàng đầu cho: %@",
            "search.results.music %@": "Nhạc cho: %@",
            "search.empty %@": "Không có kết quả cho: %@",
            "search.empty.musicHint": "Kho nhạc theo chuẩn podcast:medium còn ít, thử từ khoá tiếng Anh",
        ]
        for (key, value) in expected {
            XCTAssertEqual(try text(key, "vi"), value, key)
        }
    }

    func testEnglishTexts() throws {
        let expected = [
            "tab.favourite": "Favorites",
            "tab.home": "Discover",
            "tab.search": "Search",
            "home.section.trending": "Trending podcasts",
            "home.section.music": "Music library",
            "home.section.music.subtitle": "Music feeds using podcast:medium",
            "home.section.live": "Live podcasts",
            "home.section.recent": "Recently trending podcasts",
            "home.section.forYou": "You might like",
            "home.attribution": "Podcast data provided by Podcast Index",
            "section.error": "Couldn't load content",
            "section.empty": "Nothing here yet",
            "action.retry": "Retry",
            "onboarding.continue": "Continue",
            "category.title": "Your favourite categories",
            "category.subtitle": "(Choose at least 3)",
            "category.save": "Save and continue",
            "favourite.empty": "No favourite shows yet.",
            "favourite.discoverMore": "Discover more",
            "detail.error.title": "Oops!",
            "detail.error.message": "Couldn't load the episodes. Please try again.",
            "action.cancel": "Cancel",
            "detail.episodes.error": "Couldn't load episodes",
            "detail.episodes.empty": "No episodes yet",
            "detail.explicitHidden": "This podcast is marked explicit, so its episodes are hidden.",
            "search.prompt": "Search podcasts, artists",
            "search.mode.all": "All",
            "search.mode.music": "Music",
            "search.suggestion %@": "Try searching: %@",
            "search.suggestion.keyword": "great podcasts",
            "search.categories": "Categories",
            "search.results.all %@": "Top results for: %@",
            "search.results.music %@": "Music for: %@",
            "search.empty %@": "No results for: %@",
            "search.empty.musicHint": "The podcast:medium music catalogue is small, try English keywords",
        ]
        for (key, value) in expected {
            XCTAssertEqual(try text(key, "en"), value, key)
        }
    }

    func testOnboardingHighlightsAppearInTheirTitles() throws {
        let highlights = [
            1: ["onboarding.title.1.highlight.1", "onboarding.title.1.highlight.2"],
            2: ["onboarding.title.2.highlight.1"],
            3: ["onboarding.title.3.highlight.1"],
            4: ["onboarding.title.4.highlight.1"],
        ]
        for language in ["vi", "en"] {
            for (page, keys) in highlights {
                let title = try text("onboarding.title.\(page)", language)
                for key in keys {
                    let highlight = try text(key, language)
                    XCTAssertNotEqual(highlight, "<missing>", "\(language) \(key)")
                    XCTAssertTrue(title.contains(highlight), "\(language): '\(highlight)' not in '\(title)'")
                }
            }
        }
    }
}

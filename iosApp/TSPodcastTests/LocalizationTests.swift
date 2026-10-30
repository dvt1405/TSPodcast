import XCTest
@testable import TSPodcast

/// M1-AC8: the String Catalog compiles into vi and en tables with the §4.5 texts; M2 §4.8
/// adds the Podcast detail and Search texts; M4-AC23 every M4 key (§4.7) in both languages;
/// M5-AC11 the Up next peek strings; M6-AC23 the Player basics.
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
            "favourite.unavailable.title": "Không mở được mục này",
            "favourite.unavailable.message": "Dữ liệu của mục này không còn trên máy.",
            "favourite.unavailable.remove": "Bỏ yêu thích",
            "action.close": "Đóng",
            "detail.offlineBanner": "Đang hiện danh sách đã lưu (ngoại tuyến)",
            "notifications.prompt.title": "Nhận thông báo khi có podcast mới?",
            "notifications.prompt.enable": "Bật thông báo",
            "notifications.prompt.later": "Để sau",
            "notifications.denied.message": "Thông báo đang bị tắt trong Cài đặt của iPhone.",
            "notifications.denied.openSettings": "Mở Cài đặt",
            "settings.title": "Cài đặt",
            "settings.notifications": "Thông báo podcast mới",
            "settings.explicit": "Hiện nội dung người lớn (explicit)",
            "settings.privacy": "Chính sách bảo mật",
            "settings.version": "Phiên bản",
            "notifications.prompt.message": "Chỉ gửi khi có podcast mới. Bạn có thể tắt bất cứ lúc nào trong Cài đặt.",
            "notifications.denied.hint": "Mở Cài đặt để bật lại thông báo cho TSPodcast.",
            "deeplink.error.title": "Không mở được liên kết",
            "deeplink.error.message": "Nội dung này không còn hoặc không xem được trong ứng dụng.",
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
            "favourite.unavailable.title": "Couldn't open this item",
            "favourite.unavailable.message": "This item is no longer stored on this device.",
            "favourite.unavailable.remove": "Remove from favorites",
            "action.close": "Close",
            "detail.offlineBanner": "Showing saved episodes (offline)",
            "notifications.prompt.title": "Get notified about new podcasts?",
            "notifications.prompt.enable": "Turn on",
            "notifications.prompt.later": "Not now",
            "notifications.denied.message": "Notifications are turned off in iPhone Settings.",
            "notifications.denied.openSettings": "Open Settings",
            "settings.title": "Settings",
            "settings.notifications": "New podcast notifications",
            "settings.explicit": "Show explicit content",
            "settings.privacy": "Privacy Policy",
            "settings.version": "Version",
            "notifications.prompt.message": "Only when new podcasts arrive. You can turn this off anytime in Settings.",
            "notifications.denied.hint": "Open Settings to turn notifications back on for TSPodcast.",
            "deeplink.error.title": "Couldn't open the link",
            "deeplink.error.message": "This content is no longer available or can't be shown in the app.",
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

    /// Every string M4 added (plan §4.7, plus the alert messages that carry identifiers).
    static let m4Keys = [
        "favourite.unavailable.title", "favourite.unavailable.message", "favourite.unavailable.remove", "action.close",
        "detail.offlineBanner",
        "notifications.prompt.title", "notifications.prompt.message", "notifications.prompt.enable", "notifications.prompt.later",
        "notifications.denied.message", "notifications.denied.hint", "notifications.denied.openSettings",
        "settings.title", "settings.notifications", "settings.explicit", "settings.privacy", "settings.version",
        "deeplink.error.title", "deeplink.error.message",
    ]

    func testEveryM4KeyHasVietnameseAndEnglish() throws {
        for key in Self.m4Keys {
            let vietnamese = try text(key, "vi")
            let english = try text(key, "en")
            XCTAssertNotEqual(vietnamese, "<missing>", "vi \(key)")
            XCTAssertNotEqual(english, "<missing>", "en \(key)")
            XCTAssertNotEqual(english, vietnamese, "en of \(key) is translated")
        }
    }

    /// M5-AC11: the Up next peek strings (plan §4), exactly as in the table.
    func testUpNextPeekStrings() throws {
        let expected: [String: (vi: String, en: String)] = [
            "player.upNext.peekValue": ("Đang phát: %@", "Now playing: %@"),
            "player.upNext.expandHint": ("Mở danh sách tiếp theo", "Opens the up next list"),
            "player.upNext.collapseHint": ("Thu gọn danh sách tiếp theo", "Collapses the up next list"),
        ]
        for (key, value) in expected {
            XCTAssertEqual(try text(key, "vi"), value.vi, "vi \(key)")
            XCTAssertEqual(try text(key, "en"), value.en, "en \(key)")
        }
        let vietnamese = try text("player.upNext.peekValue", "vi")
        XCTAssertEqual(String(format: vietnamese, "Tập 1"), "Đang phát: Tập 1")
    }

    /// M6-AC23: the Player basics (skip, speed, sleep timer), exactly as in the strings table.
    func testPlayerBasicsStrings() throws {
        let expected: [String: (vi: String, en: String)] = [
            "player.skipBack": ("Lùi 15 giây", "Back 15 seconds"),
            "player.skipForward": ("Tới 30 giây", "Forward 30 seconds"),
            "player.speed": ("Tốc độ phát", "Playback speed"),
            "player.sleepTimer": ("Hẹn giờ tắt", "Sleep timer"),
            "player.sleepTimer.off": ("Tắt", "Off"),
            "player.sleepTimer.remaining": ("Còn %@", "%@ left"),
            "speed.minus": ("Giảm tốc độ", "Slower"),
            "speed.plus": ("Tăng tốc độ", "Faster"),
            "speed.applyAll": ("Áp dụng cho mọi show", "Apply to all shows"),
            "speed.applyAll.footnote": ("Khi tắt, mỗi show nhớ tốc độ riêng.", "When off, each show remembers its own speed."),
            "sleepTimer.minutes %lld": ("%lld phút", "%lld minutes"),
            "sleepTimer.option.endOfEpisode": ("Hết tập", "End of episode"),
            "sleepTimer.option.off": ("Tắt hẹn giờ", "Turn off timer"),
            "sleepTimer.status.endOfEpisode": ("Dừng khi hết tập này", "Stops at the end of this episode"),
        ]
        for (key, value) in expected {
            XCTAssertEqual(try text(key, "vi"), value.vi, "vi \(key)")
            XCTAssertEqual(try text(key, "en"), value.en, "en \(key)")
        }
        XCTAssertEqual(String(format: try text("sleepTimer.minutes %lld", "vi"), 30), "30 phút")
        XCTAssertEqual(String(format: try text("player.sleepTimer.remaining", "vi"), "28:14"), "Còn 28:14")
    }

    /// M6-AC23: hide/report, hidden shows and the consent placeholders.
    func testShowSafetyAndPrivacyStrings() throws {
        let expected: [String: (vi: String, en: String)] = [
            "show.hide": ("Ẩn show này", "Hide this show"),
            "report.action": ("Báo cáo nội dung", "Report content"),
            "settings.hiddenShows": ("Show đã ẩn", "Hidden shows"),
            "hiddenShows.unhide": ("Bỏ ẩn", "Unhide"),
            "consent.acceptAll": ("Đồng ý tất cả", "Accept all"),
            "consent.necessaryOnly": ("Chỉ cần thiết", "Necessary only"),
            "consent.saveChoice": ("Lưu lựa chọn", "Save choices"),
            "consent.necessary.title": ("Cần thiết", "Necessary"),
            "consent.analytics.title": ("Phân tích sử dụng", "Usage analytics"),
            "consent.crash.title": ("Báo cáo sự cố", "Crash reports"),
            "settings.privacySection": ("Quyền riêng tư", "Privacy"),
        ]
        for (key, value) in expected {
            XCTAssertEqual(try text(key, "vi"), value.vi, "vi \(key)")
            XCTAssertEqual(try text(key, "en"), value.en, "en \(key)")
        }
        for key in ["consent.title", "consent.intro", "consent.necessary.detail", "consent.analytics.detail", "consent.crash.detail", "consent.footnote",
                    "report.fallback.title", "report.fallback.message %@", "report.fallback.copy", "detail.hide.confirmTitle",
                    "detail.hide.confirm", "detail.hide.confirmMessage", "hiddenShows.empty", "hiddenShows.footer"] {
            for language in ["vi", "en"] {
                XCTAssertNotEqual(try text(key, language), "<missing>", "\(language) \(key)")
            }
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

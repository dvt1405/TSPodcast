import Foundation

/// Plain text with tappable links, the iOS counterpart of Android's `HtmlCompat.fromHtml` +
/// `linkify` in the Detail header. URLs (and e-mail addresses) found by `NSDataDetector` get a
/// `.link` and an underline; `Text(AttributedString)` opens them in Safari.
enum DescriptionText {
    private static let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)

    static func attributed(_ plain: String) -> AttributedString {
        guard !plain.isEmpty else { return AttributedString() }
        guard let detector else { return AttributedString(plain) }

        var result = AttributedString()
        var cursor = plain.startIndex
        let matches = detector.matches(in: plain, range: NSRange(plain.startIndex..., in: plain))
        for match in matches {
            guard let url = match.url, let range = Range(match.range, in: plain), range.lowerBound >= cursor else {
                continue
            }
            result += AttributedString(String(plain[cursor..<range.lowerBound]))
            var link = AttributedString(String(plain[range]))
            link.link = url
            link.underlineStyle = .single
            result += link
            cursor = range.upperBound
        }
        result += AttributedString(String(plain[cursor...]))
        return result
    }
}

import Foundation
import ImageIO
import OSLog
@preconcurrency import Shared
import UIKit

/// Loads cover images (features-spec §2 "Ảnh", setup-and-build §3.3), without a third-party
/// library:
///
/// - every request carries `User-Agent: SharedConstants.USER_AGENT_WEB_VALUE`, the browser
///   value Android's Coil sends, because some image hosts refuse unknown clients;
/// - `http://` is upgraded to `https://` before sending, since ATS stays strict (no
///   `NSAllowsArbitraryLoads`). A failure gives `nil` (the view shows a placeholder) and is
///   never retried over `http`;
/// - nil, blank or non-http(s) URLs give `nil` without a request;
/// - responses go through a dedicated `URLCache` (memory 50 MB, disk 200 MB in
///   `Caches/Artwork`), decoded images through an `NSCache`, and concurrent loads of one URL
///   share a single request.
actor ArtworkLoader {
    /// Counters for measuring the share of `http://` covers and the failure rate (M1-AC9).
    struct Stats: Equatable, Sendable {
        /// Loads that reached the network or the URL cache (memory hits excluded).
        var requests = 0
        /// Of `requests`, how many were `http://` URLs upgraded to `https://`.
        var upgradedFromHTTP = 0
        /// Of `requests`, how many ended without an image.
        var failures = 0
        /// Of `failures`, how many were upgraded `http://` URLs.
        var failuresFromHTTP = 0
    }

    static let shared = ArtworkLoader()

    /// Largest side, in pixels, of a decoded image: covers are shown at most ~250 pt wide.
    static let defaultMaxPixelSize: CGFloat = 750

    private(set) var stats = Stats()

    private let session: URLSession
    private let urlCache: URLCache
    private let maxPixelSize: CGFloat
    private let userAgent: String
    private let userAgentHeader: String
    private var inFlight: [URL: Task<UIImage?, Never>] = [:]

    /// `NSCache` is thread-safe, which lets views read it synchronously (`cachedImage(for:)`) to
    /// avoid a placeholder flash for images already decoded.
    private nonisolated(unsafe) let images = NSCache<NSURL, UIImage>()

    /// - Parameters:
    ///   - configuration: session configuration; tests pass one with a stub `URLProtocol`.
    ///   - urlCache: response cache; defaults to 50 MB memory / 200 MB disk in `Caches/Artwork`.
    init(
        configuration: URLSessionConfiguration = .default,
        urlCache: URLCache? = nil,
        maxPixelSize: CGFloat = ArtworkLoader.defaultMaxPixelSize
    ) {
        let cache = urlCache ?? Self.makeURLCache()
        let configuration = configuration
        configuration.urlCache = cache
        configuration.requestCachePolicy = .useProtocolCachePolicy
        configuration.timeoutIntervalForRequest = 20
        configuration.httpMaximumConnectionsPerHost = 6
        self.session = URLSession(configuration: configuration)
        self.urlCache = cache
        self.maxPixelSize = maxPixelSize
        self.userAgent = SharedConstants.shared.USER_AGENT_WEB_VALUE
        self.userAgentHeader = SharedConstants.shared.USER_AGENT_KEY
        images.totalCostLimit = 64 * 1024 * 1024
    }

    /// The URL actually requested for `raw`: trimmed, `http` upgraded to `https`; nil when blank,
    /// unparsable, without a host, or not http(s).
    nonisolated static func requestURL(for raw: String?) -> URL? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        // Some feeds put spaces or other unescaped characters in image URLs.
        let escaped = trimmed.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed.union(["%", "#"]))
        guard var components = URLComponents(string: trimmed) ?? escaped.flatMap(URLComponents.init(string:)) else {
            return nil
        }
        switch components.scheme?.lowercased() {
        case "https":
            break
        case "http":
            components.scheme = "https"
        default:
            return nil
        }
        guard let host = components.host, !host.isEmpty else { return nil }
        return components.url
    }

    /// Decoded image already in memory, or nil. Never starts a request.
    nonisolated func cachedImage(for raw: String?) -> UIImage? {
        guard let url = Self.requestURL(for: raw) else { return nil }
        return images.object(forKey: url as NSURL)
    }

    /// Image for `raw`, or nil (placeholder) when the URL is unusable or the load fails.
    func image(for raw: String?) async -> UIImage? {
        guard let url = Self.requestURL(for: raw) else { return nil }
        if let cached = images.object(forKey: url as NSURL) {
            return cached
        }
        if let running = inFlight[url] {
            return await running.value
        }
        let wasHTTP = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("http:") ?? false
        let task = Task.detached(priority: .utility) { [session, userAgent, userAgentHeader, maxPixelSize] in
            await Self.download(
                url,
                session: session,
                userAgentHeader: userAgentHeader,
                userAgent: userAgent,
                maxPixelSize: maxPixelSize
            )
        }
        inFlight[url] = task
        let image = await task.value
        inFlight[url] = nil

        stats.requests += 1
        if wasHTTP { stats.upgradedFromHTTP += 1 }
        if let image {
            images.setObject(image, forKey: url as NSURL, cost: Self.cost(of: image))
        } else {
            stats.failures += 1
            if wasHTTP { stats.failuresFromHTTP += 1 }
            AppLog.artwork.error("artwork_failed host=\(url.host() ?? "-", privacy: .public) upgraded=\(wasHTTP, privacy: .public)")
        }
        return image
    }

    /// Clears both caches (XCUITest reset).
    func removeAll() {
        images.removeAllObjects()
        urlCache.removeAllCachedResponses()
    }

    func resetStats() {
        stats = Stats()
    }

    private static func download(
        _ url: URL,
        session: URLSession,
        userAgentHeader: String,
        userAgent: String,
        maxPixelSize: CGFloat
    ) async -> UIImage? {
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: userAgentHeader)
        do {
            let (data, response) = try await session.data(for: request)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                return nil
            }
            return decode(data, maxPixelSize: maxPixelSize)
        } catch {
            return nil
        }
    }

    /// Decodes and downsamples with ImageIO, so a 3000 px cover does not cost 36 MB of memory.
    nonisolated static func decode(_ data: Data, maxPixelSize: CGFloat) -> UIImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
        let options = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ] as CFDictionary
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options) else { return nil }
        return UIImage(cgImage: image)
    }

    private static func cost(of image: UIImage) -> Int {
        guard let cgImage = image.cgImage else { return 1 }
        return cgImage.bytesPerRow * cgImage.height
    }

    private static func makeURLCache() -> URLCache {
        URLCache(
            memoryCapacity: 50 * 1024 * 1024,
            diskCapacity: 200 * 1024 * 1024,
            directory: URL.cachesDirectory.appending(path: "Artwork", directoryHint: .isDirectory)
        )
    }
}

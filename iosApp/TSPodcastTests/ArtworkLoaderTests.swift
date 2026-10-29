import Shared
import UIKit
import XCTest
@testable import TSPodcast

/// M1-AC9: User-Agent, http → https, placeholder on failure, no request for empty URLs, cache.
final class ArtworkLoaderTests: XCTestCase {
    private static let png: Data = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).pngData { context in
        UIColor.systemBlue.setFill()
        context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
    }

    private func makeLoader() -> ArtworkLoader {
        ArtworkLoader(
            configuration: StubURLProtocol.configuration(),
            urlCache: URLCache(memoryCapacity: 0, diskCapacity: 0)
        )
    }

    private func respondWithImage() {
        let png = Self.png
        StubURLProtocol.reset { _ in .data(png) }
    }

    func testRequestCarriesBrowserUserAgentFromShared() async {
        respondWithImage()
        let image = await makeLoader().image(for: "https://img.example.com/ua.jpg")

        XCTAssertNotNil(image)
        let requests = StubURLProtocol.requests
        XCTAssertEqual(requests.count, 1)
        XCTAssertEqual(requests.first?.value(forHTTPHeaderField: "User-Agent"), SharedConstants.shared.USER_AGENT_WEB_VALUE)
    }

    func testHttpURLIsRequestedOverHttps() async {
        respondWithImage()
        let loader = makeLoader()
        let image = await loader.image(for: "http://img.example.com/a.jpg")

        XCTAssertNotNil(image)
        let urls = StubURLProtocol.requests.compactMap { $0.url?.absoluteString }
        XCTAssertEqual(urls, ["https://img.example.com/a.jpg"])
        XCTAssertFalse(urls.contains { $0.hasPrefix("http:") })
        let stats = await loader.stats
        XCTAssertEqual(stats.upgradedFromHTTP, 1)
        XCTAssertEqual(stats.failures, 0)
    }

    func testFailureGivesPlaceholderWithoutHttpRetry() async {
        StubURLProtocol.reset { _ in .failure(.secureConnectionFailed) }
        let loader = makeLoader()
        let image = await loader.image(for: "http://img.example.com/broken.jpg")

        XCTAssertNil(image)
        let urls = StubURLProtocol.requests.compactMap { $0.url?.absoluteString }
        XCTAssertEqual(urls, ["https://img.example.com/broken.jpg"])
        XCTAssertEqual(urls.filter { $0.hasPrefix("http:") }.count, 0)
        let stats = await loader.stats
        XCTAssertEqual(stats.failures, 1)
        XCTAssertEqual(stats.failuresFromHTTP, 1)
    }

    func testHttpErrorStatusAndUndecodableBodyGivePlaceholder() async {
        StubURLProtocol.reset { request in
            request.url?.path() == "/404.jpg" ? .data(Data("not found".utf8), statusCode: 404) : .data(Data("<html>".utf8))
        }
        let loader = makeLoader()

        let notFound = await loader.image(for: "https://img.example.com/404.jpg")
        let notAnImage = await loader.image(for: "https://img.example.com/page.jpg")

        XCTAssertNil(notFound)
        XCTAssertNil(notAnImage)
    }

    func testNilBlankAndNonHttpURLsSendNoRequest() async {
        respondWithImage()
        let loader = makeLoader()

        let results = [
            await loader.image(for: nil),
            await loader.image(for: ""),
            await loader.image(for: "  "),
            await loader.image(for: "ftp://img.example.com/a.jpg"),
            await loader.image(for: "/relative/a.jpg"),
        ]

        XCTAssertTrue(results.allSatisfy { $0 == nil })
        XCTAssertEqual(StubURLProtocol.requests.count, 0)
    }

    func testSecondLoadIsServedFromCache() async {
        respondWithImage()
        let loader = makeLoader()

        let first = await loader.image(for: "https://img.example.com/cached.jpg")
        let second = await loader.image(for: "https://img.example.com/cached.jpg")

        XCTAssertNotNil(first)
        XCTAssertTrue(first === second)
        XCTAssertEqual(StubURLProtocol.requests.count, 1)
        XCTAssertNotNil(loader.cachedImage(for: "https://img.example.com/cached.jpg"))
        // The http spelling of the same cover maps to the same cache entry.
        XCTAssertNotNil(loader.cachedImage(for: "http://img.example.com/cached.jpg"))
    }

    func testConcurrentLoadsOfOneURLShareOneRequest() async {
        respondWithImage()
        let loader = makeLoader()

        async let a = loader.image(for: "https://img.example.com/same.jpg")
        async let b = loader.image(for: "https://img.example.com/same.jpg")
        async let c = loader.image(for: "https://img.example.com/same.jpg")
        let images = await [a, b, c]

        XCTAssertTrue(images.allSatisfy { $0 != nil })
        XCTAssertEqual(StubURLProtocol.requests.count, 1)
    }

    func testRemoveAllForgetsDecodedImages() async {
        respondWithImage()
        let loader = makeLoader()
        _ = await loader.image(for: "https://img.example.com/reset.jpg")

        await loader.removeAll()
        _ = await loader.image(for: "https://img.example.com/reset.jpg")

        XCTAssertEqual(StubURLProtocol.requests.count, 2)
    }

    func testRequestURLNormalisation() {
        XCTAssertEqual(ArtworkLoader.requestURL(for: " HTTP://a.example.com/x.png ")?.absoluteString, "https://a.example.com/x.png")
        XCTAssertEqual(ArtworkLoader.requestURL(for: "https://a.example.com/x y.png")?.absoluteString, "https://a.example.com/x%20y.png")
        XCTAssertNil(ArtworkLoader.requestURL(for: "https://"))
        XCTAssertNil(ArtworkLoader.requestURL(for: "data:image/png;base64,AAAA"))
    }
}

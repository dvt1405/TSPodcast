import Foundation

/// `URLProtocol` stub for a test `URLSession` (`protocolClasses`): records every request and
/// answers with `StubURLProtocol.respond`. Tests using it must not run in parallel with each
/// other (XCTest runs the methods of a class one after another).
final class StubURLProtocol: URLProtocol, @unchecked Sendable {
    enum Response: Sendable {
        case data(Data, statusCode: Int = 200)
        case failure(URLError.Code)
        /// Never answers (a server that is too slow); the request ends only when cancelled.
        case hang
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var recorded: [URLRequest] = []
    nonisolated(unsafe) private static var responder: @Sendable (URLRequest) -> Response = { _ in .failure(.notConnectedToInternet) }

    /// Requests received since the last `reset`, in order.
    static var requests: [URLRequest] {
        lock.withLock { recorded }
    }

    static func reset(respond: @escaping @Sendable (URLRequest) -> Response) {
        lock.withLock {
            recorded = []
            responder = respond
        }
    }

    /// A configuration whose only protocol is this stub (no real network).
    static func configuration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return configuration
    }

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let respond = Self.lock.withLock {
            Self.recorded.append(request)
            return Self.responder
        }
        switch respond(request) {
        case let .data(data, statusCode):
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png", "Cache-Control": "max-age=3600"]
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(code):
            client?.urlProtocol(self, didFailWithError: URLError(code))
        case .hang:
            break
        }
    }

    override func stopLoading() {}
}

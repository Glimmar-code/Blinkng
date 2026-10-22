import XCTest
@testable import BLINKiOS

/// Isolated protocol fixtures: no production Supabase calls or real accounts.
final class BLINKAuthPhase2Tests: XCTestCase {
    private final class StubURLProtocol: URLProtocol {
        static var handler: ((URLRequest) throws -> (Int, Data))?

        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
            guard let handler = Self.handler, let url = request.url else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            do {
                let (status, body) = try handler(request)
                let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: [:])!
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: body)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let projectURL = "https://example.supabase.co"
    private let publicKey = "sb_publishable_test_only"

    private func api() -> BLINKAuthAPI {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        let session = URLSession(configuration: configuration)
        return BLINKAuthAPI(configuration: BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": projectURL,
            "BLINK_SUPABASE_ANON_KEY": publicKey
        ])!, urlSession: session)
    }

    private static func bodyData(_ request: URLRequest) -> Data? {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let size = stream.read(&buffer, maxLength: buffer.count)
            if size < 0 { return nil }
            if size == 0 { break }
            data.append(contentsOf: buffer.prefix(size))
        }
        return data
    }

    override func tearDown() {
        StubURLProtocol.handler = nil
        super.tearDown()
    }

    func testPasswordSignInUsesExistingSupabaseHostAndPublishableHeader() async throws {
        StubURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.host, "example.supabase.co")
            XCTAssertEqual(request.url?.path, "/auth/v1/token")
            XCTAssertEqual(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
                .queryItems?.first?.value, "password")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), self.publicKey)
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"),
                         "A publishable key must not be treated as a JWT")
            let data = try XCTUnwrap(Self.bodyData(request))
            let body = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: String])
            XCTAssertEqual(body["email"], "student@example.com")
            XCTAssertEqual(body["password"], "test-password")
            return (200, Data("""
                {"access_token":"jwt123","refresh_token":"refresh123",
                 "expires_in":3600,
                 "user":{"id":"user1","email":"student@example.com"}}
                """.utf8))
        }

        let (session, user) = try await api().signIn(
            email: "student@example.com", password: "test-password"
        )
        XCTAssertEqual(user.id, "user1")
        XCTAssertEqual(session.refreshToken, "refresh123")
        XCTAssertFalse(session.shouldRefresh)
    }

    func testRegistrationWithoutSessionRequiresEmailConfirmation() async throws {
        StubURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/auth/v1/signup")
            return (200, Data("""
                {"user":{"id":"pending","email":"student@example.com"}}
                """.utf8))
        }
        let (session, user) = try await api().signUp(
            email: "student@example.com", password: "test-password"
        )
        XCTAssertNil(session)
        XCTAssertEqual(user?.id, "pending")
    }

    func testRefreshUsesRotatedTokenAndAuthenticatedUserUsesJWT() async throws {
        StubURLProtocol.handler = { request in
            if request.url?.path == "/auth/v1/token" {
                XCTAssertEqual(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
                    .queryItems?.first?.value, "refresh_token")
                let body = try XCTUnwrap(JSONSerialization.jsonObject(
                    with: XCTUnwrap(Self.bodyData(request))) as? [String: String])
                XCTAssertEqual(body["refresh_token"], "oldRefresh")
                return (200, Data("""
                    {"access_token":"newJWT","refresh_token":"rotatedRefresh",
                     "expires_in":3600}
                    """.utf8))
            }
            XCTAssertEqual(request.url?.path, "/auth/v1/user")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer newJWT")
            return (200, Data("""
                {"id":"user1","email":"student@example.com"}
                """.utf8))
        }

        let transport = api()
        let rotated = try await transport.refresh(refreshToken: "oldRefresh")
        XCTAssertEqual(rotated.refreshToken, "rotatedRefresh")
        let user = try await transport.currentUser(accessToken: rotated.accessToken)
        XCTAssertEqual(user.id, "user1")
    }

    func testExpiredRefreshIsRejectedWithoutPretendingToBeSignedIn() async throws {
        StubURLProtocol.handler = { _ in
            (401, Data(#"{"message":"Invalid Refresh Token"}"#.utf8))
        }
        do {
            _ = try await api().refresh(refreshToken: "invalid")
            XCTFail("Expected invalid-session error")
        } catch let error as BLINKAuthError {
            XCTAssertTrue(error.invalidSession)
        }
    }

    func testSignOutUsesLocalDeviceScope() async throws {
        StubURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/auth/v1/logout")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            XCTAssertEqual(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
                .queryItems?.first?.value, "local")
            return (204, Data())
        }
        try await api().signOut(accessToken: "jwt")
    }
}

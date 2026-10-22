import XCTest
@testable import BLINKiOS

/// Authenticated fixture requests only: no real Supabase mutations or test
/// marketplace listings are published to production.
final class BLINKDiscoveryPhase4Tests: XCTestCase {
    private final class StubProtocol: URLProtocol {
        static var responder: ((URLRequest) throws -> (Int, Data))?

        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
            guard let responder = Self.responder, let url = request.url else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            do {
                let (status, data) = try responder(request)
                let response = HTTPURLResponse(
                    url: url, statusCode: status, httpVersion: nil, headerFields: [:]
                )!
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let receiver = "22222222-2222-4222-8222-222222222222"

    override func tearDown() {
        StubProtocol.responder = nil
        super.tearDown()
    }

    private func api() -> BLINKDiscoveryAPI {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        return BLINKDiscoveryAPI(
            configuration: BLINKBackendConfiguration(values: [
                "BLINK_SUPABASE_URL": "https://example.supabase.co",
                "BLINK_SUPABASE_ANON_KEY": "sb_publishable_fixture"
            ])!,
            urlSession: URLSession(configuration: configuration)
        )
    }

    private static func jsonBody(_ request: URLRequest) throws -> [String: Any] {
        let data: Data
        if let body = request.httpBody {
            data = body
        } else if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var output = Data()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let read = stream.read(&buffer, maxLength: buffer.count)
                if read < 0 { throw URLError(.cannotDecodeRawData) }
                if read == 0 { break }
                output.append(contentsOf: buffer.prefix(read))
            }
            data = output
        } else {
            throw URLError(.cannotDecodeRawData)
        }
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testMarketUsesRankedServerRPCWithSearchAndSignedInJWT() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_ranked_market_page")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "sb_publishable_fixture")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer account.jwt")
            let body = try Self.jsonBody(request)
            XCTAssertEqual(body["p_query"] as? String, "laptop")
            XCTAssertEqual(body["p_limit"] as? Int, 40)
            XCTAssertTrue(body["p_cursor_score"] is NSNull)
            return (200, Data("""
                [{"item":{"id":"11111111-1111-4111-8111-111111111111",
                "title":"Campus laptop","price":"25000.00","currency":"NGN",
                "image_urls":["https://example.com/item.png"],
                "seller_username":"vendor","location":"Akure"}}]
                """.utf8))
        }

        let records = try await api().marketplace(query: " laptop ", token: "account.jwt")
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records.first?.title, "Campus laptop")
        XCTAssertEqual(records.first?.sellerHandle, "vendor")
        XCTAssertNotNil(records.first?.coverImage)
        XCTAssertTrue(records.first?.displayPrice.contains("25,000") == true)
    }

    func testMarketAcceptsDirectRowsAndNoResults() async throws {
        StubProtocol.responder = { request in
            let body = try Self.jsonBody(request)
            XCTAssertTrue(body["p_query"] is NSNull)
            return (200, Data("""
                [{"id":"11111111-1111-4111-8111-111111111111",
                "title":"Notebook","price":400}]
                """.utf8))
        }
        let records = try await api().marketplace(query: "", token: "jwt")
        XCTAssertEqual(records.first?.title, "Notebook")
        XCTAssertEqual(records.first?.price?.amount, Decimal(400))
    }

    func testConnectUsesExistingRankedOpportunityRPCAndMode() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_ranked_connect_opportunities")
            let body = try Self.jsonBody(request)
            XCTAssertEqual(body["p_mode"] as? String, "reading_mate")
            XCTAssertEqual(body["p_limit"] as? Int, 40)
            return (200, Data("""
                [{"kind":"reading_mate","payload":{"title":"Evening study",
                "description":"Join our reading group",
                "profile":{"id":"\(self.receiver)","username":"campusfriend",
                "university":"FUTA"}}}]
                """.utf8))
        }

        let records = try await api().connect(mode: .readingMate, token: "jwt")
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].receiverID, receiver)
        XCTAssertEqual(records[0].displayTitle, "Evening study")
        XCTAssertTrue(records[0].detail.contains("FUTA"))
    }

    func testConnectionRequestIsSentOnlyWithValidReceiver() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/send_connection_request")
            let body = try Self.jsonBody(request)
            XCTAssertEqual(body["p_receiver_id"] as? String, self.receiver)
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            return (200, Data("{}".utf8))
        }
        try await api().requestConnection(receiverID: receiver, token: "jwt")
        do {
            try await api().requestConnection(receiverID: "../invalid", token: "jwt")
            XCTFail("Invalid receiver should be rejected without network request")
        } catch BLINKDiscoveryError.unavailable {
            // Expected.
        }
    }

    func testInvalidJWTNeverCreatesFakeListings() async throws {
        StubProtocol.responder = { _ in (401, Data("{}".utf8)) }
        do {
            _ = try await api().marketplace(query: "", token: "revoked")
            XCTFail("Authentication error must not return fake listings")
        } catch BLINKFeedError.unauthorized {
            // Expected.
        }
    }

    func testConnectModesMatchExistingWebBackend() {
        XCTAssertEqual(BLINKConnectMode.allCases.map(\.rawValue), [
            "all", "roommate", "mentor", "reading_mate", "housing"
        ])
    }

    func testRejectsUnsafeSellerContactInformation() throws {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let item = try decoder.decode(BLINKMarketplaceListing.self, from: Data("""
            {"id":"a","seller_username":"some@example.com","image_url":"http://example.com/pic.png"}
            """.utf8))
        XCTAssertNil(item.sellerHandle)
        XCTAssertNil(item.coverImage)
    }
}

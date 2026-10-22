import XCTest
@testable import BLINKiOS

/// All Phase 6 tests run against URLProtocol fixtures, NEVER production APIs.
final class BLINKPhase6NotificationCallsTests: XCTestCase {
    private final class StubProtocol: URLProtocol {
        static var responder: ((URLRequest) throws -> (Int, Data))?
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
            guard let handler = Self.responder, let url = request.url else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            do {
                let (status, bytes) = try handler(request)
                let response = HTTPURLResponse(url: url, statusCode: status,
                                               httpVersion: nil, headerFields: [:])!
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: bytes)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let callID = "11111111-1111-4111-8111-111111111111"
    private let conversationID = "22222222-2222-4222-8222-222222222222"
    private let callerID = "33333333-3333-4333-8333-333333333333"
    private let calleeID = "44444444-4444-4444-8444-444444444444"

    override func tearDown() {
        StubProtocol.responder = nil
        super.tearDown()
    }

    private func urlSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: config)
    }

    private func project() -> BLINKBackendConfiguration {
        BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_test"
        ])!
    }

    private func callJSON(status: String = "ringing") -> Data {
        Data("""
        {"id":"\(callID)","conversation_id":"\(conversationID)",
         "caller_id":"\(callerID)","callee_id":"\(calleeID)",
         "call_type":"video","status":"\(status)",
         "created_at":"2026-10-19T12:00:00Z"}
        """.utf8)
    }

    func testReadOnlyNotificationQueryIsScopedByUserJWT() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.httpMethod, "GET")
            XCTAssertEqual(request.url?.path, "/rest/v1/notifications")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "sb_publishable_test")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer valid.jwt")
            let params = URLComponents(url: try XCTUnwrap(request.url),
                                       resolvingAgainstBaseURL: false)?.queryItems ?? []
            XCTAssertEqual(params.first(where: { $0.name == "limit" })?.value, "50")
            XCTAssertTrue((params.first(where: { $0.name == "select" })?.value ?? "").contains("is_read"))
            return (200, Data("""
              [{"id":"55555555-5555-4555-8555-555555555555",
                "type":"comment","text":"New comment",
                "sub_text":"On your post","is_read":false}]
            """.utf8))
        }
        let api = BLINKNotificationAPI(configuration: project(), urlSession: urlSession())
        let rows = try await api.list(token: "valid.jwt")
        XCTAssertEqual(rows.first?.title, "New comment")
        XCTAssertEqual(rows.first?.detail, "On your post")
        XCTAssertEqual(rows.first?.isRead, false)
    }

    func testInvalidSessionFailsInsteadOfReturningFabricatedNotifications() async throws {
        StubProtocol.responder = { _ in (401, Data("{}".utf8)) }
        do {
            _ = try await BLINKNotificationAPI(
                configuration: project(), urlSession: urlSession()
            ).list(token: "revoked.jwt")
            XCTFail("Expected user token rejection")
        } catch BLINKFeedError.unauthorized {
            // Correct: no fallback public notifications.
        }
    }

    func testAuthenticatedCallHistoryAndParticipantStatus() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.httpMethod, "GET")
            XCTAssertEqual(request.url?.path, "/rest/v1/calls")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer user.jwt")
            return (200, Data("[\(String(data: self.callJSON(), encoding: .utf8)!)]".utf8))
        }
        let api = BLINKCallsAPI(configuration: project(), urlSession: urlSession())
        let rows = try await api.recent(token: "user.jwt")
        XCTAssertEqual(rows.count, 1)
        XCTAssertTrue(rows[0].isIncoming(for: calleeID))
        XCTAssertTrue(rows[0].isOutgoing(for: callerID))
        XCTAssertTrue(rows[0].canDecline)
        XCTAssertTrue(rows[0].canEnd)
    }

    func testDeclineCallUsesExistingServerAuthorizedRPC() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/decline_call")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer user.jwt")
            return (200, self.callJSON(status: "declined"))
        }
        let response = try await BLINKCallsAPI(
            configuration: project(), urlSession: urlSession()
        ).decline(callID: callID, token: "user.jwt")
        XCTAssertEqual(response.status, .declined)
        XCTAssertFalse(response.canEnd)
    }

    func testEndCallUsesServerRPCNotDirectRowUpdate() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/end_call")
            XCTAssertEqual(request.httpMethod, "POST")
            return (200, self.callJSON(status: "ended"))
        }
        let response = try await BLINKCallsAPI(
            configuration: project(), urlSession: urlSession()
        ).end(callID: callID, token: "user.jwt")
        XCTAssertTrue(response.status.isTerminal)
    }

    func testInvalidCallIDRejectedWithoutRequest() async throws {
        StubProtocol.responder = { _ in
            XCTFail("Invalid call ID must not hit production endpoints")
            return (500, Data())
        }
        do {
            _ = try await BLINKCallsAPI(
                configuration: project(), urlSession: urlSession()
            ).decline(callID: "../other-user", token: "user.jwt")
            XCTFail("Expected local validation failure")
        } catch BLINKCallError.invalidCallID {
            // Expected.
        }
    }

    func testPhase6CannotPretendRealMediaOrVoIPPushIsReady() {
        XCTAssertFalse(BLINKiOSCallAvailability.mediaTransportReady)
        XCTAssertFalse(BLINKiOSCallAvailability.incomingVoIPPushReady)
        XCTAssertFalse(BLINKiOSCallAvailability.systemCallKitReady)
    }
}

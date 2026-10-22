import XCTest
@testable import BLINKiOS

/// WebRTC transport is compiled by the Simulator job. Test RPCs are
/// intercepted locally so NO calls, tokens or messages reach live Supabase.
final class BLINKPhase7WebRTCTests: XCTestCase {
    private final class StubProtocol: URLProtocol {
        static var handler: ((URLRequest) throws -> (Int, Data))?
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
            guard let handler = Self.handler, let url = request.url else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            do {
                let (status, data) = try handler(request)
                client?.urlProtocol(self, didReceive:
                    HTTPURLResponse(url: url, statusCode: status,
                                    httpVersion: nil, headerFields: [:])!,
                    cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let callID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private let conversationID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private let calleeID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    private func api() -> BLINKCallSignalingAPI {
        let session = URLSessionConfiguration.ephemeral
        session.protocolClasses = [StubProtocol.self]
        return BLINKCallSignalingAPI(
            configuration: BLINKBackendConfiguration(values: [
                "BLINK_SUPABASE_URL": "https://example.supabase.co",
                "BLINK_SUPABASE_ANON_KEY": "sb_publishable_test"
            ])!, urlSession: URLSession(configuration: session)
        )
    }

    override func tearDown() {
        StubProtocol.handler = nil
        super.tearDown()
    }

    private static func posted(_ request: URLRequest) throws -> [String: Any] {
        let bytes: Data
        if let body = request.httpBody {
            bytes = body
        } else if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var data = Data()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let size = stream.read(&buffer, maxLength: buffer.count)
                if size < 0 { throw URLError(.cannotDecodeRawData) }
                if size == 0 { break }
                data.append(contentsOf: buffer.prefix(size))
            }
            bytes = data
        } else { throw URLError(.cannotDecodeRawData) }
        return try XCTUnwrap(JSONSerialization.jsonObject(with: bytes) as? [String: Any])
    }

    private func callJSON() -> Data {
        Data("""
          {"id":"\(callID)","conversation_id":"\(conversationID)",
           "caller_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","callee_id":"\(calleeID)",
           "call_type":"audio","status":"ringing"}
        """.utf8)
    }

    func testStartMatchesAndroidBackendAndSendsOnlyJWT() async throws {
        StubProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/start_call")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "sb_publishable_test")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            let body = try Self.posted(request)
            XCTAssertEqual(body["p_conversation_id"] as? String, self.conversationID)
            XCTAssertEqual(body["p_callee_id"] as? String, self.calleeID)
            XCTAssertEqual(body["p_call_type"] as? String, "audio")
            return (200, self.callJSON())
        }
        let call = try await api().start(
            conversationID: conversationID, calleeID: calleeID,
            kind: .audio, token: "jwt"
        )
        XCTAssertEqual(call.id, callID)
    }

    func testSaveSignalMatchesAndroidWirePayload() async throws {
        StubProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/call_signals")
            let body = try Self.posted(request)
            XCTAssertEqual(body["kind"] as? String, "ice")
            XCTAssertEqual(body["call_id"] as? String, self.callID)
            let payload = try XCTUnwrap(body["payload"] as? [String: Any])
            XCTAssertEqual(payload["sdpMid"] as? String, "audio")
            XCTAssertEqual(payload["sdpMLineIndex"] as? Int, 0)
            XCTAssertEqual(payload["candidate"] as? String, "candidate:sample")
            return (201, Data())
        }
        try await api().signal(
            callID: callID, senderID: calleeID, kind: "ice",
            payload: BLINKCallSignalPayload(candidate: "candidate:sample",
                                            sdpMid: "audio", sdpMLineIndex: 0),
            token: "jwt"
        )
    }

    func testSignalPollingHonorsCursorAndSender() async throws {
        StubProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/call_signals")
            let args = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
            XCTAssertEqual(args.first(where: {$0.name=="id"})?.value, "gt.50")
            return (200, Data("""
                [{"id":51,"call_id":"\(self.callID)","sender_id":"\(self.calleeID)",
                  "kind":"offer","payload":{"type":"offer","sdp":"v=0"}}]
                """.utf8))
        }
        let rows = try await api().signals(callID: callID, after: 50, token: "jwt")
        XCTAssertEqual(rows.first?.id, 51)
        XCTAssertEqual(rows.first?.payload.sdp, "v=0")
    }

    func testTURNCredentialsMustBeAuthenticatedAndUseSafeURLs() async throws {
        StubProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/functions/v1/call-turn-credentials")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            let body = try Self.posted(request)
            XCTAssertEqual(body["call_id"] as? String, self.callID)
            return (200, Data("""
                {"ice_servers":[
                  {"urls":["stun:example.org:3478",
                           "turns:relay.example.org:5349?transport=tcp"],
                   "username":"ephemeral-user","credential":"temporary-password"}]}
                """.utf8))
        }
        let servers = try await api().relay(callID: callID, token: "jwt")
        XCTAssertEqual(servers.count, 1)
        XCTAssertTrue(servers[0].isAuthenticatedTURN)
    }

    func testNoSTUNOnlyFallbackWhenTURNNeverDeployed() async throws {
        StubProtocol.handler = { _ in
            (200, Data("""
                {"ice_servers":[{"urls":["stun:example.org:3478"]}]}
                """.utf8))
        }
        do {
            _ = try await api().relay(callID: callID, token: "jwt")
            XCTFail("Calls must not start on only a public STUN server")
        } catch BLINKCallError.notAvailable {
            // Required.
        }
    }

    func testRejectUnauthorizedCallEndpoints() async throws {
        StubProtocol.handler = { _ in (401, Data("{}".utf8)) }
        do {
            _ = try await api().call(callID: callID, token: "expired.jwt")
            XCTFail("Unauthorized call history should not be returned")
        } catch BLINKFeedError.unauthorized {
            // Required.
        }
    }

    func testMediaFeatureFlagsStayOffUntilRealPhoneAcceptance() {
        XCTAssertFalse(BLINKiOSCallAvailability.mediaTransportReady)
        XCTAssertFalse(BLINKiOSCallAvailability.incomingVoIPPushReady)
        XCTAssertFalse(BLINKiOSCallAvailability.systemCallKitReady)
    }
}

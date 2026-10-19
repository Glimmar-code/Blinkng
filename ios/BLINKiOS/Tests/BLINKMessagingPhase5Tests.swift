import XCTest
@testable import BLINKiOS

/// Protocol stubs exercise real BLINK messaging routes without touching
/// production user data, registering test accounts or sending any messages.
final class BLINKMessagingPhase5Tests: XCTestCase {
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
                let (status, body) = try responder(request)
                client?.urlProtocol(self, didReceive: HTTPURLResponse(
                    url: url, statusCode: status, httpVersion: nil,
                    headerFields: [:])!, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: body)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let conversationID = "11111111-1111-4111-8111-111111111111"
    private let userID = "22222222-2222-4222-8222-222222222222"

    override func tearDown() {
        StubProtocol.responder = nil
        super.tearDown()
    }

    private func api() -> BLINKMessagesAPI {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        return BLINKMessagesAPI(
            configuration: BLINKBackendConfiguration(values: [
                "BLINK_SUPABASE_URL": "https://example.supabase.co",
                "BLINK_SUPABASE_ANON_KEY": "sb_publishable_messages_fixture"
            ])!,
            urlSession: URLSession(configuration: configuration)
        )
    }

    private static func body(_ request: URLRequest) throws -> [String: Any] {
        let data: Data
        if let value = request.httpBody {
            data = value
        } else if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var collected = Data()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count < 0 { throw URLError(.cannotDecodeRawData) }
                if count == 0 { break }
                collected.append(contentsOf: buffer.prefix(count))
            }
            data = collected
        } else { throw URLError(.cannotDecodeRawData) }
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testInboxUsesAuthorizedConversationSummaryRPC() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_conversation_summaries_page")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "sb_publishable_messages_fixture")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer user.jwt")
            let body = try Self.body(request)
            XCTAssertEqual(body["p_limit"] as? Int, 50)
            XCTAssertTrue(body["p_before"] is NSNull)
            return (200, Data("""
                [{"conversation_id":"\(self.conversationID)",
                  "partner_username":"seller","partner_name":"Campus Seller",
                  "last_message":"Is this still available?",
                  "unread_count":3}]
                """.utf8))
        }
        let result = try await api().conversations(token: "user.jwt")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].displayName, "Campus Seller")
        XCTAssertEqual(result[0].unreadCount, 3)
    }

    func testMessageHistoryRespectsServerPaginationAndOrder() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_conversation_messages_page")
            let body = try Self.body(request)
            XCTAssertEqual(body["p_conversation_id"] as? String, self.conversationID)
            XCTAssertEqual(body["p_limit"] as? Int, 50)
            return (200, Data("""
                [{"id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                  "conversation_id":"\(self.conversationID)",
                  "sender_id":"\(self.userID)",
                  "content":"Newer","created_at":"2026-10-19T12:02:00Z"},
                 {"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                  "conversation_id":"\(self.conversationID)",
                  "sender_id":"\(self.userID)",
                  "content":"Older","created_at":"2026-10-19T12:01:00Z"}]
                """.utf8))
        }
        let response = try await api().messages(
            conversationID: conversationID, token: "user.jwt"
        )
        XCTAssertEqual(response.messages.map(\.displayedContent), ["Older", "Newer"])
        XCTAssertEqual(response.beforeId, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        XCTAssertFalse(response.hasMore)
    }

    func testMessageDeleteRedactsAttachment() throws {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let msg = try decoder.decode(BLINKChatMessage.self, from: Data("""
            {"id":"a","deleted_for_everyone":true,
             "content":"Hidden","media_url":"https://example.com/private.png"}
            """.utf8))
        XCTAssertEqual(msg.displayedContent, "Message deleted")
        XCTAssertNil(msg.attachment)
    }

    func testNewSellerDMUsesServerUsernameAndAcknowledgement() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/send_message_v2")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            let body = try Self.body(request)
            XCTAssertEqual(body["p_receiver_username"] as? String, "seller")
            XCTAssertEqual(body["p_content"] as? String, "Hello, is this available?")
            return (200, Data("""
                [{"message_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                  "conversation_id":"\(self.conversationID)"}]
                """.utf8))
        }
        let receipt = try await api().sendDirect(
            to: "@seller", content: "  Hello, is this available?  ", token: "jwt"
        )
        XCTAssertEqual(receipt?.conversationId, conversationID)
    }

    func testGroupMessageUsesGroupSpecificRPC() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/send_group_message")
            let body = try Self.body(request)
            XCTAssertEqual(body["p_conversation_id"] as? String, self.conversationID)
            XCTAssertEqual(body["p_content"] as? String, "Hello group")
            return (200, Data(#""aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa""#.utf8))
        }
        try await api().sendGroup(
            conversationID: conversationID, content: "Hello group", token: "jwt"
        )
    }

    func testDirectReadReceiptUsesExistingServerRPC() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/mark_conversation_read")
            XCTAssertEqual(try Self.body(request)["p_partner_username"] as? String, "seller")
            return (200, Data("1".utf8))
        }
        try await api().markDirectRead(partner: "@seller", token: "jwt")
    }

    func testGroupReadUpdateScopedToConversationAndUser() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.httpMethod, "PATCH")
            XCTAssertEqual(request.url?.path, "/rest/v1/conversation_participants")
            let q = URLComponents(url: try XCTUnwrap(request.url),
                                  resolvingAgainstBaseURL: false)?.queryItems ?? []
            XCTAssertEqual(q.first(where: { $0.name == "user_id" })?.value, "eq.\(self.userID)")
            XCTAssertEqual(q.first(where: { $0.name == "conversation_id" })?.value,
                           "eq.\(self.conversationID)")
            XCTAssertNotNil(try Self.body(request)["last_read_at"])
            return (204, Data())
        }
        try await api().markGroupRead(
            conversationID: conversationID, userID: userID, token: "jwt"
        )
    }

    func testInvalidRecipientAndBlankContentCannotBeSent() async throws {
        XCTAssertEqual(BLINKRecipient.normalize("@seller"), "seller")
        XCTAssertNil(BLINKRecipient.normalize("seller@example.com"))
        XCTAssertNil(BLINKRecipient.normalize("../admin"))
        XCTAssertNil(BLINKRecipient.normalize(""))
        XCTAssertNil(BLINKRecipient.content("  "))
        XCTAssertNil(BLINKRecipient.content(String(repeating: "x", count: 8001)))
        StubProtocol.responder = { _ in
            XCTFail("Invalid outbound messages must never reach the server")
            return (500, Data())
        }
        do {
            _ = try await api().sendDirect(to: "not@valid", content: "hello", token: "jwt")
            XCTFail("Expected invalid username")
        } catch BLINKMessagingError.invalidRecipient {
            // Expected.
        }
    }

    func testRevokedSessionRejectsInboxInsteadOfShowingFakeChats() async throws {
        StubProtocol.responder = { _ in (401, Data("{}".utf8)) }
        do {
            _ = try await api().conversations(token: "expired.jwt")
            XCTFail("Revoked token must be rejected")
        } catch BLINKFeedError.unauthorized {
            // Expected.
        }
    }
}

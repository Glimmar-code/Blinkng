import XCTest
@testable import BLINKiOS

final class BLINKFeedPhase3Tests: XCTestCase {
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
                let (code, data) = try responder(request)
                client?.urlProtocol(self, didReceive:
                    HTTPURLResponse(url: url, statusCode: code, httpVersion: nil, headerFields: [:])!,
                    cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
        override func stopLoading() {}
    }

    private let postId = "11111111-1111-4111-8111-111111111111"
    private let userId = "22222222-2222-4222-8222-222222222222"

    private func feedAPI() -> BLINKFeedAPI {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return BLINKFeedAPI(configuration: BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_testing"
        ])!, urlSession: URLSession(configuration: config))
    }

    override func tearDown() {
        StubProtocol.responder = nil
        super.tearDown()
    }

    func testUsesRankedFeedRPCAndActualProfilesWithAuthJWT() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "sb_publishable_testing")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer user.jwt")
            if request.url?.path == "/rest/v1/rpc/get_ranked_feed_page" {
                return (200, Data("""
                    [{"item":{"id":"\(self.postId)","user_id":"\(self.userId)",
                      "text":"Real post","like_count":8,"comment_count":2,
                      "image_url":"https://media.example.com/one.png","is_reel":false},
                      "next_offset":20,"as_of":"2026-10-19T12:00:00Z"}]
                    """.utf8))
            }
            XCTAssertEqual(request.url?.path, "/rest/v1/profiles")
            return (200, Data("""
                [{"id":"\(self.userId)","username":"campususer",
                  "full_name":"BLINK student","is_verified":true}]
                """.utf8))
        }
        let page = try await feedAPI().feed(mode: .posts, offset: 0, asOf: nil, accessToken: "user.jwt")
        XCTAssertEqual(page.posts.count, 1)
        XCTAssertEqual(page.posts.first?.postText, "Real post")
        XCTAssertEqual(page.posts.first?.likeCount, 8)
        XCTAssertEqual(page.authors[userId]?.displayName, "BLINK student")
        XCTAssertEqual(page.nextOffset, 20)
        XCTAssertFalse(page.hasMore)
    }

    func testFollowingUsesExistingRPCWithoutRankedWrapper() async throws {
        StubProtocol.responder = { request in
            if request.url?.path == "/rest/v1/rpc/get_following_feed" {
                return (200, Data("""
                    [{"id":"\(self.postId)","user_id":"\(self.userId)",
                      "text":"Following post"}]
                    """.utf8))
            }
            return (200, Data("[]".utf8))
        }
        let page = try await feedAPI().feed(mode: .following, offset: 0, asOf: nil, accessToken: "jwt")
        XCTAssertEqual(page.posts.first?.postText, "Following post")
        XCTAssertFalse(page.hasMore)
    }

    func testCommentsRPCUsesAuthenticatedEndpoint() async throws {
        StubProtocol.responder = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_post_comments")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer jwt")
            return (200, Data("""
                [{"id":"33333333-3333-4333-8333-333333333333",
                  "username":"friend","content":"Hello from BLINK"}]
                """.utf8))
        }
        let comments = try await feedAPI().comments(postId: postId, accessToken: "jwt")
        XCTAssertEqual(comments.first?.content, "Hello from BLINK")
    }

    func testRejectsUnauthorizedFeedInsteadOfInventingPosts() async throws {
        StubProtocol.responder = { _ in (401, Data("{}".utf8)) }
        do {
            _ = try await feedAPI().feed(mode: .posts, offset: 0, asOf: nil, accessToken: "expired.jwt")
            XCTFail("Unauthorized access should be blocked")
        } catch BLINKFeedError.unauthorized {
            // Expected.
        }
    }

    func testOnlyAllowsHTTPSMediaAndDoesNotAcceptDataURLs() {
        XCTAssertNotNil(BLINKMediaURL.safe("https://media.example.com/image.png"))
        XCTAssertNil(BLINKMediaURL.safe("http://media.example.com/image.png"))
        XCTAssertNil(BLINKMediaURL.safe("data:text/html,evil"))
        XCTAssertNil(BLINKMediaURL.safe("https://username:password@example.com/image.png"))
        XCTAssertNil(BLINKMediaURL.safe(nil))
    }
}

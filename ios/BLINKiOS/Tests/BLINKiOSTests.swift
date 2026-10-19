import XCTest
@testable import BLINKiOS

final class BLINKiOSTests: XCTestCase {
    func testFiveTabsFollowAndroidOrder() {
        XCTAssertEqual(BLINKSection.allCases.map(\.title), [
            "Home", "Reels", "Market", "Connect", "Message"
        ])
    }

    func testEveryTabHasAccessibleSymbolAndDescription() {
        for section in BLINKSection.allCases {
            XCTAssertFalse(section.symbol.isEmpty)
            XCTAssertFalse(section.description.isEmpty)
        }
    }

    func testMissingBackendConfigurationStaysDisconnected() {
        XCTAssertNil(BLINKBackendConfiguration(values: [:]))
    }

    func testRejectsNonTLSBackendAndServiceSecrets() {
        XCTAssertNil(BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "http://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_example"
        ]))
        XCTAssertNil(BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_secret_do_not_ship"
        ]))
        XCTAssertNil(BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "$(BLINK_SUPABASE_ANON_KEY)"
        ]))
    }

    func testAllowsPublishableClientKey() {
        let result = BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_example"
        ])
        XCTAssertEqual(result?.url.host, "example.supabase.co")
    }
}

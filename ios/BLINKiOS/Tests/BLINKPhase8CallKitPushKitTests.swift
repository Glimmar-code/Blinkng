import XCTest
@testable import BLINKiOS

/// Contract tests do not register with APNs or display any system call UI.
/// Real-world PushKit receipt requires an entitled, signed physical iPhone.
final class BLINKPhase8CallKitPushKitTests: XCTestCase {
    func testVoIPPushReleaseFlagDefaultsToDisabled() {
        XCTAssertFalse(BLINKVoIPConfiguration.enabled(values: [:]))
        XCTAssertFalse(BLINKVoIPConfiguration.enabled(values: [
            "BLINK_VOIP_PUSH_ENABLED": "NO"
        ]))
        XCTAssertFalse(BLINKVoIPConfiguration.enabled(values: [
            "BLINK_VOIP_PUSH_ENABLED": "unconfigured"
        ]))
        XCTAssertTrue(BLINKVoIPConfiguration.enabled(values: [
            "BLINK_VOIP_PUSH_ENABLED": "YES"
        ]))
        XCTAssertFalse(BLINKVoIPConfiguration.enabledInBuild)
    }

    func testMalformedPushDoesNotConstructCallKitRequest() {
        XCTAssertNil(BLINKVoIPPushCall(dictionary: [:]))
        XCTAssertNil(BLINKVoIPPushCall(dictionary: [
            "call_id": "../fake-call", "call_type": "video"
        ]))
        XCTAssertNil(BLINKVoIPPushCall(dictionary: [
            "call_id": 9999
        ]))
    }

    func testIncomingPushOnlyCarriesServerCallIdentifierAndGenericDisplayName() throws {
        let id = UUID()
        let input: [AnyHashable: Any] = [
            "call_id": id.uuidString,
            "call_type": "video",
            "caller_name": "Unverified lookalike account"
        ]
        let call = try XCTUnwrap(BLINKVoIPPushCall(dictionary: input))
        XCTAssertEqual(call.id, id)
        XCTAssertTrue(call.isVideo)
        XCTAssertEqual(call.displayName, "BLINK caller")
        XCTAssertNotEqual(call.displayName, input["caller_name"] as? String)
    }

    func testUnknownCallTypeIsNotTreatedAsVideo() throws {
        let id = UUID()
        let call = try XCTUnwrap(BLINKVoIPPushCall(dictionary: [
            "call_id": id.uuidString, "call_type": "unknown"
        ]))
        XCTAssertFalse(call.isVideo)
    }

    func testRegistrationIsNotAvailableWithoutSignedRolloutFlag() async throws {
        let config = try XCTUnwrap(BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_fixture"
        ]))
        let api = BLINKVoIPRegistrationAPI(configuration: config)
        do {
            try await api.register(
                token: Data([0x01, 0x02, 0x03]),
                deviceID: UUID(),
                environment: "sandbox",
                userJWT: "test-jwt"
            )
            XCTFail("Not entitled to register VoIP tokens in current test build")
        } catch BLINKCallError.notAvailable {
            // Required: no real network request.
        }
    }

    func testNoEarlyFeatureFlagsCanPretendBackgroundCallingWorks() {
        XCTAssertFalse(BLINKiOSCallAvailability.incomingVoIPPushReady)
        XCTAssertFalse(BLINKiOSCallAvailability.systemCallKitReady)
        XCTAssertFalse(BLINKiOSCallAvailability.mediaTransportReady)
    }
}

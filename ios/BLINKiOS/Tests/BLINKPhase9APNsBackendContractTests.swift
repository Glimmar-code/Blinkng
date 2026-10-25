import XCTest
@testable import BLINKiOS

/// Tests preserve the release gate: running them does not contact Supabase,
/// Apple APNs, register a token or display an incoming system call.
final class BLINKPhase9APNsBackendContractTests: XCTestCase {
    func testVoIPRemainsDisabledWithoutEntitledAppleRelease() {
        XCTAssertFalse(BLINKVoIPConfiguration.enabledInBuild)
        XCTAssertFalse(BLINKiOSCallAvailability.incomingVoIPPushReady)
        XCTAssertFalse(BLINKiOSCallAvailability.systemCallKitReady)
    }

    func testRegistrationAndRevocationNeverSendWithReleaseGateDisabled() async throws {
        let config = try XCTUnwrap(BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": "https://example.supabase.co",
            "BLINK_SUPABASE_ANON_KEY": "sb_publishable_fixture",
        ]))
        let api = BLINKVoIPRegistrationAPI(configuration: config)
        let device = UUID()
        do {
            try await api.register(token: Data(repeating: 0x5b, count: 32),
                                   deviceID: device, environment: "sandbox",
                                   userJWT: "signed.in.test.jwt")
            XCTFail("VoIP register must not be permitted in this test build")
        } catch BLINKCallError.notAvailable {
            // Expected.
        }
        do {
            try await api.revoke(deviceID: device, environment: "sandbox",
                                 userJWT: "signed.in.test.jwt")
            XCTFail("VoIP revoke is disabled in this unsigned test build")
        } catch BLINKCallError.notAvailable {
            // Expected.
        }
    }

    func testVoIPCallPayloadCannotSpoofCallerName() throws {
        let uuid = UUID()
        let candidate = try XCTUnwrap(BLINKVoIPPushCall(dictionary: [
            "call_id": uuid.uuidString,
            "call_type": "audio",
            "caller_name": "Spoofed verified user",
        ]))
        XCTAssertEqual(candidate.id, uuid)
        XCTAssertEqual(candidate.displayName, "BLINK caller")
        XCTAssertFalse(candidate.isVideo)
    }
}

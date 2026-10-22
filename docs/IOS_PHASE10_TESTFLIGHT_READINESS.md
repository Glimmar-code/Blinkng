# BLINK iOS Phase 10 — TestFlight readiness (source only)

**Status: release preparation only. Not a signed iPhone build, not a TestFlight upload.**

The iOS Phase 1–9 feature branches remain stacked, most recently PR #449. Phase 10 is based on Phase 9 and does not merge to Testlab or main.

## Implementation
- Added predictable iOS version metadata (`CFBundleShortVersionString`, `CFBundleVersion`, marketing version and build number) in XcodeGen without changing Android/Windows versions.
- Added an automatic **unsigned arm64 iPhone device archive** GitHub quality gate. It checks actual `iphoneos` compilation and generated app ID, in addition to the existing Simulator XCTest gate. An unsigned archive CANNOT install as a normal TestFlight release.
- Added a branded icon preparation script that derives a 1024px opaque image from BLINK's existing Android icon. It does not replace the original artwork. **An owner must visually review the appearance and align it with the approved black-and-white brand before any public launch.**
- Added a standalone source-release gate checking the exact app ID, stable version/build strings, push-disabled defaults and absence of server-only keys, with negative safety tests.
- Prepared an **entirely manual** Apple distribution workflow: `.github/workflows/ios-testflight-internal.yml`. It accepts explicit version/build, a typed release approval, and a separate *upload_to_testflight* switch (defaults OFF). The workflow only executes from a commit on `main`, under the owner-managed `ios-testflight` GitHub Environment (configure required reviewer approval), after checking all required quality workflows passed on that exact SHA.
- The workflow checks for Apple-provided signing secrets without printing values, imports a temporary distribution certificate and provisioning profile, verifies Team ID and bundle ID, archives for a generic iOS device, exports a signed App Store Connect IPA, records a SHA-256 checksum, and optionally uses Apple's upload tooling after explicit approval. Temporary credentials/keychain are removed afterward.
- Keeps **`BLINK_VOIP_PUSH_ENABLED=NO`** and APNs environment `unconfigured` for initial internal TestFlight. Do not activate true background calling before Apple entitlement/server/real-phone checks.
- No Apple credentials, billing data, service-role secrets, real production user content, Supabase migrations, or production Android packages were modified by Phase 10.

## Signoff gates before even one internal upload

1. The authorized Apple Developer account owner verifies enrollment/account eligibility and access permissions; registers the BLINK iOS App ID `ng.com.blink.ios`, sets up the App Store Connect app record, appropriate distribution certificate and App Store profile, and confirms account/team and bundle continuity. The assistant cannot create or verify these without owner-controlled access.
2. In GitHub repository settings, create the protected `ios-testflight` Environment with **required owner approval** and limit deployments to `main`. Add these exact secrets *only in the protected environment*: `IOS_CERT_P12_BASE64`, `IOS_CERT_PASSWORD`, `IOS_PROFILE_BASE64`, `IOS_TEAM_ID`, `ASC_API_KEY_ID`, `ASC_API_ISSUER_ID`, `ASC_API_PRIVATE_KEY`. Never paste signing keys into chat/source. Ensure Xcode 26+ is selected on the macOS runner for the 2026 App Store upload rule.
3. Confirm Apple-approved privacy and safety controls: working report/block flows, account deletion, age rating, privacy policy, appropriate permissions and subscriptions/purchase rules. No unchecked payment flows or untested features should be advertised.
4. Resolve stacked iOS branch dependencies in sequence through Testlab; run CI and inspect screenshots/interaction parity; then merge only verified changes into `main` with owner approval. Do not bypass Testlab or protection.
5. Required CI on the exact approved `main` SHA: **iOS foundation quality gate; iOS VoIP backend security gate; iOS unsigned device archive quality gate; Android quality gate; Windows desktop quality gate**. Every gate must be green; no skipped missing gates are accepted.
6. Run the workflow from `main` with **upload_to_testflight=false** first. Verify certificate/profile, produced signed IPA, icon, version/build and checksum in Actions. If signing is missing or invalid, stop without uploading.
7. After all automated checks pass, perform one consolidated real-device acceptance round on an iPhone and Android using test accounts (login, feed, media, chat, groups, market, Connect, notifications, calls, permissions, blocked-user behavior, data preservation and logout). Avoid repeatedly asking the BLINK owner to download intermediary APKs.
8. Only after passing device/security checks should the owner explicitly choose **upload_to_testflight=true**; check Apple build processing and distribute to a limited **internal** TestFlight group first. Upload success alone does not imply Apple processing, tester availability, functional calling, or App Store publication.
9. PushKit is OFF in this build. Enable genuine incoming VoIP later only after Supabase APNs backend is deployed securely, Apple PushKit/CallKit entitlement approved and two-device incoming-call tests succeed.
10. A normal TestFlight build is *not an App Store production release*. An App Store release needs separate listing, product policy and review work.

## Important limitations
- Phase 10 does **not** have access to the developer's Apple Team ID, App Store Connect API private key, certificate or signed iPhone device. No Apple account settings were changed.
- Existing iOS features still require real integration tests; working simulator CI is not proof of real server and hardware compatibility.
- Apple beta metadata, screenshots, approved branding, age rating, in-app privacy/report/block/deletion and Apple purchasing policies still need review.
- The manual release workflow is intentionally not run while Phase 1–10 remain unmerged. No iOS IPA uploaded or published.
- If Xcode 26 is not installed on the runner, the manual signed workflow **fails safely**; update the approved runner's Xcode rather than disabling minimum SDK checks.

Apple source references:
- https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/
- https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/
- https://developer.apple.com/news/upcoming-requirements/

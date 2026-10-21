# BLINK iOS Phase 9 — Apple APNs VoIP Backend & Signing Preparation

**Implementation:** committed to a STACKED feature branch only. Production Supabase remains unchanged. Apple Developer signing, APNs credentials, server deployment and physical iPhone tests are NOT completed.

## Prepared in repository
- `supabase/migrations/20261021110000_ios_voip_tokens_and_dispatch.sql` — two new tables `public.ios_voip_tokens` and `public.ios_voip_dispatches` with RLS ON, explicit REVOKE from `PUBLIC`, `anon`, `authenticated`; grants ONLY to `service_role`. Device token ownership and APNs environment are stored server-side. Token values are never returned through the authenticated Data API.
- `supabase/functions/ios-voip-register` — verifies the Supabase Auth session on the server with `admin.auth.getUser(jwt)`, checks device UUID, configured bundle ID, sandbox/production environment and exact APNs token format. Supports `register` and `revoke` for the currently authenticated account, with conflict rejection and a 6-device cap.
- `supabase/functions/ios-voip-dispatch` — internal-only, separate secret-header authentication. It verifies the actual server-side ringing, unexpired call and current block status; looks up only the callee's eligible Apple VoIP tokens, claims per-call/per-device dedupe, and signs an ES256 JWT using the server-only APNs `.p8` key before POST to Apple `/3/device/<token>`. Requires `apns-topic=<bundle>.voip`, `apns-push-type=voip`, short-lived expiration and reports **accepted by APNs**, never “received on phone.”
- `send-call-notification` gained a *double feature-gated* branch that can invoke the internal iOS dispatcher on a real `invite` before Android FCM processing. It remains OFF by default, and Android push continues unaffected if the optional iOS dispatcher is unavailable.
- iOS now attempts server-side device-token revocation before actual Supabase sign-out. If a revocation cannot be confirmed, the app must not claim the token was removed remotely. Device invalidation / account switching also require testing.
- New `iOS VoIP backend security gate` GitHub workflow performs Deno type-checks and validates default-off release flags, RLS, explicit server grants, user verification and APNs privacy protections. XCTest confirms iOS registration/revocation remains disabled in the test build.

## Fail-closed configuration (all private env secrets on Supabase, NOT in Git)
- `BLINK_IOS_VOIP_SERVER_ENABLED=false` initially (required value `true` only after staging validation).
- `BLINK_IOS_VOIP_DISPATCH_ENABLED=false` initially.
- `BLINK_IOS_DISPATCH_SECRET` = a high-entropy unique server-to-server secret never shipped to iOS or Android.
- `BLINK_IOS_APNS_BUNDLE_ID` = exact Apple-registered BLINK iOS bundle identifier (currently source uses `ng.com.blink.ios`, Apple account ownership not verified).
- `BLINK_IOS_APNS_ENVIRONMENT` = exact `sandbox` or `production`; never mix tokens.
- `BLINK_IOS_APNS_TEAM_ID`, `BLINK_IOS_APNS_KEY_ID`, `BLINK_IOS_APNS_PRIVATE_KEY` = authorized Apple developer credentials; **never paste the .p8 private key in chat or commit it**.
- Existing secure Supabase `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` are available in deployed server environment, not used from the app.

## Apple Developer account owner actions
1. Sign into the Apple Developer account that **legally owns** the BLINK app. Confirm Team ID, registered App ID and bundle ID match XcodeGen `PRODUCT_BUNDLE_IDENTIFIER`.
2. Configure Push Notifications and required VoIP background capability / provisioning on the App ID. Use a *signed physical iPhone* for PushKit; Simulator tests do not prove delivery.
3. Generate or reuse an appropriate APNs key/certificate through Apple's account controls. Store `.p8`, Key ID and Team ID only in the trusted Supabase secret store (do NOT give them to chat).
4. Rebuild with valid developer signing and a review-approved entitlement setup. Source defaults deliberately remain `BLINK_VOIP_PUSH_ENABLED=NO`, `BLINK_APNS_ENVIRONMENT=unconfigured`.
5. Set the backend registration/dispatch flags only after backend/schema migration passes staged security and functional tests and **server-issued real calls** pass two-device testing. Enable the signed-app flag LAST.
6. Test true live iPhone ↔ Android calls, screen locked, app terminated, low connectivity/NAT, direct DMs, blocked users, duplicate pushes, absent microphone/camera permissions, hangup, logout/token revocation and correct call cancel/timeout.
7. Review Apple's CallKit/PushKit policy; VoIP pushes are only for incoming calls and must promptly report the call through CallKit. Do not send ordinary messages or promotions through PushKit.

## Security and correctness limitations
- **No Supabase migration or functions were deployed** by this Phase 9 PR; the existing production `ios-voip-register` and `ios-voip-dispatch` endpoints have not been installed.
- APNs acceptance `200` does NOT prove user delivery, CallKit presentation or media connectivity. Track receipt and two-way media separately on real phones.
- The iOS client registration body identifies the app bundle, but a custom-signed or jailbroken client can spoof it. Additional iOS App Attest/device attestation is recommended before enabling push fanout at scale.
- The current private device token storage has a 30-day freshness filter in the dispatcher but not an automatic maintenance job. Set secure retention/deletion schedule and audit server access before production rollout.
- Backend double flags and iOS build flag are **independent**. Never activate them together without authorizing the deployment plan and real-device acceptance.
- Account-switching requires token ownership checks. A failed sign-out revoke can leave a stale registration until manual recovery / expiry; validate in staging before activation.

## Integration and testing gates
- `PR #445` (Phase 8) is still a draft stacked on #444. Phase 9 targets Phase 8 and does not touch `Testlab` or `main`.
- Review the SQL in a disposable Supabase staging project; apply migration and verify RLS with **anon**, **authenticated** and **service_role** tests, then secure Edge Function config and environment secrets.
- GitHub iOS Simulator/XCTest, Deno syntax/typecheck, Android and Windows checks must pass on the exact last SHA. Only THEN consider Testlab integration, a signed iOS build and two-device acceptance. Do not enable existing production secrets to test a new endpoint.

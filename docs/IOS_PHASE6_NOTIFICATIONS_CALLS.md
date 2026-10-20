# BLINK iOS Phase 6 — Notifications and Calling Foundation

**Status:** Staged iOS source. This is NOT completed two-way calling and NOT a push-notification rollout.

## Shipped to source (no production publication)

- An **in-app Activity/Notifications screen**, backed by existing `public.notifications` under authenticated user JWT and row-level security. Loading, empty state, error state and manual refresh are implemented. Only the newest 50 rows are shown, with server read status.
- **System notification permission opt-in** using Apple's `UNUserNotificationCenter`, controlled by the user from Settings. App delegate is installed for future APNs callbacks and never prints or persists a raw token. It does not attempt to upload APNs tokens as Android FCM tokens.
- A **Calls history and status screen** using existing `public.calls` SELECT (caller/callee RLS). It distinguishes audio/video, incoming/outgoing and server call status; incoming calls can be declined, and callers can cancel, via existing authenticated `decline_call` / `end_call` RPCs. It does not automatically start or answer calls.
- Call history entrypoints in iOS Messages and individual conversations. iOS Simulator compilation and protocol-mocked tests ensure endpoint contracts and verified user JWT headers.
- iPhone AppDelegate push-notification plumbing, camera/microphone permission description strings, and explicit media-readiness gates defaulting to `false`.
- No new Supabase tables, migrations, raw private notification logs, fake successful calls or credentials.

## Deliberately NOT implemented and why

1. **Voice/audio/video RTP transport**: iOS still needs a tested WebRTC binary/client, call offers/answers/ICE candidate exchange with existing Supabase `call_signals`, audio session routing, camera preview, TURN fallback, disconnect/reconnect and cleanup. An iPhone cannot be represented as connected until actual two-way media works with Android.
2. **Background incoming VoIP calls**: require Apple Developer app identifier/signing, PushKit VoIP token, a secured APNs provider/service, CallKit reporting/answer/hang-up, token lifecycle, caller authenticity validation and a true media engine. CallKit must not report calls that cannot actually connect.
3. **Background notification delivery**: require Apple Developer/APNs entitlements and production server routing APNs tokens to Apple. The existing Android FCM registration does not prove iOS push works. This phase intentionally avoids storing an APNs device token in Android FCM tables.
4. **Marking notifications read**: actual production DB exposed `SELECT` for authenticated users but **not `UPDATE`**. Even though there is an owner UPDATE RLS policy, lack of table-level GRANT means the direct API will reject PATCH. No new grants are silently applied to production. To add read updates, design a scoped backend policy/migration and test it separately.
5. **Live user tests**: all XCTest fixtures are mocked. No actual device/APNs delivery, real call, microphone/camera capture, permission prompt or two-person media test has been verified.
6. **Call history pagination**: newest 35, no older cursor. Incoming ringing state updates require the user to refresh; not a replacement for PushKit.
7. **Account controls**: reporting, abuse protection, blocked caller behavior, consent, background lifecycle and privacy settings need real device tests before Apple submission.

## Confirmed Supabase contracts (read-only schema inspection)

- `public.notifications`: owner SELECT RLS and table SELECT for authenticated; no authenticated table UPDATE grant.
- `public.calls`: caller/callee SELECT RLS, `id`, `conversation_id`, `caller_id`, `callee_id`, `call_type`, `status`, timestamps.
- `public.decline_call(p_call_id uuid)` and `public.end_call(p_call_id uuid,p_reason text)` exist and allow `authenticated` EXECUTE.
- Other call/media signaling functions exist in source but NOT used to misrepresent a functional iOS call.

## Verification gates

1. Wait for iOS simulator compile and XCTest on exact feature SHA.
2. Check Android/Windows quality gates (no changes expected).
3. Confirm app signing/Apple identity by the authorized owner before real APNs setup.
4. Test in-app notifications for two users with RLS isolation.
5. Verify CallKit/PushKit, WebRTC, TURN, two-way iOS↔Android audio/video and background delivery before enabling actual call buttons.
6. Check notification policy, permissions and in-app abuse reporting. Do not publish a feature label claiming calls are supported until actual media tests pass.

**Branching:** Phase 6 is stacked on Phase 5 #426 → Phase 4 #423 → Phase 3 #419 → Phase 2 #417 → Phase 1 #416. This PR must not be merged into main until checks, dependencies, and real-device acceptance tests pass.

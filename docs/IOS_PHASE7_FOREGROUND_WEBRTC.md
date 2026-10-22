# BLINK iOS Phase 7 — Experimental Foreground WebRTC Calling

**Staged development only. Never call this App Store or release ready without real-device confirmation.**

## Source added

1. WebRTC binary SDK via Swift Package Manager, pinned to community distribution `stasel/WebRTC` **153.0.0**. Project generation stays through XcodeGen and existing macOS simulator CI. This is a dependency addition with a sizable binary download; track license, binary provenance and build size before promotion.
2. Native audio/video peer connection with Unified Plan, real microphone/audio session, optional camera capture (device only), microphone mute, camera disable, speaker routing, renderer views and session cleanup on hangup/background. Microphone/camera permission is requested only after explicit user selection.
3. Android-compatible Supabase signaling:
   - `start_call(p_conversation_id,p_callee_id,p_call_type)`, `answer_call(p_call_id)`, `mark_call_connected`
   - `call_signals` `offer`, `answer`, `ice` records; SDP payload keys `type`, `sdp` and ICE keys `sdpMid`, `sdpMLineIndex`, `candidate`
   - authenticated `call-turn-credentials` Edge Function; fail closed if no authorized TURN username/password, no built-in static secrets
   - periodic call status/signaling reconciliation while the call sheet is active; no background traffic after leaving the view
   - Android push function called best-effort for outgoing calls; does NOT provide iOS APNs notifications.
4. Experimental voice/video buttons in individual iOS message conversations, with a foreground calling sheet, live WebRTC ICE connection state, call controls and cleanup.
5. Incoming **manual** answer from the in-app Calls screen; there are no automatic background incoming notifications on iOS.
6. Isolated URLProtocol XCTest for real signaling URLs/payload shape/authenticated TURN, fake-calls rejection and safety gates. These tests do **not** establish audio/video works between devices.

## Production blockers / do not claim these are done

- Real iPhone ↔ Android voice and video test with **two consenting test accounts** on different networks and in both call directions; verify server/RLS block, privacy, call status, and media track reliability.
- **CallKit + PushKit/APNs** for incoming calls while BLINK is closed. Requires authorized Apple Developer signing, a secure iOS-specific token backend and confirmed APNs delivery. Do not register an APNs token as Android FCM.
- Voice quality diagnostics, TURN fallback across carrier NAT, ICE restart, dropped network/reconnect, speaker/headset/Bluetooth behaviors, interruptions, battery usage, missed call handling, session timeout, camera permissions, mute state, and group calls.
- The app automatically ends its **foreground-only** call when backgrounded. No background audio entitlement is enabled.
- WebRTC SDP and ICE signals must remain visible only to actual call participants under RLS. Inspect policies before production rollout.
- Moderation/report/block and platform age/privacy safeguards must be tested; no user can use the UI to override server privacy controls.
- If any CI step fails, fix before moving the stacked PR. iOS binaries and signing are independent from signed Android APK distribution.

## Verification before merges

Phase 7 is stacked on iOS Phase 6 (#432), itself on #426 → #423 → #419 → #417 → #416. These branches have **not** been integrated into Testlab. Avoid broad Testlab-to-main merge; selectively promote only verified iOS changes after approvals.

Check the exact latest commit's macOS Xcode generation, Simulator compile/XCTest, Android and Windows gates; then verify real iPhone and Android media end-to-end and ensure the app UI matches BLINK black-and-white design.

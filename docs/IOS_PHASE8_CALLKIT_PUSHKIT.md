# BLINK iOS Phase 8 — CallKit & PushKit integration (release-gated)

**Status: implemented as gated native iOS source; disabled for real users.** No Apple Developer APNs credentials or iOS backend delivery were configured. No background calls were delivered or tested.

## Native implementation
- **CallKit CXProvider** reports real incoming VoIP payloads promptly, uses a generic BLINK caller label pending verification, and handles Answer / End / Mute / provider reset.
- Answer must pass an authenticated Supabase lookup confirming the call's callee and ringing status, create an actual WebRTC peer with TURN, and receive server acknowledgement. Otherwise the CallKit answer action fails and the screen is dismissed. The CallKit answer and SwiftUI view share *one* media coordinator; no duplicate calls.
- **PushKit PKPushRegistry** is installed at app launch only when an explicit signed build flag is enabled. Raw APNs VoIP tokens are held only transiently in memory and never logged or written to Android's FCM table.
- The proposed separate `functions/v1/ios-voip-register` registration client passes user JWT, bundle ID, app environment, device UUID and VoIP token. **There is no deployed registration endpoint** in the verified production environment; the client fails closed until it exists.
- CallKit reports the incoming call **before** performing network validation, as required by Apple; it subsequently checks call ownership/status and ends invalid calls. The APNs sender MUST authenticate each call and issue only genuine actionable VoIP pushes.
- Foreground call UI is wired to CallKit's accepted coordinator; system Close triggers a CallKit End action rather than starting a second WebRTC session.
- User logout clears local call authorization and dismisses CallKit; a server-side token revocation endpoint is still a release blocker.

## Explicitly disabled defaults
- XcodeGen `BLINK_VOIP_PUSH_ENABLED = NO` and `BLINK_APNS_ENVIRONMENT = unconfigured`. Even a successful Simulator build **does not deliver VoIP pushes**.
- No `aps-environment` or VoIP background entitlement has been silently turned on. Those require authorized Apple signing/provisioning and actual business policy review.
- No Supabase migrations, secret values or current Android/Windows app files are changed.

## Required server/backend work before enabling
1. Authorized Apple Developer account and matching signed iOS bundle ID, VoIP push key/certificate, correct APNs sandbox/production channel and required background modes/capabilities.
2. A **server-only** validated `ios-voip-register` Edge Function and token store; authenticated registration, per-device upsert, logout/account-switch token revocation, APNs environment isolation, ownership RLS, rate limits, privacy retention and inactive-token cleanup. **Never** expose APNs private key in Swift.
3. Update `send-call-notification` to resolve genuine ringing calls server-side, enforce caller and callee permissions/blocking, and deliver an APNs `voip` push with a valid `call_id` only for the intended user's active devices. Handle cancellations and missed calls.
4. Apple's policy requires PushKit VoIP pushes to represent real calls and be promptly reported to CallKit; using VoIP pushes for generic marketing, presence or chats violates that workflow. Push payloads must include the genuine call ID and must not leak personal details.
5. Harden how background calling activates AVAudioSession via CallKit. Current WebRTC coordinator sets up audio itself and requires real-device interruption/routing tests.
6. Two consenting test accounts + real iPhones and Android phones, cold-launch and killed-app states, screen lock, network change, DND, no permissions, revoked tokens, duplicated/late pushes, call timeout, long ringtone and hangup from lock screen.
7. Validate unread notifications, privacy and safety reporting/blocking rules across accounts before shipping. System calls must respect blocked users.

## Merge and release instructions
- CI: XcodeGen+macOS Simulator build, XCTest, Android/Windows gates; inspect the exact final SHA.
- This Phase 8 feature branch is stacked on Phase 7 #444 and all older drafts, **not** production Testlab.
- **Do not change** `BLINK_VOIP_PUSH_ENABLED` to `YES` until the backend and signing work is deployed and a physical-iPhone PushKit-to-CallKit-to-WebRTC test passes, with reliable server token revocation.
- Do not claim background incoming calling, TestFlight or App Store readiness from passing unit tests alone.

Docs: Apple PushKit delegate must report actual VoIP calls promptly to CallKit on iOS 13+.

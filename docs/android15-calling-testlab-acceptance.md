# BLINK Android 15 calling: Testlab acceptance gate (2026-10-21)

## Evidence and hypotheses

A Redmi 15 / Android 15 (HyperOS) user reports a caller that remains at "Calling…" and a separate native abort while WebRTC loads `libjingle_peerconnection_so.so` with `JNI DETECTED ERROR: java_class == null`.
The call UI also displays an Android Telecom audio routing failure before media becomes available.

- The current `main` Gradle build uses one explicit Android WebRTC SDK: `io.github.webrtc-sdk:android:150.7871.01`.
- The R8 configuration already keeps all `org.webrtc.**` Java classes. This does not prove the user installed an APK made from that source.
- The new CI script inspects actual minified APK DEX descriptors and ARM64 `libjingle_peerconnection_so.so`. GitHub runner must execute this check; a source review alone is insufficient.
- Actual SDP delivery, Firebase FCM tokens, Supabase call RPC/RLS, STUN/TURN network traversal, and Android Telecom endpoint availability can only be confirmed by end-to-end tests and server logs.

## Two-phone acceptance (mandatory before release)

Use two distinct real BLINK accounts, both explicitly consenting to receive a test call.
Do not test owner aliases as two independent accounts.

1. On **Phone A (Android 15 Redmi)** and **Phone B (another Android phone)** install the exact same signed candidate build; verify APK source commit, version and signing certificate. Preserve app data.
2. Allow microphone, camera (for video), and notifications; confirm both devices have valid, registered FCM tokens for their own signed-in accounts. Confirm `send-call-notification` returns delivered>0 for a test invite.
3. A calls B in foreground; confirm B sees the incoming-call UI, accepts, bidirectional audio works, and both users can hang up; check `calls` and `call_signals` server state.
4. Repeat with B screen locked and BLINK in background; answer from notification; verify FCM and Telecom, permission flow, speaker, earpiece and headset routes.
5. Make and receive VIDEO calls; confirm camera/microphone toggles, bidirectional tracks, hangup, camera cleanup and no media process crash.
6. Repeat on mobile data vs Wi-Fi, and on **different cellular networks**. Check TURN credentials/relay candidate selection if ICE cannot connect; STUN-only is insufficient for some carrier NAT combinations.
7. Disable connectivity on A temporarily during SDP transfer: confirm 3 bounded retries and clear user-visible failure instead of an endless "Calling…" state. Restore and retry a new call; no phantom active calls.
8. Never answer on B: server should expire an unanswered call, and caller UI should no longer remain stuck past 75 seconds if reconciliation fails. Check missed-call records.
9. Tap speaker before media is ready and during a call: pending preference should apply after initialization, and the UI should never claim a route changed if Android rejected it.
10. Collect sanitized `adb logcat` for `BlinkCall`, `BlinkTelecom`, `WebRtcCallClient`, `BlinkFCM`, `AndroidRuntime` and `libjingle_peerconnection_so` without exposing auth tokens or private account details.

## Gates before promoting Testlab to main

- Android unit/lint/assemble + instrumentation smoke + minified release smoke on Android 15 emulator PASS.
- JNI Java class + ARM64 native library checks PASS **on the candidate actual APK**.
- Windows build/parity checks PASS or an approved and documented Android-only exception.
- No new failed Supabase/Edge Function tests; test existing hosted call RPC, RLS and signaling end-to-end separately.
- Two real-device foreground/background voice/video trials PASS. A single emulator or a green build is not sufficient.
- Testlab merge, main PR review, and exact production release approval per `AGENTS.md`, followed by signed-APK publication and installation on a real phone.

## Current hold

Changes are experimental in the `Testlab-call-android15-connect-20261021` candidate.
The long-lived `Testlab` and `main` histories are diverged; do not force or fast-forward Testlab to discard its existing changes. Reconcile histories through reviewed PRs and independent gates.
**Not approved for main/published APK until the checks and device tests actually pass.**

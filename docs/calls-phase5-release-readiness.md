# BLINK Calling — Phase 5 release-readiness checklist

**Current release policy:** keep all call fixes in `Testlab` until both the automated checks **and** two-device evidence pass. A successful debug compile does **not** prove that production push, TURN, audio, video, or release signing work. Do not automatically merge the full Testlab branch into `main`; the branches have a large, unrelated history divergence.

## What Phase 5 implements

- Retry an interrupted call-end/decline on Android WorkManager, with a connected-network constraint, capped exponential backoff, account-ID isolation and explicit avoidance of terminal-call mutations.
- Reject permanent authorization errors rather than repeatedly retrying forbidden actions.
- Perform foreground-service startup defensively so an Android start restriction does not crash an already-active call.
- Stop a foreground call service only if its published call identifier matches the current call, never because another call screen closes.
- Avoid sending success-like call state pushes for a request that failed to persist.
- Automated tests cover the retry/backoff policy and required action-state transitions.

## Automated release gates

Run on the **exact immutable commit SHA** intended for release:

1. `BLINK calling quality gate`: Android calling Kotlin compiles, all `com.example.call.*` unit tests pass, both Supabase Edge Functions pass Deno checks.
2. `Android release smoke test`: completion and successful result on the same SHA. Do not infer release-signing success from the debug compilation task.
3. `Supabase safety gate`: successful result for matching SHA. A safety-gate result does not prove the Edge Functions have been deployed.
4. `BLINK LIFE native Android APK preview`: successful result if release workflow requires a compatible complete app build.
5. Check actual APK versionCode/versionName, signing identity, Android installation and upgrade from previous version, runtime permissions, startup and main navigation.
6. Do not proceed if any required run failed, cancelled, is pending, or ran against an older SHA.

## TURN / backend blockers

- [ ] A reachable TURN server supports UDP and TCP/TLS, and its certificate is valid.
- [ ] `BLINK_TURN_SHARED_SECRET` and `BLINK_TURN_URLS` are configured in the **function-only** server environment and match coturn, or the existing `call-turn-credentials` service is validated for a staged migration.
- [ ] The `get-call-ice-config` function is deployed securely only after staging verification; never ship master secrets or user tokens in the app.
- [ ] A participant can fetch scoped, expiring ICE credentials; nonparticipants/terminated calls are rejected.
- [ ] ICE actually uses a `relay` candidate on at least one restricted/mobile-carrier test, not merely a `stun` candidate.
- [ ] Push service account is server-side only; multiple-device FCM token registration, expiry and cleanup work.
- [ ] The existing `call-turn-credentials` fallback is removed after migration and confirmed server coverage, not before.

## Two-phone Android acceptance matrix

Use **two physical Android phones** with two real test accounts. On each network permutation, test both audio and video, including who initiates the call. Collect timestamps, Android version and device model; never paste tokens, phone numbers, private identifiers, SDP or TURN credentials in public logs.

| Scenario | Expected result |
| --- | --- |
| Both on Wi-Fi, foreground | Rings, answer works, both directions audible, both video streams visible |
| Wi-Fi ↔ mobile data | Complete call through actual NAT, no infinite connecting |
| Mobile ↔ mobile on different carriers | Answer and active call work through relay if direct peer connection fails |
| Receiver background / screen locked | Appropriate notification, Answer/Decline actions work without opening unrelated app |
| Receiver app process not running | FCM arrival recovers the call; expired invites are never shown |
| Multiple devices signed into receiver | Answer on one dismisses ringing on the others |
| Caller cancels before receiver sees invite | No ghost incoming ring |
| Receiver declines or does not answer | Durable declined/missed status, single history event |
| Busy user / simultaneous call attempts | No overlapping media or incorrect call opened by singleTask activity |
| Duplicate and out-of-order push | Exactly one ring, no resumed ring after an answered/cancelled update |
| Toggle microphone off/on | Remote hears silence while muted and audio resumes on unmute |
| Toggle camera off/on; flip | Remote sees video resume, labels and mirror orientation are accurate |
| Speaker, wired earphones, Bluetooth | Sound routes correctly and keeps privacy when external audio is connected |
| Incoming while music or other audio plays | Call obtains focus and returns it on hangup |
| 2G/3G/poor network and Wi-Fi handover | Adaptive camera quality, bounded reconnection, recover or terminate with explanation |
| Airplane mode while ending | Local media stops at once; server reconciles when device is connected again |
| Camera/microphone permission denied | Clear explanation, no crash or silent recording |
| Android 12, 13, 14, 15, and 16 where available | Permissions, notification channels and foreground service behavior match OS rules |
| Redmi/Xiaomi battery restrictions | Background incoming calls are handled or documented with a safe fallback |
| Accessibility / big font / small phone | Buttons visible and tappable, TalkBack action labels correct |
| Call >10 minutes then reconnect | TURN credential refresh works; timer does not reset incorrectly |
| Exit call / app restart | Microphone, camera, ringtone and foreground service stop; history reflects actual status |

## Signoff criteria and decision

**BLOCK release** if any of these is true:
- A test call cannot be completed between different carriers or no relay connectivity evidence exists.
- Android microphone/camera remains active after hangup, a call can be answered on the wrong account, or duplicate rings occur.
- Any required CI check fails or is pending, or the signed APK has not been physically installed and tested.
- New TURN/FCM backend credentials or migrations are unverified.
- `Testlab` cannot be reconciled cleanly with `main`, or unrelated features would be merged accidentally.

**Only after signoff:** isolate the calling changes in an appropriately based release PR; run its full checks; build a signed, versioned APK; install-test the exact artifact; promote/publish through the existing authorized release pipeline. Keep a rollback artifact and a way to disable incoming-call promotions if a release unexpectedly fails.

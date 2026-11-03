# BLINK voice/video calls — production acceptance sheet

**Applies to draft PR #371, not the entire Testlab branch.** Record the exact APK's commit SHA, version, signing identity, test date, Android OS versions, device models, and network/carrier type. Do not include usernames, phone numbers, FCM tokens, SDP, ICE credentials or account secrets in shared test evidence.

## Preflight

- [ ] The calling-main-integration, Android quality, release-smoke, Android runtime, Windows/parity, Supabase-safety and secret leak gates pass for the **same PR head SHA**.
- [ ] Install an internally signed test APK on two physically separate phones, each using a separate test account and the expected same backend environment.
- [ ] Confirm a working authenticated TURN service is deployed. STUN-only or unit-test success is not evidence that cross-carrier calls will work.
- [ ] Verify the app actually has microphone/camera permission and correct call notifications on both devices.

## Test cases

| ID | Condition | Expected outcome | Result / evidence |
| --- | --- | --- | --- |
| C01 | Both phones on Wi-Fi, voice both directions | Audible two-way voice and working End | |
| C02 | Wi-Fi ↔ mobile, voice and video | Call connects with correct peer identity | |
| C03 | Mobile ↔ mobile, different carriers | Call connects through authenticated TURN relay if direct route fails | |
| C04 | Receiver screen locked | Android CallStyle notifications Answer/Decline operate | |
| C05 | Receiver app minimized/terminated | Incoming invite works without opening unrelated screen, and expires cleanly | |
| C06 | Receiver on two logged-in devices | Answer on one dismisses all other incoming-call prompts | |
| C07 | Duplicate/out-of-order FCM calls | No repeat rings for answered/declined/expired calls | |
| C08 | Incoming decline then reconnect network | Server history is Declined; remote device is informed once | |
| C09 | Hang up with airplane mode; then reconnect | Local mic/camera stops immediately; background reconciliation eventually updates server or safely times out | |
| C10 | Sign out of account A; sign in B while A has retry | B's session never modifies A's call | |
| C11 | Call answered elsewhere or already terminal when worker runs | No incorrect status mutation, duplicate push or ghost notification | |
| C12 | Active call receives notification for another call | Never silently answers/ends the wrong call | |
| C13 | Switch Wi-Fi/mobile mid-call | Bounded reconnect or clear failure, no endless progress | |
| C14 | Toggle mute, speaker, Bluetooth/wired headset | Audio routes correctly; remote mic is silent while muted | |
| C15 | Front/back camera, turn camera off/on | Correct camera direction and no stuck camera indicator | |
| C16 | Kill app or return after call | Mic/camera and foreground call notification clean up; history matches server | |
| C17 | Disable microphone/camera/notification permission | Safe error handling; no crashes or hidden recording | |
| C18 | Large-font accessibility and compact phone | Controls remain visible and usable | |
| C19 | Android 12–16 variants including MIUI/HyperOS | Incoming/background calls comply with OS rules and OEM battery constraints | |
| C20 | Sign an upgrade using production signing key and test old-to-new install | Upgrade does not fail with invalid package or signing mismatch | |

## Release blockers

No production merge/publish if TURN relay cannot be validated, physical-phone results are missing, the APK cannot be updated from the current production signing key, app media remains active after hangup, cross-account safety fails, or any required CI check fails. CI release smoke may sign with a **disposable test key**, which must not be confused with a production-signed APK.

## PR scope and parity

This PR isolates native Android call-end retry, notification actions and Telecom lifecycle safety. Production `main` already has newer Telecom/audio UI code than `Testlab`; do not copy the full `Testlab` calling directory. The Windows equivalent uses authenticated desktop call RPCs and its own native notifications. Android WorkManager and Telecom have no Windows API counterparts. Windows does not receive persisted offline hangup retries in this PR.

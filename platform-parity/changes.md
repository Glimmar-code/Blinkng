# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-09-07 | Windows desktop foundation | Existing production client | Desktop shell + EXE/MSI build foundation | Initial migration foundation; existing Android routes still need incremental porting |

## Platform exception policy

A platform exception is allowed only when the behavior is genuinely tied to one operating system and cannot sensibly exist on the other platform. It must not be used to avoid implementing normal feature parity.

Every exception must include the exact marker below so CI can identify it:

```text
PARITY-EXCEPTION: <stable-feature-id>
Date:
Feature:
Android behavior:
Why this is genuinely Android-only:
Windows equivalent or reason no equivalent is needed:
Backend/shared behavior preserved:
Tests/validation:
Owner/reviewer note:
```

Examples of potentially valid platform-specific adapters include Android Activity/Intent behavior, CameraX integration, WorkManager scheduling, Firebase Messaging delivery, Android permissions, Windows system-tray integration, Windows native menus, desktop file dialogs, and installer packaging.

Business rules, accounts, permissions, posts, reels, messages, coins, verification, marketplace behavior, Connect behavior, ranking, moderation, notifications semantics, and admin rules are not platform exceptions and must remain equivalent across Android and Windows.

## Approved platform-specific changes

PARITY-EXCEPTION: android-legacy-foreground-call-ringtone-loop
Date: 2026-09-08
Feature: Foreground incoming-call ringtone looping on Android 7–8
Android behavior: Replays the selected Android Ringtone after each completed cycle on API 24–27 so a foreground incoming call continues ringing until Answer, Decline, replacement call, timeout, or activity destruction.
Why this is genuinely Android-only: This compensates for the Android platform Ringtone API, where native Ringtone.isLooping is unavailable before Android 9 (API 28).
Windows equivalent or reason no equivalent is needed: No equivalent is required because the Windows client does not use Android Activity, Ringtone, or API-level compatibility behavior. Windows call ringing remains implemented through its own desktop audio adapter when that route is active.
Backend/shared behavior preserved: Call signaling, call status transitions, timeout semantics, missed-call persistence, notification deduplication, and shared backend behavior are unchanged.
Tests/validation: Require the Android quality gate, Windows parity gate, and Windows desktop build to pass before merging.
Owner/reviewer note: Keep this exception limited to Android legacy ringtone playback; any shared call-semantic change still requires Windows parity.


PARITY-EXCEPTION: android-touch-chat-swipe-navigation
Date: 2026-09-08
Feature: Touch gestures for direct chat navigation and reply
Android behavior: Swipe left on any incoming or outgoing message bubble to reply. Swipe right across the open chat to return directly to Messages. The former interactive 70/30 inbox reveal is removed.
Why this is genuinely Android-only: This change is specifically a touchscreen gesture adapter implemented with Jetpack Compose pointer input.
Windows equivalent or reason no equivalent is needed: Windows retains direct full-screen chat/inbox navigation through desktop pointer/keyboard controls; no touch-drag pane is required. Message reply semantics remain the same shared product behavior.
Backend/shared behavior preserved: Message storage, reply state semantics, conversation identity, delivery/read receipts, notification routing, and backend APIs are unchanged.
Tests/validation: Android compile/unit/lint/APK quality gate plus Windows parity/build gate must pass before merge.
Owner/reviewer note: Only the Android touch interaction is excepted; any change to message/reply business semantics still requires Windows parity.

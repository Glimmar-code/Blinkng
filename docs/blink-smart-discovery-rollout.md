# BLINK Smart Discovery & Announcement Delivery — staged rollout

## Phase 1: first-login guide (implemented on Testlab; awaiting CI/device checks)
- Shared, deterministic tutorial order: Welcome → Reels → Shake to Report → Ranking → Coins → Connect → Market → Games.
- Android: dark monochrome guide dialog over the authenticated idle Home feed only. Windows: matching native desktop dialog.
- First prompt after 2 seconds of an eligible session; later prompts become eligible at 15s, 30s, 1min and beyond. They are **not forced** at those times.
- Never show more than three tips per active session; keep at least 12 seconds between prompts.
- Skip the introduction, dismiss individual tips, and remember completed steps **per account on this device**.
- Never interrupt authentication, feed loading, typing/subscreens, chat, Reels, or the game.
- This phase does not issue coin rewards, force downloads, modify server data or send push messages.
- Phase 1 progress intentionally is NOT yet synchronized between devices; remote-backed progress needs an authenticated database migration and staging verification.

## Planned phase 2: announcements and update manifest (not yet implemented)
- Versioned signed/canonical HTTPS release manifest and website-hosted APK update prompt.
- Compare integer versionCode, not versionName strings; minimum supported version set server-side.
- Mandatory updates must have a verified available APK and non-dismissable blocking only where needed for security/compatibility; a network failure must not permanently lock out offline users.
- Use allowed origin for download, verify APK SHA-256 and package signing identity, always defer installation approval to Android.
- Announcement config in Supabase with publication dates, expiry, priorities and safe server-authorized admin writes. All remote payloads are untrusted: no arbitrary links/intents/scripts.

## Planned phase 3: notifications and admin (not yet implemented)
- Delivery modes: push, in-app popup, both.
- Reuse existing Android FCM token registration, NotificationSyncWorker and notification preferences rather than replacing them.
- Store canonical announcements server-side so offline clients catch up on reconnect; only previously stored local content can show while offline.
- In-app priority queue: critical security updates > update available > release notes > welcome > feature tips.
- Deduplicate by announcement ID + account, never re-show a dismissed regular notice every login; rate limit regular pushes.
- Admin authorization enforced server-side with audit logging, target audience restrictions and default notification-consent handling.
- Notification center history: All | Updates | Announcements; preserve user preference/quiet-hours and accessibility.

## Verification matrix before main
- Android: :app:testDebugUnitTest, :app:lintDebug, :app:assembleDebug, Android 13–15 device UI testing.
- Desktop: Windows desktop build and UI check, shared-policy parity and account switching.
- Manual: welcome twice on same account; switch accounts; skip intro; open chat before eligible time; rotate/restore; dark/light and accessibility; basic offline behavior.
- Backend stages: no production Supabase migration or paid staging without explicit authorization.
- Check that Testlab is heavily diverged from main; cherry-pick/port verified commits after reviewing conflicts, never merge all stale Testlab history into main.

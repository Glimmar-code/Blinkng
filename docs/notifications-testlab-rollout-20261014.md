# BLINK Notifications: Testlab roll-out

The October 14 upgrade is incremental. Do not promote to `main` without verified quality gates and device testing.

## Staged improvements
- Social recovery paginates unread notifications (500 rows/page; up to 20 pages) and only advances the cursor on a completed scan.
- Timestamp + ID cursor prevents simultaneous events from being silently skipped.
- Recovery retries produce a failure status rather than a false success.
- Offline unread-message recovery respects timed conversation mutes.
- Wide, stable conversation notification ID namespace reduces collision risk.
- FCM packets with no recipient identity are rejected to avoid cross-account leakage.
- Notification page supports reels, stories, and rewards filters, newest/oldest sorting, and neutral black/white accents.

## Verification checklist
- Android unit tests, lint, instrumentation compile, debug APK build pass.
- Android runtime smoke, especially Android 15 / Redmi 15C, passes.
- Firebase messages for chats, calls, social events all contain `recipient_id`.
- Foreground, background, force-stopped, logout/login, reinstall, and permission-disabled behaviors checked.
- Social recovery confirmed with >1,000 unread rows, equal timestamps, and flaky connectivity.
- Multiple conversations retain independent phone notifications; direct replies work.
- Muted messages never produce recovery banners.
- Notifications open correct post/reel/profile/market destination.
- Dark and light modes remain readable; notification sorting and filters work.
- Windows desktop inbox filters, sorting, offline fallback and safe account switch checked; web/Supabase preferences verified.

## Not yet implemented
Archive/undo, configurable digests and intelligent prioritization, rich
conversation history, advanced moderation and admin analytics, and
complete cross-device read-state sync. These need additional backend and UI work.

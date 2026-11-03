# BLINK content-view integration contract

This Testlab change should retain these invariants:

- Android `recordExposure` immediately sends a UUID to `record_content_view`.
- The display window stays frozen at its first baseline for **120 seconds** (not 30).
- Repeated qualified visibility is recorded again, including scrolling away/back.
- The same server function handles ordinary posts, Reels and boosted content.
- The same post ID and viewer share the existing 100-impression ceiling.
- Normal / Blue / Gold weights remain 1 / 3 / 6.
- Revoked or expired verification is weighted as normal (1).
- The server remains authoritative for counts; the client cannot set multipliers.

Surfaces connected to tracking: Home Feed (including boosted posts),
profile cards, Search results (including trending posts), opened/shared
post details, full-screen ordinary & boosted Reels, and inline Reel previews.

Intentional exception: decorative Search backdrops must not create views.

Note: the 120-second window is **presentation-only**, not a 120-second
view qualification or a backend write delay. No 60-second dwell timer is
introduced by this change.

Validation: run Android unit tests including ContentViewDisplayStateTest and
PostImpressionTrackerTest, the Android quality gate, and review/validate the
Supabase migration in a nonproduction database before applying to production.

# BLINK iOS Phase 3 — Native home and reels

Built on Phase 2 (authentication) and proposed via a branch-to-branch pull request; **Testlab and production main remain untouched until required checks pass**.

## Implemented in source

- Real `get_ranked_feed_page` RPC for Home ("posts") and Reels ("reels") with snapshot/offset pagination.
- Real `get_following_feed` RPC for the first Following page. Cursor pagination is **not** yet implemented.
- User profiles fetched from existing `profiles` rows. Names, badges and avatars are only displayed when returned by Supabase.
- Read-only post images, HTTPS media, paused-by-default native video playback, counts from Supabase, and canonical share links.
- Real `like_post` and `unlike_post` RPCs, `get_post_comments`, and `create_comment`.
- User access JWT is used for every authenticated request; pre-expiry refresh and one 401 retry use the existing Phase 2 AuthStore/Keychain rotation.
- Empty states, loading, manual refresh and error handling; never seed users, posts, hearts or comments.
- Stubbed XCTest for backend routes, JWT headers, ranked/following feed shapes, profiles, comments and unauthorized access.
- One failing Phase 2 test helper has been repaired: iOS URLProtocol delivers JSON body in `httpBodyStream` on the runner.

## Known gaps — do not ship as feature complete

- Existing-like status is not yet read; the heart only reflects likes made during this local session, not a verified prior state. Counts refresh from backend.
- No global feed autoplay, qualified-view tracking, repost/bookmark, composer/media upload, reporting/blocking, author profile navigation or profile-follow buttons on iOS.
- Following feed cursor handling and realtime updates remain to be ported.
- Actual live Supabase account/RLS and cross-platform media-device tests have **not** been performed. Tests use mocked URLProtocol fixtures and do not assert live database deployment.
- Full user reporting and moderation must be implemented and tested before an App Store submission.
- Use the same existing server and permissions. No database migrations, new Supabase projects or production secrets.

## Checks
iOS Simulator compile and XCTest on macOS GitHub Actions, then provider and manual device testing before any promotion. Android/Windows remain untouched.

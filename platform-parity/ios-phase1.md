# BLINK iOS parity tracking

## Phase 1 — scaffold on Testlab only (19 October 2026)

The Android and Windows production routes are untouched. A new SwiftUI iOS shell adds navigation routes in exactly this order:

1. Home
2. Reels
3. Market
4. Connect
5. Message

Each screen explicitly identifies content as not connected yet. **No seeded/fake posts, users, reels, payments or chat messages.** The only enabled outgoing action opens the existing BLINK website. Supabase remains the one backend and no new database is deployed.

## Parity still required before iOS launch

Auth and session handling; verified profile and account controls; notifications; feeds/reels; conversations and calls; seller/marketplace policies; coin/subscription and payment rules; moderation and safety; deep linking; search, ranks, admin role restrictions and accessibility. Do not enable unimplemented features with visual-only buttons. Recheck Apple billing and platform permissions separately from Android.

The CI builds without Apple signing; TestFlight and App Store require developer credentials, a signing team and human account verification.

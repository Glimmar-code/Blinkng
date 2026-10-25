# iOS Phase 3 parity check

- Home: `get_ranked_feed_page` posts, real profile rows, content/media, like/comment RPCs, share.
- Reels: `get_ranked_feed_page` reels; paused video playback.
- Following: first `get_following_feed` page only; cursor still missing.
- Existing Android/Windows/web business rules and Supabase RLS must remain authoritative.
- No user-facing Android/Windows behavior was modified in this feature.
- Remaining: read verified like state, multi-page following, author profiles, follow, compose, stories, qualified views, report/block, ad visibility rules and accessibility/media testing.
- Not released and not fully parity compliant until remaining capabilities are added and tested.

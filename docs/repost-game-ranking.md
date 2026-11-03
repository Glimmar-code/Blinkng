# Repost distribution and game ranking

The Android client now supports canonical post reposts through Supabase. Reposts keep engagement on the original post, expose repost attribution and count in the feed, and use the repost RPC for authenticated toggle state.

The Game tab uses a separate game ranking feed backed by `game_rankings`, while the main app leaderboard remains independent.

This document also serves as the verification-trigger commit for the Android quality gate after the source changes were committed.

# BLINK iOS message parity: Phase 5

**New iOS:** server-backed inbox summaries and unread counts; DM/group threads; 50-message pages; explicit direct/group sending; direct/group read updates; native seller DM compose from Market.

**Same backend:** existing Supabase user IDs, authenticated RPCs and RLS. No changes to Android, Windows, web, schema, payments or stored conversations.

**Parity outstanding:** typing/presence UI, message push notifications, native voice/video calls, media and voice messages, read/delivery checkmarks, report/block, message editing/deletion and offline caches. Do not claim those features exist on iOS.

**Release:** Staged feature branch only; never publish until stacked PR CI, server permissions, moderation and device testing are complete.

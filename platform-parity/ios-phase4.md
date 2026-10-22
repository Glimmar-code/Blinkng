# BLINK iOS Phase 4 feature parity

- Market: displays server-ranked existing listings with search and seller contact handoff to BLINK web.
- Connect: lists server-ranked opportunities by `all`, `roommate`, `mentor`, `reading_mate`, `housing`, sends explicit backend-authenticated connection requests.
- Uses existing Supabase JWT, user permissions, moderation visibility and server-owned seller status. No client trust overrides.
- This is the first 40 server-ranked items/opportunities; continuation cursors are still required for full parity.
- Does **not** modify Android/Windows features, shared database migrations or live payment settings.
- Outstanding parity: native seller registration/listing creation; offers and disputes; seller ratings, messaging, requests inbox, advanced Connect features and gaming; manual two-account live provider integration.
- Maintain Testlab-only until verified; no premature App Store publication.

# BLINK native iOS — Phase 5 messaging

**Status:** Development candidate. Built on Phase 4's stacked feature branch; no Testlab, production Supabase or `main` changes.

## Added
- Native Messages inbox using the existing BLINK `get_conversation_summaries_page` RPC, with names, avatars, latest message, server unread counts and manual pull-to-refresh.
- Existing group conversation titles fetched with the user JWT from `conversations`, subject to the existing backend RLS rules.
- DMs and group conversation history with `get_conversation_messages_page`, server-returned timestamps, deleted-message redaction and older-page retrieval. No fabricated history or local plaintext persistence.
- Send a direct message using `send_message_v2`, or a group message using `send_group_message` only for a known group. Drafts clear only after acknowledged success.
- Request read updates only for threads the user opens: `mark_conversation_read` for direct messages; user-and-conversation scoped updates to `conversation_participants.last_read_at` for groups. Server/RLS determines if an update is permitted.
- Authenticated Supabase requests through the existing Phase 2 Keychain user JWT, with token refresh/retry. No service-role secrets, no new backend schema, and no calls to production during automated tests.
- In BLINK Market, selecting **Message seller** switches to the native Message tab and opens a composer prefilled with the server-provided seller username instead of redirecting to a web login.
- Native active-thread polling while visible (20 seconds) and manual refresh; no push notifications or claims of instantaneous delivery.
- XCTest contract tests exercise messaging RPC paths/headers, the expected JSON shape, history ordering, 401 rejection, read receipt scoping, safe input and server acknowledgements.

## Not yet implemented or not verified
- **Native voice/video calls**, media uploads/voice notes, push notifications, typing indicators and true real-time WebSocket subscriptions.
- Thread deletion/editing, blocking/reporting UI, request inbox, advanced group management, emoji reactions and persistent offline drafts.
- Read state from other users is not surfaced as a check mark because the existing RPC contract has not been verified for delivery/read timestamps.
- The 50-row inbox limit has no cursor navigation yet; old message pages use `p_before` and `p_before_id`.
- Real device test with **two consenting test accounts**, blocked/restricted user, seller account, a group chat, app resume, a revoked JWT, long Unicode text and cross-platform sync is REQUIRED.
- Moderation, minors' privacy safeguards and abuse-reporting must be tested before an App Store release.
- Existing server functions were checked for existence and arguments but end-to-end behavior has **not** been verified. Do not send test messages using real user accounts without permission.

## Backend API contracts checked (read-only metadata)
- `get_conversation_summaries_page(p_limit,p_before,p_before_id)`
- `get_conversation_messages_page(p_conversation_id,p_limit,p_before,p_before_id)`
- `send_message_v2(p_receiver_username,p_content)`
- `send_group_message(p_conversation_id,p_content)`
- `mark_conversation_read(p_partner_username)`
- `conversation_participants` limited PATCH, subject to RLS.

## CI and promotion
Use `iOS foundation quality gate` to generate Xcode project, compile simulator app and run XCTest; also run Android/Windows gates. Only after the full stacked iOS dependency chain (#416, #417, #419, #423) is safely reviewed should this PR be retargeted into Testlab, then selectively promoted to `main` after device/security testing.

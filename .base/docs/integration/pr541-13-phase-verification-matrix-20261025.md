# BLINK #541: 13-phase / 10-stage evidence register

**Date:** 2026-10-25. **Status:** partial implementation, draft PR; **not a release authorization**.
**Baseline:** main `437cb27da5fae38311c1816c575c25f0bdd49b36`; legacy Testlab `55da8858af259327b7affe8d97ee1577b3bbc26d`. Main-descended branch `testlab/consolidated-phase1-5-current-main-20261025`. Never fast-forward old Testlab onto current main without reconciling diffs.

| Phase | Feature | Committed evidence | Release-blocking work still required |
| --- | --- | --- | --- |
| 1 | Full audit | PR #541 and issue #528, this status register, Supabase read-only inventory | All affected screens/routes, memory/network baseline, screenshot reference comparisons, PR conflict resolution |
| 2 | Responsive + theme | `BlinkMainAdaptiveVisualTest.kt`; `BlinkAdaptiveLayoutPolicy.kt`; Items/theme fixes | Capture + compare all screens at 320/360/390/412dp, large font, notches, keyboard, light/dark and Android releases |
| 3 | Navigation + Back | `BlinkNavigationHistoryPolicy.kt`; Android ViewModel/main route stack, Profile→DM fix, Home reference contract | Search/drafts/scroll process death, nested sheets, Android predictive gesture Back, Windows Escape history |
| 4 | Feed design | Android `PostCard.kt`, `PremiumFeedScreen.kt`; Windows `ContentScreens.kt`, four-line previews | Full-post taps across all non-action areas, actual screenshot and interaction parity on devices |
| 5 | Fresh feed | `SmartRefreshPolicy.kt`, `BlinkRefreshPolicy.kt`, associated tests | Return dwell windows, real offline cache, New Posts, server qualified-view and unseen ordering E2E |
| 6 | Post tips / Marketplace separation | `BlinkPostGrowthTips.kt`, Android and Windows composers, draft category + sponsored SQL | Production migration NOT APPLIED, Market redirect, server ownership/payments and listings tests |
| 7 | Universal username search | `BlinkUserSuggestionPolicy.kt`, `BlinkMentionTokenPolicy.kt`, Android comments/post/new-message, Windows post/search; pagination | Reply, invite and secondary picker surfaces, RLS/privacy/block integration and large-list QA |
| 8 | Group chat + Communities | Android `BlinkCommunitiesPanel`, `BlinkCommunityRepository`, `BlinkGroupManagementPanel`, `ProfessionalCenterActivity`, `ChatRepository`; Windows `DesktopCommunitiesScreen`, `DesktopGroupManagementDialog`, `DesktopNewGroupDialog`; shared policies and staged SQL | Draft backend RLS/RPCs still NOT deployed, group invitation consent and owner-transfer workflow, media/receipts, offline delivery, comprehensive multi-account E2E, full moderator UI, signed release unavailable |
| 9 | Swipe to reply | `BlinkReplySwipePhysics.kt`, Android `PremiumMessagesScreen.kt` | Gesture/responsive/reduced motion checks with vertical scroll, Android Back interference, optional haptic |
| 10 | Admin switch | `BlinkReferenceMenuScreen.kt`, `AdminControlCenterActivity.kt`, Windows Menu/admin route | Secure return and capability failure runtime, cross-account controls, backend permission proofs |
| 11 | Auth image | Android `BlinkAuthReferenceLanding.kt` now reuses already-approved larger `blink_auth_reference_login_photo.webp`, draws a crisp Compose logo wordmark | Original high-resolution photograph not available, exact screenshot/cropping verification at scale and Windows visual comparison remain |
| 12 | Performance | `BlinkPerformancePolicy.kt`, Android `HighRefreshRateController.kt`; Automatic/Balanced/Power Saving | Startup/cold/warm/scroll/media/memory/network metrics, low-end devices, Windows power behavior |
| 13 | Verification & release | GitHub CI for PR #541; staged disposable PGlite contract suite; PR remains draft | Final head SHA all CI, instrumented runtime, backend isolated Auth/RLS, parity sign-off, Testlab reconciliation, exact owner release approval, main merge and signed APK publication |

## Production Supabase audit (read-only)
- Live `create_group_conversation(p_title,p_member_ids)` and `send_group_message` exist.
- `blink_communities`, `blink_community_members`, and `blink_my_group_conversations` do **not** exist on hosted production.
- Proposed `blink_feed_market_category_guard` trigger is **not installed** on hosted production.
- SQL proposals live in `docs/integration/*-staging-20261025.sql`, NOT under migrations and NOT executed against hosted production. In addition to Marketplace/Community drafts, `group-membership-security-staging-20261025.sql` stages removal of unsafe raw group self-invites, participant admin self-edits, departed-member rejoining, and inactive conversation/message RLS. Its synthetic CI regression attempts group self-invite, admin escalation, rejoin, and read/send after leaving. These tests are **not** a substitute for full real Supabase staging integration, complete permission audit or official production migration.
- Current RLS requires critical group account-isolation review: membership and message permission predicates must reject departed people, group member insert/role mutation must not permit self-invitation/elevated `is_admin`, invite privacy must hold, and management RPCs must enforce roles. Until proven, groups/Communities are **not security-releasable**.
- Hosted Supabase had zero preview branches in the latest read-only check. A paid staging environment must not be created or billed without separate authorization. No production-first tests.
- The staged Communities SQL has a fixed `$;` function terminator, pending requests, controlled owner/moderator approval, read restrictions for private/banned members, and member-roster RPCs. Group SQL has active-member-only send, invite restrictions across blocked participants, server-only group membership changes and owner-leave guard. None is yet a verified migration on the full hosted production schema.
- Both platform UIs now include creation, invitations, group member lists, community join approvals and owner moderation, but **not end-to-end verified against two real account sessions**. The presence of UI code is not evidence that its production RPCs exist.



## Additional security remediation staged on 25 October 2026
- **Critical direct-message isolation:** current hosted `cp_insert_own` authorizes inserting one's own ID into an unrelated direct conversation. This can expose other users' DM content. The new STAGING-ONLY `blink_can_self_join_creator_direct` predicate limits raw self-add to one's own direct conversation with fewer than two participants. A disposable-database negative test covers unrelated DM self-invites, and a positive test covers the original creator route. The safer server `get_or_create_direct_conversation` RPC remains the recommended entry point. **Hosted production is NOT patched yet.**
- **Security-definer RPC bypass of RLS:** current hosted `get_my_unread_message_notifications`, `ack_message_delivered`, `ack_pending_message_deliveries` and `get_my_conversation_partners` omit `left_at` checks. Existing RLS changes cannot protect calls to these privileged functions. Their signature-compatible active-member replacements are staged in `docs/integration/departed-member-rpc-hardening-staging-20261025.sql`, with synthetic departed/active-member tests. **Not deployed.** Continue audit of other privileged message/read functions before promotion.
- **SQL validation:** repaired invalid single-dollar PL/pgSQL delimiters in Group/Community drafts; community policies now drop before recreation. The disposable test runner replays Market, Communities, group membership and receipt scripts to detect unsafe non-idempotent DDL. Static inspection confirms paired dollar delimiters; real database runtime and Supabase staging verification are still required.
- No users, production SQL, payment state, production branches, signed APKs or releases were changed. The new staged security edits are **not a substitute** for a real preview migration, two-account Android/Windows testing, approved integration and final head CI.

## Verification evidence and non-passed gates
- Prior SHA `f3c558dfbff17f1dedb8f2f162655f3045e34396`: all 18 applicable GitHub checks passed, one Call Quality skipped; **not evidence for newer code**.
- Later tested SHA `5ab0f1a22f42f14a30f20d7ee59c1f5e003a1f17`: disposable Supabase migration-safety passed after fixing a missing PGlite fixture `anon` role; newer commits require their own verification.
- Record final candidate SHA only after code is frozen, then check Android quality, native APK, Android runtime smoke/release, Windows desktop build/parity, SQL safety, real-local login/Auth security, intro visual, links/domain, signed APK installer, end-to-end cross-platform chat + Market/Paystack. A green status with skipped irrelevant steps is not permission for an untested backend feature.
- Release gate: per `AGENTS.md`, require exact owner comment `BLINK APPROVE <current-PR-head-SHA>` on the production PR after verification. New commits invalidate that approval.

**DO NOT merge, deploy hosted SQL, or publish a new APK while any release blocker remains.**


## Further implementation in PR #541 (25 October 2026)
- Inspected production RPC definitions read-only. `get_conversation_messages_page` and privileged `mark_conversation_read` were still missing active `left_at` checks; added signature-compatible STAGING-ONLY implementations to `departed-member-rpc-hardening-staging-20261025.sql`.
- Added `direct-message-summaries-staging-20261025.sql` by adapting the actual deployed `get_conversation_summaries_page` function. Group threads are excluded from legacy DM results so they cannot be repeated as fake 1:1 chats; real group listing remains a separate active-membership RPC.
- Expanded the disposable PGlite suite for active/departed conversation history, unread message visibility, receipts, direct-message marking, and group-summary separation. These are **committed candidates, not reported as passing runtime checks**. Real staging still required.
- `Testlab` is significantly historically diverged from this main-based PR branch. Do not merge or reset it without safe conflict reconciliation and full cross-platform and production-compatible integration tests. Preserve current `main`, users, production Supabase, working payments and release artifacts.
- Android and Windows group media, invitations with recipient consent, reliable per-member receipts, community cover uploads, exact high-resolution Auth asset and complete visual/performance acceptance remain **unimplemented or unverified**. No deployment, real-device verification, main merge or signed APK release has been claimed.


## 2026-10-25 strict-order security fixes added after matrix review

- Added `direct-message-send-security-staging-20261025.sql` for active-member DM send/create RPCs that don't revive departed conversations. Read-receipt and message-list hardening is separately staged.
- Added `group-notification-fanout-staging-20261025.sql` to correct a hosted trigger that notifies only one group recipient and omits `left_at`.
- Added `group-invitation-consent-staging-20261025.sql` with private pending group invitations; changes BOTH group creation and later invitations to recipient-confirmed membership, new recipient-only list/response RPCs and status/capacity/block checks.
- Android and Windows clients now implement invitation inbox/accept/decline. Fixed Windows `MessagesScreen` so its early inbox return doesn't suppress New Group / Invites dialogs.
- `staged-market-community-contract-tests.mjs` now has tests for direct DM re-entry, blocked DMs, departed group notifications, private invitation visibility, declined/accepted membership, founder-only group creation and staged SQL replay.
- **Verification hold unchanged:** No local PGlite/CI results on final commit yet; no current production-compatible staging, no hosted changes, no live two-account/device tests, no Testlab merge, no owner release approval, no main merge or APK publication. New candidates are not deployable merely because their source was committed.

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
| 8 | Group chat + Communities | Android `ProfessionalCenterActivity.kt`, `BlinkCommunitiesPanel.kt`, `BlinkCommunityRepository.kt`, `ChatRepository.kt`, `BlinkGroupRoute.kt`; Windows `DesktopNewGroupDialog.kt`, `DesktopCommunitiesScreen.kt`, `DesktopSupabaseClient.kt`; staged community + group security SQL | No hosted Community tables, actual two-account role/invite/ban/read/send/leave integration, full message/media delivery, moderation UI, group notifications, complete RLS audit |
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

## Verification evidence and non-passed gates
- Prior SHA `f3c558dfbff17f1dedb8f2f162655f3045e34396`: all 18 applicable GitHub checks passed, one Call Quality skipped; **not evidence for newer code**.
- Later tested SHA `5ab0f1a22f42f14a30f20d7ee59c1f5e003a1f17`: disposable Supabase migration-safety passed after fixing a missing PGlite fixture `anon` role; newer commits require their own verification.
- Record final candidate SHA only after code is frozen, then check Android quality, native APK, Android runtime smoke/release, Windows desktop build/parity, SQL safety, real-local login/Auth security, intro visual, links/domain, signed APK installer, end-to-end cross-platform chat + Market/Paystack. A green status with skipped irrelevant steps is not permission for an untested backend feature.
- Release gate: per `AGENTS.md`, require exact owner comment `BLINK APPROVE <current-PR-head-SHA>` on the production PR after verification. New commits invalidate that approval.

**DO NOT merge, deploy hosted SQL, or publish a new APK while any release blocker remains.**

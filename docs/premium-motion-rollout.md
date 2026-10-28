# BLINK premium motion — Testlab rollout

This work is an incremental, regression-conscious implementation of the 300+ surface-level
opportunities identified in the October 28, 2026 animation audit. **Do not interpret this
document as 300 shipped animations.** Keep a single PR and merge to main only when verified.

## Motion contract

- Reuse existing Compose animation/spring tokens, pager drag, and shared-element behavior.
- Keep the approved Beyond LINK -> BLINK startup untouched; never add blank launch frames.
- Respect Android's reduced-motion setting for decorative motion and loading skeletons.
- Touch input, player playback, call controls, accessibility and the existing navigation stack win over animation.
- No simulated coin/payment/delivery/verification success before authoritative acknowledgement.
- Avoid expensive per-frame calculations, long-running ornamental loops and image predownloads.

## Current implementation in this PR

- Reusable press feedback with the same `MutableInteractionSource` as each click target.
- Reduced-motion static skeletons rather than continuously pulsating placeholders.
- Auth login/signup buttons and shared cards/chips use the consistent press feedback.
- Feed post actions, Market product cards and Leaderboard profile rows reuse the same reduced-motion-safe press behavior without duplicate per-surface animation loops.
- Auth route changes use modest directional transitions; startup/SPLASH stays unanimated.
- Primary navigation and Reels selection states respond consistently, without moving text baselines.
- Pure JVM unit tests cover disabled, reduced-motion, malformed and normal press states.

## Second Testlab implementation increment

- Native horizontal pager remains the sole Feed/Reels/Connect page transition; the duplicate nested scale/slide is removed.
- Chat conversations and native call-control feedback, including reduced-motion-aware call-state fades.
- Signed Channel image viewer fades in without prefetching or altering media access permissions.
- Profile selection indicators and entrance content respect the system motion preference.
- Search results/chips, Connect tabs, channel image actions, notifications and composer tiles have shared feedback.
- Wallet, Verification, Boost, Drops and seller profile interactions gain reduced-motion-safe feedback; decorative wallet animation loops stop when disabled.
- Menu icons, Edit Profile sections, Items tiles, Game study panes, Professional Center navigation and Admin insight panels are covered by the shared motion policy.
- No transaction, view-count, reward, verification, payment, call-signaling, or data contract changes.

## Third Testlab increment

- New Feed posts revealed by the user fade into place with no row-height animation; established scroll keys, saved position and qualified view counting are unchanged.
- Reel action counts and caption expansion obey reduced motion without remounting the video player.
- Existing picture and story viewers now avoid decorative transitions when system motion is disabled.
- Chat delivery/read glyphs, confirmed Channel reactions and Market order status changes transition from real backend responses only.
- Profile avatar reveal and product gallery selection gain small motion.
- Connect match stages, request statuses, choices and entrance animations respect reduced motion.
- Verification tier choice, Boost steps and Drops steps use short, reduced-motion-safe transitions.
- Android instrumented regression test import errors were corrected (previously failed before device testing).

## Follow-up surface implementation queue (not yet shipped)

1. Navigation: full-screen back gestures, scroll-position conservation, overlay layering.
2. Home: feed inserts, likes, saves, comment modal, post-to-profile and image zoom.
3. Reels: shared video container, seek/hold gestures, caption and comment overlays.
4. Composer: media thumbnails, poll fields, drafts, scheduling, upload verification.
5. Search: trending/results, filters, voice/visual search, result-to-detail.
6. Profile: avatar/cover, tabs, progress, photo editor and follower grids.
7. Connect: directory cards, Smart Match, requests, matching result confirmation.
8. Messages/calls: row and bubble placement, receipts, reply swipes, media, voice and call HUD.
9. Channels: channel open/close, media, reactions, polls and notifications.
10. Market: listings, galleries, seller forms, order/offer and buyer protection states.
11. Wallet/Verification/Boost/Drops/Store: transaction-safe status, targets, progress and receipts.
12. Games/BLINK LIFE: quiz transitions and lightweight optional game asset delivery.
13. Activity/notifications: row states, read filters and targets.
14. Settings/admin/creator/studio/items: sheet, toggle, search, stats and task feedback.

## Release gate

- `:app:compileDebugKotlin` and `:app:testDebugUnitTest`.
- Navigation instrumented tests for pager, back swipe and route restoration.
- Manual smoke: cold launch, login/signup, five tabs, Reel pause/scrub, chat keyboard,
  media viewer, Market, Wallet and logout.
- Verify reduced motion, font scale >= 1.5, dark/light, low network, no new blank frames.
- On a Redmi 15C / Android 15 or equivalent performance profile, review frame timing.
- Testlab first. Merge only after all required checks are verified green.

This file tracks remaining work rather than claiming it is complete.

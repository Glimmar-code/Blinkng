# BLINK completion: Sections 2, 3 and 4 (BLINK LIFE excluded)

**Scope authorized by owner:** Complete the unfinished functionality and
verification listed under Sections **2, 3 and 4** of the 2026-10-27
conversation audit, **excluding BLINK LIFE 3D**. Do not equate a merged PR
with an operational or released feature. Preserve newer main behavior.

**Release state:** This Testlab branch is a FIRST INCREMENT, not completion.
Do not publish the APK, turn on paid features, copy private media, or run
production SQL until the corresponding acceptance gates below pass.

## Priority and evidence backlog

| Track | Existing baseline | Work still required |
|---|---|---|
| Chat media studio (#595) | Merged partial editor, gallery, vault candidate | Native interactive polls, live location consent, video transitions, audio processing, PDF/OCR, vault device and privacy tests, exact reference screenshots |
| Private photo delivery (#571/#578) | Merged album/retry/view-once code | Two-account Android↔Windows tests, app-exit/reconnect/cancellation, delivery receipts, private RLS, signed APK |
| Phone and contacts (#606) | Clients merged, OTP enforcement off | Stage private matching schema/function, configure SMS provider and HMAC secret, consent/delete/opt-out/push E2E |
| Cloudflare R2 (#608) | Open draft on diverged branch | Account/buckets, credentials, CORS, safety tests, copy-first verification, rollback, private Stories/Channels/media cutover |
| Market-only Feed (#588) | Clients merged, SQL candidate only | Disposable-DB tests of category/sales-ad trigger, server sponsored approvals, media-only moderation, controlled deploy |
| WhatsApp Channels (#601) | UI and backend merged | Physical-client media, offline, visual fidelity, follower FCM end-to-end and retry |
| Coins, HP, Wallet (#586/#591) | Core screens and reward rules merged | SSV verified awards, phone/emulator wallet UX, ledger/mission/transfer live parity, expiry and refund tests |
| Refer & Grow (#572/#573) | Invite/analytics/discovery staged | Fraud-reviewed reward activation, signup attribution, worker/push/analytics live tests |
| Paystack / subscriptions | Payment Edge Functions present | Opt-in renewals, expiry/cancel, duplicate/refund security, boosts, seller payout acceptance |
| Feed + Reels | Extensive screens merged | Freshness, pagination, views, gestures, fullscreen continuity, edit/publish/privacy parity, device frame timing |
| Profiles + Stories | Theme/story code merged | Cross-client theme persistence, Story privacy/playback/replies, screenshot and font-scale acceptance |
| Search + Connect | Redesigned screens merged | Exact five tags / three users, true 30-day ranking, autocomplete, consent-based recommendations, Discover/housing/mentor tests |
| Messages + groups | Chat and invitation foundation merged | Delivery/reading/drafts, member permissions, offline recovery, moderation and UI exactness |
| Notifications | Phase-two tracker open | Archive, quiet hours, digests, cross-device read, verified FCM/worker retries |
| Website | Cloudflare route PRs merged | Real deployed deep links, Android-app parity, SEO smoke, safe public metadata |
| APK updates/releases | Signed workflow code present | Repair open publication issue, exact artifact install/upgrade, smaller delta updates after tested rollback |
| Admin & reporting | Mobile admin code present | Protected role switches, bug reports and auditable rewards, remotely managed announcements |
| University Battle | Mini-game proposal | Separately scope 1v1 game and fair online results; do not bundle BLINK LIFE 3D |
| iOS | Unreleased | Plan independent cross-platform parity/TestFlight release; do not claim builds exist |
| Whole-app performance/accessibility | Partial tests | 320–412dp/200% fonts, low-end Android 15 device, Windows parity, lifecycle and offline |

## This Testlab increment (2026-10-27)

- Add a shared Android/Windows native chat poll input/choice policy with
  focused JVM regression tests.
- Add a **STAGING-ONLY** SQL candidate with server-authoritative, single-choice
  vote idempotency, authenticated participant checks, privacy-safe aggregates,
  poll closure, deleted message handling and old-client text fallback.
- Do **not** deploy SQL or present polls as available to users. Android/Windows
  UI + actual transport integration, disposable PostgreSQL security fixture and
  full end-to-end testing remain required.

## Promotion gate for every increment

Use an isolated main-based Testlab candidate. Check latest-head Android JVM,
lint, instrumentation, debug/release APK, Windows build, browser, SQL fixture,
security and platform parity. Exercise private features with disposable users
and approved staging. Verify exact reference screenshots on an actual target
device and prove the exact signed APK installs. Confirm no wallet ledger,
old-client, server grants or data deletion regressions. Only merge to protected
main when ALL applicable checks pass. Never force merge or claim unverified
publication.

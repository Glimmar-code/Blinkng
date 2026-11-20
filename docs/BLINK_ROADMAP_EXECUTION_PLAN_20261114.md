# BLINK product roadmap execution plan

**Date:** 2026-11-14
**Source:** `../upload/pasted_content.txt` (user brief) and decisions in the task conversation
**Working branch:** `feature/contest-admin-fixes-20261114` (rebased onto `main` at `0cfb571dae916be2a013c421de21077c307aa6aa`)
**Delivery:** short-lived feature branch; pull request directly to protected `main`. No direct main edits, no changes to existing PRs or Testlab refs.

This document tracks implementation against the full user brief. It does not authorize a live Supabase migration, production rollout, paid staging resource, payment activation, or release. Backend changes must remain source-controlled, backward-compatible, and locally tested. Because no free hosted preview is currently available and paid Supabase staging is not approved, any migration/cron work remains blocked from production promotion until suitable isolated staging evidence is obtained.

## Confirmed decisions

- Do not apply the previously pasted SQL. Reuse the existing private admin-role system and existing admin/contest RPCs.
- New-user offer: 3x **total** coins on qualifying purchases in the first 24 hours after signup, capped to the first **₦5,000** of eligible spend; it replaces, rather than stacks with, a pack's regular bonus. Example: the ₦1,000 pack yields 3,000 total coins during the offer, not 3,600.
- Abuse controls for that offer: phone verification and device-risk signals. Do not use a raw hardware ID as proof of identity.
- Contest timing: exactly three admin-set timestamps: start (voting starts), end (voting ends), results. Entries may be submitted through voting end; voting can start immediately.
- Contest auto-gifts are funded from the participant's own wallet; paid votes must remain separate from gifts and the existing free-vote contract.
- Spin: four tiers (Free, 100, 500, 1,000 coins), 25 prizes per tier, free once/day, lucky hour 00:00–01:00 with only the top 10 prizes in each paid tier, averages below paid spin cost, odds disclosed in-app, pity, seven-day streak bonus, spin history, capped ad rewards, and a very rare fixed-value Jumia voucher with a monthly cap on the 1,000 tier. Prize assignment/odds must be drafted for user approval before implementation.
- Share-to-win: timing mechanics must not be disclosed to users; link-open tracking is part of the feature. User-provided timing thresholds are retained in TODO; undefined intervals are not to be guessed.
- RLS on `blink_store_settings`, `blink_contest_topic_follows`, and `blink_contest_history_participants` is a separate review/application step. Do not enable RLS without table-specific policies; this does not block unrelated feature work. New feature tables still require their own reviewed security policies.
- Do not launch paid chance-based spins/contest mechanics before Nigerian legal and Google Play policy review, odds disclosure, and age safeguards.

## Stage 1 — Admin access and contest fixes (active; timing/finalization slice in progress)

Use the current contest schema/RPCs and `private.require_blink_admin()`; do not create a second admin authorization system or expose the private schema. The actual admin username/UUID is still missing—the reply contains `[PUT YOUR USERNAME OR UUID HERE]`—so no account may be granted or modified until the owner supplies the real identifier.

### Scope and acceptance outcomes

1. Verify/fix admin username lookup and selection through the existing authorized admin user-search RPCs, keeping private profile data behind the current server boundary.
2. Change the admin contest editor to expose only start, end, and results timestamps. Preserve old clients/data using a forward-compatible server projection; do not remove legacy columns while released clients may use them.
3. Let eligible users submit an entry through the voting end time. Keep free voting, one-vote-per-account, no self-vote, moderation and server authority intact; gifts must never alter vote counts or rank.
4. Provide a participant page reachable from every contestant/vote surface, with entry image/video, name, campus, rank, vote count, Vote action, and an editable short promo line. A structured promised coin reward must be labeled verified only while server-checked wallet coverage is sufficient; otherwise block or label it unverified.
5. Add participant-scoped chat/comments and a participant-controlled pinned message, with server-side authorization, pagination, reporting/moderation hooks, and no dependence on direct table access.
6. Add a private, searchable voter list for the participant's own entry; allow one coin gift per voter per contest, 50/100/500/1,000/custom, new/suspicious-account limits, and a Thank all voters action. Add confirmed fixed-amount auto-gifting from the participant wallet, balance-coverage display, and server-side automatic pause when the wallet cannot fund the next gift. Atomic accounting, retry idempotency and notifications are required.
7. Extend participant flyers with selectable templates and a 9:16 story-size version while retaining the existing BLINK identity and QR/link safety.
8. Automatically finalize at voting end: count only valid votes, select configured winners, lock voting/entry, record a finalization snapshot and notify participants; honor the separate results timestamp for the scheduled result reveal. Provide a private-admin-only override with a reason, conflict checks, and immutable audit trail. Pending vote reviews must not silently produce fabricated votes.
9. Add contract tests and relevant Android, shared, Windows, SQL, RLS/grant, parity and migration-safety coverage. Update `platform-parity/changes.md`.

### Checkpoint — 2026-11-15 (not stage-complete)

- **Implemented:** Additive migration `20261115132000_blink_contest_three_times_auto_finalize.sql` exposes start/end/results as the canonical timing contract while retaining old columns and accepting legacy payloads; joins and voting close at end; a minute-cadence `pg_cron` task locks/finalizes due contests, counts only valid votes, stores pending-review counts plus an immutable ordered ranking/original-winner snapshot, and sends close/result notifications at their respective times. A private, reason-required admin winner override writes an immutable audit row and refuses changes after prize payment begins. Android and Windows contest forms now show only three timestamps, use shared ordering validation, display the shared start/end contract, and allow applications during voting. Existing private admin authorization remains the only admin boundary; all three new private support tables explicitly revoke direct client access.
- **Tested:** `npm test --prefix supabase/tests` passed the full repository's isolated integration/regression suite, including legacy payloads, join/vote end gates, cron registration, automatic finalization, delayed reveal, snapshot content, idempotence, private-table ACLs, unauthorized/paid override rejection, audit trail, and notifications. `gradle -p desktopApp test` on JDK 17 passed the Windows JVM suite with focused contest timing/entry tests. No hosted database was modified.
- **Not tested / held:** Android unit/build could not run because this sandbox has no Android SDK; Windows installer packaging and real-device UI were not run. The migration/cron has not been applied to any hosted database and must wait for suitable isolated staging evidence. The winner-override RPC is not yet wired to an admin screen.
- **Still open in Stage 1:** The real admin username/UUID remains `[PUT YOUR USERNAME OR UUID HERE]`, so no role was granted. The participant page, participant chat/pinning, voter list/gifts/auto-gifts, promised-reward coverage rules, and story flyer templates are not yet implemented. The three separately flagged table RLS reviews remain a distinct task and were not changed.

### Proposed timing semantics (confirm in implementation notes)

`start` is the voting/entry-open time; `end` closes entry and voting and triggers winner calculation/locking; `results` is the scheduled public reveal and winner-notification time. This uses all three requested timestamps and prevents a result from being revealed before its scheduled time. If unresolved vote reviews exist at end, the automatic job should exclude them from valid vote totals, record the excluded count, and leave an auditable admin-review/override path rather than missing the automatic close.

## Stage 2 — Wallet and complete coin history

Close accounting gaps before adding new earn/spend products. Make every mint, spend, transfer, reserve, release, refund, purchase, admin adjustment, and contest gift visible through a paginated, owner-scoped canonical ledger. Preserve balance conservation, immutable transaction references, idempotency, and Android/Windows parity. Add tests proving no money-like movement can bypass the ledger.

## Stage 3 — Signup-only 3x offer

After account creation only (not login), show the full-screen cancellable offer and server-timed 24-hour countdown. Apply 3x total (no regular pack bonus stacking) to eligible spend through the first ₦5,000; any over-cap purchase portion receives normal pack economics. Eligibility, spend metering, checkout amount, and final coin credit are server-authoritative and idempotent. Use verified phone and privacy-safe device-risk signals as decided. Keep cash checkout disabled until existing Paystack readiness requirements and provider tests pass. Do not add unapproved first-purchase doubling or starter-pack extras by implication.

## Stage 4 — Spin wheel (requires a separate approval checkpoint)

First draft and present the exact 100-prize distribution, odds per tier, expected/average value, prize fulfillment/cost ceiling, pity guarantee, streak rules, ad cap, free-spin reset and timezone, lucky-hour pool, and Jumia fixed voucher/monthly cap. Do not write the wheel implementation until the user approves that schedule. Later implementation includes the home feed banner, four tiers, server-authoritative daily free/paid spin, displayed odds, atomic coin debit/prize grant, spin history, capped ad rewards, pity, seven-day streak free spin, win-back, ticker, cards, gifts/tickets, and monthly-capped voucher. Paid chance-based release is separately held for legal/platform review.

## Stage 5 — Share-to-win and tracked opens

Implement a shared, testable state machine and a server-authoritative opaque-link/claim contract. Latest user thresholds: return in under about 1.5s is ignored; 2–5s is fake; 10–60s is real; longer than about 2 minutes is not rewarded. Original brief also says 5–10s is a gray zone and 10–120s may qualify. Exact inclusive boundaries, handling of 1.5–2s, 5–10s, 60–120s, 120–180s, clock source and late opens remain unresolved and must be settled before coding. Keep these fraud thresholds hidden from users; the user-facing failure copy is only “Share not completed.” Add unique link opens/installs, verifiable in-app shares, daily user/device caps, exactly-once claims, share streaks/ambassador progression, and fraud review without treating raw device identifiers as identity.

## Stage 6 — Remaining coin, contest, retention and community ideas

Track and evaluate, rather than silently omit, the rest of the brief: first-top-up/referral rewards; limited/event coin packs and payment methods; creator live/post gifts, earning shares and payouts; VIP spend tiers and Blink Plus; creator-funded giveaways; coin-priced status/boost/marketplace actions, tips and shoutouts; daily login streak amounts/save/partial reset/badge/flames; daily quests, weekly campus leaderboards, levels/XP, themed/live/seasonal/friend challenges, notification coordination; onboarding; creator dashboard; verified campus accounts; anonymous campus feed with moderation; contest giveaway/chat rules. Contest extension backlog also includes close-race/rank arrows, last-hour double votes, paid vote packs, prize split, voting-in-three spin, sponsored/category competitions, duplicate-device/vote-spike/verified-vote tools, confetti/winner flyers/pinned posts, and public results pages. Any paid vote or paid chance feature stays disabled until separately approved and legally/platform reviewed.

## Delivery gates

- Keep Android and Windows semantics in parity; put reusable models, validation and state in shared code first.
- Use versioned additive migrations/RPCs; never make a live production edit as the first test.
- Run only relevant Android/shared/Windows/backend checks; report exactly what ran and what did not.
- Do not create a paid staging branch or incur cloud/device-lab fees without explicit approval.
- Push only the authorized feature branch after review. Open a PR to `main`; do not merge or publish a release until required staging, security, parity and CI gates pass.
- At each stage, report implemented, tested, not tested, blocked, and next step.

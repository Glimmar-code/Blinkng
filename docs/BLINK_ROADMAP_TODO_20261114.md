# BLINK product roadmap checklist

Status key: `[ ]` not started · `[~]` in progress · `[x]` implemented and relevant tests passed · `[!]` blocked or requires a user decision/evidence.

## Stage 1 — Admin access and contest fixes (active)

- [!] Obtain a real BLINK username/UUID; the submitted value is still a placeholder. Do not grant a role to an unspecified account.
- [~] Existing `admin_search_users_v2`/`searchUsersV2` lookup path is retained; end-to-end selection and role grant are not complete because the requested admin username/UUID is still a placeholder.
- [x] Use exactly three admin-set contest times: start, end, results; preserve old clients/columns through a compatible projection. Android and Windows editors use the shared three-time validator.
- [x] Allow submissions through voting end, and preserve free-vote uniqueness, self-vote rejection, review and fair rankings.
- [ ] Participant page from every contestant/vote entry: image/video, full name, campus, rank, votes, Vote action, editable promo line.
- [ ] Structured reward promise: block or visibly label unverified when participant wallet cannot cover it.
- [ ] Participant chat/comments, participant pin controls, paginated server RPCs, moderation/reporting controls.
- [ ] Participant-only searchable voter list; one gift per voter per contest; quick 50/100/500/1,000/custom gift; new/suspicious-account limits.
- [ ] Auto-gift paid from participant wallet, explicit setup confirmation, coverage count, atomic/idempotent transfer, auto-pause on insufficient balance, and Thank all voters.
- [ ] Flyer templates and 9:16 WhatsApp Status/story format with safe QR/link and BLINK branding.
- [~] Automatic finalization at end: valid vote count, immutable ordered ranking/original-winner snapshot, lock, close/result notifications, delayed reveal and reasoned/audited private-admin winner override are implemented in the migration. Isolated SQL tests cover private ACLs, unauthorized/paid override rejection and idempotence. Override UI is not wired; no hosted staging evidence yet.
- [x] New internal tables stay in the private schema and explicitly revoke client-role access; no client RLS is enabled. Leave the three separately flagged public tables unchanged in this stage.
- [~] Android/Windows form and entry-policy parity plus isolated SQL and desktop JVM regressions pass. Android build is blocked by the absent SDK; parity log is being updated; staging/migration-safety evidence remains outstanding.
- [!] Obtain suitable isolated staging evidence before any hosted migration or merge; no paid staging resource has been requested or started.

## Stage 2 — Wallet and coin history

- [ ] Paginated owner-scoped ledger covers every coin mint, spend, transfer, hold/release, refund, purchase, contest gift, and admin/legacy adjustment.
- [ ] Idempotent paired transfer rows and reconciliation/conservation tests.
- [ ] Android and Windows wallet history show the canonical ledger and retry/error/empty states.
- [ ] Audit all balance-changing RPCs for missing/double ledger writes; platform parity and tests pass.

## Stage 3 — 3x signup offer

- [ ] Server verifies signup-only genuinely-new eligibility and starts a 24-hour timer.
- [ ] Full-screen cancellable offer with live countdown appears on signup, never on login; Android/Windows parity.
- [ ] 3x total replaces regular pack bonus on spend through first ₦5,000; above cap receives normal pack economics.
- [ ] Clarify how a purchase crossing the cap is prorated at the boundary; enforce in a server-side atomic order/fulfillment path.
- [ ] Phone verification plus privacy-safe device-risk signals; document reset/appeal behavior and avoid raw hardware IDs.
- [ ] Tests cover eligibility, window expiry, cap boundary, over-cap purchases, refunds/replays, and double-credit prevention.
- [ ] Keep checkout disabled until verified provider readiness/staging evidence; no unapproved offer stacking.

## Stage 4 — Spin wheel (do not implement before approval)

- [ ] Draft 100 prizes, 25 per tier (Free/100/500/1,000) from the supplied master list; include fixed-value Jumia voucher only on 1,000 tier with a very rare odds and monthly cap.
- [ ] Calculate and present every prize probability, expected value, and proof that paid-tier average prize value is below spin cost.
- [ ] Define top-10 paid-tier pools for 00:00–01:00 lucky hour; free pool unchanged; define timezone/reset boundary.
- [ ] Define pity trigger (10 or 20), qualifying good prize, seven-day streak free spin, and ticket/spin-gift expiry/limits.
- [ ] Define capped ad reward (about 5/day), whether reward is coins or spin tickets, and platform availability.
- [ ] Obtain explicit user approval of assignments/odds/economics before implementation.
- [ ] After approval: server-authoritative spin/config/idempotency/ledger and atomic rewards; displayed odds; daily free-spin countdown; paid tiers; history; ticker; win cards; win-back; tests.
- [ ] Complete Nigerian legal/Google Play/age-safety review before enabling paid chance-based spins.

## Stage 5 — Share-to-win

- [ ] Resolve timing boundary gaps: under ~1.5s ignored; 2–5s fake; 10–60s real; >~2m not rewarded; original 5–10s gray zone and 10–120s rule conflict; specify 1.5–2s, 60–120s, 120–180s and inclusivity.
- [ ] Keep timing/fraud rules hidden; user-facing failure text exactly “Share not completed”.
- [ ] Server-issued unique opaque share links; track opens/installs with dedupe and attribution expiry.
- [ ] Add verified in-app share path, one/two rewards per day per account/device (exact cap to confirm), exactly-once claims and worthwhile-win eligibility.
- [ ] Add share streak/ambassador outcomes and fraud watch; no raw device IDs or unverifiable external-share claims.
- [ ] Android/shared/Windows state-machine, integration and abuse tests.

## Stage 6 — Remaining monetization, retention, contests and community

- [ ] First purchase/referral top-up reward for both inviter and friend; no duplicate assignment across reward sources.
- [ ] Starter pack, event/weekend/flash bundles, ₦100–₦200 packs, tags/anchor pricing, bank transfer/USSD/card provider support, and server scheduling/expiry.
- [ ] Creator live/post gifts 10/50/200/1,000; combo, goals, leaderboard/King badge; creator share/earnings, payout, KYC/compliance; coin gifting and group gifts.
- [ ] VIP Bronze/Silver/Gold by total coins spent; Blink Plus subscription; creator-funded giveaways; coin-priced status items, boosts, market tie-ins/cashback, tips/shoutouts; keep paid votes explicitly gated pending approval/review.
- [ ] Daily check-in 5/10/15/20/30/40 coins (days 1–6), day 7 spin/bonus decision, paid streak save, partial reset, permanent 30-day badge and profile flames.
- [ ] Daily quests, weekly campus leaderboard/reset, levels/XP/perks, themed challenges, live gift-goal events, friend streaks, creator programs, seasonal events and non-spam notification scheduling.
- [ ] Campus/interests onboarding; creator analytics for views/gifts/followers/earnings; official campus account verification; anonymous campus gist/confessions feed with moderation; reports/blocks; contest chat/giveaway moderation and anti-fraud rules.
- [ ] Contest backlog: close-race/rank arrows, last-hour double votes, vote packs, prize split/fan favorite/top voter, free spin for voting in three contests, sponsored/category contests, duplicate-device/vote-spike/verified-vote tools, winner confetti/flyer/pinned post, shareable results page. Preserve free vote policy unless paid-vote changes are explicitly approved and cleared.
- [ ] Weekly coin-economy review (earned vs spent), server guardrails, promo/banner/notification non-stacking, privacy, parity and legal/platform review.

## Separate security review (does not block unrelated feature work)

- [ ] Review policies for `public.blink_store_settings`.
- [ ] Review policies for `public.blink_contest_topic_follows`.
- [ ] Review policies for `public.blink_contest_history_participants`.
- [ ] Only after each table's intended access is documented and tested, apply table-specific RLS policies in a separate migration. Never enable RLS without policies.

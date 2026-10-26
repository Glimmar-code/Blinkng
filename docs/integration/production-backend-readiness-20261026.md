# BLINK — Live Supabase referral and community activation, 26 October 2026

The connected BLINK production project `jhwgifrlxwspoedxjaly` received **eight** controlled, versioned migrations. All returned success, and the active schema, trigger and grants were checked with read-only follow-up SQL.

| Actual hosted version | Change | Live state |
| --- | --- | --- |
| `20261026052402` | Private `message-media` bucket | Private, 80 MiB ceiling, 24 approved MIME types; 3 existing objects retained |
| `20261026052615` | Referral + community foundation | Five public tables, all RLS-protected |
| `20261026052715` | Community owner foreign-key index | Active |
| `20261026054342` | Signup-safe referral attribution + visibility/discovery RPCs | Auth/email-confirmation and profile notification triggers **installed and enabled** |
| `20261026054503` | Referral milestone Coin engine + discovery-open analytics | Trigger and policy installed; **Coin payouts disabled** |
| `20261026054537` | Community creation, join/request, invite, leave and owner/moderator RPCs | Functions exposed to authenticated roles with RLS; anonymous mutations denied |
| `20261026054750` | Private referral-velocity fraud review | Private review table + detection trigger enabled |
| `20261026055226` | Private referral risk-review referrer index | Covers abuse-review owner foreign-key lookups |

Every hosted version is recorded as a matching versioned SQL file in `supabase/migrations/`. **No paid Supabase branch or project was created.** Existing referrals/community rows were empty before activation; no user accounts or app media were deleted, and the existing Auth profile-creation trigger was preserved.

## Verified production postconditions

- Auth signup `zz_blink_signup_referral` and profile notifier `zz_blink_notify_referral_on_profile` are enabled. The new referral hooks return the Auth/profile row even if an optional referral credit fails, preventing a referral fault from blocking account creation.
- Existing referral data cannot be forged via direct `authenticated` INSERT; raw Auth signup hook is not callable by `anon` or `authenticated` REST users.
- Source-based discovery and referral dashboard functions exist. Confirmed-referral rewards are **temporary profile visibility + durable achievement totals**, not automatically spendable Coins.
- `blink_community_invite`, `blink_community_join`, `blink_community_request_join`, `blink_community_leave`, `blink_community_moderate`, `blink_community_members_page` exist and authenticated EXECUTE permissions are granted; anonymous moderation is denied.
- Community members cannot directly INSERT/UPDATE their own role/status. RLS is enabled and owners can manage community metadata through owner-checked policies.
- The private velocity-review hook is enabled. Referral Coin milestone policy has **zero enabled rewards**; this must not be presented as live Coin payouts.

## Remaining validation and activation gaps — do not declare end-to-end completion

1. Hosted two-real-account sign-up, email-confirmation, idempotent credit, and Google sign-in flows have **not** been exercised. The PostgreSQL fixture passes but is not a live Auth emulator.
2. Hosted private-community invitation, invite acceptance, ban, moderator escalation, departed access and Android/Windows UI handling have **not** been verified with two actual authenticated sessions.
3. Coin *milestone payouts* are deliberately **disabled** until the award amounts, budget and anti-sybil eligibility rules are authorized, tested and audited; the base referral reward is profile visibility/achievement.
4. Independent push-outbox worker and server-side retry/acknowledge flow are **not** deployed. Referral in-app notifications are inserted through the signup/profile triggers, but end-to-end device push has not been verified.
5. Anonymous link-open/conversion analytics, visibility impression analytics and reusable group-conversation invitation consent still have separate staged migrations; none is implied live by community invitations.
6. App release / website / signed APK inclusion and real Android-Windows flows are outside the applied DB migrations.
7. Supabase lacks a hosted staging branch. CI's isolated PGlite contract suite passed, but production deployment cannot substitute for real authenticated integration checks. No live synthetic Auth users were created to simulate signups.

## Rollback and operational safeguards

Production changes were additive except for replacing community RLS policies (owner-controlled CRUD) and adding the two referral triggers to existing `auth.users` and `public.profiles`. If an Auth issue emerges, first disable the specific referral triggers (not the existing profile-creation trigger) in an audited migration; preserve underlying attribution rows and account history. For community abuse, revoke authenticated direct community writes and community mutation RPC EXECUTE and restore public/owner-only reads; preserve existing community/member data. Coin payouts remain off. Do **not** delete user data, disable RLS, or manually falsify migration history.

---

## Pre-change audit snapshot (historical; superseded where noted)

**Historical status (before 26 October production foundation fixes): STAGING/CI ONLY.**
Project inspected read-only: `Blink` (`jhwgifrlxwspoedxjaly`). No paid Supabase environment has been provisioned, and no production migration, Storage update, Auth setting or Edge Function deployment was performed in this PR.

## Exact drift found

- Hosted Supabase migration history ends at `20261023112405` (`secure_public_leaderboard_visibility`). New repository migrations from 24–25 October **are not yet deployed**, even though some Android/Windows/web code references them.
- `public.blink_referrals`, the referral supporting tables and `public.blink_communities`/`public.blink_community_members` are not present in production. Production group RPCs do exist; that does **not** prove community or consent/invitation readiness.
- `storage.buckets` has a private `message-media` bucket with `file_size_limit=null` and `allowed_mime_types=null`. The read-only MIME aggregate observed three existing objects: two `image/png` and one `image/jpeg`, all <= 0.08 MiB.
- Supabase branching returned zero branches. Without a paid hosted preview, run synthetic PGlite/PostgreSQL CI, then separately verify real Supabase Auth, Storage and Edge integration in an appropriately isolated environment before live cutover.

## Existing migrations — do not create a second competing schema

| Change | Source | Status |
| --- | --- | --- |
| Base referrals, idempotent confirmation and discovery | `supabase/migrations/20261024203000_blink_refer_and_grow.sql` | Committed, undeployed; hosted signup/trigger ordering unverified |
| Private chat albums and bucket limits | `supabase/migrations/20261025100000_chat_photo_albums_and_bucket_limits.sql` | Committed, undeployed; this PR adds fail-closed pre/postconditions and cross-platform MIME test |
| Private view-once media | `supabase/migrations/20261025103000_view_once_private_media_contract.sql` | Committed, undeployed; separate server-only access required |
| Referral coins/impression data | `supabase/migrations/20261025114000_referral_coins_and_discovery_attribution.sql` | Committed, undeployed |
| Referral delivery/link telemetry | `supabase/migrations/20261025153000_referral_delivery_and_link_telemetry.sql` | Committed, undeployed |
| Group invite consent | `supabase/migrations/20261025190000_blink_group_invitation_requests.sql` | Committed, undeployed; do not enable before server/account tests |
| Referral viewability | `supabase/migrations/20261025201000_referral_viewability_impressions.sql` | Committed, undeployed |
| Community membership/moderation | `docs/integration/groups-communities-staging-20261025.sql` | SQL candidate only, deliberately NOT an approved versioned migration |
| Marketplace trigger RPC privilege fix | `docs/integration/supabase-anon-trigger-permissions-staging-20261026.sql` | New staging candidate; must pass both trigger action paths before promotion |

**No migration should be marked applied or schema history manually edited to disguise drift.** Check actual preconditions, apply in dependency order in staging, test account lifecycle and rollback, then deploy exactly the validated revision.

## Private message-media hardening

The shared policy in `BlinkChatAttachmentPolicy.kt` permits 24 MIME types; the existing staged bucket migration has the same allowlist and a maximum 80 MiB bucket size, matching the maximum video attachment. Smaller image/voice/document limits remain app-level and must eventually be enforced by trusted backend validators, not inferred from the bucket-wide 80 MiB limit.

**Test before production:** normal photo, large valid video, all document and audio MIME classes, wrong MIME, oversized payload, interrupted upload, Windows/Android interoperability, bucket privacy, RLS and original-file retrieval; verify old clients still work. Changing the bucket limit alone does not finish private-media protection.

## Security advisor triage — avoid destructive blanket fixes

At audit, Supabase reported:

- 7 `anon`-executable SECURITY DEFINER functions. Two are `returns trigger` marketplace helpers; the isolated staging candidate revokes direct `PUBLIC`/`anon`/`authenticated` EXECUTE but keeps the triggers and verifies authorized/denied operations.
- The other five are user-facing/public-web RPCs (`check_profile_identity`, `get_blink_live_campaigns`, `get_public_web_content`, `get_public_web_profile`, `record_public_web_view`). Do **not** blindly revoke access: first review each function body, authentication boundary, data minimization, rate limiting and real frontend usage.
- 189 `authenticated` SECURITY DEFINER findings: investigate the highest-risk monetary, auth, moderation, seller and private-media endpoints individually; authenticated EXECUTE is not automatically an exploit.
- 32 tables with RLS enabled but no policies: some are private/service-only by design; review privilege grants before adding any policy.
- One Auth leaked-password-protection warning: requires authorized Supabase Auth project configuration, which is not exposed by the connected tools. Do not claim the setting was enabled.

Reference: [Supabase security linter](https://supabase.com/docs/guides/database/database-linter).

## Performance advisor triage

- 53 foreign keys without covering indexes: prioritize by slow-query evidence and actual write/read patterns; create needed indexes in an isolated environment, then assess online deployment locking.
- 78 RLS initplan performance findings: validate role and row semantics before wrapping repeated `auth.uid()` expressions in `(select auth.uid())`.
- 145 unused-index warnings: do not drop indexes just for zero observed usage; metrics can reset, and rarely-run critical queries may depend on them.
- 24 multiple-permissive-policy warnings: compare policies' commands, roles and predicates, and prove they cannot broaden read or write access before consolidation.

Reference: [Supabase database linter](https://supabase.com/docs/guides/database/database-linter).

## CI and deployment evidence required

1. Isolated `node supabase/tests/run-referral-integration.mjs`, `node supabase/tests/run-chat-media-integration.mjs`, staged communities SQL tests, and `node supabase/tests/run-anon-trigger-grants-integration.mjs` must pass on the PR's latest SHA.
2. GitHub's **migration-safety**, Android and Windows parity/checks must complete on the same SHA. No production credentials in fixture logs.
3. Use a free, disposable local test database for migration dry runs and controlled synthetic fixtures. A local fixture does not prove hosted Supabase semantics.
4. For hosted acceptance, test two accounts for signup-confirmation/referral, consent/invites, membership RLS, private-media object policy, upload MIME limit enforcement, retries, expiry and application compatibility. Keep rollback instructions and a schema inventory.
5. Only after tests and authorization pass: merge protected main, apply versioned migrations in the controlled production change window, enable dependent UI/Edge workers, and verify a fresh read-only hosted postcondition audit.

**Hard holds:** No paid staging without explicit cost approval; no direct production-first SQL trial; no main merge for changes dependent on unavailable Auth/Storage/role tests; no disabling RLS or deleting indexes to silence the advisor.

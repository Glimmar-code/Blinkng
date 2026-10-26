# BLINK Refer & Grow — deployment and acceptance gate (26 October 2026)

## Current state (verified through read-only connected production metadata)
- PR #540 is merged and contains Android, Windows and web referral surfaces and five ordered referral SQL migrations.
- **Production is changing concurrently:** During a read-only scan, the core referral tables plus `blink_referral_coin_milestones` and `blink_referral_discovery_opens`, the signup trigger, dashboard, search, leaderboard and Coin-status RPCs became visible. The first two guarded referral migrations appeared in the migration ledger. Re-run inventory at the moment of any proposed deployment; never assume an earlier scan still describes production.
- At the latest read-only scan, `blink_claim_referral_pushes`, `blink_finish_referral_push`, link stats and viewability impression stats were not deployed. The core functions are now partly available.
- The `blink-referral-push-worker` Edge Function is not deployed.
- Production's `send-activity-notification` Edge Function predates the referral-specific trusted dispatcher authorization in the repository.
- No Supabase preview branch is available. Do **not** pay for a new staging branch without explicit owner approval.

## Required promotion order
1. **Testlab**: run the existing disposable PostgreSQL 16 fixture and referral integration workflow, plus the push-worker contract test. Record exact commit SHA.
2. **Production-compatible staging**: use an isolated, no-charge disposable Supabase-compatible setup if available, with synthetic accounts (never production credentials or copied personal information). Check RLS, trigger order, stored procedures, permissions, signup and email confirmation, service-role worker, and push dispatch.
3. **Migration preflight**: compare production tables, columns, enums, existing RPC signatures, policies, triggers, grants, indexes **and completed guarded migrations** to all five versioned migrations. Confirm rollback/restore procedure. Because production is already being updated separately, do not deploy one migration blindly or replay functions/triggers already installed.
4. **Reconcile and deploy any still-missing changes** in dependency order after staging passes (never replay blindly):
   - `20261024203000_blink_refer_and_grow.sql`
   - `20261025114000_referral_coins_and_discovery_attribution.sql`
   - `20261025153000_referral_delivery_and_link_telemetry.sql`
   - `20261025190000_blink_group_invitation_requests.sql`
   - `20261025201000_referral_viewability_impressions.sql`
5. Deploy the **staging-tested compatible** `send-activity-notification` version and `blink-referral-push-worker`, respecting any existing message/social notification behavior. Worker requires a secret (at least 32 characters) in **both** trusted scheduler and Supabase function secret store; never commit it to GitHub.
6. Verify scheduler is enabled only after worker authentication, outbox claim/lease, retry/dedup, Firebase delivery and opt-out tests pass. Do not treat an HTTP 200 alone as push success.
7. Check authenticated dashboard/recommendations/search, deep links, rankings, impressions, referral links, referral leaderboard, group invitations, push and milestone status on **Android, Windows and web** using synthetic multi-account end-to-end cases.
8. Coin milestone policy **must remain disabled** until individual reward budget, eligibility, idempotence, fraud review, and abuse-control tests are approved. Do not use link clicks or self-reported impressions to mint Coins.
9. Promote Testlab into protected main and publish APK **only** after backend/provider runtime evidence is recorded and all required checks succeed on the exact release commit.

## Minimal read-only production RPC preflight
```sql
with expected(name) as (values
 ('blink_referrer_suggestions'),('blink_referral_dashboard'),
 ('blink_referral_recommendations'),('blink_referral_profile_search'),
 ('blink_referral_leaderboard'),('blink_referral_coin_status'),
 ('blink_referral_link_stats'),('blink_referral_impression_stats'),
 ('blink_capture_signup_referral'),('blink_claim_referral_pushes'),
 ('blink_finish_referral_push')
)
select name, exists(
  select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public' and p.proname=expected.name
) as present from expected order by name;
```

## Still future work (not provided by PR #540)
Public-facing QR cards/landing-page polish, referral funnel retention reporting, opt-in campus ambassadors, Growth Score fairness experiments and richer badge celebrations are optional additions. They must not delay basic reliable referral signup, attribution, discovery and push delivery.

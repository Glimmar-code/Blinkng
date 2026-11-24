# Broadcast Testlab Verification Checklist

## Current code scope
- Broadcast SQL function and metadata catalog recovery snapshots.
- FCM OAuth helper with Node regression tests.
- Android and Windows/Desktop Supabase repositories.
- Website public RPC banner.
- Android Broadcast token lifecycle and data-only account-scoped push routing.
- Testlab migrations: admin update patch fields, quiet-hours clearing, and reward configuration guard.
- Campaign coin tiers, promo/blocking feature guards.

## Verification gates
- [ ] Android full compile/unit/lint on Testlab branch.
- [ ] Windows desktop build and tests.
- [ ] Node contract tests for FCM, website, push, and migrations.
- [ ] Migration SQL syntax/safety and disposable baseline/rebuild.
- [ ] Testlab Supabase integration for admin RPCs, audience, RLS, storage, claims, read/dismiss, and analytics.
- [ ] Secure FCM secret configuration and delivery/retry/invalid-token testing.
- [ ] Scheduled publish dispatch and email provider integration.
- [ ] Concurrency/idempotency tests.

## Known limitations
- Scheduled broadcasts activate via existing lifecycle cron but do not yet auto-dispatch push.
- Email channel is not integrated.
- Blocking broadcasts are disabled in client validation until block-kind support exists.
- Non-coin promos and purchase thresholds are blocked; campaign coin rewards are supported.
- No Supabase CLI is installed in the current session, so the new migrations have not been created via CLI or applied to a database.
- Production database and Edge Functions have not been modified.

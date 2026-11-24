# Broadcast Recovery — Fix Progress

## Completed on feature branch
- Read-only catalog export utility preserved.
- 64 exact deployed public Broadcast/campaign/promo SQL function definitions captured.
- Catalog metadata captured: 132 columns, 67 constraints, 35 indexes, 23 RLS policies, 31 trigger entries, 2 active cron jobs, 4 Realtime publication tables, bucket metadata, and 4 Storage policies.
- Deployed `send-broadcast` and `send-test` Edge source recovered from Supabase.
- Corrected FCM JWT base64url and PEM whitespace parsing through a shared runtime-neutral helper.
- Added regression test coverage for FCM helper; wired into existing Supabase test suite.
- Production DB and Edge deployments have not been changed in this recovery work.

## Completed additional integration on this branch
- Android ViewModel defaults to Supabase Broadcast repository (active list, inbox, read/dismiss, campaigns/promos, prefs, admin CRUD, analytics/audit, images, test push).
- Windows/Desktop repository is also connected to the shared authenticated Supabase client.
- Android FCM token lifecycle registers a separate Broadcast device token and disables it on logout.
- Broadcast FCM uses data-only recipient-scoped messages; background notification taps and foreground in-app taps route to Broadcast inbox.
- Website RPC banner is wired with safe DOM text rendering, URL validation and local dismissals.
- Campaign reward tiers support coin amounts; unsupported non-coin promo rewards and purchase thresholds are guarded in client validation.
- Added Testlab migrations for admin-edit field coverage and quiet-hours clearing. Migrations have not been applied to any database.
- Immediate publish dispatch is connected; scheduled activation dispatch remains unconfigured.
- Added tests for website contract, push account-safety/routing, and both SQL migrations.

## Still required before calling Broadcast fully fixed
1. Supply/configure the FCM service-account secret securely in Supabase (not in source).
2. Test Edge function auth, targeting, token handling, retries, push cap, delivery audit and invalid-token cleanup against disposable Testlab data.
3. Finish automatic dispatch for future scheduled broadcasts (immediate publish currently invokes push dispatcher).
4. Compile and runtime-test Android/Windows repository adapters and verify parity.
5. Integrate email dispatch (or explicitly close it as a future feature).
6. Harden server-side validation for non-coin promo rewards and minimum purchase, not only client guards.
7. Reconcile migration history and build a disposable baseline; no Supabase CLI is installed in this session.
8. Run full CI, migration lint, integration tests and security checks after source changes.
9. Verify concurrency/idempotency (claims, rewards, publish dispatch, delivery retries).

## Important boundary
This is source recovery and Testlab implementation only. Do not deploy to production or merge to main until every required verification gate passes.

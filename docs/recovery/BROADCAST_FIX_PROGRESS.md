# Broadcast Recovery — Fix Progress

## Completed on feature branch
- Read-only catalog export utility preserved.
- 64 exact deployed public Broadcast/campaign/promo SQL function definitions captured.
- Catalog metadata captured: 132 columns, 67 constraints, 35 indexes, 23 RLS policies, 31 trigger entries, 2 active cron jobs, 4 Realtime publication tables, bucket metadata, and 4 Storage policies.
- Deployed `send-broadcast` and `send-test` Edge source recovered from Supabase.
- Corrected FCM JWT base64url and PEM whitespace parsing through a shared runtime-neutral helper.
- Added regression test coverage for FCM helper; wired into existing Supabase test suite.
- Production DB and Edge deployments have not been changed in this recovery work.

## Still required before calling Broadcast fully fixed
1. Supply/configure the FCM service-account secret securely in Supabase (not in source).
2. Test Edge function auth, targeting, token handling, retries, push cap, delivery audit and invalid-token cleanup against disposable Testlab data.
3. Connect publish/schedule action to push dispatch safely and idempotently.
4. Integrate Android and Windows repository adapters (currently placeholder/unavailable).
5. Integrate website broadcast RPC display.
6. Integrate email dispatch (or explicitly close it as a future feature).
7. Implement verified-purchase promo minimums and non-coin campaign reward handlers or reject those options at admin UI validation.
8. Reconstruct a complete migration baseline/history with safe drift reconciliation, then run a disposable local Supabase rebuild, migration lint, integration tests and security checks.
9. Verify concurrency and idempotency (promo claims, campaign rewards, publish dispatch, delivery retries) and document results.

## Important boundary
This is source recovery and Testlab implementation only. Do not deploy to production or merge to main until every required verification gate passes.

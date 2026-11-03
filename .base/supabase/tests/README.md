# Testlab migration regressions

Run `npm ci --prefix supabase/tests` and `npm test --prefix supabase/tests`.

This suite uses isolated embedded PostgreSQL. It has no production connection or credentials. The fixture captures schema metadata, existing function definitions and all legacy RLS policies from October 7, 2026. Account and content rows are synthetic.

All eight promotion migrations run and reapply, including the compatibility projection for legacy ranked reads. Checks cover private profile projection, username uniqueness, legacy presence privacy, inbox membership and state, immutable request identities, mute expiry, forwarding permissions, one-charge Boost/Drop retries, account-scoped receipts, the three-pin limit, compatibility-message isolation, token ownership, policy commands disabled client-authoritative rewards, and cross-account ranked feed, Connect, Games, Search, legacy inbox and study-circle reads after owner-only raw-profile RLS.

This is not a complete recovery baseline. Spatial columns, legacy triggers and privileges beyond the authenticated grants modeled here are outside its scope. The local cron adapter checks job registration; maintenance calls with no expiring inventory check SQL compatibility. Hosted scheduler execution, notification delivery and full application behavior remain separate integration checks.

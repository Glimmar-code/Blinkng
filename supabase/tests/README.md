# Testlab migration regressions

Run `npm ci --prefix supabase/tests` and `npm test --prefix supabase/tests`.

This suite uses an isolated embedded PostgreSQL database. It has no production connection or credentials. The schema-only fixture captures the relevant legacy shape and profile/chat policies from October 7, 2026, with synthetic account and content rows.

Checks cover migration dependencies, reapplication, private profile projection, username uniqueness, legacy presence privacy, inbox membership and state, immutable request identities, mute expiry, forwarding permissions, one-charge Boost/Drop retries, account-scoped receipts, and the three-pin limit.

This fixture is not a complete recovery baseline. Spatial columns, unrelated policies and legacy triggers are outside its scope. The local cron adapter checks job registration and reapplication; execution by the hosted scheduler and full application behavior remain separate integration checks.

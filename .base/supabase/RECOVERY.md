# Blink Supabase Recovery Guide

This folder is the source-controlled recovery blueprint for the Blink Supabase backend.

## Live project captured

- Project: Blink
- Project ref: `jhwgifrlxwspoedxjaly`
- PostgreSQL major version: 17
- Recovery snapshot date: 2026-09-07

## Recovery rule

No Supabase change is complete until it is represented in this repository. Every backend change must be paired with any required Android app changes, tested together, and included in the next Blink release.

Required lifecycle:

`Supabase change -> migration/function/config in GitHub -> Android integration if required -> backend/app tests -> next app version -> release notes`

## Mandatory version-bound fallback / rollback rule

**Every Blink Android/Git version and its compatible Supabase backend definition are one release unit.**

If Blink is checked out, restored, reverted, rolled back, rebuilt, or falls back to another version/commit/tag/release, the recovery process must also identify and restore the complete compatible Supabase backend definition for that version. Do not restore only the APK/Kotlin code while leaving an unrelated backend version behind.

The version-bound Supabase recovery set includes every source-controlled component that can affect runtime behavior:

- migrations and current-state recovery baselines
- schemas, tables, columns, enums and generated/default expressions
- primary/foreign/unique/check/exclusion constraints
- indexes and replica-identity settings
- SQL functions/RPCs, procedures and views
- triggers
- RLS enable/force state, policies, grants and permissions
- Storage bucket configuration and Storage policies
- Realtime publication membership
- cron/scheduled jobs
- Edge Function source code and function-level configuration such as `verify_jwt`
- non-secret Auth/client configuration required by that release
- controlled seed/reference/configuration data required by application logic
- required secret **names** and setup instructions

A fallback is **not complete** until the Android version and restored backend pass compatibility, security/RLS, and smoke/integration tests together.

### Destructive downgrade protection

Never blindly downgrade the live production database, drop newer columns/tables, delete newer production rows, or erase uploaded user objects merely to make an older APK match. If the desired older app version requires an incompatible/destructive backend downgrade, restore its backend into a separate recovery/staging Supabase project or implement a forward-compatible compatibility layer. Preserve production data first.

### Release snapshot requirement going forward

For every future release, keep enough source-controlled information to map the Android release/tag/commit to the matching Supabase state. A release must not be considered recoverable until its backend migrations/config/functions and any required recovery manifest are committed.

## What belongs in GitHub

Keep these under source control:

- Database migrations and schema-changing SQL
- Current-state recovery baselines used to cover legacy migration gaps
- RLS policies, grants, indexes, constraints and triggers
- SQL functions/RPCs and views
- Edge Function source code
- Edge Function non-secret runtime configuration
- Realtime publication configuration
- Storage bucket definitions and policies
- Cron/job definitions
- Non-secret local Supabase config
- Controlled seed/reference/configuration fixtures
- Required environment-variable/secret **names**, never their values
- Architecture, drift-scan and recovery documentation

## What must NOT be committed

Never commit:

- `SUPABASE_SERVICE_ROLE_KEY` or Supabase secret-key values
- Database passwords
- SMTP usernames/passwords
- Google OAuth client secrets
- Firebase service-account private keys / JSON
- Payment-provider secrets
- Production access tokens
- Private user data, messages, emails, phone numbers, auth sessions, FCM tokens or payment records
- Auth user rows or password hashes
- Production Storage object bytes

The Android app may use only client-safe Supabase configuration such as the project URL and publishable/legacy anon key as appropriate. Privileged credentials belong only in server-side secret storage.

## Current live inventory captured on 2026-09-07

After removing the empty interrupted recovery-export table, the connected Blink project contains:

- 167 applied migration-history entries
- 133 application/backend tables across `public`, `private` and `private_ranking`
- 3 regular views
- 319 SQL functions across the application schemas
- 264 application RLS policies
- 459 indexes
- 81 custom/non-internal triggers
- 9 application enums
- 8 deployed Edge Functions
- 10 Storage buckets
- 32 tables in the `supabase_realtime` publication
- 9 scheduled cron jobs

### Historical migration gap

The live migration history begins on 2026-07-24, while the raw migration files currently present in the repository begin much later. Therefore raw GitHub migration files alone are **not** a complete reconstruction source for the oldest live state.

Do not invent missing migration history and do not blindly commit raw historical SQL that may contain old privileged configuration. The recovery snapshot under `supabase/recovery/` exists to capture a safe current-state structural/backend blueprint for this legacy gap. The applied-migration manifest should be retained for auditability.

Edge Functions observed live:

- `game-rewards`
- `moderation-action`
- `verify-payment`
- `send-push-notification`
- `username-login`
- `share-preview`
- `blink-web`
- `send-call-notification`

Current function JWT requirements are recorded in `supabase/config.toml` and must travel with the matching release.

### Required Edge Function environment names

The deployed function source currently references these environment-variable names. Their **values must never be committed**:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_PUBLISHABLE_KEYS`
- `SUPABASE_SECRET_KEYS`
- `FIREBASE_SERVICE_ACCOUNT_JSON`

A recovered deployment is incomplete until the required values are restored through approved Supabase/server secret management.

Storage buckets observed live:

- `avatars` (public)
- `covers` (public)
- `feed-media` (private)
- `marketplace-media` (public)
- `message-media` (private)
- `post-media` (public)
- `posts` (public)
- `profile-media` (public)
- `status-media` (public)
- `story-media` (public)

## Scheduled jobs observed live

- `blink-admin-notification-campaigns` — every minute
- `blink-expire-admin-state` — every 5 minutes
- `blink-expire-statuses` — every 15 minutes
- `blink-presence-lease-reconcile` — every minute
- `blink-ranking-events-retention` — daily
- `blink-refresh-leaderboards` — hourly
- `blink-vip-auto-renew` — hourly
- `blink-vip-expiry-maintenance` — hourly
- `publish-due-blink-posts` — every minute

## Disaster recovery sequence

1. Identify the exact Blink app release/tag/commit being restored and its matching Supabase recovery state.
2. Create a clean isolated Supabase recovery project in the correct region and compatible PostgreSQL version when a destructive rollback would otherwise be required.
3. Install/enable required extensions before applying dependent objects.
4. Link the repository to the recovery project using the current Supabase CLI/workflow.
5. Review migration order and legacy-gap baseline, then apply the matching source-controlled migrations/schema recovery SQL.
6. Restore constraints, indexes, functions/views, triggers, RLS policies/grants, replica identity, Storage definitions, Realtime membership and cron jobs for that version.
7. Deploy every matching function under `supabase/functions/` using the recorded JWT requirement.
8. Configure Auth providers and exact redirect URLs required by that version.
9. Configure production SMTP separately if required.
10. Add Edge Function/runtime secret values through Supabase secret management; never copy secret values into Git.
11. Restore controlled reference/configuration data required by the application.
12. Restore production database rows and Storage objects only from verified backups when actual data recovery is required.
13. Run RLS/security checks, backend smoke tests, and Android integration/regression tests against the restored app/backend pair.
14. Promote/release only when app/backend compatibility checks pass.

## Important distinction: blueprint vs production data backup

GitHub stores the reproducible backend **code/configuration definition**. It is not a substitute for backups containing live user data or uploaded objects. A migration/schema recovery set can rebuild tables and logic but cannot recreate user posts/messages/accounts/files that existed only as live data. Keep regular Supabase/database/Storage backups separately according to the project backup policy.

## Future-change requirement

For every future Supabase change:

1. Create or update the corresponding migration/function/config file first or immediately after the controlled database change.
2. Commit it in the same feature/fix branch as any Android integration change.
3. Update the release/backend recovery mapping when the Supabase state changes.
4. Test old-app/new-backend compatibility where relevant.
5. Test new-app/new-backend compatibility.
6. Do not ship the app if repository schema and production schema have unexplained drift.
7. Document the backend change in release notes.
8. Before declaring a release recoverable, verify that checking out that release provides all non-secret backend definitions needed to recreate a compatible Supabase environment.

The repository is the recovery/source-of-truth copy; production Supabase is the running environment.

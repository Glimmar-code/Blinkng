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

## What belongs in GitHub

Keep these under source control:

- Database migrations and schema-changing SQL
- RLS policies, grants, indexes, constraints and triggers
- SQL functions/RPCs and views
- Edge Function source code
- Realtime publication configuration
- Storage bucket definitions and policies
- Cron/job definitions
- Non-secret local Supabase config
- Controlled seed/reference fixtures
- Architecture and recovery documentation

## What must NOT be committed

Never commit:

- `SUPABASE_SERVICE_ROLE_KEY` or Supabase secret keys
- Database passwords
- SMTP usernames/passwords
- Google OAuth client secrets
- Firebase service-account private keys / JSON
- Payment-provider secrets
- Production access tokens
- Private user data, messages, emails, phone numbers, auth sessions, FCM tokens or payment records

The Android app may use only client-safe Supabase configuration such as the project URL and publishable/legacy anon key as appropriate. Privileged credentials belong only in server-side secret storage.

## Current live inventory captured on 2026-09-07

The connected Blink project currently contains:

- 166 applied migration-history entries
- More than 100 application/backend tables across `public`, `private` and `private_ranking`
- 309 RLS policies
- 409 indexes
- 112 triggers
- 8 deployed Edge Functions
- 10 Storage buckets
- 32 tables in the `supabase_realtime` publication
- 9 scheduled cron jobs

Edge Functions observed live:

- `game-rewards`
- `moderation-action`
- `verify-payment`
- `send-push-notification`
- `username-login`
- `share-preview`
- `blink-web`
- `send-call-notification`

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

1. Create a clean Supabase project in the correct region and compatible PostgreSQL version.
2. Install/enable required extensions before applying objects that depend on them.
3. Link the repository to the new Supabase project using the current Supabase CLI.
4. Review migration order, then apply the source-controlled migrations/schema recovery SQL.
5. Deploy every function under `supabase/functions/` using its recorded JWT requirement.
6. Recreate Storage buckets and verify their policies.
7. Restore Realtime publication membership.
8. Restore scheduled cron jobs.
9. Configure Auth providers and exact redirect URLs.
10. Configure production SMTP separately.
11. Add Edge Function/runtime secrets through Supabase secret management; never copy secrets into Git.
12. Restore production data from a verified database backup if data recovery is required.
13. Run RLS/security checks, backend smoke tests, and Android integration tests.
14. Only release an APK/app update when the backend/app compatibility checks pass.

## Important distinction: blueprint vs production data backup

GitHub stores the reproducible backend definition. It is not a substitute for database backups containing live user data. A migration/schema backup can rebuild tables and logic but cannot recreate user posts/messages/accounts that existed only as rows. Keep regular Supabase/database backups separately.

## Future-change requirement

For every future Supabase change:

1. Create or update the corresponding migration/function/config file first or immediately after the controlled database change.
2. Commit it in the same feature/fix branch as any Android integration change.
3. Test old-app/new-backend compatibility where relevant.
4. Test new-app/new-backend compatibility.
5. Do not ship the app if repository schema and production schema have unexplained drift.
6. Document the backend change in the release notes.

The repository is the recovery/source-of-truth copy; production Supabase is the running environment.

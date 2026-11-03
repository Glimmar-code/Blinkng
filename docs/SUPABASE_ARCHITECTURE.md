# Blink Supabase Architecture

Snapshot date: 2026-09-07

## Source of truth

The Blink backend is split between the live Supabase project and the source-controlled recovery definition under `supabase/`. Every intentional production change must be captured in GitHub and paired with the next compatible Android release when app behavior changes.

## Database schemas

Application-owned database objects currently use these primary schemas:

- `public` — client-facing application data and RPCs protected by RLS/permissions
- `private` — privileged/admin/internal backend objects
- `private_ranking` — internal discovery/ranking state and maintenance logic

Supabase-managed schemas such as `auth`, `storage`, `realtime`, `extensions`, `supabase_functions`, `supabase_migrations` and `vault` are platform-managed and must not be replaced with hand-written copies of private platform internals.

## Main backend domains

The live schema contains the backend for:

- Profiles, follows and social graph
- Feed posts, reels, reposts, likes, bookmarks and views
- Comments, replies and comment interactions
- Stories/statuses and their interactions
- Conversations, messages, receipts and media
- Voice/video call signalling and push dispatch tracking
- Notifications and unread state
- Coins, rewards, store items, VIP and boosts
- Games, challenges and leaderboards
- Verification requests/payments/badges
- Marketplace and campus/connect features
- Admin roles, audit, moderation, campaigns and system configuration
- Discovery/ranking metrics and caches
- Scheduled publishing and maintenance

## Security model

- RLS is expected on exposed application tables.
- Authorization must use ownership/role checks appropriate to each table, not merely `TO authenticated`.
- Privileged/admin authorization belongs in server-controlled metadata/tables, never editable user metadata.
- Secret/service-role credentials must never be included in the Android APK or Git repository.
- Edge Functions that use the service role must authenticate/authorize callers before privileged operations.

## Edge Functions

The live project currently exposes 8 functions whose source belongs under `supabase/functions/`:

| Function | Live JWT requirement |
|---|---|
| `game-rewards` | required |
| `moderation-action` | required |
| `verify-payment` | required |
| `send-push-notification` | required |
| `username-login` | custom/public endpoint |
| `share-preview` | public endpoint |
| `blink-web` | public web endpoint |
| `send-call-notification` | required |

Keep the deployed function source and repository copy synchronized. Environment values such as service-role credentials and Firebase service-account JSON are runtime secrets and are intentionally excluded from source control.

## Storage

Current buckets:

| Bucket | Public |
|---|---|
| avatars | yes |
| covers | yes |
| feed-media | no |
| marketplace-media | yes |
| message-media | no |
| post-media | yes |
| posts | yes |
| profile-media | yes |
| status-media | yes |
| story-media | yes |

Bucket visibility does not replace row/object authorization. Storage policies must remain source-controlled with the database policy definitions.

## Realtime

The `supabase_realtime` publication currently includes 32 app tables, covering activities, calls/signals, comments/replies, conversations/messages, feed interactions, social requests, notifications, polls, stories, study/connect features and related live state.

## Scheduled jobs

The live database uses `pg_cron` for admin notification dispatch, admin-state expiry, status expiry, presence reconciliation, ranking-event retention, leaderboard refresh, VIP auto-renew/expiry maintenance, and scheduled post publishing.

## Extensions observed

The project uses a Supabase/Postgres extension set that includes `pg_cron`, `pg_net`, `pg_trgm`, `pgcrypto`, `postgis`, `unaccent`, `uuid-ossp`, `vector`, `wrappers`, `pg_graphql`, `pg_jsonschema`, `hypopg`, `index_advisor`, `pg_stat_statements`, `supabase_vault`, `pgsodium`, and `plpgsql`.

When rebuilding, enable only extensions required by the restored schema and verify their current Supabase support/version before applying dependent SQL.

## Release compatibility contract

Any migration that changes an app-facing column, RPC, policy behavior, function contract, storage path, Auth redirect flow, Realtime subscription, or notification payload must be evaluated against both:

1. the currently released Android app, and
2. the next Android build.

Prefer backwards-compatible backend changes first, release the compatible app, then remove deprecated backend behavior in a later release when safe.

## Recovery boundaries

This repository preserves code/configuration. Production rows and secret values require separate secure backup/secret-management processes. See `supabase/RECOVERY.md` for the recovery procedure.

# BLINK Broadcast Backend — Production Recovery Record

**Status:** Production catalog and 64 public Broadcast/campaign/promo function definitions recovered; deployed Edge Function sources recovered; Testlab recovery work in progress.
**Supabase project:** `jhwgifrlxwspoedxjaly`
**Repository:** `Glimmar-code/Blinkng`
**Recovery branch:** `feature/broadcast-recovery-correction` (based on the recovery snapshot branch)

## Safety and migration status
- Broadcast backend was deployed directly to production before migration source was committed.
- This branch does not change production.
- `supabase/recovery/export_broadcast_catalog.sql` is a read-only exporter.
- **Do not run a reconstructed baseline against production.** Existing tables/functions are deployed; an incomplete baseline could overwrite behavior.
- Recovery branch requires review and disposable/local testing before merge to main.
- Migration history has not been reconciled. No claim is made that migration history matches the live schema.
- Production remains unchanged throughout this recovery.
- No broadcast seeds inserted.

## Verified tables
`broadcast_app_config`, `broadcast_audit_log`, `broadcast_clicks`, `broadcast_daily_rollups`, `broadcast_deliveries`, `broadcast_dismissals`, `broadcast_impressions`, `broadcast_rate_limits`, `broadcast_reads`, `broadcasts`, `broadcasts_changed`, `campaign_participants`, `campaign_progress_events`, `campaign_rules`, `device_tokens`, `notification_prefs`, `promo_claims`, `promo_rules`.

Do not remove/rename similarly named legacy tables, including `notification_preferences` and `notification_push_dispatches`.

## Types
`broadcast_type`, `broadcast_status`, `broadcast_block_kind`, `broadcast_channel`, `broadcast_delivery_status`, `broadcast_claim_status`, `broadcast_goal_type`, `broadcast_reward_type`.

## Core RPC/function inventory
Admin: `admin_create_broadcast`, `admin_update_broadcast`, `admin_end_broadcast`, `admin_duplicate_broadcast`, `admin_delete_broadcast`, `admin_estimate_broadcast_audience`, `admin_get_broadcast_audit`, `admin_broadcast_analytics`, `admin_update_broadcast_config`.

User: `get_active_broadcasts`, `dismiss_broadcast`, `get_broadcast_inbox`, `get_broadcast_prefs`, `update_broadcast_prefs`, `register_broadcast_device_token`, `track_broadcast_impression`, `track_broadcast_click`, `join_campaign`, `get_campaign_progress`, `get_campaign_leaderboard`, `claim_promo`, `get_broadcast_blocking_state`, `get_website_broadcasts`.

Internal: `broadcast_admin_check`, `broadcast_audience_match`, `broadcast_audience_match_self`, `broadcast_campaign_reward`, `broadcast_lifecycle_tick`, `broadcast_log`, `broadcast_rate_limit`, `broadcast_push_cap_reserve`, semver functions, campaign event triggers, rollup/lifecycle triggers.

## Verified RLS policy model
- Admin-check policies guard broadcast/admin config, audit, analytics, deliveries, rate limits, campaign and promo administration.
- User-own policies apply to dismissal/read state, device tokens, notification preferences, participant SELECT, promo-claim SELECT.
- Live broadcast read requires live/non-deleted/time-window and audience-self match.
- Authenticated read on the lightweight broadcast change signal.
- RLS is enabled on Broadcast-related tables (verified earlier).

## Scheduling, Realtime and Storage
- Active cron: `blink_broadcast_lifecycle_minutely`, `* * * * *`, runs `broadcast_lifecycle_tick()`.
- Active cron: `blink_broadcast_purge_daily`, `15 3 * * *`, runs `broadcast_purge_old_events()`.
- Realtime publication: `broadcast_dismissals`, `broadcast_reads`, `broadcasts_changed`, `campaign_participants`.
- Bucket `broadcast-images` is public-read, 8 MiB limit, JPEG/PNG/WebP/GIF. Write policies must remain admin-only; catalog exporter retrieves current policy definitions.

## Source recovery files
- `supabase/recovery/broadcast_function_catalog_snapshot.sql`: 64 exact deployed public function definitions (read-only recovery snapshot; not a migration).
- `supabase/recovery/broadcast_catalog_metadata_snapshot.json`: 132 columns, 67 constraints, 35 indexes, 23 policies, 31 trigger definitions, 2 cron jobs, 4 Realtime tables, bucket metadata and 4 Storage policies.
- `supabase/functions/send-broadcast/index.ts` and `supabase/functions/send-test/index.ts`: exact deployed Edge sources recovered.
- `supabase/functions/_shared/fcm-auth.mjs`: shared OAuth helper, with regression tests.

## Known gaps (not yet resolved)
- FCM service-account secret is not configured; push cannot be live-tested until a secure secret is supplied. Do not commit secrets.
- Email dispatch is not integrated.
- Publish/schedule-to-push dispatch wiring is not yet connected.
- Non-coin campaign rewards and verified purchase-based promo requirements remain incomplete.
- Website integration with `get_website_broadcasts()` remains pending.
- Android/Windows production repository adapters are still unavailable placeholders.
- Migration history has not been reconciled and a complete safe replay baseline has not been built.
- Full runtime, concurrency/race, and client integration tests remain pending.

## Corrective work in recovery branch
- Fixed FCM JWT base64url conversion and PEM whitespace parsing by extracting the OAuth helper into a testable shared module.
- Added Node regression tests for base64url encoding, PEM normalization, JWT claim content, signature verification, OAuth error handling and missing-token failure.
- Tests have not yet been run in GitHub CI; branch review and CI are still required.

## Recovery steps
1. Export catalog (columns, constraints, indexes, RLS, triggers, complete function definitions, cron, Realtime, Storage) using the read-only SQL exporter.
2. Recover Edge Function source/config.
3. Reconstruct migration files with explicit migration-history reconciliation guidance. Never replay blindly.
4. Review SQL security, idempotency, dependencies; test against disposable DB.
5. Compare recovered source against production catalog.
6. Commit on this branch, run CI, review, and merge only when verified.

RPC signatures and client contract must be preserved; don't silently rename functions or alter behavior.

# BLINK Broadcast Backend — Production Recovery Record

**Status:** Production backend deployed directly to Supabase; source recovery is in progress.
**Supabase project:** `jhwgifrlxwspoedxjaly`
**Repository:** `Glimmar-code/Blinkng`
**Recovery branch:** `testlab/broadcast-backend-recovery`

## Safety / migration status

- The Broadcast backend was deployed directly to production before its migration source was committed.
- This recovery branch does not change the production database.
- The catalog exporter at `supabase/recovery/export_broadcast_catalog.sql` is for read-only extraction.
- **Do not execute a reconstructed baseline against production.** Tables/functions already exist, and an incomplete baseline could overwrite deployed logic.
- The recovery branch must be reviewed, tested against a disposable/local database, and only then merged to main.
- Migration history has not yet been registered. No claim is made that Supabase migration history matches production.
- No live or draft broadcast seeds were inserted.

## Verified deployed inventory (catalog names)

### Tables
`broadcast_app_config`, `broadcast_audit_log`, `broadcast_clicks`, `broadcast_daily_rollups`, `broadcast_deliveries`, `broadcast_dismissals`, `broadcast_impressions`, `broadcast_rate_limits`, `broadcast_reads`, `broadcasts`, `broadcasts_changed`, `campaign_participants`, `campaign_progress_events`, `campaign_rules`, `device_tokens`, `notification_prefs`, `promo_claims`, `promo_rules`.

The project also has other similarly named legacy notification tables (for example `notification_preferences` and `notification_push_dispatches`); these are not to be removed or renamed during recovery.

### Broadcast types
- `broadcast_type`, `broadcast_status`, `broadcast_block_kind`
- `broadcast_channel`, `broadcast_delivery_status`
- `broadcast_claim_status`, `broadcast_goal_type`, `broadcast_reward_type`

### Core functions
Admin: `admin_create_broadcast`, `admin_update_broadcast`, `admin_end_broadcast`, `admin_duplicate_broadcast`, `admin_delete_broadcast`, `admin_estimate_broadcast_audience`, `admin_get_broadcast_audit`, `admin_broadcast_analytics`, `admin_update_broadcast_config`.

User: `get_active_broadcasts`, `dismiss_broadcast`, `get_broadcast_inbox`, `get_broadcast_prefs`, `update_broadcast_prefs`, `register_broadcast_device_token`, `track_broadcast_impression`, `track_broadcast_click`, `join_campaign`, `get_campaign_progress`, `get_campaign_leaderboard`, `claim_promo`, `get_broadcast_blocking_state`, `get_website_broadcasts`.

Internal: `broadcast_admin_check`, `broadcast_audience_match`, `broadcast_audience_match_self`, `broadcast_campaign_reward`, `broadcast_lifecycle_tick`, `broadcast_log`, `broadcast_rate_limit`, `broadcast_push_cap_reserve`, `broadcast_semver_compare`, `broadcast_semver_at_least`, campaign event trigger functions and rollup/lifecycle trigger functions.

### Known implementation limitations at time of recovery
- FCM credentials are not configured; real push delivery is not operational yet.
- Email delivery is not integrated.
- Automatic dispatch when a broadcast is published is not wired up.
- Non-coin reward handlers and verified purchase requirements for promo rewards are incomplete.
- Website integration must call the website broadcast RPC.
- Physical client testing and concurrent promo-claim race testing have not been completed.
- The production Edge Function deployment source is not yet recovered into this repository; function names and deployed status were recorded separately, but the Supabase deployment tool does not provide a source export here.

## Recovery plan
1. Export catalog metadata (tables/columns, constraints, indexes, policies, triggers, function definitions, cron jobs, Realtime publication, Storage bucket/policies) with the read-only SQL exporter.
2. Capture and recover the Edge Function source code and configuration.
3. Reconstruct migrations carefully, split into logical files, and include migration-history reconciliation instructions. Never blindly replay.
4. Review SQL/function source for security, idempotency and dependencies. Test on disposable database.
5. Verify exact deployed-vs-recovered function/table differences.
6. Commit source on this branch, run repository CI/tests, review, then merge only when correct.

## RPC contract
See the prior implementation contract in task notes; this recovery must preserve the existing deployed signatures and behavior. Do not invent alternate function names or silently change client-facing contracts.

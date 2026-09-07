# Blink Supabase Recovery Snapshot — 2026-09-07

This directory is a recovery-only current-state blueprint of the Blink Supabase backend. It exists because older live migration history predates the SQL migration files currently present in GitHub.

## Safety boundaries

- Do not auto-run this snapshot against the live production project.
- Do not use this directory as a normal forward migration stream.
- No production user rows, Auth users, messages, uploaded object bytes, passwords, tokens, service-role keys, SMTP credentials, OAuth client secrets, Firebase private keys, or payment secrets belong here.
- Secret **names** and required configuration may be documented; secret **values** must live in the approved secret manager / Supabase project configuration.
- User data and Storage object bytes must be restored from database/Storage backups, not from Git.

## Recovery order

1. Create/choose an isolated recovery or staging Supabase project.
2. Recreate extensions, schemas, enums and tables from the schema-core snapshot.
3. Recreate constraints and indexes.
4. Recreate SQL functions and views.
5. Recreate triggers, RLS policies and grants.
6. Recreate Storage bucket configuration and Storage policies.
7. Restore Realtime publication membership and cron jobs.
8. Deploy the exact Edge Function source in `supabase/functions/` using the matching `supabase/config.toml` settings.
9. Restore required secret values from the secure secret store.
10. Restore production data/object bytes only from approved backups when performing an actual disaster recovery.
11. Run Android/backend compatibility tests before promoting the recovered project.

## Version fallback / rollback rule

The Android app version/Git commit and its matching Supabase backend definition are one release unit. If Blink is checked out, reverted, or rolled back to another version, the recovery process must identify the matching backend state and restore **all** compatible source-controlled Supabase definitions: migrations/baseline, SQL functions, views, constraints, indexes, RLS, grants, triggers, Storage definitions, Realtime publication membership, cron jobs, Edge Functions, and function configuration.

Never blindly downgrade the production database or delete newer production data just to match an older APK. If the older app requires an incompatible/destructive backend downgrade, recover that version into a separate Supabase recovery/staging project or implement a forward-compatible fix. A fallback is incomplete until the app and backend pass compatibility tests together.

## Historical migration gap

The live migration history begins before the migration files currently present in the repository. Raw historical migration SQL is intentionally not copied blindly because historical statements may contain old privileged configuration. The current-state recovery baseline is the safe reconstruction source for that legacy gap, while the version/name migration manifest records what was applied.

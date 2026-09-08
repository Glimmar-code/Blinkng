# Blinkng release safety and rollback policy

This document is mandatory for risky Blinkng releases.

## Environment flow

`Testlab` is the proving branch. `main` is the known-good production branch.

Production changes must follow this path:

1. Make risky code changes in `Testlab` (or a feature branch targeting `Testlab`).
2. Apply Supabase migrations, Auth/RLS changes, Storage changes, Edge Functions, triggers, RPCs, cron/webhooks, and other backend changes to a Supabase preview/staging environment first.
3. Use staging-only test accounts and test data. Never use production service-role credentials in an Android or Windows client.
4. Run the Android quality gate, Windows desktop quality gate, Windows parity gate, and Supabase safety gate.
5. Install the generated Testlab APK/Windows build and run the smoke-test checklist below.
6. Fix or revert failures while `main` and production remain untouched.
7. Open a pull request from `Testlab` to `main` only after the relevant checks pass.
8. Review the production migration plan and rollback plan.
9. Merge only after the change is verified.
10. Promote the tested Supabase migration/Edge Function changes to production.
11. Create a release tag/version for the exact production commit and keep the previous known-good version available as the rollback reference.

## Required smoke tests

Run the tests that are relevant to the change, and for major releases verify at minimum:

- cold start and warm start
- sign up, sign in, sign out, Google sign-in, password reset, account switching
- profile load/edit and verified badges
- feed, reels, create post, comments, likes, views, sharing
- messages, delivery/read state, notifications and notification navigation
- voice/video call start, ringing, answer/reject/end and missed-call state
- Blink Coin balances, purchases, gifts, boosts and VIP state
- marketplace and games/connect features touched by the release
- admin access boundaries and owner-admin protections
- Android and Windows feature parity for user-facing changes
- offline/reconnect behavior where relevant
- no production credentials, secret keys, service-role keys or private test data in source/build artifacts

## Database migration rules

- New schema changes belong in `supabase/migrations/`.
- Prefer additive, backwards-compatible migrations first.
- Avoid dropping a table/column in the same release that introduces its replacement. Add the replacement, migrate/backfill, verify, then remove the old object in a later release.
- A destructive migration must contain `-- destructive-change-reviewed` and a `-- rollback-plan:` comment explaining the recovery path.
- RLS/policy changes must be tested using both allowed and denied users in staging.
- `SECURITY DEFINER` functions require explicit authorization review; do not use `SECURITY DEFINER` merely to bypass an RLS error.
- Run Supabase security/performance advisors after DDL changes and resolve or explicitly review new warnings before production promotion.

## Rollback rules

### Code/app rollback

If a production code release is bad, revert the bad commit (or restore the previous known-good source), rerun the quality gates, then publish a new Android/Windows version. Do not rewrite shared production Git history just to hide the bad commit.

For Android, a rollback release still needs a higher `versionCode` than the broken published build.

### Supabase rollback

Database rollback is normally a new forward migration that restores compatibility. Never blindly reverse a migration after production data may have been written in the new format.

Before destructive production work, confirm a recoverable database backup exists. Supabase Storage objects require their own backup/recovery plan because database rollback alone does not restore deleted Storage files.

## Release versioning

Use semantic-style release tags such as `vMAJOR.MINOR.PATCH` once the actual release version is known. Do not invent a tag merely to mark an untested commit.

Each production release record should identify:

- exact commit SHA
- Android versionName/versionCode
- Windows package version
- Supabase migration range promoted
- test/check results
- rollback reference (previous known-good release)

## Emergency exception

An emergency production hotfix may bypass lengthy manual testing only when delaying the fix is more harmful than the change itself. It must still use a branch/PR, receive automated quality checks where possible, have a rollback plan, and be followed by full Testlab/staging verification. Direct destructive experimentation on production is never an emergency procedure.

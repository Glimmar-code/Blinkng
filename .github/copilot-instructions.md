# Blinkng Repository Instructions

## Mandatory Supabase → App → Release Rule

This rule applies to every developer, AI coding agent, GitHub Copilot task, pull request, fix, feature, refactor, and release in this repository.

### Core rule

**No Supabase change is considered complete until the corresponding change is represented in this GitHub repository, integrated with the Android app where applicable, tested, and included in the next app release.**

The required lifecycle is:

`Supabase change → record/sync change in GitHub → update Android app integration → test backend + app together → bump/release next app version → document change`

A Supabase change must never exist only in the Supabase Dashboard without a matching source-controlled representation or recovery record in this repository.

---

## What counts as a Supabase change

This includes, but is not limited to:

- Database tables
- Columns and data types
- Constraints
- Foreign keys
- Indexes
- Enums
- Views
- SQL functions / RPCs
- Triggers
- Row Level Security (RLS)
- Grants and permissions
- Auth configuration that affects app behavior
- User/profile schema
- Realtime configuration
- Storage buckets
- Storage policies
- Edge Functions
- Cron jobs
- Queues
- Webhooks
- Coin/balance logic
- Verification logic
- Subscription/VIP logic
- Post/reel boost logic
- Notifications
- Messaging/chat structures
- Admin permissions
- Marketplace structures
- Leaderboard structures
- Moderation/reporting structures
- Any backend contract consumed by the Android app

---

## Required procedure for every Supabase change

### 1. Capture the backend change in the repository

Every schema or backend change must be represented under the repository's `supabase/` source-of-truth area using an appropriate migration, SQL definition, Edge Function, configuration file, recovery documentation, or other reproducible source file.

Never rely on memory or on a Dashboard-only modification.

If the change was made directly in the remote Supabase project first, inspect/pull the actual remote state and create the corresponding repository change before continuing.

Do not invent migration history. Use the current Supabase CLI/MCP-supported workflow and verify the generated migration/history.

### 2. Determine the Android impact

Inspect all Android code that reads, writes, calls, subscribes to, or depends on the changed Supabase resource.

Update all affected layers as necessary, including:

- Kotlin data models
- DTOs / serialization models
- Repositories
- Supabase queries
- RPC calls
- Authentication/session handling
- Realtime subscriptions
- Storage access
- ViewModels
- Use cases/services
- UI states
- Navigation/deep links
- Error handling
- Loading/empty states
- Caching/offline behavior
- Tests

Do not leave the Android client using an outdated schema or contract.

### 3. Preserve compatibility

Before changing or removing an existing Supabase field/API used by released app versions, check backward compatibility.

Prefer additive migrations and safe rollout patterns. Avoid destructive schema changes that can immediately break users running an older APK.

When a breaking change is unavoidable, introduce a compatibility/migration strategy first and document it.

### 4. Security review is mandatory

For every relevant Supabase change:

- Keep RLS enabled on exposed tables.
- Verify SELECT/INSERT/UPDATE/DELETE policies independently.
- Ensure UPDATE policies contain correct ownership checks and `WITH CHECK` where required.
- Never use editable user metadata as an authorization source.
- Never expose `service_role`, secret keys, database passwords, SMTP passwords, OAuth client secrets, signing secrets, or other privileged credentials in Android code or committed files.
- Review privileged SQL functions carefully.
- Verify Storage policies for uploads, reads, updates/upserts, and deletes.

### 5. Test Supabase and Android together

A Supabase change must not proceed to release based only on SQL succeeding.

Test the complete affected user flow against the intended backend environment.

At minimum, verify:

- Migration/schema is valid.
- Expected app read succeeds.
- Expected app write succeeds.
- Unauthorized access is rejected.
- Existing data still works.
- Relevant RLS policies behave correctly.
- Realtime/Storage/Edge Functions work when affected.
- Android compiles.
- Relevant unit/integration/regression tests pass.
- Existing user flows affected by the change still work.

If a test fails, the release is blocked until the failure is fixed or explicitly documented as unrelated and safely waived.

### 6. Include the change in the next app release

Every completed Supabase change belongs to the **next Blink Android release**.

Before releasing:

- Ensure all corresponding repository changes are committed.
- Ensure the Android client contains all required integration changes.
- Increment the appropriate Android version/versionCode according to the project's versioning policy.
- Include the Supabase/backend change in release notes or the release changelog.
- Run the Android quality/build gate.
- Do not publish/mark the release ready while required Supabase integration work is missing.

If the Supabase change requires no client-code modification, still record it in the repository and release notes and verify that the current Android client is compatible with it before the next release.

### 7. Start the next cycle cleanly

After a release is completed, any new Supabase modification starts a new release cycle:

`New Supabase change → new repository backend change → any required Android update → tests → next app version/release`

Do not silently place a new Supabase change into an already-finished release.

---

## Release blocking conditions

The next Blink release MUST be blocked if any of the following is true:

- A Supabase Dashboard change is not represented in GitHub.
- A required migration/recovery definition is missing.
- Android code still references an obsolete Supabase contract.
- A schema change can break the currently supported app without a compatibility plan.
- RLS/security behavior has not been checked for an affected exposed resource.
- Required backend/app tests are failing.
- A required Edge Function or Storage change has not been deployed/verified.
- A secret has been committed or exposed to the Android client.
- Release notes omit a material Supabase change.

---

## Source-of-truth principle

The live Supabase project is the runtime environment. This GitHub repository must remain the recoverable and reviewable source of truth for the backend definitions needed by Blink.

Whenever repository state and remote Supabase state differ, investigate the difference before making another release. Do not blindly overwrite production.

---

## Required completion report for Supabase-related work

Whenever an AI agent or developer completes a task that changed Supabase, the final task summary must state:

1. What changed in Supabase.
2. Which migration/config/function files record the change.
3. Which Android files were updated because of it.
4. What security/RLS checks were performed.
5. What tests/build checks passed or failed.
6. Whether the change is ready for the next Blink release.
7. Any manual Supabase Dashboard configuration still required.
8. Any backward-compatibility risk for users on an older APK.

Do not claim a Supabase-related feature is fully complete if any required app integration, migration capture, security verification, testing, or release preparation remains outstanding.

# Blinkng Repository Instructions

## Permanent BLINK release policy — no owner testing downloads

This instruction applies to every BLINK task, feature branch, pull request, agent, GitHub Actions workflow, and release. **Do not ask the repository owner to download or install an unfinished APK simply to test it, confirm it works, or report results back.** Mobile data is limited. The owner should install only the approved final release intended for actual daily use.

1. **Stage first:** Develop non-destructively in a current `Testlab-*` branch, preserve all existing functionality and settings, and reconcile with the latest `main`. Do not bypass protected `main`.
2. **Automation first:** Use GitHub Actions, Android 15 emulators, UI/instrumentation tests, Android/Windows builds, backend/provider staging checks, CI artifacts, screenshot comparisons, security checks, and regression testing. Collect logs and fix failures without demanding owner-side APK installs.
3. **Only final APK:** For Android releases, test the signed *production candidate* on CI, including package/signature identity, install, previous-published-version upgrade without uninstall, and startup. Ensure the final download is published only after all required gates pass. The emulator install check is not proof that all user data or device/OEM flows survive.
4. **Physical-device exception:** For voice/video calls, WebRTC/JNI, Telecom, Bluetooth audio, OEM notifications, background behavior, and installation/updating on vendor-specific phones, require independent physical-device or hosted device-lab evidence when emulators cannot validate behavior. Do not automatically pay for device labs; ask before incurring costs. If no safe independent verification is available, hold the high-risk release in Testlab and report the precise unverified risk rather than claiming success.
5. **AI-authorized release control, no GitHub visit:** The owner can direct ChatGPT or another trusted GitHub-connected AI to fix, merge or publish BLINK. A connected agent may prepare PRs, inspect CI, add the `auto-promote` label and complete GitHub actions without asking the owner to open GitHub, click Approve, post a `BLINK APPROVE` comment, or perform manual label management. The agent must opt in only on the latest reviewed PR revision; a new push invalidates `auto-promote`, `staging-verified`, and `device-verified`. Keep branch protection, fail-closed CI, staging/provider/device evidence and security gates for sensitive changes; do not assert verification without evidence.
6. **One consolidated release:** Batch related verified app updates wherever possible. Avoid multiple successive owner downloads. Report final commit, test evidence, GitHub release and actual APK version only after publication is verified. The owner's use of the published app is not a substitute for pre-release QA.
7. **Never overclaim:** Differentiate implementation, emulator-tested, independently device-tested, merged, built, published, and confirmed on an owner's phone. The last cannot be asserted without evidence, and routine releases should not depend on asking the owner for a test-install confirmation.

See `docs/testlab-auto-promotion.md` for the exact CI and release gates.

## Always-on CI failure recovery and production publishing

For every PR, push, and production release, never treat a failed check as completion.
The trusted `BLINK CI automatic recovery` workflow examines failed GitHub
Actions jobs on the **current** `Testlab` / `Testlab-*` revision and attempts
at most two failed-job retries (three attempts total). It collects failing job
and step names, links to the error logs, and creates or updates a GitHub issue
when a failure persists or a security check cannot safely be retried.
Retries address intermittent runners/services; they do **not** fix broken code.

When a coding agent is connected and assigned a recovery blocker, it must
open the linked job logs, diagnose the actual root cause, apply a minimal
non-destructive code/config repair on an up-to-date `Testlab-*` branch,
add regression coverage, rerun all relevant CI checks, and repeat while
each iteration makes meaningful progress. Never bypass security, signing,
RLS, backend/staging/device verification, branch protections, or required checks.
Do not loop forever or overwrite newer changes; stop and report blockers
that cannot be fixed or independently verified.

After an agent has verified the exact current commit, the existing guarded
Testlab auto-promotion controller may merge it to `main` with its explicit
opt-in and attestations. Never auto-promote purely because retries passed.
Only a successful verified main merge may dispatch production workflows.
`Signed Android release` must succeed before `Publish latest APK` runs;
publishing must preserve signing identity, reject stale builds, and verify
the public artifact. The previous good APK remains available on failures.
Changes not affecting Android should not create unnecessary APKs.
Main publishing failures may receive bounded retries, but never source
changes directly on `main` or automatic unsafe secret/config changes.

## BLINK mandatory auto-fix → verify → Testlab → main → update app

User command (repeat for each BLINK repair/release request):

> Fix all identified errors professionally. Reverify every applicable check. If a test fails, read its real error log, correct the underlying code, commit, and run tests again. Repeat while meaningful progress is possible. After ALL relevant quality gates AND backend/provider integration checks pass on the latest candidate commit, push the verified change to Testlab, merge to main via a passing PR, and verify that the signed Android release and latest APK publication actually succeed. Report the real APK link and final SHA. Never claim an unverified fix, merge, deployment, or app update.

1. Inspect current Android, Windows, Supabase/backend, web/game contracts, test failures and PR state; preserve working behavior and platform parity.
2. Fix in a branch targeting Testlab. Run checks relevant to the diff: Android build/unit/lint for Android inputs, Windows parity for affected Android user features and full desktop packaging for Windows/shared changes, migration SQL/RLS/Edge Function checks for backend changes, and signing/release checks when publishing an APK. Avoid unrelated/duplicate tests. When uncertain, run all checks and explain which suites ran versus were not applicable. Match results to the candidate commit SHA.
3. If a check fails, diagnose its logs, make a real code/config fix, and rerun. Transient CI retries are NOT repairs. Never skip failures or loop forever; report an unsolved blocker clearly.
4. **Backend integration is a separate mandatory gate.** GitHub static/CI green does not establish that Supabase hosted migrations, Brevo emails, Auth templates/redirects, or new Edge Functions are deployed and functional. Test safely in local/isolated/staging infrastructure, then verify actual behavior. No production-first experiments or fabricated successes.
5. **No paid staging without approval.** The owner declined Supabase's fee for a hosted testing branch. Do not incur fees or activate paid resources automatically. If no safe free staging option can validate a required change, hold the production merge and explain the blocker.
6. Keep API keys, Supabase service-role tokens, mail credentials and personal data out of GitHub, apps and logs. Do not activate/replace signup-confirmation email settings without separate explicit instruction.
7. If and only if every applicable gate passes, merge to Testlab and then promote Testlab to main through standard review/branch rules. Confirm both merge SHAs, don't force/bypass checks.
8. After the main merge, inspect `Signed Android release` and `Publish latest APK` workflows, verify the signed downloadable APK exists, and report its actual release URL. A main commit does not silently install an APK on users' phones. Report Windows output and manual app-store steps separately.
9. When blocked, leave main and production backend unchanged, state exact failing or unavailable checks, and supply a safe next action. The instruction is a release policy, NOT an unattended agent or an ability for GitHub Actions alone to autonomously rewrite broken code.

**Email-specific hold:** PR #299 was merged to Testlab, but actual Brevo delivery, hosted Supabase migration/Edge Functions, email preference/unsubscribe flows and end-to-end tests have not passed. Social email sending stays disabled by default. Do not promote the email feature to production main solely on green static checks. See `docs/essential-email-brevo-testlab.md`.

Full reusable command: `.github/BLINK_AUTO_FIX_RELEASE_COMMAND.md`.


## Mandatory Supabase → App → Release Rule

This rule applies to every developer, AI coding agent, GitHub Copilot task, pull request, fix, feature, refactor, and release in this repository.

### Core rule

**No Supabase change is considered complete until the corresponding change is represented in this GitHub repository, integrated with the Android app where applicable, tested, and included in the next app release.**

The required lifecycle is:

`Supabase change → record/sync change in GitHub → update Android app integration → test backend + app together → bump/release next app version → document change`

A Supabase change must never exist only in the Supabase Dashboard without a matching source-controlled representation or recovery record in this repository.

---

## Mandatory version-bound Supabase fallback / rollback rule

**A Blink Android/Git version and its compatible Supabase backend are one release unit.**

Whenever any developer or AI agent checks out, restores, reverts, rolls back, rebuilds, or falls back to another Blink commit/tag/release/version, it MUST also identify the matching Supabase backend state for that version. Never consider an app rollback complete while leaving a mismatched backend behind.

The matching backend recovery set includes all source-controlled Supabase definitions that can affect runtime behavior:

- migrations and recovery baselines
- schemas/tables/columns/enums/default/generated expressions
- constraints and indexes
- SQL functions/RPCs/procedures/views
- triggers
- RLS state, policies, grants and permissions
- Storage bucket definitions and Storage policies
- Realtime publication membership
- cron/scheduled jobs
- Edge Function source and non-secret function configuration, including `verify_jwt`
- controlled reference/configuration seed data required by the app
- non-secret Auth/client configuration
- required secret/environment **names** and recovery instructions

Before a fallback version is declared usable, test that exact Android version against the restored/compatible backend and run the relevant RLS/security, backend smoke, integration, regression, and Android build checks.

### Never destroy production merely to match an older APK

Do not blindly downgrade the live production schema, drop newer database objects, delete newer rows, remove newer user uploads, or otherwise destroy post-release data to force compatibility with an older app. If an older version requires an incompatible/destructive backend state, recover it in an isolated Supabase recovery/staging project or implement a forward-compatible compatibility layer. Preserve production data first.

### Complete-code recovery requirement

When a user asks to “restore everything,” “move everything,” “fallback to another version,” “recover another version,” or equivalent wording, interpret **everything** as the complete non-secret source-controlled app + Supabase code/configuration set. Never interpret it as permission to commit private production rows, Auth users, password hashes, messages, uploaded file bytes, service-role keys, database passwords, OAuth/SMTP/Firebase/payment secrets, or other private credentials/data.

Consult `supabase/RECOVERY.md` and the matching snapshot/manifest under `supabase/recovery/` before carrying out a version recovery.

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
- Edge Function non-secret configuration
- Cron jobs
- Queues
- Webhooks
- Controlled backend reference/configuration data
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
- The version being restored has no identifiable compatible Supabase state.
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
2. Which migration/config/function/recovery files record the change.
3. Which Android files were updated because of it.
4. What security/RLS checks were performed.
5. What tests/build checks passed or failed.
6. Whether the change is ready for the next Blink release.
7. Any manual Supabase Dashboard configuration still required.
8. Any backward-compatibility risk for users on an older APK.
9. Which app version/commit the resulting backend state is compatible with or whether compatibility is intentionally broad.
10. Whether recovery of this version would require any secret values/data backups that are intentionally kept outside Git.

Do not claim a Supabase-related feature or recovery is fully complete if any required app integration, migration capture, recovery baseline, security verification, testing, or release preparation remains outstanding.

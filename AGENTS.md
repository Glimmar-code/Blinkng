# Blinkng engineering instructions

## Product rule: Android and Windows are one Blinkng product

Blinkng must be developed as one product with two supported client surfaces:

- Android APK (`app/`)
- Windows desktop (`desktopApp/`)

The same Blink account, Supabase backend, permissions, database contracts, storage, ranking rules, coins, verification, messaging, moderation, admin rules, and feature semantics must be used on both platforms.

## Mandatory shared-first rule

For every new or changed user-facing feature, start by deciding what is platform-independent. Put reusable business logic, models, validation, routing contracts, repository logic, API contracts, state machines, formatting, and reusable Compose UI in shared code as the migration progresses.

Android and Windows should contain only the platform adapters that are truly platform-specific.

Examples of Android-only adapters include Activity/Context, Android Intents, CameraX, WorkManager, Firebase Messaging delivery, Google Play services, Android Credential Manager, Android permissions, and Media3 integrations.

Examples of Windows-only adapters include desktop windows, system tray, Windows notifications, desktop file dialogs, browser OAuth callback handling, drag-and-drop, native menu bars, and EXE/MSI packaging.

## REQUIRED FEATURE PARITY RULE

**Anything added to or changed in the Android APK that affects a Blinkng user must be added to the Windows version in the same pull request.**

A change is not complete merely because the APK works. The pull request must do one of the following:

1. implement the feature once in shared code so Android and Windows receive it automatically; or
2. update both the Android implementation and its Windows counterpart; or
3. if the feature is genuinely impossible or meaningless on Windows, record an explicit platform exception in `platform-parity/changes.md` with the reason and the closest Windows equivalent.

Do not merge an Android-only user-facing feature that silently leaves Windows behind.

## AI/coding-agent workflow for every Blinkng feature

Whenever an AI agent or developer is asked to add, fix, remove, redesign, or change an Android feature:

1. inspect the existing Android behavior and backend contract;
2. identify the reusable/shared part;
3. implement or update shared logic first whenever practical;
4. wire the Android surface;
5. wire the Windows surface;
6. preserve the same route IDs, permission rules, data semantics, and server behavior;
7. update `platform-parity/changes.md`;
8. run Android quality checks;
9. run Windows desktop build checks;
10. only consider the work finished when both supported clients are accounted for.

## Backend rule

Do not create a second production backend for Windows. Windows must use the same Blinkng Supabase project and server-authoritative rules as Android. Never ship service-role secrets in either client.

## No production mock-data rule

The Windows production client must not invent fake posts, reels, users, marketplace listings, leaderboards, coins, notifications, messages, or analytics. Placeholder UI is allowed only while a route is being ported and must be clearly marked as migration UI.

## UX rule

Feature parity does not mean pixel-for-pixel phone stretching. Windows should use desktop conventions such as sidebar navigation, keyboard/mouse support, resizable panes, context menus, desktop notifications, drag-and-drop, and wide-screen layouts while preserving the same Blinkng feature and result.

## Compatibility rule

Never break the Android APK just to make desktop compile. Platform migration must be incremental, tested, and backward-compatible with existing Supabase data and released Android clients.

## REQUIRED TESTLAB SAFETY RULE

`main` is the production / known-good branch. Risky, experimental, large, destructive, or potentially breaking work must not be developed directly on `main`.

For risky changes:

1. make the change in `Testlab` first, or in a feature branch that targets `Testlab`;
2. build and test the affected Android, Windows, backend, and Supabase behavior as applicable;
3. fix or revert failures in the test branch while keeping `main` untouched;
4. merge into `main` only after the relevant tests pass and the change is confirmed to work correctly.

Supabase/database changes must also be tested safely before production. Prefer versioned migrations and rollback-aware changes instead of untracked destructive production edits.

## REQUIRED SUPABASE PREVIEW / STAGING RULE

Any risky or production-affecting Supabase change must be validated in a Supabase preview/staging environment before it is applied to the production Supabase project whenever a preview/staging environment is available.

This includes, where applicable:

- database schema and migration changes;
- RLS policies and permissions;
- Auth configuration and auth flows;
- Edge Functions, triggers, RPC/functions, cron jobs, and webhooks;
- Storage buckets and Storage policies;
- Realtime behavior;
- destructive data operations or backfills.

Use production-compatible test data without copying unnecessary sensitive user data. Run the relevant app/backend tests against staging, confirm migrations and rollback/recovery behavior, and only promote the change to production after staging passes. Production must not be used as the first place to experiment with a risky Supabase change.

This Supabase preview/staging-first policy and the Testlab-first policy are the defaults for future Blinkng development unless the repository owner explicitly instructs otherwise.

## BLINK mandatory auto-fix → verify → Testlab → main → update app

User command (repeat for each BLINK repair/release request):

> Fix all identified errors professionally. Reverify every applicable check. If a test fails, read its real error log, correct the underlying code, commit, and run tests again. Repeat while meaningful progress is possible. After ALL relevant quality gates AND backend/provider integration checks pass on the latest candidate commit, push the verified change to Testlab, merge to main via a passing PR, and verify that the signed Android release and latest APK publication actually succeed. Report the real APK link and final SHA. Never claim an unverified fix, merge, deployment, or app update.

1. Inspect current Android, Windows, Supabase/backend, web/game contracts, test failures and PR state; preserve working behavior and platform parity.
2. Fix in a branch targeting Testlab. Verify Android unit/lint/build/APK, Windows desktop/parity, migration SQL/RLS, Edge Function type checks, relevant feature/runtime tests, and signed-release checks. Match every result to the candidate commit SHA.
3. If a check fails, diagnose its logs, make a real code/config fix, and rerun. Transient CI retries are NOT repairs. Never skip failures or loop forever; report an unsolved blocker clearly.
4. **Backend integration is a separate mandatory gate.** GitHub static/CI green does not establish that Supabase hosted migrations, Brevo emails, Auth templates/redirects, or new Edge Functions are deployed and functional. Test safely in local/isolated/staging infrastructure, then verify actual behavior. No production-first experiments or fabricated successes.
5. **No paid staging without approval.** The owner declined Supabase's fee for a hosted testing branch. Do not incur fees or activate paid resources automatically. If no safe free staging option can validate a required change, hold the production merge and explain the blocker.
6. Keep API keys, Supabase service-role tokens, mail credentials and personal data out of GitHub, apps and logs. Do not activate/replace signup-confirmation email settings without separate explicit instruction.
7. If and only if every applicable gate passes, merge to Testlab and then promote Testlab to main through standard review/branch rules. Confirm both merge SHAs, don't force/bypass checks.
8. After the main merge, inspect `Signed Android release` and `Publish latest APK` workflows, verify the signed downloadable APK exists, and report its actual release URL. A main commit does not silently install an APK on users' phones. Report Windows output and manual app-store steps separately.
9. When blocked, leave main and production backend unchanged, state exact failing or unavailable checks, and supply a safe next action. The instruction is a release policy, NOT an unattended agent or an ability for GitHub Actions alone to autonomously rewrite broken code.

**Email-specific hold:** PR #299 was merged to Testlab, but actual Brevo delivery, hosted Supabase migration/Edge Functions, email preference/unsubscribe flows and end-to-end tests have not passed. Social email sending stays disabled by default. Do not promote the email feature to production main solely on green static checks. See `docs/essential-email-brevo-testlab.md`.

Full reusable command: `.github/BLINK_AUTO_FIX_RELEASE_COMMAND.md`.

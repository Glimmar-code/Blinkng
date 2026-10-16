# BLINK — Auto-Fix → Reverify → Testlab → Main → App Update

## Canonical command from the BLINK owner

> When all required checks pass, push to Testlab, verify there, merge the approved changes to main, build/publish my updated app, and verify delivery. If an error appears, inspect its logs, fix the underlying cause, rerun verification, and repeat until the checks pass. Do not skip failed checks or call incomplete work finished.

**This is the default execution policy for every BLINK fix, upgrade and feature request executed by a coding agent with repository access.** The words "fix", "check again", "merge" and "update my app" inherit this procedure unless the owner explicitly narrows the scope.

### Executable agent procedure

1. **Inspect the real state first.** Read `AGENTS.md`, `.github/copilot-instructions.md`, the affected client/backend files, GitHub PR/branch states and failing check logs. List relevant Android, Windows, shared, web, Godot and Supabase contracts. Never guess that a feature is deployed because code exists.
2. **Develop safely.** Work on a feature branch targeting `Testlab`; preserve account, session, backend and Android/Windows parity. Commit actual changes and link the PR. Never develop a risky feature directly on `main`.
3. **Run the applicable gates.** At minimum, consider Android unit tests, lint, instrumentation compilation, APK/AAB build, release smoke, native BLINK LIFE content checks, Windows compilation/package tests, parity, web checks, SQL syntax/migration safety, RLS/security, Edge Function type-checking, regressions and secrets scanning. Only tests applicable to the change are mandatory; document why others are not relevant.
4. **Auto-fix in the active coding session.** On every failing gate, read the full logs, reproduce the problem where possible, fix the root cause (never merely silence the test), commit the fix and rerun **both** the failed test and all required integration/regression gates. Continue until all required checks pass. Stop with a precise blocker if a required external environment, secret, authorization, provider, device or integration is missing; do not claim a pass for an unrun test. Do not retry indefinitely without making progress.
5. **Promote to Testlab.** Merge the feature PR into `Testlab` **only after** its required gates pass. Confirm the merge commit and rerun/inspect integration checks for `Testlab` at the merged commit. If a new error appears, fix and retest **on Testlab or a follow-up Testlab PR** before considering production.
6. **Validate backend and real integrations.** Changes to Supabase schemas, RLS, Auth, Storage, Edge Functions, cron, SMTP/Brevo, AdMob or other live integrations require a disposable/local or isolated staging test plus controlled end-to-end verification of the actual changed flow. Static-only/CI-only checks do **not** prove real delivery. Never use production as a first-run staging environment. **Use no paid Supabase branch/project or other paid service unless the owner separately and explicitly approves its price**; free local testing is preferred. If a necessary test cannot run safely at no cost, **STOP and report it**.
7. **Merge to main only when release-ready.** Create a focused PR containing **only the verified changes**. Do **not** blindly merge all of `Testlab` if it contains unrelated, unverified features. Verify the specific revision's required GitHub checks, backend compatibility, secret availability and any real staging results. Do not merge while any requirement is failed, skipped-but-required, incomplete or unknown.
8. **Update the app through the existing distribution path.** After a verified `main` merge, let the existing signed-release and verified APK publishing workflows run, verify the correct signer, version, artifact, release/check status and download link, and report the result. If they fail, diagnose, fix and rerun the applicable release checks. The existing installed Android app is **not remotely replaced** by a GitHub merge; only claim an actual device update if an authenticated store/in-app update mechanism confirms it.
9. **Report truthfully.** Include changed files, PR and commit links, gate outcomes, failures corrected, Supabase/staging deployment state, actual live-email or device verification, release artifact/version/link, and any remaining blocker. Distinguish `code staged`, `merged Testlab`, `merged main`, `backend live`, `APK published` and `app installed`.

### Hard rules — cannot be overridden by the auto-fix loop

- Protect existing BLINK users. Never skip RLS/security tests, bypass protection on `main`, disable important warnings, or introduce mocked live data to make a check green.
- Never reveal or commit provider tokens, SMTP/Brevo API keys, Supabase service-role keys, signing keys, recovery URLs containing tokens or account data.
- Do **not** activate or modify **signup confirmation email** settings during BLINK email work. Email-address change security confirmation is distinct from signup confirmation.
- No spending: the owner previously refused paid Supabase testing. A request to "fix and merge" is **not** purchase authorization.
- "Automatically fix" describes what an **active coding agent** should do with available tools. GitHub Actions can test, rebuild and report failures but cannot independently invent safe source-code fixes without a separately configured and authorized coding agent. Never promise an unattended infinite self-healing loop.
- Missing approval, provider verification, staging environment, endpoint credentials, real-device evidence or test result is a **release blocker**, not a successful pass.
- Preserve existing `AGENTS.md` parity and staging-first rules and `.github/copilot-instructions.md` version-aligned backend safeguards.

## Short invocation

`BLINK AUTO-FIX: inspect → fix → test → auto-refix failures → reverify → Testlab merge → Testlab integration verification → focused main merge → signed app build/publish → verify actual delivery. If a required validation is unavailable, stop and report the blocker; never pay, bypass checks or claim a device update without evidence.`

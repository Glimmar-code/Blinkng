# BLINK professional auto-fix, verification, Testlab, production and APK command

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

### Operator shorthand
```text
BLINK AUTO-FIX AND RELEASE:
Diagnose all reported errors. Fix root causes. Run complete applicable tests.
If checks fail, read logs → patch → re-test. Repeat until verified or blocked.
When all AND real backend/provider dependencies pass:
feature branch → Testlab → verified main PR → signed APK → published release.
Confirm SHA/artifacts/URLs. No fake pass or unsupported silent app update.
Never incur paid staging fees without approval. Preserve the signup email policy.
```

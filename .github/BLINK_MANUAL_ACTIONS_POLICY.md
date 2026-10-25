# BLINK — GitHub Actions run by the repository owner

**Control:** Workflows defined in `.github/workflows/` now use
`workflow_dispatch` (manual initiation) instead of automatic PR, push,
release, workflow-completed and scheduled triggers. Some reusable workflows
may also retain `workflow_call` for explicitly requested callers.

New code should use a new, short-lived feature branch from the latest
`main` and a PR targeting **protected `main`**.

## Running checks yourself

1. In GitHub open **Actions**, choose the applicable test workflow, click
   **Run workflow**, and select the exact feature branch of the PR.
2. Run **Android quality gate**, **Windows desktop quality gate**, **Supabase
   safety gate**, **Secret leak guard** and **APK delivery integrity**.
   Run other relevant tests: Android runtime and release smoke, web parity,
   domain integration, payment or backend tests, etc.
3. Wait until *current-commit* results are green, and fix failing checks on
   the feature branch (not directly on protected main). Re-run all affected
   checks after any code change.
4. GitHub's required-branch checks stay enabled. A manual workflow with
   missing or outdated status **does not satisfy** branch protection. If
   GitHub doesn't associate manual runs with the PR's required checks, that
   PR will remain blocked. Do not bypass branch protection; adjust the
   check/reporting integration with proper review.
5. You decide when to click **Merge pull request**. Alternatively, the trusted
   `Automatically merge verified BLINK PRs into main` workflow can be
   explicitly run by the repository owner when the feature PR has the
   `auto-promote` label, all required checks and the exact
   `BLINK_AGENT_VERIFIED_SHA` marker. It never automatically runs.

## Publishing separately and manually

1. Choose **Signed Android release** in Actions, select `main`, click
   **Run workflow**, and confirm it succeeds. It checks that the source
   is on main and verifies signing and APK installation.
2. Copy that successful signed release workflow's numeric run ID.
3. Choose **Publish latest APK**, select `main`, click **Run workflow**,
   and enter `signed_run_id`. It refuses a failed release, a run not
   started manually, a release from another repository or a stale main SHA.
4. Deploy the site separately with **Deploy Blink web** from `main` only.
   Optional website release notifications, IndexNow, referral push and
   payment-email batch processing are **not scheduled** anymore. Run them
   manually when needed. Delaying these operational jobs may delay actual
   customer-facing updates and messages.

The owner's GitHub username for owner-gated release jobs is
`Glimmar-code`. Changing the owner requires updating the explicit gate.

## Important operational effect

- Closing or merging a PR no longer automatically builds/publishes the APK
  or deploys the web frontend.
- Manual controls reduce unrequested CI usage but eliminate automatic
  alerts, scheduled queue draining, and continuous security and regression
  testing. Those checks must be run and monitored by the owner.
- Existing Actions history cannot be erased by changing workflow triggers.
- This change does not delete old feature branches or silently merge any
  unfinished PR.

# BLINK Testlab → main automatic promotion

Changes should be made on a current `Testlab-*` feature branch (or the shared `Testlab` branch) and opened as a pull request targeting `main`.

## Automatic release contract

1. Finish the work and check every task in the PR description (`- [x]`).
2. Run the matching Android, Windows, Supabase, release, and website CI checks. For Android release changes, verify the minified/installable build and device behavior as required by the feature.
3. Mark the PR **Ready for review**, then apply the **`auto-promote`** GitHub label. This is the once-per-PR signal that work and review are finished; **you do not need to ask anyone to merge afterward**.
4. For Supabase migrations/functions, JNI/ProGuard, or production release/promotion code, first verify the staging environment and add **`staging-verified`**. Checking syntax or building a disposable APK alone does **not** establish staging validity.
5. GitHub Actions checks readiness on relevant PR changes and every 15 minutes. When all gates pass, it merges the PR into `main` using a guarded squash merge and the exact tested head revision.
6. The GitHub Actions bot explicitly starts the **Signed Android release** and/or **Deploy Blink web** workflow when the merged files require it. Those workflows remain responsible for production signing, package integrity and site deployment. A merge does **not** mean the APK has shipped until release publishing succeeds.

## Fail-closed guardrails

- Never promote draft PRs, untrusted forks, PRs outside `Testlab`/`Testlab-*`, unchecked PR task lists, or PRs explicitly saying `do not auto-merge`.
- Require a clean merge, zero commits behind current `main`, and check results tied to the current PR, head SHA **and** base SHA.
- Require Android unit/lint/build, Windows parity/build, Supabase safety, and secret scans, plus relevant runtime smoke, minified release installation, web, domain and APK-integrity gates.
- A missing, pending, failed or cancelled required check blocks promotion. The newest result wins; a stale green result cannot override a newer failure. Other failing CI jobs block promotion too.
- Changed backend migrations/functions and production release-sensitive code require the independent `staging-verified` attestation, not merely a green CI result.
- Unresolved requested changes, outdated branches, merge conflicts, permission failures or an incomplete GitHub API response block the merge.
- The action never checks out untrusted PR code with its write-enabled token.
- Promotion is **opt-in per PR**. Existing open PRs are not bulk-merged, even if some prior checks passed.
- If automated merging reports an error, inspect the **Automatically promote verified Testlab** workflow run; reruns are safe while the PR is open.

**Important:** The old shared `Testlab` branch can be far behind `main`. Use a new `Testlab-*` branch based on current `main` for each task. Do not merge old Testlab wholesale without reconciling it and re-running every gate.

This policy applies to source merging. A database migration in the repository is not automatically applied to the production Supabase project, and a signed APK is not guaranteed until its release workflow reports success.

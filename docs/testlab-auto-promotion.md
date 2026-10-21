# BLINK Testlab → main automatic promotion

Changes should be made on a current `Testlab-*` feature branch (or the shared `Testlab` branch) and opened as a pull request targeting `main`.

## Automatic release contract

1. Finish the work and check every task in the PR description (`- [x]`).
2. Run the matching Android, Windows, Supabase, release, and website CI checks. For Android release changes, verify the minified/installable build and device behavior as required by the feature. The owner does not need to install staging APKs on a personal phone.
3. A connected ChatGPT/GitHub AI agent marks the PR **Ready for review** and applies **`auto-promote`** after verifying the latest head commit. This is the agent's explicit release opt-in, not a request for the owner to use the GitHub UI. A new code push revokes that opt-in until the agent re-verifies it.
4. For Supabase migrations/functions, JNI/ProGuard, or production release/promotion code, first verify the staging environment and add **`staging-verified`**. Checking syntax or building a disposable APK alone does **not** establish staging validity.
5. GitHub Actions checks readiness on relevant PR changes and every 15 minutes. When all gates pass, it merges the PR into `main` using a guarded squash merge and the exact tested head revision.
6. The GitHub Actions bot explicitly starts the **Signed Android release** and/or **Deploy Blink web** workflow when the merged files require it. Those workflows remain responsible for production signing, package integrity and site deployment. A merge does **not** mean the APK has shipped until release publishing succeeds.

## AI-controlled release, no manual owner approvals, and one final APK download

- **GitHub owner approval is not required.** No `BLINK APPROVE <SHA>` PR comments, clicking GitHub's Approve button, or manually adding GitHub labels. The owner can request fixes, approval for release, publishing, or rollback in ChatGPT or another **authorized GitHub-connected AI**; the connected agent performs the GitHub steps.
- **Explicit agent opt-in per tested revision:** The agent applies `auto-promote` to the exact reviewed Testlab PR only when implementation and relevant verification are complete. The branch protection rules and required GitHub Actions checks still apply. Unfinished/draft/unknown PRs are never auto-approved.
- **No stale promotion:** The `pull_request_target/synchronize` workflow removes `auto-promote`, `staging-verified`, and `device-verified` whenever a new commit is pushed to the PR. The agent must re-check the changed code and fresh CI results before restoring appropriate labels. Removal failures block automated promotion, not silently bypass this policy.
- **Sensitive changes still need stricter testing:** Paystack, login, Supabase, signing, release controller, JNI and security-related modifications keep all relevant CI and staging checks. In particular `staging-verified` requires actual independent integration evidence—not just passing syntax checks.
- **Physical-device exceptions:** Calling, WebRTC, Telecom, Bluetooth/audio routes, notification delivery, JNI-native binaries, and APK update/installer paths (or a PR labeled `needs-device-verification`) cannot be cleared solely by an emulator. Require independently reviewed real-device/hosted device-lab evidence and `device-verified` for the latest SHA. Never make the owner repeatedly install test-only APKs; if an independent test is impossible, explain the blocker and hold the release.
- **No owner beta-install loop:** CI covers debug/release builds and Android 15 emulators. The production signing workflow installs the actual signed candidate, tests upgrade from the previously published APK using `adb install -r`, and checks startup before publication. These checks do not establish every private-data or OEM behavior.
- **One final download:** Batch tested app changes into a signed release when appropriate. The owner downloads BLINK for normal use only after GitHub confirms that the final approved APK is published.
- **Owner/agent can stop promotion in chat:** Connected AI can remove `auto-promote` or close the PR. Do not purchase testing services or bypass protection to satisfy a release request.

## Fail-closed guardrails

- Never promote draft PRs, untrusted forks, PRs outside `Testlab`/`Testlab-*`, unchecked PR task lists, or PRs explicitly saying `do not auto-merge`.
- Require a clean merge, zero commits behind current `main`, and check results tied to the current PR, head SHA **and** base SHA.
- Require Android unit/lint/build, Windows parity/build, Supabase safety, and secret scans, plus relevant runtime smoke, minified release installation, web, domain and APK-integrity gates.
- A missing, pending, failed or cancelled required check blocks promotion. The newest result wins; a stale green result cannot override a newer failure. Other failing CI jobs block promotion too.
- Changed backend migrations/functions and production release-sensitive code require the independent `staging-verified` attestation, not merely a green CI result.
- Unresolved requested changes, outdated branches, merge conflicts, permission failures or an incomplete GitHub API response block the merge.
- The action never checks out untrusted PR code with its write-enabled token.
- Promotion is **opt-in per verified PR revision**. Existing open PRs are not bulk-merged, even if some prior checks passed. A connected AI agent, not the owner visiting GitHub, must actively opt in after reviewing the latest SHA.
- If automated merging reports an error, inspect the **Automatically promote verified Testlab** workflow run; reruns are safe while the PR is open.

**Important:** The old shared `Testlab` branch can be far behind `main`. Use a new `Testlab-*` branch based on current `main` for each task. Do not merge old Testlab wholesale without reconciling it and re-running every gate.

This policy applies to source merging. A database migration in the repository is not automatically applied to the production Supabase project, and a signed APK is not guaranteed until its release workflow reports success.

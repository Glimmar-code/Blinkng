# BLINK Testlab → main automatic promotion

Changes should be made on a current `Testlab-*` feature branch (or the shared `Testlab` branch) and opened as a pull request targeting `main`.

## Automatic release contract

1. Finish the work and check every task in the PR description (`- [x]`).
2. Run the matching Android, Windows, Supabase, release, and website CI checks. For Android release changes, verify the minified/installable build and device behavior as required by the feature. The owner does not need to install staging APKs on a personal phone.
3. Mark the PR **Ready for review**, then apply the **`auto-promote`** GitHub label. This is the once-per-PR signal that work and review are finished; **you do not need to ask anyone to merge afterward**.
4. For Supabase migrations/functions, JNI/ProGuard, or production release/promotion code, first verify the staging environment and add **`staging-verified`**. Checking syntax or building a disposable APK alone does **not** establish staging validity.
5. GitHub Actions checks readiness on relevant PR changes and every 15 minutes. When all gates pass, it merges the PR into `main` using a guarded squash merge and the exact tested head revision.
6. The GitHub Actions bot explicitly starts the **Signed Android release** and/or **Deploy Blink web** workflow when the merged files require it. Those workflows remain responsible for production signing, package integrity and site deployment. A merge does **not** mean the APK has shipped until release publishing succeeds.

## Smart owner approval and one final app download

- **Routine-only PRs** can merge automatically with `auto-promote` after the existing complete CI suite passes, without another SHA-specific owner comment. The deliberately narrow low-risk allowlist is documentation, website CSS, website PNG/JPEG/WebP images, and Android PNG/JPEG/WebP drawable/mipmap assets. A mixed PR is **sensitive**.
- **Sensitive PRs** include any executable or unknown file: authentication, Paystack, coins, Supabase, calling, Android source code, website JavaScript/HTML, build/signing, CI, and security policy. These still require `BLINK APPROVE <exact-head-SHA>` posted by `Glimmar-code` before auto-promotion. New commits invalidate approval. `BLINK REVOKE <SHA>` still takes precedence.
- **Physical-device exceptions:** calling, WebRTC, Telecom, Bluetooth/audio routes, notification delivery, JNI-native binaries, and APK update/installer paths (or a PR explicitly labeled `needs-device-verification`) cannot be cleared solely by an emulator. Require reviewed independent real-device/cloud device-lab evidence linked in the PR and the `device-verified` attestation for the **current commit**. Do not add the attestation just because unit tests passed.
- **No owner beta-install loop:** the repo tests debug builds, minified release variants, and Android 15 runtime in CI. Before an official APK can publish, `Signed Android release` installs the exact production-signed candidate on an Android 15 emulator, checks upgrade from the previous published APK using `adb install -r` (no uninstall), verifies the install state is continuous, and launches it without an immediate crash. Only a green signed-build workflow supplies the publishing workflow. This checks basic update and launch safety, **not** full account-data correctness or all OEM behavior.
- If independent physical-device evidence is essential but unavailable, **do not claim full verification or ask the owner to download a testing-only build**. Keep the sensitive release in Testlab pending a remote/device-lab or designated tester result. The owner downloads once, after the approved, published release is available.
- Removing `auto-promote` stops opt-in merging. Existing branch-protection requirements and publication-integrity checks still apply to every PR.

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

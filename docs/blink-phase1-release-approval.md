# BLINK Phase 1: production branch and release approval

## Production safety boundary

- `main` serves production and must be treated as known-good.
- Stage changes on `Testlab-*` branches (or on `Testlab`) first.
- A tested pull request is required for promotion; never force-push, rewrite history, or delete `main`.
- The production auto-promotion controller does not execute code from submitted PRs.
- Changes to backend migrations, production release scripts, signing, and deployment require an independent staging/device verification and the `staging-verified` label.
- Never mark staging verified based only on lint or unit tests; verify real integration behavior without using production as a testing ground.
- Do not bypass these rules or automatically merge while required tests are pending, failing, or missing.

## One-time required repository-owner action: protect main

**Owner must configure this in the GitHub UI.** A workflow file cannot enforce GitHub repository branch rules, and the connected GitHub API cannot change branch protection.

1. Open **Settings → Rules → Rulesets** in `Glimmar-code/Blinkng`. Create an **active branch ruleset** targeting the exact default branch `main`. Alternatively use **Settings → Branches → Add branch protection rule** for `main`.
2. Require a pull request before merging; disable branch deletion and force pushes.
3. Require passing status checks before merging. Select the **GitHub Actions** versions of these always-on PR jobs: `validate`, `guard`, `migration-safety`, `parity`, `windows-build`. Verify each selected check appears on documentation-only PRs as well.
4. Require branches to be up-to-date before merging when practicable. Ensure there is no bypass path for ordinary development changes.
5. For a single maintainer, **do not require a formal approving PR review from the PR author** (GitHub does not accept self-review approvals). Instead use the owner-authored, SHA-bound release authorization below. If independent maintainers become available, additionally require an approving review.
6. Verify enforcement with a disposable `Testlab-*` PR: missing checks must prevent the GitHub **Merge** button, and a direct push to `main` must be rejected.

Until this one-time GitHub setting is enabled and checked, the controller's safeguards **cannot prevent direct pushes or a manual merge to unprotected main**.

## Explicit authorization for production auto-promotion

The pre-existing opt-in label `auto-promote` is not permission to publish by itself.

For each production PR:

1. Develop and test changes on a `Testlab-*` branch, targeting `main`.
2. Wait for all relevant automated checks and staging/device/provider checks to pass on the **latest** PR head SHA.
3. Ensure the PR is ready, not a draft, its checklist is complete, and `main` is up-to-date in the branch.
4. The repository owner **Glimmar-code** must post this exact one-line PR comment with the **current 40-character PR head commit SHA**:

   ```text
   BLINK APPROVE <40-character-commit-sha>
   ```

5. The owner must explicitly add the `auto-promote` label. For production-sensitive files, the `staging-verified` label is also required.
6. The trusted controller checks author identity, comment content, current SHA, newest checks, branch alignment, staging evidence, and PR state before merging.
7. A new commit invalidates the old authorization. **A fresh approval comment for the new SHA is required.**

To cancel a previously granted authorization for the same SHA, post:

```text
BLINK REVOKE <40-character-commit-sha>
```

The owner's newest exact approval/revocation command for the SHA wins. Removing `auto-promote` is an additional immediate opt-out.

> **Important:** Even green automated checks do not prove real-device flows, production integrations, or a published APK are working. Keep the required integration checks and publication verification separate.

## Release after approval

The existing release automation builds a signed APK from `main`, verifies package identity, signing continuity, version code, artifacts and checksums, and publishes a GitHub Release. Android website updates remain user-approved installs, not silent updates. Release monitoring and enhanced in-app updates are separate future phases.

**Never claim deployed/published solely because a PR merged or a workflow started.** Verify the completed signed build and published downloadable asset.

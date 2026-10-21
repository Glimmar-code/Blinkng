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
5. For a single maintainer, **do not require a formal PR review from the PR author**. A connected AI manages release promotion from the owner's ChatGPT conversation and applies `auto-promote` only after verifying the latest SHA and passing required gates. No GitHub approval comment or owner interaction in GitHub is required.
6. Verify enforcement with a disposable `Testlab-*` PR: missing checks must prevent the GitHub **Merge** button, and a direct push to `main` must be rejected.

Until this one-time GitHub setting is enabled and checked, the controller's safeguards **cannot prevent direct pushes or a manual merge to unprotected main**.

## Authorized AI promotion from chat

An owner can use ChatGPT or any other trusted AI with authenticated GitHub repository access to request fixes, review, merge, publication, or rollback. The owner **does not need to open GitHub**, add labels, post a `BLINK APPROVE` comment, or approve a PR.

For each release PR:

1. The connected agent develops and tests changes in a `Testlab-*` branch targeting protected `main`.
2. Automated checks, backend staging and independent physical-device tests (if applicable) must complete on the current commit. No paid testing infrastructure is activated without permission.
3. The agent reviews code, checks that the PR is non-draft with all checklist items completed, confirms the head branch is current with `main`, and applies `auto-promote` to authorize that exact reviewed revision. `staging-verified` and `device-verified` may only be applied with real verification evidence.
4. GitHub's trusted controller applies the remaining fail-closed tests and merges the PR automatically when its checks pass. The owner does not perform a separate manual approval step.
5. Every new PR code push clears `auto-promote`, `staging-verified`, and `device-verified`, so the connected agent must re-evaluate before promotion; old attestations cannot be carried forward.
6. The owner can stop a release by asking the connected agent to remove the `auto-promote` label, convert the PR to draft, or close it. Never bypass required branch-protection rules to accelerate a release.

> **Important:** Green CI does not guarantee real-device calls, production integrations, successful publishing, or user-data preservation. Keep these checks and release verification separate from merge permission.

## Release after approval

The existing release automation builds a signed APK from `main`, verifies package identity, signing continuity, version code, artifacts and checksums, and publishes a GitHub Release. Android website updates remain user-approved installs, not silent updates. Release monitoring and enhanced in-app updates are separate future phases.

**Never claim deployed/published solely because a PR merged or a workflow started.** Verify the completed signed build and published downloadable asset.

# BLINK: verified once, reuse only unchanged expensive test inputs

## Problem

GitHub is allowed to show **Update branch** even when all source PR tests were green. Each new commit has a new SHA; required GitHub checks must still report on that SHA. Blindly copying an earlier green status would be unsafe and can conflict with branch protection.

## What the smart reuse system does

- Android and Windows **required jobs still execute at every relevant PR / main commit**; current-SHA policy, change detection, parity, security, backend safety, and release routing checks are not skipped.
- For expensive Android compilation/UI screenshots/lint/APK builds and Windows EXE/MSI packaging, a lightweight script attempts to find a **successful prior full run** on the exact preceding PR source commit, or on the tested PR parent of a two-parent `main` merge.
- It compares the **previous tested source Git tree against the current checked-out integration tree**. If any relevant source, shared code, dependency, migration, UI asset, test, build configuration, trusted workflow or CI policy file changed, it runs the entire affected suite again.
- Each workflow records a SHA-256 digest of the **actual checked-out integration tree** in a successful GitHub job step. This includes the tracked Git object IDs and paths for all build, test, dependency, workflow and native inputs; therefore, a synthetic GitHub PR merge is not mistaken for the source branch tree.
- The previous evidence must be a completed successful **GitHub Actions check** associated with the same PR (or tested PR parent on a main merge). Its recorded digest must match the current integration tree. The **individual costly steps** (including unit tests, screenshots, Android lint, debug APK/AAB or Windows installer build) must have actually succeeded. A prior run that reused tests or skipped a required step **cannot create reusable evidence**. A newer failed rerun invalidates an older success.
- If source evidence is absent, GitHub API unavailable, changed paths uncertain, workflow policy changed, or PR is from a fork, the decision **fails closed to a full test run**. No untrusted cache or user-controlled PR comment is accepted as verification.

## Example

1. PR #517 passes a complete Android suite.
2. An unrelated Markdown/web-page change merges into main.
3. If PR #517 is updated without modifying its Android, shared, dependency, test or build inputs, the new SHA can reuse its directly preceding complete successful Android evidence. Lightweight required jobs still run on the new SHA.
4. If the latest main touches Kotlin, shared code, Gradle, Supabase integration, native dependencies, screenshots, CI scripts or other relevant inputs, expensive tests run again.
5. On a two-parent production merge, the system may reuse the successfully verified PR side when its relevant inputs are identical to the resulting main tree. Production signing and live release workflows remain separate, and are never replaced by this evidence.

## Limitations and safety

- A GitHub required check **cannot simply be marked green from an earlier SHA**. GitHub still runs a short per-commit status job; only expensive steps may be reused.
- A green source check does not constitute a successful combined Testlab integration, real-device acceptance, backend staging or production authorization.
- The prior full suite MUST have executed normally with successful step records: cache artifacts, CI comments or skipped steps are insufficient.
- Reuse searches up to **24 first-parent ancestor commits**, without treating a reused/skipped check as fresh evidence. Each candidate must match the current complete build-input tree; a newer failed required check or changed input stops reuse. Only a previously executed full test job counts. If no suitable evidence is found within this bounded window, the full suite runs again.
- Workflow/controller changes are treated as protected CI changes and must be merged separately after independent review and passing checks. Do not disable branch protection to activate this optimization.
- This doesn't alter the PR #517 release hold, update PR text, or make a publication claim.

To inspect a decision, expand the **Check whether full Android/Windows inputs were previously verified** step in GitHub Actions and read the summary labeled **Verified Android/Windows build evidence**.

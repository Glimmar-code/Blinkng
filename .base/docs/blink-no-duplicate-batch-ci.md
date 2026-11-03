# BLINK — Why "Update branch" keeps repeating

GitHub ties required checks to a commit SHA. Pressing **Update branch** merges a newer `main` into the PR, producing a new commit SHA. GitHub will schedule new lightweight status checks; the Update branch button can reappear as soon as `main` moves again. The GitHub UI cannot be suppressed by a GitHub Actions workflow.

## Avoid duplicate expensive builds

1. **Do not repeatedly press Update branch.** Eligible PRs should be combined in the isolated `Testlab-batch-*` integration branch against current `main`, once per combined candidate revision.
2. On Testlab, the batch controller now checks whether **each exact-SHA workflow already has a run** (automatically triggered by `push`, `pull_request`, or `workflow_dispatch`) before requesting any manual `workflow_dispatch`. A pending, successful, or failed existing run is not dispatched again. Failed checks block the merge until fixed; they aren't silently retried to manufacture a green result.
3. The Android and Windows **verified input digest** optimization reuses costly suites only when the latest checked-out combined inputs match the fully successful recorded run. Any changed Kotlin, shared code, backend contract, Gradle, CI workflow or test input invalidates prior evidence.
4. **Android runtime emulator smoke** and **Android release smoke** have separate safety requirements. A generated APK embeds the commit SHA and its release identity must be verified against that current SHA. Those release-specific checks cannot simply be inherited from an old binary.
5. An original PR with a **hold in its description** is not eligible for automatic production integration, regardless of successful checks. Resolve the outstanding work and remove the hold only after it is genuinely ready.

## Current examples

PR #517 originally passed tests before the verified-input reuse workflows existed. A subsequent branch update brought in changes to Android and Windows **CI definitions themselves**. That changes the test inputs, so a new full run was required; this is not a cache failure. Once the new full run passes with a recorded matching input digest, a later unrelated main update can reuse eligible expensive suites. PR #517's explicit draft/release restriction remains a separate blocker and is never silently overridden.

PR #557 is an isolated Testlab integration batch with its own combined verification. The queue must not repeat a quality workflow for the same SHA merely because the batch was created through a connected account that already triggered the checks.

## Status

The same-SHA dispatch deduplication changes are introduced in a separate CI-controller PR, tested independently and merged into `main` only after required checks pass. This optimization **does not** bypass GitHub branch protection, staging/device proof, or sign production APKs.

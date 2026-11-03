# BLINK: concurrent PRs, one verified Testlab integration batch

## What is automated

1. Developers may push to many independent feature PRs targeting `main` concurrently; pushing is not merging.
2. **No button or label is required.** A trusted controller automatically scans on new or updated PRs, and every 15 minutes, for eligible internal PRs whose latest-head required CI passed. It reviews the 60 most recently updated candidates, integrates up to 10 compatible ones per batch into a **fresh `Testlab-batch-<run ID>` branch created from current `main`**, and never overwrites the historically divergent `Testlab` branch or newer `main` files. The optional `batch-testlab` label remains recognized for compatibility but is not required or an approval bypass.
3. Candidate PRs must be open, non-draft, from this repository, targeting `main`, updated in the last 30 days, have no hold/superseded indicators or unresolved change requests, and pass their current-head required checks. Previous source checks can target an earlier `main`; integration tests always run again on fresh `main`. Each source is merged using GitHub's merge API. Conflicting PRs are skipped and remain open; the controller **never picks a side of a merge conflict**. Unchanged/covered PRs are not duplicated. Source PRs with unverified backend, release, OEM/device, or CI/workflow changes are held for independent review.
4. The controller opens **one** combined PR targeting `main` and explicitly dispatches every relevant Android, Windows, Supabase, security, APK-integrity, and web/domain/game check on that isolated batch branch. Manual workflow dispatch is required because PRs created by `GITHUB_TOKEN` do not automatically start PR-triggered Actions. No candidate code is executed inside the write-enabled controller.
5. The controller verifies the **exact combined commit SHA**, checks the current `main` baseline, and will refresh the Testlab batch against newer `main` without asking you to press “Update branch”. Any such update invalidates old CI and release authorization. Failing/missing checks keep the batch out of production.
6. For a green, current, compatible batch with every required independent staging/device acceptance, the controller adds exact-SHA verification and `auto-promote`. The already-existing trusted Testlab-to-main controller rechecks all conditions and merges **with full commit ancestry** so source PRs can be recognized as included. After merging it explicitly dispatches the signed Android or website workflow as applicable. The existing signed APK publication workflow only publishes an actual successful signed release.

## Why GitHub still shows Update branch

GitHub's **Update branch** button is based on the *original PR branch* being behind `main`. The automatic batch queue **does not rewrite the original PR branch**; it creates a fresh Testlab integration branch from the latest `main`, retests all combined changes, and promotes only verified work. The button may remain visible on a source PR even while the queue is functioning.

The trusted queue now gives **actionable diagnostics for each examined PR**. The GitHub Actions run summary contains an overview table, and PRs with a relevant blocker receive one deduplicated **BLINK automatic integration status** bot comment containing the reason and next action. Status changes update that comment rather than creating repeated messages; green PRs without a prior notice are not spammed. An old hold comment is updated after the PR genuinely becomes ready. A status notice is *informational* and never counts as approval or replaces required checks.

For example, PR #517 was technically open for review but its **description explicitly said it was draft and must not be merged**. That instruction blocks automatic production integration independently of green checks or GitHub's Update branch notice. Complete the documented acceptance criteria before removing that restriction.

## Safety rules and limits

- Only **same-repository**, open, non-draft, **current-head CI green**, recently updated PRs targeting `main` can enter a batch automatically. No label or repeated **Update branch** clicks are necessary. Old, superseded, blocked, incomplete, failed, fork, and draft PRs are not automatically included.
- Only one active batch is processed at a time. A merge conflict, failed CI, unverified hosted backend integration, or missing independent device evidence **blocks** that portion of release; no loop falsely reports success and no failing test is skipped.
- PRs modifying `.github/workflows/`, `.github/actions/`, or `.github/scripts/` cannot enter an automatic batch; they need separate code review because workflow dispatch can execute code at the proposed branch ref.
- For protected `main`, the normal GitHub merge rules still apply. This automation **cannot change repository rulesets**, purchase staging, or autonomously write correct source-code repairs for semantic merge conflicts or failing tests.
- The initial Testlab batching queue was activated in PR #553, with automatic unlabeled discovery subsequently merged in PRs #554 and #556. The **PR status explanation extension** only becomes live after its own protected CI-controller PR passes and merges into `main`.
- Source PRs should not be closed or deleted merely because they are batched. Reconcile any unique changes and verified commit ancestry after main integration.

## Costs and practical use

Running many parallel builds consumes GitHub Actions minutes; batching reduces the number of repeated integration builds but does not make CI free. For especially costly Android/Windows checks, combine related commits on the source branch rather than repeatedly pushing unverified updates. Web-only patches do not trigger an unrelated signed APK. Independent production integrations must be verified separately from static green GitHub Actions.

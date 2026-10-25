# BLINK: concurrent PRs, one verified Testlab integration batch

## What is automated

1. Developers may push to many independent feature PRs targeting `main` concurrently; pushing is not merging.
2. **No button or label is required.** A trusted controller automatically scans on new or updated PRs, and every 15 minutes, for eligible internal PRs whose latest-head required CI passed. It reviews the 60 most recently updated candidates, integrates up to 10 compatible ones per batch into a **fresh `Testlab-batch-<run ID>` branch created from current `main`**, and never overwrites the historically divergent `Testlab` branch or newer `main` files. The optional `batch-testlab` label remains recognized for compatibility but is not required or an approval bypass.
3. Candidate PRs must be open, non-draft, from this repository, targeting `main`, updated in the last 30 days, have no hold/superseded indicators or unresolved change requests, and pass their current-head required checks. Previous source checks can target an earlier `main`; integration tests always run again on fresh `main`. Each source is merged using GitHub's merge API. Conflicting PRs are skipped and remain open; the controller **never picks a side of a merge conflict**. Unchanged/covered PRs are not duplicated. Source PRs with unverified backend, release, OEM/device, or CI/workflow changes are held for independent review.
4. The controller opens **one** combined PR targeting `main` and explicitly dispatches every relevant Android, Windows, Supabase, security, APK-integrity, and web/domain/game check on that isolated batch branch. Manual workflow dispatch is required because PRs created by `GITHUB_TOKEN` do not automatically start PR-triggered Actions. No candidate code is executed inside the write-enabled controller.
5. The controller verifies the **exact combined commit SHA**, checks the current `main` baseline, and will refresh the Testlab batch against newer `main` without asking you to press “Update branch”. Any such update invalidates old CI and release authorization. Failing/missing checks keep the batch out of production.
6. For a green, current, compatible batch with every required independent staging/device acceptance, the controller adds exact-SHA verification and `auto-promote`. The already-existing trusted Testlab-to-main controller rechecks all conditions and merges **with full commit ancestry** so source PRs can be recognized as included. After merging it explicitly dispatches the signed Android or website workflow as applicable. The existing signed APK publication workflow only publishes an actual successful signed release.

## Safety rules and limits

- Only **same-repository**, open, non-draft, **current-head CI green**, recently updated PRs targeting `main` can enter a batch automatically. No label or repeated **Update branch** clicks are necessary. Old, superseded, blocked, incomplete, failed, fork, and draft PRs are not automatically included.
- Only one active batch is processed at a time. A merge conflict, failed CI, unverified hosted backend integration, or missing independent device evidence **blocks** that portion of release; no loop falsely reports success and no failing test is skipped.
- PRs modifying `.github/workflows/`, `.github/actions/`, or `.github/scripts/` cannot enter an automatic batch; they need separate code review because workflow dispatch can execute code at the proposed branch ref.
- For protected `main`, the normal GitHub merge rules still apply. This automation **cannot change repository rulesets**, purchase staging, or autonomously write correct source-code repairs for semantic merge conflicts or failing tests.
- The initial queue was activated through PR #553. This **automatic discovery extension** is a separate protected CI-controller change and will only be live after its own latest-head checks and production merge succeed.
- Source PRs should not be closed or deleted merely because they are batched. Reconcile any unique changes and verified commit ancestry after main integration.

## Costs and practical use

Running many parallel builds consumes GitHub Actions minutes; batching reduces the number of repeated integration builds but does not make CI free. For especially costly Android/Windows checks, combine related commits on the source branch rather than repeatedly pushing unverified updates. Web-only patches do not trigger an unrelated signed APK. Independent production integrations must be verified separately from static green GitHub Actions.

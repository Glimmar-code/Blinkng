# BLINK Phase 5 — production release monitoring and safe recovery

**Status:** Testlab only until the dependent Phase 1–4 integration PR passes and the release automation is approved for production.

## Monitored GitHub Actions

- `Signed Android release`: records signing, package validation, Godot content pack, APK version/certificate and Android release-build failures.
- `Publish latest APK`: records failed artifact download, digest verification, draft GitHub Release checks and public publication failures.
- `Verify published BLINK APK`: records broken public latest metadata/APK download URLs, published ZIP/assets and SHA-256 drift.

These three workflows are monitored after completion on the trusted `main` branch. A read-only scheduled check provides a daily safety net for missed event notifications.

## Notification behavior

- Only unsuccessful `main` runs create/update a GitHub issue labeled `blink-release-alert` in the BLINK repository.
- The issue gives a reproducible workflow run link, exact build commit, and a safe recovery checklist. Duplicate run events do not create issue spam.
- A later successful completed run closes the associated alert. Closing an issue is a diagnostic state change, **not** proof that a phone installed the new APK.
- The workflow runs from the **trusted production `main` checkout**; it never executes PR code from untrusted workflow-run events.

## Failure recovery

1. Keep the last verified latest release publicly downloadable. Failed uploads remain as drafts; do not overwrite an existing published APK.
2. Inspect the exact failed job using its GitHub Actions link. Classify the failure: code regression, missing signing secret, mismatch of key/version, network/download corruption, backend migration, or GitHub Actions service issue.
3. Fix code first on Testlab, test Android and Windows parity as required, then rerun the full set of relevant tests. Do not unilaterally disable failing checks.
4. Bring Testlab up to date with production `main` and obtain exact owner approval for the final PR head SHA.
5. Promote the verified PR. Trigger a fresh signed Android release with a **higher version code** and the same authorized signing certificate. Do not silently deploy an older version as rollback.
6. Check that both the public APK and release-info metadata resolve to the new verified release and that Android can install it.

## Release readiness gaps

The app has many unrelated feature PRs and workflows. A green release monitor is **not** blanket certification of all marketplace, live-call, Supabase, ads, game, or user-interface behavior. Those require their own staged integration and manual smoke tests. An APK is considered newly published only once GitHub Releases shows the new signed asset and the public delivery audit succeeds.

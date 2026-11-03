# BLINK Phase 2: tested signed release and public distribution integrity

**Status:** Testlab candidate, not active in production until its PR passes and merges.

## Automated promotion gate

- On **every** PR, the `APK delivery integrity / integrity` GitHub Actions job runs instead of being path-filtered.
- The `Testlab -> main` promotion policy now **requires** `integrity` alongside Android, Windows, Supabase, and secret checks, even for documentation-only changes.
- The job executes offline regression tests for: checksum corruption, mismatched signing metadata, duplicate/invalid files, missing ZIP entries, older/stale source commits, owner-approved source integrity, and broken public-release metadata.
- All promotions continue to require the Phase 1 exact-owner approval gate; branch protection must still be enabled manually in GitHub repository Settings.

## Signed production APK safety

The existing signed-release workflow already validates the build signature, package identity, optional game size and build SHA. The publisher now adds two checks:

1. **Current-main check:** Before drafting and immediately before publication, query GitHub's **current** `main` ref, not the previous runner checkout. If that SHA differs from the source, the previous public latest remains intact.
2. **Monotonic update check:** Before drafting and again before publication, fetch the current published release metadata. Reject equal/older version codes and signing-certificate changes, including out-of-order builds from the same source SHA. If metadata cannot be checked because GitHub is unavailable, fail safely.

The publisher first uploads assets as a GitHub Release draft and downloads them back. It only marks the draft public after SHA-256/ZIP/archive checks and the above last-minute freshness checks pass.

## Public delivery audit (new)

`.github/workflows/audit-published-apk.yml` is **read-only** and runs on release publication, daily, or manual dispatch.

- Verifies the latest release is public and has exactly five required assets.
- Downloads the **real published files** from GitHub Releases.
- Validates SHA-256, ZIP contents, signed release metadata, game-pack size, version code, package identity, exact release tag and source commit.
- Verifies that BLINK's evergreen public metadata URL returns the same release, and that the website's APK download link begins with ZIP/APK bytes.
- An older publication event is skipped if it was superseded by a newer latest release. An audit failure turns the GitHub check red; it does **not** silently unpublish a release.

## Remaining before production enablement

- Complete Phase 1 repository branch-protection setup (not possible from GitHub's connected write tools).
- Wait for the latest Phase 2 PR checks to finish green; inspect failures instead of claiming success.
- Merge Phase 1 first, then rebase or reconcile Phase 2 with latest `main` and run checks again.
- Confirm signed build and publication against a real release **after** approval. Production publication tests are not performed from Testlab.

## Live update limitations

A passed APK release audit does not prove the in-app installation flow works on every Android manufacturer or network. Phase 3 develops and tests reliable user-approved APK updates separately.

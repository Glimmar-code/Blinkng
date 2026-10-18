# BLINK Phase 5 — production release monitoring and safe recovery

**Testlab only until verified and merged:** this monitoring is not running on `main` yet.

## What is monitored

After the existing official production workflows complete, `watch-production-release-failures.yml` detects **failed/timed-out** outcomes for:

- `Signed Android release` — signed APK/AAB build and certificate continuity.
- `Publish latest APK` — draft upload, checksum readback and published release.
- `Verify published BLINK APK` — downstream/daily live GitHub release bytes and download URLs.

Only completed, repository-owned **main** workflow runs are accepted; untrusted fork/branch/fake workflow events are ignored. A matching GitHub issue (label `blink-release-incident`) is opened once per workflow category; subsequent incidents are comments on the existing issue with exact run links and commit IDs. This avoids a flood of duplicate issue cards. Repository watchers/issue subscriptions determine whether notifications are received; the workflow cannot promise a phone push without extra integration.

## Failure recovery policy

1. **Do not publish the failing build.** Existing verification already blocks corrupt/stale/out-of-order releases and keeps the previous verified latest in place when draft publication fails.
2. Review the generated incident, failing workflow job log, source commit and actual public `Blink-latest.apk` hash.
3. Fix the root cause in a Testlab branch and rerun all relevant Android, Windows, security and Supabase checks. Do not silence failures.
4. Require SHA-specific repository-owner approval and a protected `main` PR merge.
5. Produce a new signed APK with an Android **higher** versionCode and unchanged signing identity; do not roll back to an old lower-version APK.
6. Verify the published asset, checksum, metadata and website download using the independent audit workflow. Only then resolve the incident.

## Safety and limits

- The watcher **never** merges, rebuilds, triggers a production release, unpublishes, copies secrets into an issue, or auto-rolls back.
- Monitoring activates only after merged to the default branch. It is not a guarantee of instant alerts while GitHub Actions or GitHub itself is unavailable.
- Failed production database or payment/provider integrations need their own owner-approved staging/deployment checks; this is a release-distribution monitor, not a substitute for real integration tests.
- Real Android 8–15 installer testing and GitHub `main` protection remain blockers. See `docs/blink-phase1-release-approval.md`.

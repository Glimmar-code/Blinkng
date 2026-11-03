# Phase 4 — BLINK background APK updates, resumption and network protection

**Status: Testlab implementation. Not merged, installed or published.**

## What was implemented

- WorkManager **unique** one-time download jobs are launched only after the user selects **Download/Resume**. The job requires an available network; an optional **unmetered only** preference waits for Android-reported unmetered connectivity.
- The Android worker uses a foreground `dataSync` notification to display download progress and request ongoing execution even if the user closes the update dialog or the Activity is recreated.
- A fixed release identity and WorkManager job UUID are persisted. On app restart the UI resumes observing its ongoing job.
- **Pause** cancels WorkManager work and preserves the partial bytes in `filesDir/blink-updates/partial/`.
- **Resume** validates the previously published release SHA and requests the remaining bytes with an HTTP `Range: bytes=N-` header.
- A proper HTTP 206 response is accepted only if `Content-Range` starts at saved offset and has a valid bounded range. HTTP 200 restarts the file from zero; HTTP 416 discards the stale partial and retries fresh.
- Interrupted transfers use WorkManager retry with exponential backoff, bounded attempt count and progress. No continuous network polling while offline.
- After downloading, the worker enforces the existing **100 MiB bound, official GitHub asset host restriction, SHA-256 hash, app package name, strictly newer Android version code, signing certificate matching the installed app** and official latest release. Only then is the file moved to the `verified/` folder accessible through the narrow FileProvider.
- The user must tap **Install verified update** and approve Android's system installation dialog. No app update is silently installed.

## Important limitations

- WorkManager scheduling and Android foreground-service restrictions can delay or interrupt progress; work is not promised to complete uninterrupted in every power/network mode.
- An *unmetered* connection is not necessarily Wi-Fi, and some Wi-Fi networks are metered. UI describes it accurately.
- Background download is not an automatic update; **only** the user starts or resumes it.
- Pause keeps the APK partial bytes, so on-device space is still used until resumed, superseded, or the app is uninstalled.
- Existing APK signing identities and platform installation restrictions must be tested with a real Android device; CI alone cannot prove every manufacturer-specific installer behavior.
- Android foreground `dataSync` and `REQUEST_INSTALL_PACKAGES` permissions require distribution-channel review before publishing to Google Play. This phase targets website-distributed BLINK APKs.
- Windows has no Android PackageInstaller or WorkManager. Shared `BlinkApkResumePolicy` is the portable range-checking component; see the `android-workmanager-foreground-apk-20261018` parity exception.

## Required production gates

1. Phase 1 PR #348 main-protection rules and SHA-bound approval must be activated and verified.
2. Phase 2 PR #350 and Phase 3 PR #352 must be reconciled/merged safely before this dependent change.
3. Run Android lint/unit/debug + emulator smoke, signed release smoke, Windows parity/build, Supabase safety, secret guard, APK integrity tests on the final integrated head SHA.
4. Test real Android devices on limited network, interrupted network, process death, 206/200/416 server behavior, low storage, user-paused work, missing notification permission, unknown-source settings, and signer mismatch. Fix any failures in Testlab.
5. Only after owner approval, verified merge and signed release publication may the official website APK be treated as updated. Never claim a Testlab push is production.

## No extra fee or production backend changes

This feature uses the existing Android WorkManager library and GitHub Releases; it does not create a Supabase paid branch or activate production database changes.

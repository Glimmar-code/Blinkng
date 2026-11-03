# BLINK Phase 3 — user-approved updates from the official website

**Status:** Android implementation on a Testlab feature branch; production unchanged.

## Already available

- GitHub Releases publishes a signed, fixed-package-ID APK and `release-info.txt` with checksum/version/signing certificate.
- Android `WebsiteApkUpdateCoordinator` checks the latest official release and only prompts for newer version codes.
- Google Play's separate `PlayInAppUpdateCoordinator` handles future Play Store installs.

## Phase 3 functionality

1. While on idle Home, BLINK checks the **official published GitHub release metadata** without blocking normal offline use.
2. Tapping **Download update** starts an HTTPS-only GitHub-asset download into BLINK's private app cache.
3. Progress is shown in BLINK. A user can cancel the foreground download without modifying the installed app.
4. BLINK enforces a **100 MiB hard limit**, exact downloaded byte count when available, and metadata-pinned SHA-256.
5. Android package archive is parsed to verify the official BLINK package ID, newer version code, expected production signing fingerprint, and continuity with the currently installed package signer.
6. Only after all checks pass, **Install verified update** shares the private APK using a restricted Android FileProvider; Android's system installer asks for the user's approval. On Android 8+ users can grant/deny the per-source permission in OS Settings. The app cannot install an APK silently.
7. Failed checks do not launch the installer; users can retry or visit BLINK's website. Google Play-installed copies use the Play update path instead.

## Quality gates

- Shared `BlinkApkUpdatePolicy` pure unit tests verify official HTTPS origins, truncated/oversized files, mismatched Android package IDs, downgrades, and signing changes.
- `scripts/test_inapp_apk_update.py` rejects a broad/file-exposing provider, missing user-install consent, unsupported host, missing package verification, or lost Play Store separation. Wired into Android quality and release smoke CI.
- All normal Android, Windows, backend and release checks must pass, plus real-device testing for package installer behavior and device manufacturer restrictions.

## Known limitations and safeguards

- **Not an invisible background updater.** Download requires user initiation and remains scoped to the Android Activity. App/process recreation may cancel the transfer. Resumable/WorkManager downloads are a separate future phase.
- **Not already installed in public BLINK.** Testlab code does not affect users until verified main promotion, signed release and publication succeed.
- Android's installer itself is responsible for final cryptographic APK/installability checks. BLINK performs an earlier safety check but must not present that as a substitute for the OS.
- `REQUEST_INSTALL_PACKAGES` requires special distribution-policy review before sending an AAB with this manifest to the Play Store. This phase targets the existing website-distributed APK; do not automatically roll it into Google Play without that review.
- No Supabase production migration, hosting expense, or signup/auth setting change is required for this phase.

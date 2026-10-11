# Production APK release requirements

The signed workflow builds once with the permanent production signer and real Firebase configuration. Android 7 (API 24, the minimum supported version) and Android 15 (API 35) emulator jobs download that exact artifact. They verify its checksum/signature, fresh installation, signed-out login and registration navigation, background/resume, offline restart and in-place upgrade from the previous public APK. The upgrade uses `adb install -r`, never a downgrade flag or uninstall between old and new.

Testlab pull requests within this repository can run this same signing/validation workflow. Those artifacts are validation-only: the publisher accepts only successful main runs and metadata marked `productionSource=main`. Fork PRs cannot run the signing job. Production signing secrets remain confined to the build job.

Publication also waits for successful Android quality, Android runtime, release smoke, Windows quality, backend safety and secret checks for the exact main commit. Missing, skipped, failed, cancelled, pending or stale checks cannot authorize publication. The auto-promotion controller dispatches these workflows after its token-based merges, which otherwise do not trigger push workflows.

The publisher requires successful reports for both Android API levels tied to the APK checksum and version. It checks the previous published version, signing identity and commit ancestry before drafting and again before marking latest. An older source commit cannot be published as an upgrade merely by assigning it a higher version. Downloads are verified again after upload. Public release assets are never overwritten; the previous latest remains available if validation fails.

The website continues to use `releases/latest/download/Blink-latest.apk`, so it follows only successfully published candidates.

## Scope and remaining device validation

These checks do not authenticate, create accounts, post, send messages, place calls or spend coins against production. Signed-in feed/reels, Store, payments, calling and session-data migrations still require controlled test accounts and appropriate staging/device tests. An x86 emulator is not an ARM Redmi/HyperOS device. Passing CI is not a claim that every feature or handset has been tested.

Existing public releases without validation reports are not republished by the new publisher. If a new gate fails, investigate that gate in Testlab; do not delete the gate or mark staging verified to bypass it.

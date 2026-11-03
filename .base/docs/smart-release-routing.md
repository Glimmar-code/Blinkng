# BLINK smart release routing

## Current production behavior

- **Android/native/shared/build changes**: merges to `main` matching `signed-release.yml` path filters start a signed Android build. The `publish-latest-apk.yml` workflow publishes only a successful signed build from `main`. Production certificate, source revision and version checks remain mandatory.
- **Website changes**: deploy through the existing website deployment workflow; do not rebuild Android solely for website assets.
- **Supabase changes**: deploy only through the existing validated backend/migration processes. A merge is **not** proof that a production database migration was applied.
- **Remote configuration/content**: changes to already-supported backend-controlled settings can reach installed apps without an APK. Kotlin, native libraries, new permissions and new screens cannot be hot-patched by this mechanism.
- **Documentation**: no APK release.

## Guardrails

The `Smart release routing gate` runs tests on PRs and main to detect accidental removal of Android triggers or publication safety gates. It does not weaken Testlab approval, signature continuity, CI checks or production protection.

## Future work (not enabled by this change)

- Consolidated release windows/debouncing for successive Android merges.
- Production backend deployment and admin-side live content controls with access control/audit history.
- Delta APK patches with integrity, version-chain and Android 15 installation testing.
- Release Management admin dashboard and push notifications.

All future features require implementation and separate verification; this policy document does not claim they are already live.

# BLINK signed Android release — required GitHub secrets

The signed release pipeline **must not** publish an APK with a disposable signing key,
placeholder Firebase project or invalid signing credentials. The latest failure
at the “Verify permanent production secrets” step means the repository owner still
needs to configure the actual production values in GitHub.

## Configure securely

In the **Glimmar-code/Blinkng** repository open
**Settings → Secrets and variables → Actions → Repository secrets → New repository secret**.
Do not commit credentials, attach them to an issue, or paste them into chat.

The embedded Google Maps view was removed; **`MAPS_API_KEY` is no longer required**
for production releases. Live-location sharing still uses the device's standard
location service only after a person explicitly starts sharing.

1. **`GOOGLE_SERVICES_JSON`** — Recommended, especially from a phone: In the
   Firebase Console, select the existing BLINK production Firebase project,
   **Project settings → General → Your apps → Android**, and download the
   `google-services.json` for `com.aistudio.blink.appvtwo`. Open the file in a
   text editor and paste the **entire JSON** as this GitHub repository secret.
   The workflow supports multiline JSON securely and never prints its contents.

   **Alternative:** Keep the existing `GOOGLE_SERVICES_JSON_BASE64` secret if
   already configured. Encode the **entire production JSON file** as a single
   base64 value (no extra newline), for example on Linux:
   `base64 -w0 google-services.json`. The workflow accepts **either** secret;
   if both are set, base64 takes precedence.

2. Confirm the existing permanent signing secrets are configured:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
   **Do not generate a new production keystore** to bypass a build problem.
   The release workflow pins the APK certificate to the real Android OAuth
   signing SHA-1 included in the Firebase configuration. Using another signing
   key would break continuity for installed users.

## Release verification

After the repository secrets are saved, open
**Actions → Signed Android release → Run workflow**, select `main`, and run it.
The workflow checks required secrets, production Firebase package, pinned
certificate, certificate continuity against the latest published production
APK, a versionCode greater than the last published version, build revision,
package/version and APK/AAB signatures.
A successful signed release triggers **Publish latest APK** automatically,
which updates the public `Blink-latest.apk` asset. The production APK must
**not** be replaced with the CI release-smoke/debug test artifact.

The `Android release smoke test` checks both the raw-JSON and base64 Firebase
secret restore paths using **synthetic** data. It does not read or copy any real
production credential.

Official references:
- https://firebase.google.com/docs/android/google-services-plugin-and-file
- https://developers.google.com/maps/api-security-best-practices

## Brand consistency

All Android adaptive, splash and density-specific launcher icons and both web/PWA
icon sizes are generated from the same approved `app/src/main/res/drawable/app_icon.png`
(B) master. Run `python3 scripts/sync_android_launcher_assets.py` (requires Pillow)
to regenerate assets and `python3 scripts/sync_android_launcher_assets.py --check`
to verify. Testlab automatically regenerates and commits logo assets; CI refuses
stale images. The website service worker uses a fresh cache version so returning
visitors receive the B mark rather than the old purple-eye logo.

## Diagnose 'App not installed: package appears invalid'

1. Only distribute the current `releases/latest/download/Blink-latest.apk` from
   the **successful signed production** workflow (never a debug or CI-smoke APK).
2. Compare the downloaded file SHA-256 to `release-info.txt` and ensure the
   browser downloaded an actual APK rather than an HTML error/partial download.
3. Compare `apksigner verify --print-certs` SHA-256 to the previously published
   production certificate; do not rotate the keystore to resolve an update conflict.
4. The Android release smoke gate checks APK/AAB signatures and installs the
   disposable-key **test** release on an Android 15 emulator. That test artifact
   must not be published or presented as an update for production users.
5. For a device-specific failure, collect the exact package-manager error via
   `adb install -r Blink-latest.apk`. Back up local drafts before considering
   uninstalling an older, differently signed app.

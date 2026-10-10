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

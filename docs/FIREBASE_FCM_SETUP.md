# Firebase Cloud Messaging setup

Blinkng contains the Android FCM receiver and Firebase dependencies. The production `app/google-services.json` is intentionally **not tracked** because GitHub secret scanning detected a Google API key in the committed Firebase client configuration.

## 1. Android Firebase app

Use the exact Android application ID:

`com.aistudio.blink.appvtwo`

For local development, download a fresh `google-services.json` from Firebase Console and place it at:

`app/google-services.json`

That path is gitignored. Never force-add it. A compile-safe template remains at `app/google-services.json.example`; debug/CI builds can use the template, but production release builds reject placeholder Firebase values.

## 2. Production CI secret

Store the complete production Firebase client JSON as a Base64-encoded GitHub Actions secret named:

`GOOGLE_SERVICES_JSON_BASE64`

The signed Android release workflow restores the file only inside the runner, validates the BLINK package identity, and deletes it with the ephemeral runner when the job ends.

Do not paste the encoded value into source, issues, pull requests, logs, or chat.

## 3. Rotate and restrict the leaked Google API key

Because the previous key was committed publicly, removing the file from the latest commit is not sufficient by itself. In Google Cloud Console/Firebase:

1. Create or obtain a replacement API key for the Android Firebase client.
2. Restrict it to the BLINK Android application (`com.aistudio.blink.appvtwo`) and the production signing certificate where supported.
3. Restrict API access to only the Google/Firebase APIs BLINK actually uses.
4. Download a fresh `google-services.json`, update `GOOGLE_SERVICES_JSON_BASE64`, verify a signed build, then revoke the exposed key.
5. Resolve the GitHub secret-scanning alert only after rotation/revocation is complete.

## 4. Cloud Messaging

Enable Cloud Messaging for the Firebase project. The Android client obtains an FCM registration token and stores it through BLINK's existing Supabase notification flow when a valid Supabase session exists.

## 5. Server-side push delivery

The server-side Firebase service-account credential must never be placed in the Android app. Keep it as a protected backend/Supabase secret:

`FIREBASE_SERVICE_ACCOUNT_JSON`

The client `google-services.json` and the server service-account credential are separate credentials with different security requirements.

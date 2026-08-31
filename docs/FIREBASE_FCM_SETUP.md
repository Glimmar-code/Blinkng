# Firebase Cloud Messaging setup

Blinkng now contains the Android FCM receiver and the Firebase Messaging dependency. The repository intentionally does **not** include a fake `google-services.json` because Firebase application credentials are project-specific.

## 1. Create the Android app in Firebase

Use the exact Android application ID:

`com.aistudio.blink.appvtwo`

Download the real `google-services.json` from Firebase Console and place it at:

`app/google-services.json`

Do not commit a production configuration file unless that is acceptable for your Firebase project policy. The Android Gradle file automatically applies the Google Services plugin when this file exists.

A safe template is available at `app/google-services.json.example`.

## 2. Enable Cloud Messaging

In Firebase Console, enable Cloud Messaging for the project. The Android client will obtain an FCM registration token and store it in the app's Supabase `profiles.fcm_token` field when a valid Supabase session exists.

## 3. Server-side push delivery

The recipient token is stored in Supabase, but the server still needs Firebase credentials to send messages. Do not put a Firebase service-account private key in the Android app. Store it as a Supabase Edge Function secret and use a server-side push function for production delivery.

Required server secret:

`FIREBASE_SERVICE_ACCOUNT_JSON`

Until the real Firebase project credentials and server secret are supplied, the source tree cannot honestly claim fully configured instant push delivery. The Android receiver is ready to consume FCM messages once the Firebase app is configured.

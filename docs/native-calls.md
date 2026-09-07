# Blink native voice and video calls

Blink uses WebRTC for one-to-one voice and video media, Supabase for authenticated call state/signaling, and FCM for incoming-call delivery.

## Production requirements

- Keep the Supabase project URL/publishable client key in the normal app configuration. Never ship a service-role key in the APK.
- Configure `BLINK_TURN_URL`, `BLINK_TURN_USERNAME`, and `BLINK_TURN_CREDENTIAL` in the release/CI environment. STUN-only builds are useful for development but are not reliable across carrier-grade NAT and restrictive networks.
- Keep `FIREBASE_SERVICE_ACCOUNT_JSON` only in the Supabase Edge Function environment for `send-call-notification`; never place that credential in Android source or BuildConfig.
- Android 13+ users must grant notification permission for incoming-call notifications.
- Voice calls require microphone permission. Video calls require microphone and camera permission.
- Android foreground-service runtime types must match the active call: microphone for voice, microphone + camera for video.
- WebRTC signaling writes are serialized and incoming signals are reconciled from the database so out-of-order Realtime delivery cannot skip SDP.
- Remote ICE candidates are buffered until the remote SDP is installed.
- The call media client acquires voice-communication audio focus for the active call and releases it during cleanup.

## Verification

Before release, test calls between two physical devices on Wi-Fi, Wi-Fi-to-mobile-data, and mobile-data-to-mobile-data. Cover app foreground/background, locked-screen incoming calls, decline/cancel/missed calls, speaker/mute/camera switching, network handoff, and call teardown.

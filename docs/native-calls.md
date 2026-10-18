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

## Phase 1 — connection infrastructure (Testlab staging)

The native Android client now serializes REST signal reconciliation, retries failed
SDP writes, uses bounded (caller-initiated) ICE restarts, and guards against stale
Supabase Realtime socket callbacks. These changes are **not proof of working calls**
until CI and two-phone network testing are completed.

### TURN deployment — required before production signoff

The new authenticated Edge Function at
`supabase/functions/get-call-ice-config/index.ts` issues ephemeral coturn
credentials after validating the caller's Supabase session and call membership.
It works with coturn's `use-auth-secret` and shared static auth secret mechanism.

1. Provision a reachable coturn relay with UDP and TLS/TCP support, a valid
   certificate for its `turns:` hostname, and an appropriate public address.
2. Configure the coturn server with `use-auth-secret` and the shared secret,
   plus secure network/firewall rules and appropriate relay port ranges.
3. Set **Edge Function-only** secrets `BLINK_TURN_SHARED_SECRET` and
   `BLINK_TURN_URLS` (comma-separated `turn:`/`turns:` URLs). Do not commit
   these secrets or place them in Android, GitHub Actions build parameters,
   public environment files or APKs.
4. Deploy `get-call-ice-config` with normal authenticated JWT verification.
   Verify a participant receives working ICE servers; a nonparticipant and an
   ended call must be rejected. Test relay candidates using two physical phones.
5. Remove the legacy build-time `BLINK_TURN_USERNAME` and
   `BLINK_TURN_CREDENTIAL` path only after every active environment has
   deployed the authenticated endpoint. The client currently retains that
   legacy fallback to avoid breaking existing installations during rollout.
6. Check expiry/credential refresh on longer calls. The server currently issues
   one-hour credentials, refreshed on caller-side ICE retries; test long calls
   before changing their lifetime.

The Android client tries the ephemeral function first, but falls back after
2.5 seconds when not deployed. **STUN success on Wi-Fi is not TURN validation.**
Production release must be blocked until this function, TURN connectivity,
credential rotation, authenticated access and the cross-network test matrix pass.

### Phase 1 test matrix

- Outgoing audio and video: Wi-Fi↔Wi-Fi, Wi-Fi↔mobile data and
  mobile data↔mobile data, including different carriers.
- Incoming offer, answer and ICE candidates delivered out of order; Realtime
  disconnected/reconnected; socket callback arriving after a newer connection.
- Drop and restore Wi-Fi midcall; test temporary packet loss, an ICE failure,
  and no network recovery (must exit reconnection rather than spin forever).
- Both microphones and cameras transmit, remote video renders, call ends
  without media left active. Repeat with both Android app processes restarted.
- Validate unauthorized/ended-call TURN requests are denied; verify no TURN
  master secret and no Supabase service-role key are packaged in the APK.

### Build/release note

Phase 1 code is staged on `Testlab`. Do not merge to `main` or publish
an APK until the Android build/test gate and physical-device verification pass.

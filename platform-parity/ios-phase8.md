# iOS Phase 8 parity status

**Source committed:** Gated CallKit / PushKit integration: incoming report, Answer / End / Mute delegates, authenticated call ownership check, one shared WebRTC coordinator, token registration client, generic caller label, user sign-out cleanup, tests.

**Not activated:** APNs private credentials, Apple signing/VoIP entitlements, APNs provider, Supabase token-registration/revocation backend, production iOS push notifications, device test. Release defaults keep VoIP push disabled.

**Existing platforms:** no Android/Windows app or production Supabase modification.

**Release tests:** iPhone↔Android audio/video, locked/background/terminated iOS, delayed/duplicate calls, DND, CallKit audio session, bad/revoked credentials, blocking/reporting; all must pass before any real user sees a VoIP push.

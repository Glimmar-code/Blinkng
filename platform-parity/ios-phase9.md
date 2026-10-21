# BLINK iOS Phase 9 — APNs backend infrastructure parity

**Code prepared:** server-only APNs device registration and revocation; per-device RLS-denied token store; internal authenticated VoIP dispatch with genuine ringing call verification, privacy/block checks, APNs ES256 token signing and duplicate suppression; optional Android call-invite handoff; iOS signout revocation; CI security checks.

**Not deployed or enabled:** Apple Developer App ID/entitlement/provisioning, APNs key in Supabase Secrets, backend tables/functions and server flags, iOS signed build, physical device token and APNs delivery.

**Not yet proved:** cold-launch incoming CallKit, screen-lock call answer, actual two-way iPhone↔Android audio/video, pending/cancelled call expiry, duplicate/late push, token revocation across account switches.

**Existing systems:** Android FCM behavior is preserved when iOS server flags are OFF; no changes to production app, data, payments or BLINK Coins.

**Promotion:** existing stacked iOS PR #416→#417→#419→#423→#426→#432→#444→#445→Phase9 draft. First pass every gate, then Testlab and real-device testing, main only after confirmed acceptance.

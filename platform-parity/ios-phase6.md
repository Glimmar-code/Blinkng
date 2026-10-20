# BLINK iOS Phase 6 platform parity

- In-app notification list: real current-user Supabase notifications; read-only until scoped authenticated GRANT/migration is reviewed.
- Notifications permission: user opt-in and APNs delegate foundation. **No background APNs delivery yet.**
- Calling: real Supabase recent call status and authenticated decline/cancel; does **not** start or answer non-working media calls.
- Still required: iOS WebRTC signaling/DTLS-SRTP/ICE/TURN, audio/camera permission & session, CallKit/PushKit for genuine system calls, foreground/background and Android cross-platform testing; APNs provider and token registration; notification mark-read grants; abuse controls.
- Production Android/Windows/Web not changed.
- Source-only staging; no claim of Apple TestFlight/App Store availability.

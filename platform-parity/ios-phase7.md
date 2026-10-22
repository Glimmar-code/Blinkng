# BLINK iOS Phase 7 parity

Implemented in source: native WebRTC peer connection with a pinned SDK, audio/video capture/render (subject to device permissions), offer/answer/ICE transport over existing Supabase tables, authorized TURN credentials, mute/speaker/camera controls, explicit foreground call UI and server-authorized call lifecycle RPCs.

Still missing for parity/release: system incoming calls (CallKit/PushKit), real background delivery, comprehensive reconnection/ICE restart, actual iPhone ↔ Android full media verification, call quality tests, group call media, accounts/privacy/abuse compliance, Apple signing/TestFlight.

Do not label `mediaTransportReady = true` until **two-way media** is proven on physical iOS + Android hardware. Staged feature branch only.

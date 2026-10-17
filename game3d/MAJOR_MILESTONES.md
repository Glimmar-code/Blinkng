# BLINK LIFE — Major improvements first

The optional game remains separate from the normal BLINK app. No
user who never opens BLINK LIFE should download or start its 3D
runtime by default. The 1,000-item realism tracker is a backlog,
not evidence of a complete game.

## Gate 1. Account-backed multiplayer foundations (in source on branch)
- Dedicated host accepts only valid short-lived signed game tickets.
- Single-server authenticated property ledger: anti-forgery checks,
  atomic saves, backup recovery, legacy import, no corrupt-write reset.
- Server accepts bounded unauthenticated joins and expires them.
- Public text chat is disabled pending moderation.
- **Missing:** real cloud deployment, encryption, end-to-end 2-device
  Internet test, account-safe database migration, operational uptime.
- **Done criteria:** confirmed connection and reconnection between
  Android and Windows over distinct public Internet networks, with
  server storage restart tests. No device verified yet.

## Gate 2. Persistent online world across game servers (not implemented)
- Staging Postgres profiles with server-only credentials and atomic
  idempotent currency/property transactions. Migrate legacy host
  records carefully; no account or property resets.
- Private instance selection, server lifecycle management, durable
  house state and backup/restore rehearsals.
- **Done criteria:** owned homes, virtual money and items survive server
  restarts and transfer between test instances.

## Gate 3. Real multiplayer life gameplay (not implemented at scale)
- Server-authoritative vehicles, legal movement, physics, passenger
  consent and nearby streaming optimized for slow networks.
- Join friends' worlds, approved home visits and persistent shared
  location **only inside the virtual world and only by opt-in**.
- **Done criteria:** multiplayer sessions and low-end Android stay
  stable under high latency and dropouts.

## Gate 4. Safe, downloadable public release (not implemented)
- Encrypted session transport, account permissions, abuse reporting,
  block/mute, human moderation, rate limits and safe defaults.
- Installable, on-demand verified content downloaded **only after
  a user chooses Play**, independent of BLINK feed/messages/market.
- Performance, storage, crash-free rate and beta feedback from
  actual Android and Windows devices.
- **Done criteria:** staging beta passes abuse/security tests and
  real-device coverage; production rollout can be rolled back safely.

**No production merge from a draft PR.** The major infrastructure
branch is independent of the earlier room-decor feature work. Work
toward live Internet play and safe persistence before polishing
minor household UI and cosmetic options.

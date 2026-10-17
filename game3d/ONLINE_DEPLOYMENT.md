# BLINK LIFE — Online multiplayer staging deployment

Status: **source code prepared; not deployed or production validated**. Do not mistake this for a live game server or a public multiplayer launch.

## Architecture

1. A signed-in BLINK user **chooses Play**. The Android host calls `supabase/functions/blink-life-session` using the existing session JWT, with a 3-second budget; it never passes the full JWT into Godot. On endpoint failure, BLINK LIFE opens offline without disturbing the rest of BLINK.
2. The Edge Function verifies that the user is authenticated via Supabase Auth and mints a **90-second, one-time signed ticket**, then returns dedicated server hostname and UDP port.
3. Godot receives just the ticket, host and port, connects through ENet UDP and registers. The server validates HMAC, expiry, nonce replay and duplicate authenticated users before adding them to the roster. Unauthenticated clients cannot participate.
4. Dedicated Godot hosts a bounded number of players (8 per instance). Real-world GPS is not collected; virtual coordinate visibility requires explicit opt-in. Home entry and ride seating require owner/driver consent.
5. Each account gets its own server-held fictional Game Cash, house purchase (400 GC), furnishings (150 GC each), and distance/cooldown-validated virtual depot shifts (+80 GC). The host owns the records; clients cannot submit balance changes.
6. Data is saved atomically to `user://blink_life_houses_v1.json` on the dedicated server. A persistent Docker volume is essential. This is **single-server file persistence, not a distributed Supabase database or global cross-server economy**. Migrating it safely to a staging Postgres schema must precede sharding and public deployment.

## Staging operator prerequisites

- Create an isolated staging/preview Supabase project, or another controlled test environment. No production Supabase mutations have been made.
- Provision an always-on Linux amd64 server with UDP port **24567** publicly reachable. Protect API, SSH and other ports with firewall; the Godot UDP port needs controlled exposure.
- Generate a unique high-entropy `BLINK_LIFE_AUTH_SECRET` (at least 32 characters), securely configure it on BOTH the dedicated game host and Supabase Edge Function environment. **Never** embed it in an APK, desktop binary, GitHub or the browser.
- Set `BLINK_LIFE_PUBLIC_HOST` to the host's public DNS name, `BLINK_LIFE_PUBLIC_PORT=24567` and `BLINK_LIFE_AUTH_SECRET` on the Edge Function. Runtime supplied `SUPABASE_URL` and `SUPABASE_ANON_KEY` must point to the same BLINK staging project.
- With project env variables configured, run `docker compose -f game3d/compose.server.yml up --build -d` on the server. `game3d/Dockerfile` pins Godot 4.3, uses a nonroot user and a persistent volume.
- Deploy the **staging** `blink-life-session` Edge Function only after confirming authenticated JWT verification and the Godot connection smoke tests. `verify_jwt=true` in `supabase/config.toml`. No production deployment command is included.
- Android Play will fetch a ticket after an authenticated user taps Play and autojoin if all infrastructure responds. Windows uses the same native Godot session UI, but does not yet have an automatic logged-in token bridge; manual short-lived ticket input is Testlab-only until that adapter is written.

## Abuse protection and privacy gates before public access

- Scope all social requests to authenticated users and apply blocks, moderation, server-side text filtering, friendship/consent-based invitations, server rate limits and packet budgets.
- Do not log tickets, account JWTs or private user identifiers unnecessarily. Rotate secret with overlap strategy or drain instances so current tickets can expire.
- Implement TLS-secured ticket issuance (via Supabase's HTTPS Edge Function). ENet UDP is **not encrypted**: replace with authenticated encrypted transport and perform penetration testing before production public deployment. HMAC ticket admission alone does not encrypt movement/chat in transit.
- Confirm server movement rate bounds, teleport/housing restrictions, passenger permissions, one-time nonce and double-login behaviour.
- Add rate-limited login issuance per authenticated account, instance selection/recovery, durable server-owned property saves in a real database, backups and audit trails; test load across multiple instances.
- Complete Android↔Windows parity and genuine Redmi-class hardware testing. Verify battery, heat, game-crash isolation, Internet connectivity, sign-out/session expiry, and return to BLINK.

## Deliberate non-features

- No cashout, gambling, real-money property purchases, BLINK Coins or BLINK-wallet integration.
- No real-world location sharing.
- No premium purchase pressure or exploitable pay-to-win rewards.
- No unrestricted public chat or proximity voice until moderation and privacy controls are complete.

See `ROADMAP_110.md` for all the requested features and their current scope.


## Major multiplayer staging hardening — online core branch

A separate draft Testlab pull request implements the first **critical
infrastructure gate** toward a real Internet world. It is not a live
server and does not change the release APK or deploy Supabase secrets.

**Server-owned home ledger:** existing UUID-scoped virtual home ownership,
fictional Game Cash and purchased furnishing counts now use a versioned
JSON envelope with an atomic write, strictly validated data, two rotating
checkpoints (primary plus previous-good backup), and startup recovery.
Unrecoverably corrupted saves or unknown future schema versions block
all writes rather than resetting purchases. Previously deployed flat
UUID-to-record V1 saves remain readable and are upgraded when a trusted
transaction writes. Recoveries should be logged and backups preserved.

**Admission controls:** dedicated sessions cap not-yet-authenticated
connections at four, allow only 10 seconds for a signed ticket, and
discard unauthenticated peers. Existing one-time HMAC tickets, duplicate
account rejection and server-authoritative purchases/jobs remain in
place. The dedicated server throttles actions (250 ms) and invitations
(5 seconds). Public, cross-user free-text chat is **OFF** until
block/report tooling, moderation and abuse escalation are verified.
Trusted private LAN preview remains separate. Do not expose user
addresses, tickets or account IDs through logs.

**Failure and recovery acceptance tests** must demonstrate: valid
old-format saves, new-format reloads, crash fallback to a valid backup,
recovery re-commit, arbitrary field injection refusal, negative or
malformed wallet refusal, expired unauthenticated peers, future-format
write protection, and no changes to regular BLINK app startup.

### Still blocking a real public online launch
1. **Server hosting:** provision and operate a real UDP-accessible
   test server; configuration alone is not a deployment.
2. **Encrypted transport:** Godot's current ENet UDP packets are
   **not encrypted**. Ticket-authenticated connection admission does
   not supply confidentiality or prevent on-path attacks. Use an
   authenticated encrypted transport before a public launch.
3. **Durable multi-instance database:** this file backend is one server
   only. Implement account-keyed transactions, idempotent economy
   events, backups and migration tooling in staging Postgres before
   public shared-world scaling.
4. **Player protection:** add block/report workflows, an operator
   moderation queue, chat/invitation privacy restrictions, appeals
   and spam controls before making social features public.
5. **Operations:** server health checks, structured safe logging,
   deployment secret rotation, instance allocation/recovery, load
   testing and latency/packet-loss observability.
6. **Device QA:** real Android and Windows login/join/disconnect, poor
   network, restarts, performance, opt-in coordinates, logout and
   background-app behavior.

**Fail closed:** property writes must stay disabled on disk errors.
Never enable unmoderated dedicated-world chat to simulate a complete
launch. No real-money transactions, BLINK Coins or cashout are part of
this milestone.

## Permanent plot addressing: staging development update

The Godot dedicated host no longer relies on current player join order
to determine owned-house coordinates. A `home_slot` is allocated on a
validated house purchase, uniquely reserved per account and stored in
the local authoritative ledger. Old three-field owned-property records
are migrated to stable plots and atomically saved before accepting
game sessions. Purchases and furnishing updates preserve the slot;
failing disk migration prevents the dedicated host starting, rather
than resetting ownership or offering a fake default balance.

The staging-only `game3d/sql/online_ledger_staging.sql` contract
proposes a unique home plot ID for use after a real database migration.
**Do not execute against production**. Run it only after careful
staging review and build the missing server-to-Postgres adapter.
Concurrent cross-instance assignments, staging migration reconciliation,
disaster recovery and encrypted public connections remain incomplete.

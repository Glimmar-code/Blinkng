# BLINK LIFE — Online multiplayer staging deployment

Status: **source code prepared; not deployed or production validated**. Do not mistake this for a live game server or a public multiplayer launch.

## Architecture

1. A signed-in BLINK user **chooses Play**. The Android host calls `supabase/functions/blink-life-session` using the existing session JWT, with a 3-second budget; it never passes the full JWT into Godot. On endpoint failure, BLINK LIFE opens offline without disturbing the rest of BLINK.
2. The Edge Function verifies that the user is authenticated via Supabase Auth and mints a **90-second, one-time signed ticket**, then returns a dedicated server hostname and WSS-over-TCP port.
3. Godot receives just the ticket, host and port, connects over certificate-verified WSS (TLS/TCP) and registers. The server validates HMAC, expiry, nonce replay and duplicate authenticated users before adding them to the roster. Unauthenticated clients cannot participate.
4. Dedicated Godot hosts a bounded number of players (8 per instance). Real-world GPS is not collected; virtual coordinate visibility requires explicit opt-in. Home entry and ride seating require owner/driver consent.
5. Each account gets its own server-held fictional Game Cash, house purchase (400 GC), furnishings (150 GC each), and distance/cooldown-validated virtual depot shifts (+80 GC). The host owns the records; clients cannot submit balance changes.
6. Data is saved atomically to `user://blink_life_houses_v1.json` on the dedicated server. A persistent Docker volume is essential. This is **single-server file persistence, not a distributed Supabase database or global cross-server economy**. Migrating it safely to a staging Postgres schema must precede sharding and public deployment.

## Staging operator prerequisites

- Create an isolated staging/preview Supabase project, or another controlled test environment. No production Supabase mutations have been made. The join function now refuses to advertise plaintext transport; old game clients require an update before connecting to TLS-enabled staging.
- Provision an always-on Linux amd64 server with TCP port **24567** reachable over an approved test network and a CA-trusted TLS certificate matching the game hostname. Protect API, SSH and other ports with firewall; the Godot TLS TCP port needs controlled exposure.
- Generate a unique high-entropy `BLINK_LIFE_AUTH_SECRET` (at least 32 characters), securely configure it on BOTH the dedicated game host and Supabase Edge Function environment. **Never** embed it in an APK, desktop binary, GitHub or the browser.
- Set `BLINK_LIFE_PUBLIC_HOST` to the host's public DNS name, `BLINK_LIFE_PUBLIC_PORT=24567` and `BLINK_LIFE_AUTH_SECRET` on the Edge Function. Runtime supplied `SUPABASE_URL` and `SUPABASE_ANON_KEY` must point to the same BLINK staging project.
- On the staging host set `BLINK_LIFE_TLS_CERT_FILE` and `BLINK_LIFE_TLS_KEY_FILE` to the mounted CA-trusted PEM certificate chain and corresponding private key. The nonroot container user must be able to read both files; never commit them to GitHub, log them or include them in an APK. A public game hostname and certificate must match exactly.
- With project env variables configured, run `docker compose -f game3d/compose.server.yml up --build -d` on the server. `game3d/Dockerfile` pins Godot 4.3, uses a nonroot user and a persistent volume.
- Deploy the **staging** `blink-life-session` Edge Function only after confirming authenticated JWT verification and the Godot connection smoke tests. `verify_jwt=true` in `supabase/config.toml`. No production deployment command is included.
- Android Play will fetch a ticket after an authenticated user taps Play and autojoin if all infrastructure responds. Windows uses the same native Godot session UI, but does not yet have an automatic logged-in token bridge; manual short-lived ticket input is Testlab-only until that adapter is written.

## Abuse protection and privacy gates before public access

- Scope all social requests to authenticated users and apply blocks, moderation, server-side text filtering, friendship/consent-based invitations, server rate limits and packet budgets.
- Do not log tickets, account JWTs or private user identifiers unnecessarily. Rotate secret with overlap strategy or drain instances so current tickets can expire.
- The dedicated transport is now WSS over TLS/TCP and verifies the server certificate hostname; the separate trusted LAN demonstration can still use unencrypted ENet on private Wi-Fi only. Stage-test real-domain TLS trust, expiry, renewals and security reviews before any public deployment. HMAC tickets additionally protect game account admission.
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


## Encrypted game transport — development branch

Major next infrastructure gate: signed online session tickets now
identify `transport: "wss"` only. Android verifies the transport, then
passes only its short-lived game ticket, TLS host and port to the
isolated Godot process. Dedicated Godot refuses startup unless the host
has a readable TLS certificate and matching private key. It listens
on TCP using `WebSocketMultiplayerPeer.create_server(...,
TLSOptions.server(key, certificate))`; Android/Windows clients use
`create_client("wss://HOST:PORT", TLSOptions.client())`, with hostname
validation and the normal trusted CA bundle. The Godot ticket is not
sent over unencrypted public ENet. Plain ENet is **LAN-only demo**,
not a valid signed Internet session transport.

The CI three-process test now uses a disposable certificate with a
`localhost` SAN and explicitly trusts that certificate for
**loopback-only integration tests**. This does not bypass production
certificate verification; there is no `client_unsafe()` fallback.
The CI signing key and TLS private key are generated locally and
discarded, never committed or deployed. A real dedicated server must
have a CA-trusted certificate, working hostname and renewals.

**Unfinished gates**: staging WSS deployment with real public DNS,
real Android/Windows connections across different ISPs, device
performance under loss/latency, real-time safety monitoring,
cross-server database storage, backup drills and full security review.
The host remains a single-instance staging prototype and has not been
deployed by these commits.

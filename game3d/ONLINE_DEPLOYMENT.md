# BLINK LIFE — encrypted dedicated Internet staging (not live)

**Status 16 October 2026:** Source-only staging. No game server, verified school registry or multi-instance persistence service has been deployed for public BLINK users. Android, Windows and the optional website APK must remain independently usable without the server. The website WebGL version remains an offline preview; only native BLINK LIFE uses authenticated dedicated networking.

## Secure transport and identities

1. A signed-in BLINK user deliberately taps Play in the **native** Android version, which requests a **90-second one-time ticket** over HTTPS through `blink-life-session`. The normal app and WebGL game do not auto-connect.
2. The ticket issuer advertises `transport: wss_tls`, the operator's TLS **DNS hostname** and public TCP port (default 24568). Android rejects plaintext/expired metadata. Godot connects only to `wss://hostname:port` and validates the CA chain/hostname; there is no `ws://` or plaintext ENet fallback for Internet sessions.
3. The dedicated Godot 4.3 server loads the operator-owned TLS certificate/full chain and private key from files that are NOT in GitHub or an APK. It validates HMAC ticket, expiry, nonce replay and duplicate authenticated accounts before adding players.
4. **Trusted LAN-only** host/join mode remains an ENet demo for two devices on the same network; **never port-forward or publish that LAN port**. No real phone GPS is accessed. Opt-in virtual game coordinates are not real locations.
5. Social invitations default off, home/ride requests need explicit consent, session blocking works, and public dedicated chat is disabled until effective moderation is in place.
6. Dedicated players currently have **single-server file** property storage. Multi-server state and university points are a separate staging backend contract; the `world_schema.sql` and `blink-life-world-sync` sources are **not deployed or wired to Godot yet**.

## Private operator deployment requirements

- Provision a domain you control, obtain a valid public CA certificate matching that hostname and arrange safe automatic renewals. BLINK's present GitHub Pages address is not a game server; **do not assume it hosts WebSocket sessions**.
- Host a Linux amd64 server, with TCP 24568 protected by an allowlisted firewall/rate-limiting proxy where practical. Do **not** open UDP 24567 to the public.
- Provision separate strong secrets for `BLINK_LIFE_AUTH_SECRET` (ticket issuer + dedicated server) and `BLINK_LIFE_WORLD_SYNC_HMAC_KEY` (only if the sync gateway is separately approved). Never put these values in a source file, logs or a mobile build.
- On the dedicated server, set `BLINK_LIFE_TLS_DIR` to a directory containing a **readable-to-container-user** `fullchain.crt` and `private.key` belonging to the operator. Store the private key using strict OS permissions. Configure `BLINK_LIFE_AUTH_SECRET`.
- The compose file provides a nonroot, read-only-rootfs Godot container, runtime memory/process caps, a volume for local data and a TLS-only TCP listener. Containers do not create or renew certificates.
- Deploy `blink-life-session` in **isolated staging** only after the matching WSS server works, with `BLINK_LIFE_PUBLIC_HOST`, `BLINK_LIFE_PUBLIC_PORT=24568`, `BLINK_LIFE_AUTH_SECRET` and Supabase Auth environment configured.
- Check actual trusted CA verification, invalid/revoked certificate rejection, stolen/expired/replayed ticket rejection, duplicate sessions, no unintended IPv4/IPv6 plaintext listeners, player blocking and WSS reconnects on actual Android and Windows.

## Persistent property / fictional reward / campus ranking staging

- `game3d/staging/world_schema.sql` is **review-only** schema SQL; it must **not be applied to BLINK's current production database**. Use a dedicated staging Supabase project after a DBA has reviewed it, created a backup and tested it.
- Private tables store **fictional, non-redeemable** Game Cash, one virtual house, one virtual vehicle, up to 12 furniture items, XP, event ledger, revision and verified campus membership. This is not BLINK Coins, ad rewards or real money.
- A service-role-only, idempotent database RPC applies fixed amounts from approved event kinds; neither clients nor Godot may submit arbitrary reward values. Job and duel XP have conservative daily caps. The event ID must be generated ONCE by the authoritative game server for the original verified action and reused for every retry.
- `blink-life-world-sync` is a **staged** Edge Function that checks a shared HMAC over the exact request bytes and timestamp, then invokes service-role-only RPCs. Use a secret manager; the service-role key must remain only in the Edge runtime. Never grant unauthenticated or normal client roles rights to the world-state RPCs or private tables.
- University rankings count points **only for administrator-verified university memberships**, never self-entered university profile text. There is no need to publish students' names, IDs, precise locations or contact information.
- Before activating sync: integrate the Godot game-server read/write adapter, test concurrent updates and crash recovery, prove no negative balances or duplicated reward credits, implement backups/restore, school verification workflow, fraud screening and rate limits, validate SQL RLS/advisors on staging, and build monitoring with rollback.

## Production-launch blockers

- **No deployed WSS domain/server or live TLS certs.**
- **No operational multi-instance property sync or leaderboard.** Current Godot social code still saves virtual property to one server JSON file.
- **No verified institution enrollment workflow or trusted ranking API to users.**
- **No full rigid-body car collisions, wheel simulation, suspension, driving sound or low-end device profiling**; the new movement model is lightweight kinematic motion only.
- **No 24/7 operations, backups, moderation/reporting, anti-abuse review, regional matchmaking, or measured server load capacity.**
- No paid matches, betting, cashout, BLINK Coin awards or monetized advantage.

Do not merge or release to production based on green headless CI alone. Require security review, staging DB and real-device regression tests plus public transport/load validation first.

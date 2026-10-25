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


## Current foundation branch: safe multiplayer interactions

This branch adds **server-enforced block/unblock for visit and ride
invitations**. Blocking a guest revokes pending invites and current visit
authorization; blocking a passenger also ends their ride. It applies
within the current session only, not across devices or logins yet.
Public-dedicated text chat remains disabled. A later service must
persist blocks and reports per verified BLINK account before public
social play.

The dedicated server now rejects entry into an unowned private home and
bounds on-foot movement, including the first position packet and
unauthorised vertical travel. This is **basic server-side anti-cheat**,
not vehicle physics, collision validation or an encrypted network.

A reviewed *staging-only* database draft lives at
`game3d/sql/online_ledger_staging.sql`. It shows a private Postgres
schema for UUID-linked virtual profiles and an idempotent,
server-only transaction for home purchase, furnishing and fictional
job rewards, with no authenticated client table/function rights. **It
has not been executed and is not integrated with the dedicated host.**
The actual server still uses the hardened single-host JSON ledger.
Deploying a database schema, provisioning host secrets and wiring
game servers must be explicit staging operator actions, never automatic
production account migrations.

### Eight major priorities, source reality

| Priority | State of this branch | Completion gate |
|---|---|---|
| Real Internet multiplayer | Authenticated dedicated ENet host source hardened | Cloud host, encrypted transport, distinct-network playtest |
| Permanent properties | Server ledger + recoverable backups; staging SQL proposal | DB adapter, staging migration, cross-instance durability |
| Vehicles & passengers | Existing consent-based LAN preview | Server-authoritative driving physics + reliable synced seats |
| Multiplayer friendships | Session invite blocks and existing opt-in roster | Persisted BLINK friendships, reports, moderation |
| FUTA-inspired world | Existing stylized local city | Rights-checked new map assets, scene streaming and on-device profiling |
| Jobs, businesses, economy | Existing trusted host Game Cash + staging idempotent contract | Server-side job validation, durable event ledger and abuse tests |
| Low-end Android | Separate process and on-demand Play preserved | Optional engine asset delivery and real low-memory tests |
| Security/operations | Ticket expiry, join throttles, read-only failures, invite blocks | Encryption, DDoS controls, moderation, dashboards and deployment |

Only the rows explicitly described as implemented in source can be
treated as completed development work. No public Internet server is
running as a result of this PR; no Testlab/main promotion or Play Store
release follows automatically.

## Major milestone: owned homes retain permanent virtual addresses

The earlier server used the **join order** to assign a home's virtual
coordinate. This was wrong: logging out and rejoining could move an owned
house and reuse a room slot for someone else. The new dedicated-world
code allocates one unique **home_slot** the first time a signed-in
account purchases its virtual home and records that plot ID with its
fictional Game Cash and furnishings. Reconnecting or restarting this
same server restores the same coordinates. Visitors still require
permission and blocking immediately revokes their access.

The existing disk-ledger reader migrates previously purchased V1 homes
with no address by assigning nonoverlapping plots in deterministic
account-ID order, then commits a versioned backup-protected checkpoint
before admitting players. Duplicate plots, illegal slot assignments
and future-format records fail closed. A player without a purchased
home receives no dedicated-home building. The in-game private wallet
status displays the owner's plot number; the virtual map still never
uses real GPS or property addresses.

The **staging-only** SQL proposal now includes a unique sequential
home plot ID and private idempotent property transactions. It has not
been deployed or connected to Godot and does not make the game
multi-server persistent. The production-ready solution needs staging
migration dry-runs, database adapter, encrypted networking, dedicated
hosting and Android/Windows tests before any live launch.

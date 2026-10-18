# BLINK LIFE 3D — Dedicated staging server launch gate

**Status: deployment-ready checklist in source, NOT a hosted server.**
This repository has no provisioned VPS, assigned staging DNS endpoint,
CA-issued staging certificate, or operator-approved staging Supabase
environment attached to this game branch. No code change can create a
running global online world by itself. Never activate production users
through an unverified host.

## 1. Operator requirements (staging only)

- Linux **amd64** VM with Docker Engine and Docker Compose installed.
- A dedicated **staging DNS hostname**, resolving to the host. Set
  `BLINK_LIFE_PUBLIC_HOST` to this DNS name on the **staging** ticket
  issuer, not a real-world player address.
- A valid **CA-trusted TLS certificate chain** and a matching private
  key for that hostname. Both files must be readable by Docker
  container uid **10001** through read-only bind mounts; do not place
  them in this repository, an Android APK or issue logs.
- A staging-only `BLINK_LIFE_AUTH_SECRET` of at least 32 high-entropy
  characters. Set the **same** value only in the dedicated game server
  and the **staging** Supabase ticket issuer environment. Rotate with
  an instance restart and accept that old short-lived tickets expire.
- Explicit operator approval to expose **TCP 24567 only**. Raw UDP is
  NOT the authenticated public game transport and is not published by
  the WSS compose stack. Restrict other ports with firewall.
- A backed-up, writable Docker volume for the game ledger. Cloud-wide
  properties still require the future database adapter. Do not apply
  `game3d/sql/online_ledger_staging.sql` without separate review.

**No real host, certificate or secrets have been supplied/activated in
this branch.** Never copy a staging signing key into a client or repo.

## 2. Fail-closed local Compose configuration check

From an operator-owned checkout of the reviewed commit on the staging
host, set these variables in the shell or secure host secret manager:

```sh
export BLINK_LIFE_AUTH_SECRET='<value-in-the-host-secret-manager>'
export BLINK_LIFE_TLS_CERT_FILE='/absolute/host/path/to/fullchain.pem'
export BLINK_LIFE_TLS_KEY_FILE='/absolute/host/path/to/privkey.pem'
docker compose -f game3d/compose.server.yml config --quiet
```

Check the server certificate matches the intended DNS name and has not
expired. Ensure the staging environment is not pointing at production.
The Compose file refuses to start with missing required host paths or
signing key. It runs the game process without root privileges.

## 3. Launch, observe, prove TLS

**Only after manual host review, backup, firewall and the staging
certificate/key checks:**

```sh
docker compose -f game3d/compose.server.yml up -d --build
docker compose -f game3d/compose.server.yml ps
```

The Docker image periodically executes
`game3d/scripts/server_healthcheck.sh`; a server is **healthy** only
while the Godot dedicated process writes a recent `ready` heartbeat
AFTER the TLS listener is created and its saved property ledger is
validated. Crashes, a missing heartbeat and stale state fail health.
This is an **internal file check**, not a public health/metrics API.
Docker Compose `restart: unless-stopped` recovers process crashes
but does **not** automatically repair corrupted property files.

On an operator computer (never with a production token), verify WSS
certificate-chain, DNS name and actual WebSocket upgrade:

```sh
python3 scripts/blink_life_staging_preflight.py \
  --host 'your-staging-game.example' --port 24567
```

The probe sends **no** user JWT, login ticket, account ID or secret.
It must reject untrusted certificates and non-WSS endpoints. Its
success **does not** prove an authenticated session or full Internet
routing on every mobile network.

## 4. Manual Internet acceptance tests — still required

Before enabling access to test users, use an **Android device and a
Windows device on different Internet networks**. Obtain staging
tickets through actual staging accounts. Verify that both join one
dedicated instance securely; a fake ticket fails; player positions
remain stable; approved guest visits work; blocked invites are
rejected; disconnect/reconnect and a host restart preserve virtual
property; no confidential credentials appear in logs. Test slow
mobile data, packet loss, certificate expiry, storage failures and
relaxed network conditions. Tickets must expire after 90 seconds.

Check that users who **never open BLINK LIFE** have unchanged BLINK
startup, feed, market, messages, background memory and APK size.
In-game GPS means **virtual coordinates**, never real phone location.
Public text chat remains disabled pending block/report moderation.
No BLINK Coins, cashouts, fiat payments or production accounts are
part of this staging gate.

## 5. Stop and rollback safely

If certificate verification, persistence, health checks, account
admission or real-device tests fail, **do not enable ticket issuance**.
Stop the staging world with:

```sh
docker compose -f game3d/compose.server.yml stop
```

Keep the persistent named data volume, back it up and investigate.
For rollback, redeploy a previously verified source revision with
the **same data volume**, after checking schema compatibility.
Never use `docker compose down -v` or wipe the ledger to silence
a failing restart.

**Not implemented here:** automated VPS provisioning, public deployment,
multi-instance Postgres adapter, game matchmaking, fleet monitoring,
account-persistent abuse reports or production traffic cutover.

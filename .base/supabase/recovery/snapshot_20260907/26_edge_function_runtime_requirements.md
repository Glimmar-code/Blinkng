# Edge Function runtime requirements — Blink recovery snapshot 2026-09-07

This file records **environment-variable names only**. Secret values must never be committed to GitHub. Restore values from the approved secret manager / Supabase project configuration.

| Edge Function | Required environment variable names | JWT verification |
|---|---|---|
| `game-rewards` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | enabled |
| `moderation-action` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | enabled |
| `verify-payment` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | enabled |
| `send-push-notification` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON` | enabled |
| `username-login` | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_PUBLISHABLE_KEYS`, `SUPABASE_SECRET_KEYS` | disabled; function performs its own client/key and credential checks |
| `share-preview` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_SECRET_KEYS` | disabled; public preview endpoint |
| `blink-web` | none read through `Deno.env.get(...)` | disabled; public web endpoint |
| `send-call-notification` | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON` | enabled |

## Recovery requirements

1. Restore the Edge Function source from `supabase/functions/`.
2. Restore the required environment-variable **values** outside Git from the approved secret store.
3. Preserve each function's intended JWT-verification setting above.
4. Deploy to an isolated recovery project first.
5. Verify deployed bundle/version behavior before promotion.
6. Never place `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_SECRET_KEYS`, Firebase private-key JSON, OAuth client secrets, SMTP passwords, database passwords, or access tokens in Android source, migrations, recovery files, workflow logs, or repository secrets documentation.

The deployment SHA-256 fingerprints and live versions are recorded in `24_live_state_fingerprint.md`.

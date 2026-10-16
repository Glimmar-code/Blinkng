# BLINK essential emails — black-and-white Testlab rollout

**Status: CODE STAGED, NOT LIVE.** This feature branch targets `Testlab` in PR #299. Do not merge to `main` or apply database changes in production without staging, QA and owner approval.

## Explicit user requirement: NO signup confirmation emails

**Do not activate, configure, or modify signup email confirmation.** This change removes the draft signup-confirmation template/config from the earlier PR. It does **not** change the existing Supabase Auth project's signup/verification policy. Disabling an existing required verification control is **not** part of this change.

Email-address **change** confirmation is a separate security flow and should remain controlled by Supabase Auth if that feature is used. Password-reset emails also continue to be handled by Supabase Auth.

## Features by activation

| Event | Source | Testlab status | Production state |
| --- | --- | --- | --- |
| Password reset | Supabase Auth | Premium black/white `recovery.html` template ready | Existing SMTP email observed; link/callback needs end-to-end re-test |
| Email change confirmation | Supabase Auth | Black/white `email_change.html` ready | Not changed |
| Password changed alert | Supabase Auth security notification | Black/white template/config ready | Must be enabled in hosted Auth settings and tested |
| Email changed alert | Supabase Auth security notification | Black/white template/config ready | Must be enabled in hosted Auth settings and tested |
| Welcome message | Confirmed-account database trigger → service-only outbox → Brevo HTTP worker | Queue + template + worker staged in GitHub | NOT deployed; must be staged first |
| New-device sign-in | **Trusted server** detects unfamiliar device, inserts unique outbox event | Template + protected dispatch contract staged | Event detector NOT implemented or connected; no emails yet |
| Suspicious account activity | **Trusted server** detects suspicious activity, inserts unique outbox event | Template + protected dispatch contract staged | Event detector NOT implemented or connected; no emails yet |
| Signup verification email | Supabase Auth | **EXCLUDED at user's request** | Existing Auth policy stays unchanged |

All templates use the same black/white brand, BLINK typography, white action buttons when necessary, black rounded cards, and support contact `blinksupport.ng@gmail.com`.

## Hosted Supabase Auth template deployment (manual)

Editing `supabase/config.toml` or committing HTML to GitHub does **not** update the hosted Supabase project's Email Templates. Configure this in a **staging** project first:

1. In Supabase → Authentication → Email Templates, open **Reset Password**, paste the raw HTML from `supabase/templates/recovery.html`, set subject to `BLINK | Reset your password`.
2. Repeat for **Change Email Address** using `email_change.html` if the account email change feature is already in use; leave signup confirmation settings alone.
3. In Supabase Auth security notifications, enable and configure **Password Changed** and **Email Changed** notifications using their matching template files, if supported by the dashboard.
4. Send only controlled tests to addresses you own. Verify full Gmail/Outlook appearance, button link, expiration, password-update behavior, and no token disclosure.
5. Prior user test redirected to `http://localhost:3000`; hosted Auth → URL Configuration needs appropriate actual Site URL and allowlisted Android callback `blink://auth/reset-password`. The BLINK Android app already requests that deep link. Check the web/Windows recovery behavior separately; changing Site URL alone does not create a web reset page. Never paste a reset link containing `access_token` or `refresh_token` into chat/logs.

**Important:** SMTP login is **not** the Brevo transactional HTTP API key. The local templates work with Supabase SMTP; the new outbox worker requires a separately configured Brevo API key.

## Welcome / security-email backend (staging only)

SQL migration: `supabase/migrations/20261016093000_blink_essential_email_outbox.sql`.

Worker: `supabase/functions/send-essential-email/index.ts`.

Non-destructive migration creates a service-role-only `public.blink_essential_email_outbox` table with RLS enabled, all anonymous/authenticated permissions revoked, strict message kinds, unique per-user event keys, status, retries, and priority. A `private`-schema Auth trigger queues `welcome` **only when** an account's `email_confirmed_at` becomes populated (or the account is created already confirmed). This does not send or require a confirmation email. Existing users are not backfilled.

The Edge Function takes **POST requests only** with an internal secret header `x-blink-email-worker-secret`. It does not accept a user's JWT or user-supplied email address as authorization, and requires a configured server-owned Supabase service role. It re-checks that the recipient address is confirmed, resolves that email only server-side, and uses Brevo's HTTPS transactional API. It sends no link or authentication token in welcome/new-device/suspicious messages.

**Required private settings in the STAGING Supabase Edge Function environment** (never put actual values in GitHub, APK/Windows app, screenshots, or this chat):
- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` (server-managed).
- `BREVO_API_KEY` (Brevo HTTP API key, not SMTP key).
- `BLINK_EMAIL_SENDER_ADDRESS` (verified sender, currently `blinksupport.ng@gmail.com` for testing where Brevo allows it; production should use an authenticated domain).
- `BLINK_EMAIL_WORKER_SECRET` (random 32+ character internal worker credential, unrelated to login credentials).

Invocation must come from a trusted scheduled server job after deploying the function, and **must never be called directly from an Android, Windows, or web client**. A maximum of 10 items per call and conservative 75 successful outbox emails per UTC day are applied to reserve SMTP capacity for account recovery. Built-in Supabase Auth emails also count towards Brevo's plan quota but are not automatically counted by this worker.

For **new-device** and **suspicious** alerts, don't enqueue emails based on arbitrary client assertions or user-supplied IPs; build a trustworthy backend login-event/device recognition source and throttling policy first. Only server-owned code using the service role may write those event types. Event key must be unique for the same user and security event. Prior to this, the templates and worker merely provide the dispatch infrastructure — no active detection is claimed.

## Testing and release gates

- Run `python3 -m unittest discover -s supabase/tests -p 'test_essential_email_templates.py'` for static template/permission checks.
- Apply the SQL migration **only in staging**, and check private trigger permissions, `auth.users` triggers, RLS, uniqueness and migration rollback compatibility.
- Test new sign-up with the project's **unchanged confirmation settings**. No new signup-verification email may be introduced by this work.
- Test one already-confirmed staging user onboarding event: exactly one welcome queued; reprocessing must not duplicate.
- Test new-device/suspicious events only using trusted backend-owned inserts in staging; do not allow app clients to fake or directly enqueue events.
- Configure a **separate** staging Brevo API credential; verify welcome email delivered, 429/backoff behavior, invalid sender errors, duplicate worker invocation, and daily limit.
- Confirm password changed/email changed notices arrive only after the actual Supabase Auth event with the correct real provider settings.
- Test Android and Windows sign-in/recovery flows; web callback if offered. Never reveal recovery tokens in logs or screenshots.
- Inspect visual delivery on Gmail mobile, Gmail web and Outlook; graceful behavior with images disabled; no external images or tracking pixels.
- Run Supabase migration/security CI and the standard Android/Windows build and parity gates. Merge to `main` only after **all** required gates and staging tests pass.

## Provider setup

- Supabase Auth SMTP: https://supabase.com/docs/guides/auth/auth-smtp
- Supabase email templates: https://supabase.com/docs/guides/auth/auth-email-templates
- Local Supabase templates: https://supabase.com/docs/guides/local-development/customizing-email-templates
- Supabase redirect allowlist: https://supabase.com/docs/guides/auth/redirect-urls
- Brevo transactional API: https://developers.brevo.com/reference/sendtransacemail

Previously shared SMTP credentials must be rotated before relying on production sending.

# BLINK referral email delivery — Testlab runbook

This change adds two transactional referral email events:

- `referral_confirmed`: sent to the referrer after the referred account confirms its email and the existing referral system persists the confirmed relationship.
- `referral_milestone`: sent when the referrer's lifetime confirmed-referral total reaches 1, 5, 10, 25, 50 or 100.

## Safety rules

- Emails are queued only for a verified referrer with `user_settings.email_notifs_enabled = true`.
- The trigger checks the durable `blink_referrals` relationship; the worker checks it again before sending.
- The outbox's unique `(user_id, kind, event_key)` key prevents duplicate sends for a referral or milestone.
- Deleted, banned, unverified and opted-out referrers are excluded.
- No referral coins, wallet balances, signup-confirmation policy, or Auth verification templates are changed.
- The outbox and Brevo worker remain service-role/server-only.
- Social digest delivery remains disabled until its separate opt-in, unsubscribe, scheduler and provider tests pass.

## Testlab migration order

1. Apply `20261101143000_blink_contest_launch_notifications.sql` (creates/extends the shared essential email outbox).
2. Apply `20261101160000_blink_referral_email_notifications.sql`.
3. Deploy the updated `send-essential-email` Edge Function to the isolated Testlab project only.

Do not apply these migrations to the existing production project as part of this PR.

## Required server configuration

Use the existing server-side Brevo configuration:

- `BREVO_API_KEY`
- `BLINK_EMAIL_SENDER_ADDRESS` (verified sender)
- `BLINK_EMAIL_WORKER_SECRET`
- Supabase-managed `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`

Never put any of these values in Android, Windows, web code, repository files or logs. Do not change Supabase Auth signup-confirmation settings.

The existing trusted minute scheduler must invoke the server-side notification worker, which drains the email outbox. Do not expose a public unauthenticated email-drain endpoint.

## Acceptance test

1. Create a Testlab referrer with a verified email and `email_notifs_enabled=true`.
2. Create a new Testlab account with that referrer ID, then confirm the new account's email.
3. Verify exactly one `blink_referral_confirmed` notification and one `referral_confirmed` outbox row.
4. For the referrer's first confirmed referral, verify one `referral_milestone:1` outbox row. Repeat with totals 5, 10, 25, 50 and 100.
5. Retry the auth/notification event and verify the unique outbox keys prevent duplicates.
6. Repeat with `email_notifs_enabled=false`; verify no referral email outbox rows are created.
7. Verify Brevo accepts the message and Gmail receives it; check Inbox, Promotions and Spam.
8. Verify worker rechecks the referral relationship, verified recipient, account state and master email preference at send time.
9. Confirm no wallet or coin ledger rows were created by this feature.

Production activation requires all Testlab acceptance steps, provider/sender verification, migration review, and CI passing.

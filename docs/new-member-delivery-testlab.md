# BLINK new-member email, SMS, contacts and push — staging activation

**Status: code staged, not deployed.** Do not merge or call this live until the following gates pass. A GitHub Testlab branch is NOT a staging Supabase database.

## Dependencies and rollout order

1. Create an isolated staging Supabase environment with permission and cost approval. Apply existing migrations in timestamp order, including the contact-discovery migration and this branch's essential-email outbox migration. Do not change production while testing.
2. Configure Brevo HTTP API secrets in staging only: BREVO_API_KEY, verified BLINK_EMAIL_SENDER_ADDRESS (support@blink.com.ng), and 32+ character BLINK_EMAIL_WORKER_SECRET. Do not put secrets into GitHub, Android, Windows, logs or ChatGPT.
3. Deploy send-essential-email with Supabase Edge JWT verification disabled ONLY for this internal worker; it checks the private x-blink-email-worker-secret header on every POST. Configure a trusted scheduled invoker using secret storage. Social email dispatch is disabled in this rollout.
4. Test a newly confirmed Google account: welcome email outbox must have exactly one pending row, then exactly one sent row after the scheduled worker runs. Verify the message arrives in a controlled inbox. No automatic backfill to older accounts. No signup confirmation policy changes.
5. Enable Supabase hosted Auth Phone provider and attach a real SMS delivery provider in staging (with cost controls). The app's Send SMS code action calls Auth phone update and Verify phone calls phone_change OTP. Test a number owned by the tester and verify auth.users.phone_confirmed_at. Never treat profiles.phone as proof of verification. Keep phone verification optional for signup until this works.
6. Configure a unique stable 32+ character BLINK_CONTACT_HMAC_SECRET in staging function secrets and deploy blink-contact-discovery with JWT verification ON. Run permission, HMAC matching, privacy, opt-out, delete, blocked-account, and rate-limit tests. Client reads Android contacts only after explicit consent; it never reads call logs.
7. After sign-in, register current-device FCM and grant notification permission. Verify active fcm_tokens row, correct delivery, foreground behavior, logout deactivation and account switching. Never reactivate a logged-out token directly in SQL.
8. Run static tests, Android JVM/lint/smoke tests and Supabase migration/security advisors. Complete real-device testing including normal signup, 400/429 errors, provider outage, empty phonebook and successful consent-based contact matches.
9. Promote code and deploy production services only after all the gates pass. Keep rollback plans and provider quotas monitored. The earlier account created before activation will not receive an automatic historical welcome email.

## Read-only verification queries

select to_regclass('public.blink_essential_email_outbox') as welcome_queue, to_regclass('public.blink_contact_settings') as contact_settings;

select kind,status,attempts,created_at,sent_at,last_error from public.blink_essential_email_outbox order by created_at desc limit 10;

## Stop conditions

Missing staging, provider authorization/credits, secrets, verified Brevo sender, trusted scheduler, or real SMS/inbox delivery means the corresponding feature is NOT fixed in production. Avoid direct production SQL guesses or bypassing phone verification.
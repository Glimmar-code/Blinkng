# BLINK Flutterwave: phases 1–2 (Testlab only)

## Scope

Phase 1: server-priced Flutterwave Standard hosted checkout, protected Edge Functions,
authenticated payment status checks, a public webhook authenticated by `verif-hash`,
a read-only customer ledger and idempotent database reconciliation.

Phase 2: Blue (₦800) and Gold (₦2,000) verification purchase from the existing
Android Get Verified sheet. Gold retains the existing 1,000-follower requirement.
Android never receives provider secret keys and never decides the charged price.

**Not included:** paid coin packs, boosts, advertising, auto-renewing subscriptions,
payouts, refunds, and marketplace settlement (phases 3–10).
Legacy `verify-payment` merely inserts a pending record; do not treat it as proof
of funds. The new Flutterwave pathways do not invoke that legacy handler.

## Safe staging rollout

1. Deploy `supabase/migrations/20261018190000_flutterwave_checkout_verification.sql`
   to a **separate Supabase staging environment**, never production first.
   Confirm `public.blink_flutterwave_payments` has RLS and no customer INSERT/UPDATE privileges.
2. Set staging-only Edge Function secrets `FLW_SECRET_KEY` (Flutterwave **test** secret),
   `FLW_WEBHOOK_HASH` (a unique, random webhook signing secret) and
   `BLINK_FLW_CHECKOUT_ENABLED=true`. Store them in Supabase secret management,
   never GitHub source, APK configuration or this document.
3. Register Flutterwave's staging webhook endpoint at
   `https://<STAGING_PROJECT_REF>.supabase.co/functions/v1/flutterwave-webhook`.
   Set exactly the same webhook hash in Flutterwave's dashboard.
4. Deploy `flutterwave-checkout`, `flutterwave-confirm`, and `flutterwave-webhook`
   using the JWT settings tracked in `supabase/config.toml`. The webhook alone
   has `verify_jwt=false` because its separate signature validation is mandatory.
5. Turn on `private.blink_economy_config.cash_checkout_enabled` **in staging only**
   after credentials and webhook configuration have been verified.
   The production default remains `false`.
6. Build a **staging APK** configured for the staging Supabase project.
   Test Blue and Gold purchases, callback/browser return, manual confirmation,
   repeated webhook delivery, duplicate confirmation and network loss.
7. Check CI: `node --test scripts/test_flutterwave_integrity.mjs` plus
   the existing Supabase safety, SQL syntax and Android compilation checks.
8. Do not promote until provider eligibility/approval, Android tests,
   migration review, payment reconciliation and release checks all pass.

## Production rollout gates

- Confirm the legitimate Flutterwave merchant account owner is verified/eligible.
- Check business configuration, supported channels and webhook routing.
- Configure **live** keys only in production Supabase secret management.
- Explicitly enable both independent cash-checkout switches **only after** verification.
- Deploy database migration before Edge Functions, and Edge Functions before APK UI.
- Verify real-money small-purchase behavior under authorized supervision and
  provider terms; do not use live credentials for automated tests.
- Verify webhook health and refund handling operationally before broad release.

## Security invariants

- The signed-in user may request checkout but cannot change amount, currency or payee.
- Client success redirects and incoming webhook claims are **never** trusted by themselves.
- A successful Flutterwave API response must match reference, status, amount, currency
  and provider transaction ID before BLINK updates a badge.
- Row locking, a unique provider transaction ID and a guarded RPC prevent duplicate
  verification extension on repeated confirmation or webhook delivery.
- Customers can SELECT only their own ledger entries; only a privileged server
  action can confirm payment and grant a badge.
- Existing active Gold users cannot be downgraded by an outstanding Blue payment.
- Automatic renewals require distinct, explicit enrollment and cancellation UX.
- BLINK Store remains frozen. Marketplace product posting remains free.

## Operational caveats

Flutterwave Standard redirects to `https://blink.com.ng/?blink_payment_return=1`.
The website is **not** proof that a payment succeeded. Android shows a separate
\"I've paid · Check verification\" action that queries a verified server endpoint.

Keep `BLINK_FLW_CHECKOUT_ENABLED=false` and `cash_checkout_enabled=false` until
staging is complete. Production Supabase is not modified by this PR.

### Rollback

Disable `BLINK_FLW_CHECKOUT_ENABLED` and the DB `cash_checkout_enabled` flag first.
Return the Android checkout to disabled. Do **not** delete settled payment
records or blindly revert completed verification entitlements.

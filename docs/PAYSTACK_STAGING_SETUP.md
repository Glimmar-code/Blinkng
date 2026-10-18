# BLINK Paystack Gateway — Testlab Integration

**Switch of provider:** Flutterwave PR #382 is closed without merging. This
implementation is Paystack-only. All new work must pass Testlab checks before
any production change. BLINK Store remains frozen and basic Marketplace
product posting stays free.

## Included now
1. Secure hosted Paystack checkout for Blue (₦800) and Gold (₦2,000)
   verification, with Gold's existing 1,000-follower eligibility check.
2. Purchases of existing server-priced Blink Coin packs.
3. Payment records in `public.blink_paystack_orders`, owner-only read access
   (RLS), server-controlled kobo prices and unique transaction references.
4. Private Supabase Edge Functions for initialization and verification,
   plus Paystack webhook HMAC-SHA512 checking against raw request bytes.
5. Independent server-to-server verification of successful transactions,
   including reference, kobo amount, NGN currency, Paystack transaction ID
   and customer email.
6. Atomic database fulfillment, so a duplicate callback/webhook can't
   duplicate coins or extend a badge twice.
7. Android UI: secure checkout links, Blink Coin pack chooser, pending
   payment state and manual confirm-after-payment action.
8. Two independent activation switches: `BLINK_PAYSTACK_CHECKOUT_ENABLED`
   in Supabase secrets AND `private.blink_economy_config.cash_checkout_enabled`.
   Both remain **OFF** until staging testing is completed.

## Staging setup (only when an eligible merchant account is ready)
1. Use an approved/eligible Paystack business account and **TEST** API secret.
   The legitimate merchant account holder must meet Paystack's age and
   identity requirements. Do not use another person's identification to
   bypass verification.
2. Provision a separate **BLINK staging Supabase project**. There is no
   authorization here to use the existing BLINK production database as staging.
3. Apply `supabase/migrations/20261018205000_paystack_staged_checkout.sql`
   to staging. Confirm tables, RPC grants, and RLS with the security advisor.
4. Set `PAYSTACK_SECRET_KEY` to the **test secret key** using Supabase secrets,
   not Android/Gradle, GitHub source, an issue comment or chat.
5. Deploy `paystack-checkout`, `paystack-confirm` and `paystack-webhook`.
   Check `supabase/config.toml`: checkout/confirm require JWT, webhook
   doesn't require JWT because it validates the separate Paystack HMAC.
6. Register the test webhook in Paystack dashboard as
   `https://<STAGING_PROJECT_REF>.supabase.co/functions/v1/paystack-webhook`.
   Callback after checkout is `https://blink.com.ng/?blink_payment_return=1`;
   this URL **does not** by itself prove a payment has succeeded.
7. Once staging webhook and secret are configured, set the staging-only
   `BLINK_PAYSTACK_CHECKOUT_ENABLED=true`, and staging DB
   `cash_checkout_enabled=true`. Never turn these on first in production.
8. Build a staging Android APK pointing to the staging Supabase environment.
   Test legitimate ₦800/₦2000/coin payments with Paystack's official test
   mechanisms; test pending, failed, repeat webhooks, retry, wrong amount,
   wrong currency, wrong email and duplicate confirmation.
9. Review security notices, Gradle builds and Play Store payment policy as
   applicable to app distribution and digital purchases.
10. Only after all gates pass, prepare a separate, reviewed deployment plan
    with approved **live** Paystack keys stored in production secrets.

## Remaining work (not falsely advertised as activated)
- Clear opt-in and opt-out recurring subscriptions, renewal notices, and
  failed recurring payment recovery; Paystack's subscription plans require
  additional merchant setup and must not be enrolled without user consent.
- Paid post/reel boosting, feed/marketplace advertising and gifts.
- Seller subaccounts, marketplace checkout, payouts and buyer disputes.
- Customer receipts, admin financial dashboards, refunds, payout history
  and automated email notifications beyond the core payment ledger.
- Full end-to-end checkout verification with real Paystack test credentials.

## Failsafe
Cash checkout starts disabled. If provider testing fails, disable the
environment flag and database switch. Keep transaction records for audits.
Do not refund automatically or award coins merely because a browser URL
reported `success`.

## Official Paystack references
- https://paystack.com/docs/api/transaction/
- https://paystack.com/docs/payments/webhooks/
- https://paystack.com/docs/payments/subscriptions/

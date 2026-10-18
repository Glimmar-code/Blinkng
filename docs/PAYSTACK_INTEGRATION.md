# BLINK Paystack integration — staging rollout

BLINK now uses **Paystack only** for the new cash checkout. The earlier
Flutterwave PR #382 is closed, unmerged, and none of its files exist in Testlab.

## Implemented on this feature branch
- Paystack Standard hosted checkout from authenticated Android screen.
- Supabase generates its own immutable order reference and approved price.
- Blue verification ₦800, Gold ₦2,000 (existing 1,000-follower minimum).
- Five BLINK Coin packs from the authoritative economy configuration.
- NGN-to-kobo conversion happens on the backend.
- Paystack transaction verification over server-side HTTPS API.
- Webhook authenticated with HMAC-SHA512 of raw request body.
- Double-credit and double-verification protection with ledger row locking,
  unique Paystack transaction ID, and atomic service-role fulfilment.
- Customer-only payment history rows under RLS.
- Android saves the pending checkout reference and offers a separate confirmation action.
- Existing 3,000-coin Blue verification option remains unchanged.
- BLINK Store remains frozen and Marketplace product listing remains free.

## Setup in a separate staging Supabase project (required)

1. Deploy migration `supabase/migrations/20261018225000_paystack_checkout.sql`
   to the staging Supabase project only, not production.
2. In **Supabase staging → Edge Functions → Secrets**, configure:
   - `PAYSTACK_SECRET_KEY`: the actual **test** secret from the merchant's Paystack account.
   - `PAYSTACK_EXPECTED_DOMAIN=test`
   - `BLINK_PAYSTACK_CHECKOUT_ENABLED=true`
   Do not put these values in Android, GitHub, or the public website.
3. Deploy `paystack-checkout`, `paystack-confirm` (both verify JWT),
   and `paystack-webhook` (JWT disabled, custom signature mandatory).
4. In the Paystack **Test** dashboard, register webhook
   `https://<STAGING_REF>.supabase.co/functions/v1/paystack-webhook`.
5. Explicitly enable `private.blink_economy_config.cash_checkout_enabled`
   in the **staging database only**, after secure test secret deployment.
   Default remains OFF everywhere otherwise.
6. Build an Android APK against staging Supabase, and test Blue verification,
   Gold eligibility failure/success, all five coin packs, network loss,
   failed payments, duplicate webhook delivery, retry and authentication.
7. Verify payment amount, currency, reference, status and unique transaction
   before receiving credits. App checkout return is never evidence of payment.
8. Confirm PR CI (Android build, unit tests, migration syntax, security tests).
   Merge to Testlab **only when green**. Do not publish from this branch.

## Merchant setup and production activation

An **eligible, verified adult merchant/business account owner** must complete
Paystack onboarding and any required KYC/legal approval. Do not use another
person's BVN/NIN or falsely claim eligibility. No credentials are in this repo.

Before live activation:
- Approve the Paystack account for the business and ensure payment channels
  are allowed for the intended BLINK purchases.
- Store a separate live `PAYSTACK_SECRET_KEY` only in production secrets.
- Set `PAYSTACK_EXPECTED_DOMAIN=live` only for the approved live merchant.
- Configure live webhook for the **production** Supabase URL.
- Deploy migration and functions before Android release.
- Turn on BOTH independent environment and database checkout flags only after
  verification and administrator sign-off.
- Verify refunds, chargebacks, receipts and transaction reconciliation before
  claiming full financial operations coverage.

## Pending separate feature work

**Not implemented in this PR:** recurring subscription plans, subscription
cancellations, multi-month discounts, direct marketplace checkout, seller split
payments, payouts, refunds, boost billing, finance analytics/exports, and
automatic recurring-payment email notifications. They require distinct
business-rule design and verified merchant capabilities, and should not be
claimed to work until their dedicated integration tests pass.

Paystack's subscription API supports eligible card and Nigerian direct-debit
authorizations. Never enroll a subscriber silently. Renewal terms, charges,
consent and cancellation must be clearly disclosed.

## Rollback

Disable `BLINK_PAYSTACK_CHECKOUT_ENABLED` and the database
`cash_checkout_enabled` flag. Existing settled payment records must
remain intact for audit. Never revert legitimate purchases by dropping rows.

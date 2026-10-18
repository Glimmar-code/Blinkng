# BLINK Paystack — staged payment integration

**Decision:** Paystack is the selected gateway. Flutterwave PR #382 was closed without merge.
This change starts from the Testlab branch to avoid unexpectedly rebasing 1000+ changes from main.

## Product coverage in this patch

- Blue verification: ₦800, 30 days.
- Gold verification: ₦2,000, 30 days; 1,000 real follower relationships required.
- Blink Coin packs: ₦100/₦500/₦1,000/₦2,000/₦5,000 (quantities from the private economy policy).
- Recoverable Android browser checkout and manual payment verification.
- Authenticated transaction lookup, server-verified value delivery, signed webhooks, idempotent coin crediting.
- **Cash checkout OFF by default.** Existing coins-for-Blue redemption continues separately.

**Not activated in this patch:** auto-renewing subscription billing, paid ads/boosts,
marketplace buyer payments/seller settlements, refunds or live production checkout.
Marketplace listing is free; there is no paid seller activation.
BLINK Store remains frozen.

## Required merchant and environment setup

1. The Paystack account must be operated by an eligible, verified merchant/account holder.
2. Create a separate **Blink staging Supabase project** with the compatible current schema;
   do not reuse the production Blink database for tests.
3. Apply the migration `20261018210000_paystack_payment_orders.sql` to staging first.
4. Configure staging Edge Function secrets using Supabase secret management:
   `PAYSTACK_SECRET_KEY=sk_test_...` and `BLINK_PAYSTACK_CHECKOUT_ENABLED=true`.
   Do not paste keys into GitHub, ChatGPT, Android manifests, web assets or app logs.
5. Deploy `paystack-initialize`, `paystack-verify`, `paystack-webhook` with
   auth rules from `supabase/config.toml`. Webhook alone has `verify_jwt=false`,
   but validates Paystack's raw-body `x-paystack-signature` (HMAC SHA-512).
6. Configure Paystack's TEST webhook URL as:
   `https://<STAGING_PROJECT_REF>.supabase.co/functions/v1/paystack-webhook`
7. Enable `private.blink_economy_config.cash_checkout_enabled=true` in staging ONLY.
8. Build an Android APK against staging; test Blue/Gold/Coins, network interruption,
   rejected signatures, amount/currency/reference changes and duplicate webhooks.
9. Verify the deposit in Paystack test mode and the exactly-once
   credit/badge update in the staging database.

## Android return and payment verification

Hosted checkout currently redirects to
`https://blink.com.ng/?blink_payment_return=paystack`.
The app stores a pending Paystack reference locally, prompts the user to reopen BLINK
and offers **Check payment** using the authenticated server endpoint.
Returning from the browser is not proof of payment, and app code never grants coins
or badges. The webhook can complete a purchase even if the phone is offline.

## Production gates (do not skip)

- Approved Paystack merchant and allowed settlement/payment methods.
- Test orders initialize and are signed correctly.
- Failure, duplicate-delivery and mismatch tests are green.
- Backend migration is reviewed against production schema **before applying**.
- Both `BLINK_PAYSTACK_CHECKOUT_ENABLED` and `cash_checkout_enabled` are independently
  off until credential verification and live checkout approval.
- Android APK smoke test and security checks pass.
- Promote to Testlab, then main, only after applicable checks pass.

## Future modules

Payment providers don't automatically implement recurring billing or seller
payouts. Auto-renew must be opt-in, clearly labeled, cancellable, and supported
by appropriate Paystack subscription plans; a customer pausing verification
benefits must not be silently charged. Refunds, split settlement, chargebacks,
payment history exports, admin finance dashboards and paid boosts require
dedicated implementation and tests before release.

## Rollback

Disable the Edge Function checkout flag and private economy cash flag,
roll back native checkout availability, and retain payment ledgers and
already-delivered user entitlements for reconciliation. Do not delete finance records.

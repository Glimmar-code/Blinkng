# Paystack readiness for BLINK

BLINK uses Paystack hosted checkout through Supabase Edge Functions. No Paystack secret is stored in Android, Windows, web assets, or Git.

## Server-authoritative products

The existing BLINK economy remains the source of truth:

- ₦100 → 100 Blink Coins
- ₦500 → 550 Blink Coins
- ₦1,000 → 1,200 Blink Coins
- ₦2,000 → 2,600 Blink Coins
- ₦5,000 → 7,000 Blink Coins
- BLINK Verified cash price → ₦800
- BLINK Verified coin price → 3,000 Blink Coins

Client code never sends an amount that the backend trusts. The backend creates an order from the private economy configuration, initializes Paystack using that amount, then fulfills only after server-side verification.

## Edge Functions

- `paystack-initialize` — authenticated. Creates a BLINK order and starts Paystack hosted checkout.
- `paystack-verify` — authenticated fallback. Rechecks a user's order with Paystack and fulfills it if valid.
- `paystack-webhook` — public webhook endpoint. Authenticates Paystack using `x-paystack-signature`, verifies the transaction again with Paystack, validates reference/amount/currency, then calls the idempotent server-only fulfillment RPC.
- `paystack-return` — public, read-only return page used after hosted checkout.

Expected production webhook URL:

`https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/paystack-webhook`

Default hosted-checkout return URL:

`https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/paystack-return`

## Secrets

Configure through Supabase Edge Function Secrets only:

`PAYSTACK_SECRET_KEY=sk_test_...` while validating the integration.

Later switch to the live secret after the Paystack account is approved and the test flow is proven. `PAYSTACK_CALLBACK_URL` is optional; when omitted BLINK uses `paystack-return`.

Do not commit any real `sk_test_` or `sk_live_` value.

## Production safety gate

`private.blink_economy_config.cash_checkout_enabled` stays `false` until:

1. Paystack has approved the merchant account.
2. The Paystack secret is configured in Supabase.
3. Test checkout initializes successfully.
4. The webhook signature check rejects invalid signatures.
5. A successful test transaction fulfills exactly once.
6. Amount/currency mismatch tests fail without granting value.
7. Android and Windows builds pass.

Only then should cash checkout be enabled.

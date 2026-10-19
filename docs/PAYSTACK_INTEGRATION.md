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

## Phase 2 on Testlab (not live)

The conflict-free PR now stages these extra pieces:

- **One-time multi-month verification**: 1, 3, 6, or 12 months; discounts of
  0%, 10%, 20%, and 30% respectively. Prices and terms come from a server
  generated order, not from Android or Windows.
- **Account-only payment receipts**: Android verification screen and an
  authenticated `paystack-history` endpoint; service-only finance summary.
- **Payment notifications**: an idempotent internal outbox entry on successful
  fulfillment. Push/email delivery still needs a sender worker and final tests.
- **Provider reversals/disputes**: signed webhook events create service-only
  audit entries; a confirmed full refund marks a payment refunded. Partial
  refunds are recorded for separate financial reconciliation. No customer-facing
  discretionary refund flow is implemented. Legally/provider-mandated refunds,
  fraud claims and chargebacks cannot be disabled.
- **Boosting**: existing paid visibility for posts, reels and marketplace
  listings is purchased with BLINK Coins. Direct naira billing of individual
  campaigns is separate, still unimplemented.

**Recurring card/direct-debit charges are not implemented or authorized.**
One-time multi-month checkout never enrolls users in automatic renewals.
An explicit, clearly explained renewal opt-in and a tested provider-side
cancellation endpoint are required before subscriptions can go live.

### No paid staging branch

Disposable PostgreSQL on GitHub Actions validates migrations and transaction
idempotency at no new Supabase branch cost. These tests do not use production.
A genuine Paystack TEST-mode end-to-end payment, webhook, and refund/dispute
scenario must still run in an isolated environment before LIVE activation.
Never repurpose another live app's database or turn on production just to test.

## Phase 3 — subscription, cash boosts and payment email (Testlab only)

**All three are source-code implementations, not active production features.**
Do not charge customers until the full merchant/transaction/device gates pass.

### Paystack recurring subscription

- User first buys a one-time Blue/Gold plan, then explicitly checks a separate
  consent box. Existing user accounts are never silently enrolled.
- `paystack-autorenew` retrieves the user's latest verified paid order and
  merchant authorization, checks Paystack's reusable credential and customer
  identity, and verifies an operator-configured plan code's billing interval,
  domain and exact NGN amount against the paid plan.
- Server creates Paystack subscription starting **after paid entitlement expiry**,
  not immediately charging again. Recurring billing runs at Paystack, not in
  Android, and a signed invoice is independently verified before any new grant.
- Cancellation calls the *provider* disable endpoint first and then updates
  server consent state. Turning a cosmetic toggle off never silently leaves a
  recurring mandate active.
- **Fail-closed environment switches:** `BLINK_PAYSTACK_RECURRING_ENABLED`
  and `BLINK_PAYSTACK_CHECKOUT_ENABLED` must explicitly equal `true`.
  `BLINK_PAYSTACK_RECURRING_PLANS` is a private JSON mapping for
  `BLUE_1`, `GOLD_1`, `BLUE_3`, etc, pointing to verified Paystack plan
  codes. Configure these only on an approved merchant's server; never in APKs.
- Missing reusable authorization, missing subscription email token, merchant
  mismatch, existing enrollment, wrong plan price or ambiguous network outcome
  all block enrollment. Manual Paystack dashboard reconciliation remains
  necessary for any ambiguous provider response. No automatic retries of
  enrollment to avoid unintended duplicate subscriptions.

### Direct cash-funded boosts

- The cash boost order uses the **same server-owned** content ownership
  validation, audience filters, duration and delivery budget as existing BLINK
  Boost/Market. No mobile-supplied price is trusted.
- An admin must explicitly configure `paystack_boost_naira_per_coin` (>0)
  after approving a price: default `0` disables cash boosts.
- After a **server-verified** Paystack payment, an atomic DB grant starts the
  boost campaign. Duplicate webhooks do not create duplicate campaigns.
- Existing undelivered budget logic returns leftover value as BLINK Coins,
  not an automatic bank refund. Provider-required reversals and chargebacks
  must still be handled.
- The isolated Testlab build lacks the modern Boost UI, so **the cash button
  must be connected to the latest Boost/Market screens after synchronizing
  Testlab with current main**. This is not user-ready at this stage.

### Transactional payment email

- Payment success, invoice-created renewal reminders, failed renewals and
  stop-renewal messages are queued with unique event keys.
- `paystack-payment-email` sends black-and-white transactional receipts over
  Brevo, behind the internal `BLINK_EMAIL_WORKER_SECRET`; it never accepts
  a recipient address supplied by a customer.
- `BLINK_PAYMENT_EMAIL_ENABLED` is OFF by default. The GitHub Actions worker
  runs every 30 minutes **only after** the repository variable
  `BLINK_PAYMENT_EMAIL_SCHEDULE_ENABLED` is `true`, and the
  `BLINK_EMAIL_WORKER_SECRET` and `BLINK_PAYMENT_EMAIL_WORKER_URL`
  secrets are installed. The Supabase function must be deployed first.
- Invalid sender/auth credentials, ambiguous sends and expired sessions fail
  closed. A provider-accepted receipt is marked sent; server errors have bounded
  retry. No social-email subscription is required for essential receipts.

### Release rules

1. Run disposable PostgreSQL assertions, Android/Windows builds and unit gates
   on the **exact PR head**, then independent Paystack TEST-mode payments.
2. Verify transaction replay, tampering, failed charges, signature validation,
   direct paid boosts, payment receipts and opt-in subscription cancellation
   with the actual merchant's test credentials.
3. Confirm merchant eligibility and approvals, server-only secrets, verified
   production webhook, notice delivery, monitoring and rollback procedures.
4. Deploy all migrations in order, then all Edge Functions, keeping flags OFF.
5. Enable only after explicit product, payment and compliance sign-off.

No paid Supabase staging branch is required; do not test by enabling
live customer charges. The merchant must meet Paystack's eligibility and KYC
requirements; do not misrepresent identity, age or business ownership.

## Pending separate feature work

**Not implemented in this PR:** actual provider recurring subscriptions and
cancellations, merchant auto-renew mandates, direct marketplace checkout,
seller split payments, payouts, voluntary refunds, direct cash boost billing,
automated finance exports, completed reversal/chargeback settlement and
automatic recurring-payment email delivery. They require distinct
business-rule design and verified merchant capabilities, and should not be
claimed to work until their dedicated integration tests pass.

Paystack's subscription API supports eligible card and Nigerian direct-debit
authorizations. Never enroll a subscriber silently. Renewal terms, charges,
consent and cancellation must be clearly disclosed.

## Rollback

Disable `BLINK_PAYSTACK_CHECKOUT_ENABLED` and the database
`cash_checkout_enabled` flag. Existing settled payment records must
remain intact for audit. Never revert legitimate purchases by dropping rows.

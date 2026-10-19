# BLINK Paystack: Marketplace purchases, Seller Settlements, Gifts and Refunds

**Default:** ALL new cash-money features stay OFF. No migration/function in this
branch has been deployed to production and no real funds may move until legally
eligible, verified merchant onboarding and completed payment/dispute testing.

## Added
- Paystack-held Marketplace buyer checkout from a current, approved seller listing.
- Server locks price, verifies listing + seller, limits one concurrent buyer checkout
  per listing, requires Paystack `charge.success` verified server-to-server.
- Successful paid marketplace order is marked `paid_hold`; seller payout remains
  blocked by default. This is a merchant-held settlement model, **not** a
  legally guaranteed escrow arrangement.
- Buyer-confirmed delivery and a 72-hour review period before an owner can
  approve the seller payout. Open disputes and refund requests stop payout.
- Owner-only verification of a seller's Paystack recipient code. No bank account
  numbers or merchant secret keys are stored in Android code.
- Owner-only payout and refund submission via Paystack APIs, both independent
  kill switches OFF, idempotent state transitions and manual reconciliation
  required after ambiguous provider/network failures.
- Paystack gift purchases of existing coin packs for another BLINK account,
  credited directly to the recipient's wallet only after verified settlement.
- Authenticated refund request queue and manually reviewed operator decisions.
- Android checkout entry on Product Detail, Gift Coins modal, Refund Request and
  Buyer-Confirmed Delivery from transaction history.

## Independently disabled gates
1. Existing `cash_checkout_enabled=false` in private economy config.
2. `BLINK_PAYSTACK_CHECKOUT_ENABLED=false` (existing).
3. `paystack_market_checkout_enabled=false` and `BLINK_PAYSTACK_MARKET_ENABLED=false`.
4. `paystack_gifts_enabled=false` and `BLINK_PAYSTACK_GIFTS_ENABLED=false`.
5. `paystack_refunds_enabled=false` and `BLINK_PAYSTACK_REFUNDS_ENABLED=false`.
6. `paystack_payouts_enabled=false` and `BLINK_PAYSTACK_PAYOUTS_ENABLED=false`.
7. Paystack test keys and `PAYSTACK_EXPECTED_DOMAIN=test` used in staging ONLY.

## Important engineering/legal caveats
- Do not describe funds as escrow; Paystack transaction splits can pay a seller
  immediately. Use only the approved business settlement strategy for the merchant.
- Seller eligibility, buyer protection and dispute handling require operational
  review and legal merchant compliance before launching live.
- Refund eligibility depends on provider transaction channel, settlement and
  merchant balance. Processing states are provisional until Paystack confirms.
- Refund initiated after payout approval is blocked; payout when a refund request
  is pending is also blocked.
- Store remains frozen; Marketplace item posting remains free.
- Both seller payouts and refunds may require extra Paystack merchant permissions.
- Merchant must be owned and legally operated by an eligible adult/registered
  business. Never falsify identity or use another person's confidential keys.
- **Neither TEST nor LIVE Paystack credentials belong in APK, repository, chat or logs.**

## Rollout and tests
1. Run the GitHub disposable PostgreSQL test workflow. It asserts server-controlled
   price, gift recipient, authorization grants, replay protection, and payout hold.
2. Apply new migration only to a separate staging Supabase project; deploy
   `paystack-checkout` and `paystack-market-ops`, with JWT enabled.
3. Run Paystack TEST-mode checkout with failed payment, reversal, duplicate
   webhook, overlapping listing purchases, invalid recipient and buyer dispute.
4. Finish supported checkout UI, refunds, operator financial approval screen,
   recipient verification, reconciliation/back-office accounting, seller policy.
5. Obtain the verified merchant's authorization before configuring LIVE keys.
6. Enable cash checkout and product-type flags only after full staging QA and
   approval, with feature-by-feature launch and documented rollback.
7. Never merge changes with failing checks and never publish an app whose backend
   tables/functions have not been deployed and validated.

### Known limitations
- Refund/transfer webhook status reconciliation must be verified against real
  TEST-mode provider payloads before activation; current changes stage initial
  flows but are not proof of real-money settlement.
- The first version of Marketplace checkout supports quantity=1 and an exclusive
  30-minute pending checkout window. Late payments or suddenly unavailable stock
  enter manual review and refund workflows rather than silently completing.
- Merchant reserve/available balance and fees require Paystack reconciliation.
- No production money movement, database deployment or live key activation
  is performed by this pull request.

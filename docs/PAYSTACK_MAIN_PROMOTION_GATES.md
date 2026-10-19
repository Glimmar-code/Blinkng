# Paystack v2 source promotion — do not activate production

This main-target PR isolates backend-only Paystack v2 files from the diverged
Testlab branch. It does not replace the existing live `paystack-webhook`, does
not integrate newer Android UI, and does not deploy Supabase.

## What's staged
- Server-authoritative Blue/Gold one-time pricing, coin purchases and paid
  boost campaign orders (no client-supplied amounts).
- Explicitly opt-in recurring Paystack billing with provider-side cancellation,
  not automatic enrollment.
- Supabase payment receipt, signature verification, idempotent fulfillment,
  merchant domain separation and disputes ledger.
- Brevo payment/renewal email worker protected by internal secret, disabled
  until intentionally configured and tested.
- Full disposable GitHub PostgreSQL migration/integrity CI.
- A **new** `paystack-webhook-v2` source function; legacy production webhook
  remains unchanged.

## Mandatory blockers before production activation
1. All PR build/security checks pass on the *exact main-target head*.
2. Perform real Paystack TEST-mode good/failed/replayed/underpaid transactions,
   subscription authorization/stop, signed invoice handling, reversal and
   merchant customer email reconciliation. Mock/Postgres CI is not equivalent.
3. Backfill all required production migrations; deploy functions with server
   secrets only after rollback plan and transaction reconciliation tested.
4. Integrate the main Android/Windows UI with backend independently; fully test
   purchase actions and cancellation on actual devices.
5. Set paid boost cash conversion price after explicit approval; the default
   price 0 disables this feature.
6. Keep `cash_checkout_enabled=false`, `BLINK_PAYSTACK_CHECKOUT_ENABLED`,
   `BLINK_PAYSTACK_RECURRING_ENABLED`, and `BLINK_PAYMENT_EMAIL_ENABLED`
   OFF until every appropriate gate has passed.
7. Audit ongoing customer payment protections, disputes, subscription consent,
   notifications and Paystack merchant approval. Provider-required chargebacks
   cannot be suppressed.

Main is >1000 commits ahead of the old Testlab fork. Do NOT merge Testlab
wholesale or force-push main: doing so could overwrite production improvements.
No paid Supabase staging branch was created.

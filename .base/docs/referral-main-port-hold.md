# BLINK Refer & Grow — main integration hold

This is a **scoped, non-production main-target draft**, not a release. Derived from merged Testlab PR #533 (Testlab merge `55da8858af259327b7affe8d97ee1577b3bbc26d`). It stages the independent server schema proposal, disposable PostgreSQL integration fixture, shared referral policy and tests on the latest production code. The application must **not** be published from this draft.

## Preserved behavior and remaining integration

- Current main differs markedly from Testlab (more than a thousand main-only commits). Do **not** merge all of Testlab to main and do **not** overwrite modified production files with older Testlab copies.
- Main uses a different signup signature: Android currently takes faculty, not the Testlab university/account-type callback. Reconcile signup metadata from the live current architecture for Android, Windows, and web. Do not invoke feature UI until the backend is deployed and verified.
- Port and verify authenticated referral selection, invite route, Search dashboard, new-user suggestions, notifications and graduated visibility into current main *after* reconciling each changed file. Preserve all unrelated newer main changes.
- The PostgreSQL migration is **not deployed**. Live Supabase project `jhwgifrlxwspoedxjaly` has neither referral tables nor a staging branch as of this integration review.
- The disposable SQL fixture passed on Testlab, but that does not establish real Supabase signup confirmation, profile triggers, auth redirect, RLS/permission grants, FCM delivery or database migration safety. Validate these first in safely isolated infrastructure. Never provision paid staging or modify production as a first experiment.
- Only after successful fresh main-target Android/Windows/web/SQL checks and hosted or fully representative safe backend tests may the actual integrated feature PR be made ready and merged to main. After merge, verify `Signed Android release` and `Publish latest APK` and the actual published download revision before claiming publication.

## Referral achievement semantics

Lifetime confirmed-referral achievements remain permanent: Starter (1), Builder (5), Advocate (10), Connector (25), Champion (50), Ambassador (100). Each new confirmation supplies 3 temporary discovery points on days 0–9, 2 on days 10–19, 1 on days 20–29, then none. Existing badges never expire. No guaranteed follow/view/coin rewards.

## Test status

- PR #533: all 12 pull-request checks succeeded.
- Testlab post-merge `55da8858...`: all eight applicable checks succeeded.
- This **main-target** draft is a new candidate: its own required checks, backend integration and release validation must be judged independently, never inherited from Testlab.

# BLINK Refer & Grow — Release Gates

The current PR #540 is a development candidate, not a published release.

## Safe integration and production order

1. Preserve diverged Testlab and main branches. Reconcile without destructive resets; rerun current-head CI.
2. Apply all five staged referral/group/impression migrations in order in an independently approved staging Supabase project/branch, not first on live auth.
3. Verify hosted signup pending -> email confirmed, duplicate callback, logout, account deletion, RLS, private-user exclusion, and zero Coin minting while disabled.
4. Stage the amended send-activity-notification function and new blink-referral-push-worker together. Configure a long random BLINK_REFERRAL_WORKER_TOKEN only in Supabase Edge secrets and GitHub Actions secrets.
5. Verify the service-role worker cannot send unrelated notifications, unprivileged users cannot claim or acknowledge messages, and duplicate retries do not notify twice.
6. To enable the opt-in retry workflow set GitHub Actions variable BLINK_REFERRAL_PUSH_ENABLED=true and BLINK_SUPABASE_REF to the approved project ref. The worker runs every five minutes; this is eventual delivery, not guaranteed instant push.
7. Test 30-day invite opens as estimates only; unique random browser ID can be reset and is never evidence for referral rewards. Confirmed email signups are the source of truth for conversion.
8. Review high-velocity risk flags before any Coin activation. The initial 1/5/10/25/50/100 Coin milestone settings remain disabled pending a security and economy approval.
9. Test Android deep links, optional referrer pre-selection, sign-up, post-Birthday placement, fallback messaging, account switching and recommendations.
10. Test Windows picker, real avatar thumbnails, signup, network errors and recommendation screens. Native protocol links and Windows push delivery are not yet proven.
11. Test website invite links, anonymous visit events, private profiles, Search ranking, conversion analytics and JS/browser accessibility.
12. Complete full actual Android/Windows/device-to-device/authenticated runtime tests, not only synthetic CI tests.
13. After staging signoff, snapshot the production schema and apply migrations sequentially with a rollback plan; deploy worker and scheduler after verifying secrets and push permissions.
14. Only then merge verified code into main, build signed APK, confirm installed build SHA and publish to the official website.

## Unresolved blockers

- No hosted staging branch: creation may incur charges and needs explicit cost authorization.
- No real confirmed hosted signup and offline FCM test of the latest candidate.
- Five-minute scheduled retry is not instantaneous; truly immediate independent delivery needs secured event-triggered setup.
- Group/community memberships require backend permission checks and real invite endpoints; autocomplete alone is insufficient.
- Profile-open events are not verified screen-impression events.
- Fraud prevention is a velocity flag, not complete anti-Sybil prevention; manual moderation tools remain missing.
- Coins are disabled by default and should stay so until budget and fraud checks are approved.
- Current-head CI and real-device verification must pass before release.

## Additional development work — not deployed

- Added consent-first group invitation APIs and actual Android, Windows and web
  invitation selectors. The group organizer is the only initial participant;
  invitees accept or decline using their own authenticated RPC. Server limits
  invite creation, verifies blocks/privacy and writes in-app notifications.
- Added source-qualified Search/recommendation impressions to a separate private
  ledger with no Coin or permanent reward effect. Android, Windows and web
  attempt to record only cards with >=60% viewport visibility for >=750ms.
  Browser and device screenshots/scroll tests are still required for proof.
- For production prepare all five SQL files in timestamp order:
  20261024203000, 20261025114000, 20261025153000, 20261025190000,
  20261025201000. Do not run them first on production.
- The disposable PostgreSQL fixture has newly added invitation and impression
  assertions. Their latest-head workflow result is a release gate and is not
  implied by an earlier green workflow.

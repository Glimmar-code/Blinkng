# BLINK full-app audit: Testlab release gate (28 October 2026)

## Scope and evidence

The owner authorized repairs after a 500-point QA plan. **The 500 points are
test scenarios, not 500 confirmed defects.** This branch is an incremental,
non-production repair candidate, not a claim that every feature is complete.

Baseline inspected: `main` commit `f9d3e68c436f3a0f8ab23cc5304fc857dfc35b3d`.
Backend inspected read-only: Supabase project `Blink` on 2026-10-28.
Neither this branch nor the earlier static audit has operated a signed APK,
performed a two-account end-to-end test, or tested on the target Android 15 device.

## Verified code and backend facts

1. Production `public.blink_trending_tags` filters the last **seven days**.
   Android and Windows already have 30-day-first, seven-day-fallback clients.
   The legacy Android method has now been aligned with that same contract.
2. `docs/integration/blink-search-30day-trends-staging-20261027.sql` defines
   restricted `blink_trending_tags_30d` and `blink_trending_creators_30d`.
   This candidate now excludes expired verification badges from the creator
   result. Source and schema columns were compared with the live schema.
3. Both SQL SELECT bodies were checked with **read-only EXPLAIN** on the live
   PostgreSQL schema (without executing or deploying either function).
   SQL syntax/planning passed; this is **not** runtime or RLS acceptance.
4. The Supabase database-advisor snapshot contains 299 security notices and
   264 performance notices. They are warnings, not 563 proven vulnerabilities:
   - 45 RLS-enabled tables without policies (some intentionally internal).
   - 10 anonymous-callable and 243 authenticated-callable SECURITY DEFINER RPC
     notices; review intent, caller checks and row visibility before changes.
   - 1 leaked-password-protection configuration warning.
   - 61 unindexed foreign-key notices; 78 auth RLS init-plan notices;
     101 possibly unused indexes; 24 multiple-permissive-policy notices.
5. Current hosted Edge Function inventory did not include the required
   contact-discovery integration. Phone/SMS provider and consented real-device
   matching must be tested before enabling that flow.
6. Native chat polls, live-location consent, complete R2 private-media
   migration and comprehensive media export/cross-device acceptance remain
   outstanding in the documented feature PRs.

## Blocking safety requirements

**Do not merge this branch or apply its SQL in production** until:

- An isolated, production-compatible PostgreSQL environment verifies both
  new RPCs for authenticated/anonymous users, blocks in both directions,
  private/deleted/expired posts, no duplicates, ranking, school filters,
  row counts, expiry and absence of leaked profile data.
- Android unit/lint/build and Windows parity tests pass on this exact SHA;
  run `node --test supabase/tests/search-30day-contract.test.mjs` as a
  source-level check, not as a replacement for database integration.
- The released APK is actually installed on the target Android 15 device;
  startup, login/signup, Reels, feed, chat, media, Market, Channels,
  payments, offline lifecycle and navigation are exercised against safe
  disposable accounts; permissions and accessibility are rechecked.
- Any backend migration is generated through the supported Supabase CLI,
  reviewed, applied to staging and validated **before** production promotion.
  No paid Supabase preview resource may be created automatically.
- The existing owner-managed PR #608 (Cloudflare R2) and PR #566 (Actions
  policy) stay separate and untouched until their individual gates pass.
- Security advisor items are triaged individually. Do not mass-grant RLS,
  revoke intentionally public functionality or delete indexes merely to
  lower the notice counter.
- Do not claim a main merge, hosted deployment or signed APK publication
  from source/EXPLAIN/static-test results.

## Tracking the 500 scenarios

The audit covers these 25 areas, each with ten actions and both ordinary
and interruption/recovery variants:

Startup/splash; password authentication; signup; Google onboarding and
referrals; sessions; contacts; Home/feed; post details; post composer;
Reels playback; Reel editor; Stories; profiles; Search; Connect;
Messages inbox; chat message interactions; chat media; calls; Channels;
Market; Wallet/coins/XP; verification/boosts/drops; notifications/settings;
admin/website/release parity.

Each scenario needs a result of **pass, fail with reproduction, blocked, or
not run**, with device/build/SHA and supporting traces. The baseline status
is **not run**. No fabricated test pass or bug count is permitted.

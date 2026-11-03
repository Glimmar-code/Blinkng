# BLINK Verified Membership Value — Testlab delivery and roadmap

## Pricing and non-regression rules
- Blue: existing ₦800 / 30-day plan or 3,000 Coins. Gold: existing ₦2,000 plan and current 1,000-follower eligibility; no change to pricing, renewals, HP/XP, organic ranking, or Coin awards.
- Verification is a paid BLINK status, not proof of government identity, real-world safety, or a guaranteed seller.
- Keep ordinary posting, Reels, messages, essential security, basic statistics, and account reporting free. No paid boost to organic ranking, leaderboard XP, or moderation outcomes.
- Only server-authoritative, active membership records permit subscriber benefits. The Creator Studio presentation uses an existing authenticated profile snapshot; its local calculations are **not** an authorization boundary.
- Avoid fake engagement, guaranteed followers, artificial scarcity and uncapped spending.
- Follow Play Store/distribution rules for subscriptions and digital services; protect underage accounts appropriately.

## Implemented in this Testlab PR
1. Android and Windows Creator Studio surfaced from one's own profile. Essential counts show for every owner, free included; aggregation is shared Kotlin logic.
2. Blue shows content interaction breakdown, loaded-content top-performing posts and Reels. Gold additionally shows hashtag performance and a shareable Android text report or Windows clipboard report.
3. Counts are computed from actually loaded original content with de-duplication and missing-save warnings. Qualified views are not unique people, and full-lifetime totals and audience retention are never invented.
4. Get Verified and Wallet screens describe these *working* benefits instead of presenting cosmetics as the only value.
5. JVM tests cover duplicate posts, missing saves, negative count safeguards, sponsored/reposted exclusions and empty data. Desktop excludes saves entirely because its post DTO does not supply that count.
6. Gold Merchant Pro read-only listing health: low-stock prompts where quantity exists, missing photos, top loaded items by reported views, and loaded category breakdowns in Android and Windows. Seller management and payment rights are unchanged; only seller-owned listings are included.

## Additional working Testlab features (October 28)
- Gold Channel Pro adds loaded-update view/reaction/poll breakdowns on Android and Windows for authorized owners/moderators with active Gold membership. It also offers editable announcements, event/recap templates and a 24-hour UTC schedule shortcut through the existing server publishing flow. Existing channel moderation and scheduling remain available to everyone who already has those admin rights.
- Blue and Gold creator workspace creates editable AI briefs for post ideas, Reel scripts, Channel announcements and safe event planning. Both Android and Windows open their EXISTING AI interfaces with prefilled text only; the person must press Send. The AI provider remains subject to its existing quota and availability. No new paid AI tokens or unlimited AI access are promised.
- Gold creator portfolio composes a shareable public bio/headline/topic introduction. It does not expose private phone/email or manufacture lifetime audience totals.
- Gold event organizer creates an invitation from an explicitly supplied name, time and venue; Android sends via chooser and Windows copies text. It does not create public RSVP records, calendar bookings or guaranteed attendance.
- Profile deep-link Robolectric regression tests were adjusted to scroll the actual LazyColumn when the new owner dashboard pushes tabs below the first viewport; the test remains an actual selected-tab assertion.
- Existing post and Channel scheduling, cosmetic Store items, photo/voice/reel editors and seller permissions are preserved; they are NOT falsely relabeled as newly implemented verification-exclusive backend grants.
- Blue/Gold profiles now show an active-membership signature on Android and Windows, using shared `BlinkVerifiedIdentitySignature`. This included cosmetic is **not** a premium Coin Store ring/banner/effect grant and cannot bypass Store ownership.

## Entitlements requiring separate provider/backend work before activation
- Monthly Gold promotion credits: **NOT funded/active**. Requires an owner-approved credit value, immutable ledger, verified payment/renewal lifecycle, idempotent issuance, spending debits, refunds, expiry, anti-fraud, user receipts and staging tests. No UI should claim issued credits.
- Premium priority support: **NOT active**. Requires service hours, support queue ownership, escalation SLAs and honest responses; paid status must never override safety or abuse reports.
- Premium visual Store bundle grants: **NOT active**. Existing Coin Store cosmetics have separate paid ownership. Do not bypass server-managed ownership or turn purchased cosmetic inventory into free membership grants.
- HD Reels editing/export and automated post scheduler v2: **NOT active**. Requires encoded file validation on budget phones, queue reliability, Windows parity, expiry and publication authorization, and backend/provider limits.
- Channel advanced moderation/team permissions, event RSVP database, AI paid quota expansion, full-range analytics and website parity still need backend schemas, RLS, tests and release controls.

## Follow-on scope — not shipped by this PR
- Creator Studio v2: server-side creator analytics for complete date ranges, safe aggregation, private RLS, indexing and backfill; Reel retention, reach and accurate posting-time advice only after instrumented event collection.
- Premium identity bundles: connect existing store cosmetics (profile banner/rings/themes) to monthly entitlements across Android/Windows/web; provide deterministic equip, expiry and safe fallbacks.
- Channel Pro: gated scheduler, channel analytics, moderation/admin collaboration and notification preferences with channel-specific quotas and abuse safeguards.
- Merchant Pro v2: account-wide inventory diagnostics independent of Market browse/search filters, saved customer replies, comprehensive item-level reports and compliant labeled promotions. Current v1 shows only seller-owned Market items loaded on screen; seller permission stays server-owned and verification alone is not a real-world guarantee.
- Creator tools: improved multi-post scheduler, reusable draft templates, meaningful premium video editing features, accessible device-safe export.
- Creator AI: clear usage caps, consent/privacy protection, data handling and free baseline. Avoid revealing private audience identities.
- Creator portfolio and event organizer: accessibility, deep links, safety/moderation and ownership checks.
- Gold promotion credits and priority support: model costs, transparent receipts, anti-abuse, fair sponsored labeling, published support coverage. Never promise viral success.
- Full Windows/browser parity beyond the current Creator Studio, durable offline states, receipts/refunds, subscription grace/renewal tests and complete screenshot/device validation.

## Release validation gate
1. Run unit tests, Android lint and assemble (Android quality gate).
2. Verify low-end Android 15 scrolling and modal behavior, light/dark, 200% font and TalkBack.
3. Verify no stale premium access after expiry and no false gating of free features.
4. Re-check Testlab branch against latest main; rerun CI if rebased.
5. Do not merge on skipped, cancelled, pending or failing required checks.

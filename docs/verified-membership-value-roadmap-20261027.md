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

## Follow-on scope — not shipped by this PR
- Creator Studio v2: server-side creator analytics for complete date ranges, safe aggregation, private RLS, indexing and backfill; Reel retention, reach and accurate posting-time advice only after instrumented event collection.
- Premium identity bundles: connect existing store cosmetics (profile banner/rings/themes) to monthly entitlements across Android/Windows/web; provide deterministic equip, expiry and safe fallbacks.
- Channel Pro: gated scheduler, channel analytics, moderation/admin collaboration and notification preferences with channel-specific quotas and abuse safeguards.
- Merchant Pro: inventory alerts, saved customer replies, item-level reporting and compliant labeled promotions. Seller permission stays server-owned; verification alone is not a real-world guarantee.
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

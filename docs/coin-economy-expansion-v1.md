# BLINK Coins: responsible Store expansion (Testlab)

## Product direction
Make coins useful for optional expression, social celebration and transparent paid promotion.
Do not pressure people to buy coins or suggest that payment guarantees popularity.

## Current implementation audit (2026-10-10)
Most of the proposed features already have existing Store catalog IDs, RPCs, or app routes.
Do **not** seed duplicate IDs or change an established wallet contract.

| Requested feature | Reusable BLINK capability | Status / action |
| --- | --- | --- |
| Premium profile | profile aura, ring, banner, themes, name, entrance, badges | Existing catalog. Test real rendering on Android/Windows. |
| Digital gifts | digital_gift and rose/crown/trophy/galaxy collectibles | Existing. Verify sender authorization, recipient lookup, cancellation, animation and non-cash character. |
| Promote posts/reels/market | post/reel/profile/marketplace promotions and Boost Growth | Existing. Verify Sponsored labelling, budget caps, real analytics; never promise viral results. |
| Premium chat | chat bubble/background, premium sticker and reaction packs | Existing. Verify two-device appearance and per-user equip semantics. |
| Themed collections | existing 25 groups, Vault/Collection and creator/market promotion bundles | Existing. Curated BLINK Identity looks in this PR only link existing products, not a new discounted bundle. |
| Creator identity | creator badge, intro, post/reel visuals | Existing. Test attribution without changing XP/organic ranking. |
| University collections | generic campus-inspired cosmetics | **Not shipped**: official school-specific assets, licensing and product catalog rows still required. |
| Seasonal collections | birthday styles and limited cosmetics | Existing partial. Server-dated drops, expiry, purchase receipts and supply need staging verification before calling anything limited. |
| Coin pack store | 100/550/1200/2600/7000 coin policy | Existing pricing defaults; cash checkout is disabled pending a verified payment path. Avoid fake payment confirmations. |
| Game cosmetics | Games route | **Not shipped**: game avatars, owned inventory and matching backend entitlements need product/technical design. |
| BLINK Identity lookbook | existing profile, creator and celebration groups | **Implemented in this Testlab PR** with no new paid SKU, currency charge or backend mutation. |

## Implemented in this staged change
- Shared `BlinkStoreJourneys` drives Android and Windows goal-based discovery without duplicated business rules.
- Eight available journeys link only existing Store groups; three planned journeys are explicitly *not offered*.
- Three preview-first BLINK Identity looks link to legitimate purchasable variants; buying pieces is individual, optional, and uses existing confirmation/Vault flow.
- Existing 70-item catalog, 25 groups, prices, wallet, purchased entitlements, promotions, VIP and routing remain unchanged.
- No new app permissions, SDKs, animations, payments, backend RPCs or migrations are introduced.

## Required follow-on implementations before claiming all features launched
1. **Payments**: verify the correct permitted checkout method for each Android distribution channel, server-verified receipt handling, signed webhook reconciliation, idempotent crediting and refunds; enable buying coins only when a real end-to-end flow works. Never credit from client receipt text alone.
2. **Gifting**: inspect existing gift RPC for atomic spend, own-account transfer rules, blocks/privacy, recipient confirmation where appropriate, anti-spam limits and moderation. Keep gifts non-withdrawable.
3. **Promotions**: ensure each placement is clearly marked Sponsored, separated from organic rankings, with measurable delivery, anti-fraud and optional budget controls.
4. **Cosmetics / collections**: register official university and seasonal SKUs in a versioned migration, stage assets, real availability dates, accessible static alternatives, previews, and 7-/30-day/permanent labels where applicable.
5. **Games**: define character identity schema, grant/equip authorization and game renderer parity before making game cosmetics purchasable.
6. **Price/value alignment**: monthly verified is currently ₦800 cash or 3,000 coins. Confirm a coherent policy with the product owner before changing prices; changes must remain server-authoritative.
7. **Fairness & safety**: never buy XP, organic leaderboard spots, creator verification or game wins; avoid random paid rewards, urgency pressure, repeated spend nags and artificial scarcity, especially for students and younger users.
8. **Analytics**: measure Store discoverability, purchase error rate, successful fulfillment, retention, voluntary repeat use and refund/support requests—not only revenue.

## Validation / release gate
- Shared Kotlin: run existing grouping tests and `BlinkStoreJourneysTest` (mapping, planned-feature exclusion, identity looks).
- Android: `./gradlew :shared:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; manual 2-column gallery, search/filter reset, previews and accessibility.
- Windows: desktop compile, 3-column gallery, previews, chips and parity smoke.
- Web: no new checkout or products; separately validate web parity when changing its Store journey UI.
- Supabase: no migration here. Future SKU/RLS/RPC changes must run on staging/preview first.
- Do not merge into main or deploy until the relevant gates pass. Never push unverified economic changes into production.

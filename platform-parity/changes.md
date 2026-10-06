## 2026-10-06 — Leaderboard Top 20 + Connect Hub/Students split

- Android: Leaderboard now renders ranks #1–#20 as one consistent scrollable list, including ranks #1–#3 in the same row design as everyone else. Connect keeps Smart Match first, then exposes two equal swipeable columns: Connect Hub and Students. Roommate/Mentor/Reading/Housing/Challenge rows now open the existing left-slide workflow reliably by opening the panel before paging its content. Student Discovery keeps Search + All/Same campus/Online filters and adds real Follow/Following actions backed by the canonical follow RPC state.
- Windows: Leaderboard is capped to the same #1–#20 live ranking. Connect exposes matching Connect Hub / Students sections, student search and All/Same campus/Online filters, and uses the same follow/unfollow backend contract with a desktop-native tab interaction.
- Backend/safety: no leaderboard formula, points/XP algorithm, Connect request rules, ranking order, auth, wallet, Store, notification or Supabase schema changes. Existing live Connect Hub and follow RPCs remain authoritative.

## 2026-10-06 — BLINK Store grouped visual gallery Testlab

- Android: replaces the long 70-row Store catalog with 25 shared visual collections in a two-column preview gallery. Opening a collection reveals the existing purchasable variants with live preview, ownership/active state, VIP gating and the existing purchase confirmation flow.
- Windows: uses the same 25 shared collections in a desktop-native three-column preview gallery with the same variant membership, ownership state, VIP gating, live preview and existing Vault/use behavior.
- Shared: `BlinkStoreProductGroups` is the single cross-platform grouping contract. All 70 existing catalog IDs remain reachable; VIP comment/reaction/entrance variants are intentionally cross-listed in their functional group and the BLINK VIP collection.
- Backend/safety: no Supabase schema, migration, RPC, RLS, catalog ID, Store price, Blink Coin rule, purchase/activation contract, boost multiplier, feed/Reels ranking, qualified-view rule, XP/points rule, authentication, notification or moderation behavior changes. The redesign is presentation/grouping only and remains on Testlab until Android and Windows gates pass.

## 2026-09-26 — Feed/session stability and unified search Testlab

- Android: preserves recoverable sessions without Sign In flashes, keeps paginated feed rows across first-page refreshes, gives Following its own existing server-backed feed/pagination, hides presence on feed cards, exposes one canonical Search surface, compacts mobile chrome, and validates Blink AI through the isolated authenticated Testlab function.
- Windows: existing durable session restore remains unchanged; Windows feed cards now also hide presence, and Blink AI uses the same isolated Testlab function/time bound while this branch is under validation. Desktop Search already presents one search surface rather than the Android-only Universal/Advanced/Explore dock.
- Shared backend: the existing `get_feed_page(..., p_feed_type='following')` contract is reused; no ranking formula, production schema, wallet, notification, payment, or production AI function is changed.
- PARITY-EXCEPTION: mobile-chrome-motion-20260926 — Android status/navigation safe-area sizing, profile slide motion, and planned swipe-dismiss utility sheets are touch/mobile-window concerns. Windows keeps desktop-native window navigation and spacing rather than imitating phone insets or swipe gestures.

## 2026-09-22 — Reference-style messaging experience promoted to main

- Android: Messages now uses the validated reference-style information architecture with Important contacts, All messages, improved search, centered chat header, floating rounded composer, and immersive chat contact profile actions.
- Windows: matching Messages / Important / All messages structure, search, centered chat header, rounded composer, and desktop contact profile panel.
- Existing BLINK messaging contracts remain in place: realtime delivery, replies, reactions, edit/delete, receipts, retries, pagination, offline outbox behavior, verification/VIP identity, and native Android call launching.
- Android Telecom compatibility remains guarded for API 26+ on the current main call implementation.
- No Supabase schema, migration, RLS, RPC, storage, or ranking/feed algorithm change is part of this promotion.

# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-10-06 | BLINK Store 25-collection visual gallery | Two-column visual preview gallery groups the existing 70 Store variants into 25 understandable products; each collection opens live variant previews and preserves Vault/purchase/activation behavior | Three-column desktop-native gallery uses the same 25 collections and underlying 70 variants, with matching preview/ownership/VIP semantics | `BlinkStoreProductGroups` is shared; existing catalog IDs, prices, wallet, VIP, boosts, ranking, Supabase contracts and user entitlements are unchanged |
| 2026-09-25 | True-black BLINK visual foundation | Android dark mode now uses a true `#000000` base with neutral black elevated surfaces, black feed cards/navigation, black messaging dark surfaces, and black window/status/navigation bars | Windows inherits the same true-black shared dark tokens through the existing desktop theme while preserving desktop-native layout and purple brand accents | Shared `BlinkDesignTokens.Dark` is the source of truth; presentation only, with no ranking, auth, messaging data, coins, marketplace, Supabase, or backend behavior changed |
| 2026-09-22 | BLINK AI reference redesign foundation | BLINK AI now opens as a full-screen dark/teal Welcome → Explore → Chat experience with the BLINK mark, shared category grid, conversation history/settings, image/audio attachments, Android dictation/TTS and existing confirmation-gated actions preserved | Windows now exposes the same Welcome → Explore → Chat journey in a desktop-native dialog, with the same category-to-mode mapping, starter prompts, history, settings and media attachments | `BlinkAiExperienceCatalog` is the shared source of truth for Explore categories, backend mode IDs and starter prompts. Existing `blink-ai-v2` history/action/backend contracts are unchanged; no production Supabase/schema change. The work stays in Testlab until Android and Windows gates pass |
| 2026-09-20 | Startup logo PNG integrity repair | Re-encoded the corrupted Compose brand mark and xxxhdpi launcher copies from the exact supplied BLINK master so startup can decode the logo without terminating | Re-encoded the matching desktop logo from the same supplied master | Shared Gradle validation now checks PNG signatures, structure and every chunk CRC across Android, Windows and web assets before Android or desktop resource processing; no backend or product behavior changed |
| 2026-09-19 | BLINK account onboarding v1 | Black BLINK welcome/auth entry; email signup asks only name/email/password; Google and email converge on live username selection, required university/department/gender, optional avatar/level/birthday, interest personalization, and a server-verified follow-5 gate; Glimmar Øf FUTA is featured but never auto-followed | Windows uses the same Supabase account/profile contract, shared username/password/interest/level/gender rules, resumable four-step setup, personalized suggestions and the same follow/unfollow RPCs; optional profile photo remains skippable | `BlinkOnboardingPolicy` is the shared business contract. Exact birthday is stored in owner-only `profile_private_details`; existing production profiles are migrated completed and future accounts start at step 0. Notifications are not an onboarding gate |
| 2026-09-19 | Paystack checkout readiness | Android exposes server-priced coin-pack and 30-day BLINK Verified checkout, opens Paystack hosted checkout, returns through a BLINK payment deep link, and re-verifies the pending order before refreshing value | Windows exposes the same server-priced coin packs and 30-day verification checkout, opens Paystack in the system browser, and provides a pending-payment verification action | Paystack secret stays only in Supabase Edge Function Secrets. Order amount/currency come from BLINK server config, webhook signatures are verified, Paystack is re-queried before fulfillment, fulfillment is idempotent, and cash checkout remains disabled until Testlab/staging validation passes |
| 2026-09-19 | Notification engine hardening | Android uses canonical notification IDs for dedupe, queues foreground banners, preserves exact profile/reel/market targets, restores failed read-state writes, removes mirrored legacy social duplicates, keeps DMs in Messages, and exposes clearer Mentions/Comments/Likes/Follows/Market/BLINK filters with server-time grouping | Windows prioritizes canonical notification rows, removes mirrored legacy social duplicates, and queues every newly detected foreground notification instead of losing bursts between polling cycles | Existing live Supabase notification rows remain authoritative; legacy activities are compatibility fallback only for non-mirrored event types. No ranking, auth, wallet, verification, moderation, or production schema change |
| 2026-09-19 | BLINK Progress Hub + weekly engagement foundation | Profile exposes a dedicated Progress Hub with daily/weekly missions, weekly completion chest, unified achievements, level/streak milestone rewards, private weekly creator recap and transparent XP history | Profile exposes the same server-backed weekly missions, chest, achievements, level/streak claims, creator recap and XP history using desktop-native layout; existing Daily Missions remain directly below the Hub | Shared `BlinkProgressHub` models define the cross-platform contract. Supabase migration is additive, reward claims are idempotent/server-authoritative, public claim tables use owner-only RLS, and production Supabase is not changed before a preview/staging validation environment exists |
| 2026-09-19 | Premium Blink Store surfaces | Blink Store adds searchable product discovery and honest before/after live previews; Profile Highlight becomes the full-surface Profile Aura; Comment Highlight becomes the full-card Comment Spotlight; equipped/active cosmetics render on profiles and comments | Windows uses the same shared cosmetic catalog, live Store previews, full profile/avatar treatments, and server-resolved full comment surfaces; comment loading now uses the protected batch RPC rather than per-author requests | `BlinkPremiumCosmetics` is the cross-platform presentation contract; purchases, balances, ownership, targeting, activation, VIP eligibility and expiry remain server-authoritative. Web Store/profile/comment surfaces use matching presentation names and full-surface treatments |
| 2026-09-19 | Canonical backend-value hydration | Android preserves Supabase leaderboard ranks; hydrates profile points/created_at/VIP/verified_at; uses owner-authorized live 7-day profile-view counts; and maps marketplace created_at + image_urls | Windows already reads canonical leaderboard_snapshots directly; DesktopProfile now retains points/created_at/VIP/verified_at/profile-view fields and DesktopMarketItem retains image_urls + created_at | Shared backend contract only; no auth, RLS, points algorithm, verification rules, VIP rules, or schema changes |
| 2026-09-18 | Profile leaderboard ranks | Profile fetches hydrate World Rank + Campus Rank from the latest `leaderboard_snapshots` row; missing ranks render as `—` instead of fake `#0` | Shared rank normalization/presentation contract compiles in the Windows client through `shared/` | No ranking algorithm, points rules, Supabase schema, or leaderboard ordering changed; this fixes profile data hydration only |
| 2026-09-18 | Profile narrow-layout and presence-badge repair | Feed presence badges render outside avatar clipping; profile action pills wrap into balanced rows; the selected profile tab indicator uses the measured position without a double 16dp offset; follower-growth timeframe controls use a full-width 7D/14D/30D row | Desktop presence avatars reserve badge space and use the same neutral offline indicator; desktop profile layout remains wide and does not need the Android narrow-width action wrapping | Presentation-only parity repair; no Supabase schema, presence heartbeat, ranking, auth, messaging, coins, verification, moderation, or notification behavior changed |
| 2026-09-07 | Windows desktop foundation | Existing production client | Desktop shell + EXE/MSI build foundation | Initial migration foundation; existing Android routes still need incremental porting |
| 2026-09-08 | Premium Blink design system foundation | Material 3 dark/light palette mapped to shared tokens; semantic surfaces, shapes, spacing, motion and premium bottom navigation | Desktop Material 3 theme mapped to the same shared palette with System/Light/Dark resolution | `BlinkDesignSystem.kt` is the cross-platform source of truth; no backend, Supabase schema, ranking, messaging, coin, auth, call or moderation behavior changed |
| 2026-09-08 | Premium Search & Discovery phase 1 | Debounced live search, rotating prompts, voice query entry, category/count chips, recents with pin/remove/privacy/expiry, autocomplete, typo correction, advanced filters/sorting, people/post/reel/hashtag/campus results, trending discovery and skeleton/empty states | Debounced live search, category/count chips, persisted recents, autocomplete, verified/university filtering, relevance/popularity sorting, people/post/reel/hashtag results and trending discovery | Both clients consume their existing search data contracts; no Supabase schema/RPC or ranking/feed algorithm change. Android-only speech recognition uses the native `RecognizerIntent` adapter; Windows retains keyboard-first search. |
| 2026-09-08 | Premium Search & Discovery phase 2A | Search/Trending/Places collection dock, smooth collection transitions, back-to-Search behavior, live engagement-ranked topics/posts/reels, trending creators, and Places derived from post/profile location metadata | Search/Trending/Places collection chips, smooth transitions, live discovery-feed trends, rising content, and Places derived from active Marketplace/Connect locations | Both clients expose only truthful collections backed by existing data. Saved is intentionally deferred until a real cross-platform bookmark contract exists; Communities/Events/global Marketplace search remain Phase 2B backend work. No Supabase schema or production ranking algorithm is changed. |
| 2026-09-18 | Final BLINK black/white brand logo | Exact supplied black rounded-square + white double-speech-bubble B is used for Android app icon, Compose brand mark, splash, themed icon and notification glyph | Desktop brand mark updated to the same black/white double-speech-bubble B geometry | Branding-only change; no backend, auth, ranking, messaging, coin, verification, moderation or Supabase behavior changed |\n
| 2026-09-18 | Exact BLINK logo repair | Replaced generated/approximated logo drawings with the exact cropped user-supplied logo asset for Android app branding, splash, launcher variants, adaptive foreground and notification mark | Replaced generated desktop logo/tray/window drawings with the exact same supplied logo asset | Web `blink-logo.png`, favicon/PWA surfaces and fallback SVG now use the same source asset; CSS no longer reshapes or fills transparent corners |\n| 2026-09-18 | 500-checkpoint Android UI design parity surface | Existing Android production UI remains the source reference; no APK behavior or backend contract changed | Adds a route-level Windows parity dock across Home/Reels/Search/Messages/Notifications/Marketplace/Connect/Games/Store/Leaderboard/Profile/Settings, using desktop-native dialogs, chips, keyboard/mouse access and responsive wide layouts | Web uses the same 50 Android source modules × 10 parity dimensions and now injects route-level Android UI parity controls into the real web screens. No Supabase schema, ranking, auth, messaging, coins, moderation or server behavior changed. |

| 2026-09-18 | Continue with Google — Web + Windows EXE | Web OAuth now returns to the stable BLINK root, restores the Supabase session safely, surfaces OAuth errors, and avoids losing hash tokens through static-route fallbacks | Windows uses PKCE with a loopback listener, but Supabase returns the one-time authorization code through www.blink.com.ng before the browser relays it to 127.0.0.1 with a per-attempt relay token | No service-role secret is exposed; no schema, ranking, messaging, wallet, moderation, verification, or Android auth behavior is changed |
| 2026-09-18 | Production public domain (www.blink.com.ng) | Android shares profiles/posts/reels with the canonical HTTPS domain and registers verified Android App Links for those routes | Windows uses the same canonical HTTPS website through the system browser; Android Intent/App Links registration does not apply to Windows | Web config, canonical metadata and GitHub Pages deployment all use https://www.blink.com.ng; legacy link parsing remains compatible |

| 2026-09-19 | BLINK Coin Economy v1 + BLINK Verified progress | Android reads server-controlled 10-coin rewarded-ad policy, 5/10/15 milestones (60/130/210 total), 15-ad daily cap, ₦800/3,000-coin BLINK Verified goal, coin-pack bonuses, progress meter and secure coin verification; fake cash activation is removed | Windows reads the same server economy policy and wallet, shows the same 3,000-coin verification progress, and can purchase BLINK Verified with coins; AdMob earning remains Android-only under the existing AdMob exception | Shared `BlinkEconomyPolicy` supplies safe offline defaults while Supabase is authoritative for live values, daily caps, milestone credits, coin verification and purchase-order records. Cash checkout remains disabled until a verified payment provider flow is connected |

| 2026-09-19 | BLINK logo placement + black authentication surfaces | Android auth/onboarding/reset surfaces use pure black so the exact black-square/white BLINK mark blends cleanly; auth top bars and the Home feed header now include the shared BLINK mark | Windows auth uses the same logo on a pure-black branded surface; existing window, tray and sidebar logo placements remain unchanged | Presentation/branding only; no Supabase schema, auth contract, ranking, messaging, coins, verification, moderation, notifications, or account semantics changed |

| 2026-09-19 | Auth session stability | Android keeps valid local/Supabase sessions through transient refresh failures, syncs rotated refresh tokens, makes email/username/Google/signup session replacement transactional, preserves the current account during Add account/Google cancellation/password recovery, and only marks local login after successful signup | Windows keeps the encrypted desktop session through network/5xx/profile restore failures and clears only on confirmed refresh-token expiry | Web preserves refreshable sessions through bootstrap/network/profile failures and clears only when the refresh token is confirmed invalid; password recovery now uses the canonical `blink://auth/reset-password` Android route; no schema or auth-policy change. Current-main compatibility is validated by the Testlab gates before promotion. |

## Platform exception policy

A platform exception is allowed only when the behavior is genuinely tied to one operating system and cannot sensibly exist on the other platform. It must not be used to avoid implementing normal feature parity.

Every exception must include the exact marker below so CI can identify it:

```text
PARITY-EXCEPTION: <stable-feature-id>
Date:
Feature:
Android behavior:
Why this is genuinely Android-only:
Windows equivalent or reason no equivalent is needed:
Backend/shared behavior preserved:
Tests/validation:
Owner/reviewer note:
```

Examples of potentially valid platform-specific adapters include Android Activity/Intent behavior, CameraX integration, WorkManager scheduling, Firebase Messaging delivery, Android permissions, Windows system-tray integration, Windows native menus, desktop file dialogs, and installer packaging.

Business rules, accounts, permissions, posts, reels, messages, coins, verification, marketplace behavior, Connect behavior, ranking, moderation, notifications semantics, and admin rules are not platform exceptions and must remain equivalent across Android and Windows.

---

PARITY-EXCEPTION: android-call-history-intent-compile-fix
Date: 2026-09-08
Feature: Chat overflow → Call history Android navigation adapter compile correction
Android behavior: Corrects the existing Android `OverflowRow` call so its `onClick` lambda is passed explicitly and can launch `CallHistoryActivity` through Android `Intent` without a Kotlin argument-binding compile failure.
Why this is genuinely Android-only: The corrected code is specifically an Android `Activity`/`Intent` navigation adapter. It does not add or change shared call-history business rules, backend contracts, data models, or user permissions.
Windows equivalent or reason no equivalent is needed: No Windows code change is required for this compile-only Android adapter correction. Windows must use its own desktop navigation mechanism when the corresponding call-history surface is implemented/updated; Android `Intent` cannot be shared with desktop.
Backend/shared behavior preserved: No Supabase schema, RPC, call-history data contract, shared business logic, or permission behavior is changed.
Tests/validation: Android quality gate rerun on `Testlab`; Windows desktop build remains independently validated by the Windows quality gate; parity exception recorded for the Android-only adapter correction.
Owner/reviewer note: This exception covers only the Android Activity/Intent compile fix. It must not be used to waive Windows parity for any future user-visible call-history feature or backend behavior change.

---

PARITY-EXCEPTION: android-crashlytics-build-traceability
Date: 2026-09-17
Feature: Release source-revision traceability
Android behavior: Embeds the exact Git commit SHA in Android BuildConfig and attaches the commit, version name, and version code to Firebase Crashlytics reports.
Why this is genuinely Android-only: Firebase Crashlytics and Android BuildConfig are Android build/runtime adapters. The change does not alter any user-visible feature, business rule, account, permission, backend contract, or data model.
Windows equivalent or reason no equivalent is needed: Windows does not use Firebase Crashlytics or Android BuildConfig. Its desktop package version remains controlled by BLINK_DESKTOP_VERSION and its CI build stays independently validated; a future Windows crash-reporting provider must attach its own source revision through that provider's native adapter.
Backend/shared behavior preserved: Supabase configuration, schemas, RPCs, storage, authentication, ranking, messaging, coins, verification, moderation, and all shared product semantics are unchanged.
Tests/validation: Android unit/lint/instrumentation/debug gates, minified release smoke build, source-SHA assertion, Windows compile/package gate, parity gate, and Supabase migration-safety gate must pass on the Testlab PR before merge.
Owner/reviewer note: This exception is limited to build diagnostics and cannot be used to waive Windows parity for user-facing behavior.


---

PARITY-EXCEPTION: android-admob-rewarded-coins
Date: 2026-09-18
Feature: Opt-in Google AdMob rewarded video for earning 10 Blink Coins
Android behavior: The existing private "Earn Blink Coin" action can explicitly open a Google Mobile Ads rewarded ad. Debug builds use Google's rewarded test unit; release builds use Blink's production rewarded unit. A completed SDK reward callback starts an authenticated one-time server claim that credits exactly 10 Blink Coins and records the transaction in the shared wallet ledger.
Why this is genuinely Android-only: Google Mobile Ads/AdMob rewarded presentation is an Android mobile advertising SDK tied to Android Activity lifecycle and Google ad inventory. The Windows desktop client cannot host the Android Google Mobile Ads SDK.
Windows equivalent or reason no equivalent is needed: No Windows ad surface is added. Windows continues to read and use the same server-authoritative Blink Coin balance and transaction history. If desktop rewarded advertising is introduced later, it must use a desktop-compatible provider while calling the same wallet business rules rather than emulating Android AdMob.
Backend/shared behavior preserved: Blink Coin balance, transaction ledger, store/VIP spending, ownership and permissions remain server-authoritative in Supabase. The reward amount is fixed at 10 server-side, claims are authenticated and idempotent, and the new RPC contract is shared backend infrastructure rather than Android-only coin logic.
Tests/validation: Android unit/lint/instrumentation/debug APK gate, Android release smoke, Windows desktop compile/package gate, parity gate and Supabase migration-safety gate must pass on this Testlab PR before promotion to main. Debug builds must request only Google's test rewarded unit.
Owner/reviewer note: This exception covers only the AdMob presentation adapter. It does not waive Windows parity for Blink Coin wallet, balances, transactions, Store/VIP behavior or any other shared economy rule.


---

PARITY-EXCEPTION: android-google-play-in-app-updates
Date: 2026-09-18
Feature: Google Play flexible in-app update delivery
Android behavior: Adds the official Google Play In-App Updates adapter. Play-distributed Android builds can request a flexible update, continue running during download, and offer a Restart action when the update is ready to install. GitHub/sideload builds no-op safely when Play update services are unavailable.
Why this is genuinely Android-only: Google Play In-App Updates is an Android/Google Play distribution API tied to Android Activity result handling and Play Store installation state.
Windows equivalent or reason no equivalent is needed: Windows distribution does not use Google Play. Windows keeps its existing desktop packaging/update path; no shared BLINK business behavior changes.
Backend/shared behavior preserved: No Supabase schema, auth, ranking, messaging, coins, verification, moderation, account, or shared product semantics are changed.
Tests/validation: Android quality gate must compile unit tests, lint, instrumentation tests, debug APK, and debug AAB on the Testlab PR. Windows build checks remain required by repository policy.
Owner/reviewer note: This exception covers only the OS/store update transport. It cannot be used to bypass Windows parity for normal BLINK features.


---

PARITY-EXCEPTION: android-admob-native-sponsored-feed-reels
Date: 2026-09-18
Feature: AdMob Native Advanced sponsored placements in Feed and Reels
Android behavior: Blink inserts clearly labelled Sponsored native ads into the Android feed after conservative content intervals and into the Android vertical Reels pager between real reels. The Google Mobile Ads NativeAdView owns advertiser asset clicks/impressions and AdChoices remains SDK-controlled. Debug builds use Google demo native ad units; release builds use Blink's Feed and Reels AdMob units.
Why this is genuinely Android-only: These placements use Google Mobile Ads' Android Native Advanced SDK and Android NativeAdView/MediaView presentation lifecycle. The Windows desktop client cannot embed the Android Google Mobile Ads SDK.
Windows equivalent or reason no equivalent is needed: No Windows ad placement is added. Windows continues to render the same posts, reels and shared Blink data without pretending to show AdMob inventory. A future desktop monetization provider must use a desktop-compatible SDK and keep sponsored content clearly labelled.
Backend/shared behavior preserved: Feed/reel ranking, Supabase data, views, likes, comments, follows, messages, coins, auth and moderation are unchanged. Ads are presentation-only rows/pages and never participate in Blink ranking or qualified-view accounting.
Tests/validation: Android unit tests, lint, instrumentation compile, debug APK build and release smoke must pass; Windows desktop build/parity and Supabase safety gates must also pass before promotion to main.
Owner/reviewer note: This exception covers only Google Mobile Ads Android presentation. It must not be used to waive Windows parity for Blink content, ranking, account, economy or moderation behavior.
---

PARITY-EXCEPTION: android-verified-web-app-links
Date: 2026-09-18
Feature: Verified HTTPS deep links for the BLINK production domain
Android behavior: Registers https://www.blink.com.ng profile, post and reel routes as verified Android App Links and routes those URLs into the matching BLINK screen. New shares use the canonical production domain while legacy BLINK web URLs remain parseable.
Why this is genuinely Android-only: Android App Links, intent filters, android:autoVerify and Intent URI routing are Android operating-system integration mechanisms. They cannot be implemented by the Windows client.
Windows equivalent or reason no equivalent is needed: Windows uses the same canonical https://www.blink.com.ng URLs through the default browser/web experience. The Windows desktop client does not register Android intent filters; Windows-specific protocol registration would be a separate native adapter if BLINK later chooses to add direct desktop deep-link launching.
Backend/shared behavior preserved: No Supabase schema, RPC, ranking, messaging, wallet, verification, moderation, permissions or account business rules are changed. Only public URL/domain routing and web hosting metadata change.
Tests/validation: BLINK domain integration check validates canonical-domain files and web JavaScript, then compiles/tests/lints/builds Android. Existing Android quality, Android release smoke, Windows desktop, Web Parity and Supabase safety gates must pass on Testlab before merge.
Owner/reviewer note: This exception covers only Android OS-level verified-link registration/routing. It does not waive Windows parity for any BLINK product feature or backend behavior change.



---

PARITY-EXCEPTION: android-admob-privacy-and-telemetry-hardening
Date: 2026-09-18
Feature: AdMob UMP consent gating, ad privacy entry point, ad telemetry, and rewarded SSV verification adapter
Android behavior: Google UMP refreshes consent on app launch, blocks all AdMob requests until ConsentInformation.canRequestAds() is true, exposes Google's privacy-options form from Settings and privacy when required, and logs coarse ad load/impression/click/failure/reward events without user IDs or advertiser content. Rewarded ads continue to credit the existing 10-coin server claim on the SDK earned-reward callback while a Google-signed SSV webhook records independent verification metadata for audit/reconciliation.
Why this is genuinely Android-only: UMP, Google Mobile Ads SDK callbacks, NativeAdView/RewardedAd lifecycle and the AdMob privacy-options form are Android/Google advertising SDK integrations. The SSV webhook exists only to authenticate callbacks from that Android AdMob inventory.
Windows equivalent or reason no equivalent is needed: Windows does not request AdMob inventory and therefore does not need UMP or AdMob privacy UI. If desktop advertising is added later, it must implement the privacy/consent controls required by that desktop ad provider.
Backend/shared behavior preserved: Blink account, feed/reel ranking, messaging, moderation, wallet balances and the 10-coin reward business rule remain unchanged. The new SSV database fields are additive audit metadata and do not change Windows coin spending or shared wallet semantics.
Tests/validation: Android unit/lint/instrumentation/debug APK/AAB and release-smoke gates, Windows desktop/parity gate, Web Parity/domain checks, and Supabase migration-safety must pass in Testlab before promotion to main.
Owner/reviewer note: This exception covers only Android AdMob/UMP presentation and AdMob callback verification. It cannot be used to waive Windows parity for shared product features or wallet rules.


---

PARITY-EXCEPTION: android-runtime-crash-hardening-20260920
Date: 2026-09-20
Feature: Android runtime crash hardening for navigation, saved state, Compose collections, media/reel paging, notifications, calls and Store entry points
Android behavior: Hardens existing Android screens against Activity/Intent launch failures, SharedPreferences type drift across APK upgrades, nullable state races, duplicate Compose keys, stale pager/list indices and Android activity lifecycle failures. It adds defensive adapters and regression coverage without adding new BLINK product behavior or changing any server-authoritative business rule.
Why this is genuinely Android-only: The affected failure modes are Android implementation details: ComponentActivity/Intent navigation, Android SharedPreferences runtime casts, Firebase Messaging delivery state, Android call Activities, Compose Android list/pager rendering and Android application/activity lifecycle. These APIs and crash modes do not exist in the Windows client.
Windows equivalent or reason no equivalent is needed: Windows keeps its existing equivalent BLINK product surfaces and shared backend behavior. No Windows feature, rule or user capability is missing because of this patch; desktop uses its own navigation, persistence and lifecycle adapters. The Windows build/package gate remains required to ensure no shared contract regression.
Backend/shared behavior preserved: No ranking, coins, verification, Store pricing/ownership, authentication policy, onboarding rules, posts, reels, messages, calls, notifications semantics, moderation, Supabase schema/RLS or shared data contract is changed. The patch only prevents Android runtime termination and safely degrades failed OS integrations.
Tests/validation: Android unit tests, lint, instrumentation compile, debug APK/AAB, release-smoke build, domain integration, Supabase safety, Windows build/package and Windows parity gates must pass on the Testlab PR before merge. Added regression tests cover wrong-type legacy preferences, missing activity launch handling, BLINK Store catalog integrity and BlinkStoreActivity creation.
Owner/reviewer note: This exception is limited to Android runtime reliability adapters and cannot be reused to waive Windows parity for future BLINK features, business rules or backend behavior.


---

PARITY-EXCEPTION: android-core-telecom-postmerge-routing-fix
Date: 2026-09-22
Feature: Android Core-Telecom call-control and audio-route reliability fix
Android behavior: Makes BLINK fail closed when Android Telecom does not provide a CallControlScope within the registration window, extends the control-ready timeout for slower devices, and prevents Android 8+ speaker changes from bypassing Telecom with a direct AudioManager fallback after a Telecom route change fails.
Why this is genuinely Android-only: Core-Telecom, CallControlScope, CallEndpointCompat and Android audio endpoint arbitration are Android operating-system call integration APIs. These APIs and failure modes do not exist on Windows.
Windows equivalent or reason no equivalent is needed: Windows keeps its existing desktop call/media routing implementation and does not use Android Telecom or AudioManager. No Windows user capability changes in this patch.
Backend/shared behavior preserved: Supabase call state, signaling, WebRTC media semantics, call permissions, history, notifications semantics and shared user-facing call rules are unchanged. This patch only corrects the Android OS adapter and the Testlab call CI invocation.
Tests/validation: Testlab Call Quality, Android quality, Android release smoke, Supabase safety, Windows build and Windows parity gates must pass on PR #133 before merge.
Owner/reviewer note: This exception is limited to Android Core-Telecom/audio-routing reliability and cannot be reused to waive Windows parity for call features, shared business rules or backend behavior.


---

PARITY-EXCEPTION: android-feed-x-style-scroll-header-20261006
Date: 2026-10-06
Feature: Scroll-linked Android Home feed header motion
Android behavior: Replaces threshold-triggered AnimatedVisibility fade/slide transitions for the Home feed top chrome with a continuously translated collapsing header. Downward gestures collapse the combined header one-to-one, intentional upward reversal reveals it, a 12dp reversal dead-zone suppresses jitter, partially exposed states settle to a stable endpoint, the real feed top always restores the full header, and the bottom navigation remains independent. Feed ranking, Following/For You data, pagination, qualified views, impressions, caching and Supabase contracts are unchanged.
Why this is genuinely Android-specific presentation: This patch changes touch/nested-scroll behavior of the compact phone Home chrome and uses Android Compose nested scrolling. It does not add, remove or alter a BLINK feature, backend rule or content result.
Windows equivalent or reason no equivalent is needed: Windows keeps its desktop navigation/chrome conventions. The desktop Home screen already derives a compact-feed signal from its LazyList scroll position for the wide-screen shell; forcing the phone's touch collapse gesture onto desktop mouse/keyboard scrolling would reduce desktop usability rather than provide feature parity.
Backend/shared behavior preserved: No shared model, route, ranking rule, wallet rule, authentication behavior, notification contract, Supabase schema, RLS policy or server function changes.
Tests/validation: Feed header motion math has JVM regression coverage. Android unit/lint/instrumentation/debug/release checks and the Windows desktop/parity gate must pass on the Testlab feature branch before promotion to main.
Owner/reviewer note: This exception is limited to mobile feed chrome motion. Any future change to what navigation actions exist, what content is shown, or how feed data is ranked still requires Windows/shared parity.

## 2026-10-06 — General Study becomes the first Game catalog entry

- Cleared the visible legacy Games hub on Android and Windows without deleting reusable backend game infrastructure.
- Added **General Study** as the first available game, with a clean catalog card and **More games coming soon** placeholder.
- Added one shared, deterministic question-bank implementation used by both clients:
  - Easy: 5,000
  - Medium: 5,000
  - Hard: 5,000
  - Expert: 5,000
  - Total: 20,000 questions across Mathematics, English, Science, Geography, History & Civics, Technology, Logic, and General Knowledge.
- Added 10-question rounds, difficulty selection, answer feedback, scores/results, replay, and difficulty switching on both Android and Windows.
- Kept the old server game tables/repository code intact for future game additions; this change only resets the visible Games experience.


---

PARITY-EXCEPTION: android-admob-rewarded-reliability-20261006
Date: 2026-10-06
Feature: Rewarded AdMob Earn Coin preload/show reliability
Android behavior: Keeps the existing opt-in Android AdMob rewarded-coin feature but makes a single user tap reliable when the rewarded ad is still preloading. The Android Activity waits for the in-flight Google Mobile Ads load, auto-opens the ad when ready, blocks duplicate taps/claims, re-preloads after resume/dismissal/failure, and surfaces network/no-fill/load errors instead of making the Earn Coin action appear dead.
Why this is genuinely Android-only: The reliability work is entirely inside Google Mobile Ads RewardedAd and Android Activity lifecycle adapters. Google Mobile Ads for Android cannot run inside the Windows desktop client.
Windows equivalent or reason no equivalent is needed: Windows does not expose an AdMob rewarded-ad surface. It continues to use the same server-authoritative Blink Coin wallet, balances, Store spending and reward policy. A future Windows rewarded-ad provider must use a desktop-compatible advertising SDK rather than emulating the Android adapter.
Backend/shared behavior preserved: The 10-coin base reward, 5/10/15 milestones, 15-ad daily cap, wallet ledger, Supabase reward RPCs, Store/VIP spending and verification rules are unchanged. This patch does not make AdMob SSV mandatory while the existing SSV callback configuration is being audited.
Tests/validation: Android rewarded-ad regression tests plus Android quality/release-smoke, Windows build/package, Windows parity, Supabase safety and domain integration gates must pass before merge.
Owner/reviewer note: This exception covers only Android AdMob loading/show lifecycle reliability. It cannot waive Windows parity for any shared Blink Coin business rule or future cross-platform earning feature.

---

PARITY-EXCEPTION: android-boost-drops-wallet-ui-polish-20261006
Date: 2026-10-06
Feature: Android Boost/Drops wallet-state and monochrome presentation repair
Android behavior: Stops failed Boost/Drops state requests from rendering a fake zero coin balance, replaces raw backend/schema errors with user-safe retry copy, reorders the Android Boost form so Audience is step 2 and Boost power is step 4, and changes the Android Drops balance/Organize/publish emphasis from purple to the app's monochrome surface/on-surface palette.
Why this is genuinely Android-specific presentation: The patch changes only Android Compose rendering and Android RPC error presentation. It does not change the shared Blink Coin wallet, prices, Boost campaign rules, Drop eligibility/reservation/claim rules, ranking, notifications, or server data contracts.
Windows equivalent or reason no equivalent is needed: Windows keeps its existing Boost/Drops-equivalent desktop presentation and continues to read the same server-authoritative wallet and backend contracts. The mobile step order, compact phone controls and Android Compose color treatment do not map one-to-one to the desktop layout. No Windows user capability or business rule is removed or added by this patch.
Backend/shared behavior preserved: Production still uses public.user_balances.spendable_coin_balance as the wallet authority. Boost pricing/charging and Drop reserve/refund/claim remain server-authoritative. Existing organic ranking and recommendation behavior are unchanged.
Tests/validation: Android unit/lint/instrumentation/debug/release checks, Supabase safety, Windows build/package and parity gates must pass on Testlab before merge.
Owner/reviewer note: This exception is limited to Android presentation/error-state parity. Any future wallet, pricing, Boost delivery, Drop reward, or cross-platform business-rule change still requires Windows/shared implementation.


---

PARITY-EXCEPTION: android-mobile-items-sensors-location-20261006
Date: 2026-10-06
Feature: BLINK Items — mobile Steps, Weather alerts, and private Live Location
Android behavior: Adds the Feed Items entry point and a grouped Items surface. Steps reads Android's low-power hardware step counter only after the user enables it and grants physical-activity access, with deduplicated milestone/goal notifications. Weather uses the user's opted-in approximate location for local forecasts and server-fetched authority alerts. Live Location uses Android fused location plus a user-visible foreground service and persistent Stop Sharing notification, is restricted to explicitly selected followed/unblocked users, expires after 15 minutes, 1 hour, or 4 hours, and retains only the current coordinate rather than movement history.
Why this is genuinely mobile-specific: Step counting depends on a phone hardware motion sensor and Android ACTIVITY_RECOGNITION. Continuous live-location publishing depends on Android runtime location permission, fused location, foreground-service lifecycle, and the mandatory Android foreground notification. A Windows desktop cannot safely or honestly emulate phone-carried step counts or background mobile GPS.
Windows equivalent or reason no equivalent is needed: The Windows client does not collect steps or publish device location. That is intentional rather than a missing capability. Existing Windows messaging, notifications, profiles and shared BLINK account data remain unchanged. The weather Edge Function is platform-neutral and can support a future desktop weather surface using a manually selected area without adding desktop tracking. Authorized live-location RPCs remain backend-compatible for a future read-only desktop viewer, but this PR does not make desktop location publishing possible.
Backend/shared behavior preserved: Feed ranking, Following/For You ordering, qualified views, XP, coins, verification, ads, posts, reels, messages, calls, authentication and existing profile data are unchanged. The live-location migration is additive and locked behind authenticated owner-scoped RPCs; exact coordinates are never exposed through Feed/Profile/Search and are deleted when sharing stops.
Tests/validation: Android unit/lint/instrumentation/debug/release checks, Supabase migration-safety, domain integration and the Windows build/parity gate must pass on Testlab before promotion. Real MAPS_API_KEY and server-side OPENWEATHER_API_KEY configuration must also be validated before production rollout.
Owner/reviewer note: This exception is limited to phone-sensor collection, Android location publishing, and their Android permission/foreground-service UI. It cannot be reused to waive Windows parity for ordinary cross-platform BLINK product features.


---

PARITY: activity-pulse-hardening-20261007
Date: 2026-10-07
Feature: Connect Community Activity + Leaderboard Rank Pulse hardening
Android behavior: Uses the shared server-controlled pulse policy, safe overflow-capped ranges, gradual session-persistent number movement, minimum hold timing, reduced-motion handling, foreground/window pausing, offline freeze, real online previews, campus activity labels, real leaderboard mover summaries, rank heat status, and rate-limited product analytics.
Windows behavior: Uses the same shared pulse policy and range/transition functions, honors the desktop Reduce Motion preference, freezes the last pulse when live fetches fail, shows real online/campus context and real rank movement summaries, and records the same server analytics events.
Backend/shared behavior: Adds an authenticated read-only pulse configuration table and an authenticated rate-limited analytics RPC/table. Presence rows, canonical leaderboard order, points, XP, feed/reel ranking, coins, verification, auth, messages and existing user data are not modified.
Tests/validation: Shared/Android pulse range tests, Android quality/release smoke, Windows compile/package/parity, and disposable Supabase migration safety must pass on Testlab before production promotion.

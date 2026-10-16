## 2026-10-15 — Ad loader lifecycle repair (Testlab)

- Android: bound rewarded loading to 30 seconds, reject stale callbacks, expire cached ads after 55 minutes, clear manager state on Activity destruction, and retry failed native placements at most twice with 30/60-second backoff. Preserve consent checks and SDK-owned video/click handling. Log native error domain/code/message/response for diagnosis.
- Shared-first assessment: these changes exclusively manage Android Google Mobile Ads SDK objects and Activity/Compose lifecycle; no platform-independent economy or reward contracts change.
- PARITY-EXCEPTION: android-admob-lifecycle — Google Mobile Ads is an Android SDK adapter. Windows retains its existing supported monetization behavior; no fake Windows ads or rewards are introduced.
- Testlab is behind main in these files. Review promotion conflicts against main explicitly; never replace main's newer ad implementations wholesale.
- Required before promotion: Android and Windows CI, then all three Google test placements on a physical device. A successful build does not prove live AdMob approval, inventory or reward delivery.

## 2026-10-15 — Shared native Godot multiplayer social neighbourhood (Testlab only)

- **Android and Windows:** The exact same `game3d/scripts/social_world.gd` is loaded inside Godot after launch, with opt-in trusted-LAN ENet host/join; real networked avatars and names, session chat, owner-approved home visits, driver-approved single passenger rides, virtual coordinate sharing OFF by default, and temporary cosmetic changes.
- **App independence:** Neither platform preloads Godot during feed, messages, search, market or other social use. LAN network is disconnected by default even inside Godot. No real GPS/BLINK payments/auth claims.
- **Scope:** This is not deployed Internet matchmaking, authenticated accounts or permanent economy. See `game3d/ROADMAP_110.md` for the 110-feature list with exact remaining work and test gates.
- **QA:** Linux/Windows Godot import/scene smoke, Android APK build, desktop package check, actual Android↔Windows LAN testing, memory/battery tests and staged security review all required. No main promotion on draft PR #283.

## 2026-10-15 — BLINK LIFE on-demand engine startup and host protection

- **Android:** The Games card now calls a Godot-free `BlinkLifeLaunchGate` only when Play is tapped; rapid double taps are ignored and start failures are handled without crashing BLINK. The Godot activity stays in isolated `:blink_life` process; `BlinkApplication` skips normal Supabase/Firebase/worker startup there.
- **Windows:** Its native Godot launcher is also invoked only when the Games button is clicked; no startup preload.
- **Checks:** New static isolation contract verifies manifest, game startup, and desktop lazy launcher. Existing Godot smoke tests and Android/Windows builds still required. APK/installer size grows even if game is never played because binaries are pre-bundled; runtime memory/graphics cost is deferred to Play, not the installation size. Physical device tests still required before main merge.

## 2026-10-15 — BLINK LIFE native Godot host (PR #283, Testlab validation)

- **Shared game source:** Both Android and Windows use the *same* Godot 4.3 `game3d/` scene and GDScript gameplay. Money is fictional local Game Cash, not BLINK Coins, payments or real rewards.
- **Android:** The Games button opens a separate landscape `GodotActivity` backed by the pinned Godot 4.3 AAR; the build copies the native scene into APK assets. `:blink_life` runs in a dedicated process to protect the Compose app and authentication lifecycle. CI inspects APK native binaries, assets, and activity registration.
- **Windows:** The Games button launches the matching native Godot scene from installer-packaged resources; Windows CI obtains the pinned Godot 4.3 runtime binary and stages the common source project into resources before MSI/EXE packaging. Development builds without the packaged engine show a clearly labelled preview fallback.
- **Status:** Staged only. Do not call this a shipping feature until Android/Windows CI, Android 15 physical launch/exit, Windows installer smoke checks, package-size and performance checks all pass. Main remains untouched. No Supabase migration or wallet changes.

## 2026-10-14 — BLINK menu navigation and accessibility upgrade (Testlab)

- **Shared:** `MenuSearch` provides case-insensitive trimmed search for Android and Windows, covered by unit tests.
- **Android:** Add searchable menu actions, a close button and native drag handle, rounded sheet corners, descriptive menu-row subtitles, truncation for long profile names, verified badge, neutral theme switches, non-bouncy section animations, logout confirmation and a proper About dialog. Existing route IDs, Supabase permissions and admin gating remain unchanged.
- **PARITY-EXCEPTION: android-native-menu-sheet** — The menu is an Android platform-specific bottom sheet, not a full-screen Windows dialog. Windows retains its desktop sidebar/keyboard/mouse navigation and platform-native notification settings; the platform-neutral search matcher is compiled by both clients. No server schema or API contracts change.
- **Release gate:** Test search terms, accessibility, menu routing, swipe dismissal, appearance, account actions, Android build + unit tests, Windows build/parity, and physical Android device behavior. Keep Testlab and main separate until verified, particularly because Testlab diverged from main.

## 2026-10-14 — BLINK LIFE experimental 3D city playground

- **Shared:** `BlinkLifeRules` defines six city locations, orthogonal movement, three local work tasks, and a fictional Game Cash market purchase. `BlinkLifeWorld3D` projects real X/Y/Z cuboid world geometry with depth ordering and an orbiting camera; `BlinkLifePrototype` embeds the shared renderer and gameplay on Android and Windows.
- **Android:** Adds a dedicated BLINK LIFE card within the existing Games > Play section; entering/exiting the playground retains existing games and Android Back handling.
- **Windows:** Adds a matching BLINK LIFE entry to the existing Games surface and renders the shared playground.
- **3D scope:** This initial low-poly software-projected isometric scene is 3D, not the previous flat 2D grid. Production-scale first/third-person 3D physics, driving, and multiplayer will require a proper accelerated game runtime and asset pipeline.
- **Native 3D project:** The new `game3d/` Godot source provides a physics-based free-roam 3D scene for future Android and Windows exports. It is a separate **unbundled experimental engine project**; the host-app entry currently still shows the shared 3D-projected preview. Neither Android APK nor Windows installer may claim native Godot gameplay until their distinct embed/export checks pass.
- **Safety:** The preview is session-only and labelled clearly. No new backend, payment, Supabase wallet, BLINK Coin, leaderboard or account-data writes. Fictional Game Cash is not withdrawable, transferable, or redeemable.
- **Release gate:** Build/test both clients, validate controls on physical Android devices and Windows, verify normal game modes are unaffected. Feature branch targets `Testlab`; do not promote to `main` while checks are unverified.

## 2026-10-13 — Seamless Android top edge / full-height feed

- **Android:** The authenticated main scaffold no longer reserves a second full status-bar inset above the feed. Feed, reels, messages, search and connect retain their individual top safe-area padding; Market and Leaderboard reserve it explicitly. Bottom and horizontal safe insets remain in the scaffold to protect the five-tab navigation and cutouts. The feed header no longer paints a purple/blue glow into the top strip. System status icons remain visible; this is edge-to-edge content, not immersive/hide-system-bars mode.
- **PARITY-EXCEPTION: android-system-bars-edge-to-edge** — Windows platform exception: Android status/navigation bars and gesture-inset APIs do not exist on Windows. Desktop already sizes content to its window and retains native title-bar / window-resizing affordances; no desktop UI or backend change is applicable.
- **Checks before production:** Android compile, lint and tests; Windows parity/build; device verification on Android 15 with display cutout and gesture navigation (feed at rest + collapsed header, Reels, Market, Connect, Messages, keyboard, dark/light mode). Stage in Testlab first, and do not promote to main until these checks pass.

## 2026-10-13 — Five-page gesture navigation and chat direction parity

- **Shared:** `BlinkSwipeNavigation` defines the canonical Feed → Reels → Market → Connect → Messages ordering, bounds and interactive scale/opacity motion. No backend, Supabase, messaging transport, moderation, coins or schema changes.
- **Android:** Main pages use a drag-responsive Compose pager. The current page subtly shrinks while the next expands; tab taps and deep links use the existing ViewModel routes. Paging is disabled while a chat, composer, menu or other modal/overlay is active. Messages open/close from the left; an individual message swiped right selects reply, whereas a chat swiped left dismisses. The pager stops after Messages.
- **Windows:** The same shared ordering is reachable with Alt+Right/Alt+Left within primary routes (without intercepting ordinary editing keys). Conversations enter and exit with matching left-side slide animations; native mouse and keyboard message replying remain available.
- **Tests required before promotion:** Android compilation, shared navigation unit tests, Windows compilation, on-device vertical-feed/reel/chat/image-carousels and predictive Android Back navigation; gesture collision and low-end animation checks. Keep `main` untouched until validated.

## 2026-10-11 — Verify APK delivery before publishing

- Android packaging exception: APK archive integrity and signed-build checksum are checked before upload and again after downloading the draft release from GitHub. Missing checksums, mismatched build identity, corrupt archives and altered metadata prevent promotion to latest.
- Public release assets are never overwritten, protecting interrupted downloads from mixing different bytes. A checksum file accompanies each future release.
- Windows: no runtime, UI or backend changes; APK delivery is Android-only. Existing Windows release behavior is preserved.
- Limits: this verifies GitHub delivery, not every browser/network/storage path to a user's device. Signature verification remains in the signed production build; this gate enforces byte equality to that artifact. Failed drafts stay private for diagnosis.

# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-09-19 | BLINK Store Economy v2 + Collection | Preview-first purchasing, My Collection, Wishlist, Saved Looks, cosmetic gifting, XP-level free claims, Campus/Seasonal collections, 7/30-day cosmetic tiers, 30-day VIP and renewable 30-day BLINK Verified; Android renders Supabase catalog prices/dates as authoritative with offline fallback | Windows matches preview-first purchasing, Collection/Wishlist/Saved Looks/gifting, XP-level claims, Campus/Seasonal availability, monthly VIP/Verified policy, purchase history and server-driven prices/dates | Supabase is authoritative for catalog pricing/durations/availability, ownership, gifting, XP claim eligibility, wishlist, saved looks, VIP activation and verification expiry; seasonal dates are enforced server-side, distribution boosts remain short and anti-stacking/cooldown rules are enforced |
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

PARITY-FEATURE: shared-opt-in-social-email-notifications
Date: 2026-10-16
Feature: BLINK transactional welcome/security messages and five optional social email categories
Android behavior: The Calls & notifications screen exposes individually opt-in email switches for New followers, Unread conversations, Post engagement, Reel milestones, and Leaderboard updates. A failed server load/save leaves all switches off/unchanged; user can update only own Supabase row.
Windows behavior: Settings exposes the same five persisted switches through DesktopSupabaseClient, using the same Supabase RLS-protected preference table. Existing global Email notifications switch is also respected on the server.
Shared behavior: BlinkSocialEmailCategory defines one shared source for preference columns, names and descriptions. Both platform adapters use existing signed-in Supabase user sessions and REST; neither holds Brevo or service-role credentials.
Server behavior: Verified-account welcome events and server-owned social digest checks enqueue to a service-only outbox. The Brevo worker rechecks consent before any social send; social delivery defaults OFF pending staging rollout. Password recovery/email change remain Supabase Auth events, and signup confirmation emails are never activated/changed by this feature.
Platform-specific exception: None needed. Both clients have equivalent social email preference controls; native push-notification channels are independent of email.
Safety & tests: Testlab PR #299; SQL migration, strict RLS, no client enqueue, no message bodies, idempotent digests, unsubscribe on signed POST, deterministic sender source, email-sending daily cap, HTML safety regression tests. Production changes blocked until staging migrations, signup-policy checks, Brevo keys/sender verification, end-to-end Gmail/Outlook and Android/Windows CI validation.

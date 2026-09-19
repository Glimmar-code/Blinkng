# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
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

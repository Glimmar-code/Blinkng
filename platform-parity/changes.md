## 2026-10-15 — Search real feed-card rendering (Testlab)

- **Android:** Phase 3 universal Search post/reel results use the same `PostCard` as Feed and Profile. Search index rows are used for discovery and ranking only; if a result is not already in the live feed cache, its full post is fetched through the authenticated `PostRepository.fetchPostById`. A missing or private post shows an unavailable/retry state rather than a synthetic FeedPost, and the existing profile/entity result cards remain unchanged.
- **Windows:** Search and Trending reuse the existing full `DesktopFeedPostCard` from Home, backed by real authenticated `DesktopFeedPost` entries with like/bookmark/repost/comment/share actions. This replaces the miniature metadata-only card without introducing fabricated content.
- **Shared-first assessment:** The existing backend post identity, authorization, media, and reaction semantics remain unchanged. No schema, RPC, ranking, currency, or account-contract migration is introduced.
- **Validation required before main:** Android unit tests/build/lint, Windows build/parity checks, authenticated search on posts not currently loaded in Feed, deleted/private result handling, slow network/retry, photo ratio/media, Reels routing, light/dark mode, and search interactions.

## 2026-10-15 — BLINK LIFE on-demand delivery (Android / Windows parity)

- **PARITY-EXCEPTION: blink-life-play-feature-delivery** — Google Play `com.android.dynamic-feature` and Play Feature Delivery exist only on Android. The downloadable game, progress/cancel controls and Game Storage module removal are Android-store-specific delivery controls, not gameplay capability changes.
- **Windows equivalent:** The existing Windows EXE/MSI already installs the native Godot engine and 3D game scene, and its Games page Play control remains unchanged. Windows does not require Google Play APIs; Windows players can launch the same `game3d/` content without downloading a Play split.
- **Shared gameplay:** No Godot scene, gameplay, accounting, or online-server behavior changed. Only where the Android native library and 3D source are packaged, and the Android UI to request this on-demand module.
- **Website caveat:** Plain GitHub APKs cannot install Play dynamic features, so this implementation is staged and must not replace the public standalone APK before a separate distribution strategy and Google Play test are completed.
- **Checks:** Android dynamic App Bundle content, base APK size, game-on-demand download/cancel/retry, isolated engine process and Windows EXE/MSI compatibility are all required.

## 2026-10-15 — BLINK LIFE native multiplayer preview (targeted main integration)

- **Android/Windows:** Same Godot 4.3 original scenes and optional game path. Android enters an isolated :blink_life Activity only after Play; Windows launches a separately packaged native process only on Play.
- **Backend:** Staging-only, nondeployed authenticated join-ticket function and dedicated Godot server source; permanent houses and fictional Game Cash use single-server file persistence, not production database. No new production migrations or real-currency connections.
- **Compatibility:** This ports only game-related files from the Testlab PR into the latest main, without reverting main's hundreds of newer feed, messages, ads, notifications, call, payment or signing improvements.
- **Release caveats:** Real-device validation still required, host not deployed, no encrypted UDP or production voice/text moderation. UI explicitly labels Preview. Existing General Study remains available.

## 2026-10-15 — Promote tested ad fixes from Testlab PR #284

- Reconciles #284 with main's newer one-tap rewarded loading, timeout, cache expiry, consent gating and Reels failed-slot removal.
- Rewarded: cancels requests/callback references when the Activity is destroyed, refuses loads/shows on a closed host, and includes SDK error code/domain in failures. Native: preserves disposal guards, adds detailed diagnostics, and retries mounted Feed slots at most twice with 30/60-second backoff. Reels continue retrying on explicit refresh to avoid reinserting blank pages mid-scroll.
- Shared-first assessment: only Android SDK/Activity/Compose adapters change; coin amounts, backend contracts and server verification remain unchanged.
- PARITY-EXCEPTION: android-admob-lifecycle — Windows cannot use the Android Mobile Ads SDK. Windows retains its existing monetization behavior; both clients retain the same economy rules. Windows build/parity checks remain required.
- Regression tests exercise timeout/retry, stale callbacks, host closure, and consent without production application startup. Live inventory and device video playback are separate from CI validation.

## 2026-10-14 — Notification reliability and premium inbox on Android and Windows (Testlab)

PARITY-EXCEPTION: android-os-fcm-notification-delivery-20261014

- **Android & Windows:** Both notification inboxes now support Reels, Stories and Rewards categories, newest/oldest sorting, monochrome unread accents, existing read actions, and preserved last-good activity on refresh failures (Windows). Their content remains fetched from authenticated server-owned notifications.
- **Android-specific delivery:** WorkManager recovery, FCM recipient-ID binding, Android system notification IDs, OS permission prompts, token registration health and repair are Android/Firebase platform integrations. Windows continues to use its authenticated desktop Supabase inbox; it does not create or use Android FCM tokens or Android notification channels.
- **Windows equivalent:** Native desktop inbox filters/search/unread sorting, safe account switch invalidation and server-backed marking read. No synthetic push-delivery claims; Windows continues to show server-originated activity when the inbox refreshes.
- **Safety and rollout:** No production database schema updates or live notification preferences were changed. Require Android tests/lint/runtime/release smoke, Windows compile/parity, and manual FCM + account-switch + light/dark testing before main promotion. This implements a subset of the previously proposed 50 improvements, not all.

## 2026-10-14 — Optional offline steps notification (Testlab)

PARITY-EXCEPTION: android-offline-foreground-steps-20261014

- **Android:** Users who explicitly enable Steps can opt in to an ongoing, silent, private step-count notification with current daily count/goal/progress, calculated entirely from the device TYPE_STEP_COUNTER sensor with no network. It uses a properly declared `health` foreground service, `FOREGROUND_SERVICE_HEALTH` and granted ACTIVITY_RECOGNITION on supported devices. Android 13+ notification permission is requested separately when needed.
- **User control:** The notification offers **Turn off**, its dismiss receiver stops tracking if Android permits the notification to be swiped away, and switching off Steps also stops the service. No automatic resurrection after a user opts out. It is designed to survive Clear all where Android permits, but Android 14+ can allow manual dismissal, notification permission removal, Task Manager stop, and battery-policy shutdown; BLINK cannot force a genuinely non-removable notification.
- **Device behavior:** Low-power sensor events update a locally saved daily estimate; notification updates are throttled and midnight is rechecked. A guarded boot/app-update receiver attempts to restart tracking only when users previously enabled it, and only when Android/OEM foreground-service policies allow. No GPS, Supabase, network traffic, wake locks, or device-admin privileges.
- **Windows equivalent:** A sensor-driven Android notification-shade foreground service is not meaningful on Windows. Desktop retains the existing account/backend semantics; no new shared data or feature-specific billing changes.
- **Release gates:** Android unit/lint/build/runtime, Windows parity/build and physical Android 15 testing (offline, midnight, permission denial, Clear all, manual swipe, stop action, reboot and OEM battery restrictions). No guarantee of Android-level non-dismissability.

## 2026-10-14 — Items privacy, Steps and Weather reliability (Testlab)

PARITY-EXCEPTION: android-items-sensor-gps-foreground-20261014

- **Android Steps:** Hide an old daily step cache after midnight, allow a configurable local daily goal (1,000–100,000) with a progress indicator, and avoid claiming the same low custom goal again for every later built-in milestone. Phone step sensors and Android activity recognition are Android-only; data remains local and estimated.
- **Android Weather:** Mark cached forecast freshness and do not use stored weather coordinates after Weather is turned off or Android location permission is revoked. Clear the saved Weather location when disabled. Persist a bounded set of weather alert IDs to avoid ping-pong duplicate warnings when several active alerts are returned, and ignore expired official notices. Existing weather backend and API contracts are unchanged.
- **Android Live Location:** Request coarse and precise permissions together for Android 12+, handle foreground-service startup failures by attempting to revoke the server session, confirm server-side session stop before claiming success, reduce idle location-list polling, and show location accuracy/freshness only within approved recipient view. No public map, publicly visible location, new permission, or schema migration.
- **Windows equivalent:** Android sensor/foreground-service/runtime-permission flows are not available in the Windows client; its desktop experience does not silently start GPS or steps collection. These patches do not alter shared Supabase location session RPC signatures or weather API models, so existing Windows account/backend behavior remains compatible. A future desktop Items/Weather surface requires a separate native implementation and privacy review.
- **UI:** The Android-only Items panel gains monochrome surfaces, clearer status labels and removal of a blank Live Location placeholder. Android-only layout and phone-specific controls do not map to Windows window chrome.
- **Verification:** This branch was created from current `main` because legacy `Testlab` has diverged. Require green Android unit/lint/assemble/runtime, Windows parity/build, and a real Android 15 device validation before promotion. A CI pass does not validate production weather delivery or GPS permissions without device/backend checks.

## 2026-10-14 — Home Feed reference-card layout and Light/Dark staging

PARITY-EXCEPTION: android-home-reference-layout-20261014

- **Android:** Preserve the screenshot's Home visual hierarchy: profile/Home/actions header, Rank–Game–Boost–Drops–muted Store shortcuts, blue rounded For You/Following selector, compact refresh/filter actions, white/charcoal elevated post cards, and the existing five-destination bottom bar/floating Create control.
- **Light and Dark:** Feed surfaces and text use active Material semantic colors rather than dark-only constants; post cards receive white or charcoal surfaces, subtle appropriate borders/shadows, and correctly themed metric icons. Android status/navigation icons follow the app's chosen theme, not only the system theme.
- **User-facing semantics unchanged:** For You/Following data, posts, authenticated interactions, qualified views, follows, ads, Boost, Drops, Store pause, pagination, refresh, moderation and Supabase contracts remain intact. No schema migration or production data update.
- **Windows equivalent:** Windows retains the same user actions and shared data semantics, with a desktop-native feed/window/sidebar. A mobile status inset, top-row icon sizing and bottom phone tab bar are intentionally Android-only presentation details.
- **Release checks:** Stage on a main-based Testlab-prefixed integration branch because the legacy Testlab branch diverged significantly from current main. Require Android build/lint/runtime, Windows desktop parity, Light/Dark screenshots and physical Redmi 15C inspection before promoting to main. Do not claim that manual device checks passed unless performed.

## 2026-10-14 — Finish full-height Android page chrome without top black gaps

PARITY-EXCEPTION: android-search-messages-system-status-insets-20261014

- **Android (forward-ported onto current main):** Preserve the already-merged Home, Reels and Connect fullscreen fix from PR #270 rather than copying the stale Testlab MainActivity and risking the newer navigation/boost/ads code. Messages and Search already render their own `statusBarsPadding()` and therefore no longer consume a second top inset in the activity Scaffold. Unlike Home, these routes retain bottom and horizontal safeDrawing insets. Market continues using the activity's single safe-area inset, but its decorative 44dp header gap is reduced to 12dp. The current main feed header already removes the purple/blue glow, so no duplicate header changes are needed.
- **Windows:** Only Android status/navigation system insets and Material3 phone-specific screen spacing change. Windows already uses its OS title bar and full client surface; no corresponding code change is relevant.
- **Validation:** Android compile, lint, tests, signed-release smoke and Windows parity/build CI required before merging into main. On-device Redmi 15C screenshot/gesture/cutout checks are still required before claiming physical pixel-perfect behavior.

## 2026-10-13 — Remove duplicate Android top and bottom system insets on Home (Testlab)

PARITY-EXCEPTION: android-home-reels-connect-edge-to-edge-safe-insets-20261013

- **Android issue:** The Material3 app-level Scaffold was already reserving the OS status bar and navigation-safe areas while the Home, Reels, and Connect Compose pages also reserved the same insets. This creates a second blank stripe above the Feed header and wastes the viewport near the bottom toolbar.
- **Android fix:** For these three pages only, let the page itself manage the status/navigation insets exactly once and paint the root status-bar background with the same Feed color. Keep transparent edge-to-edge rendering while preserving the status clock, camera cutout, touch targets, system-gesture navigation and native status shade.
- **Other pages:** Market, Messages, authentication and other app destinations retain the existing Scaffold safe-area defaults; window bars are **not** globally hidden or made immersive. No posts, ranking, Supabase, chat, storefront, ads or monetization code changes.
- **Windows equivalent:** The Windows desktop app uses a native window titlebar, side navigation and resizable desktop content without Android OS status/navigation bar insets. The corresponding desktop behavior is already full use of the drawable client area beneath its titlebar; applying Android Compose `WindowInsets` would introduce an artificial blank strip in Windows.
- **Verification:** Android quality gate, lint, runtime/screenshot checks on different status-bar heights and three-button/gesture navigation, Windows parity/build checks; physical Redmi 15C visual verification is still required before asserting pixel-perfect results.

## 2026-10-13 — Premium swipe navigation production port

- Staged and validated through Testlab pull request #265; ported selectively onto the newer production code instead of merging older Testlab dependencies into main.
- Shared: canonical Feed → Reels → Market → Connect → Messages order and bounded animation scale/opacity contract.
- Android: finger-driven horizontal pager with depth transitions; protects composer, modal, chat and feed overlays from page swipes. Direct-only Search/Leaderboard/Game routes remain accessible. Swipe a chat left to close; swipe an individual message right to reply; chats enter from the left.
- Windows: Alt+Left/Right moves between equivalent primary routes, and chat content enters/exits from the left; messaging data contracts are unchanged.
- Production requirements: all Android/Windows compile, unit, release smoke, domain and Supabase gates green on the main-target PR before merge. No database/schema/coins/auth changes.

## 2026-10-13 — Temporarily pause public BLINK Store (Testlab)

- **Shared policy:** `BlinkStoreAvailability.isPaused` keeps Store temporarily unavailable on Android and Windows while retaining the complete catalog, purchase routes, reaction effects, provider surfaces, collection data, and Supabase contracts for reopening later.
- **Android:** The five Home shortcuts are now Rank → Game → Boost → Drops → **Store**. The fifth shortcut is visually muted but remains tappable; tapping shows a simple dismissible **Coming Soon** pop-up with no close button. Back or tapping outside dismisses it. Direct Store activity entry, restored Store sheets, and Store links from Boost/Drops are also guarded, so no Store purchase or activation UI mounts.
- **Windows:** Blink Store is visibly marked **Soon** in the sidebar, and opening it displays the same no-buttons informational dialog rather than loading the Store catalog or payment UI.
- **Web:** Store, Vault, VIP and Store digital-gift routes show a Coming Soon overlay instead of rendering their catalog, prices, reactions, collections or purchase/activation controls. Existing unrelated Boost, Drops, Messages and normal reactions are unchanged.
- **Data preservation:** No migrations, billing changes, entitlement revocations, transaction updates, catalog deletions or wallet mutations. Previously owned items and balances remain server-side; UI-only access is paused pending relaunch.
- **Release gates:** Android compile/unit/lint/runtime smoke, shared tests, Windows build and web parity checks are required before main. Restore Store only by switching the synchronized shared Kotlin and web pause flags after review.

## 2026-10-13 — Connect personalized discovery, safety and match-motion hardening (Testlab)

- **Shared discovery:** New deterministic `ConnectDiscoveryPolicy` scores actual same-campus/department and optional public interests, prioritizes useful matches over generic online status, deduplicates case-insensitive IDs and gives human-readable reasons. Android and Windows call the same shared policy and unit tests. Paid boosts, coins, XP, gender, birth date, relationship status and private contacts are not ranking inputs.
- **Android and Windows:** Profile suggestions include up to two explanation labels; a per-session **Not interested** button hides a recommendation with **Restore hidden suggestions** to undo. It is a local session control, not an on-server block or durable interest preference. Server privacy, RLS and actual user messaging permissions remain authoritative.
- **Truthful presence:** Both clients avoid treating cached presence as live online when discovery refresh is unavailable. Android student cards show presence freshness; Windows cards show stale state.
- **Android Connect:** Improve top navigation touch targets to 48dp, remove an always-running Match glow/rotation animation (now active only during matching, with reduce-motion/background support), route the Housing shortcut to actual housing requests, and reset match history, saved preferences, wallet preview, pending dialogs and match details across accounts. Remove the local candidate fallback that could circumvent Supabase-approved Connect discovery.
- **Windows Connect:** Shared ordering, explanatory labels, opt-out feedback and stale-presence protection using native desktop layout. Android's touch target and Compose animation specifics are platform-only.
- **Backend:** No new migration or production data change. Spin coin billing, RLS, verification, reporting, block states, paid monetization and existing backend RPC semantics remain unchanged.
- **Release gates:** Android and Windows build, unit tests, Android 15 runtime smoke, release smoke, parity, security and Supabase safety checks; also manual session-switch, blocked-discovery, real spinner, narrow-layout, offline/500 and Redmi 15C checks before production.
- **Scope:** This ships a coherent **subset** of the proposed 100 Connect items, not all 100. Other safety, durable preferences, housing, mentorship, notifications, deep links, pagination and benchmarking remain tracked in issue #262.
## 2026-10-13 — Connect partial-load resilience and truthful online presence (Testlab)

- **Android and Windows:** The Connect header now shows a count of users actually confirmed online from the available student list rather than a fluctuating synthetic "Community Activity" number. When student presence cannot be refreshed, the live count is shown as unavailable rather than presented as confirmed.
- **Android resilience:** Preserve the last known good records for individual failed backend sections while surfacing the failures; genuine successful empty responses correctly clear stale listings. Added tests for these conditions.
- **Android backend:** Roommates, mentorship, reading mates, housing agents, housing requests, challenges, matches, and the Connect inbox load concurrently rather than one after another. Each section remains independently failure-isolated and its failure name is exposed to the UI; a failed request must not silently masquerade as an empty directory.
- **Android UI:** Show a visible partial-load warning listing unavailable sections and a Retry action. Preserve existing Connect categories, Spin, request and game functionality, authentication, monetary rules, and backend contracts.
- **Windows UI:** Surface unsuccessful Connect listings/applications/discovery/following/inbox loads with a Retry action. Keep last-known UI records on partial failures, matching the existing desktop recovery semantics.
- **Deployment:** No Supabase production schema change, RLS modification, coin charge, or new server permission is included. Test Kotlin/Compose builds, Android runtime and Windows parity, plus behavior during slow/offline/401/500 responses before promoting this branch to main.
- **Scope:** This is a reliability-focused subset of the 100 requested Connect enhancements; the other redesign, matching, safety, housing, and feature initiatives are not represented as implemented.

## 2026-10-13 — Desktop group metadata and guarded messaging

- **Android + Windows server parity:** Android already uses server-backed conversation types and recipient identities. Windows now resolves `is_group` from an authenticated, row-level-security-filtered conversation lookup because the summary RPC does **not** supply that field. Preserve the group title from the actual conversation row.
- **Safe sending:** Windows verifies the signed-in user can read the conversation and its actual group/direct kind immediately before sending. If the chat kind differs from the displayed cache, the app refuses to send and requests inbox refresh; it cannot misroute a group message to a one-to-one conversation.
- **Scope:** no Supabase migration, direct production data mutation, or APK release included here. Regression CI and live multi-account/group device testing remain required.

## 2026-10-12 — Stable Android Feed tombstones and ranked module positions

PARITY-EXCEPTION: android-frozen-feed-tombstones-and-module-stability-20261012

- **Bug fixed:** Android's deliberately frozen Compose Feed snapshot kept locally deleted, hidden, muted and realtime-deleted posts on screen after ViewModel removed them. Track per-login session-only explicit tombstone IDs in `BlinkUiState`, update optimistic delete + rollback, hide/not-interested and realtime DELETE signals. Apply them to organic rows, inline Reel previews, promoted/boosted rows, and the New Posts counter, while preserving server-ranked order and scroll position. Muted usernames and legacy display-name author references are filtered without modifying server moderation.
- **UI stability:** Reel-preview / Sponsored / Drop-module mixing seed no longer changes when the first ranked post ID changes. The random placement pattern depends only on account/lane/filter; server ranking remains authoritative and no paid promotion or ad frequency logic changes.
- **Windows parity:** Windows has its own direct list adapters and desktop account/Feed navigation, not Compose's process-retained `LazyColumn` snapshot. This exception addresses Android-only stale frozen UI rows; Supabase post deletion, muting, ranking, paid delivery and the shared backing data remain identical on Android and Windows.
- **Validation:** Android unit tests explicitly cover hidden/deleted/muted identities, including unsuccessful-delete rollback; Android quality/lint/release smoke, emulator, Windows parity and security must pass before main merge.
- **Not claimed:** physical Redmi 15C frame-time benchmark, fully tested all-font-sizes layout, backend user-ID migration, cross-account server content reconciliation, and live-ad fill depend on separate device/server checks and are not represented as complete.

## 2026-10-12 — Production-safe AdMob client reliability (Testlab)

PARITY-EXCEPTION: android-google-mobile-ads-ump-rewarded-native-20261012

- **Shared/backend:** Server-authoritative Blink Coin rewards remain a single Supabase RPC for Android and Windows. The staged SQL migration requires a signed, unique, service-role-recorded AdMob SSV callback before paying newly completed Android rewarded ads; already-paid historical claims stay idempotent. **Do not deploy the migration until Google's actual callback delivery is confirmed against Supabase staging.** The shared 10/60/130/210 coin milestones and 15/day limit are unchanged.
- **Android-only adapter:** Google Mobile Ads SDK, UMP consent form, native Feed/Reels slots and Activity-backed rewarded full-screen ads do not run on Windows. Fix a stuck ad-loading spinner by timing out the SDK call; discard stale/late callbacks; retry consent on explicit user request; reload sponsored slots on explicit refresh; report non-personal ad error codes. Android persists pending verified-claim IDs per signed-in user and reconciles them on resume, never granting coins offline or before server credit.
- **Windows equivalent:** No Android SDK calls or ads are transplanted into the Windows client. Desktop must continue to use the same backend wallet and server-enforced coin balance; it must not create or spoof AdMob SSV claims. Paid economy logic and profile balances stay consistent across platforms.
- **Verification gate:** Android release/runtime smoke, Kotlin unit tests, Windows checks, and physical-device ad verification. The SSV-enforcing migration is staged separately in PR #249 and is deliberately NOT in this safe client rollout; production AdMob serving and signed SSV delivery must be confirmed before enabling server-side enforcement. Client changes alone cannot guarantee that Google serves ads.

## 2026-10-13 — Protected desktop message sends and cross-platform inbox search (Testlab)

- **Critical messaging correctness:** Windows no longer POSTs chat messages directly to the `messages` table. Both clients now use server-authoritative Supabase sending RPCs for normal direct chats; desktop groups use `send_group_message`. The backend already enforces recipient blocks for direct messages and group membership / rate limits for group messages. Windows takes the recipient's actual `partner_username` from authorized summaries; it never guesses from display name.
- **Server confirmation:** Desktop only treats a send as complete after the RPC returns a stable server ID, and uses an optional authorized read-back to populate state. If read-back fails after confirmation, do not mislabel a committed message as failed and prompt blind resending. On a different conversation returned by the server, direct users to refresh rather than painting another chat's content.
- **Search/UI parity:** Android + Windows use the same case-insensitive shared inbox search policy over display name, username and permitted last-message preview. Windows displays the server-provided latest message with single-line ellipsis, retaining stable compact conversation cards. Shared unit tests cover match/no-match and whitespace.
- **Not covered:** server idempotency against ambiguous network outcomes, full production RPC / RLS policy penetration tests, genuine multi-user physical device testing, GIF/sticker/media work and the full 100 feature checklist remain pending. Backend schema and prod data were not changed. Testlab checks must finish before promotion.

## 2026-10-12 — Android Feed header double-scroll and exact scroll-resume position

PARITY-EXCEPTION: android-lazycolumn-collapsing-header-distance-and-offset-20261012

- Android Feed: the scroll-linked Home header previously changed visible height while returning zero consumed drag distance, so a single vertical gesture could collapse the header **and** scroll the underlying LazyColumn by the full amount. Consume only the actual vertical pixels spent moving the header, and pass the remainder to the list. Once fully collapsed/open (or reversing under the dead zone), feed scroll proceeds normally. No app-shell navigation or gesture-priority changes.
- Android Feed: append genuinely new server-delivered inline Reel teasers after the existing preview pool on realtime updates, not only on a pagination-completed transition. Already displayed preview positions and the server-supplied order remain unchanged; explicit refresh still rebuilds the snapshot. This closes a stale-preview case where new Reels never appeared until the viewer scrolled deeply.
- Android Feed: fix scroll-position restoration of partially visible posts. LazyListItemInfo.offset is negative when clipped above the viewport, but scrollToItem expects a positive into-item offset. Convert to the proper non-negative offset rather than rounding the saved position back to the post's start.
- Add unit tests for clamp, direction, available-vs-consumed delta, partial-row position restoration and integer boundaries. Follow up with Android emulator scroll/rotation and runtime smoke plus Windows build/parity.
- Windows equivalent: the desktop app uses native Compose desktop scrolling and sidebar navigation without Android's collapsing status-bar header or LazyListState insets; preserve its independent scroll-resume and responsive window behaviour. Feed data, ranking, post views, ads, monetization, credentials and Supabase remain unchanged.
- Physical Redmi 15C frame profiling and device-specific long-session validation remain outstanding; this code correction is not evidence every one of the earlier 50 audit risks has been eliminated.

## 2026-10-13 — Message inbox completeness and desktop reply/send safety (Testlab)

- **Android + Windows:** introduce a shared `All` inbox filter for active (non-archived) threads including message requests; retain `Primary`, `Unread`, `Requests` and `Archived` filters and previous default. Shared unit tests cover All semantics.
- **Windows:** upgrade the Reply action from an unlinked quoted text draft to an actual `set_message_reply` RPC, the same server-authoritative function already called by Android. Show a reply preview only when the server saves the link; gracefully acknowledge a link failure without pretending the sent message failed.
- **Windows privacy and reliability:** limit concurrent sends to one per conversation and guard all async UI updates against account or selected-conversation changes, including preserving an unsent draft on errors. Display incoming reply references based on the server's `reply_to_message_id` rather than a fabricated client quote.
- **Limitations:** this does not add server-level idempotency tokens; a request interrupted after server acceptance still needs backend deduplication before automatic resend can be considered safe. Physical device and live multi-account staging tests remain required before production promotion.

## 2026-10-12 — Message rendering, actions and privacy hardening (Testlab PR #251)

- **Shared Android + Windows:** deterministic Today / Yesterday / dated conversation dividers from server timestamps and the reader's timezone, inside keyed message rows so pagination and quote navigation keep stable indices; pure unit tests cover invalid timestamps, offset conversion and day boundaries.
- **Android:** preserve existing message actions, unread inbox, auth-aware drafts and server-side contracts while avoiding a blurred/active background inbox under full-screen chat; optimize long-history loading, reply lookups and read-position retention.
- **Windows:** use the SAME pre-existing participant-authorized Supabase RPC names as Android for message reaction, edit, delete for everyone, hide for self, report, and get_message_action_state. Show server-confirmed reaction counts and remove fake 'reaction sync pending' messaging. Do not expose privileged credentials or implement a parallel backend.
- **Windows privacy:** guard late inbox and message fetches against account/thread switching; clear previously visible private chat content immediately on account/chat changes. Drafts remain account-scoped and per-conversation. Guard destructive actions with confirmation.
- **Validation pending:** real user/account switching, network interruption, Android and Windows UI interaction and Supabase permissions must be verified before merging. Backend migrations, server policies, coin economy, and production data are untouched.

## 2026-10-12 — Message timeline performance and draft isolation (Testlab)

- **Shared:** `BlinkChatInboxPolicy` adds the unread-only inbox filter without changing existing Primary/Requests/Archived semantics. Android and Windows use the same unread predicate; focused unit tests cover archived/request exclusion.
- **Shared:** `BlinkMessageScrollPolicy` specifies deterministic initial jump, animated append at bottom, preservation of reading position, and new-message indication when reading history; four Android unit-test scenarios cover its behavior.
- **Android:** expose an Unread tab/count driven by actual unread/marked-unread state. Do not compose a second blurred inbox behind an already full-screen chat; assign stable conversation list keys so reordering does not destroy row identity; isolate lazy timeline state per conversation; memoize search/filter and reply-target lookup; distinguish history loading from an actually empty or filtered conversation; keep reply navigation accurate when a history-loading header is displayed.
- **Windows:** include the Unread inbox tab from the same shared predicate and use the same shared timeline policy when loading and appending messages; offer an explicit New messages jump instead of yanking the reader away from older history. Store drafts per conversation and user; clear old account history on session switch; clear prior chat history immediately when switching conversations; avoid fixed-width bubble overflow in narrow windows.
- **Safety:** no Supabase schema, auth policies, upload semantics, server send/receipt semantics, message contents, notification delivery or stored history are modified. Drafts on desktop are in-memory until further persistence work and are cleared on application restart. These changes must pass Android unit/build/lint, Windows build, and runtime messaging tests on Testlab before main promotion.
- **Not claimed:** full 100-feature rollout, idempotent server sends, encrypted end-to-end messaging, GIF licensing, realtime subscription reliability, and real device frame-time validation remain independent tasks. Test the first set before merging.

## 2026-10-12 — Feed audit follow-up: pagination, refresh, native ads, accessibility

PARITY-EXCEPTION: android-compose-feed-pagination-native-ad-touch-20261012

**Why Android-only:** The changed pagination deduplication and refresh throttle govern Compose LazyColumn/Android callback lifetimes, Google Mobile Ads NativeAd loading and a touchscreen phone-width action rail. Windows uses its own mouse/keyboard feed paging, desktop ad implementation and sidebar/header layout. The Windows equivalent retains server-authoritative page requests, existing retry options, and labeled native controls; no server cursor, paid placement, moderation, or business semantics are changed.



- **Shared behaviour:** Feed/inline-Reel pagination requests now use organic content page cursors to avoid duplicate Supabase requests when the same viewport remains at the end, refreshing status changes or decorative ad/boost modules rerender. The server cursor, Feed ranking, sponsored frequency, reward rules and impressions are unchanged. Explicit refresh or reconnect allows retry. Added pure unit tests for deduplication and tap throttling.
- **Android Feed:** prevent rapid pull-to-refresh/reselect/refresh-tab actions from submitting duplicate concurrent fetches. A deliberate next refresh is accepted after a short 800ms cooldown. Pending media and player state continue to be handled separately.
- **Android ads:** fail a stalled native Feed ad after 8s (Reels after 6s); discard a late Google SDK callback after timeout instead of injecting a large card into a distant scroll position. Preserve SDK impression attribution, AdChoices, consent and no-fill handling. Ads are never faked.
- **Android accessibility:** preserve visible views/likes counters at phone width but hide lower-priority numeric labels on narrow 6-action rows, retaining full TalkBack counts. Make all Home header icon touch targets 48dp, collapse its decorative title on very narrow screens, and give See More a larger target.
- **Windows parity:** Windows shares server-authoritative pagination/refresh semantics and is not subject to Android Compose LazyColumn and Google native-ads loading. This change makes no Windows backend, ranking, money, or public API changes; desktop's separate controls and mouse/keyboard navigation are retained.
- **Tests and known limitations:** Requires Android UI lint/build, emulator runtime, Windows build, release smoke, integration and security checks. Physical Redmi 15C frame-time benchmarking, benchmark/Baseline Profiles, complete responsive typography review, and user-ID/tombstone backend migration remain out of scope and must not be represented as complete.

PARITY-EXCEPTION: android-home-feed-system-insets-and-compose-media-20261012
Reason: Android uses a status bar/camera cutout and gesture or three-button navigation inset; native Windows windows do not. The mobile-only Coil feed-thumbnail sizing, Compose scroll-transition, text "See more", poll tap throttling, and bottom FloatingBottomBar behavior cannot be transplanted directly onto desktop Compose without destabilizing its separate controls.
Windows equivalent: desktop keeps a native titlebar/sidebar, platform-appropriate fixed-thumbnail bounds, native scroll layout, desktop poll submission and native post actions. It already shows the independent repost metric (not share count). The incoming-post pill on Android is an ephemeral realtime gesture; desktop users refresh their independently paged feed. This exception does not exempt any server/API/permission changes, of which there are none.

## 2026-10-12 — Feed audit hardening and production-safety follow-ups (Testlab)

- **Android status bar/nav insets:** keep the Android top status-bar safe area outside scroll-linked Home chrome; keep BottomBar and Create above gesture/three-button navigation via system insets. This is Android-only platform geometry; Windows uses native window chrome and sidebar.
- **Android Home image preview:** use stable 4:5 cropping only for X-style thumbnail cards, and retain the original, uncropped source in full-screen viewing; this prevents LazyColumn recycled rows changing dimensions after image decoding. Windows desktop uses independently fixed preview bounds.
- **Android post interactions:** repost button reflects `repostsCount` (not shares); edited post text resets its expansion; counter text does not re-run expensive animations during scrolling; comment actions are disabled when `allowComments=false`; poll votes have a short duplicate-tap guard. **Windows:** already uses `repostCount` and preserves its existing text-expansion/keyboard and comment permission behaviours. Android's poll handler and Coil image rendering are platform-specific.
- **Android incoming post UI:** preserve currently visible feed row positions while detecting newly *prepended* IDs before merging live updates. Do not treat pagination, first load, feed replacement or refresh as new-post events. Following and For You remain separate server-backed lanes. Windows uses separate desktop feed paging and refresh UI.
- **Unchanged contracts:** Supabase query/auth/RLS, Feed ranking, media upload, coins, views/XP, ad impressions, real Reel exposure, and Windows/backend data contracts remain untouched.
- **Limitations:** Full accessibility audit, physical Redmi 15C frame benchmark, complete author-ID ownership migration, and ad-placeholder redesign require separate measured validation. They are not represented as resolved here.
- **Required gates:** Android unit/lint/build, Android 15 runtime smoke, signed release smoke, Windows build, security/integrity and parity; no merge to main unless green.

## 2026-10-12 — Home feed header glow and empty Create-rail spacing (Testlab)

PARITY-EXCEPTION: android-home-feed-inset-and-floating-create-20261012

- Android Home feed: remove the purple/blue radial halo that was drawn across the top system-status inset and toolbar. Keep the system status-bar safe area and touch targets intact so no controls end up under the camera cutout or clock; preserve purple as an intentional accent for selected tabs, badges and the Create control.
- Android Home feed: remove the otherwise-empty full-width 56dp Create-action rail beneath the timeline. Anchor the existing expandable Create speed dial directly above the 56dp bottom navigation while keeping post scrolling, media, sponsored rows, and engagement intact. Reserve content-end scrolling space so the final post's controls remain reachable.
- Windows: this is an Android edge-to-edge/status-inset/overlaid bottom-navigation layout correction. Windows uses a fixed sidebar and different window insets; no equivalent empty Android utility rail is present. The shared brand colors and feature semantics are unchanged.
- No changes to user content, feeds, view counts, ad placements, navigation destinations, API calls, Supabase or SQL. Validate Android build/lint, emulator UI and last-post interaction, and Windows build/parity before promotion.

## 2026-10-12 — Feed continuity, clean identity, swipe navigation, and writing (Testlab)

- **Feed identity (Android + Windows):** display name above @username, age of post on the right; remove redundant public audience/category badges, retain restricted-audience disclosure. X-style metrics display views before likes, comments, repost, save, share. Follow, verification, report, and post permission actions remain intact.
- **Input (Android + Windows):** sentence capitalization is a keyboard suggestion for post/reel/story captions, styled posts, chat messages and desktop chat. Never rewrite user-entered text or auto-capitalize usernames, links or credentials. Android comments already had sentence-capitalization enabled.
- **Navigation (Android):** swipe Feed -> Reels -> Connect -> Market -> Messages, and reverse. For You/Following stay distinct *tap-selected* feed lanes, not horizontal swipe targets. Use non-consuming touch observation so image carousels and vertical scrolling win. Reels left swipe opens Connect rather than the author profile; the profile button continues to open the profile. Apply restrained scale/slide transitions with reduced-motion support.
- **Navigation (Windows):** keep desktop keyboard/mouse and sidebar semantics; these mobile touch gestures are intentionally Android-only, but the same page destinations and profile actions remain accessible.
- **Feed smoothness (Android):** eliminate height-changing entry animation for recycled posts; freeze each mounted image card's measured aspect-ratio frame and cache its natural ratio for future mounts; only prefetch limited image assets after scrolling pauses; avoid Reel preview autoplays during fast scroll; avoid resetting the header on unrelated feed row-key updates. Server ranking, 30s qualified views and ad-impression logic remain unchanged.
- **Feed smoothness (Windows):** its separate desktop composables already use fixed media bounds and stable scroll; no Android-specific native image pipeline / Choreographer gesture changes needed.
- **Game / utility sheets (Android):** downward half-screen pull closes after release, with explicit accessible close button. Windows retains desktop close controls.
- **Validation gates:** Android lint, debug APK and unit checks; emulator/game swipe and nested scroll smoke; desktop build/parity; release smoke; no schema or migration changes. Keep on Testlab until green.

## 2026-10-12 — Half-screen dismissal for full-screen utilities (Testlab)

PARITY-EXCEPTION: android-touch-utility-sheets-half-screen-dismiss-20261012

- Android: Game, Rank/Leaderboard, Store, Boost, Drops, and Items share a full-screen Material3 utility sheet. A downward drag covering at least half the available screen closes the utility on finger release, even when a nested scroll view captures the motion. Short pulls remain in-place; cancelled gestures do not dismiss. Close buttons and direct section navigation remain available independently.
- No changes to feed/reel ranking, game rewards, coins, backend permissions, or Supabase schema.
- Windows equivalent: the desktop app uses windows/panels and does not expose Android's touch-only ModalBottomSheet. Existing close/back controls remain the desktop equivalent.
- Validation before main promotion: policy JVM tests, Android debug build and real gesture smoke through Game and scrollable utilities, Windows desktop build/parity checks.

## 2026-10-11 — Feed, promotions, reels, inbox and profile reliability (Testlab)

PARITY-EXCEPTION: android-native-ads-player-and-rpc-recovery-20261011

- Feed recovery (Android): after a server statement timeout, retry the existing authenticated ranked discovery RPC in smaller batches while retaining its block/mute/privacy filters and stable server cursor; never use the legacy get_feed_page private-schema RPC or read raw feed_posts, whose RLS does not enforce all discovery exclusions. Failed recovery remains an error, not an empty timeline.
- Native monetization (Android): promoted feed posts only record a delivery after they meet the on-screen post visibility threshold. Promoted Reels require a settled visible page before the impression; no rewards, coins, view weights or ad billing settings change. Native Reels ad requests time out and failed slots are removed, retaining organic reels.
- Reels (Android): limit expensive prewarmed ExoPlayer decoders to the current and next page, releasing obsolete players when pages leave composition.
- Messages (Android): deliver the first authenticated inbox page to the UI while the full older history is still loading. Keep local conversation message state, preserve account scoping and do not discard older server histories.
- Profile (Android): reuse the fast initial profile timeline while loading optional liked/saved relations; preserve accurate relation flags.
- Post likes (Android): reference-count per-post mutation locks so queued likes serialize but completed keys do not accumulate for the whole process.
- Windows: this update does not change desktop playback, scroll, advertisement, or Supabase protocols. Android's Compose/ExoPlayer/OkHttp lifecycles do not exist in the Windows UI; existing desktop feed, profile and messaging behavior is preserved. No backend schema/RLS changes.
- Validation: run Kotlin JVM tests, Android lint/debug build, Android emulator/runtime smoke and signed release smoke, Windows build/parity, security and domain checks before any main promotion.

## 2026-10-11 — Native Sponsored delivery visibility (Testlab)

PARITY-EXCEPTION: android-admob-native-scroll-delivery-20261011

- Android: retain the existing approved **one Sponsored placement after 8 organic Feed posts / 7 organic Reels**, now including the exact-length boundary rather than requiring an additional post or reel. When UMP consent does not permit requests, Reels does not insert an empty sponsored page. When AdMob returns no fill/failure, remove the empty Reels ad page instead of trapping the viewer on a black page; ignore late load callbacks after slot disposal. Never fabricate an ad or impression; preserve SDK AdChoices and attribution.
- Windows equivalent: Google Mobile Ads NativeAdView is an Android-only SDK and desktop did not have a contracted ad SDK/provider. Windows retains its existing content/ranking UX, without fake sponsored content or hidden tracking. A separate desktop ad provider would require its own consent and parity review.
- No changes to rewarded ads, verified/coin economy, qualified views, search ranking, Supabase migrations, or real ad-unit IDs. Debug still uses Google's test unit IDs. Release impressions are subject to real AdMob fill, policy and consent.

## 2026-10-11 — Smooth feed scrolling and low-work post images (Testlab)

PARITY-EXCEPTION: android-compose-feed-scroll-performance-20261011

- Android: top feed chrome follows finger motion without consuming any of the underlying LazyColumn's vertical drag; avoid redundant bottom-bar state writes. Slow down background media prefetch during fast flings, remove animated loading indicators and feed-image crossfades, and defer unknown image-height changes while a list is actively scrolling. A bounded, image-free aspect-ratio cache allows recycled post cards to retain stable sizes while retaining natural image proportions.
- Windows: Android's nested-scroll dispatch, Coil/Compose loading indicators and LazyColumn image aspect reflows have no identical Windows desktop adapter; Windows' scroll mechanics and image loading remain unchanged. No functional feed or content semantic change is required on desktop.
- Shared/backend: ranking, views/qualified impressions, promotions, permissions, Supabase state and cache invalidation are not modified. Validate Android unit/lint/emulator scroll paths and existing Windows/parity gates before promoting to main. The first uncached image may settle to its true aspect after a fling; it should not resize during the drag itself.

## 2026-10-10 — Bug reports, Admin DMs and verified Blink Coin rewards (Testlab)

- Android: signed-in users open a report sheet from Account & Support or by shaking the phone while BLINK is foregrounded; optional JPEG screenshots are kept private. Users can disable shake detection and see their report review history.
- Windows equivalent: Settings provides the authenticated text report form and history; the admin view provides report review and explicit coin reward confirmation. Desktop computers have no standardized phone accelerometer, so shake-to-open and the Android photo-picker are intentionally Android-specific.
- Shared backend: `submit_blink_bug_report` stores a report, rate-limits spam, and attempts to send it as a private conversation to the configured BLINK owner. Reports survive DM delivery failures. Administrative replies are also sent as private DMs when possible.
- Rewards: admins can approve or reject reports; only the authenticated, server-protected admin RPC can award up to 500 coins to a verified report via the established canonical wallet system; row locking prevents duplicate awards. Normal admin limits remain enforced.
- Safety: screenshot bucket is private with report-owner/admin-only reads, submitters have no write access to report status or rewards; production deployment is gated on staging migration and Android/Desktop build plus role/coin verification.
- PARITY-EXCEPTION: android-shake-report-accelerometer — Windows offers manual bug reporting in Settings and admin review, while only Android devices expose a phone shake sensor.

## 2026-10-11 — Smooth scroll-linked feed header fade and settling

- Android: Feed upper chrome fades proportionally while sliding out, with a 230ms eased (non-spring) settle; small swipes no longer snap the whole header away. Removed a feed-top observer that could immediately re-open the header while the header itself was consuming a gesture. For You/Following tabs stay pinned. No feed ranking, loading, or Supabase change.
- PARITY-EXCEPTION: feed-scroll-linked-upper-header-fade — Android's small-screen touch feed chrome collapses as the user drags; Windows uses persistent top-level Home navigation with keyboard/mouse access and has no phone-sized collapsing header to animate. Windows navigation behavior remains appropriate to its desktop layout. No backend or feature divergence.
- Validation: Android JVM motion-regression tests cover partial settle thresholds, opacity endpoints and clamping; Android quality gate must pass before production integration.

## 2026-10-11 — Android cold task start and explicit Smart Refresh (Testlab PR #235)

- PARITY-EXCEPTION: android-task-recents-and-native-pull-refresh — Android's Recents removal can create a fresh Activity while leaving its Linux process alive; returning from normal backgrounding should instead keep the current Compose route. Reset saved Home tab, filter and scroll only on a genuinely new Activity task. Desktop has no Android task/Recents lifecycle, and uses its existing native window navigation and refresh controls.
- Android-specific refresh presentation: For You and Reels request a new ranked backend snapshot; the mobile view controller favors unseen server-returned IDs, removes duplicates, and keeps cached content when a network request fails. Following preserves the server's chronological order. A completed refresh with no unseen IDs shows a truthful message rather than fabricated posts.
- Windows equivalent: the existing desktop Home/Reels refresh buttons fetch current Supabase feed data, coalesce repeat requests, preserve their current list on failed requests, and support load-more without Android touch gestures or Compose UI state. Both clients retain backend authorization, audience, moderation and ranking constraints; this PR deliberately does not alter a server API or claim desktop's visual ranking rotation has changed.
- Verification: Android unit, lint, runtime, signed-release smoke and Windows build plus parity-exception review are required before merging; no live Supabase changes are part of this PR.

## 2026-10-10 — Circular Android launcher icon with balanced inner spacing

PARITY-EXCEPTION: android-circular-launcher-padding-20261010

- Android: the launcher draws the **existing approved B artwork** inside a real black circular silhouette, with its full mark visible rather than clipped by a rounded-square image. The adaptive foreground has a 72dp centered circular face; transparent outside corners and synchronized legacy density/round icons make the appearance consistent on MIUI/Redmi and other Android launchers. Keep the original splash and source logo untouched.
- Windows equivalent: Windows icons and application packaging use native ICO/tray icon sizes and do not use Android adaptive foreground/background layers or the MIUI launcher mask. The desktop retains the same approved B mark; Windows UI, window/tray resources and packaging behavior are unchanged.
- Shared/backend/web: no ranking, auth, wallets, messages, server, web/PWA or Windows changes. Android icon regeneration and per-density shape/artwork checks run during Testlab CI.

## 2026-10-11 — For You feed completeness, older content and pagination (Testlab)

- Shared ranked backend: remove the hard seven-day eligibility cutoff while retaining the existing time-decay score. When creator diversity cannot select another post, the final fallback chooses the best remaining eligible candidate rather than ending the feed early. Block, mute, private audience, hidden content, expiry, authentication, qualified views, promotions and ranked scoring remain unchanged.
- Android: retain the current scroll anchor while supplementing a short cached first page with new live post IDs. In-session ranking order stays stable, duplicates are removed, and existing near-end ranked pagination, refresh, offline caching and Following feed are preserved.
- Windows: Home now supports automatically loading older batches from its current authenticated public-post data source, with stable timestamp/ID ordering, de-duplication, error recovery and a manual Load More option. Desktop does not depend on Android's Compose/Room cache or reuse its gesture handling.
- Validation: disposable SQL regression verifies full pages, old-post eligibility, single-creator fallback, stable order and idempotent reapplication; Android quality/runtime, Windows build/parity, Supabase safety and release smoke must pass before promotion.
- Deployment: merging only commits the migration. The Supabase backend migration must be applied and verified separately before this fix can affect the public app. No live database records were modified by the Testlab work.

## 2026-10-11 — BLINK Smart Discovery v3 (Testlab candidate)

- Backend: ranked For You no longer stops after two posts from one creator when strict creator diversity runs out of alternatives. The last-resort fallback uses **only the existing, privacy-filtered candidate set**; no blocked/hidden/restricted post is reintroduced. Environments still using the original seven-day source window upgrade to 90 days; environments already on 90 days keep their current window.
- Android and Windows: use the same `BlinkSmartTags.suggest` policy to show up to three **topically relevant** trending suggestions in the post composer. Maximum five selected hashtags and existing server validation are preserved. Unrelated viral tags are not suggested solely because they are popular.
- Android search: use the real Supabase trending-tags aggregate when available while retaining cached-feed hashtags offline; explicit hashtag searches are deduplicated by the existing privacy-preserving RPC. Desktop already loads the same trending endpoint for Search.
- Both clients' explicit hashtag searches feed the existing private personalization model **at most once per user/tag/hour**, through the authenticated server RPC. Searches and deduplicated events remain private.
- Backend trend calculation remains independent of view multiplier tiers. The existing server ranking, following feed and post privacy policies remain authoritative; no inflated/promised reach for simply adding hashtags.
- Rollout: this branch is isolated from `main`. Validate Android/Windows builds, the PGlite creator-exhaustion regression and Supabase syntax/security gates first. Deploy tag/search migrations and this migration only in a staging Supabase environment before promoting to production. No production migration has been executed by this change.

## 2026-10-11 — Verify APK delivery before publishing

- Android packaging exception: APK archive integrity and signed-build checksum are checked before upload and again after downloading the draft release from GitHub. Missing checksums, mismatched build identity, corrupt archives and altered metadata prevent promotion to latest.
- Public release assets are never overwritten, protecting interrupted downloads from mixing different bytes. A checksum file accompanies each future release.
- Windows: no runtime, UI or backend changes; APK delivery is Android-only. Existing Windows release behavior is preserved.
- Limits: this verifies GitHub delivery, not every browser/network/storage path to a user's device. Signature verification remains in the signed production build; this gate enforces byte equality to that artifact. Failed drafts stay private for diagnosis.

## 2026-10-10 — Server-gated Blink Coin checkout shelf (Testlab)

- Server-catalog correctness: Android now intersects its decorative built-in catalog with active SKUs/prices returned by `get_blink_store_state`; missing items cannot be sold. Windows now hides collections/identity looks with no live backend item. This avoids showing nonexistent purchasable items during incomplete server catalog migrations.\n- Shared: `BlinkCoinCheckoutPolicy` validates fixed-price server-issued coin packs, blocks checkout when the authenticated backend reports cash checkout disabled, and permits only Paystack's expected HTTPS hosted checkout URL. No wallet balance or entitlements are modified in client code.
- Android and Windows: show the same optional coin pack prices from `get_blink_economy_status`, with clear paid-checkout disabled states. A deliberate confirmation starts authenticated `paystack-initialize`, then the provider-hosted checkout; returning users can explicitly ask `paystack-verify` to reconcile that purchase before refreshing balance.
- Android distribution guard: Play-installed builds never link to Paystack for digital coins; Play Billing or an expressly permitted enrolled alternative is required before in-app purchase can be enabled there. Website/sideload builds remain independently gated by the backend setting.\n- Backend: existing server verification/webhook contract retained. No production database writes, migrations, secret keys in clients, automatic purchases, or counterfeit balance changes. Current Blink production database has `cash_checkout_enabled=false`, so cash purchase buttons remain disabled until payment keys/provider review and staged tests are complete.
- Android safety/Windows parity: tests cover invalid checkout URLs, corrupt pack prices, and disabled purchase gate; cash purchase availability must remain false on RPC failures.

## 2026-10-10 — Private feed permissions and active post counters (Testlab)

- Root cause: signed-in users have no USAGE on the private PostgreSQL schema, while public.get_feed_page depended on its private owner-check routine. Preserve locked-down private schema; make only the scoped, authenticated-only feed RPC SECURITY DEFINER with an explicit auth.uid() guard and existing filtering.
- Profiles posts_count was zero even for authors with active posts because the old sync function had no trigger. Reattach a narrow insert/delete/active-status/author trigger; perform one idempotent backfill based exclusively on active posts/reels.
- The author's 16 inactive, textless 2026-09-01 rows are preserved as inactive. This migration never deletes, reactivates, or hides any feed row; it updates only the profile counters and permissions on the specific feed RPC.
- Android + Windows: the corrected shared backend returns following/Home feed rows and consistent counters to both clients; Android profile Posts counts the actual hydrated active non-reels.
- Production protection: verify migration in disposable PostgreSQL via supabase/tests/feed-recovery-tests.mjs; promote with Supabase preview when available, then verify role permissions and actual profiles counters before releasing.
- Rollback: remove the new active-post counter trigger and restore the original invoker permissions of get_feed_page; retain prior rows and recompute counts from active posts if necessary.

## 2026-10-10 — Preview-first Store journeys and BLINK Identity looks (Testlab)

- Shared: `BlinkStoreJourneys` introduces eight honest, goal-based discovery routes backed exclusively by existing Store groups and three curated identity looks. School-specific, dated seasonal and game SKUs stay explicitly planned; those are not available for purchase.
- Android: adds a horizontal goal filter and browseable Identity lookbook above the same 25/70 grouped catalog. All selection leads to the existing preview, confirmation, purchase and Collection flow.
- Windows: mirrors the same shared journeys/lookbook and desktop-appropriate horizontal layouts; identical group and catalog IDs, no fake data or new purchase endpoints.
- No server migrations, new purchases, checkout activation, wallet/ranking/verification changes or misleading discount claims. This PR stages discovery only; detailed remaining rollout requirements live in `docs/coin-economy-expansion-v1.md`.
- Validate the shared mapping tests, Android compilation/lint/UI, Windows parity, and manual previews before any production promotion.

## 2026-10-10 — Feed, publishing, profile, and long-swipe reliability (Testlab)

- Android: Home uses loading placeholders until a real post-fetch completes and distinguishes failed fetches from confirmed empty results. Existing Room cache remains visible while refreshing. Feed author enrichment reads only explicitly selected public profile fields and no longer relies on the absent `get_public_profiles_by_ids` RPC.
- Android/Windows shared backend semantics: a confirmed `201 Created` with a returned post is a published post; a later feed-refresh failure must not reclassify it as an unsuccessful publish. Android now stops re-fetching the whole feed inside the post-create call. Windows already returns the inserted row directly rather than waiting for a global feed refresh.
- Android: profile Posts/Reels hydrate independently before optional liked/saved collections and show retry or placeholders while loading. Windows profile content remains a separate request; neither platform shows mock posts.
- Android: Home utility surfaces (Rank, Game, Store, Boost, Drops, Items) preserve their downward-swipe gesture, but the gesture cannot dismiss the full sheet until the pointer travels 58% of the window height. Inner scroll remains independent. Explicit close/back actions remain immediate.
- PARITY-EXCEPTION: android-compose-bottom-sheet-long-swipe — Material 3 modal-sheet swipe physics is Android-only; Windows uses desktop-native window/panel navigation with explicit close affordances.
- Backend: no live schema, RLS, function, or data mutations were made. Missing/unhealthy PostgREST RPCs and inaccurate stored post counters require separate preview/staging validation before any production migration.
- Validation: GitHub Android unit tests/build/lint and desktop build must be green before production merge.

## 2026-10-10 — Home and Reels manual refresh (Testlab only)

- Android: Home For You, Home Following, Reels For You, and Reels Following now have isolated refresh requests. Manual refresh queries existing Supabase feed RPCs only, never reloads Market, messages or leaderboards. Per-lane progress coalesces repeated taps; requests are serialized against background syncs, cached rows survive failures, and fresh pages retain deduplicated paginated content.
- Android UX: Home has a pinned accessible refresh action beside Filter; both Home and Reels have 96dp intentional pull thresholds. Reels accepts pull gestures only at page zero, does not intercept normal vertical paging, and has an explicit refresh action. Existing BLINK purple loading indicator and first-load skeletons are retained.
- Windows parity: Home refresh and Reels refresh use the same backend feed contract, preserve the visible list on network errors, and prevent duplicate refresh button requests. Desktop uses its native button-based gesture alternative.
- No changes to Supabase schema/RLS/RPC, ranking, qualified views, ads, rewards, authentication, or post publishing.
- Validation: branch `Testlab-smart-home-reels-refresh-20261010` is unpromoted. Require Android compile/unit checks, Windows build, and on-device verification of pull gestures, paged feeds, offline cache, deep-linked Reels and both Following lanes before promoting to `main`.

## 2026-10-10 — Official BLINK launcher and install reliability

PARITY-EXCEPTION: android-adaptive-launcher-and-package-installer

- Android: use the approved B artwork for adaptive, legacy/round density icons, install icon and splash; validate all PNG assets against the approved master and install test-signed release APK on Android 15.
- Windows: desktop already ships the same B artwork; Windows does not use Android adaptive icon layers, splash screens, or the Android Package Installer.
- Web/PWA: synchronize 192/512 PWA icons and refresh cache for the approved B branding.
- Signed release: check the previous published production signing certificate and versionCode; retain Firebase and original signing key requirements.
- Google Maps removal was already merged into main in #206; retain its map-free Android UI and no-Maps CI guards rather than overwriting them.

## 2026-10-10 — Remove Google Maps SDK and production Maps key dependency

- Android: remove interactive Google Maps preview and marker/camera controls from Items → Live Location; keep private sharing, active recipients, recipient selection, sharing duration and existing Supabase live-location service/stop flow. Private active shares appear in a compact list without a map.
- Android infrastructure: remove Maps Compose SDK, Google Maps application metadata, Gradle Maps key placeholder and production `MAPS_API_KEY` requirement. Retain Google Play Services **Location** for opted-in Weather and time-limited Live Location; fused GPS does not need a Maps API key.
- Windows: Google Maps SDK and Maps release key were Android-only. Existing Windows Items semantics continue without modification; Supabase sharing contracts remain unchanged. This is an SDK-level platform exception.
- PARITY-EXCEPTION: android-google-maps-removal — The Android-only Google Maps renderer is removed because BLINK no longer ships a Google Maps key. Windows has no Google Maps SDK, Maps canvas, or key requirement; its existing private location-sharing view and Supabase-backed session semantics are the closest equivalent, so no desktop code change is necessary.
- Production Firebase JSON, pinned release-signing certificate and keystore remain mandatory; no placeholder credentials and no production database mutations.
- Tests: assert no Maps SDK/UI/manifest/Gradle/release secret references in Android quality and release smoke.

## 2026-10-09 — Secure Android production Firebase secret setup (Testlab)

- Release CI only: allow the existing base64 Firebase config secret or a protected multiline `GOOGLE_SERVICES_JSON` repository secret, simplifying setup on mobile without embedding credentials in source.
- Keep production Firebase project, app package, release-signing certificate continuity, Google Maps key and pinned-source verification mandatory. No placeholder config is allowed in production.
- Release smoke CI tests both restore formats and rejects missing credentials using synthetic fixtures.
- Windows, Android runtime feature parity, web and Supabase are unchanged; release-signing is an Android-specific packaging concern.
- Does not make any production secrets available; the repository owner must add actual values in GitHub Actions secrets before the signed APK can be published.

## 2026-10-09 — X-inspired Home feed media and timeline (Testlab)

- Android: Home For You/Following and boosted feed posts now render as a flat, pure-black timeline without separate outlined cards. The author row retains verification and post actions; photo galleries and styled text align beneath the author row with rounded 14dp media, stable natural-ratio framing and X-style cropped previews. Tapping photos still opens the existing full-screen zoomable gallery.
- Android engagement: comments, reposts, likes, qualified views, bookmarks and sharing appear in one compact bar. Existing server ranking, 30-second view reflection, counts, reactions, post management and navigation callbacks are unchanged; Profile uses its previous post-card style.
- Android ads: Google native Feed placements keep clear Sponsored attribution, SDK-owned assets/AdChoices/clicks, an advertiser icon when present and a black timeline surface. Reels ad placement and ad cadence remain unaffected; failed/no-fill ads still leave no fake sponsored content.
- Windows: Home now shows flat black rows, inset and rounded cropped media, and a compact metrics/actions bar. Existing desktop comments, likes, bookmarks, reposts and share-link actions remain connected to their original backend paths. The desktop-native layout remains optimized for wider windows; Android-specific AdMob NativeAdView has no Windows SDK equivalent.
- Validation: staged on `Testlab-x-feed-style-20261009`. Promote to main only if Android quality/build, runtime smoke, Windows build/parity, release smoke and Supabase safety checks pass. No migrations or modifications to live user data.

## 2026-10-09 — Premium motion upgrade (Testlab staging)

- Shared Android/Windows design tokens: unify navigation, selection, interaction and content-reveal durations while keeping Supabase and ranking semantics untouched.
- Android: refine screen/tab transitions, press feedback, Connect and Boost/Drops selection, story image crossfades, Reels controls, messages, leaderboard, notifications, search hero, composer poll, Store selection, Items header and onboarding progress. Existing feed header and post-loading motion remains lightweight and respects reduced-motion settings where already supported.
- Windows: add a corresponding subtle route crossfade and selected sidebar indicator, rather than stretching Android transitions onto desktop. All existing screen routes remain unchanged.
- Verification: Android runtime CI now enables system animation scales and tests rapid navigation changes and accessible selected-tab state; Windows NavigationDrawerItem already exposes the selected state. Retain the existing native animation reduced-motion preference. Visual review on real hardware remains recommended for fine tuning.
- Safe development: staged in `Testlab-premium-motion-suite-20261009` branched from current `main`. Require Android, Windows and runtime workflow gates before merging.

## 2026-10-09 — Compact Android Home top chrome (Testlab)

PARITY-EXCEPTION: android-home-compact-density-20261009

- Android Home: reduced the feed's brand/header vertical padding, profile-ring footprint, shortcut-row height and For You/Following tab-row height. The three rows now use 48dp each rather than approximately 58dp + 52dp + 54dp, saving about 20dp above posts on regular phones.
- Safe area/accessibility: preserve the system status bar inset and existing 48dp utility/tab tap targets; the X-style scroll-linked header collapse and pinned tabs remain unchanged. No icons, shortcuts, filter, feeds, gestures or routes are removed.
- Windows: desktop-native Home layout already controls top chrome independently and has no phone status-bar inset. This mobile screen-density change has no desktop behavior or business-logic equivalent; Windows navigation remains unchanged.
- Shared/backend: no ranking, views, notifications, wallet, Supabase, auth, Reels, content, or database contract changes.
- Validation: staged on a `Testlab-*` branch pending Android/Windows CI and on-device visual verification before promotion to `main`.

## 2026-10-09 — Official BLINK logo master, conflict-free Testlab promotion

- Android and Windows: use the exact approved official logo PNG from the previously staged Testlab logo branch, preserving the original shared blob bytes for both clients.
- The existing Android launcher, splash and Compose entry points continue to load `R.drawable.app_icon`; the Windows desktop/tray entry points continue to load `blink-logo.png`.
- Both platform resources are intentionally identical. This is an asset-only change; existing web assets, layouts, icon positioning, account data, auth, wallet, Boost, feeds/Reels, notifications and backend behavior are unchanged.
- Safety: cleanly reapplied to current `main` in a new Testlab branch, avoiding the previous 212-commit conflict. Require shared-asset integrity, Android/Windows and parity CI before merge.

## 2026-10-07 — Verified Testlab promotion compatibility

- Shared: Android and Windows share inbox classification and immutable Boost/Drop request fingerprints. Retry IDs stay bound to the same submitted inputs.
- Android: installs its own lifecycle-owned Compose host before window enhancements or consent UI. Realtime presence uses the canonical server RPC and respects existing online-status privacy.
- Windows: Primary/Requests/Archived inboxes, pin/archive/unread controls, request responses, timed/mentions-only notifications and real forwarding use the same authenticated chat RPCs. Boost uses v2 idempotent spending, budget recommendations, analytics, receipts and live refresh. The Drops tab supports real eligibility/budget previews, confirmed creation, actions/rewards, history, refunds, top givers and receipts.
- Backend: chat migrations supply additive dependencies on older schemas; forwards respect DM privacy and membership; request identities cannot be rewritten; timed mutes expire. Profile privacy honors the existing online toggle, preserves username checks through a narrow RPC and redacts inbox presence without changing pagination or ranking.
- Verification: isolated PostgreSQL fixtures check schema dependencies, account boundaries, retry spending, pin limits and reapplication. They contain synthetic rows and never connect to production. Android, Windows, web, domain and release gates remain required before promotion.

## 2026-10-07 — Logo decoding repair across clients

- Android, Windows and web: re-encode the approved compact purple-eye artwork into fully decodable PNGs, with launcher density sizes and a valid multi-size Windows ICO. All clients use the same source artwork.
- Verification: the shared asset gate now decodes the complete pixel stream as well as checking chunk CRCs. A regression fixture with correct CRCs and broken compression must fail before Android builds.
- Backend and ranking behavior: unchanged.

## 2026-10-07 — Android edge-to-edge startup resilience

PARITY-EXCEPTION: android-edge-to-edge-startup-resilience-20261007

- Android: treats `enableEdgeToEdge()` as a non-critical window enhancement. If the Android window/content container is unavailable during startup, BLINK logs the platform failure and continues to Compose instead of crashing before the welcome surface renders.
- Why Android-only: `enableEdgeToEdge()` and the affected window/content-container lifecycle are Android Activity APIs. The Windows desktop client does not use Android window-inset plumbing.
- Windows equivalent: none required; Windows keeps its existing desktop window setup and startup behavior.
- Shared/backend behavior preserved: no feed/Reels ranking, qualified views, auth, wallet, messaging, notifications, profile, Supabase schema/RPC/RLS, or user-data rules change.
- Validation: Android runtime smoke plus normal Android quality/release checks, Windows build/parity, and Supabase migration safety must pass before promotion.

## 2026-10-07 — Connect reliability, discovery and unified inbox overhaul

- Android: Connect now uses a coordinated full-height scroll surface instead of nesting the main LazyColumn around a fixed-height pager; student discovery adds Same Department and Verified filters plus Recommended/Recently Active/Campus First/A–Z sorting and resettable empty states. The 20 Connect directory entries are grouped into Housing, Study, Career & Skills, People & Community and Games. A profile-aware For You rail and unified Connect Inbox surface requests/challenges without changing the authoritative request RPCs. Pending badges ignore completed request history.
- Windows: Connect adds the same grouped discovery concepts, enhanced student filters/sorting, For You summaries and a real Inbox backed by the existing `get_connect_request_inbox`, `respond_connect_request` and `respond_game_challenge` contracts. No mock request state is introduced.
- Reliability/backend: Android Connect snapshot loading now isolates Roommate, Mentor, Reading Mate, Housing Agent, Housing Request, Challenge, Smart Match, Inbox and game-stat failures so one unavailable dataset cannot blank the whole Hub. No production Supabase schema, ranking, coins, auth, messaging, moderation or notification rules are changed.
- Safety: implemented on `Testlab-connect-overhaul-20261007`; production `main` remains unchanged until Android and Windows checks pass.


## 2026-10-07 — Final BLINK logo parity

- Android: applies the approved black-background BLINK eye mark to the launcher, round launcher, adaptive foreground, splash/in-app brand asset, and keeps the artwork centered without stretching or cropping. The manifest now uses the launcher resources so platform masking is handled correctly; themed monochrome is intentionally omitted because the approved artwork has an opaque black background.
- Windows: uses the same approved artwork for in-app branding, window/tray icon, and the native EXE/MSI package icon through the shared ICO resource.
- Web/PWA: uses the same approved artwork for boot/brand surfaces, favicon/apple touch icon target, PWA manifest icons, social preview reference, and refreshes the service-worker cache key so clients do not retain the older mark.
- Safety: branding-only change. No feed/Reels ranking, qualified views, auth, notifications, wallet/coins, Store, payments, Supabase schema/RPC/RLS, or user-data behavior is changed.

## 2026-10-07 — X-style feed polish, restoration and performance hardening

- Android Home: the BLINK branding/action chrome now collapses continuously while For You / Following remain pinned; fling velocity participates in settle decisions; 12dp direction hysteresis still suppresses jitter; pull-to-refresh forces a stable fully-open chrome state; the Create Post action compacts on downward intent and expands after deliberate upward movement or when scrolling settles.
- Resume behavior: Home stores the stable top-row key, pixel offset and header collapse fraction per account/lane, so Feed → Profile/Search → Feed restoration prefers the exact post rather than a fragile numeric index when sponsored/Drop/reel rows change.
- Media/performance: image lookahead adapts to scroll speed with a bounded prefetch set; inline reel autoplay now requires at least 70% visibility and exactly one upcoming inline reel may be prepared while paused; sampled scroll telemetry records only aggregate frame counts/durations and never post IDs, usernames, media URLs or content.
- Accessibility: Android's system animator-duration preference disables decorative feed settle/entrance animation when animations are turned off.
- Scroll consistency: Home/Following share the same feed implementation, while Profile, Search and Connect were verified to use Compose's standard LazyColumn/LazyRow physics without custom fling overrides.
- Backend/safety: no feed/reel ranking, qualified-view algorithm, repeat-view rule, wallet/coin rule, authentication, notifications, Supabase schema/RLS/RPC or server payload contract is changed.

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

PARITY-EXCEPTION: android-connect-category-dialog-lifecycle-20261006
Date: 2026-10-06
Feature: Android Connect category panel visibility repair
Android behavior: Fixes Roommates, Mentors, Reading Mates, Housing Agents, Housing and Challenges opening into only a dark scrim by composing the Android side panel before moving its nested pager.
Why this is genuinely Android-specific presentation: The failure is caused by the Android Compose Dialog + AnimatedVisibility + HorizontalPager lifecycle. No Connect data model, matching rule, request action, backend contract, ranking rule, or shared business behavior changes.
Windows equivalent or reason no equivalent is needed: Windows does not use the Android Compose modal/pager lifecycle that can deadlock in this state, so no Windows behavior change is required.
Backend/shared behavior preserved: Existing Connect Hub repository calls, roommate/mentor/reading/housing/challenge actions, follow state and Supabase behavior remain unchanged.
Tests/validation: Android quality/release-smoke, Windows build/parity and Supabase safety gates must pass on Testlab before merge.
Owner/reviewer note: This exception is limited to the Android category-panel rendering lifecycle and cannot waive parity for future Connect capabilities or business logic.

---

PARITY: activity-pulse-hardening-20261007
Date: 2026-10-07
Feature: Connect Community Activity + Leaderboard Rank Pulse hardening
Android behavior: Uses the shared server-controlled pulse policy, safe overflow-capped ranges, gradual session-persistent number movement, minimum hold timing, reduced-motion handling, foreground/window pausing, offline freeze, real online previews, campus activity labels, real leaderboard mover summaries, rank heat status, and rate-limited product analytics.
Windows behavior: Uses the same shared pulse policy and range/transition functions, honors the desktop Reduce Motion preference, freezes the last pulse when live fetches fail, shows real online/campus context and real rank movement summaries, and records the same server analytics events.
Backend/shared behavior: Adds an authenticated read-only pulse configuration table and an authenticated rate-limited analytics RPC/table. Presence rows, canonical leaderboard order, points, XP, feed/reel ranking, coins, verification, auth, messages and existing user data are not modified.
Tests/validation: Shared/Android pulse range tests, Android quality/release smoke, Windows compile/package/parity, and disposable Supabase migration safety must pass on Testlab before production promotion.


---

PARITY-EXCEPTION: android-reels-interaction-hardening-20261007
Date: 2026-10-07
Feature: Android Reels playback and interaction hardening
Android behavior: Hardens the existing Android vertical Reels experience with adjacent media preloading, persistent sound state, tap-to-pause, seekable playback progress, privacy-aware action availability, interaction-sheet playback pausing, retry handling, and actionable Not Interested feedback through the existing recommendation path. Existing For You/Following routing and server-authoritative Reel data remain in place.
Why this is genuinely Android-specific presentation: The changed playback lifecycle, VerticalPager behavior, gesture handling, Compose sheets, ExoPlayer preparation/retry state, and mobile media controls are Android UI/media-adapter concerns. They do not change the cross-platform Reel ranking formula, qualified-view rules, visibility/privacy rules, or shared content model.
Windows equivalent or reason no equivalent is needed: Windows keeps its desktop-native Reel/video playback and navigation controls rather than copying Android touch gestures, VerticalPager lifecycle, or ExoPlayer state handling. No Windows user capability, shared recommendation rule, or server contract is removed or changed by this Android reliability/presentation patch.
Backend/shared behavior preserved: Feed/Reels ranking, qualified-view timing and multipliers, points/XP, coins, verification, authentication, notifications, moderation, Supabase schema/RLS, and existing user data are unchanged. Not Interested uses the existing recommendation feedback path and does not redefine ranking weights or shared recommendation policy.
Tests/validation: Android quality, Android runtime smoke, Android release smoke, Supabase safety, BLINK domain integration, and Windows desktop build/parity gates must pass on the Testlab head before promotion to main.
Owner/reviewer note: This exception is limited to Android Reels UI/media lifecycle and touch presentation. Any future cross-platform Reel capability, recommendation algorithm, backend contract, or business-rule change still requires Windows/shared parity.


---

PARITY-EXCEPTION: android-feed-scroll-polish-20261007
Date: 2026-10-07
Feature: Android phone Home feed touch-scroll chrome, keyed restoration, inline media preparation and aggregate frame telemetry
Android behavior: Keeps For You/Following sticky while the upper Home chrome collapses with nested touch scrolling; uses velocity-aware settle, pull-to-refresh coordination, system reduced-motion preference, exact keyed resume state, adaptive image lookahead, one-next-inline-reel preparation and content-free sampled frame timing.
Why this is genuinely Android-only: The changed chrome behavior is tied to compact phone touch/nested scrolling, Android animator settings, Choreographer frame callbacks and Android inline ExoPlayer preparation. Those adapters do not map directly to Windows mouse/keyboard desktop navigation.
Windows equivalent or reason no equivalent is needed: Windows keeps desktop-native fixed navigation and standard Compose desktop scrolling. Its Home/Search/Profile/Connect routes are not given phone-style collapsing chrome. Windows continues to build/test against the same feed/backend contracts.
Backend/shared behavior preserved: Ranking, qualified views, repeat-view limits, posts/reels data, wallet, verification, authentication, notifications, moderation and all Supabase contracts are unchanged.
Tests/validation: Pure JVM tests cover velocity settling, viewport autoplay threshold, adaptive prefetch depth and keyed restore resolution. Android unit/lint/instrumentation/debug/release gates plus Windows parity/build/package and Supabase safety must pass before promotion to main.
Owner/reviewer note: This exception covers presentation/performance adapters only and cannot waive Windows parity for future feed features, ranking semantics or backend behavior.


---

PARITY-EXCEPTION: market-duplicate-lookup-compile-hotfix-20261007
Date: 2026-10-07
Feature: Market duplicate lookup compile regression hotfix
Android behavior: Removes one duplicate `fetchMarketItemById(String)` declaration from the Android Supabase service so the already-merged Market implementation compiles again. The retained implementation keeps the existing authenticated lookup, UUID validation, wishlist state, and Market behavior.
Why this is genuinely Android-specific: This PR only repairs a duplicate Kotlin declaration in the Android service layer that was created during the Market merge. It does not introduce or change a product feature, backend contract, ranking rule, user data behavior, or Windows UI.
Windows equivalent or reason no equivalent is needed: Windows does not contain the duplicated Android Kotlin declaration and already builds successfully, so there is no equivalent code change to make there.
Backend/shared behavior preserved: Supabase schema, Market rows, authentication, wallet, feed/reels, messaging, ranking and shared contracts are unchanged.
Tests/validation: Android validate, runtime-smoke, release-smoke, Windows build, parity and migration-safety must pass in Testlab before this hotfix is merged to main.
Owner/reviewer note: This exception is limited to this compile-only duplicate declaration removal and cannot be reused for future Market feature changes.


---

PARITY-EXCEPTION: android-professional-chat-upgrade-20261007
Date: 2026-10-07
Feature: Android professional messaging/chat interaction upgrade
Android behavior: Adds Android-native chat interaction polish including persistent composer drafts, media and voice-recorder flows, conversation interaction sheets, realtime message controls, richer message state handling, and Android-specific navigation/lifecycle wiring. Optional server-side conversation inbox state remains backward-compatible.
Why this is genuinely Android-specific in this PR: The changed presentation and capture flows depend on Android Compose touch interaction, Activity/URI media pickers, microphone capture, Android lifecycle/navigation, and Android realtime adapters. This PR does not redefine the shared message payload contract or remove Windows messaging capabilities.
Windows equivalent or reason no equivalent is needed: Windows keeps its existing desktop-native Messages surface with conversation search, chat search, send, reply, copy, forward, contact actions and desktop layout. The optional backend chat-control schema is additive, so Windows remains compatible without adopting phone-style voice recording, sheets, gestures, or Android lifecycle behavior in this PR.
Backend/shared behavior preserved: Existing conversations, messages, auth, read/delivery state, feed/reels, ranking, wallet and user data remain intact. New chat-control columns/tables are additive and guarded by RLS.
Tests/validation: Android validate/runtime/release/verify, Supabase migration-safety, Windows build and parity must pass in Testlab before production promotion.
Owner/reviewer note: This exception only covers Android-native interaction/capture/lifecycle presentation. It cannot be used to waive Windows parity for future shared messaging business rules or message data-model changes.

---

PARITY-CHANGE: profile-user-profile-hardening-20261007
Date: 2026-10-07
Feature: BLINK own-profile and public user-profile hardening
Shared/backend behavior: Adds explicit PUBLIC/FOLLOWERS/PRIVATE contact and presence visibility policy, server-redacted authenticated profile-detail reads, real daily follower snapshots for owner analytics, profile-specific notification modes, reusable mute/report/block contracts, follower/following lists, and a server-enforced maximum of three pinned profile posts/reels. Profile notification delivery reuses the existing notifications table and push pipeline: ALL receives public posts/reels, REELS receives public reels only, and IMPORTANT receives newly pinned public updates.
Android behavior: Public profiles now separate Posts and Reels, keep Liked/Saved activity owner-only, show real rather than fabricated follower analytics, expose searchable follower/following lists, scope search to the viewed profile, wire Share/Notifications/Mute/Report/Block menu actions, enforce contact/presence visibility, show private owner insights, and allow owners to pin/unpin content through the server limit. Edit Profile exposes privacy selectors for email, phone, WhatsApp and online/last-seen visibility.
Windows behavior: Other-user profile detail is now reachable from Search and Connect and uses the same server-redacted profile contract. The Windows profile dialog supports Posts/Reels/About, profile-local content search, follow state, profile sharing, notification mode, mute/report/block, and follower/following lists. External desktop profile reads no longer rely on the private owner projection.
Backend safety: Existing follow, block, report, mute, ranking, XP, Blink Coin, verification, feed/reel ranking and qualified-view algorithms are not rewritten. New contracts are additive; Android profile updates include a compatibility fallback while Testlab may still point at the pre-migration production schema. The migration is not applied to production from this branch.
Tests/validation required before promotion: shared tests, Android unit/lint/instrumentation compile/debug/release-smoke, Windows build/tests/package, Supabase safety, parity gates, and domain integration. Because this Supabase project currently has no preview database branch, the migration must not be applied to production merely to test this PR.

---

PARITY-EXCEPTION: android-growth-suite-wallet-ui-hardening-20261006
Date: 2026-10-06
Feature: Android Growth/Boost/Drops production hardening and wallet presentation
Android behavior: Adds Android realtime Blink Coin wallet mirroring, resilient loading/retry/offline states, Boost budget presets and campaign analytics/history/receipts, Drop eligibility previews/history/receipts, spend confirmations, process-local draft preservation, and Android notification routing into Boost/Drops. Duplicate spend submissions are protected by additive server-side idempotency RPC wrappers.
Why this is genuinely Android-specific presentation: The modified app files are Android Compose UI, Android notification/deep-link routing, and the Android Realtime adapter. They do not alter which Growth capabilities Windows is entitled to or the organic Feed/Reels ranking model.
Windows equivalent or reason no equivalent is needed: Windows keeps its existing Boost desktop experience and the existing server-authoritative Growth contracts. The new backend RPCs are additive wrappers/analytics and the prior APIs remain compatible. Phone bottom-sheet navigation, Android notification intents, pull-to-refresh, and Android Compose layout do not map one-to-one to the desktop shell.
Backend/shared behavior preserved: public.user_balances.spendable_coin_balance remains the single wallet authority. Boost pricing/delivery, Drop follower snapshots/reward/refund rules, Store pricing, verification, XP/Rank Points, authentication, and organic recommendation/ranking behavior remain server-authoritative. The new backend work adds idempotency, read-only analytics/receipts, lifecycle notifications, scheduled settlement, and realtime publication for the existing wallet table.
Tests/validation: Android unit/lint/instrumentation/debug/release checks, Windows build/package/parity, and Supabase safety must pass on Testlab PR #172 before merge. The production migration is applied only after those gates pass and the Testlab PR is merged.
Owner/reviewer note: This exception covers Android presentation/notification/realtime adapters only. It cannot waive Windows parity for future Growth business-rule, pricing, wallet, reward, or ranking changes.

---

PARITY-CHANGE: testlab-profile-content-and-ai-composer-20261007
Date: 2026-10-07
Feature: Carry forward the unresolved AI composer and profile-data fixes from PRs #58 and #86.
Android behavior: Send stays beside the AI message field while attachment controls scroll independently. Profiles read their own paginated content and authenticated-owner likes/saves rather than using the currently loaded ranked feed. Actions update profile content as well as feed caches; refresh reloads the profile query. Inactive avatar dots are removed.
Windows behavior: The existing AI dialog retains its fixed Send action. Profile content and private liked/saved relations now paginate independently of the feed, including owner-only Liked/Saved tabs. Inactive avatar dots are removed.
Shared/backend behavior: Existing feed ranking, content access, presence projection, wallet, XP and notification delivery contracts are preserved. Private relation queries always use the authenticated account. Existing real follower snapshots remain the source for history.
Validation: Android lint/unit/instrumentation/debug/release/runtime, Windows build/package, parity and isolated migration/RLS regressions must pass before main promotion.

---

PARITY-CHANGE: private-profile-ranked-rpc-compatibility-20261007
Date: 2026-10-07
Shared/backend behavior: Keep legacy ranked Feed, Connect, Games and Search RPCs compatible with owner-only raw profile reads. A narrowly projected authenticated profile function includes only existing public identity/ranking fields and redacted presence. The existing formulas, weights, audience checks, cursors and RLS on other tables remain intact. Legacy inbox reads delegate to the canonical membership/presence contract; study-circle owner requests retain requester public identity.
Android and Windows behavior: Both surfaces retain cross-account discovery and public identity while using the same private-contact and presence projection. Existing wallet, XP, rank, Boost and content algorithms are preserved.
Validation: The isolated PostgreSQL fixture includes the actual legacy RPC definitions and pg_trgm. Regression checks exercise other-user Feed, Connect, Games, Search, inbox and study-circle rows, deny raw private profiles/anonymous projection execution, and verify hidden presence. All seven migrations reapply.

---

PARITY-CHANGE: study-circle-rls-recursion-20261007
Date: 2026-10-07
Shared/backend behavior: Circle and member SELECT policies use a scoped authenticated owner/member/public access helper, avoiding mutual RLS recursion and uncorrelated membership matches. Existing circle mutations, requests and ranking formulas remain intact.
Android and Windows behavior: Existing study-circle and owner-request reads keep working through the same backend contract.
Validation: Isolated PostgreSQL checks prove private circles/members stay hidden from an account that belongs to a different circle, while the owner and approved members retain access. All eight promotion migrations reapply.

## 2026-10-11 — Android JNI crash and ranked-feed recovery (Testlab)

- PARITY-EXCEPTION: android-webrtc-jni-and-ranked-timeout — Android's R8 release shrinker renamed WebRTC JNI classes and caused a native abort during voice/video calls on Android 15. Keep `org.webrtc.**` class/member names and verify real release DEX. Windows does not use Android's R8, JNI_OnLoad or Android WebRTC SDK.
- Android: the ranked-feed RPC can time out under backend load. Fall back only for PostgreSQL statement timeout to the standard paginated feed, preserving posts/Reels type and pagination; auth/RLS failures still surface. Windows currently uses a separate chronological `feed_posts` fetch, so it is already independent of the Android ranked-feed RPC.
- Shared Supabase: expand the discovery window from seven to 90 days (the live server had only four active non-reels from the last seven days but 49 active posts in the last 90 days); postpone the content view-counter update until after points/repost work to shorten exclusive `feed_posts` lock duration; guard fallback visibility with audience, moderation and expiration checks. These changes affect both clients and require database preview/rollout, not just Android build tests.
- Validation: Android debug/release smoke, R8 DEX retention check, Windows compilation, SQL parser/static regression gates, mobile call media startup, For You pagination, Follow feed, privacy and recorded-view integrity. Do not merge or deploy until release and backend validation.

---

PARITY-EXCEPTION: android-touch-growth-drop-stepper-20261013
Date: 2026-10-13
Feature: Phone-first horizontally swipeable content selection and animated step-by-step Boost/Drop campaign forms
Android behavior: Android's compact vertical sheet changes to a finger-driven horizontally snapping content card picker, motion-reduced selection preview and Back/Continue wizard. Boost fetches the existing authenticated 'get_my_blink_boostable_content' inventory instead of relying on the public discovery-feed cache. Coin reservations and payouts remain server-authoritative.
Why this is Android-specific presentation: The change addresses touch navigation inside Android Compose bottom sheets on narrow phone screens, not a business-rule change. It introduces no new entitlement and no distinct Android pricing, eligibility, audience, wallet or promotion semantics.
Windows equivalent: The Windows desktop Boost and Drops dialogs already offer selection with mouse/keyboard and step-labeled controls (DesktopBoostGrowthScreen / DesktopDropsScreen); the desktop layout has room for its existing scrollable content pickers, and touch-snap transitions are not desirable for desktop pointer navigation. Both clients share the same core server-side campaign and Drop eligibility contracts.
Backend parity: The additive migration restores pre-existing Growth v3 RPC contracts that have not yet reached production. It does not modify the underlying Boost/Drop pricing, reward/eligibility, coin accounting, anti-replay or feed ranking rules. Server-owned content remains user-scoped.
Validation: Android compilation/release/runtime gates and migration safety must pass on Testlab before promotion; production migration must be deployed separately after validation.

---

PARITY-EXCEPTION: android-home-reels-phone-ux-20261014
Date: 2026-10-14
Feature: Android phone Home feed image/identity presentation and touch-first reel transport controls.
Android behavior: Display full photo frames with no center crop; obtain verified badge and follow identity from the loaded public author profile when available; wrap full names across up to two lines; make Follow visible on narrow Home cards. Reel overlays use four engagement actions, move auxiliary mute/options to the top, support tap pause/hold 2x playback and mouse-equivalent touch seek, and leave room for the persistent phone bottom navigation.
Why this is genuinely Android-specific presentation: The bugs are caused by Android Compose X-style card layout (including an Android-only conditional hiding Follow), Android phone-specific insets/56dp persistent bottom tabs, Media3 ExoPlayer touch gesture handling and the narrow portrait Reels overlay. These changes do not change backend follows, verification eligibility, saved states, count visibility, recommendation, media storage or account entitlement rules.
Windows equivalent: Desktop user profiles and Feed/Connect identity continue to use the same public profile/verification/follow state server data; no server contracts have changed. Desktop uses its independent pointer/keyboard-first feed/media layout and native mouse/keyboard transport controls, and does not present the Android phone bottom navigation or Media3 touch-screen reel page. Its BlinkRemoteImage component supports ContentScale.Fit where required, rather than relying on the Android's forced 4:5 crop. Porting Android long-hold speed and bottom inset offsets to desktop would be inappropriate.
Verification: Android Kotlin compile and debug/release smoke, manual Android 15 gestures/photos/narrow-screen follow/verified tests, Windows build, parity and security checks must pass on PR #275. This exception covers phone presentation and gestures only; any future shared account/verification/media semantic changes must implement both clients.


---

PARITY-EXCEPTION: android-compose-skeleton-loaders-20261015
Date: 2026-10-15
Feature: Android skeleton placeholders during initial requests for BLINK Feed-adjacent content, chat history, comments, market media, Connect discovery, giveaway/Boost lists, AI history, mission panels, leaderboard and weather.
Android behavior: Render noninteractive shapes matched to awaiting rows/cards only while uncached information is pending; preserve real cached content on refresh, existing empty/error screens, navigation, and app/session flows. Use Material color schemes for dark/light and the existing static gradient instead of per-cell infinite animations for low-end Android devices.
Why Android-specific: These changes exclusively alter Android Jetpack Compose screen-level loading presentation and phone-specific dimensions. They do not change APIs, wallet values, points, ad behavior, database schema or business rules.
Windows equivalent: The Windows desktop already has independent Compose Desktop loading/progress states and wider mouse/keyboard screen layouts in desktopApp. It does not reuse Android Activity screens or Android-specific 44dp touch avatar/list placeholders. Desktop should keep its existing cached-data-first and loading/error states; equivalent placeholder visuals can be introduced through desktop UI separately when visual QA warrants them. This is a presentation exception, not a new feature unavailable on desktop.
Validation: Android unit tests, lint, release compilation, runtime smoke and light/dark screenshot review on Testlab-skeletons-20261015 before the draft PR is made mergeable; Windows parity gate and Windows build must remain green.

## 2026-10-15 — Sponsored discovery full PostCards and search clarity (Testlab)

- Paid Search content hydrates through authenticated `PostRepository.fetchPostById` before rendering the full standard `PostCard`; failed or private content is never substituted with a shortened synthetic post.
- Promoted profiles remain compact and clearly labeled; promoted posts and reels display full cards with the same linked interactions and dedicated Open actions across both Phase 2 fallback and Phase 3 discovery.
- Offline empty-feed states accurately describe missing cache and offer a retry rather than inviting an unrelated post.
- Shared pure `searchResultsSummary` helper fixes misleading pagination language with unit tests.

## 2026-10-15 — BLINK Market reliability staging (PR #296)

- **Shared/backend contract:** Android and Windows use the same Supabase market listings, deterministic price/date/ID sorting, price-range constraints, category searches, and real seller IDs. No simulated listings or separate Windows backend.
- **Android:** Market search request generation guards and pagination offsets, server price filters, staged filter application, safe wishlist reconciliation, request-in-flight protection, seller ownership checks, upload cleanup and image controls.
- **Windows:** Marketplace query, category and min/max price filters, sorting, refresh and explicit network/empty states using the same real Supabase records.
- **Platform-specific exception:** Android product photo upload cleanup and external WhatsApp deep-link validation are Android adapters. Windows currently exposes a simpler listing creation UI; its existing form must not be treated as an equivalent verified seller checkout and remains a known parity follow-up.
- **Release blocker:** The tracked marketplace hardening migration and server RPCs are absent from live Blink Supabase, so database updates must be staged and tested before any production promotion. This PR remains draft pending complete Android, Windows, backend and real-device checks.


## 2026-10-16 — BLINK Market full-page saved and verified filters

- **Shared contract:** Android and Windows use the same authenticated Supabase wishlist RLS relationship to filter saved listings, and `seller_is_verified` to filter verified sellers, before pagination rather than discarding loaded pages locally. They share price validation and stable sorting in `MarketQueryPolicy`.
- **Android:** Marketplace request state carries saved/verified filters through initial search, refresh, lazy paging and post-publish refresh, including visible-only wishlist lookups that do not truncate results at 1,000 saved items.
- **Windows:** Equivalent Saved only and Verified sellers checkboxes query the same server schema. No mock listings or secondary backend.
- **Backend:** Existing order statuses are preserved while adding accepted/declined/completed, with isolated PGlite tests for stock reservation, authorization, idempotency and the full versioned migration. Production deployment remains a separate staged safety gate.

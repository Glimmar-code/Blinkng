## 2026-10-29 — Combined Contest, Create Post and Chat repair

- Preserves PR #638, #639 and #644 histories on `Testlab/contest-create-post-chat-20261029`.
- Shared Contest voting handles JSON-null votes correctly and fails closed for existing votes, self-votes, invalid candidates and closed contests. Completed/cancelled history, Windows load retry and explicit native media upload use matching limits on Android/Windows. Invitation outages preserve the explicit app-opening action without automatic votes.
- Combined APK/lint/instrumentation compilation, 26 focused Android tests, 25 Windows tests, full isolated SQL suite and invitation regressions passed. Native Android checks took 27m 10s; CI has a 60-minute budget for the larger full suite without dropping checks.
- Remote CI/review, hosted deployment/storage and device/Windows installer verification remain outstanding. GitHub API is blocked by the current proxy allowlist. No merge to main or production SQL deployment is claimed.

## 2026-10-29 — Combined Chat and Create Post reference implementation

- Integrated PR #639 at `96f0f2d05c83698a0db3ade66fc1fc5fc723c87f` and PR #644 at `0300879e20a4897b3a086013607d59e15ecdf82a` on local `Testlab/chat-create-post-reference-20261029`. Both original histories and screenshot-export checks are preserved. Main, original PRs and Contest work were not modified remotely.
- Android retains the charcoal conversation, real media/poll/voice components and reference Create Post flow. Removed invalid Compose test imports and corrected the Gradle distribution to the minimum required by AGP 9.1.1, with its official SHA256. Reference posting colors, text backgrounds and poll validation now share Kotlin contracts with Windows.
- Windows now has a separate reference composer alongside the retained Classic composer: text, ordered photo albums/cover selection, an exported video published as a Reel, timed single-answer polls, mentions, real trending tags, audience/location/comments, preview, encrypted account-scoped drafts and publication confirmation. Feed poll results/voting use authenticated server data; private posting stays disabled. Streamed photo/video uploads use the existing `users/<account>/posts/` storage policy. Retry preserves request/object IDs and completes partially created polls before reporting success.
- Windows conversation native poll cards now share the charcoal palette. Desktop adaptations use resizable desktop layouts/file dialogs and native video playback rather than phone hardware frames or Android permission dialogs.
- **Remaining parity limitations:** Windows does not implement Android Media3 montage/export, in-app photo crop/markup, or the full native video effects editor. It accepts already edited files. These are outstanding capabilities, not a claim that editing parity is complete. Desktop polls, storage authorization and DPAPI persistence still need authenticated Windows/backend integration evidence.
- **Verification:** See `docs/integration/reference-design-20261029.md` for executed checks and explicit device/visual/release limitations. No production migration, merge, deployment, PR publication or environment publication was performed.

## 2026-10-29 — BLINK conversation charcoal reference (Testlab, verification pending)

- **Source:** Owner-supplied screenshot (black/charcoal chat, pale outgoing bubbles, blue controls and read ticks), replacing the navy/blue outgoing style introduced in PR #640. This is a new visual reference; do not misreport PR #640 as a pixel match.
- **Shared:** Added `BlinkChatReferenceStyle` tokens so both platforms consume the same values for canvas, incoming/outgoing bubbles, text, and blue accents.
- **Android:** Updated conversation-only palette, plain header/call actions, presence, slim pinned row, incoming avatar handling, outgoing receipt contrast, reply surface, compact poll, and separated + / emoji / message+camera+microphone / blue send composer. Existing Supabase RPCs, permissions, attachment flows, voice note recording, media, and delivery states are unchanged.
- **Windows:** Conversation background, header, bubbles, receipt contrast and attachment affordance use the same shared colour values. Desktop layout and platform-specific file/media actions remain desktop-native.
- **Verification gate:** The Android screenshot must be captured on a device/emulator with the *same fixture*, matched/cropped to the reference display and compared using `scripts/testing/compare_chat_reference.py`. The script writes cropped images, an overlay, a difference heatmap and machine-readable metrics. The existence of a screenshot test is **not** evidence of a pixel match; UI/device results must be recorded before promotion.
- **Status:** Staged in Testlab. Do not merge to production on static edits alone. Run relevant Android build, instrumentation screenshot comparison and Windows build/parity before promoting. No backend migrations or credentials are included.

## 2026-10-28 — BLINK Chat reference screenshots (Testlab PR #640)

- **Android:** The dark conversation uses the supplied black/navy/cobalt palette rather than the former green reference, the pinned-message banner jumps to the original message, and the plus composer opens a 3×3 attachment menu. Existing voice recording, media permissions, outbox, privacy, sent/delivered/read receipts, photo editor, polls, replies and forwarding remain connected.
- **Media:** One/two/three photo albums now use 1-up, 2-up, or a 1+2 collage; full-screen image browsing adds an interactive album filmstrip. Video can expand to a full-screen native ExoPlayer. Voice playback shows actual position, decorative waveform bars and selectable 1×/1.5×/2× speed.
- **Windows:** Conversation bubbles and header use the same reference color values; the attachment action now opens a functional Windows attachment dialog (gallery, stickers, view-once and existing text-poll composer).
- **PARITY-EXCEPTION: chat-native-android-media-capture** — Android's CameraX/system photo/video picker, microphone recording, OS contact/location intents and player operate through native Android APIs. Windows uses a desktop file picker, the existing desktop picture viewer and Windows-supported chat actions instead of unimplemented Android intents.
- **Limit:** The supplied mockups are generated design references with mutually inconsistent phone sizes, dummy photos and variants. No sample conversation or generated people are included in the production APK. Visual parity must be checked on an Android test device at multiple sizes before any merge.

## 2026-10-28 — BLINK Contest free entry, contestant sharing and deep vote links (Testlab PR #638)

- **Shared/backend:** Authenticated free-entry and one-vote-per-account, self-vote rejection, scheduled windows, top-20 and full approved-candidate public lookup (including users outside the top 20). Admin approval and contest management remain server-authoritative; prizes are informational, not automatic payouts.
- **Android:** Fourth primary page is Contest; original Connect is available from inside it. Each contestant has Share; a generated portrait PNG flyer features the public profile photo, contest, current rank/votes and a shareable candidate-specific vote URL. Verified links route into BLINK, resume through login/signup and open the chosen contestant; the user must tap Vote manually.
- **Windows:** Contest shares the same ranking/entry backend and shows the personalized preview, with copy voting message and save PNG export; People discovery remains separately reachable.
- **Web/Cloudflare:** /contest/{contest_uuid}/{entry_uuid} serves an approved-contestant invitation with Open BLINK to vote, APK download and copyable URL. Installation via APK cannot reliably reopen an app automatically; the page retains the link and provides an explicit post-install Open action.
- **Release hold:** No production migration, live voting, link-verification guarantee, deployed website or merged PR is claimed. Backend fixture, Android+Windows compilation, security/permissions, route and device testing must pass before main.
## 2026-10-28 — Create Post reference design and interaction polish (PR #639)

- **Android reference flow:** Repaired missing `@Composable` on poll renderer; all Create Post stages now use a short 210ms Crossfade, preserving the existing validated publish path. System Back from final Post Options returns to Post Details, then to the editor; media selection Back returns to entry without erasing the draft.
- **Gallery:** Uses the Android system media picker for permission-respecting device selection, then a real in-app selected-photo grid with counts, fullscreen image preview, ordered cover, remove/reorder controls and add-more. A fully custom preselection device-wide gallery cannot be claimed without access/consent to local photos. The original native editors and image upload pipeline remain intact.
- **Editor visual polish:** Added a clearer Reel editor header while retaining real Media3 filmstrip/timeline/effects; fullscreen photo editor now has consistent BLINK purple accent controls. Poll editor now shows option count, duplicates inline and clear expiry wording. Mention suggestions show loading and no-match states without synthetic profiles.
- **Preview:** Expanded the white post preview to full-screen, with actual author avatar/name, large horizontally scrolling real images, and an explicit unpublished-draft status. Do not fabricate likes, followers or successful post URLs.
- **Visual QA:** The Android runtime smoke workflow now archives seven genuine emulator-rendered screenshots: landing, selected gallery, text actions, poll, video-clip review, post details and final options. These are useful comparison fixtures, **not proof of a pixel-perfect match**; no original reference screenshots were checked into the repo for automated pixel-diff comparison. Editors need their own authenticated screenshots and two-theme regression checks.
- **Accessibility/performance:** Media reorder actions expose content descriptions and use bounded 10-image selection; 210ms fade avoids long blocking transitions. Test on compact/large font and with IME raised before accepting exact reference parity.
- **Scope limit:** Current Android Photo Picker appearance is OS-owned; Windows media composition and poll design parity remain outstanding. `Only me` stays disabled until owner-restricted feed and media authorization is proven.

## 2026-10-28 — Advanced Create Post: clip montage, timed polls, private-audience hold (PR #639)

- **Android video flow:** Pick up to four real video clips, reorder/remove them, and render a single playable MP4 using Media3 Transformer. Retain the one-Reel `video_url` contract and export progress/error state; enforce a 180-second combined duration and preserve originals on failed exports. Multi-clip selection is not a backend array pretending to play as one movie.
- **Android polls:** Existing `polls.expires_at` receives the selected 1-hour, 1-day, 3-day or 7-day duration; created and scheduled poll timestamps are stored consistently. `PostPoll` reads the expiry and the existing authenticated client checks for expiry before attempting a vote. Account-scoped drafts retain the duration. **Server expiry enforcement is now deployed:** migration `20261028200903_blink_poll_vote_expiration_guard_20261028.sql` installed an enabled security-invoker trigger on `poll_votes`; an isolated PostgreSQL regression tests accepted and rejected votes. Real-account end-to-end checks remain outstanding.
- **Private visibility:** Read-only production inspection showed `feed_posts_select_active` permits every active post regardless of audience. Therefore `Only me` is **disabled**, with an explicit UI explanation. RLS, media storage, definer RPCs, Search, profile, push and cross-device verification remain a release-blocking backend requirement; see `docs/integration/blink-only-me-audience-security-staging-20261028.md`. Never represent the disabled option as private published posting.
- **PARITY-EXCEPTION: android-media3-clip-montage-20261028** — Media3's Android-only Transformer and Android Photo Picker are OS-specific native adapters. Windows can use its existing wide-screen composer and desktop file pickers, but Windows video montage, poll duration authoring, and the security-tested private audience are **not** implemented; desktop product-feature parity is outstanding and must be addressed before declaring this scope fully complete.
- **QA / promotion:** Check exact current-head Android unit/build/emulator MP4 export and poll persistence, Windows quality and authenticated backend role tests. No paid hosted staging is authorized. PR remains draft; do not merge or deploy privacy/poll SQL to live Supabase on CI green alone.

## 2026-10-28 — Reference Create Post screens (Testlab PR #639; release hold)

- **Android visual flow:** New full-screen black Create Post chooser, genuine saved-draft thumbnails, custom review grid of selected real images, screenshot-style seven-icon text/media/tag/mention toolbar, vertical backend-ranked @mentions, persisted recent tags, centered purple Next, dedicated Post Details with real visibility/comment settings, white post preview, and final Post Options view. The previous composer is preserved through Classic composer and More tools; photos, polls, Reels, editing, scheduling, drafts, upload, and existing submission logic remain authoritative. Cancelling native media selection or declining a destructive media switch preserves prior selections.
- **Shared data and privacy contract:** Unchanged. No new post types, Supabase writes, visibility policy, or public URL behavior are fabricated. Android native Photo Picker is used instead of an app-owned screenshot gallery, followed by an app-owned selected-photo review grid; the existing Reel editor handles selected video. WhatsApp/other-app share before posting sends selected media plus caption where available (or text-only), never an unpublished post URL.
- **PARITY-EXCEPTION: android-create-post-reference-chooser-20261028** — The supplied reference is a 10-panel portrait phone layout using Android's system Photo Picker, touch-sized modal screens, insets and device media editors. Windows has no Android Photo Picker or phone status/navigation insets; its nearest equivalent is the existing wide-window Home feed composer with text/hashtags/mentions/audience, category and native posting. This exception covers **visual/navigation presentation only**, not missing media publishing capabilities or a claim of desktop feature completion.
- **Windows desktop:** The existing native desktop text composer, hashtag discovery and @mention search are retained without regression. **Open parity gap:** Desktop currently does not implement Android's photo/video/poll upload composer or these phone-sized multi-step visuals. Media editing is Android-specific, but user-facing media-post capability parity is not met and requires a separate verified Windows implementation before this can be called product-complete.
- **Release hold and exactness:** PR is draft and confined to a Testlab-prefixed branch. Do not merge to main until the latest SHA passes Android unit/build/runtime screenshot and UI interaction tests, Windows quality/parity assessment, safe publish/draft/preview checks, and an actual side-by-side pixel review against the ten-panel image. This PR does **not** claim that all ten panels are pixel-identical: Android's system gallery and existing media editors differ from the generated illustration.

## 2026-10-28 — Contact sync opt-in, no-SMS profile numbers (Testlab)

- **Android:** Removed in-app SMS code and phone-verification controls from onboarding and Find Friends. New optional signup number is normalized and saved only to the authenticated user's profile. Visibility defaults to PRIVATE; the owner can change it later in Edit Profile.
- **Consent:** Once Home is idle following login or onboarding, BLINK offers an optional contact-sync and join-alert invitation, with Android READ_CONTACTS permission requested only on explicit acceptance. Not now triggers a reminder no sooner than 14 days. Returning opted-in users may resync under existing permission. Backend errors never erase a previous contact list.
- **Production hold:** Read-only Supabase inspection confirmed NO live contact settings tables, matching RPC or deployed `blink-contact-discovery` Edge Function on 2026-10-28. The prompt checks backend health before requesting access. The existing staged HMAC matching design requires independently verified phone ownership; no-SMS self-declared numbers cannot be treated as identity proof. A reviewed safe replacement and deployment are required before joined notifications can be claimed to work.
- **PARITY-EXCEPTION: android-phone-address-book-permission:** Windows has no Android runtime Contacts permission or device API equivalent. Shared server contact consent/discovery must remain governed by the same privacy contract; Windows contact import requires its own OS-specific design. This does not remove or change Windows features.
- **Release gates:** Latest SHA Android/Windows/Supabase CI, permission denial/revocation, signed-in phone save, real contact matching and new-member notification integration must all be verified. No production DB or app publication in this change.

## 2026-10-28 — Full-app audit Search and credential recovery (PR #635)

- **Search:** Android and Windows both expose a 30-day-first trending-tags entry point and retain truthful seven-day fallback until the new authenticated RPC is deployed. No fabricated trends or creators. The staged backend excludes expired verification badges.
- **Android recovery security:** New password and confirmation are transient Compose state, never encrypted long-lived draft fields. Legacy encrypted password-reset draft keys are actively cleared without reading the saved values.
- **PARITY-EXCEPTION: android-password-reset-deep-link:** Android OS intent dispatch now sends both `blink://auth/reset-password` and legacy `blink://reset-password` to its dedicated token-verifying reset screen. Windows does not implement Android intent filters and must use its own authorized browser/desktop recovery callback. No shared account, password, session, or backend permission semantics were changed.
- **Validation gate:** Staged SQL fixture, source regressions and Android recovery-link Robolectric tests have been added. Independent PostgreSQL role/privacy integration, latest-head Android/Windows CI and end-to-end recovery email/device verification remain required. Main and hosted Supabase are untouched.

## 2026-10-28 — Android-only touch feedback and auth movement (Testlab PR #637)

- PARITY-EXCEPTION: premium-android-touch-motion — Android's touch-down press scaling, Android system animator-duration preference, native auth-route frame transitions and Reels tab selection are platform-level motion, not business features. They add no new data or cross-platform behavior and intentionally do not alter server/shared contracts.
- **Windows equivalent:** Desktop continues to use its existing keyboard/mouse activation, focus/hover state and desktop-native navigation in `desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt` and the desktop auth UI. A touchscreen-like spring on Windows would be a regression for pointer/keyboard UX; no visual-replica claim is made. Future desktop motion polish requires separate native UX validation.
- **Validation hold:** Android build, unit tests, intro gate, Windows parity gate and smoke checks must pass; this note is not permission to merge with failing or unverified checks.

## 2026-10-27 — Notification reliability Testlab PR #622

- Shared notification timeline policy preserves unrelated legacy items and matches exact official announcement mirrors. A regression test covers event matching and unread badge exclusion.
- Android repairs canonical announcement routing, duplicate navigation, notification-only refresh, history pagination, cross-account preference storage and compact card readability.
- Windows now uses the shared event-matching policy for canonical/legacy notification merging.
- OS-only differences: Android Firebase channels, Android 15 permissions and touch swipes do not directly apply to Windows.
- **Not yet release-verified:** CI, Windows packaging, Android emulator, real Firebase/device push and hosted Supabase integration. Keep main unchanged until verification.

## 2026-10-27 — Algorithm integrity Testlab (not production-approved)

- **Shared backend contract (Android + Windows):** Revoke BLINK-owner overrides that bypass block/mute/Following rules and the effectively unbounded Feed score; filter private creators from other viewers' ranked discovery. Both clients call the same server-side Feed and wallet contract.
- **Content views:** Suppress repeated server-side view events within 60 seconds per viewer and content, preserving the existing normal/Blue/Gold weighting. This is abuse throttling, **not** proof of a full 60-second dwell; a separate qualified-watch session protocol remains necessary.
- **Reels:** Restore missing server-side session and aggregation RPCs used by Android, ignore client-supplied completion/rewatch booleans, and limit rapid per-viewer repeated samples. The Windows client must also use the same RPC if it reports Reel viewing events; no Windows-only award logic is introduced.
- **Rewards:** Only a Google-signed AdMob SSV event can settle rewarded coins. The Edge worker invokes the server-only settle RPC; Android's existing pending-claim retry behavior is retained. No cash price, milestones, existing balance, verification or HP progression changes.
- **Validation:** Draft Testlab branch only until PostgreSQL runtime tests, backend/Edge integration, Android and Windows parity checks and release gates pass.
- **Main reconciliation:** Branch includes the latest reviewed main baseline (03d62c17) without replacing the concurrent Feed, UI, or desktop fixes. New merged-tree SHA must pass its own complete CI checks. The live database was read-only during this work. Rollback requires restoring backed-up RPC definitions and handling pending signed callbacks without dropping credited ledger rows.

## 2026-10-27 — Server-ranked 30-day Search discovery (Testlab, not live)

- **Shared:** `BlinkTrendingDiscovery`, `BlinkTrendingTags` and `BlinkTrendingCreator` define a common authenticated server-originated 30-day ranking contract. No device may calculate "top users" from incomplete cached posts or use unrelated referral recommendations as a substitute. Shared JVM tests cover honest window labelling and invalid creator rejection.
- **Android:** Reference Search now requests five backend 30-day tags and three backend-ranked creators; displays the 30-day label only after the new RPC responds successfully, otherwise preserves the existing 7-day backend results with the correct label. Creator follow/profile routing reuses existing trusted actions. Missing creator RPC displays no fabricated leaderboard.
- **Windows:** The same RPC names, limits and profile fields are used by DesktopSearchPhase3Repository and the Search landing; fallback is labelled 7 days. The Windows screen keeps the local loaded-post hashtag preview explicitly labelled as such when server trends are unavailable.
- **Backend proposal ONLY:** `docs/integration/blink-search-30day-trends-staging-20261027.sql` creates two restricted aggregate RPCs using only public, unflagged, active posts; muted/blocked visibility and auth are guarded; private profile phone/email is never returned. It is not a numbered migration and has not been deployed. Before rollout: isolated PostgreSQL SQL syntax and role/RLS tests, 30-day boundary data, blocked users, anon denial, indexes/query plans, rollback and old-client compatibility. Generate a versioned migration only after these checks succeed in Testlab.
- **Release hold:** No claim of 30-day production availability until SQL is safely deployed and Android + Windows real signed-in accounts return consistent results. Separately owner-managed R2, email/SMS/contact activation and GitHub workflow PRs are untouched.

## 2026-10-27 — BLINK incomplete-UI stabilization (Testlab PR #618)

- **Contact discovery:** Android and Windows now fail closed when the optional matching Edge Function is unavailable, expose a real retry action, and keep previously displayed contact matches when a settings mutation fails. Windows settings-only responses do not erase previously fetched matches. Android displays a zero-match message only after a completed sync; small-screen buttons stack rather than overflow. Raw contacts and call logs are never added to Windows or sent without the user's explicit action.
- **Market buyer orders:** Both Android and Windows now display honest loading, empty, reload/error and expanded history states, with account-scoped state and a submit-once dispute dialog. Rejected requests retain the explanation for correction; successful requests get normal confirmation rather than an error-colored message. Neither client claims escrow or refunds.
- **Channels:** Android and Windows fetch private signed attachment links only with existing authenticated clients; unavailable/expired signed links show a retry action instead of an indefinitely loading or inert button. No Storage policy, media contract, forwarding or permissions changed.
- **Search and Connect:** Android's Search landing now supports existing server cursor `loadMore()` and additional local results, with larger touch targets. Windows already has separate desktop cursor-based discovery/search and live-feed pagination; Android's compact reference landing has no Windows analogue. Android Connect's See all and Back now reveal all currently loaded campus updates, matching the Windows loaded-post reveal (which was also raised from an arbitrary eight-item cap). Both surfaces still require new server pages for beyond-loaded results.
- **Scope and limits:** No backend deployment, SMS setup, paid service, production data mutation, signed APK, pixel-exact screenshot validation or R2 changes. Current-feature visual alignment across form factors is pending real-client verification. Do not merge only on the strength of this document; require all applicable checks on the latest SHA.

Messages & Chat route and inbox professionalism repair (Testlab candidate)

- **Shared experience:** Conversation actions are now bound from one Android action factory for both Messages-tab chats and chats opened through Feed, Profile or Connect. Forwarding, replies, message editing, deletion, mute, moderation, pinned/starred state and requests preserve the same server handlers. No RPC, Storage bucket or permission changes.
- **Android:** Outside-tab chat passes real attachment/photo-album/view-once, cancel/retry and loading handlers rather than default no-op placeholders. Calls are discoverable in the chat header; the selected message appearance controls inbox/chats; search spans all folders; folder chips are exposed; message composer and bubble typography are more readable. Previously working delivery receipt/realtime/offline behavior is unchanged.
- **Windows:** Default inbox shows all categories and exposes filter chips on initial load, and searching finds messages across archived/request/primary folders regardless of active filter. The desktop app already has native message actions and desktop-style controls; Android's window/insets and OS media-picker routing are platform-specific UI and have no Windows equivalent.
- **Next increment:** Android video message bubbles now use opt-in Media3 playback inside BLINK (player allocated only after tapping; released on disposal; stops when backgrounded), with buffering/retry and an explicit external-app fallback on error. Existing server media URLs and security boundaries remain unchanged. Quoted replies can load older pages and highlight their original message; reaction badges are actionable. Android and Windows render safe, backwards-compatible contact cards through one shared text parser; Android save/dial actions require an explicit tap, Windows offers Copy Number. Shared JUnit policy tests cover parsing and newline sanitization. Native Windows video playback is not part of this increment; keep the existing desktop video handling until a media-player integration passes cross-platform tests.
- **Search and poll UX increment:** Both Android and Windows now offer an explicitly user-triggered Search Older action using existing authorized message pagination instead of silently scanning large histories. Both render existing validated text-format polls as option cards; tapping an option only prepares a quoted reply, and neither platform displays fictitious native votes or vote counts. Existing drafts are never replaced by these actions. The Android inbox fixture now asserts the real built-in new-message picker instead of an unused legacy callback.
- **Native backend hold:** Read-only production schema inspection on 2026-10-27 verified that native chat poll tables and voting RPCs are absent. The SQL under `docs/integration/blink-chat-native-polls-staging-20261027.sql` remains a staging design ONLY. Do not deploy it or enable native vote-count UX until isolated owner/member/blocked/departed/anon role tests pass and Android+Windows end-to-end acceptance is verified. Similarly, existing Items live-location RPCs are not chat-location authorization and must not be reused for chat tracking. An expiring, explicit-consent chat sharing design needs isolated privacy/security review before activation.
- **Verification hold:** Android unit/assemble and the inbox visual instrumentation test, Windows compilation, reply/forward/media integration tests, 320dp and large-font Android screenshots, low-end gesture/device acceptance, and real account delivery receipts remain required. No build, merge or signed APK publication is claimed by this candidate entry.

## 2026-10-27 — ## 2026-10-27 — Reels eight-point completion repairs (Testlab, final head verification required)

- **Discovery tabs:** `Reels` now presents latest-first content by canonical `FeedPost.createdAt`; `For You` retains the backend's supplied recommendation order, and `Following` remains restricted to the followed source. This is a client-side presentation distinction, not a fabricated personalized model or a new backend RPC.
- **Stories:** The Reels preview shows a real story strip with a creator's uploaded-story ring only when media exists; the new-story control and story viewer reuse existing authenticated host callbacks. No synthetic story rows, storage, profile data, or backend contracts.
- **Responsive UI and animation:** The header reserves story and optional featured-banner height, reduces featured-card height for smaller Android screens and hides the featured banner on very short viewports. Loading placeholders reserve the same story space. Existing preview/fullscreen spring and velocity-qualified gestures remain; no OS-level gesture hijack.
- **Playback and resource use:** Only current/next Reel decoders are active when expanded; preview mode does not eagerly prewarm the next video. Completed inline previews release their Media3 player while preserving resume position. Progress sampling is throttled from 100ms to 200ms to reduce UI work on low-memory devices.
- **Recovery:** Media3 temporary network failures are eligible for bounded retries, while missing/private or unsupported videos do not repeatedly consume data. Actionable error messages distinguish inaccessible videos, unsupported formats, and network errors. Existing cached media and authenticated fetch policy are preserved.
- **Tests:** Added tests for feed ordering separation, story creation/viewer routing, automatic retry policy and missing-video messaging, plus existing gesture, completion and preview UI cases.
- **Parity exception:** Story-strip arrangement, responsive Android device dimensions, touchscreen gestures, local Media3 decoder policy and Android Compose animations apply only to the APK. Windows currently lacks a native interactive fullscreen Reel player and uses the same Supabase source and shared entity rules; no new Reel data/API entitlement, sensitive permission, or Windows-specific backend path was introduced. The desktop native Reel/player adaptation is a separate outstanding parity task, not silently claimed complete.
- **Release hold:** Commit code is in the Testlab PR only. Latest-commit Android unit, lint/build, emulator UI/runtime, Windows/parity and signed release gates must pass. Exact phone screenshot fidelity and physical-device gesture smoothness are not established solely by repository changes. No paid device farm or paid Supabase staging has been initiated.

## 2026-10-27 — Reels usability, playback accuracy and preview polish (Testlab candidate)

- **Android:** Keep the existing Reels pager/player mounted while changing preview/fullscreen chrome; use reel IDs rather than list positions as pager keys so newly arriving content cannot unnecessarily remount existing videos. Correct active-tab loading feedback, visible refresh and retry/create actions, and server offline/error presentation. Do not show the empty terminal screen before asynchronously loaded featured Reels can be fetched.
- **Viewing signals:** Completion now requires actual accumulated playing time, not seeking to the end or scrubbing backward. Existing recommendation RPC parameters, view counting backend, permissions and media URLs remain unchanged; this is client-side reliability work. New unit regression coverage checks the 90% watched-time threshold.
- **Preview design:** Reduce unnecessary featured-reel polling, omit the large banner when nothing is featured, use consistent BLINK wordmark and calmer monochrome banner styling; ensure header actions have 48dp targets and a clearly labeled Refresh control.
- **Windows parity / PARITY-EXCEPTION: android-media3-reels-touch-navigation:** The Android Reel screen uses Android's Media3 ExoPlayer, on-device vertical pager, touch gestures, status/navigation bars and Android Compose accessibility chrome. Windows uses desktop navigation, keyboard/mouse interactions and its separate playback surfaces; Android-only gesture layouts and touch dimensions do not apply. No new reel data schema, user action, feature entitlement or shared backend behavior has been added. Existing Windows reel discovery, playback and server-side recommendations remain unchanged.
- **Navigation/recovery:** Deep-linked videos now suppress unrelated autoplay while waiting for the requested item, expose retry/browse recovery after a bounded lookup, and route featured entries from every tab through the same target lookup. An explicit top drag handle collapses fullscreen from any page without stealing previous/next Reel paging; quick horizontal navigation requires a velocity- and distance-qualified gesture. Inactive/covered video actions cannot trigger likes or pause.
- **Preview creation/playback:** A dedicated compact Create Reel control routes to the existing composer. Video playback reports network versus format errors and clears stale buffering indicators.
- **Verification hold:** This is an unmerged Testlab candidate. Run current-head Android compile/unit/instrumentation and Windows parity checks; screenshot/gesture testing on Android 15 and low-end devices is still necessary before production promotion. The PR has no Supabase migration or production deployment.

## 2026-10-28 — Included active verified signature (PR #629, Testlab)

- **Shared policy:** `BlinkVerifiedIdentitySignature.label` emits a branded Blue/Gold member signature only when active, no identity-vetting claim, no paid Store cosmetic inventory grant.
- **Android/Windows:** Both profile headers render the same subscription-derived signature with platform-native compact styling. Existing purchased rings/banners, creator cosmetics, owner privacy, and payment/renewal contracts remain unchanged.
- **Test:** Shared Kotlin regression verifies expiry and unverified users never receive the signature. Live device visual verification and current-head full builds remain outstanding.

## 2026-10-28 — Gold Channel Pro and creator workspace parity (PR #629, Testlab)

- **Shared Kotlin:** `BlinkVerifiedChannelPro` computes only loaded update stats (no private followers or fabricated lifetime metrics), validates future UTC schedule presets and produces editable Channel templates. `BlinkVerifiedCreatorTools` creates bounded AI briefs, public portfolio copy and confirmed-data event invitations.
- **Android:** Active Gold Channel admins can view loaded-update Pro stats and prefill Channel drafts/scheduled UTC time. Blue/Gold owners can open existing BLINK AI with a prefilled prompt, never auto-send. Gold Creator Studio adds portfolio sharing and event invitation sharing through Android share chooser.
- **Windows:** Same shared Channel Pro stats, template and schedule prefill. Creator Studio opens the existing Windows BLINK AI with prefilled text only. Gold public portfolio/event copy uses the Windows clipboard. No synthetic backend records or new privileges.
- **Safety:** Existing Channel role permissions, paid Store ownership, post scheduling, subscription prices, Coins/HP, boost pricing, XP and discovery rank remain unchanged. Latest-head unit/build/smoke tests required before main. Pending Gold credits, premium queue SLAs and VIP cosmetic grants must NOT be advertised as delivered entitlements.

## 2026-10-28 — Gold Merchant Pro listing-health diagnostics (Testlab PR #629)

- **Shared-first:** `BlinkMerchantInsightsPolicy` provides deduplicated, read-only listing health from the user's own loaded Market items, with nullable unknown quantities/view/save counts. No new database writes, RLS, payments or automatic promotion.
- **Android:** Adds a Gold Merchant Pro card within the existing approved-seller dashboard for active Gold accounts. The card highlights low-stock items, missing listing photos, most viewed loaded items and represented categories; tapping a listing opens its existing product detail.
- **Windows:** Adds the same Gold Merchant Pro diagnostic panel beside the seller dashboard; desktop parses optional quantity/views/saves fields when present rather than fabricating values. Dashboard and seller approval remain unchanged.
- **Limitations:** Marketplace search, ranking filters and pagination may hide other seller listings. These figures are a *loaded-listing snapshot*, not shop-wide inventory, revenue, lifetime performance or reach guarantees. Full catalog-wide insights and merchant inbox tools remain roadmap items.
- **Tests:** Shared Kotlin unit tests cover deduplication, sold-stock exclusion, unavailable values, and no-image flags. Require fresh latest-head Android + Windows build and device checks before merging.

## 2026-10-27 — Verified creator membership value (Testlab PR #629, not merged)

- **Shared-first:** `BlinkCreatorStudioPolicy` computes unique loaded post/Reel aggregates, honest qualified views, interaction totals, top content and top tags. Both clients pass their real post models through thin adapters. No new backend/RLS/checkout paths.
- **Android:** Creator Studio on owner's profile: free baseline counters; active Blue content analytics; active Gold hashtags and Android share intent for performance reports. Get Verified and Wallet describe actually implemented benefits, not speculative reach.
- **Windows:** Creator Studio on owner's desktop profile with matching shared counts, Blue performance and Gold top tags/report clipboard export. Desktop post API currently omits aggregate saves, so saves are shown as unavailable instead of fabricated. Desktop tier resolves the canonical `verification_badge` field with legacy `verification_tier` fallback.
- **Parity limitation:** Android uses currently loaded profile posts, Windows loads a paginated profile content list, so snapshot sizes may differ. Neither platform calls snapshots lifetime/unique audience analytics. Desktop requires the server-reported verified flag plus a Blue/Gold tier; production should be retested for expiry-state propagation.
- **Testlab hold:** Android unit/lint/assemble, Windows build and user-flows must pass on the latest PR head. Unfinished items (Channel Pro, Merchant Pro, Gold promotions/AI and advanced scheduling) are roadmap proposals, NOT subscriber entitlements in this PR.

## 2026-10-27 — WebRTC Android 15 JNI call-crash repair (passed Testlab, main promotion pending)

- **Observed failure:** Redmi/Android 15 SIGABRT during `libjingle_peerconnection_so.so` `JNI_OnLoad` inside `PeerConnectionFactory.initialize`; this is a native Java-class lookup failure and cannot be recovered by catching Kotlin exceptions.
- **Android:** Preserve existing `org.webrtc.**` JNI keep rules, upgrade the pinned native/Java WebRTC AAR together from 150.7871.01 to 150.7871.04, only mark factory initialization successful after it returns, and log/handle recoverable Java/native linkage errors without hiding fatal JNI failures.
- **Release verification:** Added `rtcSmoke` with R8/resource shrinking and the production keep file, an Android 15 instrumented JNI load/factory-creation test, and APK native-binary checks for both arm64-v8a and x86_64. Debug-only tests do not validate this crash.
- **PARITY-EXCEPTION: android-webrtc-jni-arm64** — Android-only JNI class loading, ProGuard/R8 rules and APK native packaging have no equivalent in the Windows executable. Windows retains its existing native call-engine initialization and shared BLINK signaling contract.
- **Android audio-route correction:** Core-Telecom exclusively handles speaker/earpiece/Bluetooth endpoint changes on Android 8+, while the legacy WebRTC route is used only below API 26. This avoids competing `setCommunicationDevice` calls and avoids claiming a route switch succeeded when the actual device did not switch. The Telecom endpoint callback remains the source of truth for the visible speaker icon.
- **Windows parity:** WebRTC native Android initialization and APK shrinking are Android-specific; no shared calling model, Supabase signaling, credentials, message contracts, or Windows calling behavior is changed. Desktop's closest equivalent is retaining its existing native calling path; this is a platform-specific exception, not a missing user feature.
- **Hold:** Testlab checks passed for Android unit/build, Windows/parity, security and Android 15 minified JNI initialization/factory creation. Main promotion must preserve concurrent changes, pass combined main-target checks, and verify the signed APK. Live incoming/outgoing voice/video calls on the Redmi ARM64 handset remain to be confirmed.

## 2026-10-27 — Notification push trust and preference safety (Testlab candidate, not deployed)

- **Shared backend only:** Protects Android and Windows notifications equally; neither client changes its UI, API or notification event format.
- **Client authorization:** Activity, direct-message and call FCM dispatchers now all verify sender JWTs with Supabase Auth rather than decoding and trusting unsigned `sub` claims. The service-role activity notification path remains independently restricted to verified referral, Channel and contact events.
- **Call and direct-message parity:** Both Android and Windows rely on these shared server-side push authorization, recipient opt-out and dispatch-ledger semantics; the fix changes no on-device UI or calling/media protocol. Direct-message mute lookup failures are retried instead of overriding a user's preferences. Call push dispatches also fail closed when ledger read/write fails. Temporary TURN relay credentials use the same verified Auth identity rather than relying on a decoded JWT subject or the gateway configuration.
- **Recipient consent:** Failure or non-boolean results from `notification_push_allowed` now return a retryable HTTP 503 rather than sending an unapproved push. Genuine opt-outs remain terminal skips.
- **Deduplication:** An inaccessible notification dispatch ledger also yields a retryable failure instead of sending an untracked, potentially duplicate FCM push.
- **Tests:** Node tests cover unsigned/forged JWTs, auth outages, opt-outs, missing preference results and backend errors; the tests are part of the standard Supabase CI package suite.
- **Release hold:** No production Edge Function deployment, database migration, app merge or APK publication is implied. Isolated CI, real-auth/FCM staging, recipient preference and cross-platform delivery checks must pass before promotion. Existing `main`, unmerged #608/#609/#566 and production user notifications are untouched by this staging work.
- **PARITY-EXCEPTION: contact-provider-null-cursor** — Android alone can read the OS Contacts Provider after READ_CONTACTS consent. Its null-cursor handling now preserves imported contacts instead of deleting them. Windows has no Android Contacts Provider; Windows continues to use the opt-in matching settings and profiles already synced to the shared backend. This does not change matching, consent or data-retention semantics.
- **Contact discovery:** Android's permission-granted Contacts Provider returning a null cursor now shows a retryable error rather than syncing an empty contact list and deleting previously imported HMACs. This is Android-specific system integration; Windows does not read local address books and uses the same server suggestions/consent settings. The Edge Function now reports unavailable matching RPCs as errors rather than misleading zero matches.

## 2026-10-26 — Channels production database foundation ACTIVATED, UI release still held

- **Database verified live:** Supabase BLINK production now contains 6 new channel tables, 21 channel RPC functions and one private `blink-channel-media` Storage bucket, applied through `blink_channels_broadcast_media_polls_20261026` and `blink_channels_explicit_data_api_grants_20261026`. The previously existing Community and membership rows were preserved (one each at verification). Both `authenticated` grants for raw channel updates and private reports remain disabled; exposed reads/writes flow through authorization-gated functions.
- **Test evidence:** PR #601 added a disposable PostgreSQL CI workflow testing owner/moderator/follower/pending/banned and view/reaction/poll permissions; it passed before activation. The PR's Supabase safety gate required an explicit rollback/destructive-operation review and Data API grants; those requirements are incorporated.
- **Do not confuse backend activation with release:** New Android/Windows Channel code is still in the Testlab PR, not merged into `main`. Production backend availability does **not** establish complete WhatsApp-style functionality. Actual FCM fanout/retry/deep links, native in-BLINK forwarding to chats/Channels/Status, fully in-app media player and exact screenshot parity remain to be completed and verified.
- **Release decision:** Keep one consolidated PR in draft until current-head Android+Windows builds and interaction/regression checks pass, including full real user/channel scenarios; then merge `main` without losing concurrent work.

## 2026-10-26 — Channels rich update candidate (PR #601, not production)

- **Android and Windows parity in code:** Media selection/upload to a dedicated private 25 MB channel bucket (images/video/audio/PDF), signed media reads, broadcast polls and votes, external forwarding/copy, mute toggle, scheduled announcement composer/management, deduplicated unique update views, admin analytics, pin/remove controls, follower reporting and administrator moderation inbox.
- **Platform-specific UI:** Android uses an Android permission-scoped content picker and share chooser, image previews, a compact ⋮ overflow menu, chat-style cards and a scroll-to-latest channel view. Windows uses its native file picker, desktop/browser media opening, clipboard-forwarding, and larger-screen layout. Windows inline image preview and both platforms' pixel-level reference matching still require real device screenshots.
- **Backend:** The additive candidates in `docs/integration/blink-channels-broadcast-staging.sql` and `docs/integration/blink-channels-whatsapp-completion-staging.sql` are *documentation-only*, **not deployed and not yet a versioned migration**. They include private storage RLS, time-gated updates, polls, reactions, view tracking, moderated reports, per-user notification mute/read cursors, scheduled updates and admin analytics. They must both be validated against the current production-compatible schema in a free isolated test database before integration; only then create a CLI-generated migration. Production tables have NOT been changed.
- **Incomplete / no release:** Real push-delivery fanout and retries, notification deep-link handling, true native in-app forward-to-channel/chat and Status repost, end-to-end mobile/desktop media and privacy checks, rich gallery/photo/video previews, and screenshot-by-screenshot WhatsApp visual fidelity are not verified. Existing community IDs are intentionally preserved.
- **Hard hold:** Latest-head Android/Windows builds, SQL role and bucket policy tests for owner/moderator/follower/pending/banned/anon, migration dry-run + rollback, large-channel fanout plan and real flows must all pass before the consolidated PR can be merged. No paid Supabase staging branch may be created without approval.

## 2026-10-26 — BLINK Channels broadcast migration (Testlab, NOT released)

- **Product:** Existing `Community` entry is branded `Channels`, retaining legacy `blink_communities` IDs, owner, category, university, invitations, private/public visibility and existing follower data. A follower enters the dedicated channel updates page as soon as an active follow is confirmed. A private request remains pending and must not imply membership.
- **Android:** Channel directory is accessible from Connect and the Messages filter rail. Once a channel opens, Professional Center chrome is hidden; Android Back restores the directory. Admins can compose broadcast updates, followers cannot compose; reactions and counts are server-provided.
- **Windows:** Desktop sidebar and campus discovery use Channels terminology. Channel open/follow and update reader with owner/moderator-only publish and follower reactions mirror Android through the same RPC contract.
- **Shared:** `BlinkChannelPolicy` validates text update length and the allowed emoji set for both clients. `BlinkChannelUpdate` holds only aggregate reaction counts and the viewer's own reaction, not other followers' identities.
- **Backend candidate (NOT deployed):** `docs/integration/blink-channels-broadcast-staging.sql` is an isolated design candidate, NOT a Supabase migration or live schema. It adds update/reaction storage and authenticated role/read/publish/react/count RPCs. Existing community tables are preserved; SQL intentionally never returns follower rosters to regular viewers. Needs CLI-generated versioned migration, separate local/staging RLS tests, and security review before enabling.
- **Known omissions:** This is an initial **text-broadcast slice**, not WhatsApp's complete channel feature set. Media upload, polls/quizzes, history pagination, scheduled updates, push notification preferences, sharing/invitation deep-links, full owner analytics, abuse reporting, moderation operations for updates and exact screenshot/UI comparison remain unimplemented.
- **Release hold:** No staging branch is available and the new backend RPCs do not exist in production. DO NOT merge to `main`, deploy production SQL, or claim channels live until versioned migration, RLS/privacy checks, current-head Android/Windows builds, 320dp and 200%-font screenshots, and real follow→open→post→react flows succeed.

## 2026-10-26 — Rubber swipe engine (Testlab candidate; release not verified)

- **Shared contract:** Added `BlinkElasticGesturePhysics`, a platform-independent, bounded visual resistance and release policy that allows long deliberate drags or short velocity-confirmed flicks; incomplete slow gestures spring back and wrong-direction velocity never triggers actions. Existing navigation routes, data, Coin/XP and server contracts remain unchanged.
- **Android:** Velocity-aware horizontal fallback navigation, chat reply and close, story dismissal, chat photo paging/dismiss, Feed image gallery down-to-dismiss, Market listing-image paging, profile return and Reels preview expand / first-page collapse. Existing five-tab `HorizontalPager` continues to own its native interactive swipes, and child media retains priority. Backend transactions are not attached to these gestures.
- **Windows:** Desktop chat photo viewer accepts mouse/trackpad drag with the same spring, page and dismissal rules; clickable Previous/Next/Close controls remain as accessible alternatives. The five-section desktop shell retains its native navigation instead of requiring touch swipes; that is the appropriate desktop-equivalent platform exception.
- **Web:** No new gesture routing added because its existing pointer interactions are distinct and there is no equivalent native chat photo viewer; preserve browser scroll, back and accessibility controls.
- **Quality gates:** Run shared physics tests, Android JVM/instrumented gesture tests, full affected Android build, Windows UI compilation and manual nested-scroll/media/zoom, reduced-motion, accessibility and low-end-device acceptance. Changes remain isolated in a Testlab-named branch until these gates pass; no production deploy or APK publication is implied.

## 2026-10-26 — Wallet final reward eligibility and reference interactions

- **Android:** The five-page neon Wallet retains the BLINK Coin balance and working [+] shortcut, now scrolling to the Buy Coins packs. Earn Coins/Rewards shows ad #15 as verified-only; expired or unverified users see a locked milestone and a Get Verified route. Other screen affordances and existing 10-Coin base payouts remain unchanged.
- **Windows:** Equivalent 15-ad verified-only display, reward progress and locked milestone navigation. Correct wallet copy explicitly permits user-to-user Coin gifting.
- **Supabase:** The `get_blink_economy_status`, `begin_blink_rewarded_ad_claim` and `complete_blink_rewarded_ad_claim` functions re-evaluate Blue/Gold verification and its expiration to block old APK clients from earning ad #11–15 without active verification. No XP/HP level formula, Coin gift RPC, transaction rate or existing user wallet data changes.
- **Tests:** New disposable PostgreSQL integration with fake accounts checks 10-vs-15 caps, expiration and preserved ad rewards. Android unit tests cover original cumulative milestone totals. Windows AdMob playback remains intentionally unavailable (Android-only SDK).
- **Staging/release:** Do not deploy the migration or merge until full CI success. See screenshot references from this wallet conversation for strict final visual acceptance; passing code tests alone does not establish pixel-perfect UI parity.

## 2026-10-26 — Premium Profile, Status and Reels (PR #581 integration)

- **Android:** Owner-visible six profile themes, exact-reference-inspired profile cards/grid, three-dot Edit menu, story viewer rail and immersive Reels controls. A functional in-app Reel editor now offers trim, filmstrip, grayscale, rotation, text, emoji and audio using Media3 export; no mock-only trimming is shipped. Pixel-by-pixel reference equivalence still requires actual screenshot review.
- **Windows:** Reads public owner theme and persists theme edits through the existing owner-only profile policy.
- **Web:** Displays the public owner palette independently of visitor theme and offers an owner theme selector. Dedicated privacy-gated public-theme RPC exposes only a color ID for publicly viewable profiles.
- **Database:** Production additive `profile_theme` schema and six allowed values applied on October 26, 2026. Public web theme RPC applied and grants/RLS constraints reviewed; no paid Supabase staging branch was created. App-level cross-platform tests and media export checks must pass before release.
- **Preservation:** Integrate without losing market-only Feed protections, universal photo editor, coins/wallet, referral work or messaging improvements merged to main from concurrent PRs.

## 2026-10-26 — Market listings are not ordinary Feed posts (Testlab candidate)

- **Shared rule:** Android and Windows use `BlinkPostGrowthTips.marketOnlyReason` for prohibited Market categories and obvious text/alt-description product advertisements. Legitimate student discussions remain publishable.
- **Android:** Create Post explains that product sales belong in Market, flags likely sales copy before publishing, and offers an explicit **Create a Market listing** action that closes the composer and selects Market. Immediate and scheduled publishing repeat validation in the ViewModel.
- **Windows:** The Create Post area contains the same market-only message, highlights blocked sales copy, and refuses prohibited posts using the shared rule. Users navigate to Market via the desktop tab.
- **Database candidate:** Existing production text-only trigger is not sufficient. Staged SQL adds category, caption, alt-text, sponsored impersonation, and UPDATE checks. It is deliberately NOT applied to production until isolated role/RLS, paid-sponsor, legacy-client and rollback tests pass.
- **Limitations:** Image pixels with no readable description cannot be classified reliably from client-side strings. This requires a separately reviewed server-side visual moderation workflow and user reports. Do not describe this restriction as fully enforced in production until hosted integration is verified.
- **Release gates:** shared JVM tests, Android quality/runtime, Windows desktop build, PGlite SQL role tests, marketplace paid placements, and signed APK verification are mandatory before promotion.

## 2026-10-26 — Staged universal link previews (NOT production verified)

- **Shared contract:** `BlinkRichLinks` recognizes safe HTTP(S) URLs and distinguishes actual BLINK domains from impostor names. Android and Windows reuse this policy.
- **Android:** The same async, cached rich-link card appears in chat (honouring the existing chat toggle), Feed, comments, Reel captions, Story captions, Market item details, Connect bios and public profile website cards. No remote scripts or embedded autoplay.
- **Windows:** Equivalent rich cards are provided in chat, Feed, comments, Reels and profile biographies. There is no desktop chat-preview preference UI or synced Android toggle yet; this MUST be reconciled before shipping full preference parity.
- **Web:** HTML-escaped initial URL cards across Feed, comments, Reels, Stories and messages. Cards are upgraded lazily from read-only metadata via `textContent`, never injected remote HTML.
- **Backend staging only:** New HTTPS-only allowlisted `link-metadata` Edge Function, 3.5-second timeout, 128 KiB page cap, no redirect/cookie/authorization forwarding, bounded in-memory cache, safe host-only fallback on unsupported domains. Public BLINK share metadata reuses existing privacy-filtered `share-preview` endpoint; fixes placeholder canonical domain and profile link routing.
- **Limitations:** Up to three distinct HTTP(S) links in each text surface receive cards; dedicated Feed/Reel link_url fields are included. Android, Windows and web share the stored chat preview toggle, but its required production user_chat_privacy table/RPC are still absent and must be deployed safely. Arbitrary/untrusted host metadata and in-app iframe/video playback are intentionally disabled; host cards remain clickable. End-to-end deployed rich metadata, visibility filtering, large-font layout, network tests, private chat settings parity, Android builds and Windows packaging remain UNVERIFIED. No actual deployment, merge to main or release has occurred.
- **Release hold:** A safe free staging environment must verify both Edge Functions before production, and current-head Android/Windows/web checks and visual device tests must pass.

## 2026-10-26 — Five-screen premium Coins & Wallet (original economy restored)

- **Android:** Tapping any part of the Home feed Coin card or owner profile Coin card opens the same Wallet. Its five dark-neon screens are Coins & Wallet, Earn Coins, Rewards, Get Verified, and History. Return navigation honors the origin.
- **Windows:** Five wallet destinations with authenticated Coin pack offers, verified account balance, daily missions, verification and history. Desktop Coin display navigates into Wallet.
- **Unchanged economy:** Preserve the existing 10 base Coins per rewarded ad, server-configured 15-ad daily limit, cumulative 5/10/15 milestones (60/130/210), Coin packs and 3,000-Coin Blue Verification. Do not alter SQL functions or ad entitlement algorithms in this UI PR. The server is authoritative for all rewards.
- **Coin gifting stays:** Profile Interact retains the original authenticated, idempotent 20/50/100 Coin gifting using `gift_blink_coins_once`. Existing paid Coin gifts and provider rules also remain unchanged. Gift recipients get Coins; the giver's wallet is debited only when the server confirms the transfer.
- **Daily missions award both Coins and progression XP:** The original `claim_daily_mission` RPC supplies the authoritative `coinReward` and `xpReward`. UI describes XP as level XP (HP), without adding a new balance, changing rates, or allowing users to transfer XP/HP. Claims are server-idempotent.
- **PARITY-EXCEPTION: windows-rewarded-ad-playback-wallet-20261026** — AdMob playback remains Android-only; Windows shows server-backed progress/rewards and does not fabricate advertisement completion.
- **Release gate:** Check Android/Windows builds, wallet navigation, Coin transfer regression, missions granting both Coin and XP, checkout webhook and status, accessibility, screenshot fidelity and full CI before merging. No production migration or account balance changes in this branch.

## 2026-10-26 — Shared BLINK photo editing and viewer integration (Testlab; not released)

- **Android:** Kept the system's privacy-scoped Photo Picker instead of requesting unrestricted gallery access. Added one shared full-screen BLINK editor to Chat photo review, Feed post attachments, Stories, signup avatar and Profile avatar/cover after the existing avatar/cover crop. Its tools crop to square/wide/portrait, rotate/mirror, adjust exposure/contrast/saturation, draw freehand, add emoji/text overlays and save a new photo without overwriting the original. View-once media remains excluded. Normal chat photos and Shared Media history open the same black photo viewer with caption/reply/reaction, paging, pinch zoom, download cancel/progress, starred/forward/delete actions, editing a downloaded copy and explicit profile picture/wallpaper actions.
- **Windows:** Native JFileChooser is retained (no Android Photo Picker on desktop). Both chat photo albums and Market product-photo selection can open the local non-destructive desktop editor (crop/rotate/mirror/adjust/text/sticker). A normal-chat photo viewer supports image, caption, page navigation, cancellable local save and user-controlled reply/forward/star/delete. View-once photos remain the memory-only private viewer.
- **Web:** Browser-scoped file selection remains the privacy-safe gallery analogue. A BLINK Canvas editor provides crop, rotate, live exposure/contrast/saturation, draw, text, emoji and explicit export of a new JPEG before creating posts, stories, avatar and cover uploads. GIF and potentially animated WebP are not flattened; those uploads use their original media. Web messaging currently supports text but does not claim the Android native image message viewer is available there.
- **PARITY-EXCEPTION: desktop-web-android-media-picker-20261026** — The OS-native Android Photo Picker, desktop file chooser and browser file picker have different permission prompts; the same selected-file-to-editor-to-upload workflow is implemented where each platform currently supports picture upload. Exact system picker chrome cannot be controlled without invasive photo permissions and is intentionally not replicated.
- **Release hold:** Visual side-by-side reference/device verification and Android–Windows–Web end-to-end media tests are still required. Supabase RPC migrations are tested only in disposable PostgreSQL and are not yet deployed to production. Do not merge this PR or publish an APK until all required checks and privacy tests pass.

## 2026-10-26 — Twelve-hour top Reels carousel and compact receipts (Testlab only)

- **Backend (migration staged, NOT deployed):** `blink_featured_reels_12h()` ranks the five highest eligible public Reels by protected view events in the last *completed* 12-hour UTC period. Access requires authentication, filters private/blocked/flagged/expired Reels, and does not expose viewers. The cohort automatically changes on the 00:00/12:00 UTC boundaries; active clients poll without requiring a paid scheduler.
- **Android:** Reels preview replaces its Story circle row with a photo/creator/caption/views horizontal banner, 5-second automatic paging, and tap-through to the existing vertical reel player. Other Stories surfaces are untouched. New data are loaded from authenticated RPC; no fabricated trend counts are shown. Chat receipts now use one compact double-check glyph beside the timestamp within the bubble.
- **Windows:** Desktop Reels surfaces the same five server-authoritative items in an auto-advancing, desktop-width carousel. A featured click opens the corresponding BLINK web reel route while native Windows video playback remains unsupported (documented prior parity limitation); no mock video is invented. Existing Windows messaging receipts are functionally the same status states, with desktop-native presentation.
- **Status / release hold:** CI/build and migration runtime tests have not been confirmed at the latest SHA. The new SQL must be validated in an isolated Supabase environment before production. This PR is intentionally draft and cannot be merged or published until integration, platform and security gates pass.

## 2026-10-26 — Android referral input contrast and IME safety (unverified branch)

- **Android:** `BlinkReferrerPicker` can explicitly render high-contrast text and suggestions on the white reference-signup surface instead of inheriting a dark-theme color. The form now reserves IME space, and the first matched referral is brought into the visible scroll viewport when results arrive.
- **Shared/backend:** No changes to referrer identity, signup attribution, referral rewards, or database API. This is a visual/layout repair only.
- **PARITY-EXCEPTION: android-signup-ime-keyboard-suggestion-20261026** — Android's soft keyboard obscures suggestion rows, so Compose IME padding and BringIntoView are Android-only platform APIs. **Windows equivalent:** Desktop's referral picker uses a window-scrolling, mouse-and-keyboard layout with no Android IME, and the same referral rules/server results. Readability and referral semantics are shared, not exempted.
- **Verification required before production:** Android Gradle compilation, Robolectric narrow-screen screenshots and on-device keyboard tests at 100–200% font scaling. Status: code changed; tests have not been run from this session. Do not merge without checks.

## 2026-10-25 — Android touch Reels preview/fullscreen transition (Testlab only)

- **PARITY-EXCEPTION: android-reels-touch-preview-expand-20261025** — Android's requested compact Reels preview enlarges into an immersive vertical, edge-to-edge video player on a tap or gesture. This is a phone-specific interaction built around Android Compose gesture input, the bottom navigation rail and Media3 playback; it does not change the shared reel data, server response or engagement permissions.
- **Windows equivalent / limitation:** `desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt` already provides a dedicated **Reels** navigation destination, reel listing, caption, engagement counts and share actions in desktop window geometry. Its existing viewer displays video availability text rather than an Android-style Media3 player; actual desktop video playback remains a separate, explicitly unimplemented parity improvement. This exception must not be used to claim Windows full-screen video parity.
- **Verification:** Android-specific animation, fast swipes, pause/resume, orientation, font scaling and navigation insets need runtime visual checks. Shared/reel metadata and both platform build gates still apply. No production merge until current-head checks pass.

## 2026-10-25 — Refer & Grow follow-up: four requested capabilities

- **Birthday position and privacy:** Android, Windows and web registration now place Birthday immediately before the optional referrer picker. Android/Windows validate exact YYYY-MM-DD with a strict shared date policy; web uses a date field. Upon authenticated signup, birthday is saved only to `profile_private_details` (not public profile or referral metadata). If email confirmation withholds a session, web requests completion during private profile onboarding rather than persisting sensitive DOB in browser storage.
- **Coin milestones:** A separate additive SQL migration provides configurable milestones (1/5/10/25/50/100), one-time wallet credit, audit ledger and owner-only earnings summaries. Draft amounts: 5/25/50/100/250/500 BLINK Coins. ALL default disabled, because no final owner economics approval or hosted fraud-validation is available. No production Coins granted or DB updated.
- **Source attribution:** A server-controlled actor-authenticated RPC records one public profile open per viewer/profile/source/day, with self/private/blocked checks and hourly limits. Sources are Search and new-student recommendations; only aggregated seven-day counts are displayed to owners in Android, Windows and web. These are navigations, not fabricated total impressions. Android Search and new-member card profile taps and web Search taps record the source; Windows displays analytics but currently has no target-profile navigation from its Search person card.
- **Username selection:** Pure shared username policy + tested trailing-mention replacement, reusable public username autocomplete component for @mentions, and a 30-person group/community selection surface. Android message composer uses the autocomplete. Actual invitations to conversations/communities are NOT sent: current social-app screens need separate secure invite/accept endpoints and group/community membership UI before they can consume the selection. Do not auto-add students.
- **Safety/release:** PostgreSQL-16 disposable integration assertions cover Coin controls/idempotence, source attribution and anti-forgery. Real Supabase referral objects remain undeployed and no staging project exists. Current PR is a draft on `main`, and complete Android+Windows latest-SHA CI, authenticated hosting tests and per-SHA owner approval are still required. The consolidated patch must not be published prematurely.

## 2026-10-25 — BLINK Refer & Grow scoped main integration (PR #540, draft)

- **Shared:** `BlinkReferralPolicy` controls lifetime badge thresholds, 30-day referral boost decay and canonical invitations. `BlinkMenuDestination.REFER_GROW` is identical for Android and Windows. The server owns all credit, ranking and notification records.
- **Android:** Registration includes optional referrer autocomplete; signed-in Search has a referral promo with real count, relevance-first ranked public profile search and new-student suggestions. Refer & Grow is accessible from Profile and Menu. Share and Copy invitation actions have server-throttled metrics. Verified accounts and universities appear in selection. HTTPS invite links from blink.com.ng/www are validated and pre-select the referrer on signup without changing an already signed-in account.
- **Windows:** Equivalent registration selection, search promotion/ranking, My Referrals dashboard, Menu route, profile suggestions, verification labels, and invite-copy/share-action tracking. Desktop cannot claim Android App Link verification: browser handles invite URLs.
- **Web:** Public /invite/username signup preselection, auto-suggest, Search promotion and confirmed-referral counter, real growth and leaderboard details, link sharing actions.
- **Backend (not deployed):** Versioned SQL referral confirmations, no self-referral or duplicate credits, RLS, eligibility/privacy filters, at most 3 featured profiles, 30-day fading visibility, verified public suggestion fields, relevance-first user Search, weekly privacy-filtered connector board and real growth/share counts.
- **Known limitations / mandatory hold:** Live Supabase has NO new referral objects and no staging branch. Offline push timing, hosted email-confirmation trigger behavior, app-link verification and actual runtime E2E have not been demonstrated. No Coins are minted by referrals; reward values and anti-abuse/payment controls require separate approval. Source-level Search/new-user attribution and reuse in mentions, group/community invitations are not wired, and Android birthday remains in the later profile-onboarding stage. Public release still requires a verified backend gate and exact-SHA owner approval under AGENTS.md; PR #540 is draft, not production.
- **Scope safety:** Main's newer auth, Paystack, notification and website implementations were preserved. This is one consolidated PR; no direct production migration, account change, or APK publication occurred in the candidate branch.

## 2026-10-24 — Production Windows Kotlin compiler OOM follow-up (candidate)

## 2026-10-24 — Phase 5 scoped foreground feed freshness

- **Android:** Home and Reels revalidate after a per-surface one-minute cooldown when the app returns to the foreground or a primary route is revisited, while Messages, Market and Connect retain the existing conservative three-minute stale interval. This is a quiet data update, not a forced scroll-to-top or full-app reload. User-initiated feed refresh now updates only its own cooldown, rather than postponing other route refreshes.
- **Android Following lane:** Opening Following schedules its own throttled request for accounts with online data, retaining existing content and scroll position while fresh rows arrive. Existing New Posts affordance is preserved.
- **Recommendation diversity:** On an explicit ranked refresh, newly discovered *organic* posts are gently spaced when the same creator would occupy three adjacent slots; older posts retain server order, Following remains chronological, and paid/promoted placements are never displaced. No content is fabricated or dropped. Unit tests cover sponsor pinning and creator diversity.
- **PARITY-EXCEPTION: android-foreground-feed-quiet-refresh-20261024** — Android foreground lifecycle uses Activity `onResume` and Compose navigation events, which have no direct desktop equivalent. **Windows equivalent** is existing explicit reload/search and live post updates in `DesktopFeedPostCard` / `HomeScreen` of `ContentScreens.kt`; no Windows event loop or caching semantics are modified.
- **Validation:** `BlinkRefreshPolicyTest` covers first visit, repeated resumes and one-minute threshold; `test_home_reference_contract.py` checks that the screen and ViewModel retain the quiet scoped routing. Full Android/Windows CI and real-screen QA are still release gates.



- **Root cause from actual main CI:** Windows `desktopApp` is an independent Gradle root (`gradle -p desktopApp`), lacking a local `gradle.properties`; the massive generated `MessagesScreen$lambda$93` failed JVM bytecode transformation with `OOMErrorException`. The previous green Testlab build compiled a different (shorter) messaging screen; it did not validate this production file.
- **Windows scope:** Extract inbox rendering to `DesktopInboxPanel` to reduce the Kotlin/Compose method body. Preserve the existing message data, callback wiring, receipts, tabs and controls. Give desktop's independent Gradle root a 4 GiB heap with one worker and in-process Kotlin compilation so the runner does not exhaust its default compiler memory.
- **Android:** No Android UI/backend changes. The intro repairs from main SHA `0554aa5578fda4129d4871f3b242f84a8efa754b` are already merged; the signed APK publisher must verify this SHA separately.
- **Promotion gate:** Compile full Windows desktop, run desktop tests and package EXE/MSI, plus Android parity/safety checks. Do not treat a build-process retry as proof of a code fix. The Testlab messaging baseline differs significantly from main, so port only isolated safety settings to Testlab if needed; do not overwrite its messaging implementation with the production copy.

## 2026-10-24 — Startup intro optical alignment and startup responsiveness (Testlab candidate)

- **Android + Windows:** Both platforms use `BlinkBeyondLinkIntro` from shared code; no platform-specific clone, auth/session routing change or database modification.
- **Repair:** Render the approved B with blue dot and LINK vectors in a unified Canvas; baseline-align all five "eyond" letters, reduce the optical oversize of B, remove the original LINK vector's 27-unit left inset, introduce explicit separation between the three dots and the brand B, keep the final BLINK word tightly joined, and make the opening artwork visible on the first Compose frame.
- **Timing:** Remove the former 230–560ms forced blank fade, streamline from 6.4s to 3.2s with the same ordered collapse, continuous B and one enlarged wordmark.
- **Evidence:** Robolectric screenshots of first, transition and final frames at 320, 412 and 600 dp must be checked against the requested baseline and optical balance; Android/Windows CI and cold-start real-device smoothness/navigation still must pass. Testlab first, main/published APK unchanged until verified.

## 2026-10-24 — Beyond Link animated startup: scoped Testlab → main promotion candidate

## 2026-10-24 — Feed post footer, preview and opening behavior (Android and Windows)

- **Android:** Normal feed posts preview four lines before `See more`; the xFeed action row now shows Like → Comment → Repost → Save → Share for all text/image posts. True backend view count appears as small muted text below actions, not as an eye icon. Header whitespace, university and time metadata open full post while profile/Follow/options keep their own routes. Preserve media interaction, poll controls, saved state and author-hidden likes.
- **Windows equivalent:** `DesktopFeedPostCard` adopts the same Like → Comment → Repost → Save → Share order, four-line excerpts and small views label beneath. Desktop's xFeed card surface now follows the chosen app colour scheme rather than always painting black.
- **Regression tests:** Android screenshot interaction test covers liking an image post and tapping university metadata; shared Home static contract asserts action order and Windows/Android counts. Required device screenshot and actual link handling verification remain release gates.
- **Safety:** No Supabase or ranking modifications; backend-provided counts only. No claimed user engagement changes.


## 2026-10-24 — Feed viewport fills space while phone nav is hidden

- **Android:** `MainActivity` passes the Home bottom-rail scroll visibility into `PremiumFeedScreen`. When the overlaid 64dp navigation bar scrolls away, the feed no longer reserves its invisible footprint; preserve Android's system-navigation inset so posts and gesture targets remain accessible. Move the create FAB toward the safe-area bottom while the nav is hidden and restore it smoothly when the bar reappears, respecting reduced motion.
- **PARITY-EXCEPTION: android-feed-scroll-away-bottom-rail-20261024** — This corrects Android's phone-only scroll-away overlay navigation and gesture insets. **Windows equivalent:** `DesktopShell.kt` already uses desktop navigation with no vertically overlaid 64dp Android bar, so it needs no viewport-space adjustment and retains existing route behavior.
- **Validation:** Regression contract asserts state is passed to viewport and FAB, Android compile and runtime smoke, screenshot comparison while scrolling on small phones, with both system gesture and button navigation. No backend, chat or release changes.


## 2026-10-24 — Android Items respects active Light/Dark appearance

- **Android header responsiveness:** Keep Search, Items, Notifications and Menu in a compact single row on 320–389dp devices when they physically fit; reserve two rows for extremely narrow or large-accessibility-font layouts. Avoids the unwanted top gap from the old below-390dp mandatory split.
- **PARITY-EXCEPTION: android-feed-header-breakpoint-fix-20261024** — The Android reference feed header uses dp-specific phone gestures/insets, unlike Windows' desktop-native navigation. **Windows equivalent remains** its existing spacious drawer/toolbar in `DesktopShell.kt`. No Windows features or control routes are changed.
- **Android:** Replace legacy dark-only `FeedBackground`, `FeedElevatedSurface`, `FeedBorder`, `FeedTextPrimary`, and `FeedTextSecondary` references across Items' tools with Material3's active semantic palette. Preserves Game, Boost, Drops, Update Center, Weather, Steps, private location UI and existing functionality.
- **PARITY-EXCEPTION: android-items-semantic-palette-fix-20261024** — Items includes Android-specific phone utilities (sensor pedometer, Android permission-based live location, phone-specific APK updater) and is not a one-to-one desktop window. **Windows equivalent already present:** `BlinkDesktopTheme` provides Light/Dark semantics to desktop Items-like tools; this repair fixes an Android-only regression without adding, removing or diverging Windows behavior.
- **Validation:** Android compilation, static token regression test, Windows parity, and device screenshots in both appearances still required before release.


## 2026-10-24 — Restore Android Message tab and Items header routing on current main

- **Android navigation fix:** The reference phone bottom bar accidentally replaced the fifth **Message** destination with **Menu**. Restore the canonical Home → Reels → Market → Connect → Message order, selected state, unread indication, and working Message tap in the Menu overlay; preserve access to Menu via the top-right header.
- **Android header consistency:** Search, Inbox and Menu headers were showing Game directly where the approved four-square **Items** destination belongs. Use the Items grid and route it to the Items hub, where Game remains available, without altering other Game shortcuts.
- **PARITY-EXCEPTION: android-reference-phone-nav-restoration-20261024** — This is a correction to Android's compact five-icon edge-to-edge bottom navigation and phone-specific Items header controls. **Windows equivalent already exists**: `DesktopShell.kt` has a dedicated **Messages** drawer destination (before **Games**) and separate **Menu**, **Games**, and other utility destinations. Windows does not use a five-icon bottom bar or the Android Items sheet; it keeps its desktop-native navigation and existing functionality. No Windows feature is intentionally removed or deferred by this repair.
- **Release validation:** Scoped to a main-based staging branch to avoid clobbering the heavily divergent Testlab branch; verify Android builds, Windows parity, Search/Inbox/Menu route behavior and screenshot parity before reconciliation and main release.


- Source: Testlab PR #509, merged at `6c40b906bd55060ce5ef4e50a71047d806aac545`.
- Android and Windows now share the same 6.4 s black-background Compose launch sequence, turning `...beyond link` into `blink` with a white lowercase b and blue eye dot.
- Android replaces its 120 ms splash call, paints black edge-to-edge and removes the system's static icon flash. Windows overlays the intro only on initial process launch.
- Production port starts from the latest `main` instead of merging 589 unrelated Testlab commits and overwriting newer production functionality. Shared/Supabase business contracts are unchanged.
- Passing Android/Windows static and build checks does not establish pixel-exact match against the MP4 or prove on-device post-animation navigation. Device visual and launch-path sign-off must precede production merge/publish.
- Keep the current `main` branch intact until current-head PR checks and required owner SHA approval.

## 2026-10-24 — Scoped production logo asset promotion (PR #507 from Testlab)

- Official lowercase white **b** with electric-blue center dot and black rounded-square background applied via shared Android launcher/drawables, Windows Compose/taskbar/installer art, and web favicon/PWA/social metadata and website UI image.
- Assets are a visually faithful optimized raster of the user's supplied reference, rather than the original unmodified 1254px JPEG. Exact master stays in the chat asset package for future repro exports.
- Main's existing Android permissions/services, website SEO structured data, mobile feed, privacy-safe caching, and desktop packaging must remain unchanged except narrowly scoped brand references.
- Platform parity: the same approved mark is included in both Android and Windows assets. iOS AppIcon assets are staged but iOS release remains independently gated.
- Android small notifications use a platform-required monochrome icon (full-color logo is inappropriate there).
- Manual third-party vendor branding, store listings, hosted email templates and any unreferenced UI surfaces remain separate verification work; no claims of their publication from this code PR.

## 2026-10-24 — Search production candidate resynced with current main after auth upgrade

- Main advanced to d1c559f05d1fa24f19bf9ae88b3bf3354afb7a36 with approved auth routing (PR #503) while the previous Search production candidate PR #504 was open. The prior PR developed a genuine conflict with MainActivity and must not be force-merged.
- This fresh main-based candidate ports only Search landing, history submission behavior, matching Android/Windows search improvements, Search visual tests, and scoped MainActivity Search callback additions. It keeps the approved auth routes and MainActivity changes intact.
- Provenance: Search PR #500 was merged into Testlab at fd5dd79b383bb8e920ab9703c7f105b8ffb1cec5; all four post-merge Testlab gates passed.
- Do not merge to main until all current-head CI is green, runtime behavior verified, and repository-owner production approval is posted exactly as `BLINK APPROVE <PR-head-SHA>` on the active production PR.
- No backend or production APK deployment performed by the branch creation.

## 2026-10-24 — Android approved Auth welcome was unreachable in production

- **Root cause:** the approved `BlinkAuthReferenceLanding` was already wired through `OnboardingScreen`, and the Testlab shell used it, but the production `MainActivity` destination still invoked the older dark `BlinkWelcomeScreen`. Passing standalone reference screenshots did not test the production entry route.
- **Android repair:** route `AppDestination.ONBOARDING` to `OnboardingScreen` with the existing sign-in, sign-up and Google callbacks; align status-bar appearance and avoid double top insets while keeping bottom gesture/navigation space.
- **PARITY-EXCEPTION: auth-welcome-entrypoint** — This repairs Android's Activity-level navigation to the approved welcome; Windows has no Android Activity route and already shows the corresponding reference welcome screen with functional login, registration and Google actions. No new feature or changed shared backend contract needs a Windows code change. This exception is specific to the Android entry-point bug.
- **Regression protection:** an Android unit test asserts the actual app destination selects the approved screen, in addition to existing Compose click and screenshot tests at 100–200% font scaling. A signed APK must only be published after the latest candidate's release and navigation checks pass. Existing signed-in users normally go straight to Home instead of onboarding.

## 2026-10-24 — Only items 4, 5 and 7 from BLINK Auth follow-up

- **4 · Faster, safer login (Android):** Preserve the authenticated Supabase session instead of re-prompting after every login; remember the identifier only; stop refilling or persisting an account password, migrate and erase legacy encrypted remembered-password entries on auth screen; block duplicate credentials/Google submits, show early offline errors and a slow-connection hint. Do not bypass server identity validation. Existing logout/account-switch logic remains authoritative.
- **4 · Windows equivalent:** Keep DesktopAppState's existing provider session logic; add a local auth-in-flight gate for credential, reset and Google actions and slow-connection hint, without changing token format.
- **5 · University chooser:** All existing NigerianUniversities entries remain selectable offline; add fast case-insensitive search with vetted campus acronyms, grouped filters by Nigerian state, and an honest "Other / nationwide" group for institutions without verified campus location. Reuse the same chooser in Android signup and profile onboarding. No NUC accreditation or university list completeness claim.
- **7 · Optional post-onboarding monetization discovery:** After successful account onboarding **only**, offer a dismissible once-per-account guide to existing Blue/Gold verification, Coins and Boost screens. No automatic payment, permission changes, checkout bypass, enrollment or auto-renewal changes. Windows existing account and payment surfaces remain unchanged, as this is a one-time Android orientation overlay rather than a new purchase flow.
- **QA:** Focused alias, state, directory preservation and one-time modal preference unit tests; Android and Windows gate checks. Changes stage on a short-lived Testlab-based review branch from main before merge. All other recommended items are explicitly out of scope.

## 2026-10-23 — Responsive reference-form repair (staging)

- **Problem found:** The previous Android auth update used one heavily compressed campus photo for both login and three sign-up portraits, added unapproved visible controls, and had fixed-size image crops that did not adapt to narrow phones.
- **Android fix (first integration):** Split decorative tablet poster framing from functional compact-phone form; use dp/sp instead of compressing entire phone mockups. Make login reset the only secondary row action. Restore circular Google/Apple/email layout (Apple disabled until genuine OAuth is supported), searchable campus picker, real Google login, secure password visibility toggle, and explicit terms confirmation before signup.
- **Windows equivalent:** Keep the white branded Windows auth card width-capped to 520dp with narrower padding for small windows. No fake Apple OAuth button is enabled on Android or Windows.
- **Responsive:** 320–480dp compact width uses screen space for reachable native inputs; >=700dp tablets show black poster headline alongside a width-bounded white auth phone. Both forms remain vertically scrollable for landscape, short displays, keyboard and 130–200% font settings.
- **Visual fidelity:** Replace the previously repeated 6.6KB campus image with four bundled offline WebP crops extracted from the user-provided approved screenshots: the real login hero scene and three distinct signup student portraits. These source crops cannot recover detail beyond the supplied poster resolution; final pixel-level identity and surrounding monochrome marketing artwork remain unverified until native-mode emulator captures are reviewed side-by-side. The actual interactive form is intentionally prioritized over a shrunken noninteractive marketing poster on narrow phones.
- **Safety:** Preserve Supabase account creation, password rules, Google login gate, encrypted remembered-login preferences, password recovery and signup campus hand-off. No database migration or change to checkout.
- **Verification:** Add 320dp and 800dp Robolectric tests, run CI/Android compilation and review screenshot differences before promoting to main. Keep existing signed APK live until checks pass.

## 2026-10-23 — Reference-style BLINK login and student sign-up

- **Android:** The real login and create-account routes now use a white, rounded-card layout with the approved BLINK wordmark, campus photography, accessible fields, password visibility, Google auth, forgot-password recovery and working navigation. The promotional poster and device frame are excluded from the in-app screen. Existing Supabase sign-in/sign-up handlers and encrypted opt-in remembered-login storage remain in place.
- **University:** New accounts select a searchable university from the existing Nigerian directory; the selected campus is carried into the existing post-signup onboarding flow, then cleared after successful profile save. The database/auth contract is unchanged.
- **Windows:** Uses matching black-on-white form styling, branding, Google sign-in, password reset and existing auth handlers; desktop continues to collect campus during its existing profile completion flow rather than introducing a second signup API.
- **Safety:** Staged on a fresh branch from the latest main commit because the long-lived Testlab branch has diverged from main. Merge only after Android and Windows CI checks pass on the candidate head and visual checks show appropriate small-screen and large-font behavior.

## 2026-10-23 — Reference-matched authentication welcome screen (Testlab candidate)

- **Android:** Replace the animated purple onboarding illustration with a real, clickable welcome layout matching the approved BLINK auth reference: campus-student hero image, full-width rounded white card, four colored campus benefits, black Log In button, outlined Create Account, light Continue with Google and branded G icon, plus the subtitle "Connect, discover and grow Beyond LINK" with "Beyond LINK" underlined.
- **Behavior:** Preserve the existing sign-in, registration and Google auth callbacks, including the in-flight Google launch guard; no Supabase or credential changes. Keep the real buttons interactive; this is not a flat screenshot used as a login screen.
- **Windows:** Introduce an equivalent welcome/action state before the existing login form. Preserve native desktop email, sign-up, reset-password and Supabase Google authentication flows. Desktop does not include the promotional phone bezel or surrounding ad collage.
- **Tests:** Scope changes to auth; verify Android Compose buttons, 100–200% font sizes and screenshots, Android debug compilation, Windows desktop compilation. Compare rendered Android screen with the provided phone-screen reference. Do not merge into main until required CI passes.

## 2026-10-23 — Restore all ranked BLINK students without weakening private profiles

- **Root cause:** `public.game_leaderboard` was a `security_invoker` view directly over `profiles`, whose authenticated SELECT policy allows only `auth.uid() = profiles.id`. The ranking query therefore returned just the signed-in account despite the underlying database containing 21 profiles (12 with positive points). This was a public-data visibility issue, not missing scoring data.
- **Backend:** A narrowly projected, authenticated-only, read-only SECURITY DEFINER function returns leaderboard-safe fields. The `game_leaderboard` security-invoker view retains its existing 12-column ABI and appends verification and campus rank. `profiles` RLS remains owner-only. No email, phone, birthday, balance or private profile columns are returned. Explicit REVOKE for PUBLIC/anon and auth.uid() guard apply. Campus ranking uses normalized university punctuation. This is live from current points, not hourly snapshots.
- **Android:** Existing published clients continue to query `game_leaderboard` without an APK requirement; log non-success responses for diagnostics. Existing shared university canonicalization already handles FUTA aliases.
- **Windows:** Switch from history snapshot results (which duplicate users across hours) to the same authenticated live `game_leaderboard` source. Normalize campus matching via existing shared university directory.
- **Validation:** Simulate authenticated and anonymous SQL roles after deploying migration: authenticated sees all expected users, anonymous sees none; assert no private columns are exposed, scoring and campus grouping are unchanged, desktop unique user count matches Android, and owner-only `profiles` SELECT policy remains. No staging database purchased.
- **Promotion:** Merge this feature into Testlab after CI, then port only scoped commits to latest main. Deploy migration and verify in production before claiming the published Android APK is fixed.

## 2026-10-23 — Promote screenshot-inspired leaderboard to production safely

- **Android:** New reference-styled composable uses existing live rank data, global/campus scope, personal world/campus rank cards, earned XP level, real student podium and ranking rows. Existing **Top 20** rule, profile navigation, verified filter, Faculty and Academic Level scopes, manual refresh, event tracking, rank-up tracking and the live Rank Pulse card are preserved. Rank Pulse is placed below ranking rows to keep the reference's priority layout. No sample data is substituted for live values.
- **Windows:** Changes only leaderboard presentation and preserves server ranking fetches, recurring rank-change refresh, activity pulse/impressions, Top 20 and existing account semantics. Profile initials are shown where the desktop leaderboard API does not expose avatar images.
- **Scope limitations:** Friends has a disabled tab until a server-authoritative friend-only ranking query is available. Desktop rank change is left unavailable rather than invented, while Android shows observed movements. XP displayed in the personal card and activity score in ranking rows are different quantities.
- **Production gate:** Safe promotion from PR #475 into Testlab is not permission to merge the heavily divergent Testlab branch into main. Apply this scoped feature only on a current-main-based PR, require Android compile, Windows build/parity, release smoke and final UI comparisons; do not publish an APK unless released workflow succeeds.

## 2026-10-22 — Home tab rail + website APK Update Center (Testlab hotfix)

- Android Home: constrained the For You/Following tab rail to its intended 58dp height. A former minimum-height plus fillMaxHeight combination could occupy the entire viewport and leave posts below the visible area. Preserves the existing post ranking, loading, scrolling ratios, ad positions, bottom navigation and Create control.
- Android Items: adds an explicit Update Center tile, showing “Update available — tap to update” (with the verified release version when known), “No update now, check again later”, or “Checking for updates…”. Tap to re-check or open the existing authenticated-device independent, verified website-APK download/install flow. No extra APKs and no Supabase changes.
- Android update dialog: accidental outside-tap and Back dismissal no longer suppresses an available update; explicit “Remind me later” and “Continue in background” remain. Background transfers now surface a draggable in-app progress bubble which reopens the updater, and the dialog shows received/total MB when HTTP provides a known size.
- PARITY-EXCEPTION: android-website-apk-update-tile — The GitHub APK installer applies only to Android website-distributed packages. Windows continues its existing desktop software/update distribution; it must not try to install Android packages. Shared release version comparison in BlinkReleasePolicy remains unchanged.
- Production hold: test this in the fresh main-based Testlab hotfix branch, verify Android and Windows checks, and only then promote the exact reviewed commit to main. The long-lived Testlab branch is heavily divergent from current main and must not be wholesale merged.

## 2026-10-22 — Full-screen BLINK profile promotion to current main

- Android: integrated the reference-inspired 100% width profile dashboard into the *current* production baseline, not the divergent Testlab snapshot. Shows real name, avatar, university, verified tier, World Rank, owner-only Coin balance, XP progress, earned/locked badges and verification actions.
- The advanced production profile identity, connection cards, follower actions, search, reporting, blocking, notification preferences, posts, reels, market items, owner insights and Progress Hub remain accessible through the expandable "More profile details & actions" section. Existing safe data retrieval and privacy policies remain unchanged.
- Reuses existing BLINK navigation state for Home/Reels/Market/Connect/Messages and header actions. Phone-specific edge-to-edge insets handled by Android; Windows keeps desktop-native navigation. Full-screen bottom bar preserves Android gesture-navigation safety.
- Windows: adds honest achievement *categories* next to its established backend-driven XP, Coins and verified status. Category labels do not imply awards have been earned without an authoritative badge response.
- No database migrations or live payment changes. Main publication requires verified Android and Windows builds, screenshot artifacts at normal and enlarged fonts, visual review and a signed APK from the main commit. Preserve main's newer work; do not wholesale merge divergent Testlab.

## 2026-10-22 — Android Paystack return reconciliation (main-target scoped PR #465)

- PARITY-EXCEPTION: paystack-android-activity-resume — Android receives hosted-checkout returns and foreground callbacks through its own activity lifecycle. It retries only the authenticated user's saved Paystack reference and refreshes the server-confirmed Coin wallet without refreshing the Feed; no client-side Coin minting or merchant flag changes.
- Windows equivalent: Desktop already launches provider-hosted checkout with `initializePaystackCoinCheckout`, verifies the saved order with `verifyPaystackCoinCheckout` on customer action, then reloads authoritative backend wallet state. It has no Android activity/deep-link resume lifecycle. This PR intentionally changes Android automatic UX only, and does not claim Windows gains background or automatic retry.
- Residual parity task: implement account-bound automatic pending-order retry upon Windows window-focus/session restoration separately, after verifying the older desktop `paystack-initialize`/`paystack-verify` endpoint compatibility. Preserve the existing Windows manual "Check payment" button until then.
- Release gate: enforce server-only fulfillment, duplicate/webhook protection, Android/Windows CI, account-switch regression, and no extra real-money test charge.

## 2026-10-21 — BLINK Message inbox reference redesign (Phase 2 staging candidate)

- **Android inbox only:** Adds a dedicated black-and-white Compose inbox matching the supplied reference structure: BLINK wordmark/profile/search/game/notification header, Messages title with functional “See all,” real uploaded-story rail, flat conversation rows, presence badges, verified badges, previews, timestamps, and black/white unread counters. Existing Supabase conversations, shared filtering/search, archive/request/unread handling, user identity, and chat/call detail components stay intact. “Your note” currently routes to the existing create-story action pending a separate notes contract; it must not be advertised as a shipped notes feature.
- **Phase 3 source wiring:** Avatar opens own profile; Search expands the existing text-and-voice inbox search; Game opens the existing GameSection; Notifications opens Activity with live unread-dot state; See all exposes the shared All/Primary/Unread/Requests/Archived filtering, New Message, and settings; tapping a contact opens the pre-existing chat detail. Story circles open actual stories; the own tile shows a blue ring only when an uploaded story is present and otherwise opens the existing story creator. No new notes API, data model or production database migration is introduced.
- **Windows:** Desktop inbox receives the matching wordmark / See all / search header, quick-contact circles, flat message rows, and unread counters without changing Windows chat details. The desktop data contract presently supplies live conversations and online contacts but not a dedicated story feed; it does not fabricate story rings. True desktop story parity is pending a dedicated data adapter.
- **Phase 4 visual-check instrumentation:** Add one Android 15 light-theme Compose screenshot fixture and direct control assertions, exported through the existing Android runtime-smoke workflow as `inbox-reference-staging-light.png`. This capture uses test-only placeholder avatars, not stock reference photos or fabricated production data. Reference metrics tuned: five fully visible story tiles at phone width, compact Inter headings and labels, 94dp text/divider alignment, wider avatar-to-text gap. CI execution and pixel-level side-by-side verification remain separate gates and must be reported accurately. Draft PR #459 targets main for CI visibility only; merging it into main is prohibited until Testlab validation.
- **Release safety:** Isolated branch based on the latest production main, *not yet merged to Testlab or main*. This is a Phase 2 UI candidate, NOT a screenshot-verified identical match or a production APK. Resolve the heavily diverged Testlab branch without discarding newer main features; run targeted Android + Windows compilation, Android 15 emulator interaction checks, and screenshot side-by-side comparison before any promotion.

## 2026-10-21 — Owner-only Android Paystack Coins pilot (PR #443)

- **PARITY-EXCEPTION: android-owner-coin-checkout-pilot-20261021** — Android's native Coins button previously rejected all purchases using a stale global economy flag. This change allows the Android pack picker and authenticated Paystack client to reach the existing secured backend; server-side allowlist, account identity verification and checkout kill switch remain authoritative. No public payment access is being unlocked.
- **Windows equivalent:** Windows retains its existing Coins interface and global payment gate during the limited Android owner pilot. Equivalent Windows purchase behaviour is required before public rollout.
- **Required verification:** Android/Windows CI, signed APK integrity, real-phone owner and non-owner checks, exactly-once Coin credit, webhook replay rejection and receipts. No real payment is verified by this source-only change.

## 2026-10-20 — Home reference visual reconstruction (Testlab-only candidate)

- **Android:** Stage a near-reference 1:1 Compose Home presentation, not a fabricated image: BLINK wordmark and outlined Search / Items-gamepad / Notifications / Menu header; real world-rank and coin summary cards; simple underlined For You/Following; rounded real-content PostCards with user university/age, blue/gold verification only when backed by account state, black Follow control, five primary footer actions; minimal monochrome bottom navigation. Keep the floating Create action and dark theme.
- **Items:** The gamepad icon opens **Items**, not Game. Games, Boost and Drops remain accessible inside Items so removing the old Home utility row does not strand their entry points. The real messaging Stories surface remains intact; Home has no Story row.
- **PARITY-EXCEPTION: android-reference-feed-compose-20261020** — Device-specific screenshot geometry, status-bar insets, 44dp header buttons, portrait post-card spacing and five-icon Android bottom navigation are not meaningful literal Windows layouts. Desktop preserves the existing account-backed Home feed, messages, marketplace, ranking, coins, Like, Follow, Repost, Save and Game/Boost/Drops workflows. Shared validation/server semantics remain unchanged; desktop needs its own layout QA rather than stretching mobile UI.
- **Safety:** sample ranks, sample coins, fake verified users and artwork-only post content must never ship as live data. The new summary reads `currentUserProfile.worldRank` and `uiState.blinkCoinBalance`. Likes on photo cards are still available by double-tapping media and via an accessibility action, and text-only cards retain a visible Like control.
- **Release blockers:** Android unit/lint/build, Android emulator runtime screenshot with real feed, Windows parity/build, screenshot diff on a real Android phone in light and dark themes, connection/follow/like/posts/Coins click tests, and owner visual approval. Do not merge into main or publish before verification.
- **Staging strategy:** Working feature branch `Testlab-home-reference-20261020` starts from the latest main rather than rolling back hundreds of production commits in the shared diverged `Testlab` branch. This is an isolated Testlab candidate, not production.

## 2026-10-19 — BLINK production release safety and Android website updater (integrated Testlab candidate)

- **Shared:** official-release policy validates HTTPS GitHub delivery origins, maximum download sizes, Android package/version identity and signing continuity. `BlinkApkResumePolicy` validates HTTP byte-range responses, refuses mismatched offsets and restarts safely when a server ignores a Range request.
- **Android:** user-started APK updates from the official GitHub Release now use WorkManager for foreground/background progress, optional unmetered connections, pause/resume after network loss, retries, private partial storage and verified-only installer file exposure. SHA-256, package identity, version and signing certificate checks happen before the Android system installer prompts for user approval. Play-distributed installs use Play update flow, never the website sideload prompt.
- **Windows:** **PARITY-EXCEPTION: android-workmanager-apk-installer-20261019** — Windows cannot use Android WorkManager, FileProvider, foreground-service types or APK installation permissions. Windows continues with its native desktop delivery mechanisms; shared release and byte-range security checks are platform-neutral. No invented Windows download buttons.
- **Release CI:** approval bound to exact Testlab commit SHA, mandatory package integrity checks and read-only published-release auditing are required on approved production promotions. These are code-level safeguards, **not** a substitute for enabling GitHub `main` branch protection in repository Settings.
- **Validation required:** Android lint, unit and runtime smoke; Windows parity/build; secret scans; Supabase safety; APK signing release and public download audit; actual Android installation and network interruption testing. Staging changes are not production until these checks and approval succeed.

## 2026-10-18 — Android native call action delivery and offline hangup recovery (PR #371)

- **PARITY-EXCEPTION: android-telecom-workmanager-call-ending-20261018** — This change specifically repairs Android OS `NotificationCompat.CallStyle` actions, Android Telecom lifecycle, Android `singleTask` call-intent isolation and Android WorkManager's persisted network-constrained background retry. Those OS facilities have no Windows equivalent; porting Android code into Windows would be incorrect.
- **Shared call contract unchanged:** Both apps continue using existing authenticated Supabase `decline_call` and `end_call` RPCs, call ID/status values and existing server authorizations. The Android retry obtains a fresh session when the device reconnects, rejects another signed-in account, and never treats unsuccessful RPC results as successful push delivery.
- **Windows equivalent:** Desktop's `DesktopRpcActions.declineCall()` and `DesktopRpcActions.endCall()` already call the same server RPCs; `DesktopShell` polls incoming ringing calls, scopes them to the current authenticated session, and shows native desktop notifications. Desktop does not use FCM, Android Telecom, Android notification receiver activities or Android WorkManager. The desktop does **not** gain offline persistent hangup retries from this Android-specific fix, so Windows offline behavior must be evaluated separately if required.
- **No deliberate desktop regression:** This PR does not alter `desktopApp`, `shared`, backend call RPCs, authorization, voice/video transport or global account rules. Existing desktop behavior stays unchanged; review still requires the full Windows build/parity gate, Android Kotlin/call tests, runtime/release smoke, backend verification and physical handset tests.
- **Promotion boundary:** Work in this PR is based on production `main`, not a wholesale merge of diverged `Testlab`. The separate Phase 1–4 WebRTC/UI/FCM changes still require integration review against production's newer Android Telecom implementation.

## 2026-10-17 — Compact PostCard promotion from verified Testlab (#337)

- **Promotion strategy:** because Testlab diverged from main, selectively reimplement the X-inspired layout on the latest production ancestor instead of merging the entire outdated Testlab branch. The original Testlab code and CI were reviewed in PR #337.
- **Android:** x-style Home PostCards now have square, near-edge-to-edge borders, one-line name / verification / @username / abbreviated age, compact follow and options controls, no Store/VIP crown, and six actions ordered views/likes/repost/comments/save/share. Existing author lookup, pending-follow state, media caching, scroll performance and non-x PostCard functionality remain unchanged.
- **Windows:** preserves current real like/comment/repost/bookmark/share API integrations and media rendering while matching the compact x-style header and reordered controls. No fake controls or mocked account state.
- **Shared:** `BlinkPostFormat` is used for display-only short ages and metrics and has focused unit tests; no Supabase/database changes.
- **Quality gates:** both client builds, Android tests/lint, Windows parity, short/long names, different font sizes, narrow-device header and six-icon layout, offline behavior, release signing, and exact website APK publication verification. This is not production-ready until all applicable tests pass and the production PR is reviewed.

## 2026-10-17 — BLINK Smart Onboarding, Updates & Announcements (production-main port pending QA)

- Shared guide, version policy, cached official-update policy and admin broadcast safety rules ported from verified Testlab work, preserving newer main UI and navigation.
- Android: first login guide on idle Home, nonblocking latest **published** APK notice, official previously-delivered login announcements, Saved Updates in Notifications, and secure admin message composer under existing mobile-first admin menu.
- Windows: guide, cached updates and admin message composer integrated into current Windows screens without reverting newer main changes.
- Server delivery uses already-existing authenticated `admin_send_announcement` for immediate in-app campaigns. **New phone push/both and scheduled broadcasts are disabled**, not represented as working features; no Supabase schema or production data changed.
- Gate: all main PR checks, signed Android release pipeline, production version metadata and website download validation, plus real-device tests. Never merge Testlab wholesale: 94 ahead and 857 behind at integration assessment.

## 2026-10-17 — Android five-tab navigation, loading placeholders and error copy (Testlab)

- **PARITY-EXCEPTION: android-five-tab-compose-loading-20261017** — This change repairs native Jetpack Compose `HorizontalPager` / `FeedBottomBar` interactions, avoids redundant tab-state changes and broad mobile inbox refreshes, and adds Android Compose skeleton rendering for first-load Connect discovery. Android-only UI framework and performance details do not apply to Windows window/sidebar navigation.
- **Windows equivalent:** The Windows desktop app retains its existing native desktop navigation, independently loaded authenticated message lists, loading feedback and search/discovery UI. This patch neither removes those features nor changes shared API contracts, user accounts, messages, search, ranking or data semantics.
- **Customer-facing error copy:** Android error messages from network and backend exceptions are converted to nontechnical retry text. Desktop continues to use its own existing error copy and logs. No sensitive technical details or schema changes are deliberately exposed.
- **Validation:** Android unit tests, instrumented test compilation, static Android lint, debug build, release smoke and runtime verification; Windows build/parity; manual five-tab swipe/tap tests, inbox under weak network, Connect placeholders, and verification that no raw developer text reaches users. Do not merge until the required checks pass.

## 2026-10-16 — Legacy dark-color cleanup, Android and Windows (Testlab)

- **Per-screen dark backgrounds replaced:** Feed home, post cards, reels skeleton/disc, profile skeleton/avatar, admin sidebar/dialogs, verified panels, market-seller header, seller confirmation, message action menus and story media fallback now consume semantic colors or shared platform-neutral design tokens.
- **AI surface parity:** BLINK AI previously rendered hardcoded green-black surfaces and turquoise controls irrespective of app dark styling. In Dark Mode, Android now uses the Auth-like black/near-black/purple palette; Windows AI icon and selection accents use the same purple. Original AI Light Mode styling is preserved.
- **Status tokens unified:** Existing brand-purple, blue, success and warning hex values were replaced with equivalent `BlinkDesignTokens` references in affected Android and Windows screens and badge components, without changing their visible color meaning.
- **Deliberate exceptions, not remaining migration errors:** Photo/video scrims stay black for legibility; users' chosen post/theme colors, Google sign-in brand, verification/online status colors, WhatsApp, custom VIP cosmetics, feature-specific celebrations and selected Light Mode promotional art keep distinct colors where their meaning would be lost by forcing the dark surface palette. The Auth screens remain the canonical reference.
- **Regression guard:** `scripts/check_premium_dark_tokens.py` runs in Android and Windows CI and fails if covered screens reintroduce obsolete hardcoded dark surfaces or diverge from the shared Auth design contract. Full real-device visual and accessibility QA is still required; static checks alone are insufficient.
- **Main stays untouched:** Draft PR #308 remains confined to the fresh main-based `Testlab-auth-premium-dark-20261016` branch, pending fully successful test gates and review.

## 2026-10-16 — Auth-inspired premium dark design system (Testlab only)

- **Shared-first:** `BlinkDesignTokens.Dark` and `BlinkDesignTokens.PremiumDark` define the Auth-inspired pure-black/near-black layers, white-on-black glass opacity, 8%-white borders, purple accents, pill buttons and readable typography for Android and Windows.
- **Android:** Material 3 dark surface containers are explicitly populated to eliminate auto-tinted/mismatched cards. Shared premium cards, search/text inputs and primary buttons use Auth-like dark styling; Feed navigation and chrome highlights use purple in dark mode. The independent dark Messages palette is aligned; custom Pink/Light chat themes remain available.
- **Windows:** The real desktop root used an ad-hoc dark palette that bypassed `BlinkDesktopTheme`; it now uses the actual shared brand/dark/light/system tokens and dark Material 3 surfaces. Desktop navigation accent now uses its active theme.
- **Light Mode:** Existing light tokens and light-specific component styling remain unchanged. The native Auth screen itself remains unchanged. No backend, ads, auth flows, game rules or paid services are modified.
- **Scope:** This central theme/component rollout improves all MaterialTheme-based screens. It does not claim that every legacy hardcoded color has been migrated; per-route visual QA remains necessary.
- **Release gate:** Branch based on current `main` because the old `Testlab` branch has substantially diverged. Validate Android unit tests, lint, debug APK, Android instrumentation compilation, Windows build/parity, screenshot/UI and real-device accessibility checks before review/merge. Main and public APK must remain unchanged until those checks pass.

## 2026-10-15 — Website BLINK LIFE background Web download (Option B / Android-only delivery)

- **PARITY-EXCEPTION: blink-life-website-webgl-on-demand-20261015** — Website-installed Android users get a signed, compact base APK; Android DownloadManager fetches a version-pinned, SHA-256-verified Godot 4.3 WebAssembly/WebGL game archive only after the player requests it. Verification/unpacking runs in WorkManager, and an internal sandboxed WebView provides an in-app Play screen without a second APK installation. This is Android delivery and WebView integration, not a change to shared account, coin, or social-game rules.
- **Windows equivalent:** The desktop Games page continues using the bundled native Godot launcher built from the same game3d project; Windows does not have Android DownloadManager, WebViewAssetLoader, or Google Play Feature Delivery. The new on-demand ZIP is not a Windows package and must not replace native Windows assets.
- **Play Store:** Its signed dynamic feature remains Play-delivered on demand. The website/WebGL and Play/native variants require separate hardware validation; the WebGL version does not claim to have native LAN/UDP multiplayer or server-backed social gameplay.
- **Release safeguards:** Require both APK + ZIP hashes, signed upgrade continuity, website link checks, full Android CI, Windows parity/build, background download, offline WebGL playback, notification and storage controls, and real Redmi-class testing before production. This remains a draft Testlab PR and is not approved for automatic merge.

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

## 2026-10-17 — Scoped foreground refresh and list continuity (Testlab candidate)

- Shared monotonic refresh-age policy suppresses repeated foreground network requests without changing backend semantics.
- Android resume refreshes only the active Feed, Reels, Connect, Market, Messages or Leaderboard screen, preserving cached UI while requests complete. Market filters and scrolling, Connect scroll and Messages scroll are retained across navigation.
- Newer atomic `navigateToPrimaryTab`, guarded inbox-entry loading, nontechnical error messages and Connect skeletons from current main are deliberately preserved during integration.
- Android-specific Compose list/pager and Activity lifecycle are not directly applicable to Windows; the existing desktop navigation and backend data contract are unchanged.
- Android/Windows quality, runtime and release checks must pass against this rebased commit before release.


## 2026-10-17 — Android native admin workspace (mobile-specific access and readability)

- **PARITY-EXCEPTION: android-admin-mobile-workspace-20261017** — The changed surfaces are the Android Jetpack Compose feed-toolbar admin shortcut, touch-friendly tool grouping, Android modal account picker, and Android font scaling. These are native phone UI adaptations, not a shared account, reward, feed, or marketplace feature; no Windows navigation or business rule is being removed or replaced.
- **Security/back end:** Admin privileges continue to be decided by the existing authenticated Supabase capability/RPC and checked again by the admin activity and backend; this PR does not add an Android-only server endpoint or bypass role permissions.
- **Windows status / limitation:** The Windows client does not currently offer a matching native admin-dashboard interface. This PR does **not** claim desktop feature parity or imply Android admin tools are available on Windows. A separate Windows admin interface with equivalent permission checks, section navigation and searchable user targeting is necessary if Windows admin access is required. Existing Windows flows remain unchanged.
- **Verification:** Android instrumentation compilation, accessibility sizing, backend authorization checks, Windows build, and other repository merge gates must pass before main release.

## 2026-10-17 — Android crash protection (Testlab; not released)

- **Platform adapter exception:** PARITY-EXCEPTION: android-startup-crash-hardening-20261017 — Android Application startup (FCM, notifications, WorkManager, Telecom and device refresh-rate), low-memory Coil image cache, Activity stop lifecycle and Android sensor events are platform-specific and have no corresponding Android lifecycle or sensor API on Windows. Existing Windows account, messaging, cache, and startup contracts remain unchanged.
- **Shared/backend behavior:** Supabase and Drops business rules have not changed; Android Drops now tolerates an empty policy action list without crashing. Windows must continue rejecting any action absent from the same shared policy. No synthetic rewards, user data or backend contracts were added.
- **Validation required:** Android unit, lint, release compilation, runtime smoke, Windows build/parity, native crash testing on Android 15 and a real-device low-memory regression. Keep main unchanged until all relevant gates are verified.

## 2026-10-17 — BLINK Market seller eligibility and fair discovery (Testlab candidate)

- **Shared:** `MarketTrustPolicy` diversifies loaded ranking results per seller; `MarketIntentPolicy` conservatively flags price-plus-sales-call-to-action content. Unit tests cover ranking, targeting and false-positive examples.
- **Android:** remove the ₦5,000 paid seller enrollment UI; verified sellers submit store/contact/university details through an authenticated server RPC. Expired blue verification disables seller posting and displays renewal guidance. Listing forms now show accurate condition choices and **non-transactional** optional boost pricing previews. Main Feed redirects obvious commercial listings to Market.
- **Windows:** free seller enrollment uses the same RPC and verification checks, Market results use the same fairness policy, and Feed creation rejects the same high-confidence sales intent. Desktop still requires richer image upload parity before treating its listing creation UI as final.
- **Supabase:** versioned staging-only migration enforces blue/gold expiry at the database boundary, limits new listings to 3 per 24h, prevents duplicate same-seller titles, hides expired sellers from normal discovery and blocks new orders to them; retires the legacy seller-activation payment RPC. Existing paid order records remain intact. The production database is **not** modified by this PR.
- **Unfinished/release gate:** No paid Market boosts are activated or charged; the shown packages are estimates, not delivered view guarantees. Moderation appeals, campaign delivery/settlement, seller analytics, verified reviews and campus-specific full ranking require subsequent work. Hosted Supabase preview is not available without payment (not authorized); disposable PostgreSQL schema tests and live Android/Windows runtime tests are required before promotion. The old `Testlab` branch diverged from `main`, so this isolated `Testlab-*` candidate starts from main to avoid an unsafe wholesale merge.

- **Campus discovery addendum:** Android and Windows Market now offer `My University` and `All Universities` toggles. Both pass the campus filter to the backend so results and offset pagination match the selected audience; the seller diversity presentation policy remains shared.

## 2026-10-17 — Professional BLINK Market expansion (draft PR #342; stacked on #341)

### Shared contract and implementation status
- Database-first versioned migrations establish owner-scoped seller profiles, independently approved merchant badges, Bayesian-smoothed reputation scores, seller dashboard, offer/counter workflow, order disputes, owner listing moderation appeals, admin queues and decision audits, 7-/3-day verification warning activity events, 25-/30-day availability warning and pause, saved-item price-drop alerts, seller diversity and weighted market discovery, optional product videos, seller photo SHA-256 fingerprint field, and Blink Coin promoted listings with timed 2-second impressions and unused coin refunds.
- Cash/Paystack marketplace Feed boosts in naira are **NOT ACTIVATED**. The staged real promotion route is the existing **Blink Coin** campaign backend. Price figures are **previews only**, not payable products, view guarantees or deliveries. Backend integration and payment-provider verification are mandatory before activating any naira-based purchase flow.
- Ad views are qualified by a short server-enforced elapsed interval plus a client visibility check. This is stronger than an on-composition impression, but it is not cryptographic proof of human attention; bot detection and provider verification require more engineering.
- Duplicate photos are checked against first-image SHA-256 fingerprints calculated on Android/Windows, backed by a 30-day same-seller duplicate SQL rule. This is a useful first defense, not strong proof against a malicious client that lies about the digest, re-encodes images, or omits the field. Trusted server-side media hashing and perceptual matching are still needed.
- Rule-based moderation quarantines a narrow set of high-confidence prohibited descriptions and supports merchant/listing appeals. Image interpretation, complete multilingual intent classification, and an appeal before posting a rejected Feed draft still require work.

### Android + Windows parity
- Android: full business-vs-individual type/description/category/delivery/pickup registration UI; personal contact hidden from new listings by default; seller dashboard and separately approved merchant status; offer inbox, tracked orders and disputes; new seller video picker, upload and listing preview; 30-day confirmation action; live Market moderated-listing appeals; recommended ranking and timed qualified promoted cards.
- Windows: same business registration, owner dashboard, offers and counteroffers, order/dispute intake, native photo/video file selection and Market listings preview, recommended discovery, duplicate-image SHA-256 client fingerprint, with common backend approval and credit rules.
- **Exceptions / incomplete parity**: Android has an interactive admin Market Moderation Desk inside its native admin center. Windows administration is presently not an equivalent dedicated moderation panel; the admin RPCs are server-shared and require explicit parity completion. Windows supports uploaded video links in listing data but does not yet embed in-app video playback; Windows can launch uploaded media using desktop conventions. Product photo/logo preview and mobile upload UX are still basic compared with a production merchant studio.

### Release gate
- These database migrations are **committed ONLY to a Testlab-derived branch**; the connected production Supabase project is unchanged.
- The owner declined paid Supabase preview branches. Do not incur charges. Use a truly isolated no-cost Postgres fixture or free-safe preview for RLS, Auth, Storage, scheduled workers, media uploads, Paystack, and coin-ledger tests. Pure SQL parsing and GitHub green checks are insufficient.
- Do not merge PR #342 into Testlab, PR #341 into main, or publish new APK/Windows builds until latest-SHA SQL regression, Android build/tests, Windows build/parity, runtime, backend and server-provider checks have all passed. Fix actual failures rather than bypassing checks. If these gates are unavailable, leave PRs draft and production untouched.


## Cross-version APK delta updater (Testlab, 2026-10-21)

The delta manifest/validation and pure JVM `BlinkDeltaPatch` parser/reconstruction
live in shared sources (Android + Windows compile parity). Android uses its
existing WorkManager and OS PackageInstaller to reconstruct a byte-identical
publisher-signed APK directly from any of up to 10 compatible past releases.
Windows does not use APKs or Android's installer; its native desktop update
and distribution flow is unchanged. Full APK and Google Play update routes
remain independent and intact. QA and Testlab approval required before main.


## 2026-10-24 — Main reconciliation candidate: complete five-repair code port (DRAFT)

- **Scope:** Safely port all five Testlab repair code paths onto main's newer implementation rather than merge divergent branches or overwrite additional profile, inbox, realtime, authentication and desktop features. This is an **unverified production candidate**, not a production deployment.
- **Profile (Android/Windows):** The reference dashboard restores real cover, handle, headline, academic context, streak, post/follower stats, profile completion and Follow→Interact while retaining advanced profile actions behind the existing details control. Add the same identity data to Windows without stretching the phone layout. Extend shared phone-breakpoint policy *without removing main's existing adaptive APIs*.
- **Notifications:** Preserve main's `NotificationMessageHistory` and `NotificationIdentity`; add account-scoped silent grouping and a summary that opens Messages without replacing quick actions or storing message previews to disk.
- **Payments:** Reconcile V2 checkout at most once per minute, without repetitive pending toasts or unconfirmed benefit grants. Provider-verified abandoned/failed/reversed responses clear only local reminders. Preserve pending orders and signed-webhook idempotency, shared status policy and desktop typed response adapter. Existing legacy cash-order checkout is unchanged.
- **Messages (Android/Windows):** Chat owns overlapping navigation/IME insets exactly once; composer no longer adds another bottom safe-area strip. Shared receipt policy expresses server-evidenced ✓ / ✓✓ / blue ✓✓; Android and Windows inboxes show only the actual latest **outgoing** receipt, with lazy membership-protected metadata loading. Background chat or disabled read-receipt settings cannot cause read acknowledgements; Windows retains advanced message controls, pagination, drafting and privacy settings.
- **Backend and release gate:** No production Supabase schema/Edge Function was deployed, and no real payment was attempted. Relevant Android/Windows/shared tests, Paystack provider test-mode/late signed-webhook verification, and two-account online/offline/foreground Android 15 runtime checks must all pass before changing main or publishing one signed APK. No paid hosted staging without user approval.

---

PARITY-EXCEPTION: android-home-navigation-and-create-idle-20261024
Date: 2026-10-24
Feature: Fix Android touch-first Home navigation, video playback visibility and single-action Create Post affordance.
Android behavior: The bottom tab rail is fully hidden on downward scrolling and restored on upward scrolling, with system insets respected; Home Create launches the composer directly while its label reveals after six seconds without user interaction. ExoPlayer Reels and inline previews pause when the parent horizontal pager is no longer the foreground settled destination.
Why Android-specific: These problems are caused by Android Compose horizontal/vertical pagers, Android's mobile bottom nav and Media3 ExoPlayer touch-oriented playback. A desktop window does not show a floating Android-style nav bar or six-second idle FAB, and does not run Android Media3.
Windows equivalent: The desktop Compose app already uses visible sidebar/window navigation and direct composer actions. Video playback must remain tied to the actively visible desktop surface; no change to the shared posting, engagement, ranking, video entitlement or API contract is introduced.
Shared Android/Windows login behavior: Both clients now use the same BlinkAuthFeedback classification; failed password login retains safe, specific feedback and never falls back from email to an unrelated username-login request.
Validation: Android unit and build gates, Windows build/parity, scroll+video lifecycle tests on actual Android phones and separate main-vs-Testlab verification are required before merge.


## 2026-10-25 — Consolidated 13-phase stabilization progress (DRAFT #541)

- **Post composer (Android/Windows):** Shared `BlinkPostGrowthTips` supplies context-aware, non-promissory creation guidance and one canonical set of feed categories. Both clients remove Marketplace from regular post category controls; Android scheduled/legacy drafts and both direct feed API adapters refuse the removed category. **Production database RLS/RPC restrictions and marketplace-redirect UX still require a separate staging-validated integration before release.** Existing client-side sales-intent warnings remain; ordinary product discussions must not be automatically censored.
- **User mentions (Android):** Comments rank available candidate accounts by exact handle, then handle prefix and name; show up to 10, with local See more. `BlinkUserSuggestionPolicy` is shared and paginates deterministically. **Not universal yet:** current comments use existing visible candidates; server-backed discoverability, account blocking/privacy enforcement, Windows equivalent picker and other invitation surfaces remain release blockers for Phase 7.
- **Admin menu (Android/Windows):** Verified admin capability enables an obvious top-level `Switch to Admin Profile` entry on both clients. Android navigates through its existing admin handler; Windows routes to the existing admin workspace, which is independently guarded by `DesktopShell` and server capability. No client-only permission grant.
- **Message swipe (Android):** A deliberate 76dp activation with beyond-threshold resistance, drag-following motion, spring return, and optional OS haptic feedback replaces the abrupt 70ms reply slide. The shared `BlinkReplySwipePhysics` contract has JVM tests. **Platform exception:** Windows already offers Reply through the message context menu and the same server-side reply association; touch-only horizontal drag is not required for a desktop mouse. Windows should retain context-action and quote navigation tests.
- **Status / release:** All changes remain in draft review branch PR #541. Android compile/unit/emulator, Windows compile/parity, mobile 320/360/390/412dp screenshots, Supabase safety, messaging runtime, and branch reconciliation must be verified on the final SHA. This is not a completed 13-phase release, not a production backend migration, and not a published APK.


## 2026-10-25 — Follow-on 13-phase candidate (STAGING ONLY, not parity-complete)

- **Navigation:** Android ViewModel now records account-scoped bounded LIFO snapshots for primary tabs, opened profiles and private chats; Android Back uses the stack before discarding those surfaces. Shared immutable history policy with JVM tests. Still requires Back arrows, nested overlay history, process recreation, deep links and Android gesture tests. Windows retains its desktop sidebar/window navigation; desktop Back/Escape parity and state restoration are not yet verified.
- **Username selection:** Existing Supabase `search_profiles_page` RPC was inspected read-only on the live Blink project: authenticated, masks presence and excludes both directions of block relationships. Android comments now debounce actual server lookup and offer paged See more; a reusable multi-select picker is connected to New Message. Windows already uses the same RPC for global search; new-message and group-invite recipient UX parity remains an explicit outstanding blocker, not an exception.
- **Groups:** `BlinkGroupCreationPolicy` and Android/Windows adapters validate and call the existing authenticated 3–50 person `create_group_conversation` RPC, but remain UNEXPOSED until end-to-end grouping works. Live `get_conversation_summaries_page` repeats the group for each other participant and shows DM metadata, so public group creation UI cannot ship safely yet. Staged SQL for group summary and community tables/RLS lives in `docs/integration/groups-communities-staging-20261025.sql`; it has NOT been run against production.
- **Market:** `docs/integration/marketplace-feed-server-policy-staging-20261025.sql` stages a Postgres trigger rejecting non-trusted Marketplace feed categories while retaining server-owned labelled sponsored placements. This is not a deployed or tested migration. Hosted Supabase has no dev branches available, and owner disallowed paid staging.
- **Adaptive performance:** Both clients share `BlinkPerformancePolicy` decisions; Android replaces forced 120Hz with Automatic OS scheduling, Balanced <=90Hz, Power Saving <=60Hz, with persisted Settings choices and reduced decoration. Windows desktop does not control system refresh rate; equivalent desktop animation/CPU-saving preference is pending.
- **Screenshots:** Expanded actual configuration-width snapshots at 320/360/390/412dp, light/dark, accessibility font scales, for Home and Notifications. These are test definitions, not inspected/approved captured artifacts.
- **Auth image blocker:** Checked repository bytes: existing approved hero is JPEG 240x177 (6,631 bytes); login photo is WebP 472x302. Interpolation does not restore missing facial/photo detail. Original high-resolution approved artwork is required for a faithful replacement. Do not substitute an unapproved photo.
- **Release:** PR #541 remains draft, not merged, not signed/published. Android/Windows/backend/privacy/CI evidence must be reviewed on final head SHA. No production data, Paystack or deployment changes made here.


## 2026-10-25 — Additional scoped implementation (PR #541, staging only)

- **Android navigation:** Profile → Direct Message now opens the message directly without pre-closing Profile or artificially opening the Messages tab. In-app Back uses the bounded route stack for profiles, private chats and product details. A source-level regression contract checks Profile→DM→Back. Full nested overlay restoration, predictive Back gestures and process death remain release blockers.
- **Android/Windows post mentions:** Both composers now use shared `BlinkMentionTokenPolicy`, shared `BlinkUserSuggestionPolicy.rank`, server-authoritative `search_profiles_page`, 220ms debounce, first ten suggestions, paged See more and selected @username insertion. Android additionally has server-backed Comment and New Message suggestions. Group-member selection uses the Android multi-select picker. Windows group-invitation and reply composer surfaces still require integration.
- **Private group messaging:** `BlinkGroupRoute` is shared JVM logic. Android now reads member-visible group metadata, distinguishes real groups from direct conversations, suppresses synthetic user profile and call actions, loads group history by conversation ID and sends text through the existing authenticated group RPC. It does NOT silently send a group message through the DM RPC and does not automatically retry ambiguous sends. Windows already distinguishes and sends group messages using the same backend. Group media, unread/read receipts, leave/member management and live two-account privacy verification remain open.
- **Supabase:** Added `supabase/tests/staged-market-community-contract-tests.mjs`, a disposable PGlite PostgreSQL contract suite wired into the existing backend CI. Tests apply **draft SQL from docs only** to a synthetic database, checking spoofed sponsored feed placements, user-created Market posts, benign discussions, community owner/private visibility, single-row group summaries and departed-member suppression. These scripts have NOT modified hosted Supabase; no production policy has been applied.
- **Admin:** Android administrative workspace menu explicitly says “Switch to Personal Profile” while retaining server authorization and no logout. Windows equivalent uses existing Admin route navigation.
- **Auth assets:** Existing approved low-resolution hero remains unchanged in GitHub. A higher-resolution crop from the approved design exists as a local draft but has NOT been installed, reviewed on device, or approved as original source quality.
- **Quality:** Only per-commit CI results on the final head SHA count. An earlier green SHA is not certification of new group/mention changes. No main, Testlab, signed APK or production migrations changed. Full production release approval and all integration gates remain mandatory.

- **Windows private group creation:** `DesktopNewGroupDialog` now provides the same verified 3–50 member backend flow as Android: server-filtered account search with ranked 10-result pages, removal chips, group name, and creation via the existing authenticated RPC. The Messages inbox exposes New Group and refreshes conversation summaries after creation. No mock members or synthetic group identities are used. Still requires real multi-account runtime, group permissions, invite/leave and group attachments verification.


## 2026-10-25 — Communities foundation and welcome-photo upgrade (PR #541)

- **Communities parity:** Shared `BlinkCommunityPolicy` validates names, descriptions, categories, universities and rules. Android `BlinkCommunityRepository` and `BlinkCommunitiesPanel` use the actual authenticated `blink_communities` table for discovery, public/private creation, joining, owner invitations and leaving. Android Connect Hub has an explicit Explore/Create Communities action, and the Professional Center can open the Communities tab directly. Windows `DesktopCommunitiesScreen` exposes equivalent real-data discovery, creation, joining, invitations and leaving on a first-class navigation route, using `DesktopSupabaseClient` and the same policy. Server moderation RPCs are drafted, but member management UI/notifications/cover picker are NOT complete.
- **Backend hold:** draft `docs/integration/groups-communities-staging-20261025.sql` now contains owner RLS, invited/private member visibility, no raw authenticated membership writes, and server-checked join/invite/leave/moderate RPCs. `group-membership-security-staging-20261025.sql` proposes protections against private group self-joining, privilege escalation, departure reads, and guarded group-message sends/invites/management. Disposable PGlite tests were expanded. None of these drafts has been deployed as a hosted migration or proven against an isolated full production-compatible baseline, so **the new Communities route is not releasable**.
- **Authentication hero:** Android now reuses the *already tracked* higher-resolution approved campus photo `blink_auth_reference_login_photo.webp` in the welcome screen rather than rescaling `blink_auth_campus_hero.jpg` (240 × 177). The BLINK wordmark is rendered separately as crisp Compose text. Original source and exact pixel-match to approved screenshots still require manual high-DPI review; do not represent this as having recovered the full-resolution photographer original. Desktop's form/campus imagery is an intentional desktop-layout variation.
- **Testing:** Relevant Android/Windows CI and local PGlite syntax/RLS checks must pass on the final SHA; no phone screenshots, real two-account role moderation E2E, producer permissions, latency/memory or production backend checks have been passed or claimed.


## 2026-10-25 — Group/community administration parity and current hold

- **Android Groups:** Real group roster management is wired into Professional Center Groups via `BlinkGroupManagementPanel`. It loads only the current member's RLS-visible conversations and calls staged role-checked `blink_group_members`, `blink_group_invite`, `blink_group_leave` and `blink_group_manage_member` RPCs for active members, join/leave and admin role changes. Existing New Group creation is preserved.
- **Windows Groups:** `DesktopGroupManagementDialog` in Messages opens the real active-member roster rather than interpreting a group as an individual's contact card. Owner can remove/promote/demote, members can leave, admins can invite via live filtered account search. Desktop client calls the same staged backend RPCs. **Hosted RPCs do not yet exist** and these screens are not functionally live; final cross-device tests required.
- **Communities:** Both Android and Windows now support requesting membership in private spaces (pending until approval), owner member/request rosters and approving/removing/banning/promoting/demoting through the same staged server permission model. Live data sources only; no fake members or balances.
- **SQL local QA:** Corrected a real staged-community SQL unterminated `$$;` delimiter. Expanded synthetic PGlite fixtures to include production-like group fields; tests for requests, private visibility, owner approvals, post-ban denial, owner-only roster, blocked users, departure read denial and founder-leave guard. The exact head commit must pass CI to validate these edits; previous completed jobs on outdated commits do not count.
- **Release hold:** Production Supabase migration not deployed; no connected Supabase preview branches; no green CI evidence for final head, and Android/Windows real two-user runtime, media, image visual signoff and Paystack regression unverified. No Testlab/main merge or APK publication. Do not enable new UI in a production APK until backend permissions and availability are validated.


## 2026-10-25 — Group ownership transfer parity (PR #541; staging only)

- **Shared backend contract:** Added `blink_group_transfer_owner(p_conversation_id, p_new_owner_id)` to staged group security SQL. Only the authenticated creator can transfer to an already active member; new owner receives admin role atomically, and former owner can then leave. Unauthorized and nonmember successor attempts are covered in the disposable security suite.
- **Android:** Professional Repository exposes `transferGroupOwnership`, and the group management panel presents a confirmation dialog for the existing owner before transferring to a chosen member. Following confirmation it refreshes roster and current ownership.
- **Windows:** Desktop Supabase Client and group-management dialog use the same RPC, owner restriction, confirmation and membership refresh. No temporary client-side ownership state substitutes for the server.
- **Release blocker:** This is a staged, **not deployed or verified** backend contract. It remains unreleasable pending CI on the final SHA, full database staging/RLS proof, real two-account tests, all required user-facing parity checks and safe Testlab reconciliation. No signed APK has been published.


## 2026-10-25 — Private DM and consent-first group foundation (staging only)

- **Direct messages:** Staged SECURITY DEFINER replacements for `send_message` and `get_or_create_direct_conversation` now require BOTH participants active (`left_at IS NULL`), preserve the legacy RPC signatures and block rules, and create a new chat rather than silently restoring departed history. Group notification trigger is staged to fan out to all active recipients only.
- **Group consent:** Staged `create_group_conversation` still validates the selected 3–50 accounts but activates ONLY the founder; other selected users receive server-owned pending invitations. `blink_group_invite` now queues, not instantly enrolls, a new member. `blink_group_pending_invitations` and `blink_group_respond_invitation` enforce self-only accept/decline, existing server rights, blocks, and 50-member cap. Declined members remain outside group history.
- **Android parity:** `ProfessionalRepository` and `BlinkGroupManagementPanel` display actual pending invitations with Accept/Decline; new-group feedback describes recipient consent.
- **Windows parity:** `DesktopSupabaseClient`, `DesktopGroupInvitationsDialog`, and `MessagesScreen` expose an equivalent recipient inbox and decisions. New Group and invitations dialogs are outside `AnimatedContent`, so the inbox's early return cannot suppress them. Creation UI clarifies invitations.
- **Risk / gating:** All backend files remain in `docs/integration/*-staging-20261025.sql`, not migrations or hosted production. Synthetic PGlite tests have been extended, but final-head CI and end-to-end live multi-account verification have NOT passed. A full production-compatible staged RLS rollout, true message media/receipt parity and regression testing remain mandatory.

## 2026-10-25 consolidated release integration

- Integrates PRs #540, #541, #542, #543 and #544 on the current-main-based Testlab candidate. Both Android and Windows retain referrals, community/group controls, and private chat-media implementations.
- Unifies group invitations into the conversation_id/invitee_id/inviter_id schema. Referral RPCs retain their wire response names through compatibility wrappers; acceptance shares the hardened group consent function.
- Preserves group retry/profile/call safeguards while adding attachment cancellation, durable retries, albums and view-once adapters. Removes duplicated desktop account/invitation state from overlapping branches.
- Local PostgreSQL fixture tests are distinct from full hosted Auth/Storage/device verification; this candidate must pass final-head Android/Windows CI and release/backend gates before promotion.

## 2026-10-24 — Single visible BLINK profile (Testlab review required)

- **Android:** The new full-screen reference dashboard is now the only profile cover and identity surface. Removed the obsolete expand/collapse switch that rendered a second old cover, avatar, name, ranks and trust banner. Profile tabs, posts, reels, Market, Skills, About, private owner activity and refresh remain outside the removed block.
- **Relationship capabilities:** Migrated the original server-backed Follow → Interact actions (message, unfollow, coin gift with idempotency key, game challenge, mentor, friend, roommate, study mate and Connect Hub) to the new reference dashboard for both narrow/accessibility and ordinary phone layouts.
- **Windows:** Desktop already renders a single `DesktopUserProfileDialog` identity section, so there is no duplicate header to remove; it continues to use the shared account, profile and follow backend. There is no equivalent obsolete Android panel on Windows. This is an Android-only visual de-duplication and preserves existing Windows functionality.
- **Release gate:** Main remains untouched. Verify latest-commit Android builds, screenshot QA (including 320dp/200% font), owner/visitor content tabs, follow/interact actions, avatar/back navigation and Windows parity before merge or APK publication. No Supabase production change.

**PARITY-EXCEPTION: profile-single-surface** — Android removes an accidental second, obsolete profile header after the current reference dashboard. Windows already renders exactly one profile header in `DesktopUserProfileDialog`, so duplicating this Android-specific removal in Windows would be meaningless. Equivalent Windows behavior is the existing single header and unchanged profile actions.

- **Main reconciliation:** On the conflict-resolution branch, retained the newer main profile features (including the owner Refer & Grow entry, initial tab/deep link behavior and current profile handlers) while removing only the redundant second cover/identity/trust surface. This is still a draft PR; no production merge or deployment.

## 2026-10-25 — Inbox compose entry uses existing conversation chooser

- **Android:** The Messages compose action now opens the already-implemented `New conversation` dialog instead of silently navigating directly to Search. Its existing actions lead to student lookup and groups/invitations, without altering server contracts, identity, media or permissions.
- **Windows:** The desktop app retains its platform-specific messaging and invitation navigation rather than reproducing an Android-only alert. Verify Windows new-chat and invite actions in current-head desktop tests before release.
- **Parity:** User outcome is to start a direct conversation or manage group invitations; this is a platform-specific navigation repair, not a new backend feature. No Windows-side code or shared API change is introduced. This note is not runtime verification.

**PARITY-EXCEPTION: android-inbox-compose-existing-dialog-20261025** — Android's Messages screen now opens its already-existing native Material `New conversation` chooser via `showMessageComposeOptions`. Windows does not render this Android-only dialog; its existing desktop new-chat and group-invitation routes provide the equivalent user actions. No shared API, data model, authorization or feature semantics change. Verify those routes in desktop runtime tests.

## 2026-10-26 — Refer & Grow push reliability (Testlab only)
- **Shared backend:** tightened the server-only referral push worker acknowledgement contract. Retries now correctly preserve transient delivery skips and report a stale/non-persisted outbox lease as an error. Hardened the scheduled outbox drain's response validation.
- **Android:** no Android-specific UI, API shape or behavior change. Receives the same Supabase-delivered referral notification results when activated.
- **Windows:** no Windows-specific UI, API shape or behavior change. Shares identical referral attribution and backend notification semantics.
- **Web:** no web-specific UI, API shape or behavior change. Existing referral landing and dashboard functionality stays intact.
- **Backend gate:** no production schema/Edge Function deployment or Coin reward activation in this change. Production-compatible staging, RLS, multi-account signup and cross-platform acceptance tests remain mandatory before main promotion.


## 2026-10-26 — Refer & Grow completion: invitation and fraud-aware discovery
- **Shared Supabase contract:** when a referrer's confirmed registrations trigger an unresolved abuse-velocity review, pause their temporary Search and new-user recommendation boost without erasing permanent confirmed-referral achievements; restore after the review is resolved. Referral Coin rewards remain disabled. The public RPC schemas and existing Android/Windows response fields are unchanged.
- **Android:** uses the same existing discovery RPCs; no Android-only UI changes. Temporary referral boosts return zero while under review.
- **Windows:** same shared discovery semantics and public RPC contract; no Windows-only UI changes.
- **Web:** a personal invitation link now opens a safe, optional preview instead of forcing login, verifies the referrer's public username, retains attribution only for eligible new signups, and never attributes existing users. Updated wording reflects live impression/source analytics.
- **Verification:** new isolated PostgreSQL fraud assertions and Node browser-flow tests. Real email-confirmed signup, physical device notification delivery, Coin activation and production job secret configuration still require independent evidence.

## October 26, 2026 — Non-student signup option (Testlab)
Android and Windows allow a self-declared `NON_STUDENT` account type. Selecting it disables and clears campus/department/level entries. Both use shared onboarding validation and an additive database constraint migration. Campus-specific access is NOT conferred by self-declaration.

## October 26, 2026 — Phone OTP signup (Testlab candidate)
Android and Windows both request a one-time SMS verification when a new user completes onboarding; phone normalization lives in shared Kotlin. This requires a properly configured Supabase Auth SMS provider and must be verified in isolated tests before production. No phonebook/call-log permissions or contact matching are enabled by this change.

## October 27, 2026 — Contact discovery Android permission
Android asks for READ_CONTACTS only after the user presses Find My Friends in a clearly optional consent card. No READ_CALL_LOG permission is requested. The privacy card is available on Connect and at the end of new-user signup. Windows must provide the server-backed equivalent without reading device contacts.

## October 27, 2026 — Windows contact discovery parity
Windows Connect displays matches saved to the same Supabase account, supports opt-in discoverability and contact-joined alerts, allows refresh and removal, and navigates to matched profiles. Desktop does not request Android Contacts permission or infer access to a paired phonebook. Contact sync requires the explicit Android user action.


## 2026-10-26 — Consolidated Reels/Profile/Wallet/Intro Testlab candidate

- Shared launch wordmark uses one vector B and consistent LINK letter height and baseline; Android native splash uses window drawable and Android 12 icon. Windows has an existing native launch screen, so Android XML is a platform-specific adapter.
- Android Profile published posts now reuse the same real PostCard as Feed; Reels have three tiles per row with view overlays. The existing DesktopUserProfileDialog offers native posts/reels tabs but needs equivalent three-column Reel thumbnail verification before promotion.
- Android Wallet reduces vertical padding, balance hero, rows and quick actions, adding status bar-safe back navigation. Backend coins/HP and purchase prices are unchanged; Windows wallet uses same economy policy but compact-width UI requires QA.
- Android Reels fit media instead of zoom cropping and lift progress above the comment composer; the featured-banner interval is four seconds while active. Check playback continuity, banners and overlays on physical low-end phones.
- Native BLINK share picker uses real recent chat recipients, suggested users and the same authenticated send-message route; offers copy, Story-editor-with-copied-link, WhatsApp and Android chooser. Stories do not automatically publish links until editor attachment supports them. Desktop currently has a separate share-link workflow, still needing UI parity.
- Candidate must NOT merge without final-head Android/Windows builds, UI interaction tests, platform parity, and device validation.

- **Story sharing refinement:** The native BLINK share action now opens the real Story editor with the selected Reel/Post URL prefilled as a caption. Media still requires user selection and explicit Publish; it never auto-uploads someone else's Reel or bypasses permission/asset-copy rules. Android-only editor adapter; Windows keeps its copy-link action while cross-client composer parity is pending.

- **Reel poster reliability:** Android now extracts and uploads a bounded JPEG poster from locally selected Reels using the existing authenticated post media image contract. Reel publication is not blocked if a specific video cannot supply a frame; legacy Reels attempt Coil video-frame decoding lazily. Windows profile grid shows posted cover images instead of blank tiles, keeping no-external-video-download behavior for video-only legacy posts. Require actual Android permission/storage tests and Windows screenshot checks before main.

- **Windows in-app sharing parity:** Desktop Reels and Profile Reel tiles now offer a recipient picker using the authenticated conversation RPC, real recent DMs, optional suggested profiles, searchable handles and server-authorized message sends. Clipboard copy stays available. WhatsApp is an external URL through the platform browser rather than Android intents, and Windows has no device-native Story creation flow in this patch. These differences are platform capability distinctions, not production mocks.

- **Direct Reel→Story card:** Android now creates a locally rendered 9:16 branded Reel share card and inserts the verified BLINK permalink into the Story caption, while keeping explicit user Publish and the existing server-confirmed Story media upload. This avoids copying/reuploading another creator's video or using untrusted remote images. Viewers already see a rich clickable link preview for caption URLs. Windows Story creation remains a platform exception (no native Story composer in desktop yet). Inspect image generation with visual/device tests.

## 2026-10-26 — PR #598 integration follow-up

- Conflict in BlinkWalletPremiumContent resolved by retaining main's elastic right-swipe gesture and Testlab's compact hero/header, status/nav insets and five wallet views.
- Android and Windows share pickers now debounce authenticated server-side profile directory searches rather than only filtering cached suggestions; existing recipient ACLs apply. Android multi-send queues one explicit link per selected recipient; queued is not falsely reported as delivered.
- Coins plus button has a 48dp interaction box with its original 32dp visual footprint.
- This change does not replace real-device playback, high-font visual reviews, recipient delivery verification, desktop native Reel playback or the Windows Story composer. Re-validate final merge commit before promoting to main.

## PR #606 launch guard (October 27, 2026)
- Signed-in SMS verification entry is retained, but it is temporarily optional on both Android and Windows via `BlinkPhonePolicy.REQUIRE_VERIFIED_PHONE_IN_ONBOARDING=false`, because the production Supabase Auth SMS provider is not independently verified. Do not turn on mandatory verification until configuration and deliverability testing pass; signups must not be blocked.
- Contact discovery still requires separately deployed private migration, HMAC secret and Edge Function. Before backend activation, the optional suggestion control should report unavailability rather than upload any data, and call logs are never requested.

## 2026-10-26 — Chat media viewer gesture and action foundation (Testlab draft)

- **Shared:** Added `BlinkChatMediaGesturePolicy` for directional thresholds, ambiguous diagonal rejection, zoom lock, and opacity calculation, with unit tests. No server-side/media visibility rules changed.
- **Android:** Chat composer now places emoji, paperclip, camera and voice button in reference order. Conversation-only dark palette uses charcoal incoming and deep-green outgoing bubbles without changing inbox/theme settings. Normal downloaded/sent picture viewer now handles one-finger vertical up/down dismissal, left/right conversation paging, two-finger zoom with pan, message position display, and a menu workflow which can download a photo after explicit confirmation then resume the selected edit/share/gallery/sticker/AI/profile/wallpaper action. Saved picture remains an independent copy. Normal gallery browsing combines loaded and historical authorized pictures; view-once/deleted messages stay excluded.
- **Windows:** Dark chat bubbles have matching deep-green/charcoal colours while light-theme bubbles retain existing presentation. Retains existing previous/next and downloaded-copy actions; adds left/right keyboard navigation and Escape dismissal as the desktop equivalents of touch swipes.
- **Not yet verified:** Exact visual screenshot parity, actual on-device pointer competition/double-tap behavior, CI builds and end-to-end Android↔Windows transfers. An opt-in Telegram-reference three-column gallery, up-to-10 photo selection, Media3 chat video trimming/filter/music/export, audio playback and PDF first-page document review, and photo editor undo/redo/presets have been added in this PR; remaining native poll/live location, secure Media Vault, complete advanced video/audio effects, and exhaustive device screenshot and privacy testing remain outstanding.
- **Promotion gate:** Testlab branch first; no `main` merge, live database edit or signed APK publication without green applicable Android, shared and Windows checks plus device gesture verification.

## October 27, 2026 — Profile reliability and polish (Testlab candidate)
- **Shared:** A reusable ProfileCompletionPolicy accounts for student and non-student requirements instead of treating optional contact and cover fields as mandatory. Tests exercise its student/non-student cases.
- **Android:** Profile swipes now switch the visible primary tabs; owner Liked/Saved items open through the existing full-post action; the avatar can be viewed full-screen independently of a Story; the Edit button is directly accessible. About displays saved academic/work fields and hides the academic section for NON_STUDENT accounts. Editor restores selected media URIs and visibility settings across supported activity recreation, uses scrollable relationship chips and consistent completion, clears stale academic fields on save for non-students, and shows user-friendly media limits. Owner analytics explicitly labels counts from loaded posts.
- **Windows:** The desktop profile now offers an owner-only basic Edit Profile dialog for name, headline, role, bio and academic details, using the same shared completion and validation policies and existing authenticated profile PATCH endpoint. Theme selection is reached from Edit Profile. Loaded-post count and About details now reflect current fields. Android-only photo picker/activity restoration and Story/touch swipe are platform-specific; desktop media editing, contact visibility settings and photo viewer UI still require separate native parity validation.
- **No backend/production mutation:** Existing profile RPC and privacy policies are unchanged. The profile-visit-history opt-in RPC integration remains pending and requires staging validation before activation.
- **Promotion gate:** Run latest-head Android unit/compile/lint and Windows compiler/installer/parity checks, small-screen/large-font screenshots, owner/visitor/profile image interaction tests and runtime verification before merging main. Do not claim production delivery from static checks alone.

## October 27, 2026 — Consent-based profile visitor history (Testlab only)
- **Shared:** Added identical typed settings, visitor, and snapshot contracts across Android and Windows.
- **Backend contract:** Reuses existing staged `20261018025000_opt_in_profile_visitor_history.sql` authenticated RPCs. The server enforces reciprocal opt-in, profile blocking, de-duplication and owner-only visitor identity. Turning off history deletes identifiable events; there is no fallback to activities or nonconsenting identity exposure.
- **Android:** Profile visitors opens an owner-only preference and history sheet. Enable is an explicit user action, alerts are separately opted in, and disabling requires deletion confirmation. Views on other accounts call the guarded opt-in record RPC; missing/undeployed RPCs leave the history unavailable rather than lying about a user’s privacy choice.
- **Windows:** Owner-only visitor dialog supports the same enable/notify/erase/history functions using authenticated RPCs. Visiting another profile uses the same mutual-consent record call. No client-side fake visits.
- **Staging/release gate:** Backend migration and RLS/GRANT behavior must be verified in free isolated/staging PostgreSQL with two real authenticated test identities, blocked relationships, opt-out erasure and 24-hour alert de-duplication. A commit in GitHub does NOT deploy the migration. No paid staging or production-first experiments. Android and Windows build and privacy acceptance checks must pass before promotion.

## 27 October 2026 — Profile finalization candidate
- **Security:** Added migration with an explicit `get_profile_detail` allowlist instead of serializing the entire profiles row. Contact values and scopes remain visible only to authorized viewers, and blocked users receive no profile. Internal payment, onboarding-secret, moderation and session fields are not serialized.
- **Backend totals:** New authenticated, blocked-aware `get_profile_content_metrics` aggregates active/unflagged feed Posts and Reels, views, likes, comments and shares across the full account; Android and Windows fall back to honestly labeled loaded counts if the RPC has not yet migrated.
- **Android:** Owner can edit comma-separated skills, display names no longer require global uniqueness, blank relationship statuses no longer falsely dirty the editor, relationship status shows only for owner, market empty state is non-student inclusive, full profile screens expose larger touch targets and visitor history acknowledges viewed updates.
- **Windows:** Self-target actions are no longer shown on own profile, follow/mute failure is surfaced. Owner editor includes local JPEG/PNG/WebP avatar/cover selection, social links and separate email/phone/WhatsApp visibility; visitor sheet includes avatars, Profile open controls and read/seen counts; server totals take priority over loaded content counts.
- **Verification:** Disposable PostgreSQL contract suites now cover visitor opt-in and projection field privacy. The production visitor schema is already present; the new public projection and full-content RPC migration are **not** deployed until Testlab quality and safety gates pass. Native window/font-scale/screenshot parity remains a separate release acceptance task.

## 2026-10-27 — Feed professional audit repairs (PR #626, Testlab)
- **Android Home:** use one canonical per-card `record_content_view` exposure path instead of also invoking the legacy `record_post_view` list observer, preserving the separate paid Boost delivery event. Show existing Photos/Polls filters in the pinned tab row, keep filtered results free of unrelated Reel/Drop/advert insertions, recover from page-load errors and expose next-page action when the current filtered page is empty. Following obtains independent loading state; list cards provide safer Follow → Interact menus and do not unlike already-liked posts on photo double-tap.
- **Windows Home equivalent:** adds real Photos and Polls filters against fetched content. Poll presence is loaded with one authenticated, bounded poll-metadata query per page; no fabricated polls or counts are shown. Filtered empty pages offer recovery and a next-page action; the end of Feed is distinguished from a fetch failure. Windows retains its desktop-native viewport, mouse/keyboard navigation and explicit refresh, with no Android-only gesture emulation.
- **UI-only platform difference:** Android uses a 48dp touch filter control and gesture-based pull-to-refresh; Windows uses FilterChips and a clickable Refresh button. Each maps to the existing feed list and server-backed pagination. The Windows content card's poll voting UI is not introduced by this focused Feed repair; the Android native poll component continues to render voting in its existing surface.
- **Validation:** tests must pass on the final PR head, including Android screenshot/instrumentation tests and the Windows desktop parity/build gate, before main promotion. No production database migrations or release artifacts are introduced by this PR.

### Completion follow-up — remaining Feed repairs
- **Android:** boosted Feed PostCards now pass live poll-vote actions and use the canonical content-view exposure coordinator, while the separate paid Boost-delivery event remains unchanged. Photos/Polls filters exclude all inserted Reel/Drop/Boost/native-ad rows, return to the top when selected, and prefetch at most three additional existing backend pages if few matching posts are visible. Explicit Unfollow removes the creator from cached Following rows only after the canonical server RPC succeeds; an RPC rollback retains posts.
- **Chronological Following:** quiet, initial, and manual refresh reconcile the new first page with older cached pages only if the fetched tail joins an actually observed item in the previous timeline. Otherwise a disconnected cache suffix is dropped instead of fabricating missing pages. In-flight pagination cursors are invalidated on quiet refresh and tests cover contiguous, disjoint, short, and duplicate pages. Missing rows in a bounded server page are not by themselves treated as deleted.
- **Windows:** desktop Home excludes Reels and legacy video-bearing posts in the query and local list, performs bounded auto-paging for filtered Photos/Polls results, restores scroll to the top for each filter and reports poll metadata failure with a retry instead of misleadingly reporting zero polls. Home still uses the server's existing authenticated Feed and poll endpoints, no new database schema, and no mobile gesture clone.
- **Limitations:** photo/poll discovery is intentionally capped rather than downloading an unbounded history; there is no new dedicated server-side media-only query. The latest PR head still requires its normal build, unit, instrumentation, visual and release gates before merging.

### Profile repair merge reconciliation — 27 October 2026
- Integrated main after Feed PR #626 without clobbering its Windows Photos/Polls metadata handling, DesktopFeedPost.hasPoll, Android feed changes or feed parity notes. The profile-security migration and all Testlab profile changes remain isolated until latest-head Android, Windows and PostgreSQL gates pass.

## 2026-10-29 — Create Post desktop native media adapters (Testlab #646)

- Retains shared publishing permissions, media limits, draft and server contracts; Android's existing photo/Media3 adapters remain unchanged.
- Windows now edits local photos (EXIF, crop/rotation, filters, adjustments, text/symbol/doodle, undo/redo, PNG/JPEG) and exports ordered/trimmed multi-clip H.264/AAC video with aspect padding, speed, filters, text, volume/music and cancellation cleanup. Native FFmpeg dependencies are pinned and included in packaging.
- Uses desktop mouse/file dialogs and OS playback of actual exported files. Mobile timeline, draggable overlays and advanced animated effects remain differences requiring further work/visual verification.
- Local desktop suite passed 36 tests; synthetic media CLI smoke passed. New packaged Windows CI, device screenshot comparison and isolated Supabase Auth/Storage/Realtime checks remain release gates. No production backend changes or main merge. See `docs/integration/verification-20261029.md`.

## 2026-10-29 — Reference comparison and isolated service verification (#646)

- Android and Windows share reference choice gradients and Reel clip/duration constants. Both show the reference Aa/photo/video/bar-chart affordances with the yellow poll icon's dark foreground.
- Device and local Robolectric Create Post captures now use the same shared test fixtures, covering the complete existing 12-case visual matrix without shipping test profiles/media in the app.
- Added a disposable Supabase HTTP suite for actual password sessions, Contest RPC/concurrent votes, Storage ACL/public preview, Realtime notification delivery, Chat polls/block privacy and posting guards. The administrative/prerequisite schema remains synthetic; this cannot establish deployed-schema compatibility or two-device E2E.
- Local desktop 36 tests and Android visual fixture 15 tests/instrumentation compilation passed. Explicit screenshot recording and the new service integration CI still require inspection/results before merge. Full local Supabase startup exceeded disk capacity; no production resource was contacted or changed.

## 2026-10-29 — Verified Storage deletion and reference layout repairs (#646)

- Actual isolated Supabase run 40958927594 passed real Auth/PostgREST/Storage/Realtime feature assertions. Added owner-only Storage SELECT policy after the service test proved that INSERT/DELETE policies alone silently retained owner files. No production database changes.
- Android reference editor now lays out header/body sequentially instead of overlapping AnimatedContent siblings, uses compact swatches/tags, preserves advanced tools behind More tools, renders readable standalone poll controls, and keeps all six photo tools visible. Windows keeps its desktop column/layout and identical single-choice semantics.
- Shared native device/Robolectric Chat fixtures capture the actual album, voice, video, document, poll, reply and reaction components at three scroll positions. Shared aspect constants keep photo/video previews landscape; Windows retains its desktop message layout and OS media playback.
- Windows photo export now supports crop framing and movable text/stickers with verified pixel placement; the packaged diagnostic additionally checks actual Windows DPAPI encrypted draft persistence/isolation. Local desktop 37 tests and Linux media CLI passed; new Windows native verification must run on the final candidate.
- All 18 local Android reference fixture tests and device-test compilation passed on the final UI source; dark/light outgoing-file captures and compact media metadata were inspected. Android lint passed on the preceding layout repair. Repeat applicable checks on the final candidate. Captures use synthetic media; full two-client/device acceptance and deployed-schema compatibility remain distinct release gates.

## 2026-10-29 — Outgoing timestamp contrast across surfaces (#646)

- Android light appearance uses its outgoing foreground for metadata rather than an unrelated surface color. Windows now uses onPrimaryContainer for outgoing light-bubble text/metadata, keeping the same readable result with its native palette; dark reference colors remain governed by the shared contract.
- Native Android runtime failures are exposed as check annotations and retained reports so a real failing device case can be diagnosed even when the environment blocks artifact downloads. This does not waive the failing check or permit promotion.
### Packaged Windows editor visual diagnostic (2026-10-29)

`--reference-ui-smoke <empty-directory>` captures real desktop photo/video editor
windows with explicitly synthetic media. It does not construct app state, load
accounts or call Supabase. This Windows/runtime adapter complements Android's
existing instrumentation visual fixtures; it changes no feature contracts.
Local packaged Linux captures were inspected. Windows CI captures require their
own run and manual artifact review; capture completion alone is not visual approval.

## 2026-10-30 — Reaffirmed gallery and conversation references (#646)

- Android's in-app photo grid now uses the same permission-scoped MediaStore adapter as Chat, with selected-photo preview actions outside the thumbnails. Windows uses its native file dialog for library access and keeps selected-photo actions below the cover label so they fit their columns. Both use the existing shared selection cap and unchanged publishing contract; browsing does not upload anything.
- Shared light conversation colors remove unexpected pink from Android Light appearance and supply the same pale blue outgoing background/dark foreground on Windows. Charcoal default and optional Pink personalization are preserved.
- Native Android compositor capture coverage now includes the actual keyboard/mentions, real permissioned MediaStore selection, video preview and trim timeline. Windows' existing packaged GUI captures complement those platform-specific checks. New capture tests and fresh CI require execution and manual review before any visual approval.
- See `docs/integration/reference-followup-20261030.md` for exact scope and remaining differences. No backend migration or production operation is part of this follow-up.

### PARITY-EXCEPTION: android-reference-loader-isolation

The Android composer accepts an optional trending-tag loader so native and Robolectric fixtures can supply test-only tags without the default repository request. Production continues to use its existing loader. This changes no user feature, permission, payload or backend contract. The Windows equivalent is its existing injected HTTP tests and explicit account-free packaged GUI diagnostic; Android's native IME/Media3 capture assertions are platform-specific. Mention visibility is now asserted on screen in addition to loading results.

## Testlab — notifications, profile, splash, Drops/Boost and Reels (2026-10-31)

- Notification display and event type improvements use shared notification presentation and classification rules for Android/Windows. The Android activity list gets readable rows, real social actor labels and new message/contest/reward filters; Windows uses the same wire aliases and notification classification contract.
- The approved splash drawing receives a proportional B correction in the shared brand Compose asset, so Android/Windows surfaces using that asset agree. Android-only startup animation timing remains platform-specific.
- Android Reels removes the Home Stories strip, uses the existing server-ranked featured-video carousel and expands with preview-muted/immersive-audio playback. Desktop has its own layout and native audio controls; this is an explicit form-factor exception, not a change to shared ranking data.
- Profile stat ordering and owner/public action visibility remain server-derived; Android applies the saved profile card color to the owner's posts. Desktop uses its own layout while preserving the same public rank/HP information and owner-only coin controls.
- Drops and Boost keep the same secure backend contracts. Android now exposes refresh and page errors rather than falsely treating failed calls as empty data; Windows' corresponding failure/empty states remain separately implemented.
- Merge remains blocked until Android/Windows tests, actual Android screenshot comparisons and relevant live-service checks pass. No production data or environment settings changed in this patch.

- The existing idempotent coin-gift RPC already creates a receiver notification. A stage-only, receipt-keyed trigger now emits a sender receipt without duplicating notifications on retries. Android and Windows use the shared `tip` event alias; a coin-gift notification opens the wallet on Android. The SQL migration is NOT applied until disposable staging/RLS/notification delivery validation succeeds.

- Drops Top Givers now separates the actual followed state from global follow-request busy state; an unrelated account no longer appears as Following. The empty leaderboard offers genuine Browse/Create actions without synthetic rankings. This is Android-specific layout polish over existing shared backend semantics; Windows continues using its own native empty/action presentation.

- Coin-gift sender receipt trigger now has a dedicated GitHub Actions isolated PostgreSQL fixture that tests reapply, duplicate gift requests, recipient/actor IDs, metadata and deny-execute ACL; the fixture never connects to live Supabase and does not validate production dispatch or user RLS end-to-end.


### PARITY-EXCEPTION: android-drops-boost-dark-card-contrast-20261031

- Android-only contrast hardening sets explicit light foreground colors inside the dark Drops empty/podium cards and Boost quote/analytics cards. This is a native Compose presentation correction; it does not change the shared data model, API, eligibility, pricing, balances, transactions or user actions.
- Windows equivalent: the desktop Drops and Boost surfaces retain their existing native theme-aware text rendering and separate layout. The parity contract is identical data, actions and readable foreground/background contrast, not pixel-identical native card styling. The Windows parity/build gate verifies its existing desktop implementation independently.

### PARITY-EXCEPTION: contest-android-native-compose-presentation-20261101 (latest Contest CTA audit)

- The Contest PR updates Android's native Compose presentation (animated cards, chips, admin dashboard layout, applicant media preview and share-flyer dialog). These are Android-native screen treatments; contest eligibility, moderation, ranking, voting, prize data and service contracts remain shared/server-authoritative and are unchanged by the presentation work.
- Windows equivalent: retain the existing Windows-native Contest screens and their equivalent contest data/actions using desktop layout and controls. Pixel-identical Compose animation and Android image-picker/player/dialog behavior are not required on Windows; functional parity remains the same contest lifecycle, candidate moderation, free voting, leaderboard, sharing/link and prize-status capabilities. Windows parity/build checks still run independently.

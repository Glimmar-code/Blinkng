# BLINK — Feature completion and regression tracker

**Baseline:** 25 October 2026; production `main` was `10509492f247301aa2db0a66f974b3e1094c7f4e` when this feature branch was created.
**Scope:** Android, Windows, web, shared code, Supabase and production release.
**Evidence rule:** `Implemented` means code exists. It does **not** mean `Tested`, `Backend verified`, `Merged` or `Published`.

## Status codes

- **UNKNOWN** — requires a current-head code/runtime/backend inspection; do not assume absent or working.
- **PARTIAL** — verified existence of some implementation, but acceptance evidence incomplete.
- **BLOCKED** — explicit missing dependency, release safety gate or failed test.
- **VERIFIED** — acceptance checks passed on the *same exact revision* with recorded evidence.

A feature can only be marked `Published` after checking a signed APK matches a known `main` SHA, installs and launches, and publication is confirmed. Preserve Windows parity, older clients and working behavior.

## 25-feature acceptance matrix

| # | Feature | Baseline | Acceptance evidence required |
|---|---|---|---|
| 1 | Adaptive layouts | UNKNOWN | 320/360/390/412dp at 100/130/150/200% font; screenshot and emulator evidence |
| 2 | Dark/light theme | UNKNOWN | All primary screens, sheets and overlays match shared color tokens |
| 3 | Keyboard/insets accessibility | UNKNOWN | IME, cutout, gestures, nav bars and accessibility checks |
| 4 | Five-tab navigation/Back | PARTIAL | Home/Reels/Market/Connect/Message, deep links and account-safe back stack |
| 5 | Session/account persistence | UNKNOWN | Force-stop, restart, account switch, logout and token refresh |
| 6 | Feed post types and controls | PARTIAL | Text, image, poll; accurate counts; safe delete and image viewer |
| 7 | Feed freshness/pagination | UNKNOWN | New posts appear through normal refresh; no duplicates or regression |
| 8 | Feed/Marketplace separation | BLOCKED | Staged backend rule reviewed and tested; only authorized sponsored feed entries |
| 9 | Reels reference layout | PARTIAL | Approved comparison, playback and all controls at target display sizes |
| 10 | Reels fullscreen transition | PARTIAL | No player reset/black frame; upward/downward/tap/hold/back gestures |
| 11 | Message photos/videos/voice/docs | PARTIAL | Two-account Android↔Windows sends, opens, retry and offline recovery |
| 12 | Durable media upload/cancel | BLOCKED | Account-scoped Android jobs and real Windows-after-exit behavior tested |
| 13 | Hybrid image previews/albums | PARTIAL | Real lightweight thumbnails, grouped albums, on-demand originals |
| 14 | Server-authorized view-once | BLOCKED | Atomic access, participant checks, expiry, replay/retry/RLS tests |
| 15 | Messaging receipts and privacy | PARTIAL | Delivery/read state, blocked users, Storage RLS and account switching |
| 16 | Referral onboarding/attribution | PARTIAL | Referrer field after birthday and two-user registration/confirmation |
| 17 | Referral milestones/fraud | BLOCKED | Idempotent credits, abuse rules and hosted backend confirmation |
| 18 | Referral impression/conversion analytics | UNKNOWN | Search and recommendation attribution; count accuracy and access rules |
| 19 | Search/trending/mentions | PARTIAL | Exact-match ranking, 3 users, 5 tags, paging, real 30-day windows |
| 20 | Connect discovery | UNKNOWN | Ranking, caching, privacy/blocking and account-switch concurrency |
| 21 | Group messages/invitations | BLOCKED | Backend member-list contract, role checks, accept/decline, both clients |
| 22 | Communities and moderation | BLOCKED | Public/private joining, moderators, RLS, invite flow and both clients |
| 23 | Marketplace seller authorization | BLOCKED | Verified seller enforcement, expiry, sponsor/payment permission, SQL staging |
| 24 | Authentication/profiles | PARTIAL | Login, Google auth, onboarding, visitor UI, image quality and parity |
| 25 | Performance, CI and release | BLOCKED | Crash/perf evidence, Android/Windows/web builds, hosted backend gates and signed APK |

**Status in this table is a planning/evidence status, not a definitive real-world verdict.** Reconcile each row against current `main` rather than relying on older draft PR descriptions.

## Existing work and risks to preserve

- PR #523 (merged) carries messaging/media changes, but its own scope notes end-to-end Windows parity, private media policy and real-device checks as unresolved. Do not infer these tests passed from merge alone.
- PR #540 (merged) carries Refer & Grow work; its notes warn that hosted migration/registration/push tests were not established. Re-check live backend capability before enabling dependent rewards.
- PR #541 was **closed without merge**. Recover unique work selectively after comparing each changed file with current `main`; do not blindly re-merge divergent history.
- PR #566 (open when this tracker was started) changes many workflow triggers to manual-only. Before merging it, verify that mandatory PR status contexts remain obtainable and that queue workers/notifications do not silently stop.
- Historic `Testlab` was retired from the latest `main` routing. For the current project policy, use a clean feature branch off protected `main` and isolate/test risky backend changes outside production. Do not force-reset stale Testlab branches.

## Small repair in this candidate

The Android Messages compose callback was routing straight to Search while the screen already contained a `New conversation` chooser. This branch connects that callback to the existing chooser. **Android runtime verification is not yet recorded.** Desktop already has its own separate group-invitation and messaging controls; validate equivalent user navigation before marking feature #4 or #21 verified.

## Required evidence for each feature

For each row, record in a PR/release note:
1. source SHA, owner, affected Android/Windows/web/backend surfaces and a platform-parity decision;
2. actual failing behavior and acceptance scenario, not just an implementation claim;
3. tests executed on that same SHA (job URL/artifact, result, and screenshots when UI);
4. separate hosted/staged Supabase runtime evidence when necessary (RLS, Storage, Auth, migrations and workers);
5. compatibility/roll-back plan, merge SHA and signed release SHA/link when published.

If required checks cannot run, remain failing, or are not associated with the latest commit, **hold the merge** rather than overriding protection. Do not use live accounts or production data as the first test environment; no paid staging without owner approval.

## Target release gate

`Candidate → code review → Android/Windows/web/backend tests → staged runtime verification → green required checks on exact SHA → protected main merge → signed Android build → install/launch → publication evidence`.

This file is a live checklist, not evidence of completion of the 25 features.

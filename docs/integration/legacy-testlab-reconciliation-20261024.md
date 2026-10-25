# BLINK: safe reconciliation of legacy Testlab and current main

Prepared 2026-10-24. **This is an integration checkpoint, not a code merge or APK release.**

## Source refs verified at audit
- Legacy Testlab baseline: `56b4905883179697f76588acc9206b215491eea4`.
- Current main baseline: `70a60612491b9a43629829466b8bb1067ea44490`.
- Main-derived verified feature integration: draft PR **#534**, from `testlab/consolidated-phase1-4-main-safe-20261024`.
- Phase 5 active work: draft PR **#536**, built on top of PR #534.
- Compare from Testlab to main-derived Phase 1–4: **1431 commits ahead / 602 behind**. GitHub's compare file list is capped at 300. The branches are not safely fast-forwardable.

## Verified areas of disagreement

| Component | Legacy Testlab | Current-main-derived integration | Safe decision |
|---|---|---|---|
| Bottom nav | Already Home, Reels, Market, Connect, **Message** | Restores the same five destinations and unread behavior | Keep whichever implementation preserves new functionality; no old Menu regression |
| Top/Items | Legacy toolbar / Game navigation | Consistent four-square Items hub | Rework route semantics rather than replace entire files |
| `BlinkItemsScreen.kt` | **Missing** | Includes theme-aware utility pages | Introduce missing Items feature with dependencies, permissions, tests; do not copy alone |
| `BlinkReferenceMenuScreen.kt` | **Missing** | Contains current menu/wallet routes | Preserve current Menu feature and permissions |
| `PostCard.kt` | Legacy card, no current `xFeedStyle` | Updated Like-first, views metadata and four-line excerpts | Avoid wholesale copy that would drop Testlab-only bindings |
| `PremiumFeedScreen.kt` | Legacy chrome/scroll implementation | Latest header, separate Following, inline media, sponsor views, scroll-aware clearance | Integrate by functionality and test compatibility; cannot safely replace as one file |
| `MainActivity.kt` | Older route wiring | New payments, accessibility, update flow, Items, feed state | Preserve latest callbacks and security; never bulk-overwrite |
| `BlinkRefreshPolicy.kt` | **Missing**; old ViewModel has one-minute full-app sync | Per-surface guarded refresh and one-minute social TTL | Do not downgrade to full-app onResume sync |
| Windows | Older desktop feed | Improved parity with new Android feed card | Validate Windows separately |

## Required reconciliation sequence
1. Keep legacy `Testlab` and `main` unchanged. Work on isolated branches only.
2. Validate PR #534 Android quality, emulator smoke, release smoke, Windows, intro, backend and visual screenshots on its **exact head SHA**.
3. Complete Phase 5 staging verification on PR #536.
4. Enumerate Testlab-only behavior/features by subsystem (auth, profile, payments, chat, game, marketplace, administration, security and website) and compare to current main. Record explicit **keep / port / already superseded** decisions.
5. Port only missing, still-relevant Testlab changes to a main-derived reconciliation branch; keep meaningful main-only features and contracts.
6. Run full tests and staging migrations, then require a code-reviewed PR targeting Testlab; do not reset Testlab history, bypass checks, or auto-promote draft branches.
7. Only after remaining approved phases: merge fully verified release into main and publish the signed APK. Verify published SHA/version and live backend compatibility.

## Current blocker
An automatic merge between the two histories is **unsafe**. Having standalone PR checks pass does not prove that all features across these divergent branches are preserved or that images match approved screenshots. No claim of full reconciliation should be made until the above acceptance gates are satisfied.

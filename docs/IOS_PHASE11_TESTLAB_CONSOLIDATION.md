# BLINK iOS Phase 11 — Consolidated Testlab Integration

## Goal

Integrate the iOS source work from Phases 1–10 as **one coherent change** against BLINK's latest `Testlab` branch, rather than merging ten old stacked PRs separately. Preserve all Android, Windows, website and Supabase changes that landed on Testlab during iOS development.

## What is included

- Existing native iOS project, public Supabase auth, Home/Reels, Market, Connect, Messages, notification list, calling/VoIP experiment, and release CI.
- Real-user operations remain server-authorized; no separate account DB is introduced.
- iOS back-end APNs migration and two new edge-function *source files*, both **disabled by default**; the migration is not deployed merely by merging code.
- The optional existing `send-call-notification` iOS dispatcher hook remains behind TWO default-off server-side flags and must not interfere with Android FCM.
- TestFlight source, simulator and **unsigned device archive** gates. No private Apple credentials committed.

## Staging review

Compare `Testlab` and this branch. Other Testlab updates must be preserved: Android calling JNI fixes, Windows parity improvements, owner's secure login configuration, mobile inbox work, live content management and existing payment safety settings. The pre-merge comparison should show **no edits to Android/Windows app source or payment provider configuration introduced by the iOS branch**, except isolated, reviewed server-call notification additions.

### Required automated checks on this consolidated PR

1. iOS foundation: Xcode simulator build + XCTest.
2. iOS **unsigned** device archive and TestFlight source safety.
3. iOS VoIP backend security source validation; all APNs flags remain OFF.
4. Android and Windows parity/build gates.
5. Supabase migration syntax and safety; no production deployment.
6. Testlab branch mergeability and no destructive modification to unrelated features.

Tests may run on GitHub without consuming mobile data or requiring a temporary APK installation. Actual signed TestFlight publication requires a later, separately authorized Apple Developer release.

## Gate after integration

Even if the Testlab merge is successful, **do not assert TestFlight/App Store availability or working two-way iPhone calls**. Outstanding: Apple team/bundle validation, distribution credentials, App Store Connect record, signed IPA, physical iPhone & Android end-to-end tests, moderation/report/block/account deletion/billing policy compliance and APNs registration/delivery/revocation.

### Stacked PRs

Phase 1 #416 → Phase 2 #417 → Phase 3 #419 → Phase 4 #423 → Phase 5 #426 → Phase 6 #432 → Phase 7 #444 → Phase 8 #445 → Phase 9 #449 → Phase 10 #452.

The new **consolidation PR** contains the net code of these phases. After successful Testlab merge, close obsolete stacked PRs with a clear explanation to prevent accidental duplicate merges. Do not close, mark merged or alter historical PRs unless the consolidated code is actually merged and verified.

## Production

No merge to `main` or signed TestFlight upload is performed as part of this consolidation. Before promotion, audit optional call backend migrations and select the minimum release scope necessary; ensure platform parity and owner-requested release policy are preserved.

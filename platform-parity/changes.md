# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-09-07 | Windows desktop foundation | Existing production client | Desktop shell + EXE/MSI build foundation | Initial migration foundation; existing Android routes still need incremental porting |
| 2026-09-08 | Premium Blink design system foundation | Material 3 dark/light palette mapped to shared tokens; semantic surfaces, shapes, spacing, motion and premium bottom navigation | Desktop Material 3 theme mapped to the same shared palette with System/Light/Dark resolution | `BlinkDesignSystem.kt` is the cross-platform source of truth; no backend, Supabase schema, ranking, messaging, coin, auth, call or moderation behavior changed |

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

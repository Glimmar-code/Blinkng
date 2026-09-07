# Blinkng Android ↔ Windows parity ledger

Every pull request that changes a user-facing Android feature must update Windows in the same pull request, either through shared code or an explicit desktop implementation.

| Date | Feature | Android | Windows | Shared/adapter note |
|---|---|---|---|---|
| 2026-09-07 | Windows desktop foundation | Existing production client | Desktop shell + EXE/MSI build foundation | Initial migration foundation; existing Android routes still need incremental porting |

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

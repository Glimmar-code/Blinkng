# Blinkng engineering instructions

## Product rule: Android and Windows are one Blinkng product

Blinkng must be developed as one product with two supported client surfaces:

- Android APK (`app/`)
- Windows desktop (`desktopApp/`)

The same Blink account, Supabase backend, permissions, database contracts, storage, ranking rules, coins, verification, messaging, moderation, admin rules, and feature semantics must be used on both platforms.

## Mandatory shared-first rule

For every new or changed user-facing feature, start by deciding what is platform-independent. Put reusable business logic, models, validation, routing contracts, repository logic, API contracts, state machines, formatting, and reusable Compose UI in shared code as the migration progresses.

Android and Windows should contain only the platform adapters that are truly platform-specific.

Examples of Android-only adapters include Activity/Context, Android Intents, CameraX, WorkManager, Firebase Messaging delivery, Google Play services, Android Credential Manager, Android permissions, and Media3 integrations.

Examples of Windows-only adapters include desktop windows, system tray, Windows notifications, desktop file dialogs, browser OAuth callback handling, drag-and-drop, native menu bars, and EXE/MSI packaging.

## REQUIRED FEATURE PARITY RULE

**Anything added to or changed in the Android APK that affects a Blinkng user must be added to the Windows version in the same pull request.**

A change is not complete merely because the APK works. The pull request must do one of the following:

1. implement the feature once in shared code so Android and Windows receive it automatically; or
2. update both the Android implementation and its Windows counterpart; or
3. if the feature is genuinely impossible or meaningless on Windows, record an explicit platform exception in `platform-parity/changes.md` with the reason and the closest Windows equivalent.

Do not merge an Android-only user-facing feature that silently leaves Windows behind.

## AI/coding-agent workflow for every Blinkng feature

Whenever an AI agent or developer is asked to add, fix, remove, redesign, or change an Android feature:

1. inspect the existing Android behavior and backend contract;
2. identify the reusable/shared part;
3. implement or update shared logic first whenever practical;
4. wire the Android surface;
5. wire the Windows surface;
6. preserve the same route IDs, permission rules, data semantics, and server behavior;
7. update `platform-parity/changes.md`;
8. run Android quality checks;
9. run Windows desktop build checks;
10. only consider the work finished when both supported clients are accounted for.

## Backend rule

Do not create a second production backend for Windows. Windows must use the same Blinkng Supabase project and server-authoritative rules as Android. Never ship service-role secrets in either client.

## No production mock-data rule

The Windows production client must not invent fake posts, reels, users, marketplace listings, leaderboards, coins, notifications, messages, or analytics. Placeholder UI is allowed only while a route is being ported and must be clearly marked as migration UI.

## UX rule

Feature parity does not mean pixel-for-pixel phone stretching. Windows should use desktop conventions such as sidebar navigation, keyboard/mouse support, resizable panes, context menus, desktop notifications, drag-and-drop, and wide-screen layouts while preserving the same Blinkng feature and result.

## Compatibility rule

Never break the Android APK just to make desktop compile. Platform migration must be incremental, tested, and backward-compatible with existing Supabase data and released Android clients.

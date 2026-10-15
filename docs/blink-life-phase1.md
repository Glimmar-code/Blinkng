# BLINK LIFE — Phase 1 (Testlab playable prototype)

Status: experimental; **not a production release**. Built to be reviewed via a feature branch targeting `Testlab`, then verified before any promotion to `main`.

## Current playable slice

Open BLINK > Games > **BLINK LIFE · TESTLAB** on Android or Windows.

**3D requirement (PR #283 Testlab integration candidate):** Android builds now stage the native `game3d/` Godot 4.3 scene and runtime AAR in a separate game activity, while Windows installer builds stage the same project and native Godot Windows executable. The previous software-projected preview remains a fallback for desktop development builds without native game resources. This is **not** evidence that a new APK works on a phone or that main has been updated; CI, physical Android and Windows installer tests must pass before production release. See `game3d/README.md`.

- Shared original 3D city playground with six named places, cuboid buildings, low-poly trees, and a three-dimensional avatar.
- Camera rotation with 45-degree orbit controls to view the city from different angles.
- Move the 3D avatar one tile at a time using direction buttons.
- Travel to the café, tech hub, or delivery depot; answer an interactive task for fictional Game Cash.
- Visit the market to buy one desk plant; view ownership at the hostel.
- See local session balance, completed tasks, and status messages.
- Use the same pure Kotlin world/economy rules and Compose UI on Android and Windows.
- Leave all existing games untouched.

## Product boundaries

This is explicitly a **session-only local sandbox**. Game Cash is an unredeemable fictional in-game unit, **not** Nigerian naira, BLINK Coins, cryptocurrency, a bank balance, or a voucher. It cannot be transferred, withdrawn, bought with money, or converted to BLINK Coins. No multiplayer opponents, connected wallets, live accounts, cloud saves, or real payments are claimed.

Do not use this client-only state for production rewards, competitive rankings, payment decisions, or inter-user trades. The static job tasks are placeholders for initial mechanics, not production anti-cheat logic. Until authoritative backend work is complete, the entry should remain labelled as an experimental preview and not promoted as a full life-simulator release.

## Next implementation slices

1. Replace the 3D-projected prototype with a hardware-accelerated 3D game runtime (e.g. Godot/Unity) for physically navigable scenes, collision/physics, terrain, animated avatars, and mobile controls. Keep Android/Windows integration tested and provide a package-size budget.
2. Add authenticated per-account character creation and persistence through versioned Supabase migrations, preview RLS policies, and server-owned transactions.
3. Build diverse mission/job mechanics with replay protection, balanced payouts and meaningful feedback.
4. Introduce inventory, interiors and businesses with server-authoritative ownership and economy rules.
5. Add safe multiplayer sessions, reporting/blocking/moderation and fair university-based competitions.
6. Only then consider optional, clearly-priced cosmetics. Keep monetization age-appropriate, nonessential, and separate from gameplay advantage.

## Verification gate

- Run `./gradlew :app:testDebugUnitTest --tests com.example.BlinkLifeRulesTest` and `./gradlew :app:compileDebugKotlin`.
- Run `./gradlew -p desktopApp compileKotlin` using the repository-supported desktop toolchain.
- Manually test Android 15 / Redmi-class hardware: board visibility, movement bounds, each workplace, wrong answers, correct answers, spending, repeated ownership, back navigation, portrait/landscape, light/dark mode, and screen-reader labels.
- Test the same mechanics on Windows with mouse/keyboard.
- Confirm there are **no Supabase writes**, existing Game modes continue working, and no BLINK Coin or paid balance is mutated.
- Fix failures in the feature branch / Testlab; do **not** merge to `main` without passing required checks.

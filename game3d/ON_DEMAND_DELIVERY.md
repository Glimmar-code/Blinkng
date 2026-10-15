# BLINK LIFE 3D: Play on-demand game download (experimental)

## Why
The previous BLINK APK included all Godot 4.3 native engine libraries and the 3D scene whether or not the user ever played. The October 15 signed GitHub release was approximately 345 MB. This branch separates the optional engine and game assets from the social app's **base APK**.

## Delivery architecture
- `app`: the ordinary BLINK social feed/reels/market/chat and a small BLINK LIFE **Download & Play** / **Game Storage** card. It depends on Google Play Feature Delivery, not Godot.
- `blinkLifeFeature`: `com.android.dynamic-feature`, `dist:on-demand`, Godot engine, scene assets, and isolated Android `BlinkLifeGodotActivity`.
- `game3d`: shared Godot scenes and scripts for both Android and Windows; staged into the optional Android feature during its build. The game executable is never fetched as loose unverified native code from a third-party URL.
- On first tap: show real download progress, cancel/retry, confirm large transfer if Play requests it, then launch game.
- On repeat taps: launch installed game without redownloading the full game.
- Game Storage: user can request Google Play **deferred uninstall** of the module without deleting BLINK social data. Uninstall may finish after an app restart.

## How to test properly
1. Build `gradle :app:bundleDebug`. The `app-debug.aab` must contain `base/` and `blinkLifeFeature/` modules.
2. Inspect `app/build/outputs/apk/debug/app-debug.apk`: **no** Godot engine or `project.godot` should exist in the base-only APK.
3. Inspect `blinkLifeFeature/` inside the AAB: should contain `lib/arm64-v8a/libgodot_android.so`, `assets/project.godot`, and scripts.
4. Create local-testing split APKs from the AAB using `bundletool build-apks --local-testing --bundle=... --output=...`, then `bundletool install-apks --apks=...` onto an Android 8+ physical phone.
5. Test download (wifi/cellular), interrupted connection, limited space, cancel/retry, foreground/background, app restart, offline gameplay, Play Store updates, remove-game-files, and full BLINK feed/reels/messages while the game is not installed.
6. Test Google Play internal testing with a signed AAB before shipping. The actual network download size depends on device ABI, compression and Play delivery; don't promise a fixed number.

## Important differences between download sources
**Google Play:** Users download a smaller base package, then the signed on-demand module when they tap Download & Play.

**GitHub standalone APK / Blink website:** A plain standalone base APK **cannot** later obtain Play dynamic features. It remains useful for social app testing but the BLINK LIFE button must explain the Play requirement rather than crashing or downloading untrusted executables. Do **not** replace the currently functioning website production APK with this base-only APK until a supported companion distribution method is designed and verified.

## Release gates
- All on-demand Android CI tests pass; no Godot libraries/assets in the base; module is signed correctly within the AAB.
- Both Google Play internal-test build and real Android device install/uninstall flow are verified.
- Debug and production builds must not silently preload game.
- Exact optimized size reported after Play splits, not based only on source AAB size.
- Online dedicated server, optional ticket Edge Function, encrypted public transport, accounts, moderation and scalable persistent multiplayer are separate **not-yet-deployed** concerns.
- Windows desktop continues to package its native Godot runtime as before.

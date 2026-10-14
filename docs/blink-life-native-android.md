# BLINK LIFE — native 3D Android host

The BLINK Game page launches a dedicated `BlinkLife3DActivity` backed by the pinned MavenCentral **Godot 4.3.0.stable Android AAR**. The normal BLINK social app remains a Compose application; it does not load the renderer unless the player opens BLINK LIFE.

The Godot assets are sourced from `game3d/` at build time through `app`'s Android assets source set. Godot loads the native project's `project.godot` and runs `scenes/main.tscn`, not the temporary Canvas-based preview. Keep `project.godot`, `scenes/` and `scripts/` at the assets root. New imported art must undergo a Godot export/import pipeline rather than being copied as unimported external resources.

- Game screen: **Play > BLINK LIFE > Play native 3D**.
- Runtime is in a separate Android process; leaving the Activity returns to BLINK.
- 3D playground includes the free-roaming CharacterBody3D and native perspective camera.
- All currency is fictional, local, nonredeemable Game Cash; no BLINK wallet/coin mutations and no payments.
- Godot 4.3 Compatibility renderer is deliberately used to favor entry-level Android GPUs.

## Required validation before main

Run Android unit tests, debug assemble, Android lint, instrumentation compile, signed release smoke and packaged APK inspection. Confirm APK includes the `project.godot` and `scenes/main.tscn` assets, Godot engine native libraries, and manifest's `BlinkLife3DActivity`. Perform a real Android 15/Redmi device test for open, three-dimensional rendering, movement, jobs, camera, back, orientation, restart after process death, touch safety and memory/thermal behavior.

**Windows parity:** Windows retains the shared BLINK LIFE 3D preview while the native Godot source in `game3d/` is cross-platform; a native Windows engine packaging integration is still pending. Record the temporary packaging exception in `platform-parity/changes.md`. Do not claim fully matched 3D runtime until Windows embedding ships.

**Release restriction:** CI passing and static APK verification do not substitute for a physical-device smoke test. Keep the feature gated in Testlab until the native game has run successfully on Android hardware.

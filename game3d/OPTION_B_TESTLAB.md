# BLINK LIFE Option B — Testlab release contract (15 Oct 2026)

**Status:** experimental draft PR #295. Never publish or auto-merge on CI alone.

## Website / GitHub users (new)
- The **signed lightweight app APK** from `:app:assembleRelease` is the website download. It excludes Godot Android shared libraries and the game's native activity. Its exact final size must be measured, not assumed.
- The signed APK embeds SHA-256 of a separately published `blink-life-web.zip` for **the same GitHub release version**.
- `Download game` requests that archive via Android `DownloadManager`: transfers generally continue when the UI is closed, with a system notification and a retry mechanism. Interrupted transfers and battery restrictions must be tested on a Redmi 15C.
- The completion receiver schedules a background WorkManager verification/unpack. The archive is SHA-checked and ZIP entries are allowlisted before activating it. No downloaded APK, DEX, or Android native `.so` is executed.
- After verification, `Play` launches Godot 4.3's **single-threaded WebAssembly/WebGL 2** export in a sandboxed internal Android WebView using an app-private HTTPS virtual origin, **not a second app installation**. The downloaded game can be played offline after being prepared. No broad JS bridge to BLINK identity or financial data.
- Only the game module loads on play; opening Feed/Reels/Messages does not start Godot.

## Play Store users (unchanged)
- `blinkLifeFeature` stays a signed, Google Play on-demand **native** 3D module delivered from the AAB via Play Feature Delivery.
- No web-game archive is needed for Play customers, and on-demand split installation is managed by Play.
- Desktop native Godot remains separate and unchanged.

## Known limits and honest release gates
- The WebGL mode currently supports the **offline 3D world**; native LAN/UDP multiplayer is disabled in browsers. Full Internet multiplayer, homes/accounts synchronization, and advanced multiplayer are not delivered by this PR.
- Godot WebGL may render less smoothly than native on older/low-memory Android WebViews. Test rotation, controls, audio, storage, low-memory recovery, and gameplay on a physical Redmi 15C.
- Demonstrate website app update using the original release signing certificate. Do not ship a debug or disposable-key APK. Test both a fresh installation and an upgrade from the last signed 345MB release.
- Confirm `release-info.txt` has `apkSha256`, `gamePackSha256` and `gamePackSize`; release publisher must verify the signed APK plus downloadable game **before** making the release public.
- Reject a base-only APK if the associated ZIP is not downloadable/verified; never publish dangling links.
- Verify APK file sizes from the final release assets, and that the GitHub Pages site links to `releases/latest/download/Blink-latest.apk`.
- Test leaving BLINK with an active download, killing only its UI, Wi-Fi/data switch, pause/resume/cancel, low storage, archive tampering, a second download, background prep, launch without Internet, and uninstall game content without deleting social data.
- **Do not claim the Website build ships online native Godot or the existing LAN player.** The web game variant is distinct.

## Local/CI developer commands
```sh
bash scripts/setup_godot_43_web.sh
bash scripts/build_blink_life_web_pack.sh
python3 .github/scripts/test_publish_verified_apk.py
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleDebug
```

The real signed-production flow, including the Game ZIP publication, is staged in the draft PR only. Promote to `main` only after all CI checks are green **and a real-device acceptance test passes**.

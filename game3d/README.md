# BLINK LIFE — Native 3D Game (Experimental)

This is the **real 3D engine source** for BLINK LIFE, built in Godot 4 with the mobile-friendly **Compatibility** renderer.

## What runs in the native 3D scene

- Walk freely around an original stylized Nigerian-campus-inspired 3D neighbourhood, not tile-by-tile.
- Three-dimensional physics-based player (`CharacterBody3D`), collidable buildings, a floor, roads and vegetation.
- A perspective follow-camera that can orbit the player.
- WASD / arrow-key movement, Q/E camera orbit, F interaction.
- On-screen movement, orbit and interaction buttons for touch devices.
- Six destinations: Campus, Café, Tech hub, Market, Delivery depot and Hostel.
- Three local example jobs with quiz choices and fictional Game Cash.
- Buy a single decorative desk plant with earned fictional Game Cash.

All shapes are generated from original procedural geometry. No licensed photos, city maps, audio, third-party textures or proprietary game assets are included.

## Development / preview

Open the `game3d/` folder as a project in **Godot 4 (Compatibility renderer)** and run `scenes/main.tscn`.

**Packaging status:** The Godot project is not yet embedded in the BLINK Android APK or the Windows desktop release. The existing Games > BLINK LIFE preview in these apps currently runs the shared **software-projected 3D** proof-of-concept; it is not a substitute for the Godot free-roam runtime. Do not describe the shipping BLINK app as having the native 3D game until embedding and actual device testing have succeeded.

**Validation limitation:** Godot Editor / headless executable is not yet part of the repository's CI runners; these GDScript and scene files still require Godot import/runtime smoke testing.

## Required host-app integration

1. Pin a stable Godot version, export the Android library and embed it through the supported Godot Android host/fragment APIs. Protect the current activity lifecycle, back navigation, signing, package size and ABI selection.
2. Wire the existing Game page to the native Godot scene only when a Godot AAR has been successfully packaged, the Android debug APK installs and works, and release smoke tests pass.
3. Package the same Godot project for Windows and wire the existing desktop Game page. Both must use consistent gameplay semantics.
4. Move accounts, inventory, virtual Game Cash, missions, score calculations and any future BLINK Coin rewards to authenticated server-owned Supabase transactions before competitive multiplayer or persistence. **Never** trust arbitrary client-side gameplay reward claims.
5. Use original optimized meshes, LOD/occlusion and capped physics/lighting for entry-level Android devices. Benchmark on real low-end hardware.
6. Add Godot-specific headless scene validation, interaction tests and deployment checks in CI.
7. Keep changes in the feature branch / Testlab until validated; never auto-promote an unbuilt Godot runtime to production.

## Economy and safety

The native prototype's **GC** balance is fiction, exists only for its local session, and cannot be exchanged, sold, redeemed, transferred or withdrawn. It does not use real money, BLINK Coins or live BLINK wallets. Purchases are cosmetic demo interactions only. No gambling, cash-out, or pay-to-win mechanics.

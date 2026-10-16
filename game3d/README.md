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

**Packaging status (experimental PR #283):** Android now has a pinned Godot 4.3 AAR host and bundles this project's real 3D scene into APK assets at build time. The Games entry opens a dedicated landscape Godot activity in an isolated process. The Windows desktop installer build stages the same project with a pinned Godot 4.3 Windows runtime and launches it from Games; unbundled development builds retain a clearly labelled projected-3D fallback. **This code is under Testlab validation only; there is no verified production or physical-device native 3D release yet.** Check PR #283 CI and real Redmi/Windows installs before promoting to main.

**Validation:** `.github/workflows/blink-life-3d-check.yml` installs Godot 4.3, imports the scene and runs runtime smoke tests under an OpenGL-compatible virtual display. GitHub CI does not replace installation on Android phones and Windows PCs.

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

## Social multiplayer foundation (Testlab, not public release)

The native Godot world now adds `scripts/social_world.gd`. Its LAN preview is strictly opt-in through **FRIENDS / WORLD → HOST LAN / JOIN LAN** while BLINK LIFE is already open. It has no persistent Internet matchmaking, production user identity, global chat, live GPS, or wallet.

- Two devices on a **trusted private Wi-Fi** network may connect using a private IPv4 address; localhost is allowed for developer testing. The host runs a Godot ENet server on UDP port 24567. This is not safe to expose as a public game server; never port-forward or advertise the local IP publicly.
- Players see each other's low-poly 3D avatars, nickname nameplates and interpolated movement in the shared city.
- Each session participant receives a separate temporary demonstration home interior. Visiting another player's demo home requires request/owner approval, not just a shared address. Gameplay returns to the original city.
- Players can create one simple fictional vehicle each; a visitor must request and be approved before becoming the single passenger. This is a prototype visual vehicle following the driver (not a production physics/racing system).
- Short session chat, wave messages and local player mute are available. Social content has not undergone live public moderation; it must remain LAN preview until blocking, reporting, filtering and moderator tooling are integrated.
- In-game coordinate sharing is **off by default**, toggled per player. These are fictional 3D positions, **not** device location services.
- All usernames in this preview are editable unverified nicknames, not authenticated BLINK users. No fabricated online counts or fake friends. Disconnect and scene exit do not end the main BLINK app.
- `ROADMAP_110.md` tracks all 110 requested professional features honestly, separating partially prototyped LAN behavior from unfinished production systems.

**Before real online multiplayer:** Use a secure, server-authoritative Internet architecture with account-bound identities, interest management, validation, staging RLS, invites restricted by friendship and consent, accessible reporting/moderation, parental-age-appropriate protections, and server-owned inventories. Do not deploy this trust-based LAN session protocol as an Internet endpoint.

## Launch and performance isolation (Testlab)

- Normal BLINK usage (Feed, Reels, Market, Connect, Messages and other Games) **does not start the native Godot renderer or open its scene**.
- Only a deliberate **Play BLINK LIFE 3D** tap uses `BlinkLifeLaunchGate` to start a separate Android process `:blink_life`; the UI launcher does not reference Godot classes. Windows also starts the native executable only on Play.
- The secondary process bypasses the BLINK social app's Supabase, Firebase, notification-worker and other startup initialization. Exit is available inside the scene. A launch failure should leave the social app usable.
- **Important tradeoff:** The Godot engine and scene are included in the downloaded APK/Windows installer, so they still increase **download size and installed storage** even for users who never play. Lazy *runtime* loading avoids paying the engine's normal memory/graphics/CPU cost before Play; it does not make APK downloads smaller. Optional on-demand content packs require a separately designed, verified delivery mechanism.
- Godot first start may take longer as the native engine creates its graphics context and imports assets. Validate on Redmi-class physical hardware and verify memory returns to normal after exiting. The native Android/Windows integration is not production-verified.



## Realism roadmap (16 October 2026)

`REALISM_1000.csv` tracks the owner's 1,000 requested improvements with stable IDs and honest statuses. `REALISM_IMPLEMENTATION.md` describes the first shared-world batch, exact acceptance and release gaps. The scene now includes connected footpaths, street signs, building addresses and closer frontage interaction selection. These additions are Testlab source work, not a confirmed production update.

## Local weather preview (Testlab batch 4)

Controls → Local weather provides automatic changing skies, manually selected
clear conditions, light showers or capped tropical rain. Moving low-poly clouds
and batched raindrops are fictional visual effects, with no gameplay penalty,
real-world weather permission or production economy impact. Weather is not
synchronized between players and has not passed physical-device graphics checks.

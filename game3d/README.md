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

The native prototype's **GC** balance is fictional solo-tutorial currency, saved only on the current device and never accepted by multiplayer servers; it cannot be exchanged, sold, redeemed, transferred or withdrawn. It does not use real money, BLINK Coins or live BLINK wallets. Purchases are cosmetic demo interactions only. No gambling, cash-out, or pay-to-win mechanics.

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

## Local neighborhood residents (Testlab batch 5)

Eight fictional, always-labeled NPC residents follow predictable local
home/work/leisure routines along the starter city's sidewalks. They can give
brief text greetings and show umbrellas when they walk in rain. Battery-saver
mode limits the visible cast to four. Residents are decorative and use no
BLINK social identity, online-server attendance or production wallet.
This is still a staging prototype, not a finished population or live economy.

## Offline progress checkpoints (Testlab source)

Successful offline job earnings and the fictional desk-plant purchase are
saved locally with a versioned file and a recovery backup. Failed storage
writes reject the transaction instead of claiming the reward is preserved.
This progress is a **solo demo only**: it is not BLINK Coins, never redeemable,
never moved to multiplayer accounts or accepted by the dedicated server,
and must not be used for competitive scoring. The engine still starts only
when the player deliberately launches BLINK LIFE; shared sessions do not
pay local demo rewards.

## Offline home preview (staging)

Walk to **Homes** and select **Enter free starter room (offline)**. The
room is a lightweight 3D interior constructed only on entry and destroyed
on exit. Walk with the existing controls, and use **INTERACT**, **F**, or
**Escape** to return to the same city doorway. A fictional desk plant
purchased at the solo Market appears on the room desk after relaunch.

The solo room is free: it has no rent, property title, online trading or
synchronized world ownership. It does not modify the separate approved
multiplayer homes system. Bed and desk are visual furnishings for now, not
functional sleeping or study activities. Offline Game Cash never becomes
BLINK Coins, cashable funds or network-authoritative rewards.

## Free offline home customization

At the **Homes** location enter your optional solo starter room and tap
**INTERACT** to open its decoration menu. Cycle among three free wall
palettes, three floor colour variants and, after purchasing the cosmetic
desk plant, three safe plant positions. Choices save locally before
being applied. Press **Done** to return to the room or **F** to exit.
Older solo saves load using the original design. This is a small offline
demo; it is not a multiplayer property editor, free-form furniture
placement, server account progress or redeemable currency.

## Offline starter-room furniture controls

Inside the optional private starter room, tap **INTERACT** for a scrollable
decoration menu. Move the purchased desk plant to a safe one-metre floor
grid, adjust by directional buttons, turn it in 90-degree steps, and
**Undo/Redo** edits (up to 20 recent edits while the room is open).
Blocked cells cannot be saved. Existing wall/floor palette selection and
three plant display presets still work. Floor-grid position and orientation
survive restart; the undo history intentionally resets between sessions.
No virtual Game Cash is charged, and this offline feature does not alter
BLINK Coins or any public online property.

## Free chair and table placement (staging source)

The offline starter room's **INTERACT** decoration menu now offers
directional positioning and 90-degree rotation for the original chair
and an additional small side table, alongside the purchased desk plant.
All three are restricted to a low-poly 1-metre floor grid with blocking
rules for walls, permanent furniture and the doorway. Room editing is
free and reversible with Undo/Redo; new furniture positions save
locally and survive restarting the game. These changes do not operate
inside shared homes or award any BLINK Coins. This is not yet arbitrary
bed placement, furniture physics or a full online property editor.

## Optional solo room layouts

Open Homes, choose the private offline starter room and tap INTERACT.
The furniture editor now provides **Bed position** and **Wardrobe
position** (three collision-checked options each), plus three prepared
arrangements: **Original room**, **Study corner** and **Open floor**.
Layouts reposition the existing chair and side table too. Edits are
free, undoable and written to the local solo save before being applied.
Applying a plan resets the cosmetic plant's display position, but does
not remove its ownership or modify Game Cash. This is not customizable
online property, arbitrary bed dragging, public multiplayer housing or
production in-game currency.

## Your own saved room arrangements (Testlab preview)

Inside your free private starter room, open **INTERACT** and use
**Save layout 1** or **Save layout 2**. Later use **Apply layout 1/2**
to restore your favourite furniture positions and wall/floor colours.
Both slots persist in the solo local save, survive game restarts and
remain available after using Undo/Redo. Saving or applying is free and
doesn't change Game Cash, completed jobs or purchased items. Layouts
are **not** server-backed, public, tradeable or cross-device synced.

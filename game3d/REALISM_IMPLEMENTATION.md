# BLINK LIFE realism implementation

The scope is the owner's 1,000-item conversation list from 16 October 2026.
`REALISM_1000.csv` preserves all requirements and their original IDs, separately
from the older 110-item roadmap. A tracker entry is not an implemented feature.

## Status semantics

- `pending_audit`: not assessed against this requirement yet; may have older prototype code.
- `partial`: some source work exists but the requested behavior is incomplete.
- `implemented_in_source`: the scoped behavior is wired into the shared game and has named checks; packaging/device verification is still outstanding.
- `verified_testlab`: relevant Testlab checks and physical Android/Windows acceptance evidence recorded.
- `released`: verified change merged and delivered in a confirmed release.

This initial batch has five source implementations, ten partial requirements,
and 985 pending audits. No row is marked verified or released. Do not convert
existing prototypes into completion claims without checking their actual behavior.

## Batch 1: starter neighborhood

The shared Godot project now uses one metric layout for seven existing destinations,
their interaction frontages and a connected pedestrian network. It adds visible
street names and unique building addresses, frontage doors and shade canopies,
marked crossings, lamp models and a campus marker. The camera and existing job,
market and social flows remain wired through `world.gd`.

Interaction selection now uses the nearest frontage within 2.8 metres and rejects
large vertical separation instead of choosing the first building in array order.
Building doors mark interactions; they do not promise shop interiors. Lamp models
do not illuminate at night yet. District tags are not completed city districts.
The game remains a small procedural prototype, not a realistic finished city.

### Source implementation acceptance

| ID | Automated acceptance | Remaining acceptance |
| --- | --- | --- |
| 2 | All destinations use six-metre grid spacing and shared metric dimensions. | Walk distances feel comfortable on Android and Windows. |
| 14 | Every frontage has capsule clearance and joins one connected footpath graph. | Walk all approaches with touch and keyboard without getting stuck. |
| 17 | Both named streets instantiate their visible world signs. | Signs remain readable at phone and desktop resolutions. |
| 18 | Seven unique addresses have visible destination labels. | Readability and occlusion review on both devices. |
| 198 | Each entrance selects its own destination; empty crossroads and other floors do not select a shop. | Verify interaction selection around frontage edges on both clients. |

### Validation performed locally

- Godot 4.3 project import and script parsing.
- Renderer-independent scale, address, entrance-clearance and path-connectivity tests.
- Scene runtime assertions covering signage and frontage selection, existing jobs,
  purchases and LAN interactions. Local dummy renderer emits its existing null-mesh
  diagnostics; this is not a clean rendering validation.
- Existing join-ticket replay and dedicated property-persistence checks.
- Tracker cardinality, numbering, uniqueness and evidence validation.

The CI workflow runs the scene with software OpenGL under Xvfb. Android and Windows
build checks, rendered CI, physical-device usability and performance measurements
remain release gates. This change must target Testlab and must not publish an APK
or promote itself to main.

## Build-gate repair

The inherited Android quality workflow contained duplicate upload steps inserted
inside an unfinished `grep` command. This batch removes that corruption and validates
the YAML and Bash syntax. Android and Windows packaging checks now also require both
new neighborhood scripts. This repairs a check; it does not assert either build passed.

## Work sequence

Work through rows 1–50 first, completing usable blocks before expanding the map.
Within a block, prerequisites may need implementation before their dependent item.
Each subsequent batch must update the exact rows it changes, add item-specific
acceptance, link implementation and test evidence, and list outstanding limitations.

1. Finish world scale, paths, reachable interiors and one polished neighborhood.
2. Establish movement, interactions and adaptive settings required by every activity.
3. Add a shared clock, lighting/weather, NPC schedules and accessible presentation.
4. Build home ownership, vehicles, careers and enjoyable activity loops.
5. Validate identity, authoritative persistence, permissions and economy in staging
   before connecting those systems to public multiplayer or competitive rewards.
6. Expand campus, stories and activities while measuring device performance.
7. Complete moderation, recovery, delivery and production release requirements.

Game content must remain opt-in. Never start its renderer during ordinary BLINK
use. Coordinate with the separate on-demand-delivery work; this batch does not
claim the APK-size requirement is complete. Keep one shared gameplay implementation
for Android and Windows. Never use production as the first test for backend changes.

## Batch 2: controls and local day/night

The batch-1 commit `631c0f7a10aa4976d3a1afbb333e7d7b2c9da3f7` passed all six
GitHub checks, including OpenGL scene tests, Android quality/APK preview and
Windows quality/build. This confirms CI, not physical-device acceptance.

New work in batch 2:
- Stop movement while answering a quiz, editing text, using controls or viewing social UI.
- Clear touch input on application focus loss/pause; preserve other held touch
  directions when one direction is released. Normalize diagonal movement.
- Smooth acceleration, braking and turning; adjustable walking speed capped at
  the existing network movement maximum. Sprint animation remains unfinished.
- Offline fall/out-of-bounds recovery to the last grounded city position.
  Multiplayer rescue remains with server rules; this does not implement it.
- Saved 8–20 metre camera zoom, 50–100% walking speed and reduced camera-follow
  motion. Settings validate corrupt types, non-finite numbers and out-of-range values.
- A local visual day/night clock, sunrise/sunset/twilight interpolation and sun
  rotation; adjustable 10–60 minute days and fixed daylight, with saved preferences.
  Night retains ambient illumination. This is NOT synchronized multiplayer time,
  server authority, weather, NPC schedules or fully functional street lighting.
- Escape dismisses active controls/quiz/social UI before quitting.

Batch-2 totals: **13 implemented in source, 16 partial, 971 pending audit**.
No row is marked verified_testlab or released. The remaining roadmap is substantial.

New acceptance checks: settings round-trip and corrupt input; acceleration at 30/60
FPS; diagonal cap; braking; touch release; focus reset; modal input blocking; cancel
without payout; offline fall recovery; day-length arithmetic; midnight wrapping;
continuous dawn; fixed-daylight mode; actual scene light energy at noon/midnight.
Local Godot import and these assertions pass. Local dummy-renderer null-mesh
diagnostics still require the OpenGL CI path for clean rendered validation.
New-head CI and real Android/Windows usability/performance remain release gates.

## Batch 3: lighting, graphics and purchase review

- Street bulbs fade with the local visual clock. Standard mode permits at most two
  nearby shadow-free point lights; daylight and battery-saving mode disable them.
- Device-local preferences add 30 FPS battery-saving and 60 FPS standard targets,
  plus a sunlight-shadow switch. Targets are caps, not performance guarantees.
  Physical battery, thermal and low-end device measurements remain outstanding.
- The market previews the item's price, total, balance and balance after purchase.
  Explicit confirmation rechecks current funds/location and consumes the pending
  intent before changing cash. Cancel and duplicate confirmation cannot charge.
- Existing jobs show their payout before the player submits an answer.
- Modal contents detach immediately before replacement to prevent obsolete buttons
  remaining interactive during the frame they are queued for deletion.

Current totals: **22 implemented in source, 15 partial, 963 pending audit**.
No row is verified or released. Night lighting is local presentation, and the market
remains a local fictional-GC prototype; no real wallet or server economy is modified.

Local Godot 4.3 parse, preference tests and scene assertions pass. Scene tests cover
night/day lamp budgets, standard/saver limits, shadow override, graphics settings
round-trip, purchase preview without spending, cancelled/stale confirmation,
insufficient funds at confirmation, successful purchase and duplicate submission.
Dummy-renderer null-mesh diagnostics remain; fresh OpenGL CI must pass. Android and
Windows device usability, battery measurements and the other 963 items remain open.

## Batch 4: optional local rain and moving clouds

The shared Godot scene now renders two slowly moving procedural cloud clusters
and a capped, recycled MultiMesh rain pool (24 drops for light rain and 80 for
tropical rain). Automatic mode cycles clear → light rain → tropical rain using
a fictional local timer. Players can force clear, light rain or tropical rain
under Controls, and this setting survives restarts. The weather clock pauses
during app focus loss, and the renderer caps rain position updates at 20 Hz.

This remains a visual-only prototype. It does **not** use real weather, alter
vehicle traction, apply damage, trigger GPS, share authoritative multiplayer
weather, render wet surfaces, or shield rain inside buildings. It leaves
normal BLINK startup and existing LAN/demo money behavior unchanged.

Requirements **#58, #59 and #60 implemented in source; #90 partial**.
Totals after batch 4: **25 implemented in source, 16 partial, 959 pending
audit**. There are **zero verified Testlab and zero released** rows.

Named checks: `tests/controls_smoke.gd` (weather mode transitions, invalid
saved settings, bounded rendering) and `tests/smoke.gd` (scene attachment,
actual rain visibility) under native Godot CI. Fresh CI and physical
Android/Windows rendering, thermal and battery tests must pass before promotion.
The branch does not publish an APK or authorize a main merge.

## Batch 5: named local residents, home/work routines and visibility budgets

The shared Godot scene now includes eight original mesh-only **fictional NPCs**.
They have fixed local names/IDs, home/work/leisure locations and day/evening/night
destination choices using the local visual clock. Actors follow existing connected
sidewalk lanes (not direct paths through solid buildings), visually hold umbrellas
while walking in rain, and offer short accessible text greetings when approached.
Heavy rain redirects leisure travel toward shelter. Clear **NPC** labels distinguish
them from real BLINK multiplayer participants; these NPCs never increase online
counts or impersonate BLINK accounts.

The battery saver displays at most four NPCs instead of eight, and actors beyond
32 meters are culled. Simulation is local and updates at 10 Hz with no avatar
physics bodies. NPC visits remain decorative: no interior occupancy, dynamic
NavMesh obstacle avoidance, server-side persistence, human-authored voice,
friendship or player trade and no real-world location data.

Current evidence-only tracker totals: **31 implemented in source, 27 partial,
942 pending audit, zero device-verified and zero released**. The changed IDs are
80, 81, 83, 98, 401–403, 407–408, 410, 421, 430, 442, 448–450 and 500.

Acceptance checks: `tests/npc_smoke.gd` verifies unique stable IDs, valid
home/work targets, day/night/rain schedules, no building-wall route crossings and
4/8 graphics budgets; `tests/smoke.gd` validates actual scene NPC nameplates,
rain umbrellas, greetings and population caps. Both are gated by Godot CI;
the APK packaging and isolation checks now include the two NPC scripts.

The exact branch build, Windows packaging, rendered gameplay and physical-phone
battery, UI and motion tests still require independent validation. This batch does
not certify the other pending requirements or the launch readiness of public
Internet multiplayer, property persistence or optional game-download delivery.

## Batch 6: civic spaces, park seating and market noticeboards

The starter neighborhood has an original Pocket Park, three named
informational market stalls, three public benches and three waste bins.
A community noticeboard shows the three existing local fictional Game Cash
jobs and their established payouts without awarding money. Approaching and
interacting with an offline bench changes the avatar to a reversible seated
pose, blocks movement, and lets players stand up with Interact/F or Escape.
Stalls are informational, not stores with transactions. The objects are
non-colliding so they do not obstruct the seven existing building approaches.

Requirement IDs **21, 23, 40, 43 and 44 are implemented in source**,
while **166 and 167 are partial** because natural seated blending and
sit-to-stand transitions remain to be done. Relative to the concurrent
NPC batch, the evidence-only tracker counts are **36 implemented in source,
29 partial, 935 pending audit, 0 device-verified, 0 released**.

The implementation is `scripts/public_spaces.gd` with shared
`scripts/world.gd` interactions, and new checks in
`tests/neighborhood_smoke.gd` and `tests/smoke.gd`. The Android
APK preview and game-isolation checks also assert the public module exists.
These tests and all hardware acceptance gates must pass at the exact
candidate commit before Testlab promotion. Source work does not mean that
BLINK LIFE's complete 1,000-feature roadmap is delivered.

## Batch 7: durable SOLO Game Cash after app restarts (not online rewards)

Previously the tutorial's fictional Game Cash and purchased desk plant reset
to defaults whenever the 3D scene restarted. This slice adds a dedicated
version-1 local JSON save to `user://blink_life_solo_v1.json` with a
same-directory temporary write, prior-version backup, bounded typed input
validation and corrupt/unknown-version read-only safeguards. The shared
Android/Windows Godot scene now commits a **successful** solo job reward or
desk-plant purchase to disk **before** changing the visible balance. If storage
is unavailable the proposed reward or purchase is rejected with a visible
message rather than silently claiming to be saved. Saved solo state loads on
launch and can recover a previously saved backup.

These are user-editable OFFLINE tutorial points, not securely earned money.
The game explicitly blocks solo reward transactions in LAN/shared sessions,
leaves the dedicated server persistence separate and never transforms this
balance into BLINK Coins, server-authoritative inventory, cash or prizes.
The design does not yet preserve camera/position, restore multiplayer
sessions or migrate future schemas.

Tests: `tests/solo_progress_smoke.gd` exercises new installs, aborted
temporary files, two save commits, backup recovery, corrupt files,
out-of-range/invalid fields, future-version read-only protection and reload.
`tests/smoke.gd` injects a disposable save path and confirms a local job,
plant purchase and persisted recovery without affecting other tests.
The Godot CI and APK source/packaging checks include the new script.

Honest tracker changes: requirements **874, 890, 891, 892 and 895 partial**.
Now **36 implemented in source, 34 partial, 930 pending audit,
0 device verified, 0 released**. No migration is claimed for #893.
Android+Windows packaging, real-device app interruption, Android battery,
low-storage behavior and production isolation still require verification.

## Batch 8: playable starter-room preview with safe return

A small original furnished room is created only when a player explicitly
chooses **Enter free starter room (offline)** at the existing Homes frontage.
A menu also preserves the separate multiplayer-homes flow. This Godot room has
physical floor and four low-poly walls, a bed, study desk, chair, wardrobe,
window, modest lighting and a visible purchased desk plant. The room occupies
a separate instance above the local city and is freed after leaving,
so it does not consume resources during normal BLINK browsing or ordinary
in-game city exploration. The player can walk inside, use INTERACT/F or Escape
to leave and return to the original exterior entrance; movement and input
are reset on teleport. Out-of-bounds recovery stays inside the room.

Shared multiplayer property interiors, dedicated property data and server
authorization remain unchanged. Local solo rooms are **not** public homes,
tradeable/rentable property, or server-authoritative rewards. Bed/desk furniture
is presentation only, not functional study or cooking. Fixed plants do not
constitute free-placement decoration.

Specific roadmap impact: **#264 implemented in source** (on-demand scene
lifecycle), **#251, #265, #279, #300 partial**; total **37 source
implemented, 38 partial, 925 pending audit, zero device verified,
zero released**. Scene smoke checks menu choice, no involuntary teleport,
usable room boundaries, plant display, indoor-only interaction, return position,
no currency change and no multiplayer connection.
APK packaging asserts scripts/solo_home.gd is embedded.

The previous Godot CI failure was examined: tests intentionally supplying
corrupt JSON caused Godot JSON.parse_string to print engine errors, and
a future-version comparison mismatched Godot decoded numeric types. The
reader now uses JSON.new().parse() to reject corrupted save data without
unnecessary engine errors. Tests compare saved integer semantics, retaining
strict rejection of fractional amounts, and add explicit JSON float roundtrip
coverage. New GitHub Actions on the **latest commit** must pass before any
merge to Testlab. Physical Android/Windows usability and broader release
acceptance remain mandatory before main.

## Batch 9: save-first free room palettes and plant positions

The optional solo starter-room interior now has an **INTERACT** decoration
menu with three free wall colours, three free low-poly floor colour variants
and three preset positions for the owned desk plant. The plant-position
control is disabled until the cosmetic plant has actually been purchased in
the solo market. **F** still exits the room; entering decor does not
automatically teleport the player. Changes are saved before they are applied,
never deduct fictional Game Cash, and cannot be used from online sessions.

The version-one solo save is backward compatible: older save files lacking
the new optional wall_style, floor_style and plant_spot keys load with style 0.
When new style keys are supplied they must be exact integers within 0–2;
unknown or fractional values are rejected rather than clamped. A new
successful job payout or desk-plant purchase preserves existing decoration
choices in its atomic save transaction. The optional 3D room is still created
only on demand, and removed after leaving.

Requirements: **#266 implemented in source** (wall-colour preset picker),
**#267 partial** (floor colour variants, not full editable materials).
**#265 remains partial**, because plant movement is restricted to three
collision-free presets rather than arbitrary furniture editing.
Current tracker: **38 source implemented, 39 partial, 923 pending audit,
zero device verified and zero released**.

Tests: solo_progress_smoke covers loading legacy version-one saves, saved
palette roundtrips, rejecting invalid selections and unchanged currency
after rejected writes. Godot rendered scene smoke exercises the UI, wall
and floor colours, free plant positioning, save/re-entry persistence,
unknown action rejection and currency isolation. Real Android and Windows
viewing, accessibility and touch handling remain release gates. This
feature does not alter authoritative online homes, multiplayer balances,
Supabase, BLINK Coins or production delivery.

## Batch 10: safe grid positioning, quarter-turns and undo/redo (solo)

Added explicit optional 1m floor-grid positioning for the **single owned
cosmetic desk plant**, four quarter-turn orientations and up to 20
session-scoped undo/redo steps. The same edit history covers wall palettes,
floor variants, preset plant positions, grid movement and rotation.
All accepted decoration edits (including undo/redo) are validated and
written to the existing local SOLO save before the live room is changed;
a new edit clears redo history. Unsafe coordinates, furniture footprints,
walls and the character's room entry position are rejected rather than
silently re-positioned. UI uses a vertical scroll area suitable for a
small screen. There is no charge, payout or server action for decorating.

The existing V1 solo-save format remains backward-compatible; new
plant_custom, plant_x, plant_z and plant_rotation fields are optional
and default to the old safe position and no rotation. Integer bounds
and a static furniture/door collision map validate all custom floor cells.
The fixed original plant presets and previously saved wall/floor styles
remain supported. This is not generalized drag-and-drop furniture, live
physics collisions or a shared online property editor.

Tracker **#272 implemented in source** for current solo room controls;
**#270, #271, #273 partial** for one cosmetic plant rather than all furniture.
Totals: **39 source-implemented, 42 partial, 919 pending audit,
0 device verified and 0 released**.

New smoke checks cover corrupted/invalid grid saves, out-of-room and
blocked-furniture rejection, rotation display and persistence, indoor
UI accessibility, reversible editing, redo invalidation, deterministic
room re-entry and no changes to fictional Game Cash or multiplayer.
Godot CI, Android/Windows builds, touch usability and real-device
performance are required before Testlab promotion; a source implementation
is not evidence of a production feature.

## Batch 11: movable starter chair and side table (solo only)

A second offline-only furniture slice now supports a free starter **chair**
and a small **side table** in the optional solo room. Both are original,
lightweight low-poly nodes. The scrollable home decoration screen exposes
four directional grid moves and quarter-turn rotation separately for
each item. The existing desk plant, colour palettes and 20-entry undo/redo
history remain available. Room contents continue to spawn only when the
player explicitly enters their private offline home.

Before committing any change, the solo-save validator checks the chair,
table and floor-grid plant for bounded placement, rotated footprints,
bed/desk/wardrobe clearance, spawn/exit space and mutual overlap. Positions
and angles are saved before visual changes. Optional chair_x/z/rotation and
table_x/z/rotation fields are backward compatible with earlier V1 solo
saves; missing fields use default original positions. Plant X/Z can be
negative because grid coordinates are signed: the earlier Godot smoke
failure was caused by incorrectly rejecting negative JSON numeric values
at read time, even after writing a valid negative grid coordinate. This
has been corrected while retaining separate strict rejection of negative
money and invalid scene coordinates.

Added Godot saved-state and rendered-scene checks for both furnishings,
rotation, room re-entry, colliding placement refusal, backward compatible
defaults, persistence, home editor undo/redo and no cash effects. These
objects are rendered furniture only; their static footprint checker is
not a general Godot rigidbody system. The bed, wardrobe, full furniture
catalog, online property editor and production persistence remain pending.

Tracker items #265, #270, #271 and #273 now explicitly mention these
three supported decor objects but remain **partial**. **39 source implemented,
42 partial, 919 pending audit, 0 device verified, 0 released**. No Testlab
or production merge until current-commit Android, Windows, Godot and
release tests pass; real-device usability remains unverified.

## Batch 12: starter bed, wardrobe and curated room plans (solo)

The existing offline starter room now exposes three deliberately bounded
arrangements for the low-poly **bed** and **wardrobe**. The original bed
and wardrobe meshes are attached to moveable scene nodes; the bed label
moves with its parent. Positions and the one quarter-turn bed/wardrobe
variants are curated rather than unrestricted drag-and-drop so player
spawn clearance and furniture walkways remain protected.

A second menu section offers three predefined **Original room**, **Study
corner** and **Open floor** plans. Each plan arranges the starter bed,
wardrobe, chair and table together; applying a plan also returns the
optional plant to its prior first display preset, without taking it away
or changing Game Cash. Players can undo or redo the whole plan as one
save-first decoration action. The selected bed/wardrobe variants and
resulting grid placements persist locally and restore on room re-entry.

The validator now checks all four major furnishings plus custom floor
plant against room walls, desk, player spawn point and mutual overlap
using their actual rotated 2D footprints. Room plans cannot charge,
award or affect the server, and generic physics collisions for
furniture remain out of scope. Old version-one solo saves default
the new bed_layout and wardrobe_layout fields to original positions.

Roadmap: **#274 partially implemented** for three built-in layouts
in the single solo room. #265, #270, #271 and #273 remain partial;
they now include evidence for the bed and wardrobe slice.
**39 implemented in source, 43 partial, 918 pending audit,
0 device-verified and 0 released**. Unit/state and Godot rendered
scene smoke tests cover curated placements, rotation, backups, plan
application, undo/redo, restart restoration and preserved Game Cash.
Real Android/Windows behavior and production release gates still need
verification. Multiplayer property ownership/editing remains unchanged.

## Batch 13: two user-saved room bookmarks, offline and free

In addition to the three built-in Original / Study corner / Open floor
arrangements, a player can now **Save layout 1** or **Save layout 2** from
the private starter-room editor and later **Apply** either saved layout.
Each bookmark captures wall and floor style, the cosmetic plant's approved
preset/grid/rotation, and chair, table, bed and wardrobe positions. Bookmarks
store strictly cosmetics: fictional Game Cash, jobs, item ownership, login
credentials, server IDs and BLINK Coins are not copied into saved layouts.
The save data holds at most two complete decoration records; missing
bookmarks preserve compatibility with older version-one local files.

Each nested bookmark is validated against the same approved furniture
positions, rotated footprints and entry-clearance rules used by the live
editor. Unknown slot names, missing/extra fields, overlapping furniture,
bad rotations and nested currency are rejected before an atomic save.
Saving a bookmark is free, doesn't change the displayed room, and doesn't
consume undo history. Applying a bookmark becomes one reversible change;
undo/redo cannot delete user bookmarks. Local job payouts and the market
purchase preserve the bookmarks through the single save snapshot.

This is deliberately an **offline, device-local** prototype. These local
JSON saves aren't tamper-resistant, don't sync across devices, and don't
modify or confer access to multiplayer homes. It is not a public property
marketplace, paid feature or verified full release.

Roadmap requirement **#275 implemented in source** for these bounded two
save/apply slots; **#274 remains partial**, with three built-in layouts.
Totals: **40 implemented in source; 43 partially implemented;
917 pending audit; 0 device-verified; 0 released**. Regression tests
exercise both slots, persistence, invalid/overlapping bookmark rejection,
cash isolation, applying and undoing saved layouts. Latest-head CI must
pass and Android/Windows devices must still be tested before promoting.

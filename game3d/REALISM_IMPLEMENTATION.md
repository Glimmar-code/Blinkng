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

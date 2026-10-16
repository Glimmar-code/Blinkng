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

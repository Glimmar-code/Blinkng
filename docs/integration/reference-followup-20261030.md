# Reference follow-up: Create Post and Chat

The owner reaffirmed the supplied ten-stage Create Post sheet and charcoal/blue
conversation image as the targets. The pasted prior review identifies gaps; it
does not approve the current screenshots or authorize skipping acceptance gates.

## Repairs

- Android photo creation opens the in-app selection grid. It can browse recent
  permitted MediaStore photos using the same device adapter as Chat. Full and
  Android 14 limited access are respected; the system picker remains available.
  Browsing never uploads media. Shared selection limits remain unchanged.
- Gallery tiles contain images and numbered selections. Edit/remove/reorder live
  in a photo preview, preventing thumbnail controls from overlapping. Library
  order stays stable during selection; clearing the last selection keeps browsing
  open. Windows retains its native file chooser and puts its selected-photo
  action row below the cover label so controls fit their columns.
- Android and Windows light conversations use shared pale blue outgoing bubbles
  and dark text. Android light accents are cobalt instead of pink. The charcoal
  default and explicitly selectable Pink appearance remain available.

## New native evidence

`BlinkComposerNativeCaptureTest` uses full compositor screenshots, which include
the actual system IME and Media3 video surface. It asserts a visible keyboard and
loaded mention results, selects real synthetic MediaStore photos, and loads the
one-second test-only video before capturing its preview and trim timeline.
The runtime workflow requires all four new PNGs to exist. This is new coverage,
not a claim that the emulator tests have already run successfully.

The MP4 asset is a generated blue 160x120, 15 fps, one-second MPEG-4 fixture from
the existing DesktopMediaSmoke lavfi command. It is included only in the test APK.
No real account, publication, backend or user media is used by these captures.

Local validation before final candidate CI: 15 Android reference fixture cases
passed and native-test compilation succeeded on the follow-up; their clean
gallery and light-conversation PNGs were inspected. Windows' 37 local tests and
packaged app image build passed. Final local Android checks and fresh candidate
CI must verify the final source. Native GUI captures require separate review.

## Still distinct from visual completion

The gallery uses separate photo and video modes under the existing publishing
contract; it does not claim mixed photo/video album publication. Windows uses
desktop conventions. The device grid shows recent authorized photos, with the
system picker available for additional files. No exact pixel comparison or full
cross-client/backend acceptance is certified here. PR #646 remains Testlab work;
main and production Supabase must remain unchanged until the required gates pass.

The full composer fixture inherited the production trending-tag loader. The
follow-up adds an optional loader and injects test tags in visual fixtures,
removing that network dependency without changing normal production loading.
Native keyboard coverage additionally asserts that Ayo's suggestion is displayed.
Earlier capture attempts must not be described as network-free solely because
no account was signed in; no production writes or migrations were performed.

Both initial native runs on `37bf8380` failed the combined keyboard/mention
wait (41021564419 and 41021559536). No pass is claimed for those runs. The
follow-up separates mention loading from keyboard visibility, checks actual
application window IME insets instead of accessibility-window enumeration, and
requires the mention result to be displayed. Failed display captures are retained
before the test exits. The updated native source compiled successfully locally
(36 seconds); an emulator rerun is still required to establish its result.

On `be6ade3d`, native PR run 41023387177 passed instrumentation and required
capture export. Its simultaneous push run 41023381821 failed specifically waiting
for actual IME visibility after the mention had loaded. This is an unresolved
inconsistency, not an overall pass. The next test-only follow-up waits for native
composer-dialog focus before tapping, requires the IME before typing, then
asserts that the IME remains visible and the suggestion is displayed. It also
catches ComposeTimeoutException correctly so timeout screenshots are retained.
The new sequence still requires fresh native execution; assertions are not waived.

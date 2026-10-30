# Contest, Create Post and Chat verification update

This updates the historical reports in `reference-design-20261029.md` and
`contest-repair-20261029.md`. Work remains isolated on
`Testlab/contest-create-post-chat-20261029`, draft PR
[#646](https://github.com/Glimmar-code/Blinkng/pull/646).

## Latest repair and evidence update

Follow-up on `ef7fadac`: actual Android emulator runs **40965779514** and
**40965788539** passed instrumentation and screenshot export. Windows run
**40965788189** passed packaging, native video export and Windows DPAPI encrypted
draft round trip. Isolated Supabase service run **40965788275** passed. Other
candidate checks were still running when this update was written; this is not
an assertion that all checks passed.

A new explicit `--reference-ui-smoke` adapter captures the real packaged desktop
photo editor and the video editor at top/bottom scroll positions. The first local
attempt failed because Compose hosted its dialog inside the application window;
the adapter now handles both window modes while restricting capture to its own
process. The second local packaged Linux run captured all three PNGs, inspected
manually with the six photo tools, trim/frame/speed/filter/audio/overlay/export
controls visible. These are Linux GUI captures, not Windows evidence. The new
Windows CI step must execute and its PNGs must be reviewed separately. Its report
deliberately says `captured-awaiting-review`, never automatically visually passed.

The latest Testlab follow-up replaces several earlier holds below with actual
results. It does not certify a production deployment or an exact visual match.

- All required GitHub CI checks on `09c8947b` passed, including actual Android
  unit/lint/APK/AAB/instrumentation execution and both Windows jobs. Windows run
  40918665054 built EXE/MSI and a standalone app image, exercised the packaged
  H.264/AAC exporter, and uploaded installers.
- New isolated Supabase service run **40958927594** on `a4efb1dc` passed actual
  password sessions, Contest registration/approval/vote races, Realtime delivery,
  recipient-only reads, Storage ACLs/deletion, Chat poll permissions and posting
  guards. The first service run caught missing owner SELECT on Storage objects;
  migration `20261029190000_blink_contest_media_owner_select.sql` fixed the actual
  failure without weakening its assertion. See `supabase/tests/local-features/`.
- New real local Compose captures cover the full existing Create Post fixture
  matrix and rich Chat at three scroll positions. They exposed an overlapping
  text-editor header, unreadable inherited poll labels and an early blank mention
  capture. These are repaired with sequential layout, explicit contrast, and a
  real loaded-results wait. Reference choice icons/gradients and photo tools
  were also corrected. Original production content is never replaced with fixtures.
- Windows crop framing and text/sticker movement now affect exported pixels.
  The local desktop suite passed **37 tests**; Linux media CLI export/probe passed.
  A new Windows-only packaged DPAPI check awaits final-candidate CI execution. All 18 local reference-fixture tests and device-test compilation passed on the final UI source, including actual dark/light messaging preferences. Local Android lint also passed on the preceding layout repair; final CI must recheck its later UI refinements.

The local full Supabase image pull failed from disk exhaustion; it did not run
integration assertions or contact production. CI supplied the safe service host.
The application prerequisite/admin schema is synthetic: deployed-schema/admin
compatibility, full authorized message/post transport and two-running-client
acceptance remain unverified. Local captures do not establish hardware gestures,
video playback or complete Windows visual behavior. The final candidate must
pass fresh CI and remaining acceptance gates before main promotion.

The rest of this document retains historical evidence and the failures that
motivated these repairs; its earlier pending-CI/service statements are superseded
only where explicitly resolved above.

## GitHub recovery

Authenticated GitHub API access succeeded as `Glimmar-code`, with repository
push/admin access. Recovered `ebfcfb8b07e530cd716933039ccc231f84e20038` includes
Contest #638, Create Post #639 and charcoal Chat #644. Chat #640 was already
merged on 2026-10-29; it is the earlier foundation. Original open PRs/branches
remain preserved. No main merge or production deployment was performed.

Actual GitHub runs on `ebfcfb8b` completed successfully:

- Android quality: run 40907034055.
- Windows desktop build/test and EXE/MSI packaging: run 40907033923.
- Android runtime smoke: run 40909908596.

These results predate the native editor additions below. They must not be used
as evidence that the new Windows processor works in a packaged installation.
Required ruleset contexts include `validate`, `migration-safety`, `guard`,
`windows-build` and `parity`, with an up-to-date main baseline.

## Windows editing repair

The platform-independent posting limits/permissions remain in existing shared
policies. New code adapts local desktop files through Java2D and a pinned,
packaged LGPL FFmpeg processor; Android retains its native photo/Media3 adapters.

- Photos: EXIF orientation, crop presets, rotation, filters, brightness/contrast,
  text, simple symbols, mouse drawing, undo/redo, PNG or JPEG export. Originals
  are preserved; exports retain edited resolution and enforce upload limits.
- Videos: up to four ordered clips, per-clip trim, speed, aspect/letterboxing,
  filters/adjustments, text/symbol overlay, original volume and optional local
  music. Actual H.264/AAC export, progress, cancellation and partial-output
  cleanup replace the previous external-editing-only gap.
- Desktop preview opens the actual file in the OS player. This is not the
  reference's mobile timeline or embedded player. Freehand crop placement,
  draggable overlay placement and advanced animated effects are not implemented.
- FFmpeg/JavaCPP versions and license notices are packaged with desktop resources.
  Windows CI now exercises the packaged launcher with synthetic media after
  producing EXE/MSI installers, independently of classpath-based unit tests.

Local verification executed successfully on Linux/JDK 17/Gradle 8.12.1:

- Desktop `test`: **36 tests, zero failures/errors/skips**. Seven photo and four
  native-video tests include real exports/decoding, ordered frames, audio, EXIF,
  WebP, trim/speed and active cancellation cleanup.
- `run --args='--media-export-smoke …'`: produced/probed a real 1080×1080 MP4;
  report status `passed`.
- Full `npm test` in `supabase/tests`: passed, including isolated PostgreSQL
  Contest migrations/admin/approval/free-vote/ranking/timing and posting guards.
- `python3 supabase/tests/contest-concurrency.py`: passed against a newly created
  networkless PostgreSQL 17 container using a pinned image and synthetic schema.
  Eight independent connections produced exactly one vote and notification;
  anonymous/direct-table/admin escalation/self-vote and pause gates passed.
  The exact test container was removed. This is not hosted Auth/Storage evidence.
  A subsequent run exposed the image's temporary initialization server readiness
  race. Waiting for the final TCP server fixed it; the complete test then passed.

An initial export test failed because the LGPL build lacks FFmpeg's `eq` filter.
The implementation now uses supported RGB LUT filters; the full suite reran
successfully. No checks were disabled to obtain that result.

## Outstanding verification and release hold

The owner confirmed **no separate Supabase staging project is available**.
No production database, Auth setting, bucket or deployment was modified/reset.
PGlite SQL and local request tests do not establish these integration gates:

- Full migration compatibility with the deployed schema, hosted grants/RLS and
  recovery on a disposable copy of the schema.
- Real authenticated admin/entrant/voter accounts, Storage upload/ACL/download,
  moderation and vote races, notifications and Realtime propagation.
- Cross-client Contest lifecycle, post/poll publication/expiry, media uploads,
  chat delivery/receipts/reconnect and authorization failures.

These require free isolated Supabase infrastructure with Auth/Storage/Realtime
or a separately supplied staging project; production must not substitute for it.

Existing real Robolectric captures of Chat and Create Post landing were inspected.
They confirm the current styling, but contain limited synthetic content and cover
only two surfaces. Original raster reference files are absent from the workspace,
so exhaustive pixel comparisons have not been performed. Android hardware/KVM and
a Windows desktop session are unavailable here. Remaining reference screens,
device gestures, DPAPI and two-client interaction need actual runtime evidence.

GitHub runtime screenshot artifacts exist, but their redirect host
`productionresultssa2.blob.core.windows.net` is blocked by the current network
policy. A draft adds that exact host; saving does not apply/publish the environment.
This blocks artifact inspection, not local testing. New Windows CI must also pass
on the editor commit. Main remains held until all applicable verification passes.

The first Windows CI runs on `217f3830` failed in compile/test before packaging.
The workflow now emits JUnit failures as check annotations, overcoming the log
download restriction. Run 40915683469 confirmed the actual failure:
`cancellingAnActiveExportRemovesPartialOutput` left a native export log behind.
Windows held the file until the killed process exited. Cleanup now waits for
termination before deletion; the full local 36-test suite passed again. Editors
also inherit the reference dark palette when opened from a light desktop host.
Fresh Windows build/test/packaged-export verification is still required for
this repair. Diagnostic annotations never waive a failure.

On `9654accc`, the PR Windows job passed compile/tests and reached packaging,
while the simultaneous push job reproduced brief log-handle retention after
process exit. Temporary-file deletion now retries for at most three seconds,
then reports a real cleanup error rather than silently leaving files. The
cancellation test/assertions remain intact; all 36 local tests passed again.
The executable's `-L` output was also inspected: this native build enables
LGPLv3. Matching LGPLv3 and incorporated GPLv3 texts are included and verified
inside the desktop JAR alongside the existing notices.

Both Windows jobs on `09f31fb8` passed compile/tests and built EXE/MSI installers.
The diagnostic annotations then identified a CI setup failure: the standalone
launcher image was absent. Installer generation does not guarantee that image;
the build now explicitly requests `createDistributable` before running its
launcher. This is not yet evidence that the Windows packaged media export passes.
The same local Linux packaged-image task and actual launcher export both passed
using the trimmed bundled JRE, independently of Gradle's test/run classpath.

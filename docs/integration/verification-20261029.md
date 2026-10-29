# Contest, Create Post and Chat verification update

This updates the historical reports in `reference-design-20261029.md` and
`contest-repair-20261029.md`. Work remains isolated on
`Testlab/contest-create-post-chat-20261029`, draft PR
[#646](https://github.com/Glimmar-code/Blinkng/pull/646).

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
The actual failure logs redirect to the blocked artifact host; the visible
annotation only reports exit 1. The workflow now emits JUnit failures and the
build-log tail as check annotations so the real error can be diagnosed through
the accessible GitHub API. This diagnostic change does not waive the failure.

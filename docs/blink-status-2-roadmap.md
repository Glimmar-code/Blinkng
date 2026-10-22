# BLINK Status 2.0 — staged delivery checklist

**Scope:** Android, Windows, the shared Supabase backend. Do not merge historical
Testlab into main wholesale. All production changes go through focused staging
work with compatible old clients; don't publish an APK after every increment.

## Foundation — staged in this branch, not deployed
- [x] Android own status can open in the viewer; placeholder remains excluded.
- [x] Android Media3 video playback supports pause/resume and lifecycle disposal.
- [x] Paused statuses do not reset their timer.
- [x] Android fetch excludes expired stories without waiting for server deployment.
- [x] Shared 24-hour contract and targeted unit tests.
- [x] Versioned migration adds database RLS expiration and scheduled deactivation.
- [ ] Android/Windows CI, runtime replay and security checks pass on latest SHA.
- [ ] Verify schema + RLS against a **staging/preview** database; no live DDL yet.
- [ ] Release one consolidated signed APK after integration and verification.

## Security before private Status audiences
- [ ] RLS + SECURITY INVOKER / carefully audited server helper for mutual blocks,
      close friends, campus scope, original author, and story expiry.
- [ ] Migrate private stories to private Storage with authorization-checked signed
      delivery; existing public URLs cannot be retroactively made private.
- [ ] Scope views/reactions/replies to a visible, unexpired story and limit abuse.
- [ ] Explicit grants for any new Data API table and authenticated ownership.
- [ ] Consistent audience tests: owner, friend, stranger, blocked both ways,
      expired story, logged out account, account switch.

## Media, creation and management
- [ ] Stream upload (no full file readBytes), limit file size/duration, verify format.
- [ ] Optional crop/trim and compressed preview; text-only backgrounds and drafts.
- [ ] Music, stickers, location and mentions only with rights/privacy checks.
- [ ] Delete story (and media where appropriate), progress/cancel, error recovery,
      storage lifecycle, owner-only viewer insights.
- [ ] Story-linked DM previews with deleted/expired-message fallback.
- [ ] Repost with explicit consent and anti-duplication policy; reuse PR #396 only
      after rebasing individual changes onto current production.
- [ ] Responsive Story bar, per-creator groups, light/dark, font-scale tests.

## Campus and optional monetization (later)
- [ ] Campus announcements, voluntary interactive polls and Q&A.
- [ ] Marketplace item previews with verified sellers, report/block controls.
- [ ] Public sponsored status placements and optional gifts only after existing
      payment/reward backend security and policies are verified.

**Release policy:** Main stays unchanged until targeted tests pass. No production
Supabase migration, no automatic APK publication, and no temporary phone APK
downloads without verified sign-off. User-facing feature semantics must retain
Android/Windows parity or document an explicit platform exception.

## Phase 4 progress — public Status safety and repost consent

- [x] Stage creator opt-in/opt-out per original Status; default is OFF.
- [x] Stage server-validated reposts: non-self, unblocked, unmuted, unexpired originals only.
- [x] A repost inherits the original 24-hour lifetime; duplicate taps are idempotent.
- [x] Revoking consent removes derived repost records; original deletion cascades.
- [x] Account blocking in either direction and viewer muting are checked by
      authenticated database SELECT policy (regardless of a local feed cache).
- [x] Reaction, like, view, and reply INSERT policies reject inaccessible stories.
- [x] Android viewer: "Allow reposts" toggle for authors; "Repost" with consent
      and "Mute" with confirmation for viewers.
- [x] Added disposable PostgreSQL contract tests for visibility, tampering,
      revocation, expiration, and sharing replay.
- [ ] Complete Android, Windows, Supabase checks on the **latest** commit.
- [ ] Staging database proof (no paid Supabase branch available yet). Do not
      treat successful SQL parsing as production authorization validation.
- [ ] Full audience-based privacy (Close Friends, campus-only, excluded users):
      **NOT IMPLEMENTED**. The `story-media` bucket is public. Row filtering,
      creator opt-in and mute/block controls do not secure URLs already known
      to other parties. Migrate to a private bucket and signed, per-viewer
      authorized media delivery before displaying privacy audience controls.
- [ ] Window desktop's dedicated Story UI and full parity still missing.

## Phase 5 — text Status creation (staged)

- [x] Android Status composer offers Photo, Video and Text choices.
- [x] Text statuses have five whitelist-validated colour backgrounds.
- [x] Text content is limited to 240 characters, with a live counter.
- [x] Emoji stamps insert into text and are persisted in the status itself.
- [x] Text status publishing sends `media_type=text`, `text`, `caption`
      and `background` through the authenticated Stories API, without
      uploading an empty image or requiring media storage.
- [x] Story model and immersive viewer render selected text backgrounds.
- [x] Device-local, user-scoped text/caption drafts autosave; draft contents
      are not sent to Supabase until Share is pressed. Successful publishing
      clears the user's local draft; failures retain it.
- [x] Pure Kotlin tests cover text bounds and colour whitelist fallback.
- [ ] Verify Android compile, text composition layout, database INSERT and
      profile logout/account switching (automated/emulator preferred).
- [ ] Full photo editing/cropping/filters NOT implemented.
- [ ] Video trimming/export NOT implemented.
- [ ] Sticker positioning/dragging NOT implemented; emoji inserts are text.
- [ ] Secure Close Friends/campus-only statuses NOT implemented: existing
      `story-media` bucket is public and cannot enforce private audiences.
- [ ] No production migration or APK release until checks/staging validations.

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

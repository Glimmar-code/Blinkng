# BLINK → Cloudflare R2 media migration (Testlab; not live)

**Status (2026-10-27):** implementation candidate only. Production Supabase Storage
is unchanged. Cloudflare R2 buckets, credentials, billing and custom domain have
not been created or verified from this workspace. This PR MUST NOT be merged or
deployed until Cloudflare access, secure staging tests, media rights, and both
platforms' end-to-end tests pass. Existing images/reels/chats must continue to
load from their original Supabase URLs during migration.

## Production inventory at kickoff (metadata only)

Supabase project `jhwgifrlxwspoedxjaly` contains the following nonempty
Storage buckets as observed on 2026-10-27:

| Source bucket | Objects | Approx. MB | R2 destination |
| --- | ---: | ---: | --- |
| post-media | 31 | 91.01 | blink-public-media/legacy/post-media |
| profile-media | 14 | 8.35 | blink-public-media/legacy/profile-media |
| story-media | 4 | 3.29 | blink-temporary-media/legacy/story-media |
| covers | 1 | 0.99 | blink-public-media/legacy/covers |
| avatars | 2 | 0.71 | blink-public-media/legacy/avatars |
| message-media | 3 | 0.20 | blink-private-media/legacy/message-media |
| blink-bug-reports | 1 | 0.07 | blink-private-media/legacy/blink-bug-reports |

**56 objects / approximately 104.62 MB** according to metadata; numbers may
change. Empty buckets include `blink-channel-media`, `market-media`,
`market-video`, `marketplace-media`, `feed-media`, `status-media`,
`posts`, and `blink-live-banners`. Source bucket ACLs determine
classification, not whether a post is currently public. The
`message-view-once` bucket is deliberately **excluded** from automatic
migration: atomic open-and-delete semantics must be ported separately.

## R2 account prerequisites (owner action)

1. Activate an R2 subscription in the Cloudflare account. Creating buckets or
   enabling billable services needs the owner's explicit Cloudflare setup.
2. Create **three private-by-default buckets** exactly named
   `blink-public-media`, `blink-private-media`,
   `blink-temporary-media`. Only `blink-public-media` may have an
   internet-readable custom domain.
3. Configure `media.blink.com.ng` as the custom domain for the **public**
   bucket, with caching and abuse controls. Never attach a public domain or
   `r2.dev` public access to the private or temporary bucket.
4. Use separate limited-access **server-only** R2 access credentials. Set
   `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, and
   `BLINK_R2_PUBLIC_MEDIA_ORIGIN=https://media.blink.com.ng` as Supabase
   Edge Function secrets, **never in GitHub, APK, EXE, or browser**.
5. Keep `BLINK_R2_ENABLED` **unset or false** until the staging ledger
   migration, signature flow, quota/rate controls and both-platform tests pass.
6. Configure R2 browser upload CORS for `https://blink.com.ng` and
   `https://www.blink.com.ng`, allowing `PUT` with `Content-Type`, and
   expose `ETag` as needed. Native uploads do not depend on CORS.
7. Configure lifecycle expiration of **unreferenced temporary content** only
   after verifying Stories' retention window, view-once behaviour and legal
   retention requirements. Never bulk-delete arbitrary message objects.

## Step 1 — Copy existing objects without touching Supabase

Configure two `rclone` **S3 provider** remotes with their respective endpoints
and credentials in the machine's protected configuration, not this repository.
Supabase's Storage S3 endpoint and credentials must be obtained from the
Supabase Storage S3 settings; for R2 use the Cloudflare account-specific
`<account_id>.r2.cloudflarestorage.com` endpoint, region `auto`. The
`rclone` command requires a host with both providers reachable.

```bash
# Step 1a: metadata-only report; DEFAULT mode does not write to either provider
python3 scripts/migrate_supabase_media_to_r2.py --mode plan \
  --source blink-supabase: --target blink-r2: \
  --report r2-plan.json

# Step 1b: after reviewing bucket classification, copy with NO source deletes
python3 scripts/migrate_supabase_media_to_r2.py --mode copy \
  --source blink-supabase: --target blink-r2: \
  --report r2-copy.json

# Step 1c: byte-for-byte compare sources and destinations
python3 scripts/migrate_supabase_media_to_r2.py --mode verify \
  --source blink-supabase: --target blink-r2: \
  --report r2-verify.json
```

`copy` uses `--ignore-existing` so it can resume interrupted work, and
`verify` downloads bytes to compare; it may generate transfer costs. If a
pre-existing destination differs, halt, investigate and repair explicitly.
Reports are aggregates only. **Do not run `rclone sync`/purge on sources.**

## Step 2 — Staged new-upload routing

The proposed `blink-r2-media` Edge Function has
`init → signed PUT → complete (HEAD validation)`; it authenticates the
Supabase JWT server-side and stores a ledger row in
`public.blink_r2_uploads`. It refuses all requests while
`BLINK_R2_ENABLED` is not exactly `true`.

- Android and Windows route eligible *public* feed images/Reels,
  profile images, and marketplace media through the same authenticated
  contract. They fall back to Supabase only when R2 is deliberately disabled
  or the new function has not yet been deployed. They must not silently
  duplicate uploads after a real provider failure.
- The website must use the same contract before the public cutover.
- Stories, Channels, private chat attachments, voice notes and view-once
  **stay on Supabase** until their private read/visibility rules, access-token
  refreshes, cache invalidation, deletion semantics and existing client
  routing are complete. The Edge Function's private flow is preparatory,
  not evidence these clients have migrated.
- The R2 ledger migration is versioned and intentionally **not applied**
  to production as part of this draft. No application URLs have been
  rewritten; the bulk copy script never changes database references.

## Step 3 — Mandatory verification before main

- Provision a no-extra-cost local/isolated Supabase test database or obtain
  explicit approval for an available hosted staging environment. Never
  silently incur costs for Supabase branches.
- Deploy the ledger and Edge Function to isolated staging; verify
  authentication, CORS, MIME limits, owner checks, channel contributor roles,
  chat membership, signed PUT/GET expiry, orphan cleanup, billing/rate limits,
  and attempted over-limit uploads. Add a server-enforced streaming
  content-length limit before enabling public uploads, since PUT presigned
  URLs alone cannot guarantee upload byte caps or single use.
- Verify all copied objects against source bytes and inventory counts.
- Resolve original URLs, thumbnails, multi-photo albums, private attachments,
  expired Stories, deleted messages, and view-once claims. Do not disclose
  signed chat object URLs through logs, public API feeds or cached HTML.
- Test Android and Windows against staging: offline retries, large video
  streaming, cancellation, multiple accounts, sharing, open-in-chat,
  posts/Reels playback, Story audiences, channel subscribers, and old-client
  compatibility. Test web parity.
- Review costs and CDN/R2 read request rate. Test rollback by disabling
  `BLINK_R2_ENABLED` and verifying Supabase originals continue to load.
- Only then perform a staged read-routing and database reference backfill,
  preserving original `supabase_bucket/path` until verification; use an
  explicit per-object mapping and transaction log, **never blind string
  replacement**. Keep originals for a defined rollback window.

## CI / release restriction

The code-only checks in the PR are helpful but **do not establish R2
integration**. No Cloudflare account connector is connected, no R2 keys have
been provisioned through this conversation, and there is no verified
staging Cloudflare bucket. Keep Testlab candidate open and production unchanged
until these dependencies and the feature parity checklist are resolved.

Documentation: Cloudflare R2 S3 compatibility / presigned URL / CORS / rclone
and Supabase Storage S3 interoperability.

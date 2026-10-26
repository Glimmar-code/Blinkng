import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';

// Cross-platform shared MIME rules must remain exactly aligned with the
// server-owned bucket allowlist. This is a release contract, not a substitute
// for hosted Storage tests.
const source = fs.readFileSync(
    new URL('../../shared/src/main/kotlin/com/blinkng/shared/BlinkChatAttachmentPolicy.kt', import.meta.url), 'utf8'
);
const migration = fs.readFileSync(
    new URL('../../supabase/migrations/20261025100000_chat_photo_albums_and_bucket_limits.sql', import.meta.url), 'utf8'
);
const mimeSource = source.split('fun accepts(mime: String)')[1]?.split('fun verify(mime: String')[0];
const sqlMimeSource = migration.match(/allowed_mime_types\s*=\s*array\[([\s\S]*?)\]::text\[\]/)?.[1];
assert.ok(mimeSource && sqlMimeSource, 'Attachment policy and SQL bucket allowlist must both exist');
const collectMime = value => new Set([...value.matchAll(/"((?:image|video|audio|application|text)\/[^"]+)"|'((?:image|video|audio|application|text)\/[^']+)'/g)].map(match => match[1] ?? match[2]));
const clientTypes = collectMime(mimeSource);
const serverTypes = collectMime(sqlMimeSource);
assert.ok(clientTypes.size >= 20, 'An incomplete MIME allowlist is not acceptable');
assert.deepEqual([...serverTypes].sort(), [...clientTypes].sort(), 'Storage MIME allowlist must equal Android/Windows shared policy');
assert.ok(!serverTypes.has('application/octet-stream'), 'Reject unrestricted binary uploads');
assert.ok(source.includes('VIDEO("video", 80L * 1024 * 1024'), 'Bucket ceiling must match max video size');
assert.ok(migration.includes('file_size_limit=83886080'), 'Missing server-side 80 MiB ceiling');
assert.ok(migration.includes('BLINK_MESSAGE_MEDIA_BUCKET_CONSTRAINTS_MISSING'), 'Missing post-migration assertions');
console.log('PASS shared Android/Windows MIME and private bucket contract');
const db = new PGlite();
try {
 for (const path of [
  'scripts/testing/chat_media_staging_fixture.sql',
  'supabase/migrations/20261025100000_chat_photo_albums_and_bucket_limits.sql',
  'supabase/migrations/20261025103000_view_once_private_media_contract.sql',
  'scripts/testing/chat_media_contract_assertions.sql',
 ]) {
  try { await db.exec(fs.readFileSync(new URL('../../'+path,import.meta.url),'utf8')); }
  catch(error) { console.error('FAIL',path,error.message); process.exitCode=1; break; }
  console.log('PASS',path);
 }
} finally { await db.close(); }

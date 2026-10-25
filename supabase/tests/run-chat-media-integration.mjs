import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
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

import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
const db = new PGlite();
const paths = [
  'supabase/tests/referral_postgres_fixture.pgfixture',
  'supabase/migrations/20261024203000_blink_refer_and_grow.sql',
  'supabase/migrations/20261025114000_referral_coins_and_discovery_attribution.sql',
  'supabase/migrations/20261025153000_referral_delivery_and_link_telemetry.sql',
  'supabase/migrations/20261025190000_blink_group_invitation_requests.sql',
  'docs/integration/group-invitation-consent-staging-20261025.sql',
  'supabase/migrations/20261025201000_referral_viewability_impressions.sql',
  'supabase/tests/referral_postgres_assertions.pgfixture',
];
try {
  for (const path of paths) {
    try { await db.exec(fs.readFileSync(new URL('../../' + path, import.meta.url), 'utf8')); }
    catch (error) { console.error('FAIL', path, error.message); process.exitCode = 1; break; }
    console.log('PASS', path);
  }
} finally { await db.close(); }

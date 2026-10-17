import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import { pg_trgm } from '@electric-sql/pglite/contrib/pg_trgm';
import { behaviorTests } from './behavior-tests.mjs';

const fixture = JSON.parse(fs.readFileSync(new URL('./fixtures/promotion-baseline.json', import.meta.url), 'utf8'));
const migrations = [
  '20261006235500_blink_growth_suite_v3.sql',
  '20261007005500_profile_user_profile_hardening.sql',
  '20261007011000_complete_professional_chat_controls.sql',
  '20261007113500_fix_stale_notification_writers.sql',
  '20261007114500_correct_legacy_rls_policy_commands.sql',
  '20261007115500_optimize_flagged_rls_auth_initplans.sql',
  '20261007120500_keep_ranked_profile_projections_private.sql',
  '20261007121500_fix_study_circle_rls_recursion.sql',
];
const db = new PGlite({ extensions: { pg_trgm } });
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema private; create schema private_ranking; create schema extensions; create schema cron;
    create table auth.users(id uuid primary key, email text);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema public,auth,extensions,private_ranking to authenticated;
    grant execute on function auth.uid() to authenticated;
    create table cron.job(jobid bigint generated always as identity primary key,jobname text unique,schedule text,command text);
    create function cron.schedule(text,text,text) returns bigint language sql as $$insert into cron.job(jobname,schedule,command) values ($1,$2,$3) returning jobid$$;
    create publication supabase_realtime;
    create extension pg_trgm with schema extensions;`);
  for (const sql of fixture.schema) await db.exec(sql);
  const pending = [...fixture.functions];
  while (pending.length) {
    let created = 0;
    for (let i = pending.length - 1; i >= 0; i--) {
      try { await db.exec(pending[i].definition); pending.splice(i, 1); created++; }
      catch (error) { pending[i].lastError = error.message; }
    }
    if (!created) throw new Error('Unresolved fixture functions: ' + pending.map(row => row.proname + ': ' + row.lastError).join('; '));
  }
  await db.exec('grant select,insert,update,delete on all tables in schema public to authenticated;');
  for (const sql of fixture.policies) await db.exec(sql);
  for (const name of migrations) {
    await db.exec(fs.readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
    console.log('PASS migration:', name);
  }
  await behaviorTests(db);
  await (await import('./ranked-profile-tests.mjs')).rankedProfileTests(db);
  await (await import('./legacy-hardening-tests.mjs')).legacyHardeningTests(db);
  await (await import('./feed-recovery-tests.mjs')).feedRecoveryTests(db);
  await (await import('./discovery-exhaustion-tests.mjs')).discoveryExhaustionTests(db);
  await (await import('./tag-search-interest-tests.mjs')).tagSearchInterestTests(db);
  for (const name of migrations) {
    await db.exec(fs.readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
    console.log('PASS reapply:', name);
  }
  // Independent disposable DB validates marketplace order contracts against
  // the actual tracked SQL migration; no production credentials are used.
  await (await import('./marketplace-order-tests.mjs')).marketplaceOrderTests();
  // PGlite has no Supabase-managed storage schema. Provide only the storage
  // interfaces used by this migration, then apply the COMPLETE migration on
  // the disposable fixture. Never target the production project here.
  await db.exec(`reset role;
    create schema if not exists storage;
    create table if not exists storage.buckets (
      id text primary key, name text not null, public boolean not null default false,
      file_size_limit bigint, allowed_mime_types text[]
    );
    create table if not exists storage.objects (
      id uuid primary key default gen_random_uuid(), bucket_id text not null,
      name text not null
    );
    alter table storage.objects enable row level security;
    create or replace function storage.foldername(p_path text)
      returns text[] language sql immutable
      as $$select (string_to_array(p_path, '/'))[1:array_length(string_to_array(p_path, '/'),1)-1]$$;
  `);
  const marketHardeningSql = fs.readFileSync(
    new URL('../migrations/20261007005000_market_professional_hardening.sql', import.meta.url), 'utf8');
  await db.exec(marketHardeningSql);
  console.log('PASS full Market hardening migration against disposable fixture');
  await db.exec(marketHardeningSql);
  console.log('PASS reapply full Market hardening migration');

  const marketTrustSql = fs.readFileSync(
    new URL('../migrations/20261017153000_market_verified_sellers_and_listing_limits.sql', import.meta.url), 'utf8');
  await db.exec(marketTrustSql);
  console.log('PASS full verified-seller Market migration against disposable fixture');
  await db.exec(marketTrustSql);
  console.log('PASS reapply verified-seller Market migration');
  await (await import('./market-seller-trust-tests.mjs')).marketSellerTrustTests(db);

} catch (error) {
  console.error('Testlab migration regression failed:', error.message);
  process.exitCode = 1;
} finally {
  await db.close();
}

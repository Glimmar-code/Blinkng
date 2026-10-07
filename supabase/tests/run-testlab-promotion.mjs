import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import { behaviorTests } from './behavior-tests.mjs';

const fixture = JSON.parse(fs.readFileSync(new URL('./fixtures/promotion-baseline.json', import.meta.url), 'utf8'));
const migrations = [
  '20261006235500_blink_growth_suite_v3.sql',
  '20261007005500_profile_user_profile_hardening.sql',
  '20261007011000_complete_professional_chat_controls.sql',
  '20261007113500_fix_stale_notification_writers.sql',
  '20261007114500_correct_legacy_rls_policy_commands.sql',
  '20261007115500_optimize_flagged_rls_auth_initplans.sql',
];
const db = new PGlite();
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema private; create schema private_ranking; create schema extensions; create schema cron;
    create table auth.users(id uuid primary key, email text);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema public,auth to authenticated;
    grant execute on function auth.uid() to authenticated;
    create table cron.job(jobid bigint generated always as identity primary key,jobname text unique,schedule text,command text);
    create function cron.schedule(text,text,text) returns bigint language sql as $$insert into cron.job(jobname,schedule,command) values ($1,$2,$3) returning jobid$$;
    create publication supabase_realtime;`);
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
  await db.exec(`
    create or replace function public.record_game_session(p_game_type text, p_score integer, p_coins_earned integer)
    returns jsonb language sql as 'select jsonb_build_object(''score'',p_score,''coins'',p_coins_earned)';
    grant execute on function public.record_game_session(text,integer,integer) to authenticated;
  `);
  await db.exec('grant select,insert,update,delete on all tables in schema public to authenticated;');
  for (const sql of fixture.policies) await db.exec(sql);
  for (const name of migrations) {
    await db.exec(fs.readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
    console.log('PASS migration:', name);
  }
  await behaviorTests(db);
  for (const name of migrations) {
    await db.exec(fs.readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
    console.log('PASS reapply:', name);
  }
} catch (error) {
  console.error('Testlab migration regression failed:', error.message);
  process.exitCode = 1;
} finally {
  await db.close();
}

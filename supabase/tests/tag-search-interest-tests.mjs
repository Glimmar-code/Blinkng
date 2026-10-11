import assert from 'node:assert/strict';
import fs from 'node:fs';

export async function tagSearchInterestTests(db) {
  const migration = fs.readFileSync(
    new URL('../migrations/20261011104000_blink_tag_search_interest_signals.sql', import.meta.url),
    'utf8'
  );
  await db.exec(`
    reset role;
    create table if not exists public.blink_tag_search_events(
      user_id uuid not null, tag text not null, searched_hour timestamptz not null,
      primary key(user_id,tag,searched_hour)
    );
    alter table public.blink_tag_search_events enable row level security;
    revoke all on public.blink_tag_search_events from public,anon,authenticated;
    create table if not exists private_ranking.test_tag_interest_audit(
      user_id uuid not null, tag text not null
    );
    create or replace function private_ranking.bump_interest(
      p_user_id uuid, p_surface text, p_feature_type text,
      p_feature_value text, p_delta numeric
    ) returns void language plpgsql security definer set search_path to '' as $fn$
    begin
      if p_surface='feed' and p_feature_type='tag' and p_delta <= 1 then
        insert into private_ranking.test_tag_interest_audit(user_id,tag)
        values(p_user_id,p_feature_value);
      end if;
    end;
    $fn$;
    create or replace function private_ranking.refresh_discovery_interest_cache(p_user_id uuid)
    returns void language plpgsql security definer set search_path to '' as $fn$
    begin
      return;
    end;
    $fn$;
  `);
  await db.exec(migration);
  await db.exec(`select set_config('request.jwt.claim.sub','00000000-0000-4000-8000-000000000001',false);
    set role authenticated;`);
  await db.query("select public.blink_record_tag_search('#FUTA')");
  await db.query("select public.blink_record_tag_search('futa')");
  await db.query("select public.blink_record_tag_search('#Coding')");
  await db.query("select public.blink_record_tag_search('bad-tag')");

  await db.exec('reset role');
  const eventCount = (await db.query(
    "select count(*)::integer as n from public.blink_tag_search_events"
  )).rows[0].n;
  const signalCount = (await db.query(
    "select count(*)::integer as n from private_ranking.test_tag_interest_audit"
  )).rows[0].n;
  assert.equal(eventCount, 2, 'repeat tag searches are deduplicated per user/hour');
  assert.equal(signalCount, 2, 'only unique searches train interests');
  await db.exec(migration);
  await db.exec(`select set_config('request.jwt.claim.sub','',false); set role anon;`);
  await assert.rejects(db.query("select public.blink_record_tag_search('futa')"), /permission denied/);
  await db.exec('reset role');
  console.log('PASS tags: unique searches teach interests, anon denied, migration reapply-safe');
}

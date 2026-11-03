// Standalone, disposable PGlite integration test. Never connects to hosted Supabase.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const sqlFile = name => fs.readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8');
const leaderboardSql = sqlFile('20261023120500_secure_public_leaderboard_visibility.sql');
const referralsSql = sqlFile('20261026110000_referral_review_pauses_discovery_boost.sql');
const fairnessSql = sqlFile('20261027224500_algorithm_discovery_fairness.sql');
const extract = (text, start, end) => {
  const a = text.indexOf(start);
  const b = text.indexOf(end, a);
  if (a < 0 || b <= a) throw new Error('Source function extraction failed: ' + start);
  return text.slice(a,b);
};
const db = new PGlite();
const ids = ['00000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000002',
  '00000000-0000-4000-8000-000000000003',
  '00000000-0000-4000-8000-000000000004',
  '00000000-0000-4000-8000-000000000005'];
try {
  await db.exec(`
    create role authenticated;
    create role anon;
    create schema auth; create schema private;
    create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create table public.profiles(
      id uuid primary key, username text, name text, full_name text,
      avatar_url text, university text, created_at timestamptz default now(),
      points integer default 0, faculty text, academic_level text,
      verification_badge text, is_verified boolean default false,
      onboarding_completed boolean default true
    );
    create table public.game_profiles(
      user_id uuid primary key, coins bigint default 0,
      streak integer default 0, best_streak integer default 0
    );
    create table public.user_settings(user_id uuid primary key, private_account boolean default false);
    create table public.follows(follower_id uuid,following_id uuid, primary key(follower_id,following_id));
    create table public.blocks(blocker_id uuid,blocked_id uuid);
    create table public.muted_users(user_id uuid,muted_id uuid);
    create table public.blink_referrals(referrer_user_id uuid,status text,confirmed_at timestamptz);
    create table private.blink_referral_risk_reviews(referrer_user_id uuid,resolved_at timestamptz);
    grant usage on schema public,auth to authenticated;
    grant execute on function auth.uid() to authenticated;
  `);
  await db.exec(extract(leaderboardSql,
    'CREATE OR REPLACE FUNCTION public.blink_public_leaderboard_rows()',
    '-- Functions are executable by PUBLIC'));
  await db.exec(extract(referralsSql,
    'create or replace function public.blink_referral_recommendations(',
    'revoke all on function public.blink_referral_recommendations(integer)'));
  for (let i=0;i<ids.length;i++) {
    const score = i<3 ? 100 : i===3 ? 50 : 25;
    await db.query('insert into public.profiles(id,username,name,full_name,university,points) values ($1,$2,$3,$4,$5,$6)',
      [ids[i], 'algorithm'+i,'Test '+i,'Test '+i,'FUTA',score]);
  }
  await db.query('insert into public.follows(follower_id,following_id) values ($1,$2)',[ids[0],ids[2]]);
  await db.query('insert into public.muted_users(user_id,muted_id) values ($1,$2)',[ids[0],ids[3]]);
  await db.exec(fairnessSql);
  await db.exec(fairnessSql);
  await db.query("select set_config('request.jwt.claim.sub',$1,false)",[ids[0]]);
  const rankRows = (await db.query('select user_id,world_rank,campus_rank from public.blink_public_leaderboard_rows()')).rows;
  for(let i=0;i<3;i++){
    const row=rankRows.find(x=>x.user_id===ids[i]);
    assert.equal(Number(row.world_rank),1,'equal score must share world rank');
    assert.equal(Number(row.campus_rank),1,'equal campus score must share campus rank');
  }
  assert.equal(Number(rankRows.find(x=>x.user_id===ids[3]).world_rank),4,'RANK() preserves normal tie gaps');
  const suggestionRows=(await db.query('select id from public.blink_referral_recommendations(10)')).rows.map(x=>x.id);
  assert.ok(!suggestionRows.includes(ids[0]),'do not suggest self');
  assert.ok(!suggestionRows.includes(ids[2]),'do not suggest already-followed');
  assert.ok(!suggestionRows.includes(ids[3]),'do not suggest muted');
  assert.ok(suggestionRows.includes(ids[1]),'retain suitable organic discoveries');
  console.log('PASS algorithm fairness: rank ties, exclusions, migration idempotence');
} finally {
  await db.close();
}

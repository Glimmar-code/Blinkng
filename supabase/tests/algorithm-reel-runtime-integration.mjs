// Execute the actual proposed Reel RPC in an isolated PGlite database, never against hosted Supabase.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const migration=readFileSync(new URL('../migrations/20261027210000_algorithm_integrity_security_reels.sql',import.meta.url),'utf8');
const a=migration.indexOf('create or replace function public.record_reel_engagement(');
const b=migration.indexOf('revoke all on function public.record_reel_engagement(',a);
assert.ok(a>0&&b>a,'Reel RPC source boundaries changed');
const reelSql=migration.slice(a,b);
const db=new PGlite();
const author='00000000-0000-4000-8000-000000000011';
const viewer='00000000-0000-4000-8000-000000000012';
const reel='00000000-0000-4000-8000-000000000013';
const sessions=['00000000-0000-4000-8000-000000000021','00000000-0000-4000-8000-000000000022','00000000-0000-4000-8000-000000000023','00000000-0000-4000-8000-000000000024'];
const record=(sid,watched,duration=10000)=>db.query(
  'select public.record_reel_engagement($1::uuid,$2::integer,$3::integer,$4::uuid,false,false)',
  [reel,watched,duration,sid]
);
const stats=async()=> (await db.query('select watch_samples,completed_count,skip_count,completion_rate from private_ranking.discovery_reel_metrics where post_id=$1',[reel])).rows[0];
try {
  await db.exec(`
    create role authenticated;
    create role anon;
    create schema auth; create schema private_ranking;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable
      as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    create table public.feed_posts (
      id uuid primary key, user_id uuid not null, is_reel boolean not null default false,
      is_active boolean not null default true,is_flagged boolean not null default false,
      audience text default 'Everyone',expires_at timestamptz
    );
    create table public.user_settings(user_id uuid,private_account boolean);
    create table public.follows(follower_id uuid,following_id uuid);
    create table public.blocks(blocker_id uuid,blocked_id uuid);
    create table public.muted_users(user_id uuid,muted_id uuid);
    create table private_ranking.discovery_reel_metrics(
      post_id uuid primary key, completion_samples bigint not null default 0,
      completion_rate numeric not null default 0,updated_at timestamptz not null default now(),
      watch_samples bigint not null default 0,watch_percentage_sum numeric not null default 0,
      completed_count bigint not null default 0,rewatch_count bigint not null default 0,
      skip_count bigint not null default 0
    );
    create table private_ranking.reel_engagement_sessions(
      session_id uuid primary key,
      viewer_id uuid not null references auth.users(id) on delete cascade,
      post_id uuid not null references public.feed_posts(id) on delete cascade,
      watched_ms integer not null check(watched_ms>=0),
      duration_ms integer not null check(duration_ms>0),
      created_at timestamptz not null default now()
    );
    create table public.recommendation_events(
      id bigserial primary key,event_type text not null,dwell_ms integer not null
    );
    create function public.record_recommendation_event(
      p_surface text,p_target_type text,p_target_key text,p_event_type text,
      p_dwell_ms integer default 0,p_session_id uuid default null,
      p_metadata jsonb default '{}'::jsonb
    ) returns bigint language plpgsql as $$
    declare v_id bigint;
    begin
      if p_event_type not in ('impression','open','click','dwell','skip','hide','report',
         'purchase','apply','connect','challenge','complete') then
         raise exception 'INVALID_RECOMMENDATION_EVENT';
      end if;
      insert into public.recommendation_events(event_type,dwell_ms)
        values(p_event_type,p_dwell_ms) returning id into v_id;
      return v_id;
    end$$;
  `);
  await db.query('insert into auth.users(id) values($1),($2)',[author,viewer]);
  await db.query('insert into public.feed_posts(id,user_id,is_reel) values($1,$2,true)',[reel,author]);
  await db.exec(reelSql);
  await db.query("select set_config('request.jwt.claim.sub',$1,false)",[viewer]);

  await record(sessions[0],9500);
  let m=await stats();
  assert.equal(Number(m.watch_samples),1);
  assert.equal(Number(m.completed_count),1);
  assert.equal(Number(m.skip_count),0);
  assert.equal((await db.query('select event_type from public.recommendation_events order by id desc limit 1')).rows[0].event_type,'dwell');
  await record(sessions[0],9500);
  assert.equal(Number((await stats()).watch_samples),1,'same idempotency key never increments twice');
  await record(sessions[1],300);
  assert.equal(Number((await stats()).watch_samples),1,'twenty-second duplicate does not increment');

  await db.query("update private_ranking.reel_engagement_sessions set created_at=now()-interval '25 seconds'");
  await record(sessions[2],300);
  m=await stats();
  assert.equal(Number(m.watch_samples),2);
  assert.equal(Number(m.skip_count),1);
  assert.equal((await db.query('select event_type from public.recommendation_events order by id desc limit 1')).rows[0].event_type,'skip');

  await db.query("update private_ranking.reel_engagement_sessions set created_at=now()-interval '25 seconds'");
  await db.query('insert into public.blocks(blocker_id,blocked_id) values($1,$2)',[author,viewer]);
  await assert.rejects(record(sessions[3],9500),/INVALID_REEL/,'blocked viewer cannot boost Reel');
  await db.query('delete from public.blocks');
  await db.query('update public.feed_posts set audience=$1 where id=$2',['Followers',reel]);
  await db.query('insert into public.follows(follower_id,following_id) values($1,$2)',[viewer,author]);
  await record(sessions[3],9500);
  assert.equal(Number((await stats()).watch_samples),3,'approved followers can record follower-only Reel');
  console.log('PASS Reel RPC runtime: completion, skip, idempotency, cooldown, blocks, followers');
} finally {
  await db.close();
}

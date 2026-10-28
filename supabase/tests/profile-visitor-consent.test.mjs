import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';

const alice = '00000000-0000-4000-8000-000000000001';
const bob = '00000000-0000-4000-8000-000000000002';
const db = new PGlite();
const sql = fs.readFileSync(new URL('../migrations/20261018025000_opt_in_profile_visitor_history.sql', import.meta.url), 'utf8');

async function asUser(id) {
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [id]);
}
async function queryOne(text, params = []) {
  const response = await db.query(text, params);
  return response.rows[0];
}

try {
  // Fully isolated, synthetic schema and accounts. Never connects to hosted BLINK.
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    create table public.profiles(
      id uuid primary key references auth.users(id),
      username text not null unique,
      full_name text not null,
      avatar_url text
    );
    create table public.blocks(blocker_id uuid not null, blocked_id uuid not null);
    create table public.profile_views(
      id uuid primary key default gen_random_uuid(),
      profile_id uuid not null references public.profiles(id),
      viewer_id uuid not null references public.profiles(id),
      created_at timestamptz default now()
    );
    create table public.activities(
      id uuid primary key default gen_random_uuid(),
      recipient_id uuid references public.profiles(id),
      actor_id uuid references public.profiles(id),
      activity_type text, entity_type text, entity_id uuid,
      message text, created_at timestamptz default now()
    );
  `);
  await db.query('insert into auth.users(id) values ($1), ($2)', [alice,bob]);
  await db.query(`insert into public.profiles(id,username,full_name)
      values ($1,'alice','Alice'),($2,'bob','Bob')`, [alice,bob]);
  await db.exec(sql);

  // All three tables must stay unreachable through direct client PostgREST operations.
  const permissions = await queryOne(`
    select bool_and(c.relrowsecurity
      and not has_table_privilege('authenticated',c.oid,'SELECT')
      and not has_table_privilege('authenticated',c.oid,'INSERT')
      and not has_table_privilege('anon',c.oid,'SELECT')) as ok
    from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public'
      and c.relname in ('profile_visit_events','profile_visit_settings','profile_visit_receipts')
  `);
  assert.equal(permissions.ok, true, 'Visitor tables must have RLS and no direct client access');
  const anon = await queryOne(`
    select bool_or(has_function_privilege('anon',p.oid,'EXECUTE')) as can_execute
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname in (
      'get_my_profile_visit_settings','set_my_profile_visit_settings',
      'list_my_profile_visitors','record_opt_in_profile_visit')
  `);
  assert.equal(anon.can_execute, false, 'Anonymous accounts must not execute visit RPCs');

  await asUser(bob);
  assert.equal((await queryOne('select public.get_my_profile_visit_settings() as settings')).settings.participate, false);
  assert.equal((await queryOne('select public.record_opt_in_profile_visit($1) as recorded',[alice])).recorded, false);
  await queryOne('select public.set_my_profile_visit_settings(true,false)');
  assert.equal((await queryOne('select public.record_opt_in_profile_visit($1) as recorded',[alice])).recorded,false);

  await asUser(alice);
  await queryOne('select public.set_my_profile_visit_settings(true,true)');
  await asUser(bob);
  assert.equal((await queryOne('select public.record_opt_in_profile_visit($1) as recorded',[alice])).recorded,true);
  assert.equal((await queryOne('select public.record_opt_in_profile_visit($1) as recorded',[alice])).recorded,false);
  assert.equal((await queryOne('select count(*)::int as n from public.profile_visit_events')).n,1);

  await asUser(alice);
  const snapshot=(await queryOne('select public.list_my_profile_visitors() as data')).data;
  assert.equal(snapshot.enabled,true);
  assert.equal(snapshot.visitors.length,1);
  assert.equal(snapshot.visitors[0].username,'bob');
  assert.equal((await queryOne('select public.count_my_new_profile_visitors() as count')).count,1);
  await queryOne('select public.ack_my_profile_visitors_seen($1::timestamptz)',[snapshot.seen_through]);
  assert.equal((await queryOne('select public.count_my_new_profile_visitors() as count')).count,0);

  // A block hides the visitor and rejects further recording.
  await db.query('insert into public.blocks(blocker_id,blocked_id) values ($1,$2)',[alice,bob]);
  assert.equal((await queryOne('select public.list_my_profile_visitors() as data')).data.visitors.length,0);
  await asUser(bob);
  assert.equal((await queryOne('select public.record_opt_in_profile_visit($1) as recorded',[alice])).recorded,false);
  await db.query('delete from public.blocks where blocker_id=$1 and blocked_id=$2',[alice,bob]);

  // Withdraw consent and verify irrecoverable deletion and legacy-insert guard.
  await queryOne('select public.set_my_profile_visit_settings(false,false)');
  assert.equal((await queryOne('select count(*)::int as n from public.profile_visit_events')).n,0);
  assert.equal((await queryOne('select count(*)::int as n from public.profile_views')).n,0);
  await db.query('insert into public.profile_views(profile_id,viewer_id) values ($1,$2)',[alice,bob]);
  assert.equal((await queryOne('select count(*)::int as n from public.profile_views')).n,0);
  await asUser(alice);
  assert.equal((await queryOne('select public.list_my_profile_visitors() as data')).data.visitors.length,0);
  console.log('PASS isolated visitor privacy: default opt-out, bilateral consent, one visit, owner snapshot, unread receipt, blocked viewer, deletion, RLS, grants, legacy guard');
} finally {
  await db.close();
}

// Disposable PostgreSQL validation for BLINK Contest; never connects to live Supabase.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
const migration = readFileSync(
  new URL('../migrations/20261028185000_blink_contests_free_entry.sql', import.meta.url), 'utf8'
);
const admin = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const creator1 = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const creator2 = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const voter = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const later = (days) => new Date(Date.now() + days * 86400000).toISOString();
const payload = {
  title: 'BLINK Creator Challenge',
  description: 'Show your campus creativity',
  rules: 'One entry per person. Winners are chosen by valid votes.',
  category: 'Creators',
  prize_first: 30000,
  prize_second: 15000,
  prize_third: 5000,
  registration_opens_at: later(-1),
  registration_closes_at: later(1),
  voting_opens_at: later(2),
  voting_closes_at: later(3),
  results_at: later(4),
  auto_approve: false,
};
const q = async (sql, params=[]) => db.query(sql, params);
const setUser = async (user) => db.exec("select set_config('request.jwt.claim.sub','" + user + "',false);");
const rpc = async (name, params, args) => q('select public.' + name + '(' + params + ') as result', args);
const json = (v) => typeof v === 'string' ? JSON.parse(v) : v;

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create schema private;
    create table auth.users(id uuid primary key);
    create table public.profiles(
      id uuid primary key references auth.users(id),
      username text,full_name text,avatar_url text,university text
    );
    create function auth.uid() returns uuid language sql stable
      as 'select nullif(current_setting(''request.jwt.claim.sub'',true),'''')::uuid';
    create function auth.jwt() returns jsonb language sql stable
      as 'select jsonb_build_object(''is_anonymous'',false)';
    create function private.require_blink_admin() returns uuid
      language plpgsql security definer set search_path=''
      as $func$
      begin
        if auth.uid() <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid
           or auth.uid() is null then
          raise exception 'ADMIN_REQUIRED' using errcode='42501';
        end if;
        return auth.uid();
      end;
      $func$;
  `);
  for (const [id,username] of [
    [admin,'contestadmin'],[creator1,'creatorone'],[creator2,'creatortwo'],[voter,'voterthree']
  ]) {
    await q('insert into auth.users(id) values ($1)', [id]);
    await q('insert into public.profiles(id,username,full_name,university) values($1,$2,$3,$4)',
      [id,username,username.toUpperCase(),'FUTA']);
  }
  await db.exec(migration);
  await setUser(creator1);
  await assert.rejects(() => rpc('admin_save_blink_contest', '$1::uuid,$2::jsonb',
    [null,JSON.stringify(payload)]), /ADMIN_REQUIRED/);

  await setUser(admin);
  const created = json((await rpc('admin_save_blink_contest', '$1::uuid,$2::jsonb',
    [null,JSON.stringify(payload)])).rows[0].result);
  assert.equal(created.status,'draft');
  const id = created.id;
  assert.equal(created.prize_first, 30000);
  await rpc('admin_transition_blink_contest', '$1::uuid,$2::text',[id,'publish']);

  await setUser(creator1);
  const first = json((await rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I make creative campus videos',null])).rows[0].result);
  assert.equal(first.status,'pending');
  await assert.rejects(() => rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'Another duplicated application',null]), /duplicate|unique|CONTEST_FULL/i);

  await setUser(creator2);
  const second = json((await rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I create photography stories',null])).rows[0].result);
  assert.notEqual(first.entry_id, second.entry_id);

  await setUser(admin);
  const entries = json((await rpc('admin_list_blink_contest_entries', '$1::uuid', [id])).rows[0].result);
  assert.equal(entries.length, 2);
  await rpc('admin_review_blink_contest_entry', '$1::uuid,$2::text', [first.entry_id,'approve']);
  await rpc('admin_review_blink_contest_entry', '$1::uuid,$2::text', [second.entry_id,'approve']);

  // Every approved contestant has a validated public share destination.
  await setUser(voter);
  const approved = json((await rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(approved.id, first.entry_id);
  assert.equal(approved.username, 'creatorone');
  assert.equal(approved.name, 'CREATORONE');
  assert.equal(approved.rank, 1);
  assert.equal(approved.votes, 0);
  await assert.rejects(() => rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,'99999999-9999-4999-8999-999999999999']), /CANDIDATE_NOT_AVAILABLE/);

  await assert.rejects(() => rpc('vote_blink_contest','$1::uuid,$2::uuid',
    [id, first.entry_id]), /VOTING_CLOSED/);

  // Move the disposable fixture clock-window forward without waiting days.
  await q(`update public.blink_contests
    set registration_opens_at=now()-interval '3 days',
        registration_closes_at=now()-interval '1 day',
        voting_opens_at=now()-interval '12 hours',
        voting_closes_at=now()+interval '1 day',
        results_at=now()+interval '2 days' where id=$1`,[id]);
  const voted = json((await rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(voted.success,true);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,second.entry_id]), /duplicate|unique/i);

  await setUser(creator1);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id]), /SELF_VOTE_NOT_ALLOWED/);
  const details = json((await rpc('get_blink_contest', '$1::uuid',[id])).rows[0].result);
  assert.equal(details.leaderboard[0].id, first.entry_id);
  assert.equal(details.leaderboard[0].votes, 1);
  assert.equal(details.my_rank, 1);
  assert.equal(details.total_ranked, 2);

  const updated = json((await rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(updated.rank, 1);
  assert.equal(updated.votes, 1);
  assert.equal(updated.contest_title, 'BLINK Creator Challenge');

  await setUser(admin);
  await rpc('admin_transition_blink_contest', '$1::uuid,$2::text',[id,'pause']);
  await setUser(creator2);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id]), /VOTING_CLOSED/);
  await setUser(admin);
  await assert.rejects(() => rpc('admin_transition_blink_contest', '$1::uuid,$2::text',
    [id,'complete']), /INVALID_CONTEST_TRANSITION/);
  console.log('PASS BLINK Contest: SQL migration, admin role, entry approval, one free vote, ranking and timing');
} finally {
  await db.close();
}

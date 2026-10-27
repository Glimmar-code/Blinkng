// Disposable PGlite database: no live BLINK users or production writes.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const accountA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const accountB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const db = new PGlite();
try {
  await db.exec(`
    create role anon nologin;
    create role authenticated nologin;
    create schema auth;
    create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid $$;
    grant usage on schema auth, public to authenticated;
    grant usage on schema auth, public to anon;
    grant execute on function auth.uid() to authenticated;
    create table public.notifications (
      id uuid primary key, user_id uuid not null, is_read boolean not null default false,
      text text not null default ''
    );
    create table public.activities (
      id uuid primary key, recipient_id uuid not null, is_read boolean not null default false
    );
    grant select on public.notifications to authenticated;
    grant select, update on public.activities to authenticated;
    alter table public.notifications enable row level security;
    alter table public.activities enable row level security;
    create policy notifications_select_own on public.notifications
      for select to authenticated using (user_id = (select auth.uid()));
    create policy activities_select_own on public.activities
      for select to authenticated using (recipient_id = (select auth.uid()));
    create policy activities_update_own on public.activities
      for update to authenticated using (recipient_id = (select auth.uid()))
      with check (recipient_id = (select auth.uid()));
  `);
  const sql = fs.readFileSync(new URL('../migrations/20261027195000_notification_read_state_atomic.sql', import.meta.url), 'utf8');
  await db.exec(sql);
  const grant = (await db.query(`
    select has_column_privilege('authenticated','public.notifications','is_read','UPDATE') as read_column,
      has_column_privilege('authenticated','public.notifications','text','UPDATE') as text_column,
      has_function_privilege('anon','public.mark_all_my_notifications_read_atomic()','EXECUTE') as anon_rpc,
      has_function_privilege('authenticated','public.mark_all_my_notifications_read_atomic()','EXECUTE') as auth_rpc
  `)).rows[0];
  assert.equal(grant.read_column, true);
  assert.equal(grant.text_column, false);
  assert.equal(grant.anon_rpc, false);
  assert.equal(grant.auth_rpc, true);

  await db.exec(`
    insert into public.notifications(id,user_id,text) values
      ('00000000-0000-4000-8000-000000000001','${accountA}','private-a'),
      ('00000000-0000-4000-8000-000000000002','${accountB}','private-b');
    insert into public.activities(id,recipient_id) values
      ('10000000-0000-4000-8000-000000000001','${accountA}'),
      ('10000000-0000-4000-8000-000000000002','${accountB}');
  `);

  await db.exec("set role authenticated");
  await db.exec(`select set_config('request.jwt.claim.sub','${accountA}',false)`);
  assert.equal((await db.query('select count(*)::integer as count from public.notifications')).rows[0].count, 1);
  await assert.rejects(db.exec("update public.notifications set text='not allowed'"), /permission denied/i);
  await db.exec("update public.notifications set is_read = true where user_id='" + accountB + "'");
  await db.exec("update public.notifications set is_read = false where user_id='" + accountA + "'");
  const marked = await db.query("select public.mark_all_my_notifications_read_atomic() as ok");
  assert.equal(marked.rows[0].ok, true);
  await db.exec("reset role");
  const all = (await db.query("select user_id::text as uid, is_read from public.notifications order by user_id")).rows;
  const activities = (await db.query("select recipient_id::text as uid, is_read from public.activities order by recipient_id")).rows;
  assert.deepEqual(all.map(row => row.is_read), [true, false]);
  assert.deepEqual(activities.map(row => row.is_read), [true, false]);

  await db.exec("set role anon");
  await assert.rejects(db.exec("select public.mark_all_my_notifications_read_atomic()"), /permission denied/i);
  await db.exec("reset role");
  console.log('PASS notification read permissions: own only, non-read fields protected, atomic RPC, anon denied');
} finally {
  await db.close();
}

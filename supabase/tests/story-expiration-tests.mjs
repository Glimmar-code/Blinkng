import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

/**
 * Run the tracked Story migration on an isolated Postgres runtime.
 * No production project, credentials, storage or account data are used.
 */
export async function storyExpirationTests() {
  const db = new PGlite();
  const author = '00000000-0000-4000-8000-000000000101';
  const viewer = '00000000-0000-4000-8000-000000000102';
  const recent = '00000000-0000-4000-8000-000000000111';
  const expired = '00000000-0000-4000-8000-000000000112';
  const future = '00000000-0000-4000-8000-000000000113';
  const inactive = '00000000-0000-4000-8000-000000000114';
  const sql = fs.readFileSync(
    new URL('../migrations/20261022043000_blink_story_24hour_expiry.sql', import.meta.url), 'utf8');
  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create role service_role bypassrls;
      create schema auth;
      create schema cron;
      create table cron.job(jobid bigint generated always as identity primary key,
        jobname text unique, schedule text, command text);
      create function auth.uid() returns uuid language sql stable
        as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
      grant usage on schema auth to authenticated;
      grant execute on function auth.uid() to authenticated;
      create table public.stories(
        id uuid primary key,
        user_id uuid not null,
        active boolean not null default true,
        created_at timestamptz not null default now()
      );
      grant select,insert,update,delete on public.stories to authenticated;
      alter table public.stories enable row level security;
      create policy select_stories on public.stories
        for select to authenticated using (true);
      create policy update_stories on public.stories for update
        to authenticated using (auth.uid() = user_id)
        with check (auth.uid() = user_id);
      insert into public.stories (id,user_id,active,created_at) values
        ('${recent}','${author}',true,now() - interval '1 hour'),
        ('${expired}','${author}',true,now() - interval '25 hours'),
        ('${future}','${author}',true,now() + interval '1 hour'),
        ('${inactive}','${author}',false,now() - interval '1 hour');
    `);
    await db.exec(sql);
    await db.exec(sql);
    console.log('PASS Story migration installs and reapplies cleanly');

    await db.exec(`select set_config('request.jwt.claim.sub','${viewer}',false);
      set role authenticated;`);
    const visible = (await db.query('select id::text from public.stories order by id')).rows.map(x => x.id);
    assert.deepEqual(visible, [recent],
      'An authenticated viewer must not read expired, future or inactive stories');
    await assert.rejects(db.query('select private.expire_blink_stories()'),
      /permission denied/,
      'Client must not execute privileged expiration function');

    await db.exec(`reset role;
      select set_config('request.jwt.claim.sub','${author}',false);
      set role authenticated;`);
    const ownVisible = (await db.query('select id::text from public.stories order by id')).rows.map(x => x.id);
    assert.deepEqual(ownVisible, [recent],
      'A story owner also must not resurrect expired stories via SELECT');

    await db.exec('reset role;');
    const expire = (await db.query('select private.expire_blink_stories() as count')).rows[0].count;
    assert.equal(Number(expire), 1);
    const expiredState = (await db.query(`select active from public.stories where id = '${expired}'`)).rows[0];
    assert.equal(expiredState.active, false);
    assert.equal(Number((await db.query('select private.expire_blink_stories() as count')).rows[0].count), 0,
      'Replaying expiration cannot mutate any additional rows');
    console.log('PASS Story RLS cutoff, owner/viewer denial, maintenance and idempotency');
  } finally {
    await db.close();
  }
}

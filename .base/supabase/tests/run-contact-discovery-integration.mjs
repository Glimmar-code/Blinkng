import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

// No user contact numbers, hosted credentials, external networks, or production writes.
const db = new PGlite();
const owner = '11111111-1111-4111-8111-111111111111';
const joiner = '22222222-2222-4222-8222-222222222222';
const digest = 'a'.repeat(64);
const ownerDigest = 'b'.repeat(64);
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role bypassrls;
    create schema auth;
    create schema private;
    create function auth.uid() returns uuid language sql stable as $$select null::uuid$$;
    create table public.profiles(
      id uuid primary key, username text not null unique,
      full_name text, avatar_url text, verification_badge text default 'NONE',
      onboarding_completed boolean not null default false,
      created_at timestamptz not null default now()
    );
    create table public.blocks(blocker_id uuid not null, blocked_id uuid not null);
    create type public.notification_type_enum as enum ('system','social');
    create table public.notifications(
      id uuid primary key default gen_random_uuid(),
      user_id uuid not null, actor_id uuid,
      type public.notification_type_enum not null,
      target_type text, target_id uuid,
      text text, sub_text text,
      metadata jsonb not null default '{}'::jsonb,
      is_read boolean not null default false
    );
  `);
  const sql = fs.readFileSync(new URL('../migrations/20261027002000_private_contact_discovery.sql', import.meta.url), 'utf8');
  await db.exec(sql);
  const rls = await db.query(`
    select relname, relrowsecurity from pg_class where relname in
      ('blink_contact_settings','blink_contact_directory','blink_contact_imports','blink_contact_join_alerts')
    order by relname
  `);
  assert.equal(rls.rows.length, 4, 'all contact tables installed');
  assert.ok(rls.rows.every(row => row.relrowsecurity), 'all contact tables use RLS');
  const grants = await db.query(`
    select has_function_privilege('anon','public.blink_contact_matches_for(uuid)','EXECUTE') as anon_read,
      has_function_privilege('authenticated','public.blink_contact_replace_imports(uuid,text[])','EXECUTE') as user_write,
      has_function_privilege('service_role','public.blink_contact_matches_for(uuid)','EXECUTE') as trusted_read
  `);
  assert.deepEqual(Object.values(grants.rows[0]), [false, false, true],
    'client callers cannot invoke service-only contact functions');

  await db.exec(`
    insert into public.profiles(id,username,onboarding_completed) values
       ('${owner}','old_user',true),
       ('${joiner}','new_user',true);
    insert into public.blink_contact_settings(user_id,sync_enabled,discoverable,join_alerts)
    values ('${owner}',true,true,true),('${joiner}',true,true,false);
    select public.blink_contact_replace_imports('${owner}',array['${digest}']::text[]);
    select public.blink_contact_upsert_opted_in('${owner}','${ownerDigest}');
    select public.blink_contact_upsert_opted_in('${joiner}','${digest}');
  `);
  assert.equal((await db.query(`select user_id from public.blink_contact_matches_for('${owner}')`)).rows.length, 0,
    'one-sided imports must never expose a self-declared phone identity');
  assert.equal((await db.query('select * from private.blink_contact_join_alerts')).rows.length, 0,
    'one-sided import must not produce a join notification');
  await db.exec(`select public.blink_contact_replace_imports('${joiner}',array['${ownerDigest}']::text[]);
    select public.blink_contact_notify_my_join('${joiner}');`);
  const matches = await db.query(`select user_id from public.blink_contact_matches_for('${owner}')`);
  assert.equal(matches.rows.length, 1, 'mutual opt-in contacts can be suggested without SMS');
  assert.equal(matches.rows[0].user_id, joiner);
  const alert = await db.query(`select notification_id from private.blink_contact_join_alerts
    where owner_id='${owner}' and joiner_id='${joiner}'`);
  assert.equal(alert.rows.length, 1, 'mutually opted-in new account generates one guarded alert');
  assert.ok(alert.rows[0].notification_id);
  let permission = await db.query(`select public.blink_contact_push_is_authorized('${alert.rows[0].notification_id}') as permitted`);
  assert.equal(permission.rows[0].permitted, true, 'only consented alerts may be pushed');

  await db.exec(`insert into public.blocks values ('${owner}','${joiner}')`);
  assert.equal((await db.query(`select * from public.blink_contact_matches_for('${owner}')`)).rows.length, 0,
    'blocking immediately suppresses suggestions');
  permission = await db.query(`select public.blink_contact_push_is_authorized('${alert.rows[0].notification_id}') as permitted`);
  assert.equal(permission.rows[0].permitted, false, 'blocking revokes pending push authorization');
  await db.exec(`delete from public.blocks;
    update public.blink_contact_settings set join_alerts=false where user_id='${owner}'`);
  permission = await db.query(`select public.blink_contact_push_is_authorized('${alert.rows[0].notification_id}') as permitted`);
  assert.equal(permission.rows[0].permitted, false, 'revoking join alerts suppresses push');
  await db.exec(`update public.blink_contact_settings set discoverable=false where user_id='${joiner}';
    select public.blink_contact_upsert_opted_in('${joiner}','${digest}');`);
  assert.equal((await db.query(`select * from public.blink_contact_matches_for('${owner}')`)).rows.length, 0,
    'disabling discoverability removes directory matches');

  // The established member can sync AFTER the new member and must still get
  // a privacy-authorized notification without requiring the joiner to reopen BLINK.
  await db.exec(`update public.blink_contact_settings set join_alerts=true where user_id='${owner}';
    update public.blink_contact_settings set discoverable=true where user_id='${joiner}';
    select public.blink_contact_upsert_opted_in('${joiner}','${digest}');
    select public.blink_contact_replace_imports('${owner}',array['${digest}']::text[]);`);
  const lateAlerts = await db.query(`select notification_id
    from public.blink_contact_notify_recent_matches('${owner}')`);
  assert.equal(lateAlerts.rows.length, 1, 'late importing owner receives one possible-contact alert');
  assert.ok(lateAlerts.rows[0].notification_id);
  const repeated = await db.query(`select count(*)::integer as total
      from public.notifications
      where user_id='${owner}' and metadata->>'kind'='blink_contact_joined'`);
  assert.equal(repeated.rows[0].total, 1,
    'resyncing the same contacts must not create duplicate joined notifications');
  await db.exec(`select public.blink_contact_clear_imports('${owner}');`);
  const cleared = await db.query(`select * from private.blink_contact_join_alerts where owner_id='${owner}'`);
  assert.equal(cleared.rows.length, 0, 'revoking sync deletes derived contact alert records');
  const afterDelete = await db.query(`select count(*)::integer as total
      from public.notifications
      where user_id='${owner}' and metadata->>'kind'='blink_contact_joined'`);
  assert.equal(afterDelete.rows[0].total, 0,
    'deleting contact data removes only previously generated contact alerts');
  console.log('PASS mutual no-SMS contacts: RLS, reciprocity, dedup, blocks, revocation');
} finally {
  await db.close();
}

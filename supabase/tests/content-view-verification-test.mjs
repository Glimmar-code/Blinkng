import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

// This is an isolated PostgreSQL database; never link to production Supabase.
const db = new PGlite();
const viewer = '00000000-0000-4000-8000-000000000001';
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema auth;
    create function auth.uid() returns uuid language sql stable
      as $$ select '${viewer}'::uuid $$;
    create table public.profiles (
      id uuid primary key,
      verification_badge text,
      verification_tier text,
      is_verified boolean default false,
      verification_expires_at timestamptz
    );
    insert into public.profiles(id,verification_badge,verification_tier,is_verified)
      values ('${viewer}','NONE','None',false);
  `);

  // Minimal faithful copy of the installed weight selector. The schema test
  // exercises the actual migration text, not a reimplementation of its CASE.
  await db.exec(`
    create or replace function public.record_content_view(p_post_id uuid,p_event_id uuid)
    returns jsonb
    language plpgsql
    security definer
    set search_path to ''
    as $function$
    declare
      v_viewer_id uuid := auth.uid();
      v_view_weight integer := 1;
    begin
    select case
        when upper(coalesce(p.verification_badge::text, '')) = 'GOLD'
          or upper(coalesce(p.verification_tier::text, '')) = 'GOLD' then 6
        when upper(coalesce(p.verification_badge::text, '')) = 'BLUE'
          or upper(coalesce(p.verification_tier::text, '')) = 'STANDARD'
          or coalesce(p.is_verified, false) then 3
        else 1
    end
    into v_view_weight
    from public.profiles p
    where p.id = v_viewer_id;
    return jsonb_build_object('view_weight',v_view_weight);
    end;
    $function$;
    revoke all on function public.record_content_view(uuid,uuid) from public,anon;
    grant execute on function public.record_content_view(uuid,uuid) to authenticated;
  `);

  const migration = fs.readFileSync(
    new URL('../migrations/20261028083500_restrict_expired_verified_weighted_views.sql',import.meta.url),'utf8');
  await db.exec(migration);
  const weight = async () => {
    const result = await db.query(`select public.record_content_view(null::uuid,null::uuid) as result`);
    return result.rows[0].result.view_weight;
  };
  const set = async (badge,tier,verified,expiration) => {
    await db.query(`update public.profiles
      set verification_badge=$1, verification_tier=$2,
          is_verified=$3, verification_expires_at=$4
      where id=$5`,[badge,tier,verified,expiration,viewer]);
  };
  const future = '2099-01-01T00:00:00Z';
  const past = '2020-01-01T00:00:00Z';
  assert.equal(await weight(),1,'normal users count 1x');
  await set('BLUE','Standard',true,future);
  assert.equal(await weight(),3,'active Blue counts 3x');
  await set('GOLD','Gold',true,future);
  assert.equal(await weight(),6,'active Gold counts 6x');
  await set('GOLD','Gold',true,past);
  assert.equal(await weight(),1,'expired Gold counts 1x');
  await set('BLUE','Standard',false,future);
  assert.equal(await weight(),1,'revoked Blue counts 1x');
  await set('GOLD','Gold',false,null);
  assert.equal(await weight(),1,'revoked Gold counts 1x');
  await set('NONE','None',true,null);
  assert.equal(await weight(),3,'legacy active verified account counts 3x');
  await set('GOLD','Gold',true,null);
  assert.equal(await weight(),6,'valid nonexpiring Gold remains 6x');

  const attrs = (await db.query(`select p.prosecdef as security_definer,
      has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_can_execute,
      has_function_privilege('anon',p.oid,'EXECUTE') as anon_can_execute
      from pg_proc p where p.oid='public.record_content_view(uuid,uuid)'::regprocedure`)).rows[0];
  assert.equal(attrs.security_definer,true);
  assert.equal(attrs.authenticated_can_execute,true);
  assert.equal(attrs.anon_can_execute,false);

  await assert.rejects(db.exec(migration),/RECORD_CONTENT_VIEW_WEIGHT_SOURCE_CHANGED/,
    'unexpected migration reapply fails closed');
  console.log('PASS content views: normal 1x, Blue 3x, Gold 6x, expiry/revocation 1x, grants preserved');
} finally {
  await db.close();
}

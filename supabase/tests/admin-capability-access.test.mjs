import { PGlite } from '@electric-sql/pglite';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const migration = fs.readFileSync(
  new URL('../migrations/20261107100000_fix_admin_capability_private_schema_access.sql', import.meta.url),
  'utf8'
);
const ownerId = '00000000-0000-4000-8000-000000000001';

const db = new PGlite();
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create schema private;

    create function auth.uid() returns uuid
    language sql stable
    as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to authenticated;
    grant execute on function auth.uid() to authenticated;

    create table private.admin_roles (
      user_id uuid not null,
      role text not null,
      expires_at timestamptz
    );
    insert into private.admin_roles(user_id, role) values ('${ownerId}', 'owner');

    create function private.admin_get_capability_impl()
    returns jsonb language plpgsql stable security definer set search_path = ''
    as $function$
    declare
      v_uid uuid := auth.uid();
      v_role text;
      v_expires timestamptz;
    begin
      if v_uid is null then
        return jsonb_build_object('is_admin',false,'is_owner',false,'role','none','expires_at',null);
      end if;
      select r.role, r.expires_at into v_role, v_expires
      from private.admin_roles r
      where r.user_id=v_uid and (r.role='owner' or r.expires_at is null or r.expires_at > now())
      limit 1;
      return jsonb_build_object(
        'is_admin', v_role is not null,
        'is_owner', coalesce(v_role='owner',false),
        'role', coalesce(v_role,'none'),
        'expires_at', v_expires
      );
    end
    $function$;

    create function public.admin_get_capability()
    returns jsonb language sql stable set search_path = ''
    as $function$ select private.admin_get_capability_impl(); $function$;
    revoke all on function public.admin_get_capability() from public, anon;
    grant execute on function public.admin_get_capability() to authenticated, service_role;
  `);

  await db.exec(migration);

  const grants = (await db.query(`
    select
      has_schema_privilege('authenticated','private','USAGE') as private_schema_open,
      has_function_privilege('authenticated','public.admin_get_capability()','EXECUTE') as auth_can_call,
      has_function_privilege('anon','public.admin_get_capability()','EXECUTE') as anon_can_call
  `)).rows[0];
  assert.equal(grants.private_schema_open, false, 'private schema must remain closed');
  assert.equal(grants.auth_can_call, true, 'authenticated users must call the narrow public wrapper');
  assert.equal(grants.anon_can_call, false, 'anonymous users must not call the admin RPC');

  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [ownerId]);
  await db.exec('set role authenticated');
  const capability = (await db.query('select public.admin_get_capability() as capability')).rows[0].capability;
  await db.exec('reset role');

  assert.equal(capability.is_admin, true);
  assert.equal(capability.is_owner, true);
  assert.equal(capability.role, 'owner');
  console.log('PASS authenticated owner capability lookup without private-schema USAGE');
} finally {
  await db.close();
}

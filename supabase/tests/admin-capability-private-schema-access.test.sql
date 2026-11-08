-- Run with a database owner/superuser against a migrated Testlab database.
-- This proves the narrow public capability RPC works even though authenticated
-- clients deliberately have no USAGE on the private schema.

begin;

do $$
declare
  v_owner uuid;
begin
  select user_id into v_owner
  from private.admin_roles
  where role = 'owner'
  order by granted_at
  limit 1;

  if v_owner is null then
    raise exception 'Test fixture requires one owner role in private.admin_roles';
  end if;

  perform set_config('request.jwt.claim.sub', v_owner::text, true);
end
$$;

do $$
begin
  if has_schema_privilege('authenticated', 'private', 'USAGE') then
    raise exception 'Authenticated role unexpectedly has private schema access';
  end if;
  if not has_function_privilege('authenticated', 'public.admin_get_capability()', 'EXECUTE') then
    raise exception 'Authenticated role cannot execute public admin capability RPC';
  end if;
  if has_function_privilege('anon', 'public.admin_get_capability()', 'EXECUTE') then
    raise exception 'Anonymous role must not execute admin capability RPC';
  end if;
end
$$;

set local role authenticated;

do $$
declare
  v_capability jsonb;
begin
  v_capability := public.admin_get_capability();
  if coalesce((v_capability->>'is_admin')::boolean, false) is not true then
    raise exception 'Owner capability lookup did not return is_admin=true: %', v_capability;
  end if;
  if coalesce((v_capability->>'is_owner')::boolean, false) is not true then
    raise exception 'Owner capability lookup did not return is_owner=true: %', v_capability;
  end if;
  if v_capability->>'role' <> 'owner' then
    raise exception 'Expected owner role, received: %', v_capability;
  end if;
end
$$;

rollback;

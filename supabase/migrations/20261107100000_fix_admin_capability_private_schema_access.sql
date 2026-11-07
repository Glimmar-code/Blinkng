-- Fix admin capability resolution for authenticated users.
--
-- The public wrapper previously ran as SECURITY INVOKER and called a function
-- in the private schema. Authenticated users intentionally have no USAGE on
-- that schema, so the capability RPC failed before it could return the
-- existing admin role. Keep the private schema closed and elevate only this
-- narrow, read-only wrapper with a fixed empty search_path.

create or replace function public.admin_get_capability()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $function$
  select private.admin_get_capability_impl();
$function$;

revoke all on function public.admin_get_capability() from public, anon;
grant execute on function public.admin_get_capability() to authenticated, service_role;

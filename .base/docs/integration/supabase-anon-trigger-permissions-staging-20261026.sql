-- BLINK Supabase security advisor: STAGING CANDIDATE ONLY, NOT PRODUCTION.
-- The two named helpers return TRIGGER and must never be callable as public
-- REST RPCs. Preserve all other 5 public/auth endpoints pending per-function
-- contract and abuse review. Run this with isolated fixtures first.
begin;

do $$
declare
    v_name text;
    v_proc oid;
begin
    foreach v_name in array array[
        'market_require_approved_seller',
        'market_require_approved_seller_for_order'
    ] loop
        v_proc := to_regprocedure(format('public.%I()', v_name));
        if v_proc is null then
            raise exception 'BLINK_TRIGGER_FUNCTION_NOT_FOUND: %', v_name;
        end if;
        if not exists (
            select 1 from pg_proc p
            where p.oid=v_proc
              and p.prorettype='pg_catalog.trigger'::regtype
              and p.prosecdef=true
        ) then
            raise exception 'BLINK_UNEXPECTED_TRIGGER_SIGNATURE: %', v_name;
        end if;
    end loop;
end;
$$;

revoke execute on function public.market_require_approved_seller()
  from public, anon, authenticated;
revoke execute on function public.market_require_approved_seller_for_order()
  from public, anon, authenticated;

do $$
begin
    if has_function_privilege(
        'anon', 'public.market_require_approved_seller()', 'EXECUTE'
    ) or has_function_privilege(
        'authenticated','public.market_require_approved_seller()', 'EXECUTE'
    ) or has_function_privilege(
        'anon','public.market_require_approved_seller_for_order()','EXECUTE'
    ) or has_function_privilege(
        'authenticated','public.market_require_approved_seller_for_order()','EXECUTE'
    ) then
        raise exception 'BLINK_TRIGGER_RPC_GRANT_STILL_PUBLIC';
    end if;
end;
$$;
commit;

-- A narrow release candidate: no blanket revocation of SECURITY DEFINER
-- permissions, no RLS weakening, and no index dropping. Both trigger action
-- paths must be regression-tested as owner/seller and unauthorized roles.

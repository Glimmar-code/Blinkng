-- Verify the release removes RPC EXECUTE access without deleting triggers.
do $$
declare f text;
begin
  foreach f in array array[
    'public.market_require_approved_seller()',
    'public.market_require_approved_seller_for_order()'
  ]
  loop
    if has_function_privilege('anon', f, 'EXECUTE')
       or has_function_privilege('authenticated', f, 'EXECUTE')
       or has_function_privilege('public', f, 'EXECUTE')
    then
      raise exception '% must not be callable by API clients', f;
    end if;
    if not has_function_privilege('service_role', f, 'EXECUTE') then
      raise exception '% must remain callable by trusted backend code', f;
    end if;
  end loop;
end;
$$;

do $$
begin
  if (select count(*) from pg_trigger
      where not tgisinternal
        and tgfoid in (
          'public.market_require_approved_seller()'::regprocedure,
          'public.market_require_approved_seller_for_order()'::regprocedure
        )) <> 2
  then
    raise exception 'Existing seller-protection triggers must remain attached';
  end if;
end;
$$;

insert into public.market_items(id) values (1);
insert into public.marketplace_orders(id) values (1);
select 'market seller trigger privileges passed' as result;

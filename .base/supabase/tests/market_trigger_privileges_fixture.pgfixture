-- Disposable PostgreSQL fixture: fake roles and functions only.
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;

create table public.market_items(id integer primary key);
create table public.marketplace_orders(id integer primary key);

create function public.market_require_approved_seller()
returns trigger language plpgsql security definer set search_path = 'public', 'pg_temp'
as $$
begin
  return new;
end;
$$;

create function public.market_require_approved_seller_for_order()
returns trigger language plpgsql security definer set search_path = 'public', 'pg_temp'
as $$
begin
  return new;
end;
$$;

create trigger seller_guard before insert on public.market_items
for each row execute function public.market_require_approved_seller();

create trigger order_seller_guard before insert on public.marketplace_orders
for each row execute function public.market_require_approved_seller_for_order();

-- Simulate the explicit service-side privilege present in BLINK's live DB.
grant execute on function public.market_require_approved_seller() to service_role;
grant execute on function public.market_require_approved_seller_for_order() to service_role;

do $$
begin
  if not has_function_privilege('anon', 'public.market_require_approved_seller()', 'EXECUTE')
     or not has_function_privilege('authenticated', 'public.market_require_approved_seller_for_order()', 'EXECUTE')
  then
    raise exception 'Fixture must begin with the old public EXECUTE exposure';
  end if;
end;
$$;

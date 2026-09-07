create table if not exists public.blink_coin_gifts (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users(id) on delete cascade,
  receiver_id uuid not null references auth.users(id) on delete cascade,
  amount bigint not null check (amount between 1 and 1000),
  created_at timestamptz not null default now(),
  constraint blink_coin_gifts_not_self check (sender_id <> receiver_id)
);

alter table public.blink_coin_gifts enable row level security;

revoke insert, update, delete on public.blink_coin_gifts from anon, authenticated;
grant select on public.blink_coin_gifts to authenticated;

drop policy if exists blink_coin_gifts_select_involved on public.blink_coin_gifts;
create policy blink_coin_gifts_select_involved
on public.blink_coin_gifts
for select
to authenticated
using ((select auth.uid()) = sender_id or (select auth.uid()) = receiver_id);

create index if not exists blink_coin_gifts_sender_created_idx
  on public.blink_coin_gifts(sender_id, created_at desc);
create index if not exists blink_coin_gifts_receiver_created_idx
  on public.blink_coin_gifts(receiver_id, created_at desc);

create or replace function public.send_friend_request(p_receiver_id uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_request_id uuid;
begin
  if v_sender is null then
    raise exception 'AUTH_REQUIRED' using errcode = '42501';
  end if;
  if p_receiver_id is null or p_receiver_id = v_sender then
    raise exception 'INVALID_RECEIVER' using errcode = '22023';
  end if;
  if not exists (select 1 from public.profiles p where p.id = p_receiver_id) then
    raise exception 'PROFILE_NOT_FOUND' using errcode = 'P0002';
  end if;

  select cr.id into v_request_id
  from public.connection_requests cr
  where ((cr.sender_id = v_sender and cr.receiver_id = p_receiver_id)
      or (cr.sender_id = p_receiver_id and cr.receiver_id = v_sender))
    and cr.status in ('pending', 'accepted')
  order by cr.created_at desc
  limit 1;

  if v_request_id is not null then
    return v_request_id;
  end if;

  insert into public.connection_requests(sender_id, receiver_id, status)
  values (v_sender, p_receiver_id, 'pending')
  returning id into v_request_id;

  return v_request_id;
end;
$$;

revoke execute on function public.send_friend_request(uuid) from public, anon;
grant execute on function public.send_friend_request(uuid) to authenticated;

create or replace function public.gift_blink_coins(p_receiver_id uuid, p_amount bigint default 10)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_balance bigint;
begin
  if v_sender is null then
    raise exception 'AUTH_REQUIRED' using errcode = '42501';
  end if;
  if p_receiver_id is null or p_receiver_id = v_sender then
    raise exception 'INVALID_RECEIVER' using errcode = '22023';
  end if;
  if p_amount is null or p_amount < 1 or p_amount > 1000 then
    raise exception 'INVALID_AMOUNT' using errcode = '22023';
  end if;
  if not exists (select 1 from public.profiles p where p.id = p_receiver_id) then
    raise exception 'PROFILE_NOT_FOUND' using errcode = 'P0002';
  end if;

  insert into public.game_profiles(user_id) values (v_sender)
  on conflict (user_id) do nothing;
  insert into public.game_profiles(user_id) values (p_receiver_id)
  on conflict (user_id) do nothing;

  select gp.coins into v_balance
  from public.game_profiles gp
  where gp.user_id = v_sender
  for update;

  if v_balance < p_amount then
    raise exception 'INSUFFICIENT_COINS' using errcode = '22003';
  end if;

  update public.game_profiles
  set coins = coins - p_amount, updated_at = now()
  where user_id = v_sender;
  update public.game_profiles
  set coins = coins + p_amount, updated_at = now()
  where user_id = p_receiver_id;

  insert into public.blink_coin_gifts(sender_id, receiver_id, amount)
  values (v_sender, p_receiver_id, p_amount);

  return v_balance - p_amount;
end;
$$;

revoke execute on function public.gift_blink_coins(uuid, bigint) from public, anon;
grant execute on function public.gift_blink_coins(uuid, bigint) to authenticated;

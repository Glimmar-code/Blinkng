-- Testlab-only controls for synthetic campus bot fixtures.
-- These rows are NOT auth.users or public.profiles and cannot affect production engagement.
begin;

create table if not exists private.blink_test_bot_admin_audit (
  id bigint generated always as identity primary key,
  account_id bigint not null references private.blink_test_synthetic_accounts(id) on delete cascade,
  actor_id uuid not null,
  previous_active boolean not null,
  new_active boolean not null,
  reason text not null check (length(btrim(reason)) between 5 and 500),
  created_at timestamptz not null default now()
);

alter table private.blink_test_bot_admin_audit enable row level security;
revoke all on private.blink_test_bot_admin_audit from public, anon, authenticated, service_role;
revoke all on private.blink_test_synthetic_accounts from public, anon, authenticated, service_role;

create or replace function public.blink_test_bot_list()
returns table (
  id bigint, username text, full_name text, university text, faculty text,
  department text, is_active boolean, is_test_only boolean
)
language sql
stable
security definer
set search_path = ''
as $function$
  select a.id, a.username, a.full_name, a.university, a.faculty,
         a.department, a.is_active, a.is_test_only
  from private.blink_test_synthetic_accounts a
  where a.is_test_only = true
  order by a.university, a.username;
$function$;

create or replace function public.blink_test_bot_set_status(
  p_account_id bigint,
  p_is_active boolean,
  p_actor_id uuid,
  p_reason text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_previous boolean;
begin
  if p_actor_id is null then
    raise exception 'ACTOR_REQUIRED' using errcode = '22023';
  end if;
  if p_reason is null or length(btrim(p_reason)) not between 5 and 500 then
    raise exception 'REASON_REQUIRED' using errcode = '22023';
  end if;

  select a.is_active into v_previous
  from private.blink_test_synthetic_accounts a
  where a.id = p_account_id and a.is_test_only = true
  for update;

  if not found then
    raise exception 'TEST_BOT_NOT_FOUND' using errcode = 'P0002';
  end if;

  if v_previous is distinct from p_is_active then
    update private.blink_test_synthetic_accounts
    set is_active = p_is_active
    where id = p_account_id and is_test_only = true;

    insert into private.blink_test_bot_admin_audit
      (account_id, actor_id, previous_active, new_active, reason)
    values (p_account_id, p_actor_id, v_previous, p_is_active, btrim(p_reason));
  end if;
  return true;
end;
$function$;

create or replace function public.blink_test_bot_bulk_set_status(
  p_is_active boolean,
  p_actor_id uuid,
  p_reason text
)
returns integer
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_count integer;
begin
  if p_actor_id is null then
    raise exception 'ACTOR_REQUIRED' using errcode = '22023';
  end if;
  if p_reason is null or length(btrim(p_reason)) not between 5 and 500 then
    raise exception 'REASON_REQUIRED' using errcode = '22023';
  end if;

  with changed as (
    update private.blink_test_synthetic_accounts a
       set is_active = p_is_active
     where a.is_test_only = true and a.is_active is distinct from p_is_active
     returning a.id, not p_is_active as previous_active, a.is_active as new_active
  )
  insert into private.blink_test_bot_admin_audit
    (account_id, actor_id, previous_active, new_active, reason)
  select id, p_actor_id, previous_active, new_active, btrim(p_reason)
  from changed;

  get diagnostics v_count = row_count;
  return v_count;
end;
$function$;

revoke all on function public.blink_test_bot_list() from public, anon, authenticated;
revoke all on function public.blink_test_bot_set_status(bigint, boolean, uuid, text) from public, anon, authenticated;
revoke all on function public.blink_test_bot_bulk_set_status(boolean, uuid, text) from public, anon, authenticated;
grant execute on function public.blink_test_bot_list() to service_role;
grant execute on function public.blink_test_bot_set_status(bigint, boolean, uuid, text) to service_role;
grant execute on function public.blink_test_bot_bulk_set_status(boolean, uuid, text) to service_role;

commit;

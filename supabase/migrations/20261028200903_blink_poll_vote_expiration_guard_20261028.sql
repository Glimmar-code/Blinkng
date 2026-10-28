-- Applied to BLINK Supabase on 2026-10-28, migration version 20261028200903.
-- Security-invoker trigger enforces expiry and foreign option selection.
create or replace function public.blink_enforce_poll_vote_deadline()
returns trigger
language plpgsql
security invoker
set search_path = pg_catalog, public, pg_temp
as $function$
declare
  deadline timestamptz;
begin
  if new.user_id is distinct from (select auth.uid()) then
    raise exception 'Vote owner does not match authenticated user' using errcode = '42501';
  end if;
  select p.expires_at into deadline from public.polls p where p.id = new.poll_id;
  if not found then
    raise exception 'Poll does not exist' using errcode = '23503';
  end if;
  if deadline is not null and deadline <= clock_timestamp() then
    raise exception 'Poll has closed' using errcode = '23514';
  end if;
  if not exists (
    select 1 from public.poll_options po
    where po.poll_id = new.poll_id and po.id = new.option_id
  ) then
    raise exception 'Poll option does not belong to poll' using errcode = '23514';
  end if;
  return new;
end
$function$;
revoke all on function public.blink_enforce_poll_vote_deadline() from public, anon, authenticated;
drop trigger if exists blink_poll_vote_deadline on public.poll_votes;
create trigger blink_poll_vote_deadline
before insert or update of poll_id, option_id, user_id
on public.poll_votes
for each row execute function public.blink_enforce_poll_vote_deadline();

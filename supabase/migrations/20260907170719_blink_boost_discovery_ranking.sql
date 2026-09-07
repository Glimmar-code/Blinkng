create or replace function private_ranking.active_blink_boost_factor(
  p_post_id uuid,
  p_at timestamptz default now()
) returns numeric
language sql stable security definer set search_path=''
as $$
  select coalesce(max(b.multiplier),1)::numeric
  from public.blink_boosts b
  where b.content_id=p_post_id
    and b.status='ACTIVE'
    and b.starts_at<=p_at
    and b.ends_at>p_at;
$$;

revoke all on function private_ranking.active_blink_boost_factor(uuid,timestamptz)
from public,anon,authenticated;

do $$
declare
  v_oid oid;
  v_def text;
  v_old text := ') * c.verification_multiplier * c.cold_start_multiplier + private_ranking.viewer_personalization_bonus(v_user,c.post_id,c.creator_id,p_as_of)';
  v_new text := ') * c.verification_multiplier * c.cold_start_multiplier * private_ranking.active_blink_boost_factor(c.post_id,p_as_of) + private_ranking.viewer_personalization_bonus(v_user,c.post_id,c.creator_id,p_as_of)';
begin
  select p.oid into v_oid
  from pg_proc p
  join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='private_ranking' and p.proname='get_discovery_feed'
  order by p.oid desc
  limit 1;

  if v_oid is null then raise exception 'get_discovery_feed not found'; end if;
  select pg_get_functiondef(v_oid) into v_def;
  if position(v_old in v_def)=0 then
    raise exception 'Discovery score expression changed; refusing unsafe boost patch';
  end if;
  v_def:=replace(v_def,v_old,v_new);
  execute v_def;
end $$;

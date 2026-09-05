create index if not exists idx_profile_views_profile_viewer_created
    on public.profile_views (profile_id, viewer_id, created_at desc);

create index if not exists idx_activities_profile_view_dedupe
    on public.activities (recipient_id, actor_id, entity_type, created_at desc);

create or replace function public.create_activity(
    p_recipient_username text,
    p_action text,
    p_category text default 'ALL'::text,
    p_entity_type text default null::text,
    p_entity_id uuid default null::uuid
)
returns uuid
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_actor uuid := auth.uid();
  v_recipient uuid;
  v_id uuid;
  v_category text := upper(coalesce(nullif(btrim(p_category),''),'ALL'));
  v_entity_type text := nullif(btrim(p_entity_type),'');
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(btrim(p_recipient_username),'') is null then raise exception 'RECIPIENT_REQUIRED'; end if;
  if nullif(btrim(p_action),'') is null then raise exception 'ACTION_REQUIRED'; end if;
  if length(p_action)>300 then raise exception 'VALIDATION_ERROR: action too long'; end if;
  if v_category not in ('ALL','COMMENTS','LIKES','MARKET') then
    raise exception 'VALIDATION_ERROR: invalid activity category';
  end if;

  select id into v_recipient
  from public.profiles
  where lower(username)=lower(ltrim(btrim(p_recipient_username),'@'))
  limit 1;

  if v_recipient is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_recipient=v_actor then return null; end if;
  if exists(
    select 1 from public.blocks b
    where (b.blocker_id=v_actor and b.blocked_id=v_recipient)
       or (b.blocker_id=v_recipient and b.blocked_id=v_actor)
  ) then return null; end if;
  if (select count(*) from public.activities
      where actor_id=v_actor and created_at>now()-interval '1 minute')>=40 then
    raise exception 'RATE_LIMITED';
  end if;

  if upper(coalesce(v_entity_type,'')) = 'PROFILE' then
    if exists(
      select 1
      from public.activities a
      where a.recipient_id = v_recipient
        and a.actor_id = v_actor
        and upper(coalesce(a.entity_type,'')) = 'PROFILE'
        and a.created_at > now() - interval '6 hours'
    ) then
      return null;
    end if;

    insert into public.profile_views(profile_id, viewer_id)
    values(v_recipient, v_actor);
  end if;

  insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message)
  values(v_recipient,v_actor,v_category,v_entity_type,p_entity_id,btrim(p_action))
  returning id into v_id;
  return v_id;
end;
$function$;

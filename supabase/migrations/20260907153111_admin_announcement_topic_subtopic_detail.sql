alter table private.admin_notification_campaigns
  add column if not exists subtopic text;

create or replace function private.normalize_admin_campaign_message()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_payload jsonb;
begin
  new.title := left(coalesce(nullif(trim(new.title), ''), 'Blink'), 120);

  if left(ltrim(coalesce(new.message, '')), 1) = '{' then
    begin
      v_payload := new.message::jsonb;
    exception when others then
      v_payload := null;
    end;

    if v_payload is not null
       and coalesce((v_payload ->> '_blink_admin_message_v1')::boolean, false) then
      new.subtopic := nullif(left(trim(coalesce(v_payload ->> 'subtopic', '')), 160), '');
      new.message := left(coalesce(v_payload ->> 'message', ''), 12000);
    end if;
  end if;

  new.subtopic := nullif(left(trim(coalesce(new.subtopic, '')), 160), '');
  new.message := left(coalesce(new.message, ''), 12000);
  return new;
end;
$$;

drop trigger if exists trg_normalize_admin_campaign_message on private.admin_notification_campaigns;
create trigger trg_normalize_admin_campaign_message
before insert or update of title, message, subtopic
on private.admin_notification_campaigns
for each row execute function private.normalize_admin_campaign_message();

create or replace function private.dispatch_admin_campaign(p_campaign_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  c private.admin_notification_campaigns%rowtype;
  v_owner uuid:=private.blink_owner_id();
  v_count integer:=0;
  v_preview text;
begin
  select * into c from private.admin_notification_campaigns where id=p_campaign_id for update;
  if not found then raise exception 'CAMPAIGN_NOT_FOUND'; end if;
  if c.status in ('sent','cancelled') then return c.delivered_count; end if;
  if c.scheduled_at is not null and c.scheduled_at>now() then return 0; end if;
  update private.admin_notification_campaigns set status='sending',updated_at=now() where id=c.id;

  v_preview := case
    when nullif(trim(coalesce(c.subtopic,'')), '') is not null
      then c.subtopic || ' — ' || coalesce(c.message,'')
    else coalesce(c.message,'')
  end;

  with targets as (
    select p.id
    from public.profiles p
    where case coalesce(c.audience->>'type','all')
      when 'user' then p.id=(c.audience->>'user_id')::uuid
      when 'users' then p.id in (select value::uuid from jsonb_array_elements_text(coalesce(c.audience->'user_ids','[]'::jsonb)))
      when 'university' then coalesce(p.university,'')=coalesce(c.audience->>'university','')
      when 'universities' then coalesce(p.university,'') in (select value from jsonb_array_elements_text(coalesce(c.audience->'universities','[]'::jsonb)))
      when 'blue' then upper(coalesce(p.verification_badge,''))='BLUE' and p.is_verified
      when 'gold' then upper(coalesce(p.verification_badge,''))='GOLD' and p.is_verified
      when 'verified' then p.is_verified
      when 'unverified' then not coalesce(p.is_verified,false)
      else true end
  ), ins as (
    insert into public.notifications(user_id,actor_id,type,post_id,text,sub_text,is_read)
    select t.id,v_owner,'system'::public.notification_type_enum,
      case when c.link_type='post' then c.link_id else null end,
      left(coalesce(c.title,'Blink'),120),left(v_preview,2000),false
    from targets t
    where not exists(select 1 from private.admin_notification_deliveries d where d.campaign_id=c.id and d.user_id=t.id)
    returning id,user_id
  )
  insert into private.admin_notification_deliveries(campaign_id,user_id,notification_id)
  select c.id,ins.user_id,ins.id from ins
  on conflict(campaign_id,user_id) do nothing;

  insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
  select d.user_id,v_owner,'system','notification',d.notification_id,
    left(coalesce(c.title,'Blink') || ' • ' || v_preview,2000),false
  from private.admin_notification_deliveries d
  where d.campaign_id=c.id
    and not exists(select 1 from public.activities a where a.entity_type='notification' and a.entity_id=d.notification_id and a.recipient_id=d.user_id);

  select count(*) into v_count from private.admin_notification_deliveries where campaign_id=c.id;
  update private.admin_notification_campaigns set status='sent',delivered_count=v_count,updated_at=now() where id=c.id;
  return v_count;
exception when others then
  update private.admin_notification_campaigns set status='failed',updated_at=now() where id=p_campaign_id and status<>'cancelled';
  raise;
end;
$$;

create index if not exists admin_notification_deliveries_notification_id_idx
  on private.admin_notification_deliveries(notification_id)
  where notification_id is not null;

create or replace function public.get_admin_message_detail(p_activity_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_notification_id uuid;
  v_result jsonb;
begin
  if v_uid is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select d.notification_id,
         jsonb_build_object(
           'activity_id', a.id,
           'notification_id', d.notification_id,
           'campaign_id', c.id,
           'topic', coalesce(nullif(c.title,''), 'Blink'),
           'subtopic', coalesce(c.subtopic,''),
           'message', coalesce(c.message,''),
           'sender_name', coalesce(nullif(p.full_name,''), nullif(p.username,''), 'Blink Admin'),
           'created_at', c.created_at
         )
    into v_notification_id, v_result
  from public.activities a
  join private.admin_notification_deliveries d
    on d.notification_id = a.entity_id
   and d.user_id = v_uid
  join private.admin_notification_campaigns c
    on c.id = d.campaign_id
  left join public.profiles p
    on p.id = c.sender_id
  where a.id = p_activity_id
    and a.recipient_id = v_uid
    and a.entity_type = 'notification'
  limit 1;

  if v_result is null then
    return null;
  end if;

  update public.activities
     set is_read = true
   where id = p_activity_id
     and recipient_id = v_uid;

  update public.notifications
     set is_read = true
   where id = v_notification_id
     and user_id = v_uid;

  return v_result;
end;
$$;

revoke all on function public.get_admin_message_detail(uuid) from public;
grant execute on function public.get_admin_message_detail(uuid) to authenticated;

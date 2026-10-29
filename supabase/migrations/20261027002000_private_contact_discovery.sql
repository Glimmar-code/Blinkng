-- destructive-change-reviewed: deletes in replace_imports affect only the invoking user's new
-- contact HMAC records; named trigger/permission replacements are scoped to BLINK contact objects.
-- No profiles, chats, payment rows or unrelated notifications are deleted.
-- User-requested deletion removes only contact-derived notifications.
-- rollback-plan: disable blink-contact-discovery Edge Function first; remove the new
-- blink_contact_* triggers, service-only RPCs, private index/import/alert tables and
-- owner-only contact settings. This loses only optional contact matching data, not profiles.
-- No-SMS contact discovery: require both users' explicit opt-in and reciprocal phone-book hashes.
-- Matches are POSSIBLE contacts; self-declared profile phone numbers are NOT verified.
-- No raw contact numbers, names, or phone history are stored. HMAC keys stay in Edge Function env.
-- Additive release; must be validated against isolated/staging Supabase before production.
begin;

create schema if not exists private;

create table if not exists public.blink_contact_settings (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  sync_enabled boolean not null default false,
  discoverable boolean not null default false,
  join_alerts boolean not null default false,
  updated_at timestamptz not null default now(),
  last_synced_at timestamptz
);
alter table public.blink_contact_settings enable row level security;
revoke all on public.blink_contact_settings from public,anon,authenticated;
grant select on public.blink_contact_settings to authenticated;
grant all on public.blink_contact_settings to service_role;
drop policy if exists blink_contact_settings_owner_read on public.blink_contact_settings;
create policy blink_contact_settings_owner_read on public.blink_contact_settings
  for select to authenticated using ((select auth.uid())=user_id);

create table if not exists private.blink_contact_directory (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  phone_hmac text not null check(phone_hmac ~ '^[a-f0-9]{64}$'),
  registered_at timestamptz not null default now()
);
create index if not exists blink_contact_directory_hmac_idx
 on private.blink_contact_directory(phone_hmac);
alter table private.blink_contact_directory enable row level security;
revoke all on private.blink_contact_directory from public,anon,authenticated;
grant all on private.blink_contact_directory to service_role;

create table if not exists private.blink_contact_imports (
  owner_id uuid not null references public.profiles(id) on delete cascade,
  phone_hmac text not null check(phone_hmac ~ '^[a-f0-9]{64}$'),
  synced_at timestamptz not null default now(),
  primary key(owner_id,phone_hmac)
);
create index if not exists blink_contact_imports_hmac_idx
 on private.blink_contact_imports(phone_hmac);
alter table private.blink_contact_imports enable row level security;
revoke all on private.blink_contact_imports from public,anon,authenticated;
grant all on private.blink_contact_imports to service_role;

create table if not exists private.blink_contact_join_alerts (
  owner_id uuid not null references public.profiles(id) on delete cascade,
  joiner_id uuid not null references public.profiles(id) on delete cascade,
  notification_id uuid references public.notifications(id) on delete set null,
  created_at timestamptz not null default now(),
  primary key(owner_id,joiner_id),
  check(owner_id<>joiner_id)
);
alter table private.blink_contact_join_alerts enable row level security;
revoke all on private.blink_contact_join_alerts from public,anon,authenticated;
grant all on private.blink_contact_join_alerts to service_role;

-- Privileged entry points can ONLY be used by service_role; Edge verifies the end user's
-- JWT and explicit reciprocal contact consent before invoking them.
-- Phone numbers are not ownership-verified and must never imply identity proof.
create or replace function public.blink_contact_replace_imports(p_owner uuid,p_hashes text[])
returns integer language plpgsql security definer set search_path=''
as $$
declare clean_hashes text[];
begin
 if p_owner is null or p_hashes is null or cardinality(p_hashes)>500 then
   raise exception 'Invalid contacts batch';
 end if;
 if exists (select 1 from unnest(p_hashes) h where h !~ '^[a-f0-9]{64}$') then
   raise exception 'Invalid contacts hash';
 end if;
 if not exists(select 1 from public.blink_contact_settings s
   where s.user_id=p_owner and s.sync_enabled=true) then
   raise exception 'Contact syncing not enabled';
 end if;
 select coalesce(array_agg(distinct h),'{}'::text[]) into clean_hashes from unnest(p_hashes) h;
 -- Keep deduplication for unchanged matches across a resync; otherwise
 -- the same joined contact would generate repeat notifications every minute.
 delete from private.blink_contact_join_alerts alert
 where alert.owner_id=p_owner
   and not exists (
     select 1 from private.blink_contact_directory target
     where target.user_id=alert.joiner_id
       and target.phone_hmac=any(clean_hashes)
   );
 delete from private.blink_contact_imports where owner_id=p_owner;
 insert into private.blink_contact_imports(owner_id,phone_hmac)
  select p_owner,h from unnest(clean_hashes) h on conflict do nothing;
 update public.blink_contact_settings set last_synced_at=now(),updated_at=now()
  where user_id=p_owner;
 return cardinality(clean_hashes);
end $$;
revoke all on function public.blink_contact_replace_imports(uuid,text[]) from public,anon,authenticated;
grant execute on function public.blink_contact_replace_imports(uuid,text[]) to service_role;

-- Join alerts require a recent discoverable member and reciprocal opted-in imports.
create or replace function private.blink_contact_notify_join(p_joiner uuid,p_hmac text)
returns integer language plpgsql security definer set search_path=''
as $$
declare recipient record; note_id uuid; total integer:=0;
begin
 if p_joiner is null or p_hmac is null then return 0; end if;
 if not exists (select 1 from public.profiles p
   join public.blink_contact_settings s on s.user_id=p.id
   where p.id=p_joiner and p.onboarding_completed=true and
     p.created_at > now()-interval '48 hours' and
     s.discoverable=true and s.sync_enabled=true) then
   return 0;
 end if;
 for recipient in
  select distinct i.owner_id from private.blink_contact_imports i
  join public.blink_contact_settings s on s.user_id=i.owner_id
  where i.phone_hmac=p_hmac and i.owner_id<>p_joiner and
    s.sync_enabled=true and s.discoverable=true and s.join_alerts=true
    and exists (
      select 1 from private.blink_contact_directory owner_phone
      join private.blink_contact_imports reciprocal
        on reciprocal.owner_id=p_joiner and reciprocal.phone_hmac=owner_phone.phone_hmac
      where owner_phone.user_id=i.owner_id
    )
    and not exists(select 1 from public.blocks b
      where (b.blocker_id=i.owner_id and b.blocked_id=p_joiner)
         or (b.blocker_id=p_joiner and b.blocked_id=i.owner_id))
  limit 250
 loop
  insert into private.blink_contact_join_alerts(owner_id,joiner_id)
    values(recipient.owner_id,p_joiner) on conflict do nothing;
  if found then
    insert into public.notifications(
       user_id,actor_id,type,target_type,target_id,text,sub_text,metadata,is_read
    ) values (
      recipient.owner_id,p_joiner,'system'::public.notification_type_enum,
      'user',p_joiner,'A possible contact joined BLINK',
      'Both people opted in to contact matching. Phone ownership is not verified.',
      jsonb_build_object('kind','blink_contact_joined','match_type','mutual_unverified'),false
    ) returning id into note_id;
    update private.blink_contact_join_alerts
     set notification_id=note_id where owner_id=recipient.owner_id and joiner_id=p_joiner;
    total:=total+1;
  end if;
 end loop;
 return total;
end $$;
revoke all on function private.blink_contact_notify_join(uuid,text) from public,anon,authenticated,service_role;

create or replace function private.blink_contact_on_directory_change()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
 if tg_op='INSERT' or new.phone_hmac is distinct from old.phone_hmac then
   perform private.blink_contact_notify_join(new.user_id,new.phone_hmac);
 end if;
 return new;
end $$;
revoke all on function private.blink_contact_on_directory_change() from public,anon,authenticated,service_role;
drop trigger if exists blink_contact_notify_on_register on private.blink_contact_directory;
create trigger blink_contact_notify_on_register
 after insert or update of phone_hmac on private.blink_contact_directory
 for each row execute function private.blink_contact_on_directory_change();

create or replace function private.blink_contact_on_onboarding()
returns trigger language plpgsql security definer set search_path=''
as $$
declare d text;
begin
 if new.onboarding_completed=true and old.onboarding_completed is distinct from true then
  select phone_hmac into d from private.blink_contact_directory where user_id=new.id;
  if d is not null then perform private.blink_contact_notify_join(new.id,d); end if;
 end if;
 return new;
end $$;
revoke all on function private.blink_contact_on_onboarding() from public,anon,authenticated,service_role;
drop trigger if exists blink_contact_notify_after_onboarding on public.profiles;
create trigger blink_contact_notify_after_onboarding
 after update of onboarding_completed on public.profiles for each row
 execute function private.blink_contact_on_onboarding();

create or replace function public.blink_contact_upsert_opted_in(p_user uuid,p_hmac text)
returns void language plpgsql security definer set search_path=''
as $$
begin
 if p_user is null or p_hmac !~ '^[a-f0-9]{64}$' then raise exception 'Invalid opted-in phone hash'; end if;
 if exists(select 1 from public.blink_contact_settings where user_id=p_user and discoverable=true and sync_enabled=true)
    and p_hmac <> repeat('0',64) then
   insert into private.blink_contact_directory(user_id,phone_hmac) values(p_user,p_hmac)
   on conflict(user_id) do update set phone_hmac=excluded.phone_hmac,registered_at=now();
 else
   delete from private.blink_contact_directory where user_id=p_user;
 end if;
end $$;
revoke all on function public.blink_contact_upsert_opted_in(uuid,text) from public,anon,authenticated;
grant execute on function public.blink_contact_upsert_opted_in(uuid,text) to service_role;

create or replace function public.blink_contact_matches_for(p_user uuid)
returns table(user_id uuid,username text,full_name text,avatar_url text,verification_badge text)
language sql stable security definer set search_path=''
as $$
 select distinct p.id,p.username,p.full_name,p.avatar_url,p.verification_badge::text
 from private.blink_contact_imports i
 join private.blink_contact_directory d on d.phone_hmac=i.phone_hmac and d.user_id<>p_user
 join private.blink_contact_directory owner_phone on owner_phone.user_id=p_user
 join private.blink_contact_imports reciprocal on reciprocal.owner_id=d.user_id
    and reciprocal.phone_hmac=owner_phone.phone_hmac
 join public.profiles p on p.id=d.user_id
 join public.blink_contact_settings own on own.user_id=p_user
 join public.blink_contact_settings target on target.user_id=d.user_id
 where i.owner_id=p_user and own.sync_enabled=true and own.discoverable=true
   and target.sync_enabled=true and target.discoverable=true
   and p.onboarding_completed=true
   and not exists(select 1 from public.blocks b
     where (b.blocker_id=p_user and b.blocked_id=p.id)
        or (b.blocker_id=p.id and b.blocked_id=p_user))
 order by p.username limit 100;
$$;
revoke all on function public.blink_contact_matches_for(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_matches_for(uuid) to service_role;


-- Edge-only post-sync evaluation. Alert creation is deduped by owner and joiner.
create or replace function public.blink_contact_notify_my_join(p_joiner uuid)
returns integer language plpgsql security definer set search_path=''
as $$
declare self_hash text;
begin
 select phone_hmac into self_hash from private.blink_contact_directory where user_id=p_joiner;
 if self_hash is null then return 0; end if;
 return private.blink_contact_notify_join(p_joiner,self_hash);
end $$;
revoke all on function public.blink_contact_notify_my_join(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_notify_my_join(uuid) to service_role;


-- If the established member syncs after a recent joiner, evaluate the reciprocal
-- match now rather than depending on the joiner's next app launch.
-- Returns only recent alert IDs for this invoking owner, never raw contact hashes.
create or replace function public.blink_contact_notify_recent_matches(p_owner uuid)
returns table(notification_id uuid)
language plpgsql security definer set search_path=''
as $$
declare joined record;
begin
 if p_owner is null then return; end if;
 for joined in
  select distinct d.user_id, d.phone_hmac
  from private.blink_contact_imports i
  join private.blink_contact_directory d on d.phone_hmac=i.phone_hmac
  join public.profiles p on p.id=d.user_id
  where i.owner_id=p_owner and d.user_id<>p_owner
    and p.created_at>now()-interval '48 hours'
  limit 100
 loop
  perform private.blink_contact_notify_join(joined.user_id, joined.phone_hmac);
 end loop;
 return query
  select a.notification_id from private.blink_contact_join_alerts a
  where a.owner_id=p_owner and a.notification_id is not null
    and a.created_at>now()-interval '15 minutes'
  order by a.created_at desc limit 100;
end $$;
revoke all on function public.blink_contact_notify_recent_matches(uuid)
 from public,anon,authenticated;
grant execute on function public.blink_contact_notify_recent_matches(uuid) to service_role;

-- Service-role-only cleanup and verified proof for trusted push dispatch.
create or replace function public.blink_contact_clear_imports(p_owner uuid)
returns void language plpgsql security definer set search_path=''
as $$
begin
 -- Only generated contact-alert notifications are removed; other activity
 -- notifications and conversation history are never touched.
 delete from public.notifications note
 where note.id in (
   select alert.notification_id from private.blink_contact_join_alerts alert
   where (alert.owner_id=p_owner or alert.joiner_id=p_owner)
     and alert.notification_id is not null
 );
 delete from private.blink_contact_imports where owner_id=p_owner;
 delete from private.blink_contact_join_alerts
  where owner_id=p_owner or joiner_id=p_owner;
end $$;
revoke all on function public.blink_contact_clear_imports(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_clear_imports(uuid) to service_role;

create or replace function public.blink_contact_push_is_authorized(p_notification_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
 select exists (
  select 1 from private.blink_contact_join_alerts a
  join public.notifications n on n.id=a.notification_id and
   n.user_id=a.owner_id and n.actor_id=a.joiner_id
  join public.blink_contact_settings recipient on recipient.user_id=a.owner_id
  join public.blink_contact_settings joiner on joiner.user_id=a.joiner_id
  where a.notification_id=p_notification_id and
   n.metadata->>'kind'='blink_contact_joined' and
   recipient.sync_enabled=true and recipient.discoverable=true and
   recipient.join_alerts=true and joiner.sync_enabled=true and joiner.discoverable=true
   and exists (
     select 1 from private.blink_contact_directory rd
     join private.blink_contact_imports ri on ri.owner_id=a.joiner_id
        and ri.phone_hmac=rd.phone_hmac
     where rd.user_id=a.owner_id
   )
   and not exists(select 1 from public.blocks b
     where (b.blocker_id=a.owner_id and b.blocked_id=a.joiner_id)
        or (b.blocker_id=a.joiner_id and b.blocked_id=a.owner_id))
 );
$$;
revoke all on function public.blink_contact_push_is_authorized(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_push_is_authorized(uuid) to service_role;

create or replace function public.blink_contact_my_recent_push_ids(p_joiner uuid)
returns table(notification_id uuid)
language sql stable security definer set search_path=''
as $$
 select a.notification_id from private.blink_contact_join_alerts a
 where a.joiner_id=p_joiner and a.notification_id is not null and
  a.created_at>now()-interval '15 minutes'
 order by a.created_at desc limit 100;
$$;
revoke all on function public.blink_contact_my_recent_push_ids(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_my_recent_push_ids(uuid) to service_role;

commit;

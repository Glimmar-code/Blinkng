-- Contact discovery is opt-in, based only on verified phone ownership.
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
-- JWT, verified phone number and express contact consent before invoking them.
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
 delete from private.blink_contact_imports where owner_id=p_owner;
 insert into private.blink_contact_imports(owner_id,phone_hmac)
  select p_owner,h from unnest(clean_hashes) h on conflict do nothing;
 update public.blink_contact_settings set last_synced_at=now(),updated_at=now()
  where user_id=p_owner;
 return cardinality(clean_hashes);
end $$;
revoke all on function public.blink_contact_replace_imports(uuid,text[]) from public,anon,authenticated;
grant execute on function public.blink_contact_replace_imports(uuid,text[]) to service_role;

-- Join alerts require a new, verified, discoverable member and a previously synced contact.
create or replace function private.blink_contact_notify_join(p_joiner uuid,p_hmac text)
returns integer language plpgsql security definer set search_path=''
as $$
declare recipient record; note_id uuid; total integer:=0;
begin
 if p_joiner is null or p_hmac is null then return 0; end if;
 if not exists (select 1 from public.profiles p
   join public.blink_contact_settings s on s.user_id=p.id
   where p.id=p_joiner and p.onboarding_completed=true and
     p.created_at > now()-interval '48 hours' and s.discoverable=true) then
   return 0;
 end if;
 for recipient in
  select distinct i.owner_id from private.blink_contact_imports i
  join public.blink_contact_settings s on s.user_id=i.owner_id
  where i.phone_hmac=p_hmac and i.owner_id<>p_joiner and
    s.sync_enabled=true and s.join_alerts=true
  limit 250
 loop
  insert into private.blink_contact_join_alerts(owner_id,joiner_id)
    values(recipient.owner_id,p_joiner) on conflict do nothing;
  if found then
    insert into public.notifications(
       user_id,actor_id,type,target_type,target_id,text,sub_text,metadata,is_read
    ) values (
      recipient.owner_id,p_joiner,'system'::public.notification_type_enum,
      'user',p_joiner,'Someone from your contacts joined BLINK',
      'A contact you know is now on BLINK.',
      jsonb_build_object('kind','blink_contact_joined'),false
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

create or replace function public.blink_contact_upsert_verified(p_user uuid,p_hmac text)
returns void language plpgsql security definer set search_path=''
as $$
begin
 if p_user is null or p_hmac !~ '^[a-f0-9]{64}$' then raise exception 'Invalid verified phone hash'; end if;
 if exists(select 1 from public.blink_contact_settings where user_id=p_user and discoverable=true) then
   insert into private.blink_contact_directory(user_id,phone_hmac) values(p_user,p_hmac)
   on conflict(user_id) do update set phone_hmac=excluded.phone_hmac,registered_at=now();
 else
   delete from private.blink_contact_directory where user_id=p_user;
 end if;
end $$;
revoke all on function public.blink_contact_upsert_verified(uuid,text) from public,anon,authenticated;
grant execute on function public.blink_contact_upsert_verified(uuid,text) to service_role;

create or replace function public.blink_contact_matches_for(p_user uuid)
returns table(user_id uuid,username text,full_name text,avatar_url text,verification_badge text)
language sql stable security definer set search_path=''
as $$
 select distinct p.id,p.username,p.full_name,p.avatar_url,p.verification_badge::text
 from private.blink_contact_imports i
 join private.blink_contact_directory d on d.phone_hmac=i.phone_hmac and d.user_id<>p_user
 join public.profiles p on p.id=d.user_id
 join public.blink_contact_settings own on own.user_id=p_user
 join public.blink_contact_settings target on target.user_id=d.user_id
 where i.owner_id=p_user and own.sync_enabled=true and target.discoverable=true
   and p.onboarding_completed=true
 order by p.username limit 100;
$$;
revoke all on function public.blink_contact_matches_for(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_matches_for(uuid) to service_role;

-- Service-role-only cleanup and verified proof for trusted push dispatch.
create or replace function public.blink_contact_clear_imports(p_owner uuid)
returns void language plpgsql security definer set search_path=''
as $
begin
 delete from private.blink_contact_imports where owner_id=p_owner;
end $;
revoke all on function public.blink_contact_clear_imports(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_clear_imports(uuid) to service_role;

create or replace function public.blink_contact_push_is_authorized(p_notification_id uuid)
returns boolean language sql stable security definer set search_path=''
as $
 select exists (
  select 1 from private.blink_contact_join_alerts a
  join public.notifications n on n.id=a.notification_id and
   n.user_id=a.owner_id and n.actor_id=a.joiner_id
  join public.blink_contact_settings recipient on recipient.user_id=a.owner_id
  join public.blink_contact_settings joiner on joiner.user_id=a.joiner_id
  where a.notification_id=p_notification_id and
   n.metadata->>'kind'='blink_contact_joined' and
   recipient.sync_enabled=true and recipient.join_alerts=true and
   joiner.discoverable=true
 );
$;
revoke all on function public.blink_contact_push_is_authorized(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_push_is_authorized(uuid) to service_role;

create or replace function public.blink_contact_my_recent_push_ids(p_joiner uuid)
returns table(notification_id uuid)
language sql stable security definer set search_path=''
as $
 select a.notification_id from private.blink_contact_join_alerts a
 where a.joiner_id=p_joiner and a.notification_id is not null and
  a.created_at>now()-interval '15 minutes'
 order by a.created_at desc limit 100;
$;
revoke all on function public.blink_contact_my_recent_push_ids(uuid) from public,anon,authenticated;
grant execute on function public.blink_contact_my_recent_push_ids(uuid) to service_role;

commit;

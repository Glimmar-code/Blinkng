-- Restore the admin RPCs used by the Android Blink Admin workspace.
-- This is intentionally additive and safe to rerun.
create table if not exists public.blink_contest_topics (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name text not null unique check (length(trim(name)) between 3 and 100),
  category text not null check (length(category) between 2 and 60),
  description text not null default '',
  is_active boolean not null default true,
  sort_order integer not null default 100,
  created_at timestamptz not null default now()
);
alter table public.blink_contest_topics enable row level security;
revoke all on public.blink_contest_topics from public, anon, authenticated, service_role;
grant select, insert, update on public.blink_contest_topics to service_role;

insert into public.blink_contest_topics(slug,name,category,description,sort_order) values
('most-popular-student','Most Popular Student','Personality','A community popularity recognition.',1),
('school-representative','School Representative','Personality','Represent your school with pride.',2),
('campus-got-talent','Campus Got Talent','Entertainment','A multi-talent showcase for students.',3),
('best-student-business','Best Student Business','Entrepreneurship','Student-owned business showcase.',4),
('best-content-creator','Best Content Creator','Digital Creativity','Original and engaging digital content.',5),
('best-dancer','Best Dancer','Entertainment','Dance performance and creativity.',6),
('best-singer','Best Singer','Entertainment','Vocal performance competition.',7),
('best-tech-innovator','Best Tech Innovator','Academics','Practical technology innovation.',8)
on conflict (slug) do update set name=excluded.name, category=excluded.category,
 description=excluded.description, sort_order=excluded.sort_order;

alter table public.blink_contests
  add column if not exists topic_id uuid references public.blink_contest_topics(id) on delete restrict,
  add column if not exists competition_scope text not null default 'campus'
    check (competition_scope in ('campus','nationwide')),
  add column if not exists season text check (season is null or length(season)<=80);
create index if not exists blink_contests_topic_scope_idx
  on public.blink_contests(topic_id, competition_scope, status, created_at desc);

create table if not exists public.blink_contest_topic_follows (
  topic_id uuid not null references public.blink_contest_topics(id) on delete restrict,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(topic_id,user_id)
);
create table if not exists public.blink_contest_history_participants (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  entry_id uuid,
  user_id uuid,
  username text not null,
  avatar_url text,
  votes integer not null default 0,
  final_place integer,
  prize_ngn integer not null default 0,
  captured_at timestamptz not null default now(),
  unique(contest_id,entry_id)
);

-- These supporting tables are accessed only through the SECURITY DEFINER RPCs
-- above/below; keep direct Data API access closed to every client role.
alter table public.blink_contest_topic_follows enable row level security;
revoke all on public.blink_contest_topic_follows from anon, authenticated, service_role;
grant all on public.blink_contest_topic_follows to service_role;

alter table public.blink_contest_history_participants enable row level security;
revoke all on public.blink_contest_history_participants from anon, authenticated, service_role;
grant all on public.blink_contest_history_participants to service_role;

create or replace function public.list_blink_contest_topics()
returns jsonb language sql stable security definer set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',t.id,'slug',t.slug,'name',t.name,'category',t.category,
    'description',t.description,'sort_order',t.sort_order,
    'followers',(select count(*) from public.blink_contest_topic_follows f where f.topic_id=t.id),
    'contests_total',(select count(*) from public.blink_contests c where c.topic_id=t.id),
    'completed_contests',(select count(*) from public.blink_contests c where c.topic_id=t.id and c.status='completed'),
    'ongoing_contests',(select count(*) from public.blink_contests c where c.topic_id=t.id and c.status in ('published','paused')),
    'upcoming_contests',(select count(*) from public.blink_contests c where c.topic_id=t.id and c.status='published' and now()<c.registration_opens_at),
    'participants',0,'votes',0,'prizes_awarded',0
  ) order by t.sort_order,t.name),'[]'::jsonb)
  from public.blink_contest_topics t where t.is_active;
$$;

create or replace function public.admin_save_blink_contest_topic(p_id uuid,p_data jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_id uuid; v_slug text; v_name text; v_category text; v_description text;
begin
  perform private.require_blink_admin();
  if p_data is null or jsonb_typeof(p_data)<>'object' or octet_length(p_data::text)>3000 then raise exception 'INVALID_TOPIC'; end if;
  v_name:=trim(coalesce(p_data->>'name','')); v_category:=trim(coalesce(p_data->>'category',''));
  v_slug:=lower(trim(both '-' from regexp_replace(v_name,'[^a-zA-Z0-9]+','-','g')));
  v_description:=trim(coalesce(p_data->>'description',''));
  if length(v_name) not between 3 and 100 or length(v_category) not between 2 and 60 or length(v_description)>500 or v_slug='' then raise exception 'INVALID_TOPIC'; end if;
  if p_id is null then
    insert into public.blink_contest_topics(slug,name,category,description,sort_order) values(v_slug,v_name,v_category,v_description,500) returning id into v_id;
  else
    update public.blink_contest_topics set name=v_name,category=v_category,description=v_description where id=p_id returning id into v_id;
    if v_id is null then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  end if;
  return (select to_jsonb(t) from public.blink_contest_topics t where id=v_id);
end; $$;

create or replace function public.admin_set_blink_contest_topic_active(p_topic_id uuid,p_active boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
  perform private.require_blink_admin();
  update public.blink_contest_topics set is_active=p_active where id=p_topic_id;
  if not found then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  return jsonb_build_object('id',p_topic_id,'is_active',p_active);
end; $$;

create or replace function private.admin_notification_delivery_health_impl()
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare
  v_active_devices bigint:=0; v_users_with_push bigint:=0; v_social_sent bigint:=0; v_social_failed bigint:=0;
  v_social_sending bigint:=0; v_social_retried bigint:=0; v_message_sent bigint:=0; v_message_failed bigint:=0;
  v_message_sending bigint:=0; v_message_retried bigint:=0; v_failures_24h bigint:=0;
begin
  perform private.require_blink_admin();
  if to_regclass('public.fcm_tokens') is not null then
    execute 'select count(*) filter(where is_active),count(distinct user_id) filter(where is_active) from public.fcm_tokens' into v_active_devices,v_users_with_push;
  end if;
  if to_regclass('public.notification_push_dispatches') is not null then
    execute 'select count(*) filter(where status=''sent''),count(*) filter(where status=''failed''),count(*) filter(where status=''sending''),count(*) filter(where attempts>1),count(*) filter(where status=''failed'' and updated_at>=now()-interval ''24 hours'') from public.notification_push_dispatches' into v_social_sent,v_social_failed,v_social_sending,v_social_retried,v_failures_24h;
  end if;
  if to_regclass('public.message_push_dispatches') is not null then
    execute 'select count(*) filter(where status=''sent''),count(*) filter(where status=''failed''),count(*) filter(where status=''sending''),count(*) filter(where attempts>1),count(*) filter(where status=''failed'' and updated_at>=now()-interval ''24 hours'') from public.message_push_dispatches' into v_message_sent,v_message_failed,v_message_sending,v_message_retried;
  end if;
  return jsonb_build_object('active_devices',v_active_devices,'users_with_push',v_users_with_push,'social_sent',v_social_sent,'social_failed',v_social_failed,'social_sending',v_social_sending,'social_retried',v_social_retried,'message_sent',v_message_sent,'message_failed',v_message_failed,'message_sending',v_message_sending,'message_retried',v_message_retried,'failures_24h',v_failures_24h);
end; $$;
create or replace function public.admin_notification_delivery_health()
returns jsonb language sql stable security definer set search_path='' as $$ select private.admin_notification_delivery_health_impl() $$;

revoke all on function public.list_blink_contest_topics() from public,anon;
grant execute on function public.list_blink_contest_topics() to authenticated;
revoke all on function public.admin_save_blink_contest_topic(uuid,jsonb), public.admin_set_blink_contest_topic_active(uuid,boolean) from public,anon,authenticated;
grant execute on function public.admin_save_blink_contest_topic(uuid,jsonb), public.admin_set_blink_contest_topic_active(uuid,boolean) to authenticated;
revoke all on function public.admin_notification_delivery_health() from public,anon,authenticated;
grant execute on function public.admin_notification_delivery_health() to authenticated;
notify pgrst,'reload schema';

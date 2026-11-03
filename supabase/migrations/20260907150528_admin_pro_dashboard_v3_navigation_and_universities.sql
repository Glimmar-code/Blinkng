-- Blink Admin Control Center V3.
-- This migration builds on the production admin 200/700-feature foundation.

create table if not exists private.admin_sections_v3 (
  section_key text primary key,
  title text not null,
  description text not null default '',
  sort_order integer not null unique,
  show_in_sidebar boolean not null default true,
  is_top_action boolean not null default false,
  enabled boolean not null default true
);

insert into private.admin_sections_v3(section_key,title,description,sort_order,show_in_sidebar,is_top_action,enabled) values
('dashboard','Dashboard','Overview and key admin health indicators.',10,true,false,true),
('users','Users','Find and manage Blink users.',20,true,false,true),
('accounts','Accounts','Account lifecycle and identity administration.',30,true,false,true),
('profiles','Profiles','Profile information and profile-level controls.',40,true,false,true),
('sessions_devices','Sessions & Devices','Sessions, devices and account access.',50,true,false,true),
('universities','Universities','Search and manage university-scoped administration.',60,true,false,true),
('verification','Verification','Verification requests, badges and payments.',70,true,false,true),
('admins','Admins','Admin access and ownership visibility.',80,true,false,true),
('roles_permissions','Roles & Permissions','Moderator, admin role and permission controls.',90,true,false,true),
('posts','Posts','Post moderation and distribution controls.',100,true,false,true),
('reels','Reels & Videos','Reel and video administration.',110,true,false,true),
('stories','Stories','Story administration and safety.',120,true,false,true),
('comments','Comments','Comment moderation and comment insights.',130,true,false,true),
('messages','Messages','Message administration and delivery health.',140,true,false,true),
('conversations','Conversations','Conversation-level administration.',150,true,false,true),
('reports','Reports','User/content reports and report investigation.',160,true,false,true),
('moderation_queue','Moderation Queue','Moderation workload and escalations.',170,true,false,true),
('support_appeals','Support & Appeals','Support cases, report reviews and appeals.',180,true,false,true),
('safety_restrictions','Safety & Restrictions','Account safety restrictions and trust controls.',190,true,false,true),
('notifications','Notifications','Notification delivery and notification operations.',200,true,false,true),
('announcements','Announcements','App-wide and targeted announcements.',210,true,false,true),
('coins_balances','Coins & Balances','Blink Coin grants, freezes and balances.',220,true,false,true),
('points_rewards','Points & Rewards','Points, rewards and reward activity.',230,true,false,true),
('transactions','Transactions & Monetization','Token, payment and monetization operations.',240,true,false,true),
('marketplace','Marketplace','Marketplace listings and inquiries.',250,true,false,true),
('orders_sellers','Orders & Sellers','Orders and seller administration.',260,true,false,true),
('connect_hub','Connect Hub','Connect listings and application activity.',270,true,false,true),
('housing','Housing','Housing requests, applications and agents.',280,true,false,true),
('mentorship','Mentorship','Mentor profiles and requests.',290,true,false,true),
('reading_mate','Reading Mate','Reading mate profiles and requests.',300,true,false,true),
('study_circles','Study Circles','Study circles and membership/request administration.',310,true,false,true),
('games','Games','Game sessions, rewards and challenges.',320,true,false,true),
('leaderboards','Leaderboards','Leaderboard and ranking operations.',330,true,false,true),
('engagement','Engagement','Likes, follows, shares, views and engagement.',340,true,false,true),
('analytics','Analytics','Operational and growth analytics.',350,true,false,true),
('scheduled_content','Scheduled Content','Scheduled post and content operations.',360,true,false,true),
('system_config','System & Configuration','Feature flags and system configuration.',370,true,false,true),
('developer_ops','Developer Operations','Performance, push delivery and runtime health.',380,true,false,true),
('audit_logs','History','Permanent admin action history and reversals.',390,false,true,true)
on conflict(section_key) do update set
  title=excluded.title,
  description=excluded.description,
  sort_order=excluded.sort_order,
  show_in_sidebar=excluded.show_in_sidebar,
  is_top_action=excluded.is_top_action,
  enabled=excluded.enabled;

alter table private.admin_feature_registry_v2 add column if not exists section_key text;
alter table private.admin_feature_registry_v2 add column if not exists permission_key text;
alter table private.admin_feature_registry_v2 add column if not exists risk_level text not null default 'low';
alter table private.admin_feature_registry_v2 add column if not exists confirmation_kind text not null default 'none';
create index if not exists idx_admin_feature_registry_v2_section on private.admin_feature_registry_v2(section_key,feature_id);

create table if not exists private.admin_university_catalog (
  name text primary key,
  source text not null default 'android:NigerianUniversities',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Production is seeded from app/src/main/java/com/example/data/models/NigerianUniversities.kt.
-- Keep live profile values in the result as well so newly introduced institutions remain discoverable.
insert into private.admin_university_catalog(name,source)
select distinct trim(university),'profile-sync'
from public.profiles
where nullif(trim(university),'') is not null
on conflict(name) do update set active=true,updated_at=now();

create or replace function private.admin_search_universities_v3_impl(p_query text default '', p_limit integer default 100)
returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare v_actor uuid; v_q text:=trim(coalesce(p_query,''));
begin
  v_actor:=private.require_blink_admin();
  return coalesce((
    select jsonb_agg(name order by name)
    from (
      select distinct name from (
        select c.name from private.admin_university_catalog c where c.active and (v_q='' or c.name ilike '%'||v_q||'%')
        union
        select trim(p.university) from public.profiles p where nullif(trim(p.university),'') is not null and (v_q='' or p.university ilike '%'||v_q||'%')
      ) u(name)
      order by name
      limit greatest(1,least(coalesce(p_limit,100),300))
    ) q
  ),'[]'::jsonb);
end $$;

create or replace function public.admin_search_universities_v3(p_query text default '', p_limit integer default 100)
returns jsonb language sql stable security definer set search_path=''
as $$ select private.admin_search_universities_v3_impl(p_query,p_limit) $$;

create or replace function private.admin_list_sections_v3_impl()
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare v_actor uuid;
begin
  v_actor:=private.require_blink_admin();
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'section_key',s.section_key,
      'title',s.title,
      'description',s.description,
      'sort_order',s.sort_order,
      'show_in_sidebar',s.show_in_sidebar,
      'is_top_action',s.is_top_action,
      'enabled',s.enabled,
      'feature_count',(select count(*) from private.admin_feature_registry_v2 f where f.section_key=s.section_key and f.enabled)
    ) order by s.sort_order)
    from private.admin_sections_v3 s where s.enabled
  ),'[]'::jsonb);
end $$;

create or replace function public.admin_list_sections_v3()
returns jsonb language sql stable security definer set search_path=''
as $$ select private.admin_list_sections_v3_impl() $$;

create or replace function private.admin_list_features_v3_impl(p_section_key text default null, p_query text default '')
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare v_actor uuid; v_section text:=nullif(trim(coalesce(p_section_key,'')),''); v_q text:=trim(coalesce(p_query,''));
begin
  v_actor:=private.require_blink_admin();
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'feature_id',f.feature_id,
      'title',f.title,
      'category',f.category,
      'module',f.module,
      'section_key',f.section_key,
      'route_key',f.route_key,
      'target_type',f.target_type,
      'input_kind',f.input_kind,
      'owner_only',f.owner_only,
      'enabled',f.enabled,
      'reversible',f.reversible,
      'description',f.description,
      'permission_key',coalesce(f.permission_key,''),
      'risk_level',f.risk_level,
      'confirmation_kind',f.confirmation_kind
    ) order by f.feature_id)
    from private.admin_feature_registry_v2 f
    where f.enabled
      and (v_section is null or f.section_key=v_section)
      and (v_q='' or f.title ilike '%'||v_q||'%' or f.route_key ilike '%'||v_q||'%' or f.description ilike '%'||v_q||'%')
  ),'[]'::jsonb);
end $$;

create or replace function public.admin_list_features_v3(p_section_key text default null, p_query text default '')
returns jsonb language sql stable security definer set search_path=''
as $$ select private.admin_list_features_v3_impl(p_section_key,p_query) $$;

revoke all on function public.admin_search_universities_v3(text,integer) from public,anon;
revoke all on function public.admin_list_sections_v3() from public,anon;
revoke all on function public.admin_list_features_v3(text,text) from public,anon;
grant execute on function public.admin_search_universities_v3(text,integer) to authenticated;
grant execute on function public.admin_list_sections_v3() to authenticated;
grant execute on function public.admin_list_features_v3(text,text) to authenticated;

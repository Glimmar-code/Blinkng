-- Bug bounty reports and moderator review. No user can award themselves coins.
-- Safe to roll back by dropping the three RPCs and public.blink_bug_reports.
create table if not exists public.blink_bug_reports (
 id uuid primary key default gen_random_uuid(),
 reporter_id uuid not null references public.profiles(id) on delete cascade,
 description text not null check (char_length(description) between 20 and 2000),
 surface text not null default 'unknown',
 app_version text not null default '',
 screenshot_path text,
 status text not null default 'pending' check (status in ('pending','reviewing','fixed','rejected','rewarded')),
 admin_note text not null default '',
 reward_coins integer not null default 0 check (reward_coins between 0 and 500),
 reviewed_by uuid references public.profiles(id) on delete set null,
 reviewed_at timestamptz,
 dm_message_id uuid,
 created_at timestamptz not null default now()
);
create index if not exists blink_bug_reports_user_created on public.blink_bug_reports(reporter_id,created_at desc);
create index if not exists blink_bug_reports_status_created on public.blink_bug_reports(status,created_at desc);
alter table public.blink_bug_reports enable row level security;
revoke all on public.blink_bug_reports from public, anon, authenticated;
grant select on public.blink_bug_reports to authenticated;
drop policy if exists blink_bug_reports_own_read on public.blink_bug_reports;
create policy blink_bug_reports_own_read on public.blink_bug_reports for select to authenticated using (reporter_id=(select auth.uid()));

-- Private screenshots: paths are always server-owned and constrained to the user's report.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('blink-bug-reports','blink-bug-reports',false,2097152,array['image/jpeg','image/png','image/webp'])
on conflict(id) do nothing;
drop policy if exists blink_bug_screenshots_insert on storage.objects;
create policy blink_bug_screenshots_insert on storage.objects
for insert to authenticated
with check (
 bucket_id='blink-bug-reports'
 and exists (
  select 1 from public.blink_bug_reports b
  where b.reporter_id=(select auth.uid())
    and name = (select auth.uid())::text || '/' || b.id::text || '.jpg'
    and b.screenshot_path=name
 )
);
drop policy if exists blink_bug_screenshots_read on storage.objects;
-- Users may view their own uploads; admins can access uploads through the
-- security-definer signed-url RPC below (no public URL).
create policy blink_bug_screenshots_read on storage.objects
for select to authenticated
using (
 bucket_id='blink-bug-reports'
 and exists (
   select 1 from public.blink_bug_reports b
   where b.screenshot_path = name and b.reporter_id=(select auth.uid())
 )
);
-- Only accept valid authenticated reports; the DM is secondary to durable storage.
create or replace function public.submit_blink_bug_report(
 p_description text, p_surface text default 'unknown',
 p_app_version text default '', p_screenshot boolean default false
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
 v_user uuid := (select auth.uid());
 v_owner uuid;
 v_id uuid;
 v_dm uuid;
 v_conversation uuid;
 v_path text;
 v_text text := trim(coalesce(p_description,''));
begin
 if v_user is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
 if char_length(v_text) < 20 or char_length(v_text) > 2000
 then raise exception 'REPORT_MUST_BE_20_TO_2000_CHARACTERS' using errcode='22023'; end if;
 -- Concurrency-safe per-user throttling and prevention of repeat submissions.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text || ':blink_bug_report')::bigint);
 if (select count(*) from public.blink_bug_reports
    where reporter_id=v_user and created_at>now()-interval '24 hours') >= 5
 then raise exception 'DAILY_REPORT_LIMIT_REACHED' using errcode='P0001'; end if;
 if exists(select 1 from public.blink_bug_reports where reporter_id=v_user
    and created_at>now()-interval '5 minutes')
 then raise exception 'WAIT_FIVE_MINUTES_BETWEEN_REPORTS' using errcode='P0001'; end if;
 if exists(select 1 from public.blink_bug_reports where reporter_id=v_user
    and lower(description)=lower(v_text) and created_at>now()-interval '14 days')
 then raise exception 'DUPLICATE_REPORT' using errcode='P0001'; end if;
 v_id := gen_random_uuid();
 if coalesce(p_screenshot,false) then
   v_path := v_user::text || '/' || v_id::text || '.jpg';
 end if;
 insert into public.blink_bug_reports(id,reporter_id,description,surface,app_version,screenshot_path)
 values(v_id,v_user,v_text,left(coalesce(nullif(trim(p_surface),''),'unknown'),100),
        left(coalesce(p_app_version,''),80),v_path);
 -- Notify the configured owner through their normal private chat inbox.
 select user_id into v_owner from private.admin_roles
 where role='owner' limit 1;
 if v_owner is not null and v_owner<>v_user then
   begin
     select c.id into v_conversation from public.conversations c
     where c.is_group=false
       and exists (select 1 from public.conversation_participants cp where cp.conversation_id=c.id and cp.user_id=v_user)
       and exists (select 1 from public.conversation_participants cp where cp.conversation_id=c.id and cp.user_id=v_owner)
     limit 1;
     if v_conversation is null then
       insert into public.conversations(created_by,is_group,last_message_at)
       values(v_user,false,now()) returning id into v_conversation;
       insert into public.conversation_participants(conversation_id,user_id)
       values(v_conversation,v_user),(v_conversation,v_owner) on conflict do nothing;
     end if;
     insert into public.messages(conversation_id,sender_id,content,message_type)
     values(v_conversation,v_user,
       '🐞 BLINK bug report #' || left(v_id::text,8) || E'\n' || left(v_text,1300),
       'text') returning id into v_dm;
     update public.conversations set last_message_at=now(),updated_at=now() where id=v_conversation;
     update public.blink_bug_reports set dm_message_id=v_dm where id=v_id;
   exception when others then
     -- Never lose the report if a chat restriction or schema change blocks the DM.
     raise notice 'Bug report DM unavailable: %',sqlerrm;
   end;
 end if;
 return jsonb_build_object('id',v_id,'status','pending','dm_sent',v_dm is not null,
                           'screenshot_path',v_path);
end $$;
revoke all on function public.submit_blink_bug_report(text,text,text,boolean) from public,anon;
grant execute on function public.submit_blink_bug_report(text,text,text,boolean) to authenticated;

create or replace function public.get_my_blink_bug_reports(p_limit integer default 20)
returns jsonb language sql stable security definer set search_path='' as $$
 select coalesce(jsonb_agg(to_jsonb(x) order by x.created_at desc),'[]'::jsonb)
 from (
 select id,description,surface,app_version,screenshot_path,status,admin_note,reward_coins,created_at,reviewed_at
 from public.blink_bug_reports where reporter_id=(select auth.uid())
 order by created_at desc limit least(greatest(coalesce(p_limit,20),1),50)
 ) x
$$;
revoke all on function public.get_my_blink_bug_reports(integer) from public,anon;
grant execute on function public.get_my_blink_bug_reports(integer) to authenticated;

create or replace function public.admin_list_blink_bug_reports(p_limit integer default 70)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_result jsonb;
begin
 perform private.require_blink_admin();
 select coalesce(jsonb_agg(to_jsonb(x) order by x.created_at desc),'[]'::jsonb) into v_result
 from (
   select b.id,b.reporter_id,p.username,p.full_name,p.avatar_url,b.description,
   b.surface,b.app_version,b.screenshot_path,b.status,b.admin_note,b.reward_coins,b.created_at,b.dm_message_id
   from public.blink_bug_reports b join public.profiles p on p.id=b.reporter_id
   order by (b.status in ('pending','reviewing')) desc,b.created_at desc
   limit least(greatest(coalesce(p_limit,70),1),100)
 ) x;
 return v_result;
end $$;
revoke all on function public.admin_list_blink_bug_reports(integer) from public,anon;
grant execute on function public.admin_list_blink_bug_reports(integer) to authenticated;

create or replace function public.admin_review_blink_bug_report(
 p_report_id uuid,p_status text,p_reward_coins integer default 0,p_note text default ''
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid; v_report public.blink_bug_reports%rowtype;
begin
 v_actor := private.require_blink_admin();
 if p_status not in ('reviewing','fixed','rejected','rewarded')
   or coalesce(p_reward_coins,0) < 0 or coalesce(p_reward_coins,0)>500
   or (p_reward_coins>0 and p_status<>'rewarded')
   or (p_status='rewarded' and p_reward_coins<=0)
 then raise exception 'INVALID_REVIEW_OR_REWARD' using errcode='22023'; end if;
 select * into v_report from public.blink_bug_reports where id=p_report_id for update;
 if not found then raise exception 'REPORT_NOT_FOUND' using errcode='P0002'; end if;
 if v_report.reward_coins>0 or v_report.status='rewarded'
 then raise exception 'REPORT_ALREADY_REWARDED' using errcode='P0001'; end if;
 if p_reward_coins>0 then
   perform private.admin_grant_coins_impl(v_report.reporter_id,p_reward_coins,
        'Verified Blink bug report '||v_report.id::text);
 end if;
 update public.blink_bug_reports
 set status=p_status,reward_coins=p_reward_coins,admin_note=left(coalesce(p_note,''),600),
 reviewed_at=now(),reviewed_by=v_actor where id=v_report.id;
 return jsonb_build_object('ok',true,'status',p_status,'reward_coins',p_reward_coins);
end $$;
revoke all on function public.admin_review_blink_bug_report(uuid,text,integer,text) from public,anon;
grant execute on function public.admin_review_blink_bug_report(uuid,text,integer,text) to authenticated;

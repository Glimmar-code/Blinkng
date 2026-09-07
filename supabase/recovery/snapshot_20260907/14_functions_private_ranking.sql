-- Exact live private_ranking function definitions captured from Blink Supabase on 2026-09-07.
-- Recovery-only baseline. Do not auto-run against production.

CREATE OR REPLACE FUNCTION private_ranking.active_blink_boost_factor(p_post_id uuid, p_at timestamp with time zone DEFAULT now())
 RETURNS numeric
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select coalesce(max(b.multiplier),1)::numeric
  from public.blink_boosts b
  where b.content_id=p_post_id
    and b.status='ACTIVE'
    and b.starts_at<=p_at
    and b.ends_at>p_at;
$function$

CREATE OR REPLACE FUNCTION private_ranking.apply_target_signal(p_user_id uuid, p_surface text, p_target_type text, p_target_key text, p_delta numeric)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uuid uuid;
  v_value text;
  v_post public.feed_posts%rowtype;
  v_market public.market_items%rowtype;
  v_profile public.profiles%rowtype;
  v_listing public.connect_listings%rowtype;
  v_roommate public.roommate_profiles%rowtype;
  v_mentor public.mentor_profiles%rowtype;
  v_reading public.reading_mate_profiles%rowtype;
  v_housing public.housing_requests%rowtype;
begin
  if p_user_id is null or coalesce(p_delta,0) = 0 then return; end if;
  if p_target_type = 'game' then
    if lower(btrim(p_target_key)) in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge') then
      perform private_ranking.bump_interest(p_user_id,'game','game_type',lower(btrim(p_target_key)),p_delta);
    end if;
    return;
  end if;
  begin v_uuid := p_target_key::uuid; exception when invalid_text_representation then return; end;
  case p_target_type
    when 'post' then
      select * into v_post from public.feed_posts where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'author',v_post.user_id::text,p_delta*0.65);
      perform private_ranking.bump_interest(p_user_id,p_surface,'category',v_post.category,p_delta);
      perform private_ranking.bump_interest(p_user_id,p_surface,'faculty',v_post.faculty,p_delta*0.45);
      perform private_ranking.bump_interest(p_user_id,p_surface,'content_type',v_post.type,p_delta*0.35);
      for v_value in select distinct lower(btrim(x)) from unnest(coalesce(v_post.tags,'{}'::text[])||coalesce(v_post.hashtags,'{}'::text[])) x where nullif(btrim(x),'') is not null limit 20 loop
        perform private_ranking.bump_interest(p_user_id,p_surface,'tag',v_value,p_delta*0.4);
      end loop;
    when 'market_item' then
      select * into v_market from public.market_items where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'category',v_market.category,p_delta);
      perform private_ranking.bump_interest(p_user_id,p_surface,'location',v_market.location,p_delta*0.5);
      perform private_ranking.bump_interest(p_user_id,p_surface,'university',v_market.university,p_delta*0.4);
      perform private_ranking.bump_interest(p_user_id,p_surface,'seller',v_market.seller_id::text,p_delta*0.3);
    when 'profile' then
      select * into v_profile from public.profiles where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'university',v_profile.university,p_delta*0.7);
      perform private_ranking.bump_interest(p_user_id,p_surface,'faculty',v_profile.faculty,p_delta*0.5);
      perform private_ranking.bump_interest(p_user_id,p_surface,'department',v_profile.department,p_delta);
      perform private_ranking.bump_interest(p_user_id,p_surface,'academic_level',v_profile.academic_level,p_delta*0.4);
      for v_value in select distinct lower(btrim(x)) from unnest(coalesce(v_profile.core_skills,'{}'::text[])||coalesce(v_profile.hobbies,'{}'::text[])||coalesce(v_profile.languages,'{}'::text[])) x where nullif(btrim(x),'') is not null limit 30 loop
        perform private_ranking.bump_interest(p_user_id,p_surface,'profile_interest',v_value,p_delta*0.35);
      end loop;
    when 'connect_listing' then
      select * into v_listing from public.connect_listings where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'listing_type',v_listing.listing_type,p_delta);
      perform private_ranking.bump_interest(p_user_id,p_surface,'university',v_listing.university,p_delta*0.6);
      perform private_ranking.bump_interest(p_user_id,p_surface,'department',v_listing.department,p_delta*0.7);
      perform private_ranking.bump_interest(p_user_id,p_surface,'location',v_listing.location,p_delta*0.45);
      for v_value in select distinct lower(btrim(x)) from unnest(coalesce(v_listing.tags,'{}'::text[])||coalesce(v_listing.subjects,'{}'::text[])) x where nullif(btrim(x),'') is not null limit 25 loop
        perform private_ranking.bump_interest(p_user_id,p_surface,'topic',v_value,p_delta*0.45);
      end loop;
    when 'roommate' then
      select * into v_roommate from public.roommate_profiles where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'location',v_roommate.location,p_delta);
      perform private_ranking.bump_interest(p_user_id,p_surface,'room_type',v_roommate.room_type,p_delta*0.7);
    when 'mentor' then
      select * into v_mentor from public.mentor_profiles where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'mentor_mode',v_mentor.mode,p_delta*0.6);
      for v_value in select distinct lower(btrim(x)) from unnest(coalesce(v_mentor.subjects,'{}'::text[])) x where nullif(btrim(x),'') is not null limit 20 loop perform private_ranking.bump_interest(p_user_id,p_surface,'topic',v_value,p_delta*0.6); end loop;
    when 'reading_mate' then
      select * into v_reading from public.reading_mate_profiles where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'study_style',v_reading.study_style,p_delta*0.7);
      perform private_ranking.bump_interest(p_user_id,p_surface,'location',v_reading.preferred_location,p_delta*0.5);
      for v_value in select distinct lower(btrim(x)) from unnest(coalesce(v_reading.courses,'{}'::text[])) x where nullif(btrim(x),'') is not null limit 20 loop perform private_ranking.bump_interest(p_user_id,p_surface,'course',v_value,p_delta*0.7); end loop;
    when 'housing' then
      select * into v_housing from public.housing_requests where id=v_uuid; if not found then return; end if;
      perform private_ranking.bump_interest(p_user_id,p_surface,'location',v_housing.preferred_location,p_delta);
  end case;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.assign_creator_post_number()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if new.creator_post_number is null or new.creator_post_number<=0 then
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.user_id::text,0));
    select coalesce(max(fp.creator_post_number),0)+1 into new.creator_post_number from public.feed_posts fp where fp.user_id=new.user_id;
  end if;
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.bump_interest(p_user_id uuid, p_surface text, p_feature_type text, p_feature_value text, p_delta numeric)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_surface text:=lower(btrim(coalesce(p_surface,'')));
  v_type text:=lower(btrim(coalesce(p_feature_type,'')));
  v_value text:=lower(left(btrim(coalesce(p_feature_value,'')),160));
  v_delta numeric:=greatest(-12::numeric,least(12::numeric,coalesce(p_delta,0)));
begin
  if p_user_id is null or v_delta=0 or v_type='' or v_value='' then return; end if;
  if v_surface not in ('feed','reels','market','search','connect','game') then return; end if;
  insert into public.user_interest_weights(user_id,surface,feature_type,feature_value,weight,updated_at)
  values(p_user_id,v_surface,left(v_type,40),v_value,greatest(-50::numeric,least(50::numeric,v_delta)),now())
  on conflict(user_id,surface,feature_type,feature_value) do update set
    weight=greatest(-50::numeric,least(50::numeric,public.user_interest_weights.weight*power(0.5::numeric,greatest(0::numeric,extract(epoch from(now()-public.user_interest_weights.updated_at))/1209600::numeric))+excluded.weight)),updated_at=now();
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.capture_native_signal()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_user uuid; v_target text; v_surface text; v_delta numeric; v_post_id uuid; v_game_type text; v_score integer;
begin
  if tg_table_name='post_likes' then if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id; else v_user:=new.user_id; v_post_id:=new.post_id; end if; v_delta:=case when tg_op='DELETE' then -3 else 3 end;
  elsif tg_table_name='comments' then if tg_op='DELETE' then v_user:=old.author_id; v_post_id:=old.post_id; else v_user:=new.author_id; v_post_id:=new.post_id; end if; v_delta:=case when tg_op='DELETE' then -4 else 4 end;
  elsif tg_table_name='post_bookmarks' then if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id; else v_user:=new.user_id; v_post_id:=new.post_id; end if; v_delta:=case when tg_op='DELETE' then -4 else 4 end;
  elsif tg_table_name='post_shares' then if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id; else v_user:=new.user_id; v_post_id:=new.post_id; end if; v_delta:=case when tg_op='DELETE' then -5 else 5 end;
  elsif tg_table_name='marketplace_wishlist' then if tg_op='DELETE' then v_user:=old.user_id; v_target:=old.item_id::text; else v_user:=new.user_id; v_target:=new.item_id::text; end if; v_delta:=case when tg_op='DELETE' then -4 else 4 end; perform private_ranking.apply_target_signal(v_user,'market','market_item',v_target,v_delta); if tg_op='DELETE' then return old; else return new; end if;
  elsif tg_table_name='game_sessions' then if tg_op='DELETE' then v_user:=old.user_id; v_game_type:=old.game_type; v_score:=old.score; else v_user:=new.user_id; v_game_type:=new.game_type; v_score:=new.score; end if; v_delta:=least(4::numeric,1::numeric+greatest(0,coalesce(v_score,0))::numeric/200::numeric); if tg_op='DELETE' then v_delta:=-v_delta; end if; perform private_ranking.apply_target_signal(v_user,'game','game',v_game_type,v_delta); if tg_op='DELETE' then return old; else return new; end if;
  else if tg_op='DELETE' then return old; else return new; end if; end if;
  select case when fp.is_reel or nullif(fp.video_url,'') is not null then 'reels' else 'feed' end into v_surface from public.feed_posts fp where fp.id=v_post_id;
  if v_surface is not null then perform private_ranking.apply_target_signal(v_user,v_surface,'post',v_post_id::text,v_delta); perform private_ranking.refresh_discovery_interest_cache(v_user); end if;
  if tg_op='DELETE' then return old; else return new; end if;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.capture_recommendation_event()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_delta numeric:=case new.event_type when 'open' then 0.5 when 'click' then 0.75 when 'dwell' then case when new.dwell_ms>=3000 then 3 else 0 end when 'skip' then case when new.dwell_ms<1500 then -8 else -3 end when 'hide' then -10 when 'report' then -12 when 'purchase' then 6 when 'apply' then 5 when 'connect' then 5 when 'challenge' then 4 when 'complete' then 3 else 0 end;
  v_completion numeric; v_post_id uuid;
begin
  if v_delta<>0 then perform private_ranking.apply_target_signal(new.user_id,new.surface,new.target_type,new.target_key,v_delta); if new.surface in ('feed','reels') and new.target_type='post' then perform private_ranking.refresh_discovery_interest_cache(new.user_id); end if; end if;
  if new.target_type='post' and new.metadata ? 'completion_rate' then
    begin v_completion:=nullif(new.metadata->>'completion_rate','')::numeric; v_post_id:=new.target_key::uuid; if v_completion is not null then perform private_ranking.record_reel_completion(v_post_id,v_completion); end if; exception when invalid_text_representation or numeric_value_out_of_range then null; end;
  end if;
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.get_discovery_feed(p_limit integer DEFAULT 40, p_offset integer DEFAULT 0, p_as_of timestamp with time zone DEFAULT now(), p_gravity numeric DEFAULT 1.5, p_w1 numeric DEFAULT 2.0, p_w2 numeric DEFAULT 1.0, p_w3 numeric DEFAULT 250.0, p_w4 numeric DEFAULT 4.0)
 RETURNS TABLE(item jsonb, feed_score numeric, ranking_components jsonb, feed_position integer, as_of timestamp with time zone, next_offset integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user uuid:=auth.uid(); v_limit integer:=greatest(1,least(coalesce(p_limit,40),100)); v_offset integer:=greatest(0,coalesce(p_offset,0)); v_target_count integer; v_candidate_cap integer; v_position integer; v_want_reel boolean; v_want_tier text; v_pick record; v_last_creator_1 uuid; v_last_creator_2 uuid; v_viewer_location extensions.geography; v_positive_interests text[]:='{}'::text[]; v_negative_interests text[]:='{}'::text[]; v_positive_creators uuid[]:='{}'::uuid[]; v_negative_creators uuid[]:='{}'::uuid[]; v_tag_weights jsonb:='{}'::jsonb; v_creator_weights jsonb:='{}'::jsonb; v_feed_type text:=lower(coalesce(nullif(current_setting('blink.feed_type',true),''),'all'));
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_as_of is null then p_as_of:=now(); end if; if p_gravity<0.5 or p_gravity>3.0 then raise exception 'INVALID_GRAVITY'; end if; if least(p_w1,p_w2,p_w3,p_w4)<0 then raise exception 'INVALID_WEIGHT'; end if; if v_offset>5000 then raise exception 'OFFSET_TOO_LARGE'; end if; if v_feed_type not in ('all','posts','reels') then v_feed_type:='all'; end if;
  v_target_count:=least(5100,v_offset+v_limit); v_candidate_cap:=least(10000,greatest(250,v_target_count*20));
  select g.location into v_viewer_location from private_ranking.discovery_user_geo g where g.user_id=v_user;
  select c.positive_interests,c.negative_interests,c.positive_creators,c.negative_creators,c.tag_weights,c.creator_weights into v_positive_interests,v_negative_interests,v_positive_creators,v_negative_creators,v_tag_weights,v_creator_weights from private_ranking.discovery_interest_cache c where c.user_id=v_user;
  v_positive_interests:=coalesce(v_positive_interests,'{}'::text[]); v_negative_interests:=coalesce(v_negative_interests,'{}'::text[]); v_positive_creators:=coalesce(v_positive_creators,'{}'::uuid[]); v_negative_creators:=coalesce(v_negative_creators,'{}'::uuid[]); v_tag_weights:=coalesce(v_tag_weights,'{}'::jsonb); v_creator_weights:=coalesce(v_creator_weights,'{}'::jsonb);
  drop table if exists pg_temp.discovery_candidates;
  create temporary table discovery_candidates(post_id uuid primary key,creator_id uuid not null,is_reel boolean not null,creator_tier text not null,feed_score numeric not null,completion_rate numeric not null,verification_multiplier numeric not null,cold_start_multiplier numeric not null,affinity_component numeric not null,proximity_component numeric not null,created_at timestamptz not null) on commit drop;
  drop table if exists pg_temp.discovery_selected;
  create temporary table discovery_selected(position integer primary key,post_id uuid unique not null) on commit drop;
  insert into pg_temp.discovery_candidates(post_id,creator_id,is_reel,creator_tier,feed_score,completion_rate,verification_multiplier,cold_start_multiplier,affinity_component,proximity_component,created_at)
  with raw as (
    select fp.id post_id,fp.user_id creator_id,fp.is_reel,fp.creator_post_number,greatest(fp.created_at,coalesce(rr.created_at,fp.created_at)) created_at,pr.created_at creator_created_at,greatest(coalesce(pr.daily_streak,0),0) creator_streak,greatest(coalesce(fp.like_count,0),0) likes,greatest(coalesce(fp.comment_count,0),0) comments,greatest(coalesce(fp.share_count,0),0) shares,greatest(coalesce(fp.repost_count,0),0) reposts,greatest(coalesce(fp.view_count,0),0) views,coalesce(rm.completion_rate,0)::numeric completion_rate,coalesce(vm.multiplier,1)::numeric verification_multiplier,(rr.id is not null) has_followed_repost,array(select distinct lower(btrim(x)) from unnest(coalesce(fp.tags,'{}'::text[])||coalesce(fp.hashtags,'{}'::text[])||array[coalesce(fp.category,'')]) x where nullif(btrim(x),'') is not null) labels,case when v_viewer_location is not null and cg.location is not null then extensions.st_distance(v_viewer_location,cg.location) else null end distance_meters
    from public.feed_posts fp join public.profiles pr on pr.id=fp.user_id left join private_ranking.discovery_reel_metrics rm on rm.post_id=fp.id left join private_ranking.discovery_user_geo cg on cg.user_id=fp.user_id left join private_ranking.discovery_verification_multipliers vm on vm.tier=case upper(coalesce(pr.verification_badge,'NONE')) when 'GOLD' then 'gold'::public.discovery_verification_tier_enum when 'BLUE' then 'blue'::public.discovery_verification_tier_enum else 'standard'::public.discovery_verification_tier_enum end
    left join lateral(select r.id,r.user_id,r.created_at from public.post_reposts r where r.post_id=fp.id and r.user_id<>v_user and exists(select 1 from public.follows f where f.follower_id=v_user and f.following_id=r.user_id) and not exists(select 1 from public.blocks b where (b.blocker_id=v_user and b.blocked_id=r.user_id) or (b.blocker_id=r.user_id and b.blocked_id=v_user)) and not exists(select 1 from public.muted_users mu where mu.user_id=v_user and mu.muted_id=r.user_id) order by r.created_at desc limit 1) rr on true
    where fp.is_active=true and fp.is_flagged=false and (v_feed_type='all' or (v_feed_type='posts' and not coalesce(fp.is_reel,false)) or (v_feed_type='reels' and coalesce(fp.is_reel,false))) and (lower(coalesce(fp.audience,'everyone'))='everyone' or fp.user_id=v_user) and not exists(select 1 from public.blocks b where (b.blocker_id=v_user and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=v_user)) and not exists(select 1 from public.muted_users mu where mu.user_id=v_user and mu.muted_id=fp.user_id) and greatest(fp.created_at,coalesce(rr.created_at,fp.created_at))>p_as_of-interval '7 days' and greatest(fp.created_at,coalesce(rr.created_at,fp.created_at))<=p_as_of and (fp.expires_at is null or fp.expires_at>p_as_of) and not exists(select 1 from public.feed_preferences pref where pref.user_id=v_user and pref.post_id=fp.id and lower(pref.preference) in ('hide','hidden','not_interested','not interested'))
  ), components as (
    select r.*,ln((r.creator_streak+1)::numeric) streak_component,(r.likes::numeric+r.comments::numeric*3+r.shares::numeric*5+r.reposts::numeric*5+r.views::numeric*0.1) virality_component,case when r.distance_meters is null then 0::numeric else (1.0/greatest(r.distance_meters,25.0))::numeric end proximity_component,least(32::numeric,greatest(-20::numeric,cardinality(array(select a from unnest(r.labels)a intersect select b from unnest(v_positive_interests)b))::numeric*1.5-cardinality(array(select a from unnest(r.labels)a intersect select b from unnest(v_negative_interests)b))::numeric*6+coalesce((v_creator_weights->>r.creator_id::text)::numeric,0)+case when r.has_followed_repost then 12::numeric else 0::numeric end)) affinity_component,greatest(0::numeric,extract(epoch from(p_as_of-r.created_at))::numeric/3600::numeric) age_hours,case when r.creator_post_number<=5 then 2::numeric else 1::numeric end cold_start_multiplier,case when r.creator_created_at>=p_as_of-interval '30 days' or r.creator_post_number<=10 then 'new' else 'established' end creator_tier from raw r
  ), scored as (
    select c.*,((p_w1*c.streak_component+p_w2*c.virality_component+p_w3*c.proximity_component+p_w4*c.affinity_component)/power(c.age_hours+2::numeric,p_gravity))*c.verification_multiplier*c.cold_start_multiplier*private_ranking.active_blink_boost_factor(c.post_id,p_as_of)+private_ranking.viewer_personalization_bonus(v_user,c.post_id,c.creator_id,p_as_of)+case when private.is_blink_owner_id(c.creator_id) then 1000000000::numeric else 0::numeric end final_score from components c
  ) select s.post_id,s.creator_id,s.is_reel,s.creator_tier,s.final_score,s.completion_rate,s.verification_multiplier,s.cold_start_multiplier,s.affinity_component,s.proximity_component,s.created_at from scored s order by s.final_score desc,s.created_at desc,s.post_id desc limit v_candidate_cap;
  for v_position in 1..v_target_count loop
    v_want_reel:=case when v_feed_type='reels' then true when v_feed_type='posts' then false else mod(v_position,4)=0 end; v_want_tier:=case when mod(v_position-1,10) in(2,5,8) then 'new' else 'established' end;
    select c.* into v_pick from pg_temp.discovery_candidates c where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id) and c.is_reel=v_want_reel and c.creator_tier=v_want_tier and not(v_last_creator_1 is not null and v_last_creator_2=v_last_creator_1 and c.creator_id=v_last_creator_1) order by c.feed_score desc,c.created_at desc,c.post_id desc limit 1;
    if not found then select c.* into v_pick from pg_temp.discovery_candidates c where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id) and c.is_reel=v_want_reel and not(v_last_creator_1 is not null and v_last_creator_2=v_last_creator_1 and c.creator_id=v_last_creator_1) order by c.feed_score desc,c.created_at desc,c.post_id desc limit 1; end if;
    if not found then select c.* into v_pick from pg_temp.discovery_candidates c where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id) and c.creator_tier=v_want_tier and not(v_last_creator_1 is not null and v_last_creator_2=v_last_creator_1 and c.creator_id=v_last_creator_1) order by c.feed_score desc,c.created_at desc,c.post_id desc limit 1; end if;
    if not found then select c.* into v_pick from pg_temp.discovery_candidates c where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id) and not(v_last_creator_1 is not null and v_last_creator_2=v_last_creator_1 and c.creator_id=v_last_creator_1) order by c.feed_score desc,c.created_at desc,c.post_id desc limit 1; end if;
    if not found then exit; end if;
    insert into pg_temp.discovery_selected(position,post_id) values(v_position,v_pick.post_id); v_last_creator_2:=v_last_creator_1; v_last_creator_1:=v_pick.creator_id;
  end loop;
  return query select to_jsonb(fp)||jsonb_build_object('repost_id',dist.id,'reposted_by_id',dist.user_id,'reposted_by_username',dist.username,'repost_count',coalesce(fp.repost_count,0),'is_reposted_by_me',exists(select 1 from public.post_reposts mine where mine.post_id=fp.id and mine.user_id=v_user)) item,c.feed_score,jsonb_build_object('model','weighted_decay_v1','format',case when c.is_reel then 'reel' else 'post' end,'creator_tier',c.creator_tier,'verification_multiplier',c.verification_multiplier,'cold_start_boost',c.cold_start_multiplier,'reel_completion_boost',1,'proximity_applied',c.proximity_component>0,'affinity_direction',case when c.affinity_component>0 then 'positive' when c.affinity_component<0 then 'negative' else 'neutral' end,'repost_distribution_boost',dist.id is not null) ranking_components,s.position feed_position,p_as_of as_of,(v_offset+count(*) over())::integer next_offset from pg_temp.discovery_selected s join pg_temp.discovery_candidates c on c.post_id=s.post_id join public.feed_posts fp on fp.id=s.post_id left join lateral(select r.id,r.user_id,rp.username,r.created_at from public.post_reposts r join public.profiles rp on rp.id=r.user_id where r.post_id=fp.id and r.user_id<>v_user and exists(select 1 from public.follows f where f.follower_id=v_user and f.following_id=r.user_id) and not exists(select 1 from public.blocks b where (b.blocker_id=v_user and b.blocked_id=r.user_id) or (b.blocker_id=r.user_id and b.blocked_id=v_user)) and not exists(select 1 from public.muted_users mu where mu.user_id=v_user and mu.muted_id=r.user_id) order by r.created_at desc limit 1) dist on true where s.position>v_offset and s.position<=v_offset+v_limit order by s.position;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.prune_old_events()
 RETURNS bigint
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
  with deleted as (delete from public.recommendation_events where created_at<now()-interval '180 days' returning 1) select count(*)::bigint from deleted;
$function$

CREATE OR REPLACE FUNCTION private_ranking.record_recommendation_event(p_surface text, p_target_type text, p_target_key text, p_event_type text, p_dwell_ms integer DEFAULT 0, p_session_id uuid DEFAULT NULL::uuid, p_metadata jsonb DEFAULT '{}'::jsonb)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_user uuid:=auth.uid(); v_surface text:=lower(btrim(coalesce(p_surface,''))); v_target_type text:=lower(btrim(coalesce(p_target_type,''))); v_target_key text:=left(btrim(coalesce(p_target_key,'')),128); v_event_type text:=lower(btrim(coalesce(p_event_type,''))); v_uuid uuid; v_id bigint;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if; if v_surface not in('feed','reels','market','search','connect','game') then raise exception 'INVALID_RECOMMENDATION_SURFACE'; end if; if v_target_type not in('post','market_item','profile','connect_listing','roommate','mentor','reading_mate','housing','game') then raise exception 'INVALID_RECOMMENDATION_TARGET'; end if; if v_event_type not in('impression','open','click','dwell','skip','hide','report','purchase','apply','connect','challenge','complete') then raise exception 'INVALID_RECOMMENDATION_EVENT'; end if; if v_target_key='' then raise exception 'INVALID_RECOMMENDATION_TARGET'; end if; if octet_length(coalesce(p_metadata,'{}'::jsonb)::text)>4096 then raise exception 'RECOMMENDATION_METADATA_TOO_LARGE'; end if;
  if(select count(*) from public.recommendation_events e where e.user_id=v_user and e.created_at>now()-interval '1 minute')>=180 then raise exception 'RECOMMENDATION_EVENT_RATE_LIMITED'; end if;
  if v_target_type<>'game' then begin v_uuid:=v_target_key::uuid; exception when invalid_text_representation then raise exception 'INVALID_RECOMMENDATION_TARGET'; end; end if;
  if v_target_type='post' and not exists(select 1 from public.feed_posts where id=v_uuid and(is_active or user_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='market_item' and not exists(select 1 from public.market_items where id=v_uuid and(seller_id=v_user or(status='active' and not is_sold))) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='profile' and not exists(select 1 from public.profiles where id=v_uuid) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='connect_listing' and not exists(select 1 from public.connect_listings where id=v_uuid and(is_active or user_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='roommate' and not exists(select 1 from public.roommate_profiles where id=v_uuid and(is_active or user_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='mentor' and not exists(select 1 from public.mentor_profiles where id=v_uuid and(is_active or user_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='reading_mate' and not exists(select 1 from public.reading_mate_profiles where id=v_uuid and(is_active or user_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='housing' and not exists(select 1 from public.housing_requests where id=v_uuid and(status='open' or student_id=v_user)) then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND';
  elsif v_target_type='game' and v_target_key not in('brain_mix','math_sprint','logic','memory','word_power','general_knowledge') then raise exception 'RECOMMENDATION_TARGET_NOT_FOUND'; end if;
  if v_event_type in('impression','open','click','skip') then select e.id into v_id from public.recommendation_events e where e.user_id=v_user and e.surface=v_surface and e.target_type=v_target_type and e.target_key=v_target_key and e.event_type=v_event_type and e.created_at>now()-interval '15 seconds' order by e.created_at desc limit 1; if v_id is not null then return v_id; end if; end if;
  insert into public.recommendation_events(user_id,surface,target_type,target_key,event_type,dwell_ms,session_id,metadata) values(v_user,v_surface,v_target_type,v_target_key,v_event_type,greatest(0,least(coalesce(p_dwell_ms,0),600000)),p_session_id,jsonb_strip_nulls(coalesce(p_metadata,'{}'::jsonb))) returning id into v_id; return v_id;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.record_reel_completion(p_post_id uuid, p_completion_rate numeric)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_rate numeric:=greatest(0::numeric,least(1::numeric,coalesce(p_completion_rate,0)));
begin
  if p_post_id is null then return; end if; if not exists(select 1 from public.feed_posts fp where fp.id=p_post_id and fp.is_reel=true) then return; end if;
  insert into private_ranking.discovery_reel_metrics(post_id,completion_samples,completion_rate,updated_at) values(p_post_id,1,v_rate,now()) on conflict(post_id) do update set completion_rate=(private_ranking.discovery_reel_metrics.completion_rate*private_ranking.discovery_reel_metrics.completion_samples+excluded.completion_rate)/(private_ranking.discovery_reel_metrics.completion_samples+1),completion_samples=private_ranking.discovery_reel_metrics.completion_samples+1,updated_at=now();
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.refresh_discovery_interest_cache(p_user_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_positive text[]:='{}'::text[]; v_negative text[]:='{}'::text[]; v_positive_creators uuid[]:='{}'::uuid[]; v_negative_creators uuid[]:='{}'::uuid[]; v_tag_weights jsonb:='{}'::jsonb; v_creator_weights jsonb:='{}'::jsonb;
begin
  if p_user_id is null then return; end if;
  select coalesce(array(select w.feature_value from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type in('tag','category','faculty','content_type') and w.weight>=0.75 order by w.weight desc,w.updated_at desc limit 64),'{}'::text[]) into v_positive;
  select coalesce(array(select w.feature_value from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type in('tag','category','faculty','content_type') and w.weight<=-0.75 order by w.weight asc,w.updated_at desc limit 64),'{}'::text[]) into v_negative;
  select coalesce(array(select w.feature_value::uuid from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type='author' and w.weight>=0.75 and w.feature_value~*'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' order by w.weight desc,w.updated_at desc limit 64),'{}'::uuid[]) into v_positive_creators;
  select coalesce(array(select w.feature_value::uuid from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type='author' and w.weight<=-0.75 and w.feature_value~*'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' order by w.weight asc,w.updated_at desc limit 64),'{}'::uuid[]) into v_negative_creators;
  select coalesce(jsonb_object_agg(x.feature_value,x.weight),'{}'::jsonb) into v_tag_weights from(select w.feature_value,w.weight from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type in('tag','category','faculty','content_type') order by abs(w.weight) desc,w.updated_at desc limit 128)x;
  select coalesce(jsonb_object_agg(x.feature_value,x.weight),'{}'::jsonb) into v_creator_weights from(select w.feature_value,w.weight from public.user_interest_weights w where w.user_id=p_user_id and w.surface in('feed','reels') and w.feature_type='author' and w.feature_value~*'^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' order by abs(w.weight) desc,w.updated_at desc limit 128)x;
  insert into private_ranking.discovery_interest_cache(user_id,positive_interests,negative_interests,positive_creators,negative_creators,tag_weights,creator_weights,updated_at) values(p_user_id,v_positive,v_negative,v_positive_creators,v_negative_creators,v_tag_weights,v_creator_weights,now()) on conflict(user_id) do update set positive_interests=excluded.positive_interests,negative_interests=excluded.negative_interests,positive_creators=excluded.positive_creators,negative_creators=excluded.negative_creators,tag_weights=excluded.tag_weights,creator_weights=excluded.creator_weights,updated_at=now();
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.set_discovery_location(p_latitude double precision, p_longitude double precision, p_accuracy_meters numeric DEFAULT NULL::numeric)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_user uuid:=auth.uid();
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if; if p_latitude is null or p_latitude<-90 or p_latitude>90 then raise exception 'INVALID_LATITUDE'; end if; if p_longitude is null or p_longitude<-180 or p_longitude>180 then raise exception 'INVALID_LONGITUDE'; end if;
  insert into private_ranking.discovery_user_geo(user_id,location,accuracy_meters,updated_at) values(v_user,extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,case when p_accuracy_meters is null then null else greatest(0,p_accuracy_meters) end,now()) on conflict(user_id) do update set location=excluded.location,accuracy_meters=excluded.accuracy_meters,updated_at=now(); return true;
end;
$function$

CREATE OR REPLACE FUNCTION private_ranking.viewer_personalization_bonus(p_viewer uuid, p_post uuid, p_creator uuid, p_as_of timestamp with time zone)
 RETURNS numeric
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with viewer as(select id,university,faculty,department from public.profiles where id=p_viewer),creator as(select id,university,faculty,department from public.profiles where id=p_creator),post as(select id,user_id,category,faculty,type,tags,hashtags,coalesce(is_reel,false)is_reel from public.feed_posts where id=p_post),affinity as(select least(18::numeric,coalesce(sum(signal),0::numeric))score from(select count(*)::numeric*2.5 signal from public.post_likes l join public.feed_posts fp on fp.id=l.post_id where l.user_id=p_viewer and fp.user_id=p_creator and l.created_at>=coalesce(p_as_of,now())-interval '90 days' union all select count(*)::numeric*4 from public.post_bookmarks b join public.feed_posts fp on fp.id=b.post_id where b.user_id=p_viewer and fp.user_id=p_creator and b.created_at>=coalesce(p_as_of,now())-interval '90 days' union all select count(*)::numeric*4 from public.comments c join public.feed_posts fp on fp.id=c.post_id where c.author_id=p_viewer and fp.user_id=p_creator and c.created_at>=coalesce(p_as_of,now())-interval '90 days' union all select least(8::numeric,coalesce(sum(greatest(v.impression_count,1)),0)::numeric*0.35) from public.post_views v join public.feed_posts fp on fp.id=v.post_id where v.viewer_id=p_viewer and fp.user_id=p_creator and v.last_viewed_at>=coalesce(p_as_of,now())-interval '45 days')s),interest as(select least(15::numeric,coalesce(sum(greatest(iw.weight,0)),0::numeric)*0.75)score from public.user_interest_weights iw cross join post p where iw.user_id=p_viewer and iw.surface=case when p.is_reel then 'reels' else 'feed' end and((iw.feature_type='author' and iw.feature_value=p_creator::text)or(iw.feature_type='category' and iw.feature_value=lower(btrim(coalesce(p.category,''))))or(iw.feature_type='faculty' and iw.feature_value=lower(btrim(coalesce(p.faculty,''))))or(iw.feature_type='content_type' and iw.feature_value=lower(btrim(coalesce(p.type,''))))or(iw.feature_type='tag' and iw.feature_value=any(array(select lower(btrim(x)) from unnest(coalesce(p.tags,'{}'::text[])||coalesce(p.hashtags,'{}'::text[]))x where nullif(btrim(x),'') is not null))))),seen as(select coalesce(sum(greatest(v.impression_count,1)),0)::numeric impressions from public.post_views v where v.viewer_id=p_viewer and v.post_id=p_post and v.last_viewed_at>=coalesce(p_as_of,now())-interval '14 days'),parts as(select case when exists(select 1 from public.follows f where f.follower_id=p_viewer and f.following_id=p_creator)then 18::numeric else 0::numeric end follow_score,case when nullif(lower(btrim(v.university)),'') is not null and nullif(lower(btrim(v.university)),'')=nullif(lower(btrim(c.university)),'') then 8::numeric else 0::numeric end+case when nullif(lower(btrim(v.faculty)),'') is not null and nullif(lower(btrim(v.faculty)),'')=nullif(lower(btrim(c.faculty)),'') then 3::numeric else 0::numeric end+case when nullif(lower(btrim(v.department)),'') is not null and nullif(lower(btrim(v.department)),'')=nullif(lower(btrim(c.department)),'') then 5::numeric else 0::numeric end campus_score,a.score affinity_score,i.score interest_score,case when s.impressions=0 then 4::numeric else 0::numeric end unseen_bonus,least(15::numeric,s.impressions*1.5) exposure_penalty,(abs(mod(hashtextextended(p_post::text||':'||p_viewer::text||':'||floor(extract(epoch from coalesce(p_as_of,now()))/21600)::bigint::text,0),10000))::numeric/10000::numeric)*8::numeric exploration_score,case when p_viewer=p_creator then 3::numeric else 0::numeric end self_penalty from viewer v cross join creator c cross join affinity a cross join interest i cross join seen s)
select coalesce(follow_score+campus_score+affinity_score+interest_score+unseen_bonus+exploration_score-exposure_penalty-self_penalty,0::numeric) from parts;
$function$

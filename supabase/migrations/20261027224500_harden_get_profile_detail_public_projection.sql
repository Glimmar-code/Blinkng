-- destructive-change-reviewed: security-tightening only. This RPC intentionally
-- stops returning internal profile columns to other authenticated users.
-- rollback-plan: restore the previous audited get_profile_detail function from
-- the migration version before this change; it must not be used for public reads.
-- No tables or rows are modified by this migration.
begin;

create or replace function public.get_profile_detail(p_identifier text)
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare
  v_viewer uuid := auth.uid();
  v_profile public.profiles%rowtype;
  v_is_following boolean := false;
  v_identifier text := lower(ltrim(trim(coalesce(p_identifier,'')), '@'));
  v_can_presence boolean := false;
begin
  if v_viewer is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier = '' then return null; end if;
  select * into v_profile from public.profiles
    where lower(username) = v_identifier or id::text = v_identifier
    limit 1;
  if not found then return null; end if;
  if exists (select 1 from public.blocks b
      where (b.blocker_id = v_viewer and b.blocked_id = v_profile.id)
         or (b.blocker_id = v_profile.id and b.blocked_id = v_viewer)) then
    return null;
  end if;
  select exists (select 1 from public.follows f
      where f.follower_id = v_viewer and f.following_id = v_profile.id)
    into v_is_following;
  v_can_presence := v_viewer = v_profile.id or (
    coalesce((select s.show_online_status from public.user_settings s
      where s.user_id = v_profile.id), true)
    and (v_profile.presence_visibility = 'PUBLIC'
      or (v_profile.presence_visibility = 'FOLLOWERS' and v_is_following)));

  -- Strictly allowlisted public-profile fields. Deliberately excludes:
  -- seller_payment_reference, tokens, onboarding progress, internal status,
  -- market approval, wallet, billing and other private profile columns.
  return jsonb_build_object(
      'id', v_profile.id,
      'username', v_profile.username,
      'full_name', v_profile.full_name,
      'name', v_profile.name,
      'avatar_url', v_profile.avatar_url,
      'cover_photo', v_profile.cover_photo,
      'cover_photo_url', v_profile.cover_photo_url,
      'profile_theme', v_profile.profile_theme,
      'professional_headline', v_profile.professional_headline,
      'current_job_title', v_profile.current_job_title,
      'university', v_profile.university,
      'account_type', v_profile.account_type,
      'faculty', v_profile.faculty,
      'department', v_profile.department,
      'course_of_study', v_profile.course_of_study,
      'academic_level', v_profile.academic_level,
      'graduation_year', v_profile.graduation_year,
      'bio', v_profile.bio,
      'availability', v_profile.availability,
      'core_skills', v_profile.core_skills,
      'hobbies', v_profile.hobbies,
      'languages', v_profile.languages,
      'interests', v_profile.interests,
      'skill_endorsements', v_profile.skill_endorsements,
      'badges', v_profile.badges,
      'favorite_quote', v_profile.favorite_quote,
      'website', v_profile.website,
      'linkedin', v_profile.linkedin,
      'twitter', v_profile.twitter,
      'instagram', v_profile.instagram,
      'featured_link', v_profile.featured_link,
      'featured_link_label', v_profile.featured_link_label,
      'verification_badge', v_profile.verification_badge,
      'verification_tier', v_profile.verification_tier,
      'is_verified', v_profile.is_verified,
      'verified_at', v_profile.verified_at,
      'verification_expires_at', v_profile.verification_expires_at,
      'blink_vip_until', v_profile.blink_vip_until,
      'follower_count', v_profile.follower_count,
      'following_count', v_profile.following_count,
      'posts_count', v_profile.posts_count,
      'daily_streak', v_profile.daily_streak,
      'world_rank', v_profile.world_rank,
      'campus_rank', v_profile.campus_rank,
      'total_xp', v_profile.total_xp,
      'xp_level', v_profile.xp_level,
      'points', v_profile.points,
      'created_at', v_profile.created_at,
      'seller_store_name', v_profile.seller_store_name,
      'is_seller_active', v_profile.is_seller_active,
      'relationship_status', case when v_viewer = v_profile.id
        then to_jsonb(v_profile.relationship_status) else 'null'::jsonb end,
      'current_city_state', case when v_viewer = v_profile.id
        then v_profile.current_city_state else '' end,
      'email', case
        when v_viewer = v_profile.id then v_profile.email
        when v_profile.email_visibility = 'PUBLIC' then v_profile.email
        when v_profile.email_visibility = 'FOLLOWERS' and v_is_following then v_profile.email
        else '' end,
      'phone', case
        when v_viewer = v_profile.id then v_profile.phone
        when v_profile.phone_visibility = 'PUBLIC' then v_profile.phone
        when v_profile.phone_visibility = 'FOLLOWERS' and v_is_following then v_profile.phone
        else '' end,
      'whatsapp', case
        when v_viewer = v_profile.id then v_profile.whatsapp
        when v_profile.whatsapp_visibility = 'PUBLIC' then v_profile.whatsapp
        when v_profile.whatsapp_visibility = 'FOLLOWERS' and v_is_following then v_profile.whatsapp
        else '' end,
      'email_visibility', case
        when v_viewer = v_profile.id
             or v_profile.email_visibility = 'PUBLIC'
             or (v_profile.email_visibility = 'FOLLOWERS' and v_is_following)
        then v_profile.email_visibility else 'PRIVATE' end,
      'phone_visibility', case
        when v_viewer = v_profile.id
             or v_profile.phone_visibility = 'PUBLIC'
             or (v_profile.phone_visibility = 'FOLLOWERS' and v_is_following)
        then v_profile.phone_visibility else 'PRIVATE' end,
      'whatsapp_visibility', case
        when v_viewer = v_profile.id
             or v_profile.whatsapp_visibility = 'PUBLIC'
             or (v_profile.whatsapp_visibility = 'FOLLOWERS' and v_is_following)
        then v_profile.whatsapp_visibility else 'PRIVATE' end,
      'presence_visibility', case when v_viewer = v_profile.id
        then v_profile.presence_visibility else 'PRIVATE' end,
      'online_now', case when v_can_presence then v_profile.online_now else false end,
      'is_online', case when v_can_presence then v_profile.is_online else false end,
      'last_seen_at', case when v_can_presence
        then to_jsonb(v_profile.last_seen_at) else 'null'::jsonb end,
      'last_seen', case when v_can_presence
        then to_jsonb(v_profile.last_seen) else 'null'::jsonb end,
      'profile_views_this_week', case when v_viewer = v_profile.id
        then v_profile.profile_views_this_week else 0 end,
      'onboarding_completed', case when v_viewer = v_profile.id
        then v_profile.onboarding_completed else true end,
      'onboarding_step', case when v_viewer = v_profile.id
        then v_profile.onboarding_step else 0 end,
      'current_wallet_balance', case when v_viewer = v_profile.id
        then v_profile.current_wallet_balance else 0 end
  );
end; $$;

revoke all on function public.get_profile_detail(text) from public, anon;
grant execute on function public.get_profile_detail(text) to authenticated;

-- Account-wide counts are calculated on the server, independent of which
-- timeline page the mobile or desktop client has currently loaded.
create or replace function public.get_profile_content_metrics(p_profile_id uuid)
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $
declare
  v_viewer uuid := auth.uid();
begin
  if v_viewer is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_profile_id is null then return null; end if;
  if not exists(select 1 from public.profiles p where p.id=p_profile_id) then return null; end if;
  if exists(select 1 from public.blocks b
    where (b.blocker_id=v_viewer and b.blocked_id=p_profile_id)
       or (b.blocker_id=p_profile_id and b.blocked_id=v_viewer)) then
    return null;
  end if;
  return (
    select jsonb_build_object(
      'posts', count(*) filter(where not coalesce(f.is_reel,false)),
      'reels', count(*) filter(where coalesce(f.is_reel,false)),
      'views', coalesce(sum(greatest(f.view_count,0)),0),
      'likes', coalesce(sum(greatest(f.like_count,0)),0),
      'comments', coalesce(sum(greatest(f.comment_count,0)),0),
      'shares', coalesce(sum(greatest(f.share_count,0)),0))
    from public.feed_posts f where f.user_id = p_profile_id
      and f.is_active = true and coalesce(f.is_flagged,false) = false
  );
end; $;
revoke all on function public.get_profile_content_metrics(uuid) from public, anon;
grant execute on function public.get_profile_content_metrics(uuid) to authenticated;
commit;

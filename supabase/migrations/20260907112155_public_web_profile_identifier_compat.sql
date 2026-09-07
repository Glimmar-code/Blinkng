-- Keep legacy profile UUID shares working while canonical browser URLs use /@username.
create or replace function public.get_public_web_profile(p_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles%rowtype;
    v_items jsonb := '[]'::jsonb;
    v_identifier text := lower(ltrim(btrim(coalesce(p_username, '')), '@'));
    v_profile_id uuid := case
        when v_identifier ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
            then v_identifier::uuid
        else null
    end;
begin
    if v_identifier = '' or char_length(v_identifier) > 60 then
        return jsonb_build_object('available', false);
    end if;

    select p.* into v_profile
      from public.profiles p
     where (lower(p.username) = v_identifier or p.id = v_profile_id)
       and not exists (
           select 1 from public.user_settings s
            where s.user_id = p.id and coalesce(s.private_account, false) = true
       )
     limit 1;

    if not found then return jsonb_build_object('available', false); end if;

    select coalesce(jsonb_agg(item order by created_at desc), '[]'::jsonb)
      into v_items
      from (
          select fp.created_at,
                 jsonb_build_object(
                     'id', fp.id,
                     'type', case when coalesce(fp.is_reel, false) then 'reel' else 'post' end,
                     'text', coalesce(fp.text, ''),
                     'caption', coalesce(fp.caption, ''),
                     'imageUrls', case
                         when fp.images is not null and cardinality(fp.images) > 0 then to_jsonb(fp.images)
                         when nullif(fp.image_url, '') is not null then jsonb_build_array(fp.image_url)
                         else '[]'::jsonb end,
                     'videoUrl', coalesce(fp.video_url, ''),
                     'createdAt', fp.created_at,
                     'likeCount', coalesce(fp.like_count, 0),
                     'commentCount', coalesce(fp.comment_count, 0),
                     'shareCount', coalesce(fp.share_count, 0),
                     'repostCount', coalesce(fp.repost_count, 0),
                     'viewCount', coalesce(fp.view_count, 0),
                     'allowComments', coalesce(fp.allow_comments, true),
                     'hideLikes', coalesce(fp.hide_likes, false),
                     'altText', coalesce(fp.alt_text, ''),
                     'audioTitle', coalesce(fp.audio_title, '')
                 ) as item
            from public.feed_posts fp
           where fp.user_id = v_profile.id
             and fp.is_active = true
             and upper(coalesce(fp.audience, 'EVERYONE')) = 'EVERYONE'
           order by fp.created_at desc
           limit 24
      ) q;

    return jsonb_build_object(
        'available', true,
        'type', 'profile',
        'id', v_profile.id,
        'profile', jsonb_build_object(
            'id', v_profile.id,
            'username', coalesce(v_profile.username, ''),
            'fullName', coalesce(v_profile.full_name, v_profile.username, ''),
            'avatarUrl', coalesce(v_profile.avatar_url, ''),
            'coverPhotoUrl', coalesce(v_profile.cover_photo_url, v_profile.cover_photo, ''),
            'bio', coalesce(v_profile.bio, ''),
            'headline', coalesce(v_profile.professional_headline, ''),
            'university', coalesce(v_profile.university, ''),
            'faculty', coalesce(v_profile.faculty, ''),
            'department', coalesce(v_profile.department, ''),
            'postsCount', coalesce(v_profile.posts_count, 0),
            'followerCount', coalesce(v_profile.follower_count, 0),
            'followingCount', coalesce(v_profile.following_count, 0),
            'isVerified', coalesce(v_profile.is_verified, false),
            'verificationBadge', coalesce(v_profile.verification_badge, v_profile.verification_tier::text, '')
        ),
        'items', v_items
    );
end;
$$;

revoke all on function public.get_public_web_profile(text) from public, authenticated;
grant execute on function public.get_public_web_profile(text) to anon, service_role;

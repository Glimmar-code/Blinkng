-- Narrow read-only RPCs for browser visitors. These functions intentionally return
-- a small public projection instead of granting anon SELECT on profiles/feed_posts.
create or replace function public.get_public_web_content(p_content_id uuid, p_content_type text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_post public.feed_posts%rowtype;
    v_author public.profiles%rowtype;
    v_type text := lower(btrim(coalesce(p_content_type, '')));
    v_images jsonb := '[]'::jsonb;
begin
    if p_content_id is null or v_type not in ('post', 'reel') then
        return jsonb_build_object('available', false);
    end if;

    select fp.* into v_post
      from public.feed_posts fp
     where fp.id = p_content_id
       and fp.is_active = true
       and upper(coalesce(fp.audience, 'EVERYONE')) = 'EVERYONE'
       and (case when coalesce(fp.is_reel, false) then 'reel' else 'post' end) = v_type
       and not exists (
           select 1 from public.user_settings s
            where s.user_id = fp.user_id and coalesce(s.private_account, false) = true
       )
     limit 1;

    if not found then return jsonb_build_object('available', false); end if;
    select p.* into v_author from public.profiles p where p.id = v_post.user_id limit 1;

    v_images := case
        when v_post.images is not null and cardinality(v_post.images) > 0 then to_jsonb(v_post.images)
        when nullif(v_post.image_url, '') is not null then jsonb_build_array(v_post.image_url)
        else '[]'::jsonb
    end;

    return jsonb_build_object(
        'available', true,
        'type', v_type,
        'id', v_post.id,
        'content', jsonb_build_object(
            'id', v_post.id,
            'type', v_type,
            'text', coalesce(v_post.text, ''),
            'caption', coalesce(v_post.caption, ''),
            'imageUrls', v_images,
            'videoUrl', coalesce(v_post.video_url, ''),
            'createdAt', v_post.created_at,
            'likeCount', coalesce(v_post.like_count, 0),
            'commentCount', coalesce(v_post.comment_count, 0),
            'shareCount', coalesce(v_post.share_count, 0),
            'repostCount', coalesce(v_post.repost_count, 0),
            'viewCount', coalesce(v_post.view_count, 0),
            'allowComments', coalesce(v_post.allow_comments, true),
            'hideLikes', coalesce(v_post.hide_likes, false),
            'altText', coalesce(v_post.alt_text, ''),
            'audioTitle', coalesce(v_post.audio_title, ''),
            'author', jsonb_build_object(
                'id', coalesce(v_author.id, v_post.user_id),
                'username', coalesce(v_author.username, ''),
                'fullName', coalesce(v_author.full_name, v_author.username, ''),
                'avatarUrl', coalesce(v_author.avatar_url, ''),
                'isVerified', coalesce(v_author.is_verified, false),
                'verificationBadge', coalesce(v_author.verification_badge, v_author.verification_tier::text, '')
            )
        )
    );
end;
$$;

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
begin
    if v_identifier = '' or char_length(v_identifier) > 60 then return jsonb_build_object('available', false); end if;

    select p.* into v_profile
      from public.profiles p
     where lower(p.username) = v_identifier
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

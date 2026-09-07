-- Fix poll option ownership so a newly-created poll can insert its options
-- before feed_posts.poll_id is attached by the client.
drop policy if exists poll_options_insert_own on public.poll_options;
create policy poll_options_insert_own
on public.poll_options
for insert
to authenticated
with check (
  exists (
    select 1
    from public.polls p
    join public.feed_posts fp on fp.id = p.post_id
    where p.id = poll_options.poll_id
      and fp.user_id = (select auth.uid())
  )
);

-- schedule_feed_post and cancel_scheduled_feed_post are security-invoker RPCs,
-- so the authenticated owner needs explicit write policies.
drop policy if exists scheduled_feed_posts_insert_own on public.scheduled_feed_posts;
create policy scheduled_feed_posts_insert_own
on public.scheduled_feed_posts
for insert
to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists scheduled_feed_posts_update_own on public.scheduled_feed_posts;
create policy scheduled_feed_posts_update_own
on public.scheduled_feed_posts
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists scheduled_feed_posts_delete_own on public.scheduled_feed_posts;
create policy scheduled_feed_posts_delete_own
on public.scheduled_feed_posts
for delete
to authenticated
using (user_id = (select auth.uid()));

-- Repost credits are an internal anti-duplication ledger maintained by
-- SECURITY DEFINER trigger functions. Make client denial explicit.
drop policy if exists repost_point_credits_no_client_access on public.repost_point_credits;
create policy repost_point_credits_no_client_access
on public.repost_point_credits
for all
to anon, authenticated
using (false)
with check (false);
revoke all on table public.repost_point_credits from anon, authenticated;

-- Publish due schedules while preserving colored text style.
create or replace function public.publish_due_scheduled_feed_posts()
returns integer
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_row record;
  v_count integer := 0;
  v_post uuid;
  v_poll uuid;
  v_option jsonb;
  v_position integer;
  v_images text[];
  v_tags text[];
  v_gradient jsonb;
begin
  for v_row in
    select id,user_id,payload
    from public.scheduled_feed_posts
    where status='pending' and scheduled_for<=now()
    order by scheduled_for
    limit 100
    for update skip locked
  loop
    begin
      update public.scheduled_feed_posts
      set status='processing',updated_at=now(),error_message=null
      where id=v_row.id;

      select coalesce(array_agg(value), '{}'::text[]) into v_images
      from jsonb_array_elements_text(coalesce(v_row.payload->'images','[]'::jsonb));
      select coalesce(array_agg(value), '{}'::text[]) into v_tags
      from jsonb_array_elements_text(coalesce(v_row.payload->'tags','[]'::jsonb));

      v_gradient := case
        when nullif(trim(coalesce(v_row.payload->>'text_style','')),'') is not null
          then jsonb_build_object('key', lower(trim(v_row.payload->>'text_style')))
        when jsonb_typeof(v_row.payload->'gradient')='object'
          then v_row.payload->'gradient'
        else null
      end;

      insert into public.feed_posts(
        user_id,type,faculty,text,caption,image_url,video_url,gradient,tags,is_reel,audience,category,
        location,link_url,allow_comments,hide_likes,is_pinned,is_disappearing,audio_title,
        alt_text,images,is_active,created_at,updated_at
      ) values (
        v_row.user_id,
        case when coalesce((v_row.payload->>'is_reel')::boolean,false)
                  or nullif(v_row.payload->>'video_url','') is not null then 'reel'
             when v_row.payload->'poll' is not null then 'poll'
             when cardinality(v_images)>0 then 'photo'
             else 'text' end,
        nullif(v_row.payload->>'faculty',''),
        nullif(v_row.payload->>'text',''),
        nullif(v_row.payload->>'text',''),
        case when cardinality(v_images)>0 then v_images[1] else null end,
        nullif(v_row.payload->>'video_url',''),
        v_gradient,
        v_tags,
        coalesce((v_row.payload->>'is_reel')::boolean,false) or nullif(v_row.payload->>'video_url','') is not null,
        coalesce(nullif(v_row.payload->>'audience',''),'Everyone'),
        coalesce(nullif(v_row.payload->>'category',''),'Campus Life'),
        nullif(v_row.payload->>'location',''),
        nullif(v_row.payload->>'link_url',''),
        coalesce((v_row.payload->>'allow_comments')::boolean,true),
        coalesce((v_row.payload->>'hide_likes')::boolean,false),
        coalesce((v_row.payload->>'is_pinned')::boolean,false),
        coalesce((v_row.payload->>'is_disappearing')::boolean,false),
        nullif(v_row.payload->>'audio_title',''),
        nullif(v_row.payload->>'alt_text',''),
        v_images,true,now(),now()
      ) returning id into v_post;

      if v_row.payload->'poll' is not null and nullif(trim(v_row.payload#>>'{poll,question}'),'') is not null then
        insert into public.polls(post_id,question)
        values(v_post,left(v_row.payload#>>'{poll,question}',240))
        returning id into v_poll;
        v_position:=0;
        for v_option in select value from jsonb_array_elements(coalesce(v_row.payload#>'{poll,options}','[]'::jsonb))
        loop
          if nullif(trim(coalesce(v_option->>'text','')),'') is not null then
            insert into public.poll_options(poll_id,option_text,position)
            values(v_poll,left(v_option->>'text',120),v_position);
            v_position:=v_position+1;
          end if;
        end loop;
        update public.feed_posts set poll_id=v_poll where id=v_post;
      end if;

      update public.scheduled_feed_posts
      set status='published',published_post_id=v_post,published_at=now(),updated_at=now(),error_message=null
      where id=v_row.id;
      v_count:=v_count+1;
    exception when others then
      update public.scheduled_feed_posts
      set status='failed',error_message=left(sqlerrm,1000),updated_at=now()
      where id=v_row.id;
    end;
  end loop;
  return v_count;
end;
$function$;

create or replace function public.publish_scheduled_feed_post_now(p_schedule_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_row public.scheduled_feed_posts%rowtype;
  v_post uuid;
  v_poll uuid;
  v_option jsonb;
  v_position integer := 0;
  v_images text[] := '{}';
  v_tags text[] := '{}';
  v_gradient jsonb;
begin
  select * into v_row
  from public.scheduled_feed_posts
  where id=p_schedule_id
    and user_id=auth.uid()
    and status in ('pending','failed')
  for update;
  if not found then raise exception 'SCHEDULE_NOT_FOUND_OR_NOT_PUBLISHABLE'; end if;

  update public.scheduled_feed_posts
  set status='processing',updated_at=now(),error_message=null
  where id=v_row.id;

  select coalesce(array_agg(value), '{}'::text[])
  into v_images
  from jsonb_array_elements_text(coalesce(v_row.payload->'images','[]'::jsonb));

  select coalesce(array_agg(value), '{}'::text[])
  into v_tags
  from jsonb_array_elements_text(coalesce(v_row.payload->'tags','[]'::jsonb));

  v_gradient := case
    when nullif(trim(coalesce(v_row.payload->>'text_style','')),'') is not null
      then jsonb_build_object('key', lower(trim(v_row.payload->>'text_style')))
    when jsonb_typeof(v_row.payload->'gradient')='object'
      then v_row.payload->'gradient'
    else null
  end;

  insert into public.feed_posts(
    user_id,type,faculty,text,caption,image_url,video_url,gradient,tags,is_reel,audience,category,
    location,link_url,allow_comments,hide_likes,is_pinned,is_disappearing,audio_title,
    alt_text,images,is_active,created_at,updated_at
  ) values (
    v_row.user_id,
    case when coalesce((v_row.payload->>'is_reel')::boolean,false)
              or nullif(v_row.payload->>'video_url','') is not null then 'reel'
         when v_row.payload->'poll' is not null then 'poll'
         when cardinality(v_images)>0 then 'photo'
         else 'text' end,
    nullif(v_row.payload->>'faculty',''),
    nullif(v_row.payload->>'text',''),
    nullif(v_row.payload->>'text',''),
    case when cardinality(v_images)>0 then v_images[1] else null end,
    nullif(v_row.payload->>'video_url',''),
    v_gradient,
    v_tags,
    coalesce((v_row.payload->>'is_reel')::boolean,false) or nullif(v_row.payload->>'video_url','') is not null,
    coalesce(nullif(v_row.payload->>'audience',''),'Everyone'),
    coalesce(nullif(v_row.payload->>'category',''),'Campus Life'),
    nullif(v_row.payload->>'location',''),
    nullif(v_row.payload->>'link_url',''),
    coalesce((v_row.payload->>'allow_comments')::boolean,true),
    coalesce((v_row.payload->>'hide_likes')::boolean,false),
    coalesce((v_row.payload->>'is_pinned')::boolean,false),
    coalesce((v_row.payload->>'is_disappearing')::boolean,false),
    nullif(v_row.payload->>'audio_title',''),
    nullif(v_row.payload->>'alt_text',''),
    v_images,true,now(),now()
  ) returning id into v_post;

  if v_row.payload->'poll' is not null and nullif(trim(v_row.payload#>>'{poll,question}'),'') is not null then
    insert into public.polls(post_id,question)
    values(v_post,left(v_row.payload#>>'{poll,question}',240))
    returning id into v_poll;

    for v_option in select value from jsonb_array_elements(coalesce(v_row.payload#>'{poll,options}','[]'::jsonb))
    loop
      if nullif(trim(coalesce(v_option->>'text','')),'') is not null then
        insert into public.poll_options(poll_id,option_text,position)
        values(v_poll,left(v_option->>'text',120),v_position);
        v_position:=v_position+1;
      end if;
    end loop;
    update public.feed_posts set poll_id=v_poll where id=v_post;
  end if;

  update public.scheduled_feed_posts
  set status='published',published_post_id=v_post,published_at=now(),updated_at=now(),error_message=null
  where id=v_row.id;
  return v_post;
exception when others then
  update public.scheduled_feed_posts
  set status='failed',error_message=left(sqlerrm,1000),updated_at=now()
  where id=p_schedule_id;
  raise;
end;
$function$;

-- Keep generic activity notification state aligned with message read state.
create or replace function public.mark_conversation_read(p_partner_username text)
returns integer
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_me uuid := auth.uid();
  v_partner uuid;
  v_changed integer := 0;
  v_identifier text := lower(trim(leading '@' from coalesce(p_partner_username, '')));
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier = '' then raise exception 'RECIPIENT_REQUIRED'; end if;

  select p.id into v_partner
  from public.profiles p
  where lower(btrim(p.username)) = v_identifier
     or lower(btrim(coalesce(p.handle, ''))) = v_identifier
  order by case when lower(btrim(p.username)) = v_identifier then 0 else 1 end
  limit 1;

  if v_partner is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;

  update public.messages m
  set is_read = true,
      delivered_at = coalesce(m.delivered_at, now()),
      read_at = coalesce(m.read_at, now())
  where m.sender_id = v_partner
    and coalesce(m.deleted_for_everyone, false) = false
    and exists (
      select 1
      from public.conversation_participants mine
      join public.conversation_participants theirs
        on theirs.conversation_id = mine.conversation_id
       and theirs.user_id = v_partner
      where mine.user_id = v_me
        and mine.conversation_id = m.conversation_id
    )
    and (
      coalesce(m.is_read, false) = false
      or m.delivered_at is null
      or m.read_at is null
    );

  get diagnostics v_changed = row_count;

  update public.conversation_participants mine
  set last_read_at = now()
  where mine.user_id = v_me
    and exists (
      select 1
      from public.conversation_participants theirs
      where theirs.conversation_id = mine.conversation_id
        and theirs.user_id = v_partner
    );

  update public.notifications n
  set is_read = true
  where n.user_id = v_me
    and n.actor_id = v_partner
    and coalesce(n.is_read,false) = false
    and lower(coalesce(n.text,'')) like '% sent you a message%';

  return v_changed;
end;
$function$;
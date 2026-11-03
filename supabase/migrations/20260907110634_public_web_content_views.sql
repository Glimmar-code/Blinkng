-- Public browser views share the same feed_posts.view_count while keeping
-- anonymous browsers away from the underlying feed_posts table.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

do $$ begin
  create table private.web_content_views (
    post_id uuid not null references public.feed_posts(id) on delete cascade,
    visitor_hash text not null,
    impression_count integer not null default 0 check (impression_count between 0 and 100),
    last_viewed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    primary key (post_id, visitor_hash),
    constraint web_content_views_visitor_hash_length_check check (char_length(visitor_hash) between 32 and 128)
  );
exception when duplicate_table then null; end $$;

create index if not exists idx_web_content_views_last_viewed
  on private.web_content_views (last_viewed_at desc);

create or replace function public.record_public_web_view(p_post_id uuid, p_visitor_hash text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_view_count integer := 0;
    v_is_reel boolean := false;
    v_existing_count integer := 0;
    v_last_viewed_at timestamptz;
    v_new_count integer := 0;
begin
    if p_post_id is null then
        raise exception 'POST_ID_REQUIRED' using errcode = '22004';
    end if;

    if p_visitor_hash is null
       or char_length(p_visitor_hash) < 32
       or char_length(p_visitor_hash) > 128
       or p_visitor_hash !~ '^[0-9a-fA-F]+$' then
        raise exception 'INVALID_VISITOR_HASH' using errcode = '22023';
    end if;

    select coalesce(fp.view_count, 0), coalesce(fp.is_reel, false)
      into v_view_count, v_is_reel
      from public.feed_posts fp
     where fp.id = p_post_id
       and fp.is_active = true
       and upper(coalesce(fp.audience, 'EVERYONE')) = 'EVERYONE'
       and not exists (
           select 1 from public.user_settings s
            where s.user_id = fp.user_id and coalesce(s.private_account, false) = true
       )
     for update;

    if not found then
        raise exception 'CONTENT_NOT_FOUND_OR_PRIVATE' using errcode = 'P0002';
    end if;

    select coalesce(w.impression_count, 0), w.last_viewed_at
      into v_existing_count, v_last_viewed_at
      from private.web_content_views w
     where w.post_id = p_post_id
       and w.visitor_hash = lower(p_visitor_hash);

    v_existing_count := coalesce(v_existing_count, 0);

    if v_existing_count >= 100 then
        return jsonb_build_object('accepted', false, 'duplicate', false, 'cap_reached', true, 'view_count', v_view_count, 'visitor_view_count', v_existing_count, 'content_type', case when v_is_reel then 'reel' else 'post' end);
    end if;

    if v_last_viewed_at is not null and v_last_viewed_at > now() - interval '30 seconds' then
        return jsonb_build_object('accepted', false, 'duplicate', true, 'cap_reached', false, 'view_count', v_view_count, 'visitor_view_count', v_existing_count, 'content_type', case when v_is_reel then 'reel' else 'post' end);
    end if;

    insert into private.web_content_views as w (post_id, visitor_hash, impression_count, last_viewed_at)
    values (p_post_id, lower(p_visitor_hash), 1, now())
    on conflict (post_id, visitor_hash) do update
       set impression_count = least(w.impression_count + 1, 100), last_viewed_at = now()
     where w.impression_count < 100
    returning impression_count into v_new_count;

    if v_new_count is null then
        return jsonb_build_object('accepted', false, 'duplicate', false, 'cap_reached', true, 'view_count', v_view_count, 'visitor_view_count', v_existing_count, 'content_type', case when v_is_reel then 'reel' else 'post' end);
    end if;

    update public.feed_posts
       set view_count = coalesce(view_count, 0) + 1, updated_at = now()
     where id = p_post_id
    returning view_count into v_view_count;

    return jsonb_build_object('accepted', true, 'duplicate', false, 'cap_reached', v_new_count >= 100, 'view_count', v_view_count, 'visitor_view_count', v_new_count, 'content_type', case when v_is_reel then 'reel' else 'post' end);
end;
$$;

revoke all on function public.record_public_web_view(uuid,text) from public, authenticated;
grant execute on function public.record_public_web_view(uuid,text) to anon, service_role;

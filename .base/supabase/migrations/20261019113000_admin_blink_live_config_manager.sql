-- BLINK Live Content Manager. Staging first; do not deploy to live automatically.
-- Owner-only publish and pause; active BLINK admins can read unpublished drafts.
-- Rollback: REVOKE EXECUTE on both RPCs, then DROP FUNCTIONs; keep table/audit.
begin;

alter table public.blink_live_config
  add column if not exists revision bigint not null default 1;

create table if not exists private.blink_live_config_audit (
  id bigint generated always as identity primary key,
  actor_id uuid not null references auth.users(id),
  config_key text not null,
  old_value jsonb,
  new_value jsonb not null,
  old_enabled boolean,
  new_enabled boolean not null,
  old_revision bigint,
  new_revision bigint not null,
  created_at timestamptz not null default now()
);

alter table private.blink_live_config_audit enable row level security;
revoke all on private.blink_live_config_audit from public, anon, authenticated;

create or replace function public.admin_read_blink_live_config()
returns jsonb
language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform private.require_blink_admin();
  return jsonb_build_object('items', coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'key', c.key, 'value', c.value, 'enabled', c.enabled,
        'revision', c.revision, 'updated_at', c.updated_at
      ) order by c.key
    )
    from public.blink_live_config c
  ), '[]'::jsonb));
end;
$$;

create or replace function public.admin_publish_blink_live_config(
  p_key text, p_value jsonb, p_enabled boolean, p_expected_revision bigint
)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
  v_actor uuid;
  v_old public.blink_live_config%rowtype;
  v_result public.blink_live_config%rowtype;
begin
  -- Important: frontend controls are not authorization. Enforce on the server.
  v_actor := private.require_blink_owner();

  if p_key is null or p_key not in ('announcement','promotion','display','feature_visibility')
     or p_value is null or jsonb_typeof(p_value) <> 'object'
     or p_enabled is null or p_expected_revision is null or p_expected_revision < 1
     or octet_length(p_value::text) > 4096 then
    raise exception 'INVALID_LIVE_CONFIG' using errcode='22023';
  end if;

  if p_key in ('announcement','promotion') then
    if exists (select 1 from jsonb_object_keys(p_value) k where k not in ('title','message'))
       or coalesce(jsonb_typeof(p_value->'title'), '') <> 'string'
       or coalesce(jsonb_typeof(p_value->'message'), '') <> 'string'
       or char_length(p_value->>'title') > 80
       or char_length(p_value->>'message') > 300 then
      raise exception 'INVALID_LIVE_MESSAGE' using errcode='22023';
    end if;
    if p_enabled and (length(trim(p_value->>'title')) = 0
                      or length(trim(p_value->>'message')) = 0) then
      raise exception 'LIVE_MESSAGE_REQUIRES_TITLE_AND_BODY' using errcode='22023';
    end if;
  elsif p_key = 'display' then
    if exists (select 1 from jsonb_object_keys(p_value) k
               where k not in ('show_announcements','show_promotions'))
       or coalesce(jsonb_typeof(p_value->'show_announcements'), '') <> 'boolean'
       or coalesce(jsonb_typeof(p_value->'show_promotions'), '') <> 'boolean' then
      raise exception 'INVALID_DISPLAY_FLAGS' using errcode='22023';
    end if;
  elsif p_key = 'feature_visibility' then
    if exists (select 1 from jsonb_object_keys(p_value) k
               where k not in ('home_promo_enabled','home_campaign_enabled',
                   'reels_campaign_enabled','marketplace_campaign_enabled',
                   'connect_campaign_enabled','messages_campaign_enabled','games_campaign_enabled'))
       or coalesce(jsonb_typeof(p_value->'home_promo_enabled'), '') <> 'boolean'
       or exists (select 1 from jsonb_each(p_value) f where jsonb_typeof(f.value)<>'boolean') then
      raise exception 'INVALID_PRESENTATION_FLAGS' using errcode='22023';
    end if;
  end if;

  select * into v_old from public.blink_live_config where key = p_key for update;
  if not found or v_old.revision <> p_expected_revision then
    raise exception 'LIVE_CONFIG_CHANGED_RELOAD' using errcode='40001';
  end if;

  update public.blink_live_config
     set value = p_value, enabled = p_enabled,
         revision = v_old.revision + 1, updated_at = now()
   where key = p_key and revision = p_expected_revision
   returning * into v_result;

  insert into private.blink_live_config_audit(
    actor_id, config_key, old_value, new_value,
    old_enabled, new_enabled, old_revision, new_revision
  ) values (
    v_actor, p_key, v_old.value, v_result.value,
    v_old.enabled, v_result.enabled, v_old.revision, v_result.revision
  );

  return jsonb_build_object(
    'key', v_result.key, 'value', v_result.value,
    'enabled', v_result.enabled, 'revision', v_result.revision,
    'updated_at', v_result.updated_at
  );
end;
$$;

revoke all on function public.admin_read_blink_live_config() from public, anon, authenticated;
revoke all on function public.admin_publish_blink_live_config(text,jsonb,boolean,bigint)
  from public, anon, authenticated;
grant execute on function public.admin_read_blink_live_config() to authenticated;
grant execute on function public.admin_publish_blink_live_config(text,jsonb,boolean,bigint)
  to authenticated;

comment on function public.admin_publish_blink_live_config(text,jsonb,boolean,bigint)
  is 'Owner-only, audited, revision-checked publication of non-security BLINK presentation content.';
commit;

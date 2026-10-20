-- BLINK Live Campaign Analytics: owner/admin-only aggregate dashboard.
-- Records are already unique per user/campaign/event/day; displayed counts are
-- directional daily unique actions, NOT true ad impressions or billable events.
-- No user IDs, universities or individual event rows leave the private schema.
begin;

create or replace function public.admin_blink_live_campaign_analytics(
  p_days integer default 30
) returns jsonb
language plpgsql stable security definer
set search_path = ''
as $$
declare
  v_today date := (now() at time zone 'UTC')::date;
  v_since date;
  v_summary jsonb;
  v_daily jsonb;
  v_top jsonb;
begin
  perform private.require_blink_admin();
  if p_days is null or p_days not in (7, 30, 90) then
    raise exception 'INVALID_ANALYTICS_WINDOW' using errcode = '22023';
  end if;

  v_since := v_today - (p_days - 1);

  -- Summary is computed from immutable daily deduplicated engagement events.
  select jsonb_build_object(
    'impressions', count(*) filter (where event_kind='impression'),
    'clicks', count(*) filter (where event_kind='click'),
    'dismissals', count(*) filter (where event_kind='dismiss'),
    'ctr_pct', coalesce(round(
      100.0 * (count(*) filter (where event_kind='click'))
      / nullif(count(*) filter (where event_kind='impression'),0),1
    ),0)
  )
  into v_summary
  from private.blink_live_campaign_events
  where event_day between v_since and v_today;

  -- Every UTC day appears, even with zero events.
  select coalesce(jsonb_agg(
      jsonb_build_object(
        'day', d.day::date,
        'impressions', coalesce(t.impressions,0),
        'clicks', coalesce(t.clicks,0),
        'dismissals', coalesce(t.dismissals,0)
      ) order by d.day
    ), '[]'::jsonb)
  into v_daily
  from generate_series(v_since,v_today,interval '1 day') as d(day)
  left join (
    select e.event_day,
      count(*) filter (where e.event_kind='impression') as impressions,
      count(*) filter (where e.event_kind='click') as clicks,
      count(*) filter (where e.event_kind='dismiss') as dismissals
    from private.blink_live_campaign_events e
    where e.event_day between v_since and v_today
    group by e.event_day
  ) t on t.event_day=d.day::date;

  -- Ranking uses this same requested period, not lifetime values.
  -- Includes historical archived/paused campaigns to avoid hiding results.
  select coalesce(jsonb_agg(
    jsonb_build_object(
      'id', x.id, 'title', x.title, 'kind', x.kind,
      'status', x.status, 'impressions', x.impressions,
      'clicks', x.clicks, 'dismissals', x.dismissals,
      'ctr_pct', x.ctr_pct
    ) order by x.clicks desc,x.impressions desc,x.title,x.id
  ), '[]'::jsonb)
  into v_top
  from (
    select c.id,c.title,c.kind,c.status,
      coalesce(e.impressions,0) as impressions,
      coalesce(e.clicks,0) as clicks,
      coalesce(e.dismissals,0) as dismissals,
      coalesce(round(100.0 * e.clicks / nullif(e.impressions,0),1),0) as ctr_pct
    from public.blink_live_campaigns c
    left join (
      select campaign_id,
        count(*) filter (where event_kind='impression') as impressions,
        count(*) filter (where event_kind='click') as clicks,
        count(*) filter (where event_kind='dismiss') as dismissals
      from private.blink_live_campaign_events
      where event_day between v_since and v_today
      group by campaign_id
    ) e on e.campaign_id = c.id
    where not c.is_template and coalesce(e.impressions,0)>0
    order by coalesce(e.clicks,0) desc,coalesce(e.impressions,0) desc,c.title,c.id
    limit 15
  ) x;

  return jsonb_build_object(
    'days', p_days,
    'from_date', v_since,
    'through_date', v_today,
    'summary', v_summary,
    'daily', v_daily,
    'top_campaigns', v_top,
    'metric_note', 'Daily unique user/campaign actions in UTC. Estimates, not billable advertising impressions.'
  );
end;
$$;

revoke all on function public.admin_blink_live_campaign_analytics(integer)
from public,anon,authenticated;
grant execute on function public.admin_blink_live_campaign_analytics(integer)
to authenticated;

comment on function public.admin_blink_live_campaign_analytics(integer)
is 'Admin-only 7/30/90-day aggregated UTC campaign analytics, directional and non-billable.';
commit;

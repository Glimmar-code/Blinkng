-- Safe rollback of Broadcast dispatch queue and worker scheduling.
DO $$ DECLARE j record; BEGIN FOR j IN SELECT jobid FROM cron.job WHERE jobname='blink_broadcast_dispatch_worker_minutely' LOOP PERFORM cron.unschedule(j.jobid); END LOOP; END $$;
DROP TRIGGER IF EXISTS broadcast_queue_dispatch_after_live ON public.broadcasts;
DROP TRIGGER IF EXISTS broadcast_daily_dismissal_rollup ON public.broadcast_dismissals;
DROP TRIGGER IF EXISTS broadcast_daily_claim_rollup ON public.promo_claims;
DROP TRIGGER IF EXISTS broadcast_daily_conversion_rollup ON public.campaign_participants;
REVOKE EXECUTE ON FUNCTION public.claim_broadcast_dispatch_batch(integer),public.complete_broadcast_dispatch(uuid,boolean,text),
  public.claim_broadcast_delivery(uuid,uuid,public.broadcast_channel),public.complete_broadcast_delivery(uuid,boolean,text),
  public.broadcast_user_frequency_ok(uuid,public.broadcast_channel,integer,integer),public.broadcast_dispatch_audience(uuid,public.broadcast_channel,uuid,integer),
  public.broadcast_dispatch_cron_tick() FROM PUBLIC,anon,authenticated;
-- Keep queue and delivery data for forensic/retry safety; do not drop tables or delivery columns.

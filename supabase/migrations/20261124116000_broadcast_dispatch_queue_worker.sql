-- Broadcast dispatch queue, retries, frequency caps, lifecycle worker and analytics rollups.
-- Dispatch stays disabled until secrets and worker URL are configured in Testlab.

CREATE TABLE IF NOT EXISTS public.broadcast_dispatch_queue (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  broadcast_id uuid NOT NULL REFERENCES public.broadcasts(id) ON DELETE CASCADE,
  channel public.broadcast_channel NOT NULL CHECK(channel IN ('push','email')),
  status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','processing','completed','failed')),
  attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  locked_at timestamptz,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(broadcast_id,channel)
);
CREATE INDEX IF NOT EXISTS broadcast_dispatch_queue_pending_idx ON public.broadcast_dispatch_queue(status,next_attempt_at,created_at);
ALTER TABLE public.broadcast_dispatch_queue ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_dispatch_queue_admin_read ON public.broadcast_dispatch_queue FOR SELECT TO authenticated USING(public.broadcast_admin_check());
GRANT SELECT ON public.broadcast_dispatch_queue TO authenticated;
GRANT ALL ON public.broadcast_dispatch_queue TO service_role;
CREATE TABLE IF NOT EXISTS public.broadcast_dispatch_config (
  id boolean PRIMARY KEY DEFAULT true CHECK(id),
  enabled boolean NOT NULL DEFAULT false,
  worker_url text,
  updated_by uuid REFERENCES auth.users(id),
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO public.broadcast_dispatch_config(id,enabled,worker_url) VALUES(true,false,null) ON CONFLICT(id) DO NOTHING;
ALTER TABLE public.broadcast_dispatch_config ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_dispatch_config_admin_read ON public.broadcast_dispatch_config FOR SELECT TO authenticated USING(public.broadcast_admin_check());
CREATE POLICY broadcast_dispatch_config_admin_update ON public.broadcast_dispatch_config FOR UPDATE TO authenticated USING(public.broadcast_admin_check()) WITH CHECK(public.broadcast_admin_check());
GRANT SELECT,UPDATE ON public.broadcast_dispatch_config TO authenticated;
GRANT ALL ON public.broadcast_dispatch_config TO service_role;

ALTER TABLE public.broadcast_deliveries
  ADD COLUMN IF NOT EXISTS locked_at timestamptz,
  ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;
CREATE INDEX IF NOT EXISTS broadcast_deliveries_retry_idx ON public.broadcast_deliveries(status,next_retry_at,locked_at);

CREATE OR REPLACE FUNCTION public.broadcast_queue_live_dispatch()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_channels jsonb:=coalesce(NEW.channels,'{}'::jsonb);
BEGIN
  IF NEW.status='live' AND NEW.deleted_at IS NULL AND (NEW.starts_at IS NULL OR NEW.starts_at<=now()) AND (NEW.ends_at IS NULL OR NEW.ends_at>now()) THEN
    IF coalesce((v_channels->>'push')::boolean,false) THEN
      INSERT INTO public.broadcast_dispatch_queue(broadcast_id,channel) VALUES(NEW.id,'push')
        ON CONFLICT(broadcast_id,channel) DO NOTHING;
    END IF;
    IF coalesce((v_channels->>'email')::boolean,false) THEN
      INSERT INTO public.broadcast_dispatch_queue(broadcast_id,channel) VALUES(NEW.id,'email')
        ON CONFLICT(broadcast_id,channel) DO NOTHING;
    END IF;
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS broadcast_queue_dispatch_after_live ON public.broadcasts;
CREATE TRIGGER broadcast_queue_dispatch_after_live AFTER INSERT OR UPDATE OF status,starts_at,ends_at,channels ON public.broadcasts
FOR EACH ROW EXECUTE FUNCTION public.broadcast_queue_live_dispatch();

CREATE OR REPLACE FUNCTION public.claim_broadcast_dispatch_batch(p_limit integer DEFAULT 20)
RETURNS SETOF public.broadcast_dispatch_queue LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  RETURN QUERY WITH candidates AS (
    SELECT q.id FROM public.broadcast_dispatch_queue q
    WHERE q.status='pending' AND q.next_attempt_at<=now() AND (q.locked_at IS NULL OR q.locked_at<now()-interval '10 minutes')
    ORDER BY q.next_attempt_at,q.created_at FOR UPDATE SKIP LOCKED LIMIT least(greatest(coalesce(p_limit,20),1),100)
  )
  UPDATE public.broadcast_dispatch_queue q SET status='processing',attempts=q.attempts+1,locked_at=now(),updated_at=now()
    FROM candidates c WHERE q.id=c.id RETURNING q.*;
END
$$;

CREATE OR REPLACE FUNCTION public.complete_broadcast_dispatch(p_id uuid,p_success boolean,p_error text DEFAULT NULL)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE q public.broadcast_dispatch_queue%rowtype;
BEGIN
  SELECT * INTO q FROM public.broadcast_dispatch_queue WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RETURN false; END IF;
  IF p_success THEN
    UPDATE public.broadcast_dispatch_queue SET status='completed',locked_at=null,last_error=null,updated_at=now() WHERE id=p_id;
  ELSIF q.attempts<3 THEN
    UPDATE public.broadcast_dispatch_queue SET status='pending',locked_at=null,last_error=left(coalesce(p_error,'dispatch failed'),1000),
      next_attempt_at=now()+make_interval(secs=>power(2,q.attempts)::integer*30),updated_at=now() WHERE id=p_id;
  ELSE
    UPDATE public.broadcast_dispatch_queue SET status='failed',locked_at=null,last_error=left(coalesce(p_error,'dispatch failed'),1000),updated_at=now() WHERE id=p_id;
  END IF;
  RETURN true;
END
$$;

CREATE OR REPLACE FUNCTION public.claim_broadcast_delivery(p_broadcast_id uuid,p_user_id uuid,p_channel public.broadcast_channel)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id uuid;
BEGIN
  INSERT INTO public.broadcast_deliveries(broadcast_id,user_id,channel,status,attempts)
    VALUES(p_broadcast_id,p_user_id,p_channel,'queued',0) ON CONFLICT(broadcast_id,user_id,channel) DO NOTHING;
  UPDATE public.broadcast_deliveries SET locked_at=now(),attempts=attempts+1,updated_at=now()
    WHERE broadcast_id=p_broadcast_id AND user_id=p_user_id AND channel=p_channel
      AND status IN ('queued','failed') AND coalesce(next_retry_at,'-infinity'::timestamptz)<=now()
      AND (locked_at IS NULL OR locked_at<now()-interval '10 minutes') AND attempts<3
    RETURNING id INTO v_id;
  RETURN v_id;
END
$$;

CREATE OR REPLACE FUNCTION public.complete_broadcast_delivery(p_id uuid,p_success boolean,p_error text DEFAULT NULL)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE d public.broadcast_deliveries%rowtype;
BEGIN
  SELECT * INTO d FROM public.broadcast_deliveries WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RETURN false; END IF;
  IF p_success THEN
    UPDATE public.broadcast_deliveries SET status='sent',sent_at=now(),locked_at=null,next_retry_at=null,last_error=null,updated_at=now() WHERE id=p_id;
  ELSIF d.attempts<3 THEN
    UPDATE public.broadcast_deliveries SET status='failed',locked_at=null,next_retry_at=now()+make_interval(secs=>power(2,d.attempts)::integer*30),
      last_error=left(coalesce(p_error,'delivery failed'),1000),updated_at=now() WHERE id=p_id;
  ELSE
    UPDATE public.broadcast_deliveries SET status='failed',locked_at=null,next_retry_at=null,last_error=left(coalesce(p_error,'delivery failed'),1000),updated_at=now() WHERE id=p_id;
  END IF;
  RETURN true;
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_user_frequency_ok(p_user_id uuid,p_channel public.broadcast_channel,p_cap integer DEFAULT 3,p_window_days integer DEFAULT 7)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
  SELECT count(*) < greatest(1,least(coalesce(p_cap,3),20))
  FROM public.broadcast_deliveries d
  WHERE d.user_id=p_user_id AND d.channel=p_channel AND d.status='sent'
    AND d.sent_at>now()-make_interval(days=>greatest(1,least(coalesce(p_window_days,7),30)))
$$;

CREATE OR REPLACE FUNCTION public.broadcast_dispatch_audience(p_broadcast_id uuid,p_channel public.broadcast_channel,p_after_user_id uuid DEFAULT NULL,p_limit integer DEFAULT 500)
RETURNS TABLE(user_id uuid,email text,push_enabled boolean,category_enabled boolean,timezone text,quiet_start time,quiet_end time)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
  SELECT p.id,u.email,coalesce(np.push_enabled,true),
    CASE b.type WHEN 'ANNOUNCEMENT' THEN coalesce(np.announcements,true) WHEN 'CAMPAIGN' THEN coalesce(np.campaigns,true) ELSE coalesce(np.offers,true) END,
    coalesce(np.timezone,'Africa/Lagos'),np.quiet_start,np.quiet_end
  FROM public.broadcasts b
  JOIN public.profiles p ON true
  JOIN auth.users u ON u.id=p.id
  LEFT JOIN public.notification_prefs np ON np.user_id=p.id
  WHERE b.id=p_broadcast_id AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND (p_after_user_id IS NULL OR p.id>p_after_user_id)
    AND CASE p_channel
      WHEN 'push' THEN coalesce(np.push_enabled,true)
        AND CASE b.type WHEN 'ANNOUNCEMENT' THEN coalesce(np.announcements,true) WHEN 'CAMPAIGN' THEN coalesce(np.campaigns,true) ELSE coalesce(np.offers,true) END
        AND EXISTS(SELECT 1 FROM public.device_tokens dt WHERE dt.user_id=p.id AND dt.disabled_at IS NULL
          AND public.broadcast_audience_match(p.id,b.audience,dt.app_version))
      WHEN 'email' THEN coalesce(np.email,true)
        AND CASE b.type WHEN 'ANNOUNCEMENT' THEN coalesce(np.announcements,true) WHEN 'CAMPAIGN' THEN coalesce(np.campaigns,true) ELSE coalesce(np.offers,true) END
        AND public.broadcast_audience_match(p.id,b.audience,null)
      ELSE false END
  ORDER BY p.id LIMIT least(greatest(coalesce(p_limit,500),1),1000)
$$;

CREATE OR REPLACE FUNCTION public.admin_update_broadcast_dispatch_config(p_enabled boolean,p_worker_url text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor uuid:=private.require_blink_admin();
BEGIN
  IF p_enabled AND (p_worker_url IS NULL OR p_worker_url !~ '^https://[^/]+/functions/v1/broadcast-dispatch-worker$') THEN RAISE EXCEPTION 'INVALID_WORKER_URL'; END IF;
  UPDATE public.broadcast_dispatch_config SET enabled=p_enabled,worker_url=p_worker_url,updated_by=actor,updated_at=now() WHERE id=true;
  RETURN (SELECT to_jsonb(c) FROM public.broadcast_dispatch_config c WHERE id=true);
END
$$;

-- Daily aggregate counters for dismissals, claims and campaign completions.
CREATE OR REPLACE FUNCTION public.broadcast_daily_counter_trigger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $
DECLARE v_kind text; v_bid uuid; v_day date;
BEGIN
  v_bid:=NEW.broadcast_id;
  IF TG_TABLE_NAME='campaign_participants' THEN v_day:=(NEW.completed_at AT TIME ZONE 'UTC')::date; v_kind:='conversions';
  ELSE v_day:=(NEW.created_at AT TIME ZONE 'UTC')::date;
    IF TG_TABLE_NAME='broadcast_dismissals' THEN v_kind:='dismissals'; ELSE v_kind:='claims'; END IF;
  END IF;
  EXECUTE format('INSERT INTO public.broadcast_daily_rollups(day,broadcast_id,%I) VALUES($1,$2,1) ON CONFLICT(day,broadcast_id) DO UPDATE SET %I=public.broadcast_daily_rollups.%I+1,updated_at=now()',v_kind,v_kind,v_kind)
    USING v_day,v_bid;
  RETURN NEW;
END
$;
DROP TRIGGER IF EXISTS broadcast_daily_dismissal_rollup ON public.broadcast_dismissals;
CREATE TRIGGER broadcast_daily_dismissal_rollup AFTER INSERT ON public.broadcast_dismissals FOR EACH ROW EXECUTE FUNCTION public.broadcast_daily_counter_trigger();
DROP TRIGGER IF EXISTS broadcast_daily_claim_rollup ON public.promo_claims;
CREATE TRIGGER broadcast_daily_claim_rollup AFTER INSERT ON public.promo_claims FOR EACH ROW WHEN (NEW.status='claimed') EXECUTE FUNCTION public.broadcast_daily_counter_trigger();
DROP TRIGGER IF EXISTS broadcast_daily_conversion_rollup ON public.campaign_participants;
CREATE TRIGGER broadcast_daily_conversion_rollup AFTER UPDATE OF completed_at ON public.campaign_participants FOR EACH ROW WHEN (OLD.completed_at IS NULL AND NEW.completed_at IS NOT NULL) EXECUTE FUNCTION public.broadcast_daily_counter_trigger();

CREATE EXTENSION IF NOT EXISTS pg_net;
CREATE OR REPLACE FUNCTION public.broadcast_dispatch_cron_tick()
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_url text; v_secret text;
BEGIN
  SELECT worker_url INTO v_url FROM public.broadcast_dispatch_config WHERE id=true AND enabled=true;
  IF v_url IS NULL THEN RETURN; END IF;
  SELECT decrypted_secret INTO v_secret FROM vault.decrypted_secrets WHERE name='broadcast_worker_secret' LIMIT 1;
  IF v_secret IS NULL OR length(v_secret)<32 THEN RETURN; END IF;
  PERFORM net.http_post(url:=v_url,headers:=jsonb_build_object('content-type','application/json','x-broadcast-worker-secret',v_secret),body:=jsonb_build_object('source','pg_cron'));
END
$$;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='blink_broadcast_dispatch_worker_minutely') THEN
   PERFORM cron.schedule('blink_broadcast_dispatch_worker_minutely','* * * * *','select public.broadcast_dispatch_cron_tick()');
 END IF;
END $$;

REVOKE EXECUTE ON FUNCTION public.broadcast_queue_live_dispatch(),public.claim_broadcast_dispatch_batch(integer),public.complete_broadcast_dispatch(uuid,boolean,text),
  public.claim_broadcast_delivery(uuid,uuid,public.broadcast_channel),public.complete_broadcast_delivery(uuid,boolean,text),
  public.broadcast_user_frequency_ok(uuid,public.broadcast_channel,integer,integer),public.broadcast_dispatch_audience(uuid,public.broadcast_channel,uuid,integer),
  public.broadcast_dispatch_cron_tick(),public.broadcast_daily_counter_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.admin_update_broadcast_dispatch_config(boolean,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_update_broadcast_dispatch_config(boolean,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.claim_broadcast_dispatch_batch(integer),public.complete_broadcast_dispatch(uuid,boolean,text),
  public.claim_broadcast_delivery(uuid,uuid,public.broadcast_channel),public.complete_broadcast_delivery(uuid,boolean,text),
  public.broadcast_user_frequency_ok(uuid,public.broadcast_channel,integer,integer),public.broadcast_dispatch_audience(uuid,public.broadcast_channel,uuid,integer) TO service_role;

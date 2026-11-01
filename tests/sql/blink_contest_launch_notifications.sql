-- Structural regression checks for the Contest launch fan-out.
do $$
declare
  v_def text;
begin
  if not exists (
    select 1 from pg_trigger
    where tgname='trg_blink_contest_announce_launch' and not tgisinternal
  ) then raise exception 'Contest launch trigger is missing'; end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid='public.blink_essential_email_outbox'::regclass
      and conname='blink_essential_email_outbox_kind_check'
  ) then raise exception 'Contest email outbox kind is not enabled'; end if;

  select pg_get_functiondef('public.blink_claim_channel_push_notifications(integer)'::regprocedure)
    into v_def;
  if position('blink_contest_launch' in v_def)=0 then
    raise exception 'Contest push notifications are not in the durable delivery queue';
  end if;

  select pg_get_functiondef('private.blink_contest_announce_launch()'::regprocedure)
    into v_def;
  if position('old.status' in lower(v_def))=0 or
     position('new.status' in lower(v_def))=0 or
     position('contest_launch:' in v_def)=0 then
    raise exception 'Contest launch fan-out is missing transition/idempotency guards';
  end if;
end
$$;

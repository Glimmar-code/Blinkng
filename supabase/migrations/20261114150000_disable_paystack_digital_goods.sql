-- Google Play compliance guard: Paystack must not sell digital coins or digital gifts.
-- Physical marketplace checkout remains independently controlled by its own flag.

create or replace function public.create_blink_paystack_order(
  p_kind text, p_item text
) returns jsonb language plpgsql security invoker set search_path = ''
as $$
begin
  if lower(coalesce(p_kind,'')) = 'coin_pack' then
    raise exception 'GOOGLE_PLAY_BILLING_REQUIRED';
  end if;
  -- The previous implementation is intentionally not recreated here. This
  -- legacy digital-order RPC is disabled until an approved billing flow exists.
  raise exception 'CASH_CHECKOUT_DISABLED';
end;
$$;

revoke all on function public.create_blink_paystack_order(text,text)
  from public, anon, authenticated;

-- Gift checkout is a digital coin delivery path and must remain closed even if
-- a future admin enables the broad cash flag used by separate marketplace code.
create or replace function public.create_blink_paystack_gift_order(
  p_username text, p_pack_id text, p_message text default null
) returns jsonb language plpgsql security definer set search_path = ''
as $$
begin
  raise exception 'GOOGLE_PLAY_BILLING_REQUIRED';
end;
$$;

revoke all on function public.create_blink_paystack_gift_order(text,text,text)
  from public, anon, authenticated;
grant execute on function public.create_blink_paystack_gift_order(text,text,text)
  to authenticated;

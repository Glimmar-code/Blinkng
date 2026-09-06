-- Instant/reliable direct-message push delivery.
-- 1) Keep a server-only idempotency record so DB-triggered dispatch and the Android
--    sender fallback can safely race without double-notifying the recipient.
-- 2) Dispatch push from Postgres after a message insert using pg_net. pg_net sends
--    after commit, so send-push-notification can read the newly committed message.

create table if not exists public.message_push_dispatches (
  message_id uuid primary key references public.messages(id) on delete cascade,
  status text not null default 'sending' check (status in ('sending','sent','failed')),
  attempts integer not null default 1 check (attempts >= 1),
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.message_push_dispatches enable row level security;
revoke all on table public.message_push_dispatches from anon, authenticated;
grant all on table public.message_push_dispatches to service_role;

comment on table public.message_push_dispatches is
  'Server-only idempotency state for FCM message push dispatch. Prevents duplicate alerts when both the DB trigger and Android fallback request the same push.';

create or replace function public.dispatch_message_push_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_authorization text;
begin
  begin
    v_authorization := nullif(
      (current_setting('request.headers', true)::jsonb ->> 'authorization'),
      ''
    );
  exception when others then
    v_authorization := null;
  end;

  if v_authorization is not null then
    perform net.http_post(
      url := 'https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/send-push-notification',
      body := jsonb_build_object('message_id', new.id::text),
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'Authorization', v_authorization
      ),
      timeout_milliseconds := 5000
    );
  end if;

  return new;
end;
$$;

drop trigger if exists trg_dispatch_message_push_after_insert on public.messages;
create trigger trg_dispatch_message_push_after_insert
after insert on public.messages
for each row execute function public.dispatch_message_push_after_insert();

-- Disposable PostgreSQL regression fixture for sender gift notifications.
-- NEVER run this test in production: it constructs fixture tables and roles.
\set ON_ERROR_STOP on
create role anon nologin;
create role authenticated nologin;
create schema private;
create table public.blink_coin_gifts (
    id uuid primary key, sender_id uuid not null,
    receiver_id uuid not null, amount bigint not null
);
create table public.notifications (
    id bigint generated always as identity primary key,
    user_id uuid not null, actor_id uuid,
    type text not null, text text,
    sub_text text, target_type text, target_id uuid,
    metadata jsonb
);
\ir ../../supabase/migrations/20261031104500_coin_gift_sender_receipt_notifications.sql
-- Migration reapply must leave exactly one trigger on the ledger.
\ir ../../supabase/migrations/20261031104500_coin_gift_sender_receipt_notifications.sql
insert into public.blink_coin_gifts (id, sender_id, receiver_id, amount)
values (
    '00000000-0000-4000-8000-000000000001',
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    50
);
insert into public.blink_coin_gifts (id, sender_id, receiver_id, amount)
values (
    '00000000-0000-4000-8000-000000000001',
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    50
) on conflict (id) do nothing;
do $$
begin
    if (select count(*) from public.notifications) <> 1 then
       raise exception 'Exactly-once sender receipt failed on replay or migration reapply';
    end if;
    if not exists (
        select 1 from public.notifications
        where user_id = '11111111-1111-4111-8111-111111111111'
          and actor_id = '22222222-2222-4222-8222-222222222222'
          and type = 'tip'
          and target_type = 'coin_gift'
          and target_id = '00000000-0000-4000-8000-000000000001'
          and metadata->>'direction' = 'sent'
          and (metadata->>'amount')::bigint = 50
    ) then
       raise exception 'Receipt user, actor, direction or amount is incorrect';
    end if;
    if has_function_privilege('authenticated',
        'private.notify_blink_coin_gift_sender()', 'EXECUTE') then
       raise exception 'Untrusted authenticated role can execute internal trigger helper';
    end if;
end;
$$;
insert into public.blink_coin_gifts (id, sender_id, receiver_id, amount)
values (
    '00000000-0000-4000-8000-000000000002',
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    100
);
do $$
begin
    if (select count(*) from public.notifications) <> 2 then
        raise exception 'One new receipt expected for every new confirmed gift';
    end if;
end;
$$;

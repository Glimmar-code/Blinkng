begin;
create or replace function public.award_message_points() returns trigger language plpgsql security definer set search_path=public,extensions as $$ begin perform public.award_points(new.sender_id,'message_user',null); return new; end; $$;
drop trigger if exists trg_points_message on public.messages;
create trigger trg_points_message after insert on public.messages for each row execute function public.award_message_points();
commit;

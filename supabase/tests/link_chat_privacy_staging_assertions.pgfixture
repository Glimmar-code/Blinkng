\set ON_ERROR_STOP on
-- Disposable fixture: fake users only, no real Supabase keys or production data.
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE anon NOLOGIN;
CREATE SCHEMA auth;
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $uid$
  SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid
$uid$;
GRANT USAGE ON SCHEMA auth TO authenticated;
GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated;
CREATE TABLE public.profiles(id uuid PRIMARY KEY);
INSERT INTO public.profiles(id) VALUES
 ('11111111-1111-4111-8111-111111111111'),
 ('22222222-2222-4222-8222-222222222222');
\i docs/integration/user-chat-privacy-repair-staging-20261026.sql
-- Reapplying the identical candidate must be safe.
\i docs/integration/user-chat-privacy-repair-staging-20261026.sql
DO $test$
BEGIN
 IF NOT (SELECT rowsecurity FROM pg_tables WHERE schemaname='public' AND tablename='user_chat_privacy') THEN
  RAISE EXCEPTION 'RLS missing on chat privacy';
 END IF;
 IF has_function_privilege('anon','public.update_chat_privacy(text,text,text,boolean,boolean,boolean,boolean,boolean,boolean,boolean,text)','EXECUTE') THEN
  RAISE EXCEPTION 'Public chat settings RPC unexpectedly accessible';
 END IF;
END $test$;
SET ROLE authenticated;
SET request.jwt.claim.sub='11111111-1111-4111-8111-111111111111';
SELECT public.update_chat_privacy('mutuals','followers','nobody',false,false,false,false,false,false,false,'hidden');
DO $test$
DECLARE row public.user_chat_privacy%rowtype;
BEGIN
 SELECT * INTO row FROM public.user_chat_privacy WHERE user_id=auth.uid();
 IF row.who_can_message <> 'mutuals' OR row.who_can_call <> 'followers' OR row.who_can_group_invite <> 'nobody'
    OR row.show_online OR row.send_read_receipts OR row.allow_link_previews
    OR row.notification_preview <> 'hidden' THEN
  RAISE EXCEPTION 'Settings were not persisted accurately';
 END IF;
END $test$;
-- Simulate toggling on account A; verify every unrelated column is unchanged.
SELECT public.update_chat_privacy('mutuals','followers','nobody',false,false,false,false,false,false,true,'hidden');
DO $test$
DECLARE row public.user_chat_privacy%rowtype;
BEGIN
 SELECT * INTO row FROM public.user_chat_privacy WHERE user_id=auth.uid();
 IF row.allow_link_previews IS DISTINCT FROM true OR row.who_can_message <> 'mutuals' OR row.show_online
    OR row.who_can_group_invite <> 'nobody' OR row.notification_preview <> 'hidden' THEN
  RAISE EXCEPTION 'Link toggle altered unrelated privacy settings';
 END IF;
END $test$;
SET request.jwt.claim.sub='22222222-2222-4222-8222-222222222222';
DO $test$
BEGIN
 IF EXISTS (SELECT 1 FROM public.user_chat_privacy) THEN
  RAISE EXCEPTION 'RLS disclosed account A settings to account B';
 END IF;
 UPDATE public.user_chat_privacy SET allow_link_previews=false
 WHERE user_id='11111111-1111-4111-8111-111111111111';
 IF FOUND THEN
  RAISE EXCEPTION 'Cross-account update unexpectedly succeeded';
 END IF;
END $test$;
RESET ROLE;
\echo Chat privacy restoration staging RLS and preference preservation tests passed.

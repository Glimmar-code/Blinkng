-- Read-only catalog exporter for the production Broadcast backend.
-- Recovery utility only. Does not alter schemas, policies, functions, migration history,
-- Storage, Realtime, or cron. Run against the intended database and save the output.
-- Avoid executing reconstructed migration SQL against the already-deployed production DB.

-- 1) Table columns, defaults, nullability
select table_name, column_name, data_type, udt_name, is_nullable, column_default, ordinal_position
from information_schema.columns
where table_schema='public'
  and (table_name like 'broadcast%' or table_name like 'campaign%' or table_name like 'promo%'
       or table_name='notification_prefs' or table_name='device_tokens')
order by table_name, ordinal_position;

-- 2) Constraints (PK/FK/unique/check)
select t.relname as table_name, c.conname, c.contype, pg_get_constraintdef(c.oid) as definition
from pg_constraint c join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace
where n.nspname='public'
  and (t.relname like 'broadcast%' or t.relname like 'campaign%' or t.relname like 'promo%'
       or t.relname='notification_prefs' or t.relname='device_tokens')
order by t.relname,c.conname;

-- 3) Indexes
select tablename,indexname,indexdef from pg_indexes
where schemaname='public'
  and (tablename like 'broadcast%' or tablename like 'campaign%' or tablename like 'promo%'
       or tablename='notification_prefs' or tablename='device_tokens')
order by tablename,indexname;

-- 4) RLS policies
select tablename,policyname,cmd,roles,qual,with_check
from pg_policies
where schemaname='public'
  and (tablename like 'broadcast%' or tablename like 'campaign%' or tablename like 'promo%'
       or tablename='notification_prefs' or tablename='device_tokens')
order by tablename,policyname;

-- 5) Trigger definitions (table triggers plus trigger-function source)
select event_object_table as table_name,trigger_name,event_manipulation,action_timing,
       action_orientation,action_condition,action_statement
from information_schema.triggers
where event_object_schema='public'
  and (event_object_table like 'broadcast%' or event_object_table like 'campaign%'
       or event_object_table like 'promo%' or event_object_table='profiles'
       or event_object_table='marketplace_orders' or event_object_table='contest_votes')
order by event_object_table,trigger_name,event_manipulation;

-- 6) Public Broadcast-related functions. Preserve the complete pg_get_functiondef output.
select p.proname,pg_get_function_identity_arguments(p.oid) as arguments,
       pg_get_functiondef(p.oid) as definition,
       p.prosecdef as security_definer,p.proconfig as function_settings,
       p.proacl as acl
from pg_proc p join pg_namespace n on n.oid=p.pronamespace
where n.nspname='public'
  and (p.proname like '%broadcast%' or p.proname like '%campaign%' or p.proname like '%promo%')
order by p.proname,pg_get_function_identity_arguments(p.oid);

-- 7) Scheduled jobs
select jobid,jobname,schedule,command,active
from cron.job
where jobname ilike '%broadcast%' or command ilike '%broadcast%'
order by jobid;

-- 8) Realtime publication membership
select pubname,schemaname,tablename
from pg_publication_tables
where pubname='supabase_realtime'
  and (tablename like 'broadcast%' or tablename like 'campaign%')
order by tablename;

-- 9) Storage bucket metadata (public access flag, limits)
select id,name,public,file_size_limit,allowed_mime_types
from storage.buckets where id='broadcast-images';

-- 10) Storage policies
select schemaname,tablename,policyname,cmd,roles,qual,with_check
from pg_policies where schemaname='storage' and tablename='objects'
  and policyname ilike '%broadcast%'
order by policyname;

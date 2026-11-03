-- Blink Supabase recovery snapshot verifier
-- READ-ONLY: this script does not create, alter, update, insert, delete, or drop anything.
--
-- Run against the intended Supabase project after restoring/rebuilding the backend.
-- A row with matches_expected = false means the restored/live backend does not match
-- the 2026-09-07 Blink recovery snapshot and must not be promoted without review.

with
funcs as (
  select count(*)::bigint cnt,
         md5(string_agg(
           n.nspname||'.'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')\n'||pg_get_functiondef(p.oid),
           E'\n--NEXT--\n'
           order by n.nspname,p.proname,pg_get_function_identity_arguments(p.oid)
         )) hash
  from pg_proc p
  join pg_namespace n on n.oid=p.pronamespace
  where p.prokind in ('f','p')
    and n.nspname in ('private','private_ranking','public')
),
pols as (
  select count(*)::bigint cnt,
         md5(string_agg(
           schemaname||'.'||tablename||'|'||policyname||'|'||permissive||'|'||
           array_to_string(roles,',')||'|'||cmd||'|'||coalesce(qual,'')||'|'||coalesce(with_check,''),
           E'\n' order by schemaname,tablename,policyname
         )) hash
  from pg_policies
  where schemaname in ('public','storage')
),
grants as (
  select count(*)::bigint cnt,
         md5(string_agg(
           grantee||'|'||table_schema||'.'||table_name||'|'||privilege_type||'|'||is_grantable,
           E'\n' order by grantee,table_schema,table_name,privilege_type
         )) hash
  from information_schema.role_table_grants
  where table_schema in ('public','storage')
    and grantee in ('anon','authenticated','service_role')
),
defacls as (
  select count(*)::bigint cnt,
         md5(coalesce(string_agg(
           pg_get_userbyid(d.defaclrole)||'|'||coalesce(n.nspname,'*')||'|'||
           d.defaclobjtype::text||'|'||array_to_string(d.defaclacl,','),
           E'\n' order by pg_get_userbyid(d.defaclrole),coalesce(n.nspname,'*'),d.defaclobjtype::text
         ),'')) hash
  from pg_default_acl d
  left join pg_namespace n on n.oid=d.defaclnamespace
),
trigs as (
  select count(*)::bigint cnt,
         md5(string_agg(
           n.nspname||'.'||c.relname||'|'||t.tgname||'|'||pg_get_triggerdef(t.oid,true),
           E'\n' order by n.nspname,c.relname,t.tgname
         )) hash
  from pg_trigger t
  join pg_class c on c.oid=t.tgrelid
  join pg_namespace n on n.oid=c.relnamespace
  where not t.tgisinternal
    and n.nspname in ('auth','private','private_ranking','public')
),
views as (
  select count(*)::bigint cnt,
         md5(coalesce(string_agg(
           n.nspname||'.'||c.relname||'|'||pg_get_viewdef(c.oid,true),
           E'\n' order by n.nspname,c.relname
         ),'')) hash
  from pg_class c
  join pg_namespace n on n.oid=c.relnamespace
  where c.relkind in ('v','m')
    and n.nspname in ('private','private_ranking','public')
),
tables as (
  select count(*)::bigint cnt,
         md5(string_agg(n.nspname||'.'||c.relname,E'\n' order by n.nspname,c.relname)) hash
  from pg_class c
  join pg_namespace n on n.oid=c.relnamespace
  where c.relkind='r'
    and n.nspname in ('private','private_ranking','public')
),
cons as (
  select count(*)::bigint cnt,
         md5(string_agg(
           n.nspname||'.'||c.relname||'|'||con.conname||'|'||pg_get_constraintdef(con.oid,true),
           E'\n' order by n.nspname,c.relname,con.conname
         )) hash
  from pg_constraint con
  join pg_class c on c.oid=con.conrelid
  join pg_namespace n on n.oid=c.relnamespace
  where n.nspname in ('private','private_ranking','public')
),
idx as (
  select count(*)::bigint cnt,
         md5(string_agg(
           schemaname||'.'||tablename||'|'||indexname||'|'||indexdef,
           E'\n' order by schemaname,tablename,indexname
         )) hash
  from pg_indexes
  where schemaname in ('private','private_ranking','public')
),
rls as (
  select count(*)::bigint cnt,
         md5(string_agg(
           n.nspname||'.'||c.relname||'|'||c.relrowsecurity::text||'|'||c.relforcerowsecurity::text,
           E'\n' order by n.nspname,c.relname
         )) hash
  from pg_class c
  join pg_namespace n on n.oid=c.relnamespace
  where c.relkind='r'
    and n.nspname in ('private','private_ranking','public','storage')
),
buckets as (
  select count(*)::bigint cnt,
         md5(string_agg(
           id||'|'||name||'|'||public::text||'|'||coalesce(file_size_limit::text,'')||'|'||
           coalesce(array_to_string(allowed_mime_types,','),''),
           E'\n' order by id
         )) hash
  from storage.buckets
),
realtime as (
  select count(*)::bigint cnt,
         md5(coalesce(string_agg(schemaname||'.'||tablename,E'\n' order by schemaname,tablename),'')) hash
  from pg_publication_tables
  where pubname='supabase_realtime'
),
cronjobs as (
  select count(*)::bigint cnt,
         md5(coalesce(string_agg(
           jobname||'|'||schedule||'|'||command||'|'||database||'|'||username||'|'||active::text,
           E'\n' order by jobname
         ),'')) hash
  from cron.job
),
actual as (
  select * from (values
    ('tables',tables.cnt,tables.hash),
    ('constraints',cons.cnt,cons.hash),
    ('indexes',idx.cnt,idx.hash),
    ('functions',funcs.cnt,funcs.hash),
    ('views',views.cnt,views.hash),
    ('triggers',trigs.cnt,trigs.hash),
    ('policies',pols.cnt,pols.hash),
    ('rls_state',rls.cnt,rls.hash),
    ('table_grants',grants.cnt,grants.hash),
    ('default_acls',defacls.cnt,defacls.hash),
    ('storage_buckets',buckets.cnt,buckets.hash),
    ('realtime_tables',realtime.cnt,realtime.hash),
    ('cron_jobs',cronjobs.cnt,cronjobs.hash)
  ) v(area,actual_count,actual_md5)
  from funcs,pols,grants,defacls,trigs,views,tables,cons,idx,rls,buckets,realtime,cronjobs
),
expected as (
  select * from (values
    ('tables',133::bigint,'0c7bf1b2e58fe6ec3346334026a112d4'),
    ('constraints',556::bigint,'055cf21cddf7dbce6763b29b0df3f662'),
    ('indexes',458::bigint,'767ce81f7a8b79e7122bc358d7b618c4'),
    ('functions',320::bigint,'3c4768506ff40a703f3e204d06ca1064'),
    ('views',3::bigint,'b4431e20dc3a047f53bf6caaa3acba62'),
    ('triggers',84::bigint,'b002be5ef1e9fd0a4f812ba24edef7ff'),
    ('policies',310::bigint,'466dc57fbccfa02e751d4ca2789f75cf'),
    ('rls_state',141::bigint,'982f3811309115667c9758d66b164f8e'),
    ('table_grants',1466::bigint,'aef76448ede722e873b9a1086c3f31a2'),
    ('default_acls',30::bigint,'43f70f626d215c5678c3afe433148ce9'),
    ('storage_buckets',10::bigint,'907d7379455b2b826d8f6678a1299da4'),
    ('realtime_tables',32::bigint,'976c3b2c3835b47003bbffafd990420a'),
    ('cron_jobs',9::bigint,'e2c22bd30227b8dd4109acf9f50156ab')
  ) v(area,expected_count,expected_md5)
)
select
  e.area,
  e.expected_count,
  a.actual_count,
  e.expected_md5,
  a.actual_md5,
  (e.expected_count=a.actual_count and e.expected_md5=a.actual_md5) as matches_expected
from expected e
join actual a using(area)
order by e.area;

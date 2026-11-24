-- Safe rollback for Broadcast function recovery.
-- Do not drop function definitions automatically; tables/data remain and dropping may break triggers.
-- To disable the client feature safely, revoke all Broadcast RPC execution from client roles.
DO $$ DECLARE r record; BEGIN
 FOR r IN SELECT p.oid::regprocedure::text sig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND (p.proname LIKE '%broadcast%' OR p.proname LIKE '%campaign%' OR p.proname LIKE '%promo%') LOOP
   EXECUTE 'REVOKE EXECUTE ON FUNCTION '||r.sig||' FROM PUBLIC, anon, authenticated';
 END LOOP;
END $$;

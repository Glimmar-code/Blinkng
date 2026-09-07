-- Mirror production migration 20260907175503.
-- This was an operational cleanup marker after an interrupted schema export.
-- It intentionally leaves no persistent schema object behind; replaying it is a no-op.
select 1;

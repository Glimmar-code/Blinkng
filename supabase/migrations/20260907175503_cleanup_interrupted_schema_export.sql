-- Cleanup from the interrupted 2026-09-07 schema-only recovery export.
-- No production/user data was stored in this temporary table.

drop table if exists public._schema_export_ce4b50d06cdcf588;

-- BLINK Items live-location hardening.
-- Direct table access remains forbidden; authenticated clients use the audited RPC surface only.

create index if not exists live_location_recipients_owner_idx
    on public.live_location_recipients(owner_id, session_id);

create index if not exists live_location_positions_owner_idx
    on public.live_location_positions(owner_id, updated_at desc);

drop policy if exists live_location_sessions_no_direct_access
    on public.live_location_sessions;
create policy live_location_sessions_no_direct_access
on public.live_location_sessions
for all
to authenticated
using (false)
with check (false);

drop policy if exists live_location_recipients_no_direct_access
    on public.live_location_recipients;
create policy live_location_recipients_no_direct_access
on public.live_location_recipients
for all
to authenticated
using (false)
with check (false);

drop policy if exists live_location_positions_no_direct_access
    on public.live_location_positions;
create policy live_location_positions_no_direct_access
on public.live_location_positions
for all
to authenticated
using (false)
with check (false);

revoke all on public.live_location_sessions from public, anon, authenticated;
revoke all on public.live_location_recipients from public, anon, authenticated;
revoke all on public.live_location_positions from public, anon, authenticated;

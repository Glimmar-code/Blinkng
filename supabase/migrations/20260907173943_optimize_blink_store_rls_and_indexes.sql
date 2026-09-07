-- Mirror production migration 20260907173943.
-- Keep Store/VIP ownership policies efficient and add covering indexes for foreign keys.

-- Recreate ownership policies using init-plan friendly auth.uid() evaluation.
drop policy if exists blink_inventory_own_read on public.blink_inventory;
create policy blink_inventory_own_read on public.blink_inventory
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_transactions_own_read on public.blink_coin_transactions;
create policy blink_transactions_own_read on public.blink_coin_transactions
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_activations_own_read on public.blink_item_activations;
create policy blink_activations_own_read on public.blink_item_activations
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_equipped_own_read on public.blink_equipped_items;
create policy blink_equipped_own_read on public.blink_equipped_items
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_vip_passes_own_read on public.blink_vip_passes;
create policy blink_vip_passes_own_read on public.blink_vip_passes
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_vip_benefits_own_read on public.blink_vip_benefit_balances;
create policy blink_vip_benefits_own_read on public.blink_vip_benefit_balances
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_vip_claims_own_read on public.blink_vip_claims;
create policy blink_vip_claims_own_read on public.blink_vip_claims
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_boosts_own_read on public.blink_boosts;
create policy blink_boosts_own_read on public.blink_boosts
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists blink_boost_touches_owner_read on public.blink_boost_touches;
create policy blink_boost_touches_owner_read on public.blink_boost_touches
for select to authenticated using (
  exists (
    select 1
    from public.blink_boosts b
    where b.id = boost_id
      and b.user_id = (select auth.uid())
  )
);

create index if not exists blink_boost_touches_viewer_idx on public.blink_boost_touches(viewer_id);
create index if not exists blink_boosts_inventory_idx on public.blink_boosts(inventory_id);
create index if not exists blink_coin_transactions_catalog_idx on public.blink_coin_transactions(catalog_id);
create index if not exists blink_digital_gifts_inventory_idx on public.blink_digital_gifts(inventory_id);
create index if not exists blink_equipped_items_catalog_idx on public.blink_equipped_items(catalog_id);
create index if not exists blink_equipped_items_inventory_idx on public.blink_equipped_items(inventory_id);
create index if not exists blink_inventory_catalog_idx on public.blink_inventory(catalog_id);
create index if not exists blink_inventory_gifted_by_idx on public.blink_inventory(gifted_by);
create index if not exists blink_item_activations_catalog_idx on public.blink_item_activations(catalog_id);
create index if not exists blink_item_activations_inventory_idx on public.blink_item_activations(inventory_id);
create index if not exists blink_vip_benefit_balances_user_idx on public.blink_vip_benefit_balances(user_id);
create index if not exists blink_vip_claims_pass_idx on public.blink_vip_claims(pass_id);
create index if not exists blink_vip_passes_gifted_by_idx on public.blink_vip_passes(gifted_by);
create index if not exists blink_vip_passes_inventory_idx on public.blink_vip_passes(inventory_id);

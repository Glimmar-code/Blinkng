-- Exact live RLS policy definitions: public tables T-Z.
-- Captured 2026-09-07. Recovery baseline; apply only in an isolated rebuild.

DROP POLICY IF EXISTS token_transactions_select_own ON public.token_transactions;
CREATE POLICY token_transactions_select_own ON public.token_transactions AS PERMISSIVE FOR ALL TO authenticated
USING (((auth.uid() = recipient_id) OR (auth.uid() = sender_id)));

DROP POLICY IF EXISTS user_balances_select_own ON public.user_balances;
CREATE POLICY user_balances_select_own ON public.user_balances AS PERMISSIVE FOR SELECT TO authenticated
USING ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS "devices: owner manage" ON public.user_devices;
CREATE POLICY "devices: owner manage" ON public.user_devices AS PERMISSIVE FOR ALL TO public
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS user_devices_delete_own ON public.user_devices;
CREATE POLICY user_devices_delete_own ON public.user_devices AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS user_devices_insert_own ON public.user_devices;
CREATE POLICY user_devices_insert_own ON public.user_devices AS PERMISSIVE FOR ALL TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS user_devices_select_own ON public.user_devices;
CREATE POLICY user_devices_select_own ON public.user_devices AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS user_devices_update_own ON public.user_devices;
CREATE POLICY user_devices_update_own ON public.user_devices AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id))
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS user_interest_weights_select_own ON public.user_interest_weights;
CREATE POLICY user_interest_weights_select_own ON public.user_interest_weights AS PERMISSIVE FOR SELECT TO authenticated
USING ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS user_settings_delete_own ON public.user_settings;
CREATE POLICY user_settings_delete_own ON public.user_settings AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS user_settings_insert_own ON public.user_settings;
CREATE POLICY user_settings_insert_own ON public.user_settings AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS user_settings_select_own ON public.user_settings;
CREATE POLICY user_settings_select_own ON public.user_settings AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS user_settings_update_own ON public.user_settings;
CREATE POLICY user_settings_update_own ON public.user_settings AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS verif_pay_insert_own ON public.verification_payments;
CREATE POLICY verif_pay_insert_own ON public.verification_payments AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS verif_pay_select_own ON public.verification_payments;
CREATE POLICY verif_pay_select_own ON public.verification_payments AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS verif_delete_own ON public.verification_requests;
CREATE POLICY verif_delete_own ON public.verification_requests AS PERMISSIVE FOR DELETE TO authenticated
USING (((auth.uid() = user_id) AND (status = 'pending'::text)));

DROP POLICY IF EXISTS verif_insert_own ON public.verification_requests;
CREATE POLICY verif_insert_own ON public.verification_requests AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((auth.uid() = user_id) AND (status = 'pending'::text)));

DROP POLICY IF EXISTS verif_select_own ON public.verification_requests;
CREATE POLICY verif_select_own ON public.verification_requests AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS verifications_select_own ON public.verifications;
CREATE POLICY verifications_select_own ON public.verifications AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

-- Exact live RLS policy definitions: public tables G-L.
-- Captured 2026-09-07. Recovery baseline; apply only in an isolated rebuild.

DROP POLICY IF EXISTS game_challenges_select_involved ON public.game_challenges;
CREATE POLICY game_challenges_select_involved ON public.game_challenges AS PERMISSIVE FOR SELECT TO authenticated
USING (((( SELECT auth.uid() AS uid) = challenger_id) OR (( SELECT auth.uid() AS uid) = opponent_id)));

DROP POLICY IF EXISTS game_profiles_select_all ON public.game_profiles;
CREATE POLICY game_profiles_select_all ON public.game_profiles AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS game_rewards_select_own ON public.game_rewards;
CREATE POLICY game_rewards_select_own ON public.game_rewards AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS game_sessions_insert_own ON public.game_sessions;
CREATE POLICY game_sessions_insert_own ON public.game_sessions AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS game_sessions_select_own ON public.game_sessions;
CREATE POLICY game_sessions_select_own ON public.game_sessions AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS housing_agents_delete_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_delete_own ON public.housing_agent_profiles AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS housing_agents_insert_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_insert_own ON public.housing_agent_profiles AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (is_verified = false)));

DROP POLICY IF EXISTS housing_agents_read ON public.housing_agent_profiles;
CREATE POLICY housing_agents_read ON public.housing_agent_profiles AS PERMISSIVE FOR SELECT TO authenticated
USING (((is_active AND is_verified) OR (user_id = ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS housing_agents_update_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_update_own ON public.housing_agent_profiles AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS housing_applications_insert_verified_agent ON public.housing_request_applications;
CREATE POLICY housing_applications_insert_verified_agent ON public.housing_request_applications AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((EXISTS ( SELECT 1
   FROM housing_agent_profiles ha
  WHERE ((ha.id = housing_request_applications.agent_profile_id) AND (ha.user_id = ( SELECT auth.uid() AS uid)) AND ha.is_verified AND ha.is_active))) AND (EXISTS ( SELECT 1
   FROM housing_requests hr
  WHERE ((hr.id = housing_request_applications.housing_request_id) AND (hr.student_id <> ( SELECT auth.uid() AS uid)) AND (hr.status = 'open'::text))))));

DROP POLICY IF EXISTS housing_applications_read_participants ON public.housing_request_applications;
CREATE POLICY housing_applications_read_participants ON public.housing_request_applications AS PERMISSIVE FOR SELECT TO authenticated
USING (((EXISTS ( SELECT 1
   FROM housing_requests hr
  WHERE ((hr.id = housing_request_applications.housing_request_id) AND (hr.student_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles ha
  WHERE ((ha.id = housing_request_applications.agent_profile_id) AND (ha.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS housing_applications_update_participants ON public.housing_request_applications;
CREATE POLICY housing_applications_update_participants ON public.housing_request_applications AS PERMISSIVE FOR UPDATE TO authenticated
USING (((EXISTS ( SELECT 1
   FROM housing_requests hr
  WHERE ((hr.id = housing_request_applications.housing_request_id) AND (hr.student_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles ha
  WHERE ((ha.id = housing_request_applications.agent_profile_id) AND (ha.user_id = ( SELECT auth.uid() AS uid)))))))
WITH CHECK (((EXISTS ( SELECT 1
   FROM housing_requests hr
  WHERE ((hr.id = housing_request_applications.housing_request_id) AND (hr.student_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles ha
  WHERE ((ha.id = housing_request_applications.agent_profile_id) AND (ha.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS housing_requests_insert_own ON public.housing_requests;
CREATE POLICY housing_requests_insert_own ON public.housing_requests AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((student_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS housing_requests_read ON public.housing_requests;
CREATE POLICY housing_requests_read ON public.housing_requests AS PERMISSIVE FOR SELECT TO authenticated
USING (((status = 'open'::text) OR (student_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles a
  WHERE ((a.id = housing_requests.agent_id) AND (a.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS housing_requests_update_participants ON public.housing_requests;
CREATE POLICY housing_requests_update_participants ON public.housing_requests AS PERMISSIVE FOR UPDATE TO authenticated
USING (((student_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles a
  WHERE ((a.id = housing_requests.agent_id) AND (a.user_id = ( SELECT auth.uid() AS uid)) AND a.is_verified)))))
WITH CHECK (((student_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM housing_agent_profiles a
  WHERE ((a.id = housing_requests.agent_id) AND (a.user_id = ( SELECT auth.uid() AS uid)) AND a.is_verified)))));

DROP POLICY IF EXISTS interactions_insert_own ON public.interactions;
CREATE POLICY interactions_insert_own ON public.interactions AS PERMISSIVE FOR ALL TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS interactions_select_all ON public.interactions;
CREATE POLICY interactions_select_all ON public.interactions AS PERMISSIVE FOR ALL TO authenticated
USING (true);

DROP POLICY IF EXISTS leaderboard_snapshots_select_all ON public.leaderboard_snapshots;
CREATE POLICY leaderboard_snapshots_select_all ON public.leaderboard_snapshots AS PERMISSIVE FOR ALL TO authenticated
USING (true);

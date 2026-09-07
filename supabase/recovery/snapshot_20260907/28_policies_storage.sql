-- Exact live RLS policy definitions for storage.objects.
-- Captured 2026-09-07. Recovery baseline; apply only in an isolated rebuild.

DROP POLICY IF EXISTS auth_delete_posts_bucket ON storage.objects;
CREATE POLICY auth_delete_posts_bucket ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'posts'::text) AND (owner = auth.uid())));

DROP POLICY IF EXISTS auth_update_posts_bucket ON storage.objects;
CREATE POLICY auth_update_posts_bucket ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'posts'::text) AND (owner = auth.uid())))
WITH CHECK ((bucket_id = 'posts'::text));

DROP POLICY IF EXISTS auth_upload_posts_bucket ON storage.objects;
CREATE POLICY auth_upload_posts_bucket ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'posts'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS "avatars: public read" ON storage.objects;
CREATE POLICY "avatars: public read" ON storage.objects AS PERMISSIVE FOR SELECT TO anon, authenticated
USING (((bucket_id = 'avatars'::text) AND (name ~~ '%/%'::text)));

DROP POLICY IF EXISTS "avatars: user can delete own" ON storage.objects;
CREATE POLICY "avatars: user can delete own" ON storage.objects AS PERMISSIVE FOR DELETE TO public
USING (((bucket_id = 'avatars'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "avatars: user can update own" ON storage.objects;
CREATE POLICY "avatars: user can update own" ON storage.objects AS PERMISSIVE FOR UPDATE TO public
USING (((bucket_id = 'avatars'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "avatars: user can upload own" ON storage.objects;
CREATE POLICY "avatars: user can upload own" ON storage.objects AS PERMISSIVE FOR INSERT TO public
WITH CHECK (((bucket_id = 'avatars'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS avatars_delete_own ON storage.objects;
CREATE POLICY avatars_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'avatars'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS avatars_insert_own ON storage.objects;
CREATE POLICY avatars_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'avatars'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS avatars_public_read ON storage.objects;
CREATE POLICY avatars_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'avatars'::text));

DROP POLICY IF EXISTS avatars_update_own ON storage.objects;
CREATE POLICY avatars_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'avatars'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'avatars'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS "covers: public read" ON storage.objects;
CREATE POLICY "covers: public read" ON storage.objects AS PERMISSIVE FOR SELECT TO anon, authenticated
USING (((bucket_id = 'covers'::text) AND (name ~~ '%/%'::text)));

DROP POLICY IF EXISTS "covers: user can delete own" ON storage.objects;
CREATE POLICY "covers: user can delete own" ON storage.objects AS PERMISSIVE FOR DELETE TO public
USING (((bucket_id = 'covers'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "covers: user can update own" ON storage.objects;
CREATE POLICY "covers: user can update own" ON storage.objects AS PERMISSIVE FOR UPDATE TO public
USING (((bucket_id = 'covers'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "covers: user can upload own" ON storage.objects;
CREATE POLICY "covers: user can upload own" ON storage.objects AS PERMISSIVE FOR INSERT TO public
WITH CHECK (((bucket_id = 'covers'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS covers_delete_own ON storage.objects;
CREATE POLICY covers_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'covers'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS covers_insert_own ON storage.objects;
CREATE POLICY covers_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'covers'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS covers_public_read ON storage.objects;
CREATE POLICY covers_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'covers'::text));

DROP POLICY IF EXISTS covers_update_own ON storage.objects;
CREATE POLICY covers_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'covers'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'covers'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS marketplace_media_delete_own ON storage.objects;
CREATE POLICY marketplace_media_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'marketplace-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS marketplace_media_insert_own ON storage.objects;
CREATE POLICY marketplace_media_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'marketplace-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS marketplace_media_public_read ON storage.objects;
CREATE POLICY marketplace_media_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'marketplace-media'::text));

DROP POLICY IF EXISTS marketplace_media_update_own ON storage.objects;
CREATE POLICY marketplace_media_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'marketplace-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'marketplace-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS message_media_delete_owner ON storage.objects;
CREATE POLICY message_media_delete_owner ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'message-media'::text) AND ((storage.foldername(name))[1] = 'conversations'::text) AND ((storage.foldername(name))[3] = 'users'::text) AND ((storage.foldername(name))[4] = (auth.uid())::text)));

DROP POLICY IF EXISTS message_media_insert_participant ON storage.objects;
CREATE POLICY message_media_insert_participant ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'message-media'::text) AND ((storage.foldername(name))[1] = 'conversations'::text) AND ((storage.foldername(name))[3] = 'users'::text) AND ((storage.foldername(name))[4] = (auth.uid())::text) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.user_id = auth.uid()) AND ((cp.conversation_id)::text = (storage.foldername(objects.name))[2]))))));

DROP POLICY IF EXISTS message_media_select_participant ON storage.objects;
CREATE POLICY message_media_select_participant ON storage.objects AS PERMISSIVE FOR SELECT TO authenticated
USING (((bucket_id = 'message-media'::text) AND ((storage.foldername(name))[1] = 'conversations'::text) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.user_id = auth.uid()) AND ((cp.conversation_id)::text = (storage.foldername(objects.name))[2]))))));

DROP POLICY IF EXISTS message_media_update_owner ON storage.objects;
CREATE POLICY message_media_update_owner ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'message-media'::text) AND ((storage.foldername(name))[1] = 'conversations'::text) AND ((storage.foldername(name))[3] = 'users'::text) AND ((storage.foldername(name))[4] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'message-media'::text) AND ((storage.foldername(name))[1] = 'conversations'::text) AND ((storage.foldername(name))[3] = 'users'::text) AND ((storage.foldername(name))[4] = (auth.uid())::text)));

DROP POLICY IF EXISTS "post-media: public read" ON storage.objects;
CREATE POLICY "post-media: public read" ON storage.objects AS PERMISSIVE FOR SELECT TO anon, authenticated
USING (((bucket_id = 'post-media'::text) AND (name ~~ '%/%'::text)));

DROP POLICY IF EXISTS "post-media: uploader can delete" ON storage.objects;
CREATE POLICY "post-media: uploader can delete" ON storage.objects AS PERMISSIVE FOR DELETE TO public
USING (((bucket_id = 'post-media'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "post-media: uploader can update" ON storage.objects;
CREATE POLICY "post-media: uploader can update" ON storage.objects AS PERMISSIVE FOR UPDATE TO public
USING (((bucket_id = 'post-media'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS post_media_delete_own ON storage.objects;
CREATE POLICY post_media_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'post-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS post_media_insert_own ON storage.objects;
CREATE POLICY post_media_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'post-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS post_media_public_read ON storage.objects;
CREATE POLICY post_media_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'post-media'::text));

DROP POLICY IF EXISTS post_media_update_own ON storage.objects;
CREATE POLICY post_media_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'post-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'post-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS profile_media_delete_own ON storage.objects;
CREATE POLICY profile_media_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'profile-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS profile_media_insert_own ON storage.objects;
CREATE POLICY profile_media_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'profile-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS profile_media_public_read ON storage.objects;
CREATE POLICY profile_media_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'profile-media'::text));

DROP POLICY IF EXISTS profile_media_update_own ON storage.objects;
CREATE POLICY profile_media_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'profile-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'profile-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS public_read_posts_bucket ON storage.objects;
CREATE POLICY public_read_posts_bucket ON storage.objects AS PERMISSIVE FOR SELECT TO anon, authenticated
USING ((bucket_id = 'posts'::text));

DROP POLICY IF EXISTS "status-media: public read" ON storage.objects;
CREATE POLICY "status-media: public read" ON storage.objects AS PERMISSIVE FOR SELECT TO anon, authenticated
USING (((bucket_id = 'status-media'::text) AND (name ~~ '%/%'::text)));

DROP POLICY IF EXISTS "status-media: uploader can delete" ON storage.objects;
CREATE POLICY "status-media: uploader can delete" ON storage.objects AS PERMISSIVE FOR DELETE TO public
USING (((bucket_id = 'status-media'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS "status-media: uploader can update" ON storage.objects;
CREATE POLICY "status-media: uploader can update" ON storage.objects AS PERMISSIVE FOR UPDATE TO public
USING (((bucket_id = 'status-media'::text) AND ((auth.uid())::text = (storage.foldername(name))[1])));

DROP POLICY IF EXISTS status_media_delete_own ON storage.objects;
CREATE POLICY status_media_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'status-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS status_media_insert_own ON storage.objects;
CREATE POLICY status_media_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'status-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS status_media_public_read ON storage.objects;
CREATE POLICY status_media_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'status-media'::text));

DROP POLICY IF EXISTS status_media_update_own ON storage.objects;
CREATE POLICY status_media_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'status-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'status-media'::text) AND ((storage.foldername(name))[1] = 'users'::text) AND ((storage.foldername(name))[2] = (auth.uid())::text)));

DROP POLICY IF EXISTS story_media_delete_own ON storage.objects;
CREATE POLICY story_media_delete_own ON storage.objects AS PERMISSIVE FOR DELETE TO authenticated
USING (((bucket_id = 'story-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS story_media_insert_own ON storage.objects;
CREATE POLICY story_media_insert_own ON storage.objects AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((bucket_id = 'story-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

DROP POLICY IF EXISTS story_media_public_read ON storage.objects;
CREATE POLICY story_media_public_read ON storage.objects AS PERMISSIVE FOR SELECT TO public
USING ((bucket_id = 'story-media'::text));

DROP POLICY IF EXISTS story_media_update_own ON storage.objects;
CREATE POLICY story_media_update_own ON storage.objects AS PERMISSIVE FOR UPDATE TO authenticated
USING (((bucket_id = 'story-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)))
WITH CHECK (((bucket_id = 'story-media'::text) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

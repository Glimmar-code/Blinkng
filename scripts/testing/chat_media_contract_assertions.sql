-- CI-only contract and access-control checks, no production credentials.
DO $$
DECLARE image_bucket record;
DECLARE normal_bucket record;
BEGIN
    SELECT * INTO image_bucket FROM storage.buckets WHERE id='message-view-once';
    SELECT * INTO normal_bucket FROM storage.buckets WHERE id='message-media';
    IF image_bucket IS NULL OR image_bucket.public OR
        image_bucket.file_size_limit <> 15728640 THEN
        RAISE EXCEPTION 'One-time bucket must be private with 15 MiB limit';
    END IF;
    IF normal_bucket IS NULL OR normal_bucket.public OR
        normal_bucket.file_size_limit <> 83886080 THEN
        RAISE EXCEPTION 'Normal media bucket limits were not applied';
    END IF;
    IF has_function_privilege(
        'authenticated',
        'public.blink_claim_view_once_image(uuid, uuid)', 'EXECUTE') OR
       has_function_privilege(
        'anon',
        'public.blink_claim_view_once_image(uuid, uuid)', 'EXECUTE') OR
       has_function_privilege(
        'authenticated',
        'public.blink_register_view_once_image(uuid, uuid, uuid, text, text)',
        'EXECUTE') THEN
        RAISE EXCEPTION 'One-time RPC leaked to a client role';
    END IF;
END;
$$;

INSERT INTO public.conversation_participants(conversation_id,user_id)
VALUES
('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000002');

INSERT INTO public.messages(id,conversation_id,sender_id,message_type,content,
    image_view_once,deleted_for_everyone)
VALUES
('00000000-0000-4000-8000-000000000011',
 '00000000-0000-4000-8000-000000000010',
 '00000000-0000-4000-8000-000000000001', 'image','View once',true,false),
('00000000-0000-4000-8000-000000000012',
 '00000000-0000-4000-8000-000000000010',
 '00000000-0000-4000-8000-000000000001', 'image','Deleted',true,true);

SET ROLE service_role;
DO $$
DECLARE once_id uuid := '00000000-0000-4000-8000-000000000011';
DECLARE deleted_id uuid := '00000000-0000-4000-8000-000000000012';
DECLARE convo uuid := '00000000-0000-4000-8000-000000000010';
DECLARE sender uuid := '00000000-0000-4000-8000-000000000001';
DECLARE recipient uuid := '00000000-0000-4000-8000-000000000002';
DECLARE outsider uuid := '00000000-0000-4000-8000-000000000003';
DECLARE count_rows int;
BEGIN
    PERFORM public.blink_register_view_once_image(
        once_id,convo,sender,
        'conversations/' || convo::text || '/users/' || sender::text ||
        '/' || once_id::text || '.jpg','image/jpeg');
    PERFORM public.blink_register_view_once_image(
        deleted_id,convo,sender,
        'conversations/' || convo::text || '/users/' || sender::text ||
        '/' || deleted_id::text || '.jpg','image/jpeg');

    SELECT count(*) INTO count_rows
      FROM public.blink_claim_view_once_image(once_id, outsider);
    IF count_rows <> 0 THEN RAISE EXCEPTION 'Outsider claimed a picture'; END IF;

    SELECT count(*) INTO count_rows
      FROM public.blink_claim_view_once_image(once_id, sender);
    IF count_rows <> 0 THEN RAISE EXCEPTION 'Sender claimed a picture'; END IF;

    SELECT count(*) INTO count_rows
      FROM public.blink_claim_view_once_image(deleted_id, recipient);
    IF count_rows <> 0 THEN RAISE EXCEPTION 'Deleted picture was claimable'; END IF;

    SELECT count(*) INTO count_rows
      FROM public.blink_claim_view_once_image(once_id, recipient);
    IF count_rows <> 1 THEN RAISE EXCEPTION 'Recipient first claim failed'; END IF;

    SELECT count(*) INTO count_rows
      FROM public.blink_claim_view_once_image(once_id, recipient);
    IF count_rows <> 0 THEN RAISE EXCEPTION 'Recipient replay was allowed'; END IF;
END;
$$;
RESET ROLE;
SELECT 'Chat private media staging contract passed' AS result;

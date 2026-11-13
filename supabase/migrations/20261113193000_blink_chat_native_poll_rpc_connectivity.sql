-- Restore the native chat-poll RPC contract used by Android and Windows.
-- Additive/replay-tolerant storage setup; no deployment is performed here.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE TABLE IF NOT EXISTS public.blink_chat_polls (
    message_id uuid PRIMARY KEY
        REFERENCES public.messages(id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL
        REFERENCES public.conversations(id) ON DELETE CASCADE,
    created_by uuid NOT NULL
        REFERENCES public.profiles(id),
    question text NOT NULL
        CHECK (char_length(question) BETWEEN 1 AND 200),
    options jsonb NOT NULL
        CHECK (jsonb_typeof(options) = 'array'
            AND jsonb_array_length(options) BETWEEN 2 AND 8),
    created_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.blink_chat_poll_votes (
    message_id uuid NOT NULL
        REFERENCES public.blink_chat_polls(message_id) ON DELETE CASCADE,
    user_id uuid NOT NULL
        REFERENCES public.profiles(id) ON DELETE CASCADE,
    selected_option smallint NOT NULL
        CHECK (selected_option BETWEEN 0 AND 7),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS blink_chat_poll_votes_message_idx
    ON public.blink_chat_poll_votes(message_id);

-- Voter rows and poll definitions are private; clients use only the guarded
-- RPCs below. SECURITY DEFINER functions are the sole authenticated boundary.
ALTER TABLE public.blink_chat_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blink_chat_poll_votes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.blink_chat_polls,
    public.blink_chat_poll_votes FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    public.blink_chat_polls, public.blink_chat_poll_votes TO service_role;

CREATE OR REPLACE FUNCTION public.blink_chat_attach_poll(
    p_message_id uuid,
    p_question text,
    p_options text[]
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_message public.messages%ROWTYPE;
    v_question text := btrim(coalesce(p_question, ''));
    v_option text;
    v_labels text[] := ARRAY[]::text[];
    v_folded text[] := ARRAY[]::text[];
    v_count integer := coalesce(array_length(p_options, 1), 0);
    v_existing public.blink_chat_polls%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'BLINK_CHAT_AUTH_REQUIRED' USING ERRCODE = '28000';
    END IF;
    IF char_length(v_question) NOT BETWEEN 1 AND 200
        OR v_count NOT BETWEEN 2 AND 8 THEN
        RAISE EXCEPTION 'BLINK_CHAT_INVALID_POLL' USING ERRCODE = '22023';
    END IF;

    FOR i IN 1..v_count LOOP
        v_option := btrim(coalesce(p_options[i], ''));
        IF char_length(v_option) NOT BETWEEN 1 AND 80
            OR lower(v_option) = ANY(v_folded) THEN
            RAISE EXCEPTION 'BLINK_CHAT_INVALID_POLL_OPTION'
                USING ERRCODE = '22023';
        END IF;
        v_labels := array_append(v_labels, v_option);
        v_folded := array_append(v_folded, lower(v_option));
    END LOOP;

    -- Polls attach only to a recently sent ordinary text message. Sending
    -- itself continues through the existing chat RPC and authorization rules.
    SELECT m.* INTO v_message
    FROM public.messages AS m
    WHERE m.id = p_message_id
    FOR UPDATE;
    IF NOT FOUND
        OR v_message.sender_id IS DISTINCT FROM v_actor
        OR v_message.deleted_for_everyone IS TRUE
        OR v_message.media_url IS NOT NULL
        OR v_message.message_type NOT IN ('text', 'poll')
        OR btrim(coalesce(v_message.content, '')) <> v_question
        OR v_message.created_at < now() - interval '5 minutes' THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_MESSAGE_INVALID'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.conversation_participants AS cp
        WHERE cp.conversation_id = v_message.conversation_id
          AND cp.user_id = v_actor
          AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_NOT_PARTICIPANT' USING ERRCODE = '42501';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.conversations AS c
        JOIN public.conversation_participants AS other
          ON other.conversation_id = c.id
         AND other.user_id <> v_actor
         AND other.left_at IS NULL
        JOIN public.blocks AS b
          ON (b.blocker_id = v_actor AND b.blocked_id = other.user_id)
          OR (b.blocked_id = v_actor AND b.blocker_id = other.user_id)
        WHERE c.id = v_message.conversation_id
          AND c.is_group IS FALSE
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    SELECT p.* INTO v_existing
    FROM public.blink_chat_polls AS p
    WHERE p.message_id = p_message_id;
    IF FOUND THEN
        IF v_existing.created_by <> v_actor
            OR v_existing.question <> v_question
            OR v_existing.options <> to_jsonb(v_labels) THEN
            RAISE EXCEPTION 'BLINK_CHAT_POLL_ALREADY_ATTACHED'
                USING ERRCODE = '23505';
        END IF;
        RETURN p_message_id;
    END IF;

    INSERT INTO public.blink_chat_polls
        (message_id, conversation_id, created_by, question, options)
    VALUES
        (p_message_id, v_message.conversation_id, v_actor,
         v_question, to_jsonb(v_labels));

    -- Persist native-poll discoverability in the existing message row.
    UPDATE public.messages
    SET message_type = 'poll'
    WHERE id = p_message_id;
    RETURN p_message_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.blink_chat_vote_poll(
    p_message_id uuid,
    p_option integer
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_poll public.blink_chat_polls%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'BLINK_CHAT_AUTH_REQUIRED' USING ERRCODE = '28000';
    END IF;

    -- Serialize concurrent changes for this poll. The (message_id,user_id)
    -- primary key guarantees retries or choice changes remain one ballot.
    SELECT p.* INTO v_poll
    FROM public.blink_chat_polls AS p
    JOIN public.messages AS m ON m.id = p.message_id
    WHERE p.message_id = p_message_id
      AND m.deleted_for_everyone IS NOT TRUE
    FOR UPDATE OF p;
    IF NOT FOUND
        OR v_poll.closed_at IS NOT NULL
        OR p_option IS NULL
        OR p_option < 0
        OR p_option >= jsonb_array_length(v_poll.options) THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_UNAVAILABLE' USING ERRCODE = '22023';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.conversation_participants AS cp
        WHERE cp.conversation_id = v_poll.conversation_id
          AND cp.user_id = v_actor
          AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_NOT_PARTICIPANT' USING ERRCODE = '42501';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.conversations AS c
        JOIN public.conversation_participants AS other
          ON other.conversation_id = c.id
         AND other.user_id <> v_actor
         AND other.left_at IS NULL
        JOIN public.blocks AS b
          ON (b.blocker_id = v_actor AND b.blocked_id = other.user_id)
          OR (b.blocked_id = v_actor AND b.blocker_id = other.user_id)
        WHERE c.id = v_poll.conversation_id
          AND c.is_group IS FALSE
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.blink_chat_poll_votes
        (message_id, user_id, selected_option)
    VALUES (p_message_id, v_actor, p_option)
    ON CONFLICT (message_id, user_id)
    DO UPDATE SET
        selected_option = EXCLUDED.selected_option,
        updated_at = CASE
            WHEN public.blink_chat_poll_votes.selected_option
                IS DISTINCT FROM EXCLUDED.selected_option THEN now()
            ELSE public.blink_chat_poll_votes.updated_at
        END;
    RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION public.blink_chat_poll_results(p_message_id uuid)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor uuid := auth.uid();
    v_poll public.blink_chat_polls%ROWTYPE;
    v_options jsonb;
    v_choice smallint;
    v_total bigint;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'BLINK_CHAT_AUTH_REQUIRED' USING ERRCODE = '28000';
    END IF;

    SELECT p.* INTO v_poll
    FROM public.blink_chat_polls AS p
    JOIN public.messages AS m ON m.id = p.message_id
    WHERE p.message_id = p_message_id
      AND m.deleted_for_everyone IS NOT TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_UNAVAILABLE' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM public.conversation_participants AS cp
        WHERE cp.conversation_id = v_poll.conversation_id
          AND cp.user_id = v_actor
          AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_UNAVAILABLE' USING ERRCODE = '42501';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.conversations AS c
        JOIN public.conversation_participants AS other
          ON other.conversation_id = c.id
         AND other.user_id <> v_actor
         AND other.left_at IS NULL
        JOIN public.blocks AS b
          ON (b.blocker_id = v_actor AND b.blocked_id = other.user_id)
          OR (b.blocked_id = v_actor AND b.blocker_id = other.user_id)
        WHERE c.id = v_poll.conversation_id
          AND c.is_group IS FALSE
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    SELECT v.selected_option INTO v_choice
    FROM public.blink_chat_poll_votes AS v
    WHERE v.message_id = p_message_id AND v.user_id = v_actor;

    SELECT count(*) INTO v_total
    FROM public.blink_chat_poll_votes AS v
    WHERE v.message_id = p_message_id;

    SELECT jsonb_agg(
        jsonb_build_object(
            'label', v_poll.options ->> i.idx,
            'votes', (
                SELECT count(*)
                FROM public.blink_chat_poll_votes AS v
                WHERE v.message_id = p_message_id
                  AND v.selected_option = i.idx::smallint
            )
        ) ORDER BY i.idx
    ) INTO v_options
    FROM generate_series(0, jsonb_array_length(v_poll.options) - 1)
        AS i(idx);

    -- This shape is consumed verbatim by both native clients; all counts and
    -- the caller's selection are calculated from server-side vote rows.
    RETURN jsonb_build_object(
        'message_id', p_message_id,
        'question', v_poll.question,
        'options', v_options,
        'total_votes', v_total,
        'my_vote', v_choice,
        'closed', v_poll.closed_at IS NOT NULL
    );
END;
$$;

REVOKE ALL ON FUNCTION
    public.blink_chat_attach_poll(uuid, text, text[]),
    public.blink_chat_vote_poll(uuid, integer),
    public.blink_chat_poll_results(uuid)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION
    public.blink_chat_attach_poll(uuid, text, text[]),
    public.blink_chat_vote_poll(uuid, integer),
    public.blink_chat_poll_results(uuid)
TO authenticated;

COMMIT;

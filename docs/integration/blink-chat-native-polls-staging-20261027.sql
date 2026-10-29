-- BLINK NATIVE CHAT POLLS: TESTLAB/STAGING CANDIDATE ONLY — 2026-10-27
-- Not a production migration. Never run in production until disposable-DB,
-- privacy, messaging and Android/Windows E2E tests are green.
--
-- Backwards-compatible v1: publish a normal *authorized* text message with
-- question as its content, then attach a poll to that message via RPC. Older
-- clients display the question as text; newer clients render an interactive
-- poll. This does not bypass BLINK's message-send authorization or outbox.
-- One choice per member; server-authoritative vote changes are idempotent.
BEGIN;

CREATE TABLE public.blink_chat_polls (
    message_id uuid PRIMARY KEY REFERENCES public.messages(id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    created_by uuid NOT NULL REFERENCES public.profiles(id),
    question text NOT NULL CHECK (char_length(question) BETWEEN 1 AND 200),
    options jsonb NOT NULL CHECK (
        jsonb_typeof(options) = 'array'
        AND jsonb_array_length(options) BETWEEN 2 AND 8
    ),
    created_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz
);

CREATE TABLE public.blink_chat_poll_votes (
    message_id uuid NOT NULL REFERENCES public.blink_chat_polls(message_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    selected_option smallint NOT NULL CHECK (selected_option BETWEEN 0 AND 7),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
CREATE INDEX blink_chat_poll_votes_message_idx
    ON public.blink_chat_poll_votes(message_id);

-- No direct untrusted table reads, writes, or voter identity enumeration.
-- Function results expose counts and only the caller's own choice.
ALTER TABLE public.blink_chat_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blink_chat_poll_votes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.blink_chat_polls, public.blink_chat_poll_votes
    FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    public.blink_chat_polls, public.blink_chat_poll_votes TO service_role;

-- Private helper: for one-to-one threads respect either participant's block
-- decision. Being left in a historical conversation must never grant renewed
-- access to its poll results after a block.
CREATE FUNCTION private.blink_chat_poll_direct_blocked(
    p_conversation_id uuid, p_actor uuid
) RETURNS boolean
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = ''
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.conversations c
        JOIN public.conversation_participants other
          ON other.conversation_id = c.id
         AND other.user_id <> p_actor
         AND other.left_at IS NULL
        JOIN public.blocks b
          ON ((b.blocker_id = p_actor AND b.blocked_id = other.user_id)
           OR (b.blocked_id = p_actor AND b.blocker_id = other.user_id))
        WHERE c.id = p_conversation_id
          AND c.is_group IS FALSE
    );
$$;
REVOKE ALL ON FUNCTION private.blink_chat_poll_direct_blocked(uuid, uuid)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.blink_chat_poll_direct_blocked(uuid, uuid)
    TO service_role;

CREATE FUNCTION public.blink_chat_attach_poll(
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
    IF char_length(v_question) NOT BETWEEN 1 AND 200 OR v_count NOT BETWEEN 2 AND 8 THEN
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

    -- Requires an existing message sent through the standard authorized
    -- transport. Poll attachment cannot send messages or bypass block rules.
    SELECT * INTO v_message FROM public.messages
    WHERE id = p_message_id FOR UPDATE;
    IF NOT FOUND OR v_message.sender_id IS DISTINCT FROM v_actor
        OR v_message.deleted_for_everyone IS TRUE
        OR v_message.media_url IS NOT NULL
        OR v_message.message_type NOT IN ('text', 'poll')
        OR btrim(coalesce(v_message.content, '')) <> v_question
        OR v_message.created_at < now() - interval '5 minutes' THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_MESSAGE_INVALID'
            USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = v_message.conversation_id
          AND cp.user_id = v_actor AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_NOT_PARTICIPANT' USING ERRCODE = '42501';
    END IF;

    IF private.blink_chat_poll_direct_blocked(v_message.conversation_id, v_actor) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_existing FROM public.blink_chat_polls
        WHERE message_id = p_message_id;
    IF FOUND THEN
        IF v_existing.created_by <> v_actor
            OR v_existing.question <> v_question
            OR v_existing.options <> to_jsonb(v_labels) THEN
            RAISE EXCEPTION 'BLINK_CHAT_POLL_ALREADY_ATTACHED' USING ERRCODE = '23505';
        END IF;
        RETURN p_message_id;
    END IF;

    INSERT INTO public.blink_chat_polls
        (message_id, conversation_id, created_by, question, options)
    VALUES (p_message_id, v_message.conversation_id, v_actor,
            v_question, to_jsonb(v_labels));
    -- Mark this as a native poll so authenticated clients fetch actual results;
    -- older clients can still show the question as text.
    UPDATE public.messages SET message_type='poll' WHERE id=p_message_id;
    RETURN p_message_id;
END;
$$;

CREATE FUNCTION public.blink_chat_vote_poll(
    p_message_id uuid, p_option integer
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
    SELECT p.* INTO v_poll FROM public.blink_chat_polls p
    JOIN public.messages m ON m.id = p.message_id
    WHERE p.message_id = p_message_id
      AND m.deleted_for_everyone IS NOT TRUE
    FOR UPDATE OF p;
    IF NOT FOUND OR v_poll.closed_at IS NOT NULL
        OR p_option IS NULL
        OR p_option < 0
        OR p_option >= jsonb_array_length(v_poll.options) THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_UNAVAILABLE' USING ERRCODE = '22023';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = v_poll.conversation_id
          AND cp.user_id = v_actor AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_NOT_PARTICIPANT' USING ERRCODE = '42501';
    END IF;
    IF private.blink_chat_poll_direct_blocked(v_poll.conversation_id, v_actor) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.blink_chat_poll_votes
        (message_id, user_id, selected_option)
    VALUES (p_message_id, v_actor, p_option)
    ON CONFLICT (message_id, user_id)
    DO UPDATE SET selected_option = EXCLUDED.selected_option,
        updated_at = CASE
            WHEN public.blink_chat_poll_votes.selected_option
                IS DISTINCT FROM EXCLUDED.selected_option THEN now()
            ELSE public.blink_chat_poll_votes.updated_at
        END;
    RETURN true;
END;
$$;

CREATE FUNCTION public.blink_chat_poll_results(p_message_id uuid)
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
    SELECT p.* INTO v_poll FROM public.blink_chat_polls p
    JOIN public.messages m ON m.id = p.message_id
    WHERE p.message_id = p_message_id
      AND m.deleted_for_everyone IS NOT TRUE;
    IF NOT FOUND OR NOT EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = v_poll.conversation_id
          AND cp.user_id = v_actor AND cp.left_at IS NULL
    ) THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_UNAVAILABLE' USING ERRCODE = '42501';
    END IF;

    IF private.blink_chat_poll_direct_blocked(v_poll.conversation_id, v_actor) THEN
        RAISE EXCEPTION 'BLINK_CHAT_BLOCKED' USING ERRCODE = '42501';
    END IF;

    SELECT selected_option INTO v_choice FROM public.blink_chat_poll_votes
    WHERE message_id = p_message_id AND user_id = v_actor;
    SELECT count(*) INTO v_total FROM public.blink_chat_poll_votes
    WHERE message_id = p_message_id;
    SELECT jsonb_agg(
        jsonb_build_object(
            'label', v_poll.options->>idx,
            'votes', (SELECT count(*) FROM public.blink_chat_poll_votes v
                      WHERE v.message_id = p_message_id AND v.selected_option = idx)
        ) ORDER BY idx
    ) INTO v_options
    FROM generate_series(0, jsonb_array_length(v_poll.options) - 1) idx;

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

CREATE FUNCTION public.blink_chat_close_poll(p_message_id uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
    v_actor uuid := auth.uid();
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'BLINK_CHAT_AUTH_REQUIRED' USING ERRCODE = '28000';
    END IF;
    UPDATE public.blink_chat_polls p SET closed_at = coalesce(closed_at, now())
    WHERE p.message_id = p_message_id AND p.created_by = v_actor
      AND EXISTS (
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id = p.conversation_id AND cp.user_id = v_actor
          AND cp.left_at IS NULL
      );
    IF NOT FOUND THEN
        RAISE EXCEPTION 'BLINK_CHAT_POLL_CLOSE_DENIED' USING ERRCODE = '42501';
    END IF;
    RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION
    public.blink_chat_attach_poll(uuid, text, text[]),
    public.blink_chat_vote_poll(uuid, integer),
    public.blink_chat_poll_results(uuid),
    public.blink_chat_close_poll(uuid)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION
    public.blink_chat_attach_poll(uuid, text, text[]),
    public.blink_chat_vote_poll(uuid, integer),
    public.blink_chat_poll_results(uuid),
    public.blink_chat_close_poll(uuid)
TO authenticated;
COMMIT;

-- REQUIRED BEFORE DEPLOYMENT (not executed in this PR):
-- 1. Isolated PostgreSQL with realistic auth.uid() fixtures, 2 active members,
--    departed member, blocked user and 2 unrelated conversations.
-- 2. Original message created through BLINK's ordinary message-send RPC,
--    not by the poll function. Prove denial if the underlying send is blocked.
-- 3. Repeated attach of same payload accepted; changed payload rejected.
-- 4. Two to eight unique options; reject duplicate/casefold/oversized options.
-- 5. Concurrent vote changes, idempotent retry and exactly one vote/user.
-- 6. Only current members can vote/read; unrelated/anonymous/departed denied.
-- 6a. Direct-message participants blocked in EITHER direction cannot attach,
--     read or vote, even when membership history still contains them.
-- 7. Close makes voting fail; delete-for-everyone hides results.
-- 8. No directly exposed voter IDs, public SELECT, or unauthenticated RPC.
-- 9. Android + Windows client UI must display native poll instead of plain
--    text with a loading/failure fallback and vote progress sync.
-- 10. Rollback preserves existing messages and removes new poll tables only
--     after written data-retention review. Never truncate user data.

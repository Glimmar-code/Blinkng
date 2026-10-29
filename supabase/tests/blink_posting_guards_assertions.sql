-- Test as a simulated authenticated user, never a live account.
set role authenticated;
select set_config('request.jwt.claim.sub','30000000-0000-0000-0000-000000000001',false);

-- Valid future poll is accepted.
insert into public.poll_votes (id,poll_id,option_id,user_id) values
('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001');

-- Old polls without expiration remain votable.
insert into public.poll_votes (id,poll_id,option_id,user_id) values
('40000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003',
 '20000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000001');

do $test$
begin
    -- Expired poll is blocked.
    begin
        insert into public.poll_votes values
        ('40000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000002',
         '20000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001');
        raise exception 'Expired vote unexpectedly accepted';
    exception when sqlstate '23514' then null;
    end;

    -- Cross-poll option is blocked.
    begin
        insert into public.poll_votes values
        ('40000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001',
         '20000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000001');
        raise exception 'Cross-poll option unexpectedly accepted';
    exception when sqlstate '23514' then null;
    end;

    -- A client cannot vote as a different user.
    begin
        insert into public.poll_votes values
        ('40000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000001',
         '20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000002');
        raise exception 'Forged voter unexpectedly accepted';
    exception when sqlstate '42501' then null;
    end;

    if has_function_privilege('authenticated', 'public.blink_enforce_poll_vote_deadline()', 'execute') then
        raise exception 'Poll guard improperly exposed as direct callable API';
    end if;
end
$test$;

reset role;

-- Private audience cannot be inserted until all storage and RPC paths are protected.
insert into public.feed_posts (id,audience)
values ('50000000-0000-0000-0000-000000000001','Everyone');
do $test$
begin
    begin
        insert into public.feed_posts values ('50000000-0000-0000-0000-000000000002','Only me');
        raise exception 'Unsafe private audience unexpectedly accepted';
    exception when check_violation then null;
    end;
    begin
        insert into public.feed_posts values ('50000000-0000-0000-0000-000000000003','PRIVATE');
        raise exception 'Upper-case unsafe private audience unexpectedly accepted';
    exception when check_violation then null;
    end;
    if (select count(*) from public.poll_votes) <> 2 then
        raise exception 'Unexpected test vote count';
    end if;
end
$test$;
select 'BLINK posting guards isolated tests passed' as result;

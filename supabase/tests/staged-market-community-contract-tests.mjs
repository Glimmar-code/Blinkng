import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';

async function rejects(action, code) {
  let error = null;
  try { await action(); } catch (cause) { error = cause; }
  assert.ok(error, 'Expected security rule to reject the operation');
  assert.match(error.message, new RegExp(code));
}

export async function stagedMarketCommunityContractTests() {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create role service_role bypassrls;
      create schema auth;
      create table auth.users(id uuid primary key, email text);
      create table public.profiles(
        id uuid primary key, username text, full_name text, avatar_url text
      );
      create table public.blocks(blocker_id uuid,blocked_id uuid);
      create function auth.uid() returns uuid language sql stable as
        $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
      grant usage on schema auth, public to authenticated, service_role;
      grant execute on function auth.uid() to authenticated, service_role;
      create table public.feed_posts (
        id uuid primary key default gen_random_uuid(),
        category text not null default 'Campus Life',
        is_sponsored boolean not null default false,
        ad_label text,
        text text
      );
      grant select, insert, update, delete on public.feed_posts to authenticated, service_role;
      create table public.conversations (
        id uuid primary key, title text, description text, avatar_url text,
        is_group boolean not null default false, created_by uuid,
        updated_at timestamptz not null default now()
      );
      create table public.conversation_participants (
        conversation_id uuid references public.conversations(id), user_id uuid,
        left_at timestamptz, is_admin boolean not null default false,
        joined_at timestamptz not null default now(), last_read_at timestamptz
      );
    `);

    const market = fs.readFileSync(
      new URL('../../docs/integration/marketplace-feed-server-policy-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(market);

    await db.exec(`set role authenticated`);
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text) values ('Marketplace','For sale')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_label)
      values ('Campus Life',true,'Sponsored')`), 'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await db.exec(`
      insert into public.feed_posts(category,text) values ('Campus Life','Discussion of a product')`);
    await rejects(() => db.exec(`
      update public.feed_posts set category='Market' where category='Campus Life'`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      update public.feed_posts set is_sponsored=true where category='Campus Life'`),
      'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await db.exec('reset role');
    await db.exec('set role service_role');
    await db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_label)
      values ('Marketplace',true,'Sponsored')`);
    await db.exec('reset role');
    console.log('PASS staged feed category and server-only paid-promotion enforcement');

    const owner = '11111111-1111-4111-8111-111111111111';
    const other = '22222222-2222-4222-8222-222222222222';
    await db.exec(`
      insert into auth.users(id,email) values
      ('${owner}','owner@example.test'),('${other}','reader@example.test');
      insert into public.profiles(id,username) values
      ('${owner}','testowner'),('${other}','testreader')
    `);
    const groupCommunity = fs.readFileSync(
      new URL('../../docs/integration/groups-communities-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(groupCommunity);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await db.exec(`
      insert into public.blink_communities
        (id,owner_id,name,university,category,visibility)
      values
        ('33333333-3333-4333-8333-333333333333','${owner}','Study Circle','FUTA','Academic','private'),
        ('44444444-4444-4444-8444-444444444444','${owner}','Open Campus','FUTA','Campus','public')`);
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const count = await db.query('select count(*)::integer as n from public.blink_communities');
    assert.equal(count.rows[0].n, 1, 'Other users must not read private owner-only groups');
    await rejects(() => db.exec(`
      insert into public.blink_communities(owner_id,name,university,category)
      values ('${owner}','Fake Community','FUTA','Campus')`),
      'row-level security');
    await rejects(() => db.exec(`
      select public.blink_community_join('33333333-3333-4333-8333-333333333333')`),
      'COMMUNITY_INVITE_REQUIRED');
    const requested = await db.query(`
      select public.blink_community_request_join(
      '33333333-3333-4333-8333-333333333333') as status`);
    assert.equal(requested.rows[0].status, 'pending');
    await rejects(() => db.exec(`
      select public.blink_community_join('33333333-3333-4333-8333-333333333333')`),
      'COMMUNITY_INVITE_REQUIRED');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await rejects(() => db.exec(`
      select public.blink_community_moderate(
        '33333333-3333-4333-8333-333333333333',
        '${owner}','ban')`), 'CANNOT_MODERATE_OWNER_OR_SELF');
    const invitation = await db.query(`
      select public.blink_community_invite(
        '33333333-3333-4333-8333-333333333333','${other}') as status`);
    assert.equal(invitation.rows[0].status, 'invited');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const joined = await db.query(`
      select public.blink_community_join('33333333-3333-4333-8333-333333333333') as status`);
    assert.equal(joined.rows[0].status, 'active');
    const memberView = await db.query(`
      select id from public.blink_communities
      where id='33333333-3333-4333-8333-333333333333'`);
    assert.equal(memberView.rows.length, 1, 'Invited member can view private community');
    await rejects(() => db.exec(`
      select public.blink_community_moderate(
        '33333333-3333-4333-8333-333333333333','${owner}','ban')`),
      'COMMUNITY_ADMIN_REQUIRED');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const banned = await db.query(`
      select public.blink_community_moderate(
        '33333333-3333-4333-8333-333333333333',
        '${other}','ban') as applied`);
    assert.equal(banned.rows[0].applied, true);
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      select public.blink_community_join('33333333-3333-4333-8333-333333333333')`),
      'COMMUNITY_MEMBERSHIP_BANNED');
    const bannedView = await db.query(`
      select id from public.blink_communities
      where id='33333333-3333-4333-8333-333333333333'`);
    assert.equal(bannedView.rows.length, 0, 'Banned member has no private read access');
    await db.exec('reset role');
    // Independent owner-approved private request, including real roster rows.
    const applicant = '99999999-9999-4999-8999-999999999999';
    const requestClub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    await db.exec(`
      insert into auth.users(id,email) values
        ('${applicant}','applicant@example.test');
      insert into public.profiles(id,username,full_name) values
        ('${applicant}','requestedstudent','Requested Student');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await db.exec(`
      insert into public.blink_communities(id,owner_id,name,university,category,visibility)
      values('${requestClub}','${owner}','Requests Club','FUTA','Academic','private')`);
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    const requestStatus = await db.query(`
      select public.blink_community_request_join('${requestClub}') as status`);
    assert.equal(requestStatus.rows[0].status,'pending');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const pendingRows = await db.query(`
      select user_id,status from public.blink_community_members_page('${requestClub}',50)`);
    assert.equal(pendingRows.rows.length,1);
    assert.equal(pendingRows.rows[0].status,'pending');
    const approved = await db.query(`
      select public.blink_community_moderate('${requestClub}','${applicant}','approve') as accepted`);
    assert.equal(approved.rows[0].accepted,true);
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    const approvedView = await db.query(`
      select id from public.blink_communities where id='${requestClub}'`);
    assert.equal(approvedView.rows.length,1,'Approved applicant can view private community');
    await rejects(() => db.exec(`
      select * from public.blink_community_members_page('${requestClub}',50)`),
      'COMMUNITY_ADMIN_REQUIRED');
    await db.exec('reset role');
    console.log('PASS staged requests, owner-only pending roster, approval and private visibility');
    console.log('PASS staged invite/join/private read/moderation/ban RLS');

    // A group is listed exactly once for an active member even when 2+
    // participants exist. Leaving removes it from that person's list.
    const group = '55555555-5555-4555-8555-555555555555';
    await db.exec(`
      insert into public.conversations(id,title,is_group)
        values ('${group}','FUTA Readers',true);
      insert into public.conversation_participants(conversation_id,user_id)
        values ('${group}','${owner}'),('${group}','${other}');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const mine = await db.query('select group_id,title,member_count from public.blink_my_group_conversations(50)');
    assert.equal(mine.rows.length, 1);
    assert.equal(mine.rows[0].title, 'FUTA Readers');
    assert.equal(Number(mine.rows[0].member_count), 2);
    await db.exec('reset role');
    await db.exec(`update public.conversation_participants
      set left_at=now() where conversation_id='${group}' and user_id='${other}'`);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const departed = await db.query('select group_id from public.blink_my_group_conversations(50)');
    assert.equal(departed.rows.length, 0, 'Departed members cannot list groups');
    await db.exec('reset role');
    console.log('PASS staged one-row group inbox and departed-member suppression');

    // Model currently deployed permissive membership policies, apply the
    // proposed replacement policies locally, and assert authenticated users
    // cannot self-invite, self-promote or keep reading after leaving.
    await db.exec(`
      create table public.messages (
        id uuid primary key default gen_random_uuid(),
        conversation_id uuid references public.conversations(id),
        sender_id uuid, content text, message_type text default 'text',
        created_at timestamptz default now(),
        delivered_at timestamptz, is_read boolean default false,
        read_at timestamptz, deleted_for_everyone boolean default false
      );
      grant select,insert,update on public.conversation_participants to authenticated;
      grant select,update on public.conversations to authenticated;
      grant select,insert on public.messages to authenticated;
      alter table public.conversations enable row level security;
      alter table public.conversation_participants enable row level security;
      alter table public.messages enable row level security;
      create policy cp_select_own on public.conversation_participants
        for select to authenticated using (user_id=auth.uid());
      create policy cp_update_own on public.conversation_participants
        for update to authenticated using (user_id=auth.uid())
        with check (user_id=auth.uid());
      create policy cp_insert_own on public.conversation_participants
        for insert to authenticated with check (user_id=auth.uid());
      create policy conv_select_member on public.conversations
        for select to authenticated using (exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id=conversations.id and cp.user_id=auth.uid()
        ));
      create policy conv_update_member on public.conversations
        for update to authenticated using (exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id=conversations.id and cp.user_id=auth.uid()
        ));
      create policy messages_select_member on public.messages
        for select to authenticated using (exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id=messages.conversation_id
            and cp.user_id=auth.uid()
        ));
      create policy messages_insert_member on public.messages
        for insert to authenticated with check (
          sender_id=auth.uid() and exists (
            select 1 from public.conversation_participants cp
            where cp.conversation_id=messages.conversation_id
              and cp.user_id=auth.uid()
          )
        );
    `);
    const hardening = fs.readFileSync(
      new URL('../../docs/integration/group-membership-security-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(hardening);
    const receiptGuard = fs.readFileSync(
      new URL('../../docs/integration/departed-member-rpc-hardening-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(receiptGuard);

    const privateGroup = '66666666-6666-4666-8666-666666666666';
    const dm = '77777777-7777-4777-8777-777777777777';
    const unrelatedDm = '55555555-aaaa-4555-8555-555555555555';
    await db.exec(`
      insert into public.conversations(id,title,is_group,created_by) values
        ('${privateGroup}','Private Readers',true,'${owner}'),
        ('${dm}','Legacy Direct',false,'${other}'),
        ('${unrelatedDm}','Different Owners Direct',false,'${owner}');
      insert into public.conversation_participants(conversation_id,user_id)
        values ('${privateGroup}','${owner}'),
               ('${unrelatedDm}','${owner}');
      insert into public.messages(id,conversation_id,sender_id,content)
        values ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee','${group}','${owner}','Group history');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      insert into public.conversation_participants(conversation_id,user_id)
      values ('${privateGroup}','${other}')`), 'row-level security');
    await rejects(() => db.exec(`
      insert into public.conversation_participants(conversation_id,user_id)
      values ('${unrelatedDm}','${other}')`), 'row-level security');
    await db.exec(`
      insert into public.conversation_participants(conversation_id,user_id)
      values ('${dm}','${other}')`);
    await rejects(() => db.exec(`
      update public.conversation_participants set is_admin=true
        where conversation_id='${group}' and user_id='${other}'`),
      'BLINK_MEMBERSHIP_CHANGE_REQUIRES_SERVER');

    await db.exec(`
      update public.conversation_participants set left_at=now()
        where conversation_id='${group}' and user_id='${other}'`);
    await rejects(() => db.exec(`
      update public.conversation_participants set left_at=null
        where conversation_id='${group}' and user_id='${other}'`),
      'BLINK_MEMBERSHIP_CHANGE_REQUIRES_SERVER');
    const groupView = await db.query(`
      select id from public.conversations where id='${group}'`);
    assert.equal(groupView.rows.length, 0, 'Departed user cannot view group');
    const messageView = await db.query(`
      select id from public.messages where conversation_id='${group}'`);
    assert.equal(messageView.rows.length, 0, 'Departed user cannot read old group messages');
    const departedUnread = await db.query(`
      select message_id from public.get_my_unread_message_notifications(20)`);
    assert.equal(departedUnread.rows.length,0,'Departed member cannot read unread content via definer RPC');
    const departedAck = await db.query(`
      select public.ack_message_delivered(
        'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee') as ok`);
    assert.equal(departedAck.rows[0].ok,false,'Departed member cannot acknowledge group message');
    const departedBatch = await db.query(
      'select public.ack_pending_message_deliveries() as n');
    assert.equal(Number(departedBatch.rows[0].n),0,'Departed member must not mark group deliveries');
    await rejects(() => db.exec(`
      insert into public.messages(conversation_id,sender_id,content)
      values ('${group}','${other}','Unauthorized after leaving')`),
      'row-level security');
    await rejects(() => db.exec(`
      select public.send_group_message('${group}','Unauthorized after leaving')`),
      'NOT_AN_ACTIVE_GROUP_MEMBER');
    await db.exec('reset role');
    // Two active group members, but only the administrator may change title.
    const managed = '88888888-8888-4888-8888-888888888888';
    await db.exec(`
      insert into public.conversations(id,title,is_group,created_by)
      values ('${managed}','Member Club',true,'${owner}');
      insert into public.conversation_participants(conversation_id,user_id,is_admin)
      values ('${managed}','${owner}',true),
             ('${managed}','${other}',false);
      insert into public.messages(id,conversation_id,sender_id,content)
        values ('ffffffff-ffff-4fff-8fff-ffffffffffff','${managed}','${owner}','Active group alert');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      update public.conversations set title='Hijacked Club' where id='${managed}'`),
      'BLINK_GROUP_ADMIN_REQUIRED');
    await rejects(() => db.exec(`
      update public.conversations set is_group=false where id='${managed}'`),
      'BLINK_GROUP_IDENTITY_REQUIRES_SERVER');
    await db.exec(`
      select set_config('request.jwt.claim.sub','${other}',false)`);
    const groupRoster = await db.query(`
      select user_id,is_admin from public.blink_group_members('${managed}')`);
    assert.equal(groupRoster.rows.length, 2, 'Active group user can view unique roster rows');
    const activeUnread = await db.query(`
      select message_id from public.get_my_unread_message_notifications(20)`);
    assert.equal(activeUnread.rows.length,1,'Active group member retains unread access');
    const activeAck = await db.query(`
      select public.ack_message_delivered(
        'ffffffff-ffff-4fff-8fff-ffffffffffff') as ok`);
    assert.equal(activeAck.rows[0].ok,true,'Active group recipient retains delivery acknowledgement');
    await rejects(() => db.exec(`
      select * from public.blink_group_members('${group}')`),
      'NOT_AN_ACTIVE_GROUP_MEMBER');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await rejects(() => db.exec(`
      update public.conversation_participants set left_at=now()
      where conversation_id='${managed}' and user_id='${owner}'`),
      'GROUP_OWNER_MUST_TRANSFER_FIRST');
    await db.exec('reset role');
    console.log('PASS staged founder cannot silently abandon group via raw UPDATE');
    console.log('PASS staged active roster permissions');
    console.log('PASS staged group send/identity/title permissions');
    console.log('PASS staged self-invite/admin escalation/rejoin/departed-group RLS');
    await db.exec(hardening);
    await db.exec(receiptGuard);
    await db.exec(groupCommunity);
    await db.exec(market);
    console.log('PASS staged SQL replay (idempotency, triggers and policies)');
    console.log('PASS independent offline staged Marketplace + Community contracts');
  } finally {
    await db.close();
  }
}

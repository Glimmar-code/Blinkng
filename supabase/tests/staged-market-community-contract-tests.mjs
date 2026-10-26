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
      create schema private;
      create function private.blink_profile_presence_visible(uuid)
        returns boolean language sql stable as $$select true$$;
      create table auth.users(id uuid primary key, email text);
      create table public.profiles(
        id uuid primary key, username text, full_name text, avatar_url text, handle text,
        online_now boolean, is_online boolean, last_seen_at timestamptz,
        last_seen timestamptz
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
        ad_cta text,
        text text,
        caption text,
        alt_text text
      );
      grant select, insert, update, delete on public.feed_posts to authenticated, service_role;
      create table public.conversations (
        id uuid primary key default gen_random_uuid(), title text, description text, avatar_url text,
        is_group boolean not null default false, created_by uuid,
        updated_at timestamptz not null default now(),
        created_at timestamptz not null default now(),
        last_message_at timestamptz
      );
      create table public.conversation_participants (
        conversation_id uuid references public.conversations(id), user_id uuid,
        left_at timestamptz, is_admin boolean not null default false,
        joined_at timestamptz not null default now(), last_read_at timestamptz,
        primary key (conversation_id,user_id)
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
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_label)
      values ('Campus Life',false,'Sponsored')`), 'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_cta)
      values ('Campus Life',false,'Buy now')`), 'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await db.exec(`
      insert into public.feed_posts(category,text) values ('Campus Life','Discussion of a product')`);
    await db.exec(`
      insert into public.feed_posts(category,text) values ('Academic','How expensive are laptops this semester?')`);
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text)
      values ('Campus Life','Selling this laptop for NGN 150000')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text)
      values ('Campus Life','Selling my backpack for NGN 5000')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text,caption)
      values ('Campus Life','Campus photos','For sale NGN 4500')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text,alt_text)
      values ('Campus Life','Campus photos','Buy now ₦20000')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      update public.feed_posts set caption='Selling this bag for NGN 1500'
      where text='Discussion of a product'`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      update public.feed_posts set category='Market' where category='Campus Life'`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      update public.feed_posts set is_sponsored=true where category='Campus Life'`),
      'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await rejects(() => db.exec(`
      update public.feed_posts set ad_cta='Shop' where category='Campus Life'`),
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
    await rejects(() => db.exec(`
      select public.blink_community_leave('33333333-3333-4333-8333-333333333333')`),
      'COMMUNITY_MEMBERSHIP_BANNED');
    await rejects(() => db.exec(`
      select public.blink_community_request_join('33333333-3333-4333-8333-333333333333')`),
      'COMMUNITY_MEMBERSHIP_BANNED');
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
    // Two active moderators cannot ban one another, even if both can
    // otherwise moderate ordinary members. Only the owner may demote or ban.
    await db.exec(`
      insert into public.blink_community_members(community_id,user_id,role,status)
      values ('${requestClub}','${other}','member','active');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    for (const target of [applicant,other]) {
      const upgraded = await db.query(`
        select public.blink_community_moderate(
          '${requestClub}','${target}','promote') as ok`);
      assert.equal(upgraded.rows[0].ok,true);
    }
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    await rejects(() => db.exec(`
      select public.blink_community_moderate(
        '${requestClub}','${other}','ban')`),
      'COMMUNITY_OWNER_REQUIRED_FOR_MODERATOR');
    await rejects(() => db.exec(`
      select public.blink_community_moderate(
        '${requestClub}','${other}','remove')`),
      'COMMUNITY_OWNER_REQUIRED_FOR_MODERATOR');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const bannedModerator = await db.query(`
      select public.blink_community_moderate(
        '${requestClub}','${other}','ban') as ok`);
    assert.equal(bannedModerator.rows[0].ok,true);
    await db.exec('reset role');
    console.log('PASS community moderator hierarchy and owner-only peer moderation');
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
      create table public.notifications (
        user_id uuid, actor_id uuid, is_read boolean default false,
        text text, type text, sub_text text
      );
      create table public.messages (
        id uuid primary key default gen_random_uuid(),
        conversation_id uuid references public.conversations(id),
        sender_id uuid, content text, message_type text default 'text',
        created_at timestamptz default now(),
        delivered_at timestamptz, is_read boolean default false,
        read_at timestamptz, deleted_for_everyone boolean default false
      );
      grant select,insert,update on public.conversation_participants to authenticated;
      grant select,insert,update on public.conversations to authenticated;
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
    const groupConsent = fs.readFileSync(
      new URL('../../docs/integration/group-invitation-consent-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(groupConsent);
    // The untrusted client role must never bypass the validated group
    // creation RPC by inserting an arbitrary "is_group" conversation.
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      insert into public.conversations(id,created_by,is_group,title)
      values('12345678-1234-4234-8234-123456789abc','${other}',true,'Forged group')`),
      'BLINK_GROUP_CREATION_REQUIRES_SERVER');
    await db.exec('reset role');
    const receiptGuard = fs.readFileSync(
      new URL('../../docs/integration/departed-member-rpc-hardening-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(receiptGuard);
    const dmSummaries = fs.readFileSync(
      new URL('../../docs/integration/direct-message-summaries-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(dmSummaries);
    const dmSend = fs.readFileSync(
      new URL('../../docs/integration/direct-message-send-security-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(dmSend);

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
    const departedPage = await db.query(`
      select id from public.get_conversation_messages_page('${group}',20)`);
    assert.equal(departedPage.rows.length,0,'Departed user cannot paginate message history');
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
    const summaries = await db.query(
      'select conversation_id from public.get_conversation_summaries_page(20)');
    assert.equal(summaries.rows.length,0,
      'Group-only inbox must not contain misleading direct-message duplicates');
    const groupRoster = await db.query(`
      select user_id,is_admin from public.blink_group_members('${managed}')`);
    assert.equal(groupRoster.rows.length, 2, 'Active group user can view unique roster rows');
    const activeUnread = await db.query(`
      select message_id from public.get_my_unread_message_notifications(20)`);
    assert.equal(activeUnread.rows.length,1,'Active group member retains unread access');
    const activePage = await db.query(`
      select id from public.get_conversation_messages_page('${managed}',20)`);
    assert.equal(activePage.rows.length,1,'Active member can still paginate group history');
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
    // No admin can force an applicant into an existing group. Invitations
    // remain hidden from non-recipients and require explicit acceptance.
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      select public.blink_group_invite('${managed}','${applicant}')`),
      'GROUP_ADMIN_REQUIRED');
    await rejects(() => db.exec(`
      select public.blink_group_respond_invitation('${managed}',true)`),
      'GROUP_INVITATION_NOT_PENDING');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const sentInvite = await db.query(`
      select public.blink_group_invite('${managed}','${applicant}') as ok`);
    assert.equal(sentInvite.rows[0].ok,true,'Owner can issue an invitation');
    const membersPending = await db.query(`
      select user_id from public.blink_group_members('${managed}')`);
    assert.equal(membersPending.rows.length,2,
      'Invite must not immediately add a nonconsenting user as an active member');
    const ownerInvites = await db.query(`
      select * from public.blink_group_pending_invitations(50)`);
    assert.equal(ownerInvites.rows.length,0,'Pending invites visible only to invited user');
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    const applicantInvites = await db.query(`
      select conversation_id,title from public.blink_group_pending_invitations(50)`);
    assert.equal(applicantInvites.rows.length,1);
    assert.equal(applicantInvites.rows[0].conversation_id,managed);
    await rejects(() => db.exec(`
      select * from public.blink_group_members('${managed}')`),
      'NOT_AN_ACTIVE_GROUP_MEMBER');
    const rejected = await db.query(`
      select public.blink_group_respond_invitation('${managed}',false) as ok`);
    assert.equal(rejected.rows[0].ok,true,'Applicant can decline without joining');
    await rejects(() => db.exec(`
      select public.blink_group_respond_invitation('${managed}',true)`),
      'GROUP_INVITATION_NOT_PENDING');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await db.exec(`select public.blink_group_invite('${managed}','${applicant}')`);
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    const accepted = await db.query(`
      select public.blink_group_respond_invitation('${managed}',true) as ok`);
    assert.equal(accepted.rows[0].ok,true,'Explicit user acceptance authorizes membership');
    const memberAfterConsent = await db.query(`
      select user_id from public.blink_group_members('${managed}')`);
    assert.equal(memberAfterConsent.rows.length,3);
    const remainingInvites = await db.query(`
      select * from public.blink_group_pending_invitations(50)`);
    assert.equal(remainingInvites.rows.length,0,'Accepted invitation leaves pending inbox');
    await db.exec('reset role');
    // Initial group creation must also require every selected student's
    // consent. The trusted founder is the only immediate member.
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await rejects(() => db.exec(`
      select public.create_group_conversation('Too few', array['${applicant}'::uuid])`),
      'GROUP_MEMBER_LIMIT');
    const createdGroup = await db.query(`
      select public.create_group_conversation(
        'Opt-in study group',array['${other}'::uuid,'${applicant}'::uuid]
      ) as id`);
    const consentGroup = createdGroup.rows[0].id;
    const createdRoster = await db.query(`
      select user_id from public.blink_group_members('${consentGroup}')`);
    assert.equal(createdRoster.rows.length,1,
      'New group founder is active; invitees must not be silently enrolled');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const pendingOther = await db.query(`
      select conversation_id from public.blink_group_pending_invitations(50)
      where conversation_id='${consentGroup}'`);
    assert.equal(pendingOther.rows.length,1);
    await db.exec(`select public.blink_group_respond_invitation('${consentGroup}',true)`);
    await db.exec(`select set_config('request.jwt.claim.sub','${applicant}',false)`);
    const pendingApplicant = await db.query(`
      select conversation_id from public.blink_group_pending_invitations(50)
      where conversation_id='${consentGroup}'`);
    assert.equal(pendingApplicant.rows.length,1);
    await db.exec(`select public.blink_group_respond_invitation('${consentGroup}',false)`);
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const optedInRoster = await db.query(`
      select user_id from public.blink_group_members('${consentGroup}')`);
    assert.equal(optedInRoster.rows.length,2,
      'Declined initial invite cannot appear in active group roster');
    await db.exec('reset role');
    console.log('PASS staged founder-only group creation and recipient opt-in');

    console.log('PASS staged group invitation consent, decline, acceptance and role isolation');
    // Read-receipt authorization must survive regular two-person messages
    // while denying the same RPC after someone leaves that direct chat.
    await db.exec(`
      insert into public.conversation_participants(conversation_id,user_id)
        values ('${dm}','${owner}');
      insert into public.messages(id,conversation_id,sender_id,content)
        values ('aaaaaaaa-bbbb-4aaa-8aaa-aaaaaaaaaaaa','${dm}','${owner}','Fresh direct message');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const dmRead = await db.query(
      "select public.mark_conversation_read('testowner') as n");
    assert.equal(Number(dmRead.rows[0].n),1,'Current DM member marks message as read');
    await db.exec(`
      update public.conversation_participants set left_at=now()
      where conversation_id='${dm}' and user_id='${other}'`);
    await db.exec('reset role');
    await db.exec(`
      insert into public.messages(id,conversation_id,sender_id,content)
        values ('bbbbbbbb-cccc-4bbb-8bbb-bbbbbbbbbbbb','${dm}','${owner}','After departure');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const departedDmRead = await db.query(
      "select public.mark_conversation_read('testowner') as n");
    assert.equal(Number(departedDmRead.rows[0].n),0,'Departed DM member cannot mark new messages read');
    const departedDmHistory = await db.query(`
      select id from public.get_conversation_messages_page('${dm}',20)`);
    assert.equal(departedDmHistory.rows.length,0,'Departed DM member cannot fetch history');
    await db.exec('reset role');
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      select public.blink_group_transfer_owner('${managed}','${owner}')`),
      'GROUP_OWNER_REQUIRED');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    // This applicant accepted an invitation earlier in the same scenario.
    // Mark them departed before asserting the inactive-successor denial.
    await db.exec('reset role');
    await db.exec(`update public.conversation_participants set left_at=now()
      where conversation_id='${managed}' and user_id='${applicant}'`);
    await db.exec('set role authenticated');
    await rejects(() => db.exec(`
      select public.blink_group_transfer_owner('${managed}','${applicant}')`),
      'NEW_OWNER_MUST_BE_ACTIVE_MEMBER');
    const transferred = await db.query(`
      select public.blink_group_transfer_owner('${managed}','${other}') as ok`);
    assert.equal(transferred.rows[0].ok,true,'Owner can transfer to active member');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const newOwner = await db.query(`
      select created_by from public.conversations where id='${managed}'`);
    assert.equal(newOwner.rows[0].created_by,other,'Creator identity moved to successor');
    const newAdmin = await db.query(`
      select is_admin from public.conversation_participants
      where conversation_id='${managed}' and user_id='${other}'`);
    assert.equal(newAdmin.rows[0].is_admin,true,'Successor is made group admin');
    await rejects(() => db.exec(`
      select public.blink_group_leave('${managed}')`),
      'GROUP_OWNER_MUST_TRANSFER_FIRST');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const ownerLeave = await db.query(`
      select public.blink_group_leave('${managed}') as ok`);
    assert.equal(ownerLeave.rows[0].ok,true,'Previous owner can leave after transfer');
    await db.exec('reset role');
    // SECURITY DEFINER send_message must not silently revive history from a
    // direct conversation after one participant has left that conversation.
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const newChat = await db.query(
      "select public.get_or_create_direct_conversation('testowner') as id");
    const newId = newChat.rows[0].id;
    assert.notEqual(newId,dm,'Departed users get a new DM instead of old chat access');
    const freshMessage = await db.query(
      "select public.send_message('testowner','New direct message') as id");
    const freshLocation = await db.query(
      `select conversation_id from public.messages where id='${freshMessage.rows[0].id}'`);
    assert.equal(freshLocation.rows[0].conversation_id,newId,
      'DM send reuses active newly established chat only');
    const oldMember = await db.query(`
      select left_at from public.conversation_participants
      where conversation_id='${dm}' and user_id='${other}'`);
    assert.ok(oldMember.rows[0].left_at,
      'Server-side DM sending must never silently rejoin departed chats');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const reply = await db.query(
      "select public.send_message('testreader','Response from original account') as id");
    const replyLocation = await db.query(
      `select conversation_id from public.messages where id='${reply.rows[0].id}'`);
    assert.equal(replyLocation.rows[0].conversation_id,newId,
      'Both participants reuse only the active conversation');
    await db.exec('reset role');
    await db.exec(`
      insert into public.blocks(blocker_id,blocked_id) values ('${owner}','${other}')`);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    await rejects(() => db.exec(`
      select public.send_message('testowner','Not permitted')`),'USER_BLOCKED');
    await rejects(() => db.exec(`
      select public.get_or_create_direct_conversation('testowner')`),'USER_BLOCKED');
    await db.exec('reset role');
    console.log('PASS staged DM send re-entry boundaries, active reuse and block enforcement');

    // The hosted legacy notification trigger only picks one recipient for
    // every group message (LIMIT 1). The stage candidate must fan out to all
    // active recipients, while never alerting a departed group participant.
    const groupFanout = fs.readFileSync(
      new URL('../../docs/integration/group-notification-fanout-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(groupFanout);
    const fanoutGroup = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
    await db.exec(`
      create trigger trg_notify_message_created after insert on public.messages
        for each row execute function public.notify_message_created();
      insert into public.conversations(id,title,is_group,created_by)
        values('${fanoutGroup}','Notify Readers',true,'${owner}');
      insert into public.conversation_participants(conversation_id,user_id,is_admin)
        values('${fanoutGroup}','${owner}',true),
              ('${fanoutGroup}','${other}',false),
              ('${fanoutGroup}','${applicant}',false);
      insert into public.messages(conversation_id,sender_id,content)
        values('${fanoutGroup}','${owner}','All active members');
    `);
    const firstFanout = await db.query(`
      select user_id,count(*)::integer as n from public.notifications
      where actor_id='${owner}' and text like '% sent you a message%'
      group by user_id order by user_id`);
    assert.equal(firstFanout.rows.length,2,
      'One group notification must reach each of two active recipients');
    assert.equal(firstFanout.rows.reduce((n,row)=>n+row.n,0),2);
    await db.exec(`
      update public.conversation_participants set left_at=now()
      where conversation_id='${fanoutGroup}' and user_id='${other}';
      insert into public.messages(conversation_id,sender_id,content)
        values('${fanoutGroup}','${owner}','Remaining member only');
    `);
    const afterLeaveFanout = await db.query(`
      select user_id,count(*)::integer as n from public.notifications
      where actor_id='${owner}' and text like '% sent you a message%'
      group by user_id`);
    const memberCount = Object.fromEntries(
      afterLeaveFanout.rows.map(row=>[row.user_id,row.n]));
    assert.equal(memberCount[other],1,
      'Departed group member must not receive the second notification');
    assert.equal(memberCount[applicant],2,
      'Remaining active member must receive both notifications');
    await db.exec(groupFanout);
    console.log('PASS staged active-only group notification fan-out and replay');

    console.log('PASS staged group owner transfer, successor authorization and safe leave');
    console.log('PASS active and departed direct-chat history/read-receipt authorization');
    console.log('PASS staged founder cannot silently abandon group via raw UPDATE');
    console.log('PASS staged active roster permissions');
    console.log('PASS staged group send/identity/title permissions');
    console.log('PASS staged self-invite/admin escalation/rejoin/departed-group RLS');
    await db.exec(hardening);
    await db.exec(groupConsent);
    await db.exec(receiptGuard);
    await db.exec(dmSummaries);
    await db.exec(dmSend);
    await db.exec(groupFanout);
    await db.exec(groupCommunity);
    await db.exec(market);
    console.log('PASS staged SQL replay (idempotency, triggers and policies)');
    console.log('PASS independent offline staged Marketplace + Community contracts');
  } finally {
    await db.close();
  }
}

import assert from "node:assert/strict";

export async function presenceSessionTests(db) {
  const viewer = "00000000-0000-4000-8000-000000000101";
  const partner = "00000000-0000-4000-8000-000000000102";
  const expiryUser = "00000000-0000-4000-8000-000000000103";
  const conversation = "00000000-0000-4000-8000-000000000201";
  const sessionOne = "00000000-0000-4000-8000-000000000301";
  const sessionTwo = "00000000-0000-4000-8000-000000000302";
  const sessionThree = "00000000-0000-4000-8000-000000000303";
  const expirySession = "00000000-0000-4000-8000-000000000304";

  await db.exec(`reset role;
    insert into auth.users(id, email)
    values
      ('${viewer}', 'presence-viewer@example.test'),
      ('${partner}', 'presence-partner@example.test'),
      ('${expiryUser}', 'presence-expiry@example.test')
    on conflict (id) do nothing;

    insert into public.profiles(id, username, presence_visibility)
    values
      ('${viewer}', 'presence_viewer', 'PUBLIC'),
      ('${partner}', 'presence_partner', 'PUBLIC'),
      ('${expiryUser}', 'presence_expiry', 'PUBLIC')
    on conflict (id) do update set presence_visibility = excluded.presence_visibility;

    insert into public.user_settings(user_id, show_online_status)
    values ('${partner}', true)
    on conflict (user_id) do update set show_online_status = excluded.show_online_status;

    insert into public.user_chat_privacy(user_id, show_online, show_last_seen)
    values ('${partner}', true, true)
    on conflict (user_id) do update set
      show_online = excluded.show_online,
      show_last_seen = excluded.show_last_seen;

    insert into public.conversations(id, created_by, is_group)
    values ('${conversation}', '${viewer}', false)
    on conflict (id) do nothing;

    insert into public.conversation_participants(conversation_id, user_id)
    values ('${conversation}', '${viewer}'), ('${conversation}', '${partner}')
    on conflict do nothing;

    insert into auth.sessions(id, user_id)
    values
      ('${sessionOne}', '${partner}'),
      ('${sessionTwo}', '${partner}'),
      ('${sessionThree}', '${partner}'),
      ('${expirySession}', '${expiryUser}')
    on conflict (id) do nothing;
  `);

  const setClaims = async (userId, sessionId) => {
    await db.query(
      "select set_config('request.jwt.claim.sub', $1, false), set_config('request.jwt.claims', $2, false)",
      [userId, JSON.stringify({ sub: userId, session_id: sessionId })],
    );
  };
  const profile = async (userId) => (await db.query(
    "select online_now, last_seen_at from public.profiles where id = $1",
    [userId],
  )).rows[0];
  const presence = async (userId) => (await db.query(
    "select * from private.get_chat_presence_for_viewer($1)",
    [userId],
  )).rows[0];

  await db.exec("set role authenticated");
  await setClaims(partner, sessionOne);
  await db.query("select public.set_my_presence(true)");
  await setClaims(partner, sessionTwo);
  await db.query("select public.set_my_presence(true)");

  const lastSeenBeforeOneDeviceStops = (await profile(partner)).last_seen_at;
  await setClaims(partner, sessionOne);
  await db.query("select public.set_my_presence(false)");
  const afterOneDeviceStops = await profile(partner);
  assert.equal(afterOneDeviceStops.online_now, true,
    "ending one device session must not offline the account while another device remains active");
  assert.equal(new Date(afterOneDeviceStops.last_seen_at).getTime(),
    new Date(lastSeenBeforeOneDeviceStops).getTime(),
    "last seen must not advance while another device remains online");

  // Revoking the final device session must make the user offline immediately,
  // even if that device's last heartbeat is still inside the lease window.
  await db.exec("reset role");
  await db.query("update auth.sessions set user_id = $1 where id = $2", [viewer, sessionTwo]);
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  const revokedPresence = await presence(partner);
  assert.equal(revokedPresence.partner_online, false,
    "a revoked Auth session must not remain online during the remaining lease window");

  await setClaims(partner, sessionTwo);
  await assert.rejects(
    () => db.query("select public.set_my_presence(true)"),
    /AUTH_SESSION_REVOKED/,
    "a revoked Supabase session must not be able to resurrect online presence",
  );
  await db.exec("reset role");
  await db.query("select private.reconcile_presence_lease()");
  const afterAllDevicesStop = await profile(partner);
  assert.equal(afterAllDevicesStop.online_now, false,
    "the account must become offline after its final active Auth session is revoked");
  assert.ok(new Date(afterAllDevicesStop.last_seen_at).getTime() >=
    new Date(lastSeenBeforeOneDeviceStops).getTime(),
    "last seen must advance when the final active device stops");

  // A stale lease is reconciled using its last server heartbeat, not a device clock.
  await db.exec(`update public.profiles
      set online_now = true, is_online = true,
          last_seen_at = statement_timestamp() - interval '1 day',
          last_seen = statement_timestamp() - interval '1 day'
      where id = '${expiryUser}';
    insert into private.user_presence_sessions(user_id, session_id, last_heartbeat_at, is_online)
    values ('${expiryUser}', '${expirySession}', statement_timestamp() - interval '2 minutes', true)
    on conflict (user_id, session_id) do update set
      last_heartbeat_at = excluded.last_heartbeat_at, is_online = true;
    select private.reconcile_presence_lease();
  `);
  const expired = await profile(expiryUser);
  assert.equal(expired.online_now, false, "expired heartbeat lease must reconcile to offline");
  const ageMs = Date.now() - new Date(expired.last_seen_at).getTime();
  assert.ok(ageMs > 60_000 && ageMs < 4 * 60_000,
    "expired last seen must use the last server heartbeat time");

  await db.exec("reset role");
  await db.query(
    "update public.profiles set last_seen_at = statement_timestamp() - interval '1 hour', last_seen = statement_timestamp() - interval '1 hour' where id = $1",
    [partner],
  );
  await db.query(
    "update public.user_chat_privacy set show_online = false, show_last_seen = true where user_id = $1",
    [partner],
  );
  await db.query(
    "insert into private.user_presence_sessions(user_id, session_id, last_heartbeat_at, is_online) values ($1, $2, statement_timestamp(), true) on conflict (user_id, session_id) do update set last_heartbeat_at = excluded.last_heartbeat_at, is_online = true",
    [partner, sessionThree],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  let current = await presence(partner);
  assert.equal(current.partner_online, false);
  assert.ok(current.partner_last_seen, "last seen is visible when only online status is hidden");
  assert.ok(Date.now() - new Date(current.partner_last_seen).getTime() > 30 * 60_000,
    "hiding online status must not leak a fresh heartbeat as last seen");

  await db.exec("reset role");
  await db.query(
    "update public.user_chat_privacy set show_last_seen = false where user_id = $1",
    [partner],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  current = await presence(partner);
  assert.equal(current.partner_last_seen, null,
    "show_last_seen=false must hide the timestamp");

  await db.exec("reset role");
  await db.query(
    "update public.user_chat_privacy set show_online = true, show_last_seen = true where user_id = $1",
    [partner],
  );
  await db.query(
    "update public.profiles set presence_visibility = 'PRIVATE' where id = $1",
    [partner],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  current = await presence(partner);
  assert.equal(current.partner_online, false, "PRIVATE profile visibility must hide online state");
  assert.equal(current.partner_last_seen, null, "PRIVATE profile visibility must hide last seen");

  await db.exec("reset role");
  await db.query(
    "update public.profiles set presence_visibility = 'FOLLOWERS' where id = $1",
    [partner],
  );
  await db.query(
    "insert into public.follows(follower_id, following_id) values ($1, $2) on conflict do nothing",
    [viewer, partner],
  );
  await db.query(
    "insert into private.user_presence_sessions(user_id, session_id, last_heartbeat_at, is_online) values ($1, $2, statement_timestamp(), true) on conflict (user_id, session_id) do update set last_heartbeat_at = excluded.last_heartbeat_at, is_online = true",
    [partner, sessionThree],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  current = await presence(partner);
  assert.equal(current.partner_online, true,
    "FOLLOWERS visibility must allow an authenticated follower to see online status");

  await db.exec("reset role");
  await db.query(
    "delete from public.follows where follower_id = $1 and following_id = $2",
    [viewer, partner],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  current = await presence(partner);
  assert.equal(current.partner_online, false,
    "FOLLOWERS visibility must hide status from a non-follower");

  await db.exec("reset role");
  await db.query(
    "update public.profiles set presence_visibility = 'PUBLIC' where id = $1",
    [partner],
  );
  await db.query(
    "update public.user_settings set show_online_status = false where user_id = $1",
    [partner],
  );
  await db.exec("set role authenticated");
  await setClaims(viewer, sessionOne);
  current = await presence(partner);
  assert.equal(current.partner_online, false,
    "the global show_online_status setting must hide active state");
  assert.equal(current.partner_last_seen, null,
    "the global show_online_status setting must hide last seen");

  await db.exec("reset role");
  console.log("PASS session-scoped presence, multi-device aggregation, lease expiry and privacy");
}

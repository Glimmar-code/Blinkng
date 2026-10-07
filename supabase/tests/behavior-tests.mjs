import assert from 'node:assert/strict';
export async function behaviorTests(db) {
 const A='00000000-0000-4000-8000-000000000001',B='00000000-0000-4000-8000-000000000002',C='00000000-0000-4000-8000-000000000003';
 const chat='00000000-0000-4000-8000-000000000010',msg='00000000-0000-4000-8000-000000000011',post='00000000-0000-4000-8000-000000000012',key='00000000-0000-4000-8000-000000000013';
 const sql=s=>db.query(s); const scalar=async s=>Object.values((await sql(s)).rows[0])[0];
 async function user(id){await db.exec(`reset role;select set_config('request.jwt.claim.sub','${id}',false);set role authenticated;`);}
 async function admin(){await db.exec('reset role;');}
 async function test(name,fn){try{await fn();console.log('PASS behavior:',name);}catch(e){console.log('FAIL behavior:',name,e.message);throw e;}}
 await db.exec(`insert into auth.users(id,email) values ('${A}','owner@example.invalid'),('${B}','recipient@example.invalid'),('${C}','outsider@example.invalid');
 insert into public.profiles(id,username,email,full_name,university,online_now,is_online,phone,whatsapp) values
 ('${A}','test_owner','owner@example.invalid','Owner','Test Campus',true,true,'111','222'),
 ('${B}','test_recipient','recipient@example.invalid','Recipient','Test Campus',true,true,'333','444'),
 ('${C}','test_outsider','outsider@example.invalid','Outsider','Other Campus',false,false,'555','666');
 insert into public.user_balances(user_id,spendable_coin_balance) values ('${A}',100000),('${B}',100000),('${C}',100000);
 insert into public.user_settings(user_id,dm_privacy,show_online_status) values ('${A}','everyone',true),('${B}','everyone',true),('${C}','nobody',true);
 insert into public.follows(follower_id,following_id) values ('${B}','${A}');
 insert into public.conversations(id,created_by,is_group) values ('${chat}','${A}',false);
 insert into public.conversation_participants(conversation_id,user_id) values ('${chat}','${A}'),('${chat}','${B}');
 insert into public.messages(id,conversation_id,sender_id,content) values ('${msg}','${chat}','${A}','Fixture message');
 insert into public.feed_posts(id,user_id,text,is_reel,is_active,creator_post_number) values ('${post}','${A}','Fixture post',false,true,1);`);
 await test('private profile fields and raw rows stay owner-only',async()=>{
  await user(A);const profile=await scalar("select public.get_profile_detail('test_recipient')");
  assert.equal(profile.email,'');assert.equal(profile.phone,'');assert.equal(profile.whatsapp,'');assert.equal(profile.fcm_token,undefined);
  assert.equal(await scalar(`select count(*)::int from public.profiles where id='${B}'`),0);
  assert.equal(await scalar("select public.is_blink_username_available('test_recipient')"),false);
  assert.equal(await scalar("select public.is_blink_username_available('test_owner')"),true);
 });
 await test('legacy online toggle redacts every profile and inbox presence',async()=>{
  await user(B);await sql(`update public.user_settings set show_online_status=false where user_id='${B}'`);await sql('select public.set_my_presence(true)');
  assert.equal(await scalar(`select online_now from public.profiles where id='${B}'`),false);
  await user(A);const profile=await scalar("select public.get_profile_detail('test_recipient')");assert.equal(profile.online_now,false);assert.equal(profile.last_seen_at,null);
  const summary=(await sql('select * from public.get_conversation_summaries_page()')).rows[0];assert.equal(summary.partner_online,false);assert.equal(summary.partner_last_seen,null);
 });
 await test('chat controls isolate membership and preserve independent flags',async()=>{
  await user(A);await sql(`select public.set_chat_inbox_state('${chat}',false,true,true)`);await sql(`select public.set_chat_inbox_state('${chat}',true,null,null)`);
  const row=(await sql(`select * from public.get_chat_inbox_state(array['${chat}']::uuid[])`)).rows[0];
  assert.equal(row.is_archived,true);assert.equal(row.is_pinned,true);assert.equal(row.marked_unread,true);
  await user(C);assert.equal((await sql(`select * from public.get_chat_inbox_state(array['${chat}']::uuid[])`)).rows.length,0);
  await assert.rejects(sql(`select public.set_chat_inbox_state('${chat}',true,null,null)`),/NOT_A_CONVERSATION_MEMBER/);
 });
 await test('request recipient can decline without editing identities',async()=>{
  await user(A);assert.equal(await scalar(`select public.respond_direct_message_request('${chat}',true)`),false);
  await user(B);assert.equal(await scalar(`select public.respond_direct_message_request('${chat}',false)`),true);
  const row=(await sql(`select * from public.get_chat_inbox_state(array['${chat}']::uuid[])`)).rows[0];assert.equal(row.request_status,'declined');assert.equal(row.is_archived,true);
  await assert.rejects(sql(`update public.direct_message_requests set sender_id='${C}' where recipient_id='${B}'`),/permission denied/);
 });
 await test('timed mute expires and nonmembers cannot change notifications',async()=>{
  await user(A);await sql(`select public.set_conversation_notification_settings('${chat}','none',now()-interval '1 hour','hidden',false)`);
  const row=(await sql(`select * from public.get_conversation_notification_settings(array['${chat}']::uuid[])`)).rows[0];assert.equal(row.notification_mode,'all');
  await user(C);await assert.rejects(sql(`select public.set_conversation_notification_settings('${chat}','none')`),/NOT_A_CONVERSATION_MEMBER/);
 });
 await test('forwarding enforces privacy and source visibility',async()=>{
  await user(A);await assert.rejects(sql(`select public.forward_chat_message('${msg}','test_outsider')`),/DM_PRIVACY_RESTRICTED/);
  await user(C);await assert.rejects(sql(`select public.forward_chat_message('${msg}','test_recipient')`),/MESSAGE_NOT_VISIBLE/);
  await user(A);assert.equal(await scalar("select public.block_chat_user('test_recipient',true)"),true);
  await assert.rejects(sql(`select public.forward_chat_message('${msg}','test_recipient')`),/MESSAGING_BLOCKED/);
  await sql("select public.block_chat_user('test_recipient',false)");
 });
 await test('Boost retries charge once and return the same campaign',async()=>{
  await user(A);const query=`select public.create_blink_boost_campaign_v2('PROFILE','${A}',25,'PROFILE_VISITS','MY_UNIVERSITY',3,null,'${key}')`;
  const before=await scalar(`select spendable_coin_balance from public.user_balances where user_id='${A}'`);
  const one=await scalar(query);const after=await scalar(`select spendable_coin_balance from public.user_balances where user_id='${A}'`);const two=await scalar(query);
  assert.equal(one.campaign_id,two.campaign_id);assert.equal(await scalar(`select spendable_coin_balance from public.user_balances where user_id='${A}'`),after);
  assert.equal(before-after,one.charged);assert.equal(await scalar(`select count(*)::int from public.blink_boost_campaigns_v2 where user_id='${A}'`),1);
  await assert.rejects(sql('select * from private.blink_growth_request_idempotency'),/permission denied/);
 });
 await test('Drop retries reserve once and snapshots eligible followers',async()=>{
  await user(A);const query=`select public.create_blink_drop_v2('POST','${post}','LIKE',100,1,'MY_CAMPUS',24,'${key}')`;
  const before=await scalar(`select spendable_coin_balance from public.user_balances where user_id='${A}'`);const one=await scalar(query);const two=await scalar(query);
  assert.deepEqual(one,two);assert.equal(before-await scalar(`select spendable_coin_balance from public.user_balances where user_id='${A}'`),100);
  await user(B);const state=await scalar('select public.get_blink_drops_state_v2(30)');assert.equal(state.active_drops.length,1);
 });
 await test('Growth receipts and analytics are account-scoped',async()=>{
  await user(C);const receipts=await scalar('select public.get_blink_growth_receipts(30)');assert.deepEqual(receipts.items,[]);
  const analytics=await scalar('select public.get_blink_growth_analytics()');assert.equal(analytics.boost.campaigns,0);assert.equal(analytics.drops.drops,0);
 });
 await test('owner pin limit rejects a fourth pin without modifying content',async()=>{
  await admin();
  for(let i=20;i<24;i++)await sql(`insert into public.feed_posts(id,user_id,text,is_reel,is_active,creator_post_number) values ('00000000-0000-4000-8000-${String(i).padStart(12,'0')}','${A}','Pin fixture',false,true,${i})`);
  await user(A);
  for(let i=20;i<23;i++)assert.equal(await scalar(`select public.set_profile_pin('00000000-0000-4000-8000-${String(i).padStart(12,'0')}',true)`),true);
  await assert.rejects(sql("select public.set_profile_pin('00000000-0000-4000-8000-000000000023',true)"),/PROFILE_PIN_LIMIT_REACHED/);
  assert.equal(await scalar(`select count(*)::int from public.feed_posts where user_id='${A}' and is_pinned`),3);
 });
 await test('departed members cannot read inbox summaries or controls',async()=>{
  await admin();await sql(`update public.conversation_participants set left_at=now() where conversation_id='${chat}' and user_id='${B}'`);
  await user(B);assert.equal((await sql('select * from public.get_conversation_summaries_page()')).rows.length,0);
  await assert.rejects(sql(`select public.set_chat_inbox_state('${chat}',true,null,null)`),/NOT_A_CONVERSATION_MEMBER/);
 });
 await admin();
}

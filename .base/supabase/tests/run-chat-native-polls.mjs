import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const db=new PGlite();
const ids=['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333'];
const conv='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',msg='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const q=(sql,args=[])=>db.query(sql,args);
const actor=async(id)=>db.exec("select set_config('request.jwt.claim.sub','"+id+"',false)");
const rpc=(name,args)=>q('select public.'+name+'('+args.map((_,i)=>'$'+(i+1)).join(',')+') as result',args);
const obj=x=>typeof x==='string'?JSON.parse(x):x;
try {
  await db.exec(`
    create role anon; create role authenticated; create role service_role;
    create schema auth; create schema private;
    create function auth.uid() returns uuid language sql stable
      as 'select nullif(current_setting(''request.jwt.claim.sub'',true),'''')::uuid';
    create table public.profiles(id uuid primary key);
    create table public.conversations(id uuid primary key,is_group boolean not null default false);
    create table public.conversation_participants(
      conversation_id uuid references public.conversations(id),
      user_id uuid references public.profiles(id), left_at timestamptz,
      primary key(conversation_id,user_id));
    create table public.blocks(blocker_id uuid,blocked_id uuid,
      primary key(blocker_id,blocked_id));
    create table public.messages(
      id uuid primary key,conversation_id uuid references public.conversations(id),
      sender_id uuid references public.profiles(id),content text,media_url text,
      message_type text not null default 'text',deleted_for_everyone boolean not null default false,
      created_at timestamptz not null default now());
  `);
  for(const id of ids) await q('insert into public.profiles values($1)',[id]);
  await q('insert into public.conversations(id) values($1)',[conv]);
  for(const id of ids.slice(0,2)) await q('insert into public.conversation_participants(conversation_id,user_id) values($1,$2)',[conv,id]);
  await q('insert into public.messages(id,conversation_id,sender_id,content) values($1,$2,$3,$4)',[msg,conv,ids[0],'Campus poll?']);
  await db.exec(fs.readFileSync(new URL('../migrations/20261028220000_blink_chat_native_polls.sql',import.meta.url),'utf8'));
  await actor(ids[0]);
  await rpc('blink_chat_attach_poll',[msg,'Campus poll?',['FUTA','UNILAG','UI']]);
  await rpc('blink_chat_attach_poll',[msg,'Campus poll?',['FUTA','UNILAG','UI']]);
  await assert.rejects(()=>rpc('blink_chat_attach_poll',[msg,'Campus poll?',['Other','UI']]),/ALREADY_ATTACHED/);
  await actor(ids[2]);
  await assert.rejects(()=>rpc('blink_chat_poll_results',[msg]),/POLL_UNAVAILABLE/);
  await assert.rejects(()=>rpc('blink_chat_vote_poll',[msg,0]),/NOT_PARTICIPANT/);
  await actor(ids[1]);
  await rpc('blink_chat_vote_poll',[msg,1]);
  await rpc('blink_chat_vote_poll',[msg,1]);
  let data=obj((await rpc('blink_chat_poll_results',[msg])).rows[0].result);
  assert.equal(Number(data.total_votes),1);
  assert.equal(Number(data.options[1].votes),1);
  assert.equal(Number(data.my_vote),1);
  await rpc('blink_chat_vote_poll',[msg,2]);
  data=obj((await rpc('blink_chat_poll_results',[msg])).rows[0].result);
  assert.equal(Number(data.total_votes),1);
  assert.equal(Number(data.options[2].votes),1);
  await q('insert into public.blocks values($1,$2)',[ids[0],ids[1]]);
  await assert.rejects(()=>rpc('blink_chat_poll_results',[msg]),/BLOCKED/);
  await assert.rejects(()=>rpc('blink_chat_vote_poll',[msg,0]),/BLOCKED/);
  await q('delete from public.blocks');
  await actor(ids[0]);await rpc('blink_chat_close_poll',[msg]);
  await actor(ids[1]);
  await assert.rejects(()=>rpc('blink_chat_vote_poll',[msg,0]),/POLL_UNAVAILABLE/);
  await q('update public.messages set deleted_for_everyone=true where id=$1',[msg]);
  await assert.rejects(()=>rpc('blink_chat_poll_results',[msg]),/POLL_UNAVAILABLE/);
  assert.equal((await q("select has_table_privilege('authenticated','public.blink_chat_poll_votes','SELECT') as p")).rows[0].p,false);
  console.log('PASS Chat native polls: private membership, idempotent votes, change, block, close and delete');
} finally {await db.close();}

import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source=fs.readFileSync(new URL('../web/parity.js',import.meta.url),'utf8');
const start=source.indexOf('  async function getUserSettings(');
const end=source.indexOf('  async function renderNotificationSettings()',start);
assert.ok(start>=0&&end>start,'Privacy persistence functions must exist');
const code=source.slice(start,end);
const alice='11111111-1111-4111-8111-111111111111';
const bob='22222222-2222-4222-8222-222222222222';
let active=alice,chatValue=false,returnEmpty=false,failChat=false,switchAccount=false;
let calls=[];
const context={
  uid:()=>active,authed:()=>!!active,
  encodeURIComponent,
  table:async(name)=>{
    if(name==='user_settings')return [{user_id:alice,read_receipts:true,private_account:false}];
    if(name==='user_chat_privacy'){
      if(failChat)throw Error('Cannot load chat privacy');
      if(switchAccount)active=bob;
      return [{send_read_receipts:chatValue}];
    }
    throw Error('Unexpected table '+name);
  },
  api:async(path,opts)=>{
    calls.push({path,opts});
    if(path.startsWith('/rest/v1/user_chat_privacy')&&failChat)throw Error('chat save failed');
    if(switchAccount)active=bob;
    if(returnEmpty)return [];
    return [{user_id:alice,...opts.body}];
  },
};
vm.createContext(context);
vm.runInContext(code+'\nglobalThis.settingsFunctions={getUserSettings,saveUserSettings};',context);
const {getUserSettings,saveUserSettings}=context.settingsFunctions;

// The old legacy user_settings.read_receipts is NOT authoritative.
assert.equal((await getUserSettings(true)).read_receipts,false);
assert.equal((await getUserSettings(false)).read_receipts,true);
chatValue=true;
assert.equal((await getUserSettings(true)).read_receipts,true);
failChat=true;
await assert.rejects(getUserSettings(true),/Cannot load chat privacy/);
failChat=false;

// Only the canonical chat privacy table may receive the receipt setting.
calls=[];await saveUserSettings({read_receipts:false});
assert.equal(calls.length,1);
assert.match(calls[0].path,/^\/rest\/v1\/user_chat_privacy/);
assert.equal(calls[0].opts.body.send_read_receipts,false);
assert.ok(!('read_receipts' in calls[0].opts.body));

// Mixed saves require confirmation for EACH table.
calls=[];await saveUserSettings({private_account:true,read_receipts:false});
assert.equal(calls.length,2);
assert.match(calls[0].path,/^\/rest\/v1\/user_settings/);
assert.ok(!('read_receipts' in calls[0].opts.body));
assert.match(calls[1].path,/^\/rest\/v1\/user_chat_privacy/);

// Zero updated rows, or failed chat writes, must not be reported as success.
returnEmpty=true;
await assert.rejects(saveUserSettings({private_account:true}),/not confirmed/);
returnEmpty=false;failChat=true;
await assert.rejects(saveUserSettings({private_account:true,read_receipts:false}),/chat save failed/);
failChat=false;

// Account switching while a request is in flight cannot be accepted as saved.
switchAccount=true;
await assert.rejects(saveUserSettings({read_receipts:true}),/not confirmed/);
active=alice;switchAccount=false;

// Both web settings surfaces must load canonical receipts.
assert.match(source,/loading\('Privacy'\); try\{const s=await getUserSettings\(true\)/);
assert.match(source,/loading\('Notification settings'\);[\s\S]*?const s=await getUserSettings\(true\)/);
assert.ok(!source.includes("body:{user_id:uid(),...fields,updated_at:"),'Unverified writes must not be restored');
console.log('Web privacy: canonical receipts, confirmed writes, partial failures and account switching passed.');

import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Exercise the real web functions in a deliberately minimal browser sandbox.
const source=fs.readFileSync(new URL('../web/parity.js',import.meta.url),'utf8');
const start=source.indexOf('  // Only known, explicitly account-scoped draft namespaces');
const end=source.indexOf('  async function getUserSettings()',start);
assert.ok(start>=0 && end>start,'Draft access functions must be present');
const code=source.slice(start,end);
const alice='11111111-1111-4111-8111-111111111111';
const bob='22222222-2222-4222-8222-222222222222';
const storage=new Map([
  [`blink_web_post_draft_v2:${alice}`,'Alice personal draft'],
  [`blink_web_post_draft_v2:${bob}`,'Bob personal draft'],
  [`blink_web_parity_draft_v2:${alice}:abc`,'Alice parity draft'],
  ['unowned_draft','Unscoped private content'],
  [`unrelated_draft:${alice}`,'Unknown account storage'],
]);
let active=alice, signedIn=true, markup='', saved=0, deleted=0;
const saveButtons=[], deleteButtons=[], notices=[];
const context={
  storageKeys:()=>[...storage.keys()],
  storageGet:(key)=>storage.get(key)||'',
  storageSet:(key,value)=>{saved++;storage.set(key,value);return true;},
  storageRemove:(key)=>{deleted++;storage.delete(key);},
  uid:()=>active,authed:()=>signedIn,
  authGuard:()=>signedIn,
  shell:(_,html)=>{markup=html;},
  esc:(value)=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;'),
  toast:(message)=>notices.push(message),
  CSS:{escape:value=>value},
  document:{
    querySelectorAll:(selector)=>selector==='[data-save-draft]'?saveButtons:deleteButtons,
    querySelector:()=>({value:'Updated personal draft'}),
  },
};
vm.createContext(context);
vm.runInContext(`${code}\n globalThis.testDrafts={ownedDraftKeys,canEditDraft,renderDrafts};`,context);
const {ownedDraftKeys,canEditDraft,renderDrafts}=context.testDrafts;
assert.equal(ownedDraftKeys('').length,0);
assert.equal(ownedDraftKeys(bob).length,1);
assert.equal(ownedDraftKeys(alice).length,2);
assert.ok(!ownedDraftKeys(alice).includes('unowned_draft'));
assert.ok(!ownedDraftKeys(alice).includes(`blink_web_post_draft_v2:${bob}`));

// The rendered page cannot reveal drafts from a different account.
saveButtons.push({dataset:{saveDraft:`blink_web_post_draft_v2:${alice}`}});
deleteButtons.push({dataset:{deleteDraft:`blink_web_post_draft_v2:${alice}`}});
renderDrafts();
assert.match(markup,/Alice personal draft/);
assert.doesNotMatch(markup,/Bob personal draft/);
assert.doesNotMatch(markup,/Unscoped private content/);

// A stale button from Alice's page must not be usable after switching to Bob.
active=bob;
saveButtons[0].onclick();
deleteButtons[0].onclick();
assert.equal(saved,0);
assert.equal(deleted,0);
assert.equal(storage.get(`blink_web_post_draft_v2:${alice}`),'Alice personal draft');
assert.equal(canEditDraft(`blink_web_post_draft_v2:${alice}`,alice),false);

// Signed-out people must neither view drafts nor edit an old page.
active=alice;signedIn=false;markup='';
renderDrafts();
assert.equal(markup,'');
saveButtons[0].onclick();
deleteButtons[0].onclick();
assert.equal(saved,0);
assert.equal(deleted,0);

// The owner can still save and delete their own draft normally.
signedIn=true;
renderDrafts();
saveButtons[0].onclick();
assert.equal(saved,1);
assert.equal(storage.get(`blink_web_post_draft_v2:${alice}`),'Updated personal draft');
deleteButtons[0].onclick();
assert.equal(deleted,1);
assert.equal(storage.has(`blink_web_post_draft_v2:${alice}`),false);

assert.ok(source.includes('const draftKeys=authed()?ownedDraftKeys(uid()):[];'),'Offline draft count must also be account scoped');
console.log('Web drafts: account isolation, logout guard, stale UI and owner edits passed.');

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const first=source.indexOf('  async function renderReferralInvitation(');
const last=source.indexOf('\n  function renderLogin()',first);
assert.ok(first>0 && last>first, 'Invitation renderer must remain in BLINK web app');
const isolatedSource=source.slice(first,last)+'\nglobalThis.testInvite=renderReferralInvitation;';

function harness({signedIn=false,match=true,fail=false}={}){
  const store=new Map(), calls=[],routes=[],nodes=new Map();
  function node(){
    return {
      isConnected:true,
      textContent:'',
      children:[],
      addEventListener(_event,fn){this.action=fn;},
      replaceChildren(...children){this.children=children;},
      append(...children){this.children.push(...children);},
    };
  }
  const ROOT={innerHTML:'',querySelector(selector){
    if(!nodes.has(selector))nodes.set(selector,node());
    return nodes.get(selector);
  }};
  const person={id:'00000000-0000-0000-0000-000000000001',
    username:'alpha',full_name:'Alpha',university:'FUTA'};
  const ctx={
    ROOT,
    LOGO_URL:'/blink-logo.png',
    isAuthed:()=>signedIn,
    setTitle:()=>{},
    navigate:(route)=>routes.push(route),
    document:{createElement:()=>node()},
    crypto:{randomUUID:()=> '00000000-0000-4000-8000-000000000099'},
    storageGet:(key,fallback='')=>store.get(key)??fallback,
    storageSet:(key,val)=>{store.set(key,val);return true;},
    storageRemove:(key)=>store.delete(key),
    INVITE_REFERRER_KEY:'invited',
    INVITE_VISITOR_KEY:'visitor',
    anonRpc:async (fn,args)=>{
      calls.push({fn,args});
      if(fail)throw new Error('offline');
      return fn==='blink_referrer_suggestions'?(match?[person]:[]):true;
    }
  };
  vm.runInNewContext(isolatedSource,ctx,{timeout:1000});
  return {run:ctx.testInvite,store,calls,routes,nodes,ROOT};
}

test('verified invitation preselects an eligible referrer only for new visitors',async()=>{
  const h=harness();
  await h.run('ALPHA');
  assert.equal(h.store.get('invited'),'alpha');
  assert.equal(h.calls.filter(x=>x.fn==='blink_record_referral_link_open').length,1);
  assert.equal(h.nodes.get('#invite-status').textContent,
    'Join the BLINK community with an invitation from:');
  h.nodes.get('#invite-join').action();
  assert.deepEqual(h.routes,['/login']);
});

test('existing members can explore without creating a new referral',async()=>{
  const h=harness({signedIn:true});
  await h.run('alpha');
  assert.equal(h.store.has('invited'),false);
  assert.equal(h.calls.filter(x=>x.fn==='blink_record_referral_link_open').length,0);
  h.nodes.get('#invite-join').action();
  assert.deepEqual(h.routes,['/feed']);
});

test('missing and unverified profiles never get signup attribution',async()=>{
  for(const options of [{match:false},{fail:true}]){
    const h=harness(options);
    h.store.set('invited','previous');
    await h.run('alpha');
    assert.equal(h.store.has('invited'),false);
    assert.equal(h.calls.filter(x=>x.fn==='blink_record_referral_link_open').length,0);
    assert.match(h.nodes.get('#invite-status').textContent,/invitation|verify/i);
  }
});

test('route uses the invitation landing page instead of forced login',()=>{
  assert.ok(source.includes("if(invited)return renderReferralInvitation(invited[1]);"));
  assert.ok(!source.includes("return navigate('/login',true);\n    }\n    if(path==='/login')"));
});

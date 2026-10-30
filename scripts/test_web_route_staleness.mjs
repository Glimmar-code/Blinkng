import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const cut=(from,to)=>{
  const a=source.indexOf(from),b=source.indexOf(to,a+from.length);
  assert.ok(a>=0&&b>a,'Cannot locate app renderer: '+from);
  return source.slice(a,b);
};
const guard=cut('  const still=()=>','  function navigate(');
const market=cut('  async function renderMarket(){','  async function renderConnect(){');
const connect=cut('  async function renderConnect(){','  async function renderGames(){');
let owner='user-A';
const state={pageSerial:0};
const location={href:'https://blink.test/market',search:''};
const ROOT={innerHTML:''};
const pending=[];
const doc={getElementById:()=>({}),querySelectorAll:()=>[]};
const api={
  state,location,uid:()=>owner,ROOT,
  requireAuth:()=>true,bindCommon:()=>{},
  shell:(body,title)=>title+':'+body,
  rpc:(name)=>new Promise((resolve,reject)=>pending.push({name,resolve,reject})),
  safeUrl:v=>v||'',esc:v=>String(v??''),money:v=>String(v),
  richLinkCard:()=>'',fmt:v=>String(v),document:doc,
  URL,URLSearchParams,encodeURIComponent,toast:()=>{},navigate:()=>{}
};
const create=new Function(...Object.keys(api),
  guard+market+connect+'return {renderMarket,renderConnect};');
const {renderMarket,renderConnect}=create(...Object.values(api));
const oldOffer={item:{id:'old',title:'OLD RESULT',price:200,description:''}};
const latestOffer={item:{id:'new',title:'LATEST RESULT',price:300,description:''}};

// A slow Market response cannot replace a newer Connect screen.
const old=renderMarket();
assert.match(ROOT.innerHTML,/Marketplace/);
location.href='https://blink.test/connect';
const next=renderConnect();
assert.match(ROOT.innerHTML,/Connect/);
pending.shift().resolve([oldOffer]);
await old;
assert.doesNotMatch(ROOT.innerHTML,/OLD RESULT/);
assert.match(ROOT.innerHTML,/Connect/);
pending.shift().resolve([{kind:'mentor',payload:{title:'NEW CONNECT RESULT',profile:{id:'person'}}}]);
await next;
assert.match(ROOT.innerHTML,/NEW CONNECT RESULT/);

// Two requests for the SAME URL must reject the older result as well.
location.href='https://blink.test/market';
const first=renderMarket(),second=renderMarket();
assert.equal(pending.length,2);
pending[1].resolve([latestOffer]);await second;
pending[0].resolve([oldOffer]);await first;
assert.match(ROOT.innerHTML,/LATEST RESULT/);
assert.doesNotMatch(ROOT.innerHTML,/OLD RESULT/);
pending.length=0;

// Switching Blink accounts invalidates a pending private response.
const before=ROOT.innerHTML;
const accountStale=renderMarket();
owner='user-B';
pending.shift().resolve([oldOffer]);await accountStale;
assert.equal(ROOT.innerHTML.includes('OLD RESULT'),false);
assert.match(ROOT.innerHTML,/Marketplace/);
owner='user-A';

// Stale network failures must never replace the new screen with an old error.
const failed=renderMarket();
location.href='https://blink.test/connect';
const fresh=renderConnect();
pending.shift().reject(new Error('OLD SECRET ERROR'));
await failed;
assert.doesNotMatch(ROOT.innerHTML,/OLD SECRET ERROR/);
pending.shift().resolve([]);await fresh;
assert.match(ROOT.innerHTML,/Connect/);

// Guard coverage on all other async page renderers and the profile URL canonicalizer.
for(const [from,to] of [
  ['  async function renderFeed(', '  async function loadMoreFeed('],
  ['  async function renderFollowing(', '  async function renderReels('],
  ['  async function renderReels(', '  const {themeId,'],
  ['  async function renderPublicProfile(', '  async function renderPublicContent('],
  ['  async function renderPublicContent(', '  async function openComments('],
  ['  async function renderActivity(', '  async function renderMessages('],
  ['  async function renderMessages(', '  async function renderGroupInvitations('],
  ['  async function renderConversation(', '  async function renderMarket('],
  ['  async function renderGames(', '  async function renderScheduled('],
  ['  async function renderScheduled(', '  async function renderSettings('],
  ['  async function renderSiteAnalytics(', '  async function render(path=']
]) {
  const code=cut(from,to);
  assert.match(code,/still\(\)/,'Missing route freshness guard: '+from);
  assert.match(code,/live\(\)/,'Route freshness must be enforced after awaits: '+from);
}
const profile=cut('  async function renderPublicProfile(', '  async function renderPublicContent(');
assert.ok(profile.indexOf('if(!live())return;')<profile.indexOf('history.replaceState('),
  'Do not canonicalize a stale public profile URL');
new Function(source);
const utf8=Buffer.byteLength(source,'utf8');
assert.ok(utf8<=160000,`web/app.js exceeds the 160KB mobile budget: ${utf8}`);
console.log('PASS web stale navigation: different routes, same-route refetch, account switch, old errors, coverage, profile canonicalization and asset budget.');

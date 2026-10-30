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
const loadMore=cut('  async function loadMoreFeed(){','  async function renderFollowing(');
let owner='user-A';
const state={pageSerial:0,loading:false,feedOffset:30,feedAsOf:'snapshot',feedMode:'posts'};
const location={href:'https://blink.test/market',search:''};
const ROOT={innerHTML:''};
const pending=[];
const appended=[];
const feedButton={textContent:'Load more',isConnected:true};
const feedList={
  querySelectorAll:()=>[],
  insertAdjacentHTML:(position,markup)=>appended.push(markup)
};
const doc={getElementById:id=>id==='load-more-feed'?feedButton:id==='feed-list'?feedList:{},querySelectorAll:()=>[]};
const api={
  state,location,uid:()=>owner,ROOT,
  requireAuth:()=>true,bindCommon:()=>{},
  shell:(body,title)=>title+':'+body,
  rpc:(name)=>new Promise((resolve,reject)=>pending.push({name,resolve,reject})),
  safeUrl:v=>v||'',esc:v=>String(v??''),money:v=>String(v),
  richLinkCard:()=>'',fmt:v=>String(v),document:doc,
  fetchProfiles:async()=>new Map(),normalizeFeedItem:r=>r,postCard:r=>r.title,bindPostCards:()=>{},
  URL,URLSearchParams,encodeURIComponent,toast:()=>{},navigate:()=>{}
};
const create=new Function(...Object.keys(api),
  guard+market+connect+loadMore+'return {renderMarket,renderConnect,loadMoreFeed};');
const {renderMarket,renderConnect,loadMoreFeed}=create(...Object.values(api));
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


// A paginated feed request must not mutate a newer screen or overwrite a new
// pagination cursor after navigating away.
location.href='https://blink.test/feed';owner='user-A';
const oldCursor=state.feedOffset;
const stalePage=loadMoreFeed();
assert.equal(state.loading,true);
location.href='https://blink.test/reels';
pending.shift().resolve([{id:'old-page',title:'STALE FEED POST',next_offset:60}]);
await stalePage;
assert.equal(state.feedOffset,oldCursor);
assert.equal(appended.length,0);
assert.equal(feedButton.textContent,'Loading…');

location.href='https://blink.test/feed';state.loading=false;
const freshPage=loadMoreFeed();
pending.shift().resolve([{id:'new-page',title:'CURRENT FEED POST',next_offset:60}]);
await freshPage;
assert.equal(state.feedOffset,60);
assert.equal(state.loading,false);
assert.equal(appended.length,1);
assert.match(appended[0],/CURRENT FEED POST/);

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
  ['  async function renderSettings(', '  async function renderAI('],
  ['  async function renderSiteAnalytics(', '  async function render(path=']
]) {
  const code=cut(from,to);
  assert.match(code,/still\(\)/,'Missing route freshness guard: '+from);
  assert.match(code,/live\(\)/,'Route freshness must be enforced after awaits: '+from);
}
assert.match(loadMore,/if\(!live\(\)\)return/,'Feed pagination must reject stale responses');
assert.ok(source.includes('Keep tap-to-play available on older browsers'),'Preserve browser video fallback');
const profileLoad=cut('  async function loadMyProfile(){','  async function loadFollowing(){');
assert.match(profileLoad,/owner=uid\(\)/,'Capture account before loading the profile');
assert.match(profileLoad,/if\(owner!==uid\(\)\)return null/,'Old profile results must never overwrite the new account');
let profileOwner='user-A';
const profileState={profile:{username:'original'}};
const requested=[];
const loader=new Function('state','uid','encodeQ','table',
  profileLoad+'return loadMyProfile;')(profileState,()=>profileOwner,encodeURIComponent,
  ()=>new Promise(resolve=>requested.push(resolve)));
const oldProfile=loader();
profileOwner='user-B';
requested.shift()([{username:'wrong-account'}]);
assert.equal(await oldProfile,null);
assert.equal(profileState.profile.username,'original');
const newProfile=loader();
requested.shift()([{username:'correct-account'}]);
assert.equal((await newProfile).username,'correct-account');
assert.equal(profileState.profile.username,'correct-account');

const profile=cut('  async function renderPublicProfile(', '  async function renderPublicContent(');
assert.ok(profile.indexOf('if(!live())return;')<profile.indexOf('history.replaceState('),
  'Do not canonicalize a stale public profile URL');
new Function(source);
const utf8=Buffer.byteLength(source,'utf8');
assert.ok(utf8<=160000,`web/app.js exceeds the 160KB mobile budget: ${utf8}`);
console.log('PASS web stale navigation: different routes, same-route refetch, account switch, old errors, coverage, profile canonicalization and asset budget.');

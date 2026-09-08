(() => {
  'use strict';

  const C = window.BLINK_WEB_CONFIG || {};
  const BASE = String(C.supabaseUrl || '').replace(/\/$/, '');
  const KEY = C.publishableKey || '';
  const PREVIEW_BASE = location.hostname.endsWith('github.io') ? '/Blinkng' : '';
  const SESSION_KEY = 'blink_web_session_v3';
  const PREF_KEY = 'blink_web_parity_prefs_v1';
  const RECENT_KEY = 'blink_web_recent_accounts_v1';
  const PARITY_ROUTES = new Set([
    '/more','/store','/vault','/vip','/boosts','/gifts','/calls','/offline','/drafts','/verification','/analytics','/study','/accounts','/professional','/leaderboard','/seller',
    '/settings/notifications','/settings/appearance','/settings/privacy','/settings/data','/settings/accessibility'
  ]);

  const MODULES = [
    ['feed','Feed','PremiumFeedScreen.kt','/feed','Social'],
    ['reels','Reels','VideoReelsScreen.kt','/reels','Social'],
    ['search','Search','ProfessionalSearchScreen.kt','/search','Discovery'],
    ['messages','Messages','PremiumMessagesScreen.kt','/messages','Communication'],
    ['activity','Activity','ActivityScreen.kt','/activity','Notifications'],
    ['marketplace','Marketplace','MarketScreen.kt','/market','Commerce'],
    ['connect','Connect Hub','ConnectHubPremiumPanel.kt','/connect','Discovery'],
    ['games','Games','GameSection.kt','/games','Games'],
    ['scheduled-posts','Scheduled posts','ScheduledPostRepository.kt','/scheduled','Creator'],
    ['blink-ai','Blink AI','BlinkAiSheet.kt','/ai','AI'],
    ['blink-store','Blink Store','BlinkStoreActivity.kt','/store','Economy'],
    ['vault','Vault / Inventory','BlinkEconomyModels.kt','/vault','Economy'],
    ['vip','Blink VIP','BlinkVipMark.kt','/vip','Economy'],
    ['boosts','Boosts','BlinkEconomyService.kt','/boosts','Creator'],
    ['digital-gifts','Digital gifts','BlinkEconomyService.kt','/gifts','Economy'],
    ['account-switcher','Account switcher','AccountSwitcherActivity.kt','/accounts','Account'],
    ['google-signin','Google sign-in','GoogleAuthCallbackActivity.kt','/login','Account'],
    ['password-recovery','Password recovery','PasswordRecoveryClient.kt','/settings/password','Account'],
    ['remembered-login','Remembered login','RememberedLoginStore.kt','/accounts','Account'],
    ['session-refresh','Session refresh','SupabaseSessionRefresher.kt','/accounts','Account'],
    ['call-launcher','Voice/video call launcher','BlinkCallLauncher.kt','/calls','Communication'],
    ['call-history','Call history','CallHistoryActivity.kt','/calls','Communication'],
    ['call-realtime','Realtime calls','CallRealtimeChannel.kt','/calls','Communication'],
    ['call-sounds','Call sound preferences','CallSoundPreferences.kt','/settings/notifications','Communication'],
    ['incoming-call-banner','Incoming call banner','IncomingCallBannerActivity.kt','/calls','Communication'],
    ['media-cache','Media cache','BlinkMediaCache.kt','/offline','Offline'],
    ['offline-content','Offline content','OfflineContentStore.kt','/offline','Offline'],
    ['offline-mutation-queue','Offline mutation queue','OfflineMutationQueue.kt','/offline','Offline'],
    ['persistent-drafts','Persistent drafts','PersistentTextDraftStore.kt','/drafts','Offline'],
    ['post-comments','Comments','CommentSheet.kt','/feed','Social'],
    ['post-options','Post options','PostOptionsMenuSheet.kt','/feed','Social'],
    ['create-post','Create post','CreatePostSheet.kt','/feed','Creator'],
    ['story-bar','Stories','StoryBar.kt','/feed','Social'],
    ['story-viewer','Story viewer','StoryViewerDialog.kt','/feed','Social'],
    ['create-story','Create story','CreateStoryScreen.kt','/feed','Creator'],
    ['verification','Verification','GetVerifiedSheet.kt','/verification','Identity'],
    ['profile-edit','Edit profile','EditProfileScreen.kt','/settings/profile','Identity'],
    ['profile-follow','Follow / interact','ProfileFollowInteractButton.kt','/search','Identity'],
    ['profile-analytics','Follower analytics','FollowerGrowthChart.kt','/analytics','Creator'],
    ['seller','Seller tools','BecomeSellerScreen.kt','/seller','Commerce'],
    ['study-circles','Study Circles','StudyCirclesPanel.kt','/study','Campus'],
    ['professional-center','Professional Center','ProfessionalCenterActivity.kt','/professional','Account'],
    ['professional-search','Professional search','ProfessionalSearchScreen.kt','/search','Discovery'],
    ['leaderboard','Leaderboard','LeaderboardScreen.kt','/leaderboard','Games'],
    ['product-detail','Product details','ProductDetailScreen.kt','/market','Commerce'],
    ['notification-settings','Notification settings','NotificationAndCallSettingsActivity.kt','/settings/notifications','Notifications'],
    ['instant-chat-notifications','Instant chat notifications','InstantChatNotification.kt','/settings/notifications','Notifications'],
    ['deep-links','Deep links','DeepLinkRouter.kt','/more','Platform'],
    ['premium-feed','Premium feed chrome','PremiumFeedChrome.kt','/feed','Social'],
    ['premium-messages','Premium messaging','PremiumMessagesScreen.kt','/messages','Communication']
  ].map(([key,title,source,route,group]) => ({key,title,source,route,group}));

  const LAYERS = [
    ['route','Route parity','Web route and deep-link access'],
    ['responsive','Responsive parity','Desktop, tablet and mobile layout behavior'],
    ['keyboard','Keyboard parity','Keyboard and command-palette access'],
    ['state','State parity','Persisted state and session continuity'],
    ['realtime','Realtime parity','Live backend refresh and event awareness'],
    ['offline','Offline parity','Offline-safe fallback and recovery'],
    ['accessibility','Accessibility parity','Focus, labels, reduced motion and readable controls'],
    ['feedback','Interaction parity','Loading, success, error and optimistic feedback'],
    ['analytics','Analytics parity','Useful counts, status and creator feedback'],
    ['bridge','App bridge parity','Open/share/deep-link bridge between web and Android']
  ].map(([key,title,description]) => ({key,title,description}));

  const FEATURES = MODULES.flatMap((module) => LAYERS.map((layer) => ({
    id: `${module.key}:${layer.key}`,
    title: `${module.title} — ${layer.title}`,
    module: module.title,
    moduleKey: module.key,
    group: module.group,
    source: module.source,
    route: module.route,
    layer: layer.title,
    description: layer.description
  })));

  if (FEATURES.length !== 500) console.warn('Blink parity registry expected 500 entries, got', FEATURES.length);

  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmt = (v) => new Intl.NumberFormat().format(Number(v || 0));
  const money = (v, currency='BLINK') => currency === 'BLINK' ? `${fmt(v)} coins` : new Intl.NumberFormat('en-NG',{style:'currency',currency:currency||'NGN',maximumFractionDigits:0}).format(Number(v||0));
  const safe = (v) => { try { const u = new URL(String(v || ''), location.origin); return ['http:','https:','blob:'].includes(u.protocol) ? u.href : ''; } catch { return ''; } };
  const session = () => { try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch { return null; } };
  const token = () => session()?.access_token || '';
  const uid = () => session()?.user?.id || '';
  const authed = () => !!token();
  const routeHref = (path) => `${PREVIEW_BASE}${path}` || '/';
  const currentPath = () => { let p = location.pathname || '/'; if (PREVIEW_BASE && p.startsWith(PREVIEW_BASE)) p = p.slice(PREVIEW_BASE.length) || '/'; return decodeURI(p); };
  const root = () => document.getElementById('app');

  function toast(message, kind='info') {
    let el = document.getElementById('parity-toast');
    if (!el) { el = document.createElement('div'); el.id='parity-toast'; el.className='parity-toast'; document.body.appendChild(el); }
    el.className = `parity-toast show ${kind}`;
    el.textContent = message;
    clearTimeout(toast.t); toast.t=setTimeout(()=>el.classList.remove('show'),2600);
  }

  async function parseJson(res) { const t=await res.text(); if (!t) return null; try { return JSON.parse(t); } catch { return t; } }
  async function api(path,{method='GET',body=null,prefer='',auth=true}={}) {
    if (!BASE || !KEY) throw new Error('Blink web backend is not configured.');
    const h={apikey:KEY,Accept:'application/json'};
    h.Authorization=`Bearer ${auth && token() ? token() : KEY}`;
    if (body != null) h['Content-Type']='application/json';
    if (prefer) h.Prefer=prefer;
    const res=await fetch(`${BASE}${path}`,{method,headers:h,body:body==null?undefined:JSON.stringify(body)});
    if (!res.ok) { const e=await parseJson(res); throw new Error(e?.message||e?.error_description||e?.error||`Blink request failed (${res.status})`); }
    return parseJson(res);
  }
  const rpc=(name,body={})=>api(`/rest/v1/rpc/${name}`,{method:'POST',body});
  const table=(name,query='')=>api(`/rest/v1/${name}${query?`?${query}`:''}`);

  function prefs() { try { return {...{theme:'system',density:'comfortable',fontScale:1,reduceMotion:false,dataSaver:false,autoplay:true},...JSON.parse(localStorage.getItem(PREF_KEY)||'{}')}; } catch { return {theme:'system',density:'comfortable',fontScale:1,reduceMotion:false,dataSaver:false,autoplay:true}; } }
  function savePrefs(next) { localStorage.setItem(PREF_KEY,JSON.stringify({...prefs(),...next})); applyPrefs(); }
  function applyPrefs() {
    const p=prefs();
    document.documentElement.dataset.blinkTheme=p.theme;
    document.documentElement.dataset.blinkDensity=p.density;
    document.documentElement.dataset.blinkMotion=p.reduceMotion?'reduced':'full';
    document.documentElement.dataset.blinkDataSaver=p.dataSaver?'on':'off';
    document.documentElement.dataset.blinkAutoplay=p.autoplay?'on':'off';
    document.documentElement.style.setProperty('--blink-font-scale',String(Math.max(.85,Math.min(1.3,Number(p.fontScale)||1))));
  }

  function isParityRoute(path=currentPath()) { return PARITY_ROUTES.has(path); }
  function go(path) {
    if (PARITY_ROUTES.has(path)) { history.pushState({},'',routeHref(path)); renderParity(path); window.scrollTo({top:0,behavior:prefs().reduceMotion?'auto':'smooth'}); }
    else { history.pushState({},'',routeHref(path)); window.dispatchEvent(new PopStateEvent('popstate')); }
  }

  function appNav() {
    return [
      ['/feed','⌂','Home'],['/reels','▶','Reels'],['/search','⌕','Search'],['/messages','✉','Messages'],['/activity','♡','Activity'],['/market','▣','Market'],['/connect','◎','Connect'],['/games','◇','Games'],['/ai','✦','Blink AI']
    ].map(([route,icon,label])=>`<button data-parity-go="${route}"><span>${icon}</span>${label}</button>`).join('');
  }
  function parityNav() {
    return [
      ['/more','All app features'],['/store','Blink Store'],['/vault','Vault'],['/vip','VIP'],['/boosts','Boosts'],['/calls','Calls'],['/study','Study Circles'],['/analytics','Analytics'],['/offline','Offline'],['/settings/appearance','Web settings']
    ].map(([r,l])=>`<button class="${currentPath()===r?'active':''}" data-parity-go="${r}">${esc(l)}</button>`).join('');
  }
  function shell(title,body,aside='') {
    document.title=`${title} · Blink`;
    root().innerHTML=`<div class="parity-root" data-parity-root>
      <a class="parity-skip" href="#parity-main">Skip to content</a>
      <aside class="parity-left"><button class="parity-brand" data-parity-go="/feed"><span>B</span><strong>Blink</strong></button><nav>${appNav()}</nav><div class="parity-divider"></div><nav>${parityNav()}</nav></aside>
      <main class="parity-main" id="parity-main"><header class="parity-top"><div><span class="parity-eyebrow">Web parity</span><h1>${esc(title)}</h1></div><div class="parity-top-actions"><button data-parity-command>⌘K</button><button data-parity-go="/more">500</button></div></header><div class="parity-content">${body}</div></main>
      <aside class="parity-right">${aside||rightRail()}</aside>
      <nav class="parity-mobile"><button data-parity-go="/feed">⌂<span>Home</span></button><button data-parity-go="/messages">✉<span>Messages</span></button><button data-parity-go="/store">◆<span>Store</span></button><button data-parity-go="/more">•••<span>More</span></button></nav>
    </div>`;
    bindShell();
  }
  function rightRail() {
    const p=prefs();
    return `<section class="parity-panel"><h3>Web status</h3><div class="parity-status-row"><span class="status-dot ${navigator.onLine?'online':'offline'}"></span><strong>${navigator.onLine?'Online':'Offline'}</strong></div><p class="muted">500 Android-derived web parity capabilities across 50 source modules.</p><button class="parity-btn primary" data-parity-install hidden>Install Blink Web</button></section><section class="parity-panel"><h3>Quick controls</h3><label class="parity-toggle"><span>Reduce motion</span><input type="checkbox" data-pref="reduceMotion" ${p.reduceMotion?'checked':''}></label><label class="parity-toggle"><span>Data saver</span><input type="checkbox" data-pref="dataSaver" ${p.dataSaver?'checked':''}></label><label class="parity-toggle"><span>Autoplay</span><input type="checkbox" data-pref="autoplay" ${p.autoplay?'checked':''}></label></section>`;
  }

  function bindShell() {
    document.querySelectorAll('[data-parity-go]').forEach(x=>x.onclick=()=>go(x.dataset.parityGo));
    document.querySelectorAll('[data-parity-command]').forEach(x=>x.onclick=openCommand);
    document.querySelectorAll('[data-pref]').forEach(x=>x.onchange=()=>savePrefs({[x.dataset.pref]:x.checked}));
    if (installPrompt) document.querySelectorAll('[data-parity-install]').forEach(x=>{x.hidden=false;x.onclick=installApp;});
  }

  function loading(title) { shell(title,'<div class="parity-loading"><div class="spinner"></div><p>Loading from your Blink account…</p></div>'); }
  function authGuard() { if (authed()) return true; toast('Sign in to Blink first.','warn'); go('/login'); return false; }

  async function getProfile() {
    if (!uid()) return null;
    const rows=await table('profiles',`id=eq.${encodeURIComponent(uid())}&select=id,username,full_name,avatar_url,follower_count,following_count,posts_count,points,is_verified,verification_badge,blink_vip_until,professional_headline,university,faculty,department,academic_level&limit=1`);
    return Array.isArray(rows)?rows[0]||null:null;
  }

  async function storeState() { return rpc('get_blink_store_state',{}); }
  const arrayish=(v)=>Array.isArray(v)?v:(Array.isArray(v?.items)?v.items:[]);
  const pick=(o,...keys)=>keys.find(k=>o&&o[k]!==undefined)?o[keys.find(k=>o&&o[k]!==undefined)]:undefined;

  async function renderStore() {
    if (!authGuard()) return; loading('Blink Store');
    try {
      const s=await storeState(); const catalog=arrayish(s?.catalog||s?.items||[]); const balance=pick(s,'balance','coin_balance','coinBalance')||0; const vip=s?.vip||{};
      shell('Blink Store',`<section class="parity-hero"><div><span class="parity-eyebrow">Same economy as Android</span><h2>${money(balance)}</h2><p>Buy with Blink Coins. Charges and entitlements remain server-side.</p></div><div class="hero-actions"><button class="parity-btn" data-parity-go="/vault">Open Vault</button><button class="parity-btn" data-parity-go="/vip">VIP ${pick(vip,'active')?'active':'status'}</button></div></section><div class="parity-grid store-grid">${catalog.map(item=>storeCard(item,balance)).join('')||'<div class="parity-panel"><h3>Store catalog unavailable</h3><p>Refresh after your server catalog is available.</p></div>'}</div>`, `<section class="parity-panel"><h3>Wallet</h3><div class="parity-big">${fmt(balance)}</div><p class="muted">Blink Coins</p><button class="parity-btn" data-parity-go="/vault">Inventory</button></section>${rightRail()}`);
      document.querySelectorAll('[data-buy]').forEach(btn=>btn.onclick=()=>purchaseItem(btn.dataset.buy,Number(btn.dataset.multiplier||1)));
    } catch(e) { shell('Blink Store',errorCard(e)); }
  }
  function storeCard(item,balance) {
    const id=pick(item,'id','catalog_id','catalogId')||''; const name=pick(item,'name','title')||'Blink item'; const desc=pick(item,'description')||''; const price=Number(pick(item,'price','coin_price','coinPrice')||0); const category=pick(item,'category')||'Store'; const multipliers=pick(item,'boost_multipliers','boostMultipliers')||[];
    return `<article class="parity-panel store-card"><div class="store-icon">${esc(iconFor(category))}</div><span class="parity-chip">${esc(category)}</span><h3>${esc(name)}</h3><p>${esc(desc)}</p><div class="store-foot"><strong>${fmt(price)} coins</strong>${Array.isArray(multipliers)&&multipliers.length>1?`<select data-mult-select="${esc(id)}">${multipliers.map(m=>`<option value="${m}">${m}×</option>`).join('')}</select>`:''}<button class="parity-btn primary" data-buy="${esc(id)}" ${balance<price?'disabled':''}>Buy</button></div></article>`;
  }
  async function purchaseItem(id,multiplier=1) {
    const select=document.querySelector(`[data-mult-select="${CSS.escape(id)}"]`); if(select) multiplier=Number(select.value||1);
    try { const r=await rpc('purchase_blink_item',{p_catalog_id:id,p_quantity:1,p_boost_multiplier:multiplier}); toast(r?.message||'Purchased.','success'); renderStore(); } catch(e) { toast(e.message,'error'); }
  }

  async function renderVault() {
    if (!authGuard()) return; loading('Vault');
    try {
      const s=await storeState(); const inventory=arrayish(s?.inventory||[]); const balance=pick(s,'balance','coin_balance','coinBalance')||0;
      shell('Vault',`<section class="parity-hero"><div><span class="parity-eyebrow">Your Blink Collection</span><h2>${fmt(inventory.length)} inventory entries</h2><p>Activate usable items and see permanent, timed and content-specific ownership.</p></div><button class="parity-btn" data-parity-go="/store">Browse Store</button></section><div class="parity-list">${inventory.map(vaultRow).join('')||'<div class="parity-panel"><h3>Your Vault is empty</h3><p>Items you buy in Blink Store appear here.</p></div>'}</div>`, `<section class="parity-panel"><h3>Balance</h3><div class="parity-big">${fmt(balance)}</div><p class="muted">coins</p></section>${rightRail()}`);
      document.querySelectorAll('[data-activate]').forEach(btn=>btn.onclick=()=>activateInventory(btn.dataset.activate));
    } catch(e){ shell('Vault',errorCard(e)); }
  }
  function vaultRow(it) {
    const id=pick(it,'id','inventory_id','inventoryId')||''; const name=pick(it,'name','item_name','itemName')||pick(it,'catalog_id','catalogId')||'Blink item'; const status=String(pick(it,'status')||'available'); const qty=pick(it,'quantity')||1; const expires=pick(it,'expires_at','expiresAt'); const target=pick(it,'target_type','targetType');
    return `<article class="parity-panel vault-row"><div class="grow"><span class="parity-chip">${esc(status)}</span><h3>${esc(name)}</h3><p class="muted">Quantity ${fmt(qty)}${expires?` · Expires ${esc(new Date(expires).toLocaleString())}`:''}${target?` · ${esc(target)}`:''}</p></div>${status==='available'?`<div class="vault-actions"><input class="parity-field compact" data-target-for="${esc(id)}" placeholder="Target ID (if needed)"><button class="parity-btn primary" data-activate="${esc(id)}">Activate</button></div>`:''}</article>`;
  }
  async function activateInventory(id) {
    const input=document.querySelector(`[data-target-for="${CSS.escape(id)}"]`); const target=(input?.value||'').trim()||null;
    try { const r=await rpc('activate_blink_item',{p_inventory_id:id,p_target_id:target}); toast(r?.message||'Item activated.','success'); renderVault(); } catch(e) { toast(e.message,'error'); }
  }

  async function renderVip() {
    if (!authGuard()) return; loading('Blink VIP');
    try {
      const s=await storeState(); const v=s?.vip||{}; const active=!!pick(v,'active'); const exp=pick(v,'expires_at','expiresAt');
      const benefits=[['daily_coin_bonus','Daily coin bonus',pick(v,'daily_coin_bonus_claimed','dailyCoinBonusClaimed')?0:1],['post_boost_2x','2× post boost',pick(v,'unclaimed_post_boosts_2x','unclaimedPostBoosts2x')||0],['reel_boost_2x','2× Reel boost',pick(v,'unclaimed_reel_boosts_2x','unclaimedReelBoosts2x')||0],['profile_spotlight','Profile Spotlight',pick(v,'profile_spotlights_remaining','profileSpotlightsRemaining')||0],['post_spotlight','Post Spotlight',pick(v,'post_spotlights_remaining','postSpotlightsRemaining')||0],['reel_spotlight','Reel Spotlight',pick(v,'reel_spotlights_remaining','reelSpotlightsRemaining')||0],['marketplace_highlight','Marketplace highlight',pick(v,'marketplace_highlights_remaining','marketplaceHighlightsRemaining')||0]];
      shell('Blink VIP',`<section class="parity-hero vip-hero"><div><span class="parity-eyebrow">10-day premium pass</span><h2>${active?'VIP active':'VIP inactive'}</h2><p>${exp?`Expires ${esc(new Date(exp).toLocaleString())}`:'Activate or renew through Blink Store.'}</p></div><div class="hero-actions"><button class="parity-btn primary" data-vip-renew>${active?'Renew VIP':'Get VIP'}</button><label class="parity-toggle"><span>Auto renew</span><input type="checkbox" data-vip-auto ${pick(v,'auto_renew','autoRenew')?'checked':''}></label></div></section><div class="parity-grid">${benefits.map(([id,label,count])=>`<article class="parity-panel"><h3>${esc(label)}</h3><div class="parity-big">${fmt(count)}</div><button class="parity-btn" data-vip-claim="${id}" ${!active||!count?'disabled':''}>Claim</button></article>`).join('')}</div><section class="parity-panel"><h3>Gift Blink VIP</h3><div class="inline-form"><input class="parity-field" id="vip-gift-user" placeholder="@username"><button class="parity-btn" data-vip-gift>Gift VIP</button></div></section>`);
      document.querySelector('[data-vip-renew]')?.addEventListener('click',async()=>{try{await rpc('renew_blink_vip',{});toast('VIP updated.','success');renderVip();}catch(e){toast(e.message,'error');}});
      document.querySelector('[data-vip-auto]')?.addEventListener('change',async e=>{try{await rpc('set_blink_vip_auto_renew',{p_enabled:e.target.checked});toast('Auto-renew updated.','success');}catch(err){e.target.checked=!e.target.checked;toast(err.message,'error');}});
      document.querySelectorAll('[data-vip-claim]').forEach(b=>b.onclick=async()=>{try{await rpc('claim_blink_vip_benefit',{p_benefit:b.dataset.vipClaim});toast('Benefit claimed.','success');renderVip();}catch(e){toast(e.message,'error');}});
      document.querySelector('[data-vip-gift]')?.addEventListener('click',async()=>{const username=document.getElementById('vip-gift-user').value.trim().replace(/^@/,'');if(!username)return toast('Enter a username.','warn');try{await rpc('gift_blink_vip',{p_recipient_username:username});toast('VIP gift sent.','success');}catch(e){toast(e.message,'error');}});
    } catch(e){ shell('Blink VIP',errorCard(e)); }
  }

  async function renderBoosts() {
    if (!authGuard()) return; loading('Boosts');
    try {
      const [s,content]=await Promise.all([storeState(),rpc('get_my_blink_boostable_content',{}).catch(()=>({}))]); const inventory=arrayish(s?.inventory||[]).filter(i=>/boost|spotlight/i.test(`${pick(i,'name','item_name','itemName')||''} ${pick(i,'catalog_id','catalogId')||''}`)); const active=arrayish(s?.active_boosts||s?.activeBoosts||[]); const posts=arrayish(content?.posts||content?.items||content);
      shell('Boosts',`<section class="parity-hero"><div><span class="parity-eyebrow">Creator promotion</span><h2>${fmt(active.length)} active boosts</h2><p>Use eligible Vault credits on your own posts or Reels. Distribution rules remain server-side.</p></div><button class="parity-btn" data-parity-go="/store">Buy boost</button></section><h2 class="section-title">Active</h2><div class="parity-grid">${active.map(b=>`<article class="parity-panel"><span class="parity-chip">${esc(pick(b,'status')||'active')}</span><h3>${esc(pick(b,'content_type','contentType')||'Content')} ${esc(String(pick(b,'multiplier')||1))}×</h3><p>${fmt(pick(b,'extra_impressions','extraImpressions')||0)} extra impressions · ${fmt(pick(b,'profile_visits','profileVisits')||0)} profile visits</p></article>`).join('')||'<div class="parity-panel">No active boosts.</div>'}</div><h2 class="section-title">Available credits</h2><div class="parity-list">${inventory.map(i=>`<article class="parity-panel boost-row"><div class="grow"><h3>${esc(pick(i,'name','item_name','itemName')||pick(i,'catalog_id','catalogId'))}</h3><p class="muted">${esc(pick(i,'status')||'available')} · quantity ${fmt(pick(i,'quantity')||1)}</p></div><select class="parity-field compact" data-boost-target="${esc(pick(i,'id','inventory_id','inventoryId'))}">${posts.map(p=>`<option value="${esc(pick(p,'id','content_id','contentId'))}">${esc((pick(p,'text','caption','title')||pick(p,'id')||'Content').toString().slice(0,55))}</option>`).join('')}</select><button class="parity-btn primary" data-use-boost="${esc(pick(i,'id','inventory_id','inventoryId'))}" ${!posts.length?'disabled':''}>Use</button></article>`).join('')||'<div class="parity-panel">No boost credits in your Vault.</div>'}</div>`);
      document.querySelectorAll('[data-use-boost]').forEach(b=>b.onclick=async()=>{const id=b.dataset.useBoost;const target=document.querySelector(`[data-boost-target="${CSS.escape(id)}"]`)?.value||null;try{await rpc('activate_blink_item',{p_inventory_id:id,p_target_id:target});toast('Boost activated.','success');renderBoosts();}catch(e){toast(e.message,'error');}});
    } catch(e){ shell('Boosts',errorCard(e)); }
  }

  async function renderGifts() {
    if (!authGuard()) return; loading('Digital gifts');
    try { const s=await storeState(); const inv=arrayish(s?.inventory||[]).filter(i=>/gift/i.test(`${pick(i,'name','item_name','itemName')||''} ${pick(i,'catalog_id','catalogId')||''}`) && String(pick(i,'status')||'').toLowerCase()==='available');
      shell('Digital gifts',`<section class="parity-panel"><h3>Send a collectible gift</h3><p class="muted">Uses the same Vault and server-side gift rules as Android.</p><div class="form-stack"><select class="parity-field" id="gift-item">${inv.map(i=>`<option value="${esc(pick(i,'id','inventory_id','inventoryId'))}">${esc(pick(i,'name','item_name','itemName')||'Digital gift')}</option>`).join('')}</select><input class="parity-field" id="gift-user" placeholder="@recipient"><textarea class="parity-field" id="gift-message" maxlength="200" placeholder="Gift message (optional)"></textarea><button class="parity-btn primary" id="send-gift" ${!inv.length?'disabled':''}>Send gift</button></div></section>`);
      document.getElementById('send-gift')?.addEventListener('click',async()=>{const inventory=document.getElementById('gift-item').value;const username=document.getElementById('gift-user').value.trim().replace(/^@/,'');const message=document.getElementById('gift-message').value.trim();if(!username)return toast('Enter a recipient username.','warn');try{await rpc('send_blink_digital_gift',{p_inventory_id:inventory,p_recipient_username:username,p_message:message||null});toast('Gift sent.','success');renderGifts();}catch(e){toast(e.message,'error');}});
    } catch(e){ shell('Digital gifts',errorCard(e)); }
  }

  async function renderCalls() {
    if (!authGuard()) return; loading('Calls');
    try { const rows=await table('calls','select=*&order=created_at.desc&limit=50'); const me=uid(); const peerIds=[...new Set((rows||[]).map(c=>c.caller_id===me?c.callee_id:c.caller_id).filter(Boolean))]; let profiles=[]; if(peerIds.length) profiles=await table('profiles',`id=in.(${peerIds.map(encodeURIComponent).join(',')})&select=id,username,full_name,avatar_url`).catch(()=>[]); const map=new Map((profiles||[]).map(p=>[p.id,p]));
      shell('Calls',`<section class="parity-hero"><div><span class="parity-eyebrow">Synced call history</span><h2>${fmt((rows||[]).length)} recent calls</h2><p>Voice/video call records use the same calls table as Android. Live browser media remains gated until WebRTC browser signaling is fully validated.</p></div><button class="parity-btn" data-parity-go="/messages">Open Messages</button></section><div class="parity-list">${(rows||[]).map(c=>{const peer=map.get(c.caller_id===me?c.callee_id:c.caller_id)||{};return `<article class="parity-panel call-row"><div class="parity-avatar">${esc((peer.full_name||peer.username||'B')[0].toUpperCase())}</div><div class="grow"><h3>${esc(peer.full_name||peer.username||'Blink user')}</h3><p class="muted">${esc(c.call_type||'voice')} · ${esc(c.status||'unknown')} · ${esc(new Date(c.created_at).toLocaleString())}</p></div><span class="parity-chip">${c.caller_id===me?'Outgoing':'Incoming'}</span></article>`;}).join('')||'<div class="parity-panel">No call history yet.</div>'}</div>`);
    } catch(e){ shell('Calls',errorCard(e)); }
  }

  async function renderStudy() {
    if (!authGuard()) return; loading('Study Circles');
    try { const rows=arrayish(await rpc('get_study_circles_page',{p_query:null,p_limit:60,p_offset:0}));
      shell('Study Circles',`<section class="parity-panel"><div class="panel-head"><div><h3>Campus study groups</h3><p class="muted">Same Study Circle RPCs as Android.</p></div><button class="parity-btn" data-study-create>Create circle</button></div></section><div class="parity-grid">${rows.map(c=>`<article class="parity-panel"><span class="parity-chip">${c.is_private?'Private':'Open'}</span><h3>${esc(c.name||'Study Circle')}</h3><p>${esc(c.description||'')}</p><p class="muted">${esc(c.faculty||'')} ${c.course?`· ${esc(c.course)}`:''} · ${fmt(c.member_count)} / ${fmt(c.max_members||50)} members</p><button class="parity-btn ${c.is_member?'':'primary'}" data-study-action="${esc(c.id)}" data-member="${c.is_member?'1':'0'}">${c.is_member?'Leave':'Join'}</button></article>`).join('')||'<div class="parity-panel">No Study Circles found.</div>'}</div>`);
      document.querySelectorAll('[data-study-action]').forEach(b=>b.onclick=async()=>{try{if(b.dataset.member==='1')await rpc('leave_study_circle',{p_circle_id:b.dataset.studyAction});else await rpc('request_study_circle_join',{p_circle_id:b.dataset.studyAction});toast(b.dataset.member==='1'?'Left circle.':'Join request updated.','success');renderStudy();}catch(e){toast(e.message,'error');}});
      document.querySelector('[data-study-create]')?.addEventListener('click',openStudyCreate);
    } catch(e){ shell('Study Circles',errorCard(e)); }
  }
  function openStudyCreate() { openMiniModal('Create Study Circle',`<div class="form-stack"><input class="parity-field" id="study-name" placeholder="Circle name"><textarea class="parity-field" id="study-desc" placeholder="Description"></textarea><input class="parity-field" id="study-faculty" placeholder="Faculty"><input class="parity-field" id="study-course" placeholder="Course"><input class="parity-field" id="study-max" type="number" min="2" max="200" value="50"><label class="parity-toggle"><span>Private</span><input type="checkbox" id="study-private"></label><button class="parity-btn primary" id="study-save">Create</button></div>`); document.getElementById('study-save').onclick=async()=>{try{await rpc('create_study_circle',{p_name:document.getElementById('study-name').value.trim(),p_description:document.getElementById('study-desc').value.trim()||null,p_faculty:document.getElementById('study-faculty').value.trim()||null,p_course:document.getElementById('study-course').value.trim()||null,p_max_members:Number(document.getElementById('study-max').value||50),p_is_private:document.getElementById('study-private').checked});closeMiniModal();toast('Study Circle created.','success');renderStudy();}catch(e){toast(e.message,'error');}}; }

  async function renderAnalytics() {
    if (!authGuard()) return; loading('Creator analytics');
    try { const [p,posts]=await Promise.all([getProfile(),table('posts',`user_id=eq.${encodeURIComponent(uid())}&select=id,is_reel,view_count,like_count,comment_count,repost_count,created_at&order=created_at.desc&limit=100`).catch(()=>[])]); const sum=(k)=>(posts||[]).reduce((a,x)=>a+Number(x[k]||0),0); const reels=(posts||[]).filter(x=>x.is_reel).length;
      shell('Creator analytics',`<section class="parity-hero"><div><span class="parity-eyebrow">Profile snapshot</span><h2>${esc(p?.full_name||p?.username||'Creator')}</h2><p>${esc(p?.professional_headline||'Your web creator metrics use the same profile and content data as Android.')}</p></div></section><div class="metrics-grid"><div class="metric"><strong>${fmt(p?.follower_count)}</strong><span>Followers</span></div><div class="metric"><strong>${fmt(p?.following_count)}</strong><span>Following</span></div><div class="metric"><strong>${fmt(p?.posts_count)}</strong><span>Posts</span></div><div class="metric"><strong>${fmt(p?.points)}</strong><span>Points</span></div><div class="metric"><strong>${fmt(sum('view_count'))}</strong><span>Views (last 100)</span></div><div class="metric"><strong>${fmt(sum('like_count'))}</strong><span>Likes</span></div><div class="metric"><strong>${fmt(sum('comment_count'))}</strong><span>Comments</span></div><div class="metric"><strong>${fmt(reels)}</strong><span>Reels</span></div></div><section class="parity-panel"><h3>Recent content performance</h3><div class="mini-table">${(posts||[]).slice(0,20).map(x=>`<div><span>${x.is_reel?'Reel':'Post'} · ${new Date(x.created_at).toLocaleDateString()}</span><strong>${fmt(x.view_count)} views · ${fmt(x.like_count)} likes</strong></div>`).join('')||'<p>No recent content.</p>'}</div></section>`);
    } catch(e){ shell('Creator analytics',errorCard(e)); }
  }

  async function renderVerification() {
    if (!authGuard()) return; loading('Verification');
    try { const p=await getProfile(); const badge=String(p?.verification_badge||'').toLowerCase(); const active=!!p?.is_verified;
      shell('Verification',`<section class="parity-hero"><div><span class="parity-eyebrow">Identity</span><h2>${active?`${badge||'Verified'} verification active`:'Not verified yet'}</h2><p>${active?'Your public web profile already displays your verified identity.':'Verification status is synced from the same profile record used by Android.'}</p></div><button class="parity-btn" data-parity-go="/@${esc(p?.username||'')}">View profile</button></section><section class="parity-panel"><h3>Current status</h3><div class="status-card"><div class="verification-orb ${active?'active':''}">${active?'✓':'B'}</div><div><strong>${esc(p?.full_name||p?.username||'Blink account')}</strong><p class="muted">${active?`${esc(badge||'standard')} badge`:'No active verification badge'}</p></div></div></section>`);
    } catch(e){ shell('Verification',errorCard(e)); }
  }

  async function renderOffline() {
    const estimate=navigator.storage?.estimate?await navigator.storage.estimate().catch(()=>null):null; const regs='serviceWorker' in navigator?await navigator.serviceWorker.getRegistrations().catch(()=>[]):[]; const draftKeys=Object.keys(localStorage).filter(k=>/draft/i.test(k));
    shell('Offline & data saver',`<section class="parity-hero"><div><span class="parity-eyebrow">Offline parity</span><h2>${navigator.onLine?'Connected':'Working offline'}</h2><p>Web shell caching, local drafts, data saver, recovery controls and network awareness mirror the app’s offline-first behavior where browser capabilities allow.</p></div></section><div class="metrics-grid"><div class="metric"><strong>${regs.length?'Active':'Off'}</strong><span>Service worker</span></div><div class="metric"><strong>${fmt(draftKeys.length)}</strong><span>Local drafts</span></div><div class="metric"><strong>${estimate?`${Math.round((estimate.usage||0)/1024/1024)} MB`:'—'}</strong><span>Browser storage</span></div><div class="metric"><strong>${prefs().dataSaver?'On':'Off'}</strong><span>Data saver</span></div></div><section class="parity-panel"><h3>Controls</h3><label class="parity-toggle"><span>Data saver</span><input type="checkbox" data-pref="dataSaver" ${prefs().dataSaver?'checked':''}></label><label class="parity-toggle"><span>Autoplay videos</span><input type="checkbox" data-pref="autoplay" ${prefs().autoplay?'checked':''}></label><button class="parity-btn" data-clear-web-cache>Refresh offline cache</button><button class="parity-btn" data-parity-go="/drafts">Open drafts</button></section>`);
    bindShell(); document.querySelector('[data-clear-web-cache]')?.addEventListener('click',async()=>{try{for(const k of await caches.keys())if(k.startsWith('blink-web'))await caches.delete(k);for(const r of await navigator.serviceWorker.getRegistrations())await r.update();toast('Offline cache refreshed.','success');}catch(e){toast(e.message,'error');}});
  }

  function renderDrafts() {
    const keys=Object.keys(localStorage).filter(k=>/draft/i.test(k));
    shell('Drafts',`<section class="parity-panel"><h3>Persistent web drafts</h3><p class="muted">Draft storage is local to this browser. Existing Blink web post drafts remain compatible with the main composer.</p></section><div class="parity-list">${keys.map(k=>{let v=localStorage.getItem(k)||'';return `<article class="parity-panel"><span class="parity-chip">${esc(k)}</span><textarea class="parity-field" data-draft-key="${esc(k)}">${esc(v)}</textarea><div class="row-actions"><button class="parity-btn" data-save-draft="${esc(k)}">Save</button><button class="parity-btn danger" data-delete-draft="${esc(k)}">Delete</button></div></article>`;}).join('')||'<div class="parity-panel">No drafts saved in this browser.</div>'}</div>`);
    document.querySelectorAll('[data-save-draft]').forEach(b=>b.onclick=()=>{const k=b.dataset.saveDraft;localStorage.setItem(k,document.querySelector(`[data-draft-key="${CSS.escape(k)}"]`).value);toast('Draft saved.','success');}); document.querySelectorAll('[data-delete-draft]').forEach(b=>b.onclick=()=>{localStorage.removeItem(b.dataset.deleteDraft);toast('Draft deleted.','success');renderDrafts();});
  }

  async function getUserSettings() {
    if (!uid()) return {};
    const rows=await table('user_settings',`user_id=eq.${encodeURIComponent(uid())}&select=*&limit=1`).catch(()=>[]); return rows?.[0]||{};
  }
  async function saveUserSettings(fields) {
    return api('/rest/v1/user_settings?on_conflict=user_id',{method:'POST',body:{user_id:uid(),...fields,updated_at:new Date().toISOString()},prefer:'resolution=merge-duplicates,return=representation'});
  }

  async function renderNotificationSettings() {
    if (!authGuard()) return; loading('Notification settings');
    try { const s=await getUserSettings(); const perm='Notification' in window?Notification.permission:'unsupported';
      shell('Notification settings',`<section class="parity-panel"><h3>Browser notification permission</h3><p class="muted">Browser permission: ${esc(perm)}</p><button class="parity-btn" data-notify-permission ${perm==='unsupported'?'disabled':''}>Request permission</button></section><section class="parity-panel"><h3>Blink preferences</h3>${toggleSetting('push_notifs_enabled','Push notifications',s.push_notifs_enabled!==false)}${toggleSetting('email_notifs_enabled','Email notifications',s.email_notifs_enabled!==false)}${toggleSetting('show_online_status','Show online status',s.show_online_status!==false)}${toggleSetting('read_receipts','Read receipts',s.read_receipts!==false)}<button class="parity-btn primary" data-save-settings>Save</button></section>`);
      document.querySelector('[data-notify-permission]')?.addEventListener('click',async()=>{try{const r=await Notification.requestPermission();toast(`Notification permission: ${r}`,'success');renderNotificationSettings();}catch(e){toast(e.message,'error');}}); document.querySelector('[data-save-settings]')?.addEventListener('click',()=>saveSettingsFromForm(['push_notifs_enabled','email_notifs_enabled','show_online_status','read_receipts'],renderNotificationSettings));
    } catch(e){ shell('Notification settings',errorCard(e)); }
  }
  function toggleSetting(key,label,checked){return `<label class="parity-toggle"><span>${esc(label)}</span><input type="checkbox" data-setting="${key}" ${checked?'checked':''}></label>`;}
  async function saveSettingsFromForm(keys,rerender){const fields={};keys.forEach(k=>fields[k]=!!document.querySelector(`[data-setting="${k}"]`)?.checked);try{await saveUserSettings(fields);toast('Settings saved.','success');if(rerender)rerender();}catch(e){toast(e.message,'error');}}

  async function renderAppearanceSettings() {
    if (!authGuard()) return; const p=prefs(); const s=await getUserSettings().catch(()=>({}));
    shell('Appearance',`<section class="parity-panel"><h3>Theme</h3><div class="segmented">${['system','dark','light'].map(v=>`<button class="${p.theme===v?'active':''}" data-theme="${v}">${v}</button>`).join('')}</div><h3>Density</h3><div class="segmented">${['compact','comfortable','spacious'].map(v=>`<button class="${p.density===v?'active':''}" data-density="${v}">${v}</button>`).join('')}</div><h3>Text size</h3><input type="range" min="0.85" max="1.3" step="0.05" value="${Number(p.fontScale)||1}" data-font-scale><label class="parity-toggle"><span>Reduce motion</span><input type="checkbox" data-pref="reduceMotion" ${p.reduceMotion?'checked':''}></label><label class="parity-toggle"><span>Autoplay videos</span><input type="checkbox" data-pref="autoplay" ${p.autoplay?'checked':''}></label><button class="parity-btn primary" data-sync-appearance>Save to Blink account</button></section>`);
    bindShell(); document.querySelectorAll('[data-theme]').forEach(b=>b.onclick=()=>{savePrefs({theme:b.dataset.theme});renderAppearanceSettings();}); document.querySelectorAll('[data-density]').forEach(b=>b.onclick=()=>{savePrefs({density:b.dataset.density});renderAppearanceSettings();}); document.querySelector('[data-font-scale]').oninput=e=>savePrefs({fontScale:Number(e.target.value)}); document.querySelector('[data-sync-appearance]').onclick=async()=>{try{await saveUserSettings({theme:prefs().theme,autoplay_videos:prefs().autoplay,reduce_motion:prefs().reduceMotion,data_saver:prefs().dataSaver});toast('Appearance synced.','success');}catch(e){toast(e.message,'error');}};
  }

  async function renderPrivacySettings() {
    if (!authGuard()) return; loading('Privacy'); try{const s=await getUserSettings();shell('Privacy',`<section class="parity-panel"><h3>Account privacy</h3>${toggleSetting('private_account','Private account',!!s.private_account)}${toggleSetting('show_online_status','Show online status',s.show_online_status!==false)}${toggleSetting('read_receipts','Read receipts',s.read_receipts!==false)}<label class="field-label">Who can message you<select class="parity-field" data-dm-privacy><option value="everyone" ${s.dm_privacy==='everyone'?'selected':''}>Everyone</option><option value="followers" ${s.dm_privacy==='followers'?'selected':''}>Followers</option><option value="connections" ${s.dm_privacy==='connections'?'selected':''}>Connections</option><option value="none" ${s.dm_privacy==='none'?'selected':''}>No one</option></select></label><button class="parity-btn primary" data-save-privacy>Save privacy</button></section>`);document.querySelector('[data-save-privacy]').onclick=async()=>{const fields={private_account:document.querySelector('[data-setting="private_account"]').checked,show_online_status:document.querySelector('[data-setting="show_online_status"]').checked,read_receipts:document.querySelector('[data-setting="read_receipts"]').checked,dm_privacy:document.querySelector('[data-dm-privacy]').value};try{await saveUserSettings(fields);toast('Privacy saved.','success');}catch(e){toast(e.message,'error');}};}catch(e){shell('Privacy',errorCard(e));}
  }

  function renderAccessibility() {
    const p=prefs(); shell('Accessibility',`<section class="parity-panel"><h3>Web accessibility</h3><label class="parity-toggle"><span>Reduce motion</span><input type="checkbox" data-pref="reduceMotion" ${p.reduceMotion?'checked':''}></label><label class="field-label">Text scale<input class="parity-field" type="range" min="0.85" max="1.3" step="0.05" value="${p.fontScale}" data-font-scale></label><label class="field-label">Interface density<select class="parity-field" data-density-select><option value="compact">Compact</option><option value="comfortable">Comfortable</option><option value="spacious">Spacious</option></select></label><p class="muted">All parity controls use keyboard-focusable elements, visible focus states, labeled inputs and a skip-to-content link.</p></section>`); bindShell(); document.querySelector('[data-font-scale]').oninput=e=>savePrefs({fontScale:Number(e.target.value)}); const d=document.querySelector('[data-density-select]');d.value=p.density;d.onchange=()=>savePrefs({density:d.value});
  }

  async function renderDataSettings() {
    if (!authGuard()) return; shell('Your data',`<section class="parity-panel"><h3>Export Blink account data</h3><p class="muted">Uses the same <code>export_my_account_data</code> server RPC as Android.</p><button class="parity-btn primary" data-export>Export my data</button></section><section class="parity-panel"><h3>Local browser data</h3><p class="muted">Clear only Blink Web parity preferences and local drafts on this browser.</p><button class="parity-btn danger" data-clear-local>Clear local web data</button></section>`); document.querySelector('[data-export]').onclick=async()=>{try{const data=await rpc('export_my_account_data',{});const blob=new Blob([typeof data==='string'?data:JSON.stringify(data,null,2)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`blink-account-export-${new Date().toISOString().slice(0,10)}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),2000);toast('Export ready.','success');}catch(e){toast(e.message,'error');}}; document.querySelector('[data-clear-local]').onclick=()=>{Object.keys(localStorage).filter(k=>/blink_web_parity|draft/i.test(k)).forEach(k=>localStorage.removeItem(k));toast('Local web data cleared.','success');applyPrefs();};
  }

  async function renderAccounts() {
    if (!authGuard()) return; const p=await getProfile().catch(()=>null); let recent=[]; try{recent=JSON.parse(localStorage.getItem(RECENT_KEY)||'[]');}catch{} if(p?.username&&!recent.some(x=>x.username===p.username)){recent=[{username:p.username,name:p.full_name||p.username,last:new Date().toISOString()},...recent].slice(0,8);localStorage.setItem(RECENT_KEY,JSON.stringify(recent));}
    shell('Accounts',`<section class="parity-panel"><h3>Current account</h3><div class="status-card"><div class="parity-avatar">${esc((p?.full_name||p?.username||'B')[0].toUpperCase())}</div><div class="grow"><strong>${esc(p?.full_name||p?.username||'Blink account')}</strong><p class="muted">@${esc(p?.username||'')}</p></div><button class="parity-btn" data-parity-go="/settings/profile">Edit profile</button></div></section><section class="parity-panel"><h3>Recent web accounts</h3><p class="muted">For security, the parity layer remembers account names only — not additional access tokens.</p>${recent.map(r=>`<div class="mini-row"><span>@${esc(r.username)}</span><span class="muted">${esc(r.name||'')}</span></div>`).join('')||'<p>No recent accounts.</p>'}<button class="parity-btn" data-parity-go="/login">Sign in with another account</button></section>`);
  }

  async function renderProfessional() {
    if (!authGuard()) return; loading('Professional Center'); try { const [settings,orders,blocks]=await Promise.all([getUserSettings(),table('marketplace_orders',`or=(buyer_id.eq.${encodeURIComponent(uid())},seller_id.eq.${encodeURIComponent(uid())})&select=*&order=created_at.desc&limit=25`).catch(()=>[]),table('blocks',`blocker_id=eq.${encodeURIComponent(uid())}&select=blocked_id,created_at&order=created_at.desc&limit=50`).catch(()=>[])]);
      shell('Professional Center',`<div class="metrics-grid"><div class="metric"><strong>${fmt(orders.length)}</strong><span>Marketplace orders</span></div><div class="metric"><strong>${fmt(blocks.length)}</strong><span>Blocked accounts</span></div><div class="metric"><strong>${settings.private_account?'Private':'Public'}</strong><span>Account visibility</span></div><div class="metric"><strong>${settings.dm_privacy||'everyone'}</strong><span>DM privacy</span></div></div><section class="parity-panel"><h3>Professional shortcuts</h3><div class="shortcut-grid"><button data-parity-go="/seller">Seller tools</button><button data-parity-go="/analytics">Creator analytics</button><button data-parity-go="/settings/privacy">Privacy</button><button data-parity-go="/settings/data">Export data</button><button data-parity-go="/verification">Verification</button><button data-parity-go="/study">Study Circles</button></div></section>`); bindShell();
    } catch(e){ shell('Professional Center',errorCard(e)); }
  }

  async function renderLeaderboard() {
    if (!authGuard()) return; loading('Leaderboard'); try { const raw=await rpc('get_game_leaderboard',{p_period:'all_time',p_scope:'global',p_limit:100}); const rows=arrayish(raw?.rows||raw?.leaderboard||raw); shell('Leaderboard',`<section class="parity-hero"><div><span class="parity-eyebrow">Global</span><h2>Top Blink players</h2><p>Same leaderboard RPC as the Android game section.</p></div><button class="parity-btn" data-parity-go="/games">Open Games</button></section><div class="parity-list">${rows.map((u,i)=>`<article class="parity-panel leaderboard-row"><strong class="rank">#${i+1}</strong><div class="grow"><h3>${esc(u.full_name||u.username||'Player')}</h3><p class="muted">@${esc(u.username||'')} · ${fmt(u.score||u.points)} points</p></div></article>`).join('')||'<div class="parity-panel">Leaderboard is empty.</div>'}</div>`);}catch(e){shell('Leaderboard',errorCard(e));}
  }

  async function renderSeller() {
    if (!authGuard()) return; loading('Seller tools'); try { const orders=await table('marketplace_orders',`seller_id=eq.${encodeURIComponent(uid())}&select=*&order=created_at.desc&limit=100`).catch(()=>[]); const listings=await table('market_items',`seller_id=eq.${encodeURIComponent(uid())}&select=id,title,price,currency,status,created_at&order=created_at.desc&limit=100`).catch(()=>[]); shell('Seller tools',`<div class="metrics-grid"><div class="metric"><strong>${fmt(listings.length)}</strong><span>Listings</span></div><div class="metric"><strong>${fmt(orders.length)}</strong><span>Orders</span></div><div class="metric"><strong>${fmt(orders.filter(o=>o.status==='completed').length)}</strong><span>Completed</span></div><div class="metric"><strong>${fmt(orders.filter(o=>o.status==='pending').length)}</strong><span>Pending</span></div></div><section class="parity-panel"><div class="panel-head"><h3>Your listings</h3><button class="parity-btn" data-parity-go="/market">Open Marketplace</button></div>${listings.slice(0,20).map(i=>`<div class="mini-row"><span>${esc(i.title||'Listing')}</span><strong>${i.currency==='NGN'?new Intl.NumberFormat('en-NG',{style:'currency',currency:'NGN',maximumFractionDigits:0}).format(Number(i.price||0)):fmt(i.price)}</strong></div>`).join('')||'<p>No seller listings found.</p>'}</section>`);bindShell();}catch(e){shell('Seller tools',errorCard(e));}
  }

  function renderHub() {
    const groups=[...new Set(MODULES.map(m=>m.group))].sort(); shell('500 app features on web',`<section class="parity-hero"><div><span class="parity-eyebrow">Android → Web</span><h2>500 parity capabilities</h2><p>50 real Blink Android modules × 10 web parity layers. Search the registry or open a module directly.</p></div><div class="hero-actions"><span class="parity-kpi"><strong>50</strong> modules</span><span class="parity-kpi"><strong>10</strong> layers</span><span class="parity-kpi"><strong>500</strong> capabilities</span></div></section><section class="parity-panel"><div class="feature-tools"><input class="parity-field" id="feature-search" placeholder="Search 500 features, modules or Android source files…"><select class="parity-field compact" id="feature-group"><option value="">All groups</option>${groups.map(g=>`<option>${esc(g)}</option>`).join('')}</select></div><div class="feature-results" id="feature-results"></div></section>`); const render=()=>{const q=document.getElementById('feature-search').value.trim().toLowerCase();const g=document.getElementById('feature-group').value;const rows=FEATURES.filter(f=>(!g||f.group===g)&&(!q||`${f.title} ${f.source} ${f.group} ${f.description}`.toLowerCase().includes(q))).slice(0,120);document.getElementById('feature-results').innerHTML=`<div class="feature-count">Showing ${fmt(rows.length)} of ${fmt(FEATURES.filter(f=>(!g||f.group===g)&&(!q||`${f.title} ${f.source} ${f.group} ${f.description}`.toLowerCase().includes(q))).length)} matching capabilities</div>${rows.map(f=>`<button class="feature-row" data-feature-route="${esc(f.route)}"><span class="feature-id">${esc(f.id)}</span><span class="grow"><strong>${esc(f.title)}</strong><small>${esc(f.group)} · ${esc(f.source)}</small></span><span>→</span></button>`).join('')}`;document.querySelectorAll('[data-feature-route]').forEach(b=>b.onclick=()=>go(b.dataset.featureRoute));};document.getElementById('feature-search').oninput=render;document.getElementById('feature-group').onchange=render;render();
  }

  function errorCard(e){return `<section class="parity-panel error-panel"><h3>Couldn’t load this Blink feature</h3><p>${esc(e?.message||e)}</p><div class="row-actions"><button class="parity-btn" data-parity-go="${currentPath()}">Try again</button><button class="parity-btn" data-parity-go="/more">Open parity center</button></div></section>`;}
  function iconFor(category=''){const s=String(category).toLowerCase();if(s.includes('profile'))return '◎';if(s.includes('chat'))return '✉';if(s.includes('reel'))return '▶';if(s.includes('boost'))return '↗';if(s.includes('market'))return '▣';if(s.includes('vip'))return '◆';if(s.includes('gift'))return '🎁';return '✦';}

  function openMiniModal(title,html){closeMiniModal();const d=document.createElement('div');d.id='parity-modal';d.className='parity-modal-backdrop';d.innerHTML=`<div class="parity-modal"><header><h2>${esc(title)}</h2><button data-close-parity-modal>×</button></header>${html}</div>`;document.body.appendChild(d);d.onclick=e=>{if(e.target===d)closeMiniModal();};d.querySelector('[data-close-parity-modal]').onclick=closeMiniModal;}
  function closeMiniModal(){document.getElementById('parity-modal')?.remove();}

  function openCommand() {
    openMiniModal('Blink command palette',`<input class="parity-field" id="command-search" autofocus placeholder="Search 500 features or type a destination…"><div id="command-results" class="command-results"></div>`); const input=document.getElementById('command-search'); const draw=()=>{const q=input.value.trim().toLowerCase();const rows=FEATURES.filter(f=>!q||`${f.title} ${f.source} ${f.group}`.toLowerCase().includes(q)).slice(0,18);document.getElementById('command-results').innerHTML=rows.map(f=>`<button data-command-route="${esc(f.route)}"><span><strong>${esc(f.title)}</strong><small>${esc(f.source)}</small></span><kbd>↵</kbd></button>`).join('');document.querySelectorAll('[data-command-route]').forEach(b=>b.onclick=()=>{closeMiniModal();go(b.dataset.commandRoute);});}; input.oninput=draw; input.onkeydown=e=>{if(e.key==='Escape')closeMiniModal();if(e.key==='Enter'){document.querySelector('[data-command-route]')?.click();}};draw();setTimeout(()=>input.focus(),0);
  }

  let installPrompt=null;
  window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();installPrompt=e;decorateExistingShell();});
  async function installApp(){if(!installPrompt)return;await installPrompt.prompt();await installPrompt.userChoice;installPrompt=null;decorateExistingShell();}

  function decorateExistingShell() {
    if (isParityRoute()) return;
    const sidebar=document.querySelector('.sidebar'); if(sidebar&&!sidebar.querySelector('[data-parity-launcher]')){const b=document.createElement('button');b.className='nav-btn';b.dataset.parityLauncher='1';b.innerHTML='<span class="icon">•••</span><span>More app features</span><span class="badge">500</span>';b.onclick=()=>go('/more');const spacer=sidebar.querySelector('.sidebar-spacer');sidebar.insertBefore(b,spacer||null);}
    const top=document.querySelector('.top-actions'); if(top&&!top.querySelector('[data-parity-command]')){const b=document.createElement('button');b.className='btn small-btn ghost parity-command-small';b.dataset.parityCommand='1';b.textContent='⌘K';b.onclick=openCommand;top.prepend(b);}
    if(!document.getElementById('parity-network-pill')){const pill=document.createElement('button');pill.id='parity-network-pill';pill.className=`parity-network-pill ${navigator.onLine?'online':'offline'}`;pill.textContent=navigator.onLine?'Online':'Offline';pill.onclick=()=>go('/offline');document.body.appendChild(pill);}
  }

  function renderParity(path=currentPath()) {
    if (!PARITY_ROUTES.has(path)) return false;
    switch(path){
      case '/more': renderHub(); break;
      case '/store': renderStore(); break;
      case '/vault': renderVault(); break;
      case '/vip': renderVip(); break;
      case '/boosts': renderBoosts(); break;
      case '/gifts': renderGifts(); break;
      case '/calls': renderCalls(); break;
      case '/study': renderStudy(); break;
      case '/analytics': renderAnalytics(); break;
      case '/verification': renderVerification(); break;
      case '/offline': renderOffline(); break;
      case '/drafts': renderDrafts(); break;
      case '/settings/notifications': renderNotificationSettings(); break;
      case '/settings/appearance': renderAppearanceSettings(); break;
      case '/settings/privacy': renderPrivacySettings(); break;
      case '/settings/data': renderDataSettings(); break;
      case '/settings/accessibility': renderAccessibility(); break;
      case '/accounts': renderAccounts(); break;
      case '/professional': renderProfessional(); break;
      case '/leaderboard': renderLeaderboard(); break;
      case '/seller': renderSeller(); break;
      default: renderHub();
    }
    return true;
  }

  document.addEventListener('click',e=>{
    const t=e.target.closest?.('[data-parity-route]'); if(t){e.preventDefault();e.stopPropagation();go(t.dataset.parityRoute);}
  },true);

  window.addEventListener('popstate',()=>{if(isParityRoute())setTimeout(()=>renderParity(currentPath()),0);});
  window.addEventListener('online',()=>{decorateExistingShell();const p=document.getElementById('parity-network-pill');if(p){p.textContent='Online';p.className='parity-network-pill online';}if(isParityRoute())renderParity(currentPath());});
  window.addEventListener('offline',()=>{decorateExistingShell();const p=document.getElementById('parity-network-pill');if(p){p.textContent='Offline';p.className='parity-network-pill offline';}if(isParityRoute())renderParity(currentPath());});

  let chord=''; let chordTimer=null;
  document.addEventListener('keydown',e=>{
    const tag=e.target?.tagName; const typing=['INPUT','TEXTAREA','SELECT'].includes(tag)||e.target?.isContentEditable;
    if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();openCommand();return;}
    if(e.key==='Escape')closeMiniModal();
    if(typing)return;
    const k=e.key.toLowerCase();
    if(k==='g'){chord='g';clearTimeout(chordTimer);chordTimer=setTimeout(()=>chord='',1200);return;}
    if(chord==='g'){const map={h:'/feed',r:'/reels',m:'/messages',s:'/store',v:'/vault',a:'/ai',c:'/calls',o:'/more'};if(map[k]){e.preventDefault();go(map[k]);}chord='';}
  });

  const observer=new MutationObserver(()=>{
    if(isParityRoute()) { if(!document.querySelector('[data-parity-root]')) queueMicrotask(()=>renderParity(currentPath())); }
    else decorateExistingShell();
  });

  applyPrefs();
  observer.observe(document.documentElement,{subtree:true,childList:true});
  setTimeout(()=>{if(isParityRoute())renderParity(currentPath());else decorateExistingShell();},60);

  window.BlinkWebParity={features:FEATURES,modules:MODULES,layers:LAYERS,open:go,command:openCommand,render:renderParity};
})();

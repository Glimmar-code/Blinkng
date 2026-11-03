(() => {
  'use strict';

  const C = window.BLINK_WEB_CONFIG || {};
  const SUPABASE_URL = String(C.supabaseUrl || '').replace(/\/$/, '');
  const KEY = C.publishableKey || '';
  const PREVIEW_BASE = location.hostname.endsWith('github.io') ? '/Blinkng' : '';
  const LOGO_URL = `${PREVIEW_BASE}/blink-logo-v2.png`;
  const SITE_URL = String(C.siteUrl || location.origin).replace(/\/$/, '');
  const ROOT = document.getElementById('app');
  const MODALS = document.getElementById('modal-root');
  const toastEl = document.getElementById('toast');
  const SESSION_KEY = 'blink_web_session_v3';
  const INVITE_REFERRER_KEY = 'blink_web_invited_by_v1';
  const INVITE_VISITOR_KEY = 'blink_web_invite_visitor_v1';
  const DRAFT_KEY = 'blink_web_post_draft_v2';
  const VISITOR_KEY = 'blink_web_visitor_v2';
  const PARITY_PREF_KEY = 'blink_web_parity_prefs_v1';
  const OAUTH_NEXT_KEY = 'blink_web_oauth_next_v1';
  const APK_URL = 'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk';
  const API_TIMEOUT_MS = 20000;

  const state = {
    session: null,
    profile: null,
    following: new Set(),
    feed: [],
    feedMode: 'posts',
    feedOffset: 0,
    feedAsOf: null,
    loading: false,
    notificationsUnread: 0,
    currentTitle: 'Blink',
    qualifiedTimers: new Map(),
    viewObserver: null,
    mediaObserver: null,
    searchRequestId: 0,
    pageSerial: 0,
    authError: null,
  };

  let refreshPromise = null;

  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const safeUrl = (v) => { try { const u = new URL(String(v || ''), location.origin); return ['http:','https:','blob:'].includes(u.protocol) ? u.href : ''; } catch { return ''; } };
  const fmt = (v) => new Intl.NumberFormat().format(Number(v || 0));
  const money = (v, currency='NGN') => { try { return new Intl.NumberFormat('en-NG',{style:'currency',currency:currency||'NGN',maximumFractionDigits:0}).format(Number(v||0)); } catch { return `₦${fmt(v)}`; } };
  const ago = (v) => { const d=new Date(v); if(Number.isNaN(d.valueOf()))return ''; const s=Math.max(1,Math.floor((Date.now()-d)/1000)); if(s<60)return `${s}s`; if(s<3600)return `${Math.floor(s/60)}m`; if(s<86400)return `${Math.floor(s/3600)}h`; if(s<604800)return `${Math.floor(s/86400)}d`; return d.toLocaleDateString(); };
  const uid = () => state.session?.user?.id || '';
  const token = () => state.session?.access_token || '';
  const isAuthed = () => !!token();
  const routeHref = (path) => `${PREVIEW_BASE}${path}` || '/';
  const encodeQ = encodeURIComponent;
  const storageGet = (key, fallback='') => { try { const v=localStorage.getItem(key); return v == null ? fallback : v; } catch { return fallback; } };
  const storageSet = (key, value) => { try { localStorage.setItem(key, value); return true; } catch { return false; } };
  const storageRemove = (key) => { try { localStorage.removeItem(key); } catch {} };
  const safeDecodeUri = (value) => { try { return decodeURI(value); } catch { return value; } };
  const draftKey = () => `${DRAFT_KEY}:${uid() || 'anonymous'}`;

  async function fetchWithTimeout(url, options={}, timeoutMs=API_TIMEOUT_MS) {
    const controller = new AbortController();
    const upstream = options.signal;
    let timedOut = false;
    const cancelFromCaller = () => controller.abort(upstream?.reason);
    if(upstream){
      if(upstream.aborted)cancelFromCaller();
      else upstream.addEventListener('abort',cancelFromCaller,{once:true});
    }
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
    try {
      return await fetch(url, {...options, signal: controller.signal});
    } catch (error) {
      if(timedOut && error?.name === 'AbortError')
        throw new Error('Request timed out. Check your connection and try again.');
      throw error;
    } finally {
      clearTimeout(timer);
      upstream?.removeEventListener('abort',cancelFromCaller);
    }
  }

  function toast(message, ms=2400){ toastEl.textContent=message; toastEl.classList.add('show'); clearTimeout(toast.t); toast.t=setTimeout(()=>toastEl.classList.remove('show'),ms); }
  function setTitle(title){ state.currentTitle=title || 'Blink'; document.title = title && title !== 'Blink' ? `${title} · BLINK` : 'BLINK Nigeria | Social & Campus Community App'; }
  function icon(name){ const map={home:'⌂',reels:'▶',search:'⌕',messages:'✉',activity:'♡',market:'▣',connect:'◎',games:'◇',scheduled:'◷',settings:'⚙',ai:'✦',create:'＋'}; return map[name]||'•'; }
  function avatar(url, name='', cls='avatar-sm'){ const src=safeUrl(url); return src ? `<img class="${cls}" src="${esc(src)}" alt="${esc(name)}" loading="lazy">` : `<div class="${cls} avatar-fallback">${esc((name||'B').trim().charAt(0).toUpperCase()||'B')}</div>`; }
  function verifyMark(p){ const badge=String(p?.verification_badge||p?.verificationBadge||'').toLowerCase(); const verified=p?.is_verified ?? p?.isVerified; return verified ? `<span class="verified" title="Verified">●</span>${badge.includes('gold')?'<span class="vip" title="Gold verified">★</span>':''}`:''; }
  function vipMark(p){ const until=p?.blink_vip_until; return until && new Date(until)>new Date() ? '<span class="vip" title="Blink VIP">◆</span>':''; }
  function premiumCosmetic(id=''){
    const known={
      profile_highlight_1h:['AURA','#7c3aed','#ec4899','#f59e0b',92],profile_glow_1h:['NEON','#7c3aed','#ec4899','#2563eb',80],profile_glow_7d:['AURORA','#06b6d4','#7c3aed','#ec4899',96],profile_spotlight_1h:['SPOTLIGHT','#f59e0b','#ec4899','#7c3aed',88],profile_spotlight_24h:['SPOTLIGHT','#f59e0b','#ec4899','#7c3aed',97],birthday_profile_theme:['CELEBRATE','#ec4899','#f59e0b','#06b6d4',86],profile_background:['MIDNIGHT','#111827','#7c3aed','#2563eb',82],profile_theme_3d:['PRISM','#7c3aed','#2563eb','#06b6d4',90],profile_theme_bundle:['SIGNATURE','#2563eb','#7c3aed','#ec4899',94],profile_banner:['BANNER','#111827','#7c3aed','#ec4899',91],profile_particle_effect:['COSMIC','#7c3aed','#06b6d4','#ec4899',93],profile_ring:['CHROME','#2563eb','#7c3aed','#06b6d4',72],animated_profile_ring:['ORBIT','#06b6d4','#7c3aed','#ec4899',88],premium_profile_frame:['PRESTIGE','#f59e0b','#ec4899','#7c3aed',98],avatar_decoration:['AVATAR FX','#ec4899','#7c3aed','#06b6d4',95],username_glow_24h:['LUMINOUS','#7c3aed','#ec4899','#06b6d4',84],username_font:['SIGNATURE','#2563eb','#7c3aed','#ec4899',78],animated_name:['LIVE NAME','#06b6d4','#7c3aed','#ec4899',93],custom_profile_badge:['PREMIUM','#7c3aed','#ec4899','#f59e0b',82],creator_badge:['CREATOR','#f59e0b','#f97316','#ec4899',96],limited_edition_badge:['LIMITED','#f59e0b','#ef4444','#ec4899',99],profile_entrance_animation:['REVEAL','#7c3aed','#ec4899','#2563eb',76],creator_intro_card:['INTRO','#f59e0b','#ec4899','#7c3aed',91],vip_profile_entrance:['VIP','#f59e0b','#7c3aed','#ec4899',100],blink_vip_10d:['VIP','#f59e0b','#7c3aed','#ec4899',100],
      comment_highlight:['SPOTLIGHT','#ec4899','#7c3aed','#f59e0b',96],comment_color:['AURORA','#7c3aed','#2563eb','#06b6d4',78],comment_entrance_animation:['PREMIERE','#06b6d4','#7c3aed','#ec4899',92],vip_comment_effect:['VIP','#f59e0b','#ec4899','#7c3aed',100]
    };
    const x=known[String(id).toLowerCase()];return x?{id,label:x[0],colors:x.slice(1,4),priority:x[4]}:null;
  }
  function cosmeticVars(c){return c?`--premium-1:${c.colors[0]};--premium-2:${c.colors[1]};--premium-3:${c.colors[2]}`:'';}

  function loadSession(){ try { const raw=storageGet(SESSION_KEY,''); if(!raw)return null; const s=JSON.parse(raw); if(!s?.access_token)return null; return s; } catch { return null; } }
  function saveSession(s){ state.session=s; if(s)storageSet(SESSION_KEY,JSON.stringify(s)); else storageRemove(SESSION_KEY); }
  function clearSession(){ saveSession(null); state.profile=null; state.following=new Set(); state.notificationsUnread=0; }
  function isExpiredRefreshResponse(status,payload){
    if(![400,401,403].includes(Number(status)))return false;
    const body=(typeof payload==='string'?payload:JSON.stringify(payload||{})).toLowerCase();
    return body.includes('refresh_token_not_found') ||
      body.includes('invalid refresh token') ||
      (body.includes('refresh token')&&body.includes('revoked')) ||
      body.includes('invalid_grant') ||
      body.includes('session_not_found');
  }

  async function parseJsonSafe(res){ const text=await res.text(); if(!text)return null; try{return JSON.parse(text);}catch{return text;} }
  function headers(auth=true, extra={}){ const h={apikey:KEY,Accept:'application/json',...extra}; if(auth && token())h.Authorization=`Bearer ${token()}`; else h.Authorization=`Bearer ${KEY}`; return h; }
  async function refreshSession(){
    if (refreshPromise) return refreshPromise;
    const rt=state.session?.refresh_token; if(!rt)return false;
    const previousUser=state.session?.user||null;
    refreshPromise=(async()=>{
      const res=await fetchWithTimeout(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`,{
        method:'POST',
        headers:headers(false,{'Content-Type':'application/json'}),
        body:JSON.stringify({refresh_token:rt})
      });
      if(!res.ok){
        const payload=await parseJsonSafe(res);
        if(isExpiredRefreshResponse(res.status,payload))clearSession();
        return false;
      }
      const data=await res.json();
      saveSession({...data,user:data.user||previousUser});
      return true;
    })();
    try{return await refreshPromise;}finally{refreshPromise=null;}
  }
  async function api(path,{method='GET',body=null,auth=true,prefer=null,raw=false,timeoutMs=API_TIMEOUT_MS}={}){
    const h=headers(auth);
    const isJson=body!=null && !(body instanceof Blob) && !(body instanceof ArrayBuffer) && !(body instanceof FormData);
    if(isJson)h['Content-Type']='application/json';
    if(prefer)h.Prefer=prefer;
    const requestBody=body==null?undefined:(isJson?JSON.stringify(body):body);
    const req=()=>fetchWithTimeout(`${SUPABASE_URL}${path}`,{method,headers:h,body:requestBody},timeoutMs);
    let res=await req();
    if(auth && res.status===401 && state.session?.refresh_token){
      const refreshed=await refreshSession().catch(()=>false);
      if(refreshed){h.Authorization=`Bearer ${token()}`;res=await req();}
    }
    if(!res.ok){
      const err=await parseJsonSafe(res);
      const msg=err?.message||err?.error_description||err?.error||`${method} ${path} failed (${res.status})`;
      throw new Error(msg);
    }
    if(raw)return res;
    return parseJsonSafe(res);
  }
  async function rpc(name, body={}, auth=true){ return api(`/rest/v1/rpc/${name}`,{method:'POST',body,auth}); }
  async function anonRpc(name,body={}){ return rpc(name,body,false); }
  async function table(name,query='',auth=true){ return api(`/rest/v1/${name}${query?`?${query}`:''}`,{auth}); }
  async function insert(name,body,prefer='return=representation'){ return api(`/rest/v1/${name}`,{method:'POST',body,prefer}); }
  async function patch(name,filter,body){ return api(`/rest/v1/${name}?${filter}`,{method:'PATCH',body,prefer:'return=representation'}); }

  function safeInternalPath(value,fallback='/feed'){
    const path=String(value||'').trim();
    return path.startsWith('/')&&!path.startsWith('//')?path:fallback;
  }

  function relayDesktopOAuth(){
    const query=new URLSearchParams(location.search);
    if(query.get('desktop_oauth')!=='1')return false;
    const fragment=new URLSearchParams(location.hash.replace(/^#/,''));
    const code=query.get('code')||fragment.get('code')||'';
    const error=query.get('error_description')||query.get('error')||fragment.get('error_description')||fragment.get('error')||'';
    const port=Number(query.get('port')||0);
    const relay=String(query.get('relay')||'');
    if(!Number.isInteger(port)||port<1024||port>65535||!/^[A-Za-z0-9_-]{32,256}$/.test(relay)){
      state.authError='The Windows Google sign-in callback was invalid. Please try again from the BLINK app.';
      history.replaceState({},'',routeHref('/login'));
      return false;
    }
    const callback=new URL(`http://127.0.0.1:${port}/callback`);
    callback.searchParams.set('relay',relay);
    if(code)callback.searchParams.set('code',code);
    else callback.searchParams.set('error',error||'Google sign-in did not return an authorization code.');
    location.replace(callback.toString());
    return true;
  }

  async function bootstrapSession(){
    state.session=loadSession();
    const hash=new URLSearchParams(location.hash.replace(/^#/,''));
    const oauthError=hash.get('error_description')||hash.get('error')||'';
    if(oauthError&&!hash.get('access_token')){
      state.authError='Google sign-in could not be completed. Please try again.';
      storageRemove(OAUTH_NEXT_KEY);
      history.replaceState({},'',routeHref('/login'));
      return;
    }
    if(hash.get('access_token')){
      const access_token=hash.get('access_token');
      const refresh_token=hash.get('refresh_token');
      const expires_in=Number(hash.get('expires_in')||3600);
      const type=hash.get('type');
      if(!refresh_token || !Number.isFinite(expires_in) || expires_in<=0){
        state.authError='This sign-in link is incomplete or expired. Please try again.';
        storageRemove(OAUTH_NEXT_KEY);
        history.replaceState({},'',routeHref('/login'));
        return;
      }
      saveSession({
        access_token,
        refresh_token,
        expires_in,
        expires_at:Math.floor(Date.now()/1000)+expires_in,
        token_type:hash.get('token_type')||'bearer',
        user:null
      });
      const next=type==='recovery'?'/settings/password':safeInternalPath(storageGet(OAUTH_NEXT_KEY,''),'/feed');
      storageRemove(OAUTH_NEXT_KEY);
      history.replaceState({},'',routeHref(next));
    }
    if(!state.session)return;
    try{
      const user=await api('/auth/v1/user',{auth:true}); state.session.user=user; saveSession(state.session);
      await Promise.all([loadMyProfile(),loadFollowing(),refreshUnreadCount()]);
      void wakeReferralPush();
    }catch(e){
      console.warn('session bootstrap',e);
      if(!state.session?.refresh_token)clearSession();
    }
  }

  async function loadMyProfile(){ if(!uid())return null;const owner=uid(); const rows=await table('profiles',`id=eq.${encodeQ(owner)}&select=id,username,full_name,avatar_url,cover_photo_url,bio,professional_headline,university,faculty,department,academic_level,profile_theme,posts_count,follower_count,following_count,is_verified,verification_badge,blink_vip_until,daily_streak,points,total_xp,xp_level&limit=1`);if(owner!==uid())return null; state.profile=Array.isArray(rows)?rows[0]||null:null; return state.profile; }
  async function loadFollowing(){ if(!isAuthed())return; try{ const rows=await rpc('get_my_following_ids',{}); state.following=new Set((rows||[]).map(String)); }catch{ state.following=new Set(); } }
  async function refreshUnreadCount(){ if(!uid())return; try{ const rows=await table('notifications',`user_id=eq.${encodeQ(uid())}&is_read=eq.false&select=id&limit=99`); state.notificationsUnread=rows?.length||0; }catch{state.notificationsUnread=0;} }

  function currentPath(){
    const redirected=new URLSearchParams(location.search).get('p');
    if(redirected){
      const clean=(redirected.startsWith('/')?redirected:`/${redirected}`).replace(/^\/\/+/, '/');
      history.replaceState({},'',routeHref(clean));
      return safeDecodeUri(clean.split('?')[0].split('#')[0]);
    }
    let path=location.pathname||'/';
    if(PREVIEW_BASE&&path.startsWith(PREVIEW_BASE))path=path.slice(PREVIEW_BASE.length)||'/';
    return safeDecodeUri(path||'/');
  }
  const still=()=>{const n=++state.pageSerial,url=location.href,owner=uid();return()=>n===state.pageSerial&&url===location.href&&owner===uid();};
  function navigate(path,replace=false){
    const next=typeof path==='string'&&path.startsWith('/')&&!path.startsWith('//')?path:'/';
    const href=routeHref(next);
    history[replace?'replaceState':'pushState']({},'',href);
    render(currentPath());
    window.scrollTo({top:0,behavior:'instant'});
  }
  window.addEventListener('popstate',()=>render(currentPath()));
  document.addEventListener('visibilitychange',()=>{
    if(document.hidden){
      for(const postId of [...state.qualifiedTimers.keys()])cancelQualifiedView(postId);
      document.querySelectorAll('video').forEach(v=>v.pause());
    }else if(state.viewObserver){
      state.viewObserver.disconnect();state.viewObserver=null;
      document.querySelectorAll('[data-view-tracked]').forEach(el=>{delete el.dataset.viewTracked;});
      bindQualifiedViews();
    }
  });

  function navItem(path,key,label,badge=''){ const active=currentPath()===path || (path!=='/'&&currentPath().startsWith(path)); return `<button class="nav-btn ${active?'active':''}" data-nav="${esc(path)}"><span class="icon">${icon(key)}</span><span>${esc(label)}</span>${badge?`<span class="badge">${esc(badge)}</span>`:''}</button>`; }
  function mobileItem(path,key,label){ const active=currentPath()===path || (path!=='/'&&currentPath().startsWith(path)); return `<button class="${active?'active':''}" data-nav="${esc(path)}"><span>${icon(key)}</span>${esc(label)}</button>`; }
  function shell(content,title='Blink',right=''){
    setTitle(title);
    const p=state.profile;
    const analyticsLink=state.session?.user?.app_metadata?.admin===true
      ? navItem('/site-stats','analytics','Website analytics') : '';
    return `<div class="app-shell">
      <aside class="sidebar">
        <button class="nav-btn brand" data-nav="/feed"><img class="mark" src="${LOGO_URL}" alt="" aria-hidden="true"><span>Blink</span></button>
        ${navItem('/feed','home','Home')}${navItem('/reels','reels','Reels')}${navItem('/search','search','Search')}${navItem('/messages','messages','Messages')}${navItem('/activity','activity','Activity',state.notificationsUnread?String(state.notificationsUnread):'')}${navItem('/market','market','Marketplace')}${navItem('/connect','connect','Connect')}${navItem('/games','games','Games')}${navItem('/scheduled','scheduled','Scheduled')}${navItem('/ai','ai','Blink AI')}${analyticsLink}
        <div class="sidebar-spacer"></div>${navItem('/settings/profile','settings','Settings')}
        ${p?`<button class="profile-mini" data-nav="/@${esc(p.username)}">${avatar(p.avatar_url,p.full_name)}<span><strong>${esc(p.full_name||p.username)}</strong><span class="handle">@${esc(p.username)}</span></span></button>`:''}
      </aside>
      <main class="main"><header class="topbar"><h1>${esc(title)}</h1><div class="top-actions"><a class="btn small-btn download-app" href="${APK_URL}" aria-label="Download BLINK Android app">↓ App</a><button class="btn small-btn" data-action="create-post">＋ Post</button>${p?`<button class="btn small-btn ghost" data-action="logout">Log out</button>`:'<button class="btn small-btn primary" data-nav="/login">Log in</button>'}</div></header><div class="content-wrap">${content}</div></main>
      <aside class="rightbar">${right||rightRail()}</aside>
      <nav class="mobile-nav">${mobileItem('/feed','home','Home')}${mobileItem('/reels','reels','Reels')}${mobileItem('/market','market','Market')}${mobileItem('/connect','connect','Connect')}${mobileItem('/messages','messages','Message')}</nav>
    </div>`;
  }
  function rightRail(){
    const profileButton=state.profile?.username
      ? `<button class="btn" data-action="open-app">Open my profile in app</button>`
      : '';
    return `<div class="side-card"><h3>Blink Web</h3><p class="muted small">Your web session uses the same Blink account and Supabase backend as Android.</p><a class="btn primary download-app" href="${APK_URL}">↓ Download Android app</a>${profileButton}</div><div class="side-card"><h3>Quick links</h3><div class="quick-list"><button class="nav-btn" data-nav="/market">▣ Marketplace</button><button class="nav-btn" data-nav="/connect">◎ Connect Hub</button><button class="nav-btn" data-nav="/games">◇ Games</button><button class="nav-btn" data-nav="/scheduled">◷ Scheduled posts</button><button class="nav-btn" data-nav="/ai">✦ Blink AI</button></div></div>`;
  }
  function requireAuth(){ if(isAuthed())return true; navigate('/login'); return false; }

  const readChatPreviewPrivacy = () => window.BlinkLinkPreview.readPrivacy(table);
  const saveChatPreviewPrivacy = (privacy, enabled) => window.BlinkLinkPreview.savePrivacy(privacy, enabled, rpc);
  const richLinkCard = text => window.BlinkLinkPreview?.card(text) || '';
  const hydrateLinkCards = () => window.BlinkLinkPreview?.hydrate();

  function bindCommon(){
    document.querySelectorAll('[data-nav]').forEach(el=>el.onclick=(e)=>{e.preventDefault();navigate(el.dataset.nav);});
    document.querySelectorAll('[data-action="logout"]').forEach(el=>el.onclick=logout);
    document.querySelectorAll('[data-action="open-app"]').forEach(el=>el.onclick=()=>openAndroid('profile',state.profile?.username||''));
    document.querySelectorAll('[data-action="create-post"]').forEach(el=>el.onclick=()=>requireAuth()&&openPostComposer());
    hydrateLinkCards();
  }

  function openAndroid(type,id){
    const clean=String(id||'').trim().replace(/^@/,'');
    if(!clean){ location.href=APK_URL; return; }
    const pkg=C.androidPackage||'com.aistudio.blink.appvtwo';
    const fallback=encodeURIComponent(APK_URL);
    location.href=`intent://${encodeURIComponent(type)}/${encodeURIComponent(clean)}#Intent;scheme=blink;package=${pkg};S.browser_fallback_url=${fallback};end`;
  }
  function closeModal(){ MODALS.innerHTML=''; }
  function modal(title,body,footer=''){
    MODALS.innerHTML=`<div class="modal-backdrop"><div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="blink-modal-title"><div class="modal-head"><h2 id="blink-modal-title">${esc(title)}</h2><button type="button" class="icon-btn" data-close-modal aria-label="Close">×</button></div>${body}${footer}</div></div>`;
    const backdrop=MODALS.querySelector('.modal-backdrop');
    backdrop?.addEventListener('click',e=>{if(e.target===backdrop)closeModal();});
    MODALS.querySelectorAll('[data-close-modal]').forEach(x=>x.onclick=closeModal);
    MODALS.querySelector('input,textarea,select,button')?.focus({preventScroll:true});
  }

  async function signOutTo(p){try{if(token())await api('/auth/v1/logout',{method:'POST'})}catch{}
    clearSession();state.feed=[];state.feedAsOf=null;state.searchRequestId++;closeModal();toast('Signed out');navigate(p)
  }
  async function logout(){return signOutTo('/')}
  window.BlinkWebAccount={switchToLogin:()=>signOutTo('/login')};

  async function login(identifier,password){
    const input=identifier.trim().toLowerCase(); let data;
    if(input.includes('@')&&!input.startsWith('@')) {
      try { data=await api('/auth/v1/token?grant_type=password',{method:'POST',body:{email:input,password},auth:false}); }
      catch { data=await api('/functions/v1/username-login',{method:'POST',body:{username:input,password},auth:false}); }
    } else data=await api('/functions/v1/username-login',{method:'POST',body:{username:input.replace(/^@/,''),password},auth:false});
    if(!data?.access_token)throw new Error('Blink did not return a valid session.'); saveSession(data); if(!data.user){const user=await api('/auth/v1/user');state.session.user=user;saveSession(state.session);} await loadMyProfile();await loadFollowing();await refreshUnreadCount(); void wakeReferralPush();
  }
  async function wakeReferralPush(){
    if(!isAuthed())return;
    try{
      const rows=await rpc('blink_my_referral_push_ids');
      for(const row of (Array.isArray(rows)?rows:[]).slice(0,3)){
        if(!row.notification_id)continue;
        await api('/functions/v1/send-activity-notification',{
          method:'POST',body:{notification_id:String(row.notification_id)}
        });
      }
    }catch(e){
      console.warn('Referral push confirmation was deferred:',e?.message||e);
    }
  }

  async function signup(fullName,username,email,password,referrerId='',birthDate=''){
    const parsed=new Date(birthDate+'T00:00:00Z');
    if(!/^\d{4}-\d{2}-\d{2}$/.test(birthDate) ||
       !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0,10)!==birthDate ||
       parsed > new Date() || parsed < new Date(new Date().getUTCFullYear()-120,0,1)){
      throw new Error('Enter a valid birthday before selecting your referrer.');
    }
    if(password.length<8||!/[A-Z]/.test(password)||!/[a-z]/.test(password)||!/\d/.test(password)||!/[\W_]/.test(password))throw new Error('Use 8+ characters with uppercase, lowercase, a number and a symbol.');
    const cleanUser=username.trim().toLowerCase().replace(/^@/,''); if(!/^[a-z0-9][a-z0-9._-]{1,29}$/.test(cleanUser))throw new Error('Use 2–30 lowercase letters, numbers, dots, dashes or underscores.');
    const metadata={username:cleanUser,full_name:fullName.trim(),name:fullName.trim()};
    if(/^[0-9a-fA-F-]{36}$/.test(String(referrerId)))metadata.blink_referrer_id=referrerId;
    const data=await api('/auth/v1/signup',{method:'POST',auth:false,body:{email:email.trim().toLowerCase(),password,data:metadata}});
    if(data?.access_token){
      saveSession(data);
      let privateBirthdaySaved=false;
      try {
        await api('/rest/v1/profile_private_details?on_conflict=user_id',{
          method:'POST', prefer:'resolution=merge-duplicates,return=minimal',
          body:{user_id:uid(),birth_date:birthDate}
        });
        privateBirthdaySaved=true;
      }catch{/* Account creation succeeds; private birthday can be updated in profile setup. */}
      await loadMyProfile();void wakeReferralPush();
      data.private_birthday_saved=privateBirthdaySaved;
    }
    return data;
  }
  function googleLogin(){
    state.authError=null;
    storageSet(OAUTH_NEXT_KEY,'/feed');
    const redirectBase=(SITE_URL||location.origin).replace(/\/+$/,'');
    const params=new URLSearchParams({provider:'google',redirect_to:`${redirectBase}/`});
    location.assign(`${SUPABASE_URL}/auth/v1/authorize?${params.toString()}`);
  }
  async function requestReset(email){ const redirect=SITE_URL+'/settings/password'; await api('/auth/v1/recover',{method:'POST',auth:false,body:{email:email.trim().toLowerCase(),redirect_to:redirect}}); }
  async function updatePassword(password){ if(password.length<8)throw new Error('Use at least 8 characters.'); await api('/auth/v1/user',{method:'PUT',body:{password}}); }

  function renderLanding(){
    setTitle('Blink');
    ROOT.innerHTML=`<main class="landing-page" aria-label="BLINK Nigeria">
      <a class="landing-skip" href="#landing-main">Skip to content</a>
      <header class="landing-header">
        <div class="landing-container landing-header-inner">
          <a href="${routeHref('/')}" class="landing-brand" aria-label="BLINK Nigeria homepage">
            <img src="${LOGO_URL}" width="39" height="39" alt="BLINK" decoding="async" fetchpriority="high">
            <span>BLINK<span class="landing-brand-tag">Nigeria</span></span>
          </a>
          <nav class="landing-desktop-nav" aria-label="Website navigation">
            <a href="${routeHref('/features/')}">Features</a>
            <a href="${routeHref('/about/')}">About</a>
            <a href="${routeHref('/download/')}">Download</a>
            <a href="${routeHref('/faq/')}">FAQ</a>
          </nav>
          <div class="landing-header-actions">
            <button class="landing-top-cta" type="button" data-landing-nav="${isAuthed()?'/feed':'/login'}">${isAuthed()?'Open BLINK':'Sign in'} <span aria-hidden="true">↗</span></button>
            <details class="landing-menu">
              <summary aria-label="Open website menu">Menu <span aria-hidden="true">☰</span></summary>
              <nav aria-label="Mobile website navigation">
                <a href="${routeHref('/features/')}">Features</a>
                <a href="${routeHref('/about/')}">About BLINK Nigeria</a>
                <a href="${routeHref('/download/')}">Download BLINK App</a>
                <a href="${routeHref('/faq/')}">FAQ</a>
                <a href="${routeHref('/privacy/')}">Privacy</a>
                <a href="https://whatsapp.com/channel/0029VbBU5Hr7tkj5mIvJNH1u" target="_blank" rel="noopener noreferrer">WhatsApp Channel</a>
              </nav>
            </details>
          </div>
        </div>
      </header>

      <section id="landing-main" class="landing-container landing-hero" aria-labelledby="landing-title">
        <div class="landing-hero-copy">
          <div class="landing-eyebrow">Your campus. Your community. Your world.</div>
          <h1 id="landing-title"><span class="gradient-text">BLINK Nigeria</span> — Connect, Share &amp; Discover.</h1>
          <p class="landing-lead">A social and campus community app in Nigeria. Meet people, share posts and reels, explore creators, and discover what matters to your community.</p>
          <div class="landing-actions">
            <a href="${routeHref('/features/')}" class="landing-action landing-action--primary">Explore BLINK <span aria-hidden="true">↗</span></a>
            <a href="${APK_URL}" class="landing-action landing-action--secondary">↓ Download Android app</a>
          </div>
          <p class="landing-help">Browse what BLINK offers or sign in to use your account on the web.</p>
          <form id="public-search" class="landing-search" role="search" aria-label="Find a public BLINK profile">
            <label for="landing-username">Looking for someone on BLINK?</label>
            <div class="landing-search-row">
              <input class="field" id="landing-username" name="username" type="text" maxlength="32" placeholder="Search @username" autocomplete="off" autocapitalize="none" spellcheck="false" aria-label="Public BLINK username">
              <button type="submit" class="landing-search-submit">View profile <span aria-hidden="true">→</span></button>
            </div>
            <p id="public-search-status" role="status" aria-live="polite" class="discovery-status">Only publicly available profiles can be opened.</p>
          </form>
        </div>
        <aside class="landing-preview" aria-label="Explore BLINK highlights">
          <div class="landing-preview-title"><span>Inside BLINK</span><span class="landing-preview-tag">Made to connect</span></div>
          <div class="landing-preview-headline">Your community, all in one place.</div>
          <div class="landing-preview-list">
            <div class="landing-preview-row"><span class="landing-preview-icon" aria-hidden="true">▤</span><div><strong>Share your story</strong><span>Posts, updates and conversations</span></div></div>
            <div class="landing-preview-row"><span class="landing-preview-icon" aria-hidden="true">▶</span><div><strong>See what's trending</strong><span>Reels and creative moments</span></div></div>
            <div class="landing-preview-row"><span class="landing-preview-icon" aria-hidden="true">◎</span><div><strong>Find your people</strong><span>Connect with your campus community</span></div></div>
          </div>
          <div class="landing-preview-bottom"><span>Explore BLINK features</span><strong>One community <span aria-hidden="true">↗</span></strong></div>
        </aside>
      </section>


      <section class="landing-container discovery-section" aria-labelledby="discovery-tour-title">
        <div class="landing-section-head"><div><span class="landing-kicker">Explore before you sign in</span><h2 id="discovery-tour-title">Discover what BLINK can do.</h2></div></div>
        <div class="discovery-tour">
          <div class="discovery-tabs" role="tablist" aria-label="Explore BLINK feature areas">
            <button type="button" role="tab" id="tour-tab-feed" data-tour-tab="feed" aria-selected="true" aria-controls="tour-panel">Feed</button>
            <button type="button" role="tab" id="tour-tab-reels" data-tour-tab="reels" aria-selected="false" aria-controls="tour-panel">Reels</button>
            <button type="button" role="tab" id="tour-tab-market" data-tour-tab="market" aria-selected="false" aria-controls="tour-panel">Market</button>
            <button type="button" role="tab" id="tour-tab-connect" data-tour-tab="connect" aria-selected="false" aria-controls="tour-panel">Connect</button>
          </div>
          <div class="discovery-tour-body" id="tour-panel" role="tabpanel" aria-labelledby="tour-tab-feed" tabindex="0">
            <span class="landing-kicker">Feature guide · Not a live feed</span>
            <h3 id="tour-heading">A space for real conversations</h3>
            <p id="tour-body">Discover what BLINK has to offer.</p>
            <a id="tour-action" href="${routeHref('/features/')}" class="landing-action landing-action--secondary">Browse BLINK features</a>
          </div>
        </div>
      </section>

      <section class="landing-container discovery-section" id="campuses" aria-labelledby="campuses-title">
        <div class="landing-section-head"><div><span class="landing-kicker">Discover Nigerian universities</span><h2 id="campuses-title">Find your campus on BLINK.</h2></div></div>
        <p class="discovery-support">Find schools in BLINK’s searchable university catalogue. The list is not a live count of members or communities.</p>
        <div class="discovery-campuses">
          <label for="campus-search">Search by university name or abbreviation (e.g. FUTA)</label>
          <input id="campus-search" class="field" name="campus" placeholder="Search FUTA, UNILAG, University of Lagos…" type="search" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="90">
          <p role="status" id="campus-status" aria-live="polite" class="discovery-status">Search the same Nigerian university catalogue used by BLINK Android.</p>
          <ul id="campus-results" class="campus-results" aria-label="Matching universities"></ul>
          <div id="campus-selection" class="campus-selection" hidden>
            <span>Your selected campus: <strong data-selected-campus></strong></span>
            <button class="landing-action landing-action--primary" type="button" data-landing-nav="${isAuthed()?'/connect':'/login'}">${isAuthed()?'Open BLINK Connect':'Sign in to connect'} <span aria-hidden="true">↗</span></button>
          </div>
        </div>
      </section>

      <section class="landing-container landing-features" id="landing-features" aria-labelledby="landing-features-title">
        <div class="landing-section-head"><div><span class="landing-kicker">One app. More possibilities.</span><h2 id="landing-features-title">Everything you need to connect.</h2></div><a href="${routeHref('/features/')}">Explore all features <span aria-hidden="true">↗</span></a></div>
        <div class="landing-feature-grid">
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">▤</span><h3>BLINK Feed</h3><p>Discover community posts, share updates and join conversations that matter to you.</p></article>
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">▶</span><h3>BLINK Reels</h3><p>Explore short videos, discover creators and share memorable moments.</p></article>
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">▣</span><h3>BLINK Market</h3><p>Find listings, explore products and connect with people selling nearby.</p></article>
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">◎</span><h3>BLINK Connect</h3><p>Meet new people and discover connections in your university community.</p></article>
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">✉</span><h3>BLINK Messages</h3><p>Stay in touch with friends and continue conversations in one place.</p></article>
          <article class="landing-feature-card"><span class="landing-feature-icon" aria-hidden="true">◇</span><h3>BLINK LIFE</h3><p>Learn about BLINK's optional game experience for supported devices.</p></article>
        </div>
      </section>

      <section class="landing-container" aria-label="Join BLINK">
        <div class="landing-bottom-cta">
          <div><h2>Ready to be part of BLINK?</h2><p>Explore the community on the web or take BLINK with you on Android.</p></div>
          <div class="landing-actions"><button class="landing-action landing-action--primary" type="button" data-landing-nav="${isAuthed()?'/feed':'/login'}">${isAuthed()?'Open your feed':'Sign in to BLINK'}</button><a href="${APK_URL}" class="landing-action landing-action--secondary">Download app</a></div>
        </div>
      </section>

      <footer class="landing-footer">
        <div class="landing-container landing-footer-inner">
          <span>© BLINK Nigeria · Connect, share and discover.</span>
          <nav class="landing-footer-links" aria-label="Footer navigation">
            <a href="${routeHref('/about/')}">About BLINK Nigeria</a>
            <a href="${routeHref('/features/')}">Features</a>
            <a href="${routeHref('/download/')}">Download BLINK App</a>
            <a href="${routeHref('/faq/')}">FAQ</a>
            <a href="${routeHref('/students/')}">BLINK for Students</a>
            <a href="${routeHref('/marketplace/')}">BLINK Market</a>
            <a href="${routeHref('/safety/')}">Safety</a>
            <a href="${routeHref('/privacy/')}">Privacy Policy</a>
            <a href="https://whatsapp.com/channel/0029VbBU5Hr7tkj5mIvJNH1u" target="_blank" rel="noopener noreferrer">WhatsApp Channel</a>
          </nav>
        </div>
      </footer>
    </main>`;
    ROOT.querySelectorAll('[data-landing-nav]').forEach(button=>{
      button.addEventListener('click',event=>{
        event.preventDefault();
        navigate(event.currentTarget.dataset.landingNav);
      });
    });
    const searchForm=ROOT.querySelector('#public-search');
    searchForm?.addEventListener('submit',async event=>{
      event.preventDefault();
      const field=searchForm.elements.namedItem('username');
      const button=searchForm.querySelector('button[type="submit"]');
      const status=ROOT.querySelector('#public-search-status');
      const username=String(field?.value||'').trim().replace(/^@/,'');
      if(!/^[A-Za-z0-9._-]{2,32}$/.test(username)){
        if(status)status.textContent='Enter a valid BLINK username (2–32 letters, numbers, dots, underscores or hyphens).';
        field?.focus();return;
      }
      if(button?.disabled)return;
      if(button)button.disabled=true;
      if(status)status.textContent='Checking this public profile…';
      try{
        const info=await anonRpc('get_public_web_profile',{p_username:username});
        if(!info?.available||!info.profile){
          if(status)status.textContent='No public BLINK profile found for that username.';
          return;
        }
        navigate('/@'+encodeURIComponent(username));
      }catch{
        if(status)status.textContent='Profile lookup is temporarily unavailable. Please try again.';
      }finally{if(button)button.disabled=false;}
    });
    window.BLINK_DISCOVERY?.initLanding(ROOT);
  }

  async function renderReferralInvitation(rawUsername){
    const username=String(rawUsername||'').trim().toLowerCase();
    const signedIn=isAuthed();
    setTitle('Join BLINK');
    ROOT.innerHTML=`<main class="auth-wrap"><section class="card auth-card" id="invite-preview" aria-live="polite">
      <div class="brand" style="padding:0 0 8px"><img class="mark" src="${LOGO_URL}" alt="" aria-hidden="true">BLINK</div>
      <h1>You've been invited to BLINK</h1>
      <p class="muted" id="invite-status">Checking this invitation…</p>
      <div id="invite-person" class="form-stack" style="margin-bottom:16px"></div>
      <button class="btn primary" type="button" id="invite-join">${signedIn?'Open BLINK':'Create your BLINK account'}</button>
      <button class="btn ghost" type="button" id="invite-explore" style="margin-top:8px">Explore BLINK</button>
      <p class="muted small" style="margin-top:12px">A referral is counted only after a new account is verified. Joining is optional. Invite links do not award Coins for clicks.</p>
    </section></main>`;
    ROOT.querySelector('#invite-join')?.addEventListener('click',()=>navigate(signedIn?'/feed':'/login'));
    ROOT.querySelector('#invite-explore')?.addEventListener('click',()=>navigate(signedIn?'/feed':'/'));
    const status=ROOT.querySelector('#invite-status');
    const person=ROOT.querySelector('#invite-person');
    try{
      const suggestions=await anonRpc('blink_referrer_suggestions',
        {p_query:username,p_limit:10,p_offset:0});
      if(!status?.isConnected)return; // Ignore a response after route change.
      const match=(Array.isArray(suggestions)?suggestions:[])
        .find(row=>String(row.username||'').toLowerCase()===username);
      if(!match){
        storageRemove(INVITE_REFERRER_KEY);
        status.textContent='This invitation is no longer available. You can still join BLINK.';
        return;
      }
      if(!signedIn)storageSet(INVITE_REFERRER_KEY,username);
      status.textContent='Join the BLINK community with an invitation from:';
      const label=document.createElement('strong');
      label.textContent=(match.full_name||match.username)+' (@'+match.username+')';
      person?.replaceChildren(label);
      if(match.university){
        const campus=document.createElement('span');
        campus.className='muted small';
        campus.textContent=String(match.university);
        person?.append(campus);
      }
      if(!signedIn){
        let visitor=storageGet(INVITE_VISITOR_KEY,'');
        if(!/^[0-9a-f-]{36}$/i.test(visitor)){
          visitor=crypto.randomUUID();
          storageSet(INVITE_VISITOR_KEY,visitor);
        }
        void anonRpc('blink_record_referral_link_open',{
          p_username:username,p_visitor:visitor
        }).catch(()=>{});
      }
    }catch{
      if(status?.isConnected){
        storageRemove(INVITE_REFERRER_KEY);
        status.textContent='We could not verify the invitation right now. You can still join BLINK without a referrer.';
      }
    }
  }

  function renderLogin(){
    if(isAuthed()){navigate('/feed',true);return;}
    setTitle('Sign in'); ROOT.innerHTML=`<div class="auth-wrap"><div class="card auth-card"><div class="brand" style="padding:0 0 8px"><img class="mark" src="${LOGO_URL}" alt="" aria-hidden="true">Blink</div><h1>Welcome back</h1><p class="muted">Use the same account you use in the Android app.</p><div class="auth-tabs"><button class="btn primary" data-auth-tab="login">Sign in</button><button class="btn" data-auth-tab="signup">Create account</button></div><div id="auth-panel"></div><p class="auth-note">Your browser receives a normal user session. Blink's service-role credentials are never exposed to the web.</p><button class="btn ghost" style="width:100%;margin-top:8px" data-nav="/">Browse without signing in</button></div></div>`;
    const panel=document.getElementById('auth-panel');
    const showLogin=()=>{panel.innerHTML=`${state.authError?`<div class="error" style="margin-bottom:10px">${esc(state.authError)}</div>`:''}<form id="login-form" class="form-stack"><input class="field" name="identifier" placeholder="Email or @username" required autocomplete="username"><input class="field" name="password" type="password" placeholder="Password" required autocomplete="current-password"><button class="btn primary">Sign in</button><button class="btn" type="button" id="google-auth">Continue with Google</button><button class="btn ghost" type="button" id="forgot">Forgot password?</button><div id="auth-msg"></div></form>`; document.getElementById('login-form').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),msg=document.getElementById('auth-msg');msg.innerHTML='<span class="muted">Signing in…</span>';try{await login(f.get('identifier'),f.get('password'));toast('Signed in');navigate('/feed');}catch(err){msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;}}; document.getElementById('google-auth').onclick=e=>{const b=e.currentTarget;b.disabled=true;b.textContent='Opening Google…';googleLogin();}; document.getElementById('forgot').onclick=()=>{modal('Reset password',`<form id="reset-form" class="form-stack"><input class="field" type="email" name="email" placeholder="Email address" required><button class="btn primary">Send reset email</button><div id="reset-msg"></div></form>`);document.getElementById('reset-form').onsubmit=async e=>{e.preventDefault();const email=new FormData(e.currentTarget).get('email');try{await requestReset(email);document.getElementById('reset-msg').innerHTML='<div class="success">Check your inbox for the Blink password reset link.</div>';}catch(err){document.getElementById('reset-msg').innerHTML=`<div class="error">${esc(err.message)}</div>`;}};};};
    const showSignup=()=>{
      panel.innerHTML = '<form id="signup-form" class="form-stack">' +
        '<input class="field" name="name" placeholder="Full name" required>' +
        '<input class="field" name="username" placeholder="Username" required>' +
        '<input class="field" type="email" name="email" placeholder="Email" required>' +
        '<input class="field" type="password" name="password" placeholder="Strong password" required>' +
        '<label class="muted small" for="signup-birthday">Birthday</label>' +
        '<input class="field" id="signup-birthday" name="birthday" type="date" required autocomplete="bday">' +
        '<p class="muted small">Your birthday is private and is not shared with a referrer.</p>' +
        '<label class="muted small" for="referrer-search">Referred by (optional)</label>' +
        '<input class="field" id="referrer-search" name="referrer" placeholder="Search name or @username" autocomplete="off">' +
        '<div id="referrer-results" class="form-stack" aria-live="polite"></div>' +
        '<button class="btn primary">Create Blink account</button>' +
        '<div id="auth-msg" aria-live="polite"></div></form>';
      let selectedReferrerId = '';
      let offset = 0;
      let seq = 0;
      let debounce = null;
      const field = document.getElementById('referrer-search');
      const results = document.getElementById('referrer-results');
      const invited = storageGet(INVITE_REFERRER_KEY, '');
      const lookup = async (append=false) => {
        const query = String(field.value||'').trim().replace(/^@/,'').slice(0,40);
        if(!query){results.replaceChildren();return;}
        const thisSearch = ++seq;
        if(!append){offset=0;results.replaceChildren();}
        try {
          const rows=await anonRpc('blink_referrer_suggestions',
            {p_query:query,p_limit:10,p_offset:offset});
          if(thisSearch !== seq)return;
          if(!append)results.replaceChildren();
          if(!rows?.length && !append){results.textContent='No matching users found.';return;}
          for(const person of rows){
            const button=document.createElement('button');
            button.type='button';
            button.className='btn';
            const photoUrl=safeUrl(person.avatar_url);
            const photo=document.createElement(photoUrl?'img':'span');
            photo.className='avatar-sm';
            if(photoUrl){photo.src=photoUrl;photo.alt='';photo.loading='lazy';}
            else{photo.classList.add('avatar-fallback');photo.textContent='@';}
            const display=document.createElement('span');
            display.textContent=(person.full_name || person.username)+' (@'+person.username+')'+
              (person.university?' · '+person.university:'');
            button.append(photo,display);
            if(person.is_verified){
              const badge=document.createElement('span');
              badge.className='verified';
              badge.title='Verified account';
              badge.setAttribute('aria-label','Verified account');
              badge.textContent='✓';
              button.append(badge);
            }
            button.onclick=()=>{
              selectedReferrerId=String(person.id||'');
              field.value='@'+person.username;
              results.textContent='Selected: @'+person.username;
            };
            results.appendChild(button);
          }
          if(invited && !selectedReferrerId && query.toLowerCase()===invited.toLowerCase()){
            const exact=rows.find(person=>person.username?.toLowerCase()===invited.toLowerCase());
            if(exact){selectedReferrerId=String(exact.id);field.value='@'+exact.username;results.textContent='Referred by @'+exact.username;}
          }
          if(rows.length===10 && offset<500){
            const more=document.createElement('button');
            more.type='button';more.className='btn ghost';more.textContent='See more people';
            more.onclick=()=>{more.remove();offset+=10;lookup(true);};
            results.appendChild(more);
          }
        } catch {
          if(thisSearch===seq)results.textContent='Referrer search is unavailable. This field is optional.';
        }
      };
      field.addEventListener('input',()=>{
        selectedReferrerId='';
        storageRemove(INVITE_REFERRER_KEY);
        clearTimeout(debounce);
        debounce=setTimeout(()=>lookup(),260);
      });
      if(invited){field.value='@'+invited;lookup();}
      document.getElementById('signup-form').onsubmit=async e=>{
        e.preventDefault();
        const f=new FormData(e.currentTarget),msg=document.getElementById('auth-msg');
        msg.textContent='Creating account…';
        try{
          const data=await signup(f.get('name'),f.get('username'),f.get('email'),f.get('password'),selectedReferrerId,String(f.get('birthday')||''));
          storageRemove(INVITE_REFERRER_KEY);
          if(data?.access_token){
            toast(data.private_birthday_saved?'Account created':'Account created. Confirm your birthday in Profile.');
            navigate('/feed');
          } else msg.textContent='Account created. Check your email, then confirm your birthday during profile setup.';
        }catch(err){msg.textContent=err.message||'Sign up failed.';}
      };
    };
    document.querySelector('[data-auth-tab="login"]').onclick=showLogin;document.querySelector('[data-auth-tab="signup"]').onclick=showSignup;document.querySelector('[data-nav="/"]').onclick=()=>navigate('/');if(storageGet(INVITE_REFERRER_KEY))showSignup();else showLogin();
  }

  async function uploadPublicFile(bucket,file,prefix='web'){
    if(!uid())throw new Error('Sign in first.');
    if(!file)throw new Error('Choose a file first.');
    const safeName=file.name.replace(/[^a-zA-Z0-9._-]/g,'_').slice(-120)||'upload';
    const objectPath=`${uid()}/${prefix}/${Date.now()}-${Math.random().toString(36).slice(2,8)}-${safeName}`;
    const res=await fetchWithTimeout(`${SUPABASE_URL}/storage/v1/object/${bucket}/${objectPath}`,{
      method:'POST',
      headers:{apikey:KEY,Authorization:`Bearer ${token()}`,'Content-Type':file.type||'application/octet-stream','x-upsert':'false'},
      body:file
    },120000);
    if(!res.ok){const e=await parseJsonSafe(res);throw new Error(e?.message||`Upload failed (${res.status})`);}
    return `${SUPABASE_URL}/storage/v1/object/public/${bucket}/${objectPath}`;
  }

  function validateMediaFile(file,{allowVideo=true,imageMaxMb=15,videoMaxMb=100}={}){
    if(!file)return null;
    const type=String(file.type||'').toLowerCase();
    const isImage=type.startsWith('image/');
    const isVideo=type.startsWith('video/');
    if(!isImage&&!(allowVideo&&isVideo))throw new Error(allowVideo?'Choose a valid image or video file.':'Choose a valid image file.');
    const maxBytes=(isVideo?videoMaxMb:imageMaxMb)*1024*1024;
    if(file.size>maxBytes)throw new Error(`${isVideo?'Video':'Image'} is too large. Maximum size is ${isVideo?videoMaxMb:imageMaxMb} MB.`);
    return {isImage,isVideo};
  }

  
  async function blinkStickerDb(){
    if(!window.indexedDB)throw new Error('This browser does not support saved stickers.');
    return new Promise((resolve,reject)=>{
      const req=indexedDB.open('blink-private-stickers-v1',1);
      req.onupgradeneeded=()=>{
        if(!req.result.objectStoreNames.contains('stickers'))
          req.result.createObjectStore('stickers',{keyPath:'key'});
      };
      req.onsuccess=()=>resolve(req.result);
      req.onerror=()=>reject(req.error||new Error('Sticker storage unavailable.'));
    });
  }
  async function blinkStickerOperation(mode,record=null){
    const owner=uid();
    if(!owner)throw new Error('Sign in to save and reuse stickers.');
    const db=await blinkStickerDb();
    return new Promise((resolve,reject)=>{
      const tx=db.transaction('stickers',mode==='list'?'readonly':'readwrite');
      const store=tx.objectStore('stickers');
      if(mode==='list'){
        const req=store.getAll();
        req.onsuccess=()=>resolve((req.result||[]).filter(x=>x.owner===owner)
          .sort((a,b)=>(b.created||0)-(a.created||0)).slice(0,30));
        req.onerror=()=>reject(req.error);
      }else{
        const req=mode==='remove'?store.delete(record.key):store.put(record);
        req.onsuccess=()=>resolve(true);
        req.onerror=()=>reject(req.error);
      }
      tx.oncomplete=()=>db.close();
      tx.onerror=()=>{db.close();reject(tx.error);};
    });
  }
  async function blinkSaveCanvasSticker(canvas){
    const existing=await blinkStickerOperation('list');
    if(existing.length>=30)throw new Error('You can keep up to 30 stickers. Remove one first.');
    const cropped=document.createElement('canvas');
    cropped.width=512;cropped.height=512;
    const cg=cropped.getContext('2d');
    const ratio=Math.min(512/canvas.width,512/canvas.height);
    const width=Math.round(canvas.width*ratio);
    const height=Math.round(canvas.height*ratio);
    cg.drawImage(canvas,(512-width)/2,(512-height)/2,width,height);
    const blob=await new Promise((resolve,reject)=>cropped.toBlob(
      x=>x?resolve(x):reject(new Error('Could not save sticker.')),'image/png'));
    if(blob.size>2*1024*1024)throw new Error('Sticker image is too large.');
    return blinkStickerOperation('save',{
      key:uid()+':'+crypto.randomUUID(),owner:uid(),
      created:Date.now(),blob
    });
  }

  async function blinkEditSelectedPhoto(file){
    if(!file||!/^image\/(jpeg|png)$/i.test(file.type))return file;
    const bitmap=await createImageBitmap(file);
    const ratio=Math.min(1,2048/Math.max(bitmap.width,bitmap.height));
    let surface=document.createElement('canvas');
    surface.width=Math.max(1,Math.round(bitmap.width*ratio));
    surface.height=Math.max(1,Math.round(bitmap.height*ratio));
    let g=surface.getContext('2d',{willReadFrequently:true});
    g.drawImage(bitmap,0,0,surface.width,surface.height);
    bitmap.close();
    const shade=document.createElement('div');
    shade.setAttribute('role','dialog');
    shade.setAttribute('aria-modal','true');
    shade.setAttribute('aria-label','BLINK Photo Editor');
    shade.style.cssText='position:fixed;inset:0;z-index:100000;background:#060608;color:#fff;display:flex;flex-direction:column;gap:12px;padding:env(safe-area-inset-top) 12px env(safe-area-inset-bottom);box-sizing:border-box;';
    const bar=document.createElement('div');
    bar.style.cssText='display:flex;justify-content:space-between;align-items:center;padding:10px 0;';
    const controls=document.createElement('div');
    controls.style.cssText='display:flex;gap:8px;justify-content:center;flex-wrap:wrap;';
    const view=document.createElement('div');
    view.style.cssText='flex:1;min-height:150px;display:grid;place-items:center;overflow:auto;background:#000;';
    surface.style.cssText='max-width:100%;max-height:100%;object-fit:contain;touch-action:none;box-shadow:0 0 2px #222;';
    const lower=document.createElement('div');
    lower.style.cssText='display:flex;flex-direction:column;gap:8px;min-height:115px;padding:8px 10px 16px;background:#101014;border-radius:14px;';
    const status=document.createElement('div');
    status.style.cssText='font-size:12px;color:#bdbdcc;text-align:center;';
    const mkBtn=(label,action)=>{
      const button=document.createElement('button');button.type='button';button.textContent=label;
      button.style.cssText='border:1px solid #555;background:#24242c;color:white;border-radius:12px;padding:9px 12px;font:inherit;font-size:12px;cursor:pointer;';
      button.addEventListener('click',action);return button;
    };
    let doneResolve;
    const response=new Promise(resolve=>{doneResolve=resolve;});
    let finished=false,drawEnabled=false,pointerDown=false;
    let ink='#fff';
    let edits={exposure:0,contrast:100,saturation:100};
    function close(result){
      if(finished)return;
      finished=true;shade.remove();
      document.removeEventListener('keydown',escape);
      doneResolve(result);
    }
    function escape(e){if(e.key==='Escape')close(file);}
    document.addEventListener('keydown',escape);
    bar.append(mkBtn('✕ CANCEL',()=>close(file)));
    const title=document.createElement('strong');title.textContent='BLINK PHOTO';
    bar.append(title);
    bar.append(mkBtn('✓ DONE',()=>{
      try{
        const adjusted=document.createElement('canvas');
        adjusted.width=surface.width;adjusted.height=surface.height;
        const cg=adjusted.getContext('2d');
        cg.filter=`brightness(${100+edits.exposure}%) contrast(${edits.contrast}%) saturate(${edits.saturation}%)`;
        cg.drawImage(surface,0,0);cg.filter='none';
        adjusted.toBlob(blob=>{
          if(!blob){status.textContent='Could not export picture.';return;}
          close(new File([blob],`BLINK_${Date.now()}.jpg`,{type:'image/jpeg'}));
        },'image/jpeg',.9);
      }catch(e){status.textContent='Could not save edits. Choose Cancel to use the original.';}
    }));
    let active='Adjust';
    function renderTools(){
      lower.replaceChildren();
      status.textContent=drawEnabled?'Draw on the picture with your finger or mouse.':'Edits are local until you publish.';
      const tools=document.createElement('div');
      tools.style.cssText='display:flex;gap:6px;justify-content:center;flex-wrap:wrap;';
      ['Adjust','Crop','Draw','Sticker','Text'].forEach(name=>tools.append(
        mkBtn(name,()=>{active=name;drawEnabled=name==='Draw';renderTools();})
      ));
      lower.append(tools);
      const options=document.createElement('div');
      options.style.cssText='display:flex;gap:10px;align-items:center;justify-content:center;flex-wrap:wrap;';
      const changeCanvas=newCanvas=>{
        const old=surface;
        surface=newCanvas;
        surface.style.cssText=old.style.cssText;
        old.replaceWith(surface);
        g=surface.getContext('2d',{willReadFrequently:true});
        attachDraw();
      };
      if(active==='Adjust'){
        [['Exposure','exposure',-80,80],['Contrast','contrast',50,150],['Saturation','saturation',0,200]]
        .forEach(([label,key,min,max])=>{
          const row=document.createElement('label');row.style.cssText='display:flex;gap:4px;align-items:center;font-size:11px;';
          row.textContent=label;
          const input=document.createElement('input');input.type='range';input.min=min;input.max=max;
          input.value=edits[key];input.setAttribute('aria-label',label);
          input.oninput=()=>{edits[key]=Number(input.value);
            surface.style.filter=`brightness(${100+edits.exposure}%) contrast(${edits.contrast}%) saturate(${edits.saturation}%)`;};
          row.append(input);options.append(row);
        });
      }else if(active==='Crop'){
        const crop=(aspect)=>{
          const ratio=surface.width/surface.height;
          const w=ratio>aspect?Math.round(surface.height*aspect):surface.width;
          const h=ratio>aspect?surface.height:Math.round(surface.width/aspect);
          const next=document.createElement('canvas');next.width=w;next.height=h;
          next.getContext('2d').drawImage(surface,(surface.width-w)/2,(surface.height-h)/2,w,h,0,0,w,h);
          changeCanvas(next);
        };
        [['Square',1],['16:9',16/9],['9:16',9/16]].forEach(([label,ratio])=>
          options.append(mkBtn(label,()=>crop(ratio))));
        options.append(mkBtn('Rotate 90°',()=>{
          const next=document.createElement('canvas');next.width=surface.height;next.height=surface.width;
          const ctx=next.getContext('2d');ctx.translate(next.width,0);ctx.rotate(Math.PI/2);
          ctx.drawImage(surface,0,0);changeCanvas(next);
        }));
      }else if(active==='Draw'){
        ['#fff','#ffeb3b','#ec375c','#3bb9ff','#000'].forEach(color=>{
          const swatch=mkBtn('●',()=>ink=color);
          swatch.style.color=color;options.append(swatch);
        });
      }else if(active==='Sticker'){
        ['❤️','✨','🔥','😂','⭐','💜'].forEach(emoji=>
          options.append(mkBtn(emoji,()=>{
            g.font=`bold ${Math.round(surface.width*.12)}px sans-serif`;
            g.textAlign='center';g.fillText(emoji,surface.width/2,surface.height/2);
          })));
        options.append(mkBtn('＋ Save photo as sticker',async()=>{
          status.textContent='Saving your local sticker…';
          try{
            await blinkSaveCanvasSticker(surface);
            status.textContent='Sticker saved for reuse on this browser.';
            if(active==='Sticker')renderTools();
          }catch(err){status.textContent=err.message;}
        }));
        blinkStickerOperation('list').then(records=>{
          if(active!=='Sticker'||finished)return;
          const gallery=document.createElement('div');
          gallery.style.cssText='display:flex;gap:7px;overflow-x:auto;align-items:center;width:100%;';
          records.forEach(record=>{
            const picture=document.createElement('img');
            const previewUrl=URL.createObjectURL(record.blob);
            picture.src=previewUrl;
            picture.alt='Add saved sticker to photo';
            picture.style.cssText='height:48px;width:48px;object-fit:contain;background:#282830;border-radius:10px;cursor:pointer;';
            picture.onload=()=>URL.revokeObjectURL(previewUrl);
            picture.onclick=async()=>{
              const bitmap=await createImageBitmap(record.blob);
              const side=Math.max(32,Math.min(surface.width,surface.height)*.28);
              g.drawImage(bitmap,(surface.width-side)/2,(surface.height-side)/2,side,side);
              bitmap.close();
              status.textContent='Saved sticker added to your photo.';
            };
            gallery.append(picture);
            const remove=mkBtn('✕',async()=>{
              if(!window.confirm('Remove saved sticker from this browser?'))return;
              await blinkStickerOperation('remove',record);
              if(active==='Sticker')renderTools();
            });
            remove.setAttribute('aria-label','Remove saved sticker');
            gallery.append(remove);
          });
          lower.append(gallery);
        }).catch(err=>{status.textContent=err.message;});
      }else if(active==='Text'){
        const input=document.createElement('input');input.type='text';input.maxLength=80;
        input.placeholder='Write on picture';input.style.cssText='background:#212127;color:#fff;border:1px solid #aaa;padding:8px;border-radius:8px;';
        options.append(input,mkBtn('Add text',()=>{
          const str=input.value.trim();if(!str)return;
          g.font=`bold ${Math.round(surface.width*.065)}px sans-serif`;
          g.fillStyle=ink;g.textAlign='center';
          g.shadowColor='#000';g.shadowBlur=5;
          g.fillText(str,surface.width/2,surface.height/2);
          g.shadowBlur=0;input.value='';
        }));
      }
      lower.append(options,status);
    }
    let previousPos=null;
    function attachDraw(){
      surface.addEventListener('pointerdown',e=>{
        if(!drawEnabled)return;
        pointerDown=true;surface.setPointerCapture(e.pointerId);
        const b=surface.getBoundingClientRect();
        previousPos=[(e.clientX-b.left)*surface.width/b.width,(e.clientY-b.top)*surface.height/b.height];
      });
      surface.addEventListener('pointermove',e=>{
        if(!drawEnabled||!pointerDown||!previousPos)return;
        const b=surface.getBoundingClientRect();
        const pos=[(e.clientX-b.left)*surface.width/b.width,(e.clientY-b.top)*surface.height/b.height];
        g.strokeStyle=ink;g.lineWidth=Math.max(3,surface.width*.007);
        g.lineCap='round';g.beginPath();g.moveTo(...previousPos);g.lineTo(...pos);g.stroke();
        previousPos=pos;
      });
      ['pointerup','pointercancel'].forEach(event=>surface.addEventListener(event,()=>{
        pointerDown=false;previousPos=null;
      }));
    }
    attachDraw();renderTools();
    view.append(surface);shade.append(bar,view,lower);
    document.body.appendChild(shade);
    return response;
  }

  function marketOnlyFeedWarning(category,text){
    const categoryKey=String(category||'').trim().toLowerCase();
    if(['marketplace','market','buy & sell','for sale','selling'].includes(categoryKey))
      return 'Product listings must be created in BLINK Market.';
    const copy=String(text||'').trim();
    const salePhrase=/(^|[^a-z])(for sale|selling (?:this|my|a|an)|available for (?:purchase|sale)|dm to (?:buy|order)|buy now|order now|price is)([^a-z]|$)/i;
    const nairaPrice=/(₦|NGN\s*)\s*[0-9]/i;
    return salePhrase.test(copy)&&nairaPrice.test(copy)
      ? 'This appears to be a product advertisement. Please list it in BLINK Market instead.'
      : '';
  }

  function openPostComposer(){
    const draft=storageGet(draftKey(),'');
    modal('Create post',`<form id="post-form" class="form-stack"><textarea class="field" name="text" maxlength="8000" placeholder="What's happening on campus?">${esc(draft)}</textarea><div class="form-grid"><select class="field" name="audience"><option>Everyone</option><option>Followers</option></select><select class="field" name="category"><option>Campus Life</option><option>Education</option><option>Entertainment</option><option>Sports</option><option>Technology</option></select></div><div class="side-card"><strong>Product sales belong in BLINK Market</strong><p class="muted small">Regular Feed posts are for campus updates, not selling products.</p><button type="button" class="btn small-btn" id="post-open-market">Create a Market listing</button></div><div id="post-market-warning" class="error" role="alert" hidden></div><input class="field" name="tags" maxlength="500" placeholder="Tags, comma separated"><input class="field" name="location" maxlength="160" placeholder="Location (optional)"><label class="checkbox"><input type="checkbox" name="reel"> Publish as Reel</label><label class="btn" style="text-align:center">Attach image/video<input class="hidden" id="post-media-file" type="file" accept="image/*,video/*"></label><div id="media-name" class="muted small"></div><button type="submit" class="btn primary">Publish</button><div id="post-msg"></div></form>`);
    const form=document.getElementById('post-form');
    const file=document.getElementById('post-media-file');
    const mediaName=document.getElementById('media-name');
    const submit=form.querySelector('button[type="submit"]');
    let editedMedia=null;
    const marketWarning=document.getElementById('post-market-warning');
    document.getElementById('post-open-market').onclick=()=>{closeModal();navigate('/market');};
    function updateMarketWarning(){
      const warning=marketOnlyFeedWarning(form.elements.category.value,form.elements.text.value);
      marketWarning.textContent=warning;
      marketWarning.hidden=!warning;
      return warning;
    }
    form.elements.text.oninput=e=>{storageSet(draftKey(),e.target.value);updateMarketWarning();};
    form.elements.category.onchange=updateMarketWarning;
    updateMarketWarning();
    file.onchange=async()=>{
      try{
        editedMedia=null;
        submit.disabled=true;
        const media=file.files[0];
        const info=media?validateMediaFile(media):null;
        mediaName.textContent=media?.name||'';
        if(info?.isImage){
          editedMedia=await blinkEditSelectedPhoto(media);
          mediaName.textContent=`${editedMedia.name} · ready to publish`;
        }
      }catch(err){
        file.value='';editedMedia=null;
        mediaName.textContent=err.message;
      }finally{submit.disabled=false;}
    };
    form.onsubmit=async e=>{
      e.preventDefault();
      const f=new FormData(form),msg=document.getElementById('post-msg'),media=editedMedia||file.files[0];
      submit.disabled=true;
      msg.innerHTML='<span class="muted">Publishing…</span>';
      try{
        const text=String(f.get('text')||'').trim();
        const restricted=marketOnlyFeedWarning(f.get('category'),text);
        if(restricted)throw new Error(restricted);
        let mediaUrl='',videoUrl='',images=[];
        if(media){
          const kind=validateMediaFile(media);
          mediaUrl=await uploadPublicFile('post-media',media,'posts');
          if(kind.isVideo)videoUrl=mediaUrl;else images=[mediaUrl];
        }
        if(!text&&!media)throw new Error('Add text or media.');
        const body={
          user_id:uid(),text,caption:text,type:videoUrl?'video':images.length?'image':'text',
          is_reel:f.get('reel')==='on'||!!videoUrl,
          audience:f.get('audience')||'Everyone',category:f.get('category')||'Campus Life',
          location:String(f.get('location')||'').trim()||null,
          tags:String(f.get('tags')||'').split(',').map(x=>x.trim()).filter(Boolean).slice(0,20),
          images,image_url:images[0]||null,video_url:videoUrl||null
        };
        const rows=await insert('feed_posts',body);
        storageRemove(draftKey());
        closeModal();
        toast('Published');
        if(rows?.[0]?.id)navigate(`/${body.is_reel?'reel':'post'}/${rows[0].id}`);else render('/feed');
      }catch(err){
        msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;
        submit.disabled=false;
      }
    };
  }

  async function createStory(){
    modal('Add story',`<form id="story-form" class="form-stack"><textarea class="field" name="text" maxlength="1000" placeholder="Story caption"></textarea><label class="btn" style="text-align:center">Choose image/video<input id="story-file" class="hidden" type="file" accept="image/*,video/*" required></label><div id="story-file-name" class="muted small"></div><button type="submit" class="btn primary">Share story</button><div id="story-msg"></div></form>`);
    const file=document.getElementById('story-file');
    const form=document.getElementById('story-form');
    const label=document.getElementById('story-file-name');
    const submit=form.querySelector('button[type="submit"]');
    let editedStoryFile=null;
    file.onchange=async()=>{
      try{
        editedStoryFile=null;
        submit.disabled=true;
        const media=file.files[0];
        const kind=media?validateMediaFile(media,{imageMaxMb:12,videoMaxMb:80}):null;
        label.textContent=media?.name||'';
        if(kind?.isImage){
          editedStoryFile=await blinkEditSelectedPhoto(media);
          label.textContent=`${editedStoryFile.name} · ready to share`;
        }
      }
      catch(err){file.value='';editedStoryFile=null;label.textContent=err.message;}
      finally{submit.disabled=false;}
    };
    form.onsubmit=async e=>{
      e.preventDefault();
      const media=editedStoryFile||file.files[0],msg=document.getElementById('story-msg');
      if(!media){msg.innerHTML='<div class="error">Choose an image or video.</div>';return;}
      submit.disabled=true;
      msg.innerHTML='<span class="muted">Uploading…</span>';
      try{
        const kind=validateMediaFile(media,{imageMaxMb:12,videoMaxMb:80});
        const url=await uploadPublicFile('story-media',media,'stories');
        const caption=String(new FormData(form).get('text')||'').trim();
        await insert('stories',{user_id:uid(),active:true,media_url:url,media_type:kind.isVideo?'video':'image',caption,text:caption,image_url:kind.isVideo?null:url,video_url:kind.isVideo?url:null});
        closeModal();toast('Story shared');render('/feed');
      }catch(err){msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;submit.disabled=false;}
    };
  };

  async function fetchProfiles(ids){ const unique=[...new Set(ids.filter(Boolean))];if(!unique.length)return new Map(); const rows=await rpc('get_public_profiles_by_ids',{p_ids:unique});return new Map((rows||[]).map(p=>[p.id,p])); }
  function normalizeFeedItem(row,profiles){ const x=row?.item||row; const p=profiles.get(x.user_id)||{}; return {...x,author:p}; }
  function mediaBlock(post){
    const imgs=(post.images?.length?post.images:[post.image_url]).filter(Boolean).map(safeUrl).filter(Boolean);
    const raw=typeof post.video_url==='string'?post.video_url.trim():'';
    const video=safeUrl(raw);
    const declaredVideo=post.media_type==='video'||post.type==='video'||post.is_reel===true;
    const videoExtension=/\.(mp4|webm|mov|m4v|ogv)(?:[?#]|$)/i.test(raw);
    const invalidPlaceholder=/^(null|undefined|none|false|0|about:blank)$/i.test(raw);
    const hasRealVideo=!!video&&!invalidPlaceholder&&(declaredVideo||videoExtension||/\/storage\/v1\/object\//i.test(raw));
    if(hasRealVideo)return `<div class="post-media" data-video-post><video src="${esc(video)}" controls playsinline preload="none" data-autopause onerror="this.closest('[data-video-post]').innerHTML='<div class=&quot;muted&quot; style=&quot;padding:16px&quot;>Video unavailable</div>'"></video></div>`;
    if(imgs.length===1)return `<div class="post-media"><img src="${esc(imgs[0])}" alt="${esc(post.alt_text||'Post image')}" loading="lazy"></div>`;
    if(imgs.length>1)return `<div class="post-media media-grid">${imgs.slice(0,4).map(u=>`<img src="${esc(u)}" alt="" loading="lazy">`).join('')}</div>`;
    return '';
  }
  function postCard(post){ const a=post.author||{};const isReel=!!post.is_reel;const path=`/${isReel?'reel':'post'}/${post.id}`;return `<article class="card post-card" data-post-id="${esc(post.id)}"><header class="post-head"><button class="profile-mini" style="border:0;background:transparent;padding:0" data-nav="/@${esc(a.username||post.user_id)}">${avatar(a.avatar_url,a.full_name||a.username)}<span class="grow"><strong>${esc(a.full_name||a.username||'Blink user')}${verifyMark(a)}${vipMark(a)}</strong><span class="handle">@${esc(a.username||'user')} · ${esc(ago(post.created_at))}</span></span></button>${post.is_sponsored?'<span class="handle">Sponsored</span>':''}<button class="icon-btn" data-open="${esc(path)}">⋯</button></header><div class="post-body">${post.text||post.caption?`<p class="post-text">${esc(post.text||post.caption)}</p>`:''}${richLinkCard([post.text||post.caption||'',post.link_url||''].join(' '))}${Array.isArray(post.tags)&&post.tags.length?`<div class="tags">${post.tags.slice(0,10).map(t=>`#${esc(t)}`).join(' ')}</div>`:''}</div>${mediaBlock(post)}<div class="post-stats"><span>${fmt(post.view_count)} views</span><span>${fmt(post.repost_count)} reposts</span><span>${fmt(post.comment_count)} comments</span><span>${fmt(post.like_count)} likes</span></div><div class="post-actions"><button data-like="${esc(post.id)}" class="like">♡ Like</button><button data-repost="${esc(post.id)}">↻ Repost</button><button data-comments="${esc(post.id)}">◌ Comment</button><button data-bookmark="${esc(post.id)}" class="bookmark">⌑ Save</button><button data-share="${esc(path)}">↗ Share</button></div></article>`; }
  function bindPostCards(){
    document.querySelectorAll('[data-open]').forEach(x=>x.onclick=()=>navigate(x.dataset.open));
    document.querySelectorAll('[data-like]').forEach(x=>x.onclick=async()=>{if(!requireAuth()||x.disabled)return;const on=x.classList.toggle('on');x.disabled=true;x.setAttribute('aria-pressed',String(on));try{await rpc(on?'like_post':'unlike_post',{p_post_id:x.dataset.like});toast(on?'Liked':'Like removed');}catch(e){x.classList.toggle('on');x.setAttribute('aria-pressed',String(!on));toast(e.message);}finally{x.disabled=false;}});
    document.querySelectorAll('[data-bookmark]').forEach(x=>x.onclick=async()=>{if(!requireAuth()||x.disabled)return;const on=x.classList.toggle('on');x.disabled=true;x.setAttribute('aria-pressed',String(on));try{await rpc(on?'bookmark_post':'unbookmark_post',{p_post_id:x.dataset.bookmark});toast(on?'Saved':'Removed from saved');}catch(e){x.classList.toggle('on');x.setAttribute('aria-pressed',String(!on));toast(e.message);}finally{x.disabled=false;}});
    document.querySelectorAll('[data-repost]').forEach(x=>x.onclick=async()=>{if(!requireAuth()||x.disabled)return;x.disabled=true;try{await rpc('toggle_post_repost',{p_post_id:x.dataset.repost});const on=x.classList.toggle('on');x.setAttribute('aria-pressed',String(on));toast('Repost updated');}catch(e){toast(e.message);}finally{x.disabled=false;}});
    document.querySelectorAll('[data-comments]').forEach(x=>x.onclick=()=>openComments(x.dataset.comments));
    document.querySelectorAll('[data-share]').forEach(x=>x.onclick=()=>sharePath(x.dataset.share));
    document.querySelectorAll('[data-nav]').forEach(x=>x.onclick=e=>{e.preventDefault();navigate(x.dataset.nav);});
    bindQualifiedViews();
    autoPauseVideos();
  }
  function autoPauseVideos(){
    const vids=[...document.querySelectorAll('video[data-autopause]')];
    state.mediaObserver?.disconnect();
    state.mediaObserver=null;
    // Keep tap-to-play available on older browsers.
    if(!('IntersectionObserver'in window))return;
    state.mediaObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.intersectionRatio<.35)entry.target.pause();}),{threshold:[0,.35,.7]});
    vids.forEach(v=>state.mediaObserver.observe(v));
  }
  async function sharePath(path){
    const normalizedPath = String(path || '/').startsWith('/') ? String(path || '/') : `/${String(path || '')}`;
    const url = SITE_URL + normalizedPath;
    if(navigator.share){
      try{await navigator.share({title:'Blink',url});return;}
      catch(err){if(err?.name==='AbortError')return;}
    }
    try{
      if(navigator.clipboard?.writeText){await navigator.clipboard.writeText(url);}
      else{
        const area=document.createElement('textarea');area.value=url;area.style.position='fixed';area.style.opacity='0';document.body.appendChild(area);area.select();
        if(!document.execCommand('copy'))throw new Error('copy failed');
        area.remove();
      }
      toast('Link copied');
    }catch{toast('Could not copy the link.');}
  }
  function getVisitorId(){
    let id=storageGet(VISITOR_KEY,'');
    if(!id){
      id=globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random().toString(36).slice(2)}`;
      storageSet(VISITOR_KEY,id);
    }
    return id;
  }
  async function hashVisitor(){
    const value=getVisitorId();
    if(globalThis.crypto?.subtle){
      const bytes=new TextEncoder().encode(value);
      const hash=await crypto.subtle.digest('SHA-256',bytes);
      return [...new Uint8Array(hash)].map(b=>b.toString(16).padStart(2,'0')).join('');
    }
    let h=2166136261;
    for(let i=0;i<value.length;i++){h^=value.charCodeAt(i);h=Math.imul(h,16777619);}
    return `fallback-${(h>>>0).toString(16).padStart(8,'0')}`;
  }
  function scheduleQualifiedView(postId,element){
    if(!postId||element?.dataset.viewRecorded==='1'||state.qualifiedTimers.has(postId)||document.visibilityState!=='visible')return;
    const timer=setTimeout(async()=>{
      let recorded=false;
      try{
        if(isAuthed())await rpc('record_qualified_post_view',{p_post_id:postId,p_viewer_username:state.profile?.username||'',p_viewed_for_seconds:60});
        else await anonRpc('record_public_web_view',{p_post_id:postId,p_visitor_hash:await hashVisitor()});
        recorded=true;
      }catch{}
      finally{
        state.qualifiedTimers.delete(postId);
        if(recorded&&element)element.dataset.viewRecorded='1';
      }
    },60000);
    state.qualifiedTimers.set(postId,timer);
  }

  function cancelQualifiedView(postId){
    const timer=state.qualifiedTimers.get(postId);
    if(timer)clearTimeout(timer);
    state.qualifiedTimers.delete(postId);
  }

  function bindQualifiedViews(){
    if(!('IntersectionObserver'in window))return;
    if(!state.viewObserver){
      state.viewObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{
        const el=entry.target,postId=el.dataset.postId;
        if(!postId)return;
        if(entry.isIntersecting&&entry.intersectionRatio>=.6)scheduleQualifiedView(postId,el);
        else cancelQualifiedView(postId);
      }),{threshold:[0,.6,1]});
    }
    document.querySelectorAll('[data-post-id]').forEach(el=>{
      if(el.dataset.viewTracked==='1'||el.dataset.viewRecorded==='1')return;
      el.dataset.viewTracked='1';
      state.viewObserver.observe(el);
    });
  }

  async function loadStories(){ if(!isAuthed())return []; try{const rows=await table('stories','active=eq.true&select=id,user_id,media_url,media_type,caption,text,image_url,video_url,created_at,likes_count,views_count&order=created_at.desc&limit=40'); const profiles=await fetchProfiles(rows.map(x=>x.user_id)); return rows.map(s=>({...s,author:profiles.get(s.user_id)||{}}));}catch{return [];} }
  function storyStrip(stories){return `<div class="stories"><button class="story-ring" data-create-story><div class="avatar-sm avatar-fallback" style="width:58px;height:58px">＋</div><strong>Your story</strong></button>${stories.map(s=>`<button class="story-ring" data-story="${esc(s.id)}">${avatar(s.author.avatar_url,s.author.full_name,'avatar-sm')}<strong>${esc(s.author.username||'story')}</strong></button>`).join('')}</div>`;}
  function bindStories(stories){document.querySelector('[data-create-story]')?.addEventListener('click',()=>requireAuth()&&createStory());document.querySelectorAll('[data-story]').forEach(x=>x.onclick=()=>openStory(stories.find(s=>s.id===x.dataset.story)));}
  function openStory(s){if(!s)return;const media=safeUrl(s.video_url||s.image_url||s.media_url);modal(`@${s.author?.username||'Blink'}`,`${s.media_type==='video'||s.video_url?`<video src="${esc(media)}" controls autoplay playsinline style="width:100%;max-height:70vh"></video>`:`<img src="${esc(media)}" alt="Story" style="width:100%;max-height:70vh;object-fit:contain">`}<p>${esc(s.caption||s.text||'')}</p>${richLinkCard(s.caption||s.text||'')}<div class="muted small">${fmt(s.views_count)} views · ${fmt(s.likes_count)} likes</div>`);hydrateLinkCards();}

  async function renderFeed(path='/feed'){
    if(!requireAuth())return;const live=still(); state.loading=true; ROOT.innerHTML=shell('<div class="feed-skeleton" role="status" aria-label="Loading BLINK feed"><div class="feed-skeleton-card"><div class="feed-skeleton-line short"></div><div class="feed-skeleton-line medium"></div><div class="feed-skeleton-media"></div><div class="feed-skeleton-line"></div></div><div class="feed-skeleton-card"><div class="feed-skeleton-line short"></div><div class="feed-skeleton-line medium"></div><div class="feed-skeleton-media"></div></div><span class="sr-only">Loading your BLINK feed</span></div>','Home');bindCommon();
    try{const stories=await loadStories();const mode=new URLSearchParams(location.search).get('mode')||'posts';state.feedMode=['posts','reels','all'].includes(mode)?mode:'posts';const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:0,p_as_of:null,p_feed_type:state.feedMode});state.feedOffset=rows?.[0]?.next_offset||rows?.length||0;state.feedAsOf=rows?.[0]?.as_of||null;const profiles=await fetchProfiles((rows||[]).map(r=>(r.item||r).user_id));if(!live())return;state.feed=(rows||[]).map(r=>normalizeFeedItem(r,profiles));const content=`<div class="tabs"><button class="tab ${state.feedMode==='posts'?'active':''}" data-feed-mode="posts">For you</button><button class="tab" data-following>Following</button></div><section class="card composer" style="display:none"><div class="composer-row">${avatar(state.profile?.avatar_url,state.profile?.full_name)}<textarea id="quick-draft" placeholder="Create a post…">${esc(storageGet(draftKey(),''))}</textarea></div><div class="composer-tools"><div class="left"><button class="btn small-btn" data-action="create-story">Story</button><button class="btn small-btn" data-action="create-post">Photo / video</button></div><button class="btn primary small-btn" data-action="quick-publish">Post</button></div></section><div class="feed" id="feed-list">${state.feed.length?state.feed.map(postCard).join(''):'<div class="card empty"><h2>No posts yet</h2><p>Your ranked feed is empty right now.</p></div>'}</div><div class="load-more"><button class="btn" id="load-more-feed">Load more</button></div>`;ROOT.innerHTML=shell(content,'Home');bindCommon();bindPostCards();document.querySelectorAll('[data-feed-mode]').forEach(x=>x.onclick=()=>{const u=new URL(location.href);u.searchParams.set('mode',x.dataset.feedMode);history.replaceState({},'',u);renderFeed();});document.querySelector('[data-following]').onclick=renderFollowing;document.querySelector('[data-action="create-story"]').onclick=()=>createStory();const q=document.getElementById('quick-draft');q.oninput=()=>storageSet(draftKey(),q.value);document.querySelector('[data-action="quick-publish"]').onclick=()=>{if(q.value.trim())openPostComposer();else toast('Type something first.');};document.getElementById('load-more-feed').onclick=loadMoreFeed;}catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="card empty"><h2>Couldn’t load feed</h2><p>${esc(e.message)}</p><button class="btn" data-nav="/feed">Try again</button></div>`,'Home');bindCommon();}
    finally{if(live())state.loading=false;}
  }
  async function loadMoreFeed(){if(state.loading)return;state.loading=true;const live=still();const btn=document.getElementById('load-more-feed');if(btn)btn.textContent='Loading…';try{const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:state.feedOffset,p_as_of:state.feedAsOf,p_feed_type:state.feedMode});if(!live())return;if(!rows?.length){toast('You’re caught up');return;}state.feedOffset=rows[0]?.next_offset||state.feedOffset+rows.length;const profiles=await fetchProfiles(rows.map(r=>(r.item||r).user_id));const items=rows.map(r=>normalizeFeedItem(r,profiles));if(!live())return;const list=document.getElementById('feed-list');if(!list)return;const existing=new Set([...list.querySelectorAll('[data-post-id]')].map(el=>el.dataset.postId));const fresh=items.filter(item=>!existing.has(String(item.id)));if(fresh.length){list.insertAdjacentHTML('beforeend',fresh.map(postCard).join(''));bindPostCards();}else toast('No new posts on this page');}catch(e){toast(e.message);}finally{if(live()){state.loading=false;if(btn?.isConnected)btn.textContent='Load more';}}}
  async function renderFollowing(){
    const live=still();try{
      const rows=await rpc('get_following_feed',{p_limit:40,p_cursor:null});
      const profiles=await fetchProfiles((rows||[]).map(x=>x.user_id));if(!live())return;
      state.feedMode='following';
      state.feed=(rows||[]).map(x=>normalizeFeedItem(x,profiles));
      const followingList=document.getElementById('feed-list');if(!followingList)return;followingList.innerHTML=state.feed.length?state.feed.map(postCard).join(''):'<div class="card empty"><h2>Your Following feed is quiet</h2><p>Follow more people to see their posts here.</p></div>';
      document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
      document.querySelector('[data-following]')?.classList.add('active');
      const loadMore=document.getElementById('load-more-feed');
      if(loadMore)loadMore.hidden=true;
      bindPostCards();
    }catch(e){if(live())toast(e.message);}
  }

  async function renderReels(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<div class="boot" style="min-height:55vh"><div class="spinner"></div></div>','Reels');bindCommon();
    try{
      const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:0,p_as_of:null,p_feed_type:'reels'});
      const profiles=await fetchProfiles((rows||[]).map(r=>(r.item||r).user_id));if(!live())return;
      const reels=(rows||[]).map(r=>normalizeFeedItem(r,profiles));
      const html=reels.length?`<div class="reels-feed">${reels.map(r=>{const a=r.author||{},src=safeUrl(r.video_url||r.image_url||(r.images||[])[0]);return `<article class="reel-card" data-post-id="${esc(r.id)}">${r.video_url?`<video data-src="${esc(src)}" playsinline controls muted preload="none" data-autopause></video>`:`<img src="${esc(src)}" alt="Reel" loading="lazy" decoding="async">`}<div class="reel-overlay"><div class="reel-creator"><button type="button" class="reel-creator-link" data-nav="/@${esc(a.username||r.user_id)}">${avatar(a.avatar_url,a.full_name||a.username,'avatar-sm')}<strong>@${esc(a.username||'user')}${verifyMark(a)}</strong></button><button type="button" class="reel-follow" data-reel-follow="${esc(r.user_id)}" aria-label="Follow creator">${state.following.has(r.user_id)?'Following':'Follow'}</button></div><p class="reel-caption">${esc(r.text||r.caption||'')}</p>${richLinkCard([r.text||r.caption||'',r.link_url||''].join(' '))}</div><div class="reel-actions"><button type="button" data-like="${esc(r.id)}" aria-label="Like reel">♡<small>Like</small></button><button type="button" data-comments="${esc(r.id)}" aria-label="Comment on reel">◉<small>Comment</small></button><button type="button" data-bookmark="${esc(r.id)}" aria-label="Save reel">▢<small>Save</small></button><button type="button" data-share="/reel/${esc(r.id)}" aria-label="Share reel">↗<small>Share</small></button></div></article>`;}).join('')}</div>`:'<div class="card empty"><h2>No reels available</h2></div>';
      ROOT.innerHTML=shell(html,'Reels');bindCommon();bindPostCards();
      document.querySelectorAll('[data-reel-follow]').forEach(button=>button.addEventListener('click',async()=>{
        if(button.disabled||!requireAuth())return;
        const creator=button.dataset.reelFollow;
        if(!creator||creator===uid())return;
        button.disabled=true;
        try{
          const already=state.following.has(creator);
          await rpc(already?'unfollow_user':'follow_user',{p_target_user_id:creator});
          if(already)state.following.delete(creator);else state.following.add(creator);
          document.querySelectorAll('[data-reel-follow]').forEach(b=>{if(b.dataset.reelFollow===creator)b.textContent=already?'Follow':'Following';});
        }catch(error){toast(error.message);}finally{button.disabled=false;}
      }));

      state.mediaObserver?.disconnect();
      state.mediaObserver=null;
      const reelCards=[...document.querySelectorAll('.reel-card')];
      if('IntersectionObserver'in window){
        let prefs={};try{prefs=JSON.parse(storageGet(PARITY_PREF_KEY,'{}'))||{};}catch{}
        const autoplay=prefs.autoplay!==false
          &&navigator.connection?.saveData!==true
          &&!matchMedia('(prefers-reduced-motion: reduce)').matches;
        state.mediaObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{
          const video=entry.target.querySelector('video');if(!video)return;
          if(entry.isIntersecting && video.dataset.src && !video.getAttribute('src')){
            video.setAttribute('src',video.dataset.src);
            video.addEventListener('canplay',()=>{
              if(video.dataset.reelVisible==='true' && autoplay)video.play().catch(()=>{});
            },{once:true});
            video.load();
          }
          video.dataset.reelVisible=entry.intersectionRatio>.7?'true':'false';
          if(entry.intersectionRatio>.7&&autoplay)video.play().catch(()=>{});
          else video.pause();
        }),{threshold:[0,.7],rootMargin:'300px 0px'});
        reelCards.forEach(card=>state.mediaObserver.observe(card));
      }else{
        reelCards.forEach(card=>{
          const video=card.querySelector('video');
          if(video?.dataset.src)video.setAttribute('src',video.dataset.src);
        });
      }
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="card empty"><h2>Couldn’t load reels</h2><p>${esc(e.message)}</p></div>`,'Reels');bindCommon();}
  }


  const {themeId,ownerThemeCss,ownerThemePicker}=window.BlinkProfileTheme;

  async function renderPublicProfile(identifier){const live=still();
    ROOT.innerHTML='<div class="boot"><div class="spinner"></div></div>';
    try{
      const cleanIdentifier=String(identifier||'').trim().replace(/^@/,'');
      if(!cleanIdentifier)throw new Error('Profile not available');
      const data=await anonRpc('get_public_web_profile',{p_username:cleanIdentifier});
      if(!data?.available||!data.profile)throw new Error('Profile not available');
      const p=data.profile,items=Array.isArray(data.items)?data.items:[];
      const publicTheme=themeId(await anonRpc('get_public_web_profile_theme',{p_username:p.username}).catch(()=>null));
      let publicPremium=null;
      if(isAuthed()){
        try{
          const style=await rpc('get_blink_public_premium_style',{p_username:p.username});
          const cosmetics=(Array.isArray(style?.items)?style.items:[]).map(x=>premiumCosmetic(x.catalog_id)).filter(Boolean);
          if(style?.is_vip)cosmetics.push(premiumCosmetic('blink_vip_10d'));
          publicPremium=cosmetics.sort((a,b)=>b.priority-a.priority)[0]||null;
        }catch{}
      }
      if(!live())return;
      if(p.username&&currentPath()!==`/@${p.username}`)history.replaceState({},'',routeHref(`/@${p.username}`));
      const actions=isAuthed()
        ? `<button type="button" class="btn primary" id="follow-profile">${state.following.has(p.id)?'Following':'Follow'}</button><button type="button" class="btn" id="message-profile">Message</button>`
        : `<button type="button" class="btn primary" data-nav="/login">Log in to follow</button><button type="button" class="btn" data-open-app-profile>Open app</button>`;
      const cover=safeUrl(p.coverPhotoUrl||p.cover_photo_url||p.coverPhoto||p.cover_photo);
      const tiles=items.map(i=>{
        const media=safeUrl(i.videoUrl||(i.imageUrls||[])[0]||i.imageUrl);
        const itemType=String(i.type||'').toLowerCase()==='reel'||i.isReel||!!i.videoUrl?'reel':'post';
        return `<button type="button" class="profile-tile" data-nav="/${itemType}/${esc(i.id)}">${media?(i.videoUrl?`<video src="${esc(media)}" muted preload="none"></video>`:`<img src="${esc(media)}" alt="" loading="lazy">`):`<div class="tile-text">${esc((i.text||i.caption||'Post').slice(0,120))}</div>`}<span class="tile-badge">${itemType==='reel'?'▶ Reel':'Post'} · ${fmt(i.viewCount)} views</span></button>`;
      }).join('');
      const html=`<div class="profile-card card owner-profile-theme ${publicPremium?'premium-profile-surface':''}" style="${ownerThemeCss(publicTheme)}${publicPremium?cosmeticVars(publicPremium):''}"><div class="profile-cover">${cover?`<img class="profile-cover-image" src="${esc(cover)}" alt="" loading="lazy">`:''}</div><div class="profile-content"><div class="profile-top">${avatar(p.avatarUrl,p.fullName,'profile-avatar')}<div>${actions}</div></div><div class="profile-info"><h2>${esc(p.fullName||p.username)}${verifyMark(p)}</h2><div class="handle">@${esc(p.username)}</div>${p.headline?`<div class="profile-meta">${esc(p.headline)}</div>`:''}${p.bio?`<p class="profile-bio">${esc(p.bio)}</p>${richLinkCard(p.bio)}`:''}<div class="profile-meta">${[p.university,p.faculty,p.department].filter(Boolean).map(esc).join(' · ')}</div><div class="stats-grid"><div class="stat"><strong>${fmt(p.postsCount)}</strong><span>Posts</span></div><div class="stat"><strong>${fmt(p.followerCount)}</strong><span>Followers</span></div><div class="stat"><strong>${fmt(p.followingCount)}</strong><span>Following</span></div></div></div></div>${publicPremium?`<span class="premium-profile-signature">${esc(publicPremium.label)}</span>`:''}</div><div class="profile-grid">${tiles}</div>`;
      ROOT.innerHTML=isAuthed()?shell(html,p.fullName||`@${p.username}`):`<div style="width:min(100%,760px);margin:0 auto;padding:14px"><header class="topbar"><button type="button" class="brand nav-btn" data-nav="/"><img class="mark" src="${LOGO_URL}" alt="" aria-hidden="true">Blink</button><div><a class="btn download-app" href="${APK_URL}">Download app</a><button type="button" class="btn" data-nav="/login">Log in</button></div></header>${html}</div>`;
      bindCommon();
      document.querySelector('[data-open-app-profile]')?.addEventListener('click',()=>openAndroid('profile',p.username));
      document.getElementById('follow-profile')?.addEventListener('click',async e=>{
        const button=e.currentTarget;button.disabled=true;
        try{
          const following=state.following.has(p.id);
          await rpc(following?'unfollow_user':'follow_user',{p_following_id:p.id});
          if(following)state.following.delete(p.id);else state.following.add(p.id);
          button.textContent=following?'Follow':'Following';
          toast(following?'Unfollowed':'Following');
        }catch(err){toast(err.message);}
        finally{button.disabled=false;}
      });
      document.getElementById('message-profile')?.addEventListener('click',()=>navigate(`/messages/new?user=${encodeURIComponent(p.username)}`));
    }catch(e){
      if(!live())return;ROOT.innerHTML=`<div class="auth-wrap"><div class="card empty"><h2>Profile unavailable</h2><p>This account may be private, removed or the username may be incorrect.</p><button type="button" class="btn" data-nav="/">Back to Blink</button></div></div>`;bindCommon();
    }
  }

  async function renderPublicContent(type,id){const live=still();ROOT.innerHTML='<div class="boot"><div class="spinner"></div></div>';try{const data=await anonRpc('get_public_web_content',{p_content_id:id,p_content_type:type});if(!live())return;if(!data?.available)throw new Error('Unavailable');const c=data.content,a=c.author||{};const normalized={id:c.id,user_id:a.id,text:c.text||c.caption,caption:c.caption,images:c.imageUrls||[],video_url:c.videoUrl,created_at:c.createdAt,like_count:c.likeCount,comment_count:c.commentCount,share_count:c.shareCount,repost_count:c.repostCount,view_count:c.viewCount,is_reel:type==='reel',allow_comments:c.allowComments,hide_likes:c.hideLikes,author:{id:a.id,username:a.username,full_name:a.fullName,avatar_url:a.avatarUrl,is_verified:a.isVerified,verification_badge:a.verificationBadge}};const extra=`<div class="card" style="padding:13px;margin-top:12px;display:flex;gap:8px;align-items:center;justify-content:space-between"><span class="muted small">${isAuthed()?'Interact with this content using your Blink account.':'Log in to like, comment, follow and message.'}</span><button class="btn primary small-btn" id="open-content-app">Open in Blink</button></div>`;ROOT.innerHTML=isAuthed()?shell(postCard(normalized)+extra,type==='reel'?'Reel':'Post'):`<div style="width:min(100%,760px);margin:0 auto;padding:14px"><header class="topbar"><button class="brand nav-btn" data-nav="/"><img class="mark" src="${LOGO_URL}" alt="" aria-hidden="true">Blink</button><button class="btn primary" data-nav="/login">Log in</button></header>${postCard(normalized)}${extra}</div>`;bindCommon();bindPostCards();document.getElementById('open-content-app').onclick=()=>openAndroid(type,id);}catch{if(!live())return;ROOT.innerHTML=`<div class="auth-wrap"><div class="card empty"><h2>Content unavailable</h2><p>This ${esc(type)} may be private or removed.</p><button class="btn" data-nav="/">Back to Blink</button></div></div>`;bindCommon();}}

  async function openComments(postId){
    if(!requireAuth())return;
    modal('Comments','<div id="comments-body"><div class="spinner"></div></div>');
    try{
      const rows=await rpc('get_post_comments',{p_post_id:postId});
      const body=document.getElementById('comments-body');if(!body)return;
      body.innerHTML=`<div class="comment-list">${(rows||[]).map(comment=>{const cosmetic=premiumCosmetic(comment.premium_style_id);return `<div class="comment ${cosmetic?'premium-comment-surface':''}" ${cosmetic?`style="${cosmeticVars(cosmetic)}"`:''}><div class="meta"><strong>${esc(comment.display_name||comment.username||'Blink user')}</strong> @${esc(comment.username||'')} · ${esc(ago(comment.created_at))}${cosmetic?`<span class="comment-premium-label">${esc(cosmetic.label)}</span>`:''}</div><div>${esc(comment.content)}</div>${richLinkCard(comment.content)}<button type="button" class="btn small-btn ghost" data-reply-comment="${esc(comment.id)}">Reply · ${fmt(comment.likes_count)} likes</button></div>`;}).join('')||'<p class="muted">No comments yet.</p>'}</div><form id="comment-form" class="chat-compose" style="margin-top:12px"><input class="field" name="text" maxlength="2000" placeholder="Write a comment…" required><button type="submit" class="btn primary">Send</button></form>`;
      hydrateLinkCards();
      let parent=null;
      document.querySelectorAll('[data-reply-comment]').forEach(x=>x.onclick=()=>{parent=x.dataset.replyComment;const input=document.querySelector('#comment-form input');input.placeholder='Write a reply…';input.focus();});
      document.getElementById('comment-form').onsubmit=async e=>{
        e.preventDefault();
        const input=e.currentTarget.elements.text;
        const text=String(input.value||'').trim();
        if(!text){toast('Write a comment first.');return;}
        const submit=e.currentTarget.querySelector('button[type="submit"]');submit.disabled=true;
        try{await rpc('create_comment',{p_post_id:postId,p_content:text,p_parent_comment_id:parent||null});toast('Comment posted');closeModal();openComments(postId);}
        catch(err){toast(err.message);submit.disabled=false;}
      };
    }catch(e){const body=document.getElementById('comments-body');if(body)body.innerHTML=`<div class="error">${esc(e.message)}</div>`;}
  }

  async function showReferralGrowth(){
    if(!requireAuth())return;
    modal('Refer & Grow', '<p>Invite friends. Confirmed referrals can improve your chances of being recommended to new members.</p>' +
      '<div id="referral-stats" class="muted">Loading your real referral activity…</div>' +
      '<div id="referral-growth" class="muted" style="margin-top:9px"></div>' +
      '<div id="referral-sources" class="muted" style="margin-top:9px"></div>' +
      '<div id="referral-impressions" class="muted" style="margin-top:9px"></div>' +
      '<div id="referral-links" class="muted" style="margin-top:9px"></div>' +
      '<div id="referral-coins" class="muted" style="margin-top:9px"></div>' +
      '<div id="referral-top" class="muted" style="margin-top:9px"></div>' +
      '<div class="form-stack"><button type="button" class="btn primary" id="share-referral">Share invitation</button></div>');
    const stats=document.getElementById('referral-stats');
    if(!stats)return;
    try{
      const rows=await rpc('blink_referral_dashboard');
      if(!stats.isConnected)return;
      const row=Array.isArray(rows)?rows[0]:null;
      const milestone=row?.achievement_next_target;
      stats.textContent=row
        ? 'Lifetime confirmed referrals: '+fmt(row.total_referrals)+
          ' · Achievement: '+String(row.achievement_tier||'New member')+
          ' · '+(milestone == null
                ? 'All referral milestones unlocked'
                : fmt(Math.max(0,Number(milestone)-Number(row.total_referrals||0)))+
                  ' more to reach '+fmt(milestone))+
          ' · Active last 30 days: '+fmt(row.active_referrals)+
          ' · Temporary visibility: '+fmt(row.visibility_points)+' points ('+
          String(row.visibility_label||'Normal discovery')+')'+
          ' · Each new confirmation adds 3 points for 10 days, then 2, then 1; after 30 days it expires. Your achievement never fades.'
        : 'No referral activity yet.';
    }catch{if(stats.isConnected)stats.textContent='Referral statistics are not available until the server update is ready.';}
    void Promise.allSettled([
      rpc('blink_referral_growth_stats'), rpc('blink_referral_leaderboard',{p_days:7,p_limit:5}),
      rpc('blink_referral_source_analytics'), rpc('blink_referral_coin_status'),
      rpc('blink_referral_link_stats'), rpc('blink_referral_impression_stats')
    ]).then(([growthResult,leaderResult,sourceResult,coinResult,linkResult,impressionResult])=>{
      const impressionSummary=document.getElementById('referral-impressions');
      if(impressionSummary?.isConnected && impressionResult.status==='fulfilled'){
        const item=Array.isArray(impressionResult.value)?impressionResult.value[0]:null;
        if(item)impressionSummary.textContent='Estimated seen Search profiles (7 days): '+
          fmt(item.search_impressions_7d)+' · Recommendations: '+
          fmt(item.recommendation_impressions_7d)+
          '. Impressions are visibility-qualified estimates, not verified human views.';
      }
      const linkStats=document.getElementById('referral-links');
      if(linkStats?.isConnected && linkResult.status==='fulfilled'){
        const item=Array.isArray(linkResult.value)?linkResult.value[0]:null;
        if(item)linkStats.textContent='Unique invite-browser opens (30 days, estimated): '+
          fmt(item.unique_link_opens_30d)+
          ' · Confirmed new accounts (30 days): '+fmt(item.confirmed_referrals_30d)+
          '. Opens are approximate and never earn Coins.';
      }
      const growth=document.getElementById('referral-growth');
      if(growth?.isConnected && growthResult.status==='fulfilled'){
        const x=Array.isArray(growthResult.value)?growthResult.value[0]:null;
        if(x) growth.textContent='Real profile views this week: '+fmt(x.profile_views_this_week)+
          ' · Followers gained this week: '+fmt(x.followers_gained_this_week)+
          ' · Invite share actions: '+fmt(x.share_actions)+
          ' · See Search and recommendation profile opens above for recorded source attribution.';
      }
      const sources=document.getElementById('referral-sources');
      if(sources?.isConnected && sourceResult.status==='fulfilled'){
        const item=Array.isArray(sourceResult.value)?sourceResult.value[0]:null;
        if(item)sources.textContent='Unique profile opens from Search (7 days): '+
          fmt(item.search_profile_opens)+' · From recommendations: '+fmt(item.recommendation_profile_opens);
      }
      const coins=document.getElementById('referral-coins');
      if(coins?.isConnected && coinResult.status==='fulfilled'){
        const item=Array.isArray(coinResult.value)?coinResult.value[0]:null;
        if(item)coins.textContent='Referral Coins earned: '+fmt(item.earned_coins)+
          (item.reward_enabled?' · Next milestone: '+fmt(item.next_milestone):
            ' · Milestone awards await owner approval.');
      }
      const lead=document.getElementById('referral-top');
      if(lead?.isConnected && leaderResult.status==='fulfilled'){
        const rows=Array.isArray(leaderResult.value)?leaderResult.value:[];
        if(rows.length)lead.textContent='Top connectors this week: '+
          rows.map(x=>'#'+x.rank_position+' @'+x.username+' ('+fmt(x.successful_referrals)+')').join(' · ');
      }
    });
    document.getElementById('share-referral')?.addEventListener('click',async()=>{
      const username=String(state.profile?.username||'').toLowerCase().replace(/^@/,'');
      if(!/^[a-z0-9][a-z0-9._-]{1,29}$/.test(username)){toast('Complete your username first.');return;}
      const invite='Join me on BLINK — connect and discover students beyond link.\n'+
        SITE_URL+'/invite/'+encodeURIComponent(username);
      try{
        if(navigator.share) {
          await navigator.share({title:'Join BLINK',text:invite});
          void rpc('blink_record_referral_share',{p_channel:'web'}).catch(()=>{});
        } else if(navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(invite);
          toast('Invite copied.');
          void rpc('blink_record_referral_share',{p_channel:'copy'}).catch(()=>{});
        } else toast('Sharing is unavailable in this browser.');
      }catch{}
    });
  }

  async function renderSearch(){
    if(!requireAuth())return;
    const q=new URLSearchParams(location.search).get('q')||'';
    const form=`<form id="search-form" style="display:flex;gap:8px;margin-bottom:13px"><input class="search-input" name="q" maxlength="120" value="${esc(q)}" placeholder="Search people, posts, tags…" autocomplete="off"><button class="btn primary">Search</button></form><div id="search-results"></div>`;
    const promo='<button type="button" class="card" id="refer-grow-promo" style="width:100%;text-align:left;margin-bottom:13px;padding:15px">' +
      '<strong>🚀 Refer friends. Get discovered.</strong><p class="muted small">Invite students to BLINK. Confirmed referrals can improve your chances of being recommended to new members.</p>' +
      '<span class="btn small-btn primary">Invite friends →</span>'+
      '<span class="muted small" id="refer-grow-promo-count"></span></button>';
    ROOT.innerHTML=shell(promo+form,'Search');bindCommon();
    document.getElementById('refer-grow-promo').onclick=showReferralGrowth;
    void rpc('blink_referral_dashboard').then(rows=>{
      const n=document.getElementById('refer-grow-promo-count');
      const item=Array.isArray(rows)?rows[0]:null;
      if(n?.isConnected && item)n.textContent=' · '+fmt(item.total_referrals)+' confirmed referrals';
    }).catch(()=>{});
    document.getElementById('search-form').onsubmit=e=>{
      e.preventDefault();const v=String(new FormData(e.currentTarget).get('q')||'').trim();
      const u=new URL(location.href);if(v)u.searchParams.set('q',v);else u.searchParams.delete('q');history.pushState({},'',u);runSearch(v);
    };
    if(q)runSearch(q);else document.getElementById('search-results').innerHTML='<div class="card empty"><h2>Find anything on Blink</h2><p>Search profiles and public feed content.</p></div>';
  }
  async function runSearch(query){
    const q=String(query||'').trim();
    const out=document.getElementById('search-results');if(!out)return;
    if(!q){out.innerHTML='<div class="card empty"><h2>Find anything on Blink</h2><p>Enter a name, username, post or tag.</p></div>';return;}
    const requestId=++state.searchRequestId;
    out.innerHTML='<div class="spinner"></div>';
    try{
      const profileResults=isAuthed()
        ? rpc('blink_referral_profile_search',{p_query:q,p_limit:20})
            .catch(()=>rpc('search_profiles_page',{p_query:q,p_limit:20,p_after_username:null,p_after_id:null}))
        : rpc('search_profiles_page',{p_query:q,p_limit:20,p_after_username:null,p_after_id:null});
      const [profiles,posts]=await Promise.all([
        profileResults,
        rpc('search_feed_page',{p_query:q,p_limit:30,p_before:null,p_before_id:null})
      ]);
      if(requestId!==state.searchRequestId||currentPath()!=='/search')return;
      const pmap=await fetchProfiles((posts||[]).map(x=>x.user_id));
      if(requestId!==state.searchRequestId||!document.getElementById('search-results'))return;
      out.innerHTML=`<h3>People</h3><div class="list">${(profiles||[]).map(p=>`<button type="button" class="card list-item" data-nav="/@${esc(p.username)}" data-ref-profile="${esc(p.id)}">${avatar(p.avatar_url,p.full_name)}<span class="grow"><span class="title">${esc(p.full_name||p.username)}${verifyMark(p)}</span><span class="sub">@${esc(p.username)} · ${esc(p.university||'Blink')}</span></span></button>`).join('')||'<p class="muted">No people found.</p>'}</div><h3>Posts</h3><div class="feed">${(posts||[]).map(x=>postCard(normalizeFeedItem(x,pmap))).join('')||'<p class="muted">No posts found.</p>'}</div>`;
      bindPostCards();
      if(typeof IntersectionObserver==='function'){
        const timers=new Map(),tracked=new Set();
        const observer=new IntersectionObserver(entries=>{
          for(const entry of entries){
            const element=entry.target;
            const eligible=entry.isIntersecting && entry.intersectionRatio>=0.6 &&
              document.visibilityState==='visible' && element.isConnected;
            if(!eligible){if(timers.has(element))clearTimeout(timers.get(element));timers.delete(element);continue;}
            if(tracked.has(element)||timers.has(element))continue;
            const timer=setTimeout(()=>{
              timers.delete(element);
              const rect=element.getBoundingClientRect();
              if(!element.isConnected || document.visibilityState!=='visible' ||
                 rect.height<=0 || rect.width<=0) return;
              const visible=Math.max(0,Math.min(rect.bottom,innerHeight)-Math.max(0,rect.top));
              if(visible/rect.height<0.6)return;
              const id=element.getAttribute('data-ref-profile');
              if(id){tracked.add(element);void rpc('blink_record_referral_impression',
                {p_profile:id,p_source:'search'}).catch(()=>{});}
            },750);
            timers.set(element,timer);
          }
        },{threshold:[0,0.6,1]});
        out.querySelectorAll('[data-ref-profile]').forEach(el=>observer.observe(el));
      }
      out.querySelectorAll('[data-ref-profile]').forEach(button=>
        button.addEventListener('click',()=>{
          const id=button.getAttribute('data-ref-profile');
          if(id)void rpc('blink_record_referral_discovery_open',
            {p_profile:id,p_source:'search'}).catch(()=>{});
        })
      );
    }catch(e){if(requestId===state.searchRequestId&&out.isConnected)out.innerHTML=`<div class="error">${esc(e.message)}</div>`;}
  }

  async function renderActivity(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<div class="spinner"></div>','Activity');bindCommon();
    try{
      const [notifications,activities]=await Promise.all([
        table('notifications',`user_id=eq.${encodeQ(uid())}&select=id,actor_id,type,post_id,text,sub_text,is_read,created_at,actor_is_vip,vip_priority&order=created_at.desc&limit=80`),
        table('activities',`recipient_id=eq.${encodeQ(uid())}&select=id,actor_id,activity_type,entity_type,entity_id,message,is_read,created_at&order=created_at.desc&limit=80`)
      ]);
      const rows=[...(notifications||[]).map(x=>({...x,kind:'notification'})),...(activities||[]).map(x=>({...x,text:x.message,kind:'activity'}))].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at));
      const profiles=await fetchProfiles(rows.map(x=>x.actor_id));if(!live())return;
      const list=rows.map(n=>{
        const p=profiles.get(n.actor_id)||{};
        const entity=String(n.entity_type||'').toLowerCase();
        let dest=n.post_id?`/post/${n.post_id}`:'';
        if(!dest&&n.entity_id&&(entity.includes('reel')||entity.includes('post')))dest=`/${entity.includes('reel')?'reel':'post'}/${n.entity_id}`;
        if(!dest&&p.username&&(String(n.type||n.activity_type||'').toLowerCase().includes('follow')||entity.includes('profile')))dest=`/@${p.username}`;
        return `<button type="button" class="card list-item notification ${n.is_read?'':'unread'}" data-activity="${esc(n.id)}" data-kind="${n.kind}" data-was-unread="${n.is_read?'0':'1'}" ${dest?`data-nav="${esc(dest)}"`:''}>${avatar(p.avatar_url,p.full_name)}<span class="grow"><span class="title">${n.actor_is_vip?'◆ ':''}${esc(n.text||n.type||n.activity_type||'Activity')}</span><span class="sub">${esc(n.sub_text||'')} ${ago(n.created_at)}</span></span></button>`;
      }).join('');
      ROOT.innerHTML=shell(`<div class="tabs"><button type="button" class="tab active">All</button><button type="button" class="tab" id="mark-read">Mark all read</button></div><div class="list">${list||'<div class="card empty"><h2>No activity yet</h2></div>'}</div>`,'Activity');
      bindCommon();
      document.querySelectorAll('[data-activity]').forEach(el=>el.addEventListener('click',()=>{
        if(el.dataset.wasUnread!=='1')return;
        el.dataset.wasUnread='0';el.classList.remove('unread');
        const id=el.dataset.activity,kind=el.dataset.kind;
        if(kind==='notification'){patch('notifications',`id=eq.${encodeQ(id)}&user_id=eq.${encodeQ(uid())}`,{is_read:true}).catch(()=>{});state.notificationsUnread=Math.max(0,state.notificationsUnread-1);}
        else patch('activities',`id=eq.${encodeQ(id)}&recipient_id=eq.${encodeQ(uid())}`,{is_read:true}).catch(()=>{});
      }));
      document.getElementById('mark-read').onclick=async()=>{
        try{await Promise.all([patch('notifications',`user_id=eq.${encodeQ(uid())}`,{is_read:true}),patch('activities',`recipient_id=eq.${encodeQ(uid())}`,{is_read:true})]);state.notificationsUnread=0;toast('Marked as read');renderActivity();}
        catch(e){toast(e.message);}
      };
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Activity');bindCommon();}
  }

  async function renderMessages(path){
    if(!requireAuth())return;const live=still();
    const parts=path.split('/').filter(Boolean),convId=parts[1],target=new URLSearchParams(location.search).get('user')||'';
    if(convId==='new'){renderNewMessage(target);return;}
    if(convId==='groups'){renderGroupInvitations();return;}
    if(convId){renderConversation(convId,target);return;}
    ROOT.innerHTML=shell('<div class="spinner"></div>','Messages');bindCommon();
    try{
      let summaries;
      try{summaries=await rpc('get_conversation_summaries_page',{p_limit:100,p_before:null,p_before_id:null});}
      catch{
        const unread=await rpc('get_my_unread_message_notifications',{p_limit:100});
        summaries=(unread||[]).map(m=>({conversation_id:m.conversation_id,partner_id:m.sender_id,partner_username:m.sender_username,partner_name:m.sender_name,partner_avatar:m.sender_avatar,last_message:m.content,last_message_at:m.created_at,unread_count:1}));
      }
      const byConversation=new Map();
      for(const row of summaries||[]){
        const key=String(row.conversation_id||'');if(!key)continue;
        const prev=byConversation.get(key);
        if(!prev)byConversation.set(key,{...row});
        else prev.unread_count=Math.max(Number(prev.unread_count||0),Number(row.unread_count||0));
      }
      const rows=[...byConversation.values()];
      let meta=new Map();
      if(rows.length){
        try{
          const ids=rows.map(x=>x.conversation_id).filter(Boolean);
          const list=await table('conversations',`id=in.(${ids.map(encodeQ).join(',')})&select=id,is_group,title,avatar_url`);
          meta=new Map((list||[]).map(x=>[String(x.id),x]));
        }catch{}
      }
      if(!live())return;const html=`<div style="display:flex;gap:8px;margin-bottom:12px"><button type="button" class="btn primary" id="new-message">New message</button><button type="button" class="btn" id="group-invites">Groups &amp; invitations</button></div><div class="list">${rows.map(m=>{
        const group=meta.get(String(m.conversation_id));
        const isGroup=!!group?.is_group;
        const title=isGroup?(group.title||'Group chat'):(m.partner_name||m.partner_username||'Blink user');
        const picture=isGroup?group.avatar_url:m.partner_avatar;
        const targetQuery=!isGroup&&m.partner_username?`?user=${encodeURIComponent(m.partner_username)}`:'';
        const unread=Number(m.unread_count||0);
        return `<button type="button" class="card list-item ${unread?'notification unread':''}" data-nav="/messages/${esc(m.conversation_id)}${targetQuery}">${avatar(picture,title)}<span class="grow"><span class="title">${esc(title)}${m.partner_online&&!isGroup?' <span class="online-dot" title="Online"></span>':''}</span><span class="sub">${esc(m.last_message||'No messages yet')} · ${ago(m.last_message_at)}</span></span>${unread?`<span class="badge">${fmt(unread)}</span>`:''}</button>`;
      }).join('')||'<div class="card empty"><h2>No conversations yet</h2><p>Start a conversation with a Blink user.</p></div>'}</div>`;
      ROOT.innerHTML=shell(html,'Messages');bindCommon();document.getElementById('new-message').onclick=()=>navigate('/messages/new');document.getElementById('group-invites').onclick=()=>navigate('/messages/groups');
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Messages');bindCommon();}
  }
  async function renderGroupInvitations(){
    if(!requireAuth())return;
    const html=`<section class="card" style="padding:16px"><h2>Invite classmates to a group</h2><p class="muted">Invited students choose whether to join. No automatic memberships.</p><form class="form-stack" id="invite-group-form"><label class="form-label">Group name</label><input name="title" class="field" maxlength="80" minlength="2" required><label class="form-label">Find students by username</label><input class="field" id="group-member-lookup" placeholder="Search @username" maxlength="40" autocomplete="off"><div id="group-member-results"></div><div id="group-selected" class="muted"></div><button class="btn primary" id="group-create" type="submit">Create group &amp; send invitations</button><p id="group-status" aria-live="polite"></p></form></section><section class="card" style="padding:16px;margin-top:12px"><h2>Your pending group invitations</h2><div id="group-pending"><div class="spinner"></div></div></section>`;
    ROOT.innerHTML=shell(html,'Groups & invitations');bindCommon();
    const selection=new Map(),input=document.getElementById('group-member-lookup');
    const suggestions=document.getElementById('group-member-results'),chosen=document.getElementById('group-selected');
    const status=document.getElementById('group-status');let seq=0,debounce;
    function updateSelected(){
      chosen.textContent=selection.size?'Selected ('+selection.size+'/30): '+[...selection.values()].map(x=>'@'+x.username).join(', '):'Select at least two different students.';
      document.getElementById('group-create').disabled=selection.size<2;
    }
    updateSelected();
    async function lookupMembers(){
      const token=++seq,query=input.value.trim().replace(/^@/,'');
      if(query.length<2){suggestions.textContent='';return;}
      try{
        const rows=await anonRpc('blink_referrer_suggestions',{p_query:query,p_limit:10,p_offset:0});
        if(token!==seq)return;
        suggestions.textContent='';
        (rows||[]).filter(person=>person.id!==uid()&&!selection.has(person.id)).slice(0,10).forEach(person=>{
          const b=document.createElement('button');b.type='button';b.className='btn ghost';
          b.textContent=(person.full_name||person.username)+' (@'+person.username+')'+(person.is_verified?' ✓':'');
          b.style.margin='4px';b.onclick=()=>{if(selection.size>=30){toast('Maximum 30 invitations');return;}selection.set(person.id,person);input.value='';suggestions.textContent='';updateSelected();};
          suggestions.append(b);
        });
      }catch{if(token===seq)suggestions.textContent='Student search is unavailable.';}
    }
    input.addEventListener('input',()=>{clearTimeout(debounce);debounce=setTimeout(lookupMembers,260);});
    document.getElementById('invite-group-form').onsubmit=async e=>{
      e.preventDefault();if(selection.size<2)return;
      const form=e.currentTarget,button=document.getElementById('group-create');button.disabled=true;status.textContent='Creating invitations…';
      try{
        const title=String(new FormData(form).get('title')||'').trim();
        await rpc('blink_create_invite_only_group',{p_title:title,p_member_ids:[...selection.keys()]});
        toast('Invitations sent. Students must accept to join.');navigate('/messages');
      }catch(error){status.textContent='Unable to create group: '+String(error.message||'Try again.').slice(0,140);button.disabled=false;}
    };
    const list=document.getElementById('group-pending');
    try{
      const rows=await rpc('blink_my_group_invitations',{p_limit:20});
      if(!list.isConnected)return;list.textContent='';
      if(!rows?.length)list.textContent='No pending invitations.';
      (rows||[]).forEach(item=>{
        const row=document.createElement('div');row.className='list-item';
        const title=document.createElement('strong');title.textContent=item.group_title||'Group invitation';
        const from=document.createElement('span');from.className='muted';from.textContent=' From @'+item.inviter_username;
        row.append(title,from);
        for(const [label,accept] of [['Accept',true],['Decline',false]]){
          const button=document.createElement('button');button.type='button';button.className='btn ghost';button.style.margin='4px';button.textContent=label;
          button.onclick=async()=>{button.disabled=true;try{
            const ok=await rpc('blink_respond_to_group_invite',{p_group_id:item.group_id,p_accept:accept});
            if(ok!==true)throw Error('Invitation is no longer valid');
            toast(accept?'Invitation accepted':'Invitation declined');row.remove();
          }catch(error){toast('Unable to respond to invitation.');button.disabled=false;}};
          row.append(button);
        }list.append(row);
      });
    }catch{list.textContent='Invitations are unavailable until the server update is deployed.';}
  }
  function renderNewMessage(prefill=''){
    ROOT.innerHTML=shell(`<div class="card" style="padding:15px"><form id="new-message-form" class="form-stack"><label class="form-label">Username</label><input class="field" name="username" maxlength="64" value="${esc(prefill)}" placeholder="@username" required autocomplete="off"><label class="form-label">Message</label><textarea class="field" name="text" maxlength="8000" placeholder="Write a message" required></textarea><button type="submit" class="btn primary">Send</button><div id="message-status"></div></form></div>`,'New message');bindCommon();
    document.getElementById('new-message-form').onsubmit=async e=>{
      e.preventDefault();const form=e.currentTarget,status=document.getElementById('message-status');
      const f=new FormData(form),username=String(f.get('username')||'').trim().replace(/^@/,''),text=String(f.get('text')||'').trim();
      if(!username||!text){status.innerHTML='<div class="error">Enter a username and message.</div>';return;}
      const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
      try{
        const rows=await rpc('send_message_v2',{p_receiver_username:username,p_content:text});
        const r=Array.isArray(rows)?rows[0]:rows;toast('Message sent');
        if(r?.conversation_id)navigate(`/messages/${r.conversation_id}?user=${encodeURIComponent(username)}`);else navigate('/messages');
      }catch(err){status.innerHTML=`<div class="error">${esc(err.message)}</div>`;submit.disabled=false;}
    };
  }
  async function renderConversation(id,target=''){const live=still();
    ROOT.innerHTML=shell('<div class="spinner"></div>','Chat');bindCommon();
    try{
      let partner=String(target||'').trim().replace(/^@/,'');
      let conversationMeta=null;
      try{const meta=await table('conversations',`id=eq.${encodeQ(id)}&select=id,is_group,title,avatar_url&limit=1`);conversationMeta=meta?.[0]||null;}catch{}
      const isGroup=!!conversationMeta?.is_group;
      if(!partner&&!isGroup){
        try{const summaries=await rpc('get_conversation_summaries_page',{p_limit:100,p_before:null,p_before_id:null});partner=String((summaries||[]).find(x=>String(x.conversation_id)===String(id))?.partner_username||'');}catch{}
      }
      if(partner&&!isGroup){await rpc('mark_conversation_read',{p_partner_username:partner}).catch(()=>{});refreshUnreadCount();}
      else if(isGroup){patch('conversation_participants',`conversation_id=eq.${encodeQ(id)}&user_id=eq.${encodeQ(uid())}`,{last_read_at:new Date().toISOString()}).catch(()=>{});}
      const rows=await rpc('get_conversation_messages_page',{p_conversation_id:id,p_limit:100,p_before:null,p_before_id:null});
      const profiles=await fetchProfiles((rows||[]).map(x=>x.sender_id));
      const chatPreviewPrivacy = await readChatPreviewPrivacy();if(!live())return;
      const title=isGroup?(conversationMeta?.title||'Group chat'):(partner?`@${partner}`:'Conversation');
      const messages=(rows||[]).slice().reverse().map(m=>{
        const mine=m.sender_id===uid(),p=profiles.get(m.sender_id)||{},viewOnce=!!(m.image_view_once||m.view_once),attachment=viewOnce?'':safeUrl(m.media_url);
        return `<div class="bubble ${mine?'me':''}">${esc(m.deleted_for_everyone?'Message deleted':viewOnce?'View-once private media':m.content||'')}${!m.deleted_for_everyone&&!viewOnce&&chatPreviewPrivacy.allowed?richLinkCard(m.content):''}${attachment?`<br><a href="${esc(attachment)}" target="_blank" rel="noopener noreferrer">Attachment</a>`:''}<small>${mine?'You':`@${esc(p.username||'user')}`} · ${ago(m.created_at)}${m.edited_at?' · edited':''}</small></div>`;
      }).join('');
      const html=`<div class="card chat-window"><div class="post-head"><button type="button" class="btn small-btn" data-nav="/messages">← Messages</button><div class="grow"><strong>${esc(title)}</strong><span class="handle">Synced with Blink</span></div><label class="small" style="display:flex;align-items:center;gap:6px"><input type="checkbox" id="chat-link-preview" ${chatPreviewPrivacy.allowed?'checked':''} ${!chatPreviewPrivacy.available?'disabled':''}>Link previews</label></div><div class="chat-messages" id="chat-messages">${messages||'<div class="muted small">No messages yet.</div>'}</div><form id="chat-form" class="chat-compose"><input class="field" name="text" maxlength="8000" placeholder="Message" required autocomplete="off"><button type="submit" class="btn primary">Send</button></form></div>`;
      ROOT.innerHTML=shell(html,title);bindCommon();
      document.getElementById('chat-link-preview')?.addEventListener('change',async event=>{
        const input=event.currentTarget, desired=input.checked;
        input.disabled=true;
        try {
          await saveChatPreviewPrivacy(chatPreviewPrivacy,desired);
          renderConversation(id,target);
        } catch(error) {
          input.checked=!desired;toast(error.message);input.disabled=false;
        }
      });
      const scroll=document.getElementById('chat-messages');scroll.scrollTop=scroll.scrollHeight;
      document.getElementById('chat-form').onsubmit=async e=>{
        e.preventDefault();const form=e.currentTarget,text=String(new FormData(form).get('text')||'').trim();if(!text){return;}
        const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
        try{
          if(isGroup){
            await rpc('send_group_message',{p_conversation_id:id,p_content:text});
          }else{
            if(!partner)throw new Error('Could not identify the conversation partner.');
            await rpc('send_message_v2',{p_receiver_username:partner,p_content:text});
          }
          form.reset();renderConversation(id,partner);
        }catch(err){toast(err.message);submit.disabled=false;}
      };
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Chat');bindCommon();}
  }

  async function renderMarket(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<div class="spinner"></div>','Marketplace');bindCommon();
    try{
      const q=new URLSearchParams(location.search).get('q')||'';
      const rows=await rpc('get_ranked_market_page',{p_query:q||null,p_category:null,p_min_price:null,p_max_price:null,p_limit:40,p_cursor_score:null,p_cursor_created_at:null,p_cursor_id:null,p_as_of:null});
      if(!live())return;const items=(rows||[]).map(r=>r.item||r);
      const cards=items.map(i=>{
        const image=safeUrl((i.image_urls||[])[0]||i.image_url);
        const seller=String(i.seller_username||'').trim().replace(/^@/,'');
        const action=seller?`<button type="button" class="btn primary" data-message-seller="${esc(seller)}">Message seller</button>`:'<button type="button" class="btn" disabled>Seller unavailable</button>';
        return `<article class="card market-card">${image?`<img src="${esc(image)}" alt="${esc(i.title||'Marketplace item')}" loading="lazy">`:''}<h3>${esc(i.title||'Item')}</h3><div class="price">${money(i.price,i.currency)}</div><p class="muted small">${esc(i.condition||'')} · ${esc(i.location||i.university||'')}</p><p>${esc((i.description||'').slice(0,300))}</p>${richLinkCard(i.description||'')}${action}</article>`;
      }).join('');
      ROOT.innerHTML=shell(`<form id="market-search" style="display:flex;gap:8px;margin-bottom:12px"><input class="field" name="q" maxlength="100" value="${esc(q)}" placeholder="Search marketplace" autocomplete="off"><button class="btn">Search</button></form><div class="market-grid">${cards||'<div class="card empty"><h2>No items found</h2></div>'}</div>`,'Marketplace');
      bindCommon();
      document.getElementById('market-search').onsubmit=e=>{e.preventDefault();const v=String(new FormData(e.currentTarget).get('q')||'').trim();const u=new URL(location.href);if(v)u.searchParams.set('q',v);else u.searchParams.delete('q');history.pushState({},'',u);renderMarket();};
      document.querySelectorAll('[data-message-seller]').forEach(x=>x.onclick=()=>navigate(`/messages/new?user=${encodeURIComponent(x.dataset.messageSeller)}`));
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Marketplace');bindCommon();}
  }

  async function renderConnect(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<div class="spinner"></div>','Connect');bindCommon();
    try{
      const allowed=new Set(['all','roommate','mentor','reading_mate','housing']);
      const requested=new URLSearchParams(location.search).get('mode')||'all';
      const mode=allowed.has(requested)?requested:'all';
      const rows=await rpc('get_ranked_connect_opportunities',{p_mode:mode,p_limit:40,p_cursor_score:null,p_cursor_kind:null,p_cursor_id:null,p_as_of:null});
      if(!live())return;const cards=(rows||[]).map(r=>{
        const p=r.payload||{},profile=p.profile||{};
        const receiverId=String(profile.id||'');
        const title=p.title||p.headline||profile.full_name||profile.username||p.name||'Connect opportunity';
        const description=p.description||p.bio||'';
        const detail=[profile.university||p.university,profile.department||p.department,p.location||p.preferred_location].filter(Boolean).join(' · ');
        const action=receiverId?`<button type="button" class="btn small-btn" data-connect-receiver="${esc(receiverId)}">Connect</button>`:'<button type="button" class="btn small-btn" disabled>Unavailable</button>';
        return `<article class="card connect-card"><span class="handle">${esc(r.kind||'Connect')}</span><h3>${esc(title)}</h3><p>${esc(String(description).slice(0,260))}</p>${richLinkCard(description)}<div class="muted small">${esc(detail)}</div>${action}</article>`;
      }).join('');
      ROOT.innerHTML=shell(`<div class="tabs">${['all','roommate','mentor','reading_mate','housing'].map(m=>`<button type="button" class="tab ${mode===m?'active':''}" data-connect-mode="${m}">${m.replaceAll('_',' ')}</button>`).join('')}</div><div class="connect-grid">${cards||'<div class="card empty"><h2>No matches yet</h2></div>'}</div>`,'Connect');
      bindCommon();
      document.querySelectorAll('[data-connect-mode]').forEach(x=>x.onclick=()=>{const u=new URL(location.href);u.searchParams.set('mode',x.dataset.connectMode);history.pushState({},'',u);renderConnect();});
      document.querySelectorAll('[data-connect-receiver]').forEach(x=>x.onclick=async()=>{
        const button=x;button.disabled=true;
        try{await rpc('send_connection_request',{p_receiver_id:button.dataset.connectReceiver});button.textContent='Request sent';toast('Connection request sent');}
        catch(e){toast(e.message);button.disabled=false;}
      });
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Connect');bindCommon();}
  }

  async function renderGames(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<div class="spinner"></div>','Games');bindCommon();
    try{
      const [dash,board]=await Promise.all([rpc('get_game_dashboard',{}),rpc('get_game_leaderboard',{p_period:'all_time',p_scope:'global',p_limit:50})]);
      if(!live())return;const leaderboard=Array.isArray(board)?board:(board?.rows||board?.leaderboard||[]);
      ROOT.innerHTML=shell(`<div class="game-grid"><div class="card game-card"><h3>Your game profile</h3><p class="price">${fmt(dash?.score||dash?.profile?.score||0)} points</p><p class="muted">Coins ${fmt(dash?.coins||dash?.profile?.coins||0)} · Streak ${fmt(dash?.streak||dash?.profile?.streak||0)}</p></div><div class="card game-card"><h3>Play on Android</h3><p class="muted">Your scores and rewards remain synced to the same Blink backend.</p><a class="btn primary download-app" href="${APK_URL}">Download latest BLINK app</a></div></div><h3>Leaderboard</h3><div class="list">${leaderboard.map((u,i)=>`<div class="card list-item"><strong>#${i+1}</strong>${avatar(u.avatar_url,u.full_name||u.username)}<span class="grow"><span class="title">${esc(u.full_name||u.username||'Player')}</span><span class="sub">${fmt(u.score||u.points)} points</span></span></div>`).join('')||'<div class="card empty">Leaderboard is empty.</div>'}</div>`,'Games');
      bindCommon();
    }catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Games');bindCommon();}
  }

  async function renderScheduled(){if(!requireAuth())return;const live=still();ROOT.innerHTML=shell('<div class="spinner"></div>','Scheduled posts');bindCommon();try{const rows=await table('scheduled_feed_posts','select=id,payload,scheduled_for,status,error_message,published_post_id,created_at&order=scheduled_for.desc&limit=100');if(!live())return;ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Schedule a post</h3><form id="schedule-form" class="form-stack"><textarea class="field" name="text" placeholder="Post text" required></textarea><input class="field" name="when" type="datetime-local" required><input class="field" name="tags" placeholder="Tags, comma separated"><label class="checkbox"><input type="checkbox" name="reel"> Reel</label><button class="btn primary">Schedule</button><div id="schedule-msg"></div></form></div><div class="list">${(rows||[]).map(s=>`<div class="card schedule-row"><div><strong>${esc((s.payload?.text||'Scheduled post').slice(0,160))}</strong><div class="muted small">${new Date(s.scheduled_for).toLocaleString()} · ${esc(s.status)}</div>${s.error_message?`<div class="error">${esc(s.error_message)}</div>`:''}</div><div class="schedule-actions">${s.status==='pending'||s.status==='failed'?`<button class="btn small-btn" data-publish-scheduled="${esc(s.id)}">Publish now</button><button class="btn danger small-btn" data-cancel-scheduled="${esc(s.id)}">Cancel</button>`:''}</div></div>`).join('')||'<div class="card empty"><h2>No scheduled posts</h2></div>'}</div>`,'Scheduled posts');bindCommon();document.getElementById('schedule-form').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),when=new Date(f.get('when'));if(Number.isNaN(when.valueOf())||when<=new Date()){toast('Choose a future time.');return;}const payload={text:String(f.get('text')).trim(),faculty:state.profile?.faculty||'',video_url:null,images:[],tags:String(f.get('tags')||'').split(',').map(x=>x.trim()).filter(Boolean),is_reel:f.get('reel')==='on',audience:'Everyone',category:'Campus Life',location:null,link_url:null,allow_comments:true,hide_likes:false,is_pinned:false,is_disappearing:false,audio_title:null,alt_text:null};try{await rpc('schedule_feed_post',{p_payload:payload,p_scheduled_for:when.toISOString()});toast('Post scheduled');renderScheduled();}catch(err){document.getElementById('schedule-msg').innerHTML=`<div class="error">${esc(err.message)}</div>`;}};document.querySelectorAll('[data-cancel-scheduled]').forEach(x=>x.onclick=async()=>{try{await rpc('cancel_scheduled_feed_post',{p_schedule_id:x.dataset.cancelScheduled});toast('Schedule cancelled');renderScheduled();}catch(e){toast(e.message);}});document.querySelectorAll('[data-publish-scheduled]').forEach(x=>x.onclick=async()=>{try{const id=await rpc('publish_scheduled_feed_post_now',{p_schedule_id:x.dataset.publishScheduled});toast('Published now');if(id)navigate(`/post/${String(id).replace(/"/g,'')}`);else renderScheduled();}catch(e){toast(e.message);}});}catch(e){if(!live())return;ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Scheduled posts');bindCommon();}}

  async function renderSettings(path){
    if(!requireAuth())return;const live=still();
    if(path==='/settings/password'){
      ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Change password</h3><form id="password-form" class="form-stack"><input class="field" type="password" name="password" minlength="8" autocomplete="new-password" placeholder="New password" required><input class="field" type="password" name="confirm" minlength="8" autocomplete="new-password" placeholder="Confirm password" required><button type="submit" class="btn primary">Update password</button><div id="password-msg"></div></form></div>`,'Security');
      bindCommon();
      document.getElementById('password-form').onsubmit=async e=>{
        e.preventDefault();const form=e.currentTarget,f=new FormData(form),password=String(f.get('password')||''),confirm=String(f.get('confirm')||''),msg=document.getElementById('password-msg');
        if(password!==confirm){msg.innerHTML='<div class="error">Passwords do not match.</div>';return;}
        if(password.length<8||!/[A-Z]/.test(password)||!/[a-z]/.test(password)||!/[0-9]/.test(password)||!/[^A-Za-z0-9\s]/.test(password)){msg.innerHTML='<div class="error">Use at least 8 characters with uppercase, lowercase, a number and a symbol.</div>';return;}
        const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
        try{await updatePassword(password);msg.innerHTML='<div class="success">Password updated.</div>';form.reset();}
        catch(err){msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;}
        finally{submit.disabled=false;}
      };
      return;
    }
    await loadMyProfile();if(!live())return;const p=state.profile||{};
    const chatPreviewPrivacy = await readChatPreviewPrivacy();if(!live())return;
    let missionPayload={missions:[]};
    try{missionPayload=await rpc('get_my_daily_missions',{})||missionPayload;}catch(err){console.warn('daily missions',err);}
    if(!live())return;const missions=Array.isArray(missionPayload?.missions)?missionPayload.missions:[];
    const missionHtml=missions.map(m=>{
      const progress=Math.max(0,Number(m.progress)||0),target=Math.max(1,Number(m.target)||1),pct=Math.min(100,Math.round(progress*100/target));
      const status=m.claimed?'Claimed':m.claimable?'Claim':'In progress';
      return `<div class="card" style="padding:12px;margin-top:9px"><div style="display:flex;gap:10px;justify-content:space-between;align-items:flex-start"><div class="grow"><strong>${esc(m.title||'Daily mission')}</strong><div class="muted small">${esc(m.description||'')}</div></div><span class="small"><strong>${Math.min(progress,target)}/${target}</strong></span></div><div style="height:6px;border-radius:99px;overflow:hidden;background:var(--surface-2,#e5e7eb);margin:9px 0"><div style="height:100%;width:${pct}%;background:currentColor"></div></div><div style="display:flex;align-items:center;justify-content:space-between;gap:8px"><span class="muted small">+${fmt(m.coin_reward||0)} coins · +${fmt(m.xp_reward||0)} XP</span><button type="button" class="btn small-btn ${m.claimable?'primary':'ghost'}" data-claim-mission="${esc(m.key||'')}" ${m.claimable?'':'disabled'}>${status}</button></div></div>`;
    }).join('');
    ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Edit profile</h3><form id="profile-form" class="form-stack"><div class="form-grid"><input class="field" name="name" maxlength="100" value="${esc(p.full_name||'')}" placeholder="Full name"><input class="field" name="username" maxlength="30" value="${esc(p.username||'')}" placeholder="Username" autocomplete="off"></div><textarea class="field" name="bio" maxlength="500" placeholder="Bio">${esc(p.bio||'')}</textarea>${ownerThemePicker(p.profile_theme)}<input class="field" name="headline" maxlength="160" value="${esc(p.professional_headline||'')}" placeholder="Professional headline"><div class="form-grid"><input class="field" name="university" maxlength="160" value="${esc(p.university||'')}" placeholder="University"><input class="field" name="faculty" maxlength="120" value="${esc(p.faculty||'')}" placeholder="Faculty"><input class="field" name="department" maxlength="120" value="${esc(p.department||'')}" placeholder="Department"><input class="field" name="level" maxlength="80" value="${esc(p.academic_level||'')}" placeholder="Academic level"></div><button type="submit" class="btn primary">Save profile</button><div id="profile-msg"></div></form></div><div class="card settings-section"><h3>Profile media</h3><div class="form-grid"><label class="btn" style="text-align:center">Change avatar<input class="hidden" id="avatar-file" type="file" accept="image/*"></label><label class="btn" style="text-align:center">Change cover<input class="hidden" id="cover-file" type="file" accept="image/*"></label></div></div><div class="card settings-section"><h3>Security</h3><button type="button" class="btn" data-nav="/settings/password">Change password</button></div><div class="card settings-section"><h3>Message link previews</h3><label style="display:flex;align-items:center;gap:10px"><input type="checkbox" id="settings-link-preview" ${chatPreviewPrivacy.allowed?'checked':''} ${!chatPreviewPrivacy.available?'disabled':''}>Automatically show public link previews in private conversations</label><p class="muted small">This setting is synced with Android and Windows. Turning it off stops new preview requests.</p></div><div class="card settings-section"><h3>Creator snapshot</h3><div class="stats-grid"><div class="stat"><strong>${fmt(p.follower_count)}</strong><span>Followers</span></div><div class="stat"><strong>${fmt(p.posts_count)}</strong><span>Posts</span></div><div class="stat"><strong>${fmt(p.points)}</strong><span>Points</span></div><div class="stat"><strong>Lv. ${fmt(p.xp_level||1)}</strong><span>${fmt(p.total_xp||0)} XP</span></div></div></div><div class="card settings-section"><h3>Daily Missions</h3><p class="muted small">Complete meaningful activity for up to 20 Blink Coins + 80 XP per day.</p>${missionHtml||'<div class="muted small">Missions are syncing. Try again shortly.</div>'}</div>`,'Settings');
    bindCommon();
    document.getElementById('settings-link-preview')?.addEventListener('change',async event=>{
      const input=event.currentTarget, desired=input.checked;
      input.disabled=true;
      try { await saveChatPreviewPrivacy(chatPreviewPrivacy,desired);toast('Link preview setting saved'); }
      catch(error) { input.checked=!desired;toast(error.message); }
      finally { input.disabled=false; }
    });
    document.querySelectorAll('[data-claim-mission]').forEach(button=>button.onclick=async()=>{
      const key=button.dataset.claimMission;if(!key)return;button.disabled=true;button.textContent='Claiming…';
      try{const result=await rpc('claim_daily_mission',{p_mission_key:key});toast(`+${fmt(result?.coin_reward||0)} Blink Coins · +${fmt(result?.xp_reward||0)} XP`);await loadMyProfile();renderSettings('/settings/profile');}
      catch(err){toast(err.message);button.disabled=false;button.textContent='Claim';}
    });
    document.getElementById('profile-form').onsubmit=async e=>{
      e.preventDefault();const form=e.currentTarget,f=new FormData(form),msg=document.getElementById('profile-msg');
      const username=String(f.get('username')||'').trim().toLowerCase().replace(/^@/,'');
      if(!/^[a-z0-9][a-z0-9._-]{1,29}$/.test(username)){msg.innerHTML='<div class="error">Username must be 2–30 lowercase letters, numbers, dots, dashes or underscores.</div>';return;}
      const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
      try{
        await patch('profiles',`id=eq.${encodeQ(uid())}`,{
          full_name:String(f.get('name')||'').trim(),username,handle:username,bio:String(f.get('bio')||'').trim(),
          professional_headline:String(f.get('headline')||'').trim(),profile_theme:themeId(f.get('profile_theme')),university:String(f.get('university')||'').trim(),
          faculty:String(f.get('faculty')||'').trim(),department:String(f.get('department')||'').trim(),academic_level:String(f.get('level')||'').trim()
        });
        await loadMyProfile();msg.innerHTML='<div class="success">Profile saved.</div>';
      }catch(err){
        const message=/duplicate|unique/i.test(err.message||'')?'That username is already taken.':err.message;
        msg.innerHTML=`<div class="error">${esc(message)}</div>`;
      }finally{submit.disabled=false;}
    };
    document.getElementById('avatar-file').onchange=async e=>{
      let file=e.target.files[0];if(!file)return;
      try{validateMediaFile(file,{allowVideo:false,imageMaxMb:8});
        file=await blinkEditSelectedPhoto(file);
        const url=await uploadPublicFile('avatars',file,'avatar');await patch('profiles',`id=eq.${encodeQ(uid())}`,{avatar_url:url});await loadMyProfile();toast('Avatar updated');renderSettings('/settings/profile');}
      catch(err){e.target.value='';toast(err.message);}
    };
    document.getElementById('cover-file').onchange=async e=>{
      let file=e.target.files[0];if(!file)return;
      try{validateMediaFile(file,{allowVideo:false,imageMaxMb:12});file=await blinkEditSelectedPhoto(file);const url=await uploadPublicFile('covers',file,'cover');await patch('profiles',`id=eq.${encodeQ(uid())}`,{cover_photo_url:url,cover_photo:url});await loadMyProfile();toast('Cover updated');renderSettings('/settings/profile');}
      catch(err){e.target.value='';toast(err.message);}
    };
  }

  async function renderAI(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Blink AI</h3><p class="muted small">Uses the same authenticated Blink AI backend as Android.</p><div id="ai-thread" class="form-stack" style="margin:14px 0"></div><form id="ai-form" class="chat-compose"><input class="field" name="message" maxlength="8000" autocomplete="off" placeholder="Ask Blink AI…" required><button type="submit" class="btn primary">Send</button></form><div id="ai-status"></div></div>`,'Blink AI');
    bindCommon();let previous=null;
    document.getElementById('ai-form').onsubmit=async e=>{
      e.preventDefault();const form=e.currentTarget,text=String(new FormData(form).get('message')||'').trim(),thread=document.getElementById('ai-thread'),status=document.getElementById('ai-status');
      if(!text)return;
      const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
      thread.insertAdjacentHTML('beforeend',`<div class="bubble me">${esc(text)}</div>`);form.reset();status.innerHTML='<span class="muted">Blink AI is thinking…</span>';
      try{
        let data=null;
        for(const endpoint of ['blink-ai']){
          const request=async()=>fetchWithTimeout(`${SUPABASE_URL}/functions/v1/${endpoint}`,{method:'POST',headers:{apikey:KEY,Authorization:`Bearer ${token()}`,'Content-Type':'application/json',Accept:'application/json'},body:JSON.stringify({message:text,use_personal_context:true,use_web_search:true,mode:'fast',tone:'balanced',response_length:'medium',temporary_chat:false,attachments:[],...(previous?{previous_interaction_id:previous}:{})})},45000);
          let res=await request();
          if(res.status===401&&state.session?.refresh_token&&await refreshSession().catch(()=>false))res=await request();
          if(res.ok){data=await res.json();break;}
          if(![404,500,502,503].includes(res.status)){const er=await parseJsonSafe(res);throw new Error(er?.error||er?.message||`Blink AI failed (${res.status})`);}
        }
        if(!data?.text)throw new Error('Blink AI returned no answer.');
        previous=data.interaction_id||previous;
        const links=(Array.isArray(data.sources)?data.sources:[]).map(s=>{const url=safeUrl(s.url);return url?`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${esc(s.title||'Source')}</a>`:'';}).filter(Boolean);
        thread.insertAdjacentHTML('beforeend',`<div class="bubble">${esc(data.text)}${links.length?`<small>${links.join(' · ')}</small>`:''}</div>`);
        status.innerHTML='';thread.lastElementChild?.scrollIntoView({block:'nearest'});
      }catch(err){status.innerHTML=`<div class="error">${esc(err.message)}</div>`;}
      finally{submit.disabled=false;}
    };
  }

  function updateRoutePrivacy(path){
    const publicHome=path==='/'||path==='';
    const robots=document.querySelector('meta[name="robots"]');
    if(robots)robots.setAttribute('content',publicHome?'index,follow,max-image-preview:large':'noindex,nofollow');
    const referrer=document.querySelector('meta[name="referrer"]');
    if(referrer)referrer.setAttribute('content',publicHome?'strict-origin-when-cross-origin':'no-referrer');
  }

  async function renderSiteAnalytics(){
    if(!requireAuth())return;const live=still();
    ROOT.innerHTML=shell('<section class="card"><h2>Website analytics</h2><p class="muted">Loading aggregate public website counts…</p></section>','Website analytics');
    bindCommon();
    try{
      const result=await rpc('get_blink_site_metrics',{p_days:30});
      const entries=Array.isArray(result?.records)?result.records:[];
      const views=entries.filter(x=>x.kind==='view').reduce((n,x)=>n+Number(x.count||0),0);
      const clicks=entries.filter(x=>x.kind==='download').reduce((n,x)=>n+Number(x.count||0),0);
      const byPage=['home','about','features','download','faq','privacy'].map(page=>{
        const pageRows=entries.filter(x=>x.page===page);
        const impressions=pageRows.filter(x=>x.kind==='view').reduce((n,x)=>n+Number(x.count||0),0);
        const downloads=pageRows.filter(x=>x.kind==='download').reduce((n,x)=>n+Number(x.count||0),0);
        return `<tr><th scope="row">${esc(page==='home'?'Homepage':page.charAt(0).toUpperCase()+page.slice(1))}</th><td>${fmt(impressions)}</td><td>${fmt(downloads)}</td></tr>`;
      }).join('');
      const html=`<section class="card" aria-labelledby="site-metrics-title">
        <h2 id="site-metrics-title">Public website analytics · Last 30 days</h2>
        <p class="muted">Anonymous aggregate events only. Counts may be approximate and do not represent unique visitors or successful APK installations.</p>
        <div class="stats-grid"><div class="stat"><strong>${fmt(views)}</strong><span>Public page views</span></div><div class="stat"><strong>${fmt(clicks)}</strong><span>APK button taps</span></div></div>
        <div style="overflow-x:auto;margin-top:20px"><table class="table"><caption>Public website counts by page · ${esc(result?.timezone||'Africa/Lagos')}</caption><thead><tr><th scope="col">Page</th><th scope="col">Views</th><th scope="col">APK taps</th></tr></thead><tbody>${byPage}</tbody></table></div>
        ${entries.length?'':'<p class="muted">No events recorded. Collection remains disabled until privacy and production deployment are approved.</p>'}
        <p class="muted">Private accounts, messages, searches and selected campuses are not included. No visitor IDs or cookies are used for these aggregate counters.</p>
      </section>`;
      if(!live())return;ROOT.innerHTML=shell(html,'Website analytics');
      bindCommon();
    }catch{
      if(!live())return;ROOT.innerHTML=shell('<div class="card empty"><h2>Admin access required</h2><p>Website analytics are available only to authorized BLINK administrators after the database migration is deployed.</p></div>','Website analytics');
      bindCommon();
    }
  }

  async function render(path=currentPath()){
    updateRoutePrivacy(path);
    for(const timer of state.qualifiedTimers.values())clearTimeout(timer);
    state.qualifiedTimers.clear();
    state.viewObserver?.disconnect();state.viewObserver=null;
    state.mediaObserver?.disconnect();state.mediaObserver=null;
    state.searchRequestId++;
    const contestInvite = path.match(/^\/contest\/([0-9a-f-]{36})\/([0-9a-f-]{36})\/?$/i);
    if (contestInvite && window.BlinkContestInvite?.render?.(ROOT, contestInvite[1], contestInvite[2])) return;
    if(window.BlinkWebParity?.render?.(path))return;
    if(path==='/'||path==='')return renderLanding();
    const invited=path.match(/^\/invite\/([a-z0-9._-]{2,30})\/?$/i);
    if(invited)return renderReferralInvitation(invited[1]);
    if(path==='/login')return renderLogin();
    if(path==='/site-stats')return renderSiteAnalytics();
    if(path.startsWith('/@'))return renderPublicProfile(safeDecodeUri(path.slice(2)));
    const legacy=path.match(/^\/profile\/([^/]+)$/);if(legacy)return renderPublicProfile(safeDecodeUri(legacy[1]));
    const content=path.match(/^\/(post|reel)\/([0-9a-f-]+)$/i);if(content)return renderPublicContent(content[1].toLowerCase(),content[2]);
    if(path==='/feed')return renderFeed(path);
    if(path==='/reels')return renderReels();
    if(path==='/search')return renderSearch();
    if(path.startsWith('/messages'))return renderMessages(path);
    if(path==='/activity')return renderActivity();
    if(path==='/market')return renderMarket();
    if(path==='/connect')return renderConnect();
    if(path==='/games')return renderGames();
    if(path==='/scheduled')return renderScheduled();
    if(path.startsWith('/settings'))return renderSettings(path);
    if(path==='/ai')return renderAI();
    ROOT.innerHTML=`<div class="auth-wrap"><div class="card empty"><h2>Page not found</h2><p>That Blink link does not exist.</p><button type="button" class="btn" data-nav="/">Go home</button></div></div>`;bindCommon();
  }

  function scheduleServiceWorkerRegistration(){
    if(!('serviceWorker' in navigator) || location.protocol!=='https:')return;
    const register=()=>navigator.serviceWorker.register(routeHref('/sw.js')).catch(()=>{});
    const whenIdle=()=>{
      if('requestIdleCallback' in window)window.requestIdleCallback(register,{timeout:4000});
      else window.setTimeout(register,1500);
    };
    if(document.readyState==='complete')whenIdle();
    else window.addEventListener('load',whenIdle,{once:true});
  }

  async function init(){
    if(!SUPABASE_URL||!KEY){ROOT.innerHTML='<div class="auth-wrap"><div class="card empty"><h2>Web configuration missing</h2></div></div>';return;}
    if(relayDesktopOAuth())return;
    try{await bootstrapSession();}catch(e){console.warn(e);} await render(currentPath());
    scheduleServiceWorkerRegistration();
  }

  init();
})();

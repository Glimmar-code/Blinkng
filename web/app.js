(() => {
  'use strict';

  const C = window.BLINK_WEB_CONFIG || {};
  const SUPABASE_URL = String(C.supabaseUrl || '').replace(/\/$/, '');
  const KEY = C.publishableKey || '';
  const PREVIEW_BASE = location.hostname.endsWith('github.io') ? '/Blinkng' : '';
  const ROOT = document.getElementById('app');
  const MODALS = document.getElementById('modal-root');
  const toastEl = document.getElementById('toast');
  const SESSION_KEY = 'blink_web_session_v3';
  const DRAFT_KEY = 'blink_web_post_draft_v2';
  const VISITOR_KEY = 'blink_web_visitor_v2';
  const PARITY_PREF_KEY = 'blink_web_parity_prefs_v1';
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
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(url, {...options, signal: options.signal || controller.signal});
    } catch (error) {
      if (error?.name === 'AbortError') throw new Error('Request timed out. Check your connection and try again.');
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  function toast(message, ms=2400){ toastEl.textContent=message; toastEl.classList.add('show'); clearTimeout(toast.t); toast.t=setTimeout(()=>toastEl.classList.remove('show'),ms); }
  function setTitle(title){ state.currentTitle=title || 'Blink'; document.title = title && title !== 'Blink' ? `${title} · Blink` : 'Blink'; }
  function icon(name){ const map={home:'⌂',reels:'▶',search:'⌕',messages:'✉',activity:'♡',market:'▣',connect:'◎',games:'◇',scheduled:'◷',settings:'⚙',ai:'✦',create:'＋'}; return map[name]||'•'; }
  function avatar(url, name='', cls='avatar-sm'){ const src=safeUrl(url); return src ? `<img class="${cls}" src="${esc(src)}" alt="${esc(name)}" loading="lazy">` : `<div class="${cls} avatar-fallback">${esc((name||'B').trim().charAt(0).toUpperCase()||'B')}</div>`; }
  function verifyMark(p){ const badge=String(p?.verification_badge||p?.verificationBadge||'').toLowerCase(); const verified=p?.is_verified ?? p?.isVerified; return verified ? `<span class="verified" title="Verified">●</span>${badge.includes('gold')?'<span class="vip" title="Gold verified">★</span>':''}`:''; }
  function vipMark(p){ const until=p?.blink_vip_until; return until && new Date(until)>new Date() ? '<span class="vip" title="Blink VIP">◆</span>':''; }

  function loadSession(){ try { const raw=storageGet(SESSION_KEY,''); if(!raw)return null; const s=JSON.parse(raw); if(!s?.access_token)return null; return s; } catch { return null; } }
  function saveSession(s){ state.session=s; if(s)storageSet(SESSION_KEY,JSON.stringify(s)); else storageRemove(SESSION_KEY); }
  function clearSession(){ saveSession(null); state.profile=null; state.following=new Set(); state.notificationsUnread=0; }

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
        if(res.status===400||res.status===401)clearSession();
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

  async function bootstrapSession(){
    state.session=loadSession();
    const hash=new URLSearchParams(location.hash.replace(/^#/,''));
    if(hash.get('access_token')){
      const access_token=hash.get('access_token'); const refresh_token=hash.get('refresh_token'); const expires_in=Number(hash.get('expires_in')||3600); const type=hash.get('type');
      saveSession({access_token,refresh_token,expires_in,token_type:'bearer',user:null}); history.replaceState({},'',location.pathname+location.search);
      if(type==='recovery'){ navigate('/settings/password',true); return; }
    }
    if(!state.session)return;
    try{
      const user=await api('/auth/v1/user',{auth:true}); state.session.user=user; saveSession(state.session);
      await loadMyProfile(); await loadFollowing(); await refreshUnreadCount();
    }catch(e){ console.warn('session bootstrap',e); clearSession(); }
  }

  async function loadMyProfile(){ if(!uid())return null; const rows=await table('profiles',`id=eq.${encodeQ(uid())}&select=id,username,full_name,avatar_url,cover_photo_url,bio,professional_headline,university,faculty,department,academic_level,posts_count,follower_count,following_count,is_verified,verification_badge,blink_vip_until,daily_streak,points&limit=1`); state.profile=Array.isArray(rows)?rows[0]||null:null; return state.profile; }
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
    return `<div class="app-shell">
      <aside class="sidebar">
        <button class="nav-btn brand" data-nav="/feed"><img class="mark" src="blink-logo.png" alt="" aria-hidden="true"><span>Blink</span></button>
        ${navItem('/feed','home','Home')}${navItem('/reels','reels','Reels')}${navItem('/search','search','Search')}${navItem('/messages','messages','Messages')}${navItem('/activity','activity','Activity',state.notificationsUnread?String(state.notificationsUnread):'')}${navItem('/market','market','Marketplace')}${navItem('/connect','connect','Connect')}${navItem('/games','games','Games')}${navItem('/scheduled','scheduled','Scheduled')}${navItem('/ai','ai','Blink AI')}
        <div class="sidebar-spacer"></div>${navItem('/settings/profile','settings','Settings')}
        ${p?`<button class="profile-mini" data-nav="/@${esc(p.username)}">${avatar(p.avatar_url,p.full_name)}<span><strong>${esc(p.full_name||p.username)}</strong><span class="handle">@${esc(p.username)}</span></span></button>`:''}
      </aside>
      <main class="main"><header class="topbar"><h1>${esc(title)}</h1><div class="top-actions"><a class="btn small-btn download-app" href="https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk" aria-label="Download BLINK Android app">↓ App</a><button class="btn small-btn" data-action="create-post">＋ Post</button>${p?`<button class="btn small-btn ghost" data-action="logout">Log out</button>`:'<button class="btn small-btn primary" data-nav="/login">Log in</button>'}</div></header><div class="content-wrap">${content}</div></main>
      <aside class="rightbar">${right||rightRail()}</aside>
      <nav class="mobile-nav">${mobileItem('/feed','home','Home')}${mobileItem('/reels','reels','Reels')}${mobileItem('/search','search','Search')}${mobileItem('/messages','messages','Messages')}${mobileItem('/activity','activity','Activity')}</nav>
    </div>`;
  }
  function rightRail(){
    const profileButton=state.profile?.username
      ? `<button class="btn" data-action="open-app">Open my profile in app</button>`
      : '';
    return `<div class="side-card"><h3>Blink Web</h3><p class="muted small">Your web session uses the same Blink account and Supabase backend as Android.</p><a class="btn primary download-app" href="${APK_URL}">↓ Download Android app</a>${profileButton}</div><div class="side-card"><h3>Quick links</h3><div class="quick-list"><button class="nav-btn" data-nav="/market">▣ Marketplace</button><button class="nav-btn" data-nav="/connect">◎ Connect Hub</button><button class="nav-btn" data-nav="/games">◇ Games</button><button class="nav-btn" data-nav="/scheduled">◷ Scheduled posts</button><button class="nav-btn" data-nav="/ai">✦ Blink AI</button></div></div>`;
  }
  function requireAuth(){ if(isAuthed())return true; navigate('/login'); return false; }

  function bindCommon(){
    document.querySelectorAll('[data-nav]').forEach(el=>el.onclick=(e)=>{e.preventDefault();navigate(el.dataset.nav);});
    document.querySelectorAll('[data-action="logout"]').forEach(el=>el.onclick=logout);
    document.querySelectorAll('[data-action="open-app"]').forEach(el=>el.onclick=()=>openAndroid('profile',state.profile?.username||''));
    document.querySelectorAll('[data-action="create-post"]').forEach(el=>el.onclick=()=>requireAuth()&&openPostComposer());
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

  async function logout(){ try{ if(token())await api('/auth/v1/logout',{method:'POST'}); }catch{} clearSession(); toast('Signed out'); navigate('/'); }

  async function login(identifier,password){
    const input=identifier.trim().toLowerCase(); let data;
    if(input.includes('@')&&!input.startsWith('@')) data=await api('/auth/v1/token?grant_type=password',{method:'POST',body:{email:input,password},auth:false});
    else data=await api('/functions/v1/username-login',{method:'POST',body:{username:input.replace(/^@/,''),password},auth:false});
    if(!data?.access_token)throw new Error('Blink did not return a valid session.'); saveSession(data); if(!data.user){const user=await api('/auth/v1/user');state.session.user=user;saveSession(state.session);} await loadMyProfile();await loadFollowing();await refreshUnreadCount();
  }
  async function signup(fullName,username,email,password){
    if(password.length<8||!/[A-Z]/.test(password)||!/[a-z]/.test(password)||!/\d/.test(password)||!/[\W_]/.test(password))throw new Error('Use 8+ characters with uppercase, lowercase, a number and a symbol.');
    const cleanUser=username.trim().toLowerCase().replace(/^@/,''); if(!/^[a-z0-9][a-z0-9._-]{1,29}$/.test(cleanUser))throw new Error('Use 2–30 lowercase letters, numbers, dots, dashes or underscores.');
    const data=await api('/auth/v1/signup',{method:'POST',auth:false,body:{email:email.trim().toLowerCase(),password,data:{username:cleanUser,full_name:fullName.trim(),name:fullName.trim()}}});
    if(data?.access_token){saveSession(data);await loadMyProfile();} return data;
  }
  function googleLogin(){ const redirect=encodeURIComponent(location.origin+routeHref('/feed')); location.href=`${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${redirect}`; }
  async function requestReset(email){ const redirect=location.origin+routeHref('/settings/password'); await api('/auth/v1/recover',{method:'POST',auth:false,body:{email:email.trim().toLowerCase(),redirect_to:redirect}}); }
  async function updatePassword(password){ if(password.length<8)throw new Error('Use at least 8 characters.'); await api('/auth/v1/user',{method:'PUT',body:{password}}); }

  function renderLanding(){
    setTitle('Blink'); ROOT.innerHTML=`<div class="auth-wrap"><div style="width:min(100%,1000px)"><div class="hero"><img class="mark hero-mark" src="blink-logo.png" alt="BLINK"><h1><span class="gradient-text">Blink</span> everywhere.</h1><p>Browse public profiles, posts and reels without installing anything. Sign in to use your full Blink account on the web.</p><div class="hero-actions"><button class="btn primary" data-nav="${isAuthed()?'/feed':'/login'}">${isAuthed()?'Open feed':'Sign in to Blink'}</button><a class="btn download-app" href="https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk">↓ Download Android app</a></div><form id="public-search" class="landing-search"><input class="field" name="username" placeholder="Open @username" autocomplete="off"><button class="btn">View profile</button></form></div><div class="feature-grid"><div class="card feature"><b>Public links</b><p>Profiles, posts and reels open directly in Chrome and still use Blink view counting.</p></div><div class="card feature"><b>Full account</b><p>Feed, follows, comments, messages, scheduled posts, market, Connect and games share the same backend.</p></div><div class="card feature"><b>App handoff</b><p>Open the same content in the Android app with deep links whenever Blink is installed.</p></div></div></div></div>`;
    document.querySelector('[data-nav]')?.addEventListener('click',e=>{e.preventDefault();navigate(e.currentTarget.dataset.nav);}); document.getElementById('public-search').onsubmit=e=>{e.preventDefault();const u=new FormData(e.currentTarget).get('username').trim().replace(/^@/,'');if(u)navigate(`/@${encodeURIComponent(u)}`);};
  }

  function renderLogin(){
    if(isAuthed()){navigate('/feed',true);return;}
    setTitle('Sign in'); ROOT.innerHTML=`<div class="auth-wrap"><div class="card auth-card"><div class="brand" style="padding:0 0 8px"><img class="mark" src="blink-logo.png" alt="" aria-hidden="true">Blink</div><h1>Welcome back</h1><p class="muted">Use the same account you use in the Android app.</p><div class="auth-tabs"><button class="btn primary" data-auth-tab="login">Sign in</button><button class="btn" data-auth-tab="signup">Create account</button></div><div id="auth-panel"></div><p class="auth-note">Your browser receives a normal user session. Blink's service-role credentials are never exposed to the web.</p><button class="btn ghost" style="width:100%;margin-top:8px" data-nav="/">Browse without signing in</button></div></div>`;
    const panel=document.getElementById('auth-panel');
    const showLogin=()=>{panel.innerHTML=`<form id="login-form" class="form-stack"><input class="field" name="identifier" placeholder="Email or @username" required autocomplete="username"><input class="field" name="password" type="password" placeholder="Password" required autocomplete="current-password"><button class="btn primary">Sign in</button><button class="btn" type="button" id="google-auth">Continue with Google</button><button class="btn ghost" type="button" id="forgot">Forgot password?</button><div id="auth-msg"></div></form>`; document.getElementById('login-form').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),msg=document.getElementById('auth-msg');msg.innerHTML='<span class="muted">Signing in…</span>';try{await login(f.get('identifier'),f.get('password'));toast('Signed in');navigate('/feed');}catch(err){msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;}}; document.getElementById('google-auth').onclick=googleLogin; document.getElementById('forgot').onclick=()=>{modal('Reset password',`<form id="reset-form" class="form-stack"><input class="field" type="email" name="email" placeholder="Email address" required><button class="btn primary">Send reset email</button><div id="reset-msg"></div></form>`);document.getElementById('reset-form').onsubmit=async e=>{e.preventDefault();const email=new FormData(e.currentTarget).get('email');try{await requestReset(email);document.getElementById('reset-msg').innerHTML='<div class="success">Check your inbox for the Blink password reset link.</div>';}catch(err){document.getElementById('reset-msg').innerHTML=`<div class="error">${esc(err.message)}</div>`;}};};};
    const showSignup=()=>{panel.innerHTML=`<form id="signup-form" class="form-stack"><input class="field" name="name" placeholder="Full name" required><input class="field" name="username" placeholder="Username" required><input class="field" type="email" name="email" placeholder="Email" required><input class="field" type="password" name="password" placeholder="Strong password" required><button class="btn primary">Create Blink account</button><div id="auth-msg"></div></form>`;document.getElementById('signup-form').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),msg=document.getElementById('auth-msg');try{const data=await signup(f.get('name'),f.get('username'),f.get('email'),f.get('password'));if(data?.access_token){toast('Account created');navigate('/feed');}else msg.innerHTML='<div class="success">Account created. Check your email if Blink requires verification.</div>';}catch(err){msg.innerHTML=`<div class="error">${esc(err.message)}</div>`;}};};
    document.querySelector('[data-auth-tab="login"]').onclick=showLogin;document.querySelector('[data-auth-tab="signup"]').onclick=showSignup;document.querySelector('[data-nav="/"]').onclick=()=>navigate('/');showLogin();
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

  function openPostComposer(){
    const draft=storageGet(draftKey(),'');
    modal('Create post',`<form id="post-form" class="form-stack"><textarea class="field" name="text" maxlength="8000" placeholder="What's happening on campus?">${esc(draft)}</textarea><div class="form-grid"><select class="field" name="audience"><option>Everyone</option><option>Followers</option></select><select class="field" name="category"><option>Campus Life</option><option>Education</option><option>Entertainment</option><option>Marketplace</option><option>Sports</option><option>Technology</option></select></div><input class="field" name="tags" maxlength="500" placeholder="Tags, comma separated"><input class="field" name="location" maxlength="160" placeholder="Location (optional)"><label class="checkbox"><input type="checkbox" name="reel"> Publish as Reel</label><label class="btn" style="text-align:center">Attach image/video<input class="hidden" id="post-media-file" type="file" accept="image/*,video/*"></label><div id="media-name" class="muted small"></div><button type="submit" class="btn primary">Publish</button><div id="post-msg"></div></form>`);
    const form=document.getElementById('post-form');
    const file=document.getElementById('post-media-file');
    const mediaName=document.getElementById('media-name');
    const submit=form.querySelector('button[type="submit"]');
    form.elements.text.oninput=e=>storageSet(draftKey(),e.target.value);
    file.onchange=()=>{
      try{
        const media=file.files[0];
        if(media)validateMediaFile(media);
        mediaName.textContent=media?.name||'';
      }catch(err){
        file.value='';
        mediaName.textContent=err.message;
      }
    };
    form.onsubmit=async e=>{
      e.preventDefault();
      const f=new FormData(form),msg=document.getElementById('post-msg'),media=file.files[0];
      submit.disabled=true;
      msg.innerHTML='<span class="muted">Publishing…</span>';
      try{
        let mediaUrl='',videoUrl='',images=[];
        if(media){
          const kind=validateMediaFile(media);
          mediaUrl=await uploadPublicFile('post-media',media,'posts');
          if(kind.isVideo)videoUrl=mediaUrl;else images=[mediaUrl];
        }
        const text=String(f.get('text')||'').trim();
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
    file.onchange=()=>{
      try{const media=file.files[0];if(media)validateMediaFile(media,{imageMaxMb:12,videoMaxMb:80});label.textContent=media?.name||'';}
      catch(err){file.value='';label.textContent=err.message;}
    };
    form.onsubmit=async e=>{
      e.preventDefault();
      const media=file.files[0],msg=document.getElementById('story-msg');
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

  async function fetchProfiles(ids){ const unique=[...new Set(ids.filter(Boolean))];if(!unique.length)return new Map(); const rows=await table('profiles',`id=in.(${unique.map(encodeQ).join(',')})&select=id,username,full_name,avatar_url,is_verified,verification_badge,blink_vip_until,university,faculty`);return new Map((rows||[]).map(p=>[p.id,p])); }
  function normalizeFeedItem(row,profiles){ const x=row?.item||row; const p=profiles.get(x.user_id)||{}; return {...x,author:p}; }
  function mediaBlock(post){ const imgs=(post.images?.length?post.images:[post.image_url]).filter(Boolean).map(safeUrl).filter(Boolean);const video=safeUrl(post.video_url);if(video)return `<div class="post-media"><video src="${esc(video)}" controls playsinline preload="metadata" data-autopause></video></div>`;if(imgs.length===1)return `<div class="post-media"><img src="${esc(imgs[0])}" alt="${esc(post.alt_text||'Post image')}" loading="lazy"></div>`;if(imgs.length>1)return `<div class="post-media media-grid">${imgs.slice(0,4).map(u=>`<img src="${esc(u)}" alt="" loading="lazy">`).join('')}</div>`;return ''; }
  function postCard(post){ const a=post.author||{};const isReel=!!post.is_reel;const path=`/${isReel?'reel':'post'}/${post.id}`;return `<article class="card post-card" data-post-id="${esc(post.id)}"><header class="post-head"><button class="profile-mini" style="border:0;background:transparent;padding:0" data-nav="/@${esc(a.username||post.user_id)}">${avatar(a.avatar_url,a.full_name||a.username)}<span class="grow"><strong>${esc(a.full_name||a.username||'Blink user')}${verifyMark(a)}${vipMark(a)}</strong><span class="handle">@${esc(a.username||'user')} · ${esc(ago(post.created_at))}</span></span></button>${post.is_sponsored?'<span class="handle">Sponsored</span>':''}<button class="icon-btn" data-open="${esc(path)}">⋯</button></header><div class="post-body">${post.text||post.caption?`<p class="post-text">${esc(post.text||post.caption)}</p>`:''}${Array.isArray(post.tags)&&post.tags.length?`<div class="tags">${post.tags.slice(0,10).map(t=>`#${esc(t)}`).join(' ')}</div>`:''}</div>${mediaBlock(post)}<div class="post-stats"><span>${fmt(post.like_count)} likes</span><span>${fmt(post.comment_count)} comments</span><span>${fmt(post.repost_count)} reposts</span><span>${fmt(post.view_count)} views</span></div><div class="post-actions"><button data-like="${esc(post.id)}" class="like">♡ Like</button><button data-comments="${esc(post.id)}">◌ Comment</button><button data-repost="${esc(post.id)}">↻ Repost</button><button data-bookmark="${esc(post.id)}" class="bookmark">⌑ Save</button><button data-share="${esc(path)}">↗ Share</button></div></article>`; }
  function bindPostCards(){
    document.querySelectorAll('[data-open]').forEach(x=>x.onclick=()=>navigate(x.dataset.open));
    document.querySelectorAll('[data-like]').forEach(x=>x.onclick=async()=>{if(!requireAuth())return;try{const on=x.classList.toggle('on');await rpc(on?'like_post':'unlike_post',{p_post_id:x.dataset.like});toast(on?'Liked':'Like removed');}catch(e){x.classList.toggle('on');toast(e.message);}});
    document.querySelectorAll('[data-bookmark]').forEach(x=>x.onclick=async()=>{if(!requireAuth())return;try{const on=x.classList.toggle('on');await rpc(on?'bookmark_post':'unbookmark_post',{p_post_id:x.dataset.bookmark});toast(on?'Saved':'Removed from saved');}catch(e){x.classList.toggle('on');toast(e.message);}});
    document.querySelectorAll('[data-repost]').forEach(x=>x.onclick=async()=>{if(!requireAuth())return;try{await rpc('toggle_post_repost',{p_post_id:x.dataset.repost});x.classList.toggle('on');toast('Repost updated');}catch(e){toast(e.message);}});
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
    if(!('IntersectionObserver'in window))return;
    state.mediaObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.intersectionRatio<.35)entry.target.pause();}),{threshold:[0,.35,.7]});
    vids.forEach(v=>state.mediaObserver.observe(v));
  }
  async function sharePath(path){
    const url=location.origin+routeHref(path);
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
  function openStory(s){if(!s)return;const media=safeUrl(s.video_url||s.image_url||s.media_url);modal(`@${s.author?.username||'Blink'}`,`${s.media_type==='video'||s.video_url?`<video src="${esc(media)}" controls autoplay playsinline style="width:100%;max-height:70vh"></video>`:`<img src="${esc(media)}" alt="Story" style="width:100%;max-height:70vh;object-fit:contain">`}<p>${esc(s.caption||s.text||'')}</p><div class="muted small">${fmt(s.views_count)} views · ${fmt(s.likes_count)} likes</div>`);}

  async function renderFeed(path='/feed'){
    if(!requireAuth())return; state.loading=true; ROOT.innerHTML=shell('<div class="boot" style="min-height:55vh"><div><div class="spinner"></div><p class="muted">Loading your Blink feed…</p></div></div>','Home');bindCommon();
    try{const stories=await loadStories();const mode=new URLSearchParams(location.search).get('mode')||'posts';state.feedMode=['posts','reels','all'].includes(mode)?mode:'posts';const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:0,p_as_of:null,p_feed_type:state.feedMode});state.feedOffset=rows?.[0]?.next_offset||rows?.length||0;state.feedAsOf=rows?.[0]?.as_of||null;const profiles=await fetchProfiles((rows||[]).map(r=>(r.item||r).user_id));state.feed=(rows||[]).map(r=>normalizeFeedItem(r,profiles));const content=`${storyStrip(stories)}<div class="tabs"><button class="tab ${state.feedMode==='posts'?'active':''}" data-feed-mode="posts">For you</button><button class="tab" data-following>Following</button><button class="tab ${state.feedMode==='all'?'active':''}" data-feed-mode="all">Mixed</button></div><section class="card composer"><div class="composer-row">${avatar(state.profile?.avatar_url,state.profile?.full_name)}<textarea id="quick-draft" placeholder="Create a post…">${esc(storageGet(draftKey(),''))}</textarea></div><div class="composer-tools"><div class="left"><button class="btn small-btn" data-action="create-story">Story</button><button class="btn small-btn" data-action="create-post">Photo / video</button></div><button class="btn primary small-btn" data-action="quick-publish">Post</button></div></section><div class="feed" id="feed-list">${state.feed.length?state.feed.map(postCard).join(''):'<div class="card empty"><h2>No posts yet</h2><p>Your ranked feed is empty right now.</p></div>'}</div><div class="load-more"><button class="btn" id="load-more-feed">Load more</button></div>`;ROOT.innerHTML=shell(content,'Home');bindCommon();bindStories(stories);bindPostCards();document.querySelectorAll('[data-feed-mode]').forEach(x=>x.onclick=()=>{const u=new URL(location.href);u.searchParams.set('mode',x.dataset.feedMode);history.replaceState({},'',u);renderFeed();});document.querySelector('[data-following]').onclick=renderFollowing;document.querySelector('[data-action="create-story"]').onclick=()=>createStory();const q=document.getElementById('quick-draft');q.oninput=()=>storageSet(draftKey(),q.value);document.querySelector('[data-action="quick-publish"]').onclick=()=>{if(q.value.trim())openPostComposer();else toast('Type something first.');};document.getElementById('load-more-feed').onclick=loadMoreFeed;}catch(e){ROOT.innerHTML=shell(`<div class="card empty"><h2>Couldn’t load feed</h2><p>${esc(e.message)}</p><button class="btn" data-nav="/feed">Try again</button></div>`,'Home');bindCommon();}
    finally{state.loading=false;}
  }
  async function loadMoreFeed(){if(state.loading)return;state.loading=true;const btn=document.getElementById('load-more-feed');if(btn)btn.textContent='Loading…';try{const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:state.feedOffset,p_as_of:state.feedAsOf,p_feed_type:state.feedMode});if(!rows?.length){toast('You’re caught up');return;}state.feedOffset=rows[0]?.next_offset||state.feedOffset+rows.length;const profiles=await fetchProfiles(rows.map(r=>(r.item||r).user_id));const items=rows.map(r=>normalizeFeedItem(r,profiles));document.getElementById('feed-list').insertAdjacentHTML('beforeend',items.map(postCard).join(''));bindPostCards();}catch(e){toast(e.message);}finally{state.loading=false;if(btn)btn.textContent='Load more';}}
  async function renderFollowing(){
    try{
      const rows=await rpc('get_following_feed',{p_limit:40,p_cursor:null});
      const profiles=await fetchProfiles((rows||[]).map(x=>x.user_id));
      state.feedMode='following';
      state.feed=(rows||[]).map(x=>normalizeFeedItem(x,profiles));
      document.getElementById('feed-list').innerHTML=state.feed.length?state.feed.map(postCard).join(''):'<div class="card empty"><h2>Your Following feed is quiet</h2><p>Follow more people to see their posts here.</p></div>';
      document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
      document.querySelector('[data-following]')?.classList.add('active');
      const loadMore=document.getElementById('load-more-feed');
      if(loadMore)loadMore.hidden=true;
      bindPostCards();
    }catch(e){toast(e.message);}
  }

  async function renderReels(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell('<div class="boot" style="min-height:55vh"><div class="spinner"></div></div>','Reels');bindCommon();
    try{
      const rows=await rpc('get_ranked_feed_page',{p_limit:30,p_offset:0,p_as_of:null,p_feed_type:'reels'});
      const profiles=await fetchProfiles((rows||[]).map(r=>(r.item||r).user_id));
      const reels=(rows||[]).map(r=>normalizeFeedItem(r,profiles));
      const html=reels.length?`<div class="reels-feed">${reels.map(r=>{const a=r.author||{},src=safeUrl(r.video_url||r.image_url||(r.images||[])[0]);return `<article class="reel-card" data-post-id="${esc(r.id)}">${r.video_url?`<video src="${esc(src)}" playsinline controls muted preload="metadata" data-autopause></video>`:`<img src="${esc(src)}" alt="Reel">`}<div class="reel-overlay"><strong>@${esc(a.username||'user')}${verifyMark(a)}</strong><p>${esc(r.text||r.caption||'')}</p></div><div class="reel-actions"><button type="button" data-like="${esc(r.id)}">♡</button><button type="button" data-comments="${esc(r.id)}">◌</button><button type="button" data-share="/reel/${esc(r.id)}">↗</button></div></article>`;}).join('')}</div>`:'<div class="card empty"><h2>No reels available</h2></div>';
      ROOT.innerHTML=shell(html,'Reels');bindCommon();bindPostCards();
      state.mediaObserver?.disconnect();
      state.mediaObserver=null;
      if('IntersectionObserver'in window){
        let prefs={};try{prefs=JSON.parse(storageGet(PARITY_PREF_KEY,'{}'))||{};}catch{}
        const autoplay=prefs.autoplay!==false&&!matchMedia('(prefers-reduced-motion: reduce)').matches;
        state.mediaObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{
          const video=entry.target.querySelector('video');if(!video)return;
          if(entry.intersectionRatio>.7&&autoplay)video.play().catch(()=>{});else video.pause();
        }),{threshold:[0,.7]});
        document.querySelectorAll('.reel-card').forEach(r=>state.mediaObserver.observe(r));
      }
    }catch(e){ROOT.innerHTML=shell(`<div class="card empty"><h2>Couldn’t load reels</h2><p>${esc(e.message)}</p></div>`,'Reels');bindCommon();}
  }

  async function renderPublicProfile(identifier){
    ROOT.innerHTML='<div class="boot"><div class="spinner"></div></div>';
    try{
      const cleanIdentifier=String(identifier||'').trim().replace(/^@/,'');
      if(!cleanIdentifier)throw new Error('Profile not available');
      const data=await anonRpc('get_public_web_profile',{p_username:cleanIdentifier});
      if(!data?.available||!data.profile)throw new Error('Profile not available');
      const p=data.profile,items=Array.isArray(data.items)?data.items:[];
      if(p.username&&currentPath()!==`/@${p.username}`)history.replaceState({},'',routeHref(`/@${p.username}`));
      const actions=isAuthed()
        ? `<button type="button" class="btn primary" id="follow-profile">${state.following.has(p.id)?'Following':'Follow'}</button><button type="button" class="btn" id="message-profile">Message</button>`
        : `<button type="button" class="btn primary" data-nav="/login">Log in to follow</button><button type="button" class="btn" data-open-app-profile>Open app</button>`;
      const cover=safeUrl(p.coverPhotoUrl||p.cover_photo_url||p.coverPhoto||p.cover_photo);
      const tiles=items.map(i=>{
        const media=safeUrl(i.videoUrl||(i.imageUrls||[])[0]||i.imageUrl);
        const itemType=String(i.type||'').toLowerCase()==='reel'||i.isReel||!!i.videoUrl?'reel':'post';
        return `<button type="button" class="profile-tile" data-nav="/${itemType}/${esc(i.id)}">${media?(i.videoUrl?`<video src="${esc(media)}" muted preload="metadata"></video>`:`<img src="${esc(media)}" alt="" loading="lazy">`):`<div class="tile-text">${esc((i.text||i.caption||'Post').slice(0,120))}</div>`}<span class="tile-badge">${itemType==='reel'?'▶ Reel':'Post'} · ${fmt(i.viewCount)} views</span></button>`;
      }).join('');
      const html=`<div class="profile-card card"><div class="profile-cover">${cover?`<img class="profile-cover-image" src="${esc(cover)}" alt="" loading="lazy">`:''}</div><div class="profile-content"><div class="profile-top">${avatar(p.avatarUrl,p.fullName,'profile-avatar')}<div>${actions}</div></div><div class="profile-info"><h2>${esc(p.fullName||p.username)}${verifyMark(p)}</h2><div class="handle">@${esc(p.username)}</div>${p.headline?`<div class="profile-meta">${esc(p.headline)}</div>`:''}${p.bio?`<p class="profile-bio">${esc(p.bio)}</p>`:''}<div class="profile-meta">${[p.university,p.faculty,p.department].filter(Boolean).map(esc).join(' · ')}</div><div class="stats-grid"><div class="stat"><strong>${fmt(p.postsCount)}</strong><span>Posts</span></div><div class="stat"><strong>${fmt(p.followerCount)}</strong><span>Followers</span></div><div class="stat"><strong>${fmt(p.followingCount)}</strong><span>Following</span></div></div></div></div></div><div class="profile-grid">${tiles}</div>`;
      ROOT.innerHTML=isAuthed()?shell(html,p.fullName||`@${p.username}`):`<div style="width:min(100%,760px);margin:0 auto;padding:14px"><header class="topbar"><button type="button" class="brand nav-btn" data-nav="/"><img class="mark" src="blink-logo.png" alt="" aria-hidden="true">Blink</button><div><a class="btn download-app" href="${APK_URL}">Download app</a><button type="button" class="btn" data-nav="/login">Log in</button></div></header>${html}</div>`;
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
      ROOT.innerHTML=`<div class="auth-wrap"><div class="card empty"><h2>Profile unavailable</h2><p>This account may be private, removed or the username may be incorrect.</p><button type="button" class="btn" data-nav="/">Back to Blink</button></div></div>`;bindCommon();
    }
  }

  async function renderPublicContent(type,id){ROOT.innerHTML='<div class="boot"><div class="spinner"></div></div>';try{const data=await anonRpc('get_public_web_content',{p_content_id:id,p_content_type:type});if(!data?.available)throw new Error('Unavailable');const c=data.content,a=c.author||{};const normalized={id:c.id,user_id:a.id,text:c.text||c.caption,caption:c.caption,images:c.imageUrls||[],video_url:c.videoUrl,created_at:c.createdAt,like_count:c.likeCount,comment_count:c.commentCount,share_count:c.shareCount,repost_count:c.repostCount,view_count:c.viewCount,is_reel:type==='reel',allow_comments:c.allowComments,hide_likes:c.hideLikes,author:{id:a.id,username:a.username,full_name:a.fullName,avatar_url:a.avatarUrl,is_verified:a.isVerified,verification_badge:a.verificationBadge}};const extra=`<div class="card" style="padding:13px;margin-top:12px;display:flex;gap:8px;align-items:center;justify-content:space-between"><span class="muted small">${isAuthed()?'Interact with this content using your Blink account.':'Log in to like, comment, follow and message.'}</span><button class="btn primary small-btn" id="open-content-app">Open in Blink</button></div>`;ROOT.innerHTML=isAuthed()?shell(postCard(normalized)+extra,type==='reel'?'Reel':'Post'):`<div style="width:min(100%,760px);margin:0 auto;padding:14px"><header class="topbar"><button class="brand nav-btn" data-nav="/"><img class="mark" src="blink-logo.png" alt="" aria-hidden="true">Blink</button><button class="btn primary" data-nav="/login">Log in</button></header>${postCard(normalized)}${extra}</div>`;bindCommon();bindPostCards();document.getElementById('open-content-app').onclick=()=>openAndroid(type,id);}catch{ROOT.innerHTML=`<div class="auth-wrap"><div class="card empty"><h2>Content unavailable</h2><p>This ${esc(type)} may be private or removed.</p><button class="btn" data-nav="/">Back to Blink</button></div></div>`;bindCommon();}}

  async function openComments(postId){
    if(!requireAuth())return;
    modal('Comments','<div id="comments-body"><div class="spinner"></div></div>');
    try{
      const rows=await rpc('get_post_comments',{p_post_id:postId});
      const body=document.getElementById('comments-body');if(!body)return;
      body.innerHTML=`<div class="comment-list">${(rows||[]).map(comment=>`<div class="comment"><div class="meta"><strong>${esc(comment.display_name||comment.username||'Blink user')}</strong> @${esc(comment.username||'')} · ${esc(ago(comment.created_at))}</div><div>${esc(comment.content)}</div><button type="button" class="btn small-btn ghost" data-reply-comment="${esc(comment.id)}">Reply · ${fmt(comment.likes_count)} likes</button></div>`).join('')||'<p class="muted">No comments yet.</p>'}</div><form id="comment-form" class="chat-compose" style="margin-top:12px"><input class="field" name="text" maxlength="2000" placeholder="Write a comment…" required><button type="submit" class="btn primary">Send</button></form>`;
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

  async function renderSearch(){
    if(!requireAuth())return;
    const q=new URLSearchParams(location.search).get('q')||'';
    const form=`<form id="search-form" style="display:flex;gap:8px;margin-bottom:13px"><input class="search-input" name="q" maxlength="120" value="${esc(q)}" placeholder="Search people, posts, tags…" autocomplete="off"><button class="btn primary">Search</button></form><div id="search-results"></div>`;
    ROOT.innerHTML=shell(form,'Search');bindCommon();
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
      const [profiles,posts]=await Promise.all([
        rpc('search_profiles_page',{p_query:q,p_limit:20,p_after_username:null,p_after_id:null}),
        rpc('search_feed_page',{p_query:q,p_limit:30,p_before:null,p_before_id:null})
      ]);
      if(requestId!==state.searchRequestId||currentPath()!=='/search')return;
      const pmap=await fetchProfiles((posts||[]).map(x=>x.user_id));
      if(requestId!==state.searchRequestId||!document.getElementById('search-results'))return;
      out.innerHTML=`<h3>People</h3><div class="list">${(profiles||[]).map(p=>`<button type="button" class="card list-item" data-nav="/@${esc(p.username)}">${avatar(p.avatar_url,p.full_name)}<span class="grow"><span class="title">${esc(p.full_name||p.username)}${verifyMark(p)}</span><span class="sub">@${esc(p.username)} · ${esc(p.university||'Blink')}</span></span></button>`).join('')||'<p class="muted">No people found.</p>'}</div><h3>Posts</h3><div class="feed">${(posts||[]).map(x=>postCard(normalizeFeedItem(x,pmap))).join('')||'<p class="muted">No posts found.</p>'}</div>`;
      bindPostCards();
    }catch(e){if(requestId===state.searchRequestId&&out.isConnected)out.innerHTML=`<div class="error">${esc(e.message)}</div>`;}
  }

  async function renderActivity(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell('<div class="spinner"></div>','Activity');bindCommon();
    try{
      const [notifications,activities]=await Promise.all([
        table('notifications',`user_id=eq.${encodeQ(uid())}&select=id,actor_id,type,post_id,text,sub_text,is_read,created_at,actor_is_vip,vip_priority&order=created_at.desc&limit=80`),
        table('activities',`recipient_id=eq.${encodeQ(uid())}&select=id,actor_id,activity_type,entity_type,entity_id,message,is_read,created_at&order=created_at.desc&limit=80`)
      ]);
      const rows=[...(notifications||[]).map(x=>({...x,kind:'notification'})),...(activities||[]).map(x=>({...x,text:x.message,kind:'activity'}))].sort((a,b)=>new Date(b.created_at)-new Date(a.created_at));
      const profiles=await fetchProfiles(rows.map(x=>x.actor_id));
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
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Activity');bindCommon();}
  }

  async function renderMessages(path){
    if(!requireAuth())return;
    const parts=path.split('/').filter(Boolean),convId=parts[1],target=new URLSearchParams(location.search).get('user')||'';
    if(convId==='new'){renderNewMessage(target);return;}
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
      const html=`<div style="display:flex;gap:8px;margin-bottom:12px"><button type="button" class="btn primary" id="new-message">New message</button></div><div class="list">${rows.map(m=>{
        const group=meta.get(String(m.conversation_id));
        const isGroup=!!group?.is_group;
        const title=isGroup?(group.title||'Group chat'):(m.partner_name||m.partner_username||'Blink user');
        const picture=isGroup?group.avatar_url:m.partner_avatar;
        const targetQuery=!isGroup&&m.partner_username?`?user=${encodeURIComponent(m.partner_username)}`:'';
        const unread=Number(m.unread_count||0);
        return `<button type="button" class="card list-item ${unread?'notification unread':''}" data-nav="/messages/${esc(m.conversation_id)}${targetQuery}">${avatar(picture,title)}<span class="grow"><span class="title">${esc(title)}${m.partner_online&&!isGroup?' <span class="online-dot" title="Online"></span>':''}</span><span class="sub">${esc(m.last_message||'No messages yet')} · ${ago(m.last_message_at)}</span></span>${unread?`<span class="badge">${fmt(unread)}</span>`:''}</button>`;
      }).join('')||'<div class="card empty"><h2>No conversations yet</h2><p>Start a conversation with a Blink user.</p></div>'}</div>`;
      ROOT.innerHTML=shell(html,'Messages');bindCommon();document.getElementById('new-message').onclick=()=>navigate('/messages/new');
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Messages');bindCommon();}
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
  async function renderConversation(id,target=''){
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
      const title=isGroup?(conversationMeta?.title||'Group chat'):(partner?`@${partner}`:'Conversation');
      const messages=(rows||[]).slice().reverse().map(m=>{
        const mine=m.sender_id===uid(),p=profiles.get(m.sender_id)||{},attachment=safeUrl(m.media_url);
        return `<div class="bubble ${mine?'me':''}">${esc(m.deleted_for_everyone?'Message deleted':m.content||'')}${attachment?`<br><a href="${esc(attachment)}" target="_blank" rel="noopener noreferrer">Attachment</a>`:''}<small>${mine?'You':`@${esc(p.username||'user')}`} · ${ago(m.created_at)}${m.edited_at?' · edited':''}</small></div>`;
      }).join('');
      const html=`<div class="card chat-window"><div class="post-head"><button type="button" class="btn small-btn" data-nav="/messages">← Messages</button><div class="grow"><strong>${esc(title)}</strong><span class="handle">Synced with Blink</span></div></div><div class="chat-messages" id="chat-messages">${messages||'<div class="muted small">No messages yet.</div>'}</div><form id="chat-form" class="chat-compose"><input class="field" name="text" maxlength="8000" placeholder="Message" required autocomplete="off"><button type="submit" class="btn primary">Send</button></form></div>`;
      ROOT.innerHTML=shell(html,title);bindCommon();
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
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Chat');bindCommon();}
  }

  async function renderMarket(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell('<div class="spinner"></div>','Marketplace');bindCommon();
    try{
      const q=new URLSearchParams(location.search).get('q')||'';
      const rows=await rpc('get_ranked_market_page',{p_query:q||null,p_category:null,p_min_price:null,p_max_price:null,p_limit:40,p_cursor_score:null,p_cursor_created_at:null,p_cursor_id:null,p_as_of:null});
      const items=(rows||[]).map(r=>r.item||r);
      const cards=items.map(i=>{
        const image=safeUrl((i.image_urls||[])[0]||i.image_url);
        const seller=String(i.seller_username||'').trim().replace(/^@/,'');
        const action=seller?`<button type="button" class="btn primary" data-message-seller="${esc(seller)}">Message seller</button>`:'<button type="button" class="btn" disabled>Seller unavailable</button>';
        return `<article class="card market-card">${image?`<img src="${esc(image)}" alt="${esc(i.title||'Marketplace item')}" loading="lazy">`:''}<h3>${esc(i.title||'Item')}</h3><div class="price">${money(i.price,i.currency)}</div><p class="muted small">${esc(i.condition||'')} · ${esc(i.location||i.university||'')}</p><p>${esc((i.description||'').slice(0,300))}</p>${action}</article>`;
      }).join('');
      ROOT.innerHTML=shell(`<form id="market-search" style="display:flex;gap:8px;margin-bottom:12px"><input class="field" name="q" maxlength="100" value="${esc(q)}" placeholder="Search marketplace" autocomplete="off"><button class="btn">Search</button></form><div class="market-grid">${cards||'<div class="card empty"><h2>No items found</h2></div>'}</div>`,'Marketplace');
      bindCommon();
      document.getElementById('market-search').onsubmit=e=>{e.preventDefault();const v=String(new FormData(e.currentTarget).get('q')||'').trim();const u=new URL(location.href);if(v)u.searchParams.set('q',v);else u.searchParams.delete('q');history.pushState({},'',u);renderMarket();};
      document.querySelectorAll('[data-message-seller]').forEach(x=>x.onclick=()=>navigate(`/messages/new?user=${encodeURIComponent(x.dataset.messageSeller)}`));
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Marketplace');bindCommon();}
  }

  async function renderConnect(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell('<div class="spinner"></div>','Connect');bindCommon();
    try{
      const allowed=new Set(['all','roommate','mentor','reading_mate','housing']);
      const requested=new URLSearchParams(location.search).get('mode')||'all';
      const mode=allowed.has(requested)?requested:'all';
      const rows=await rpc('get_ranked_connect_opportunities',{p_mode:mode,p_limit:40,p_cursor_score:null,p_cursor_kind:null,p_cursor_id:null,p_as_of:null});
      const cards=(rows||[]).map(r=>{
        const p=r.payload||{},profile=p.profile||{};
        const receiverId=String(profile.id||'');
        const title=p.title||p.headline||profile.full_name||profile.username||p.name||'Connect opportunity';
        const description=p.description||p.bio||'';
        const detail=[profile.university||p.university,profile.department||p.department,p.location||p.preferred_location].filter(Boolean).join(' · ');
        const action=receiverId?`<button type="button" class="btn small-btn" data-connect-receiver="${esc(receiverId)}">Connect</button>`:'<button type="button" class="btn small-btn" disabled>Unavailable</button>';
        return `<article class="card connect-card"><span class="handle">${esc(r.kind||'Connect')}</span><h3>${esc(title)}</h3><p>${esc(String(description).slice(0,260))}</p><div class="muted small">${esc(detail)}</div>${action}</article>`;
      }).join('');
      ROOT.innerHTML=shell(`<div class="tabs">${['all','roommate','mentor','reading_mate','housing'].map(m=>`<button type="button" class="tab ${mode===m?'active':''}" data-connect-mode="${m}">${m.replaceAll('_',' ')}</button>`).join('')}</div><div class="connect-grid">${cards||'<div class="card empty"><h2>No matches yet</h2></div>'}</div>`,'Connect');
      bindCommon();
      document.querySelectorAll('[data-connect-mode]').forEach(x=>x.onclick=()=>{const u=new URL(location.href);u.searchParams.set('mode',x.dataset.connectMode);history.pushState({},'',u);renderConnect();});
      document.querySelectorAll('[data-connect-receiver]').forEach(x=>x.onclick=async()=>{
        const button=x;button.disabled=true;
        try{await rpc('send_connection_request',{p_receiver_id:button.dataset.connectReceiver});button.textContent='Request sent';toast('Connection request sent');}
        catch(e){toast(e.message);button.disabled=false;}
      });
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Connect');bindCommon();}
  }

  async function renderGames(){
    if(!requireAuth())return;
    ROOT.innerHTML=shell('<div class="spinner"></div>','Games');bindCommon();
    try{
      const [dash,board]=await Promise.all([rpc('get_game_dashboard',{}),rpc('get_game_leaderboard',{p_period:'all_time',p_scope:'global',p_limit:50})]);
      const leaderboard=Array.isArray(board)?board:(board?.rows||board?.leaderboard||[]);
      ROOT.innerHTML=shell(`<div class="game-grid"><div class="card game-card"><h3>Your game profile</h3><p class="price">${fmt(dash?.score||dash?.profile?.score||0)} points</p><p class="muted">Coins ${fmt(dash?.coins||dash?.profile?.coins||0)} · Streak ${fmt(dash?.streak||dash?.profile?.streak||0)}</p></div><div class="card game-card"><h3>Play on Android</h3><p class="muted">Your scores and rewards remain synced to the same Blink backend.</p><a class="btn primary download-app" href="${APK_URL}">Download latest BLINK app</a></div></div><h3>Leaderboard</h3><div class="list">${leaderboard.map((u,i)=>`<div class="card list-item"><strong>#${i+1}</strong>${avatar(u.avatar_url,u.full_name||u.username)}<span class="grow"><span class="title">${esc(u.full_name||u.username||'Player')}</span><span class="sub">${fmt(u.score||u.points)} points</span></span></div>`).join('')||'<div class="card empty">Leaderboard is empty.</div>'}</div>`,'Games');
      bindCommon();
    }catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Games');bindCommon();}
  }

  async function renderScheduled(){if(!requireAuth())return;ROOT.innerHTML=shell('<div class="spinner"></div>','Scheduled posts');bindCommon();try{const rows=await table('scheduled_feed_posts','select=id,payload,scheduled_for,status,error_message,published_post_id,created_at&order=scheduled_for.desc&limit=100');ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Schedule a post</h3><form id="schedule-form" class="form-stack"><textarea class="field" name="text" placeholder="Post text" required></textarea><input class="field" name="when" type="datetime-local" required><input class="field" name="tags" placeholder="Tags, comma separated"><label class="checkbox"><input type="checkbox" name="reel"> Reel</label><button class="btn primary">Schedule</button><div id="schedule-msg"></div></form></div><div class="list">${(rows||[]).map(s=>`<div class="card schedule-row"><div><strong>${esc((s.payload?.text||'Scheduled post').slice(0,160))}</strong><div class="muted small">${new Date(s.scheduled_for).toLocaleString()} · ${esc(s.status)}</div>${s.error_message?`<div class="error">${esc(s.error_message)}</div>`:''}</div><div class="schedule-actions">${s.status==='pending'||s.status==='failed'?`<button class="btn small-btn" data-publish-scheduled="${esc(s.id)}">Publish now</button><button class="btn danger small-btn" data-cancel-scheduled="${esc(s.id)}">Cancel</button>`:''}</div></div>`).join('')||'<div class="card empty"><h2>No scheduled posts</h2></div>'}</div>`,'Scheduled posts');bindCommon();document.getElementById('schedule-form').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.currentTarget),when=new Date(f.get('when'));if(Number.isNaN(when.valueOf())||when<=new Date()){toast('Choose a future time.');return;}const payload={text:String(f.get('text')).trim(),faculty:state.profile?.faculty||'',video_url:null,images:[],tags:String(f.get('tags')||'').split(',').map(x=>x.trim()).filter(Boolean),is_reel:f.get('reel')==='on',audience:'Everyone',category:'Campus Life',location:null,link_url:null,allow_comments:true,hide_likes:false,is_pinned:false,is_disappearing:false,audio_title:null,alt_text:null};try{await rpc('schedule_feed_post',{p_payload:payload,p_scheduled_for:when.toISOString()});toast('Post scheduled');renderScheduled();}catch(err){document.getElementById('schedule-msg').innerHTML=`<div class="error">${esc(err.message)}</div>`;}};document.querySelectorAll('[data-cancel-scheduled]').forEach(x=>x.onclick=async()=>{try{await rpc('cancel_scheduled_feed_post',{p_schedule_id:x.dataset.cancelScheduled});toast('Schedule cancelled');renderScheduled();}catch(e){toast(e.message);}});document.querySelectorAll('[data-publish-scheduled]').forEach(x=>x.onclick=async()=>{try{const id=await rpc('publish_scheduled_feed_post_now',{p_schedule_id:x.dataset.publishScheduled});toast('Published now');if(id)navigate(`/post/${String(id).replace(/"/g,'')}`);else renderScheduled();}catch(e){toast(e.message);}});}catch(e){ROOT.innerHTML=shell(`<div class="error">${esc(e.message)}</div>`,'Scheduled posts');bindCommon();}}

  async function renderSettings(path){
    if(!requireAuth())return;
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
    await loadMyProfile();const p=state.profile||{};
    ROOT.innerHTML=shell(`<div class="card settings-section"><h3>Edit profile</h3><form id="profile-form" class="form-stack"><div class="form-grid"><input class="field" name="name" maxlength="100" value="${esc(p.full_name||'')}" placeholder="Full name"><input class="field" name="username" maxlength="30" value="${esc(p.username||'')}" placeholder="Username" autocomplete="off"></div><textarea class="field" name="bio" maxlength="500" placeholder="Bio">${esc(p.bio||'')}</textarea><input class="field" name="headline" maxlength="160" value="${esc(p.professional_headline||'')}" placeholder="Professional headline"><div class="form-grid"><input class="field" name="university" maxlength="160" value="${esc(p.university||'')}" placeholder="University"><input class="field" name="faculty" maxlength="120" value="${esc(p.faculty||'')}" placeholder="Faculty"><input class="field" name="department" maxlength="120" value="${esc(p.department||'')}" placeholder="Department"><input class="field" name="level" maxlength="80" value="${esc(p.academic_level||'')}" placeholder="Academic level"></div><button type="submit" class="btn primary">Save profile</button><div id="profile-msg"></div></form></div><div class="card settings-section"><h3>Profile media</h3><div class="form-grid"><label class="btn" style="text-align:center">Change avatar<input class="hidden" id="avatar-file" type="file" accept="image/*"></label><label class="btn" style="text-align:center">Change cover<input class="hidden" id="cover-file" type="file" accept="image/*"></label></div></div><div class="card settings-section"><h3>Security</h3><button type="button" class="btn" data-nav="/settings/password">Change password</button></div><div class="card settings-section"><h3>Creator snapshot</h3><div class="stats-grid"><div class="stat"><strong>${fmt(p.follower_count)}</strong><span>Followers</span></div><div class="stat"><strong>${fmt(p.posts_count)}</strong><span>Posts</span></div><div class="stat"><strong>${fmt(p.points)}</strong><span>Points</span></div></div></div>`,'Settings');
    bindCommon();
    document.getElementById('profile-form').onsubmit=async e=>{
      e.preventDefault();const form=e.currentTarget,f=new FormData(form),msg=document.getElementById('profile-msg');
      const username=String(f.get('username')||'').trim().toLowerCase().replace(/^@/,'');
      if(!/^[a-z0-9][a-z0-9._-]{1,29}$/.test(username)){msg.innerHTML='<div class="error">Username must be 2–30 lowercase letters, numbers, dots, dashes or underscores.</div>';return;}
      const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
      try{
        await patch('profiles',`id=eq.${encodeQ(uid())}`,{
          full_name:String(f.get('name')||'').trim(),username,handle:username,bio:String(f.get('bio')||'').trim(),
          professional_headline:String(f.get('headline')||'').trim(),university:String(f.get('university')||'').trim(),
          faculty:String(f.get('faculty')||'').trim(),department:String(f.get('department')||'').trim(),academic_level:String(f.get('level')||'').trim()
        });
        await loadMyProfile();msg.innerHTML='<div class="success">Profile saved.</div>';
      }catch(err){
        const message=/duplicate|unique/i.test(err.message||'')?'That username is already taken.':err.message;
        msg.innerHTML=`<div class="error">${esc(message)}</div>`;
      }finally{submit.disabled=false;}
    };
    document.getElementById('avatar-file').onchange=async e=>{
      const file=e.target.files[0];if(!file)return;
      try{validateMediaFile(file,{allowVideo:false,imageMaxMb:8});const url=await uploadPublicFile('avatars',file,'avatar');await patch('profiles',`id=eq.${encodeQ(uid())}`,{avatar_url:url});await loadMyProfile();toast('Avatar updated');renderSettings('/settings/profile');}
      catch(err){e.target.value='';toast(err.message);}
    };
    document.getElementById('cover-file').onchange=async e=>{
      const file=e.target.files[0];if(!file)return;
      try{validateMediaFile(file,{allowVideo:false,imageMaxMb:12});const url=await uploadPublicFile('covers',file,'cover');await patch('profiles',`id=eq.${encodeQ(uid())}`,{cover_photo_url:url,cover_photo:url});await loadMyProfile();toast('Cover updated');renderSettings('/settings/profile');}
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
        for(const endpoint of ['blink-ai-v2','blink-ai']){
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

  async function render(path=currentPath()){
    for(const timer of state.qualifiedTimers.values())clearTimeout(timer);
    state.qualifiedTimers.clear();
    state.viewObserver?.disconnect();state.viewObserver=null;
    state.mediaObserver?.disconnect();state.mediaObserver=null;
    state.searchRequestId++;
    if(window.BlinkWebParity?.render?.(path))return;
    if(path==='/'||path==='')return renderLanding();
    if(path==='/login')return renderLogin();
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

  async function init(){
    if(!SUPABASE_URL||!KEY){ROOT.innerHTML='<div class="auth-wrap"><div class="card empty"><h2>Web configuration missing</h2></div></div>';return;}
    try{await bootstrapSession();}catch(e){console.warn(e);} await render(currentPath());
    if('serviceWorker'in navigator && location.protocol==='https:')navigator.serviceWorker.register(routeHref('/sw.js')).catch(()=>{});
  }

  init();
})();

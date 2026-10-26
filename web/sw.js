// BLINK Phase 3: predictable offline shell, fresh UI assets, and privacy-safe caching.
// Never store user-specific routes, query-string links, Supabase data or API responses.
const CACHE='blink-web-v28-link-previews';
const CACHE_PREFIX='blink-web-';
const SHELL=[
  './',
  './index.html',
  './404.html',
  './styles.css',
  './landing.css',
  './discovery.js',
  './campuses.json',
  './parity.css',
  './config.js',
  './link-previews.js',
  './app.js',
  './parity.js',
  './blink-icon.svg',
  './blink-logo-v2.png',
  './favicon-32.png',
  './apple-touch-icon.png',
  './manifest.webmanifest'
];
const SHELL_PATHS=new Set(SHELL.map(path=>new URL(path,self.registration.scope).pathname));
const IMAGE_EXTENSIONS=/\.(?:svg|png|jpg|jpeg|webp|ico)$/i;

self.addEventListener('install',event=>{
  event.waitUntil(
    caches.open(CACHE)
      .then(cache=>cache.addAll(SHELL))
      .then(()=>self.skipWaiting())
  );
});

self.addEventListener('activate',event=>{
  event.waitUntil(
    caches.keys()
      .then(keys=>Promise.all(keys.filter(key=>key.startsWith(CACHE_PREFIX)&&key!==CACHE).map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

const fresh=async (request,event,cache)=>{
  try{
    const response=await fetch(request);
    if(response.ok && response.status===200 && response.type!=='opaque' && !/no-store/i.test(response.headers.get('Cache-Control')||'')){
      event.waitUntil(cache.put(request,response.clone()).catch(()=>{}));
    }
    return response;
  }catch{
    return (await cache.match(request))||Response.error();
  }
};

self.addEventListener('fetch',event=>{
  const request=event.request;
  if(request.method!=='GET')return;

  const url=new URL(request.url);
  if(url.origin!==self.location.origin)return;

  // Never intercept access tokens, private route queries or authenticated requests.
  if(request.headers.has('Authorization')||url.search)return;

  if(request.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const response=await fetch(request);
        if(response.ok||response.status!==404)return response;
        // GitHub Pages SPA deep links may return a 404; load the static shell
        // without recording the visited username, route or token in CacheStorage.
        const cache=await caches.open(CACHE);
        return (await cache.match('./index.html'))||response;
      }catch{
        const cache=await caches.open(CACHE);
        return (await cache.match('./index.html'))||Response.error();
      }
    })());
    return;
  }

  // Only the build's known public assets are allowed into CacheStorage.
  if(!SHELL_PATHS.has(url.pathname))return;
  event.respondWith((async()=>{
    const cache=await caches.open(CACHE);
    if(IMAGE_EXTENSIONS.test(url.pathname)){
      const cached=await cache.match(request);
      if(cached){
        event.waitUntil(fetch(request).then(response=>{
          if(response.ok&&response.status===200&&response.type!=='opaque')return cache.put(request,response);
        }).catch(()=>{}));
        return cached;
      }
    }
    return fresh(request,event,cache);
  })());
});

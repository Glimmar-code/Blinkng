const CACHE='blink-web-v10-logo-display-fix';
const SHELL=[
  './',
  './index.html',
  './404.html',
  './styles.css',
  './parity.css',
  './config.js',
  './app.js',
  './parity.js',
  './blink-icon.svg',
  './blink-logo.png',
  './blink-logo-v2.png',
  './manifest.webmanifest'
];

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
      .then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

self.addEventListener('fetch',event=>{
  const request=event.request;
  if(request.method!=='GET')return;

  const url=new URL(request.url);
  if(url.origin!==self.location.origin)return;

  if(request.mode==='navigate'){
    event.respondWith(
      fetch(request)
        .then(async response=>{
          if(response&&response.ok){
            const copy=response.clone();
            caches.open(CACHE).then(cache=>cache.put(request,copy)).catch(()=>{});
            return response;
          }
          return (await caches.match(request))
            || (await caches.match('./index.html'))
            || response
            || Response.error();
        })
        .catch(async()=>{
          return (await caches.match(request))
            || (await caches.match('./index.html'))
            || Response.error();
        })
    );
    return;
  }

  event.respondWith(
    caches.match(request).then(cached=>{
      const network=fetch(request).then(response=>{
        if(response&&response.ok&&response.type!=='opaque'){
          const copy=response.clone();
          caches.open(CACHE).then(cache=>cache.put(request,copy)).catch(()=>{});
        }
        return response;
      });
      return cached||network.catch(()=>Response.error());
    })
  );
});

/* BLINK official public-page anonymous analytics.
 * Explicitly activated by site owner 2026-10-18 after security and privacy review.
 * Aggregates only public page categories and APK link taps; no visitor identifiers.
 */
(() => {
  'use strict';
  const ENABLED = true; // Owner-authorized production activation (18 Oct 2026).
  if(!ENABLED)return;
  // Preview builds, insecure local servers and third-party copies never contribute.
  if(location.protocol!=='https:' || location.hostname!=='blink.com.ng')return;
  if(navigator.globalPrivacyControl === true || navigator.doNotTrack === '1')return;
  const pages = new Map([
    ['/','home'],['/about/','about'],['/features/','features'],
    ['/download/','download'],['/faq/','faq'],['/privacy/','privacy']
  ]);
  const prefix=location.hostname.endsWith('github.io')?'/Blinkng':'';
  const pathname=location.pathname.replace(new RegExp('^'+prefix.replace(/\//g,'\\/')+'(?=/|$)'), '')||'/';
  const page=pages.get(pathname.endsWith('/')?pathname:pathname+'/');
  if(!page || new URLSearchParams(location.search).has('p'))return;
  const endpoint='https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/blink-site-analytics';
  const send=kind=>{
    // No cookies, Authorization, ID, email, user path, page URL, or query fields.
    fetch(endpoint,{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({page,kind}),
      credentials:'omit',
      referrerPolicy:'no-referrer',
      mode:'cors',
      keepalive:true
    }).catch(()=>{});
  };
  const boot=()=>{
    send('view');
    document.addEventListener('click',e=>{
      const link=e.target instanceof Element ? e.target.closest('a[href]') : null;
      if(!link)return;
      try{
        const url=new URL(link.href,location.href);
        if(url.hostname==='github.com' &&
           url.pathname==='/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk'){
          send('download');
        }
      }catch{}
    },{capture:true,passive:true});
  };
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});
  else boot();
})();

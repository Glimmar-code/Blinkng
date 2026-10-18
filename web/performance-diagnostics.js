/* BLINK Phase 3 mobile performance diagnostics.
 * Deliberately inactive by default. Append ?blink_perf=1 to opt in locally.
 * No events, identifiers, URLs, queries, cookies or timings are transmitted.
 */
(() => {
  'use strict';
  const params = new URLSearchParams(location.search);
  if(params.get('blink_perf')!=='1')return;
  const numbers = {fcp:null,lcp:null,cls:0,inp:null,ttfb:null,domContentLoaded:null,assetsKB:null};
  // QA-only local funnel counters. Neither values entered in search boxes
  // nor usernames, campuses, IPs or URLs are collected or transmitted.
  const interactions={apkDownloads:0,featureTabs:0,campusSelections:0,profileSearches:0};
  const onClick=event=>{
    const node=event.target;
    if(!(node instanceof Element))return;
    if(node.closest('a[href*="releases/latest/download/Blink-latest.apk"]'))interactions.apkDownloads++;
    else if(node.closest('button[data-tour-tab]'))interactions.featureTabs++;
    else if(node.closest('button.campus-option'))interactions.campusSelections++;
  };
  const onSubmit=event=>{
    const form=event.target;
    if(form instanceof Element && form.id==='public-search')interactions.profileSearches++;
  };
  document.addEventListener('click',onClick,{capture:true,passive:true});
  document.addEventListener('submit',onSubmit,{capture:true});
  const round = v => Number.isFinite(v) ? Math.round(v*100)/100 : null;
  const observedInteractions = new Map();
  const observers = [];
  const register = (type,callback,extra={}) => {
    try {
      const supported=PerformanceObserver.supportedEntryTypes||[];
      if(!supported.includes(type))return;
      const observer=new PerformanceObserver(list=>callback(list.getEntries()));
      observer.observe({type,buffered:true,...extra});
      observers.push(observer);
    }catch{}
  };
  register('paint',entries=>{
    for(const item of entries){
      if(item.name==='first-contentful-paint')numbers.fcp=round(item.startTime);
    }
  });
  register('largest-contentful-paint',entries=>{
    for(const item of entries)numbers.lcp=round(item.startTime);
  });
  register('layout-shift',entries=>{
    for(const item of entries){
      if(!item.hadRecentInput)numbers.cls=round(numbers.cls+item.value);
    }
  });
  register('event',entries=>{
    for(const item of entries){
      if(!item.interactionId)continue;
      const duration=item.duration||0;
      const prev=observedInteractions.get(item.interactionId)||0;
      observedInteractions.set(item.interactionId,Math.max(duration,prev));
    }
    const durations=[...observedInteractions.values()].sort((a,b)=>b-a);
    if(durations.length){
      const ignored=Math.floor(durations.length/50);
      numbers.inp=round(durations[Math.min(ignored,durations.length-1)]);
    }
  },{durationThreshold:40});

  const collect = () => {
    const navigation=performance.getEntriesByType('navigation')[0];
    numbers.ttfb=navigation?round(navigation.responseStart):null;
    numbers.domContentLoaded=navigation?round(navigation.domContentLoadedEventEnd):null;
    const resources=performance.getEntriesByType('resource');
    const transferred=resources.reduce((sum,r)=>sum+(Number.isFinite(r.transferSize)?Math.max(0,r.transferSize):0),0);
    numbers.assetsKB=round(transferred/1024);
    return Object.freeze({...numbers,interactions:{...interactions}});
  };
  let reportTimer;
  const report = () => {
    clearTimeout(reportTimer);
    const snapshot=collect();
    // Prints aggregated performance numbers only; never prints page URL or user data.
    console.info('[BLINK performance diagnostics — local only]',snapshot);
  };
  const onHidden=()=>{if(document.visibilityState==='hidden')report();};
  document.addEventListener('visibilitychange',onHidden,{passive:true});
  reportTimer=setTimeout(report,12000);
  window.addEventListener('pagehide',()=>{
    clearTimeout(reportTimer);
    for(const observer of observers)observer.disconnect();
    observedInteractions.clear();
    document.removeEventListener('click',onClick,true);
    document.removeEventListener('submit',onSubmit,true);
  },{once:true});
})();

/* BLINK Phase 2: anonymous-safe campus discovery, guided feature tour, and public release details. */
(() => {
  'use strict';
  const ready = fn => document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', fn, {once:true}) : fn();
  const aliases = new Map([
    ['futa', 'Federal University of Technology, Akure'],
    ['futminna', 'Federal University of Technology, Minna'],
    ['futo', 'Federal University of Technology, Owerri'],
    ['unilag', 'University of Lagos'],
    ['ui', 'University of Ibadan'],
    ['uniben', 'University of Benin'],
    ['unilorin', 'University of Ilorin'],
    ['lasu', 'Lagos State University'],
    ['oau', 'Obafemi Awolowo University, Ile-Ife'],
    ['unn', 'University of Nigeria, Nsukka'],
    ['abu', 'Ahmadu Bello University, Zaria'],
    ['n o u n', 'National Open University of Nigeria']
  ]);
  const normalize = value => String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  let universitiesPromise;
  async function getUniversities() {
    if(!universitiesPromise) {
      const prefix = location.hostname.endsWith('github.io') ? '/Blinkng/' : '/';
      universitiesPromise = fetch(prefix + 'campuses.json', {headers:{Accept:'application/json'},signal:AbortSignal.timeout(10000)})
        .then(r=>{if(!r.ok)throw Error('Campus list unavailable');return r.json();})
        .then(v=>{if(!Array.isArray(v.universities)||v.universities.length<100)throw Error('Campus catalogue invalid');return v.universities;})
        .catch(err=>{universitiesPromise=null;throw err;});
    }
    return universitiesPromise;
  }
  function addResult(list,school,onChoose){
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'campus-option'; btn.textContent = school;
    btn.addEventListener('click',()=>onChoose(school));
    li.append(btn); list.append(li);
  }
  function initCampus(root) {
    const input = root.querySelector('#campus-search');
    const list = root.querySelector('#campus-results');
    const status = root.querySelector('#campus-status');
    const selection = root.querySelector('#campus-selection');
    if(!input||!list||!status||!selection)return;
    let request=0;
    const show = async()=>{
      const current=++request;
      status.textContent='Searching university catalogue…';
      let schools;try{schools=await getUniversities();}catch{if(current===request){status.textContent='Campus catalogue could not load. Try again.';list.replaceChildren();}return;}
      if(current!==request)return;
      const term=normalize(input.value);
      const expanded=aliases.get(term);
      const matched=schools.filter(s=>!term || normalize(s).includes(term) || (expanded && s === expanded)).slice(0,8);
      list.replaceChildren();
      matched.forEach(s=>addResult(list,s,school=>{
        selection.hidden=false;
        selection.querySelector('[data-selected-campus]').textContent=school;
        input.value=school;list.replaceChildren();
        status.textContent='Selected from BLINK\'s university catalogue.';
      }));
      status.textContent=matched.length ? (term ? 'Select a university below.' : 'Start typing to find your campus.') : 'No matching university in this catalogue.';
    };
    input.addEventListener('input',show);
    input.addEventListener('focus',show);
    status.textContent='Search the same Nigerian university list used by BLINK Android.';
  }
  const tour = {
    feed:{heading:'A space for real conversations',body:'Share a post, follow people you know and explore updates from BLINK accounts.',action:'Browse BLINK features',path:'/features/'},
    reels:{heading:'Short videos, big moments',body:'Watch reels and discover creators. Sign in to engage with content and share your own.',action:'Explore Reels',path:'/features/'},
    market:{heading:'Discover the BLINK marketplace',body:'Explore product listings and connect with sellers from inside the BLINK community. Seller posting requires approval.',action:'Learn about BLINK Market',path:'/features/'},
    connect:{heading:'Connections beyond the feed',body:'Explore university connections, reading partners and other opportunities available in BLINK Connect.',action:'Learn about Connect',path:'/features/'}
  };
  function initTour(root){
    const tabs=[...root.querySelectorAll('[data-tour-tab]')];
    const heading=root.querySelector('#tour-heading'),body=root.querySelector('#tour-body'),action=root.querySelector('#tour-action'),panel=root.querySelector('#tour-panel');
    if(!tabs.length||!heading||!body||!action)return;
    const render=key=>{
      const item=tour[key]||tour.feed;
      tabs.forEach(tab=>{
        const selected=tab.dataset.tourTab===key;
        tab.setAttribute('aria-selected',String(selected));
        tab.tabIndex=selected?0:-1;
      });
      if(panel)panel.setAttribute('aria-labelledby','tour-tab-'+key);
      heading.textContent=item.heading;body.textContent=item.body;action.textContent=item.action;
      action.setAttribute('href',(location.hostname.endsWith('github.io')?'/Blinkng':'')+item.path);
    };
    tabs.forEach((tab,i)=>{
      tab.addEventListener('click',()=>render(tab.dataset.tourTab));
      tab.addEventListener('keydown',e=>{
        if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;
        e.preventDefault();
        const next=e.key==='Home'?0:e.key==='End'?tabs.length-1:(i+(e.key==='ArrowRight'?1:-1)+tabs.length)%tabs.length;
        tabs[next].focus();render(tabs[next].dataset.tourTab);
      });
    });
    render('feed');
  }
  function initLanding(root=document) {initCampus(root);initTour(root);}
  async function initReleaseInfo(root=document) {
    const target=root.querySelector('#blink-release-info');
    if(!target)return;
    target.textContent='Checking the latest published version…';
    try {
      const r=await fetch('https://api.github.com/repos/Glimmar-code/Blinkng/releases/latest',{
        headers:{Accept:'application/vnd.github+json'},signal:AbortSignal.timeout(10000)});
      if(!r.ok)throw Error('Unavailable');
      const data=await r.json();
      if(data.draft || data.prerelease)throw Error('Not a production release');
      const asset=(data.assets||[]).find(v=>v.name==='Blink-latest.apk');
      if(!asset || !data.tag_name)throw Error('No published APK');
      const version=String(data.name||data.tag_name).slice(0,90);
      const mb=(asset.size/1000000).toFixed(1);
      target.textContent=`Latest published APK: ${version} · ${mb} MB (decimal)`;
    }catch{
      target.textContent='Latest version and file size: view official GitHub Releases.';
    }
  }
  window.BLINK_DISCOVERY = Object.freeze({initLanding,initReleaseInfo});
  ready(()=>initReleaseInfo());
})();

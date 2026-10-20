/* Mobile web polish. Keeps existing feed, auth, post actions and media observers. */
(() => {
 'use strict';
 const mobile=()=>matchMedia('(max-width:760px)').matches;
 function update(){
   if(!mobile())return;
   const shell=document.querySelector('.app-shell');
   if(!shell)return;
   const nav=shell.querySelector('.mobile-nav');
   if(nav && nav.dataset.blinkFiveTabs!=='1'){
     nav.dataset.blinkFiveTabs='1';
     const items=[['/feed','⌂','Home'],['/reels','▶','Reels'],['/market','▣','Market'],['/connect','◎','Connect'],['/messages','✉','Message']];
     nav.innerHTML=items.map(([route,icon,label])=>'<button type="button" data-nav="'+route+'" class="'+(location.pathname===route?'active':'')+'"><span>'+icon+'</span>'+label+'</button>').join('');
     nav.addEventListener('click',e=>{const button=e.target.closest('button[data-nav]');if(!button)return;const existing=shell.querySelector('.sidebar [data-nav="'+button.dataset.nav+'"]');if(existing)existing.click();else{history.pushState({},'',button.dataset.nav);window.dispatchEvent(new PopStateEvent('popstate'));}});
   }
   if(location.pathname==='/feed'){
     const mixed=shell.querySelector('.tabs [data-feed-mode="all"]');if(mixed)mixed.hidden=true;
     const stories=shell.querySelectorAll('[data-story-id],.story-strip,.stories');stories.forEach(el=>{if(el.closest('.content-wrap')&&!el.closest('.feed'))el.style.display='none';});
     if(!shell.querySelector('.blink-mobile-create')){
       const button=document.createElement('button');button.className='blink-mobile-create';button.type='button';button.textContent='+';button.setAttribute('aria-label','Create post');
       button.addEventListener('click',()=>{const source=shell.querySelector('[data-action="create-post"]');source?.click();});
       shell.append(button);
     }
   }else shell.querySelector('.blink-mobile-create')?.remove();
   if(location.pathname==='/reels'){
     shell.querySelectorAll('.reel-card video').forEach(video=>{
       if(video.dataset.blinkGestures)return;video.dataset.blinkGestures='1';
       video.addEventListener('click',()=>{if(video.paused)video.play().catch(()=>{});else video.pause();});
       let held=false,timer;video.addEventListener('pointerdown',()=>{timer=setTimeout(()=>{held=true;video.playbackRate=2;},450);});
       for(const event of ['pointerup','pointercancel','pointerleave'])video.addEventListener(event,()=>{clearTimeout(timer);if(held){video.playbackRate=1;held=false;}});
     });
   }
 }
 let pending=false;const schedule=()=>{if(pending)return;pending=true;requestAnimationFrame(()=>{pending=false;update();});};
 const observer=new MutationObserver(schedule);
 const start=()=>{observer.observe(document.body,{childList:true,subtree:true});schedule();};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start,{once:true});else start();
 window.addEventListener('popstate',schedule);window.addEventListener('resize',schedule);
})();

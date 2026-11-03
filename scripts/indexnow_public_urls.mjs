// BLINK IndexNow: public informational pages only. Never submit app routes,
// profiles, user-generated content, messages or account data to search engines.
export const SITE='https://blink.com.ng';
export const INDEXNOW_KEY='c7a33ad1df24b04816761dc5dae7fbfa';
export const INDEXNOW_KEY_LOCATION=SITE+'/'+INDEXNOW_KEY+'.txt';
export const APPROVED_PUBLIC_PATHS=Object.freeze([
 '/','/about/','/features/','/download/','/faq/','/privacy/',
 '/students/','/marketplace/','/safety/'
]);

export function selectChangedPublicUrls(changedFiles,{bootstrap=false}={}){
 if(!Array.isArray(changedFiles))throw new TypeError('Expected changed file names');
 const source=new Set(changedFiles);
 if(bootstrap||source.has('web/'+INDEXNOW_KEY+'.txt')){
  return APPROVED_PUBLIC_PATHS.map(p=>SITE+p);
 }
 return APPROVED_PUBLIC_PATHS.filter(p=>{
  const html=p==='/'?'web/index.html':'web'+p+'index.html';
  return source.has(html);
 }).map(p=>SITE+p);
}

export function validateIndexNowUrls(urls){
 if(!Array.isArray(urls)||urls.length>APPROVED_PUBLIC_PATHS.length)throw Error('Unexpected URL batch');
 const allowed=new Set(APPROVED_PUBLIC_PATHS.map(p=>SITE+p));
 const unique=new Set();
 for(const url of urls){
  if(typeof url!=='string'||!allowed.has(url)||unique.has(url))throw Error('Unapproved or repeated IndexNow URL');
  const u=new URL(url);
  if(u.origin!==SITE||u.search||u.hash||u.username||u.password)throw Error('Unsafe IndexNow URL');
  unique.add(url);
 }
 return urls;
}

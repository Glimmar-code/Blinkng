/**
 * Lazy-loaded Cloudflare R2 uploader for public BLINK web media.
 * No storage provider secret is exposed to the browser.
 * Returns null only when the server intentionally disabled R2.
 */
export async function tryUploadR2({bucket,file,baseUrl,anonKey,accessToken,fetchWithTimeout,parseJsonSafe}) {
  const purpose=bucket==='post-media'?(file.type.startsWith('video/')?'reel':'feed'):
    (bucket==='avatars'||bucket==='covers')?'profile':
    (bucket==='market-media'||bucket==='marketplace-media')?'market':null;
  if(!purpose)return null;
  const mime=(file.type||'').toLowerCase();
  const valid={
    feed:new Set(['image/jpeg','image/png','image/webp','image/heic']),
    reel:new Set(['video/mp4','video/webm']),
    profile:new Set(['image/jpeg','image/png','image/webp']),
    market:new Set(['image/jpeg','image/png','image/webp','video/mp4','video/webm'])
  };
  const max={feed:25000000,reel:80000000,profile:8000000,market:50000000};
  if(!valid[purpose].has(mime)||file.size<=0||file.size>max[purpose])return null;

  const gateway=`${baseUrl}/functions/v1/blink-r2-media`;
  async function call(payload) {
    const response=await fetchWithTimeout(gateway,{
      method:'POST',headers:{apikey:anonKey,Authorization:`Bearer ${accessToken}`,'Content-Type':'application/json'},
      body:JSON.stringify(payload)
    },30000);
    const data=await parseJsonSafe(response);
    return {status:response.status,ok:response.ok,data};
  }
  const init=await call({action:'init',purpose,mimeType:mime,bytes:file.size});
  const absent=init.status===503&&init.data?.error==='R2_DISABLED' ||
    init.status===404&&init.data?.code==='NOT_FOUND'&&
    init.data?.message==='Requested function was not found';
  if(!init.ok&&!absent)throw new Error('BLINK media upload could not start.');
  if(!init.ok)return null;

  const signed=new URL(init.data?.uploadUrl||'');
  if(signed.protocol!=='https:'||signed.username||signed.password||
     !signed.hostname.endsWith('.r2.cloudflarestorage.com'))
    throw new Error('Untrusted BLINK upload destination.');
  const uploaded=await fetchWithTimeout(signed.toString(),{
    method:'PUT',headers:{'Content-Type':mime},body:file
  },120000);
  if(!uploaded.ok)throw new Error(`BLINK media transfer failed (${uploaded.status}).`);

  const complete=await call({action:'complete',purpose,assetId:init.data.assetId});
  if(!complete.ok)throw new Error('BLINK media confirmation failed.');
  const published=new URL(complete.data?.publicUrl||'');
  if(published.protocol!=='https:'||published.username||published.password||
     (published.hostname!=='blink.com.ng'&&!published.hostname.endsWith('.blink.com.ng'))||
     published.search||published.hash)
    throw new Error('Untrusted BLINK media URL.');
  return published.toString();
}

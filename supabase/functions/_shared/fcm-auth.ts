/** Shared FCM OAuth helper. Runtime-neutral for Supabase Deno Edge and Node tests. */
export function base64UrlEncodeBytes(data: Uint8Array): string {
  return btoa(String.fromCharCode(...data)).replace(/=/g,"").replace(/\+/g,"-").replace(/\//g,"_");
}
export function normalizePrivateKeyPem(pem: string): Uint8Array {
  const normalized=pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----/g,"").replace(/\s/g,"");
  return Uint8Array.from(atob(normalized),c=>c.charCodeAt(0));
}
export async function fcmAccessToken(serviceAccount: Record<string,string>,fetcher: typeof fetch=fetch,nowSeconds=Math.floor(Date.now()/1000)): Promise<string> {
  const header=base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({alg:"RS256",typ:"JWT"})));
  const claims=base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({
    iss:serviceAccount.client_email,scope:"https://www.googleapis.com/auth/firebase.messaging",
    aud:"https://oauth2.googleapis.com/token",iat:nowSeconds,exp:nowSeconds+3600
  })));
  const key=await crypto.subtle.importKey("pkcs8",normalizePrivateKeyPem(serviceAccount.private_key),
    {name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
  const signature=base64UrlEncodeBytes(new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,new TextEncoder().encode(header+"."+claims))));
  const response=await fetcher("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},
    body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:header+"."+claims+"."+signature})});
  if(!response.ok)throw new Error("FCM_OAUTH_FAILED");
  const result=await response.json();
  if(!result.access_token)throw new Error("FCM_OAUTH_TOKEN_MISSING");
  return result.access_token;
}
let cachedToken:string|null=null,cacheExpires=0;
export async function cachedFcmAccessToken(serviceAccount:Record<string,string>):Promise<string>{
  const now=Date.now();
  if(cachedToken&&now<cacheExpires)return cachedToken;
  cachedToken=await fcmAccessToken(serviceAccount);
  cacheExpires=now+50*60*1000;
  return cachedToken;
}

/** Shared FCM OAuth helper. */
export function base64UrlEncodeBytes(data){return btoa(String.fromCharCode(...data)).replace(/=/g,"").replace(/\+/g,"-").replace(/\//g,"_");}
export function normalizePrivateKeyPem(pem){const s=pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----/g,"").replace(/\s/g,"");return Uint8Array.from(atob(s),c=>c.charCodeAt(0));}
export async function fcmAccessToken(sa,fetcher=fetch,now=Math.floor(Date.now()/1000)){
 const h=base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({alg:"RS256",typ:"JWT"})));
 const c=base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({iss:sa.client_email,scope:"https://www.googleapis.com/auth/firebase.messaging",aud:"https://oauth2.googleapis.com/token",iat:now,exp:now+3600})));
 const k=await crypto.subtle.importKey("pkcs8",normalizePrivateKeyPem(sa.private_key),{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
 const sig=base64UrlEncodeBytes(new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5",k,new TextEncoder().encode(h+"."+c))));
 const r=await fetcher("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:h+"."+c+"."+sig})});
 if(!r.ok)throw new Error("FCM_OAUTH_FAILED");const j=await r.json();if(!j.access_token)throw new Error("FCM_OAUTH_TOKEN_MISSING");return j.access_token;
}
let cached=null,expiry=0;
export async function cachedFcmAccessToken(sa){if(cached&&Date.now()<expiry)return cached;cached=await fcmAccessToken(sa);expiry=Date.now()+50*60*1000;return cached;}

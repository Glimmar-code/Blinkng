import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { fcmAccessToken } from "../_shared/fcm-auth.mjs";
const URL=Deno.env.get("SUPABASE_URL")!,KEY=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,FCM=Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
function b64(u:Uint8Array){return btoa(String.fromCharCode(...u)).replace(/=/g,"").replace(/\+/g,"-").replace(/\///g,"_")}

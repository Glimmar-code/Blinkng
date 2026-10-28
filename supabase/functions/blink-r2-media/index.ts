/**
 * BLINK media R2 signing gateway (server-side secrets only).
 *
 * Testlab rollout: disabled unless BLINK_R2_ENABLED=true and all R2 secrets
 * are configured. Old clients/Supabase Storage URLs remain valid.
 * NEVER use this endpoint for view-once images until the atomic claim/delete
 * service is explicitly ported and tested. No private bucket has a public URL.
 */
import { createClient } from "npm:@supabase/supabase-js@2";
import { S3Client, PutObjectCommand, GetObjectCommand, HeadObjectCommand } from "npm:@aws-sdk/client-s3@3.835.0";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3.835.0";
import { readBoundedJsonObject } from "./request-body.ts";

type Purpose = "feed" | "reel" | "profile" | "market" | "story" | "channel" | "chat";
const allowed: Record<Purpose, { bucket: string; maxBytes: number; mimes: readonly string[]; needsContext: boolean }> = {
  feed: { bucket:"blink-public-media", maxBytes: 25_000_000, mimes:["image/jpeg","image/png","image/webp","image/heic"], needsContext:false },
  reel: { bucket:"blink-public-media", maxBytes: 80_000_000, mimes:["video/mp4","video/webm"], needsContext:false },
  profile: { bucket:"blink-public-media", maxBytes: 8_000_000, mimes:["image/jpeg","image/png","image/webp"], needsContext:false },
  market: { bucket:"blink-public-media", maxBytes: 50_000_000, mimes:["image/jpeg","image/png","image/webp","video/mp4","video/webm"], needsContext:false },
  story: { bucket:"blink-temporary-media", maxBytes: 50_000_000, mimes:["image/jpeg","image/png","image/webp","video/mp4"], needsContext:false },
  channel: { bucket:"blink-private-media", maxBytes: 26_214_400, mimes:["image/jpeg","image/png","image/webp","video/mp4","audio/mp4","audio/mpeg","application/pdf"], needsContext:true },
  chat: { bucket:"blink-private-media", maxBytes: 83_886_080, mimes:["image/jpeg","image/png","image/webp","image/gif","video/mp4","video/webm","audio/mp4","audio/mpeg","application/pdf"], needsContext:true },
};
const extension: Record<string,string> = {
  "image/jpeg":"jpg","image/png":"png","image/webp":"webp","image/heic":"heic",
  "image/gif":"gif","video/mp4":"mp4","video/webm":"webm",
  "audio/mp4":"m4a","audio/mpeg":"mp3","application/pdf":"pdf",
};
const uuid = (v: unknown): v is string => typeof v === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
const error = (status: number, code: string, headers: Record<string,string>) =>
  new Response(JSON.stringify({ error: code }), { status, headers });
const json = (payload: unknown, headers: Record<string,string>, status = 200) =>
  new Response(JSON.stringify(payload), { status, headers });

Deno.serve(async req => {
  const origin = req.headers.get("origin") || "";
  const allowedOrigins = ["https://blink.com.ng", "https://www.blink.com.ng"];
  const headers: Record<string,string> = {
    "content-type":"application/json","cache-control":"no-store","vary":"Origin",
    "access-control-allow-methods":"POST,OPTIONS",
    "access-control-allow-headers":"authorization,apikey,content-type",
  };
  if (allowedOrigins.includes(origin)) headers["access-control-allow-origin"] = origin;
  if (req.method === "OPTIONS") return new Response(null, {status:204,headers});
  if (req.method !== "POST") return error(405,"METHOD_NOT_ALLOWED",headers);
  if (origin && !allowedOrigins.includes(origin)) return error(403,"ORIGIN_NOT_ALLOWED",headers);
  if (Deno.env.get("BLINK_R2_ENABLED") !== "true")
    return error(503,"R2_DISABLED",headers);

  const base = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const account = Deno.env.get("R2_ACCOUNT_ID");
  const access = Deno.env.get("R2_ACCESS_KEY_ID");
  const secret = Deno.env.get("R2_SECRET_ACCESS_KEY");
  const publicOrigin = Deno.env.get("BLINK_R2_PUBLIC_MEDIA_ORIGIN") || "";
  if (!base || !anon || !serviceKey || !account || !access || !secret)
    return error(503,"MEDIA_NOT_CONFIGURED",headers);
  if (!/^[a-f0-9]{32}$/i.test(account) || !/^[A-Za-z0-9._-]+$/.test(access) || !secret.trim())
    return error(503,"R2_CREDENTIAL_CONFIG_INVALID",headers);
  let publicBase: URL;
  try {
    publicBase = new URL(publicOrigin);
    if (publicBase.protocol !== "https:" || publicBase.username || publicBase.password ||
        publicBase.pathname !== "/" || publicBase.search || publicBase.hash) throw new Error();
  } catch {
    return error(503,"R2_PUBLIC_ORIGIN_NOT_CONFIGURED",headers);
  }

  const token = /^Bearer\s+(\S+)$/i.exec(req.headers.get("authorization") || "")?.[1];
  if (!token) return error(401,"SIGN_IN_REQUIRED",headers);
  const admin = createClient(base, serviceKey, { auth: { autoRefreshToken:false, persistSession:false } });
  const { data: verified, error: verifyError } = await admin.auth.getUser(token);
  const userId = verifyError ? undefined : verified.user?.id;
  if (!userId) return error(401,"INVALID_SESSION",headers);
  const parsed = await readBoundedJsonObject(req);
  if (!parsed.ok) return error(
    parsed.error === "REQUEST_TOO_LARGE" ? 413 : 400, parsed.error, headers,
  );
  const input = parsed.value;
  if (typeof input.action !== "string") return error(400,"INVALID_REQUEST",headers);

  const s3 = new S3Client({
    region:"auto", endpoint:`https://${account}.r2.cloudflarestorage.com`,
    credentials:{ accessKeyId:access, secretAccessKey:secret },
  });
  // The service-role client is restricted to internal ledger writes and
  // ownership/membership checks; it is never sent to Android, Windows or web.
  async function mayAccess(purpose: Purpose, context: string | null): Promise<boolean> {
    if (purpose === "chat") {
      if (!uuid(context)) return false;
      const {data,error} = await admin.from("conversation_participants")
        .select("user_id").eq("conversation_id",context).eq("user_id",userId).is("left_at",null)
        .maybeSingle();
      return !error && !!data;
    }
    if (purpose === "channel") {
      if (!uuid(context)) return false;
      const {data:channel,error} = await admin.from("blink_communities")
        .select("owner_id").eq("id",context).maybeSingle();
      if (error || !channel) return false;
      if (channel.owner_id === userId) return true;
      const {data:member, error:memberError} = await admin.from("blink_community_members")
        .select("role,status").eq("community_id",context).eq("user_id",userId)
        .maybeSingle();
      return !memberError && member?.status === "active" &&
        ["owner","admin","moderator"].includes(member.role);
    }
    return true;
  }
  if (typeof input.purpose !== "string" || !Object.hasOwn(allowed,input.purpose))
    return error(400,"INVALID_PURPOSE",headers);
  // Subscribers may read a channel attachment, but only permitted
  // contributors are allowed to create one.
  async function mayReadChannel(context: string | null): Promise<boolean> {
    if (!uuid(context)) return false;
    const {data:channel,error} = await admin.from("blink_communities")
      .select("owner_id").eq("id",context).maybeSingle();
    if (error || !channel) return false;
    if (channel.owner_id === userId) return true;
    const {data:member, error:memberError} = await admin.from("blink_community_members")
      .select("status").eq("community_id",context).eq("user_id",userId)
      .maybeSingle();
    return !memberError && member?.status === "active";
  }
  const purpose = input.purpose as Purpose;
  const spec = allowed[purpose];

  try {
    if (input.action === "init") {
      const mime = input.mimeType;
      const bytes = input.bytes;
      const context = input.contextId === null || input.contextId === undefined ? null : input.contextId;
      if (typeof mime !== "string" || !spec.mimes.includes(mime) ||
          typeof bytes !== "number" || !Number.isSafeInteger(bytes) ||
          bytes < 1 || bytes > spec.maxBytes ||
          (spec.needsContext && !uuid(context)) ||
          (context !== null && !uuid(context)))
        return error(400,"MEDIA_TYPE_OR_SIZE_NOT_ALLOWED",headers);
      if (!await mayAccess(purpose, context as string | null))
        return error(403,"MEDIA_SCOPE_FORBIDDEN",headers);

      // A per-user cap limits accidental loops and automated upload abuse.
      const since = new Date(Date.now() - 86_400_000).toISOString();
      const {count, error:quotaError} = await admin.from("blink_r2_uploads")
        .select("id",{head:true,count:"exact"}).eq("owner_id",userId).gte("created_at",since);
      if (quotaError) return error(503,"UPLOAD_LEDGER_UNAVAILABLE",headers);
      if ((count ?? 0) >= 200) return error(429,"DAILY_UPLOAD_LIMIT",headers);
      const assetId = crypto.randomUUID();
      const key = `${purpose}/${userId}/${assetId}.${extension[mime]}`;
      const {error:insertError} = await admin.from("blink_r2_uploads").insert({
        id:assetId,owner_id:userId,purpose,context_id:context,
        bucket:spec.bucket,object_key:key,mime_type:mime,bytes_expected:bytes,
      });
      if (insertError) return error(503,"UPLOAD_LEDGER_UNAVAILABLE",headers);
      const signed = await getSignedUrl(s3,new PutObjectCommand({
        Bucket:spec.bucket,Key:key,ContentType:mime,
      }),{expiresIn:120});
      return json({assetId, uploadUrl:signed, method:"PUT", contentType:mime, expiresIn:120},headers);
    }

    if (input.action === "complete" || input.action === "read") {
      if (!uuid(input.assetId)) return error(400,"INVALID_ASSET",headers);
      const {data:asset,error:rowError} = await admin.from("blink_r2_uploads")
        .select("*").eq("id",input.assetId).maybeSingle();
      if (rowError || !asset || asset.purpose !== purpose)
        return error(404,"ASSET_NOT_FOUND",headers);
      if (input.action === "complete") {
        if (asset.owner_id !== userId || asset.state === "rejected")
          return error(403,"MEDIA_SCOPE_FORBIDDEN",headers);
        if (!await mayAccess(purpose,asset.context_id))
          return error(403,"MEDIA_SCOPE_FORBIDDEN",headers);
        const info = await s3.send(new HeadObjectCommand({
          Bucket:asset.bucket,Key:asset.object_key,
        }));
        if (!info.ContentLength || info.ContentLength !== asset.bytes_expected ||
            info.ContentType?.split(";")[0]?.toLowerCase() !== asset.mime_type) {
          await admin.from("blink_r2_uploads").update({state:"rejected"}).eq("id",asset.id);
          return error(422,"UPLOADED_MEDIA_MISMATCH",headers);
        }
        const {error:doneError} = await admin.from("blink_r2_uploads")
          .update({state:"ready",bytes_confirmed:info.ContentLength,
            completed_at:new Date().toISOString()})
          .eq("id",asset.id).eq("owner_id",userId);
        if (doneError) return error(503,"UPLOAD_LEDGER_UNAVAILABLE",headers);
        const isPublic = asset.bucket === "blink-public-media";
        return json({assetId:asset.id, ref:`r2:${asset.id}`,
          publicUrl:isPublic ? new URL(asset.object_key,publicBase).toString() : null,
          private:!isPublic},headers);
      }
      if (asset.state !== "ready" || asset.bucket === "blink-public-media")
        return error(404,"ASSET_NOT_AVAILABLE",headers);
      // Reading chat/channel media additionally requires a linked, visible
      // message/update: mere knowledge of an asset ID grants nothing.
      if (purpose === "chat") {
        const {data:link} = await admin.from("messages").select("id")
          .eq("conversation_id",asset.context_id).eq("media_url",`r2:${asset.id}`)
          .eq("deleted_for_everyone",false).limit(1).maybeSingle();
        if (!link || !await mayAccess("chat",asset.context_id))
          return error(403,"MEDIA_NOT_SHARED",headers);
      } else if (purpose === "channel") {
        const {data:link} = await admin.from("blink_channel_updates").select("id")
          .eq("channel_id",asset.context_id).eq("media_path",`r2:${asset.id}`)
          .is("removed_at",null).limit(1).maybeSingle();
        if (!link || !await mayReadChannel(asset.context_id))
          return error(403,"MEDIA_NOT_SHARED",headers);
      } else if (purpose === "story") {
        // Story viewer entitlement still needs a dedicated privacy gate;
        // during the staged rollout only the uploader can read.
        if (asset.owner_id !== userId) return error(403,"STORY_ACCESS_NOT_READY",headers);
      } else return error(403,"MEDIA_SCOPE_FORBIDDEN",headers);
      const url = await getSignedUrl(s3,new GetObjectCommand({
        Bucket:asset.bucket,Key:asset.object_key,
      }),{expiresIn:60});
      return json({downloadUrl:url,expiresIn:60,cache:"private-no-store"},headers);
    }
    return error(400,"INVALID_ACTION",headers);
  } catch (e) {
    // Never leak AWS request details, signed tokens, private keys, or paths.
    console.error("R2 media operation failed", e instanceof Error ? e.name : "unknown");
    return error(503,"MEDIA_BACKEND_UNAVAILABLE",headers);
  }
});

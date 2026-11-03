import { createClient } from "npm:@supabase/supabase-js@2";

const jsonHeaders = {
  "content-type": "application/json",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization,apikey,content-type",
  "access-control-allow-methods": "GET,POST,OPTIONS",
};
const reply = (status: number, error: string) =>
  new Response(JSON.stringify({ error }), { status, headers: jsonHeaders });
const uuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

const accepted = new Map([
  ["image/jpeg", "jpg"], ["image/png", "png"], ["image/webp", "webp"],
  ["image/heic", "heic"], ["image/heif", "heif"],
]);
function realImageMime(content: Uint8Array, declared: string): boolean {
  if (declared === "image/jpeg") return content[0] === 0xff && content[1] === 0xd8 && content[2] === 0xff;
  if (declared === "image/png")
    return [137,80,78,71,13,10,26,10].every((v,i)=>content[i]===v);
  if (declared === "image/webp") {
    return new TextDecoder().decode(content.slice(0,4)) === "RIFF" &&
      new TextDecoder().decode(content.slice(8,12)) === "WEBP";
  }
  if (declared === "image/heic" || declared === "image/heif") {
    const brand = new TextDecoder().decode(content.slice(8,12));
    return new TextDecoder().decode(content.slice(4,8)) === "ftyp" &&
      ["heic","heix","hevc","mif1","msf1","heif"].includes(brand);
  }
  return false;
}

async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response(null, {status:204,headers:jsonHeaders});
  if (!["POST","GET"].includes(req.method)) return reply(405, "METHOD_NOT_ALLOWED");

  const base = Deno.env.get("SUPABASE_URL");
  const adminKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const token = /^Bearer\s+(\S+)$/i.exec(req.headers.get("authorization") || "")?.[1];
  if (!base || !adminKey || !anon) return reply(503, "MEDIA_NOT_CONFIGURED");
  if (!token) return reply(401, "SIGN_IN_REQUIRED");

  const svc = createClient(base, adminKey, {
    auth: {persistSession:false,autoRefreshToken:false},
  });
  const {data: verified, error: authError} = await svc.auth.getUser(token);
  const user = authError ? null : verified.user;
  if (!user) return reply(401, "SESSION_INVALID");

  if (req.method === "GET") {
    const messageId = new URL(req.url).searchParams.get("message_id");
    if (!uuid(messageId)) return reply(400, "INVALID_MESSAGE");
    // This service-role-only RPC atomically claims at most one viewing slot,
    // and independently checks that user is a participant and not the sender.
    const {data, error} = await svc.rpc("blink_claim_view_once_image", {
      p_message_id:messageId,p_viewer_id:user.id,
    });
    if (error) return reply(503, "VIEW_UNAVAILABLE");
    const row = Array.isArray(data) ? data[0] : null;
    if (!row?.path || !accepted.has(row.mime_type)) return reply(410, "ALREADY_VIEWED");
    const {data: blob, error: downloadError} =
      await svc.storage.from("message-view-once").download(row.path);
    if (downloadError || !blob) return reply(410, "PICTURE_NOT_AVAILABLE");
    const bytes = await blob.arrayBuffer();
    // Delete after the one authorized read; the DB claim already prohibits replay.
    await svc.storage.from("message-view-once").remove([row.path]);
    return new Response(bytes, {
      status:200,
      headers:{
        "content-type":row.mime_type,
        "content-disposition":"inline",
        "cache-control":"no-store, no-cache, must-revalidate, private",
        "pragma":"no-cache",
        "x-content-type-options":"nosniff",
        "access-control-allow-origin":"*",
        "content-security-policy":"default-src 'none'",
      },
    });
  }

  try {
    const type = req.headers.get("content-type") || "";
    if (!type.toLowerCase().startsWith("multipart/form-data"))
      return reply(415, "IMAGE_REQUIRED");
    const fields = await req.formData();
    const conversationId = fields.get("conversation_id");
    const messageId = fields.get("message_id");
    const photo = fields.get("photo");
    if (!uuid(conversationId) || !uuid(messageId) || !(photo instanceof File))
      return reply(400, "BAD_UPLOAD_FIELDS");
    if (!accepted.has(photo.type) || photo.size < 1 || photo.size > 15*1024*1024)
      return reply(413, "PHOTO_NOT_ALLOWED");
    const bytes = new Uint8Array(await photo.arrayBuffer());
    if (!realImageMime(bytes, photo.type)) return reply(415, "PHOTO_CONTENT_INVALID");

    // Never use service credentials to authorize the caller. Membership must
    // be checked explicitly and inserts still go through the user's RLS.
    const {data: participants, error: participantError} = await svc
      .from("conversation_participants").select("user_id")
      .eq("conversation_id", conversationId).limit(3);
    if (participantError || participants?.length !== 2 ||
        !participants.some((entry) => entry.user_id === user.id))
      return reply(403, "NOT_A_DIRECT_CHAT_MEMBER");

    const peer = participants.find((entry) => entry.user_id !== user.id)?.user_id;
    if (!uuid(peer)) return reply(403, "INVALID_RECIPIENT");
    // RLS on messages INSERT checks sender membership, but doesn't enforce
    // direct-chat type or user blocks. Verify both using trusted server data.
    const {data: chat, error: chatError} = await svc.from("conversations")
      .select("id,is_group").eq("id", conversationId).maybeSingle();
    if (chatError || !chat || chat.is_group !== false)
      return reply(403, "DIRECT_CHAT_REQUIRED");
    const {data: blocked, error: blockError} = await svc.from("blocks")
      .select("blocker_id")
      .or(`and(blocker_id.eq.${user.id},blocked_id.eq.${peer}),and(blocker_id.eq.${peer},blocked_id.eq.${user.id})`)
      .limit(1);
    if (blockError || !blocked || blocked.length !== 0)
      return reply(403, "RECIPIENT_UNAVAILABLE");

    const userClient = createClient(base, anon, {
      auth:{persistSession:false,autoRefreshToken:false},
      global:{headers:{Authorization:`Bearer ${token}`}},
    });
    const id = messageId.toLowerCase();
    const {data: existing} = await userClient.from("messages")
      .select("id,sender_id,conversation_id,image_view_once").eq("id",id).maybeSingle();
    if (existing) {
      if (existing.sender_id === user.id && existing.conversation_id === conversationId &&
          existing.image_view_once === true)
        return new Response(JSON.stringify({messageId:id,alreadySent:true}),
          {status:200,headers:jsonHeaders});
      return reply(409, "MESSAGE_ID_ALREADY_USED");
    }

    const extension = accepted.get(photo.type);
    const objectPath = `conversations/${conversationId}/users/${user.id}/${id}.${extension}`;
    const {error: uploadError} = await svc.storage.from("message-view-once")
      .upload(objectPath, bytes, {upsert:false,contentType:photo.type});
    if (uploadError) return reply(503, "PRIVATE_UPLOAD_FAILED");
    const caption = String(fields.get("caption") || "").slice(0,1024);
    const {error: insertError} = await userClient.from("messages").insert({
      id,conversation_id:conversationId,sender_id:user.id,
      content: caption || "View once photo",message_type:"image",
      media_url:null,is_read:false,image_view_once:true,
    });
    if (insertError) {
      await svc.storage.from("message-view-once").remove([objectPath]);
      return reply(403, "MESSAGE_NOT_ALLOWED");
    }
    const {data: registered, error: registrationError} =
      await svc.rpc("blink_register_view_once_image",{
        p_message_id:id,p_conversation_id:conversationId,p_sender_id:user.id,
        p_storage_path:objectPath,p_mime_type:photo.type,
      });
    if (registrationError || registered !== true) {
      await svc.from("messages").delete().eq("id",id).eq("sender_id",user.id);
      await svc.storage.from("message-view-once").remove([objectPath]);
      return reply(503, "PRIVATE_MESSAGE_NOT_REGISTERED");
    }
    return new Response(JSON.stringify({messageId:id}),
      {status:201,headers:jsonHeaders});
  } catch (_err) {
    return reply(503, "VIEW_ONCE_UNAVAILABLE");
  }
}
Deno.serve(handle);

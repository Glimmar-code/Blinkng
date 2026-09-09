import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
const MAX_VIDEO_BYTES = 100_000_000;
const GEMINI_MODEL = "gemini-2.5-flash";

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}
function bearer(req: Request) {
  return req.headers.get("authorization")?.replace(/^Bearer\s+/i, "").trim() || "";
}
function config() {
  const url = Deno.env.get("SUPABASE_URL")?.trim().replace(/\/$/, "") || "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY")?.trim() || "";
  const gemini = Deno.env.get("GEMINI_API_KEY")?.trim() || "";
  if (!url || !service || !anon) throw new Error("Supabase runtime configuration is unavailable.");
  if (!gemini) throw new Error("Gemini media indexing is not configured.");
  return { url, service, anon, gemini };
}
async function verifyUser(jwt: string, url: string, anon: string) {
  if (!jwt) return false;
  const result = await fetch(`${url}/auth/v1/user`, { headers: { apikey: anon, Authorization: `Bearer ${jwt}` } });
  return result.ok;
}
async function adminFetch(path: string, init: RequestInit = {}) {
  const { url, service } = config();
  return await fetch(`${url}${path}`, {
    ...init,
    headers: { apikey: service, Authorization: `Bearer ${service}`, Accept: "application/json", ...(init.headers || {}) },
  });
}
function safeHttps(value: string): URL | null {
  try { const url = new URL(value); return url.protocol === "https:" ? url : null; } catch { return null; }
}
async function updateJob(postId: string, changes: Record<string, unknown>) {
  const result = await adminFetch(`/rest/v1/reel_search_index_jobs?post_id=eq.${encodeURIComponent(postId)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ ...changes, updated_at: new Date().toISOString() }),
  });
  if (!result.ok) throw new Error(`Could not update reel indexing job (${result.status}).`);
}

async function downloadVideo(urlText: string) {
  const url = safeHttps(urlText);
  if (!url) throw new Error("Only HTTPS reels can be indexed.");
  const media = await fetch(url, { redirect: "follow" });
  if (!media.ok) throw new Error(`Reel download failed with HTTP ${media.status}.`);
  const mime = (media.headers.get("content-type") || "video/mp4").split(";")[0].trim().toLowerCase();
  if (!mime.startsWith("video/")) throw new Error("Indexed media is not a video.");
  const length = Number(media.headers.get("content-length") || "0");
  if (Number.isFinite(length) && length > MAX_VIDEO_BYTES) throw new Error("Reel exceeds the transcript indexing size limit.");
  const bytes = new Uint8Array(await media.arrayBuffer());
  if (bytes.byteLength === 0 || bytes.byteLength > MAX_VIDEO_BYTES) throw new Error("Reel size is invalid.");
  return { bytes, mime };
}

async function uploadGeminiFile(bytes: Uint8Array, mime: string, apiKey: string) {
  const start = await fetch(`https://generativelanguage.googleapis.com/upload/v1beta/files?key=${encodeURIComponent(apiKey)}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Upload-Protocol": "resumable",
      "X-Goog-Upload-Command": "start",
      "X-Goog-Upload-Header-Content-Length": String(bytes.byteLength),
      "X-Goog-Upload-Header-Content-Type": mime,
    },
    body: JSON.stringify({ file: { display_name: `blink-search-reel-${crypto.randomUUID()}` } }),
  });
  if (!start.ok) throw new Error(`Gemini upload start failed (${start.status}).`);
  const uploadUrl = start.headers.get("x-goog-upload-url");
  if (!uploadUrl) throw new Error("Gemini did not return an upload URL.");
  const upload = await fetch(uploadUrl, {
    method: "POST",
    headers: {
      "Content-Length": String(bytes.byteLength),
      "X-Goog-Upload-Offset": "0",
      "X-Goog-Upload-Command": "upload, finalize",
    },
    body: bytes,
  });
  const payload = await upload.json().catch(() => ({}));
  if (!upload.ok) throw new Error(`Gemini upload failed (${upload.status}).`);
  const file = payload?.file ?? payload;
  const name = String(file?.name || "");
  const uri = String(file?.uri || "");
  if (!name || !uri) throw new Error("Gemini file response was incomplete.");
  return { name, uri, mime };
}

async function waitForGeminiFile(file: { name: string; uri: string; mime: string }, apiKey: string) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const status = await fetch(`https://generativelanguage.googleapis.com/v1beta/${file.name}?key=${encodeURIComponent(apiKey)}`);
    const payload = await status.json().catch(() => ({}));
    if (!status.ok) throw new Error(`Gemini file status failed (${status.status}).`);
    const state = String(payload?.state || "").toUpperCase();
    if (state === "ACTIVE" || !state) return { ...file, uri: String(payload?.uri || file.uri) };
    if (state === "FAILED") throw new Error("Gemini could not process this reel.");
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error("Reel processing timed out.");
}

function extractText(payload: any) {
  const parts = payload?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return "";
  return parts.map((part: any) => typeof part?.text === "string" ? part.text : "").filter(Boolean).join("\n");
}
function parseSegments(rawText: string) {
  const clean = rawText.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "");
  const parsed = JSON.parse(clean);
  const source = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.segments) ? parsed.segments : [];
  const rows: Array<{ start_ms: number; end_ms: number; transcript: string; language: string; confidence: number | null }> = [];
  for (const item of source.slice(0, 500)) {
    const start = Math.max(0, Math.round(Number(item?.start_ms)));
    const end = Math.max(start + 1, Math.round(Number(item?.end_ms)));
    const transcript = String(item?.transcript || "").replace(/\s+/g, " ").trim().slice(0, 1600);
    if (!Number.isFinite(start) || !Number.isFinite(end) || !transcript) continue;
    rows.push({
      start_ms: start,
      end_ms: end,
      transcript,
      language: String(item?.language || "und").slice(0, 20),
      confidence: Number.isFinite(Number(item?.confidence)) ? Math.max(0, Math.min(1, Number(item.confidence))) : null,
    });
  }
  if (!rows.length) throw new Error("No searchable speech or text segments were found in this reel.");
  return rows;
}

async function transcribe(file: { uri: string; mime: string }, apiKey: string) {
  const prompt = [
    "Create a timestamped search transcript for this Blink reel.",
    "Return ONLY valid JSON with this exact shape: {\"segments\":[{\"start_ms\":0,\"end_ms\":1000,\"transcript\":\"...\",\"language\":\"en\",\"confidence\":0.9}]}",
    "Use the actual video timeline. Include spoken words and concise visible on-screen text that a user could reasonably search for.",
    "Keep segments short, ordered, non-overlapping where practical, and never invent words that are not audible or visible.",
    "All timestamps must be integer milliseconds from the beginning of the reel.",
  ].join("\n");
  const request = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${encodeURIComponent(apiKey)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ file_data: { mime_type: file.mime, file_uri: file.uri } }, { text: prompt }] }],
      generationConfig: { temperature: 0, responseMimeType: "application/json" },
    }),
  });
  const payload = await request.json().catch(() => ({}));
  if (!request.ok) throw new Error(`Gemini transcript request failed (${request.status}).`);
  return parseSegments(extractText(payload));
}

async function replaceSegments(postId: string, rows: ReturnType<typeof parseSegments>) {
  const remove = await adminFetch(`/rest/v1/reel_transcript_segments?post_id=eq.${encodeURIComponent(postId)}`, { method: "DELETE", headers: { Prefer: "return=minimal" } });
  if (!remove.ok) throw new Error(`Could not replace the old reel transcript (${remove.status}).`);
  const insert = await adminFetch("/rest/v1/reel_transcript_segments", {
    method: "POST",
    headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify(rows.map((row) => ({ post_id: postId, ...row }))),
  });
  if (!insert.ok) throw new Error(`Could not save the reel transcript (${insert.status}): ${await insert.text()}`);
}

async function deleteGeminiFile(name: string, apiKey: string) {
  if (!name) return;
  await fetch(`https://generativelanguage.googleapis.com/v1beta/${name}?key=${encodeURIComponent(apiKey)}`, { method: "DELETE" }).catch(() => undefined);
}

async function processJob(job: any) {
  const postId = String(job.post_id || "");
  const attempts = Number(job.attempts || 0) + 1;
  if (!postId) return { ok: false, error: "Invalid reel job" };
  await updateJob(postId, { status: "running", attempts, locked_at: new Date().toISOString(), last_error: null });
  let uploadedName = "";
  try {
    const { gemini } = config();
    const video = await downloadVideo(String(job.video_url || ""));
    const uploaded = await uploadGeminiFile(video.bytes, video.mime, gemini);
    uploadedName = uploaded.name;
    const ready = await waitForGeminiFile(uploaded, gemini);
    const rows = await transcribe(ready, gemini);
    await replaceSegments(postId, rows);
    await updateJob(postId, { status: "done", locked_at: null, completed_at: new Date().toISOString(), last_error: null });
    return { ok: true, post_id: postId, segments: rows.length };
  } catch (error) {
    const message = String(error instanceof Error ? error.message : error).slice(0, 700);
    const terminal = attempts >= 4;
    const retryAt = new Date(Date.now() + Math.min(120, 2 ** attempts * 3) * 60_000).toISOString();
    await updateJob(postId, { status: terminal ? "failed" : "pending", locked_at: null, next_attempt_at: retryAt, last_error: message }).catch(() => undefined);
    return { ok: false, post_id: postId, error: message };
  } finally {
    const gemini = Deno.env.get("GEMINI_API_KEY")?.trim() || "";
    if (uploadedName && gemini) await deleteGeminiFile(uploadedName, gemini);
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: JSON_HEADERS });
  if (req.method !== "POST") return response({ error: "Method not allowed." }, 405);
  try {
    const { url, anon } = config();
    if (!(await verifyUser(bearer(req), url, anon))) return response({ error: "A signed-in Blink account is required." }, 401);
    const body = await req.json().catch(() => ({}));
    const limit = Math.max(1, Math.min(Number(body?.limit || 1), 2));
    const now = new Date().toISOString();
    const jobsResponse = await adminFetch(`/rest/v1/reel_search_index_jobs?select=post_id,video_url,attempts&status=eq.pending&next_attempt_at=lte.${encodeURIComponent(now)}&order=created_at.asc&limit=${limit}`);
    if (!jobsResponse.ok) return response({ error: "Could not load reel indexing jobs." }, 503);
    const jobs = await jobsResponse.json().catch(() => []);
    const results = [];
    for (const job of Array.isArray(jobs) ? jobs : []) results.push(await processJob(job));
    return response({ processed: results.length, succeeded: results.filter((item) => item.ok).length, results });
  } catch (error) {
    console.error("search-reel-indexer", error);
    return response({ error: "Reel search indexing is temporarily unavailable." }, 500);
  }
});

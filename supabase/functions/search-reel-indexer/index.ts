import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
const MAX_VIDEO_BYTES = 100_000_000;
const GEMINI_MODEL = "gemini-2.5-flash";
const MAX_JOBS = 2;

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
  try {
    const result = await fetch(`${url}/auth/v1/user`, {
      headers: { apikey: anon, Authorization: `Bearer ${jwt}` },
      signal: AbortSignal.timeout(10_000),
    });
    return result.ok;
  } catch {
    return false;
  }
}

async function adminFetch(path: string, init: RequestInit = {}) {
  const { url, service } = config();
  return await fetch(`${url}${path}`, {
    ...init,
    headers: {
      apikey: service,
      Authorization: `Bearer ${service}`,
      Accept: "application/json",
      ...(init.headers || {}),
    },
    signal: init.signal ?? AbortSignal.timeout(20_000),
  });
}

function safeHttps(value: string): URL | null {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password) return null;
    return url;
  } catch {
    return null;
  }
}

async function readBounded(response: Response, maxBytes: number) {
  if (!response.body) throw new Error("Reel response was empty.");
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel();
        throw new Error("Reel exceeds the transcript indexing size limit.");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

async function downloadVideo(urlText: string) {
  const url = safeHttps(urlText);
  if (!url) throw new Error("Only HTTPS reels can be indexed.");
  const media = await fetch(url, { redirect: "follow", signal: AbortSignal.timeout(120_000) });
  if (!media.ok) throw new Error(`Reel download failed with HTTP ${media.status}.`);
  if (!safeHttps(media.url)) throw new Error("Reel redirect did not remain HTTPS.");
  const mime = (media.headers.get("content-type") || "video/mp4").split(";")[0].trim().toLowerCase();
  if (!mime.startsWith("video/")) throw new Error("Indexed media is not a video.");
  const length = Number(media.headers.get("content-length") || "0");
  if (Number.isFinite(length) && length > MAX_VIDEO_BYTES) throw new Error("Reel exceeds the transcript indexing size limit.");
  const bytes = await readBounded(media, MAX_VIDEO_BYTES);
  if (bytes.byteLength === 0) throw new Error("Reel size is invalid.");
  return { bytes, mime };
}

function safeGeminiUploadUrl(value: string) {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.hostname !== "generativelanguage.googleapis.com") return null;
    return url;
  } catch {
    return null;
  }
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
    signal: AbortSignal.timeout(30_000),
  });
  if (!start.ok) throw new Error(`Gemini upload start failed (${start.status}).`);
  const uploadUrl = safeGeminiUploadUrl(start.headers.get("x-goog-upload-url") || "");
  if (!uploadUrl) throw new Error("Gemini did not return a valid upload URL.");
  const upload = await fetch(uploadUrl, {
    method: "POST",
    headers: {
      "Content-Length": String(bytes.byteLength),
      "X-Goog-Upload-Offset": "0",
      "X-Goog-Upload-Command": "upload, finalize",
    },
    body: bytes,
    signal: AbortSignal.timeout(120_000),
  });
  const payload = await upload.json().catch(() => ({}));
  if (!upload.ok) throw new Error(`Gemini upload failed (${upload.status}).`);
  const file = payload?.file ?? payload;
  const name = String(file?.name || "");
  const uri = String(file?.uri || "");
  if (!name || !uri || !name.startsWith("files/")) throw new Error("Gemini file response was incomplete.");
  return { name, uri, mime };
}

async function waitForGeminiFile(file: { name: string; uri: string; mime: string }, apiKey: string) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const status = await fetch(`https://generativelanguage.googleapis.com/v1beta/${file.name}?key=${encodeURIComponent(apiKey)}`, {
      signal: AbortSignal.timeout(15_000),
    });
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
    const rawStart = Number(item?.start_ms);
    const rawEnd = Number(item?.end_ms);
    if (!Number.isFinite(rawStart) || !Number.isFinite(rawEnd)) continue;
    const start = Math.max(0, Math.round(rawStart));
    const end = Math.max(start + 1, Math.round(rawEnd));
    const transcript = String(item?.transcript || "").replace(/\s+/g, " ").trim().slice(0, 1600);
    if (!transcript) continue;
    const rawConfidence = item?.confidence == null ? NaN : Number(item.confidence);
    rows.push({
      start_ms: start,
      end_ms: end,
      transcript,
      language: String(item?.language || "und").slice(0, 20),
      confidence: Number.isFinite(rawConfidence) ? Math.max(0, Math.min(1, rawConfidence)) : null,
    });
  }
  rows.sort((a, b) => a.start_ms - b.start_ms || a.end_ms - b.end_ms);
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
    signal: AbortSignal.timeout(120_000),
  });
  const payload = await request.json().catch(() => ({}));
  if (!request.ok) throw new Error(`Gemini transcript request failed (${request.status}).`);
  return parseSegments(extractText(payload));
}

function jobFilter(postId: string, lease: string) {
  return `post_id=eq.${encodeURIComponent(postId)}&status=eq.running&locked_at=eq.${encodeURIComponent(lease)}`;
}

async function claimJob(candidate: any) {
  const postId = String(candidate?.post_id || "");
  if (!postId) return null;
  const lease = new Date().toISOString();
  const result = await adminFetch(
    `/rest/v1/reel_search_index_jobs?post_id=eq.${encodeURIComponent(postId)}&status=eq.pending&next_attempt_at=lte.${encodeURIComponent(lease)}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json", Prefer: "return=representation" },
      body: JSON.stringify({
        status: "running",
        attempts: Number(candidate.attempts || 0) + 1,
        locked_at: lease,
        last_error: null,
        updated_at: lease,
      }),
    },
  );
  if (!result.ok) throw new Error(`Could not claim reel indexing job (${result.status}).`);
  const rows = await result.json().catch(() => []);
  return Array.isArray(rows) && rows.length ? { ...rows[0], lease } : null;
}

async function updateJob(postId: string, lease: string, changes: Record<string, unknown>) {
  const result = await adminFetch(`/rest/v1/reel_search_index_jobs?${jobFilter(postId, lease)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({ ...changes, updated_at: new Date().toISOString() }),
  });
  if (!result.ok) throw new Error(`Could not update reel indexing job (${result.status}).`);
  const rows = await result.json().catch(() => []);
  return Array.isArray(rows) && rows.length > 0;
}

async function hasCurrentLease(job: any) {
  const query = `/rest/v1/reel_search_index_jobs?select=post_id&post_id=eq.${encodeURIComponent(job.post_id)}&status=eq.running&locked_at=eq.${encodeURIComponent(job.lease)}&video_url=eq.${encodeURIComponent(job.video_url)}&limit=1`;
  const result = await adminFetch(query);
  if (!result.ok) throw new Error("Could not verify the reel indexing lease.");
  const rows = await result.json().catch(() => []);
  return Array.isArray(rows) && rows.length > 0;
}

async function replaceSegments(postId: string, rows: ReturnType<typeof parseSegments>) {
  const remove = await adminFetch(`/rest/v1/reel_transcript_segments?post_id=eq.${encodeURIComponent(postId)}`, {
    method: "DELETE",
    headers: { Prefer: "return=minimal" },
  });
  if (!remove.ok) throw new Error(`Could not replace the old reel transcript (${remove.status}).`);
  const insert = await adminFetch("/rest/v1/reel_transcript_segments", {
    method: "POST",
    headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify(rows.map((row) => ({ post_id: postId, ...row }))),
  });
  if (!insert.ok) throw new Error(`Could not save the reel transcript (${insert.status}): ${await insert.text()}`);
}

async function deleteGeminiFile(name: string, apiKey: string) {
  if (!name || !name.startsWith("files/")) return;
  await fetch(`https://generativelanguage.googleapis.com/v1beta/${name}?key=${encodeURIComponent(apiKey)}`, {
    method: "DELETE",
    signal: AbortSignal.timeout(15_000),
  }).catch(() => undefined);
}

async function processJob(job: any) {
  const postId = String(job.post_id || "");
  const lease = String(job.lease || "");
  const attempts = Number(job.attempts || 0);
  if (!postId || !lease) return { ok: false, error: "Invalid claimed reel job" };
  let uploadedName = "";
  const { gemini } = config();
  try {
    const video = await downloadVideo(String(job.video_url || ""));
    const uploaded = await uploadGeminiFile(video.bytes, video.mime, gemini);
    uploadedName = uploaded.name;
    const ready = await waitForGeminiFile(uploaded, gemini);
    const rows = await transcribe(ready, gemini);
    if (!(await hasCurrentLease(job))) return { ok: false, post_id: postId, skipped: "lease_lost" };
    await replaceSegments(postId, rows);
    const persisted = await updateJob(postId, lease, {
      status: "done",
      locked_at: null,
      completed_at: new Date().toISOString(),
      last_error: null,
    });
    return persisted ? { ok: true, post_id: postId, segments: rows.length } : { ok: false, post_id: postId, skipped: "lease_lost" };
  } catch (error) {
    const message = String(error instanceof Error ? error.message : error).slice(0, 700);
    const terminal = attempts >= 4;
    const retryAt = new Date(Date.now() + Math.min(120, 2 ** attempts * 3) * 60_000).toISOString();
    const persisted = await updateJob(postId, lease, {
      status: terminal ? "failed" : "pending",
      locked_at: null,
      next_attempt_at: retryAt,
      last_error: message,
    }).catch(() => false);
    return { ok: false, post_id: postId, error: message, state_persisted: persisted };
  } finally {
    if (uploadedName) await deleteGeminiFile(uploadedName, gemini);
  }
}

function requestedLimit(body: any) {
  const value = Number(body?.limit ?? 1);
  if (!Number.isFinite(value)) return 1;
  return Math.max(1, Math.min(Math.floor(value), MAX_JOBS));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: JSON_HEADERS });
  if (req.method !== "POST") return response({ error: "Method not allowed." }, 405);
  try {
    const { url, anon } = config();
    if (!(await verifyUser(bearer(req), url, anon))) return response({ error: "A signed-in Blink account is required." }, 401);
    const body = await req.json().catch(() => ({}));
    const limit = requestedLimit(body);
    const now = new Date().toISOString();
    const jobsResponse = await adminFetch(`/rest/v1/reel_search_index_jobs?select=post_id,video_url,attempts&status=eq.pending&next_attempt_at=lte.${encodeURIComponent(now)}&order=created_at.asc&limit=${limit}`);
    if (!jobsResponse.ok) return response({ error: "Could not load reel indexing jobs." }, 503);
    const jobs = await jobsResponse.json().catch(() => []);
    const results = [];
    for (const candidate of Array.isArray(jobs) ? jobs : []) {
      const claimed = await claimJob(candidate);
      if (claimed) results.push(await processJob(claimed));
    }
    return response({ processed: results.length, succeeded: results.filter((item) => item.ok).length, results });
  } catch (error) {
    console.error("search-reel-indexer", error);
    return response({ error: "Reel search indexing is temporarily unavailable." }, 500);
  }
});

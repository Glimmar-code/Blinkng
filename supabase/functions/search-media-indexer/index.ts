import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { Image } from "https://deno.land/x/imagescript@1.3.0/mod.ts";

const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
const MAX_IMAGE_BYTES = 18_000_000;
const WIDTH = 16;
const HEIGHT = 8;
const DIMENSIONS = WIDTH * HEIGHT * 4;
const MODEL = "blink-perceptual-rgb-luma-v1";
const MAX_JOBS = 8;

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
  if (!url || !service || !anon) throw new Error("Supabase runtime configuration is unavailable.");
  return { url, service, anon };
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

function normalize(values: number[]) {
  if (values.length !== DIMENSIONS) throw new Error("Unexpected descriptor size.");
  const norm = Math.sqrt(values.reduce((sum, value) => sum + value * value, 0));
  if (!Number.isFinite(norm) || norm <= 1e-12) throw new Error("Image descriptor is empty.");
  return values.map((value) => value / norm);
}

async function readBounded(response: Response, maxBytes: number) {
  if (!response.body) throw new Error("Media response was empty.");
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
        throw new Error("Image exceeds the indexing size limit.");
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

async function descriptorFor(urlText: string) {
  const url = safeHttps(urlText);
  if (!url) throw new Error("Only HTTPS media can be indexed.");
  const media = await fetch(url, { redirect: "follow", signal: AbortSignal.timeout(90_000) });
  if (!media.ok) throw new Error(`Media download failed with HTTP ${media.status}.`);
  if (!safeHttps(media.url)) throw new Error("Media redirect did not remain HTTPS.");
  const contentType = (media.headers.get("content-type") || "").toLowerCase();
  if (!contentType.startsWith("image/")) throw new Error("Indexed media is not an image.");
  const length = Number(media.headers.get("content-length") || "0");
  if (Number.isFinite(length) && length > MAX_IMAGE_BYTES) throw new Error("Image exceeds the indexing size limit.");
  const bytes = await readBounded(media, MAX_IMAGE_BYTES);
  if (bytes.byteLength === 0) throw new Error("Image size is invalid.");

  const decoded = await Image.decode(bytes);
  decoded.resize(WIDTH, HEIGHT);
  const values: number[] = [];
  for (let y = 0; y < HEIGHT; y += 1) {
    for (let x = 0; x < WIDTH; x += 1) {
      const [r, g, b] = Image.colorToRGBA(decoded.getPixelAt(x, y));
      const rf = r / 255;
      const gf = g / 255;
      const bf = b / 255;
      values.push(rf, gf, bf, 0.2126 * rf + 0.7152 * gf + 0.0722 * bf);
    }
  }
  return normalize(values);
}

function jobFilter(id: string, lease: string) {
  return `id=eq.${encodeURIComponent(id)}&status=eq.running&locked_at=eq.${encodeURIComponent(lease)}`;
}

async function claimJob(candidate: any) {
  const id = String(candidate?.id || "");
  if (!id) return null;
  const lease = new Date().toISOString();
  const now = lease;
  const result = await adminFetch(
    `/rest/v1/search_media_index_jobs?id=eq.${encodeURIComponent(id)}&status=eq.pending&next_attempt_at=lte.${encodeURIComponent(now)}`,
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
  if (!result.ok) throw new Error(`Could not claim media index job (${result.status}).`);
  const rows = await result.json().catch(() => []);
  return Array.isArray(rows) && rows.length ? { ...rows[0], lease } : null;
}

async function updateJob(id: string, lease: string, changes: Record<string, unknown>) {
  const result = await adminFetch(`/rest/v1/search_media_index_jobs?${jobFilter(id, lease)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({ ...changes, updated_at: new Date().toISOString() }),
  });
  if (!result.ok) throw new Error(`Could not update media index job (${result.status}).`);
  const rows = await result.json().catch(() => []);
  return Array.isArray(rows) && rows.length > 0;
}

async function storeEmbedding(job: any, embedding: number[]) {
  const result = await adminFetch(
    "/rest/v1/search_image_embeddings?on_conflict=entity_type,entity_id,image_url",
    {
      method: "POST",
      headers: { "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify({
        entity_type: job.entity_type,
        entity_id: job.entity_id,
        image_url: job.image_url,
        model: MODEL,
        embedding: `[${embedding.map((value) => value.toFixed(8)).join(",")}]`,
        updated_at: new Date().toISOString(),
      }),
    },
  );
  if (!result.ok) throw new Error(`Embedding upsert failed (${result.status}): ${await result.text()}`);
}

async function processJob(job: any) {
  const id = String(job.id || "");
  const attempts = Number(job.attempts || 0);
  const lease = String(job.lease || "");
  if (!id || !lease) return { ok: false, error: "Invalid claimed media job" };
  try {
    const embedding = await descriptorFor(String(job.image_url || ""));
    await storeEmbedding(job, embedding);
    const persisted = await updateJob(id, lease, {
      status: "done",
      locked_at: null,
      completed_at: new Date().toISOString(),
      last_error: null,
    });
    return persisted ? { ok: true, id } : { ok: false, id, skipped: "lease_lost" };
  } catch (error) {
    const message = String(error instanceof Error ? error.message : error).slice(0, 700);
    const terminal = attempts >= 5;
    const retryAt = new Date(Date.now() + Math.min(60, 2 ** attempts) * 60_000).toISOString();
    const persisted = await updateJob(id, lease, {
      status: terminal ? "failed" : "pending",
      locked_at: null,
      next_attempt_at: retryAt,
      last_error: message,
    }).catch(() => false);
    return { ok: false, id, error: message, state_persisted: persisted };
  }
}

function requestedLimit(body: any) {
  const value = Number(body?.limit ?? 4);
  if (!Number.isFinite(value)) return 4;
  return Math.max(1, Math.min(Math.floor(value), MAX_JOBS));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: JSON_HEADERS });
  if (req.method !== "POST") return response({ error: "Method not allowed." }, 405);
  try {
    const { url, anon } = config();
    const jwt = bearer(req);
    if (!(await verifyUser(jwt, url, anon))) return response({ error: "A signed-in Blink account is required." }, 401);
    const body = await req.json().catch(() => ({}));
    const limit = requestedLimit(body);
    const now = new Date().toISOString();
    const query = `/rest/v1/search_media_index_jobs?select=id,entity_type,entity_id,image_url,attempts&status=eq.pending&next_attempt_at=lte.${encodeURIComponent(now)}&order=created_at.asc&limit=${limit}`;
    const jobsResponse = await adminFetch(query);
    if (!jobsResponse.ok) return response({ error: "Could not load media indexing jobs." }, 503);
    const jobs = await jobsResponse.json().catch(() => []);
    const results = [];
    for (const candidate of Array.isArray(jobs) ? jobs : []) {
      const claimed = await claimJob(candidate);
      if (claimed) results.push(await processJob(claimed));
    }
    return response({ processed: results.length, succeeded: results.filter((item) => item.ok).length, results });
  } catch (error) {
    console.error("search-media-indexer", error);
    return response({ error: "Visual indexing is temporarily unavailable." }, 500);
  }
});

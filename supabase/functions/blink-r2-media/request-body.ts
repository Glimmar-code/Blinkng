/**
 * Hard limit for control-plane JSON, independent of attacker-controlled
 * Content-Length. Never buffer an unbounded request body in the R2 signer.
 */
export type BoundedJson =
  | { ok: true; value: Record<string, unknown> }
  | { ok: false; error: "REQUEST_TOO_LARGE" | "INVALID_REQUEST" };

export async function readBoundedJsonObject(
  request: Request,
  maxBytes = 4096,
): Promise<BoundedJson> {
  const claimed = request.headers.get("content-length");
  if (claimed !== null) {
    if (!/^\\d+$/.test(claimed)) return { ok: false, error: "INVALID_REQUEST" };
    if (Number(claimed) > maxBytes) return { ok: false, error: "REQUEST_TOO_LARGE" };
  }
  if (!request.body) return { ok: false, error: "INVALID_REQUEST" };

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const next = await reader.read();
      if (next.done) break;
      length += next.value.byteLength;
      if (length > maxBytes) {
        await reader.cancel("Request exceeds BLINK control-plane limit").catch(() => {});
        return { ok: false, error: "REQUEST_TOO_LARGE" };
      }
      chunks.push(next.value);
    }
  } catch {
    return { ok: false, error: "INVALID_REQUEST" };
  } finally {
    reader.releaseLock();
  }

  try {
    const buffer = new Uint8Array(length);
    let offset = 0;
    for (const chunk of chunks) {
      buffer.set(chunk, offset);
      offset += chunk.byteLength;
    }
    const decoded = new TextDecoder("utf-8", { fatal: true }).decode(buffer);
    const parsed: unknown = JSON.parse(decoded);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
      return { ok: false, error: "INVALID_REQUEST" };
    return { ok: true, value: parsed as Record<string, unknown> };
  } catch {
    return { ok: false, error: "INVALID_REQUEST" };
  }
}

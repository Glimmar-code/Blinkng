import assert from "node:assert/strict";
import { readBoundedJsonObject } from "./request-body.ts";

function request(body: string, claimed?: string) {
  const req = new Request("https://blink.com.ng/media", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  });
  req.headers.delete("content-length");
  if (claimed !== undefined) req.headers.set("content-length", claimed);
  return req;
}

Deno.test("accepts small JSON without Content-Length", async () => {
  const result = await readBoundedJsonObject(request('{"action":"init"}'));
  assert.deepEqual(result, { ok: true, value: { action: "init" } });
});

Deno.test("blocks oversized payload with forged small Content-Length", async () => {
  const result = await readBoundedJsonObject(request(
    JSON.stringify({ action: "init", excess: "x".repeat(5000) }), "16",
  ));
  assert.deepEqual(result, { ok: false, error: "REQUEST_TOO_LARGE" });
});

Deno.test("blocks oversized payload with no Content-Length", async () => {
  const result = await readBoundedJsonObject(request(" ".repeat(4097)));
  assert.deepEqual(result, { ok: false, error: "REQUEST_TOO_LARGE" });
});

Deno.test("rejects oversized claimed length before parsing", async () => {
  const result = await readBoundedJsonObject(request('{"action":"init"}', "9000"));
  assert.deepEqual(result, { ok: false, error: "REQUEST_TOO_LARGE" });
});

Deno.test("rejects invalid lengths and malformed or non-object JSON", async () => {
  assert.deepEqual(await readBoundedJsonObject(request("{}", "-1")),
    { ok: false, error: "INVALID_REQUEST" });
  assert.deepEqual(await readBoundedJsonObject(request("{bad")),
    { ok: false, error: "INVALID_REQUEST" });
  assert.deepEqual(await readBoundedJsonObject(request("[]")),
    { ok: false, error: "INVALID_REQUEST" });
});

Deno.test("allows a valid JSON body exactly at the byte boundary", async () => {
  const prefix = '{"note":"';
  const suffix = '"}';
  const body = prefix + "a".repeat(4096 - prefix.length - suffix.length) + suffix;
  assert.equal(new TextEncoder().encode(body).length, 4096);
  const result = await readBoundedJsonObject(request(body));
  assert.equal(result.ok, true);
});

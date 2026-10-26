// Local Deno tests: zero production requests, no Supabase project or secrets needed.
Deno.env.set("BLINK_LINK_METADATA_TEST", "1");
const { allowedUrl, publicPreview, handleLinkMetadata } = await import("./index.ts");

Deno.test("blocks unsafe schemes, credentials, nonstandard ports and malformed URLs", () => {
  for (const unsafe of [
    "http://example.com", "https://user:secret@example.com",
    "https://github.com:8443/x", "file:///etc/passwd",
    "https://[::1]/", "javascript:alert(1)"
  ]) {
    if (allowedUrl(unsafe) !== null) throw new Error("Accepted unsafe URL");
  }
});

Deno.test("unknown hostname returns an inert fallback without fetching", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (_input: RequestInfo | URL) => {
      throw new Error("Unexpected external fetch");
    };
    const result = await publicPreview(new URL("https://unknown.example/something"));
    if (result.title !== "unknown.example" || result.image !== "") {
      throw new Error("Unexpected non-generic preview for untrusted host");
    }
  } finally {
    globalThis.fetch = original;
  }
});

Deno.test("known site receives bounded safe Open Graph preview", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async (_input: RequestInfo | URL) =>
      new Response(
        '<html><head><meta property="og:title" content="Example &amp; Test">' +
        '<meta property="og:description" content="Public page preview">' +
        '<meta property="og:image" content="https://i.ytimg.com/vi/abc/default.jpg"></head></html>',
        { status: 200, headers: { "content-type": "text/html; charset=utf-8" } }
      );
    const data = await publicPreview(new URL("https://www.youtube.com/watch?v=abc"));
    if (data.title !== "Example & Test" || data.description !== "Public page preview") {
      throw new Error("HTML metadata parsing failed");
    }
    if (!data.image.startsWith("https://")) throw new Error("Image preview missing");
  } finally {
    globalThis.fetch = original;
  }
});

Deno.test("private signed links never leak request query values in metadata", async () => {
  const target = encodeURIComponent("https://github.com/readme?access_token=privateexample");
  const response = await handleLinkMetadata(
    new Request("http://localhost/?url=" + target)
  );
  const content = await response.json();
  if (content.description !== "Private link" ||
      JSON.stringify(content).includes("privateexample")) {
    throw new Error("Private link exposed");
  }
});

Deno.test("public handler rejects unsuitable HTTP methods and URLs", async () => {
  const failed = await handleLinkMetadata(new Request("http://localhost/?url=" +
    encodeURIComponent("http://127.0.0.1/admin")));
  if (failed.status !== 400) throw new Error("Insecure URL accepted");
  const rejected = await handleLinkMetadata(new Request("http://localhost/", { method: "POST" }));
  if (rejected.status !== 405) throw new Error("Unsupported method accepted");
});

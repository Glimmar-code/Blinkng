import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const jsonHeaders = {
  ...corsHeaders,
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

function readTextOutput(payload: any): string {
  const steps = Array.isArray(payload?.steps) ? payload.steps : [];
  for (let i = steps.length - 1; i >= 0; i -= 1) {
    const step = steps[i];
    if (step?.type !== "model_output" || !Array.isArray(step?.content)) continue;
    const text = step.content
      .filter((item: any) => item?.type === "text" && typeof item?.text === "string")
      .map((item: any) => item.text.trim())
      .filter(Boolean)
      .join("\n");
    if (text) return text;
  }
  return "";
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed." }, 405);
  }

  try {
    const apiKey = Deno.env.get("GEMINI_API_KEY")?.trim();
    if (!apiKey) {
      return jsonResponse({
        error: "Blink AI is not configured yet.",
        code: "missing_gemini_key",
      }, 503);
    }

    const body = await req.json().catch(() => ({}));
    const message = typeof body?.message === "string" ? body.message.trim() : "";
    const previousInteractionId = typeof body?.previous_interaction_id === "string"
      ? body.previous_interaction_id.trim()
      : "";

    if (!message) {
      return jsonResponse({ error: "Please enter a message." }, 400);
    }
    if (message.length > 4000) {
      return jsonResponse({ error: "Message is too long. Keep it under 4,000 characters." }, 413);
    }

    const model = Deno.env.get("GEMINI_MODEL")?.trim() || "gemini-2.5-flash-lite";
    const requestBody: Record<string, unknown> = {
      model,
      input: message,
      store: true,
      system_instruction:
        "You are Blink AI, the helpful AI assistant inside the Blink social media app. " +
        "Answer clearly and naturally. Help with learning, writing, brainstorming, explanations, " +
        "captions, summaries and everyday questions. Do not claim to be a Blink administrator, " +
        "do not pretend to access private user data, and keep responses appropriate and safe.",
      generation_config: {
        max_output_tokens: 1200,
      },
    };

    if (previousInteractionId) {
      requestBody.previous_interaction_id = previousInteractionId;
    }

    const geminiResponse = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/interactions",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey,
        },
        body: JSON.stringify(requestBody),
      },
    );

    const raw = await geminiResponse.text();
    let payload: any = {};
    try {
      payload = raw ? JSON.parse(raw) : {};
    } catch {
      payload = {};
    }

    if (!geminiResponse.ok) {
      const upstreamMessage =
        payload?.error?.message || payload?.message || "Blink AI is temporarily unavailable.";
      const retryAfter = geminiResponse.headers.get("retry-after");
      return jsonResponse({
        error: upstreamMessage,
        code: "gemini_error",
        upstream_status: geminiResponse.status,
        retry_after: retryAfter,
      }, geminiResponse.status === 429 ? 429 : 502);
    }

    const text = readTextOutput(payload);
    if (!text) {
      return jsonResponse({ error: "Blink AI returned an empty response." }, 502);
    }

    return jsonResponse({
      text,
      interaction_id: typeof payload?.id === "string" ? payload.id : null,
      model: typeof payload?.model === "string" ? payload.model : model,
      usage: payload?.usage ?? null,
    });
  } catch (error) {
    console.error("blink-ai error", error);
    return jsonResponse({ error: "Blink AI could not process that request." }, 500);
  }
});

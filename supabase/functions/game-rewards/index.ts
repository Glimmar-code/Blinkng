import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.replace("Bearer ", ""));
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const { gameType, score, coinsEarned, sessionToken } = body;

    if (!gameType || typeof score !== "number" || typeof coinsEarned !== "number") {
      return new Response(JSON.stringify({ error: "VALIDATION_ERROR", message: "gameType, score, coinsEarned required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (score < 0 || coinsEarned < 0) {
      return new Response(JSON.stringify({ error: "VALIDATION_ERROR", message: "score and coinsEarned must be non-negative" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Server-side validation: enforce reasonable score-to-coins ratio to prevent abuse
    const maxAllowedCoins = Math.ceil(score / 10);
    const finalCoinsEarned = Math.min(coinsEarned, maxAllowedCoins);

    // Record the game session
    const { data: session, error: sessionError } = await supabase
      .from("game_sessions")
      .insert({
        user_id: user.id,
        game_type: gameType,
        score: Math.floor(score),
        coins_earned: finalCoinsEarned,
      })
      .select()
      .single();

    if (sessionError) {
      return new Response(JSON.stringify({ error: "DATABASE_ERROR", message: sessionError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Award the reward through the trusted RPC
    const { data: rewardResult, error: rewardError } = await supabase.rpc("award_game_reward", {
      p_user_id: user.id,
      p_reward_type: "coins",
      p_amount: finalCoinsEarned,
      p_source: gameType,
    });

    if (rewardError) {
      return new Response(JSON.stringify({ error: "DATABASE_ERROR", message: rewardError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({
      success: true,
      sessionId: session.id,
      coinsAwarded: finalCoinsEarned,
      reward: rewardResult,
    }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: "INTERNAL_ERROR", message: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});

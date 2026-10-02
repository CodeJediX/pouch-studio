import { createClient } from "npm:@supabase/supabase-js@2.117.2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const auth = createClient(supabaseUrl, anonKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

function response(request: Request, body: unknown, status = 200) {
  const origin = request.headers.get("origin") ?? "";
  const allowedOrigin = origin === "https://codejedix.github.io" ||
      /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)
    ? origin
    : "https://codejedix.github.io";

  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Access-Control-Allow-Origin": allowedOrigin,
      "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      "Vary": "Origin",
    },
  });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return response(request, { ok: true });
  if (request.method !== "POST") return response(request, { error: "Method not allowed." }, 405);

  try {
    const body = await request.json();
    const username = String(body?.username ?? "").trim().toLowerCase();
    const password = String(body?.password ?? "");

    if (!/^[a-z0-9][a-z0-9._-]{2,29}$/.test(username) || password.length < 8) {
      return response(request, { error: "Invalid username or password." });
    }

    const { data: profile, error: profileError } = await admin
      .from("profiles")
      .select("user_id")
      .eq("username", username)
      .maybeSingle();

    if (profileError) {
      console.error("Username lookup failed", profileError.message);
      return response(request, { error: "Sign in is temporarily unavailable." }, 503);
    }

    if (!profile) {
      await new Promise((resolve) => setTimeout(resolve, 250));
      return response(request, { error: "Invalid username or password." });
    }

    const { data: userResult, error: userError } = await admin.auth.admin.getUserById(profile.user_id);
    const email = userResult.user?.email;
    if (userError || !email) {
      console.error("Auth user lookup failed", userError?.message ?? "Missing email");
      return response(request, { error: "Invalid username or password." });
    }

    const { data: signIn, error: signInError } = await auth.auth.signInWithPassword({ email, password });
    if (signInError || !signIn.session) {
      return response(request, { error: "Invalid username or password." });
    }

    return response(request, { session: signIn.session });
  } catch (error) {
    console.error("Username login failed", error instanceof Error ? error.message : "Unknown error");
    return response(request, { error: "Sign in is temporarily unavailable." }, 500);
  }
});

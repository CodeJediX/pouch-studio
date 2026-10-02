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

async function sha256(value: string) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return response(request, { ok: true });
  if (request.method !== "POST") return response(request, { error: "Method not allowed." }, 405);

  try {
    const body = await request.json();
    const action = String(body?.action ?? "login");
    const username = String(body?.username ?? "").trim().toLowerCase();
    const password = String(body?.password ?? "");

    if (!/^[a-z0-9][a-z0-9._-]{2,29}$/.test(username) || password.length < 8) {
      return response(request, { error: "Invalid username or password." });
    }

    if (action === "complete-account-setup") {
      const setupToken = String(body?.setupToken ?? "");
      if (!/^[0-9a-f]{64}$/.test(setupToken)) {
        return response(request, { error: "This setup link is invalid or expired." });
      }

      const tokenHash = await sha256(setupToken);
      const { data: pendingToken, error: tokenLookupError } = await admin
        .from("account_setup_tokens")
        .select("user_id, expires_at, used_at")
        .eq("token_hash", tokenHash)
        .maybeSingle();

      if (tokenLookupError || !pendingToken || pendingToken.used_at || new Date(pendingToken.expires_at) <= new Date()) {
        return response(request, { error: "This setup link is invalid or expired." });
      }

      const { data: usernameOwner, error: usernameError } = await admin
        .from("profiles")
        .select("user_id")
        .eq("username", username)
        .maybeSingle();

      if (usernameError) {
        console.error("Username availability check failed", usernameError.message);
        return response(request, { error: "Account setup is temporarily unavailable." }, 503);
      }
      if (usernameOwner && usernameOwner.user_id !== pendingToken.user_id) {
        return response(request, { error: "That username is already taken." });
      }

      const { data: claimedUserId, error: claimError } = await admin
        .rpc("claim_account_setup_token", { p_token_hash: tokenHash });
      if (claimError || !claimedUserId || claimedUserId !== pendingToken.user_id) {
        return response(request, { error: "This setup link is invalid or expired." });
      }

      const { data: existingUser, error: existingUserError } = await admin.auth.admin.getUserById(claimedUserId);
      const email = existingUser.user?.email;
      if (existingUserError || !email) {
        console.error("Setup user lookup failed", existingUserError?.message ?? "Missing email");
        return response(request, { error: "Account setup is temporarily unavailable." }, 503);
      }

      const { error: updateError } = await admin.auth.admin.updateUserById(claimedUserId, {
        password,
        user_metadata: { ...(existingUser.user?.user_metadata ?? {}), username },
      });
      if (updateError) {
        console.error("Password setup failed", updateError.message);
        return response(request, { error: "Could not save the new login." }, 503);
      }

      const { error: profileUpdateError } = await admin
        .from("profiles")
        .upsert({ user_id: claimedUserId, username }, { onConflict: "user_id" });
      if (profileUpdateError) {
        console.error("Profile setup failed", profileUpdateError.message);
        return response(request, { error: "Could not save the username." }, 503);
      }

      const { data: setupSignIn, error: setupSignInError } = await auth.auth.signInWithPassword({ email, password });
      if (setupSignInError || !setupSignIn.session) {
        return response(request, { error: "Login saved. Return to sign in with your new username and password." });
      }

      return response(request, { session: setupSignIn.session });
    }

    if (action !== "login") return response(request, { error: "Method not allowed." }, 405);

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

import { createDataPublic, setTokens } from "./fetchUtils";

export async function signIn(email, password) {
  const e = String(email || "").trim().toLowerCase();
  const p = String(password || "").trim();

  if (!e || !p) {
    return { success: false, error: "Email and password are required." };
  }

  try {
    const data = await createDataPublic("/v2/auth/login", { email: e, password: p });
    if (!data?.access_token) {
      return { success: false, error: "Sign in failed. Invalid response." };
    }

    setTokens(data);
    try { window.dispatchEvent(new Event("itbms:auth-changed")); } catch {}
    return { success: true, data };
  } catch (err) {
    const status = err?.response?.status;
    const msg =
      (err?.response?.data?.message || err?.response?.data?.error) ??
      (status === 403
        ? "You need to activate your account before signing in."
        : status >= 400 && status < 500
        ? "Email or Password is incorrect."
        : "Server error. Please try again in a moment.");

    return { success: false, error: msg, status };
  }
}

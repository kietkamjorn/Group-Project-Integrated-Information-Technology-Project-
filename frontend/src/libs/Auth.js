import {
  buildUrl,
  getAccessToken,
  setTokens,
  decodeJwt,
  clearTokens,
} from "./fetchUtils";

export const auth = {
  accessToken: "",
  user: null,
  loadAuthState() {
    this.accessToken = getAccessToken();
    if (!this.accessToken) {
      this.user = null;
      return;
    }
    const c = decodeJwt(this.accessToken);
    if (!c?.role) {
      this.clear();
      return;
    }
    this.user = {
      id: c.id ?? c.sub ?? null,
      nickname: c.nickname ?? c.nickName ?? "",
      email: c.email ?? "",
      role: c.role ?? "",
      exp: c.exp ?? null,
    };
  },

  clear() {
    this.accessToken = "";
    this.user = null;
    clearTokens();
    try { window.dispatchEvent(new Event("itbms:auth-changed")); } catch {}
  },

  async login(email, password) {
    const res = await fetch(buildUrl("/v2/auth/login"), {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (!res.ok) {
      let msg = `Sign in failed (${res.status})`;
      try {
        const e = await res.json();
        if (e?.message) msg = e.message;
      } catch {}
      throw new Error(msg);
    }

    const data = await res.json(); 
    setTokens(data);              

    this.accessToken = getAccessToken();
    const c = decodeJwt(this.accessToken) || {};

    this.user = {
      id: c.id ?? c.sub ?? null,
      nickname: c.nickname ?? c.nickName ?? "",
      email: c.email ?? "",
      role: c.role ?? "",
      exp: c.exp ?? null,
    };

    try { window.dispatchEvent(new Event("itbms:auth-changed")); } catch {}
  },

  isAuthenticated() {
    return !!this.user && !!this.accessToken;
  },
  role() {
    return String(this.user?.role || "").toUpperCase();
  },
  isSeller() {
    return this.role() === "SELLER";
  },
};

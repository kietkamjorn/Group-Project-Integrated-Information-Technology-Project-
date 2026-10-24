/* ---------- Base URL ---------- */
const getBaseUrl = () => {
  const base =
    import.meta.env.VITE_API_BASE ||
    import.meta.env.VITE_API_URL ||
    "http://localhost:8080/itb-mshop";
  return String(base).replace(/\/+$/, "");
};
export const buildUrl = (path) =>
  `${getBaseUrl()}${path.startsWith("/") ? "" : "/"}${path}`;

/* ---------- Response Handler ---------- */
const handleResponse = async (response) => {
  if (!response.ok) {
    let errorData = null;
    try {
      errorData = await response.clone().json();
    } catch {
      try {
        errorData = await response.text();
      } catch {}
    }
    const err = new Error(`Error: ${response.status}`);
    err.response = { status: response.status, data: errorData };
    throw err;
  }

  if (response.status === 204) return null;

  const ct = response.headers.get("content-type") || "";
  const text = await response.text();
  if (!text) return null;
  if (ct.includes("application/json")) {
    try {
      return JSON.parse(text);
    } catch {}
  }
  return text;
};

/* ---------- JWT Helpers ---------- */
const b64uToJson = (b64u) => {
  try {
    const b64 = b64u.replace(/-/g, "+").replace(/_/g, "/");
    const text = decodeURIComponent(
      atob(b64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    return JSON.parse(text);
  } catch {
    return {};
  }
};
export const decodeJwt = (token) => {
  if (!token) return {};
  const parts = token.split(".");
  if (parts.length !== 3) return {};
  return b64uToJson(parts[1]);
};

/* ---------- Token Storage (access เท่านั้น) ---------- */
export const getAccessToken = () => localStorage.getItem("access_token") || "";

export const setTokens = (obj = {}) => {
  const access =
    obj.access_token || obj.accessToken || obj.token || obj.access || null;
  if (access) localStorage.setItem("access_token", access);
};

export const clearTokens = () => {
  localStorage.removeItem("access_token");
};

/* ---------- Refresh Flow (ใช้ HttpOnly cookie) ---------- */
const refreshAccessToken = async () => {
  const url = buildUrl("/v2/auth/refresh");

  const res = await fetch(url, {
    method: "POST",
    credentials: "include",
  });

  if (res.status === 400 || res.status === 401) {
    clearTokens();
    const err = new Error("Refresh token expired/invalid.");
    err.status = res.status;
    throw err;
  }
  if (res.status === 403) {
    clearTokens();
    const err = new Error("Your account has not been activated.");
    err.status = 403;
    throw err;
  }
  if (!res.ok) {
    throw new Error("There is a problem. Please try again later.");
  }

  let data = null;
  try {
    data = await res.json();
  } catch {
    data = null;
  }

  const newAccess =
    data?.access_token || data?.accessToken || data?.token || null;

  if (!newAccess) throw new Error("No access token in refresh response");

  setTokens({ access_token: newAccess });

  try {
    window.dispatchEvent(
      new CustomEvent("itbms:auth-refreshed", {
        detail: { accessToken: newAccess },
      })
    );
    window.dispatchEvent(new Event("itbms:auth-changed"));
  } catch {}

  return newAccess;
};

/* ---------- Core: withAuthFetch (แนบ Bearer + auto refresh) ---------- */
export const withAuthFetch = async (path, init = {}, retries = 1) => {
  const url = buildUrl(path);
  const token = getAccessToken();

  const headers = new Headers(init.headers || {});
  if (!headers.has("Accept")) headers.set("Accept", "application/json");

  const isJsonBody =
    init.body &&
    typeof init.body === "string" &&
    (headers.get("Content-Type") || "").includes("application/json");
  if (!headers.has("Content-Type") && isJsonBody) {
    headers.set("Content-Type", "application/json");
  }

  if (token) headers.set("Authorization", `Bearer ${token}`);

  const baseInit = { credentials: "omit" };

  let res = await fetch(url, { ...baseInit, ...init, headers });

  if (res.status === 401 && retries > 0) {
    try {
      await refreshAccessToken();

      const err = new Error("Authentication refreshed. Reloading.");
      err.code = "AUTH_REFRESH_RELOAD";
      throw err;
    } catch (e) {
      if (e.code === "AUTH_REFRESH_RELOAD") {
        return new Promise(() => {});
      }

      try {
        window.dispatchEvent(
          new CustomEvent("itbms:auth-refresh-failed", {
            detail: { code: e?.status || 0, message: e?.message || "" },
          })
        );
      } catch {}
      clearTokens();
      const code = e?.status || 401;
      const err = new Error(`Error: ${code}`);
      err.response = { status: code, data: e?.message || "Unauthorized" };
      throw err;
    }
  }

  return handleResponse(res);
};

/* ---------- Convenience Wrappers ---------- */
export const readData = (path, init = {}) => withAuthFetch(path, init);

export const createData = (path, body, init = {}) =>
  withAuthFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(init.headers || {}) },
    body: JSON.stringify(body),
    ...init,
  });

export const postMultipart = (path, formData, init = {}) =>
  withAuthFetch(path, {
    method: "POST",
    body: formData,
    ...init,
  });

export const updateData = (path, body, init = {}) =>
  withAuthFetch(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...(init.headers || {}) },
    body: JSON.stringify(body),
    ...init,
  });

export const deleteData = (path, init = {}) =>
  withAuthFetch(path, { method: "DELETE", ...init });

/* ---------- Logout (ลบ refresh cookie + blacklist access token) ---------- */
export const logout = async () => {
  try {
    await fetch(buildUrl("/v2/auth/signout"), {
      method: "POST",
      credentials: "include", 
      headers: getAccessToken()
        ? { Authorization: `Bearer ${getAccessToken()}` }
        : {},
    });
  } catch {}
  // กันการรีเฟรชทับหลัง logout
  localStorage.setItem("auth:blockRefresh", "1");
  clearTokens();
  try {
    window.dispatchEvent(new Event("itbms:auth-changed"));
  } catch {}
  setTimeout(() => localStorage.removeItem("auth:blockRefresh"), 3000);
};

/* ---------- Public (ไม่ต้อง auth) ---------- */
export const publicFetch = async (path, init = {}) => {
  const url = buildUrl(path);
  const headers = new Headers(init.headers || {});
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  const res = await fetch(url, {
    ...init,
    headers,
    credentials: "omit",
    cache: "no-store",
  });
  return handleResponse(res);
};

export const createDataPublic = (path, body, init = {}) =>
  publicFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(init.headers || {}) },
    body: JSON.stringify(body),
    ...init,
  });

export const postMultipartPublic = (path, formData, init = {}) =>
  publicFetch(path, { method: "POST", body: formData, ...init });

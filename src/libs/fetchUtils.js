// fetchUtils.js
const handleResponse = async (response) => {
  if (!response.ok) {
    let errorData;
    try {
      errorData = await response.json();
    } catch {
      errorData = await response.text();
    }
    const error = new Error(`Error: ${response.status}`);
    error.response = { status: response.status, data: errorData };
    throw error;
  }

  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  const text = await response.text();         
  if (!text) return null;                      

  if (contentType.includes('application/json')) {
    try { return JSON.parse(text); } catch { /* ตกลงไปคืน text */ }
  }
  return text; 
};


const retryFetch = async (url, options, retries = 3, delay = 1000) => {
  for (let i = 0; i < retries; i++) {
    try {
      const response = await fetch(url, options);
      return await handleResponse(response);
    } catch (error) {
      if (i < retries - 1) {
        await new Promise((resolve) => setTimeout(resolve, delay));
      } else {
        throw error;
      }
    }
  }
};

// fetchUtils.js
const getBaseUrl = () => {
  const u = import.meta.env.VITE_API_URL;
  if (!u) throw new Error("VITE_API_URL is required");
  return u.endsWith("/") ? u : u + "/";
};

export const buildUrl = (path) => {
  const base = getBaseUrl();
  const clean = String(path || "").replace(/^\/+/, ""); 
  return new URL(clean, base).href;                     
};

export const readData = async (path, init = {}) => {
  const url = buildUrl(path);
  const res = await fetch(url, { credentials: "include", ...init });
  if (!res.ok) throw new Error(String(res.status));
  return res.json();
};


//ใช้สำหรับ POST
export const createData = async (path, body, init = {}) => {
  const fullURL = buildUrl(path);
  const response = await fetch(fullURL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(init.headers || {}) },
    body: JSON.stringify(body),
    credentials: init.credentials ?? "same-origin", 
    ...init,
  });
  return handleResponse(response);
};

// เพิ่มฟังก์ชันนี้เข้าไปใน fetchUtils.js
export const postMultipart = async (path, formData) => {
  const fullURL = buildUrl(path);
  const response = await fetch(fullURL, {
    method: "POST",
    body: formData, // browser จะใส่ multipart/form-data ให้เอง
  });
  return handleResponse(response);
};



// ใช้สำหรับ PUT
export const updateData = async (path, body) => {
  const fullURL = buildUrl(path);

  const response = await fetch(fullURL, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  return handleResponse(response);
};

// ใช้สำหรับ DELETE
export const deleteData = async (path) => {
  const fullURL = buildUrl(path);
  const response = await fetch(fullURL, {
    method: "DELETE",
  });

  if (response.status === 204 || response.status === 200) {
    return true;
  }

  const error = new Error(`HTTP error ${response.status}`);
  error.response = {
    status: response.status,
    data: await response.json().catch(() => null), // handle JSON error gracefully
  };
  throw error;
};

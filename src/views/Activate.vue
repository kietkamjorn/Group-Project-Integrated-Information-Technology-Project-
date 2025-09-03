<script setup>
import { ref, onMounted } from "vue";
import { buildUrl } from "../libs/fetchUtils";

const status = ref("pending");
const title  = ref("Verifying your email…");
const message = ref("Please wait a moment...");
const techDetail = ref("");

function friendlyMessage(statusCode, serverMsg = "") {
  const msg = String(serverMsg || "").toLowerCase();
  if (msg.includes("already used")) return "This verification link has already been used.";
  if (msg.includes("expired"))      return "This verification link has expired.";
  if (msg.includes("invalid"))      return "Invalid verification token.";
  if (statusCode === 410)           return "This verification link is no longer valid.";
  if (statusCode === 404)           return "Verification token not found.";
  return "Invalid or expired verification token.";
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const token = params.get("token");

  if (!token) {
    status.value = "error";
    title.value = "Verification failed";
    message.value = "No token was found in the verification link.";
    return;
  }

  try {
    const res = await fetch(buildUrl("/v2/auth/verify-email"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token })
    });

    if (res.ok) {
      status.value = "success";
      title.value = "Email verified";
      message.value = "Your account has been activated. Thank you!";
      return;
    }

    const ct = res.headers.get("content-type") || "";
    let serverMsg = "";
    if (ct.includes("application/json")) {
      const data = await res.json().catch(() => ({}));
      serverMsg = data?.message || "";
      techDetail.value = JSON.stringify(data, null, 2);
    } else {
      const txt = await res.text().catch(() => "");
      serverMsg = txt;
      techDetail.value = txt;
    }

    status.value = "error";
    title.value = "Verification failed";
    message.value = friendlyMessage(res.status, serverMsg);

  } catch {
    status.value = "error";
    title.value = "Verification error";
    message.value = "Unable to reach the server. Please try again.";
  }
});
</script>

<template>
  <div class="min-h-screen grid place-items-center bg-gradient-to-br from-blue-50 to-indigo-100 p-4">
    <div
      class="w-full max-w-lg rounded-2xl border p-8 text-center shadow-xl bg-white"
      :class="status==='success' ? 'border-green-200'
            : status==='error' ? 'border-red-200'
            : 'border-gray-100'"
    >
      <div class="mx-auto mb-4 w-12 h-12 grid place-items-center rounded-full"
           :class="status==='success' ? 'bg-green-100 text-green-600'
                 : status==='error' ? 'bg-red-100 text-red-600'
                 : 'bg-indigo-100 text-indigo-600'">
        <svg v-if="status==='success'" viewBox="0 0 24 24" class="w-7 h-7" fill="currentColor">
          <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/>
        </svg>
        <svg v-else-if="status==='error'" viewBox="0 0 24 24" class="w-7 h-7" fill="currentColor">
          <path d="M12 2a10 10 0 1 0 .001 20.001A10 10 0 0 0 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" class="w-7 h-7 animate-pulse" fill="currentColor">
          <path d="M12 22a10 10 0 1 1 10-10 10.011 10.011 0 0 1-10 10zm-1-9h2V7h-2zm0 4h2v-2h-2z"/>
        </svg>
      </div>

      <h1 class="text-2xl font-semibold mb-2">{{ title }}</h1>
      <p class="text-gray-700 mb-4">{{ message }}</p>

      <details v-if="techDetail" class="mt-2 text-left mx-auto max-w-md">
        <summary class="cursor-pointer text-xs text-gray-400 hover:text-gray-500">
          Technical details
        </summary>
        <pre class="mt-2 text-xs text-gray-500 whitespace-pre-wrap break-words bg-gray-50 rounded p-3">
{{ techDetail }}
        </pre>
      </details>

      <div class="flex justify-center gap-3 mt-6">
        <a href="/kp4/sale-items"
           class="inline-flex items-center px-4 py-2 rounded-lg bg-indigo-700 text-white hover:bg-indigo-800 shadow">
          Go to Shop
        </a>
        <a href="/kp4/signin"
           class="inline-flex items-center px-4 py-2 rounded-lg bg-gray-200 text-gray-800 hover:bg-gray-300">
          Go to Sign In
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* no extra styles needed */
</style>

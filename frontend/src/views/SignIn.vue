<script setup>
import { ref, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { buildUrl, setTokens, decodeJwt } from "@/libs/fetchUtils";
import { auth } from "@/libs/Auth";

const email = ref("");
const password = ref("");
const isSubmitting = ref(false);
const message = ref("");

const router = useRouter();
const route = useRoute();

const isSignInDisabled = computed(
  () =>
    isSubmitting.value ||
    !(email.value || "").trim() ||
    !(password.value || "").trim()
);

const goForgot = () => router.push("/forgot-password");

const onSignIn = async () => {
  if (isSubmitting.value) return;
  isSubmitting.value = true;
  message.value = "";

  const readServerMessage = async (res) => {
    try {
      const ct = res.headers.get("content-type") || "";
      if (ct.includes("application/json")) {
        const j = await res.clone().json();
        return (j && (j.message || j.error || j.code)) || "";
      }
      return await res.clone().text();
    } catch {
      return "";
    }
  };

  const pickMessage = (raw, status) => {
    const s = String(raw || "").trim();

    if (status === 403 || /activat|verify/i.test(s)) {
      return "You need to activate your account before signing in.";
    }
    if (
      /invalid[_\s-]?credentials|bad[_\s-]?credentials|user[_\s-]?not[_\s-]?found|account[_\s-]?not[_\s-]?found|no\s+such\s+user/i.test(
        s
      )
    ) {
      return "Email or Password is incorrect.";
    }
    if (status >= 400 && status < 500) return "Email or Password is incorrect.";
    if (status >= 500) return "Server error. Please try again in a moment.";
    return "";
  };

  try {
    const res = await fetch(buildUrl("/v2/auth/login"), {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      
      body: JSON.stringify({
        email: (email.value || "").trim(),
        password: password.value || "",
      }),
    });

    if (res.status === 403) {
      message.value = "You need to activate your account before signing in.";
      return;
    }
    if (!res.ok) {
      const raw = await readServerMessage(res);
      message.value = pickMessage(raw, res.status) || "Sign in failed. Please try again.";
      return;
    }

    const data = await res.json().catch(() => ({}));

    // เผื่อ BE ตอบ 200 แต่แนบ business error
    const codeLike = (data && (data.code || data.error || data.message)) || "";
    if (codeLike) {
      const m = pickMessage(codeLike, 400);
      if (m) {
        message.value = m;
        return;
      }
    }

    if (!data?.access_token) {
      message.value = "Sign in failed. Please try again.";
      return;
    }

    if (auth && typeof auth.setTokens === "function") auth.setTokens(data);
    else setTokens(data);

    try {
      window.dispatchEvent(new Event("itbms:auth-changed"));
    } catch {}

    // เลือกหน้าเริ่มต้นตาม role
    let role = "";
    try {
      const jwt = decodeJwt(data.access_token);
      role = String(jwt?.role || jwt?.userType || jwt?.type || "").toUpperCase();
    } catch {}
    const defaultTarget = role === "SELLER" ? "/sale-items/list" : "/sale-items";

    // รองรับ redirect เดิม
    const q = route.query.redirect;
    const target = (Array.isArray(q) ? q[0] : q) || defaultTarget;
    router.replace(target);
  } catch (e) {
    console.error(e);
    message.value = "Sign in failed. Please try again.";
  } finally {
    isSubmitting.value = false;
  }
};

const onCancel = () => router.back();
</script>

<template>
  <div class="flex justify-center items-center min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
    <div class="bg-white p-10 rounded-2xl shadow-xl w-full max-w-lg border border-gray-100">
      <h2 class="text-3xl font-extrabold mb-6 text-center text-indigo-700">
        Sign In ITB-Mshop
      </h2>

      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Email</label>
        <input
          type="email"
          v-model="email"
          maxlength="50"
          placeholder="you@example.com"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400 itbms-email text-black"
        />
      </div>

      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Password</label>
        <input
          type="password"
          v-model="password"
          maxlength="14"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400 itbms-password text-black"
        />
      </div>

      <div class="text-right mt-2">
        <button
          type="button"
          class="text-indigo-600 hover:text-indigo-700 hover:underline text-sm"
          @click="goForgot"
        >
          Forgot password?
        </button>
      </div>

      <div class="flex justify-between mt-8 pt-5">
        <button
          class="itbms-signin-button bg-indigo-700 text-white px-6 py-2 rounded-lg shadow-md hover:bg-indigo-800 disabled:bg-indigo-300 disabled:text-white disabled:cursor-not-allowed"
          :disabled="isSignInDisabled"
          @click="onSignIn"
        >
          Sign In
        </button>

        <button class="bg-gray-200 text-gray-700 px-6 py-2 rounded-lg hover:bg-gray-300" @click="onCancel">
          Cancel
        </button>
      </div>

      <p v-if="message" class="itbms-message text-red-600 text-sm mt-3 pt-4">
        {{ message }}
      </p>
    </div>
  </div>
</template>

<style scoped></style>

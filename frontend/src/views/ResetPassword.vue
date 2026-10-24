<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { buildUrl } from "@/libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const token = ref("");
const newPassword = ref("");
const confirmPassword = ref("");

const isSubmitting = ref(false);
const successMsg = ref("");
const errorMsg = ref("");

const show1 = ref(false);
const show2 = ref(false);

onMounted(() => {
  token.value = String(route.query.token || "");
  if (!token.value) {
    errorMsg.value = "Missing or invalid reset link.";
  }
});

const pwTooShort = computed(() => (newPassword.value || "").length < 8);
const pwMismatch = computed(() => newPassword.value !== confirmPassword.value);

const isDisabled = computed(
  () =>
    isSubmitting.value ||
    !token.value ||
    !newPassword.value ||
    !confirmPassword.value ||
    pwTooShort.value ||
    pwMismatch.value
);

const onSubmit = async () => {
  if (isDisabled.value) return;

  isSubmitting.value = true;
  successMsg.value = "";
  errorMsg.value = "";

  try {
    const res = await fetch(buildUrl("/v2/auth/reset-password"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        token: token.value,
        newPassword: newPassword.value,
      }),
    });

    if (!res.ok) {
      if (res.status === 400) {
        errorMsg.value = "Invalid reset token.";
      } else if (res.status === 410) {
        errorMsg.value = "Reset link has expired. Please request a new one.";
      } else {
        try {
          const j = await res.json();
          errorMsg.value = j?.message || "Unable to reset password.";
        } catch {
          errorMsg.value = "Unable to reset password.";
        }
      }
      return;
    }

    successMsg.value = "Your password has been reset successfully.";
    setTimeout(() => router.replace("/signin"), 1800);
  } catch {
    errorMsg.value = "Something went wrong. Please try again.";
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div class="flex justify-center items-center min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
    <div class="bg-white py-10 px-8 sm:px-12 rounded-2xl shadow-xl w-full max-w-xl border border-gray-100">
      <h2 class="text-3xl sm:text-4xl font-extrabold text-center text-indigo-700">
        Reset Password
      </h2>
      <p class="mt-4 text-center text-gray-600 leading-relaxed">
        Set a new password for your account.
      </p>
      <div v-if="successMsg" class="mt-6 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-800 px-4 py-3">
        {{ successMsg }}
      </div>
      <div v-if="errorMsg" class="mt-6 rounded-lg border border-rose-200 bg-rose-50 text-rose-800 px-4 py-3">
        {{ errorMsg }}
      </div>
      <div class="mt-8 space-y-5">
        <div>
          <label class="block font-medium text-gray-700 mb-2">New password</label>
          <div class="relative">
            <input
              :type="show1 ? 'text' : 'password'"
              v-model="newPassword"
              maxlength="50"
              class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black pr-12"
              placeholder="At least 8 characters"
            />
            <button
              type="button"
              class="absolute right-2 top-1/2 -translate-y-1/2 p-2 text-gray-600 hover:text-gray-900"
              :aria-label="show1 ? 'Hide password' : 'Show password'"
              @click="show1 = !show1"
            >
              <svg v-if="!show1" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.5"/>
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.5"/>
                <path d="M9.88 9.88A3 3 0 0012 15a3 3 0 002.12-.88M7.1 7.1C4.5 8.64 3 12 3 12s3.6 7 10 7a10.8 10.8 0 005.9-1.9M14.12 9.88A3 3 0 0012 9a3 3 0 00-1.88.65M20.9 13.2A13.3 13.3 0 0023 12s-3.6-7-10-7a10.8 10.8 0 00-4.2.8" stroke="currentColor" stroke-width="1.5"/>
              </svg>
            </button>
          </div>
          <p v-if="pwTooShort" class="text-xs text-rose-600 mt-1">
            Password must be at least 8 characters.
          </p>
        </div>

        <div>
          <label class="block font-medium text-gray-700 mb-2">Confirm new password</label>
          <div class="relative">
            <input
              :type="show2 ? 'text' : 'password'"
              v-model="confirmPassword"
              maxlength="50"
              class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black pr-12"
              placeholder="Re-enter your new password"
            />
            <button
              type="button"
              class="absolute right-2 top-1/2 -translate-y-1/2 p-2 text-gray-600 hover:text-gray-900"
              :aria-label="show2 ? 'Hide password' : 'Show password'"
              @click="show2 = !show2"
            >
              <svg v-if="!show2" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.5"/>
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.5"/>
                <path d="M9.88 9.88A3 3 0 0012 15a3 3 0 002.12-.88M7.1 7.1C4.5 8.64 3 12 3 12s3.6 7 10 7a10.8 10.8 0 005.9-1.9M14.12 9.88A3 3 0 0012 9a3 3 0 00-1.88.65M20.9 13.2A13.3 13.3 0 0023 12s-3.6-7-10-7a10.8 10.8 0 00-4.2.8" stroke="currentColor" stroke-width="1.5"/>
              </svg>
            </button>
          </div>
          <p v-if="!pwTooShort && pwMismatch" class="text-xs text-rose-600 mt-1">
            Passwords do not match.
          </p>
        </div>
      </div>

      <div class="mt-8 pt-6 flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3">
        <router-link to="/signin" class="w-full sm:w-auto px-5 py-2.5 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 text-center">
          Back to sign in
        </router-link>

        <button
          type="button"
          class="w-full sm:w-auto px-6 py-2.5 rounded-lg bg-indigo-700 text-white hover:bg-indigo-800 disabled:bg-indigo-300 disabled:cursor-not-allowed"
          :disabled="isDisabled"
          @click="onSubmit"
        >
          {{ isSubmitting ? "Saving…" : "Set new password" }}
        </button>
      </div>

      <p class="mt-6 pt-6 text-xs text-gray-500">
        If your link is expired, please
        <router-link to="/forgot-password" class="text-indigo-600 hover:underline">
          request a new reset link
        </router-link>.
      </p>
    </div>
  </div>
</template>

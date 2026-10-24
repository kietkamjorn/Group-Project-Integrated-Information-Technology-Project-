<script setup>
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

/* --------- state --------- */
const router = useRouter();
const oldPassword = ref("");
const newPassword = ref("");
const confirmPassword = ref("");

const showOld = ref(false);
const showNew = ref(false);
const showConfirm = ref(false);

const isSubmitting = ref(false);
const successMsg = ref("");
const errorMsg = ref("");

const pwTooShort = computed(() => (newPassword.value || "").length < 8);
const pwSameAsOld = computed(
  () => !!newPassword.value && newPassword.value === oldPassword.value
);
const pwMismatch = computed(
  () =>
    !pwTooShort.value &&
    !!confirmPassword.value &&
    newPassword.value !== confirmPassword.value
);
const weakPattern = /^(?=.*[A-Za-z])(?=.*\d).+$/;
const pwWeak = computed(() => !!newPassword.value && !weakPattern.test(newPassword.value));

const isDisabled = computed(
  () =>
    isSubmitting.value ||
    !oldPassword.value ||
    !newPassword.value ||
    !confirmPassword.value ||
    pwTooShort.value ||
    pwSameAsOld.value ||
    pwMismatch.value
);

/* --------- api call --------- */
async function onSubmit() {
  if (isDisabled.value) return;
  isSubmitting.value = true;
  successMsg.value = "";
  errorMsg.value = "";

  try {
    const token = getAccessToken();
    const me = token ? decodeJwt(token) : null;
    const uid = me?.sub;

    if (!uid) {
      errorMsg.value = "You are not signed in.";
      setTimeout(() => (errorMsg.value = ""), 5000);
      return;
    }

    const body = {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    };

    await withAuthFetch(`/v2/auth/change-password/${uid}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    successMsg.value = "Password changed successfully.";
    oldPassword.value = "";
    newPassword.value = "";
    confirmPassword.value = "";

    setTimeout(() => router.push("/profile"), 1200);
  } catch (err) {
    const status = err?.response?.status;
    const raw = err?.response?.data;
    const serverMsg =
      (typeof raw === "string" && raw) ||
      (typeof raw === "object" && (raw?.message || raw?.error)) ||
      "";

    if (status === 401) {
      errorMsg.value = "Your session has expired. Please sign in again.";
      setTimeout(() => router.push("/signin"), 1200);
    } else if (status === 400 && /old password/i.test(serverMsg)) {
      errorMsg.value = "Old password is incorrect.";
    } else if (status === 403) {
      errorMsg.value = "You are not allowed to change this password.";
    } else if (status === 404) {
      errorMsg.value = "User not found.";
    } else {
      errorMsg.value = serverMsg || "Failed to change password. Please try again.";
    }
    setTimeout(() => (errorMsg.value = ""), 5000);
  } finally {
    isSubmitting.value = false;
  }
}

const backToProfile = () => router.push("/profile");
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex justify-center p-4 sm:p-6 lg:p-8 pt-16 lg:pt-20">
    <div class="w-full max-w-3xl mx-auto">
      <div class="bg-gradient-to-r from-blue-500 to-purple-600 rounded-2xl shadow-lg p-6 sm:p-8 flex items-center">
        <div class="flex-shrink-0">
          <div class="w-16 h-16 sm:w-20 sm:h-20 rounded-full bg-white/20 flex items-center justify-center ring-4 ring-white/40">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-9 w-9 text-white" viewBox="0 0 24 24" fill="none">
              <path d="M8 11a4 4 0 1 1 7.75 1.5H18v3h-3v-2h-1.25A4 4 0 0 1 8 11Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M6 15.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 0 0-7 0Z" stroke="currentColor" stroke-width="1.6"/>
            </svg>
          </div>
        </div>
        <div class="ml-4 sm:ml-6 flex-grow">
          <h1 class="text-2xl sm:text-3xl font-bold text-white">Change Password</h1>
          <p class="text-white/80 text-sm sm:text-base">Update your account password.</p>
        </div>
        <div class="ml-4 flex-shrink-0">
          <button @click="backToProfile" class="px-4 py-2 rounded-lg bg-white text-blue-600 font-semibold shadow-md hover:bg-gray-100 transition-all text-sm sm:text-base">
            Back to Profile
          </button>
        </div>
      </div>
      
      <div class="mt-8 bg-white rounded-2xl shadow-xl border border-gray-100">
        <div
          v-if="successMsg"
          class="mx-6 mt-6 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-800 px-4 py-3"
          aria-live="polite"
        >
          {{ successMsg }}
        </div>
        <div
          v-if="errorMsg"
          class="mx-6 mt-6 rounded-lg border border-rose-200 bg-rose-50 text-rose-800 px-4 py-3"
          aria-live="polite"
        >
          {{ errorMsg }}
        </div>

        <div class="p-6 sm:p-8">
          <form class="grid grid-cols-1 gap-6" @submit.prevent="onSubmit">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Old password</label>
              <div class="relative">
                <input
                  :type="showOld ? 'text' : 'password'"
                  v-model="oldPassword"
                  maxlength="64"
                  autocomplete="current-password"
                  class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black pr-12"
                  autofocus
                />
                <button type="button" class="absolute right-2 top-1/2 -translate-y-1/2 p-2 text-gray-600 hover:text-gray-900"
                        @click="showOld = !showOld" aria-label="toggle old password visibility">
                  <svg v-if="!showOld" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.5"/>
                    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M10.6 5.1C11.05 5.03 11.52 5 12 5c6.4 0 10 7 10 7a18.3 18.3 0 0 1-5.07 5.45M6.6 6.6C3.96 8.39 2 12 2 12s3.6 7 10 7c1.59 0 3.05-.36 4.35-.98" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">New password</label>
              <div class="relative">
                <input
                  :type="showNew ? 'text' : 'password'"
                  v-model="newPassword"
                  maxlength="64"
                  autocomplete="new-password"
                  placeholder="At least 8 characters"
                  class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black pr-12"
                />
                <button type="button" class="absolute right-2 top-1/2 -translate-y-1/2 p-2 text-gray-600 hover:text-gray-900"
                        @click="showNew = !showNew" aria-label="toggle new password visibility">
                  <svg v-if="!showNew" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.5"/>
                    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M10.6 5.1C11.05 5.03 11.52 5 12 5c6.4 0 10 7 10 7a18.3 18.3 0 0 1-5.07 5.45M6.6 6.6C3.96 8.39 2 12 2 12s3.6 7 10 7c1.59 0 3.05-.36 4.35-.98" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
              <p v-if="pwTooShort" class="text-xs text-rose-600 mt-1">Password must be at least 8 characters.</p>
              <p v-else-if="pwWeak" class="text-xs text-amber-600 mt-1">Use both letters and numbers for a stronger password.</p>
              <p v-if="pwSameAsOld" class="text-xs text-rose-600 mt-1">New password must be different from old password.</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">Confirm new password</label>
              <div class="relative">
                <input
                  :type="showConfirm ? 'text' : 'password'"
                  v-model="confirmPassword"
                  maxlength="64"
                  autocomplete="new-password"
                  placeholder="Re-enter new password"
                  class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black pr-12"
                />
                <button type="button" class="absolute right-2 top-1/2 -translate-y-1/2 p-2 text-gray-600 hover:text-gray-900"
                        @click="showConfirm = !showConfirm" aria-label="toggle confirm password visibility">
                  <svg v-if="!showConfirm" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" stroke="currentColor" stroke-width="1.5"/>
                    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M10.6 5.1C11.05 5.03 11.52 5 12 5c6.4 0 10 7 10 7a18.3 18.3 0 0 1-5.07 5.45M6.6 6.6C3.96 8.39 2 12 2 12s3.6 7 10 7c1.59 0 3.05-.36 4.35-.98" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                    <path d="M9.88 9.88A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
              <p v-if="!pwTooShort && pwMismatch" class="text-xs text-rose-600 mt-1">Passwords do not match.</p>
            </div>

            <div class="mt-2 pt-2 flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3">
              <button type="button" class="w-full sm:w-auto px-5 py-2.5 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200"
                      @click="backToProfile">
                Cancel
              </button>
              <button
                type="submit"
                class="w-full sm:w-auto px-6 py-2.5 rounded-lg bg-indigo-700 text-white hover:bg-indigo-800 disabled:bg-indigo-300 disabled:cursor-not-allowed"
                :disabled="isDisabled"
              >
                {{ isSubmitting ? "Saving…" : "Change password" }}
              </button>
            </div>
          </form>
        </div>
      </div>

      <p class="mt-6 text-xs text-gray-500 text-center">
        Forgot your current password?
        <router-link to="/forgot-password" class="text-indigo-600 hover:underline">Reset it here</router-link>.
      </p>
    </div>
  </div>
</template>

<style scoped>
button[aria-label*="visibility"] { line-height: 0; }
</style>

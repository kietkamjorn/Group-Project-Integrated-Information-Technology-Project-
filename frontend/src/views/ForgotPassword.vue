<script setup>
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { createDataPublic } from "@/libs/fetchUtils";

const router = useRouter();

const email = ref("");
const isSubmitting = ref(false);
const successMsg = ref("");
const errorMsg = ref("");

const isEmailFormatOk = computed(() => {
  const v = (email.value || "").trim();
  return !!v && /.+@.+\..+/.test(v);
});

const isDisabled = computed(() => isSubmitting.value || !isEmailFormatOk.value);

const backToSignIn = () => router.push("/signin");

const onSubmit = async () => {
  if (isDisabled.value) return;

  isSubmitting.value = true;
  successMsg.value = "";
  errorMsg.value = "";

  try {
    const payload = { email: (email.value || "").trim() };

    await createDataPublic("/v2/auth/forgot-password", payload);

    successMsg.value =
      "If an account exists for that email, we’ve sent a reset link.";
    setTimeout(() => (successMsg.value = ""), 6000);
  } catch (err) {
    const status = err?.response?.status;

    if (status === 404) {
      errorMsg.value = "We couldn’t find an account with that email.";
      setTimeout(() => (errorMsg.value = ""), 6000);
      return;
    }

    successMsg.value =
      "If an account exists for that email, we’ve sent a reset link.";
    setTimeout(() => (successMsg.value = ""), 6000);
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div class="flex justify-center items-center min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
    <div class="bg-white py-10 px-8 sm:px-12 rounded-2xl shadow-xl w-full max-w-xl border border-gray-100">
      <h2 class="text-3xl sm:text-4xl font-extrabold text-center text-indigo-700">Forgot Password</h2>
      <p class="mt-4 text-center text-gray-600 leading-relaxed">
        Enter your email address and we’ll send you a link to reset your password.
      </p>

      <!-- Success banner -->
      <div
        v-if="successMsg"
        class="mt-6 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-800 px-4 py-3"
        aria-live="polite"
      >
        {{ successMsg }}
      </div>

      <!-- Error banner -->
      <div
        v-if="errorMsg"
        class="mt-6 rounded-lg border border-rose-200 bg-rose-50 text-rose-800 px-4 py-3"
        aria-live="polite"
      >
        {{ errorMsg }}
      </div>

      <form class="mt-8 space-y-4" @submit.prevent="onSubmit">
        <label for="email" class="block font-medium text-gray-700">Email</label>
        <input
          id="email"
          type="email"
          v-model="email"
          maxlength="50"
          autocomplete="email"
          placeholder="you@example.com"
          class="w-full border rounded-lg px-4 py-3 focus:ring-2 focus:ring-indigo-400 text-black"
          required
          autofocus
        />
        <p v-if="email && !isEmailFormatOk" class="text-sm text-rose-600">
          Please enter a valid email address.
        </p>

        <div class="mt-6 pt-2 flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3">
          <button
            type="button"
            class="w-full sm:w-auto px-5 py-2.5 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200"
            @click="backToSignIn"
          >
            Back to sign in
          </button>

          <button
            type="submit"
            class="w-full sm:w-auto px-6 py-2.5 rounded-lg bg-indigo-700 text-white hover:bg-indigo-800 disabled:bg-indigo-300 disabled:cursor-not-allowed"
            :disabled="isDisabled"
          >
            {{ isSubmitting ? "Sending…" : "Send reset link" }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

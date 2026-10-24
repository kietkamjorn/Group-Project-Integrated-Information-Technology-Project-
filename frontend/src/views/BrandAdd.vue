<script setup>
import { ref, reactive, computed } from "vue";
import { useRouter } from "vue-router";
import { createData } from "@/libs/fetchUtils";

const router = useRouter();
const isLoading = ref(false);
const error = ref("");
const successMessage = ref("");

const product = reactive({
  name: "",
  websiteUrl: "",
  isActive: false,
  countryOfOrigin: "",
});

/* ====== ง่ายมาก: กด Enter ไปช่องถัดไปด้วย form.elements ====== */
const goNext = (e) => {
  e.preventDefault();
  const form = e.target?.form;
  if (!form) return;
  const els = Array.from(form.elements).filter(
    (el) => !el.disabled && el.type !== "hidden" && !el.readOnly
  );
  const idx = els.indexOf(e.target);
  const next = els[idx + 1] || form.querySelector('[type="submit"]');
  next?.focus?.();
};

/* ===== helpers & validation ===== */
const trim = (s) => (s ?? "").trim();
const ensureScheme = (u) => (/^[a-z]+:\/\//i.test(u) ? u : `https://${u}`);
const isValidUrl = (u) => {
  const t = trim(u);
  if (!t) return true;
  try {
    new URL(ensureScheme(t));
    return true;
  } catch {
    return false;
  }
};

const validationMessage = computed(() => {
  const name = trim(product.name);
  if (name.length < 1 || name.length > 30)
    return "Brand name must be 1–30 characters.";
  if (!isValidUrl(product.websiteUrl)) return "Website URL is invalid.";
  const c = trim(product.countryOfOrigin);
  if (c && c.length > 80) return "Country of origin must be at most 80 characters.";
  return "";
});

const submitProduct = async () => {
  if (validationMessage.value) return;
  isLoading.value = true;
  error.value = "";
  successMessage.value = "";
  try {
    const payload = {
      name: trim(product.name).slice(0, 30),
      websiteUrl: trim(product.websiteUrl)
        ? ensureScheme(trim(product.websiteUrl))
        : null,
      isActive: !!product.isActive,
      countryOfOrigin: trim(product.countryOfOrigin) || null,
    };
    await createData("/v2/brands", payload);
    successMessage.value = "The brand has been added.";
    setTimeout(() => router.push("/brands"), 900);
  } catch (err) {
    const st = err?.response?.status;
    const serverMsg =
      typeof err?.response?.data === "string"
        ? err.response.data
        : err?.response?.data?.message;
    error.value = serverMsg || `Create failed (status ${st ?? "unknown"}).`;
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="px-6 py-6">
    <nav class="text-gray-500 text-sm mb-4">
      <router-link to="/brands" class="hover:underline">Brand List</router-link>
      <span class="mx-2">></span><span>New Brand</span>
    </nav>

    <div class="bg-white rounded-lg shadow p-6 text-black">
      <form @submit.prevent="submitProduct" class="space-y-5">
        <!-- Name -->
        <div>
          <label class="block text-gray-700 font-medium mb-1">Name *</label>
          <input
            v-model="product.name"
            type="text"
            maxlength="30"
            placeholder="Brand name (1–30)"
            class="w-full border rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            @keydown.enter="goNext"
            autofocus
          />
        </div>

        <!-- Website URL -->
        <div>
          <label class="block text-gray-700 font-medium mb-1"
            >Website URL</label
          >
          <input
            v-model="product.websiteUrl"
            type="text"
            maxlength="200"
            placeholder="https://example.com"
            class="w-full border rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            @keydown.enter="goNext"
          />
        </div>

        <!-- Active -->
        <label class="label cursor-pointer items-center gap-3">
          <span class="text-gray-700 font-medium">Active</span>
          <input
            type="checkbox"
            v-model="product.isActive"
            class="toggle toggle-primary"
          />
        </label>

        <!-- Country Of Origin -->
        <div>
          <label class="block text-gray-700 font-medium mb-1"
            >Country of Origin</label
          >
          <input
            v-model="product.countryOfOrigin"
            type="text"
            maxlength="80"
            class="w-full border rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            @keydown.enter="goNext"
          />
        </div>

        <!-- Messages -->
        <div
          v-if="validationMessage"
          class="bg-red-100 text-red-700 px-4 py-2 rounded"
        >
          {{ validationMessage }}
        </div>
        <div v-if="error" class="bg-red-100 text-red-700 px-4 py-2 rounded">
          {{ error }}
        </div>
        <div
          v-if="successMessage"
          class="bg-green-100 text-green-700 px-4 py-2 rounded"
        >
          {{ successMessage }}
        </div>

        <!-- Actions -->
        <div class="flex gap-3 pt-2">
          <button
            type="submit"
            :disabled="isLoading || !!validationMessage"
            class="bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
          >
            {{ isLoading ? "Saving…" : "Save" }}
          </button>
          <button
            type="button"
            class="bg-gray-200 text-gray-800 px-6 py-2 rounded-md hover:bg-gray-300"
            @click="$router.push('/brands')"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

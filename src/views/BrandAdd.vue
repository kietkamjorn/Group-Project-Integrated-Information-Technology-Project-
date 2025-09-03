<script setup>
import { ref, computed, onMounted, nextTick, watch, reactive } from "vue";
import { useRouter } from "vue-router";
import { createData } from "../libs/fetchUtils";
const router = useRouter();
const error = ref(null);
const isLoading = ref(false);
const fieldRefs = [];
const successMessage = ref("");



if (window.__VUE_DEVTOOLS_GLOBAL_HOOK__) {
  const devtoolsPanel = document.querySelector(".vue-devtools__panel");
  if (devtoolsPanel) devtoolsPanel.style.display = "none";
}

const product = reactive({
  name: "",
  websiteUrl: "",
  isActive: false,
  countryOfOrigin: "",
});

const isValidUrl = (u) => {
  try {
    new URL(u);
    return true;
  } catch {
    return false;
  }
};

const validationMessage = computed(() => {
  const name = (product.name ?? "").trim();
  if (name.length < 1 || name.length > 30) {
    return "Brand name must be 1-30 characters long.";
  }

  const country = (product.countryOfOrigin ?? "").trim();
  if (country.length > 0 && (country.length < 1 || country.length > 80)) {
    return "Brand country of origin must be 1-80 characters long or not specified.";
  }

  const url = (product.websiteUrl ?? "").trim();
  if (url.length > 0 && !isValidUrl(url)) {
    return "Brand URL must be a valid URL or not specified.";
  }

  return null;
});

const isFormValid = computed(() => {
  return product.name.trim() !== "";
});

const focusNext = (index) => {
  const nextField = fieldRefs[index + 1];
  if (nextField?.focus) {
    nextTick(() => nextField.focus());
  }
};

watch(
  () => product.isActive,
  (newVal) => {}
);

const submitProduct = async () => {
  try {
    isLoading.value = true;

    if (!product.name.trim()) {
      error.value = "Please enter brand name.";
      return;
    }

    const payload = {
      name: product.name.trim().slice(0, 80),
      websiteUrl: (product.websiteUrl ?? "").trim(), // ไม่ใช้ maxlength ใน input
      isActive: !!product.isActive,
      countryOfOrigin: (product.countryOfOrigin ?? "").trim(), // ไม่ใช้ maxlength ใน input
    };

    await createData("/v2/brands", payload);

    successMessage.value = "The brand has been added.";

    setTimeout(() => {
      router.push("/brands");
    }, 2000);
  } catch (err) {
    error.value = err.message;
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {});
</script>

<template>
  <div class="px-10 py-8">
    <nav class="text-gray-500 text-lg mb-6">
      <router-link
        to="/sale-items/list"
        class="hover:underline font-bold Itbms-item-list"
        >Sale Item List</router-link
      >
      <span class="mx-2"> > </span>
      <router-link
        to="/brands"
        class="hover:underline font-bold itbms-add-button"
        >Brand List</router-link
      >
      <span class="mx-2"> > </span>
      <span class="mx-2"> New Brand </span>
    </nav>
    <div class="itbms-row bg-white rounded-lg shadow-md p-6 text-black">
      <form @submit.prevent="submitProduct" class="space-y-6">
        <!-- Name -->
        <label class="block">
          <span class="text-gray-700 font-medium">Name</span>
          <input
            v-model="product.name"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-name"
            :ref="(el) => (fieldRefs[0] = el)"
            @keydown.enter.prevent="focusNext(0)"
            maxlength="80"
            placeholder="Enter brand name"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ product.name.length }}/80
          </p>
        </label>

        <!-- Website URL -->
        <label class="block">
          <span class="text-gray-700 font-medium">Website URL</span>
          <input
            v-model="product.websiteUrl"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-websiteUrl"
            :ref="(el) => (fieldRefs[1] = el)"
            @keydown.enter.prevent="focusNext(1)"
            placeholder="Enter website URL"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ product.websiteUrl?.length || 0 }}/40
          </p>
        </label>

        <!-- Active Toggle -->
        <label class="flex items-center gap-4 cursor-pointer">
          <span class="text-gray-700 font-medium">Active</span>
          <div class="relative w-14 h-7">
            <input
              type="checkbox"
              class="peer opacity-0 absolute inset-0 cursor-pointer itbms-isActive"
              v-model="product.isActive"
              style="z-index: 10"
            />
            <div
              class="w-full h-full bg-gray-300 rounded-full peer-checked:bg-blue-600 transition-colors duration-300 ease-in-out"
            ></div>
            <div
              class="absolute left-1 top-0.5 w-6 h-6 bg-white rounded-full shadow-sm transition-transform duration-300 ease-in-out peer-checked:translate-x-7"
              style="z-index: 0"
            ></div>
          </div>
        </label>

        <!-- Country Of Origin -->
        <label class="block">
          <span class="text-gray-700 font-medium">Country Of Origin</span>
          <input
            v-model="product.countryOfOrigin"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-countryOfOrigin"
            :ref="(el) => (fieldRefs[2] = el)"
            @keydown.enter.prevent="focusNext(2)"
            placeholder="Enter country of origin"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ product.countryOfOrigin.length }}/80
          </p>
        </label>

        <!-- Save / Cancel Buttons -->
        <div class="flex gap-4 pb-5">
          <button
            type="submit"
            :disabled="isLoading || !!validationMessage"
            class="bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-all disabled:bg-gray-400 disabled:cursor-not-allowed itbms-save-button"
          >
            Save
          </button>
          <button
            type="button"
            class="bg-red-600 text-white px-6 py-2 rounded-md hover:bg-red-700 focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all itbms-cancel-button"
            @click="router.push('/brands')"
          >
            Cancel
          </button>
        </div>

        <!-- Success/Error Messages -->
        <div
          v-if="validationMessage"
          class="mb-4 bg-red-100 text-red-600 px-4 py-3 rounded shadow itbms-message"
        >
          {{ validationMessage }}
        </div>
        <div
          v-if="successMessage"
          class="text-green-600 bg-green-100 p-3 pb-6 rounded-md itbms-message"
        >
          {{ successMessage }}
        </div>
        <div v-if="error" class="text-red-600 bg-red-100 p-3 rounded-md">
          {{ error }}
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped></style>

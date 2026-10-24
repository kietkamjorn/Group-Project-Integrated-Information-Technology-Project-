<!-- src/views/Brandedit.vue -->
<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData, updateData } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const brand = ref({});
const originalBrand = ref({});
const error = ref("");
const successMessage = ref("");

// เปรียบเทียบสภาพเดิมกับปัจจุบัน
const hasChanged = computed(
  () => JSON.stringify(brand.value) !== JSON.stringify(originalBrand.value)
);

/* ========== Validation แบบสั้น/พื้นฐาน ========== */
const trim = (s) => (s ?? "").trim();
const isValidUrl = (u) => {
  const t = trim(u);
  if (!t) return true; // optional
  try {
    new URL(t);
    return true;
  } catch {
    return false;
  }
};

const validationMessage = computed(() => {
  const name = trim(brand.value.name);
  const origin = trim(brand.value.countryOfOrigin);
  const url = trim(brand.value.websiteUrl);

  if (name.length < 1 || name.length > 30) {
    return "Brand name must be 1-30 characters long.";
  }
  if (origin && (origin.length < 1 || origin.length > 80)) {
    return "Brand country of origin must be 1-80 characters long or not specified.";
  }
  if (!isValidUrl(url)) {
    return "Brand URL must be a valid URL or not specified.";
  }
  return null;
});

/* ========== โหลดข้อมูล ========== */
const loadBrand = async () => {
  const id = Number(route.params.id);
  if (!Number.isFinite(id)) {
    error.value = "Invalid brand id";
    return;
  }
  try {
    const data = await readData(`/v2/brands/${id}`);
    brand.value = { ...data };
    originalBrand.value = { ...data };
  } catch (err) {
    if (err?.response?.status === 404) {
      router.push({
        path: "/brands",
        query: { errorMessage: "The brand does not exist." },
      });
    } else {
      error.value = "ไม่สามารถโหลดข้อมูลแบรนด์ได้";
    }
  }
};
onMounted(loadBrand);

/* ========== บันทึก ========== */
const saveBrand = async () => {
  if (validationMessage.value) return;
  try {
    const id = Number(brand.value.id ?? route.params.id);
    const body = {
      name: trim(brand.value.name),
      websiteUrl: trim(brand.value.websiteUrl) || null,
      countryOfOrigin: trim(brand.value.countryOfOrigin) || null,
      isActive: !!brand.value.isActive,
    };
    await updateData(`/v2/brands/${id}`, body);
    successMessage.value = "The brand has been updated.";
    setTimeout(() => router.push("/brands"), 1200);
  } catch (err) {
    error.value =
      err?.response?.data?.message ||
      (typeof err?.response?.data === "string" ? err.response.data : "") ||
      "ไม่สามารถอัปเดตแบรนด์ได้";
  }
};

/* ========== ย้ายโฟกัสแบบง่าย (พื้นฐาน) ========== */
const goNext = (e) => {
  const form = e.target?.form;
  if (!form) return;
  const i = Array.from(form.elements).indexOf(e.target);
  const next = form.elements[i + 1];
  if (next?.focus) next.focus();
};
</script>

<template>
  <div class="px-10 py-8 text-black">
    <nav class="text-gray-500 text-lg mb-6 pb-2">
      <router-link to="/brands" class="hover:underline font-bold"
        >Brand List</router-link
      >
      <span class="mx-2"> > </span>
      <span>Edit Brand</span>
    </nav>

    <div
      v-if="Object.keys(brand).length > 0"
      class="itbms-row bg-white rounded-lg shadow-md p-6"
    >
      <form @submit.prevent="saveBrand" class="space-y-6">
        <!-- Brand Name -->
        <label class="block">
          <span class="text-gray-700 font-medium">Brand Name</span>
          <input
            v-model="brand.name"
            maxlength="30"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-name"
            @keydown.enter.prevent="goNext"
            placeholder="Enter brand name"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ (brand.name || "").length }}/30
          </p>
        </label>

        <!-- Website URL -->
        <label class="block">
          <span class="text-gray-700 font-medium">Website URL</span>
          <input
            v-model="brand.websiteUrl"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-websiteUrl"
            @keydown.enter.prevent="goNext"
            placeholder="Enter website URL"
          />
        </label>

        <!-- Active Toggle -->
        <label class="label cursor-pointer items-center gap-3">
          <span class="text-gray-700 font-medium">Active</span>
          <input
            type="checkbox"
            v-model="brand.isActive"
            class="toggle toggle-primary"
            @keydown.enter.prevent="goNext"
          />
        </label>

        <!-- Country Of Origin -->
        <label class="block">
          <span class="text-gray-700 font-medium">Country Of Origin</span>
          <input
            v-model="brand.countryOfOrigin"
            maxlength="80"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-countryOfOrigin"
            @keydown.enter.prevent="goNext"
            placeholder="Enter country of origin"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ (brand.countryOfOrigin || "").length }}/80
          </p>
        </label>

        <!-- Save / Cancel -->
        <div class="flex gap-4">
          <button
            type="submit"
            :disabled="!hasChanged || !!validationMessage"
            class="bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-all disabled:bg-gray-400 disabled:cursor-not-allowed itbms-save-button"
          >
            Save
          </button>
          <router-link
            to="/brands"
            class="bg-red-600 text-white px-6 py-2 rounded-md hover:bg-red-700 focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all text-center itbms-cancel-button"
          >
            Cancel
          </router-link>
        </div>

        <!-- Messages -->
        <div
          v-if="validationMessage"
          class="text-red-600 bg-red-100 p-3 rounded-md itbms-message"
        >
          {{ validationMessage }}
        </div>
        <div
          v-if="successMessage"
          class="text-green-600 bg-green-100 p-3 rounded-md itbms-message"
        >
          {{ successMessage }}
        </div>
        <div
          v-if="error"
          class="text-red-600 bg-red-100 p-3 rounded-md itbms-message"
        >
          {{ error }}
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped></style>

<script setup>
import { ref, onMounted, computed, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData, updateData } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();
const brand = ref({});
const originalBrand = ref({});
const error = ref("");
const successMessage = ref("");
const fieldRefs = [];

const focusNext = (index) => {
  const nextField = fieldRefs[index + 1];
  if (nextField?.focus) nextTick(() => nextField.focus());
};

const hasChanged = computed(() =>
  JSON.stringify(brand.value) !== JSON.stringify(originalBrand.value)
);

// ====== Validation ======
const trim = (s) => (s ?? "").trim();

function isValidUrl(u) {
  const t = trim(u);
  if (!t) return true; // optional
  try {
    new URL(t);
    return true;
  } catch {
    return false;
  }
}

const validationMessage = computed(() => {
  const name = trim(brand.value.name);
  const origin = trim(brand.value.countryOfOrigin);
  const url = trim(brand.value.websiteUrl);

  // 1) Name: 1–30 chars หลัง trim
  if (name.length < 1 || name.length > 30) {
    return "Brand name must be 1-30 characters long.";
  }

  // 2) Country: optional; ถ้ากรอกแล้วต้อง 1–80 chars หลัง trim
  if (origin && (origin.length < 1 || origin.length > 80)) {
    return "Brand country of origin must be 1-80 characters long or not specified.";
  }

  // 3) URL: optional; ถ้ากรอกต้องเป็น URL ที่ valid
  if (!isValidUrl(url)) {
    return "Brand URL must be a valid URL or not specified.";
  }

  return null;
});

const loadBrand = async () => {
  const brandId = route.params.id;
  try {
    const data = await readData(`/itb-mshop/v1/brands/${brandId}`);
    brand.value = structuredClone(data);
    originalBrand.value = structuredClone(data);
  } catch (err) {
    if (err?.response?.status === 404) {
      router.push({
        path: "/brands",
        query: { message: "The brand does not exist." },
      });
    } else {
      error.value = "ไม่สามารถโหลดข้อมูลแบรนด์ได้";
    }
  }
};

const saveBrand = async () => {
  // กันพลาด ถ้ายังมี error ก็ไม่ต้องยิง
  if (validationMessage.value) return;
  try {
    await updateData(`/itb-mshop/v1/brands/${brand.value.id}`, brand.value);
    successMessage.value = "The brand has been updated.";
    setTimeout(() => router.push("/brands"), 1500);
  } catch (err) {
    error.value = "ไม่สามารถอัปเดตแบรนด์ได้";
  }
};

onMounted(loadBrand);
</script>

<template>
  <div class="px-10 py-8 text-black">
    <nav class="text-gray-500 text-lg mb-6 pb-2">
      <router-link to="/brands" class="hover:underline font-bold">Brand List</router-link>
      <span class="mx-2"> > </span>
      <span>Edit Brand</span>
    </nav>

    <div v-if="Object.keys(brand).length > 0" class="itbms-row bg-white rounded-lg shadow-md p-6">
      <form @submit.prevent="saveBrand" class="space-y-6">
        <!-- Brand Name (เอา maxlength ออก) -->
        <label class="block">
          <span class="text-gray-700 font-medium">Brand Name</span>
          <input
            v-model="brand.name"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-name"
            :ref="(el) => (fieldRefs[0] = el)"
            @keydown.enter.prevent="focusNext(0)"
            placeholder="Enter brand name"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ brand.name ? brand.name.length : 0 }}
          </p>
        </label>

        <!-- Website URL -->
        <label class="block">
          <span class="text-gray-700 font-medium">Website URL</span>
          <input
            v-model="brand.websiteUrl"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-websiteUrl"
            :ref="(el) => (fieldRefs[1] = el)"
            @keydown.enter.prevent="focusNext(1)"
            placeholder="Enter website URL"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ brand.websiteUrl ? brand.websiteUrl.length : 0 }}
          </p>
        </label>

        <!-- Active Toggle -->
        <label class="flex items-center gap-4">
          <span class="text-gray-700 font-medium">Active</span>
          <div class="relative w-14 h-7">
            <input
              type="checkbox"
              v-model="brand.isActive"
              class="peer opacity-0 absolute inset-0 cursor-pointer itbms-isActive"
            />
            <div class="w-full h-full bg-gray-300 rounded-full peer-checked:bg-blue-600 transition-colors duration-300 ease-in-out"></div>
            <div class="absolute left-1 top-0.5 w-6 h-6 bg-white rounded-full shadow-sm transition-transform duration-300 ease-in-out peer-checked:translate-x-7"></div>
          </div>
        </label>

        <!-- Country Of Origin (เอา maxlength ออก) -->
        <label class="block">
          <span class="text-gray-700 font-medium">Country Of Origin</span>
          <input
            v-model="brand.countryOfOrigin"
            class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-countryOfOrigin"
            :ref="(el) => (fieldRefs[2] = el)"
            @keydown.enter.prevent="focusNext(2)"
            placeholder="Enter country of origin"
          />
          <p class="text-sm text-gray-500 text-right mt-1">
            {{ brand.countryOfOrigin ? brand.countryOfOrigin.length : 0 }}
          </p>
        </label>

        <!-- Save / Cancel Buttons -->
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

        <!-- Validation message ตรงตามที่ Cypress เช็ค -->
        <div v-if="validationMessage" class="text-red-600 bg-red-100 p-3 rounded-md itbms-message">
          {{ validationMessage }}
        </div>

        <!-- Success/Error -->
        <div v-if="successMessage" class="text-green-600 bg-green-100 p-3 rounded-md itbms-message">
          {{ successMessage }}
        </div>
        <div v-if="error" class="text-red-600 bg-red-100 p-3 rounded-md itbms-message">
          {{ error }}
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped></style>

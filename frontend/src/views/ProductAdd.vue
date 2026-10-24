<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "../libs/fetchUtils";

const router = useRouter();
const route = useRoute();

const error = ref(null);
const isLoading = ref(false);
const fieldRefs = [];
const successMessage = ref("");

const product = ref({
  brand: null,
  model: "",
  description: "",
  price: null,
  ramGb: null,
  screenSizeInch: null,
  quantity: null,
  storageGb: null,
  color: "",
  image: null,
});

const brands = ref([]);
const fileInput = ref(null);
const selectedFileNames = ref([]);
const previewUrls = ref([]);
const selectedFiles = ref([]);
const uploadError = ref("");

const openFilePicker = () => fileInput.value?.click();

function removeFile(idx) {
  const removedUrl = previewUrls.value[idx];
  previewUrls.value = previewUrls.value.filter((_, i) => i !== idx);
  selectedFileNames.value = selectedFileNames.value.filter((_, i) => i !== idx);
  selectedFiles.value = selectedFiles.value.filter((_, i) => i !== idx);
  if (product.value.image === removedUrl) {
    product.value.image = previewUrls.value[0] || null;
  }
  if (removedUrl?.startsWith("blob:")) {
    try { URL.revokeObjectURL(removedUrl); } catch {}
  }
}

const MAX_IMAGES = 4;
const SIZE_LIMIT = 2 * 1024 * 1024; // 2MB

const handleFilesSelected = (e) => {
  uploadError.value = "";
  const remaining = Math.max(0, MAX_IMAGES - previewUrls.value.length);
  if (!remaining) {
    uploadError.value = `You can upload up to ${MAX_IMAGES} images.`;
    e.target.value = "";
    return;
  }

  const picked = Array.from(e.target.files || []).slice(0, remaining);
  const tooBig = [];
  const valid = picked.filter((f) => {
    const ok = f.type.startsWith("image/") && f.size <= SIZE_LIMIT;
    if (!ok && f.size > SIZE_LIMIT) tooBig.push(f.name);
    return ok;
  });

  if (tooBig.length) {
    uploadError.value = `These files exceed 2MB: ${tooBig.join(", ")}`;
  }
  if (!valid.length) {
    e.target.value = "";
    return;
  }

  const urls = valid.map((f) => URL.createObjectURL(f));
  previewUrls.value = [...previewUrls.value, ...urls].slice(0, MAX_IMAGES);
  selectedFileNames.value = [
    ...selectedFileNames.value,
    ...valid.map((f) => f.name),
  ].slice(0, MAX_IMAGES);
  selectedFiles.value = [...selectedFiles.value, ...valid].slice(0, MAX_IMAGES);

  if (!product.value.image) product.value.image = urls[0];
  e.target.value = "";
};

const focusNext = (index) => {
  const nextField = fieldRefs[index + 1];
  if (nextField?.focus) nextTick(() => nextField.focus());
};

const modelNonSpaceLen = computed(() =>
  Array.from(product.value.model ?? "").filter((ch) => !/\s/.test(ch)).length
);

// ===== Validation =====
function decimalPlaces(n) {
  const s = String(n ?? "");
  const i = s.indexOf(".");
  return i >= 0 ? s.length - i - 1 : 0;
}

const validationMessage = computed(() => {
  const m = (product.value.model ?? "").trim();
  const d = (product.value.description ?? "").trim();
  const c = (product.value.color ?? "").trim();

  if (!product.value.brand) return "Brand must be selected.";
  if (m.length < 1 || m.length > 60) return "Model must be 1–60 characters long.";

  const p = product.value.price;
  if (!Number.isInteger(p) || p < 0) return "Price must be a non-negative integer.";

  if (d.length < 1 || d.length > 16384) return "Description must be 1–16,384 characters long.";

  const q = product.value.quantity;
  if (!Number.isInteger(q) || q < 0) return "Quantity must be a non-negative integer.";

  const s = product.value.screenSizeInch;
  if (s !== null && s !== undefined && s !== "") {
    const num = Number(s);
    if (!(num > 0) || decimalPlaces(num) > 2) {
      return "Screen size must be a positive number with at most 2 decimals, or left blank.";
    }
  }

  const r = product.value.ramGb;
  if (r !== null && r !== undefined && r !== "") {
    if (!Number.isInteger(r) || r <= 0)
      return "RAM must be a positive integer, or left blank.";
  }

  const st = product.value.storageGb;
  if (st !== null && st !== undefined && st !== "") {
    if (!Number.isInteger(st) || st <= 0)
      return "Storage must be a positive integer, or left blank.";
  }

  if (c.length > 40) return "Color must be 1–40 characters, or left blank.";

  return null;
});

// ===== Load brands (Auth) =====
const loadBrands = async () => {
  try {
    const data = await withAuthFetch("/v2/brands");
    const list = Array.isArray(data) ? data : [];
    brands.value = list.map((b) => ({ id: b.id ?? b.brandId, name: b.name }));
  } catch (err) {
    console.error("Failed to load brands:", err);
    brands.value = [];
  }
};

onMounted(() => {
  loadBrands();
});

// ===== Submit (JSON + Auth) =====
const submitProduct = async () => {
  try {
    isLoading.value = true;
    error.value = null;
    successMessage.value = "";

    if (validationMessage.value) {
      error.value = validationMessage.value;
      return;
    }
    if (!product.value.brand) {
      error.value = "Please select a brand.";
      return;
    }

    const brandPk = product.value.brand?.id ?? product.value.brand?.brandId;
    if (!brandPk) {
      error.value = "Brand id not found.";
      return;
    }

    const token = getAccessToken?.();
    const user = token ? decodeJwt(token) : null;
    const sellerId = user?.sub;
    if (!sellerId) {
      error.value = "Please sign in again.";
      return;
    }

    const payload = {
      brandId: brandPk,
      model: (product.value.model ?? "").trim(),
      description: (product.value.description ?? "").trim(),
      price: Number.isInteger(product.value.price) ? product.value.price : 0,
      quantity: Number.isInteger(product.value.quantity) ? product.value.quantity : 0,
      storageGb:
        product.value.storageGb != null && product.value.storageGb !== ""
          ? Number(product.value.storageGb)
          : null,
      ramGb:
        product.value.ramGb != null && product.value.ramGb !== ""
          ? Number(product.value.ramGb)
          : null,
      screenSizeInch:
        product.value.screenSizeInch != null && String(product.value.screenSizeInch) !== ""
          ? Number(product.value.screenSizeInch)
          : null,
      color: (product.value.color ?? "").trim() || null,
    };

    await withAuthFetch(`/v2/sellers/${sellerId}/sale-items`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    successMessage.value = "The sale item has been successfully added.";
    router.push({ path: "/sale-items/list", query: { success: "true" } });
  } catch (err) {
    error.value =
      (typeof err?.response?.data === "string" && err.response.data) ||
      err?.response?.data?.message ||
      err?.message ||
      "Save failed.";
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="bg-gray-100 flex items-center justify-center p-4 sm:p-6 lg:p-8 text-black">
    <div class="w-full bg-white rounded-lg shadow-lg p-6 sm:p-8 lg:p-10">
      <nav class="text-gray-600 text-sm sm:text-base mb-6 flex space-x-2 pb-4">
        <router-link to="/sale-items" class="hover:text-blue-600 font-medium transition-colors itbms-home-button">
          Home
        </router-link>
        <span class="text-gray-400"> > </span>
        <span class="text-gray-500"> New Sale Item</span>
      </nav>

      <div class="itbms-row flex flex-col lg:flex-row gap-8">
        <!-- Image Section -->
        <div class="flex-shrink-0">
          <div class="w-full max-w-[600px] aspect-square overflow-hidden rounded-lg shadow-sm mx-auto">
            <img
              :src="product.image || previewUrls[0] || 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png'"
              alt="Product Image"
              class="w-130 h-130 object-cover"
            />
          </div>

          <div class="flex justify-center gap-3 pt-4 pb-8 flex-wrap">
            <div
              v-for="(thumb, index) in previewUrls.length ? previewUrls : [
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
              ]"
              :key="index"
              class="w-16 h-16 rounded-md overflow-hidden border transition-colors duration-200"
              :class="thumb === product.image ? 'border-blue-500' : 'border-gray-200'"
            >
              <img
                :src="thumb"
                alt="Thumbnail Image"
                class="w-full h-full object-cover cursor-pointer hover:scale-105 transition-transform duration-200"
                @click="product.image = thumb"
              />
            </div>
          </div>

          <button
            type="button"
            @click="openFilePicker"
            class="block mt-8 bg-blue-400 text-white px-4 py-2 rounded-md hover:bg-blue-700 focus:ring-2 transition-all text-center itbms-upload-button"
          >
            Upload Pictures
          </button>

          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            capture="environment"
            multiple
            class="hidden"
            @change="handleFilesSelected"
          />

          <div v-for="(n, idx) in selectedFileNames" :key="n + idx" class="flex items-center gap-2 bg-gray-100">
            <span class="text-sm bg-gray-100 px-2 py-1 rounded w-fit break-all">{{ n }}</span>
            <button
              type="button"
              class="text-gray-500 hover:text-red-600 font-bold leading-none"
              aria-label="Remove file"
              @click="removeFile(idx)"
            >
              ×
            </button>
          </div>

          <p v-if="uploadError" class="mt-3 text-sm text-rose-600">{{ uploadError }}</p>
        </div>

        <!-- Form Section -->
        <form @submit.prevent="submitProduct" class="flex-1 space-y-6">
          <!-- Brand -->
          <label class="block">
            <span class="text-gray-700 font-medium">Brand</span>
            <select
              v-model="product.brand"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-brand"
            >
              <option :value="null" disabled hidden>Select brand</option>
              <option v-for="b in brands" :key="b.id ?? b.brandId" :value="b">
                {{ b.name }}
              </option>
            </select>
          </label>

          <!-- Model -->
          <label class="block">
            <span class="text-gray-700 font-medium">Model</span>
            <input
              v-model="product.model"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-model"
              :ref="(el) => (fieldRefs[1] = el)"
              @keydown.enter.prevent="focusNext(1)"
              placeholder="Enter model name"
              maxlength="60"
            />
            <p class="text-sm text-gray-500 text-right mt-1">{{ modelNonSpaceLen }}/60</p>
          </label>

          <!-- Price -->
          <label class="block">
            <span class="text-gray-700 font-medium">Price (Baht)</span>
            <input
              type="number"
              inputmode="numeric"
              min="0"
              step="1"
              v-model.number="product.price"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-price"
              :ref="(el) => (fieldRefs[2] = el)"
              @keydown.enter.prevent="focusNext(2)"
              placeholder="Enter price"
            />
          </label>

          <!-- Description -->
          <label class="block">
            <span class="text-gray-700 font-medium">Description</span>
            <textarea
              v-model="product.description"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-description"
              :ref="(el) => (fieldRefs[3] = el)"
              @keydown.enter.prevent="focusNext(3)"
              rows="4"
              placeholder="Enter product description"
              maxlength="16384"
            ></textarea>
          </label>

          <!-- RAM -->
          <label class="block">
            <span class="text-gray-700 font-medium">RAM (GB)</span>
            <input
              type="number"
              inputmode="numeric"
              min="1"
              step="1"
              v-model.number="product.ramGb"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-ramGb"
              :ref="(el) => (fieldRefs[4] = el)"
              @keydown.enter.prevent="focusNext(4)"
              placeholder="Enter RAM size"
            />
          </label>

          <!-- Screen Size -->
          <label class="block">
            <span class="text-gray-700 font-medium">Screen Size (Inches)</span>
            <input
              type="number"
              inputmode="decimal"
              min="0.01"
              step="0.01"
              v-model.number="product.screenSizeInch"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-screenSizeInch"
              :ref="(el) => (fieldRefs[5] = el)"
              @keydown.enter.prevent="focusNext(5)"
              placeholder="Enter screen size"
            />
          </label>

          <!-- Storage -->
          <label class="block">
            <span class="text-gray-700 font-medium">Storage (GB)</span>
            <input
              type="number"
              inputmode="numeric"
              min="1"
              step="1"
              v-model.number="product.storageGb"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-storageGb"
              :ref="(el) => (fieldRefs[6] = el)"
              @keydown.enter.prevent="focusNext(6)"
              placeholder="Enter storage size"
            />
          </label>

          <!-- Color -->
          <label class="block">
            <span class="text-gray-700 font-medium">Color</span>
            <input
              v-model="product.color"
              maxlength="40"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-color"
              :ref="(el) => (fieldRefs[7] = el)"
              @keydown.enter.prevent="focusNext(7)"
              placeholder="Enter color"
            />
          </label>

          <!-- Quantity -->
          <label class="block">
            <span class="text-gray-700 font-medium">Quantity</span>
            <input
              type="number"
              inputmode="numeric"
              min="0"
              step="1"
              v-model.number="product.quantity"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-quantity"
              :ref="(el) => (fieldRefs[8] = el)"
              @keydown.enter.prevent="submitProduct"
              placeholder="Enter quantity"
            />
          </label>

          <!-- Buttons -->
          <div class="flex gap-4 pt-5 pb-5">
            <button
              type="submit"
              class="bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-all disabled:bg-gray-400 disabled:cursor-not-allowed itbms-save-button"
              :disabled="isLoading || !!validationMessage"
            >
              {{ isLoading ? "Saving..." : "Save" }}
            </button>
            <router-link
              to="/sale-items"
              class="bg-red-600 text-white px-6 py-2 rounded-md hover:bg-red-700 focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all text-center itbms-cancel-button"
            >
              Cancel
            </router-link>
          </div>

          <!-- Messages -->
          <div v-if="validationMessage" class="mb-4 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message">
            {{ validationMessage }}
          </div>

          <div v-if="successMessage" class="text-green-600 bg-green-100 p-3 rounded-md itbms-message">
            {{ successMessage }}
          </div>
          <div v-if="error" class="text-red-600 bg-red-100 p-3 rounded-md">
            {{ error }}
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped></style>

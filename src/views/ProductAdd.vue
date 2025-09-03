<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
import { readData, postMultipart } from "../libs/fetchUtils";
const router = useRouter();
const route = useRoute();
const error = ref(null);
const isLoading = ref(false);
const fieldRefs = [];
const successMessage = ref("");
const referringPath = ref("");
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
});
const brands = ref([]);
const fileInput = ref(null);
const selectedFileNames = ref([]);
const previewUrls = ref([]); 
const selectedFiles = ref([]); 
const uploadError = ref(null);

const apiOrigin = new URL(
  import.meta.env.VITE_API_URL || window.location.origin
).origin;
const toAbsoluteMediaUrl = (u) =>
  !u ? null : u.startsWith("http") ? u : `${apiOrigin}${u}`;

function sanitizeEdges(s, max) {
  const v = (s ?? "").replace(/^\s+|\s+$/g, ""); 
  return max ? v.slice(0, max) : v; 
}

const openFilePicker = () => {
  fileInput.value?.click();
};

function removeFile(idx) {
  const removedUrl = previewUrls.value[idx];
  previewUrls.value = previewUrls.value.filter((_, i) => i !== idx);
  selectedFileNames.value = selectedFileNames.value.filter((_, i) => i !== idx);
  selectedFiles.value = selectedFiles.value.filter((_, i) => i !== idx);
  if (product.value.image === removedUrl) {
    product.value.image = previewUrls.value[0] || null;
  }
  if (removedUrl?.startsWith("blob:")) {
    try {
      URL.revokeObjectURL(removedUrl);
    } catch {}
  }
}

const handleFilesSelected = (e) => {
  uploadError.value = null;
  const maxTotal = 4;
  const sizeLimit = 2 * 1024 * 1024;

  const remaining = Math.max(0, maxTotal - previewUrls.value.length);
  if (remaining === 0) {
    uploadError.value = "อัปโหลดได้สูงสุด 4 รูป";
    e.target.value = "";
    return;
  }

  const picked = Array.from(e.target.files || []).slice(0, remaining);
  const tooBig = [];
  const valid = picked.filter((f) => {
    const ok = f.type.startsWith("image/") && f.size <= sizeLimit;
    if (!ok && f.size > sizeLimit) tooBig.push(f.name);
    return ok;
  });
  if (tooBig.length) uploadError.value = `ไฟล์เกิน 2MB: ${tooBig.join(", ")}`;
  if (!valid.length) {
    e.target.value = "";
    return;
  }

  const urls = valid.map((f) => URL.createObjectURL(f));
  previewUrls.value = [...previewUrls.value, ...urls].slice(0, maxTotal);
  selectedFileNames.value = [
    ...selectedFileNames.value,
    ...valid.map((f) => f.name),
  ].slice(0, maxTotal);
  selectedFiles.value = [...selectedFiles.value, ...valid].slice(0, maxTotal);

  if (!product.value.image) product.value.image = urls[0];
  e.target.value = "";
};

const focusNext = (index) => {
  const nextField = fieldRefs[index + 1];
  if (nextField?.focus) {
    nextTick(() => nextField.focus());
  }
};

const modelNonSpaceLen = computed(
  () =>
    Array.from(product.value.model ?? "").filter((ch) => !/\s/.test(ch)).length
);

//valid
function decimalPlaces(n) {
  const s = String(n ?? "");
  const i = s.indexOf(".");
  return i >= 0 ? s.length - i - 1 : 0;
}

const validationMessage = computed(() => {
  const m = (product.value.model ?? "").trim();
  const d = (product.value.description ?? "").trim();
  const c = (product.value.color ?? "").trim();

  // 1) Brand
  if (!product.value.brand) return "Brand must be selected.";

  // 2) Model: 1–60 หลัง trim
  if (m.length < 1 || m.length > 60)
    return "Model must be 1-60 characters long.";

  // 3) Price: non-negative integer
  const p = product.value.price;
  if (!Number.isInteger(p) || p < 0)
    return "Price must be non-negative integer.";

  // 4) Description: 1–16,384 หลัง trim
  if (d.length < 1 || d.length > 16384)
    return "Description must be 1-16,384 characters long.";

  // 5) Quantity: non-negative integer
  const q = product.value.quantity;
  if (!Number.isInteger(q) || q < 0)
    return "Quantity must be non-negative integer.";

  // 6) Screen size: >0 และทศนิยมไม่เกิน 2 (หรือไม่ระบุ)
  const s = product.value.screenSizeInch;
  if (s !== null && s !== undefined && s !== "") {
    const num = Number(s);
    if (!(num > 0) || decimalPlaces(num) > 2) {
      return "Screen size must be positive number with at most 2 decimal points or not specified.";
    }
  }

  // 7) RAM: บวกเท่านั้น (หรือไม่ระบุ)
  const r = product.value.ramGb;
  if (r !== null && r !== undefined && r !== "") {
    if (!Number.isInteger(r) || r <= 0)
      return "RAM size must be positive integer or not specified.";
  }

  // 8) Storage: บวกเท่านั้น (หรือไม่ระบุ)
  const st = product.value.storageGb;
  if (st !== null && st !== undefined && st !== "") {
    if (!Number.isInteger(st) || st <= 0)
      return "Storage size must be positive integer or not specified.";
  }

  // 9) Color: optional แต่ถ้ากรอก ต้อง 1–40 หลัง trim
  if (c.length > 40)
    return "Color must be 1-40 characters long or not specified.";

  return null;
});

// ฟังก์ชันสำหรับดึงข้อมูลแบรนด์
const loadBrands = async () => {
  try {
    const data = await readData("/v2/brands"); // API สำหรับดึงข้อมูลแบรนด์
    brands.value = data;
  } catch (err) {
    console.error("โหลดแบรนด์ไม่สำเร็จ", err);
  }
};

// เรียกฟังก์ชัน loadBrands เมื่อ component ถูก mounted
onMounted(() => {
  loadBrands();
  if (route.query.from) {
    referringPath.value = route.query.from;
  } else if (router.options.history.state.back) {
    referringPath.value = router.options.history.state.back;
  }
});

// ฟังก์ชันสำหรับ submit
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
      error.value = "Please Select Brand Thankyou";
      return;
    }

    const brandPk = product.value.brand?.id ?? product.value.brand?.brandId;
    if (!brandPk) {
      error.value = "Not Found BrandId";
      return;
    }

    const fd = new FormData();
    fd.append("brand.id", String(brandPk));
    fd.append("model", (product.value.model ?? "").trim());
    fd.append("description", (product.value.description ?? "").trim());
    if (Number.isInteger(product.value.price))
      fd.append("price", String(product.value.price));
    if (Number.isInteger(product.value.quantity))
      fd.append("quantity", String(product.value.quantity));
    if (
      product.value.screenSizeInch != null &&
      product.value.screenSizeInch !== ""
    )
      fd.append("screenSizeInch", String(product.value.screenSizeInch));
    if (Number.isInteger(product.value.ramGb))
      fd.append("ramGb", String(product.value.ramGb));
    if (Number.isInteger(product.value.storageGb))
      fd.append("storageGb", String(product.value.storageGb));
    if ((product.value.color ?? "").trim() !== "")
      fd.append("color", (product.value.color ?? "").trim());

    // แนบไฟล์
    (selectedFiles.value || []).forEach((file) => fd.append("files", file));

    // ยิง API
    const created = await postMultipart("/v2/sale-items", fd);

    // ดึงข้อมูลจาก response
    const newId =
      created?.id ??
      created?.productId ??
      created?.saleItem?.id ??
      created?.saleItem?.productId;

    const pics =
      created?.saleItemImages ??
      created?.pictures ??
      created?.saleItemPictures ??
      created?.images ??
      [];

    const sorted = Array.isArray(pics)
      ? pics
          .slice()
          .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))
      : [];

    const coverRel = sorted[0]?.url ?? null;
    const apiOrigin = new URL(
      import.meta.env.VITE_API_URL || window.location.origin
    ).origin;
    const cover = coverRel?.startsWith("http")
      ? coverRel
      : coverRel
      ? `${apiOrigin}${coverRel}`
      : null;

    // แจ้งหน้า list
    window.dispatchEvent(
      new CustomEvent("sale-item:created", {
        detail: {
          id: newId,
          image: cover,
          saleItemImages: sorted.map((pic) => ({
            ...pic,
            url: pic.url?.startsWith("http")
              ? pic.url
              : pic.url
              ? `${apiOrigin}${pic.url}`
              : null,
          })),
          brandName: product.value.brand?.name ?? "",
          ramGb: product.value.ramGb ?? null,
          storageGb: product.value.storageGb ?? null,
          screenSizeInch: product.value.screenSizeInch ?? null,
          price: product.value.price ?? null,
          model: (product.value.model ?? "").trim(),
          description: (product.value.description ?? "").trim(),
          color: (product.value.color ?? "").trim(),
          quantity: product.value.quantity ?? null,
        },
      })
    );

    successMessage.value = "The sale item has been successfully added.";
    router.push({ path: "/sale-items", query: { success: "true" } });
  } catch (err) {
    console.error("=== DEBUG: API Error ===");
    console.error("Error:", err);
    console.error("Response:", err?.response);
    console.error("Response data:", err?.response?.data);

    error.value = err?.response?.data?.message || err.message || "Save failed";
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div
    class="bg-gray-100 flex items-center justify-center p-4 sm:p-6 lg:p-8 text-black"
  >
    <div class="w-full bg-white rounded-lg shadow-lg p-6 sm:p-8 lg:p-10">
      <nav class="text-gray-600 text-sm sm:text-base mb-6 flex space-x-2 pb-4">
        <router-link
          to="/sale-items"
          class="hover:text-blue-600 font-medium transition-colors itbms-home-button"
        >
          Home
        </router-link>
        <span class="text-gray-400"> > </span>
        <span class="text-gray-500"> New Sale Item</span>
      </nav>
      <div class="itbms-row flex flex-col lg:flex-row gap-8">
        <!-- Image Section -->
        <div class="flex-shrink-0">
          <!-- รูปหลัก -->
          <div
            class="w-full max-w-[600px] aspect-square overflow-hidden rounded-lg shadow-sm mx-auto"
          >
            <img
              :src="
                product.image ||
                previewUrls[0] ||
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png'
              "
              alt="Product Image"
              class="w-130 h-130 object-cover"
            />
          </div>

          <!-- รูปย่อย -->
          <div class="flex justify-center gap-3 pt-4 pb-8 flex-wrap">
            <div
              v-for="(thumb, index) in previewUrls.length
                ? previewUrls
                : [
                    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png',
                  ]"
              :key="index"
              class="w-16 h-16 rounded-md overflow-hidden border transition-colors duration-200"
              :class="
                thumb === product.image ? 'border-blue-500' : 'border-gray-200'
              "
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
          <div
            v-for="(n, idx) in selectedFileNames"
            :key="n + idx"
            class="flex items-center gap-2 bg-gray-100"
          >
            <span class="text-sm bg-gray-100 px-2 py-1 rounded w-fit break-all">
              {{ n }}
            </span>

            <button
              type="button"
              class="text-gray-500 hover:text-red-600 font-bold leading-none"
              aria-label="Remove file"
              @click="removeFile(idx)"
            >
              ×
            </button>
          </div>
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
              <option value="">Select brand</option>
              <option
                v-for="(brand, index) in brands"
                :key="index"
                :value="brand"
              >
                {{ brand.name }}
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
            />
            <p class="text-sm text-gray-500 text-right mt-1">
              {{ modelNonSpaceLen }}/60
            </p>
          </label>

          <!-- Price -->
          <label class="block">
            <span class="text-gray-700 font-medium">Price (Baht)</span>
            <input
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
            ></textarea>
          </label>

          <!-- RAM -->
          <label class="block">
            <span class="text-gray-700 font-medium">RAM (GB)</span>
            <input
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
              v-model.number="product.screenSizeInch"
              step="0.1"
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
          <div>
            <p
              v-if="uploadError"
              class="mb-4 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message"
            >
              {{ uploadError }}
            </p>
          </div>
          <div
            v-if="validationMessage"
            class="mb-4 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message"
          >
            {{ validationMessage }}
          </div>

          <div
            v-if="successMessage"
            class="text-green-600 bg-green-100 p-3 rounded-md itbms-message"
          >
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

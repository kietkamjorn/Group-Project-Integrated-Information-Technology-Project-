<script setup>
import { ref, onMounted, computed, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData, updateData } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();
const productId = route.params.id;

const product = ref({});
const originalProduct = ref({});
const isLoading = ref(true);
const error = ref(null);
const fieldRefs = [];
const brands = ref([]);
const successMessage = ref("");

/* ====== API base & helper ====== */
const API_URL = import.meta.env.VITE_API_URL; // eg. http://localhost:8080/itb-mshop
const parsed = new URL(API_URL);
const API_ORIGIN = parsed.origin; // http://localhost:8080
const API_CTX = parsed.pathname.replace(/\/$/, ""); // /itb-mshop
const API = `${API_ORIGIN}${API_CTX}`;

// ทำ URL ให้เป็น absolute ถูกต้องทุกเคส
const toAbs = (u) => {
  if (!u) return null;
  if (u.startsWith("http")) return u;
  if (u.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${u}`; // กันเคสที่ u มี ctx มาแล้ว
  if (u.startsWith("/media/")) return `${API_ORIGIN}${API_CTX}${u}`; // เติม ctx ให้ /media/**
  return `${API}/${u.replace(/^\/+/, "")}`; // อื่น ๆ ให้ยึด API เป็นฐาน
};

/* ====== รูปภาพ: ลบแล้วหายทันที, อัปโหลดแทรกตำแหน่ง, เซฟพร้อมข้อมูล ====== */
const MAX_PICS = 4;

// slots: [{ id?, fileName, url, file?, isNew }]
const slots = ref([]);
const initialOrder = ref([]); // ไว้เทียบว่ามีการสลับลำดับมั้ย (เฉพาะรูปเดิม)
const deletedIds = ref([]); // เก็บ id ของรูปเดิมที่ถูกลบ
const insertAtIndex = ref(null); // ตำแหน่งแทรกไฟล์ใหม่ครั้งถัดไป (หลังจากกดลบ)
const fileInput = ref(null);

const picsToSlots = (pics = []) => {
  const sorted = pics
    .slice()
    .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
  slots.value = sorted.map((p) => ({
    id: p.id,
    fileName: p.fileName,
    url: toAbs(p.url),
    isNew: false,
    file: null,
  }));
  initialOrder.value = sorted.map((p) => p.id).filter(Boolean);
  deletedIds.value = [];
};

// แทนที่ loadPictures() เดิม ด้วยเวอร์ชัน fallback
const loadPictures = async () => {
  // ใช้รูปที่แนบมากับ /v2/sale-items/{id} ก่อน
  const embedded = Array.isArray(product.value?.saleItemImages)
    ? product.value.saleItemImages
    : null;

  if (embedded && embedded.length) {
    picsToSlots(embedded);
    return;
  }

  // ถ้าจำเป็นจริงๆ ค่อยลองยิง /pictures (และต้องไม่ทำให้หน้าแครชถ้า 500)
  try {
    const pics = await readData(`/v2/sale-items/${productId}/pictures`);
    picsToSlots(Array.isArray(pics) ? pics : []);
  } catch (e) {
    console.warn("โหลด /pictures ไม่สำเร็จ, ใช้ค่าว่างแทน", e);
    picsToSlots([]);
  }
};

const firstVisibleUrl = () => slots.value[0]?.url ?? null;

const moveUp = (i) => {
  if (i <= 0) return;
  const a = slots.value[i - 1];
  slots.value[i - 1] = slots.value[i];
  slots.value[i] = a;
};
const moveDown = (i) => {
  if (i >= slots.value.length - 1) return;
  const a = slots.value[i + 1];
  slots.value[i + 1] = slots.value[i];
  slots.value[i] = a;
};

// ลบทันที: ตัดออกจาก slots, ถ้าเป็นรูปเดิมให้จำ id ไว้ลบตอน save
const removeAt = (i) => {
  const s = slots.value[i];
  if (!s) return;
  if (!s.isNew && s.id) deletedIds.value.push(s.id);
  slots.value.splice(i, 1);
  insertAtIndex.value = i; // จำตำแหน่งไว้ให้ไฟล์ใหม่มาแทนช่องนี้
};

const openGlobalPicker = () => fileInput.value?.click();

// อัปโหลด: แทรกตั้งแต่ insertAtIndex ถ้ามี ไม่งั้นต่อท้าย, จำกัด MAX_PICS
const handleGlobalFilesSelected = (e) => {
  const files = Array.from(e.target.files || []);
  e.target.value = "";
  if (!files.length) return;

  let idx = insertAtIndex.value ?? slots.value.length;
  for (const f of files) {
    if (slots.value.length >= MAX_PICS) break;
    const blobUrl = URL.createObjectURL(f);
    const entry = {
      id: undefined,
      fileName: f.name,
      url: blobUrl,
      isNew: true,
      file: f,
    };
    slots.value.splice(idx, 0, entry);
    idx++;
  }
  insertAtIndex.value = null; // ใช้เสร็จแล้วล้าง
};

const patchOrders = async (ids) => {
  if (!ids || !ids.length) return;

  const payload = ids.map((pid, i) => ({
    pictureId: pid,
    displayOrder: i + 1,
  }));

  const res = await fetch(`${API}/v2/sale-items/${productId}/pictures/orders`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    credentials: "include",
  });

  if (!res.ok) {
    let detail = "";
    try {
      // ถ้าเป็น Response จริง ให้อ่าน body แบบปลอดภัย
      if (
        res &&
        typeof res.clone === "function" &&
        typeof res.text === "function"
      ) {
        detail = await res.clone().text();
      } else {
        // กันเคสโดน lib อื่นดัก fetch แล้วคืน object ธรรมดา
        detail = typeof res === "object" ? JSON.stringify(res) : String(res);
      }
    } catch (e) {
      // เงียบไว้
    }
    console.error("PATCH /orders failed", payload, detail);
    throw new Error("Update picture orders failed");
  }
};

// มีการแก้รูปหรือไม่: ลบ / เพิ่มใหม่ / หรือสลับลำดับของรูปเดิม
const picturesDirty = computed(() => {
  if (deletedIds.value.length) return true;
  if (slots.value.some((s) => s.isNew)) return true;

  // เช็ค order ของ "รูปเดิม" เท่านั้น
  const currentExisting = slots.value
    .filter((s) => !s.isNew && s.id)
    .map((s) => s.id);
  return JSON.stringify(currentExisting) !== JSON.stringify(initialOrder.value);
});

/* เซฟทั้งหมด: รูป (ลบ/อัปโหลด/จัดลำดับ) + ข้อมูลสินค้า */
const isSavingAll = ref(false);

const saveAll = async () => {
  try {
    isSavingAll.value = true;

    // 1) payload สินค้า
    const toNumOrNull = (v) =>
      v === "" || v === null || v === undefined ? null : Number(v);

    const saleItemPayload = {
      id: product.value.id,
      model: (product.value.model ?? "").trim(),
      description: (product.value.description ?? "").trim(),
      price: Number(product.value.price ?? 0),
      quantity: Number(product.value.quantity ?? 0),
      storageGb: Number(product.value.storageGb),
      color: (product.value.color ?? "").trim(),
      ramGb: toNumOrNull(product.value.ramGb),
      screenSizeInch: toNumOrNull(product.value.screenSizeInch),
      brand: { id: Number(product.value.brandId) },
    };

    // 2) เก็บไฟล์ใหม่/ที่จะลบ
    const newFiles = slots.value
      .filter((s) => s.isNew && s.file)
      .map((s) => s.file);
    const toDelete = [...deletedIds.value];

    // 3) PUT รวม (multipart)
    const fd = new FormData();
    fd.append(
      "saleItem",
      new File([JSON.stringify(saleItemPayload)], "saleItem.json", {
        type: "application/json",
      })
    );
    newFiles.forEach((f) => fd.append("files", f));
    toDelete.forEach((id) => fd.append("deletePictureIds", String(id)));

    const putRes = await fetch(`${API}/v2/sale-items/${productId}`, {
      method: "PUT",
      body: fd,
      credentials: "include",
    });
    if (!putRes.ok) {
      const errText = await putRes.text().catch(() => "");
      throw new Error(`Update sale item failed (${putRes.status}) ${errText}`);
    }

    // **ใช้รูปจากผลลัพธ์ PUT แทนการยิง /pictures**
    const updated = await putRes.json().catch(() => null);
    const list = Array.isArray(updated?.saleItemImages)
      ? updated.saleItemImages
      : Array.isArray(product.value?.saleItemImages)
      ? product.value.saleItemImages
      : [];

    // 4) คำนวนลำดับตาม slots ปัจจุบัน แล้ว PATCH /orders
    const tail = (name) =>
      (name || "").includes("_") ? name.split("_").slice(1).join("_") : name;

    const byTail = new Map();
    const allIds = [];
    const unused = new Set();
    for (const p of list) {
      const t = tail(p.fileName);
      if (!byTail.has(t)) byTail.set(t, []);
      byTail.get(t).push(p.id);
      allIds.push(p.id);
      unused.add(p.id);
    }

    const orderedIds = [];
    for (const s of slots.value) {
      if (s.id && unused.has(s.id)) {
        orderedIds.push(s.id);
        unused.delete(s.id);
      } else if (s.isNew && s.fileName) {
        const t = tail(s.fileName);
        const q = byTail.get(t);
        while (q && q.length) {
          const cand = q.shift();
          if (unused.has(cand)) {
            orderedIds.push(cand);
            unused.delete(cand);
            break;
          }
        }
      }
    }
    if (orderedIds.length !== allIds.length) {
      for (const id of allIds)
        if (!orderedIds.includes(id)) orderedIds.push(id);
    }

    try {
      if (orderedIds.length) await patchOrders(orderedIds);
    } catch (e) {
      console.warn(
        "PATCH /orders ล้มเหลว แต่จะไปต่อ (ข้อมูลอื่นบันทึกแล้ว)",
        e
      );
    }

    // 5) อัปเดต UI จาก list ที่ได้ (ไม่เรียก /pictures)
    const sorted = list
      .slice()
      .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
    slots.value = sorted.map((p) => ({
      id: p.id,
      fileName: p.fileName,
      url: toAbs(p.url),
      isNew: false,
      file: null,
    }));
    initialOrder.value = sorted.map((p) => p.id);
    deletedIds.value = [];
    product.value.saleItemImages = list;

    successMessage.value = "Saved successfully.";
    setTimeout(() => router.push(`/sale-items/${productId}`), 1200);
  } catch (err) {
    console.error(err);
    error.value = err.message || "Save failed";
    setTimeout(() => (error.value = ""), 3000);
  } finally {
    isSavingAll.value = false;
  }
};

/* ====== ฟอร์มสินค้า (เหมือนเดิม) ====== */
const hasChangedProduct = computed(
  () => JSON.stringify(product.value) !== JSON.stringify(originalProduct.value)
);
const hasChangedAll = computed(
  () =>
    !!validationMessage.value === false &&
    (hasChangedProduct.value || picturesDirty.value)
);

const focusNext = (index) => {
  const nextField = fieldRefs[index + 1];
  if (nextField?.focus) nextTick(() => nextField.focus());
};

function decimalPlaces(n) {
  const s = String(n ?? "");
  const i = s.indexOf(".");
  return i >= 0 ? s.length - i - 1 : 0;
}

const validationMessage = computed(() => {
  const m = (product.value.model ?? "").trim();
  const d = (product.value.description ?? "").trim();
  const c = (product.value.color ?? "").trim();

  const hasBrand =
    (product.value.brand && typeof product.value.brand === "object" && product.value.brand.id != null) ||
    (product.value.brandId !== null && product.value.brandId !== undefined && product.value.brandId !== "");
  if (!hasBrand) return "Brand must be selected.";

  if (m.length < 1 || m.length > 60) return "Model must be 1-60 characters long.";

  const p = product.value.price;
  if (!Number.isInteger(p) || p < 0) return "Price must be non-negative integer.";

  if (d.length < 1 || d.length > 16384) return "Description must be 1-16,384 characters long.";

  const q = product.value.quantity;
  if (!Number.isInteger(q) || q < 0) return "Quantity must be non-negative integer.";

  const s = product.value.screenSizeInch;
  if (s !== null && s !== undefined && s !== "") {
    const num = Number(s);
    const decimalPlaces = (n) => {
      const str = String(n ?? "");
      const i = str.indexOf(".");
      return i >= 0 ? str.length - i - 1 : 0;
    };
    if (!(num > 0) || decimalPlaces(num) > 2)
      return "Screen size must be positive number with at most 2 decimal points or not specified.";
  }

  const r = product.value.ramGb;
  if (r !== null && r !== undefined && r !== "") {
    if (!Number.isInteger(r) || r <= 0)
      return "RAM size must be positive integer or not specified.";
  }

  // ✅ ทำให้ color เป็น required (1–40)
  if (c.length < 1 || c.length > 40)
    return "Color is required (1–40 characters).";

  // ที่เหลือตามเดิมของคุณ…
  const st = product.value.storageGb;
  if (!Number.isInteger(st) || st <= 0)
    return "Storage is required and must be a positive integer.";

  return null;
});

const loadProduct = async (productId) => {
  isLoading.value = true;
  try {
    const numericId = parseInt(productId, 10);
    const item = await readData(`/v2/sale-items/${numericId}`);

    if (item.brand && typeof item.brand === "object")
      item.brandId = item.brand.id;

    const matchedBrand = brands.value.find((b) => b.name === item.brandName);
    if (matchedBrand) item.brandId = matchedBrand.id;

    product.value = JSON.parse(JSON.stringify(item));
    originalProduct.value = JSON.parse(JSON.stringify(product.value));
  } catch (err) {
    error.value = "ไม่สามารถโหลดข้อมูลสินค้าได้";
    console.error(err);
  } finally {
    isLoading.value = false;
  }
};

const loadBrands = async () => {
  try {
    const data = await readData("/v2/brands");
    brands.value = data;
    if (!product.value.brandId && brands.value.length > 0) {
      product.value.brandId = brands.value[0].id;
    }
  } catch (err) {
    console.error("โหลดแบรนด์ไม่สำเร็จ", err);
  }
};

onMounted(async () => {
  await loadBrands();
  await loadProduct(productId); // product.value จะมี saleItemImages มาด้วย
  await loadPictures(); // ถ้า /pictures 500 → ใช้ saleItemImages
});
</script>

<template>
  <div
    class="bg-gray-100 flex items-center justify-center p-4 sm:p-6 lg:p-8 text-black"
  >
    <div class="w-full bg-white rounded-lg shadow-lg p-6 sm:p-8 lg:p-10">
      <nav class="text-gray-500 text-lg mb-6 pb-4">
        <router-link
          to="/sale-items"
          class="hover:underline font-bold itbms-back-button"
          >Home</router-link
        >
        <span class="mx-2"> > </span>
        <router-link
          to="/sale-items"
          class="hover:underline font-bold itbms-home-button"
        >
          {{ product?.model ?? "..." }} {{ product?.color ?? "" }}
        </router-link>
      </nav>

      <div v-if="isLoading" class="text-center text-gray-500 text-xl">
        Loading...
      </div>

      <div
        v-else-if="error"
        class="flex flex-col justify-center items-center text-center text-gray-500 text-xl"
      >
        <div class="itbms-message bg-white p-6 rounded-lg shadow-lg">
          <div class="mb-6">{{ error }}</div>
          <router-link
            to="/sale-items"
            class="mt-6 inline-block bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition"
          >
            Home
          </router-link>
        </div>
      </div>

      <div v-else class="itbms-row flex gap-8 flex-col lg:flex-row text-black">
        <!-- รูปภาพ -->
        <div class="lg:w-1/2">
          <div
            class="w-full max-w-[600px] aspect-square overflow-hidden rounded-lg shadow-sm mx-auto border"
          >
            <img
              :src="
                firstVisibleUrl() ||
                'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png'
              "
              alt="รูปภาพสินค้า"
              class="w-full h-full object-contain"
            />
          </div>

          <div class="flex justify-center gap-3 pt-4 pb-4 flex-wrap">
            <div
              v-for="(s, idx) in slots"
              :key="s.id || s.fileName || idx"
              class="w-16 h-16 rounded-md overflow-hidden border bg-white"
            >
              <img
                :src="
                  s.url ||
                  'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png'
                "
                alt="thumb"
                class="w-full h-full object-cover"
              />
            </div>
          </div>

          <!-- ปุ่ม Upload แบบเดียวกับหน้า Add -->
          <div class="flex gap-3 items-center mb-4">
            <button
              type="button"
              @click="openGlobalPicker"
              class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition itbms-upload-button"
            >
              Upload Pictures
            </button>
          </div>
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            class="hidden"
            @change="handleGlobalFilesSelected"
          />

          <!-- รายการไฟล์ + ปุ่ม ↑ ↓ ✕ -->
          <div class="bg-gray-100 rounded-md p-3">
            <div
              v-for="(s, idx) in slots"
              :key="s.id || s.fileName || idx"
              class="flex items-center justify-between py-2 border-b last:border-b-0"
            >
              <div class="truncate mr-2">
                <span :class="{ 'text-blue-600': s.isNew }">{{
                  s.fileName || `picture ${idx + 1}`
                }}</span>
                <span v-if="s.isNew" class="ml-2 text-xs text-blue-500"
                  >(new)</span
                >
              </div>
              <div class="flex gap-2">
                <button
                  class="px-2 py-1 rounded border"
                  @click="moveUp(idx)"
                  title="Move up"
                >
                  ↑
                </button>
                <button
                  class="px-2 py-1 rounded border"
                  @click="moveDown(idx)"
                  title="Move down"
                >
                  ↓
                </button>
                <button
                  class="px-2 py-1 rounded border text-red-600"
                  @click="removeAt(idx)"
                  title="Delete"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ฟอร์มข้อมูลสินค้า -->
        <div class="flex-1 space-y-6">
          <h1 class="text-2xl mb-4 pb-4">Edit Product</h1>

          <label class="block mb-2">
            Brand:
            <select
              v-model="product.brandId"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-brand"
              :ref="(el) => (fieldRefs[0] = el)"
              @keydown.enter.prevent="focusNext(0)"
              data-testid="itbms-brand"
            >
              <option disabled value="">Select brand</option>
              <option v-for="brand in brands" :key="brand.id" :value="brand.id">
                {{ brand.name }}
              </option>
            </select>
          </label>

          <label class="block mb-2">
            Model:
            <input
              v-model="product.model"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-model"
              :ref="(el) => (fieldRefs[1] = el)"
              @keydown.enter.prevent="focusNext(1)"
            />
          </label>

          <label class="block mb-2">
            Price (Baht):
            <input
              v-model.number="product.price"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-price"
              :ref="(el) => (fieldRefs[2] = el)"
              @keydown.enter.prevent="focusNext(2)"
              data-testid="itbms-price"
            />
          </label>

          <label class="block mb-2">
            Description:
            <textarea
              v-model="product.description"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-description"
              :ref="(el) => (fieldRefs[3] = el)"
              @keydown.enter.prevent="focusNext(3)"
              data-testid="itbms-description"
            ></textarea>
          </label>

          <label class="block mb-2">
            Ram (GB):
            <input
              v-model.number="product.ramGb"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-ramGb"
              :ref="(el) => (fieldRefs[4] = el)"
              @keydown.enter.prevent="focusNext(4)"
              data-testid="itbms-ramGb"
            />
          </label>

          <label class="block mb-2">
            Screen Size (Inches):
            <input
              v-model.number="product.screenSizeInch"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-screenSizeInch"
              :ref="(el) => (fieldRefs[5] = el)"
              @keydown.enter.prevent="focusNext(5)"
              data-testid="itbms-screenSizeInch"
            />
          </label>

          <label class="block mb-2">
            Storage (GB):
            <input
              v-model.number="product.storageGb"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-storageGb"
              :ref="(el) => (fieldRefs[6] = el)"
              @keydown.enter.prevent="focusNext(6)"
              data-testid="itbms-storageGb"
            />
          </label>

          <label class="block mb-2">
            Color:
            <input
              v-model="product.color"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-color"
              :ref="(el) => (fieldRefs[7] = el)"
              @keydown.enter.prevent="focusNext(7)"
              data-testid="itbms-color"
            />
          </label>

          <label class="block mb-4">
            Quantity:
            <input
              v-model.number="product.quantity"
              class="mt-1 block w-full border border-gray-300 rounded-md p-3 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all itbms-quantity"
              :ref="(el) => (fieldRefs[8] = el)"
              @keydown.enter.prevent
              data-testid="itbms-quantity"
            />
          </label>

          <div class="flex gap-4 pt-5">
            <button
              :disabled="!!validationMessage || !hasChangedAll || isSavingAll"
              @click="saveAll"
              class="bg-blue-400 text-white px-6 py-2 rounded hover:bg-blue-500 transition itbms-save-button disabled:opacity-60"
            >
              {{ isSavingAll ? "Saving..." : "Save" }}
            </button>

            <router-link
              :to="{ name: 'ProductDetails', params: { id: productId } }"
              class="bg-red-400 text-white px-6 py-2 rounded hover:bg-red-700 transition itbms-cancel-button"
            >
              Cancel
            </router-link>
          </div>

          <div v-if="validationMessage" class="itbms-message">
            {{ validationMessage }}
          </div>
          <div v-if="successMessage" class="text-green-600 mt-4 itbms-message">
            {{ successMessage }}
          </div>
          <div v-if="error" class="text-red-500 mt-4">{{ error }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>

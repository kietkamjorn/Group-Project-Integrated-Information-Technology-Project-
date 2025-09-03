<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from "vue";
import { readData } from "@/libs/fetchUtils";

/* ===== state หลัก ===== */
const error = ref(null);
const isLoading = ref(true);
const brands = ref([]);
const sortByBrandNameAsc = ref(false);
const customMin = ref("");
const customMax = ref("");

const draftPrices = ref([]);
const draftMin = ref("");
const draftMax = ref("");

/* ===== props / emits ===== */
const props = defineProps({
  pageSize: { type: Number, default: 10 },
  modelValue: { type: Array, default: () => [] },
  onBrandSelect: Function,
  prices: { type: Array, default: () => [] },
  storages: { type: Array, default: () => [] },
});

function toKey(v) {
  if (v && typeof v === "object") {
    const label = v.label ?? "";
    const m = (n) => (n === Infinity ? "∞" : n === -Infinity ? "-∞" : n ?? "");
    const min = m(v.min);
    const max = m(v.max);
    return `price:${label}|${min}|${max}`;
  }
  return String(v ?? "");
}

function isSame(a, b) {
  if (!Array.isArray(a) || !Array.isArray(b)) return false;
  if (a.length !== b.length) return false;
  const A = a.map(toKey).sort();
  const B = b.map(toKey).sort();
  for (let i = 0; i < A.length; i++) if (A[i] !== B[i]) return false;
  return true;
}
const emit = defineEmits([
  "update:pageSize",
  "update:brands",
  "update:sort-order",
  "update:modelValue",
  "update:selectedBrands",
  "update:sort-by-brand-name-asc",
  "update:currentPage",
  "change",
  "update:prices",
  "update:storages",
]);

/* ===== Brand filter ===== */
const selectedBrands = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

const sortedSelectedBrands = computed(() => {
  if (sortByBrandNameAsc.value) {
    return [...selectedBrands.value].sort((a, b) =>
      a.name.localeCompare(b.name, "en", { sensitivity: "base" })
    );
  }
  return [...selectedBrands.value].sort((a, b) => a.id - b.id);
});

const sortedBrands = computed(() => {
  return [...brands.value].sort((a, b) =>
    a.name.localeCompare(b.name, "en", { sensitivity: "base" })
  );
});

function toggleBrand(brand) {
  const exists = selectedBrands.value.includes(brand);
  const newSelection = exists
    ? selectedBrands.value.filter((b) => b !== brand)
    : [...selectedBrands.value, brand];

  selectedBrands.value = newSelection;
  emit("update:modelValue", newSelection);
  emit("update:brands", newSelection);
  emit("update:currentPage", 1);
  emit("change", newSelection);
}

function removeBrand(brandToRemove) {
  const newSelection = selectedBrands.value.filter((brand) => {
    if (brand.id && brandToRemove.id) return brand.id !== brandToRemove.id;
    return brand.name !== brandToRemove.name;
  });
  selectedBrands.value = newSelection;
  emit("update:modelValue", newSelection);
  emit("update:brands", newSelection);
  emit("update:currentPage", 1);
  emit("change", newSelection);
}

const priceOptions = [
  { label: "0 - 5,000", min: 0, max: 5000 },
  { label: "5,001-10,000", min: 5001, max: 10000 },
  { label: "10,001-20,000", min: 10001, max: 20000 },
  { label: "20,001-30,000", min: 20001, max: 30000 },
  { label: "30,001-40,000", min: 30001, max: 40000 },
  { label: "40,001-50,000", min: 40001, max: 50000 },
  { label: "50,001 +", min: 50001, max: Infinity },
];

function parseNumOrNull(s) {
  const t = String(s ?? "").trim();
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

function combineRanges(pArr, minStr, maxStr) {
  const min = parseNumOrNull(minStr);
  const max = parseNumOrNull(maxStr);
  const result = [...pArr];
  if (min !== null || max !== null) {
    result.push({ label: "__custom__", min: min ?? 0, max: max ?? Infinity });
  }
  return result;
}

function applyPrice() {
  // อัปเดตค่า custom ที่ใช้งานจริง (ไม่สร้างแท็ก)
  customMin.value = draftMin.value;
  customMax.value = draftMax.value;

  const combined = combineRanges(
    selectedPrices.value,
    customMin.value,
    customMax.value
  );
  emit("update:prices", combined);
  emit("update:currentPage", 1);
  isPriceOpen.value = false;
}

function clearPrice() {
  // ล้างทั้ง preset และ custom ของ "ส่วนราคา" นี้
  selectedPrices.value = [];
  customMin.value = "";
  customMax.value = "";
  draftMin.value = "";
  draftMax.value = "";

  emit("update:prices", []);
  emit("update:currentPage", 1);
  isPriceOpen.value = false;
}

const selectedPrices = ref([]); // เก็บเป็น object ของ option
function togglePrice(option) {
  const exists = selectedPrices.value.find((o) => o.label === option.label);
  selectedPrices.value = exists
    ? selectedPrices.value.filter((o) => o.label !== option.label)
    : [...selectedPrices.value, option];

  const combined = combineRanges(
    selectedPrices.value,
    customMin.value,
    customMax.value
  );
  emit("update:prices", combined);
  emit("update:currentPage", 1);
}

function removePrice(option) {
  selectedPrices.value = selectedPrices.value.filter(
    (o) => o.label !== option.label
  );
  const combined = combineRanges(
    selectedPrices.value,
    customMin.value,
    customMax.value
  );
  emit("update:prices", combined);
  emit("update:currentPage", 1);
}

/* ===== Storage Size filter ===== */
const storageOptions = ["32Gb", "64Gb", "128Gb", "256Gb", "512Gb", "1Tb+"];
const selectedStorages = ref([]); // เก็บเป็น string label
function toggleStorage(label) {
  selectedStorages.value = selectedStorages.value.includes(label)
    ? selectedStorages.value.filter((l) => l !== label)
    : [...selectedStorages.value, label];
  emit("update:storages", selectedStorages.value);
  emit("update:currentPage", 1);
}
function removeStorage(label) {
  selectedStorages.value = selectedStorages.value.filter((l) => l !== label);
  emit("update:storages", selectedStorages.value);
  emit("update:currentPage", 1);
}

/* ===== ปุ่ม / dropdown states ===== */
const isBrandOpen = ref(false);
const isPriceOpen = ref(false);
const isStorageOpen = ref(false);

function openBrand() {
  isBrandOpen.value = true;
  isPriceOpen.value = false;
  isStorageOpen.value = false;
}
function openPrice() {
  isPriceOpen.value = true;
  isBrandOpen.value = false;
  isStorageOpen.value = false;

  draftMin.value = customMin.value;
  draftMax.value = customMax.value;
}

function openStorage() {
  isStorageOpen.value = true;
  isBrandOpen.value = false;
  isPriceOpen.value = false;
}

/* ===== page size & sort ===== */
const pageSize = ref(props.pageSize);
const pageSizes = [5, 10, 20];
const sortOrder = ref("none");

function setSortOrder(order) {
  sortOrder.value = order;
  if (order === "asc" && selectedBrands.value.length > 0) {
    sortByBrandNameAsc.value = true;
  } else {
    sortByBrandNameAsc.value = false;
  }
  emit("update:sort-order", order); // << เปลี่ยน
  emit("update:sort-by-brand-name-asc", sortByBrandNameAsc.value); // << เปลี่ยน
}

watch(pageSize, (newVal) => emit("update:pageSize", newVal));

watch(
  () => props.prices,
  (v) => {
    const val = Array.isArray(v) ? v : [];
    if (!isSame(val, selectedPrices.value)) selectedPrices.value = [...val];
  },
  { immediate: true, deep: true }
);

watch(
  () => props.storages,
  (v) => {
    const val = Array.isArray(v) ? v : [];
    if (!isSame(val, selectedStorages.value)) selectedStorages.value = [...val];
  },
  { immediate: true, deep: true }
);

/* ===== clear ===== */
function clearFilter() {
  selectedBrands.value = [];
  selectedPrices.value = [];
  selectedStorages.value = [];
  sortByBrandNameAsc.value = false;
  isBrandOpen.value = false;
  isPriceOpen.value = false;
  isStorageOpen.value = false;
  emit("update:brands", []);
  emit("update:prices", []);
  emit("update:storages", []);
  emit("update:sort-by-brand-name-asc", false);
  emit("update:currentPage", 1);
}

/* ===== โหลดแบรนด์จาก backend ===== */
const dropdownRef = ref(null);
const priceRef = ref(null);
const storageRef = ref(null);

const loadProducts = async () => {
  try {
    const apiPath = "/v2/brands";
    const data = await readData(apiPath);
    if (!Array.isArray(data)) throw new Error("API did not return an array");
    brands.value = data; // ไม่เรียง เพื่อคง id
    error.value = null;
  } catch (err) {
    error.value = "ไม่สามารถโหลดข้อมูลแบรนด์ได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};

const handleClickOutside = (e) => {
  const withinBrand = dropdownRef.value && dropdownRef.value.contains(e.target);
  const withinPrice = priceRef.value && priceRef.value.contains(e.target);
  const withinStorage = storageRef.value && storageRef.value.contains(e.target);
  if (!withinBrand && !withinPrice && !withinStorage) {
    isBrandOpen.value = false;
    isPriceOpen.value = false;
    isStorageOpen.value = false;
  }
};

onMounted(() => {
  loadProducts();
  document.addEventListener("click", handleClickOutside);
});
onUnmounted(() => document.removeEventListener("click", handleClickOutside));
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-4">
    <!-- ===== Left side: Filters ===== -->
    <div class="flex items-center gap-2 text-black">
      <!-- Brand filter -->
      <div class="relative itbms-brand-filter" ref="dropdownRef">
        <button
          type="button"
          class="relative w-102 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openBrand"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600"
              >Brand</span
            >
            <template v-if="selectedBrands.length > 0">
              <span
                v-for="brand in sortedSelectedBrands"
                :key="brand.id || brand.name"
                class="itbms-brand-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ brand.name }}</span>
                <button
                  type="button"
                  class="itbms-brand-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop="removeBrand(brand)"
                >
                  ×
                </button>
              </span>
            </template>
            <span v-else class="text-gray-400 text-sm">Filter by brand(s)</span>
          </div>
        </button>

        <div
          v-if="isBrandOpen"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none text-sm"
        >
          <ul>
            <li
              v-for="brand in sortedBrands"
              :key="brand.id || brand.name"
              :class="[
                selectedBrands.includes(brand)
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-900',
                'relative cursor-pointer select-none py-2 pl-10 pr-4 itbms-filter-item',
              ]"
              :data-brand-name="brand.name.toLowerCase()"
              @click="toggleBrand(brand)"
            >
              <span
                :class="[
                  selectedBrands.includes(brand)
                    ? 'font-medium'
                    : 'font-normal',
                  'block truncate',
                ]"
              >
                {{ brand.name }}
              </span>
              <span
                v-if="selectedBrands.includes(brand)"
                :class="[
                  selectedBrands.includes(brand)
                    ? 'text-white'
                    : 'text-blue-600',
                  'absolute inset-y-0 left-0 flex items-center pl-3',
                ]"
              >
                ✓
              </span>
            </li>
            <li
              v-if="sortedBrands.length === 0"
              class="py-2 pl-10 pr-4 text-gray-500"
            >
              ไม่มีแบรนด์ให้เลือก
            </li>
          </ul>
        </div>
      </div>

      <!-- Price filter -->
      <div class="relative itbms-price-filter" ref="priceRef">
        <button
          type="button"
          class="relative w-64 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openPrice"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600"
              >Price</span
            >

            <template v-if="selectedPrices.length > 0">
              <span
                v-for="p in selectedPrices"
                :key="p.label"
                class="itbms-price-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ p.label }}</span>
                <button
                  type="button"
                  class="itbms-price-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop="removePrice(p)"
                >
                  ×
                </button>
              </span>
            </template>

            <span v-else class="text-gray-400 text-sm">Price Range</span>
          </div>
        </button>

        <div
          v-if="isPriceOpen"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none text-sm"
        >
          <ul>
            <li
              v-for="opt in priceOptions"
              :key="opt.label"
              class="relative cursor-pointer select-none py-2 pl-4 pr-4 hover:bg-gray-100"
              @click="togglePrice(opt)"
            >
              {{ opt.label }}
            </li>
          </ul>
          <div class="h-px bg-gray-200 my-2"></div>

          <div class="px-3 pb-3">
            <div class="text-xs text-gray-500 mb-1 pb-2">Custom range</div>
            <div class="flex items-center gap-2 pb-3">
              <input
                v-model="draftMin"
                type="number"
                min="0"
                placeholder="Min"
                class="w-20 px-2 py-1 border rounded"
              />
              <span>—</span>
              <input
                v-model="draftMax"
                type="number"
                min="0"
                placeholder="Max"
                class="w-20 px-2 py-1 border rounded"
              />
            </div>

            <div class="flex justify-start gap-2 mt-3">
              <button
                type="button"
                class="px-3 py-1 rounded border"
                @click="clearPrice"
              >
                Clear
              </button>
              <button
                type="button"
                class="px-3 py-1 rounded bg-blue-600 text-white"
                @click="applyPrice"
              >
                Apply
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Storage Size filter -->
      <div class="relative itbms-storage-size-filter" ref="storageRef">
        <button
          type="button"
          class="relative w-56 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openStorage"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600"
              >Storage Size</span
            >
            <template v-if="selectedStorages.length > 0">
              <span
                v-for="s in selectedStorages"
                :key="s"
                class="itbms-storage-size-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ s }}</span>
                <button
                  type="button"
                  class="itbms-storage-size-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop="removeStorage(s)"
                >
                  ×
                </button>
              </span>
            </template>
            <span v-else class="text-gray-400 text-sm">Storage Range</span>
          </div>
        </button>

        <div
          v-if="isStorageOpen"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none text-sm"
        >
          <ul>
            <li
              v-for="opt in storageOptions"
              :key="opt"
              class="relative cursor-pointer select-none py-2 pl-4 pr-4 hover:bg-gray-100"
              @click="toggleStorage(opt)"
            >
              {{ opt }}
            </li>
          </ul>
        </div>
      </div>

      <!-- Clear ทั้งหมด -->
      <div class="flex items-center gap-2 p-2 rounded-md">
        <button
          @click="clearFilter"
          class="itbms-brand-filter-clear flex bg-red-600 items-center gap-1 px-3 py-2 text-white rounded-md hover:bg-red-500 transition duration-200 focus:outline-none focus:ring-2 focus:ring-red-500"
          :disabled="
            selectedBrands.length === 0 &&
            selectedPrices.length === 0 &&
            selectedStorages.length === 0
          "
        >
          <span>Clear</span>
        </button>
      </div>
    </div>

    <!-- ===== Right side: Page size and Sort controls ===== -->
    <div class="flex items-center gap-4">
      <div class="flex items-center gap-2">
        <span class="text-gray-700 font-medium">Show</span>
        <select
          v-model="pageSize"
          class="itbms-page-size px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 text-black bg-white"
        >
          <option v-for="size in [5, 10, 20]" :key="size" :value="size">
            {{ size }}
          </option>
        </select>
        <span class="text-gray-700 font-medium">entries</span>
      </div>

      <div class="flex items-center gap-1">
        <button
          @click="setSortOrder('none')"
          :class="[
            'itbms-brand-none p-2 border rounded-md transition duration-200',
            sortOrder === 'none'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="List view"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            fill="currentColor"
            class="bi bi-list"
            viewBox="0 0 16 16"
          >
            <path
              fill-rule="evenodd"
              d="M2.5 12a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5"
            />
          </svg>
        </button>

        <button
          @click="setSortOrder('asc')"
          :class="[
            'itbms-brand-asc p-2 border rounded-md transition duration-200',
            sortOrder === 'asc'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="Sort ascending"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            fill="currentColor"
            class="bi bi-sort-up"
            viewBox="0 0 16 16"
          >
            <path
              d="M3.5 12.5a.5.5 0 0 1-1 0V3.707L1.354 4.854a.5.5 0 1 1-.708-.708l2-1.999.007-.007a.5.5 0 0 1 .7.006l2 2a.5.5 0 1 1-.707.708L3.5 3.707zm3.5-9a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5M7.5 6a.5.5 0 0 0 0 1h5a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h3a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h1a.5.5 0 0 0 0-1z"
            />
          </svg>
        </button>

        <button
          @click="setSortOrder('desc')"
          :class="[
            'itbms-brand-desc p-2 border rounded-md transition duration-200',
            sortOrder === 'desc'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="Sort descending"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            fill="currentColor"
            class="bi bi-sort-down"
            viewBox="0 0 16 16"
          >
            <path
              d="M3.5 2.5a.5.5 0 0 0-1 0v8.793l-1.146-1.147a.5.5 0 0 0-.708.708l2 1.999.007.007a.497.497 0 0 0 .7-.006l2-2a.5.5 0 0 0-.707-.708L3.5 11.293zm3.5 1a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5M7.5 6a.5.5 0 0 0 0 1h5a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h3a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h1a.5.5 0 0 0 0-1z"
            />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.itbms-brand-filter {
  position: relative;
}
.itbms-price-filter {
  position: relative;
}
.itbms-storage-size-filter {
  position: relative;
}
</style>

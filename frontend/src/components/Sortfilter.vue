<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from "vue";
import { withAuthFetch ,publicFetch } from "@/libs/fetchUtils";

/* ===== props / emits ===== */
const props = defineProps({
  pageSize: { type: Number, default: 10 },
  modelValue: { type: Array, default: () => [] }, 
  prices: { type: Array, default: () => [] },
  storages: { type: Array, default: () => [] },
});
const emit = defineEmits([
  "update:pageSize",
  "update:brands",
  "update:modelValue",
  "update:sort-order",
  "update:sort-by-brand-name-asc",
  "update:prices",
  "update:storages",
  "update:currentPage",
]);

/* ===== State ===== */
const brands = ref([]);
const sortByBrandNameAsc = ref(false);

const priceOptions = [
  { label: "0 - 5,000", min: 0, max: 5000 },
  { label: "5,001-10,000", min: 5001, max: 10000 },
  { label: "10,001-20,000", min: 10001, max: 20000 },
  { label: "20,001-30,000", min: 20001, max: 30000 },
  { label: "30,001-40,000", min: 30001, max: 40000 },
  { label: "40,001-50,000", min: 40001, max: 50000 },
  { label: "50,001 +", min: 50001, max: Infinity },
];
const selectedPrices = ref([]);
const customMin = ref("");
const customMax = ref("");
const draftMin = ref("");
const draftMax = ref("");

// Storage
const storageOptions = ["32Gb", "64Gb", "128Gb", "256Gb", "512Gb", "1Tb+"];
const selectedStorages = ref([]);

// Page size & Sort
const pageSize = ref(props.pageSize);
const sortOrder = ref("none");

// Dropdown toggles
const isBrandOpen = ref(false);
const isPriceOpen = ref(false);
const isStorageOpen = ref(false);
const dropdownRef = ref(null);
const priceRef = ref(null);
const storageRef = ref(null);

/* ===== Utils ===== */
const brandEq = (x, y) => {
  if (!x || !y) return false;
  if (x.id != null && y.id != null) return Number(x.id) === Number(y.id);
  const nx = (x.name || "").toLowerCase().trim();
  const ny = (y.name || "").toLowerCase().trim();
  return nx && ny && nx === ny;
};
const sameArray = (a, b) => JSON.stringify(a || []) === JSON.stringify(b || []);

const parseNumOrNull = (s) => {
  const t = String(s ?? "").trim();
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
};
const combineRanges = (pArr, minStr, maxStr) => {
  const min = parseNumOrNull(minStr);
  const max = parseNumOrNull(maxStr);
  const result = [...(pArr || [])];
  if (min !== null || max !== null) {
    result.push({ label: "__custom__", min: min ?? 0, max: max ?? Infinity });
  }
  return result;
};

/* ===== Brand filter ===== */
const selectedBrands = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

const sortedSelectedBrands = computed(() =>
  sortByBrandNameAsc.value
    ? [...selectedBrands.value].sort((a, b) =>
        (a.name || "").localeCompare(b.name || "", "en", { sensitivity: "base" })
      )
    : [...selectedBrands.value].sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
);

const sortedBrands = computed(() =>
  [...brands.value].sort((a, b) =>
    (a.name || "").localeCompare(b.name || "", "en", { sensitivity: "base" })
  )
);

function toggleBrand(brand) {
  const idx = selectedBrands.value.findIndex((b) => brandEq(b, brand));
  const newSelection =
    idx >= 0
      ? selectedBrands.value.filter((_, i) => i !== idx)
      : [...selectedBrands.value, brand];

  selectedBrands.value = newSelection;
  emit("update:brands", newSelection);
  emit("update:currentPage", 1);
}

function removeBrand(brandToRemove) {
  selectedBrands.value = selectedBrands.value.filter(
    (b) => !brandEq(b, brandToRemove)
  );
  emit("update:brands", selectedBrands.value);
  emit("update:currentPage", 1);
}

/* ===== Price filter ===== */
function togglePrice(option) {
  const exists = selectedPrices.value.find((o) => o.label === option.label);
  selectedPrices.value = exists
    ? selectedPrices.value.filter((o) => o.label !== option.label)
    : [...selectedPrices.value, option];

  emit(
    "update:prices",
    combineRanges(selectedPrices.value, customMin.value, customMax.value)
  );
  emit("update:currentPage", 1);
}

function removePrice(option) {
  selectedPrices.value = selectedPrices.value.filter(
    (o) => o.label !== option.label
  );
  emit(
    "update:prices",
    combineRanges(selectedPrices.value, customMin.value, customMax.value)
  );
  emit("update:currentPage", 1);
}

function applyPrice() {
  customMin.value = draftMin.value;
  customMax.value = draftMax.value;
  emit(
    "update:prices",
    combineRanges(selectedPrices.value, customMin.value, customMax.value)
  );
  emit("update:currentPage", 1);
  isPriceOpen.value = false;
}

function clearPrice() {
  selectedPrices.value = [];
  customMin.value = "";
  customMax.value = "";
  draftMin.value = "";
  draftMax.value = "";
  emit("update:prices", []);
  emit("update:currentPage", 1);
  isPriceOpen.value = false;
}

/* ===== Storage filter ===== */
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

/* ===== Page size & Sort ===== */
watch(pageSize, (n) => emit("update:pageSize", n));

function setSortOrder(order) {
  sortOrder.value = order;
  sortByBrandNameAsc.value = order === "asc" && selectedBrands.value.length > 0;
  emit("update:sort-order", order);
  emit("update:sort-by-brand-name-asc", sortByBrandNameAsc.value);
}

/* ===== Sync props -> local state ===== */
watch(
  () => props.prices,
  (v) => {
    const next = Array.isArray(v) ? v : [];
    if (!sameArray(next, selectedPrices.value))
      selectedPrices.value = [...next.filter(Boolean)];
  },
  { immediate: true, deep: true }
);
watch(
  () => props.storages,
  (v) => {
    const next = Array.isArray(v) ? v : [];
    if (!sameArray(next, selectedStorages.value))
      selectedStorages.value = [...next];
  },
  { immediate: true, deep: true }
);

/* ===== Dropdown helpers ===== */
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
function clearFilter() {
  selectedBrands.value = [];
  selectedPrices.value = [];
  selectedStorages.value = [];
  sortByBrandNameAsc.value = false;
  customMin.value = "";
  customMax.value = "";
  draftMin.value = "";
  draftMax.value = "";
  isBrandOpen.value = false;
  isPriceOpen.value = false;
  isStorageOpen.value = false;
  emit("update:brands", []);
  emit("update:prices", []);
  emit("update:storages", []);
  emit("update:sort-by-brand-name-asc", false);
  emit("update:currentPage", 1);
}

/* ===== Load brands ===== */
const loadBrands = async () => {
  try {
    const data = await publicFetch("/v2/brands");
    brands.value = Array.isArray(data) ? data : [];
  } catch (err) {
    console.error("Load brands failed:", err);
    brands.value = [];
  }
};

/* ===== Click outside to close ===== */
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
  loadBrands();
  document.addEventListener("click", handleClickOutside);
});
onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
});
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-4">
    <!-- Left: filters -->
    <div class="flex items-center gap-2 text-black">
      <!-- Brand -->
      <div class="relative itbms-brand-filter" ref="dropdownRef">
        <button
          type="button"
          class="relative w-102 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openBrand"
          aria-haspopup="listbox"
          :aria-expanded="isBrandOpen ? 'true' : 'false'"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600">Brand</span>
            <template v-if="selectedBrands.length">
              <span
                v-for="brand in sortedSelectedBrands"
                :key="brand.id || brand.name"
                class="itbms-brand-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ brand.name }}</span>
                <!-- FIX: ใช้ span role=button กัน nested button -->
                <span
                  role="button"
                  tabindex="0"
                  class="itbms-brand-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop.prevent="removeBrand(brand)"
                  @keydown.enter.stop.prevent="removeBrand(brand)"
                  aria-label="Remove brand"
                >
                  ×
                </span>
              </span>
            </template>
            <span v-else class="text-gray-400 text-sm">Filter by brand(s)</span>
          </div>
        </button>

        <div
          v-if="isBrandOpen"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none text-sm"
          role="listbox"
        >
          <ul>
            <li
              v-for="brand in sortedBrands"
              :key="brand.id || brand.name"
              :class="[
                selectedBrands.some((b) => brandEq(b, brand))
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-900',
                'relative cursor-pointer select-none py-2 pl-10 pr-4 itbms-filter-item',
              ]"
              :data-brand-name="brand.name.toLowerCase()"
              @click="toggleBrand(brand)"
            >
              <span
                :class="[
                  selectedBrands.some((b) => brandEq(b, brand))
                    ? 'font-medium'
                    : 'font-normal',
                  'block truncate',
                ]"
              >
                {{ brand.name }}
              </span>
              <span
                v-if="selectedBrands.some((b) => brandEq(b, brand))"
                class="absolute inset-y-0 left-0 flex items-center pl-3 text-white"
              >
                ✓
              </span>
            </li>
            <li v-if="!sortedBrands.length" class="py-2 pl-10 pr-4 text-gray-500">
              ไม่มีแบรนด์ให้เลือก
            </li>
          </ul>
        </div>
      </div>

      <!-- Price -->
      <div class="relative itbms-price-filter" ref="priceRef">
        <button
          type="button"
          class="relative w-64 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openPrice"
          aria-haspopup="dialog"
          :aria-expanded="isPriceOpen ? 'true' : 'false'"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600">Price</span>

            <template v-if="selectedPrices.length || customMin || customMax">
              <span
                v-for="p in selectedPrices"
                :key="p.label"
                class="itbms-price-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ p.label }}</span>
                <!-- FIX: span role=button -->
                <span
                  role="button"
                  tabindex="0"
                  class="itbms-price-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop.prevent="removePrice(p)"
                  @keydown.enter.stop.prevent="removePrice(p)"
                  aria-label="Remove price range"
                >
                  ×
                </span>
              </span>

              <span
                v-if="customMin || customMax"
                class="itbms-price-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">
                  {{ customMin || "0" }} — {{ customMax || "∞" }}
                </span>
                <!-- FIX: span role=button -->
                <span
                  role="button"
                  tabindex="0"
                  class="itbms-price-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop.prevent="clearPrice"
                  @keydown.enter.stop.prevent="clearPrice"
                  aria-label="Clear custom price"
                >
                  ×
                </span>
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
              <button type="button" class="px-3 py-1 rounded border" @click="clearPrice">
                Clear
              </button>
              <button type="button" class="px-3 py-1 rounded bg-blue-600 text-white" @click="applyPrice">
                Apply
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Storage -->
      <div class="relative itbms-storage-size-filter" ref="storageRef">
        <button
          type="button"
          class="relative w-56 cursor-pointer rounded-md border border-gray-300 bg-white py-1.5 px-3 text-left shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-h-[40px] flex items-center justify-between flex-wrap gap-1"
          @click.stop="openStorage"
          aria-haspopup="listbox"
          :aria-expanded="isStorageOpen ? 'true' : 'false'"
        >
          <div class="flex flex-wrap gap-1 items-center">
            <span class="text-xs font-semibold block w-full text-gray-600">Storage Size</span>
            <template v-if="selectedStorages.length">
              <span
                v-for="s in selectedStorages"
                :key="s"
                class="itbms-storage-size-item flex items-center gap-1 bg-gray-200 text-black px-2 py-0.5 rounded text-sm"
              >
                <span class="font-semibold text-black">{{ s }}</span>
                <!-- FIX: เรียก removeStorage(s) และใช้ span role=button -->
                <span
                  role="button"
                  tabindex="0"
                  class="itbms-storage-size-item-clear hover:text-red-600 focus:outline-none"
                  @click.stop.prevent="removeStorage(s)"
                  @keydown.enter.stop.prevent="removeStorage(s)"
                  aria-label="Remove storage size"
                >
                  ×
                </span>
              </span>
            </template>
            <span v-else class="text-gray-400 text-sm">Storage Range</span>
          </div>
        </button>

        <div
          v-if="isStorageOpen"
          class="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none text-sm"
          role="listbox"
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

      <!-- Clear all -->
      <div class="flex items-center gap-2 p-2 rounded-md">
        <button
          @click="clearFilter"
          class="itbms-brand-filter-clear flex bg-red-600 items-center gap-1 px-3 py-2 text-white rounded-md hover:bg-red-500 transition"
          :disabled="
            !selectedBrands.length &&
            !selectedPrices.length &&
            !selectedStorages.length &&
            !customMin &&
            !customMax
          "
        >
          <span>Clear</span>
        </button>
      </div>
    </div>

    <!-- Right: Page size & Sort -->
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
            'itbms-brand-none p-2 border rounded-md transition',
            sortOrder === 'none'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="List view"
          aria-pressed="true"
        >
          <!-- list icon -->
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-list" viewBox="0 0 16 16">
            <path
              fill-rule="evenodd"
              d="M2.5 12a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5m0-4a.5.5 0 0 1 .5-.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1-.5-.5"
            />
          </svg>
        </button>

        <button
          @click="setSortOrder('asc')"
          :class="[
            'itbms-brand-asc p-2 border rounded-md transition',
            sortOrder === 'asc'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="Sort ascending"
        >
          <!-- sort up -->
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-sort-up" viewBox="0 0 16 16">
            <path
              d="M3.5 12.5a.5.5 0 0 1-1 0V3.707L1.354 4.854a.5.5 0 1 1-.708-.708l2-1.999.007-.007a.5.5 0 0 1 .7.006l2 2a.5.5 0 1 1-.707.708L3.5 3.707zm3.5-9a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5M7.5 6a.5.5 0 0 0 0 1h5a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h3a.5.5 0 0 0 0-1zm0 3a.5.5 0 0 0 0 1h1a.5.5 0 0 0 0-1z"
            />
          </svg>
        </button>

        <button
          @click="setSortOrder('desc')"
          :class="[
            'itbms-brand-desc p-2 border rounded-md transition',
            sortOrder === 'desc'
              ? 'bg-blue-500 text-white border-blue-500'
              : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200',
          ]"
          title="Sort descending"
        >
          <!-- sort down -->
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-sort-down" viewBox="0 0 16 16">
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
.itbms-brand-filter,
.itbms-price-filter,
.itbms-storage-size-filter {
  position: relative;
}
</style>

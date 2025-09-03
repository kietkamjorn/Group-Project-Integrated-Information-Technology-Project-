<script setup>
import {
  ref,
  computed,
  onMounted,
  nextTick,
  watch,
  onBeforeUnmount,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData } from "../libs/fetchUtils";
import Sortfilter from "./Sortfilter.vue";
import Pagination from "./Pagination.vue";

const selectedBrands = ref([]);
const route = useRoute();
const router = useRouter();
const products = ref([]);
const error = ref(null);
const isLoading = ref(true);
const allProducts = ref([]);
const sortOrder = ref("none");
const appliedBrands = ref([]);
const sortByBrandNameAsc = ref(false);
const selectedPrices = ref([]);
const selectedStorages = ref([]);

// === NEW: keyword รับจาก Navbar ===
const searchKeyword = ref("");
const SS_KEY = "gallery:persist";
const API_URL = import.meta.env.VITE_API_URL; 
const parsed = new URL(API_URL);
const API_ORIGIN = parsed.origin; 
const API_CTX = parsed.pathname.replace(/\/$/, ""); 
const API = `${API_ORIGIN}${API_CTX}`; 

// แปลง URL จาก backend -> absolute ให้ถูกกับ context path
const toAbs = (u) => {
  if (!u) return null;
  if (u.startsWith("http")) return u;
  if (u.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${u}`;
  if (u.startsWith("/media/")) return `${API}${u}`;
  if (u.startsWith("media/")) return `${API}/${u}`;
  return `${API}/${String(u).replace(/^\/+/, "")}`;
};

const decorateItem = (item) => {
  const pics = (
    item.saleItemImages ||
    item.pictures ||
    item.saleItemPictures ||
    item.images ||
    []
  )
    .slice()
    .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));

  const coverRel = pics[0]?.url || null;

  return {
    ...item,
    image: coverRel ? toAbs(coverRel) : null,
    thumbs: pics.map((p) => toAbs(p.url)).filter(Boolean),
  };
};

function saveState() {
  const state = {
    pageSize: pageSize.value,
    currentPage: currentPage.value,
    sortOrder: sortOrder.value,
    sortByBrandNameAsc: !!sortByBrandNameAsc.value,
    brands: selectedBrands.value.map((b) => b?.name).filter(Boolean),
    prices: selectedPrices.value,
    storages: selectedStorages.value,
  };
  localStorage.setItem(SS_KEY, JSON.stringify(state));
}

function loadState() {
  try {
    const raw = localStorage.getItem(SS_KEY);
    if (!raw) return;
    const s = JSON.parse(raw);
    if (s.pageSize) pageSize.value = Number(s.pageSize) || 10;
    if (s.currentPage) currentPage.value = Number(s.currentPage) || 1;
    if (s.sortOrder) sortOrder.value = s.sortOrder;
    if (typeof s.sortByBrandNameAsc === "boolean") {
      sortByBrandNameAsc.value = s.sortByBrandNameAsc;
    }
    if (Array.isArray(s.brands) && s.brands.length) {
      selectedBrands.value = s.brands.map((n, i) => ({ id: i + 1, name: n }));
      appliedBrands.value = [...selectedBrands.value];
    }
    if (Array.isArray(s.prices)) selectedPrices.value = s.prices;
    if (Array.isArray(s.storages)) selectedStorages.value = s.storages;
  } catch (error) {
    console.error("Error loading state:", error);
  }
}

const currentPage = ref(1);
const pageSize = ref(10);

const deleteErrorMessage = ref(route.query.errorMessage || null);
const successMessage = ref(
  route.query.success ? "The sale item has been successfully added." : null
);

const handleCreated = (e) => {
  const d = e.detail || {};
  const newItem = decorateItem({
    ...d,
    id: d.id ?? d.productId,
    saleItemImages: d.saleItemImages || [],
    image: d.image || null,
  });

  allProducts.value = [newItem, ...allProducts.value];
  filterProducts();
};
onMounted(() => window.addEventListener("sale-item:created", handleCreated));
onBeforeUnmount(() =>
  window.removeEventListener("sale-item:created", handleCreated)
);

const isRestoring = ref(true);
onMounted(() => {
  loadState();

  (async () => {
    // เริ่มต้น: โหลดทั้งหมดก่อน
    await loadProducts();
    filterProducts();
    isRestoring.value = false;
  })();

  // สมัครฟัง event จาก Navbar
  window.addEventListener("itbms:search", onNavSearch);
  window.addEventListener("itbms:clear", onNavClear);

  const errorMessage = route.query.errorMessage;
  if (errorMessage) {
    deleteErrorMessage.value = errorMessage;
    nextTick(() => {
      setTimeout(() => {
        deleteErrorMessage.value = null;
        const query = { ...route.query };
        delete query.errorMessage;
        router.replace({ query });
      }, 5000);
    });
  }
  const success = route.query.success;
  if (success) {
    successMessage.value = "The sale item has been successfully added.";
    nextTick(() => {
      setTimeout(() => {
        successMessage.value = null;
        const query = { ...route.query };
        delete query.success;
        router.replace({ query });
      }, 5000);
    });
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("itbms:search", onNavSearch);
  window.removeEventListener("itbms:clear", onNavClear);
});

const loadProducts = async () => {
  isLoading.value = true;
  try {
    const response = await readData(
      "/v2/sale-items?page=0&size=1000&sortField=id&sortDirection=asc"
    );
    const list = Array.isArray(response)
      ? response
      : Array.isArray(response?.content)
      ? response.content
      : Array.isArray(response?.items)
      ? response.items
      : Array.isArray(response?.data)
      ? response.data
      : [];

    allProducts.value = list.map(decorateItem);
    products.value = allProducts.value.slice();
    error.value = null;
  } catch (e) {
    console.error(e);
    error.value = "ไม่สามารถโหลดข้อมูลสินค้าได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};

// === NEW: helpers สำหรับ query ของ BE ===
const toBrandNames = (arr = []) =>
  (arr || []).map((b) => (typeof b === "string" ? b : b?.name)).filter(Boolean);

const storageLabelToNumber = (v) => {
  if (typeof v === "number") return v;
  const s = String(v ?? "").trim();
  if (!s) return null;
  if (/tb\+?/i.test(s)) return 1024; // "1Tb+" -> 1024 GB
  const m = s.match(/\d+/);
  return m ? Number(m[0]) : null; // "256Gb" -> 256
};
const normalizeStorages = (arr = []) =>
  arr.map(storageLabelToNumber).filter((n) => Number.isFinite(n));

const normalizePrice = (ranges) => {
  if (!Array.isArray(ranges) || !ranges.length) return null;
  let min = Infinity,
    max = -Infinity,
    open = false;
  for (const r of ranges) {
    const rMin = Number(r?.min);
    const rMax = r?.max;
    if (Number.isFinite(rMin)) min = Math.min(min, rMin);
    if (rMax == null || rMax === Infinity) open = true;
    else if (Number.isFinite(Number(rMax))) max = Math.max(max, Number(rMax));
    else open = true;
  }
  return {
    min: Number.isFinite(min) ? min : null,
    max: open ? null : Number.isFinite(max) ? max : null,
  };
};

const buildFilterQuery = () => {
  const p = new URLSearchParams();

  toBrandNames(selectedBrands.value).forEach((b) => p.append("brands", b));

  const pr = normalizePrice(selectedPrices.value);
  if (pr?.min != null) p.append("priceMin", String(pr.min));
  if (pr?.max != null) p.append("priceMax", String(pr.max));

  normalizeStorages(selectedStorages.value).forEach((n) =>
    p.append("storageSizes", String(n))
  );

  const kw = searchKeyword.value.trim();
  if (kw) p.append("searchKeyWord", kw);

  p.append("page", "0");
  p.append("size", "1000");
  p.append("sortField", "id");
  p.append("sortDirection", "asc");
  return p.toString();
};

// === NEW: ดึงรายการจาก /filter (AND: keyword + filters)
const requestProducts = async () => {
  isLoading.value = true;
  try {
    const qs = buildFilterQuery();
    const res = await readData(`/v2/sale-items/filter${qs ? `?${qs}` : ""}`);
    const list = Array.isArray(res?.content)
      ? res.content
      : Array.isArray(res)
      ? res
      : [];
    allProducts.value = list.map(decorateItem);
    error.value = null;
  } catch (e) {
    console.error(e);
    error.value = "ไม่สามารถค้นหาได้ กรุณาลองใหม่";
    allProducts.value = [];
  } finally {
    isLoading.value = false;
    filterProducts();
  }
};

// === NEW: handlers event จาก Navbar ===
const onNavSearch = (e) => {
  searchKeyword.value = (e?.detail?.keyword || "").trim();
  currentPage.value = 1;
  requestProducts();
};
const onNavClear = () => {
  searchKeyword.value = "";
  currentPage.value = 1;
  requestProducts(); // เคลียร์เฉพาะ keyword — filters เดิมยังอยู่
};

const handlePageChange = async (page) => {
  const lastUI = totalPages.value;
  currentPage.value = page;

  const isFirst = page === 1;
  const isLast = lastUI > 0 && page === lastUI;
  if (isFirst || isLast) {
    if (searchKeyword.value) {
      await requestProducts();
    } else {
      await loadProducts();
      filterProducts();
    }
    const newLast = Math.max(
      1,
      Math.ceil(products.value.length / pageSize.value)
    );
    if (currentPage.value > newLast) currentPage.value = newLast;
  }
};

const handlePageSizeChange = (newPageSize) => {
  pageSize.value = newPageSize;
  currentPage.value = 1;
  filterProducts();
};

const handleSortOrderChange = (newOrder) => {
  sortOrder.value = newOrder;
  currentPage.value = 1;
  filterProducts();
};

// CHANGED: ถ้ามี keyword → ยิง BE ใหม่เพื่อ AND
const handleBrandFilter = (brands) => {
  const toNames = (arr) => (arr || []).map((b) => b?.name || "").sort();
  const changed =
    JSON.stringify(toNames(selectedBrands.value)) !==
    JSON.stringify(toNames(brands));

  selectedBrands.value = brands;
  appliedBrands.value = [...brands];

  if (!isRestoring.value && changed) currentPage.value = 1;

  if (searchKeyword.value) requestProducts();
  else filterProducts();
};

// CHANGED: ถ้ามี keyword → ยิง BE ใหม่เพื่อ AND
const handleSortByNameUpdate = (value) => {
  sortByBrandNameAsc.value = value;
  currentPage.value = 1;
  filterProducts();
};

// CHANGED: ถ้ามี keyword → ยิง BE ใหม่เพื่อ AND
const handlePricesUpdate = (ranges) => {
  selectedPrices.value = Array.isArray(ranges) ? ranges : [];
  currentPage.value = 1;
  if (searchKeyword.value) requestProducts();
  else filterProducts();
};

// CHANGED: ถ้ามี keyword → ยิง BE ใหม่เพื่อ AND
const handleStoragesUpdate = (labels) => {
  selectedStorages.value = Array.isArray(labels) ? labels : [];
  currentPage.value = 1;
  if (searchKeyword.value) requestProducts();
  else filterProducts();
};

const paginatedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return products.value.slice(start, end);
});

const totalPages = computed(() => {
  return Math.ceil(products.value.length / pageSize.value);
});

const clampCurrentPage = () => {
  const newTotal = Math.ceil(products.value.length / pageSize.value) || 1;
  if (currentPage.value > newTotal) currentPage.value = newTotal;
  if (currentPage.value < 1) currentPage.value = 1;
};

watch([products, pageSize], clampCurrentPage, { deep: true, immediate: true });

watch(allProducts, () => filterProducts(), { deep: true });

const filterProducts = () => {
  let filtered = allProducts.value.slice();

  // --- Brand ---
  const brandSet = new Set(
    (selectedBrands.value || []).map((b) => (b.name || "").toLowerCase())
  );
  if (brandSet.size > 0) {
    filtered = filtered.filter((p) =>
      brandSet.has((p.brandName || "").toLowerCase())
    );
  }

  // ----- กรองตามราคา -----
  if (selectedPrices.value.length > 0) {
    filtered = filtered.filter((item) => {
      const price = Number(item.price);
      if (!Number.isFinite(price)) return false;
      return selectedPrices.value.some(
        ({ min, max }) =>
          price >= min && (max === Infinity ? true : price <= max)
      );
    });
  }

  if (selectedPrices.value.length > 0) {
    filtered = filtered.filter((item) => {
      const price = Number(item.price);
      if (!Number.isFinite(price)) return false;
      return selectedPrices.value.some(({ min, max }) => {
        const minN = Number.isFinite(Number(min)) ? Number(min) : 0;
        const maxN = Number.isFinite(Number(max)) ? Number(max) : Infinity;
        return price >= minN && price <= maxN;
      });
    });
  }

  // ----- กรองตาม Storage -----
  if (selectedStorages.value.length > 0) {
    filtered = filtered.filter((item) => {
      const s = Number(item.storageGb);
      if (!s) return false;
      return selectedStorages.value.some((label) => {
        if (label === "1Tb+") return s >= 1024;
        const n = parseInt(label, 10); // "256Gb" -> 256
        return s === n;
      });
    });
  }

  // --- Sort ตามแบรนด์ (คง logic เดิม) ---
  if (selectedBrands.value.length === 0) {
    if (sortOrder.value === "asc") {
      filtered.sort((a, b) => a.brandName?.localeCompare(b.brandName));
    } else if (sortOrder.value === "desc") {
      filtered.sort((a, b) => b.brandName?.localeCompare(a.brandName));
    }
  } else {
    if (sortOrder.value === "asc") {
      filtered.sort((a, b) => {
        const c = a.brandName.localeCompare(b.brandName);
        return c !== 0 ? c : a.id - b.id;
      });
    } else if (sortOrder.value === "desc") {
      filtered.sort((a, b) => {
        const c = b.brandName.localeCompare(a.brandName);
        return c !== 0 ? c : a.id - b.id;
      });
    } else if (sortByBrandNameAsc.value) {
      filtered.sort((a, b) => {
        const c = a.brandName.localeCompare(b.brandName);
        return c !== 0 ? c : a.id - b.id;
      });
    } else {
      // เรียงตามลำดับแบรนด์ที่ผู้ใช้เลือก (เหมือนเดิม)
      const brandOrder = selectedBrands.value
        .slice()
        .sort((a, b) => a.id - b.id)
        .reduce(
          (acc, b, i) => ((acc[(b.name || "").toLowerCase()] = i), acc),
          {}
        );
      filtered.sort((a, b) => {
        const A = brandOrder[(a.brandName || "").toLowerCase()] ?? 999;
        const B = brandOrder[(b.brandName || "").toLowerCase()] ?? 999;
        return A !== B ? A - B : a.id - b.id;
      });
    }
  }

  products.value = filtered;
};

watch(
  [
    pageSize,
    currentPage,
    sortOrder,
    sortByBrandNameAsc,
    selectedBrands,
    selectedPrices,
    selectedStorages,
  ],
  () => saveState(),
  { deep: true }
);

// ดึงรูปปกของแต่ละสินค้า: ถ้าลิงก์เสียใช้รูปสำรอง
const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png";

const onImgError = (e) => {
  e.target.onerror = null;
  e.target.src = FALLBACK_IMG;
};
</script>

<template>
  <div class="w-full px-4 py-5 not-prose">
    <div
      v-if="successMessage"
      class="mb-8 bg-green-100 text-green-700 px-4 py-3 rounded shadow itbms-message"
    >
      {{ successMessage }}
    </div>

    <div
      v-if="deleteErrorMessage"
      class="mb-8 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message"
    >
      {{ deleteErrorMessage }}
    </div>

    <div v-if="isLoading" class="text-center">Loading...</div>
    <div v-else-if="allProducts.length === 0" class="text-center text-black">
      No Sale Item
    </div>

    <div v-else class="flex flex-col items-center">
      <div class="w-full max-w-6xl mb-4 pb-4 mt-6">
        <Sortfilter
          v-model="selectedBrands"
          :page-size="pageSize"
          :prices="selectedPrices"
          :storages="selectedStorages"
          @update:page-size="handlePageSizeChange"
          @update:brands="handleBrandFilter"
          @update:sort-order="handleSortOrderChange"
          @update:sort-by-brand-name-asc="handleSortByNameUpdate"
          @update:prices="handlePricesUpdate"
          @update:storages="handleStoragesUpdate"
        />
      </div>

      <div class="w-full max-w-6xl flex justify-start mb-4 pb-4 mt-6">
        <router-link
          to="/sale-items/add"
          class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition itbms-sale-item-add"
        >
          Add Sale Item
        </router-link>
      </div>

      <div
        class="grid grid-cols-[repeat(auto-fit,minmax(13rem,1fr))] gap-6 justify-items-center w-full max-w-6xl"
      >
        <router-link
          v-for="product in paginatedProducts.filter((p) => p.id != null)"
          :key="product.id"
          :to="{ name: 'ProductDetails', params: { id: product.id } }"
          class="itbms-row bg-white rounded-xl shadow-md p-4 flex flex-col items-center w-52 cursor-pointer hover:shadow-2xl transition-shadow duration-300"
        >
          <img
            :src="product.image || FALLBACK_IMG"
            alt="รูปภาพสินค้า"
            class="w-40 h-48 object-contain mb-3"
            @error="onImgError"
          />
          <div class="text-left w-full">
            <h2
              class="text-base font-bold text-black mb-1 itbms-brand"
              :data-brand="(product.brandName || '').toLowerCase()"
            >
              {{ product.brandName || "-" }}
            </h2>

            <h3 class="text-base font-semibold text-gray-800 mb-1 itbms-model">
              {{ product.model || "-" }}
            </h3>
            <p class="text-sm text-gray-600 mb-1 flex items-center itbms-ramGb">
              <span class="w-16">RAM:</span>
              <span>{{ product.ramGb || "-" }}</span>
            </p>
            <p
              class="text-sm text-gray-600 mb-1 flex items-center itbms-storageGb"
            >
              <span class="w-16">Storage:</span>
              <span>
                {{ product.storageGb || "-" }}
                <span class="itbms-storageGb-unit">GB</span>
              </span>
            </p>
            <p class="text-sm text-gray-600 mb-1 flex items-center">
              <span class="w-16">Screen:</span>
              <span>{{ product.screenSizeInch || "-" }} Inches</span>
            </p>
            <p
              class="text-base font-bold text-gray-800 mb-1 flex items-center itbms-price"
            >
              <span class="w-16">Price:</span>
              <span>{{ product.price?.toLocaleString() || "-" }} ฿</span>
            </p>
          </div>
        </router-link>
      </div>

      <div class="mt-8" v-show="totalPages > 1">
        <Pagination
          v-model:currentPage="currentPage"
          :total-pages="totalPages"
          @page-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped></style>

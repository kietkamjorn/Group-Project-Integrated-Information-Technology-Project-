<!-- src/components/ProductList.vue -->
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
import {
  withAuthFetch,
  publicFetch,
  decodeJwt,
  getAccessToken,
} from "@/libs/fetchUtils";

import Sortfilter from "./Sortfilter.vue";
import Pagination from "./Pagination.vue";

/* ===== State หลัก ===== */
const route = useRoute();
const router = useRouter();

const allProducts = ref([]);
const products = ref([]);
const isLoading = ref(true);
const error = ref(null);

const selectedBrands = ref([]);
const selectedPrices = ref([]);
const selectedStorages = ref([]);

const sortOrder = ref("none");
const sortByBrandNameAsc = ref(false);

const searchKeyword = ref("");

const currentPage = ref(1);
const pageSize = ref(10);

const deleteErrorMessage = ref(route.query.errorMessage || null);
const successMessage = ref(
  route.query.success ? "The sale item has been successfully added." : null
);

/* ===== Persist UI state (localStorage) ===== */
const SS_KEY = "gallery:persist";
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
    }
    if (Array.isArray(s.prices)) selectedPrices.value = s.prices;
    if (Array.isArray(s.storages)) selectedStorages.value = s.storages;
  } catch {}
}

/* ===== URL/ภาพจาก BE ===== */
const API_URL =
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE ||
  "http://localhost:8080/itb-mshop";
let API_ORIGIN = "";
let API_CTX = "";
try {
  const parsed = new URL(API_URL);
  API_ORIGIN = parsed.origin;
  API_CTX = parsed.pathname.replace(/\/$/, "");
} catch {}
const API = `${API_ORIGIN}${API_CTX}`;

const toAbs = (u) => {
  if (!u) return null;
  if (u.startsWith("http")) return u;
  if (u.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${u}`;
  if (u.startsWith("/media/")) return `${API}${u}`;
  if (u.startsWith("media/")) return `${API}/${u}`;
  return `${API}/${String(u).replace(/^\/+/, "")}`;
};

const decorateItem = (item) => {
  const pics =
    (
      item.saleItemImages ||
      item.pictures ||
      item.saleItemPictures ||
      item.images ||
      []
    )
      ?.slice()
      ?.sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)) || [];

  const coverRel = pics[0]?.url || null;

  return {
    ...item,
    image: coverRel ? toAbs(coverRel) : null,
    thumbs: pics.map((p) => toAbs(p.url)).filter(Boolean),
    sellerId:
      item.sellerId ?? item.seller?.id ?? item.sellerID ?? item.ownerId ?? null,
    sellerNickname:
      item.sellerNickname ??
      item.seller?.nickname ??
      item.sellerName ??
      "Seller",
  };
};

/* ===== Cart (localStorage per user) ===== */
const ensureFreshAuth = async () => {
  try {
    const t = getAccessToken?.();
    const me = t ? decodeJwt(t) : null;
    if (me?.sub) {
      await withAuthFetch(`/v2/users/${me.sub}`, {
        method: "GET",
        cache: "no-store",
      });
    }
  } catch {}
};
const CART_VERSION = "v1";
function getCartKey() {
  const token = getAccessToken?.();
  const uid = token ? decodeJwt(token)?.sub : "guest";
  return `cart:${CART_VERSION}:${uid}`;
}
const readCart = () => {
  try {
    const raw = localStorage.getItem(getCartKey());
    return raw ? JSON.parse(raw) : { groups: [] };
  } catch {
    return { groups: [] };
  }
};
const writeCart = (cart) => {
  localStorage.setItem(getCartKey(), JSON.stringify(cart));
  try {
    window.dispatchEvent(new Event("itbms:cart-updated"));
  } catch {}
};
const ensureLogin = () => {
  const token = getAccessToken();
  if (!token) {
    const back = router.currentRoute.value?.fullPath || "/sale-items";
    router.push({ name: "Signin", query: { redirect: back } });
    return false;
  }
  return true;
};

const fetchSellerInfo = async (saleItemId) => {
  try {
    await ensureFreshAuth();
    const d = await withAuthFetch(`/v2/sale-items/${saleItemId}`);
    return {
      sellerId:
        d?.sellerId ?? d?.seller?.id ?? d?.sellerID ?? d?.ownerId ?? null,
      sellerNickname:
        d?.sellerNickname ?? d?.seller?.nickname ?? d?.sellerName ?? "Seller",
    };
  } catch (err1) {
    const st1 = err1?.response?.status || 0;
    if (st1 === 400 || st1 === 401 || st1 === 403) throw err1;

    try {
      await ensureFreshAuth();
      const d = await withAuthFetch(`/v1/sale-items/${saleItemId}`);
      return {
        sellerId:
          d?.sellerId ?? d?.seller?.id ?? d?.sellerID ?? d?.ownerId ?? null,
        sellerNickname:
          d?.sellerNickname ?? d?.seller?.nickname ?? d?.sellerName ?? "Seller",
      };
    } catch (err2) {
      const st2 = err2?.response?.status || 0;
      if (st2 === 400 || st2 === 401 || st2 === 403) throw err2;
      return { sellerId: null, sellerNickname: "Seller" };
    }
  }
};

const addOneToCart = async (rawItem) => {
  if (!ensureLogin()) return;

  const item = rawItem ?? {};
  const saleItemId = Number(item.id ?? item.saleItemId);
  if (!Number.isFinite(saleItemId)) {
    alert("Cannot add this item to cart (missing id).");
    return;
  }

  let sellerId =
    item?.sellerId ??
    item?.seller?.id ??
    item?.sellerID ??
    item?.ownerId ??
    null;
  let sellerNickname =
    item?.sellerNickname ?? item?.seller?.nickname ?? item?.sellerName ?? null;

  if (sellerId == null) {
    try {
      const info = await fetchSellerInfo(saleItemId);
      sellerId = info.sellerId;
      sellerNickname = info.sellerNickname;
    } catch (e) {
      // token invalid/refresh ล้มเหลวระหว่างกด Add to cart
      // → ส่งไป Sign in พร้อม redirect กลับมาหน้าเดิมหลังล็อกอิน
      const back = router.currentRoute.value?.fullPath || "/sale-items";
      router.push({ name: "Signin", query: { redirect: back } });
      return;
    }
  }

  if (sellerId == null) {
    alert(
      `Cannot add this item to cart: Seller information not found (saleItemId: ${saleItemId})`
    );
    return;
  }
  sellerNickname ||= "Seller";

  // กันผู้ขายหยิบของตัวเอง
  try {
    const token = getAccessToken() || "";
    const currentUserId = token ? decodeJwt(token)?.sub : null;
    if (currentUserId != null && String(currentUserId) === String(sellerId)) {
      alert("You cannot add your own sale item to the cart.");
      return;
    }
  } catch {}

  const availableQty = Math.max(
    1,
    Number(item.quantity ?? item.available ?? item.qty ?? 1) || 1
  );
  const unitPrice = Number(item.price ?? 0) || 0;

  const cart = readCart();
  let group = cart.groups.find((g) => String(g.sellerId) === String(sellerId));
  if (!group) {
    group = { sellerId, sellerNickname, checked: true, items: [] };
    cart.groups.push(group);
  } else if (!group.sellerNickname && sellerNickname) {
    group.sellerNickname = sellerNickname;
  }

  let row = group.items.find((r) => Number(r.saleItemId) === saleItemId);
  if (!row) {
    row = {
      saleItemId,
      sellerId,
      brandName: item.brandName ?? "",
      model: item.model ?? "",
      color: item.color ?? null,
      storageGb: item.storageGb ?? null,
      ramGb: item.ramGb ?? null,
      unitPrice,
      availableQty,
      qty: 0,
      checked: true,
      image: item.image ?? item.pictureUrl ?? null,
    };
    group.items.push(row);
  } else if (row.sellerId == null && sellerId != null) {
    row.sellerId = sellerId;
  }

  row.qty = Math.min((row.qty || 0) + 1, row.availableQty);
  writeCart(cart);
};

/* ===== โหลดสินค้า ===== */
const loadProducts = async () => {
  isLoading.value = true;
  try {
    const list = await publicFetch("/v1/sale-items");
    allProducts.value = (Array.isArray(list) ? list : []).map(decorateItem);
    error.value = null;
  } catch (e) {
    console.error(e);
    error.value = "ไม่สามารถโหลดข้อมูลสินค้าได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};

/* ===== ฟิลเตอร์/เรียง ===== */
const filterProducts = () => {
  let filtered = allProducts.value.slice();

  // Keyword
  const kw = (searchKeyword.value || "").trim().toLowerCase();
  if (kw) {
    filtered = filtered.filter((p) => {
      const hay = [p.brandName, p.model, p.color, p.description]
        .map((x) => (x ?? "").toString().toLowerCase())
        .join(" ");
      return hay.includes(kw);
    });
  }

  // Brand
  const brandSet = new Set(
    (selectedBrands.value || []).map((b) => (b.name || "").toLowerCase())
  );
  if (brandSet.size > 0) {
    filtered = filtered.filter((p) =>
      brandSet.has((p.brandName || "").toLowerCase())
    );
  }

  // Price
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

  // Storage
  if (selectedStorages.value.length > 0) {
    filtered = filtered.filter((item) => {
      const s = Number(item.storageGb);
      if (!s) return false;
      return selectedStorages.value.some((label) => {
        if (label === "1Tb+") return s >= 1024;
        const n = parseInt(label, 10);
        return s === n;
      });
    });
  }

  // Sort (เหมือนเดิม)
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
      const brandOrder = selectedBrands.value
        .slice()
        .sort((a, b) => a.id - b.id)
        .reduce((acc, b, i) => {
          acc[(b.name || "").toLowerCase()] = i;
          return acc;
        }, {});
      filtered.sort((a, b) => {
        const A = brandOrder[(a.brandName || "").toLowerCase()] ?? 999;
        const B = brandOrder[(b.brandName || "").toLowerCase()] ?? 999;
        return A !== B ? A - B : a.id - b.id;
      });
    }
  }

  products.value = filtered;
};

/* ===== Lifecycle ===== */
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
    await ensureFreshAuth();
    await loadProducts();
    filterProducts();
    isRestoring.value = false;
  })();

  // รับ keyword จาก Navbar
  window.addEventListener("itbms:search", onNavSearch);
  window.addEventListener("itbms:clear", onNavClear);

  // แสดงข้อความจาก query แล้วเคลียร์เอง
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

/* ===== Events/Handlers ===== */
const onNavSearch = (e) => {
  searchKeyword.value = (e?.detail?.keyword || "").trim();
  currentPage.value = 1;
  filterProducts();
};
const onNavClear = () => {
  searchKeyword.value = "";
  currentPage.value = 1;
  filterProducts();
};

const handlePageChange = (page) => (currentPage.value = page);

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
const handleBrandFilter = (brands) => {
  const toNames = (arr) => (arr || []).map((b) => b?.name || "").sort();
  const changed =
    JSON.stringify(toNames(selectedBrands.value)) !==
    JSON.stringify(toNames(brands));

  selectedBrands.value = brands;
  if (!isRestoring.value && changed) currentPage.value = 1;
  filterProducts();
};
const handleSortByNameUpdate = (value) => {
  sortByBrandNameAsc.value = value;
  currentPage.value = 1;
  filterProducts();
};
const handlePricesUpdate = (ranges) => {
  selectedPrices.value = Array.isArray(ranges) ? ranges : [];
  currentPage.value = 1;
  filterProducts();
};
const handleStoragesUpdate = (labels) => {
  selectedStorages.value = Array.isArray(labels) ? labels : [];
  currentPage.value = 1;
  filterProducts();
};

/* ===== Pagination derive ===== */
const paginatedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return products.value.slice(start, end);
});
const totalPages = computed(() =>
  Math.ceil(products.value.length / pageSize.value)
);
const clampCurrentPage = () => {
  const newTotal = Math.ceil(products.value.length / pageSize.value) || 1;
  if (currentPage.value > newTotal) currentPage.value = newTotal;
  if (currentPage.value < 1) currentPage.value = 1;
};
watch([products, pageSize], clampCurrentPage, { deep: true, immediate: true });

// persist state เมื่อมีการเปลี่ยนค่า
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
  saveState,
  { deep: true }
);

/* ===== รูป fallback ===== */
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

    <div v-if="error" class="mb-6 bg-red-50 text-red-700 px-4 py-3 rounded">
      {{ error }}
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

      <div
        class="grid grid-cols-[repeat(auto-fit,minmax(13rem,1fr))] gap-6 justify-items-center w-full max-w-6xl"
      >
        <div
          v-for="product in paginatedProducts.filter((p) => p.id != null)"
          :key="product.id"
          class="itbms-row bg-white rounded-xl shadow-md p-4 flex flex-col items-center w-52 hover:shadow-2xl transition-shadow duration-300"
        >
          <router-link
            :to="{
              name: 'ProductDetails',
              params: { id: product.id },
              query: {
                sellerId:
                  product.sellerId ??
                  product.seller?.id ??
                  product.sellerID ??
                  product.ownerId ??
                  null,
                sellerNickname:
                  product.sellerNickname ??
                  product.seller?.nickname ??
                  product.sellerName ??
                  'Seller',
              },
            }"
            class="w-full flex flex-col items-center"
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

              <h3
                class="text-base font-semibold text-gray-800 mb-1 itbms-model"
              >
                {{ product.model || "-" }}
              </h3>

              <p
                class="text-sm text-gray-600 mb-1 flex items-center itbms-ramGb"
              >
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

          <button
            class="mt-3 w-full bg-blue-500 text-white px-3 py-1.5 rounded hover:bg-blue-700 transition itbms-add-to-cart"
            @click.stop="addOneToCart(product)"
          >
            Add to cart
          </button>
        </div>
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

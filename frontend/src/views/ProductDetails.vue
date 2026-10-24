<!-- src/views/ProductDetails.vue -->
<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { withAuthFetch, decodeJwt } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();
const productId = route.params.id;

const product = ref(null);
const isLoading = ref(true);
const error = ref(null);

const showConfirmDialog = ref(false);
const deleteSuccess = ref(null);

// --- Safe API origin/context parsing ---
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
} catch {
  API_ORIGIN = "";
  API_CTX = "";
}
const API = `${API_ORIGIN}${API_CTX}`;

// Convert relative media path from BE to absolute
const toAbs = (u) => {
  if (!u) return null;
  if (u.startsWith("http")) return u;
  if (API_CTX && u.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${u}`;
  if (u.startsWith("/media/")) return `${API}${u}`;
  if (u.startsWith("media/")) return `${API}/${u}`;
  return `${API}/${String(u).replace(/^\/+/, "")}`;
};

// Helpers to read seller info
const getSellerId = (p) =>
  p?.sellerId ?? p?.seller?.id ?? p?.sellerID ?? p?.ownerId ?? null;
const getSellerNick = (p) =>
  p?.sellerNickname ?? p?.seller?.nickname ?? p?.sellerName ?? "Seller";

// Current user id from access_token (if any)
const currentUserId = computed(() => {
  const token = localStorage.getItem("access_token");
  try {
    return token ? decodeJwt(token)?.sub ?? null : null;
  } catch {
    return null;
  }
});

// Owner check
const isOwner = computed(() => {
  const sid = getSellerId(product.value);
  const me = currentUserId.value;
  return sid != null && me != null && String(sid) === String(me);
});

// Load product
const loadProduct = async (pid) => {
  const numericId = parseInt(pid, 10);
  if (isNaN(numericId)) {
    error.value = "productId is not a valid number.";
    isLoading.value = false;
    return;
  }

  try {
    const item = await withAuthFetch(`/v2/sale-items/${numericId}`);
    if (item && typeof item === "object") {
      const pictures = Array.isArray(item.saleItemImages) ? item.saleItemImages : [];
      const sortedPictures = pictures
        .slice()
        .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));

      const mainImage = toAbs(sortedPictures[0]?.url) || null;
      const additionalImages = sortedPictures.map((pic) => toAbs(pic.url)).filter(Boolean);

      product.value = {
        ...item,
        image: mainImage,
        "itbms-additionalImages": additionalImages,
        priceUnit: "THB",
        ramGbUnit: item.ramGb ? "GB" : "",
        storageGbUnit: item.storageGb ? "GB" : "",
        quantityUnit: "unit",
      };
      error.value = null;
    } else {
      throw new Error("Not found");
    }
  } catch {
    error.value = "The requested sale item does not exist.";
    setTimeout(() => router.push("/sale-items"), 1500);
  } finally {
    isLoading.value = false;
  }
};

// === CART (localStorage) ===
const CART_VERSION = "v1";
const getCartKey = () => {
  const token = localStorage.getItem("access_token");
  const uid = token ? decodeJwt(token)?.sub : "guest";
  return `cart:${CART_VERSION}:${uid}`;
};

const qty = ref(1);
const maxQty = computed(() => Number(product.value?.quantity ?? 1) || 1);

const ensureLogin = () => {
  const token = localStorage.getItem("access_token");
  if (!token) {
    router.push("/signin");
    return false;
  }
  return true;
};

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

const canAddMore = computed(() => {
  const available = Number(product.value?.quantity ?? 1) || 1;
  return qty.value < available;
});
const inc = () => {
  const available = Number(product.value?.quantity ?? 1) || 1;
  qty.value = Math.min(available, qty.value + 1);
};
const dec = () => {
  qty.value = Math.max(1, qty.value - 1);
};

const addToCart = () => {
  if (!ensureLogin()) return;
  const item = product.value;
  if (!item) return;

  const token = localStorage.getItem("access_token") || "";
  let me = null;
  try {
    me = decodeJwt(token)?.sub ?? null;
  } catch {}

  const saleItemId = Number(item.id);
  const sellerId = getSellerId(item) ?? null;
  const sellerNickname = getSellerNick(item) ?? "Seller";

  if (me && sellerId && String(me) === String(sellerId)) {
    alert("You cannot add your own sale item to the cart.");
    return;
  }
  if (!Number.isFinite(saleItemId)) {
    alert("Cannot add this item to cart (missing id).");
    return;
  }

  const available = Number(item.quantity ?? 1) || 1;
  const unitPrice = Number(item.price ?? 0) || 0;

  const cart = readCart();
  let group = cart.groups.find((g) => Number(g.sellerId) === Number(sellerId));
  if (!group) {
    group = { sellerId, sellerNickname, checked: true, items: [] };
    cart.groups.push(group);
  }

  let row = group.items.find((r) => Number(r.saleItemId) === saleItemId);
  if (!row) {
    row = {
      saleItemId,
      brandName: item.brandName ?? "",
      model: item.model ?? "",
      color: item.color ?? null,
      storageGb: item.storageGb ?? null,
      ramGb: item.ramGb ?? null,
      unitPrice,
      availableQty: available,
      qty: 0,
      checked: true,
      image: item.image ?? null,
    };
    group.items.push(row);
  }

  const addQty = Number(qty.value || 1);
  row.qty = Math.min((row.qty || 0) + addQty, row.availableQty || available);

  writeCart(cart);
};

// --- Delete (owner only) with robust fallbacks ---
const deleteSaleItemWithFallback = async (id) => {
  const attempts = [
    () => withAuthFetch(`/v2/sale-items/${id}`, { method: "DELETE" }),
    () =>
      withAuthFetch(`/v2/sale-items/${id}`, {
        method: "POST",
        headers: { "X-HTTP-Method-Override": "DELETE" },
      }),
    () => withAuthFetch(`/v2/sale-items/delete/${id}`, { method: "POST" }),
    () => withAuthFetch(`/v1/sale-items/${id}`, { method: "DELETE" }),
    () => withAuthFetch(`/v1/sale-items/delete/${id}`, { method: "POST" }),
  ];

  let lastErr = null;
  for (const run of attempts) {
    try {
      return await run();
    } catch (err) {
      const status = err?.response?.status;
      const msg =
        (typeof err?.response?.data === "string"
          ? err.response.data
          : err?.response?.data?.message) || String(err?.message || "");

      if (status === 404 || status === 409) throw err;
      const notSupported =
        status === 405 ||
        /not supported|method not allowed|request method/i.test(msg);
      if (!notSupported) {
        // try next anyway; keep last error
      }
      lastErr = err;
    }
  }
  throw lastErr;
};

const openDelete = () => (showConfirmDialog.value = true);
const cancelDelete = () => (showConfirmDialog.value = false);

const confirmDelete = async () => {
  try {
    const id = Number(route.params.id);
    await deleteSaleItemWithFallback(id);
    deleteSuccess.value = "The sale item has been deleted.";
    showConfirmDialog.value = false;
    setTimeout(() => router.push("/sale-items"), 1200);
  } catch (err) {
    const st = err?.response?.status;
    const serverMsg =
      typeof err?.response?.data === "string"
        ? err.response.data
        : err?.response?.data?.message;

    let message = serverMsg || `Delete failed (status ${st ?? "unknown"}).`;
    if (st === 404) {
      message = "The requested sale item does not exist.";
    } else if (st === 409) {
      message =
        serverMsg ||
        "Delete is not allowed. This sale item is referenced by other records.";
    } else if (st === 500) {
      if (/not supported|method not allowed/i.test(serverMsg || "")) {
        message =
          "Delete is not supported by this endpoint. Switched method failed.";
      }
    }

    router.push({ path: "/sale-items", query: { errorMessage: message } });
  }
};

const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png";

const onImgError = (e) => {
  e.target.onerror = null;
  e.target.src = FALLBACK_IMG;
};

onMounted(() => {
  loadProduct(productId);
});
</script>

<template>
  <div class="px-10 py-8 w-full bg-white rounded-lg shadow-lg p-6 sm:p-8 lg:p-10">
    <nav class="text-gray-500 text-lg mb-6">
      <router-link to="/sale-items" class="hover:underline font-bold">Home</router-link>
      <span class="mx-2"> > </span>
      <span class="font-bold itbms-home-button">
        {{ product?.model ?? "..." }} {{ product?.color ?? "" }}
      </span>
    </nav>

    <div v-if="isLoading" class="text-center text-gray-500 text-xl">
      Loading...
    </div>

    <div v-else-if="error" class="flex flex-col justify-center items-center text-center text-gray-700">
      <div class="itbms-message bg-white p-6 rounded-lg shadow-lg">
        <div class="mb-6">{{ error }}</div>
        <router-link
          to="/sale-items"
          class="mt-6 inline-block bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 transition"
        >
          Back to Home
        </router-link>
      </div>
    </div>

    <div v-else-if="product" class="itbms-row flex gap-8 flex-col lg:flex-row">
      <!-- Left: Images -->
      <div class="lg:w-1/2 flex flex-col items-center">
        <img
          :src="product.image || FALLBACK_IMG"
          alt="Product image"
          class="w-150 h-150 object-contain mb-3 border"
          @error="onImgError"
        />
        <div class="flex justify-center gap-4 pt-4 flex-wrap">
          <img
            v-for="(thumb, index) in (product['itbms-additionalImages']?.length
              ? product['itbms-additionalImages']
              : [FALLBACK_IMG, FALLBACK_IMG, FALLBACK_IMG, FALLBACK_IMG])"
            :key="index + '-' + thumb"
            :src="thumb || FALLBACK_IMG"
            alt="Thumbnail"
            class="w-20 h-20 object-cover border rounded cursor-pointer hover:scale-105 transition"
            @click="product.image = thumb || FALLBACK_IMG"
            @error="onImgError"
          />
        </div>
      </div>

      <!-- Right: Details -->
      <div class="flex-1 py-4 text-xl leading-loose pt-20">
        <p class="itbms-brand text-gray-600 mb-6">
          <strong>Brand:</strong> {{ product.brandName ?? "-" }}
        </p>
        <p class="itbms-model text-gray-600 mb-6">
          <strong>Model:</strong> {{ product.model ?? "-" }}
        </p>
        <p class="itbms-price text-gray-600 mb-6">
          <strong>Price:</strong> {{ product.price?.toLocaleString() ?? "-" }} {{ product.priceUnit ?? "" }}
        </p>
        <p class="itbms-description text-gray-600 mb-6">
          <strong>Description:</strong> {{ product.description ?? "-" }}
        </p>
        <p class="itbms-ramGb text-gray-600 mb-6">
          <strong>Ram:</strong> {{ product.ramGb ?? "-" }}
          <span class="itbms-ramGb-unit">{{ product.ramGbUnit ?? "" }}</span>
        </p>
        <p class="itbms-screenSizeInch text-gray-600 mb-6">
          <strong>Screen Size:</strong> {{ product.screenSizeInch ?? "-" }}
          <span class="itbms-screenSizeInch-unit">Inches</span>
        </p>
        <p class="itbms-storageGb text-gray-600 mb-6">
          <strong>Storage:</strong> {{ product.storageGb ?? "-" }}
          <span class="itbms-storageGb-unit">{{ product.storageGbUnit ?? "" }}</span>
        </p>
        <p class="itbms-color text-gray-600 mb-6">
          <strong>Color:</strong> {{ product.color === "" || product.color == null ? "-" : product.color }}
        </p>
        <p class="itbms-quantity text-gray-600 mb-6">
          <strong>Available quantity:</strong> {{ product.quantity ?? "-" }}
          <span class="itbms-quantity-unit">{{ product.quantityUnit ?? "" }}</span>
        </p>

        <!-- Actions -->
        <div class="flex flex-col sm:flex-row gap-3 mt-6 items-start sm:items-center">
          <!-- Qty control (buyer) -->
          <div v-if="!isOwner" class="flex items-center gap-2">
            <button
              type="button"
              class="px-3 py-1 rounded border text-gray-700 hover:bg-gray-50"
              @click="dec"
              :disabled="qty <= 1"
            >
              −
            </button>

            <input
              class="w-16 text-center border rounded py-1"
              type="number"
              min="1"
              :max="maxQty"
              v-model.number="qty"
              @change="qty = Math.min(Math.max(1, Number(qty) || 1), maxQty)"
            />

            <button
              type="button"
              class="px-3 py-1 rounded border text-gray-700 hover:bg-gray-50"
              @click="inc"
              :disabled="qty >= maxQty"
            >
              +
            </button>
          </div>

          <!-- Buyer's Add-to-cart -->
          <button
            v-if="!isOwner"
            type="button"
            class="bg-emerald-600 text-white px-6 py-2 rounded hover:bg-emerald-700 transition itbms-add-to-cart"
            @click="addToCart"
          >
            Add to cart
          </button>

          <!-- Owner's Edit/Delete -->
          <router-link
            v-if="isOwner"
            :to="{ name: 'EditSaleItem', params: { id: product.id } }"
            class="inline-flex items-center gap-2 px-4 py-2 rounded border bg-blue-500 text-white"
          >
            Edit
          </router-link>

          <button
            v-if="isOwner"
            type="button"
            class="inline-flex items-center gap-2 px-4 py-2 rounded border bg-red-500 text-white"
            @click="openDelete"
          >
            Delete
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm Delete Modal (owner) -->
    <div
      v-if="showConfirmDialog"
      class="fixed inset-0 flex items-center justify-center z-50 bg-black/40 p-4"
    >
      <div class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black">
        <h2 class="text-2xl font-bold mb-4">Delete Confirmation</h2>
        <p class="mb-6 itbms-message">Do you want to delete this sale item?</p>
        <div class="flex justify-center gap-4 pt-4">
          <button
            class="bg-gray-100 text-gray-700 px-6 py-2 rounded hover:bg-gray-200 transition itbms-cancel-button"
            @click="cancelDelete"
          >
            Cancel
          </button>
          <button
            class="bg-rose-600 text-white px-6 py-2 rounded hover:bg-rose-700 transition itbms-confirm-button"
            @click="confirmDelete"
          >
            Confirm
          </button>
        </div>
      </div>
    </div>

    <!-- Success Message -->
    <div
      v-if="deleteSuccess"
      class="fixed bottom-6 left-1/2 transform -translate-x-1/2 bg-green-100 text-green-700 px-6 py-3 rounded shadow-lg itbms-message"
    >
      {{ deleteSuccess }}
    </div>
  </div>
</template>

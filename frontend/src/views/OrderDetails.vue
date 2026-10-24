<!-- src/views/OrderDetails.vue -->
<script setup>
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const error = ref("");
const order = ref(null); 

const goBackToOrders = () => {
  if (window.history.length > 1) router.back();
  else router.push("/your-orders");
};

const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png";

const fmt = (n) => (Number.isFinite(n) ? n.toLocaleString() : "-");
const fmtDate = (v) => {
  const d = v instanceof Date ? v : v ? new Date(v) : null;
  return d && !isNaN(d) ? d.toLocaleString() : "-";
};


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
  const s = String(u);
  if (/^https?:\/\//i.test(s)) return s;
  if (s.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${s}`;
  if (s.startsWith("/media/")) return `${API}${s}`;
  if (s.startsWith("media/")) return `${API}/${s}`;
  return `${API}/${s.replace(/^\/+/, "")}`;
};

function normalizeOrder(o) {
  if (!o) return null;

  const sellersRaw = Array.isArray(o.sellers)
    ? o.sellers
    : o.seller
    ? [o.seller]
    : [{ nickname: o.sellerNickname ?? o.nickname ?? o.sellerName ?? "Seller", items: o.orderItems ?? o.items ?? [] }];

  const sellers = sellersRaw.map((s) => {
    const nickname = s.nickname ?? s.sellerNickname ?? s.sellerName ?? "Seller";
    const linesRaw = Array.isArray(s.items)
      ? s.items
      : Array.isArray(o.orderItems)
      ? o.orderItems
      : Array.isArray(o.items)
      ? o.items
      : [];

    const items = linesRaw.map((it) => ({
      saleItemId: it.saleItemId ?? it.itemId ?? it.productId ?? null,
      brandName: it.brandName ?? it.brand ?? "",
      model: it.model ?? "",
      color: it.color ?? null,
      storageGb: it.storageGb ?? it.storage ?? null,
      ramGb: it.ramGb ?? it.ram ?? null,
      qty: Number(it.quantity ?? it.qty) || 0,
      unitPrice: Number(it.price ?? it.unitPrice) || 0,
      image: it.image ? toAbs(it.image) : it.pictureUrl ? toAbs(it.pictureUrl) : null,
    }));

    return { nickname, items };
  });

  const itemsFlat = sellers.flatMap((s) => s.items);
  const total =
    Number(o.total) ||
    itemsFlat.reduce(
      (s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0),
      0
    );

  return {
    id: o.id ?? o.orderId ?? undefined,
    createdOn: o.orderDate ?? o.createdOn ?? o.createdDate ?? o.orderedOn ?? o.date ?? null,
    paymentOn: o.paymentDate ?? o.paymentOn ?? o.paidOn ?? null,
    orderStatus: o.orderStatus ?? o.status ?? null,
    shippingAddress: o.shippingAddress ?? o.shipTo ?? o.address ?? null,
    orderNote: o.orderNote ?? o.note ?? null,
    sellers,
    total,
  };
}

async function hydrate(orderObj) {
  if (!orderObj) return;

  const needIds = new Set();
  for (const s of orderObj.sellers || []) {
    for (const it of s.items || []) {
      if (it.saleItemId && (!it.brandName || !it.model || !it.image)) {
        needIds.add(Number(it.saleItemId));
      }
    }
  }
  if (!needIds.size) return;

  const cache = new Map();
  await Promise.all(
    [...needIds].map(async (id) => {
      try {
        const d = await withAuthFetch(`/v2/sale-items/${id}`);
        cache.set(id, {
          brandName: d?.brandName ?? "",
          model: d?.model ?? "",
          color: d?.color ?? null,
          storageGb: d?.storageGb ?? null,
          ramGb: d?.ramGb ?? null,
          image: d?.saleItemImages?.[0]?.url ? toAbs(d.saleItemImages[0].url) : null,
        });
      } catch {
        cache.set(id, null);
      }
    })
  );

  for (const s of orderObj.sellers || []) {
    for (const it of s.items || []) {
      const info = cache.get(Number(it.saleItemId));
      if (!info) continue;
      it.brandName = it.brandName || info.brandName || "";
      it.model = it.model || info.model || "";
      it.color = it.color ?? info.color;
      it.storageGb = it.storageGb ?? info.storageGb;
      it.ramGb = it.ramGb ?? info.ramGb;
      it.image = it.image || info.image || FALLBACK_IMG;
    }
  }
}

/* ---- Load order by route param ---- */
async function load() {
  isLoading.value = true;
  error.value = "";

  try {
    const token = getAccessToken();
    const me = token ? decodeJwt(token) : null;
    if (!me?.sub) {
      router.replace({ path: "/signin", query: { redirect: route.fullPath } });
      return;
    }

    const id = Number(route.params.orderId);
    if (!Number.isFinite(id)) {
      error.value = "Invalid order id.";
      return;
    }

    // Try direct get
    let raw = null;
    try {
      raw = await withAuthFetch(`/v2/orders/${id}`);
    } catch {
      // Fallback: fetch all my orders then find
      const list = await withAuthFetch(`/v2/users/${me.sub}/orders`);
      const arr = Array.isArray(list) ? list : list?.content || [];
      raw =
        arr.find((o) => Number(o.id ?? o.orderId) === id) ||
        null;
    }

    if (!raw) {
      error.value = "Order not found.";
      return;
    }

    const norm = normalizeOrder(raw);
    // Convert dates to Date
    if (norm.createdOn) norm.createdOn = new Date(norm.createdOn);
    if (norm.paymentOn) norm.paymentOn = new Date(norm.paymentOn);
    order.value = norm;

    await hydrate(order.value);
  } catch (e) {
    error.value =
      (typeof e?.response?.data === "string" && e.response.data) ||
      e?.response?.data?.message ||
      e?.message ||
      "Cannot load order.";
  } finally {
    isLoading.value = false;
  }
}

onMounted(load);

const lineTotal = (it) => (Number(it.qty) || 0) * (Number(it.unitPrice) || 0);
</script>

<template>
  <div class="w-full px-4 sm:px-6 lg:px-10 py-8 not-prose">
    <div class="mb-4 flex items-center gap-2 text-lg">
      <button
        type="button"
        class="itbms-your-orders-button text-gray-500 hover:underline font-semibold"
        @click="goBackToOrders"
      >
        Your Orders
      </button>
      <span aria-hidden="true" class="text-gray-400">›</span>
      <span class="text-gray-900 font-semibold">Order Details</span>
    </div>

    <div v-if="isLoading" class="text-gray-600">Loading...</div>
    <div v-else-if="error" class="bg-rose-100 text-rose-800 px-4 py-3 rounded">
      {{ error }}
    </div>

    <section
      v-else
      class="bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm overflow-hidden"
    >
      <!-- Header -->
      <div class="p-5 border-b border-gray-100 grid gap-4 md:grid-cols-2">
        <div class="space-y-2">
          <div>
            <span class="text-gray-500">Order No:</span>
            <span class="font-semibold itbms-order-id">{{ order.id ?? "-" }}</span>
          </div>
          <div>
            <span class="text-gray-500">Order Date:</span>
            <span class="font-semibold itbms-order-date">{{ fmtDate(order.createdOn) }}</span>
          </div>
          <div>
            <span class="text-gray-500">Total:</span>
            <span class="font-semibold text-lg itbms-total-order-price">{{ fmt(order.total) }}</span>
          </div>
          <div>
            <span class="text-gray-500">Shipped To:</span>
            <div class="itbms-shipping-address">
              {{ order.shippingAddress || "-" }}
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <div>
            <span class="text-gray-500">Seller:</span>
            <span class="font-semibold itbms-nickname">
              <!-- ถ้าหลาย seller จะโชว์คนแรก + (+N more) -->
              <template v-if="order.sellers && order.sellers.length">
                {{ order.sellers[0].nickname || "Seller" }}
                <span v-if="order.sellers.length > 1" class="text-gray-500">
                  (+{{ order.sellers.length - 1 }} more)
                </span>
              </template>
              <template v-else>Seller</template>
            </span>
          </div>
          <div>
            <span class="text-gray-500">Payment Date:</span>
            <span class="font-semibold itbms-payment-date">
              {{ order.paymentOn ? fmtDate(order.paymentOn) : "-" }}
            </span>
          </div>
          <div>
            <span class="text-gray-500">Status:</span>
            <span class="font-semibold itbms-order-status">{{ order.orderStatus || "-" }}</span>
          </div>
          <div v-if="order.orderNote">
            <span class="text-gray-500">Note:</span>
            <span class="itbms-order-note">{{ order.orderNote }}</span>
          </div>
        </div>
      </div>

      <!-- Items -->
      <div class="divide-y divide-gray-100">
        <div v-for="s in order.sellers" :key="s.nickname" class="p-5">
          <div
            v-for="it in s.items"
            :key="(it.saleItemId ?? '') + '-' + (it.model ?? '') + '-' + (it.storageGb ?? '') + '-' + (it.color ?? '')"
            class="flex items-start gap-4 py-3"
          >
            <img
              :src="it.image || FALLBACK_IMG"
              class="w-20 h-20 object-cover rounded-lg ring-1 ring-gray-200"
              alt="item"
            />
            <div class="flex-1">
              <div class="font-medium text-gray-900 itbms-item-description">
                <span class="text-gray-700">{{ it.brandName || "-" }}</span>
                &nbsp;{{ it.model || "-" }}
                <span v-if="it.storageGb"> ({{ it.storageGb }}GB</span
                ><span v-if="it.color">, {{ it.color }}</span
                ><span v-if="it.storageGb">)</span>
              </div>
              <div class="text-sm text-gray-500 itbms-item-quantity">
                Qty: {{ it.qty }}
              </div>
            </div>
            <div class="w-40 text-right">
              <div class="text-sm text-gray-500">Unit Price</div>
              <div class="font-semibold itbms-item-price">
                {{ fmt(it.unitPrice) }}
              </div>
            </div>
            <div class="w-40 text-right">
              <div class="text-sm text-gray-500">Price</div>
              <div class="text-lg font-semibold itbms-item-total-price">
                {{ fmt(lineTotal(it)) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

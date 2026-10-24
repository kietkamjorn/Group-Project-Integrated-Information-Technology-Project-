<script setup>
import { ref, onMounted, computed } from "vue";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";
import { useRoute } from "vue-router";

const route = useRoute();

const isLoading = ref(true);
const error = ref(null);
const success = ref(
  route.query.success === "true"
    ? "Your order has been successfully processed."
    : null
);
const orders = ref([]);

/* ========== Preview (จาก Cart) ========== */
const previewOrder = ref(null);
const loadPreview = () => {
  const key = route.query.previewKey;
  if (!key) return;
  try {
    const raw = sessionStorage.getItem(key);
    if (raw) {
      const v = JSON.parse(raw);
      v.orderStatus = String(v.orderStatus || v.status || "").toUpperCase() || null;
      if (v.createdOn) v.createdOn = new Date(v.createdOn);
      if (v.paymentOn) v.paymentOn = new Date(v.paymentOn);
      previewOrder.value = v;
    }
  } catch {}
};

/* ========== Utils ========== */
const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png";

const fmt = (n) => (Number.isFinite(n) ? n.toLocaleString() : "-");
const fmtDate = (v) => {
  const d = v instanceof Date ? v : v ? new Date(v) : null;
  return d && !isNaN(d) ? d.toLocaleString() : "-";
};

/* ========== Normalize response จาก BE ========== */
const normalizeOrders = (raw) => {
  const list = Array.isArray(raw?.content)
    ? raw.content
    : Array.isArray(raw)
    ? raw
    : [];

  return list
    .map((o) => {
      const id = o.id ?? o.orderId ?? undefined;
      const orderDate =
        o.orderDate ?? o.createdOn ?? o.createdDate ?? o.orderedOn ?? o.date ?? null;
      const paymentDate = o.paymentDate ?? o.paymentOn ?? o.paidOn ?? null;
      const status = o.orderStatus ?? o.status ?? null;
      const shipping = o.shippingAddress ?? o.shipTo ?? o.address ?? null;
      const note = o.orderNote ?? o.note ?? null;

      const sellerId = o.sellerId ?? o.seller?.id ?? null;
      const nickname = o.sellerNickname ?? o.nickname ?? o.sellerName ?? null;

      const linesRaw = Array.isArray(o.orderItems)
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
        image: it.image ?? it.pictureUrl ?? null,
      }));

      const total =
        Number(o.total) ||
        items.reduce(
          (s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0),
          0
        );

      return {
        id,
        createdOn: orderDate ? new Date(orderDate) : null,
        paymentOn: paymentDate ? new Date(paymentDate) : null,
        orderStatus: status,
        shippingAddress: shipping,
        orderNote: note,
        sellers: [{ sellerId, nickname: nickname || "Seller", items }],
        total,
      };
    })
    .sort(
      (a, b) =>
        (b.createdOn?.getTime?.() || 0) - (a.createdOn?.getTime?.() || 0)
    );
};

/* ========== เติมรายละเอียด sale item (brand/model/รูป + seller) ========== */
const hydrateOrders = async () => {
  const ids = new Set();
  for (const o of orders.value) {
    for (const s of o.sellers || []) {
      for (const it of s.items || []) {
        if (it.saleItemId) ids.add(Number(it.saleItemId));
      }
    }
  }
  if (!ids.size) return;

  const cache = new Map();
  await Promise.all(
    [...ids].map(async (id) => {
      try {
        const d = await withAuthFetch(`/v2/sale-items/${id}`);
        cache.set(id, {
          brandName: d?.brandName ?? "",
          model: d?.model ?? "",
          color: d?.color ?? null,
          storageGb: d?.storageGb ?? null,
          ramGb: d?.ramGb ?? null,
          image: d?.saleItemImages?.[0]?.url || null,
          sellerId: d?.sellerId ?? d?.seller?.id ?? null,
          sellerNickname: d?.sellerNickname ?? d?.seller?.nickName ?? null,
        });
      } catch {
        cache.set(id, null);
      }
    })
  );

  for (const o of orders.value) {
    for (const s of o.sellers || []) {
      for (const it of s.items || []) {
        const info = cache.get(Number(it.saleItemId));
        if (!info) continue;

        it.brandName = it.brandName || info.brandName || "";
        it.model = it.model || info.model || "";
        it.color = it.color ?? info.color;
        it.storageGb = it.storageGb ?? info.storageGb;
        it.ramGb = it.ramGb ?? info.ramGb;
        it.image = it.image || info.image || FALLBACK_IMG;

        if (!s.sellerId && info.sellerId) s.sellerId = info.sellerId;
        if (!s.nickname || s.nickname === "Seller") {
          if (info.sellerNickname) s.nickname = info.sellerNickname;
        }
      }
    }
  }
};

/* ========== โหลดออเดอร์จาก BE ========== */
const loadOrders = async () => {
  isLoading.value = true;
  try {
    const token = getAccessToken?.();
    const me = token ? decodeJwt(token) : null;
    const userId = me?.sub;

    let data;
    if (userId != null) {
      try {
        data = await withAuthFetch(`/v2/users/${userId}/orders`);
      } catch {
        data = await withAuthFetch(`/v2/orders?userId=${userId}`);
      }
    } else {
      data = await withAuthFetch(`/v2/orders`);
    }

    orders.value = normalizeOrders(data);
    await hydrateOrders();
    error.value = null;
  } catch (err) {
    error.value = err?.message || "Cannot load your orders.";
    orders.value = [];
  } finally {
    isLoading.value = false;
    if (success.value) setTimeout(() => (success.value = null), 3000);
  }
};

onMounted(() => {
  loadPreview();
  loadOrders();
});

/* ========== Tabs ========== */
const TABS = ["COMPLETED", "PENDING", "CANCELED"];
const activeTab = ref("PENDING");

const countBy = (status) =>
  computed(() => orders.value.filter((o) => (o.orderStatus || "") === status).length);

const completedCount = countBy("COMPLETED");
const pendingCount = countBy("PENDING");
const canceledCount = countBy("CANCELED");

const filteredOrders = computed(() => {
  const st = activeTab.value;
  return orders.value.filter((o) => (o.orderStatus || "") === st);
});

const showPreview = computed(() => {
  const p = previewOrder.value;
  if (!p) return false;
  const st = (p.orderStatus || p.status || "").toUpperCase();
  return !!st && st === activeTab.value;
});
</script>

<template>
  <div class="w-full px-4 sm:px-6 lg:px-10 py-8 not-prose">
    <h1 class="text-2xl sm:text-3xl font-semibold text-gray-900 mb-6">
      Your Orders
    </h1>

    <div
      v-if="success"
      class="mb-4 bg-emerald-100 text-emerald-800 px-4 py-3 rounded itbms-message"
    >
      {{ success }}
    </div>
    <div
      v-if="error"
      class="mb-4 bg-rose-100 text-rose-800 px-4 py-3 rounded itbms-message"
    >
      {{ error }}
    </div>

    <!-- Tabs -->
    <div class="itbms-tabs mb-4 flex items-center gap-6 text-gray-500">
      <button
        class="itbms-tab-pending relative pb-2"
        :class="activeTab === 'PENDING' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'"
        @click="activeTab = 'PENDING'"
      >
        Pending
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ pendingCount }}</span>
        <span v-if="activeTab === 'PENDING'" class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"></span>
      </button>

      <button
        class="itbms-tab-completed relative pb-2"
        :class="activeTab === 'COMPLETED' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'"
        @click="activeTab = 'COMPLETED'"
      >
        Completed
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ completedCount }}</span>
        <span v-if="activeTab === 'COMPLETED'" class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"></span>
      </button>

      <button
        class="itbms-tab-canceled relative pb-2"
        :class="activeTab === 'CANCELED' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'"
        @click="activeTab = 'CANCELED'"
      >
        Canceled
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ canceledCount }}</span>
        <span v-if="activeTab === 'CANCELED'" class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"></span>
      </button>
    </div>

    <div v-if="isLoading" class="text-gray-600">Loading...</div>

    <!-- PREVIEW (เฉพาะเมื่อสถานะตรงกับแท็บ) -->
    <section
      v-if="!isLoading && showPreview"
      class="itbms-row bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm overflow-hidden mb-6"
    >
      <div class="p-5 border-b border-gray-100">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="flex items-center gap-2">
            <span class="inline-flex items-center justify-center w-6 h-6 rounded bg-gray-800 text-white text-xs">S</span>
            <div class="font-semibold text-gray-900 itbms-nickname">
              {{ previewOrder.sellers?.[0]?.nickname || "Seller" }}
            </div>
          </div>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-x-8 gap-y-2 text-sm">
            <div>
              <div class="text-gray-500">Order No:</div>
              <div class="font-semibold itbms-order-id">{{ previewOrder.id ?? "-" }}</div>
            </div>
            <div>
              <div class="text-gray-500">Order Date:</div>
              <div class="font-semibold itbms-order-date">{{ fmtDate(previewOrder.createdOn) }}</div>
            </div>
            <div>
              <div class="text-gray-500">Payment Date:</div>
              <div class="font-semibold itbms-payment-date">
                {{ previewOrder.paymentOn ? fmtDate(previewOrder.paymentOn) : "-" }}
              </div>
            </div>
            <div>
              <div class="text-gray-500">Total:</div>
              <div class="font-semibold text-lg itbms-total-order-price">
                {{ fmt(previewOrder.total) }}
              </div>
            </div>
            <div class="md:col-span-1">
              <div class="text-gray-500">Status:</div>
              <div class="font-semibold itbms-order-status">
                {{ previewOrder.orderStatus || "COMPLETED" }}
              </div>
            </div>
          </div>
        </div>

        <div class="mt-3 text-sm">
          <div class="text-gray-500">Shipped To:</div>
          <div class="itbms-shipping-address">
            {{ previewOrder.shippingAddress || "-" }}
          </div>
        </div>
        <div v-if="previewOrder.orderNote" class="mt-2 text-sm">
          <div class="text-gray-500">Note :</div>
          <div class="itbms-order-note">{{ previewOrder.orderNote }}</div>
        </div>
      </div>

      <div class="divide-y divide-gray-100">
        <div v-for="s in previewOrder.sellers" :key="s.nickname" class="p-5">
          <div
            v-for="it in s.items"
            :key="it.model + String(it.storageGb) + String(it.color)"
            class="itbms-item-row flex gap-4 items-start"
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
            </div>
            <div class="w-24 text-right">
              <div class="text-sm text-gray-500 itbms-item-quantity">Qty {{ it.qty }}</div>
            </div>
            <div class="w-36 text-right">
              <div class="text-sm text-gray-500">Price:</div>
              <div class="text-lg font-semibold text-gray-900 itbms-item-total-price">
                {{ fmt((it.qty || 0) * (it.unitPrice || 0)) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- เมื่อไม่มีรายการในแท็บนี้เลย -->
    <div
      v-else-if="!filteredOrders.length"
      class="bg-white rounded-2xl ring-1 ring-gray-100 p-10 text-center text-gray-600 shadow-sm"
    >
      No {{ activeTab.toLowerCase() }} orders.
    </div>

    <!-- Orders จาก BE (กรองตามแท็บ) -->
    <div v-else class="space-y-6">
      <section
        v-for="o in filteredOrders"
        :key="o.id"
        class="itbms-row bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm hover:ring-indigo-300 transition-all cursor-pointer"
        @click.stop="$router.push({ name: 'YourOrderDetail', params: { orderId: o.id } })"
      >
        <div class="p-5 border-b border-gray-100">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="flex items-center gap-2">
              <span class="inline-flex items-center justify-center w-6 h-6 rounded bg-gray-800 text-white text-xs">S</span>
              <div class="font-semibold text-gray-900 itbms-nickname">
                {{ o.sellers?.[0]?.nickname || "Seller" }}
              </div>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-4 gap-x-8 gap-y-2 text-sm">
              <div>
                <div class="text-gray-500">Order No:</div>
                <div class="font-semibold itbms-order-id">{{ o.id ?? "-" }}</div>
              </div>
              <div>
                <div class="text-gray-500">Order Date:</div>
                <div class="font-semibold itbms-order-date">{{ fmtDate(o.createdOn) }}</div>
              </div>
              <div>
                <div class="text-gray-500">Payment Date:</div>
                <div class="font-semibold itbms-payment-date">{{ o.paymentOn ? fmtDate(o.paymentOn) : "-" }}</div>
              </div>
              <div>
                <div class="text-gray-500">Total:</div>
                <div class="font-semibold text-lg itbms-total-order-price">{{ fmt(o.total) }}</div>
              </div>
              <div class="md:col-span-1">
                <div class="text-gray-500">Status:</div>
                <div class="font-semibold itbms-order-status">{{ o.orderStatus || "-" }}</div>
              </div>
            </div>
          </div>

          <div v-if="o.shippingAddress" class="mt-3 text-sm">
            <div class="text-gray-500">Shipped To:</div>
            <div class="itbms-shipping-address">{{ o.shippingAddress }}</div>
          </div>
          <div v-if="o.orderNote" class="mt-2 text-sm">
            <div class="text-gray-500">Note :</div>
            <div class="itbms-order-note">{{ o.orderNote }}</div>
          </div>
        </div>

        <div class="divide-y divide-gray-100">
          <div v-for="s in o.sellers" :key="s.nickname" class="p-5">
            <div
              v-for="it in s.items"
              :key="it.model + String(it.storageGb) + String(it.color)"
              class="itbms-item-row flex gap-4 items-start"
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
              </div>
              <div class="w-24 text-right">
                <div class="text-sm text-gray-500 itbms-item-quantity">Qty {{ it.qty }}</div>
              </div>
              <div class="w-36 text-right">
                <div class="text-sm text-gray-500">Price:</div>
                <div class="text-lg font-semibold text-gray-900 itbms-item-total-price">
                  {{ fmt((it.qty || 0) * (it.unitPrice || 0)) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

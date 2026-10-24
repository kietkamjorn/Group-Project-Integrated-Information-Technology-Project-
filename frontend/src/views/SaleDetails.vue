<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const error = ref(null);
const order = ref(null); // normalized

const goBackToOrders = () => router.push("/sale-orders");

const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png";

const fmt = (n) => (Number.isFinite(n) ? n.toLocaleString() : "-");
const fmtDate = (v) => {
  const d = v instanceof Date ? v : v ? new Date(v) : null;
  return d && !isNaN(d) ? d.toLocaleString() : "-";
};

// ---- normalize (seller view: show buyer) ----
const normalizeOne = (o) => {
  if (!o) return null;
  const id = o.id ?? o.orderId ?? undefined;

  const orderDate =
    o.orderDate ?? o.createdOn ?? o.createdDate ?? o.orderedOn ?? o.date ?? null;
  const paymentDate = o.paymentDate ?? o.paymentOn ?? o.paidOn ?? null;
  const status = o.orderStatus ?? o.status ?? null;
  const shipping = o.shippingAddress ?? o.shipTo ?? o.address ?? null;
  const note = o.orderNote ?? o.note ?? null;

  // buyer emphasis for seller page
  const buyerNickname =
    o.buyerNickname ?? o.buyerName ?? o.buyer?.nickname ?? o.buyer?.name ?? "Buyer";
  const buyerId = o.buyerId ?? o.buyer?.id ?? o.buyerID ?? o.customerId ?? null;

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
    items.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0);

  return {
    id,
    createdOn: orderDate ? new Date(orderDate) : null,
    paymentOn: paymentDate ? new Date(paymentDate) : null,
    orderStatus: status,
    shippingAddress: shipping,
    orderNote: note,
    buyer: { nickname: buyerNickname, id: buyerId },
    // keep same structure as buyer page for items list
    sellers: [{ nickname: "You", items }],
    total,
  };
};

// ---- hydrate missing sale-item info (brand/model/image) ----
const hydrate = async () => {
  if (!order.value) return;
  const ids = new Set();
  for (const s of order.value.sellers || []) {
    for (const it of s.items || []) {
      if (it.saleItemId && (!it.brandName || !it.model || !it.image)) {
        ids.add(Number(it.saleItemId));
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
        });
      } catch {
        cache.set(id, null);
      }
    })
  );

  for (const s of order.value.sellers || []) {
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
};

// ---- load for SELLER (with fallbacks) ----
const load = async () => {
  isLoading.value = true;
  error.value = null;
  try {
    const id = Number(route.params.orderId);
    if (!Number.isFinite(id)) throw new Error("Invalid order id");

    const me = decodeJwt(getAccessToken());
    const sellerId = me?.sub;

    let raw = null;
    const attempts = [
      () => withAuthFetch(`/v2/sale-orders/${id}`), // seller-focused endpoint
      () => (sellerId ? withAuthFetch(`/v2/sellers/${sellerId}/orders/${id}`) : Promise.reject()),
      () => withAuthFetch(`/v2/orders/${id}`), // generic fallback
    ];

    let lastErr = null;
    for (const run of attempts) {
      try {
        raw = await run();
        if (raw) break;
      } catch (e) {
        lastErr = e;
      }
    }
    if (!raw) throw lastErr || new Error("Order not found");

    order.value = normalizeOne(raw);
    if (!order.value) throw new Error("Order not found");

    await hydrate();
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || "Cannot load order.";
    order.value = null;
  } finally {
    isLoading.value = false;
  }
};

onMounted(load);

const lineTotal = (it) => (Number(it.qty) || 0) * (Number(it.unitPrice) || 0);
</script>

<template>
  <div class="w-full px-4 sm:px-6 lg:px-10 py-8 not-prose">
    <div class="mb-4 flex items-center gap-2 text-lg">
      <button
        type="button"
        class="itbms-sale-orders-button text-gray-500 hover:underline font-semibold"
        @click="goBackToOrders"
      >
        Sale Orders
      </button>
      <span aria-hidden="true" class="text-gray-400">›</span>
      <span class="text-gray-900 font-semibold">Order Details</span>
    </div>

    <div v-if="isLoading" class="text-gray-600">Loading...</div>
    <div v-else-if="error" class="bg-rose-100 text-rose-800 px-4 py-3 rounded">
      {{ error }}
    </div>

    <section v-else class="bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm overflow-hidden">
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
            <span class="text-gray-500">Ship To:</span>
            <div class="itbms-shipping-address">
              {{ order.shippingAddress || "-" }}
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <div>
            <span class="text-gray-500">Buyer :</span>
            <span class="font-semibold itbms-nickname">
              {{ order.buyer?.nickname || "Buyer" }}
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
            <span class="font-semibold itbms-order-status">
              {{ order.orderStatus || "-" }}
            </span>
          </div>
          <div v-if="order.orderNote">
            <span class="text-gray-500">Note :</span>
            <span class="itbms-order-note">{{ order.orderNote }}</span>
          </div>
        </div>
      </div>

      <!-- Items -->
      <div class="divide-y divide-gray-100">
        <div v-for="s in order.sellers" :key="s.nickname" class="p-5">
          <div
            v-for="it in s.items"
            :key="(it.saleItemId ?? 0) + '-' + (it.model || '') + '-' + String(it.storageGb) + '-' + String(it.color)"
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

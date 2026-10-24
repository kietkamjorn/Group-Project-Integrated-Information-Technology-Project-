<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const isLoading = ref(true);
const error = ref(null);
const success = ref(route.query.success ? "Order action was successful." : null);
const orders = ref([]);

/* ========== Preview modal (เผื่อใช้ต่อ) ========== */
const previewOrder = ref({});
const showPreview = ref(false);

/* ========== Utils ========== */
const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png";

const fmt = (n) => (Number.isFinite(n) ? n.toLocaleString() : "-");

const fmtDate = (v) => {
  const d = v instanceof Date ? v : v ? new Date(v) : null;
  return d && !isNaN(d)
    ? new Intl.DateTimeFormat("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d)
    : "-";
};

/* ========== แปลง response จาก BE (มุมมอง Seller) ========== */
const normalizeOrders = (raw) => {
  const list = Array.isArray(raw?.content) ? raw.content : Array.isArray(raw) ? raw : [];

  return list
    .map((o) => {
      const id = o.id ?? o.orderId ?? undefined;
      const orderDate =
        o.orderDate ?? o.createdOn ?? o.createdDate ?? o.orderedOn ?? o.date ?? null;
      const paymentDate = o.paymentDate ?? o.paymentOn ?? o.paidOn ?? null;
      const status = o.orderStatus ?? o.status ?? null;
      const shipping = o.shippingAddress ?? o.shipTo ?? o.address ?? null;
      const note = o.orderNote ?? o.note ?? null;

      // Seller view: เก็บข้อมูล "ผู้ซื้อ" ไว้ใน schema.sellers[0]
      const buyerId = o.buyerId ?? o.buyer?.id ?? null;
      const buyerNickname = o.buyerNickname ?? o.nickname ?? o.buyerName ?? null;

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

        // NOTE: ใช้ชื่อ field เดิม (sellers) เพื่อให้ UI reuse ง่าย
        sellers: [{ sellerId: buyerId, nickname: buyerNickname || "Buyer", items }],
        total,
      };
    })
    .sort((a, b) => {
      const dateComparison = (b.createdOn?.getTime?.() || 0) - (a.createdOn?.getTime?.() || 0);
      if (dateComparison !== 0) return dateComparison;
      // ถ้าวันที่เท่ากัน ให้เรียงตาม id ล่าสุดก่อน
      return (Number(b.id) || 0) - (Number(a.id) || 0);
    });
};

/* ========== เติมรายละเอียดสินค้าทีหลัง (brand/model/รูป) ========== */
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
      }
    }
  }
};

/* ========== โหลดออเดอร์ฝั่ง Seller ========== */
const loadOrders = async () => {
  isLoading.value = true;
  error.value = null;

  let sellerId = null;
  try {
    const token = getAccessToken();
    sellerId = token ? decodeJwt(token)?.sub : null;
  } catch {}

  if (!sellerId) {
    error.value = "Seller ID not found. Please sign in as a seller.";
    isLoading.value = false;
    return;
  }

  try {
    const data = await withAuthFetch(`/v2/sellers/${sellerId}/orders`);
    orders.value = normalizeOrders(data);
    await hydrateOrders();
  } catch (err) {
    error.value = err?.message || "Cannot load sale orders.";
    orders.value = [];
  } finally {
    isLoading.value = false;
    if (success.value) setTimeout(() => (success.value = null), 3000);
  }
};

onMounted(() => {
  loadOrders();
});

/* ========== Tabs & Filters ========== */
const activeTab = ref("NEW");

// กลุ่ม NEW: รวม NEW/PENDING
const newOrdersFiltered = computed(() =>
  orders.value.filter((o) => {
    const st = (o.orderStatus || "").toUpperCase();
    return st === "NEW" || st === "PENDING";
  })
);

// badge ของ New = จำนวนจริง
const newCount = computed(() => newOrdersFiltered.value.length);

// กลุ่ม ALL: ยกเว้น CANCELED
const allOrdersFiltered = computed(() =>
  orders.value.filter((o) => (o.orderStatus || "").toUpperCase() !== "CANCELED")
);
const allCount = computed(() => allOrdersFiltered.value.length);

// กลุ่ม CANCELED
const canceledCount = computed(
  () => orders.value.filter((o) => (o.orderStatus || "").toUpperCase() === "CANCELED").length
);

// รายการที่จะแสดงตาม tab
const filteredOrders = computed(() => {
  const tab = activeTab.value;
  switch (tab) {
    case "NEW": {
      const list = newOrdersFiltered.value;
      // แสดงออเดอร์ใหม่ล่าสุดแค่อันเดียว (ตามสเปคที่ให้ไว้ก่อนหน้า)
      return list.length > 0 ? [list[0]] : [];
    }
    case "ALL":
      return allOrdersFiltered.value;
    case "CANCELED":
      return orders.value.filter((o) => (o.orderStatus || "").toUpperCase() === "CANCELED");
    default:
      return [];
  }
});
</script>

<template>
  <div class="w-full px-4 sm:px-6 lg:px-10 py-8 not-prose">
    <h1 class="text-2xl sm:text-3xl font-semibold text-gray-900 mb-6">
      Sale Orders
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

    <div class="itbms-tabs mb-4 flex items-center gap-6 text-gray-500">
      <button
        class="itbms-tab-new relative pb-2"
        :class="activeTab === 'NEW' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'"
        @click="activeTab = 'NEW'"
      >
        New Orders
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ newCount }}</span>
        <span
          v-if="activeTab === 'NEW'"
          class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"
        ></span>
      </button>

      <button
        class="itbms-tab-all relative pb-2"
        :class="activeTab === 'ALL' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'"
        @click="activeTab = 'ALL'"
      >
        All Orders
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ allCount }}</span>
        <span
          v-if="activeTab === 'ALL'"
          class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"
        ></span>
      </button>

      <button
        class="itbms-tab-canceled relative pb-2"
        :class="
          activeTab === 'CANCELED' ? 'text-gray-900 font-semibold' : 'hover:text-gray-800'
        "
        @click="activeTab = 'CANCELED'"
      >
        Canceled Orders
        <span class="ml-2 text-xs bg-gray-100 px-2 py-0.5 rounded">{{ canceledCount }}</span>
        <span
          v-if="activeTab === 'CANCELED'"
          class="absolute left-0 right-0 -bottom-px h-0.5 bg-gray-900"
        ></span>
      </button>
    </div>

    <div v-if="isLoading" class="text-gray-600">Loading...</div>

    <section v-if="false"> </section>

    <div
      v-else-if="!filteredOrders.length"
      class="bg-white rounded-2xl ring-1 ring-gray-100 p-10 text-center text-gray-600 shadow-sm"
    >
      No {{ activeTab === "NEW" ? "new" : activeTab.toLowerCase() }} orders.
    </div>

    <div v-else class="space-y-6">
      <section
        v-for="o in filteredOrders"
        :key="o.id"
        class="itbms-row bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm hover:ring-indigo-300 transition-all cursor-pointer"
        @click.stop="$router.push({ name: 'SaleOrderDetail', params: { orderId: o.id } })"
      >
        <div class="p-5 border-b border-gray-100">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="flex items-center gap-2">
              <span class="inline-flex items-center justify-center w-6 h-6 rounded bg-gray-800 text-white text-xs"
                >B</span
              >
              <div class="font-semibold text-gray-900 itbms-nickname">
                {{ o.sellers?.[0]?.nickname || "Buyer" }}
              </div>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-4 gap-x-8 gap-y-2 text-sm">
              <div>
                <div class="text-gray-500">Order No:</div>
                <div class="font-semibold itbms-order-id">
                  {{ o.id ?? "-" }}
                </div>
              </div>
              <div>
                <div class="text-gray-500">Order Date:</div>
                <div class="font-semibold itbms-order-date">
                  {{ fmtDate(o.createdOn) }}
                </div>
              </div>
              <div>
                <div class="text-gray-500">Payment Date:</div>
                <div class="font-semibold itbms-payment-date">
                  {{ o.paymentOn ? fmtDate(o.paymentOn) : "-" }}
                </div>
              </div>
              <div>
                <div class="text-gray-500">Total:</div>
                <div class="font-semibold text-lg itbms-total-order-price">
                  Baht {{ fmt(o.total) }}
                </div>
              </div>
              <div class="md:col-span-1">
                <div class="text-gray-500">Status:</div>
                <div class="font-semibold itbms-order-status">
                  {{ o.orderStatus || "-" }}
                </div>
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
                <div class="text-sm text-gray-500 itbms-item-quantity">
                  Qty {{ it.qty }}
                </div>
              </div>
              <div class="w-40 text-right">
                <div class="text-sm text-gray-500">Price:</div>
                <div class="text-lg font-semibold text-gray-900 itbms-item-total-price">
                  Baht {{ fmt((it.qty || 0) * (it.unitPrice || 0)) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

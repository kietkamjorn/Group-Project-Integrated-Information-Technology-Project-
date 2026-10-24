<!-- src/views/cart.vue -->
<script setup>
import { reactive, ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const router = useRouter();
const shippingAddress = ref("");
const orderNote = ref("");

// centralize user-facing messages (EN)
const MSG = {
  healSkippedPrefix:
    "Heads up:\nSome items are missing seller information and were skipped.\nPlease remove and re-add these items to the cart:\n",
  selectAtLeastOne: "Please select at least one item.",
  buyerMissing: "Cannot place order: buyerId not found. Please sign in again.",
  needAddress: "Please fill your shipping address.",
  missingSellerPrefix:
    "Some items still have no seller information and will not be ordered:\n",
  someFailedPrefix: "Some orders failed:\n",
};

/* ===================== CART STATE (localStorage) ===================== */
const CART_VERSION = "v1";
const getCartKey = () => {
  const token = getAccessToken?.();
  const uid = token ? decodeJwt(token)?.sub : "guest";
  return `cart:${CART_VERSION}:${uid}`;
};

const state = reactive({
  groups: [], // [{ sellerId, sellerNickname, checked, items:[{ saleItemId, brandName, model, color, storageGb, ramGb, unitPrice, availableQty, qty, checked, image }] }]
  confirmOpen: false,
  pendingRemove: { sellerId: null, saleItemId: null },
});

const readCart = () => {
  try {
    const raw = localStorage.getItem(getCartKey());
    return raw ? JSON.parse(raw) : { groups: [] };
  } catch {
    return { groups: [] };
  }
};

const persist = () => {
  localStorage.setItem(getCartKey(), JSON.stringify({ groups: state.groups }));
  try {
    window.dispatchEvent(new Event("itbms:cart-updated"));
  } catch {}
};

/* ===== Auth probe: กระตุ้น refresh หาก access token หมดอายุ ===== */
const ensureFreshAuth = async () => {
  try {
    const t = getAccessToken?.();
    const me = t ? decodeJwt(t) : null;
    if (me?.sub) {
      // endpoint เบาๆ ที่ต้อง auth → ถ้า access หมดอายุ จะ 401 แล้ว withAuthFetch จะ refresh ให้
      await withAuthFetch(`/v2/users/${me.sub}`, { method: "GET", cache: "no-store" });
    }
  } catch {
    // ไม่ต้องทำอะไร: withAuthFetch จะจัดการ refresh (หรือ fail แล้วส่วนกลางรับ event ต่อ)
  }
};

onMounted(() => {
  ensureFreshAuth(); // 🔸 กระตุ้น flow 401→refresh ทันทีที่เข้า Cart
  const data = readCart();
  state.groups = Array.isArray(data.groups) ? data.groups : [];
  loadPrefs();
});

watch(shippingAddress, () => savePrefsDebounced());
watch(orderNote, () => savePrefsDebounced());

/* ===== Default address & note (per-user) ===== */
const PREFS_VERSION = "v1";
const getPrefsKey = () => {
  const token = getAccessToken?.();
  const uid = token ? decodeJwt(token)?.sub : "guest";
  return `cart:prefs:${PREFS_VERSION}:${uid}`;
};

const loadPrefs = () => {
  try {
    const raw = localStorage.getItem(getPrefsKey());
    if (!raw) return;
    const p = JSON.parse(raw);
    if (typeof p?.shippingAddress === "string")
      shippingAddress.value = p.shippingAddress;
    if (typeof p?.orderNote === "string") orderNote.value = p.orderNote;
  } catch {}
};

const savePrefs = () => {
  try {
    const data = {
      shippingAddress: String(shippingAddress.value || "").trim(),
      orderNote: String(orderNote.value || "").trim(),
    };
    localStorage.setItem(getPrefsKey(), JSON.stringify(data));
  } catch {}
};

// debounce helper
const debounce = (fn, ms = 300) => {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
};
const savePrefsDebounced = debounce(savePrefs, 300);

/* ===================== HELPERS / COMPUTEDS ===================== */
const fmt = (n) => (Number.isFinite(n) ? n.toLocaleString() : "-");
const lineTotal = (it) => (Number(it.qty) || 0) * (Number(it.unitPrice) || 0);
const subTotalOfGroup = (g) =>
  g.items.filter((i) => i.checked).reduce((s, it) => s + lineTotal(it), 0);

const grandTotal = computed(() =>
  state.groups.reduce((sum, g) => sum + subTotalOfGroup(g), 0)
);

const selectedCount = computed(() =>
  state.groups.reduce(
    (c, g) => c + g.items.filter((i) => i.checked && i.qty > 0).length,
    0
  )
);

const totalSelectedQty = computed(() =>
  state.groups.reduce(
    (sum, g) =>
      sum +
      g.items
        .filter((i) => i.checked)
        .reduce((s, it) => s + (Number(it.qty) || 0), 0),
    0
  )
);

const anySelected = computed(() => selectedCount.value > 0);

/* ---- Select all (two-way) ---- */
const selectAll = computed({
  get() {
    if (!state.groups.length) return false;
    return state.groups.every(
      (g) => g.checked && g.items.every((i) => i.checked)
    );
  },
  set(checked) {
    state.groups.forEach((g) => {
      g.checked = !!checked;
      g.items.forEach((i) => (i.checked = !!checked));
    });
    persist();
  },
});

// disable place if address empty / nothing selected / placing
const hasAddress = computed(
  () => String(shippingAddress.value || "").trim().length > 0
);
const isPlacing = ref(false);
const isDisabledPlaceOrder = computed(
  () => !anySelected.value || !hasAddress.value || isPlacing.value
);

/* ---- Toggle group / item ---- */
const toggleGroup = (g, checked) => {
  g.checked = !!checked;
  g.items.forEach((i) => (i.checked = g.checked));
  persist();
};
const toggleItem = (g) => {
  g.checked = g.items.every((i) => i.checked);
  persist();
};

/* ---- Qty controls ---- */
const clampQty = (it) => {
  const max = Number(it.availableQty) || 1;
  let q = Number(it.qty) || 0;
  if (q < 1) q = 1;
  if (q > max) q = max;
  it.qty = q;
  persist();
};
const inc = (it) => {
  it.qty = Math.min((Number(it.qty) || 0) + 1, Number(it.availableQty) || 1);
  persist();
};
const dec = (sellerId, it) => {
  const q = Number(it.qty) || 0;
  if (q <= 1) {
    openRemove(sellerId, it);
  } else {
    it.qty = q - 1;
    persist();
  }
};

/* ---- Remove modal ---- */
const openRemove = (sellerId, it) => {
  state.pendingRemove = { sellerId, saleItemId: it.saleItemId };
  state.confirmOpen = true;
};
const cancelRemove = () => {
  state.confirmOpen = false;
  state.pendingRemove = { sellerId: null, saleItemId: null };
};
const confirmRemove = () => {
  const { sellerId, saleItemId } = state.pendingRemove;
  const gIdx = state.groups.findIndex(
    (g) => Number(g.sellerId) === Number(sellerId)
  );
  if (gIdx >= 0) {
    state.groups[gIdx].items = state.groups[gIdx].items.filter(
      (x) => Number(x.saleItemId) !== Number(saleItemId)
    );
    if (!state.groups[gIdx].items.length) state.groups.splice(gIdx, 1);
  }
  persist();
  cancelRemove();
};

/* ---- Navigation ---- */
const goShopping = () => router.push("/sale-items");

/* ===================== PLACE ORDER ===================== */
const buildOrderPayloads = () => {
  const token = getAccessToken?.();
  const buyerId = token ? decodeJwt(token)?.sub : null;

  const shipping = String(shippingAddress.value || "").trim();
  const orderNoteVal = String(orderNote.value || "").trim() || null;

  const payloads = state.groups.map((g) => {
    const orderItems = g.items
      .filter((it) => !!it.checked && Number(it.qty) > 0)
      .map((it) => ({
        saleItemId: Number(it.saleItemId),
        price: Number(it.unitPrice),
        quantity: Number(it.qty),
      }));

    return {
      buyerId,
      sellerId: g.sellerId ?? null,
      shippingAddress: shipping,
      orderNote: orderNoteVal,
      orderItems,
    };
  });

  return payloads.filter((p) => p.orderItems.length > 0);
};

// normalize price (remove commas / spaces)
const normalizeUnitPrice = (v) => {
  const n = Number(String(v ?? 0).replace(/[, ]+/g, "").trim());
  return Number.isFinite(n) ? n : 0;
};

/** heal cart: ensure sellerId per item and regroup by sellerId */
const healCart = async () => {
  const data = readCart();
  const groupsMap = new Map();
  const saleItemCache = new Map();
  const removed = [];

  const getDetail = async (saleItemId) => {
    if (saleItemCache.has(saleItemId)) return saleItemCache.get(saleItemId);

    let d = null;
    try {
      d = await withAuthFetch(`/v2/sale-items/${saleItemId}`);
    } catch {
      try {
        d = await withAuthFetch(`/v1/sale-items/${saleItemId}`);
      } catch {}
    }

    const sid =
      d?.sellerId ?? d?.seller?.id ?? d?.sellerID ?? d?.ownerId ?? null;
    const sn =
      d?.sellerNickname ?? d?.seller?.nickname ?? d?.sellerName ?? null;
    const v = { sid, sn };
    saleItemCache.set(saleItemId, v);
    return v;
  };

  for (const g of Array.isArray(data.groups) ? data.groups : []) {
    for (const it of Array.isArray(g.items) ? g.items : []) {
      it.unitPrice = normalizeUnitPrice(it.unitPrice);

      let sid = it.sellerId ?? g.sellerId ?? null;
      let sn = g.sellerNickname ?? "Seller";

      if (sid == null) {
        const info = await getDetail(Number(it.saleItemId));
        sid = info.sid;
        sn = info.sn ?? sn;
      }

      if (sid == null) {
        removed.push(it.saleItemId);
        continue;
      }

      const key = String(sid);
      if (!groupsMap.has(key)) {
        groupsMap.set(key, {
          sellerId: sid,
          sellerNickname: sn ?? "Seller",
          checked: true,
          items: [],
        });
      }
      groupsMap.get(key).items.push(it);
    }
  }

  state.groups = Array.from(groupsMap.values());
  persist();

  return removed;
};

const resolveMissingSellerIds = async (payloads) => {
  for (const p of payloads) {
    if (p.sellerId != null) continue;
    const first = p.orderItems?.[0];
    if (!first) continue;

    try {
      const detail = await withAuthFetch(`/v2/sale-items/${first.saleItemId}`);
      const sid =
        detail?.sellerId ??
        detail?.seller?.id ??
        detail?.sellerID ??
        detail?.ownerId ??
        null;
      if (sid != null) p.sellerId = Number(sid);
    } catch {}
  }
  return payloads;
};

// preview builder (kept for future usage)
const buildPreviewFromState = () => {
  const sellers = state.groups
    .filter((g) => g.items.some((it) => it.checked && Number(it.qty) > 0))
    .map((g) => {
      const items = g.items
        .filter((it) => it.checked && Number(it.qty) > 0)
        .map((it) => ({
          brandName: it.brandName ?? "",
          model: it.model ?? "",
          color: it.color ?? null,
          storageGb: it.storageGb ?? null,
          ramGb: it.ramGb ?? null,
          qty: Number(it.qty) || 0,
          unitPrice: Number(it.unitPrice) || 0,
          image:
            it.image ||
            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png",
        }));
      return {
        nickname: g.sellerNickname || "Seller",
        items,
      };
    });

  const total = sellers.reduce(
    (sum, s) =>
      sum +
      s.items.reduce((a, it) => a + (it.qty || 0) * (it.unitPrice || 0), 0),
    0
  );

  return {
    id: null,
    createdOn: new Date().toISOString(),
    paymentOn: new Date().toISOString(),
    status: "Completed",
    shippingAddress: String(shippingAddress.value || "").trim(),
    note: String(orderNote.value || "").trim() || null,
    sellers,
    total,
  };
};

const savePreviewAndGetKey = (preview) => {
  const key = `ord-${Date.now()}`;
  try {
    sessionStorage.setItem(key, JSON.stringify(preview));
  } catch {}
  return key;
};

const placeOrder = async () => {
  if (isPlacing.value) return;

  // 1) heal cart
  const unresolved = await healCart();
  if (unresolved.length) {
    alert(
      MSG.healSkippedPrefix +
        unresolved.map((id) => `- saleItemId: ${id}`).join("\n")
    );
    // proceed with resolvable items
  }

  // 2) build payloads
  let payloads = buildOrderPayloads();
  payloads = await resolveMissingSellerIds(payloads);

  // 3) FE validations
  if (!payloads.length) {
    alert(MSG.selectAtLeastOne);
    return;
  }
  if (!payloads[0].buyerId) {
    alert(MSG.buyerMissing);
    router.push("/signin");
    return;
  }
  if (!payloads[0].shippingAddress) {
    alert(MSG.needAddress);
    return;
  }
  if (payloads.some((p) => p.sellerId == null)) {
    const missing = payloads.filter((p) => p.sellerId == null);
    alert(
      MSG.missingSellerPrefix +
        missing
          .flatMap(
            (p) =>
              p.orderItems?.map((i) => `- saleItemId: ${i.saleItemId}`) || []
          )
          .join("\n")
    );
  }

  // 4) send orders per seller
  isPlacing.value = true;
  const failed = [];
  try {
    for (const p of payloads.filter((x) => x.sellerId != null)) {
      try {
        await withAuthFetch("/v2/orders", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(p),
        });
      } catch (err) {
        const msg =
          (typeof err?.response?.data === "string"
            ? err.response.data
            : err?.response?.data?.message) ||
          (Array.isArray(err?.response?.data?.errors)
            ? err.response.data.errors.map((e) => e.message).join(", ")
            : "") ||
          err?.message ||
          "Unknown error";
        failed.push({ sellerId: p.sellerId, message: msg });
      }
    }

    // 5) remove success-placed items from cart
    const successSellerIds = new Set(
      payloads
        .filter(
          (p) =>
            p.sellerId != null && !failed.find((f) => f.sellerId === p.sellerId)
        )
        .map((p) => String(p.sellerId))
    );

    state.groups = state.groups
      .map((g) => {
        if (!successSellerIds.has(String(g.sellerId))) return g;
        return { ...g, items: g.items.filter((i) => !i.checked) };
      })
      .filter((g) => g.items.length > 0);
    persist();

    if (failed.length) {
      alert(
        MSG.someFailedPrefix +
          failed.map((f) => `Seller ${f.sellerId ?? "-"}: ${f.message}`).join("\n")
      );
    } else {
      const preview = buildPreviewFromState();
      const previewKey = savePreviewAndGetKey(preview);
      // previewKey พร้อมใช้ ถ้าต้องการบนหน้า your-orders
      router.push({ path: "/your-orders", query: { success: "true" } });
    }
  } finally {
    isPlacing.value = false;
  }
};

watch(
  () => getAccessToken?.(),
  async () => {
    await ensureFreshAuth(); 
    const data = readCart();
    state.groups = Array.isArray(data.groups) ? data.groups : [];
  },
  { immediate: true }
);
</script>


<template>
  <div class="w-full px-4 sm:px-6 lg:px-10 py-8 not-prose">
    <!-- Title -->
    <div class="mb-6 flex items-end justify-between gap-4 pb-4">
      <div>
        <h1 class="text-2xl sm:text-3xl font-semibold text-gray-900">
          Your Cart
        </h1>
      </div>
    </div>

    <!-- Empty -->
    <div
      v-if="!state.groups.length"
      class="bg-white rounded-2xl ring-1 ring-gray-100 p-10 text-center text-gray-600 shadow-sm"
    >
      <img
        class="mx-auto mb-4 w-24 h-24 opacity-70"
        alt="Empty"
        src="https://cdn-icons-png.flaticon.com/512/2038/2038854.png"
      />
      <p class="text-lg mb-1">Your cart is empty.</p>
      <p class="text-sm text-gray-500">
        Find great deals and add them to your cart.
      </p>
      <button
        class="mt-5 inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 text-white hover:bg-blue-700"
        @click="goShopping"
      >
        Go Shopping
      </button>
    </div>

    <!-- Cart -->
    <div v-else class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <!-- Left: list -->
      <div class="xl:col-span-2 space-y-6">
        <!-- Select all -->
        <div
          class="bg-white rounded-2xl ring-1 ring-gray-100 p-4 flex items-center justify-between shadow-sm"
        >
          <label
            class="inline-flex items-center gap-3 cursor-pointer select-none"
          >
            <input
              id="chk-all"
              type="checkbox"
              class="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              v-model="selectAll"
            />
            <span class="text-gray-800 font-medium">Select all</span>
          </label>
          <div class="text-sm text-gray-500">
            Selected items:
            <span class="font-semibold text-gray-900">{{ selectedCount }}</span>
          </div>
        </div>

        <!-- Groups -->
        <section
          v-for="g in state.groups"
          :key="g.sellerId"
          class="bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm overflow-hidden"
        >
          <!-- Header -->
          <div
            class="flex items-center justify-between p-4 sm:p-5 border-b border-gray-100 bg-gray-50/60"
          >
            <div class="flex items-center gap-3">
              <input
                :id="`seller-${g.sellerId}`"
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                :checked="g.checked"
                @change="toggleGroup(g, $event.target.checked)"
              />
              <label
                :for="`seller-${g.sellerId}`"
                class="font-medium text-gray-900 flex items-center gap-2"
              >
                <span
                  class="inline-flex items-center justify-center w-6 h-6 rounded-full bg-blue-600 text-white text-xs"
                  >S</span
                >
                {{ g.sellerNickname || "Seller" }}
              </label>
            </div>
            <div class="text-sm text-gray-500">
              Subtotal (selected):
              <span class="font-semibold text-gray-900">{{
                fmt(subTotalOfGroup(g))
              }}</span>
            </div>
          </div>

          <!-- Items -->
          <ul class="divide-y divide-gray-100">
            <li
              v-for="it in g.items"
              :key="it.saleItemId"
              class="p-4 sm:p-5 flex flex-col md:flex-row gap-4 md:gap-6"
            >
              <!-- Left: checkbox + image + meta -->
              <div class="flex items-start gap-3 md:w-1/2">
                <input
                  :id="`it-${g.sellerId}-${it.saleItemId}`"
                  type="checkbox"
                  class="mt-2 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  v-model="it.checked"
                  @change="toggleItem(g)"
                />
                <img
                  :src="
                    it.image ||
                    'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/240px-No_picture_available.png'
                  "
                  alt="thumb"
                  class="w-20 h-20 object-cover rounded-xl ring-1 ring-gray-200 bg-gray-50"
                />
                <label :for="`it-${g.sellerId}-${it.saleItemId}`" class="block">
                  <div class="text-gray-900 font-semibold leading-6">
                    {{ it.brandName || "-" }}
                    <span class="text-gray-400">·</span> {{ it.model || "-" }}
                  </div>
                  <div
                    class="text-gray-500 text-sm mt-0.5 flex flex-wrap gap-x-2"
                  >
                    <span v-if="it.ramGb">RAM {{ it.ramGb }}GB</span>
                    <span v-if="it.storageGb" class="text-gray-300">|</span>
                    <span v-if="it.storageGb">{{ it.storageGb }}GB</span>
                    <span v-if="it.color" class="text-gray-300">|</span>
                    <span v-if="it.color">{{ it.color }}</span>
                  </div>
                  <div class="text-sm text-gray-500 mt-2">
                    Price:
                    <span class="font-medium text-gray-900">{{
                      fmt(it.unitPrice)
                    }}</span>
                  </div>
                </label>
              </div>

              <!-- Right: qty + totals + remove -->
              <div
                class="md:flex-1 grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 items-center gap-3 md:gap-4"
              >
                <div
                  class="col-span-2 sm:col-span-1 md:col-span-2 flex items-center justify-start md:justify-center text-sm"
                >
                  <span class="text-gray-500">Available:&nbsp;</span>
                  <span class="font-medium text-gray-900">{{
                    it.availableQty
                  }}</span>
                </div>

                <div
                  class="col-span-2 sm:col-span-2 md:col-span-2 flex items-center justify-start md:justify-center"
                >
                  <div
                    class="inline-flex items-center rounded-full ring-1 ring-gray-200 overflow-hidden"
                  >
                    <button
                      type="button"
                      class="px-3 py-2 text-gray-700 hover:bg-gray-50"
                      @click="dec(g.sellerId, it)"
                    >
                      −
                    </button>
                    <input
                      class="w-16 text-center outline-none py-2 text-gray-900"
                      type="number"
                      min="1"
                      :max="it.availableQty"
                      v-model.number="it.qty"
                      @change="clampQty(it)"
                    />
                    <button
                      type="button"
                      class="px-3 py-2 text-gray-700 hover:bg-gray-50"
                      @click="inc(it)"
                      :disabled="it.qty >= it.availableQty"
                    >
                      +
                    </button>
                  </div>
                </div>

                <div class="col-span-1 text-right md:text-center">
                  <div class="text-xs text-gray-500">Total</div>
                  <div class="text-lg font-semibold text-gray-900 tabular-nums">
                    {{ fmt(lineTotal(it)) }}
                  </div>
                </div>

                <div class="col-span-1 md:text-right">
                  <button
                    class="text-rose-600 hover:text-rose-700 font-medium"
                    @click="openRemove(g.sellerId, it)"
                  >
                    Remove
                  </button>
                </div>
              </div>
            </li>
          </ul>
        </section>
      </div>

      <!-- Right: summary -->
      <aside class="space-y-6">
        <div
          class="bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm p-5 sticky top-6"
        >
          <h2 class="text-lg font-semibold text-gray-900 mb-3">Cart Summary</h2>

          <!-- Ship To -->
          <div class="mb-4">
            <h3 class="text-base font-semibold text-gray-900 mb-2">Ship To</h3>

            <label class="block text-sm text-gray-600 mb-1"
              >Address
              <span class="text-gray-400"
                >(Address No, Street, Subdistrict, District, Province, Postal
                Code)</span
              ></label
            >
            <textarea
              v-model="shippingAddress"
              rows="3"
              class="itbms-shipping-address w-full rounded-lg border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
              placeholder="123/45 Moo 6, T. Bangna, A. Bangna, Bangkok 10260"
            ></textarea>

            <label class="block text-sm text-gray-600 mt-4 mb-1">Note</label>
            <textarea
              v-model="orderNote"
              rows="2"
              class="itbms-order-note w-full rounded-lg border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
              placeholder="Additional instructions or requests"
            ></textarea>
          </div>

          <!-- Totals -->
          <div class="mt-4 border-t pt-4 text-sm">
            <div class="flex justify-between mb-1">
              <span class="text-gray-600">Total items:</span>
              <span class="itbms-total-order-items font-semibold text-gray-900">
                {{ totalSelectedQty }}
              </span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-600">Total price:</span>
              <span class="itbms-total-order-price font-semibold text-gray-900">
                Baht {{ fmt(grandTotal) }}
              </span>
            </div>
          </div>

          <!-- Actions -->
          <div class="mt-4 flex flex-col gap-3">
            <button
              class="itbms-place-order-button mt-4 w-full inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl text-white font-medium transition"
              :class="[
                isDisabledPlaceOrder
                  ? 'bg-emerald-600 opacity-50 cursor-not-allowed'
                  : 'bg-emerald-600 hover:bg-emerald-700',
              ]"
              :disabled="isDisabledPlaceOrder"
              :aria-disabled="isDisabledPlaceOrder ? 'true' : 'false'"
              @click="placeOrder"
            >
              <span>{{ isPlacing ? "Placing..." : "Place Order" }}</span>
            </button>

            <button
              v-if="state.groups.length"
              class="mt-4 mb-6 w-full inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-blue-500 text-white font-medium hover:bg-blue-700 disabled:opacity-50 transition"
              @click="$router.push('/sale-items')"
            >
              Continue Shopping
            </button>
          </div>

          <div class="h-2"></div>
        </div>

        <div class="bg-white rounded-2xl ring-1 ring-gray-100 shadow-sm p-5">
          <p class="text-sm text-gray-500">
            You can select sellers or individual items using the checkboxes.
            Totals reflect only the selected items.
          </p>
        </div>
      </aside>
    </div>

    <!-- Confirm Modal -->
    <div
      v-if="state.confirmOpen"
      class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
    >
      <div
        class="bg-white w-full max-w-md rounded-2xl shadow-xl ring-1 ring-gray-100 p-6"
      >
        <h3 class="text-xl font-semibold text-gray-900">Remove item</h3>
        <p class="mt-2 text-gray-600 itbms-message">
          Do you want to remove the sale item from the cart?
        </p>
        <div class="mt-6 flex justify-end gap-3">
          <button
            class="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50"
            @click="cancelRemove"
          >
            Cancel
          </button>
          <button
            class="px-4 py-2 rounded-lg bg-rose-600 text-white hover:bg-rose-700"
            @click="confirmRemove"
          >
            Confirm
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

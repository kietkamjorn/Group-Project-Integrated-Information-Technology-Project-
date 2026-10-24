<script setup>
import { ref, onMounted, computed, onActivated, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";
import Pagination from "@/components/Pagination.vue";

const route = useRoute();
const router = useRouter();

const currentPage = ref(1);
const itemsPerPage = ref(10);
const products = ref([]);
const isLoading = ref(true);

const showConfirmDialog = ref(false);
const saleItemToDeleteId = ref(null);

const successMessage = ref(null);
const deleteSuccess = ref(null);
const deleteErrorMessage = ref(route.query.errorMessage || null);

/* helpers */

const ensureFreshAuth = async () => {
  try {
    const t = getAccessToken?.();
    const me = t ? decodeJwt(t) : null;
    if (me?.sub) {
      await withAuthFetch(`/v2/users/${me.sub}`, { method: "GET", cache: "no-store" });
    }
  } catch {
  }
};

const getStatusCode = (err) => {
  const direct = err?.response?.status;
  if (Number.isFinite(direct)) return direct;
  const msg = String(err?.message ?? "");
  const m = msg.match(/\b\d{3}\b/);
  return m ? Number(m[0]) : undefined;
};

const mySellerId = () => {
  try {
    const token = getAccessToken?.();
    const me = token ? decodeJwt(token) : null;
    return me?.sub ?? null;
  } catch {
    return null;
  }
};

/* pagination */
const totalPages = computed(() =>
  Math.max(1, Math.ceil(products.value.length / itemsPerPage.value))
);

const paginatedProducts = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage.value;
  return products.value.slice(start, start + itemsPerPage.value);
});

/* data */
const loadProducts = async () => {
  isLoading.value = true;
  try {
    const sid = mySellerId();
    const url = sid != null
      ? `/v2/sellers/${sid}/sale-items`
      : `/v2/sale-items?page=0&size=1000&sortField=id&sortDirection=asc`;
    const data = await withAuthFetch(url, { method: "GET", cache: "no-store" });
    const items = Array.isArray(data)
      ? data
      : Array.isArray(data?.content)
        ? data.content
        : [];

    products.value = items
      .slice()
      .sort((a, b) => (Number(a.id) || 0) - (Number(b.id) || 0));

    const total = Math.max(1, Math.ceil(products.value.length / itemsPerPage.value));
    if (currentPage.value > total) currentPage.value = total;
    if (currentPage.value < 1) currentPage.value = 1;
  } catch {
    deleteErrorMessage.value = "Failed to load sale items. Please try again.";
  } finally {
    isLoading.value = false;
  }
};

/* delete */
const confirmDelete = async () => {
  try {
    const id = saleItemToDeleteId.value;
    const sid = mySellerId();
    if (sid == null) {
      router.replace({ path: "/signin", query: { redirect: "/sale-items/list" } });
      return;
    }

    try {
      await withAuthFetch(`/v2/sellers/${sid}/sale-items/${id}`, { method: "DELETE" });
    } catch (err) {
      const st  = getStatusCode(err);
      const msg = (typeof err?.response?.data === "string"
                    ? err.response.data
                    : err?.response?.data?.message) || "";
      if (st === 405 || /not supported|method not allowed/i.test(msg)) {
        await withAuthFetch(`/v2/sellers/${sid}/sale-items/${id}`, {
          method: "POST",
          headers: { "X-HTTP-Method-Override": "DELETE" },
        });
      } else {
        throw err;
      }
    }

    deleteSuccess.value = "The sale item has been deleted.";
    products.value = products.value.filter((item) => item.id !== id);
    showConfirmDialog.value = false;
  } catch (err) {
    const st  = getStatusCode(err);
    const msg = (typeof err?.response?.data === "string"
                  ? err.response.data
                  : err?.response?.data?.message) || err?.message || "";
    deleteErrorMessage.value =
      st === 403 ? "You are not authorized to delete this item."
    : st === 404 ? "The requested sale item does not exist."
    : "Delete failed: " + msg;
    showConfirmDialog.value = false;
  }
};

const showDeleteDialog = (id) => {
  saleItemToDeleteId.value = id;
  showConfirmDialog.value = true;
};

/* lifecycle */
onMounted(async () => {
  if (route.query.success === "true") {
    successMessage.value = "The sale item has been successfully added.";
    setTimeout(() => (successMessage.value = null), 3000);
  }

  if (route.query.errorMessage) {
    deleteErrorMessage.value = String(route.query.errorMessage);
    setTimeout(() => (deleteErrorMessage.value = null), 5000);
    const q = { ...route.query };
    delete q.errorMessage;
    router.replace({ query: q });
  }

  const token = getAccessToken();
  if (!token) {
    router.replace({ path: "/signin", query: { redirect: "/sale-items/list" } });
    return;
  }

  await ensureFreshAuth();
  await loadProducts();
});
onActivated(async () => {
  await ensureFreshAuth();
  await loadProducts();
});
const onAuthRefreshed = async () => {
  await loadProducts();
};
onMounted(() => window.addEventListener("itbms:auth-changed", onAuthRefreshed));
onBeforeUnmount(() => window.removeEventListener("itbms:auth-changed", onAuthRefreshed));

</script>

<template>
  <div class="w-full px-4 py-5 not-prose">
    <div
      v-if="successMessage"
      class="mb-4 bg-green-100 text-green-700 px-4 py-3 rounded shadow itbms-message"
      aria-live="polite"
    >
      {{ successMessage }}
    </div>

    <div
      v-if="deleteErrorMessage"
      class="mb-8 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message"
      aria-live="polite"
    >
      {{ deleteErrorMessage }}
    </div>

    <div v-if="isLoading" class="text-center text-gray-600">Loading...</div>

    <!-- header actions -->
    <div v-else class="w-full max-w-full flex justify-between mb-4 pb-4 mt-6">
      <router-link
        to="/sale-items/add"
        class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition itbms-sale-item-add"
      >
        Add Sale Item
      </router-link>
      <router-link
        to="/brands"
        class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition itbms-manage-brand"
      >
        Manage Brand
      </router-link>
    </div>

    <div v-if="!isLoading" class="flex flex-col items-center">
      <div class="w-full rounded-2xl ring-1 ring-blue-100 bg-white shadow overflow-hidden">
        <div class="overflow-x-auto">
          <!-- เพิ่มคอลัมน์ Quantity (เพิ่ม colgroup + header + cell) -->
          <table class="min-w-[1040px] w-full text-sm leading-6">
            <colgroup>
              <col style="width: 72px" />
              <col style="width: 14%" />
              <col style="width: 22%" />
              <col style="width: 10%" />
              <col style="width: 10%" />
              <col style="width: 12%" />
              <col style="width: 10%" />  <!-- Price -->
              <col style="width: 10%" />  <!-- Quantity (NEW) -->
              <col style="width: 10%" />  <!-- Action -->
            </colgroup>

            <thead>
              <tr>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-left  px-5 py-3 text-xs font-semibold uppercase tracking-wide first:rounded-tl-2xl">Id</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-left  px-5 py-3 text-xs font-semibold uppercase tracking-wide">Brand</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-left  px-5 py-3 text-xs font-semibold uppercase tracking-wide">Model</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide">Ram</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide">Storage</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-left  px-5 py-3 text-xs font-semibold uppercase tracking-wide">Color</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide">Price</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide">Quantity</th>
                <th class="sticky top-0 z-10 bg-blue-600 text-white text-center px-5 py-3 text-xs font-semibold uppercase tracking-wide last:rounded-tr-2xl">Action</th>
              </tr>
            </thead>

            <tbody class="divide-y divide-blue-50 text-gray-900">
              <tr v-if="products.length === 0">
                <td colspan="9" class="px-5 py-6 text-center text-black">
                  No Sale Item
                </td>
              </tr>

              <tr
                v-else
                v-for="product in paginatedProducts"
                :key="product.id"
                class="group itbms-row hover:bg-blue-50 transition-colors"
              >
                <td class="px-5 py-2.5 text-right tabular-nums itbms-id whitespace-nowrap">
                  {{ product.id }}
                </td>
                <td class="px-5 py-2.5 itbms-brand whitespace-nowrap">
                  {{ product.brandName }}
                </td>
                <td class="px-5 py-2.5 itbms-model">
                  {{ product.model }}
                </td>
                <td class="px-5 py-2.5 text-right tabular-nums itbms-ramGb whitespace-nowrap">
                  {{ product.ramGb ?? "-" }}
                </td>
                <td class="px-5 py-2.5 text-right tabular-nums itbms-storageGb whitespace-nowrap">
                  {{ product.storageGb ?? "-" }}
                </td>
                <td class="px-5 py-2.5 itbms-color whitespace-nowrap">
                  {{ product.color ?? "-" }}
                </td>
                <td class="px-5 py-2.5 text-right tabular-nums font-medium itbms-price whitespace-nowrap">
                  {{ product.price?.toLocaleString?.() ?? product.price }}
                </td>
                <!-- NEW: Quantity -->
                <td class="px-5 py-2.5 text-right tabular-nums itbms-quantity whitespace-nowrap">
                  {{ product.quantity ?? product.available ?? product.qty ?? "-" }}
                </td>

                <td class="px-5 py-2">
                  <div class="flex items-center justify-center gap-2">
                    <router-link
                      :to="{ name: 'EditSaleItem', params: { id: product.id } }"
                      class="itbms-edit-button inline-flex items-center gap-1.5 rounded-lg border border-blue-300 text-blue-700 px-3 py-1.5 hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-400"
                      aria-label="Edit"
                      title="Edit"
                    >
                      <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M16.862 4.487a2.25 2.25 0 1 1 3.182 3.182L9 18.713 4.5 19.5l.787-4.5L16.862 4.487z" />
                        <path d="M19.5 7.5L16.5 4.5" />
                      </svg>
                      <span class="text-xs font-medium">Edit</span>
                    </router-link>

                    <button
                      @click="showDeleteDialog(product.id)"
                      class="itbms-delete-button inline-flex items-center gap-1.5 rounded-lg border border-red-300 text-red-700 px-3 py-1.5 hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-400"
                      aria-label="Delete"
                      title="Delete"
                    >
                      <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M6 7.5h12M9.75 7.5v9.75m4.5-9.75v9.75M7.5 7.5l1.125 12.375A2.25 2.25 0 0 0 10.875 22h2.25a2.25 2.25 0 0 0 2.25-2.125L16.5 7.5M9.75 4.5h4.5A1.5 1.5 0 0 1 15.75 6h-7.5A1.5 1.5 0 0 1 9.75 4.5z" />
                      </svg>
                      <span class="text-xs font-medium">Delete</span>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <Pagination
            v-show="products.length > 0 && totalPages > 1"
            :current-page="currentPage"
            :total-pages="totalPages"
            :items-per-page="itemsPerPage"
            @update:currentPage="(p) => (currentPage = p)"
            @update:itemsPerPage="(n) => (itemsPerPage = n)"
          />
        </div>
      </div>
    </div>

    <!-- Confirm Modal -->
    <div v-if="showConfirmDialog" class="fixed inset-0 flex items-center justify-center z-50 bg-black/30">
      <div class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black">
        <h2 class="text-2xl font-bold mb-4">Delete Confirmation</h2>
        <p class="mb-6 itbms-message">Do you want to delete this sale item?</p>
        <div class="flex justify-center gap-4 pt-4">
          <button
            class="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition itbms-cancel-button"
            @click="showConfirmDialog = false"
          >
            Cancel
          </button>
          <button
            class="bg-green-500 text-white px-6 py-2 rounded hover:bg-green-600 transition itbms-confirm-button"
            @click="confirmDelete"
          >
            Confirm
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="deleteSuccess"
      class="fixed bottom-6 left-1/2 transform -translate-x-1/2 bg-green-100 text-green-700 px-6 py-3 rounded shadow-lg itbms-message"
      aria-live="polite"
    >
      {{ deleteSuccess }}
    </div>
  </div>
</template>

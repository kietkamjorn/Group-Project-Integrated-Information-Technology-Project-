<script setup>
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData, deleteData } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();

const products = ref([]);
const isLoading = ref(true);

const showConfirmDialog = ref(false);
const saleItemToDeleteId = ref(null);

const successMessage = ref(null);
const deleteSuccess = ref(null);
const deleteErrorMessage = ref(route.query.errorMessage || null);

const loadProducts = async () => {
  try {
    const apiPath = "/v2/sale-items";
    const data = await readData(apiPath);

    // สมมติว่ามันเป็น { content: [...] }
    const items = Array.isArray(data)
      ? data
      : Array.isArray(data.content)
      ? data.content
      : [];

    products.value = items.slice().sort((a, b) => a.id - b.id);
  } catch (err) {
    deleteErrorMessage.value = "ไม่สามารถโหลดข้อมูลสินค้าได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};


const confirmDelete = async () => {
  try {
    const id = saleItemToDeleteId.value;
    const isDeleted = await deleteData(`/v1/sale-items/${id}`);

    if (!isDeleted) {
      throw new Error("The requested sale item does not exist.");
    }

    deleteSuccess.value = "The sale item has been deleted.";
    // ตัดในหน้าให้เห็นผลเร็ว
    products.value = products.value.filter((item) => item.id !== id);
    showConfirmDialog.value = false;

    // รีเฟรชจากหลังบ้าน
    await loadProducts();

    setTimeout(() => {
      deleteSuccess.value = null;
    }, 3000);
  } catch (err) {
    let message = "";
    if (err?.response?.status === 404) {
      message = "The requested sale item does not exist.";
    } else {
      message =
        "Delete failed: " + (err.response?.data?.message || err.message);
    }
    // ✅ แสดงผ่านแบนเนอร์ error เดียวกัน
    deleteErrorMessage.value = message;
    showConfirmDialog.value = false;
  }
};

const showDeleteDialog = (id) => {
  saleItemToDeleteId.value = id;
  showConfirmDialog.value = true;
};

onMounted(() => {
  // success จากการ add
  if (route.query.success === "true") {
    successMessage.value = "The sale item has been successfully added.";
    setTimeout(() => (successMessage.value = null), 3000);
  }

  // errorMessage จาก query (กรณี redirect กลับมาพร้อม error)
  if (route.query.errorMessage) {
    deleteErrorMessage.value = String(route.query.errorMessage);
    setTimeout(() => (deleteErrorMessage.value = null), 5000);

    const q = { ...route.query };
    delete q.errorMessage;
    router.replace({ query: q });
  }

  loadProducts();
});
</script>

<template>
  <div class="w-full px-4 py-5 not-prose">
    <div
      v-if="successMessage"
      class="mb-4 bg-green-100 text-green-700 px-4 py-3 rounded shadow itbms-message"
    >
      {{ successMessage }}
    </div>

    <div
      v-if="deleteErrorMessage"
      class="mb-8 bg-red-100 text-red-700 px-4 py-3 rounded shadow itbms-message"
    >
      {{ deleteErrorMessage }}
    </div>

    <div v-if="isLoading" class="text-center">กำลังโหลด...</div>
    <div v-else-if="products.length === 0" class="text-center text-black">
      No Sale Item
    </div>
    <div v-else>
      <div class="w-full max-w-full flex justify-between mb-4 pb-4 mt-6">
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

      <div class="flex flex-col items-center">
        <div
          class="w-full rounded-2xl ring-1 ring-blue-100 bg-white shadow overflow-hidden"
        >
          <div class="overflow-x-auto">
            <table class="min-w-[960px] w-full text-sm leading-6">
              <colgroup>
                <col style="width: 72px" />
                <!-- Id -->
                <col style="width: 14%" />
                <!-- Brand -->
                <col style="width: 24%" />
                <!-- Model -->
                <col style="width: 10%" />
                <!-- Ram -->
                <col style="width: 12%" />
                <!-- Storage -->
                <col style="width: 12%" />
                <!-- Color -->
                <col style="width: 12%" />
                <!-- Price -->
                <col style="width: 10%" />
                <!-- Action -->
              </colgroup>

              <thead>
                <tr>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-left px-5 py-3 text-xs font-semibold uppercase tracking-wide first:rounded-tl-2xl"
                  >
                    Id
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-left px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Brand
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-left px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Model
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Ram
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Storage
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-left px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Color
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-right px-5 py-3 text-xs font-semibold uppercase tracking-wide"
                  >
                    Price
                  </th>
                  <th
                    class="sticky top-0 z-10 bg-blue-600 text-white text-center px-5 py-3 text-xs font-semibold uppercase tracking-wide last:rounded-tr-2xl"
                  >
                    Action
                  </th>
                </tr>
              </thead>

              <tbody class="divide-y divide-blue-50 text-gray-900">
                <tr v-if="products.length === 0">
                  <td colspan="8" class="px-5 py-6 text-center">
                    No Sale Item.
                  </td>
                </tr>

                <tr
                  v-for="product in products"
                  :key="product.id"
                  class="group itbms-row hover:bg-blue-50 transition-colors"
                >
                  <td
                    class="px-5 py-2.5 text-right tabular-nums itbms-id whitespace-nowrap"
                  >
                    {{ product.id }}
                  </td>
                  <td class="px-5 py-2.5 itbms-brand whitespace-nowrap">
                    {{ product.brandName }}
                  </td>
                  <td class="px-5 py-2.5 itbms-model">
                    {{ product.model }}
                  </td>
                  <td
                    class="px-5 py-2.5 text-right tabular-nums itbms-ramGb whitespace-nowrap"
                  >
                    {{ product.ramGb ?? "-" }}
                  </td>
                  <td
                    class="px-5 py-2.5 text-right tabular-nums itbms-storageGb whitespace-nowrap"
                  >
                    {{ product.storageGb ?? "-" }}
                  </td>
                  <td class="px-5 py-2.5 itbms-color whitespace-nowrap">
                    {{ product.color ?? "-" }}
                  </td>
                  <td
                    class="px-5 py-2.5 text-right tabular-nums font-medium itbms-price whitespace-nowrap"
                  >
                    {{ product.price.toLocaleString() }}
                  </td>
                  <td class="px-5 py-2">
                    <div class="flex items-center justify-center gap-2">
                      <!-- Edit: ปุ่ม outline + ไอคอนเส้นบาง -->
                      <router-link
                        :to="{
                          name: 'EditSaleItem',
                          params: { id: product.id },
                        }"
                        class="itbms-edit-button inline-flex items-center gap-1.5 rounded-lg border border-blue-300 text-blue-700 px-3 py-1.5 hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-400"
                        aria-label="Edit"
                        title="Edit"
                      >
                        <svg
                          class="w-4 h-4"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          aria-hidden="true"
                        >
                          <path
                            d="M16.862 4.487a2.25 2.25 0 1 1 3.182 3.182L9 18.713 4.5 19.5l.787-4.5L16.862 4.487z"
                          />
                          <path d="M19.5 7.5L16.5 4.5" />
                        </svg>
                        <span class="text-xs font-medium">Edit</span>
                      </router-link>

                      <!-- Delete: ปุ่ม outline + ไอคอนเส้นบาง -->
                      <button
                        @click="showDeleteDialog(product.id)"
                        class="itbms-delete-button inline-flex items-center gap-1.5 rounded-lg border border-red-300 text-red-700 px-3 py-1.5 hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-400"
                        aria-label="Delete"
                        title="Delete"
                      >
                        <svg
                          class="w-4 h-4"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          aria-hidden="true"
                        >
                          <path
                            d="M6 7.5h12M9.75 7.5v9.75m4.5-9.75v9.75M7.5 7.5l1.125 12.375A2.25 2.25 0 0 0 10.875 22h2.25a2.25 2.25 0 0 0 2.25-2.125L16.5 7.5M9.75 4.5h4.5A1.5 1.5 0 0 1 15.75 6h-7.5A1.5 1.5 0 0 1 9.75 4.5z"
                          />
                        </svg>
                        <span class="text-xs font-medium">Delete</span>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Confirm Modal -->
    <div
      v-if="showConfirmDialog"
      class="fixed inset-0 flex items-center justify-center z-50"
    >
      <div
        class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black"
      >
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
    >
      {{ deleteSuccess }}
    </div>
  </div>
</template>

<style scoped></style>

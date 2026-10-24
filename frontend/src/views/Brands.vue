<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { withAuthFetch } from "@/libs/fetchUtils";

const router = useRouter();

/* ===== state หลัก ===== */
const brands = ref([]);
const isLoading = ref(true);
const error = ref(null);

const showConfirmDialog = ref(false);
const showErrorDialog = ref(false);

const deleteSuccess = ref(null);
const successMessage = ref(null);

const targetId = ref(null);

/* ===== helpers ===== */
const selectedBrand = computed(
  () => brands.value.find((b) => b.id === targetId.value) || {}
);

// โหลดแบรนด์ทั้งหมด
const loadBrands = async () => {
  isLoading.value = true;
  try {
    const data = await withAuthFetch("/v2/brands");
    if (!Array.isArray(data)) throw new Error("API did not return an array");

    brands.value = data.slice().sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
    error.value = null;
  } catch (e) {
    console.error(e);
    error.value = "ไม่สามารถโหลดข้อมูลแบรนด์ได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};

// แยก status code จาก Error("Error: xxx")
const getStatusCode = (err) => {
  const msg = String(err?.message ?? "");
  const m = msg.match(/\d{3}/);
  return m ? Number(m[0]) : undefined;
};

// ปิด dialog ต่างๆ
const closeDialogs = () => {
  showConfirmDialog.value = false;
  showErrorDialog.value = false;
  error.value = null;
};

// เปิด confirm ก่อนลบ (เช็ค noOfSaleItems = 0 เท่านั้นถึงจะลบได้)
const askDelete = (id) => {
  targetId.value = id;
  const b = brands.value.find((x) => x.id === id);

  if (!b) {
    error.value = "An error has occurred, the brand does not exist.";
    showErrorDialog.value = true;
    return;
  }
  if ((b.noOfSaleItems ?? 0) > 0) {
    error.value = `Delete ${b.name} is not allowed. There are sale items with ${b.name} brand.`;
    showErrorDialog.value = true;
    return;
  }
  showConfirmDialog.value = true;
};

// ยืนยันลบ
const confirmDelete = async () => {
  const id = targetId.value;
  try {
    await withAuthFetch(`/v2/brands/${id}`, { method: "DELETE" });

    // ตัดออกจาก list ในหน้าเลย ไม่ต้องรีโหลดทั้งหน้า
    brands.value = brands.value.filter((x) => x.id !== id);
    deleteSuccess.value = "The brand has been deleted.";
    closeDialogs();

    setTimeout(() => (deleteSuccess.value = null), 3000);
  } catch (err) {
    const code = getStatusCode(err);
    const b = brands.value.find((x) => x.id === id);
    const name = b?.name || "Unknown";

    if (code === 404) {
      error.value = "An error has occurred, the brand does not exist.";
    } else {
      error.value = `Delete ${name} is not allowed. There are sale items with ${name} brand.`;
    }
    closeDialogs();
    showErrorDialog.value = true;
  }
};

// ปุ่ม Cancel ใน dialog
const goToBrands = () => closeDialogs();

/* ===== mounted ===== */
onMounted(async () => {
  successMessage.value = router.options.history.state?.message || null;
  if (successMessage.value) {
    setTimeout(() => (successMessage.value = null), 3000);
  }
  await loadBrands();
});
</script>

<template>
  <div class="px-10 py-8">
    <nav class="text-gray-500 text-lg mb-6 pb-2">
      <router-link
        to="/sale-items/list"
        class="hover:underline font-bold Itbms-item-list"
      >
        Sale Item List
      </router-link>
      <span class="mx-2"> > </span>
      <router-link
        to="/brands/add"
        class="hover:underline font-bold itbms-add-button"
      >
        Add Brand
      </router-link>
    </nav>

    <div v-if="isLoading" class="text-center text-gray-500 text-xl">
      Loading...
    </div>

    <div
      v-else-if="error && !showErrorDialog && !showConfirmDialog"
      class="text-center text-gray-600"
    >
      {{ error }}
    </div>

    <div v-else class="flex flex-col items-center">
      <table
        class="table-auto w-full border-collapse bg-white rounded-lg shadow-md overflow-hidden"
      >
        <thead>
          <tr class="bg-blue-500 text-white text-center">
            <th class="border border-black px-4 py-3 text-sm font-semibold">
              Id
            </th>
            <th class="border border-black px-4 py-3 text-sm font-semibold">
              Name
            </th>
            <th class="border border-black px-4 py-3 text-sm font-semibold">
              Action
            </th>
          </tr>
        </thead>
        <tbody class="bg-blue-100 text-center text-black">
          <tr
            v-for="b in brands"
            :key="b.id"
            class="itbms-row hover:bg-blue-200 transition-colors duration-150"
          >
            <td class="border border-black px-4 py-3 itbms-id">
              {{ b.id }}
            </td>
            <td class="border border-black px-4 py-3 itbms-name">
              {{ b.name }}
            </td>
            <td class="border border-black px-4 py-3">
              <div class="flex gap-2 justify-center">
                <router-link
                  :to="{ name: 'Brandedit', params: { id: b.id } }"
                  class="bg-blue-400 text-white px-4 py-1 rounded-md hover:bg-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-all itbms-edit-button"
                >
                  E
                </router-link>
                <button
                  @click="askDelete(b.id)"
                  class="bg-red-400 text-white px-4 py-1 rounded-md hover:bg-red-600 focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all itbms-delete-button"
                >
                  D
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="!brands.length">
            <td colspan="3" class="text-center text-gray-600 py-6">
              No brands
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Confirm Dialog -->
    <div
      v-if="showConfirmDialog"
      class="fixed inset-0 flex items-center justify-center z-50 bg-black/20"
    >
      <div class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black">
        <h2 class="text-2xl font-bold mb-4">Delete Confirmation</h2>
        <p class="mb-6 itbms-message">
          Do you want to delete {{ selectedBrand.name }} brand?
        </p>
        <div class="flex justify-center gap-4 pt-4">
          <button
            class="bg-gray-300 text-gray-900 px-6 py-2 rounded hover:bg-gray-400 transition"
            @click="goToBrands"
          >
            Cancel
          </button>
          <button
            class="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition itbms-confirm-button"
            @click="confirmDelete"
          >
            Confirm
          </button>
        </div>
      </div>
    </div>

    <!-- Error Dialog -->
    <div
      v-if="showErrorDialog"
      class="fixed inset-0 flex items-center justify-center z-50 bg-black/20"
    >
      <div class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black">
        <h2 class="text-2xl font-bold mb-4">Delete Error</h2>
        <p class="mb-6 itbms-message">{{ error }}</p>
        <div class="flex justify-center pt-4">
          <button
            class="bg-gray-300 text-gray-900 px-6 py-2 rounded hover:bg-gray-400 transition"
            @click="goToBrands"
          >
            Close
          </button>
        </div>
      </div>
    </div>

    <!-- Toasts -->
    <div
      v-if="successMessage"
      class="fixed bottom-6 left-1/2 -translate-x-1/2 bg-yellow-100 text-yellow-700 px-6 py-3 rounded shadow-lg itbms-message"
    >
      {{ successMessage }}
    </div>

    <div
      v-if="deleteSuccess"
      class="fixed bottom-6 left-1/2 -translate-x-1/2 bg-green-100 text-green-700 px-6 py-3 rounded shadow-lg itbms-message"
    >
      {{ deleteSuccess }}
    </div>
  </div>
</template>

<style scoped></style>

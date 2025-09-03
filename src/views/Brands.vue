<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { readData, deleteData } from "../libs/fetchUtils";
import.meta.env;
const router = useRouter();
const products = ref([]);
const error = ref(null);
const isLoading = ref(true);
const showConfirmDialog = ref(false);
const showErrorDialog = ref(false);
const deleteSuccess = ref(null);
const saleItemToDeleteId = ref(null);
const successMessage = ref(null);

const goToBrands = () => {
  showConfirmDialog.value = false;
  showErrorDialog.value = false;
  error.value = null;
};

const selectedProduct = computed(() => {
  return products.value.find((p) => p.id === saleItemToDeleteId.value) || {};
});

const loadProducts = async () => {
  try {
    const apiPath = "/v2/brands";
    const data = await readData(apiPath);

    if (!Array.isArray(data)) {
      throw new Error("API did not return an array");
    }
    const items = data.sort((a, b) => a.productId - b.productId);
    products.value = items;
    error.value = null;
  } catch (err) {
    error.value = "ไม่สามารถโหลดข้อมูลสินค้าได้ กรุณาลองใหม่";
  } finally {
    isLoading.value = false;
  }
};

// ฟังก์ชันเช็คว่าสามารถลบได้หรือไม่
const checkCanDelete = (id) => {
  const product = products.value.find((item) => item.id === id);
  
  if (!product) {
    return { 
      canDelete: false, 
      error: "An error has occurred, the brand does not exist." 
    };
  }

  // เช็คจาก noOfSaleItems ถ้ามีค่ามากกว่า 0 แสดงว่าลบไม่ได้
  if (product.noOfSaleItems && product.noOfSaleItems > 0) {
    return { 
      canDelete: false, 
      error: `Delete ${product.name} is not allowed. There are sale items with ${product.name} brand.` 
    };
  }

  return { canDelete: true, error: null };
};

const confirmDelete = async () => {
  try {
    const id = saleItemToDeleteId.value;
    await deleteData(`/v2/brands/${id}`);

    deleteSuccess.value = "The brand has been deleted.";
    products.value = products.value.filter((item) => item.id !== id);
    showConfirmDialog.value = false;

    await loadProducts();

    setTimeout(() => {
      deleteSuccess.value = null;
    }, 3000);
  } catch (err) {
    const statusCode = err?.response?.status;

    if (statusCode === 404) {
      error.value = "An error has occurred, the brand does not exist.";
      showConfirmDialog.value = false;
      showErrorDialog.value = true;

      setTimeout(() => {
        router.push("/brands").then(() => {
          window.location.reload();
        });
      }, 3000);
      return;
    }

    const id = saleItemToDeleteId.value;
    const product = products.value.find((item) => item.id === id);
    const name = product?.name || "Unknown";

    error.value = `Delete ${name} is not allowed. There are sale items with ${name} brand.`;
    showConfirmDialog.value = false;
    showErrorDialog.value = true;

    setTimeout(() => {
      router.push("/brands").then(() => {
        window.location.reload();
      });
    }, 3000);
  }
};

const showDeleteDialog = (id) => {
  saleItemToDeleteId.value = id;
  
  // เช็คก่อนว่าสามารถลบได้หรือไม่
  const { canDelete, error: checkError } = checkCanDelete(id);
  
  if (canDelete) {
    // ถ้าลบได้ให้แสดง confirmation dialog
    showConfirmDialog.value = true;
  } else {
    // ถ้าลบไม่ได้ให้แสดง error popup ทันที
    error.value = checkError;
    showErrorDialog.value = true;
    
    setTimeout(() => {
      router.push("/brands").then(() => {
        window.location.reload();
      });
    }, 3000);
  }
};

const goBack = () => {
  setTimeout(() => {
    router.push("/brands").then(() => {
      window.location.reload();
    });
  }, 1000);
};

onMounted(async () => {
  successMessage.value = router.options.history.state?.message || null;

  if (successMessage.value) {
    setTimeout(() => {
      successMessage.value = null;
    }, 3000);
  }

  await loadProducts();
});
</script>

<template>
  <div class="px-10 py-8">
    <nav class="text-gray-500 text-lg mb-6 pb-2">
      <router-link
        to="/sale-items/list"
        class="hover:underline font-bold Itbms-item-list"
        >Sale Item List</router-link
      >
      <span class="mx-2"> > </span>
      <router-link
        to="/brands/add"
        class="hover:underline font-bold itbms-add-button"
        >Add Brand</router-link
      >
    </nav>
    <div v-if="isLoading" class="text-center text-gray-500 text-xl">
      Loading...
    </div>

    <div
      v-else-if="error && !showErrorDialog && !showConfirmDialog"
      class="flex flex-col justify-center items-center text-center text-gray-500 text-xl h-screen"
    ></div>
    <div v-else class="flex flex-col items-center">
      <table
        class="table-auto w-full border-collapse bg-white rounded-lg shadow-md overflow-hidden"
      >
        <thead>
          <tr class="bg-blue-500 text-white text-center">
            <th
              class="border border-black px-4 py-3 text-sm font-semibold tracking-wide"
            >
              Id
            </th>
            <th
              class="border border-black px-4 py-3 text-sm font-semibold tracking-wide"
            >
              Name
            </th>
            <th
              class="border border-black px-4 py-3 text-sm font-semibold tracking-wide"
            >
              Action
            </th>
          </tr>
        </thead>
        <tbody class="bg-blue-100 text-center text-black">
          <tr
            v-for="product in products"
            :key="product.id"
            class="itbms-row hover:bg-blue-200 transition-colors duration-150"
          >
            <td class="border border-black px-4 py-3 itbms-id">
              {{ product.id }}
            </td>
            <td class="border border-black px-4 py-3 itbms-name">
              {{ product.name }}
            </td>
            <td class="border border-black px-4 py-3">
              <div class="flex gap-2 justify-center">
                <router-link
                  :to="{ name: 'Brandedit', params: { id: product.id } }"
                  class="bg-blue-400 text-white px-4 py-1 rounded-md hover:bg-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-all itbms-edit-button"
                >
                  E
                </router-link>
                <button
                  @click="showDeleteDialog(product.id)"
                  class="bg-red-400 text-white px-4 py-1 rounded-md hover:bg-red-700 focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all itbms-delete-button"
                >
                  D
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="showConfirmDialog"
      class="fixed inset-0 flex items-center justify-center z-50"
    >
      <div
        class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black"
      >
        <h2 class="text-2xl font-bold mb-4">Delete Confirmation</h2>

        <p class="mb-6 itbms-message">
          Do you want to delete {{ selectedProduct.name }} brand?
        </p>

        <div class="flex justify-center gap-4 pt-4">
          <button
            class="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition itbms-cancel-button"
            @click="goToBrands"
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

    <!-- Error Dialog  -->
    <div
      v-if="showErrorDialog"
      class="fixed inset-0 flex items-center justify-center z-50"
    >
      <div
        class="bg-white rounded-lg p-8 w-full max-w-md text-center shadow-xl text-black"
      >
        <h2 class="text-2xl font-bold mb-4">Delete Confirmation</h2>

        <p class="mb-6 itbms-message">
          {{ error }}
        </p>

        <div class="flex justify-center gap-4 pt-4">
          <button
            class="bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition itbms-cancel-button"
            @click="goToBrands"
          >
            Cancle
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="successMessage"
      class="fixed bottom-6 left-1/2 transform -translate-x-1/2 bg-yellow-100 text-yellow-700 px-6 py-3 rounded shadow-lg itbms-message"
    >
      {{ successMessage }}
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
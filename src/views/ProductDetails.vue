<script setup>
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { readData, deleteData } from "../libs/fetchUtils";

const route = useRoute();
const router = useRouter();
const productId = route.params.id;
const product = ref(null);
const isLoading = ref(true);
const error = ref(null);

const showConfirmDialog = ref(false);
const deleteSuccess = ref(null);

const API_URL = import.meta.env.VITE_API_URL; // เช่น http://localhost:8080/itb-mshop
const parsed = new URL(API_URL);
const API_ORIGIN = parsed.origin; // http://localhost:8080
const API_CTX = parsed.pathname.replace(/\/$/, ""); // /itb-mshop
const API = `${API_ORIGIN}${API_CTX}`; // http://localhost:8080/itb-mshop

const toAbs = (u) => {
  if (!u) return null;
  if (u.startsWith("http")) return u;
  if (u.startsWith(`${API_CTX}/media/`)) return `${API_ORIGIN}${u}`; // ถ้าเผลอคืนมาพร้อม context path
  if (u.startsWith("/media/")) return `${API}${u}`; // ✅ คง /itb-mshop ไว้
  if (u.startsWith("media/")) return `${API}/${u}`; // เผื่อไม่มี / นำหน้า
  return `${API}/${String(u).replace(/^\/+/, "")}`; // กรณีอื่นๆ
};
// ฟังก์ชันการดึงข้อมูลสินค้า
const loadProduct = async (productId) => {
  const numericId = parseInt(productId, 10);

  if (isNaN(numericId)) {
    error.value = "productId is not a valid number";
    isLoading.value = false;
    console.error("productId is not a valid number:", productId);
    return;
  }

  try {
    // 1. โหลดข้อมูลสินค้าพื้นฐาน
    const item = await readData(`/v2/sale-items/${numericId}`);

    if (item && typeof item === "object") {
      const pictures = Array.isArray(item.saleItemImages)
        ? item.saleItemImages
        : [];
      const sortedPictures = pictures
        .slice()
        .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));

      const mainImage = toAbs(sortedPictures[0]?.url) || null;
      const additionalImages = sortedPictures
        .map((pic) => toAbs(pic.url))
        .filter(Boolean);

      product.value = {
        ...item,
        image: mainImage,
        "ltbms-additionalImages": additionalImages,
        priceUnit: "THB",
        ramGbUnit: item.ramGb ? "GB" : "",
        storageGbUnit: item.storageGb ? "GB" : "",
        quantityUnit: "unit",
      };
      error.value = null;
    } else {
      throw new Error("ไม่พบสินค้าที่ระบุหรือข้อมูลไม่ถูกต้อง");
    }
  } catch (err) {
    error.value = "The requested sale item does not exist.";
    console.error("เกิดข้อผิดพลาดในการโหลดสินค้า:", err.message, err);

    setTimeout(() => {
      goBack();
    }, 2000);
  } finally {
    isLoading.value = false;
  }
};

const confirmDelete = async () => {
  try {
    const saleItemId = route.params.id;
    const isDeleted = await deleteData(`/v1/sale-items/${saleItemId}`);

    if (isDeleted) {
      deleteSuccess.value = "The sale item has been deleted.";
      setTimeout(() => {
        router.push("/sale-items");
      }, 2000);
    } else {
      throw new Error("The requested sale item does not exist.");
    }
  } catch (err) {
    let message = "";

    if (err?.response?.status === 404) {
      message = "The requested sale item does not exist.";
    } else {
      message =
        "Delete failed: " + (err.response?.data?.message || err.message);
    }
    router.push({
      path: "/sale-items",
      query: { errorMessage: message },
    });
  }
};

const FALLBACK_IMG =
  "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/No_picture_available.png/802px-No_picture_available.png";

const onImgError = (e) => {
  // กันลูปถ้ารูปสำรองมีปัญหาเอง
  e.target.onerror = null;
  e.target.src = FALLBACK_IMG;
};
// ฟังก์ชันกลับไปหน้า Home
const goBack = () => {
  router.push("/sale-items");
};

// เรียกใช้ฟังก์ชัน loadProduct เมื่อหน้าถูกโหลด
onMounted(() => {
  loadProduct(productId);
});
</script>

<template>
  <div
    class="px-10 py-8 w-full bg-white rounded-lg shadow-lg p-6 sm:p-8 lg:p-10"
  >
    <nav class="text-gray-500 text-lg mb-6">
      <router-link to="/sale-items" class="hover:underline font-bold"
        >Home</router-link
      >
      <span class="mx-2"> > </span>
      <router-link
        to="/sale-items"
        class="hover:underline font-bold itbms-home-button"
      >
        {{ product?.model ?? "..." }} {{ product?.color ?? "" }}
      </router-link>
    </nav>
    <div v-if="isLoading" class="text-center text-gray-500 text-xl">
      Loading...
    </div>
    <div
      v-else-if="error"
      class="flex flex-col justify-center items-center text-center text-gray-500 text-xl h-screen"
    >
      <div class="itbms-message bg-white p-6 rounded-lg shadow-lg">
        <div class="mb-6">{{ error }}</div>
        <button
          class="mt-6 bg-red-500 text-white px-6 py-2 rounded hover:bg-red-600 transition"
          @click="goBack"
        >
          Home
        </button>
      </div>
    </div>

    <!-- แสดงข้อมูลสินค้า -->
    <div v-else-if="product" class="itbms-row flex gap-8 flex-col lg:flex-row">
      <div class="lg:w-1/2 flex flex-col items-center">
        <!-- รูปใหญ่ -->
        <img
          :src="product.image || FALLBACK_IMG"
          alt="รูปภาพสินค้า"
          class="w-150 h-150 object-contain mb-3 border"
          @error="onImgError"
        />

        <!-- รูปย่อย -->
        <div class="flex justify-center gap-4 pt-4 flex-wrap">
          <img
            v-for="(thumb, index) in product['ltbms-additionalImages'] &&
            product['ltbms-additionalImages'].length
              ? product['ltbms-additionalImages']
              : [FALLBACK_IMG, FALLBACK_IMG, FALLBACK_IMG, FALLBACK_IMG]"
            :key="index + '-' + thumb"
            :src="thumb || FALLBACK_IMG"
            alt="รูปย่อย"
            class="w-20 h-20 object-cover border rounded cursor-pointer hover:scale-105 transition"
            @click="product.image = thumb || FALLBACK_IMG"
            @error="onImgError"
          />
        </div>
      </div>

      <!-- รายละเอียดสินค้า -->
      <div class="flex-1 py-4 text-xl leading-loose pt-20">
        <p class="itbms-brand text-gray-600 mb-6">
          <strong>Brand:</strong> {{ product.brandName ?? "-" }}
        </p>
        <p class="itbms-model text-gray-600 mb-6">
          <strong>Model:</strong> {{ product.model ?? "-" }}
        </p>
        <p class="itbms-price text-gray-600 mb-6">
          <strong>Price:</strong>
          {{ product.price?.toLocaleString() ?? "-" }}
          {{ product.priceUnit ?? "" }}
        </p>
        <p class="itbms-description text-gray-600 mb-6">
          <strong>Description:</strong>
          {{ product.description ?? "-" }}
        </p>
        <p class="itbms-ramGb text-gray-600 mb-6">
          <strong>Ram:</strong>
          {{ product.ramGb ?? "-" }}
          <span class="itbms-ramGb-unit">{{ product.ramGbUnit ?? "" }}</span>
        </p>
        <p class="itbms-screenSizeInch text-gray-600 mb-6">
          <strong>Screen Size:</strong>
          {{ product.screenSizeInch ?? "-" }}
          <span class="itbms-screenSizeInch-unit">Inches</span>
        </p>
        <p class="itbms-storageGb text-gray-600 mb-6">
          <strong>Storage:</strong>
          {{ product.storageGb ?? "-" }}
          <span class="itbms-storageGb-unit">{{
            product.storageGbUnit ?? ""
          }}</span>
        </p>

        <p class="itbms-color text-gray-600 mb-6">
          <strong>Color:</strong>
          {{
            product.color === "" || product.color == null ? "-" : product.color
          }}
        </p>

        <p class="itbms-quantity text-gray-600 mb-6">
          <strong>Available quantity:</strong>
          {{ product.quantity ?? "-" }}
          <span class="itbms-quantity-unit">{{
            product.quantityUnit ?? ""
          }}</span>
        </p>

        <div class="flex gap-4 mt-4">
          <router-link
            :to="{ name: 'EditSaleItem', params: { id: product.id } }"
            class="bg-blue-400 text-white px-6 py-2 rounded hover:bg-blue-500 transition itbms-edit-button"
          >
            Edit
          </router-link>

          <!-- ปุ่ม Delete -->
          <button
            @click="showConfirmDialog = true"
            class="bg-red-400 text-white px-6 py-2 rounded hover:bg-red-700 transition itbms-delete-button"
          >
            Delete
          </button>
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

    <!-- Success Message -->
    <div
      v-if="deleteSuccess"
      class="fixed bottom-6 left-1/2 transform -translate-x-1/2 bg-green-100 text-green-700 px-6 py-3 rounded shadow-lg itbms-message"
    >
      {{ deleteSuccess }}
    </div>
  </div>
</template>

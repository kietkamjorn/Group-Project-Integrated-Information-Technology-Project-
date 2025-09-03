<script setup>
import { computed, watch } from "vue";

const props = defineProps({
  currentPage: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 },
  itemsPerPage: { type: Number, default: 10 },
});

const emit = defineEmits([
  "page-change",
  "update:currentPage",
  "update:itemsPerPage",
]);

const currentPage = computed(() => props.currentPage);
const totalPages = computed(() => props.totalPages);

watch(totalPages, (newTotal) => {
  if (currentPage.value > newTotal && newTotal > 0) {
    goToPage(newTotal);
  }
});

// แสดงเลขหน้าได้สูงสุด 10 ปุ่ม (รองรับ TC#3/#4)
const visiblePages = computed(() => {
  const pages = [];
  const total = totalPages.value;
  const maxVisiblePages = 10;

  if (total <= 0) return pages;

  let startPage = 1;
  let endPage = total;

  if (total > maxVisiblePages) {
    startPage = Math.max(
      currentPage.value - Math.floor(maxVisiblePages / 2),
      1
    );
    endPage = startPage + maxVisiblePages - 1;
    if (endPage > total) {
      endPage = total;
      startPage = endPage - maxVisiblePages + 1;
    }
  }

  for (let i = startPage; i <= endPage; i++) pages.push(i);
  return pages;
});

const goToPage = (page) => {
  if (page < 1 || page > props.totalPages) return;
  if (page !== currentPage.value) {
    emit("update:currentPage", page);
  }
  emit("page-change", page);
};
const goToFirst = () => goToPage(1);
const goToLast = () => goToPage(props.totalPages);
const goToPrev = () => goToPage(currentPage.value - 1);
const goToNext = () => goToPage(currentPage.value + 1);
</script>

<template>
  <div class="flex flex-wrap items-center space-x-4 p-4 mx-4">
    <button
      class="itbms-page-first px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
      :disabled="currentPage === 1"
      :class="{ 'opacity-50 cursor-not-allowed': currentPage === 1 }"
      @click="goToFirst"
    >
      First
    </button>

    <button
      class="itbms-page-prev px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
      :disabled="currentPage === 1"
      :class="{ 'opacity-50 cursor-not-allowed': currentPage === 1 }"
      @click="goToPrev"
    >
      Prev
    </button>

    <button
      v-for="page in visiblePages"
      :key="page"
      :class="[
        `itbms-page-${page - 1}`,
        'px-4 py-2 rounded',
        page === currentPage
          ? 'bg-blue-700 text-white font-semibold'
          : 'bg-blue-500 text-white hover:bg-blue-600',
      ]"
      @click="goToPage(page)"
    >
      {{ page }}
    </button>

    <button
      class="itbms-page-next px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
      :disabled="currentPage === totalPages"
      :class="{ 'opacity-50 cursor-not-allowed': currentPage === totalPages }"
      @click="goToNext"
    >
      Next
    </button>

    <button
      class="itbms-page-last px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
      :disabled="currentPage === totalPages"
      :class="{ 'opacity-50 cursor-not-allowed': currentPage === totalPages }"
      @click="goToLast"
    >
      Last
    </button>
  </div>
</template>

<style scoped></style>

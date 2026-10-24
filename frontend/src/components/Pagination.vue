<script setup>
import { computed, watch } from "vue";

const props = defineProps({
  currentPage: { type: Number, default: 1 },
  totalPages:  { type: Number, default: 1 },
  maxVisible:  { type: Number, default: 10 }, 
});

const emit = defineEmits(["update:currentPage", "page-change"]);

const currentPage = computed(() => props.currentPage);
const totalPages  = computed(() => props.totalPages);

watch(totalPages, (newTotal) => {
  if (newTotal > 0 && currentPage.value > newTotal) goToPage(newTotal);
});

const isFirst = computed(() => currentPage.value <= 1);
const isLast  = computed(() => currentPage.value >= totalPages.value);

const visiblePages = computed(() => {
  const total = totalPages.value;
  if (total <= 0) return [];

  const max = Math.max(1, props.maxVisible);
  if (total <= max) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  let start = currentPage.value - Math.floor(max / 2);
  start = Math.max(1, start);
  let end = start + max - 1;
  if (end > total) {
    end = total;
    start = Math.max(1, end - max + 1);
  }

  return Array.from({ length: end - start + 1 }, (_, i) => start + i);
});

function goToPage(page) {
  if (page < 1 || page > totalPages.value) return;
  if (page !== currentPage.value) {
    emit("update:currentPage", page);
    emit("page-change", page);
  }
}
const goToFirst = () => goToPage(1);
const goToLast  = () => goToPage(totalPages.value);
const goToPrev  = () => goToPage(currentPage.value - 1);
const goToNext  = () => goToPage(currentPage.value + 1);
</script>

<template>
  <nav class="flex flex-wrap items-center gap-2 p-4 mx-4" aria-label="Pagination">
    <button
      class="itbms-page-first px-3 py-2 rounded bg-blue-500 text-white hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
      :disabled="isFirst"
      @click="goToFirst"
    >
      First
    </button>

    <button
      class="itbms-page-prev px-3 py-2 rounded bg-blue-500 text-white hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
      :disabled="isFirst"
      @click="goToPrev"
    >
      Prev
    </button>

    <button
      v-for="page in visiblePages"
      :key="page"
      :class="[
        `itbms-page-${page - 1}`,
        'px-3 py-2 rounded',
        page === currentPage
          ? 'bg-blue-700 text-white font-semibold'
          : 'bg-blue-500 text-white hover:bg-blue-600'
      ]"
      @click="goToPage(page)"
      :aria-current="page === currentPage ? 'page' : undefined"
      :aria-label="`Go to page ${page}`"
    >
      {{ page }}
    </button>

    <button
      class="itbms-page-next px-3 py-2 rounded bg-blue-500 text-white hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
      :disabled="isLast"
      @click="goToNext"
    >
      Next
    </button>

    <button
      class="itbms-page-last px-3 py-2 rounded bg-blue-500 text-white hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
      :disabled="isLast"
      @click="goToLast"
    >
      Last
    </button>
  </nav>
</template>

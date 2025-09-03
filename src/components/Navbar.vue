<script setup>
import { ref } from "vue";
const searchInput = ref("");

// รับ filters จาก parent
const doSearch = (e) => {
  e?.preventDefault?.();
  e?.stopPropagation?.();
  window.dispatchEvent(
    new CustomEvent("itbms:search", {
      detail: { keyword: searchInput.value.trim() },
    })
  );
};
const clearSearch = (e) => {
  e?.preventDefault?.();
  e?.stopPropagation?.();
  searchInput.value = "";
  window.dispatchEvent(new CustomEvent("itbms:clear"));
};
</script>

<template>
  <nav class="bg-sit text-white shadow-md">
    <div class="max-w-9xl mx-auto px-4 h-12 flex justify-between">
      <div class="flex items-center space-x-4">
        <router-link to="/" class="font-bold pe-10 text-3xl"
          >ITB MShop</router-link
        >

        <div class="relative w-64">
          <input
            v-model="searchInput"
            @keyup.enter="doSearch"
            class="itbms-search-text w-full px-3 py-1 pr-10 rounded-full text-black text-md focus:outline-none border-2 border-white bg-white"
            placeholder="Search..."
          />
          <button
            v-if="searchInput"
            @click="clearSearch"
            class="itbms-search-clear-button absolute right-12 top-1/2 -translate-y-1/2 text-gray-500 hover:text-black"
            aria-label="Clear"
          >
            ✕
          </button>

          <div
            class="absolute right-10 top-1/2 -translate-y-1/2 h-5 border-l border-black"
          ></div>

          <button
            type="button"
            @click="doSearch"
            class="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center text-black"
            aria-label="Search"
            title="Search"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              class="w-4 h-4 text-gray-500 hover:text-black"
              fill="currentColor"
              viewBox="0 0 1024 1024"
            >
              <path
                d="M1014.64 969.04L703.71 656.207c57.952-69.408 92.88-158.704 92.88-256.208c0-220.912-179.088-400-400-400s-400 179.088-400 400s179.088 400 400 400c100.368 0 192.048-37.056 262.288-98.144l310.496 312.448c12.496 12.497 32.769 12.497 45.265 0c12.48-12.496 12.48-32.752 0-45.263zM396.59 736.527c-185.856 0-336.528-150.672-336.528-336.528S210.734 63.471 396.59 63.471s336.528 150.672 336.528 336.528S582.446 736.527 396.59 736.527"
              />
            </svg>
          </button>
        </div>
      </div>

      <div class="flex items-center space-x-6 px-8 text-lg">
        <router-link to="/register" class="text-white hover:text-gray-300 pe-10"
          >Sign Up</router-link
        >
        <router-link to="/signin" class="text-white hover:text-gray-300"
          >Sign In</router-link
        >
      </div>
    </div>
  </nav>
</template>
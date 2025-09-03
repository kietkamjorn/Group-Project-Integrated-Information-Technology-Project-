<!-- src/views/App.vue -->
<script setup>
import Navbar from './components/Navbar.vue';
</script>

<template>
  <div class="bg-white min-h-screen">
    <Navbar  />
    <router-view />
  </div>
</template>

<style scoped>
</style>
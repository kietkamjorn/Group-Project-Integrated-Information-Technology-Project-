<!-- src/views/Signout.vue -->
<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { logout } from "@/libs/fetchUtils";

const router = useRouter();

onMounted(async () => {
  try { await logout(); } catch {}
  try {
    Object.keys(localStorage)
      .filter((k) => k.startsWith("cart:v1:"))
      .forEach((k) => localStorage.removeItem(k));
  } catch {}
  try {
    window.dispatchEvent(new Event("itbms:cart-updated"));
    window.dispatchEvent(new Event("itbms:auth-changed"));
  } catch {}

  
  router.replace("/signin");
});
</script>

<template>
  <div class="p-8 text-gray-600">Signing you out…</div>
</template>

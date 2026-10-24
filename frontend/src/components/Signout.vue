<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { buildUrl } from "@/libs/fetchUtils";

const router = useRouter();
const NICK_KEY = "itbms:nickname";
const CART_PREFIX = "cart:v1:"; 

function clearAllCartV1() {
  try {
    const keysToRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(CART_PREFIX)) keysToRemove.push(k);
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k));
  } catch {}
}

onMounted(async () => {
  try {
    try {
      await fetch(buildUrl("/v2/auth/signout"), {
        method: "POST",
        credentials: "include", 
      });
    } catch {
    }
    localStorage.removeItem(NICK_KEY);
    clearAllCartV1();
    localStorage.removeItem("access_token");
    localStorage.removeItem("auth:blockRefresh");
    localStorage.removeItem("refresh_token");
    sessionStorage.removeItem("access_token");
    sessionStorage.removeItem("refresh_token");
    window.dispatchEvent(new Event("itbms:cart-updated"));
    window.dispatchEvent(new Event("itbms:auth-changed"));
  } finally {
    router.replace("/sale-items");
  }
});
</script>

<template>
  <div class="p-8 text-gray-600">Signing you out…</div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import Navbar from "./components/Navbar.vue";

const router = useRouter();

const banner = ref({ show: false, kind: "info", text: "" });
function showBanner(kind, text, ms = 4000) {
  banner.value = { show: true, kind, text };
  if (ms > 0) setTimeout(() => (banner.value.show = false), ms);
}

function onRefreshFailed(e) {
  const code = e?.detail?.code;
  const requiresAuth = router.currentRoute.value?.meta?.requiresAuth === true;

  if (code === 400 || code === 401) {
    showBanner("warn", "Your session has expired. Please sign in again.", 4500);
    // ⬇️ เด้งไป /signin เฉพาะกรณีอยู่หน้าที่ต้อง auth เท่านั้น
    if (requiresAuth) {
      router.replace({ name: "Signin", query: { reason: "expired" } });
    }
  } else if (code === 403) {
    showBanner("warn", "Your account is not activated.", 4500);
    if (requiresAuth) {
      router.replace({ name: "Signin", query: { reason: "inactive" } });
    }
  } else {
    showBanner("error", "There is a problem. Please try again later.", 5000);
  }
}

onMounted(() => {
  window.addEventListener("itbms:auth-refresh-failed", onRefreshFailed);
});

onBeforeUnmount(() => {
  window.removeEventListener("itbms:auth-refresh-failed", onRefreshFailed);
});
</script>

<template>
  <div class="bg-white min-h-screen">
    <Navbar />
    <transition name="fade">
      <div v-if="banner.show" class="mx-auto max-w-5xl mt-4 px-4">
        <div
          class="rounded-lg px-4 py-3 text-sm"
          :class="{
            'bg-amber-50 text-amber-800 border border-amber-200': banner.kind === 'warn',
            'bg-rose-50 text-rose-800 border border-rose-200': banner.kind === 'error',
            'bg-emerald-50 text-emerald-800 border border-emerald-200': banner.kind === 'success',
            'bg-sky-50 text-sky-800 border border-sky-200': banner.kind === 'info',
          }"
        >
          {{ banner.text }}
        </div>
      </div>
    </transition>

    <router-view />
  </div>
</template>

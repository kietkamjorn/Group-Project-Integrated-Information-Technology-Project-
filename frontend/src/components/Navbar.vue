<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { decodeJwt, getAccessToken } from "@/libs/fetchUtils";

/* ---------- Search ---------- */
const searchInput = ref("");
const keyword = computed(() => searchInput.value.trim());

const doSearch = (e) => {
  e?.preventDefault?.();
  e?.stopPropagation?.();
  if (!keyword.value) return;
  window.dispatchEvent(
    new CustomEvent("itbms:search", { detail: { keyword: keyword.value } })
  );
};
const clearSearch = (e) => {
  e?.preventDefault?.();
  e?.stopPropagation?.();
  searchInput.value = "";
  window.dispatchEvent(new CustomEvent("itbms:clear"));
};

/* ---------- Auth / Navbar right side ---------- */
const router = useRouter();
const route = useRoute();
const user = ref(null);

const NICK_KEY = "itbms:nickname";
const nicknameOverride = ref(null);
try { nicknameOverride.value = localStorage.getItem(NICK_KEY) || null; } catch {}

function fromToken() {
  const token = getAccessToken();
  if (!token) return null;
  const p = decodeJwt(token) || {};
  const nick = p.nickname ?? p.nickName ?? null;
  if (!nick && !p.email && !p.role) return null;
  const base = { nickname: nick, email: p.email ?? null, role: p.role ?? null };
  if (nicknameOverride.value) base.nickname = nicknameOverride.value;
  return base;
}
function loadUser() {
  user.value = fromToken();
}

const isLoggedIn = computed(() => !!user.value);
const nickname = computed(() => user.value?.nickname ?? "");
const role = computed(() => user.value?.role ?? "-");
const isSeller = computed(() => String(role.value || "").toUpperCase() === "SELLER");

/* ---------- Sign out ---------- */
async function signOut() {
  try { isMenuOpen.value = false; } catch {}
  router.push({ name: "SignOut" });
}

/* ---------- Cart badge (per user) ---------- */
const cartCount = ref(0);
const cartDisabled = computed(() => !isLoggedIn.value || cartCount.value === 0);

const CART_VERSION = "v1";
function getCartKey() {
  const token = getAccessToken();
  const uid = token ? decodeJwt(token)?.sub : "guest";
  return `cart:${CART_VERSION}:${uid}`;
}
function readCartCount() {
  try {
    const raw = localStorage.getItem(getCartKey());
    if (!raw) return 0;
    const cart = JSON.parse(raw);
    const groups = Array.isArray(cart?.groups) ? cart.groups : [];
    let total = 0;
    for (const g of groups) {
      const items = Array.isArray(g?.items) ? g.items : [];
      for (const r of items) total += Number(r?.qty || 0);
    }
    return total;
  } catch {
    return 0;
  }
}
function updateCartBadge() {
  cartCount.value = readCartCount();
}

/* ---------- Bag (Seller) ---------- */
const newOrderCount = ref(0);
function readNewOrderCount() {
  // TODO: เรียก API จริงได้ภายหลัง
  return 1;
}
function updateBagBadge() {
  newOrderCount.value = isSeller.value ? readNewOrderCount() : 0;
}
const bagDisabled = computed(() => !isLoggedIn.value || !isSeller.value);

/* ---------- Dropdown (Profile menu) ---------- */
const isMenuOpen = ref(false);
const menuRef = ref(null);
const triggerRef = ref(null);

function toggleMenu() { isMenuOpen.value = !isMenuOpen.value; }
function closeMenu() { isMenuOpen.value = false; }
function onKeydown(e) { if (e.key === "Escape") closeMenu(); }
function onClickOutside(e) {
  const t = e.target;
  if (triggerRef.value && triggerRef.value.contains(t)) return;
  const inMenu = menuRef.value && menuRef.value.contains(t);
  if (!inMenu) closeMenu();
}

const goProfile = () => { closeMenu(); router.push("/profile"); };
const goOrder   = () => { closeMenu(); router.push("/your-orders"); };

const goCart = () => {
  if (!isLoggedIn.value) return router.push("/signin");
  if (cartDisabled.value) return;
  router.push("/cart");
};

const goBag = () => {
  if (!isLoggedIn.value) return router.push("/signin");
  if (bagDisabled.value) return;
  router.push("/sale-orders");
};

/* ---------- Global listeners ---------- */
const onProfileUpdated = (event) => {
  if (user.value && event.detail?.nickname) {
    user.value.nickname = event.detail.nickname;
    nicknameOverride.value = event.detail.nickname;
    try { localStorage.setItem(NICK_KEY, event.detail.nickname); } catch {}
  }
};
const onStorage = (ev) => {
  if ([ "access_token", "auth:blockRefresh", NICK_KEY ].includes(ev.key) || ev.key?.startsWith("cart:v1:")) {
    if (ev.key === NICK_KEY) nicknameOverride.value = ev.newValue || null;
    if (ev.key?.startsWith("cart:v1:")) updateCartBadge();
  }
};
const onAuthChanged = () => {
  const token = getAccessToken();
  user.value = token ? fromToken() : null;
  updateCartBadge();
  updateBagBadge();
};
const onCartUpdated = () => updateCartBadge();

onMounted(() => {
  loadUser();
  updateCartBadge();
  updateBagBadge();

  window.addEventListener("storage", onStorage);
  window.addEventListener("itbms:auth-changed", onAuthChanged);
  window.addEventListener("itbms:profile-updated", onProfileUpdated);
  window.addEventListener("itbms:cart-updated", onCartUpdated);
  document.addEventListener("click", onClickOutside);
  document.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener("storage", onStorage);
  window.removeEventListener("itbms:auth-changed", onAuthChanged);
  window.removeEventListener("itbms:profile-updated", onProfileUpdated);
  window.removeEventListener("itbms:cart-updated", onCartUpdated);
  document.removeEventListener("click", onClickOutside);
  document.removeEventListener("keydown", onKeydown);
});

/* ---------- react to route change ---------- */
watch(
  () => route.fullPath,
  () => {
    loadUser();
    updateCartBadge();
    updateBagBadge();
  }
);
</script>

<template>
  <nav class="bg-sit text-white shadow-md">
    <div class="max-w-9xl mx-auto px-4 h-12 flex justify-between">
      <div class="flex items-center space-x-4">
        <router-link to="/sale-items" class="font-bold pe-10 text-3xl">
          ITB MShop
        </router-link>

        <div class="relative w-64">
          <form @submit.prevent="doSearch" class="relative">
            <input
              v-model="searchInput"
              type="text"
              class="w-full px-3 py-1 rounded-lg text-black text-md focus:outline-none border-2 border-white bg-white"
              placeholder="Search"
              aria-label="Search items"
            />
            <button
              v-if="keyword"
              type="button"
              @click="clearSearch"
              class="absolute right-10 top-1/2 -translate-y-1/2 px-2 py-0.5 text-gray-600 hover:text-gray-900"
              aria-label="Clear search"
              title="Clear"
            >
              ×
            </button>
          </form>
        </div>
      </div>

      <div class="flex items-center space-x-6 px-8 text-lg">
        <template v-if="isLoggedIn">
          <div class="relative">
            <button
              ref="triggerRef"
              type="button"
              class="itbms-nav-profile-link inline-flex items-center gap-2 px-3 py-1.5 rounded-md hover:bg-white/10 focus:outline-none"
              aria-haspopup="menu"
              :aria-expanded="isMenuOpen ? 'true' : 'false'"
              @click="toggleMenu"
            >
              <span class="hidden sm:inline">Hello, {{ nickname }}</span>
              <svg class="w-4 h-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.24a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z" clip-rule="evenodd"/>
              </svg>
            </button>

            <div
              v-show="isMenuOpen"
              ref="menuRef"
              class="absolute right-0 mt-2 w-56 bg-white text-black rounded-lg shadow-lg ring-1 ring-black/5 overflow-hidden z-50"
              role="menu"
            >
              <div class="px-4 py-3 border-b border-gray-100">
                <div class="text-sm text-gray-500">Signed in as</div>
                <div class="text-sm font-semibold text-gray-900 truncate">
                  {{ nickname }}
                </div>
                <div class="text-xs text-green-700 mt-0.5 truncate">
                  Role: {{ role }}
                </div>
              </div>

              <button class="w-full text-left px-4 py-2 text-sm hover:bg-gray-100" role="menuitem" @click="goProfile">
                Profile
              </button>
              <button class="w-full text-left px-4 py-2 text-sm hover:bg-gray-100" role="menuitem" @click="goOrder">
                Your Order
              </button>
              <button class="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-gray-100" role="menuitem" @click="signOut">
                Sign out
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <router-link to="/register" class="text-white hover:text-gray-300 pe-10">Sign up</router-link>
          <router-link to="/signin" class="text-white hover:text-gray-300">Sign in</router-link>
        </template>

        <button
          v-if="isSeller"
          type="button"
          @click="goBag"
          class="relative inline-flex items-center gap-2 px-3 py-1.5 rounded-md focus:outline-none hover:bg-white/10"
          :disabled="bagDisabled"
          :aria-disabled="bagDisabled ? 'true' : 'false'"
        >
          <svg viewBox="0 0 24 24" class="w-5 h-5" fill="currentColor" aria-hidden="true">
            <path d="M19 6h-2c0-2.21-1.79-4-4-4S9 3.79 9 6H7c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2m-8-2c1.1 0 2 .9 2 2H9c-1.1 0-.9-2 2-2m-4 4h12v11H7V8m3.5 3c-.28 0-.5.22-.5.5v2c0 .28.22.5.5.5s.5-.22.5-.5v-2c0-.28-.22-.5-.5-.5m4 0c-.28 0-.5.22-.5.5v2c0 .28.22.5.5.5s.5-.22.5-.5v-2c0-.28-.22-.5-.5-.5"/>
          </svg>
          <span class="text-base">Bag</span>
          <span
            v-if="newOrderCount > 0"
            class="absolute -top-1 -right-1 min-w-[1.25rem] h-5 px-1 rounded-full bg-red-500 text-white text-xs font-semibold flex items-center justify-center"
          >
            {{ newOrderCount }}
          </span>
        </button>

        <button
          type="button"
          @click="goCart"
          :disabled="cartDisabled"
          :aria-disabled="cartDisabled ? 'true' : 'false'"
          :class="[
            'relative inline-flex items-center gap-2 px-3 py-1.5 rounded-md focus:outline-none',
            cartDisabled ? 'opacity-50 cursor-not-allowed pointer-events-none' : 'hover:bg-white/10',
          ]"
        >
          <svg viewBox="0 0 24 24" class="w-5 h-5" fill="currentColor" aria-hidden="true">
            <path d="M7 18a2 2 0 1 0 0 4 2 2 0 0 0 0-4m10 0a2 2 0 1 0-.001 3.999A2 2 0 0 0 17 18M7.343 6l.573 2H21a1 1 0 0 1 .976 1.217l-1.8 7.2A2 2 0 0 1 18.237 18H8a2 2 0 0 1-1.941-1.515L3.28 4H2a1 1 0 1 1 0-2h2.72a1 1 0 0 1 .971.757L6.157 6zM8 16h10.237l1.5-6H8.657z"/>
          </svg>
          <span class="text-base">Cart</span>
          <span
            v-if="isLoggedIn && cartCount > 0"
            class="absolute -top-1 -right-1 min-w-[1.25rem] h-5 px-1 rounded-full bg-red-500 text-white text-xs font-semibold flex items-center justify-center"
          >
            {{ cartCount }}
          </span>
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped></style>

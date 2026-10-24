<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const router = useRouter();

const profile = ref(null);
const loading = ref(true);
const error = ref("");
const isSeller = ref(false);

/** Mask middle digits; keep last 3 and the final digit */
const maskShow234FromEnd = (val) => {
  const digits = String(val ?? "").replace(/\D/g, "");
  if (!digits) return "";
  // if 1–2 digits, just return masked length
  if (digits.length <= 2) return "x".repeat(digits.length);
  // >=3 digits → keep last 3 then the last digit separately
  const tail3 = digits.slice(-4, -1) || digits.slice(-3);
  return `xxxxxx${tail3}x`;
};

const fetchProfile = async () => {
  loading.value = true;
  error.value = "";
  try {
    const token = getAccessToken();
    if (!token) {
      router.push("/signin");
      return;
    }

    let uid = null;
    try {
      uid = decodeJwt(token)?.sub ?? null;
    } catch {}
    if (!uid) {
      router.push("/signin");
      return;
    }

    const res = await withAuthFetch(`/v2/users/${uid}`);

    const roleUpper = String(
      res.role || res.userType || res.type || "BUYER"
    ).toUpperCase();

    const rawPhone = res.phone ?? res.phoneNumber ?? res.mobile ?? "";
    const rawBankAcc = res.bankAccount ?? res.account ?? res.bank_no ?? "";
    const showMasked = roleUpper === "SELLER";

    profile.value = {
      id: res.id,
      email: res.email || "",
      nickname: res.nickName || res.nickname || "",
      fullName: res.fullName || res.fullname || "",
      role: roleUpper === "SELLER" ? "Seller" : "Buyer",
      phoneNumber: rawPhone,
      bankAccount: rawBankAcc,
      bankName: res.bankName || "",
      maskedPhone: showMasked ? maskShow234FromEnd(rawPhone) : rawPhone,
      maskedBankAccount: showMasked ? maskShow234FromEnd(rawBankAcc) : rawBankAcc,
    };

    isSeller.value = roleUpper === "SELLER";
  } catch (err) {
    console.error(err);
    error.value = "Failed to load profile.";
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchProfile();
  window.addEventListener("itbms:profile-updated", fetchProfile);
});
onBeforeUnmount(() => {
  window.removeEventListener("itbms:profile-updated", fetchProfile);
});
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex justify-center p-4 sm:p-6 lg:p-8 pt-16 lg:pt-20">
    <div class="w-full max-w-7xl mx-auto bg-white rounded-2xl shadow-xl p-6 sm:p-8">

      <div v-if="loading" class="text-center py-20">
        <p class="text-gray-500">Loading profile...</p>
      </div>

      <div v-else-if="error" class="text-center py-10">
        <p class="text-rose-600 bg-rose-50 inline-block rounded px-4 py-2">{{ error }}</p>
      </div>

      <div v-else-if="profile">
        <!-- Header -->
        <div class="bg-gradient-to-r from-blue-500 to-purple-600 rounded-2xl shadow-lg p-6 sm:p-8 flex items-center">
          <div class="flex-shrink-0">
            <div class="w-20 h-20 sm:w-24 sm:h-24 rounded-full bg-white/20 flex items-center justify-center ring-4 ring-white/50">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 sm:h-14 sm:w-14 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M5.121 17.804A13.937 13.937 0 0112 15c2.21 0 4.305.534 6.121 1.804M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
          </div>

          <div class="ml-4 sm:ml-6 flex-grow pl-5">
            <h1 class="text-2xl sm:text-3xl font-bold text-white break-words">
              {{ profile.nickname || "(No nickname)" }}
            </h1>
            <p class="text-sm sm:text-base text-white/80 itbms-type">
              {{ profile.role }}
            </p>
          </div>

          <div class="ml-4 flex-shrink-0 flex items-center gap-3">
            <router-link
              to="/profile/edit"
              class="px-4 py-2 rounded-lg bg-white text-blue-600 font-semibold shadow-md hover:bg-gray-100 transition-all text-sm sm:text-base itbms-profile-button"
            >
              Edit Profile
            </router-link>

            <router-link
              to="/change-password"
              class="px-4 py-2 rounded-lg bg-white text-blue-600 font-semibold shadow-md hover:bg-gray-100 transition-all text-sm sm:text-base itbms-profile-button"
            >
              Change Password
            </router-link>
          </div>
        </div>

        <div class="mt-8 grid grid-cols-1 lg:grid-cols-3 gap-8">
          <!-- Account details -->
          <div class="lg:col-span-2 pt-5">
            <div class="bg-white rounded-2xl shadow-md border border-gray-200">
              <div class="border-b border-gray-200 px-6 py-4">
                <h2 class="text-lg font-semibold text-gray-800">Account Details</h2>
              </div>
              <dl class="divide-y divide-gray-200">
                <div class="px-6 py-4 grid grid-cols-3 gap-4">
                  <dt class="text-sm font-medium text-gray-500">Nickname</dt>
                  <dd class="text-sm text-gray-900 col-span-2 break-words itbms-nickname">
                    {{ profile.nickname || "-" }}
                  </dd>
                </div>
                <div class="px-6 py-4 grid grid-cols-3 gap-4">
                  <dt class="text-sm font-medium text-gray-500">Full Name</dt>
                  <dd class="text-sm text-gray-900 col-span-2 break-words itbms-fullname">
                    {{ profile.fullName || "-" }}
                  </dd>
                </div>
                <div class="px-6 py-4 grid grid-cols-3 gap-4">
                  <dt class="text-sm font-medium text-gray-500">Email Address</dt>
                  <dd class="text-sm text-gray-900 col-span-2 break-words itbms-email">
                    {{ profile.email || "-" }}
                  </dd>
                </div>
              </dl>
            </div>
          </div>

          <!-- Seller info (masked) -->
          <div class="lg:col-span-1 pt-5" v-if="isSeller">
            <div class="bg-white rounded-2xl shadow-md border border-gray-200">
              <div class="border-b border-gray-200 px-6 py-4">
                <h2 class="text-lg font-semibold text-gray-800">Seller Information</h2>
              </div>
              <dl class="divide-y divide-gray-200">
                <div class="px-6 py-4">
                  <dt class="text-sm font-medium text-gray-500 mb-1">Mobile</dt>
                  <dd class="text-sm text-gray-900 font-mono itbms-mobile">
                    {{ profile.maskedPhone || "-" }}
                  </dd>
                </div>
                <div class="px-6 py-4">
                  <dt class="text-sm font-medium text-gray-500 mb-1">Bank Account</dt>
                  <dd class="text-sm text-gray-900 font-mono itbms-bankAccount">
                    {{ profile.maskedBankAccount || "-" }}
                  </dd>
                </div>
                <div class="px-6 py-4">
                  <dt class="text-sm font-medium text-gray-500 mb-1">Bank Name</dt>
                  <dd class="text-sm text-gray-900 itbms-bankName">
                    {{ profile.bankName || "-" }}
                  </dd>
                </div>
              </dl>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped></style>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { withAuthFetch, getAccessToken, decodeJwt } from "@/libs/fetchUtils";

const router = useRouter();

const profile = ref({ nickname: "", fullName: "" });
const originalProfile = ref({});
const role = ref("");
const loading = ref(true);
const isSaving = ref(false);

// Mask function
const maskShow234FromEnd = (val) => {
  const digits = (val ?? "").toString().replace(/\D/g, "");
  if (!digits) return "";
  let mid;
  if (digits.length >= 4) {
    mid = digits.slice(-4, -1);
  } else if (digits.length >= 3) {
    mid = digits.slice(-3);
  } else {
    mid = digits;
  }
  return `xxxxxx${mid}x`;
};

// Validation
const fieldErrors = ref({ nickname: "", fullName: "" });

const validateNickname = (v) => {
  const s = (v || "").trim();
  if (!s) return "Nickname is required.";
  if (!/^[A-Za-z0-9._-]{2,30}$/.test(s))
    return "Nickname must be 2–30 characters: A–Z, a–z, 0–9, dot(.), underscore(_), hyphen(-).";
  return "";
};

const validateFullName = (v) => {
  const s = (v || "").trim();
  if (!s) return "Fullname is required.";
  if (s.length < 2 || s.length > 60) return "Fullname must be 2–60 characters.";
  if (!/^[\p{L}\s'-]+$/u.test(s))
    return "Fullname allows letters, spaces, apostrophe ('), hyphen (-) only.";
  return "";
};

const runValidation = () => {
  fieldErrors.value.nickname = validateNickname(profile.value.nickname);
  fieldErrors.value.fullName = validateFullName(profile.value.fullName);
  return !fieldErrors.value.nickname && !fieldErrors.value.fullName;
};

const isFormValid = computed(() => {
  return (
    !validateNickname(profile.value.nickname) &&
    !validateFullName(profile.value.fullName)
  );
});

const isModified = computed(() => {
  if (!profile.value || !originalProfile.value) return false;
  return (
    profile.value.nickname !== originalProfile.value.nickname ||
    profile.value.fullName !== originalProfile.value.fullName
  );
});

const canSave = computed(
  () => isModified.value && isFormValid.value && !isSaving.value
);

// Load profile
const fetchProfile = async () => {
  const token = getAccessToken();
  if (!token) return router.push("/signin");
  const user = decodeJwt(token);
  if (!user?.sub) return router.push("/signin");

  try {
    const res = await withAuthFetch(`/v2/users/${user.sub}`);

    profile.value = {
      id: res.id ?? user.sub,
      userType: res.userType || res.role || res.type || "",
      email: res.email || "",
      nickname: res.nickName || res.nickname || "",
      fullName: res.fullName || res.fullname || "",
      phoneNumber: res.phone ?? res.phoneNumber ?? res.mobile ?? "",
      bankAccount: res.bankAccount ?? res.account ?? res.bank_no ?? "",
      bankName: res.bankName || "",
      nationalId: res.nationalId || res.idCardNumber || "",
    };
    originalProfile.value = { ...profile.value };
    role.value = (profile.value.userType || "").toUpperCase();
  } catch (e) {
    console.error("Failed to fetch profile:", e);
  } finally {
    loading.value = false;
  }
};

watch(
  () => profile.value.nickname,
  (newValue) => {
    fieldErrors.value.nickname = validateNickname(newValue);
  }
);

watch(
  () => profile.value.fullName,
  (newValue) => {
    fieldErrors.value.fullName = validateFullName(newValue);
  }
);

onMounted(fetchProfile);

const formError = ref("");

const saveProfile = async () => {
  if (!profile.value) return;

  if (!runValidation()) {
    formError.value = "Please fix the highlighted fields.";
    return;
  }

  const token = getAccessToken();
  const sub = token ? decodeJwt(token)?.sub : null;
  const targetId = profile.value.id ?? sub;
  if (!targetId) {
    alert("ไม่พบรหัสผู้ใช้ (id) กรุณาเข้าสู่ระบบใหม่");
    return router.push("/signin");
  }

  // ส่งทั้ง fullname และ fullName เพื่อรองรับทุกฝั่ง
  const payload = {
    nickname: profile.value.nickname.trim(),
    fullname: profile.value.fullName.trim(),
    fullName: profile.value.fullName.trim(),
  };

  if (
    payload.nickname === (originalProfile.value.nickname || "").trim() &&
    payload.fullname === (originalProfile.value.fullName || "").trim()
  ) {
    formError.value = "No changes to save.";
    return;
  }

  isSaving.value = true;
  formError.value = "";
  try {
    await withAuthFetch(`/v2/users/${targetId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(payload),
    });

    window.dispatchEvent(
      new CustomEvent("itbms:profile-updated", {
        detail: { nickname: payload.nickname },
      })
    );

    alert("Profile data is updated successfully.");
    router.push("/profile");
  } catch (e) {
    console.error("Update error:", e);
    formError.value = "Update failed";
  } finally {
    isSaving.value = false;
  }
};

// Cancel edit
const cancelEdit = () => {
  profile.value = { ...originalProfile.value };
  formError.value = "";
  router.push("/profile");
};
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex justify-center p-4 sm:p-6 lg:p-8 pt-16 lg:pt-20">
    <div class="w-full max-w-7xl mx-auto bg-white rounded-2xl shadow-xl p-6 sm:p-8">
      <div v-if="loading" class="text-center py-20">
        <p class="text-gray-500">Loading profile...</p>
      </div>

      <div v-if="!loading && profile">
        <div class="bg-gradient-to-r from-blue-500 to-purple-600 rounded-2xl shadow-lg p-6 sm:p-8 flex items-center">
          <div class="flex-shrink-0">
            <div class="w-20 h-20 sm:w-24 sm:h-24 rounded-full bg-white/20 flex items-center justify-center ring-4 ring-white/50">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 sm:h-14 sm:w-14 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5.121 17.804A13.937 13.937 0 0112 15c2.21 0 4.305.534 6.121 1.804M15 11a3 3 0 11-6 0 3 3 0 016 0z"/>
              </svg>
            </div>
          </div>

          <div class="ml-4 sm:ml-6 flex-grow pl-5">
            <h1 class="text-2xl sm:text-3xl font-bold text-white break-words">{{ profile.nickname || "Edit Profile" }}</h1>
            <p class="text-sm sm:text-base text-white/80">{{ role }}</p>
          </div>
        </div>

        <div class="mt-8 grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div class="lg:col-span-2 pt-5">
            <div class="bg-white rounded-2xl shadow-md border border-gray-200">
              <div class="border-b border-gray-200 px-6 py-4">
                <h2 class="text-lg font-semibold text-gray-800">Account Details</h2>
              </div>

              <div class="p-6 space-y-6">
                <div>
                  <label class="block text-sm font-medium text-gray-500">Nickname</label>
                  <input
                    v-model="profile.nickname"
                    type="text"
                    placeholder="Enter your nickname"
                    :disabled="isSaving"
                    class="mt-1 block w-full border rounded-md shadow-sm p-3 transition-all itbms-nickname"
                    :class="fieldErrors.nickname ? 'border-red-400 bg-red-50 focus:ring-red-500 focus:border-red-500' : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'"
                  />
                  <p v-if="fieldErrors.nickname" class="mt-2 text-xs text-red-600">{{ fieldErrors.nickname }}</p>
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-500">Full Name</label>
                  <input
                    v-model="profile.fullName"
                    type="text"
                    placeholder="Enter your full name"
                    :disabled="isSaving"
                    class="mt-1 block w-full border rounded-md shadow-sm p-3 transition-all itbms-fullname"
                    :class="fieldErrors.fullName ? 'border-red-400 bg-red-50 focus:ring-red-500 focus:border-red-500' : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'"
                  />
                  <p v-if="fieldErrors.fullName" class="mt-2 text-xs text-red-600">{{ fieldErrors.fullName }}</p>
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-500">Email Address</label>
                  <input
                    :value="profile.email"
                    readonly
                    class="mt-1 block w-full border-gray-200 rounded-md shadow-sm p-3 bg-gray-100 text-gray-500 cursor-not-allowed itbms-email"
                  />
                </div>
              </div>

              <div class="border-t border-gray-200 px-6 py-4">
                <div v-if="formError" class="mb-4 bg-red-50 border border-red-200 rounded-md p-3 text-sm text-red-700">
                  {{ formError }}
                </div>
                <div class="flex items-center gap-4">
                  <button
                    @click="saveProfile"
                    :disabled="!canSave"
                    class="px-5 py-2 rounded-md bg-green-600 text-white font-semibold hover:bg-blue-700 transition-all disabled:bg-gray-400 disabled:cursor-not-allowed itbms-save-button"
                  >
                    <span v-if="isSaving">Saving...</span>
                    <span v-else>Save Changes</span>
                  </button>
                  <button
                    @click="cancelEdit"
                    :disabled="isSaving"
                    class="px-5 py-2 rounded-md bg-red-500 text-white font-semibold hover:bg-red-700 transition-all disabled:opacity-50 itbms-cancel-button"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="lg:col-span-1 pt-5">
            <template v-if="role === 'SELLER'">
              <div class="bg-white rounded-2xl shadow-md border border-gray-200">
                <div class="border-b border-gray-200 px-6 py-4">
                  <h2 class="text-lg font-semibold text-gray-800">Seller Information</h2>
                </div>
                <div class="p-6 space-y-6">
                  <!-- ให้ Cypress จับเป็น input ตามที่สเปคต้องการ -->
                  <div>
                    <label class="block text-sm font-medium text-gray-500">Mobile</label>
                    <input
                      :value="maskShow234FromEnd(profile?.phoneNumber)"
                      readonly
                      class="mt-1 block w-full rounded-md shadow-sm p-3 bg-gray-100 text-gray-700 itbms-mobile"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-500">Bank Account</label>
                    <input
                      :value="maskShow234FromEnd(profile?.bankAccount)"
                      readonly
                      class="mt-1 block w-full rounded-md shadow-sm p-3 bg-gray-100 text-gray-700 itbms-bankAccount"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-500">Bank Name</label>
                    <input
                      :value="profile.bankName"
                      readonly
                      class="mt-1 block w-full rounded-md shadow-sm p-3 bg-gray-100 text-gray-700 itbms-bankName"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped></style>

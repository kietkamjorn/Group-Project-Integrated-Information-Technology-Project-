<script setup>
import { ref, computed } from "vue";
import { postMultipart } from "../libs/fetchUtils";

const accountType = ref("buyer");

const form = ref({
  nickname: "",
  email: "",
  password: "",
  fullname: "",
  phone: "",
  bankAccount: "",
  bankName: "",
  idCardNumber: "",
  idFront: null,
  idBack: null,
});

const isSubmitting = ref(false);

/* ===== helpers ===== */
const nonSpace = (s) => (s ?? "").replace(/\s+/g, ""); // ใช้กับ password
const trimmed = (s) => (s ?? "").trim(); // ใช้กับฟิลด์อื่น

// fullname: ต้องไม่เป็นช่องว่างล้วน
const requiredOkTrim = (s) => trimmed(s).length > 0;
// ฟิลด์อื่น ๆ: ใช้ trim ตอน validate (ยอมให้มีช่องว่างตอนพิมพ์ แต่ต้องไม่ว่างหลัง trim)
const requiredOkTrimmed = (s) => trimmed(s).length > 0;
// password: ห้ามมี space (จะลบทิ้งทั้งหมด) และต้องไม่ว่างหลังลบ
const requiredOkPassword = (s) => passRule.test(s ?? "");
const passRule = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,14}$/;
/* ===== validate ===== */
const isFormValid = computed(() => {
  if (
    !requiredOkTrimmed(form.value.nickname) ||
    !requiredOkTrimmed(form.value.email) ||
    !requiredOkPassword(form.value.password) ||
    !requiredOkTrim(form.value.fullname)
  ) {
    return false;
  }

  if (accountType.value === "seller") {
    if (
      !requiredOkTrimmed(form.value.phone) ||
      !requiredOkTrimmed(form.value.bankAccount) ||
      !requiredOkTrimmed(form.value.bankName) ||
      !requiredOkTrimmed(form.value.idCardNumber) ||
      !form.value.idFront ||
      !form.value.idBack
    ) {
      return false;
    }
  }
  return true;
});

/* ===== files ===== */
const handleFileChange = (e, field) => {
  const f = e.target.files?.[0];
  if (!f) {
    form.value[field] = null;
    return;
  }
  if (!/^image\//.test(f.type)) return alert("กรุณาเลือกไฟล์รูปภาพ");
  if (f.size > 2 * 1024 * 1024) return alert("ขนาดไฟล์ต้องไม่เกิน 2MB");
  form.value[field] = f;
};

/* ===== reset ===== */
const resetForm = () => {
  form.value.nickname = "";
  form.value.email = "";
  form.value.password = "";
  form.value.fullname = "";
  form.value.phone = "";
  form.value.bankAccount = "";
  form.value.bankName = "";
  form.value.idCardNumber = "";
  form.value.idFront = null;
  form.value.idBack = null;
  accountType.value = "buyer";
};

/* ===== submit ===== */
const submitForm = async () => {
  if (!isFormValid.value) return;
  isSubmitting.value = true;

  const fd = new FormData();
  fd.append("nickName", trimmed(form.value.nickname));
  fd.append("email", trimmed(form.value.email));
  fd.append("password", nonSpace(form.value.password));
  fd.append("fullName", trimmed(form.value.fullname));
  fd.append("userType", accountType.value.toUpperCase());

  if (accountType.value === "seller") {
    if (form.value.phone) fd.append("phoneNumber", trimmed(form.value.phone));
    if (form.value.bankAccount)
      fd.append("bankAccount", trimmed(form.value.bankAccount));
    if (form.value.bankName)
      fd.append("bankName", trimmed(form.value.bankName));
    if (form.value.idCardNumber)
      fd.append("idCardNumber", trimmed(form.value.idCardNumber));
    if (form.value.idFront) fd.append("idCardImageFront", form.value.idFront);
    if (form.value.idBack) fd.append("idCardImageBack", form.value.idBack);
  }

  try {
    await postMultipart("v2/users/register", fd);
    alert(
      "บัญชีผู้ใช้ได้รับการลงทะเบียนเรียบร้อยแล้ว กรุณาตรวจสอบอีเมลเพื่อยืนยันบัญชี"
    );
    window.location.href = "/kp4/sale-items";
  } catch (err) {
    console.error(err);
    const msg = err?.response?.data?.message || "เกิดข้อผิดพลาดในการลงทะเบียน";
    alert(msg);
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<template>
  <div
    class="flex justify-center items-center min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100"
  >
    <div
      class="bg-white p-10 rounded-2xl shadow-xl w-full max-w-lg border border-gray-100"
    >
      <h2 class="text-3xl font-extrabold mb-6 text-center text-indigo-700">
        Register ITB-Mshop
      </h2>

      <!-- Account type switch -->
      <div class="flex justify-center mb-8 space-x-6 pt-5">
        <div class="pr-8">
          <button
            @click="accountType = 'buyer'"
            :class="
              accountType === 'buyer'
                ? 'bg-indigo-700 text-white shadow-md'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            "
            class="px-6 py-2 rounded-lg font-semibold transition-all"
          >
            Buyer
          </button>
        </div>

        <button
          @click="accountType = 'seller'"
          :class="
            accountType === 'seller'
              ? 'bg-indigo-700 text-white shadow-md'
              : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
          "
          class="px-6 py-2 rounded-lg font-semibold transition-all"
        >
          Seller
        </button>
      </div>

      <!-- Buyer fields -->
      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Nickname*</label>
        <input
          v-model="form.nickname"
          type="text"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
        />
      </div>

      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Email*</label>
        <input
          v-model="form.email"
          type="email"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
        />
      </div>

      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Password*</label>
        <input
          v-model="form.password"
          type="password"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
          @keydown.space.prevent
          @input="form.password = form.password.replace(/\s+/g, '')"
        />
        <p class="text-xs text-gray-500 mt-2">
          Minimum length 8, include lower, upper, number & special character
        </p>
      </div>

      <div class="mb-5">
        <label class="block mb-2 font-medium text-gray-700">Fullname*</label>
        <input
          v-model="form.fullname"
          type="text"
          class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
        />
      </div>

      <!-- Seller fields -->
      <div v-if="accountType === 'seller'" class="pt-7">
        <div class="border-t pb-6"></div>
        <h3 class="text-lg font-semibold text-gray-800 mb-4 pb-2">
          Seller Information
        </h3>

        <div class="mb-4">
          <label class="block mb-1">Mobile Number*</label>
          <input
            v-model="form.phone"
            type="text"
            class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
          />
        </div>

        <div class="mb-4">
          <label class="block mb-1">Bank Account Number*</label>
          <input
            v-model="form.bankAccount"
            type="text"
            class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
          />
        </div>

        <div class="mb-4">
          <label class="block mb-1">Bank Name*</label>
          <input
            v-model="form.bankName"
            type="text"
            class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
          />
        </div>

        <div class="mb-4 pb-4">
          <label class="block mb-1">National ID Number*</label>
          <input
            v-model="form.idCardNumber"
            type="text"
            class="w-full border rounded-lg px-3 py-2 focus:ring-2 focus:ring-indigo-400"
          />
        </div>

        <div class="mb-4">
          <label class="block mb-2 text-sm font-medium text-gray-700">
            National ID Photo (Front) <span class="text-red-500">*</span>
          </label>
          <input
            type="file"
            accept="image/*"
            @change="(e) => handleFileChange(e, 'idFront')"
            class="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-500 hover:file:bg-blue-100 cursor-pointer"
          />
        </div>

        <div class="mb-4 pt-2">
          <label class="block mb-2 text-sm font-medium text-gray-700">
            National ID Photo (Back) <span class="text-red-500">*</span>
          </label>
          <input
            type="file"
            accept="image/*"
            @change="(e) => handleFileChange(e, 'idBack')"
            class="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-500 hover:file:bg-blue-100 cursor-pointer"
          />
        </div>
      </div>

      <!-- Action buttons -->
      <div class="flex justify-between mt-8 pt-5">
        <button
          :disabled="!isFormValid || isSubmitting"
          @click="submitForm"
          class="bg-indigo-700 text-white px-6 py-2 rounded-lg shadow-md hover:bg-indigo-800 disabled:opacity-50"
        >
          {{ isSubmitting ? "Submitting..." : "Submit" }}
        </button>
        <button
          @click="resetForm"
          class="bg-gray-200 text-gray-700 px-6 py-2 rounded-lg hover:bg-gray-300"
        >
          Cancel
        </button>
      </div>
    </div>
  </div>
</template>

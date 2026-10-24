<script setup></script>

<template>
  <div class="min-h-screen flex items-center justify-center p-6">
    <div
      class="min-h-screen min-w-screen shadow-md rounded-md flex flex-col md:flex-row items-center p-8 w-full max-w-5xl"
    >
      <div class="flex-1 text-center md:text-left mb-6 md:mb-0 pl-12">
        <h2
          class="text-5xl md:text-5xl font-bold text-black mb-4 leading-16 text-shadow-lg"
        >
          เลือกมือถือสุดล้ำ<br />
          ราคาดี บริการเยี่ยม<br />
          ครบทุกความต้องการ!
        </h2>
        <br />
        <div class="flex justify-center md:justify-start gap-10 mb-4 min-w-0">
          <router-link
            :to="{ name: 'Home' }"
            class="bg-blue-500 hover:bg-blue-600 text-white px-6 py-3 rounded-lg flex items-center w-fit text-lg itbms-shopnow"
          >
            Shop Now
            <span class="ml-2">✨</span>
          </router-link>
        </div>
      </div>

      <!-- Right: Image and Price -->
      <div class="flex-1 flex flex-col items-center justify-center">
        <img
          src="https://media-cdn.bnn.in.th/360354/iphone-15-promax-natural-Titanium.jpg"
          alt="iPhone 14 Pro Max"
          class="w-150 md:w-150 mx-auto"
        />
        <div class="mt-4 text-center text-black">
          <h3 class="text-lg font-semibold">Apple iPhone 15 Pro Max</h3>
          <p class="text-md">Start ฿39,990</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
</script>

<style scoped></style>

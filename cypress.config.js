import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    specPattern: "cypress/e2e/**/*.{cy,spec}.{js,jsx,ts,tsx}",
    baseUrl: process.env.CYPRESS_TARGET === 'prod'
      ? 'http://intproj24.sit.kmutt.ac.th/kp4'
      : 'http://localhost:5173/kp4',
    experimentalRunAllSpecs: true,
    viewportWidth: 1280,
    viewportHeight: 720,

    setupNodeEvents(on, config) {
      const prodAPI = 'http://intproj24.sit.kmutt.ac.th/kp4/itb-mshop';
      const localAPI = 'http://localhost:8080/itb-mshop';

      config.baseAPI = (process.env.CYPRESS_BASE_API ||
        (process.env.CYPRESS_TARGET === 'prod' ? prodAPI : localAPI)
      ).replace(/\/$/, ''); // กัน / ท้าย

      return config;
    },
  },
});


import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    specPattern: "cypress/e2e/**/*.{cy,spec}.{js,jsx,ts,tsx}",
    baseUrl: process.env.CYPRESS_TARGET === 'prod'
      ? 'http://168.110.9.184/kp4/'
      : 'http://localhost:5173/kp4',
    experimentalRunAllSpecs: true,
    viewportWidth: 1280,
    viewportHeight: 720,

    setupNodeEvents(on, config) {
      const prodAPI = "http://168.110.9.184/kp4/itb-mshop"; 
      const localAPI = "http://localhost:8080/itb-mshop";
    
      const target = process.env.CYPRESS_TARGET || config.env.TARGET;
      const customBaseApi = process.env.CYPRESS_BASE_API || config.env.BASE_API;
    
      config.env.baseAPI = (
        customBaseApi || (target === "prod" ? prodAPI : localAPI)
      ).replace(/\/$/, "");
    
      return config;
    },
  },
});
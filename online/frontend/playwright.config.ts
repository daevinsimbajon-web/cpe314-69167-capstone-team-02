import { defineConfig } from '@playwright/test';
// Browser suites create accounts and games. Require a deliberately isolated test app.
const testBase=process.env.HOOTLY_E2E_BASE_URL;
if(!testBase||!/^http:\/\/(localhost|127\.0\.0\.1):5174\/?$/.test(testBase))throw new Error('Run browser tests against the isolated app on port 5174. Set HOOTLY_E2E_BASE_URL=http://localhost:5174; never use the regular app database.');
export default defineConfig({
  testDir:'./e2e',timeout:60000,workers:1,fullyParallel:false,
  use:{baseURL:testBase,headless:true,trace:'retain-on-failure'},
  projects:[{name:'chromium',use:{browserName:'chromium',channel:process.env.PLAYWRIGHT_CHANNEL}}],
});

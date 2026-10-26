import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the running full stack (docker compose up), not mocks.
 * اختبارات شاملة على النظام الكامل وهو شغال (docker compose up)، مو على بيانات وهمية.
 *
 * BASE_URL defaults to the compose frontend (nginx on :8083).
 * BASE_URL افتراضيًا هو واجهة compose (nginx على المنفذ 8083).
 */
export default defineConfig({
  testDir: './tests',
  // Tests share one database, so they run one at a time to keep counts and boards predictable.
  // الاختبارات تشترك في قاعدة بيانات وحدة، فتشتغل وحدة وحدة حتى تبقى الأعداد واللوحة متوقعة.
  workers: 1,
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:8083',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Arabic is the primary market, so the browser asks for Arabic first.
    // العربية هي السوق الأساسي، فالمتصفح يطلب العربية أولًا.
    locale: 'ar-SA',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
});

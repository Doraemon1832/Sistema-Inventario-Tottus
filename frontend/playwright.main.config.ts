import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/main',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  timeout: 45_000,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure'
  },
  projects: [{ name: 'main-chromium', use: { ...devices['Desktop Chrome'] } }]
});

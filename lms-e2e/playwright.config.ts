import { defineConfig, devices } from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config();

/**
 * Paths to the browser storage-state snapshots produced by tests/auth.setup.ts.
 * Import these constants in test files that need a pre-authenticated context:
 *
 *   import { STORAGE_STATE } from '../../playwright.config';
 *   test.use({ storageState: STORAGE_STATE.student });
 */
export const STORAGE_STATE = {
  admin:   '.auth/admin.json',
  teacher: '.auth/teacher.json',
  student: '.auth/student.json',
} as const;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,

  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
  ],

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: { Accept: 'application/json' },
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
  },

  projects: [
    // ── 1. Auth bootstrap ─────────────────────────────────────────────────────
    // Runs tests/auth.setup.ts, which logs in as each role via the REST API,
    // seeds Zustand localStorage, and saves .auth/{role}.json storage-state files.
    // All UI projects below declare this as a dependency so it runs first.
    {
      name: 'auth setup',
      testMatch: /auth\.setup\.ts/,
    },

    // ── 2. UI browser project (Chromium) ─────────────────────────────────────
    // Individual test files declare which role they need:
    //   test.use({ storageState: STORAGE_STATE.student });
    // Tests that test the unauthenticated UI (login page, etc.) omit this line.
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['auth setup'],
      testIgnore: /auth\.setup\.ts/,
    },

    // ── 3. API-only tests ─────────────────────────────────────────────────────
    // Files ending in .api.spec.ts run here. They use ApiHelper directly and
    // do not open a browser window, so no auth setup dependency is needed.
    {
      name: 'api',
      testMatch: /.*\.api\.spec\.ts/,
      use: { browserName: 'chromium' },
    },
  ],

  /* All test artifacts (traces, screenshots, videos) go here. */
  outputDir: 'test-results/',
});

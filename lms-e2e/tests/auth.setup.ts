/**
 * Auth Setup — runs once before all UI test projects.
 *
 * For each configured role (admin, teacher, student) this file:
 *   1. Calls POST /api/auth/login via the REST API to get JWT tokens.
 *   2. Opens a blank browser page and seeds the Zustand `lms_auth` key in
 *      localStorage with the token and user payload (via injectAuthState).
 *   3. Saves the resulting browser storage state to .auth/{role}.json so
 *      every subsequent UI test can reuse the session without re-logging in.
 *
 * Individual test files opt into a role by adding:
 *   import { STORAGE_STATE } from '../../playwright.config';
 *   test.use({ storageState: STORAGE_STATE.student });
 */

import { test as setup, expect, request as playwrightRequest } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { STORAGE_STATE } from '../playwright.config';
import { LoginResponse, injectAuthState } from '../helpers/auth.helper';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080/api';
const BASE_URL  = process.env.BASE_URL   ?? 'http://localhost:8080';

/**
 * Authenticate via the REST API and return the parsed login response.
 */
async function apiLogin(email: string, password: string): Promise<LoginResponse> {
  const ctx = await playwrightRequest.newContext();
  const res = await ctx.post(`${API_BASE}/auth/login`, {
    data: { email, password },
    headers: { 'Content-Type': 'application/json' },
  });

  if (!res.ok()) {
    const body = await res.text();
    throw new Error(`Login failed for ${email} — HTTP ${res.status()}: ${body}`);
  }

  const data = (await res.json()) as LoginResponse;
  await ctx.dispose();
  return data;
}

/**
 * Seed Zustand localStorage (via injectAuthState) and save the resulting
 * browser storage state to disk.
 */
async function seedAndSave(
  page: import('@playwright/test').Page,
  loginData: LoginResponse,
  outputPath: string,
): Promise<void> {
  // Navigate to the app root so localStorage is scoped to the right origin.
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });
  await injectAuthState(page, loginData);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  await page.context().storageState({ path: outputPath });
}

// ---------------------------------------------------------------------------
// Setup tests — Playwright runs each `setup(…)` in sequence
// ---------------------------------------------------------------------------

setup('authenticate as admin', async ({ page }) => {
  const email    = process.env.ADMIN_EMAIL    ?? 'admin@lms.test';
  const password = process.env.ADMIN_PASSWORD ?? 'Admin@12345';

  const data = await apiLogin(email, password);
  expect(data.accessToken, 'Admin login must return an access token').toBeTruthy();

  await seedAndSave(page, data, STORAGE_STATE.admin);
  console.log(`  ✓ Admin storage state saved → ${STORAGE_STATE.admin}`);
});

setup('authenticate as teacher', async ({ page }) => {
  const email    = process.env.TEACHER_EMAIL    ?? 'teacher@lms.test';
  const password = process.env.TEACHER_PASSWORD ?? 'Teacher@12345';

  const data = await apiLogin(email, password);
  expect(data.accessToken, 'Teacher login must return an access token').toBeTruthy();

  await seedAndSave(page, data, STORAGE_STATE.teacher);
  console.log(`  ✓ Teacher storage state saved → ${STORAGE_STATE.teacher}`);
});

setup('authenticate as student', async ({ page }) => {
  const email    = process.env.STUDENT_EMAIL    ?? 'student@lms.test';
  const password = process.env.STUDENT_PASSWORD ?? 'Student@12345';

  const data = await apiLogin(email, password);
  expect(data.accessToken, 'Student login must return an access token').toBeTruthy();

  await seedAndSave(page, data, STORAGE_STATE.student);
  console.log(`  ✓ Student storage state saved → ${STORAGE_STATE.student}`);
});

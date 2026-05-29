import { APIRequestContext, Page } from '@playwright/test';
import { ApiHelper } from './api.helper';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

/** Full response from POST /api/auth/login and POST /api/auth/refresh. */
export interface LoginResponse extends AuthTokens {
  /** Access token lifetime in milliseconds. */
  expiresIn: number;
  user: {
    id: string;
    email: string;
    name: string;
    role: string;
  };
}

// ---------------------------------------------------------------------------
// API helpers
// ---------------------------------------------------------------------------

/**
 * Authenticates via the REST API and returns the full login response
 * (tokens + user info + expiresIn).
 */
export async function loginFull(
  request: APIRequestContext,
  credentials: LoginCredentials,
): Promise<LoginResponse> {
  const api = new ApiHelper(request);
  return api.post<LoginResponse>('/auth/login', credentials);
}

/**
 * Authenticates via the REST API and returns only the JWT token pair.
 * Use this in fixtures and setup files — never store tokens in test code.
 */
export async function loginViaApi(
  request: APIRequestContext,
  credentials: LoginCredentials,
): Promise<AuthTokens> {
  return loginFull(request, credentials);
}

/**
 * Rotates a refresh token — calls POST /api/auth/refresh.
 * Returns a new token pair with updated expiry.
 */
export async function refreshTokens(
  request: APIRequestContext,
  refreshToken: string,
): Promise<LoginResponse> {
  const api = new ApiHelper(request);
  return api.post<LoginResponse>('/auth/refresh', { refreshToken });
}

/**
 * Revokes a refresh token — calls POST /api/auth/logout (204 No Content).
 * Silently succeeds even if the token is already expired.
 */
export async function logoutViaApi(
  request: APIRequestContext,
  refreshToken: string,
): Promise<void> {
  const api = new ApiHelper(request);
  // logout returns 204; ApiHelper.post expects JSON — use delete-style path.
  await api.post<void>('/auth/logout', { refreshToken });
}

// ---------------------------------------------------------------------------
// Browser storage helpers
// ---------------------------------------------------------------------------

/**
 * Seeds the Zustand `lms_auth` key in the page's localStorage from a
 * `LoginResponse`.  The page must already be navigated to the app origin.
 *
 * Zustand-persist storage shape:
 *   { state: { accessToken, refreshToken, expiresAt, user }, version: 0 }
 *
 * Use this in UI tests that need to authenticate as a dynamically-created
 * user (e.g., one produced by `seedWorld`) rather than a pre-seeded role.
 */
export async function injectAuthState(
  page: Page,
  loginData: LoginResponse,
): Promise<void> {
  const storageEntry = {
    state: {
      accessToken:  loginData.accessToken,
      refreshToken: loginData.refreshToken,
      expiresAt:    Date.now() + loginData.expiresIn,
      user:         loginData.user,
    },
    version: 0,
  };
  await page.evaluate(
    ([key, value]: [string, typeof storageEntry]) =>
      localStorage.setItem(key, JSON.stringify(value)),
    ['lms_auth', storageEntry] as [string, typeof storageEntry],
  );
}

/**
 * Navigates to the app root, injects auth for the given credentials, then
 * reloads so the app picks up the seeded state.
 *
 * Convenience wrapper over `loginFull` + `injectAuthState`.
 */
export async function loginAsUser(
  page: Page,
  request: APIRequestContext,
  credentials: LoginCredentials,
): Promise<LoginResponse> {
  const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080';
  const data = await loginFull(request, credentials);
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  await injectAuthState(page, data);
  await page.reload({ waitUntil: 'domcontentloaded' });
  return data;
}

// ---------------------------------------------------------------------------
// Convenience token getters
// ---------------------------------------------------------------------------

/** Convenience: obtain an ADMIN access token using env-configured credentials. */
export async function getAdminToken(request: APIRequestContext): Promise<string> {
  const tokens = await loginViaApi(request, {
    email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
    password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
  });
  return tokens.accessToken;
}

/** Convenience: obtain a TEACHER access token. */
export async function getTeacherToken(request: APIRequestContext): Promise<string> {
  const tokens = await loginViaApi(request, {
    email: process.env.TEACHER_EMAIL ?? 'teacher@lms.test',
    password: process.env.TEACHER_PASSWORD ?? 'Teacher@12345',
  });
  return tokens.accessToken;
}

/** Convenience: obtain a STUDENT access token. */
export async function getStudentToken(request: APIRequestContext): Promise<string> {
  const tokens = await loginViaApi(request, {
    email: process.env.STUDENT_EMAIL ?? 'student@lms.test',
    password: process.env.STUDENT_PASSWORD ?? 'Student@12345',
  });
  return tokens.accessToken;
}

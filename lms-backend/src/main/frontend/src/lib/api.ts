import axios, { type AxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/authStore';
import type { LoginResponse } from '@/types/api';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

/** Endpoints that must never trigger a refresh attempt of their own. */
const AUTH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

// Attach JWT from Zustand store to every outgoing request.
api.interceptors.request.use((config) => {
  // The instance already carries `baseURL: '/api'`. A path that repeats the
  // prefix resolves to /api/api/… and 404s — which reads as "this feature is
  // broken" rather than "this URL is wrong", so fail loudly in development.
  if (import.meta.env.DEV && config.url?.startsWith('/api/')) {
    throw new Error(
      `API path "${config.url}" repeats the /api prefix already set by baseURL. ` +
        `Use "${config.url.slice(4)}" instead.`,
    );
  }

  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

/**
 * In-flight refresh, shared by every request that hits a 401 at once.
 *
 * Without this, a page that fires six queries on mount would attempt six
 * refreshes; the server rotates refresh tokens, so five of them would present
 * an already-consumed token and fail, logging the user out.
 */
let refreshInFlight: Promise<string> | null = null;

function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) return refreshInFlight;

  const { refreshToken } = useAuthStore.getState();
  if (!refreshToken) return Promise.reject(new Error('No refresh token'));

  refreshInFlight = axios
    // A bare axios call, so this request skips the interceptors above and
    // cannot recurse back into refresh handling.
    .post<LoginResponse>('/api/auth/refresh', { refreshToken })
    .then((res) => {
      useAuthStore.getState().login(res.data);
      return res.data.accessToken;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

// On 401, try once to refresh the session before giving up on it.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error);
    }

    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = AUTH_PATHS.some((path) => original?.url?.startsWith(path));

    // An expired access token is the common case and is recoverable; only a
    // failed refresh means the session is genuinely over. Logging out on every
    // 401 threw away unsaved work — a half-finished quiz attempt, say.
    if (original && !original._retried && !isAuthCall) {
      original._retried = true;
      try {
        const accessToken = await refreshAccessToken();
        original.headers = { ...original.headers, Authorization: `Bearer ${accessToken}` };
        return api(original);
      } catch {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }
    }

    useAuthStore.getState().logout();
    return Promise.reject(error);
  },
);

export default api;

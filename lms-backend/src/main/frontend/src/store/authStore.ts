import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { LoginResponse, UserInfo } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  /** Absolute timestamp (ms) when the access token expires */
  expiresAt: number | null;
  user: UserInfo | null;
  login: (response: LoginResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      user: null,
      login: ({ accessToken, refreshToken, expiresIn, user }) =>
        set({
          accessToken,
          refreshToken,
          expiresAt: Date.now() + expiresIn,
          user,
        }),
      logout: () =>
        set({ accessToken: null, refreshToken: null, expiresAt: null, user: null }),
    }),
    {
      name: 'lms_auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        user: state.user,
      }),
    },
  ),
);

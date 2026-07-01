import { create } from 'zustand';
import type { MeResponse } from '@readmind/shared';
import * as authApi from '@/api/auth';
import { tokens } from '@/lib/tokens';

interface AuthState {
  user: MeResponse | null;
  /** 초기 토큰 검증(getMe) 진행 중 여부 — 라우트 가드가 깜빡임 없이 기다리게 한다. */
  initializing: boolean;
  isAuthenticated: () => boolean;
  bootstrap: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  initializing: true,

  isAuthenticated: () => get().user !== null,

  /** 새로고침 시 저장된 토큰으로 사용자 복원. 토큰이 없거나 만료면 비로그인 상태. */
  bootstrap: async () => {
    if (!tokens.getAccess()) {
      set({ initializing: false });
      return;
    }
    try {
      const me = await authApi.getMe();
      set({ user: me, initializing: false });
    } catch {
      tokens.clear();
      set({ user: null, initializing: false });
    }
  },

  login: async (email, password) => {
    const res = await authApi.login({ email, password });
    tokens.set(res.accessToken, res.refreshToken);
    const me = await authApi.getMe();
    set({ user: me });
  },

  signup: async (email, password, displayName) => {
    await authApi.signup({ email, password, displayName });
    await get().login(email, password);
  },

  logout: () => {
    tokens.clear();
    set({ user: null });
  },
}));

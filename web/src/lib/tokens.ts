/** JWT 토큰 보관 — Phase 0은 localStorage. (운영 강화는 추후: httpOnly 쿠키 등.) */
const ACCESS_KEY = 'readmind.accessToken';
const REFRESH_KEY = 'readmind.refreshToken';

export const tokens = {
  getAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  },
  getRefresh(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  setAccess(accessToken: string): void {
    localStorage.setItem(ACCESS_KEY, accessToken);
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

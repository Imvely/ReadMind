/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API base URL. 미설정 시 '/api/v1'(same-origin, dev는 vite 프록시 경유). 절대 URL이면 브라우저가 직접 호출(백엔드 CORS 필요). */
  readonly VITE_API_BASE_URL?: string;
}

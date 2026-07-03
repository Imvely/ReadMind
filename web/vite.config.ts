/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// @readmind/shared는 TS 소스를 alias로 직접 참조한다(별도 빌드 없음, 모노레포 SSOT).
export default defineConfig(({ mode }) => {
  // 주의: .env.local의 값은 config 자신의 process.env에 주입되지 않는다(import.meta.env 전용).
  // loadEnv 없이 process.env로 읽으면 VITE_DEV_API_TARGET이 조용히 무시되어
  // 프록시가 기본값(로컬 docker 백엔드)으로 가는 사고가 난다 — 반드시 loadEnv 경유.
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), '');
  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@readmind/shared': fileURLToPath(
          new URL('../packages/shared/src/index.ts', import.meta.url),
        ),
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      // 개발 중 백엔드(/api) 프록시 — CORS 없이 same-origin으로 호출.
      // VITE_DEV_API_TARGET 로 로컬(기본) ↔ Cloud Run 백엔드를 전환. 브라우저는 항상 localhost:5173만 보므로 CORS 불필요.
      proxy: {
        '/api': {
          target: env.VITE_DEV_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});

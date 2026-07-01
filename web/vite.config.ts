/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// @readmind/shared는 TS 소스를 alias로 직접 참조한다(별도 빌드 없음, 모노레포 SSOT).
export default defineConfig({
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
    // 개발 중 백엔드(/api/v1) 프록시 — CORS 없이 same-origin으로 호출.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});

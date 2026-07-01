# ReadMind 웹 (M3, React/Vite). 정적 빌드 → nginx 서빙.
# @readmind/shared(모노레포) 참조 때문에 빌드 컨텍스트는 레포 루트여야 한다:
#   docker build -f web/Dockerfile .   (compose는 context: . 로 설정)

# ── 1) 빌드 스테이지 ──
FROM node:20-alpine AS build
WORKDIR /app

# 의존성 캐시 레이어: 워크스페이스 매니페스트 + 락파일만 먼저 복사.
COPY package.json package-lock.json ./
COPY packages/shared/package.json packages/shared/package.json
COPY web/package.json web/package.json
RUN npm ci

# 소스 복사 후 프로덕션 빌드(tsc --noEmit + vite build). shared 는 alias 로 직접 참조.
COPY packages/shared packages/shared
COPY web web
RUN npm run build --workspace web

# ── 2) 런타임 스테이지 (정적 서빙) ──
FROM nginx:alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/web/dist /usr/share/nginx/html
EXPOSE 80
